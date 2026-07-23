package com.huawei.clouds.openrewrite.feignokhttp;

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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact build nodes whose version ownership or family alignment is not safe to infer. */
public final class FindFeignOkHttp13BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    static final String OWNER =
            "This Feign OkHttp version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, or outside " +
            "local ownership; migrate the actual owner deliberately and prove that io.github.openfeign:feign-okhttp:13.6 resolves";
    static final String OUTSIDE =
            "This fixed Feign OkHttp version is not one of the workbook-selected sources; it is intentionally not " +
            "auto-upgraded, so assess its own path to 13.6";
    static final String VARIANT =
            "This nonstandard Feign OkHttp classifier/type/extension is outside the workbook's ordinary jar selection; " +
            "verify that the variant exists for 13.6 before changing it";
    static final String FEIGN_ALIGNMENT =
            "This Feign companion module is not aligned to 13.6; align the actually resolved Feign family and test " +
            "Client/AsyncClient, Request.Options, Response protocol, retry, logging, encoder, and decoder contracts";
    static final String OKHTTP_ALIGNMENT =
            "Feign OkHttp 13.6 is built with the OkHttp 4.12.0 BOM; align this direct OkHttp module deliberately and " +
            "verify Okio/Kotlin runtime, interceptors, request bodies, HTTP/2, TLS, compression, and client lifecycle";
    static final String JAVA =
            "Feign 13.6 and OkHttp 4 require Java 8 or newer bytecode/runtime; raise this explicit baseline and retest TLS/ALPN";

    @Override
    public String getDisplayName() {
        return "Find Feign OkHttp 13 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Maven and root Gradle nodes with unresolved Feign OkHttp versions, variants, Feign-family or " +
               "OkHttp 4.12 alignment risks, and explicit pre-Java-8 baselines.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || UpgradeSelectedFeignOkHttpDependency.excluded(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    if (!containsTargetDependency(document, ctx)) return tree;
                    ScopeModel model = scopeModel(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (UpgradeSelectedFeignOkHttpDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindFeignOkHttp13BuildRisks::preJava8).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (!UpgradeSelectedFeignOkHttpDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            String scope = scope(getCursor());
                            boolean managedDeclaration = inDependencyManagement(getCursor());

                            if (UpgradeSelectedFeignOkHttpDependency.GROUP.equals(group) && artifact.startsWith("feign-") &&
                                !UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(artifact)) {
                                return resolved(version, scope, model, UpgradeSelectedFeignOkHttpDependency.TARGET) ||
                                       version.isEmpty() && !managedDeclaration && model.managed(scope, group, artifact,
                                               UpgradeSelectedFeignOkHttpDependency.TARGET)
                                        ? t : markVersionOrOwner(t, FEIGN_ALIGNMENT);
                            }
                            if (okHttpModule(group, artifact)) {
                                return resolved(version, scope, model, "4.12.0") ||
                                       version.isEmpty() && !managedDeclaration && model.managedOkHttp(scope, artifact)
                                        ? t : markVersionOrOwner(t, OKHTTP_ALIGNMENT);
                            }
                            if (!UpgradeSelectedFeignOkHttpDependency.GROUP.equals(group) ||
                                !UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(artifact)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            if (resolved(version, scope, model, UpgradeSelectedFeignOkHttpDependency.TARGET) ||
                                version.isEmpty() && !managedDeclaration && model.managed(scope, group, artifact,
                                        UpgradeSelectedFeignOkHttpDependency.TARGET)) return t;
                            String resolvedVersion = model.resolve(scope, version);
                            if (resolvedVersion != null && FIXED.matcher(resolvedVersion).matches()) {
                                return markVersionOrOwner(t, OUTSIDE);
                            }
                            if (!FIXED.matcher(version).matches()) return markVersionOrOwner(t, OWNER);
                            return markVersionOrOwner(t, OUTSIDE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    if (!containsTargetDependency(groovy, ctx)) return tree;
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignOkHttpDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!dependency) return m;
                            m = markManagedArguments(m);
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
                            boolean dependency = UpgradeSelectedFeignOkHttpDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    if (!containsTargetDependency(kotlin, ctx)) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignOkHttpDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markManagedArguments(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignOkHttpDependency.isDirectDependencyLiteral(getCursor());
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

    private static ScopeModel scopeModel(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedFeignOkHttpDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        ScopeModel model = new ScopeModel(counts, values, new HashMap<>());
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedFeignOkHttpDependency.isProjectDependency(getCursor(), t) &&
                    inDependencyManagement(getCursor())) {
                    String version = t.getChildValue("version").map(String::trim).orElse("");
                    String resolved = model.resolve(scope(getCursor()), version);
                    if (resolved == null && FIXED.matcher(version).matches()) resolved = version;
                    if (resolved != null) {
                        String coordinate = t.getChildValue("groupId").orElse("") + ":" +
                                            t.getChildValue("artifactId").orElse("");
                        model.management().put(new PropertyKey(scope(getCursor()), coordinate), resolved);
                    }
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return model;
    }

    private static boolean resolved(String version, String scope, ScopeModel model, String expected) {
        return expected.equals(version) || expected.equals(model.resolve(scope, version));
    }

    private static boolean inDependencyManagement(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies.getParentTreeCursor();
        return owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName());
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

    private static boolean okHttpModule(String group, String artifact) {
        return "com.squareup.okhttp3".equals(group) && artifact != null && !artifact.isBlank();
    }

    private static boolean preJava8(String value) {
        return value.matches("1\\.[0-7]") || value.matches("[1-7]");
    }

    private static boolean containsTargetDependency(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedFeignOkHttpDependency.isProjectDependency(getCursor(), visited) &&
                    UpgradeSelectedFeignOkHttpDependency.GROUP.equals(
                            visited.getChildValue("groupId").orElse(null)) &&
                    UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(
                            visited.getChildValue("artifactId").orElse(null))) found[0] = true;
                return visited;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static boolean containsTargetDependency(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedFeignOkHttpDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && targetInvocation(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(compilationUnit, ctx);
        return found[0];
    }

    private static boolean containsTargetDependency(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedFeignOkHttpDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && targetInvocation(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(compilationUnit, ctx);
        return found[0];
    }

    private static boolean targetInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedFeignOkHttpDependency.GROUP.equals(
                    UpgradeSelectedFeignOkHttpDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(
                    UpgradeSelectedFeignOkHttpDependency.mapValue(invocation, "name"))) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && targetCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                UpgradeSelectedFeignOkHttpDependency.GROUP.equals(
                        UpgradeSelectedFeignOkHttpDependency.mapValue(map, "group")) &&
                UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(
                        UpgradeSelectedFeignOkHttpDependency.mapValue(map, "name"))) return true;
            if (templateMentionsAuditedDependency(argument)) return true;
            String printed = argument.printTrimmed().toLowerCase(java.util.Locale.ROOT);
            String compact = printed.replaceAll("[^a-z0-9]", "");
            if (printed.startsWith("libs") && compact.contains("feign") && compact.contains("okhttp")) return true;
        }
        return false;
    }

    private static boolean targetCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedFeignOkHttpDependency.GROUP + ":" +
                        UpgradeSelectedFeignOkHttpDependency.ARTIFACT;
        return prefix.equals(coordinate) || coordinate.startsWith(prefix + ":");
    }

    private static String coordinateMessage(Object literal) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if (UpgradeSelectedFeignOkHttpDependency.GROUP.equals(parts[0]) && parts[1].startsWith("feign-") &&
            !UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(parts[1])) {
            return parts.length == 3 && UpgradeSelectedFeignOkHttpDependency.TARGET.equals(parts[2])
                    ? null : FEIGN_ALIGNMENT;
        }
        if (okHttpModule(parts[0], parts[1])) {
            return parts.length == 3 && "4.12.0".equals(parts[2]) ? null : OKHTTP_ALIGNMENT;
        }
        if (!UpgradeSelectedFeignOkHttpDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return OWNER;
        return UpgradeSelectedFeignOkHttpDependency.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        return dependencyMessage(UpgradeSelectedFeignOkHttpDependency.mapValue(invocation, "group"),
                UpgradeSelectedFeignOkHttpDependency.mapValue(invocation, "name"),
                UpgradeSelectedFeignOkHttpDependency.mapValue(invocation, "version"),
                UpgradeSelectedFeignOkHttpDependency.hasVariant(invocation));
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(UpgradeSelectedFeignOkHttpDependency.mapValue(map, "group"),
                UpgradeSelectedFeignOkHttpDependency.mapValue(map, "name"),
                UpgradeSelectedFeignOkHttpDependency.mapValue(map, "version"),
                UpgradeSelectedFeignOkHttpDependency.hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (UpgradeSelectedFeignOkHttpDependency.GROUP.equals(group) && artifact != null &&
            artifact.startsWith("feign-") && !UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(artifact)) {
            return UpgradeSelectedFeignOkHttpDependency.TARGET.equals(version) ? null : FEIGN_ALIGNMENT;
        }
        if (okHttpModule(group, artifact)) return "4.12.0".equals(version) ? null : OKHTTP_ALIGNMENT;
        if (!UpgradeSelectedFeignOkHttpDependency.GROUP.equals(group) ||
            !UpgradeSelectedFeignOkHttpDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches()) return OWNER;
        return UpgradeSelectedFeignOkHttpDependency.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static J.MethodInvocation markManagedArguments(J.MethodInvocation invocation) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument ->
                managedArgumentMessage(argument) == null ? argument : mark(argument, managedArgumentMessage(argument)))
                .toList());
    }

    private static String managedArgumentMessage(J argument) {
        if (templateMentionsAuditedDependency(argument)) return OWNER;
        String printed = argument.printTrimmed().toLowerCase(java.util.Locale.ROOT);
        if (argument instanceof J.MethodInvocation nested &&
            Set.of("platform", "enforcedplatform").contains(nested.getSimpleName().toLowerCase(java.util.Locale.ROOT))) {
            if (printed.contains("com.squareup.okhttp3:okhttp-bom")) return OKHTTP_ALIGNMENT;
            if (printed.contains("io.github.openfeign:feign-bom") || printed.contains("feign-okhttp")) return OWNER;
        }
        String compact = printed.replaceAll("[^a-z0-9]", "");
        return printed.startsWith("libs") && compact.contains("feign") && compact.contains("okhttp") ? OWNER : null;
    }

    private static boolean templateMentionsAuditedDependency(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.contains(UpgradeSelectedFeignOkHttpDependency.GROUP + ":" +
                                                  UpgradeSelectedFeignOkHttpDependency.ARTIFACT + ":"));
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private record PropertyKey(String scope, String name) {
    }

    private record ScopeModel(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values,
                              Map<PropertyKey, String> management) {
        String resolve(String scope, String version) {
            Matcher matcher = PROPERTY.matcher(version);
            if (!matcher.matches()) return null;
            PropertyKey local = new PropertyKey(scope, matcher.group(1));
            PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
            PropertyKey owner = !"ROOT".equals(scope) && counts.containsKey(local) ? local : root;
            return counts.getOrDefault(owner, 0) == 1 ? values.get(owner) : null;
        }

        boolean managed(String scope, String group, String artifact, String expected) {
            String coordinate = group + ":" + artifact;
            String value = management.get(new PropertyKey(scope, coordinate));
            if (value == null && !"ROOT".equals(scope)) value = management.get(new PropertyKey("ROOT", coordinate));
            return expected.equals(value);
        }

        boolean managedOkHttp(String scope, String artifact) {
            return managed(scope, "com.squareup.okhttp3", artifact, "4.12.0") ||
                   managed(scope, "com.squareup.okhttp3", "okhttp-bom", "4.12.0");
        }
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
