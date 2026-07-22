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
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Mark owned build declarations that are deliberately outside the strict dependency upgrade. */
public final class FindIcu4jBuildMigrationRisks extends Recipe {
    private static final Pattern FIXED_VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> COMPANIONS = Set.of("icu4j-charset", "icu4j-localespi");
    private static final String EXTERNAL_MESSAGE =
            "This ICU4J version is versionless, property/catalog/BOM-managed, ranged, dynamic, or otherwise not a fixed owned literal; migrate its actual owner and verify that the resolved runtime is exactly 77.1";
    private static final String OUTSIDE_MESSAGE =
            "This fixed ICU4J version is outside the workbook source selection; choose its migration deliberately instead of widening the automatic 67.1/73.1/73.2 upgrade";
    private static final String MANAGED_MESSAGE =
            "This ICU4J declaration is under dependencyManagement rather than a direct dependency; update the owning BOM/management policy deliberately and verify all consumers before selecting 77.1";
    private static final String COMPANION_MESSAGE =
            "This ICU4J companion is not aligned to 77.1; align icu4j, icu4j-charset, and icu4j-localespi as one family and verify SPI discovery, charsets, binary linkage, exclusions, shading, and classloaders";
    private static final String VARIANT_MESSAGE =
            "This classified or non-JAR ICU4J artifact is outside deterministic runtime scope; verify that 77.1 publishes the same artifact shape and that shading/classloader behavior remains valid";
    private static final String JAVA_MESSAGE =
            "ICU4J 77.1 requires Java 8 or newer; raise the owning compiler/toolchain level and verify every runtime, CI image, Android desugaring path, test launcher, and packaged deployment";
    private static final String SHADE_MESSAGE =
            "ICU4J is shaded or relocated; rebuild every shaded artifact with 77.1 and verify services, resources, data loading, reflection, serialization, duplicate classes, and classloader isolation";

