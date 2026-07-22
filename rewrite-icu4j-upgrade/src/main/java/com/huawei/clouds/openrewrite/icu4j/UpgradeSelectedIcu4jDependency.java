package com.huawei.clouds.openrewrite.icu4j;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only ICU4J versions that are visible in the migration workbook. */
public final class UpgradeSelectedIcu4jDependency extends Recipe {
    static final String GROUP = "com.ibm.icu";
    static final String ARTIFACT = "icu4j";
    static final Set<String> SOURCE_VERSIONS = Set.of("67.1", "73.1", "73.2");
    static final String TARGET = "77.1";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2",
            ".yarn", ".idea", "node_modules", "vendor"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected ICU4J dependencies to 77.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct, standard com.ibm.icu:icu4j dependencies whose literal version is 67.1, " +
               "73.1, or 73.2, including safely owned Maven properties; leave BOMs, catalogs, variants, " +
               "dynamic versions, generated files, and external owners unchanged.";
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
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            if (!direct) return visited;
                            if (GROUP.equals(invocationMapValue(visited, "group")) &&
                                ARTIFACT.equals(invocationMapValue(visited, "name")) &&
                                isSourceVersion(invocationMapValue(visited, "version")) && !hasVariantKey(visited)) {
                                return visited.withArguments(visited.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                            }
                            return visited.withArguments(visited.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> icuReferences = new HashMap<>();

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, ec);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, ec);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), visited)) {
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(visited.getName(), value.trim()));
                }
                if (isStandardOwnedIcuDependency(getCursor(), visited)) {
                    propertyName(visited).ifPresent(name -> icuReferences.merge(name, 1, Integer::sum));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        icuReferences.forEach((name, count) -> {
            if (count > 0 && SOURCE_VERSIONS.contains(values.get(name)) && definitions.getOrDefault(name, 0) == 1 &&
                allReferences.getOrDefault(name, 0).equals(count)) safeProperties.add(name);
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), visited) && safeProperties.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isStandardOwnedIcuDependency(getCursor(), visited) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isStandardDirectIcuDependency(Cursor cursor, Xml.Tag tag) {
        return isDirectProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null)) && tag.getChild("classifier").isEmpty() &&
               "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean isStandardOwnedIcuDependency(Cursor cursor, Xml.Tag tag) {
        return isAnyOwnedDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null)) && tag.getChild("classifier").isEmpty() &&
               "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean isDirectProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependenciesCursor = cursor.getParentTreeCursor();
        if (!(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) return false;
        Cursor ownerCursor = dependenciesCursor.getParentTreeCursor();
        return isProjectOwner(ownerCursor) || isProfileOwner(ownerCursor);
    }

    static boolean isAnyOwnedDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependenciesCursor = cursor.getParentTreeCursor();
        if (!(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) return false;
        Cursor owner = dependenciesCursor.getParentTreeCursor();
        if (isProjectOwner(owner) || isProfileOwner(owner)) return true;
        if (!(owner.getValue() instanceof Xml.Tag ownerTag) || !"dependencyManagement".equals(ownerTag.getName())) {
            return false;
        }
        return isProjectOwner(owner.getParentTreeCursor()) || isProfileOwner(owner.getParentTreeCursor());
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag parentTag) || !"properties".equals(parentTag.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor owner = parent.getParentTreeCursor();
        return isProjectOwner(owner) || isProfileOwner(owner);
    }

    static boolean isProjectOwner(Cursor cursor) {
        return cursor.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName()) &&
               cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean isProfileOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               isProjectOwner(profiles.getParentTreeCursor());
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        boolean dependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!dependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName())) return false;
                    dependencies = true;
                } else return false;
            }
        }
        return dependencies;
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
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !isSourceVersion(mapValue(map, "version")) || hasVariantKey(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) return literal;
        return replaceLiteral(literal, value, PREFIX + TARGET);
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        return literal.getValue() instanceof String version && SOURCE_VERSIONS.contains(version)
                ? replaceLiteral(literal, version, TARGET) : literal;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean hasVariantKey(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type", "variant").contains(mapKey(entry)));
    }

    static boolean hasVariantKey(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type", "variant").contains(mapKey(entry)));
    }

    private static boolean isSourceVersion(String version) {
        return version != null && SOURCE_VERSIONS.contains(version);
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    static boolean generated(Path path) {
        for (Path part : path.normalize()) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) || value.startsWith("generated") || value.startsWith("install")) {
                return true;
            }
        }
        return false;
    }
}
