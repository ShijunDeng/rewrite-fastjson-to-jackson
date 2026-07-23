package com.huawei.clouds.openrewrite.nettyhandler;

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

/** Locate version ownership, target conflicts, family alignment, TLS and packaging risks. */
public final class FindNettyHandler41136BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    static final String NO_DOWNGRADE_PREFIX = "目标版本冲突（禁止降级）";
    static final String OWNER =
            "This netty-handler version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, " +
            "shared or externally owned; migrate the real owner and verify 4.1.136.Final resolves without violating " +
            "the upgrade-only policy";
    static final String OUTSIDE =
            "This fixed netty-handler version is outside the approved 4.1.x source set and target; choose its " +
            "support path explicitly instead of widening the automatic recipe";
    static final String VARIANT =
            "This classified or non-JAR netty-handler artifact is outside deterministic scope; verify the " +
            "required 4.1.136.Final artifact shape before changing it";
    static final String FAMILY =
            "Netty modules share handler, buffer, transport and binary contracts; align this io.netty companion with " +
            "netty-handler 4.1.136.Final through netty-bom and verify the resolved dependency graph";
    static final String TLS =
            "Netty TLS/ALPN depends on this native or provider integration; verify provider compatibility, native " +
            "classifier loading, cipher/protocol negotiation and fallback on every deployment platform";
    static final String TLS_LAUNCH =
            "This launch setting changes Netty/JDK TLS provider, protocol, named-group, session-cache or delegated-" +
            "task behavior; verify the resolved 4.1.136.Final behavior in the final runtime image";
    static final String PACKAGING =
            "Shading or relocating io.netty can break service loading, native transports, ALPN and handler identity; " +
            "verify relocation rules, META-INF/services, native classifiers and packaged TLS/proxy smoke tests";

    @Override
    public String getDisplayName() {
        return "Find Netty handler 4.1.136 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark every newer source/4.1 target conflict while leaving it unchanged, plus unresolved or " +
               "outside versions, variants, Netty-family skew, TLS/ALPN providers and packaging integrations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || NettyHandlerSupport.generated(source.getSourcePath())) {
                    return tree;
                }
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
        String managedVersion = managedMavenBom(source, scopes, properties, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), scopes)) return visited;
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    "maven-shade-plugin".equals(visited.getChildValue("artifactId").orElse("")) &&
                    mentionsNettyConfiguration(visited)) return mark(visited, PACKAGING);
                if ((tlsLaunchToken(visited.getName()) ||
                     visited.getValue().map(String::trim).filter(
                             FindNettyHandler41136BuildRisks::tlsLaunchToken).isPresent()) &&
                    (NettyHandlerSupport.isMavenPropertyDefinition(getCursor(), visited) ||
                     insideProjectBuildPlugin(getCursor()))) return mark(visited, TLS_LAUNCH);
                if (!NettyHandlerSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String resolved = resolve(raw, getCursor(), properties);
                if (NettyHandlerSupport.GROUP.equals(group) && NettyHandlerSupport.ARTIFACT.equals(artifact)) {
                    if (!NettyHandlerSupport.standardJar(visited)) return mark(visited, VARIANT);
                    String message = primaryMessage(resolved, managedVersion);
                    return message == null ? visited : markVersion(visited, message);
                }
                if (NettyHandlerSupport.isNettyBom(getCursor(), visited)) {
                    String message = ownerMessage(resolved);
                    return message == null ? visited : markVersion(visited, message);
                }
                if (tlsDependency(group, artifact)) return mark(visited, TLS);
                if (nettyCompanion(group, artifact) && !aligned(resolved, managedVersion)) {
                    return markVersion(visited, FAMILY);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        String managedVersion = managedGroovyBom(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if ("relocate".equals(visited.getSimpleName()) && topLevelOwner(getCursor(), "shadowJar") &&
                    visited.getArguments().stream().anyMatch(FindNettyHandler41136BuildRisks::mentionsNettyLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (!dependency) return visited;
                String group = NettyHandlerSupport.mapValue(visited, "group");
                String artifact = NettyHandlerSupport.mapValue(visited, "name");
                String version = NettyHandlerSupport.mapValue(visited, "version");
                boolean variant = NettyHandlerSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = NettyHandlerSupport.mapValue(map, "group");
                    artifact = NettyHandlerSupport.mapValue(map, "name");
                    version = NettyHandlerSupport.mapValue(map, "version");
                    variant = NettyHandlerSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant, managedVersion);
                if (message != null) return mark(visited, message);
                return visited.getArguments().stream().anyMatch(FindNettyHandler41136BuildRisks::dynamicPrimary)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = NettyHandlerSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isRootPlatformLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (visited.getValue() instanceof String value && tlsLaunchToken(value)) {
                    return mark(visited, TLS_LAUNCH);
                }
                String message = direct ? coordinateMessage(visited.getValue(), managedVersion) :
                        platform ? platformMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        String managedVersion = managedKotlinBom(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return dependency && visited.getArguments().stream().anyMatch(FindNettyHandler41136BuildRisks::dynamicPrimary)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = NettyHandlerSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isRootPlatformLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (visited.getValue() instanceof String value && tlsLaunchToken(value)) {
                    return mark(visited, TLS_LAUNCH);
                }
                String message = direct ? coordinateMessage(visited.getValue(), managedVersion) :
                        platform ? platformMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant,
                                            String managedVersion) {
        if (NettyHandlerSupport.GROUP.equals(group) && NettyHandlerSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            return primaryMessage(version, managedVersion);
        }
        if (tlsDependency(group, artifact)) return TLS;
        return nettyCompanion(group, artifact) && !aligned(version, managedVersion) ? FAMILY : null;
    }

    private static String primaryMessage(String version, String managedVersion) {
        if ((version == null || version.isBlank()) && managedVersion != null) {
            return ownerMessage(managedVersion);
        }
        return ownerMessage(version);
    }

    private static String ownerMessage(String version) {
        if (NettyHandlerSupport.TARGET.equals(version) ||
            version != null && NettyHandlerSupport.AUTO_SOURCES.contains(version)) return null;
        if (NettyHandlerSupport.higherThanTarget(version)) return targetConflictMessage(version);
        return version == null || !FIXED.matcher(version).matches() ? OWNER : OUTSIDE;
    }

    static String targetConflictMessage(String version) {
        return "目标版本冲突（禁止降级）: " + version +
               " is newer than the requested 4.1.136.Final target or belongs to a newer Netty branch; this " +
               "declaration is intentionally unchanged and needs an explicit forward-target decision";
    }

    private static String coordinateMessage(Object value, String managedVersion) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : null;
        if (NettyHandlerSupport.GROUP.equals(group) && NettyHandlerSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || version != null && version.contains("@")) return VARIANT;
            return primaryMessage(version, managedVersion);
        }
        if (tlsDependency(group, artifact)) return TLS;
        return nettyCompanion(group, artifact) && !aligned(version, managedVersion) ? FAMILY : null;
    }

    private static String platformMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String prefix = NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.BOM + ":";
        if (!coordinate.startsWith(prefix)) return null;
        return ownerMessage(coordinate.substring(prefix.length()));
    }

    private static boolean aligned(String version, String managedVersion) {
        return NettyHandlerSupport.TARGET.equals(version) ||
               (version == null || version.isBlank()) && NettyHandlerSupport.TARGET.equals(managedVersion);
    }

    private static boolean nettyCompanion(String group, String artifact) {
        return NettyHandlerSupport.GROUP.equals(group) && artifact != null && artifact.startsWith("netty-") &&
               !NettyHandlerSupport.ARTIFACT.equals(artifact) && !artifact.startsWith("netty-tcnative");
    }

    private static boolean tlsDependency(String group, String artifact) {
        return (NettyHandlerSupport.GROUP.equals(group) && artifact != null &&
                artifact.startsWith("netty-tcnative")) ||
               "org.conscrypt".equals(group) || "org.eclipse.jetty.alpn".equals(group);
    }

    private static boolean mentionsNettyConfiguration(Xml.Tag plugin) {
        if (plugin.getChild("configuration").filter(FindNettyHandler41136BuildRisks::configurationMentionsNetty)
                .isPresent()) return true;
        return plugin.getChild("executions").stream().flatMap(executions -> executions.getChildren().stream())
                .filter(execution -> "execution".equals(execution.getName()))
                .flatMap(execution -> execution.getChildren().stream())
                .filter(configuration -> "configuration".equals(configuration.getName()))
                .anyMatch(FindNettyHandler41136BuildRisks::configurationMentionsNetty);
    }

    private static boolean configurationMentionsNetty(Xml.Tag tag) {
        if (tag.getValue().map(String::trim).filter(FindNettyHandler41136BuildRisks::nettyConfigurationToken)
                .isPresent()) return true;
        if (tag.getAttributes().stream().map(Xml.Attribute::getValueAsString).map(String::trim)
                .anyMatch(FindNettyHandler41136BuildRisks::nettyConfigurationToken)) return true;
        return tag.getChildren().stream().anyMatch(FindNettyHandler41136BuildRisks::configurationMentionsNetty);
    }

    private static boolean nettyConfigurationToken(String value) {
        return "io.netty".equals(value) || value.startsWith("io.netty.") || value.startsWith("io.netty:");
    }

    private static boolean tlsLaunchToken(String value) {
        String trimmed = value.trim();
        return trimmed.contains("io.netty.handler.ssl.") ||
               trimmed.contains("jdk.tls.") ||
               trimmed.contains("javax.net.ssl.sessionCacheSize");
    }

    private static String managedMavenBom(Xml.Document source, Scopes scopes, Properties properties,
                                          ExecutionContext ctx) {
        Set<String> versions = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (visible(getCursor(), scopes) && NettyHandlerSupport.isNettyBom(getCursor(), visited)) {
                    String raw = visited.getChildValue("version").map(String::trim).orElse("");
                    String resolved = resolve(raw, getCursor(), properties);
                    if (resolved != null) versions.add(resolved);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return versions.size() == 1 ? versions.iterator().next() : null;
    }

    private static String managedGroovyBom(G.CompilationUnit source, ExecutionContext ctx) {
        Set<String> versions = new HashSet<>();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean platform = isRootPlatformLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                collectBomVersion(platform, visited, versions);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return versions.size() == 1 ? versions.iterator().next() : null;
    }

    private static String managedKotlinBom(K.CompilationUnit source, ExecutionContext ctx) {
        Set<String> versions = new HashSet<>();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean platform = isRootPlatformLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                collectBomVersion(platform, visited, versions);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return versions.size() == 1 ? versions.iterator().next() : null;
    }

    private static void collectBomVersion(boolean platform, J.Literal literal, Set<String> versions) {
        if (!platform || !(literal.getValue() instanceof String coordinate)) return;
        String prefix = NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.BOM + ":";
        if (coordinate.startsWith(prefix)) versions.add(coordinate.substring(prefix.length()));
    }

    private static boolean isRootPlatformLiteral(Cursor cursor) {
        Cursor platformCursor = cursor.getParentTreeCursor();
        if (!(platformCursor.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) ||
              "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor dependencyCursor = platformCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof J.MethodInvocation dependency &&
               NettyHandlerSupport.isGradleDependencyInvocation(dependencyCursor, dependency);
    }

    private static Scopes scopes(Xml.Document source, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (NettyHandlerSupport.isHandlerDependency(getCursor(), visited)) {
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
                if (NettyHandlerSupport.isMavenPropertyDefinition(getCursor(), visited)) {
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
                boolean direct = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
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
                boolean direct = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (NettyHandlerSupport.GROUP.equals(NettyHandlerSupport.mapValue(method, "group")) &&
            NettyHandlerSupport.ARTIFACT.equals(NettyHandlerSupport.mapValue(method, "name"))) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                NettyHandlerSupport.GROUP.equals(NettyHandlerSupport.mapValue(map, "group")) &&
                NettyHandlerSupport.ARTIFACT.equals(NettyHandlerSupport.mapValue(map, "name"))) return true;
            if (dynamicPrimary(argument)) return true;
        }
        return false;
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.ARTIFACT) ||
                coordinate.startsWith(NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.ARTIFACT + ":"));
    }

    private static boolean dynamicPrimary(J expression) {
        java.util.List<J> parts;
        if (expression instanceof G.GString string) parts = string.getStrings();
        else if (expression instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().map(String::stripLeading)
                .map(value -> value.startsWith(NettyHandlerSupport.GROUP + ":" +
                                               NettyHandlerSupport.ARTIFACT + ":"))
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
