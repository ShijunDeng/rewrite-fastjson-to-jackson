package com.huawei.clouds.openrewrite.jakartaservlet;

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

/** Upgrade only Jakarta Servlet API versions explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedJakartaServletApiDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of("4.0.3", "4.0.4", "5.0.0", "6.0.0");
    static final String TARGET_VERSION = "6.1.0";
    static final String GROUP = "jakarta.servlet";
    static final String ARTIFACT = "jakarta.servlet-api";

    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "providedCompile",
            "runtime", "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "testFixturesApi",
            "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp");

    @Override
    public String getDisplayName() {
        return "Strictly upgrade selected Jakarta Servlet API declarations to 6.1.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct jakarta.servlet:jakarta.servlet-api declarations whose literal or exclusively " +
               "owned local Maven-property version exactly matches one of the four spreadsheet source versions.";
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
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return migrateGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return migrateKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Set<String>> propertyValues = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, Integer> allUses = new HashMap<>();
        Map<String, Integer> eligibleUses = new HashMap<>();

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                countReferences(charData.getText(), allUses);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                countReferences(attribute.getValueAsString(), allUses);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isPropertiesChild(getCursor(), t)) {
                    definitions.merge(t.getName(), 1, Integer::sum);
                    t.getValue().map(String::trim).ifPresent(value ->
                            propertyValues.computeIfAbsent(t.getName(), ignored -> new HashSet<>()).add(value));
                }
                if (isTarget(t)) {
                    propertyName(t).filter(name -> isSelectedProperty(name, propertyValues))
                            .ifPresent(name -> eligibleUses.merge(name, 1, Integer::sum));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Set<String> exclusiveProperties = new HashSet<>();
        eligibleUses.forEach((name, count) -> {
            if (definitions.getOrDefault(name, 0) == 1 && count.equals(allUses.get(name))) {
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
                if (!isTarget(t)) {
                    return t;
                }
                String rawVersion = t.getChildValue("version").map(String::trim).orElse("");
                if (SOURCE_VERSIONS.contains(rawVersion)) {
                    return t.withChildValue("version", TARGET_VERSION);
                }
                Optional<String> property = propertyName(t);
                if (property.isPresent() && isSelectedProperty(property.get(), propertyValues) &&
                    !exclusiveProperties.contains(property.get())) {
                    return t.withChildValue("version", TARGET_VERSION);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
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
                                    ? entry.withValue(replaceLiteral(literal, version, TARGET_VERSION)) : argument).toList());
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
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean isSelectedProperty(String name, Map<String, Set<String>> propertyValues) {
        Set<String> values = propertyValues.get(name);
        return values != null && values.size() == 1 && SOURCE_VERSIONS.contains(values.iterator().next());
    }

    private static void countReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    static boolean isTarget(Xml.Tag tag) {
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
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
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
