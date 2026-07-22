package com.huawei.clouds.openrewrite.feigncore;

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

import java.util.Set;
import java.util.regex.Pattern;

/** Marks exact Feign Core dependency ownership and duplicate-classpath risks left after deterministic upgrades. */
public final class FindFeign13BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    private static final String ALIGNMENT_MESSAGE =
            "This Feign companion module is not aligned to 13.6; choose an actually published compatible module version " +
            "and verify the resolved Feign family classpath before deployment";
    private static final String OWNER_MESSAGE =
            "This Feign Core version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, or outside the workbook selection; migrate the actual version owner deliberately and verify that 13.6 is resolved";
    private static final String VERSION_MESSAGE =
            "This fixed Feign Core version is outside the workbook source set and target; it is intentionally not auto-upgraded, so choose its migration path explicitly";
    private static final String VARIANT_MESSAGE =
            "This classified or non-JAR Feign Core artifact is outside deterministic upgrade scope; verify that 13.6 publishes the required artifact shape before changing it";
    private static final String JAVA_MESSAGE =
            "Feign Core 13.6 requires Java 8 or newer; update this explicit compiler baseline before resolving the target artifact";

    @Override
    public String getDisplayName() {
        return "Find Feign Core 13.6 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark only owned Maven/Gradle Feign Core declarations with unresolved ownership, Feign-family " +
               "alignment risk, nonstandard variants, fixed out-of-scope versions, or an explicit pre-Java-8 baseline.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || UpgradeSelectedFeignCoreDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            if (UpgradeSelectedFeignCoreDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindFeign13BuildRisks::preJava8).isPresent()) {
                                return mark(t, JAVA_MESSAGE);
                            }
                            if (!UpgradeSelectedFeignCoreDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            if (UpgradeSelectedFeignCoreDependency.GROUP.equals(group) &&
                                artifact.startsWith("feign-") &&
                                !UpgradeSelectedFeignCoreDependency.ARTIFACT.equals(artifact) &&
                                !UpgradeSelectedFeignCoreDependency.TARGET.equals(version)) {
                                return t.getChild("version").isPresent() ? markChild(t, "version", ALIGNMENT_MESSAGE)
                                        : mark(t, ALIGNMENT_MESSAGE);
                            }
                            if (!UpgradeSelectedFeignCoreDependency.GROUP.equals(group) ||
                                !UpgradeSelectedFeignCoreDependency.ARTIFACT.equals(artifact)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT_MESSAGE);
                            if (!FIXED.matcher(version).matches()) {
                                return t.getChild("version").isPresent() ? markChild(t, "version", OWNER_MESSAGE)
                                        : mark(t, OWNER_MESSAGE);
                            }
                            return UpgradeSelectedFeignCoreDependency.TARGET.equals(version) ? t
                                    : markChild(t, "version", VERSION_MESSAGE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean dependency = UpgradeSelectedFeignCoreDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!dependency) return m;
                            String message = mapMessage(m);
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                message = map == null ? null : mapMessage(map);
                            }
                            return message == null ? m : mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = UpgradeSelectedFeignCoreDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = UpgradeSelectedFeignCoreDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String coordinateMessage(Object literal) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2 || !UpgradeSelectedFeignCoreDependency.GROUP.equals(parts[0]) ||
            !parts[1].startsWith("feign-")) return null;
        if (!UpgradeSelectedFeignCoreDependency.ARTIFACT.equals(parts[1])) {
            return parts.length == 3 && UpgradeSelectedFeignCoreDependency.TARGET.equals(parts[2])
                    ? null : ALIGNMENT_MESSAGE;
        }
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT_MESSAGE;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return OWNER_MESSAGE;
        return UpgradeSelectedFeignCoreDependency.TARGET.equals(parts[2]) ? null : VERSION_MESSAGE;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        String group = mapValue(invocation, "group");
        String artifact = mapValue(invocation, "name");
        String version = mapValue(invocation, "version");
        boolean variant = hasVariant(invocation);
        return dependencyMessage(group, artifact, version, variant);
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(mapValue(map, "group"), mapValue(map, "name"), mapValue(map, "version"),
                hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (!UpgradeSelectedFeignCoreDependency.GROUP.equals(group) || artifact == null ||
            !artifact.startsWith("feign-")) return null;
        if (!UpgradeSelectedFeignCoreDependency.ARTIFACT.equals(artifact)) {
            return UpgradeSelectedFeignCoreDependency.TARGET.equals(version) ? null : ALIGNMENT_MESSAGE;
        }
        if (variant) return VARIANT_MESSAGE;
        if (version == null || !FIXED.matcher(version).matches()) return OWNER_MESSAGE;
        return UpgradeSelectedFeignCoreDependency.TARGET.equals(version) ? null : VERSION_MESSAGE;
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
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
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean preJava8(String value) {
        return value.matches("1\\.[0-7]") || value.matches("[1-7]");
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
