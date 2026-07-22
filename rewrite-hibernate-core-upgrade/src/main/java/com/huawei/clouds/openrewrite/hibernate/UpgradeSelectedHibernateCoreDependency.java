package com.huawei.clouds.openrewrite.hibernate;

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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrades only complete Hibernate Core declarations whose source version is visible in the workbook. */
public final class UpgradeSelectedHibernateCoreDependency extends Recipe {
    static final String LEGACY_GROUP = "org.hibernate";
    static final String TARGET_GROUP = "org.hibernate.orm";
    static final String ARTIFACT = "hibernate-core";
    static final String TARGET = "7.2.12.Final";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "5.4.15.Final", "5.4.24.Final", "5.4.25.Final", "5.4.28.Final", "5.5.6",
            "5.6.5.Final", "5.6.7.Final", "5.6.9.Final", "5.6.14.Final", "5.6.15.Final"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", ".gradle", ".mvn", ".idea", "node_modules"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Hibernate Core declarations to 7.2.12.Final";
    }

    @Override
    public String getDescription() {
        return "Moves only standard direct Maven/Gradle Hibernate Core declarations whose complete literal or " +
               "safely isolated Maven-property value is one of the ten workbook-visible source versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GradleVisitor().visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static final class GradleVisitor extends GroovyIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            boolean direct = isGradleDependencyInvocation(getCursor(), method);
            J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            if (!direct) return visited;
            if (selectedGroup(invocationMapValue(visited, "group")) &&
                ARTIFACT.equals(invocationMapValue(visited, "name")) &&
                SOURCE_VERSIONS.contains(invocationMapValue(visited, "version")) && !hasVariantKey(visited)) {
                return visited.withArguments(visited.getArguments().stream().map(argument -> {
                    if (!(argument instanceof G.MapEntry entry)) return argument;
                    String key = mapKey(entry);
                    if ("group".equals(key) && entry.getValue() instanceof J.Literal literal) {
                        return entry.withValue(replaceLiteral(literal, literal.getValue(), TARGET_GROUP));
                    }
                    if ("version".equals(key) && entry.getValue() instanceof J.Literal literal) {
                        return entry.withValue(replaceLiteral(literal, literal.getValue(), TARGET));
                    }
                    return argument;
                }).toList());
            }
            return visited.withArguments(visited.getArguments().stream().map(argument ->
                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
            boolean direct = isDirectGradleDependencyLiteral(getCursor());
            J.Literal visited = super.visitLiteral(literal, ctx);
            return direct ? upgradeCoordinate(visited) : visited;
        }
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isMavenPropertyDefinition(getCursor(), tag)) {
                    definitions.merge(tag.getName(), 1, Integer::sum);
                    tag.getValue().ifPresent(value -> propertyValues.put(tag.getName(), value.trim()));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visitNonNull(document, ctx);

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isSupportedHibernateCore(getCursor(), visited)) {
                    propertyName(visited).filter(name -> propertyValues.containsKey(name) &&
                                                          SOURCE_VERSIONS.contains(propertyValues.get(name)))
                            .ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (count > 0 && count.equals(allReferences.get(name)) && definitions.getOrDefault(name, 0) == 1) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isMavenPropertyDefinition(getCursor(), visited) && safeProperties.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (!isSupportedHibernateCore(getCursor(), visited)) return visited;
                String version = visited.getChildValue("version").orElse("");
                boolean literal = SOURCE_VERSIONS.contains(version);
                boolean isolatedProperty = propertyName(visited).filter(safeProperties::contains).isPresent();
                if (!literal && !isolatedProperty) return visited;
                Xml.Tag migrated = visited.withChildValue("groupId", TARGET_GROUP);
                return literal ? migrated.withChildValue("version", TARGET) : migrated;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isSupportedHibernateCore(Cursor cursor, Xml.Tag tag) {
        if (!isHibernateCoreCoordinate(tag) || tag.getChild("classifier").isPresent() ||
            !"jar".equals(tag.getChildValue("type").orElse("jar"))) return false;
        return isProjectDependency(cursor, tag);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor containerCursor = cursor.getParentTreeCursor();
        if (!(containerCursor.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor ownerCursor = containerCursor.getParentTreeCursor();
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner)) return false;
        if (isProjectOwner(ownerCursor) || isProfileOwner(ownerCursor)) return true;
        if (!"dependencyManagement".equals(owner.getName())) return false;
        Cursor managedOwner = ownerCursor.getParentTreeCursor();
        return managedOwner != null && (isProjectOwner(managedOwner) || isProfileOwner(managedOwner));
    }

    static boolean isHibernateCoreCoordinate(Xml.Tag tag) {
        String group = tag.getChildValue("groupId").orElse(null);
        return "dependency".equals(tag.getName()) && selectedGroup(group) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean selectedGroup(String group) {
        return LEGACY_GROUP.equals(group) || TARGET_GROUP.equals(group);
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag parentTag) ||
            !"properties".equals(parentTag.getName())) {
            return false;
        }
        Cursor ownerCursor = parent.getParentTreeCursor();
        return ownerCursor != null &&
               (isProjectOwner(ownerCursor) || isProfileOwner(ownerCursor)) &&
               !"properties".equals(tag.getName());
    }

    static boolean isProjectOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag project) || !"project".equals(project.getName())) return false;
        Cursor document = cursor.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }

    static boolean isProfileOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        if (profiles == null || !(profiles.getValue() instanceof Xml.Tag profilesTag) ||
            !"profiles".equals(profilesTag.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project != null && isProjectOwner(project);
    }

    static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                return "dependencies".equals(ancestor.getSimpleName());
            }
        }
        return false;
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
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

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!selectedGroup(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(mapValue(map, "version")) || hasVariantKey(map)) return map;
        return map.withElements(map.getElements().stream().map(entry -> {
            String key = mapKey(entry);
            if ("group".equals(key) && entry.getValue() instanceof J.Literal literal) {
                return entry.withValue(replaceLiteral(literal, literal.getValue(), TARGET_GROUP));
            }
            if ("version".equals(key) && entry.getValue() instanceof J.Literal literal) {
                return entry.withValue(replaceLiteral(literal, literal.getValue(), TARGET));
            }
            return entry;
        }).toList());
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariantKey(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariantKey(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String legacyPrefix = LEGACY_GROUP + ":" + ARTIFACT + ":";
        String targetPrefix = TARGET_GROUP + ":" + ARTIFACT + ":";
        String prefix = value.startsWith(legacyPrefix) ? legacyPrefix : value.startsWith(targetPrefix) ? targetPrefix : null;
        if (prefix == null || !SOURCE_VERSIONS.contains(value.substring(prefix.length()))) return literal;
        return replaceLiteral(literal, value, targetPrefix + TARGET);
    }

    private static J.Literal replaceLiteral(J.Literal literal, Object oldValue, String newValue) {
        if (!(oldValue instanceof String oldString)) return literal;
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(
                source == null ? null : source.replace(oldString, newValue));
    }

    static boolean generated(Path path) {
        for (Path part : path.normalize()) if (GENERATED_DIRECTORIES.contains(part.toString())) return true;
        return false;
    }
}
