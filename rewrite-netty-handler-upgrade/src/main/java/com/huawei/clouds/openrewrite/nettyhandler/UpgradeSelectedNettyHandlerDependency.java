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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Upgrade only exact 4.1.x Netty handler versions selected by the workbook and their local BOM owner. */
public final class UpgradeSelectedNettyHandlerDependency extends Recipe {
    private static final String PREFIX =
            NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.ARTIFACT + ":";
    private static final String BOM_PREFIX =
            NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.BOM + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Netty handler dependencies to 4.1.136.Final";
    }

    @Override
    public String getDescription() {
        return "Upgrade only the twenty exact 4.1.x io.netty:netty-handler source versions and an unambiguous " +
               "local netty-bom owner in standard Maven or root Gradle declarations; never downgrade any newer line.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    NettyHandlerSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return migrateGroovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return migrateKotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean hasHandler = hasHandler(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!dependency) return visited;
                if (coordinate(NettyHandlerSupport.mapValue(visited, "group"),
                               NettyHandlerSupport.mapValue(visited, "name")) &&
                    NettyHandlerSupport.AUTO_SOURCES.contains(NettyHandlerSupport.mapValue(visited, "version")) &&
                    !NettyHandlerSupport.hasVariant(visited)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry &&
                            "version".equals(NettyHandlerSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal
                                    ? entry.withValue(NettyHandlerSupport.replaceLiteral(
                                            literal, NettyHandlerSupport.TARGET))
                                    : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean dependency = NettyHandlerSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = hasHandler && isRootPlatformLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return dependency || platform ? upgradeCoordinate(visited, platform) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasHandler = hasHandler(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean dependency = NettyHandlerSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = hasHandler && isRootPlatformLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return dependency || platform ? upgradeCoordinate(visited, platform) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Set<UUID> safeBomIds = safeMavenBomIds(document, ctx);
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (NettyHandlerSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profilePropertyNames.add(owner.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyOwner, Integer> allReferences = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, allReferences,
                        targetVersionReference(getCursor(), visited.getText(), safeBomIds)
                                ? targetReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, allReferences, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyOwner> safe = targetReferences.keySet().stream()
                .filter(owner -> values.get(owner) != null && NettyHandlerSupport.AUTO_SOURCES.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> allReferences.getOrDefault(owner, 0).equals(targetReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) || !profilePropertyNames.contains(owner.name()))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (NettyHandlerSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    safe.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(NettyHandlerSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withValue(NettyHandlerSupport.TARGET);
                }
                if (NettyHandlerSupport.isHandlerDependency(getCursor(), visited) &&
                    NettyHandlerSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(NettyHandlerSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withChildValue("version", NettyHandlerSupport.TARGET);
                }
                if (safeBomIds.contains(visited.getId()) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(NettyHandlerSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withChildValue("version", NettyHandlerSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name, Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static boolean targetVersionReference(Cursor cursor, String text, Set<UUID> safeBomIds) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        if (!(dependencyCursor.getValue() instanceof Xml.Tag dependency)) return false;
        return NettyHandlerSupport.isHandlerDependency(dependencyCursor, dependency) &&
               NettyHandlerSupport.standardJar(dependency) ||
               safeBomIds.contains(dependency.getId());
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> owned) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (owned != null) owned.merge(owner, 1, Integer::sum);
        }
    }

    private static boolean coordinate(String group, String artifact) {
        return NettyHandlerSupport.GROUP.equals(group) && NettyHandlerSupport.ARTIFACT.equals(artifact);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!coordinate(NettyHandlerSupport.mapValue(map, "group"), NettyHandlerSupport.mapValue(map, "name")) ||
            !NettyHandlerSupport.AUTO_SOURCES.contains(NettyHandlerSupport.mapValue(map, "version")) ||
            NettyHandlerSupport.hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(NettyHandlerSupport.mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(NettyHandlerSupport.replaceLiteral(literal, NettyHandlerSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal, boolean allowBom) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String prefix = value.startsWith(PREFIX) ? PREFIX :
                allowBom && value.startsWith(BOM_PREFIX) ? BOM_PREFIX : null;
        if (prefix == null) return literal;
        String version = value.substring(prefix.length());
        return NettyHandlerSupport.AUTO_SOURCES.contains(version)
                ? NettyHandlerSupport.replaceLiteral(literal, prefix + NettyHandlerSupport.TARGET) : literal;
    }

    private static boolean isRootPlatformLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) ||
              "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor dependency = parent.getParentTreeCursor();
        return dependency.getValue() instanceof J.MethodInvocation invocation &&
               NettyHandlerSupport.isGradleDependencyInvocation(dependency, invocation);
    }

    private static Set<UUID> safeMavenBomIds(Xml.Document source, ExecutionContext ctx) {
        Set<String> handlerScopes = new HashSet<>();
        Map<String, List<UUID>> bomsByScope = new LinkedHashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (NettyHandlerSupport.isHandlerDependency(getCursor(), visited) &&
                    NettyHandlerSupport.standardJar(visited)) {
                    handlerScopes.add(profileId(getCursor()) == null ? "ROOT" : profileId(getCursor()));
                }
                if (NettyHandlerSupport.isNettyBom(getCursor(), visited)) {
                    String profile = profileId(getCursor());
                    bomsByScope.computeIfAbsent(profile == null ? "ROOT" : profile,
                            ignored -> new java.util.ArrayList<>()).add(visited.getId());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        if (handlerScopes.isEmpty() || bomsByScope.isEmpty()) return Set.of();

        int total = bomsByScope.values().stream().mapToInt(List::size).sum();
        if (total == 1) {
            Map.Entry<String, List<UUID>> only = bomsByScope.entrySet().iterator().next();
            if ("ROOT".equals(only.getKey()) || handlerScopes.contains(only.getKey())) {
                return Set.of(only.getValue().get(0));
            }
            return Set.of();
        }

        // Multiple imported BOMs are only deterministic when they belong to separate profiles that each
        // declare their own handler. A root BOM mixed with profile BOMs has activation/merge semantics that
        // cannot be decided safely from syntax alone.
        if (bomsByScope.containsKey("ROOT")) return Set.of();
        Set<UUID> safe = new HashSet<>();
        bomsByScope.forEach((scope, ids) -> {
            if (ids.size() == 1 && handlerScopes.contains(scope)) safe.add(ids.get(0));
        });
        return Set.copyOf(safe);
    }

    private static boolean hasHandler(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && mentionsHandler(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasHandler(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = NettyHandlerSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && mentionsHandler(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean mentionsHandler(J.MethodInvocation invocation) {
        if (coordinate(NettyHandlerSupport.mapValue(invocation, "group"),
                       NettyHandlerSupport.mapValue(invocation, "name"))) return true;
        return invocation.getArguments().stream().anyMatch(argument -> {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String value) {
                return value.equals(NettyHandlerSupport.GROUP + ":" + NettyHandlerSupport.ARTIFACT) ||
                       value.startsWith(PREFIX);
            }
            return argument instanceof G.MapLiteral map &&
                   coordinate(NettyHandlerSupport.mapValue(map, "group"),
                              NettyHandlerSupport.mapValue(map, "name"));
        });
    }

    private record PropertyOwner(String scope, String name) {
    }
}