    @Override
    public String getDisplayName() {
        return "Find ICU4J 77 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Maven/Gradle owners for external or non-selected versions, dependency management, " +
               "variants, companion skew, Java levels below 8, and ICU shading/relocation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedIcu4jDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    boolean hasFamilyDependency = hasOwnedFamilyDependency(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag visited = super.visitTag(tag, ec);
                            String compilerRisk = hasFamilyDependency ? javaLevelMessage(getCursor(), visited) : null;
                            if (compilerRisk != null) return mark(visited, compilerRisk);
                            if (isOwnedShadeRelocationPattern(getCursor(), visited) &&
                                visited.getValue().map(String::trim).filter("com.ibm.icu"::equals).isPresent()) {
                                return mark(visited, SHADE_MESSAGE);
                            }
                            if (!UpgradeSelectedIcu4jDependency.isAnyOwnedDependency(getCursor(), visited)) {
                                return visited;
                            }
                            String group = visited.getChildValue("groupId").orElse("");
                            String artifact = visited.getChildValue("artifactId").orElse("");
                            if (!UpgradeSelectedIcu4jDependency.GROUP.equals(group) ||
                                !(UpgradeSelectedIcu4jDependency.ARTIFACT.equals(artifact) || COMPANIONS.contains(artifact))) {
                                return visited;
                            }
                            if (visited.getChild("classifier").isPresent() ||
                                !"jar".equals(visited.getChildValue("type").orElse("jar"))) {
                                return mark(visited, VARIANT_MESSAGE);
                            }
                            String rawVersion = visited.getChildValue("version").map(String::trim).orElse("");
                            String version = resolveVersion(document, getCursor(), rawVersion);
                            if (!UpgradeSelectedIcu4jDependency.isDirectProjectDependency(getCursor(), visited)) {
                                if (UpgradeSelectedIcu4jDependency.TARGET.equals(version)) return visited;
                                if (version != null && FIXED_VERSION.matcher(version).matches()) {
                                    if (UpgradeSelectedIcu4jDependency.ARTIFACT.equals(artifact) &&
                                        UpgradeSelectedIcu4jDependency.SOURCE_VERSIONS.contains(version)) return visited;
                                    return markChild(visited, "version", COMPANIONS.contains(artifact)
                                            ? COMPANION_MESSAGE : OUTSIDE_MESSAGE);
                                }
                                return markChild(visited, "version", MANAGED_MESSAGE);
                            }
                            if ((version == null || version.isEmpty()) &&
                                managedAtTarget(document, getCursor(), artifact)) return visited;
                            if (version == null || !FIXED_VERSION.matcher(version).matches()) {
                                return visited.getChild("version").isPresent()
                                        ? markChild(visited, "version", EXTERNAL_MESSAGE) : mark(visited, EXTERNAL_MESSAGE);
                            }
                            if (COMPANIONS.contains(artifact) && !UpgradeSelectedIcu4jDependency.TARGET.equals(version)) {
                                return markChild(visited, "version", COMPANION_MESSAGE);
                            }
                            if (UpgradeSelectedIcu4jDependency.ARTIFACT.equals(artifact) &&
                                !UpgradeSelectedIcu4jDependency.TARGET.equals(version) &&
                                !UpgradeSelectedIcu4jDependency.SOURCE_VERSIONS.contains(version)) {
                                return markChild(visited, "version", OUTSIDE_MESSAGE);
                            }
                            return visited;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedIcu4jDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            if (!direct) return visited;
                            String group = invocationMapValue(visited, "group");
                            String artifact = invocationMapValue(visited, "name");
                            String version = invocationMapValue(visited, "version");
                            String message = mapMessage(group, artifact, version,
                                    UpgradeSelectedIcu4jDependency.hasVariantKey(visited));
                            if (message == null) {
                                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                if (map != null) message = mapMessage(mapValue(map, "group"), mapValue(map, "name"),
                                        mapValue(map, "version"), UpgradeSelectedIcu4jDependency.hasVariantKey(map));
                            }
                            return message == null ? visited : mark(visited, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedIcu4jDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedIcu4jDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String javaLevelMessage(Cursor cursor, Xml.Tag tag) {
        String name = tag.getName();
        if (Set.of("maven.compiler.source", "maven.compiler.target", "maven.compiler.release").contains(name) &&
            UpgradeSelectedIcu4jDependency.isMavenPropertyDefinition(cursor, tag)) {
            return belowJava8(tag.getValue().orElse("")) ? JAVA_MESSAGE : null;
        }
        if (!Set.of("source", "target", "release").contains(name) || !belowJava8(tag.getValue().orElse(""))) {
            return null;
        }
        Cursor configuration = cursor.getParentTreeCursor();
        if (!(configuration.getValue() instanceof Xml.Tag configurationTag) ||
            !"configuration".equals(configurationTag.getName())) return null;
        Cursor plugin = pluginForConfiguration(configuration);
        return isOwnedBuildPlugin(plugin, "maven-compiler-plugin") ? JAVA_MESSAGE : null;
    }

    private static boolean hasOwnedFamilyDependency(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                if (UpgradeSelectedIcu4jDependency.isAnyOwnedDependency(getCursor(), tag) &&
                    UpgradeSelectedIcu4jDependency.GROUP.equals(tag.getChildValue("groupId").orElse("")) &&
                    (UpgradeSelectedIcu4jDependency.ARTIFACT.equals(tag.getChildValue("artifactId").orElse("")) ||
                     COMPANIONS.contains(tag.getChildValue("artifactId").orElse("")))) found[0] = true;
                return found[0] ? tag : super.visitTag(tag, ec);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static boolean isOwnedShadeRelocationPattern(Cursor cursor, Xml.Tag tag) {
        if (!"pattern".equals(tag.getName())) return false;
        Cursor relocation = cursor.getParentTreeCursor();
        Cursor relocations = relocation == null ? null : relocation.getParentTreeCursor();
        Cursor configuration = relocations == null ? null : relocations.getParentTreeCursor();
        if (!(relocation != null && relocation.getValue() instanceof Xml.Tag relocationTag &&
              "relocation".equals(relocationTag.getName())) ||
            !(relocations != null && relocations.getValue() instanceof Xml.Tag relocationsTag &&
              "relocations".equals(relocationsTag.getName())) ||
            !(configuration != null && configuration.getValue() instanceof Xml.Tag configurationTag &&
              "configuration".equals(configurationTag.getName()))) return false;
        Cursor plugin = pluginForConfiguration(configuration);
        return isOwnedBuildPlugin(plugin, "maven-shade-plugin");
    }

    private static Cursor pluginForConfiguration(Cursor configuration) {
        Cursor owner = configuration.getParentTreeCursor();
        if (owner != null && owner.getValue() instanceof Xml.Tag execution && "execution".equals(execution.getName())) {
            Cursor executions = owner.getParentTreeCursor();
            return executions == null ? null : executions.getParentTreeCursor();
        }
        return owner;
    }

    private static boolean isOwnedBuildPlugin(Cursor plugin, String artifact) {
        if (plugin == null || !(plugin.getValue() instanceof Xml.Tag pluginTag) ||
            !"plugin".equals(pluginTag.getName()) ||
            !artifact.equals(pluginTag.getChildValue("artifactId").orElse("")) ||
            !"org.apache.maven.plugins".equals(pluginTag.getChildValue("groupId").orElse("org.apache.maven.plugins"))) {
            return false;
        }
        Cursor plugins = plugin.getParentTreeCursor();
        if (plugins == null || !(plugins.getValue() instanceof Xml.Tag pluginsTag) ||
            !"plugins".equals(pluginsTag.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("build".equals(ownerTag.getName())) {
            Cursor projectOrProfile = owner.getParentTreeCursor();
            return UpgradeSelectedIcu4jDependency.isProjectOwner(projectOrProfile) ||
                   UpgradeSelectedIcu4jDependency.isProfileOwner(projectOrProfile);
        }
        if (!"pluginManagement".equals(ownerTag.getName())) return false;
        Cursor build = owner.getParentTreeCursor();
        Cursor projectOrProfile = build == null ? null : build.getParentTreeCursor();
        return build != null && build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               (UpgradeSelectedIcu4jDependency.isProjectOwner(projectOrProfile) ||
                UpgradeSelectedIcu4jDependency.isProfileOwner(projectOrProfile));
    }

    private static String resolveVersion(Xml.Document document, Cursor dependency, String raw) {
        var matcher = PROPERTY_REFERENCE.matcher(raw);
        if (!matcher.matches()) return raw;
        return propertyValue(document, owningProfile(dependency), matcher.group(1)).orElse(null);
    }

    private static boolean managedAtTarget(Xml.Document document, Cursor dependency, String artifact) {
        if (managementVersion(document.getRoot(), document, null, artifact)
                .filter(UpgradeSelectedIcu4jDependency.TARGET::equals).isPresent()) return true;
        Xml.Tag profile = owningProfile(dependency);
        return profile != null && managementVersion(profile, document, profile, artifact)
                .filter(UpgradeSelectedIcu4jDependency.TARGET::equals).isPresent();
    }

    private static Optional<String> managementVersion(Xml.Tag owner, Xml.Document document,
                                                       Xml.Tag profile, String artifact) {
        Optional<Xml.Tag> dependencies = owner.getChild("dependencyManagement").flatMap(dm -> dm.getChild("dependencies"));
        if (dependencies.isEmpty()) return Optional.empty();
        for (Xml.Tag dependency : dependencies.get().getChildren()) {
            if (UpgradeSelectedIcu4jDependency.GROUP.equals(dependency.getChildValue("groupId").orElse("")) &&
                artifact.equals(dependency.getChildValue("artifactId").orElse("")) &&
                dependency.getChild("classifier").isEmpty() &&
                "jar".equals(dependency.getChildValue("type").orElse("jar"))) {
                String raw = dependency.getChildValue("version").map(String::trim).orElse("");
                var matcher = PROPERTY_REFERENCE.matcher(raw);
                return matcher.matches() ? propertyValue(document, profile, matcher.group(1)) : Optional.of(raw);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> propertyValue(Xml.Document document, Xml.Tag profile, String name) {
        if (profile != null) {
            Optional<String> scoped = profile.getChild("properties").flatMap(properties ->
                    properties.getChildValue(name)).map(String::trim);
            if (scoped.isPresent()) return scoped;
        }
        return document.getRoot().getChild("properties").flatMap(properties ->
                properties.getChildValue(name)).map(String::trim);
    }

    private static Xml.Tag owningProfile(Cursor dependency) {
        Cursor dependencies = dependency.getParentTreeCursor();
        Cursor owner = dependencies == null ? null : dependencies.getParentTreeCursor();
        if (owner != null && owner.getValue() instanceof Xml.Tag ownerTag &&
            "dependencyManagement".equals(ownerTag.getName())) owner = owner.getParentTreeCursor();
        return owner != null && UpgradeSelectedIcu4jDependency.isProfileOwner(owner)
                ? (Xml.Tag) owner.getValue() : null;
    }

    private static boolean belowJava8(String raw) {
        try {
            String value = raw.trim();
            double parsed = Double.parseDouble(value);
            return value.startsWith("1.") ? parsed < 1.8 : parsed < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length < 2 || !UpgradeSelectedIcu4jDependency.GROUP.equals(parts[0]) ||
            !(UpgradeSelectedIcu4jDependency.ARTIFACT.equals(parts[1]) || COMPANIONS.contains(parts[1]))) {
            return literal;
        }
        if (parts.length > 3) return mark(literal, VARIANT_MESSAGE);
        if (parts.length != 3 || !FIXED_VERSION.matcher(parts[2]).matches()) {
            return mark(literal, EXTERNAL_MESSAGE);
        }
        String message = mapMessage(parts[0], parts[1], parts[2], false);
        return message == null ? literal : mark(literal, message);
    }

    private static String mapMessage(String group, String artifact, String version, boolean variant) {
        if (!UpgradeSelectedIcu4jDependency.GROUP.equals(group) ||
            !(UpgradeSelectedIcu4jDependency.ARTIFACT.equals(artifact) || COMPANIONS.contains(artifact))) return null;
        if (variant) return VARIANT_MESSAGE;
        if (version == null || !FIXED_VERSION.matcher(version).matches()) return EXTERNAL_MESSAGE;
        if (COMPANIONS.contains(artifact)) {
            return UpgradeSelectedIcu4jDependency.TARGET.equals(version) ? null : COMPANION_MESSAGE;
        }
        return UpgradeSelectedIcu4jDependency.TARGET.equals(version) ||
               UpgradeSelectedIcu4jDependency.SOURCE_VERSIONS.contains(version) ? null : OUTSIDE_MESSAGE;
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(UpgradeSelectedIcu4jDependency.mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(UpgradeSelectedIcu4jDependency.mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static Xml.Tag markChild(Xml.Tag owner, String name, String message) {
        return owner.getChild(name).map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
