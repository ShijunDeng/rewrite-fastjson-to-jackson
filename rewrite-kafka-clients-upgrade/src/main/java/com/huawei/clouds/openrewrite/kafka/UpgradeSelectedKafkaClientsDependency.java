package com.huawei.clouds.openrewrite.kafka;

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

/** Upgrade only kafka-clients versions whose exact values remain visible in the spreadsheet. */
public final class UpgradeSelectedKafkaClientsDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "2.4.1", "2.5.1", "3.1.2", "3.4.0", "3.4.1",
            "3.5.1", "3.6.0", "3.6.1", "3.6.2", "3.7.0"
    );
    private static final String TARGET_VERSION = "4.1.2";
    private static final String COORDINATE_PREFIX = "org.apache.kafka:kafka-clients:";
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected kafka-clients dependencies to 4.1.2";
    }

    @Override
    public String getDescription() {
        return "Update direct Maven and Gradle org.apache.kafka:kafka-clients declarations only when their " +
               "resolved literal version is one of the exact spreadsheet source versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return upgradePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!GRADLE_CONFIGURATIONS.contains(m.getSimpleName())) {
                                return m;
                            }
                            String group = invocationMapValue(m, "group");
                            String name = invocationMapValue(m, "name");
                            String version = invocationMapValue(m, "version");
                            if ("org.apache.kafka".equals(group) && "kafka-clients".equals(name) &&
                                isSourceVersion(version)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value))));

        Set<String> eligibleProperties = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isKafkaClientsDependency(t)) {
                    propertyName(t.getChildValue("version").orElse(null)).filter(name ->
                            isSourceVersion(properties.get(name))).ifPresent(eligibleProperties::add);
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Map<String, Boolean> exclusiveProperties = new HashMap<>();
        String source = document.printAll();
        eligibleProperties.forEach(name -> {
            String token = "${" + name + "}";
            int references = 0;
            for (int offset = source.indexOf(token); offset >= 0; offset = source.indexOf(token, offset + token.length())) {
                references++;
            }
            exclusiveProperties.put(name, references == 1);
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isKafkaClientsDependency(t)) {
                    String version = t.getChildValue("version").orElse(null);
                    if (isSourceVersion(version)) {
                        return t.withChildValue("version", TARGET_VERSION);
                    }
                    String property = propertyName(version).orElse(null);
                    if (property != null && eligibleProperties.contains(property) &&
                        !exclusiveProperties.getOrDefault(property, false)) {
                        return t.withChildValue("version", TARGET_VERSION);
                    }
                }
                if (eligibleProperties.contains(t.getName()) &&
                    exclusiveProperties.getOrDefault(t.getName(), false) &&
                    isSourceVersion(t.getValue().orElse(null))) {
                    return t.withValue(TARGET_VERSION);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isKafkaClientsDependency(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               "org.apache.kafka".equals(tag.getChildValue("groupId").orElse(null)) &&
               "kafka-clients".equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static java.util.Optional<String> propertyName(String version) {
        if (version != null && version.startsWith("${") && version.endsWith("}") && version.length() > 3) {
            return java.util.Optional.of(version.substring(2, version.length() - 1));
        }
        return java.util.Optional.empty();
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
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
        if (!"org.apache.kafka".equals(mapValue(map, "group")) ||
            !"kafka-clients".equals(mapValue(map, "name")) ||
            !isSourceVersion(mapValue(map, "version"))) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        if (entry.getKey() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(COORDINATE_PREFIX)) {
            return literal;
        }
        String version = value.substring(COORDINATE_PREFIX.length());
        if (!isSourceVersion(version)) {
            return literal;
        }
        String replacement = COORDINATE_PREFIX + TARGET_VERSION;
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !isSourceVersion(value)) {
            return literal;
        }
        String valueSource = literal.getValueSource();
        return literal.withValue(TARGET_VERSION).withValueSource(
                valueSource == null ? null : valueSource.replace(value, TARGET_VERSION));
    }

    private static boolean isSourceVersion(String version) {
        return version != null && SOURCE_VERSIONS.contains(version);
    }
}
