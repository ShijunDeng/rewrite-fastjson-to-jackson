package com.huawei.clouds.openrewrite.hikaricp;

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

/** Upgrade only HikariCP versions explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedHikariCPDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of("3.3.0", "3.4.5", "4.0.3");
    private static final String TARGET = "6.3.3";
    private static final String GROUP = "com.zaxxer";
    private static final String ARTIFACT = "HikariCP";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
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
        return "Upgrade spreadsheet-selected HikariCP declarations to 6.3.3";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct com.zaxxer:HikariCP declarations whose literal or safely isolated Maven-property version exactly matches 3.3.0, 3.4.5, or 4.0.3.";
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
        Map<String, Integer> hikariReferences = new HashMap<>();
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
                if (isEligibleHikariDependency(getCursor(), tag)) {
                    propertyName(tag).filter(name -> SOURCE_VERSIONS.contains(propertyValues.get(name)))
                            .ifPresent(name -> hikariReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        hikariReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && Integer.valueOf(1).equals(propertyDefinitions.get(name))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isMavenPropertiesChild(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isEligibleHikariDependency(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (!GRADLE_CONFIGURATIONS.contains(m.getSimpleName()) || !isInsideDependenciesBlock(getCursor())) {
                    return m;
                }
                if (GROUP.equals(invocationMapValue(m, "group")) && ARTIFACT.equals(invocationMapValue(m, "name")) &&
                    isSelectedVersion(invocationMapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry ? upgradeVersionEntry(entry) : argument).toList());
                }
                return m.withArguments(m.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map && isHikariMap(map) ? upgradeMap(map) : argument).toList());
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

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    static boolean isEligibleHikariDependency(Cursor cursor, Xml.Tag tag) {
        if (!isHikari(tag) || tag.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isPresent() ||
            tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty() && !"jar".equals(value)).isPresent()) {
            return false;
        }

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

    private static boolean isHikari(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               GROUP.equals(tag.getChildValue("groupId").map(String::trim).orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").map(String::trim).orElse(null));
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").map(String::trim).orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
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

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) {
            return literal;
        }
        return replaceLiteral(literal, value, PREFIX + TARGET);
    }

    private static boolean isHikariMap(G.MapLiteral map) {
        return GROUP.equals(mapValue(map, "group")) && ARTIFACT.equals(mapValue(map, "name")) &&
               isSelectedVersion(mapValue(map, "version"));
    }

    private static boolean isSelectedVersion(String version) {
        return version != null && SOURCE_VERSIONS.contains(version);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream()
                .map(UpgradeSelectedHikariCPDependency::upgradeVersionEntry).toList());
    }

    private static G.MapEntry upgradeVersionEntry(G.MapEntry entry) {
        if (!"version".equals(mapKey(entry)) || !(entry.getValue() instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) || !SOURCE_VERSIONS.contains(version)) {
            return entry;
        }
        return entry.withValue(replaceLiteral(literal, version, TARGET));
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
