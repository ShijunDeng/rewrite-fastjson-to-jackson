package com.huawei.clouds.openrewrite.hibernatevalidator;

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

/** Upgrade only Hibernate Validator versions explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedHibernateValidatorDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
            "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
    );
    private static final String TARGET = "8.0.3.Final";
    private static final String GROUP = "org.hibernate.validator";
    private static final String CORE = "hibernate-validator";
    private static final String COORDINATE_PREFIX = GROUP + ":";
    private static final Set<String> FAMILY_ARTIFACTS = Set.of(
            CORE, "hibernate-validator-cdi", "hibernate-validator-annotation-processor"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", ".gradle", ".idea", "node_modules"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Hibernate Validator declarations to 8.0.3.Final";
    }

    @Override
    public String getDescription() {
        return "When a build file selects an explicitly listed org.hibernate.validator:hibernate-validator " +
               "version, upgrade exact listed core, CDI, and annotation-processor declarations using only " +
               "literal versions or Maven properties referenced exclusively by that artifact family.";
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
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return migrateGroovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return migrateKotlin(compilationUnit, ctx);
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
                if (isMavenPropertiesChild(getCursor(), tag)) {
                    propertyDefinitions.merge(tag.getName(), 1, Integer::sum);
                    tag.getValue().ifPresent(value -> propertyValues.put(tag.getName(), value.trim()));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Map<String, Integer> allReferences = new HashMap<>();
        collectReferences(document.printAll(), allReferences);
        Map<String, Integer> familyReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isEligibleFamilyBlock(getCursor(), tag)) {
                    collectReferences(tag, familyReferences);
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        familyReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && Integer.valueOf(1).equals(propertyDefinitions.get(name)) &&
                SOURCE_VERSIONS.contains(propertyValues.get(name))) {
                safeProperties.add(name);
            }
        });

        boolean[] selectedCore = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isEligibleFamilyBlock(getCursor(), tag) &&
                    CORE.equals(tag.getChildValue("artifactId").map(String::trim).orElse(null))) {
                    String version = tag.getChildValue("version").map(String::trim).orElse("");
                    selectedCore[0] |= SOURCE_VERSIONS.contains(version) ||
                                       propertyName(version).filter(safeProperties::contains).isPresent();
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        if (!selectedCore[0]) {
            return document;
        }

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isMavenPropertiesChild(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isEligibleFamilyBlock(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] selectedCore = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (GRADLE_CONFIGURATIONS.contains(m.getSimpleName()) && isInsideDependenciesBlock(getCursor()) &&
                    invocationMatches(m, CORE)) {
                    selectedCore[0] = true;
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                if (direct && coordinateMatches(visited, CORE)) {
                    selectedCore[0] = true;
                }
                return visited;
            }
        }.visit(compilationUnit, ctx);
        if (!selectedCore[0]) {
            return compilationUnit;
        }

        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (!GRADLE_CONFIGURATIONS.contains(m.getSimpleName()) || !isInsideDependenciesBlock(getCursor())) {
                    return m;
                }
                if (isSelectedFamilyInvocationMap(m)) {
                    return m.withArguments(m.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry ? upgradeVersionEntry(entry) : argument).toList());
                }
                return m.withArguments(m.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map && isSelectedFamilyMap(map) ? upgradeMap(map) : argument)
                        .toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeFamilyCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] selectedCore = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                if (direct && coordinateMatches(visited, CORE)) {
                    selectedCore[0] = true;
                }
                return visited;
            }
        }.visit(compilationUnit, ctx);
        if (!selectedCore[0]) {
            return compilationUnit;
        }

        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeFamilyCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static boolean invocationMatches(J.MethodInvocation invocation, String artifact) {
        if (GROUP.equals(invocationMapValue(invocation, "group")) &&
            artifact.equals(invocationMapValue(invocation, "name")) &&
            SOURCE_VERSIONS.contains(invocationMapValue(invocation, "version"))) {
            return true;
        }
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .anyMatch(map -> GROUP.equals(mapValue(map, "group")) && artifact.equals(mapValue(map, "name")) &&
                                 SOURCE_VERSIONS.contains(mapValue(map, "version")));
    }

    private static boolean isSelectedFamilyInvocationMap(J.MethodInvocation invocation) {
        return GROUP.equals(invocationMapValue(invocation, "group")) &&
               FAMILY_ARTIFACTS.contains(invocationMapValue(invocation, "name")) &&
               SOURCE_VERSIONS.contains(invocationMapValue(invocation, "version"));
    }

    private static boolean isSelectedFamilyMap(G.MapLiteral map) {
        return GROUP.equals(mapValue(map, "group")) && FAMILY_ARTIFACTS.contains(mapValue(map, "name")) &&
               SOURCE_VERSIONS.contains(mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream()
                .map(UpgradeSelectedHibernateValidatorDependency::upgradeVersionEntry).toList());
    }

    private static G.MapEntry upgradeVersionEntry(G.MapEntry entry) {
        if (!"version".equals(mapKey(entry)) || !(entry.getValue() instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) || !SOURCE_VERSIONS.contains(version)) {
            return entry;
        }
        return entry.withValue(replaceLiteral(literal, version, TARGET));
    }

    private static J.Literal upgradeFamilyCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        for (String artifact : FAMILY_ARTIFACTS) {
            String prefix = COORDINATE_PREFIX + artifact + ":";
            if (value.startsWith(prefix) && SOURCE_VERSIONS.contains(value.substring(prefix.length()))) {
                return replaceLiteral(literal, value, prefix + TARGET);
            }
        }
        return literal;
    }

    private static boolean coordinateMatches(J.Literal literal, String artifact) {
        if (!(literal.getValue() instanceof String value)) {
            return false;
        }
        String prefix = COORDINATE_PREFIX + artifact + ":";
        return value.startsWith(prefix) && SOURCE_VERSIONS.contains(value.substring(prefix.length()));
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
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean isEligibleFamilyBlock(Cursor cursor, Xml.Tag tag) {
        if (!GROUP.equals(tag.getChildValue("groupId").map(String::trim).orElse(null)) ||
            tag.getChildValue("artifactId").map(String::trim).filter(FAMILY_ARTIFACTS::contains).isEmpty() ||
            tag.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isPresent() ||
            tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty() && !"jar".equals(value)).isPresent()) {
            return false;
        }
        if ("dependency".equals(tag.getName())) {
            return isProjectDependency(cursor);
        }
        if (!"path".equals(tag.getName()) ||
            !"hibernate-validator-annotation-processor".equals(
                    tag.getChildValue("artifactId").map(String::trim).orElse(null))) {
            return false;
        }
        Cursor parent = cursor.getParentTreeCursor();
        if (parent == null || !(parent.getValue() instanceof Xml.Tag paths) ||
            !"annotationProcessorPaths".equals(paths.getName())) {
            return false;
        }
        for (Cursor ancestor = parent.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof Xml.Tag owner && "plugin".equals(owner.getName())) {
                String groupId = owner.getChildValue("groupId").map(String::trim)
                        .orElse("org.apache.maven.plugins");
                return "org.apache.maven.plugins".equals(groupId) &&
                       "maven-compiler-plugin".equals(
                               owner.getChildValue("artifactId").map(String::trim).orElse(null));
            }
        }
        return false;
    }

    private static boolean isProjectDependency(Cursor cursor) {
        Cursor dependenciesCursor = cursor.getParentTreeCursor();
        if (dependenciesCursor == null || !(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) {
            return false;
        }
        Cursor ownerCursor = dependenciesCursor.getParentTreeCursor();
        if (ownerCursor == null || !(ownerCursor.getValue() instanceof Xml.Tag owner)) {
            return false;
        }
        if ("project".equals(owner.getName()) || "profile".equals(owner.getName())) {
            return true;
        }
        if (!"dependencyManagement".equals(owner.getName())) {
            return false;
        }
        Cursor managedOwnerCursor = ownerCursor.getParentTreeCursor();
        return managedOwnerCursor != null && managedOwnerCursor.getValue() instanceof Xml.Tag managedOwner &&
               ("project".equals(managedOwner.getName()) || "profile".equals(managedOwner.getName()));
    }

    private static Optional<String> propertyName(String version) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(version);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    private static void collectReferences(Xml.Tag tag, Map<String, Integer> references) {
        tag.getValue().ifPresent(value -> collectReferences(value, references));
        tag.getAttributes().forEach(attribute -> collectReferences(attribute.getValueAsString(), references));
        tag.getChildren().forEach(child -> collectReferences(child, references));
    }

    private static boolean isMavenPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (parent == null) {
            return false;
        }
        Cursor grandparent = parent.getParentTreeCursor();
        if (grandparent == null) {
            return false;
        }
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName()) && grandparent.getValue() instanceof Xml.Tag owner &&
               ("project".equals(owner.getName()) || "profile".equals(owner.getName()));
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent != null && parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) && isInsideDependenciesBlock(parent);
    }

    private static boolean isInsideDependenciesBlock(Cursor cursor) {
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof J.MethodInvocation invocation &&
                "dependencies".equals(invocation.getSimpleName())) {
                return true;
            }
        }
        return false;
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
