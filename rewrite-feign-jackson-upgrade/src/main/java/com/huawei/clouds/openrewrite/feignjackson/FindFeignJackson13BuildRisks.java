package com.huawei.clouds.openrewrite.feignjackson;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find build declarations that require a version owner, variant, or classpath decision. */
public final class FindFeignJackson13BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    private static final String OWNER =
            "This Feign Jackson version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, or outside " +
            "the workbook selection; migrate the actual owner deliberately and verify that 13.6 resolves";
    private static final String OUTSIDE =
            "This fixed Feign Jackson version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration path explicitly";
    private static final String VARIANT =
            "This classified or non-JAR Feign Jackson artifact is outside deterministic scope; verify that 13.6 " +
            "publishes the required artifact shape before changing it";
    private static final String FEIGN_ALIGNMENT =
            "This directly declared Feign companion is not aligned to 13.6; verify dependency mediation and converge " +
            "all Feign API/runtime modules to a binary-compatible, actually published version";
    private static final String JACKSON_ALIGNMENT =
            "Feign Jackson 13.6 is built and tested with Jackson 2.18.3; this direct Jackson module may override that " +
            "line, so converge the Jackson BOM/modules deliberately and run codec/security regression tests";
    private static final String JAVA =
            "Feign Jackson 13.6 requires Java 8 or newer; update this explicit compiler baseline before resolving it";

    @Override
    public String getDisplayName() {
        return "Find Feign Jackson 13.6 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and root Gradle nodes with unresolved Feign Jackson versions, nonstandard " +
               "variants, Feign-family/Jackson alignment risks, or a pre-Java-8 baseline.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedFeignJacksonDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (UpgradeSelectedFeignJacksonDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindFeignJackson13BuildRisks::preJava8).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (!UpgradeSelectedFeignJacksonDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            if (UpgradeSelectedFeignJacksonDependency.GROUP.equals(group) &&
                                artifact.startsWith("feign-") &&
                                !UpgradeSelectedFeignJacksonDependency.ARTIFACT.equals(artifact)) {
                                return resolvesTo(version, getCursor(), properties,
                                        UpgradeSelectedFeignJacksonDependency.TARGET) ? t :
                                        markVersionOrOwner(t, FEIGN_ALIGNMENT);
                            }
                            if (jacksonModule(group, artifact)) {
                                return resolvesTo(version, getCursor(), properties, "2.18.3") ? t :
                                        markVersionOrOwner(t, JACKSON_ALIGNMENT);
                            }
                            if (!UpgradeSelectedFeignJacksonDependency.GROUP.equals(group) ||
                                !UpgradeSelectedFeignJacksonDependency.ARTIFACT.equals(artifact)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            if (resolvesTo(version, getCursor(), properties,
                                    UpgradeSelectedFeignJacksonDependency.TARGET)) return t;
                            if (!FIXED.matcher(version).matches()) return markVersionOrOwner(t, OWNER);
                            return UpgradeSelectedFeignJacksonDependency.TARGET.equals(version) ? t :
                                    markVersionOrOwner(t, OUTSIDE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignJacksonDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!dependency) return m;
                            m = markDynamicTemplateArgument(m);
                            String message = mapMessage(m);
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                message = map == null ? null : mapMessage(map);
                            }
                            return message == null ? m : mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignJacksonDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignJacksonDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignJacksonDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
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
        if (parts.length < 2) return null;
        if (UpgradeSelectedFeignJacksonDependency.GROUP.equals(parts[0]) && parts[1].startsWith("feign-") &&
            !UpgradeSelectedFeignJacksonDependency.ARTIFACT.equals(parts[1])) {
            return parts.length == 3 && UpgradeSelectedFeignJacksonDependency.TARGET.equals(parts[2])
                    ? null : FEIGN_ALIGNMENT;
        }
        if (jacksonModule(parts[0], parts[1])) {
            return parts.length == 3 && "2.18.3".equals(parts[2]) ? null : JACKSON_ALIGNMENT;
        }
        if (!UpgradeSelectedFeignJacksonDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedFeignJacksonDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return OWNER;
        return UpgradeSelectedFeignJacksonDependency.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        return dependencyMessage(UpgradeSelectedFeignJacksonDependency.mapValue(invocation, "group"),
                UpgradeSelectedFeignJacksonDependency.mapValue(invocation, "name"),
                UpgradeSelectedFeignJacksonDependency.mapValue(invocation, "version"),
                UpgradeSelectedFeignJacksonDependency.hasVariant(invocation));
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(UpgradeSelectedFeignJacksonDependency.mapValue(map, "group"),
                UpgradeSelectedFeignJacksonDependency.mapValue(map, "name"),
                UpgradeSelectedFeignJacksonDependency.mapValue(map, "version"),
                UpgradeSelectedFeignJacksonDependency.hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (UpgradeSelectedFeignJacksonDependency.GROUP.equals(group) && artifact != null &&
            artifact.startsWith("feign-") && !UpgradeSelectedFeignJacksonDependency.ARTIFACT.equals(artifact)) {
            return UpgradeSelectedFeignJacksonDependency.TARGET.equals(version) ? null : FEIGN_ALIGNMENT;
        }
        if (jacksonModule(group, artifact)) return "2.18.3".equals(version) ? null : JACKSON_ALIGNMENT;
        if (!UpgradeSelectedFeignJacksonDependency.GROUP.equals(group) ||
            !UpgradeSelectedFeignJacksonDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches()) return OWNER;
        return UpgradeSelectedFeignJacksonDependency.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static boolean jacksonModule(String group, String artifact) {
        return group != null && artifact != null && artifact.startsWith("jackson-") &&
               (group.equals("com.fasterxml.jackson.core") || group.equals("com.fasterxml.jackson.datatype") ||
                group.equals("com.fasterxml.jackson.module") || group.equals("com.fasterxml.jackson.dataformat"));
    }

    private static boolean preJava8(String value) {
        return value.matches("1\\.[0-7]") || value.matches("[1-7]");
    }

    private static ScopedProperties scopedProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedFeignJacksonDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new ScopedProperties(counts, values);
    }

    private static boolean resolvesTo(String version, org.openrewrite.Cursor cursor,
                                      ScopedProperties properties, String expected) {
        if (expected.equals(version)) return true;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return false;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        return properties.counts().getOrDefault(owner, 0) == 1 &&
               expected.equals(properties.values().get(owner));
    }

    private static String scope(org.openrewrite.Cursor cursor) {
        for (org.openrewrite.Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private record PropertyKey(String scope, String name) {
    }

    private record ScopedProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }

    private static J.MethodInvocation markDynamicTemplateArgument(J.MethodInvocation invocation) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument ->
                templateMentionsTarget(argument) ? mark(argument, OWNER) : argument).toList());
    }

    private static boolean templateMentionsTarget(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.contains(UpgradeSelectedFeignJacksonDependency.GROUP + ":" +
                                                  UpgradeSelectedFeignJacksonDependency.ARTIFACT + ":"));
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
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
