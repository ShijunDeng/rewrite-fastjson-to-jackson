package com.huawei.clouds.openrewrite.nettycodechttp;

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

/** Locate version ownership, branch-conflict, family alignment and packaging risks. */
public final class FindNettyCodecHttp41136BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    static final String NO_DOWNGRADE =
            "4.2.10.Final is on Netty's newer 4.2 branch while the workbook target is 4.1.136.Final; automatic " +
            "downgrade is forbidden, so this declaration is intentionally unchanged and needs an explicit target decision";
    static final String OWNER =
            "This netty-codec-http version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared " +
            "or externally owned; migrate the real owner and verify 4.1.136.Final resolves without a downgrade";
    static final String OUTSIDE =
            "This fixed netty-codec-http version is outside the approved 4.1.x source set and target; choose its " +
            "support path explicitly instead of widening the automatic recipe";
    static final String VARIANT =
            "This classified or non-JAR netty-codec-http artifact is outside deterministic scope; verify the required " +
            "4.1.136.Final artifact shape before changing it";
    static final String FAMILY =
            "Netty modules share internal and binary contracts; align this io.netty companion with " +
            "netty-codec-http 4.1.136.Final through netty-bom and verify the resolved dependency graph";
    static final String PACKAGING =
            "Shading or relocating io.netty can break service loading, native transports and handler identity; verify " +
            "relocation rules, META-INF/services, native classifiers and a packaged HTTP client/server smoke test";
    static final String RFC9112 =
            "This launch/build setting disables Netty RFC 9112 Transfer-Encoding enforcement and can re-enable " +
            "request smuggling; remove it or document the trusted boundary and test rejection of TE+CL requests";
    private static final String RFC9112_PROPERTY = "io.netty.handler.codec.http.rfc9112TransferEncoding";

    @Override
    public String getDisplayName() {
        return "Find Netty HTTP codec 4.1.136 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark the 4.2 source/4.1 target conflict, unresolved or outside versions, variants, Netty-family skew " +
               "and packaging integrations; conflicting 4.2 sources remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || NettyCodecHttpSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Scopes scopes = scopes(source, ctx);
        if (scopes.empty()) return source;
        Properties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), scopes)) return visited;
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor())) {
                    String text = visited.printTrimmed(getCursor());
                    if ("maven-shade-plugin".equals(visited.getChildValue("artifactId").orElse("")) &&
                        text.contains("io.netty")) return mark(visited, PACKAGING);
                }
                if (visited.getValue().filter(value -> value.contains(RFC9112_PROPERTY)).isPresent() &&
                    (NettyCodecHttpSupport.isMavenPropertyDefinition(getCursor(), visited) ||
                     insideProjectBuildPlugin(getCursor()))) {
                    return mark(visited, RFC9112);
                }
                if (!NettyCodecHttpSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String resolved = resolve(raw, getCursor(), properties);
                if (NettyCodecHttpSupport.GROUP.equals(group) && NettyCodecHttpSupport.ARTIFACT.equals(artifact)) {
                    if (!NettyCodecHttpSupport.standardJar(visited)) return mark(visited, VARIANT);
                    String message = primaryMessage(resolved);
                    return message == null ? visited : markVersion(visited, message);
                }
                if (nettyCompanion(group, artifact) && !NettyCodecHttpSupport.TARGET.equals(resolved)) {
                    return markVersion(visited, FAMILY);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = NettyCodecHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if ("relocate".equals(visited.getSimpleName()) && topLevelOwner(getCursor(), "shadowJar") &&
                    visited.getArguments().stream().anyMatch(FindNettyCodecHttp41136BuildRisks::mentionsNettyLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (!dependency) return visited;
                String group = NettyCodecHttpSupport.mapValue(visited, "group");
                String artifact = NettyCodecHttpSupport.mapValue(visited, "name");
                String version = NettyCodecHttpSupport.mapValue(visited, "version");
                boolean variant = NettyCodecHttpSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = NettyCodecHttpSupport.mapValue(map, "group");
                    artifact = NettyCodecHttpSupport.mapValue(map, "name");
                    version = NettyCodecHttpSupport.mapValue(map, "version");
                    variant = NettyCodecHttpSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                return visited.getArguments().stream()
                        .anyMatch(FindNettyCodecHttp41136BuildRisks::dynamicPrimary)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = NettyCodecHttpSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (visited.getValue() instanceof String value && value.contains(RFC9112_PROPERTY)) {
                    return mark(visited, RFC9112);
                }
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = NettyCodecHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return dependency && visited.getArguments().stream()
                        .anyMatch(FindNettyCodecHttp41136BuildRisks::dynamicPrimary)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = NettyCodecHttpSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (visited.getValue() instanceof String value && value.contains(RFC9112_PROPERTY)) {
                    return mark(visited, RFC9112);
                }
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (NettyCodecHttpSupport.GROUP.equals(group) && NettyCodecHttpSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            return primaryMessage(version);
        }
        return nettyCompanion(group, artifact) && !NettyCodecHttpSupport.TARGET.equals(version) ? FAMILY : null;
    }

    private static String primaryMessage(String version) {
        if (NettyCodecHttpSupport.TARGET.equals(version)) return null;
        if (version != null && NettyCodecHttpSupport.AUTO_SOURCES.contains(version)) return null;
        if (NettyCodecHttpSupport.DOWNGRADE_CONFLICT.equals(version)) return NO_DOWNGRADE;
        return version == null || !FIXED.matcher(version).matches() ? OWNER : OUTSIDE;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : null;
        if (NettyCodecHttpSupport.GROUP.equals(group) && NettyCodecHttpSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || version != null && version.contains("@")) return VARIANT;
            return primaryMessage(version);
        }
        return nettyCompanion(group, artifact) && !NettyCodecHttpSupport.TARGET.equals(version) ? FAMILY : null;
    }

    private static boolean nettyCompanion(String group, String artifact) {
        return NettyCodecHttpSupport.GROUP.equals(group) && artifact != null && artifact.startsWith("netty-") &&
               !NettyCodecHttpSupport.ARTIFACT.equals(artifact) && !artifact.startsWith("netty-tcnative");
    }

    private static Scopes scopes(Xml.Document source, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (NettyCodecHttpSupport.isCodecHttpDependency(getCursor(), visited)) {
                    String owner = scope(getCursor());
                    if ("ROOT".equals(owner)) root[0] = true;
                    else profiles.add(owner);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new Scopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, Scopes scopes) {
        String owner = scope(cursor);
        if ("ROOT".equals(owner)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(owner);
    }

    private static Properties properties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (NettyCodecHttpSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        Map<PropertyKey, Integer> references = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), counts, references);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), counts, references);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new Properties(counts, values, references, profileNames);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
                                          Map<PropertyKey, Integer> references) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            String currentScope = scope(cursor);
            PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
            PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
            PropertyKey owner = !"ROOT".equals(currentScope) && definitions.containsKey(local) ? local : root;
            references.merge(owner, 1, Integer::sum);
        }
    }

    private static String resolve(String raw, Cursor cursor, Properties properties) {
        if (FIXED.matcher(raw).matches()) return raw;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return null;
        String currentScope = scope(cursor);
        PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(currentScope) && properties.counts().containsKey(local) ? local : root;
        if (properties.counts().getOrDefault(owner, 0) != 1 ||
            properties.references().getOrDefault(owner, 0) != 1 ||
            "ROOT".equals(owner.scope()) && properties.profileNames().contains(owner.name())) return null;
        return properties.values().get(owner);
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private static boolean hasPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = NettyCodecHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasPrimary(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = NettyCodecHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (NettyCodecHttpSupport.GROUP.equals(NettyCodecHttpSupport.mapValue(method, "group")) &&
            NettyCodecHttpSupport.ARTIFACT.equals(NettyCodecHttpSupport.mapValue(method, "name"))) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                NettyCodecHttpSupport.GROUP.equals(NettyCodecHttpSupport.mapValue(map, "group")) &&
                NettyCodecHttpSupport.ARTIFACT.equals(NettyCodecHttpSupport.mapValue(map, "name"))) return true;
            if (dynamicPrimary(argument)) return true;
        }
        return false;
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(NettyCodecHttpSupport.GROUP + ":" + NettyCodecHttpSupport.ARTIFACT) ||
                coordinate.startsWith(NettyCodecHttpSupport.GROUP + ":" + NettyCodecHttpSupport.ARTIFACT + ":"));
    }

    private static boolean dynamicPrimary(J expression) {
        java.util.List<J> parts;
        if (expression instanceof G.GString string) parts = string.getStrings();
        else if (expression instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().map(String::stripLeading)
                .map(value -> value.startsWith(NettyCodecHttpSupport.GROUP + ":" +
                                               NettyCodecHttpSupport.ARTIFACT + ":"))
                .orElse(false);
    }

    private static boolean mentionsNettyLiteral(J expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value &&
               (value.equals("io.netty") || value.startsWith("io.netty."));
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        if (!(build.getValue() instanceof Xml.Tag b) || !"build".equals(b.getName())) return false;
        return projectOrProfile(build.getParentTreeCursor());
    }

    private static boolean insideProjectBuildPlugin(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "plugin".equals(tag.getName())) {
                return projectBuildPlugin(current);
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean projectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag container) || !"profiles".equals(container.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean topLevelOwner(Cursor cursor, String name) {
        int count = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation method) {
                count++;
                owner = method.getSimpleName();
            }
        }
        return count == 1 && name.equals(owner);
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = mark(version, message);
            return marked == version ? dependency : dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> mark(dependency, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record Properties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values,
                              Map<PropertyKey, Integer> references, Set<String> profileNames) {
    }

    private record Scopes(boolean root, Set<String> profiles) {
        private boolean empty() {
            return !root && profiles.isEmpty();
        }
    }
}
