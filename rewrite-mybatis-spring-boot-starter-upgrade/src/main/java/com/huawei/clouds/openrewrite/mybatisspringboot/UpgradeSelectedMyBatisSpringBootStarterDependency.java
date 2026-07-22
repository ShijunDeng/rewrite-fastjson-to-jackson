package com.huawei.clouds.openrewrite.mybatisspringboot;

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

/** Upgrade only MyBatis Spring Boot Starter versions explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedMyBatisSpringBootStarterDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "1.1.1", "1.3.2", "2.0.0", "2.1.2", "2.1.3", "2.1.4",
            "2.2.0", "2.2.2", "2.3.0", "2.3.1"
    );
    static final String TARGET = "4.0.0";
    static final String GROUP = "org.mybatis.spring.boot";
    static final String CORE = "mybatis-spring-boot-starter";
    static final Set<String> FAMILY_ARTIFACTS = Set.of(
            CORE, "mybatis-spring-boot-autoconfigure",
            "mybatis-spring-boot-test-autoconfigure", "mybatis-spring-boot-starter-test"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", ".gradle", ".idea", "node_modules"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected MyBatis Spring Boot Starter declarations to 4.0.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade the starter and explicitly versioned family modules only when the starter's literal or safely isolated Maven-property version exactly matches a source value visible in the spreadsheet.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle") &&
                    hasSelectedGroovyCore(compilationUnit, ctx)) {
                    return migrateGroovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts") &&
                    hasSelectedKotlinCore(compilationUnit, ctx)) {
                    return migrateKotlin(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> property.getValue()
                                .ifPresent(value -> propertyValues.put(property.getName(), value.trim()))));

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> familyReferences = new HashMap<>();
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
                if (isMavenFamilyDependency(getCursor(), tag)) {
                    propertyName(tag).ifPresent(name -> familyReferences.merge(name, 1, Integer::sum));
                }
                if (isPropertiesChild(getCursor(), tag) && !isProjectPropertiesChild(getCursor(), tag)) {
                    shadowedProperties.add(tag.getName());
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        familyReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && !shadowedProperties.contains(name)) {
                safeProperties.add(name);
            }
        });

        boolean selectedCore = hasSelectedMavenCore(document, propertyValues, safeProperties, ctx);
        if (!selectedCore) {
            return document;
        }

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isProjectPropertiesChild(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isMavenFamilyDependency(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean hasSelectedMavenCore(Xml.Document document, Map<String, String> propertyValues,
                                                 Set<String> safeProperties, ExecutionContext ctx) {
        boolean[] selected = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isMavenCoreDependency(getCursor(), tag)) {
                    String version = tag.getChildValue("version").map(String::trim).orElse("");
                    if (SOURCE_VERSIONS.contains(version)) {
                        selected[0] = true;
                    } else {
                        propertyName(tag).filter(safeProperties::contains)
                                .map(propertyValues::get).filter(SOURCE_VERSIONS::contains)
                                .ifPresent(ignored -> selected[0] = true);
                    }
                }
                return selected[0] ? tag : super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return selected[0];
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (!isGradleDependencyInvocation(getCursor(), m)) {
                    return m;
                }
                if (isFamilyMap(m)) {
                    return m.withArguments(m.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry ? upgradeVersionEntry(entry) : argument).toList());
                }
                return m.withArguments(m.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map && isFamilyMap(map) ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static boolean hasSelectedGroovyCore(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] selected = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (isGradleDependencyInvocation(getCursor(), method) && isSelectedCoreMap(method)) {
                    selected[0] = true;
                }
                return selected[0] ? method : super.visitMethodInvocation(method, executionContext);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (isDirectGradleDependencyLiteral(getCursor()) && isSelectedCoreCoordinate(literal)) {
                    selected[0] = true;
                }
                return literal;
            }
        }.visit(compilationUnit, ctx);
        return selected[0];
    }

    private static boolean hasSelectedKotlinCore(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] selected = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (isDirectGradleDependencyLiteral(getCursor()) && isSelectedCoreCoordinate(literal)) {
                    selected[0] = true;
                }
                return literal;
            }
        }.visit(compilationUnit, ctx);
        return selected[0];
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    static boolean hasFamilyCoordinates(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               tag.getChildValue("artifactId").filter(FAMILY_ARTIFACTS::contains).isPresent();
    }

    static boolean hasCoreCoordinates(Xml.Tag tag) {
        return hasFamilyCoordinates(tag) && CORE.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardJar(Xml.Tag tag) {
        boolean standardType = tag.getChildValue("type").map(String::trim)
                .filter(value -> !value.isEmpty()).map("jar"::equals).orElse(true);
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        return standardType && noClassifier;
    }

    static boolean isMavenDependencyBlock(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) {
            return false;
        }
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag dependencies) || !"dependencies".equals(dependencies.getName())) {
            return false;
        }
        Cursor owner = parent.getParentTreeCursor();
        if (isProjectOrProfileOwner(owner)) {
            return true;
        }
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag) ||
            !"dependencyManagement".equals(ownerTag.getName())) {
            return false;
        }
        return isProjectOrProfileOwner(owner.getParentTreeCursor());
    }

    private static boolean isMavenFamilyDependency(Cursor cursor, Xml.Tag tag) {
        return isMavenDependencyBlock(cursor, tag) && hasFamilyCoordinates(tag) && isStandardJar(tag);
    }

    private static boolean isMavenCoreDependency(Cursor cursor, Xml.Tag tag) {
        return isMavenFamilyDependency(cursor, tag) && CORE.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").map(String::trim).orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName());
    }

    private static boolean isProjectPropertiesChild(Cursor cursor, Xml.Tag tag) {
        if (!isPropertiesChild(cursor, tag)) {
            return false;
        }
        Cursor propertiesCursor = cursor.getParentTreeCursor();
        Cursor ownerCursor = propertiesCursor.getParentTreeCursor();
        return isProjectOwner(ownerCursor);
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) {
            return false;
        }
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor &&
                "dependencies".equals(ancestor.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProjectOrProfileOwner(Cursor owner) {
        if (isProjectOwner(owner)) {
            return true;
        }
        if (owner == null || !(owner.getValue() instanceof Xml.Tag profile) ||
            !"profile".equals(profile.getName())) {
            return false;
        }
        Cursor profiles = owner.getParentTreeCursor();
        Cursor project = profiles == null ? null : profiles.getParentTreeCursor();
        return profiles != null && profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) && isProjectOwner(project);
    }

    private static boolean isProjectOwner(Cursor owner) {
        if (owner == null || !(owner.getValue() instanceof Xml.Tag project) ||
            !"project".equals(project.getName())) {
            return false;
        }
        Cursor document = owner.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }

    private static boolean isSelectedCoreCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return false;
        }
        String prefix = GROUP + ":" + CORE + ":";
        return value.startsWith(prefix) && SOURCE_VERSIONS.contains(value.substring(prefix.length()));
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        for (String artifact : FAMILY_ARTIFACTS) {
            String prefix = GROUP + ":" + artifact + ":";
            if (value.startsWith(prefix) && SOURCE_VERSIONS.contains(value.substring(prefix.length()))) {
                return replaceLiteral(literal, value, prefix + TARGET);
            }
        }
        return literal;
    }

    private static boolean isSelectedCoreMap(J.MethodInvocation invocation) {
        if (hasGradleVariant(invocation)) {
            return false;
        }
        return (GROUP.equals(invocationMapValue(invocation, "group")) &&
                CORE.equals(invocationMapValue(invocation, "name")) &&
                SOURCE_VERSIONS.contains(invocationMapValue(invocation, "version"))) ||
               invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                       .anyMatch(map -> GROUP.equals(mapValue(map, "group")) && CORE.equals(mapValue(map, "name")) &&
                                        SOURCE_VERSIONS.contains(mapValue(map, "version")) && !hasVariantKey(map));
    }

    private static boolean isFamilyMap(J.MethodInvocation invocation) {
        return GROUP.equals(invocationMapValue(invocation, "group")) &&
               FAMILY_ARTIFACTS.contains(invocationMapValue(invocation, "name")) &&
               SOURCE_VERSIONS.contains(invocationMapValue(invocation, "version")) &&
               !hasGradleVariant(invocation);
    }

    private static boolean isFamilyMap(G.MapLiteral map) {
        return GROUP.equals(mapValue(map, "group")) && FAMILY_ARTIFACTS.contains(mapValue(map, "name")) &&
               SOURCE_VERSIONS.contains(mapValue(map, "version")) && !hasVariantKey(map);
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
        return map.withElements(map.getElements().stream()
                .map(UpgradeSelectedMyBatisSpringBootStarterDependency::upgradeVersionEntry).toList());
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

    static boolean hasGradleVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && isVariantKey(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasVariantKey(map));
    }

    private static boolean hasVariantKey(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> isVariantKey(mapKey(entry)));
    }

    private static boolean isVariantKey(String key) {
        return Set.of("classifier", "ext", "type").contains(key);
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path) {
            if (GENERATED_DIRECTORIES.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }
}
