package com.huawei.clouds.openrewrite.disruptor;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find build declarations that require an owner, artifact-shape, or Java-baseline decision. */
public final class FindDisruptor4BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    static final String OWNER =
            "This Disruptor version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; migrate the actual owner deliberately and verify that com.lmax:disruptor:4.0.0 resolves";
    static final String OUTSIDE =
            "This fixed Disruptor version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration and security-support path explicitly";
    static final String VARIANT =
            "This classified or non-JAR Disruptor artifact is outside deterministic scope; verify that 4.0.0 publishes " +
            "the required artifact shape before changing it";
    static final String JAVA =
            "Disruptor 4.0.0 requires Java 11 or newer; update this explicit compiler baseline before resolving it";

    @Override
    public String getDisplayName() {
        return "Find Disruptor 4.0.0 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and root Gradle nodes with unresolved/outside Disruptor versions, variants, " +
               "or a pre-Java-11 compiler baseline.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedDisruptorDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    MavenClassicScopes classicScopes = classicScopes(document, ctx);
                    if (classicScopes.isEmpty()) return tree;
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (!classicVisible(getCursor(), classicScopes)) return t;
                            if (UpgradeSelectedDisruptorDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindDisruptor4BuildRisks::preJava11).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (!UpgradeSelectedDisruptorDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            if (!UpgradeSelectedDisruptorDependency.GROUP.equals(group) ||
                                !UpgradeSelectedDisruptorDependency.ARTIFACT.equals(artifact)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            String resolved = resolve(version, getCursor(), properties);
                            if (UpgradeSelectedDisruptorDependency.TARGET.equals(resolved)) return t;
                            if (resolved == null || UpgradeSelectedDisruptorDependency.SOURCE_VERSIONS.contains(resolved) ||
                                !FIXED.matcher(resolved).matches()) return markVersionOrOwner(t, OWNER);
                            return markVersionOrOwner(t, OUTSIDE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    if (!containsClassic(groovy, ctx)) return tree;
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedDisruptorDependency
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
                            boolean dependency = UpgradeSelectedDisruptorDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    if (!containsClassic(kotlin, ctx)) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedDisruptorDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedDisruptorDependency.isDirectDependencyLiteral(getCursor());
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
        if (!UpgradeSelectedDisruptorDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedDisruptorDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches() ||
            UpgradeSelectedDisruptorDependency.SOURCE_VERSIONS.contains(parts[2])) return OWNER;
        return UpgradeSelectedDisruptorDependency.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        return dependencyMessage(UpgradeSelectedDisruptorDependency.mapValue(invocation, "group"),
                UpgradeSelectedDisruptorDependency.mapValue(invocation, "name"),
                UpgradeSelectedDisruptorDependency.mapValue(invocation, "version"),
                UpgradeSelectedDisruptorDependency.hasVariant(invocation));
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(UpgradeSelectedDisruptorDependency.mapValue(map, "group"),
                UpgradeSelectedDisruptorDependency.mapValue(map, "name"),
                UpgradeSelectedDisruptorDependency.mapValue(map, "version"),
                UpgradeSelectedDisruptorDependency.hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (!UpgradeSelectedDisruptorDependency.GROUP.equals(group) ||
            !UpgradeSelectedDisruptorDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches() ||
            UpgradeSelectedDisruptorDependency.SOURCE_VERSIONS.contains(version)) return OWNER;
        return UpgradeSelectedDisruptorDependency.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static MavenClassicScopes classicScopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedDisruptorDependency.isProjectDependency(getCursor(), t) &&
                    UpgradeSelectedDisruptorDependency.GROUP.equals(t.getChildValue("groupId").orElse(null)) &&
                    UpgradeSelectedDisruptorDependency.ARTIFACT.equals(t.getChildValue("artifactId").orElse(null))) {
                    String owner = scope(getCursor());
                    if ("ROOT".equals(owner)) root[0] = true;
                    else profiles.add(owner);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenClassicScopes(root[0], Set.copyOf(profiles));
    }

    /**
     * A root declaration participates in every profile. A profile declaration participates only in that profile,
     * while root build settings still affect a build that activates the profile.
     */
    private static boolean classicVisible(Cursor cursor, MavenClassicScopes scopes) {
        String owner = scope(cursor);
        if ("ROOT".equals(owner)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(owner);
    }

    private static boolean containsClassic(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedDisruptorDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && classicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsClassic(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedDisruptorDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && classicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean classicInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedDisruptorDependency.GROUP.equals(
                    UpgradeSelectedDisruptorDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedDisruptorDependency.ARTIFACT.equals(
                    UpgradeSelectedDisruptorDependency.mapValue(invocation, "name"))) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && classicCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedDisruptorDependency.GROUP.equals(
                    UpgradeSelectedDisruptorDependency.mapValue(map, "group")) &&
                UpgradeSelectedDisruptorDependency.ARTIFACT.equals(
                    UpgradeSelectedDisruptorDependency.mapValue(map, "name"))) return true;
            if (templateMentionsClassic(argument)) return true;
        }
        return false;
    }

    private static boolean classicCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedDisruptorDependency.GROUP + ":" +
                        UpgradeSelectedDisruptorDependency.ARTIFACT;
        return prefix.equals(coordinate) || coordinate.startsWith(prefix + ":");
    }

    private static ScopedProperties scopedProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedDisruptorDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new ScopedProperties(counts, values);
    }

    private static String resolve(String version, Cursor cursor, ScopedProperties properties) {
        if (FIXED.matcher(version).matches()) return version;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return null;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        return properties.counts().getOrDefault(owner, 0) == 1 ? properties.values().get(owner) : null;
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private record PropertyKey(String scope, String name) {
    }

    private record ScopedProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }

    private record MavenClassicScopes(boolean root, Set<String> profiles) {
        private boolean isEmpty() {
            return !root && profiles.isEmpty();
        }
    }

    private static boolean preJava11(String value) {
        return value.matches("1\\.[0-9]") || value.matches("(?:[1-9]|10)");
    }

    private static J.MethodInvocation markDynamicTemplateArgument(J.MethodInvocation invocation) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument ->
                templateMentionsClassic(argument) ? mark(argument, OWNER) : argument).toList());
    }

    private static boolean templateMentionsClassic(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.contains(UpgradeSelectedDisruptorDependency.GROUP + ":" +
                                                  UpgradeSelectedDisruptorDependency.ARTIFACT + ":"));
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
