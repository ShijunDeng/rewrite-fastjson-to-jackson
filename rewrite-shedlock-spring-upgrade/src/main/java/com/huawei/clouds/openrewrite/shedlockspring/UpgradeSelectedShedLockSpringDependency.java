package com.huawei.clouds.openrewrite.shedlockspring;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only ShedLock Spring versions explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedShedLockSpringDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of("2.2.0", "4.29.0", "4.33.0", "4.41.0", "4.44.0");
    static final String TARGET_VERSION = "7.2.1";
    private static final String GROUP = "net.javacrumbs.shedlock";
    private static final String ARTIFACT = "shedlock-spring";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");

    @Override
    public String getDisplayName() {
        return "Strictly upgrade selected ShedLock Spring declarations to 7.2.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct net.javacrumbs.shedlock:shedlock-spring declarations whose literal or local " +
               "Maven-property version exactly matches one of the five versions visible in the spreadsheet.";
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
                    return migratePom(document, ctx);
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
                            if (GROUP.equals(group) && ARTIFACT.equals(name) && SOURCE_VERSIONS.contains(version)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(replaceLiteral(literal, version, TARGET_VERSION))
                                                : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return dependency ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return dependency ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Xml.Tag root = document.getRoot();
        Map<String, String> properties = new HashMap<>();
        root.getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .forEach(property -> property.getValue().ifPresent(value ->
                        properties.put(property.getName(), value.trim()))));

        Map<String, Integer> allUses = new HashMap<>();
        Map<String, Integer> eligibleUses = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Matcher matcher = PROPERTY_REFERENCE.matcher(charData.getText().trim());
                while (matcher.find()) {
                    allUses.merge(matcher.group(1), 1, Integer::sum);
                }
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Matcher matcher = PROPERTY_REFERENCE.matcher(attribute.getValueAsString());
                while (matcher.find()) {
                    allUses.merge(matcher.group(1), 1, Integer::sum);
                }
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isShedLockSpring(tag)) {
                    propertyName(tag).filter(name -> SOURCE_VERSIONS.contains(properties.get(name)))
                            .ifPresent(name -> eligibleUses.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> exclusiveProperties = new HashSet<>();
        eligibleUses.forEach((name, count) -> {
            if (count.equals(allUses.get(name))) {
                exclusiveProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isPropertiesChild(getCursor(), t) && exclusiveProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET_VERSION);
                }
                if (!isShedLockSpring(t)) {
                    return t;
                }
                String rawVersion = t.getChildValue("version").map(String::trim).orElse("");
                if (SOURCE_VERSIONS.contains(rawVersion)) {
                    return t.withChildValue("version", TARGET_VERSION);
                }
                Optional<String> property = propertyName(t);
                if (property.isPresent() && SOURCE_VERSIONS.contains(properties.get(property.get())) &&
                    !exclusiveProperties.contains(property.get())) {
                    return t.withChildValue("version", TARGET_VERSION);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isShedLockSpring(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse("").trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName());
    }

    static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String value) {
            return value;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        String version = mapValue(map, "version");
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(version)) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(replaceLiteral(literal, version, TARGET_VERSION)) : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String coordinate)) {
            return literal;
        }
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 3 || !GROUP.equals(parts[0]) || !ARTIFACT.equals(parts[1]) ||
            !SOURCE_VERSIONS.contains(parts[2])) {
            return literal;
        }
        parts[2] = TARGET_VERSION;
        return replaceLiteral(literal, coordinate, String.join(":", parts));
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }
}
