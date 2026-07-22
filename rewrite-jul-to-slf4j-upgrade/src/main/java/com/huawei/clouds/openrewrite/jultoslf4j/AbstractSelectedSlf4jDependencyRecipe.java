package com.huawei.clouds.openrewrite.jultoslf4j;

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

/** Shared strict Maven and Gradle implementation for the JUL-to-SLF4J migration recipes. */
abstract class AbstractSelectedSlf4jDependencyRecipe extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of("1.7.30", "1.7.32", "1.7.36");
    static final String TARGET = "2.0.17";
    private static final String GROUP = "org.slf4j";
    private static final String CORE = "jul-to-slf4j";
    private static final Set<String> COMPANIONS = Set.of(
            "slf4j-api", "slf4j-simple", "slf4j-nop", "slf4j-reload4j",
            "jcl-over-slf4j", "log4j-over-slf4j"
    );
    private static final Set<String> FAMILY = Set.of(
            CORE, "slf4j-api", "slf4j-simple", "slf4j-nop", "slf4j-reload4j",
            "jcl-over-slf4j", "log4j-over-slf4j"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern COMPANION_2_VERSION = Pattern.compile("^2[.]0[.](\\d+)$");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", ".gradle", ".idea", "node_modules"
    );

    enum Mode {
        CORE_FROM_SOURCE,
        FAMILY_FROM_SOURCE,
        COMPANIONS_FOR_TARGET
    }

    private final Mode mode;

    protected AbstractSelectedSlf4jDependencyRecipe(Mode mode) {
        this.mode = mode;
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
                    hasEligibleGroovyCore(compilationUnit, ctx)) {
                    return migrateGroovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts") &&
                    hasEligibleKotlinCore(compilationUnit, ctx)) {
                    return migrateKotlin(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> property.getValue()
                                .ifPresent(value -> propertyValues.put(property.getName(), value.trim()))));

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> coreReferences = new HashMap<>();
        Map<String, Integer> familyReferences = new HashMap<>();
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
                if (isFamily(getCursor(), tag)) {
                    propertyName(tag).ifPresent(name -> familyReferences.merge(name, 1, Integer::sum));
                }
                if (isCore(getCursor(), tag)) {
                    propertyName(tag).ifPresent(name -> coreReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> coreOwnedProperties = ownedProperties(allReferences, coreReferences);
        Set<String> familyOwnedProperties = ownedProperties(allReferences, familyReferences);
        if (!hasEligibleMavenCore(document, propertyValues, coreOwnedProperties, familyOwnedProperties, ctx)) {
            return document;
        }

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isRootPropertiesChild(getCursor(), t) && shouldUpgradeProperty(
                        t.getName(), t.getValue().map(String::trim).orElse(""),
                        coreOwnedProperties, familyOwnedProperties, coreReferences.keySet())) {
                    return t.withValue(TARGET);
                }
                if (isFamily(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(version -> shouldUpgrade(t.getChildValue("artifactId").orElse(""), version))
                        .isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private boolean hasEligibleMavenCore(Xml.Document document, Map<String, String> propertyValues,
                                          Set<String> coreOwnedProperties, Set<String> familyOwnedProperties,
                                          ExecutionContext ctx) {
        boolean[] selected = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isCore(getCursor(), tag)) {
                    String version = tag.getChildValue("version").map(String::trim).orElse("");
                    if (eligibleCoreVersion(version)) {
                        selected[0] = true;
                    } else {
                        propertyName(tag).filter(name -> propertyCanGateCore(name, coreOwnedProperties,
                                        familyOwnedProperties))
                                .map(propertyValues::get).filter(AbstractSelectedSlf4jDependencyRecipe.this::eligibleCoreVersion)
                                .ifPresent(ignored -> selected[0] = true);
                    }
                }
                return selected[0] ? tag : super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return selected[0];
    }

    private boolean propertyCanGateCore(String name, Set<String> coreOwned, Set<String> familyOwned) {
        if (mode == Mode.CORE_FROM_SOURCE) {
            return coreOwned.contains(name);
        }
        if (mode == Mode.FAMILY_FROM_SOURCE) {
            return familyOwned.contains(name);
        }
        return true;
    }

    private boolean eligibleCoreVersion(String version) {
        return mode == Mode.COMPANIONS_FOR_TARGET ? TARGET.equals(version) : SOURCE_VERSIONS.contains(version);
    }

    private boolean shouldUpgradeProperty(String name, String version, Set<String> coreOwned,
                                          Set<String> familyOwned, Set<String> propertiesUsedByCore) {
        return switch (mode) {
            case CORE_FROM_SOURCE -> coreOwned.contains(name) && SOURCE_VERSIONS.contains(version);
            case FAMILY_FROM_SOURCE -> familyOwned.contains(name) &&
                                       (propertiesUsedByCore.contains(name) ? SOURCE_VERSIONS.contains(version) :
                                               isUpgradeableCompanionVersion(version));
            case COMPANIONS_FOR_TARGET -> familyOwned.contains(name) && !propertiesUsedByCore.contains(name) &&
                                          isUpgradeableCompanionVersion(version);
        };
    }

    private boolean shouldUpgrade(String artifact, String version) {
        if (CORE.equals(artifact)) {
            return mode != Mode.COMPANIONS_FOR_TARGET && SOURCE_VERSIONS.contains(version);
        }
        return mode != Mode.CORE_FROM_SOURCE && COMPANIONS.contains(artifact) &&
               isUpgradeableCompanionVersion(version);
    }

    private boolean hasEligibleGroovyCore(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] selected = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (isGradleDependencyInvocation(getCursor(), method) && isEligibleCoreMap(method)) {
                    selected[0] = true;
                }
                return selected[0] ? method : super.visitMethodInvocation(method, executionContext);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (isDirectGradleDependencyLiteral(getCursor()) && isEligibleCoreCoordinate(literal)) {
                    selected[0] = true;
                }
                return literal;
            }
        }.visit(compilationUnit, ctx);
        return selected[0];
    }

    private boolean hasEligibleKotlinCore(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] selected = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (isDirectGradleDependencyLiteral(getCursor()) && isEligibleCoreCoordinate(literal)) {
                    selected[0] = true;
                }
                return literal;
            }
        }.visit(compilationUnit, ctx);
        return selected[0];
    }

    private G.CompilationUnit migrateGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (!isGradleDependencyInvocation(getCursor(), m) || hasGradleVariant(m)) {
                    return m;
                }
                String artifact = invocationMapValue(m, "name");
                String version = invocationMapValue(m, "version");
                if (GROUP.equals(invocationMapValue(m, "group")) && shouldUpgrade(artifact, version)) {
                    return m.withArguments(m.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry ? upgradeVersionEntry(entry) : argument).toList());
                }
                return m.withArguments(m.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map && shouldUpgradeMap(map) ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private K.CompilationUnit migrateKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private boolean isEligibleCoreCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return false;
        }
        String prefix = GROUP + ":" + CORE + ":";
        return value.startsWith(prefix) && eligibleCoreVersion(value.substring(prefix.length()));
    }

    private J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        for (String artifact : FAMILY) {
            String prefix = GROUP + ":" + artifact + ":";
            if (value.startsWith(prefix) && shouldUpgrade(artifact, value.substring(prefix.length()))) {
                return replaceLiteral(literal, value, prefix + TARGET);
            }
        }
        return literal;
    }

    private boolean isEligibleCoreMap(J.MethodInvocation invocation) {
        if (hasGradleVariant(invocation)) {
            return false;
        }
        return (GROUP.equals(invocationMapValue(invocation, "group")) &&
                CORE.equals(invocationMapValue(invocation, "name")) &&
                eligibleCoreVersion(invocationMapValue(invocation, "version"))) ||
               invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                       .anyMatch(map -> GROUP.equals(mapValue(map, "group")) && CORE.equals(mapValue(map, "name")) &&
                                        eligibleCoreVersion(mapValue(map, "version")));
    }

    private boolean shouldUpgradeMap(G.MapLiteral map) {
        return !hasGradleVariant(map) && GROUP.equals(mapValue(map, "group")) &&
               shouldUpgrade(mapValue(map, "name"), mapValue(map, "version"));
    }

    private static Set<String> ownedProperties(Map<String, Integer> allReferences,
                                                Map<String, Integer> allowedReferences) {
        Set<String> owned = new HashSet<>();
        allowedReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name))) {
                owned.add(name);
            }
        });
        return owned;
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    private static boolean isFamily(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && !hasMavenVariant(tag) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               tag.getChildValue("artifactId").filter(FAMILY::contains).isPresent();
    }

    private static boolean isCore(Cursor cursor, Xml.Tag tag) {
        return isFamily(cursor, tag) && CORE.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").map(String::trim).orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isRootPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag parentTag) || !"properties".equals(parentTag.getName())) {
            return false;
        }
        Cursor owner = parent.getParentTreeCursor();
        Cursor document = owner == null ? null : owner.getParentTreeCursor();
        return !"properties".equals(tag.getName()) && owner != null && owner.getValue() instanceof Xml.Tag ownerTag &&
               "project".equals(ownerTag.getName()) && document != null && document.getValue() instanceof Xml.Document;
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) || !"dependencies".equals(container.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("project".equals(ownerTag.getName()) || "profile".equals(ownerTag.getName())) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        Cursor managedOwner = owner.getParentTreeCursor();
        return managedOwner != null && managedOwner.getValue() instanceof Xml.Tag managedOwnerTag &&
               ("project".equals(managedOwnerTag.getName()) || "profile".equals(managedOwnerTag.getName()));
    }

    static boolean isActiveProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!isProjectDependency(cursor, tag)) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies == null ? null : dependencies.getParentTreeCursor();
        return owner != null && (!(owner.getValue() instanceof Xml.Tag ownerTag) ||
                                 !"dependencyManagement".equals(ownerTag.getName()));
    }

    static boolean hasMavenVariant(Xml.Tag dependency) {
        if (dependency.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isPresent()) {
            return true;
        }
        return dependency.getChildValue("type").map(String::trim)
                .filter(value -> !value.isEmpty() && !"jar".equals(value)).isPresent();
    }

    static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof J.MethodInvocation owner) {
                return "dependencies".equals(owner.getSimpleName());
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

    static boolean hasGradleVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && isVariantKey(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasGradleVariant(map));
    }

    private static boolean hasGradleVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> isVariantKey(mapKey(entry)));
    }

    private static boolean isVariantKey(String key) {
        return "classifier".equals(key) || "ext".equals(key) || "type".equals(key);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(this::upgradeVersionEntry).toList());
    }

    private G.MapEntry upgradeVersionEntry(G.MapEntry entry) {
        if (!"version".equals(mapKey(entry)) || !(entry.getValue() instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) ||
            !(SOURCE_VERSIONS.contains(version) || isUpgradeableCompanionVersion(version))) {
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

    private static boolean isUpgradeableCompanionVersion(String version) {
        if (SOURCE_VERSIONS.contains(version)) {
            return true;
        }
        Matcher matcher = COMPANION_2_VERSION.matcher(version == null ? "" : version);
        if (!matcher.matches()) {
            return false;
        }
        try {
            return Integer.parseInt(matcher.group(1)) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
