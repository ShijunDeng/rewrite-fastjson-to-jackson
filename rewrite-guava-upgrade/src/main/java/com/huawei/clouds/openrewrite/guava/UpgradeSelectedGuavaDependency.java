package com.huawei.clouds.openrewrite.guava;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only Guava versions explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedGuavaDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "21", "21.0", "29.0-jre", "30.1-jre", "30.1.1-jre", "31.1-jre",
            "32.0.0-jre", "32.0.1-jre", "32.1.0-jre", "32.1.1-android", "32.1.1-jre"
    );
    private static final String TARGET = "33.5.0-jre";
    private static final String GROUP = "com.google.guava";
    private static final String ARTIFACT = "guava";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
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
        return "Upgrade spreadsheet-selected Guava declarations to 33.5.0-jre";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct com.google.guava:guava declarations whose literal or safely isolated Maven-property version exactly matches a spreadsheet source value.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!isGradleDependencyInvocation(getCursor(), m)) {
                                return m;
                            }
                            String group = invocationMapValue(m, "group");
                            String name = invocationMapValue(m, "name");
                            String version = invocationMapValue(m, "version");
                            if (GROUP.equals(group) && ARTIFACT.equals(name) && SOURCE_VERSIONS.contains(version) &&
                                !hasVariantKey(m)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry ? upgradeVersionEntry(entry) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
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

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        Map<String, Integer> propertyDefinitions = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isMavenPropertyDefinition(getCursor(), tag)) {
                    propertyDefinitions.merge(tag.getName(), 1, Integer::sum);
                    tag.getValue().ifPresent(value -> propertyValues.put(tag.getName(), value.trim()));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

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
                if (isSupportedGuavaDependency(getCursor(), tag)) {
                    propertyName(tag).filter(name -> SOURCE_VERSIONS.contains(propertyValues.get(name)))
                            .ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && Integer.valueOf(1).equals(propertyDefinitions.get(name))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isMavenPropertyDefinition(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isSupportedGuavaDependency(getCursor(), t) && t.getChildValue("version")
                        .map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    static boolean isGuava(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static java.util.Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag parentTag) || !"properties".equals(parentTag.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor ownerCursor = parent.getParentTreeCursor();
        return ownerCursor.getValue() instanceof Xml.Tag owner &&
               ("project".equals(owner.getName()) || "profile".equals(owner.getName()));
    }

    static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor &&
                "dependencies".equals(ancestor.getSimpleName())) return true;
        }
        return false;
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(mapValue(map, "version")) || hasVariantKey(map)) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(UpgradeSelectedGuavaDependency::upgradeVersionEntry).toList());
    }

    private static boolean hasVariantKey(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariantKey(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static G.MapEntry upgradeVersionEntry(G.MapEntry entry) {
        if (!"version".equals(mapKey(entry)) || !(entry.getValue() instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) || !SOURCE_VERSIONS.contains(version)) {
            return entry;
        }
        return entry.withValue(replaceLiteral(literal, version, TARGET));
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) {
            return literal;
        }
        return replaceLiteral(literal, value, PREFIX + TARGET);
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }

    static boolean isSupportedGuavaDependency(Cursor cursor, Xml.Tag tag) {
        if (!isGuava(tag) || tag.getChild("classifier").isPresent() ||
            !"jar".equals(tag.getChildValue("type").orElse("jar"))) return false;
        Cursor containerCursor = cursor.getParentTreeCursor();
        if (!(containerCursor.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor ownerCursor = containerCursor.getParentTreeCursor();
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner)) return false;
        if ("project".equals(owner.getName()) || "profile".equals(owner.getName())) return true;
        if (!"dependencyManagement".equals(owner.getName())) return false;
        Cursor managedOwner = ownerCursor.getParentTreeCursor();
        return managedOwner.getValue() instanceof Xml.Tag actualOwner &&
               ("project".equals(actualOwner.getName()) || "profile".equals(actualOwner.getName()));
    }
}
