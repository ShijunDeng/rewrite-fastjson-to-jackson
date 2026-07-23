package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Marks build and packaging decisions that can hide or misresolve the JPEG ImageIO service provider. */
public final class FindTwelveMonkeysJpegBuildRisks extends Recipe {
    private static final Set<String> COMPANIONS = Set.of(
            "com.twelvemonkeys.imageio:imageio-core", "com.twelvemonkeys.imageio:imageio-metadata",
            "com.twelvemonkeys.common:common-lang", "com.twelvemonkeys.common:common-io",
            "com.twelvemonkeys.common:common-image");

    @Override
    public String getDisplayName() {
        return "Find TwelveMonkeys JPEG 3.12 build and runtime risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved dependency ownership, mixed TwelveMonkeys versions, service/MR-JAR stripping, shading, OSGi service-loader capabilities, native images, and custom class-loader packaging.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || TwelveMonkeysJpegSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        boolean[] rootTarget = {false};
        Set<UUID> profileTargets = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TwelveMonkeysJpegSupport.isJpegDependency(getCursor(), visited) && TwelveMonkeysJpegSupport.standardJar(visited)) {
                    UUID profile = profileId(getCursor());
                    if (profile == null) rootTarget[0] = true;
                    else profileTargets.add(profile);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                UUID profile = profileId(getCursor());
                boolean visible = profile == null ? rootTarget[0] || !profileTargets.isEmpty() :
                        rootTarget[0] || profileTargets.contains(profile);

                if (TwelveMonkeysJpegSupport.isJpegDependency(getCursor(), visited)) {
                    if (!TwelveMonkeysJpegSupport.standardJar(visited)) return SearchResult.found(visited,
                            "Classifier/type variants are outside the workbook's ordinary imageio-jpeg JAR and require an explicit provider/resource decision");
                    String version = visited.getChildValue("version").orElse("").trim();
                    if (version.isEmpty()) return SearchResult.found(visited,
                            "imageio-jpeg is parent/BOM managed; upgrade the actual owner to 3.12.0 and verify the complete resolved TwelveMonkeys family");
                    if (version.contains("${")) return SearchResult.found(visited,
                            "imageio-jpeg uses property ownership; if the property is shared outside the TwelveMonkeys family, update its real owner manually and inspect dependency convergence");
                    if (!TwelveMonkeysJpegSupport.SOURCE.equals(version) && !TwelveMonkeysJpegSupport.TARGET.equals(version)) {
                        return SearchResult.found(visited,
                                "This fixed imageio-jpeg version is outside the workbook's 3.9.3 source selection; determine its supported migration path instead of widening AUTO");
                    }
                    return visited;
                }

                if ("dependency".equals(visited.getName()) && TwelveMonkeysJpegSupport.isProjectDependency(getCursor(), visited) &&
                    TwelveMonkeysJpegSupport.standardJar(visited) && targetSameOwner(profile, rootTarget[0], profileTargets)) {
                    String coordinate = visited.getChildValue("groupId").orElse("") + ":" + visited.getChildValue("artifactId").orElse("");
                    if (COMPANIONS.contains(coordinate)) {
                        String version = visited.getChildValue("version").orElse("").trim();
                        if (!version.isEmpty() && !TwelveMonkeysJpegSupport.TARGET.equals(version) && !TwelveMonkeysJpegSupport.SOURCE.equals(version) && !version.contains("${")) {
                            return SearchResult.found(visited,
                                    "Mixed explicit TwelveMonkeys versions can cause linkage/provider failures; converge imageio-core, imageio-metadata, and common modules with imageio-jpeg 3.12.0");
                        }
                    }
                }

                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) && visible) {
                    String group = visited.getChildValue("groupId").orElse("");
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    String printed = visited.printTrimmed(getCursor());
                    if ("org.apache.maven.plugins".equals(group) && "maven-shade-plugin".equals(artifact) &&
                        !printed.contains("ServicesResourceTransformer")) {
                        return SearchResult.found(visited,
                                "Shaded JPEG plugin has no ServicesResourceTransformer; preserve/merge META-INF/services reader and writer SPI files plus META-INF/versions module metadata, then assert runtime provider order");
                    }
                    if (("org.apache.felix".equals(group) && "maven-bundle-plugin".equals(artifact)) || artifact.contains("bnd")) {
                        return SearchResult.found(visited,
                                "OSGi packaging detected: 3.10+ publishes osgi.serviceloader reader/writer capabilities; preserve Provide-/Require-Capability and validate registrar-mediated ImageIO discovery");
                    }
                    if (("org.graalvm.buildtools".equals(group) && "native-maven-plugin".equals(artifact)) || printed.contains("native-image")) {
                        return SearchResult.found(visited,
                                "Native-image packaging detected: include ImageReaderSpi/ImageWriterSpi service metadata, implementation constructors, desktop/ImageIO reachability, ICC profiles, and representative JPEG resources");
                    }
                    if ("org.springframework.boot".equals(group) && "spring-boot-maven-plugin".equals(artifact)) {
                        return SearchResult.found(visited,
                                "Executable/nested JAR packaging detected: verify the runtime class loader exposes both ImageIO service descriptors and the multi-release module descriptor before image APIs initialize");
                    }
                }

                if (visible && ("exclude".equals(visited.getName()) || "filter".equals(visited.getName()))) {
                    String printed = visited.printTrimmed(getCursor());
                    if (printed.contains("META-INF/services") || printed.contains("META-INF/versions")) {
                        return SearchResult.found(visited,
                                "This resource rule can remove ImageIO SPI or MR-JAR metadata required by imageio-jpeg 3.12.0; keep service descriptors and validate the packaged artifact, not only the test classpath");
                    }
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean owns = ownsGroovy(source, ctx);
        if (!owns) return source;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                return markGradleRisk(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean owns = ownsKotlin(source, ctx);
        if (!owns) return source;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                return markGradleRisk(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static J.MethodInvocation markGradleRisk(J.MethodInvocation method, Cursor cursor) {
        String printed = method.printTrimmed(cursor);
        if (("exclude".equals(method.getSimpleName()) || "filter".equals(method.getSimpleName())) &&
            (printed.contains("META-INF/services") || printed.contains("META-INF/versions"))) {
            return SearchResult.found(method,
                    "Do not strip ImageIO service descriptors or multi-release metadata from the 3.12.0 runtime artifact; inspect the built JAR and provider order");
        }
        if (("shadowJar".equals(method.getSimpleName()) || printed.contains("com.github.johnrengelman.shadow")) &&
            !printed.contains("mergeServiceFiles")) {
            return SearchResult.found(method,
                    "Shadow packaging detected; merge service files and preserve META-INF/versions so the TwelveMonkeys JPEG reader/writer remains discoverable");
        }
        if (printed.contains("nativeImage") || printed.contains("graalvmNative") || printed.contains("org.graalvm.buildtools.native")) {
            return SearchResult.found(method,
                    "Native-image packaging detected; retain ImageIO SPI services, constructors, java.desktop reachability, ICC data, and JPEG test corpus");
        }
        if (!TwelveMonkeysJpegSupport.isGradleDependencyInvocation(cursor, method)) return method;
        String coordinate = literalCoordinate(method);
        if (coordinate != null && coordinate.startsWith(TwelveMonkeysJpegSupport.GROUP + ":" + TwelveMonkeysJpegSupport.ARTIFACT + ":")) {
            String version = coordinate.substring((TwelveMonkeysJpegSupport.GROUP + ":" + TwelveMonkeysJpegSupport.ARTIFACT + ":").length());
            if (!TwelveMonkeysJpegSupport.SOURCE.equals(version) && !TwelveMonkeysJpegSupport.TARGET.equals(version)) {
                return SearchResult.found(method,
                        "This imageio-jpeg version/selector is outside the workbook's exact 3.9.3 source; resolve catalogs/platforms or choose its own migration path");
            }
        }
        return method;
    }

    private static String literalCoordinate(J.MethodInvocation method) {
        if (method.getArguments().isEmpty() || !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return null;
        return value;
    }

    private static boolean ownsGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (targetGradle(method, getCursor())) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean ownsKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (targetGradle(method, getCursor())) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean targetGradle(J.MethodInvocation method, Cursor cursor) {
        if (!TwelveMonkeysJpegSupport.isGradleDependencyInvocation(cursor, method)) return false;
        String coordinate = literalCoordinate(method);
        if (coordinate != null && coordinate.startsWith(TwelveMonkeysJpegSupport.GROUP + ":" + TwelveMonkeysJpegSupport.ARTIFACT + ":") &&
            !coordinate.substring((TwelveMonkeysJpegSupport.GROUP + ":" + TwelveMonkeysJpegSupport.ARTIFACT + ":").length()).contains(":")) return true;
        return TwelveMonkeysJpegSupport.GROUP.equals(TwelveMonkeysJpegSupport.mapValue(method, "group")) &&
               TwelveMonkeysJpegSupport.ARTIFACT.equals(TwelveMonkeysJpegSupport.mapValue(method, "name")) &&
               !TwelveMonkeysJpegSupport.hasVariant(method);
    }

    private static boolean targetSameOwner(UUID profile, boolean rootTarget, Set<UUID> profileTargets) {
        return profile == null ? rootTarget : profileTargets.contains(profile);
    }

    private static UUID profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId();
            if (current.getValue() instanceof Xml.Document) return null;
        }
        return null;
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        if (!(build.getValue() instanceof Xml.Tag buildTag) || !"build".equals(buildTag.getName())) return false;
        Cursor owner = build.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName()) &&
            owner.getParentTreeCursor().getValue() instanceof Xml.Document) return true;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag root && "project".equals(root.getName());
    }
}
