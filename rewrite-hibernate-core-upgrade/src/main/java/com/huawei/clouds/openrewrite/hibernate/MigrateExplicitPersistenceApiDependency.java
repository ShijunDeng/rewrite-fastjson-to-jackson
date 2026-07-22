package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Migrates only the explicit standard Java Persistence 2.2 dependency to Jakarta Persistence 3.2. */
public final class MigrateExplicitPersistenceApiDependency extends Recipe {
    private static final String OLD = "javax.persistence:javax.persistence-api:2.2";
    private static final String TARGET = "jakarta.persistence:jakarta.persistence-api:3.2.0";
    @Override
    public String getDisplayName() {
        return "Migrate explicit Java Persistence 2.2 API dependency to Jakarta Persistence 3.2";
    }

    @Override
    public String getDescription() {
        return "Changes only an explicit literal javax.persistence:javax.persistence-api:2.2 standard dependency; " +
               "versionless, property-managed, classified, plugin, and other-version declarations remain untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedHibernateCoreDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag visited = super.visitTag(tag, executionContext);
                            if (!isEligible(getCursor(), visited)) return visited;
                            return visited.withChildValue("groupId", "jakarta.persistence")
                                    .withChildValue("artifactId", "jakarta.persistence-api")
                                    .withChildValue("version", "3.2.0");
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean direct = UpgradeSelectedHibernateCoreDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                            if (!direct) return visited;
                            if (oldCoordinate(mapValue(visited, "group"), mapValue(visited, "name"),
                                    mapValue(visited, "version")) && !hasVariant(visited)) {
                                return visited.withArguments(visited.getArguments().stream()
                                        .map(MigrateExplicitPersistenceApiDependency::migrateArgument).toList());
                            }
                            return visited.withArguments(visited.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? migrateMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = direct(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? replace(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = direct(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? replace(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean isEligible(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName()) ||
            !"javax.persistence".equals(tag.getChildValue("groupId").orElse(null)) ||
            !"javax.persistence-api".equals(tag.getChildValue("artifactId").orElse(null)) ||
            !"2.2".equals(tag.getChildValue("version").orElse(null)) ||
            tag.getChild("classifier").isPresent() || !"jar".equals(tag.getChildValue("type").orElse("jar"))) {
            return false;
        }
        return UpgradeSelectedHibernateCoreDependency.isProjectDependency(cursor, tag);
    }

    private static boolean direct(Cursor cursor) {
        return UpgradeSelectedHibernateCoreDependency.isDirectGradleDependencyLiteral(cursor);
    }

    private static J.Literal replace(J.Literal literal) {
        if (!OLD.equals(literal.getValue())) return literal;
        String source = literal.getValueSource();
        return literal.withValue(TARGET).withValueSource(source == null ? null : source.replace(OLD, TARGET));
    }

    private static boolean oldCoordinate(String group, String artifact, String version) {
        return "javax.persistence".equals(group) && "javax.persistence-api".equals(artifact) &&
               "2.2".equals(version);
    }

    private static Expression migrateArgument(Expression argument) {
        if (!(argument instanceof G.MapEntry entry)) return argument;
        String key = mapKey(entry);
        if (!(entry.getValue() instanceof J.Literal literal)) return argument;
        if ("group".equals(key)) return entry.withValue(replaceLiteral(literal, "javax.persistence", "jakarta.persistence"));
        if ("name".equals(key)) return entry.withValue(replaceLiteral(literal, "javax.persistence-api", "jakarta.persistence-api"));
        if ("version".equals(key)) return entry.withValue(replaceLiteral(literal, "2.2", "3.2.0"));
        return argument;
    }

    private static G.MapLiteral migrateMap(G.MapLiteral map) {
        if (!oldCoordinate(mapValue(map, "group"), mapValue(map, "name"), mapValue(map, "version")) ||
            hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry -> (G.MapEntry) migrateArgument(entry)).toList());
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> java.util.Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> java.util.Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        if (!oldValue.equals(literal.getValue())) return literal;
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }
}
