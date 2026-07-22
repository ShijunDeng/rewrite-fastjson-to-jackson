package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Upgrades only spreadsheet-selected Flyway Core declarations. */
public final class UpgradeSelectedFlywayCoreDependency extends Recipe {
    private static final String PREFIX = "org.flywaydb:flyway-core:";

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Flyway Core dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrade only explicit org.flywaydb:flyway-core versions named by the spreadsheet, preserving managed, dynamic, range, and shared-property declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return upgradePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    boolean exclusiveFlywayVersion = isExclusiveFlywayVersionVariable(cu.printAll());
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext p) {
                            J.Assignment a = super.visitAssignment(assignment, p);
                            if (exclusiveFlywayVersion && "flywayVersion".equals(a.getVariable().printTrimmed()) &&
                                a.getAssignment() instanceof J.Literal literal && literal.getValue() instanceof String version &&
                                FlywayVersions.isSource(version)) {
                                return a.withAssignment(upgradeVersionLiteral(literal));
                            }
                            return a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, p);
                            if (!FlywayVersions.GRADLE_CONFIGURATIONS.contains(m.getSimpleName())) {
                                return m;
                            }
                            if ("org.flywaydb".equals(mapValue(m, "group")) &&
                                "flyway-core".equals(mapValue(m, "name")) &&
                                FlywayVersions.isSource(mapValue(m, "version"))) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                            }
                            return m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        Set<String> eligible = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (isCore(t)) {
                    propertyName(t.getChildValue("version").orElse(null))
                            .filter(name -> FlywayVersions.isSource(properties.get(name))).ifPresent(eligible::add);
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        String source = document.printAll();
        Map<String, Boolean> exclusive = new HashMap<>();
        eligible.forEach(name -> exclusive.put(name, count(source, "${" + name + "}") == 1));
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (isCore(t)) {
                    String version = t.getChildValue("version").orElse(null);
                    if (FlywayVersions.isSource(version)) {
                        return t.withChildValue("version", FlywayVersions.TARGET);
                    }
                    String property = propertyName(version).orElse(null);
                    if (property != null && eligible.contains(property) && !exclusive.getOrDefault(property, false)) {
                        return t.withChildValue("version", FlywayVersions.TARGET);
                    }
                }
                if (eligible.contains(t.getName()) && exclusive.getOrDefault(t.getName(), false) &&
                    FlywayVersions.isSource(t.getValue().orElse(null))) {
                    return t.withValue(FlywayVersions.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isCore(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               "org.flywaydb".equals(tag.getChildValue("groupId").orElse(null)) &&
               "flyway-core".equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static Optional<String> propertyName(String version) {
        return version != null && version.startsWith("${") && version.endsWith("}")
                ? Optional.of(version.substring(2, version.length() - 1)) : Optional.empty();
    }

    private static int count(String source, String token) {
        int result = 0;
        for (int offset = source.indexOf(token); offset >= 0; offset = source.indexOf(token, offset + token.length())) {
            result++;
        }
        return result;
    }

    private static boolean isExclusiveFlywayVersionVariable(String source) {
        boolean coreReference = source.contains("org.flywaydb:flyway-core:$flywayVersion") ||
                                source.contains("org.flywaydb:flyway-core:${flywayVersion}");
        return coreReference && count(source, "flywayVersion") == 2;
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               FlywayVersions.GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !FlywayVersions.isSource(value.substring(PREFIX.length()))) {
            return literal;
        }
        String replacement = PREFIX + FlywayVersions.TARGET;
        return literal.withValue(replacement).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(value, replacement));
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !FlywayVersions.isSource(value)) {
            return literal;
        }
        return literal.withValue(FlywayVersions.TARGET).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(value, FlywayVersions.TARGET));
    }
}
