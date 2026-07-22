package com.huawei.clouds.openrewrite.springdataelasticsearch;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.gradle.marker.GradleProject;
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

/** Updates only dependency declarations whose complete source version is visible in the spreadsheet. */
public final class UpgradeSelectedSpringDataElasticsearchDependency extends Recipe {
    static final String GROUP = "org.springframework.data";
    static final String ARTIFACT = "spring-data-elasticsearch";
    static final String TARGET = "6.0.5";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "4.2.4", "4.2.8", "4.2.12", "4.4.8", "4.4.12", "4.4.14"
    );
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
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
        return "Upgrade spreadsheet-selected Spring Data Elasticsearch declarations to 6.0.5";
    }

    @Override
    public String getDescription() {
        return "Updates only direct Maven/Gradle org.springframework.data:spring-data-elasticsearch declarations " +
               "whose complete literal or safely isolated Maven-property value is one of the six visible spreadsheet versions.";
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
                    if (compilationUnit.getMarkers().findFirst(GradleProject.class).isEmpty()) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
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
            J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            if (!isGradleDependencyInvocation(getCursor(), visited)) return visited;
            if (GROUP.equals(invocationMapValue(visited, "group")) &&
                ARTIFACT.equals(invocationMapValue(visited, "name")) &&
                SOURCE_VERSIONS.contains(invocationMapValue(visited, "version")) &&
                !hasGradleVariant(visited)) {
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                        entry.getValue() instanceof J.Literal literal
                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
            }
            return visited.withArguments(visited.getArguments().stream().map(argument ->
                    argument instanceof G.MapLiteral map && !hasGradleVariant(map) ? upgradeMap(map) : argument)
                    .toList());
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
            boolean direct = isDirectDependencyLiteral(getCursor());
            J.Literal visited = super.visitLiteral(literal, ctx);
            return direct ? upgradeCoordinate(visited) : visited;
        }
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> rootDefinitions = new HashMap<>();
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> {
                            rootDefinitions.merge(property.getName(), 1, Integer::sum);
                            property.getValue().ifPresent(value -> propertyValues.put(property.getName(), value.trim()));
                        }));
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleReferences = new HashMap<>();
        Set<String> shadowedProperties = new HashSet<>();

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
                if (isPropertiesChild(getCursor(), tag) && !isProjectPropertiesChild(getCursor(), tag)) {
                    shadowedProperties.add(tag.getName());
                }
                if (isMavenTargetDependency(getCursor(), tag)) {
                    propertyName(tag).filter(name -> SOURCE_VERSIONS.contains(propertyValues.get(name)))
                            .ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (count > 0 && count.equals(allReferences.get(name)) && rootDefinitions.getOrDefault(name, 0) == 1 &&
                !shadowedProperties.contains(name)) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isProjectPropertiesChild(getCursor(), visited) && safeProperties.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isMavenTargetDependency(getCursor(), visited) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean hasTargetCoordinates(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardJar(Xml.Tag tag) {
        boolean standardType = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        return standardType && noClassifier;
    }

    static boolean isMavenDependencyBlock(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag dependencies) || !"dependencies".equals(dependencies.getName())) {
            return false;
        }
        Cursor owner = parent.getParentTreeCursor();
        if (isProjectOrProfileOwner(owner)) return true;
        if (owner == null || !(owner.getValue() instanceof Xml.Tag management) ||
            !"dependencyManagement".equals(management.getName())) return false;
        return isProjectOrProfileOwner(owner.getParentTreeCursor());
    }

    static boolean isMavenTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isMavenDependencyBlock(cursor, tag) && hasTargetCoordinates(tag) && isStandardJar(tag);
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName());
    }

    private static boolean isProjectPropertiesChild(Cursor cursor, Xml.Tag tag) {
        if (!isPropertiesChild(cursor, tag)) return false;
        Cursor properties = cursor.getParentTreeCursor();
        return isProjectOwner(properties.getParentTreeCursor());
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
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
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(mapValue(map, "version"))) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    static boolean hasGradleVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .map(UpgradeSelectedSpringDataElasticsearchDependency::mapKey)
                .anyMatch(key -> "classifier".equals(key) || "ext".equals(key) || "type".equals(key)) ||
               invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                       .anyMatch(UpgradeSelectedSpringDataElasticsearchDependency::hasGradleVariant);
    }

    private static boolean hasGradleVariant(G.MapLiteral map) {
        return map.getElements().stream().map(UpgradeSelectedSpringDataElasticsearchDependency::mapKey)
                .anyMatch(key -> "classifier".equals(key) || "ext".equals(key) || "type".equals(key));
    }

    private static String mapKey(G.MapEntry entry) {
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

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    private static boolean isProjectOrProfileOwner(Cursor owner) {
        if (isProjectOwner(owner)) return true;
        if (owner == null || !(owner.getValue() instanceof Xml.Tag profile) ||
            !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        return profiles != null && profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) && isProjectOwner(profiles.getParentTreeCursor());
    }

    private static boolean isProjectOwner(Cursor owner) {
        if (owner == null || !(owner.getValue() instanceof Xml.Tag project) ||
            !"project".equals(project.getName())) return false;
        Cursor document = owner.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }

    static boolean generated(Path path) {
        for (Path part : path.normalize()) if (GENERATED_DIRECTORIES.contains(part.toString())) return true;
        return false;
    }
}
