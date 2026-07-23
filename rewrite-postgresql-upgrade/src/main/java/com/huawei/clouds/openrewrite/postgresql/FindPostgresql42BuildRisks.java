package com.huawei.clouds.openrewrite.postgresql;

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

/** Marks exact PostgreSQL build nodes whose version ownership is not safe to infer. */
public final class FindPostgresql42BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    static final String OWNER =
            "This PostgreSQL JDBC version is absent, inherited, variable, ranged, dynamic, catalog/platform/BOM-managed, " +
            "or externally owned; migrate the real owner explicitly and prove that 42.7.13 is selected";
    static final String OUTSIDE =
            "This fixed PostgreSQL JDBC version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so assess its own supported path and server/JVM compatibility before selecting 42.7.13";
    static final String VARIANT =
            "This classified or non-JAR PostgreSQL artifact is outside the workbook's ordinary driver selection; " +
            "verify that the intended 42.7.13 variant exists before changing it";
    static final String JAVA =
            "PostgreSQL JDBC 42.7.13 requires Java 8 or newer; raise this explicit baseline and retest TLS, GSS, XA, " +
            "java.time, service loading and container runtime behavior";

    @Override
    public String getDisplayName() {
        return "Find PostgreSQL JDBC 42.7 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Maven and root Gradle nodes with unresolved/outside PostgreSQL versions, variants, and " +
               "explicit pre-Java-8 build baselines, gated by a real pgjdbc dependency in the same owner.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedPostgresqlDependency.excluded(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    if (!containsTarget(document, ctx)) return tree;
                    Model model = model(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (UpgradeSelectedPostgresqlDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindPostgresql42BuildRisks::preJava8).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (!sameCoordinate(getCursor(), t)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            String resolved = model.resolve(scope(getCursor()), version);
                            if (UpgradeSelectedPostgresqlDependency.TARGET.equals(resolved) ||
                                version.isEmpty() && !inDependencyManagement(getCursor()) &&
                                model.managed(scope(getCursor()), UpgradeSelectedPostgresqlDependency.TARGET)) return t;
                            if (resolved != null && FIXED.matcher(resolved).matches()) {
                                return markVersionOrOwner(t, OUTSIDE);
                            }
                            return markVersionOrOwner(t, OWNER);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    if (!containsTarget(groovy, ctx)) return tree;
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedPostgresqlDependency
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
                            boolean dependency = UpgradeSelectedPostgresqlDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    if (!containsTarget(kotlin, ctx)) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedPostgresqlDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markManagedArguments(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedPostgresqlDependency.isDirectDependencyLiteral(getCursor());
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

    private static Model model(Xml.Document document, ExecutionContext ctx) {
        Map<Key, Integer> counts = new HashMap<>();
        Map<Key, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedPostgresqlDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    Key key = new Key(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        Model model = new Model(counts, values, new HashMap<>());
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (sameCoordinate(getCursor(), t) && inDependencyManagement(getCursor())) {
                    String resolved = model.resolve(scope(getCursor()),
                            t.getChildValue("version").map(String::trim).orElse(""));
                    if (resolved != null) model.management().put(scope(getCursor()), resolved);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return model;
    }

    private static boolean containsTarget(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (sameCoordinate(getCursor(), t)) found[0] = true;
                return t;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static boolean containsTarget(G.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                if (UpgradeSelectedPostgresqlDependency.isDirectDependencyLiteral(getCursor()) &&
                    mentionsCoordinate(literal.getValue())) found[0] = true;
                return super.visitLiteral(literal, ec);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                if (UpgradeSelectedPostgresqlDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    (mapTarget(method) || method.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                            .map(G.MapLiteral.class::cast).anyMatch(FindPostgresql42BuildRisks::mapTarget) ||
                     method.getArguments().stream()
                            .anyMatch(FindPostgresql42BuildRisks::argumentMentionsTarget))) found[0] = true;
                return super.visitMethodInvocation(method, ec);
            }
        }.visitNonNull(cu, ctx);
        return found[0];
    }

    private static boolean containsTarget(K.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                if (UpgradeSelectedPostgresqlDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    method.getArguments().stream().anyMatch(FindPostgresql42BuildRisks::argumentMentionsTarget)) {
                    found[0] = true;
                }
                return super.visitMethodInvocation(method, ec);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                if (UpgradeSelectedPostgresqlDependency.isDirectDependencyLiteral(getCursor()) &&
                    mentionsCoordinate(literal.getValue())) found[0] = true;
                return super.visitLiteral(literal, ec);
            }
        }.visitNonNull(cu, ctx);
        return found[0];
    }

    private static boolean sameCoordinate(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedPostgresqlDependency.isProjectDependency(cursor, tag) &&
               UpgradeSelectedPostgresqlDependency.GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               UpgradeSelectedPostgresqlDependency.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
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

    private static boolean preJava8(String value) {
        return value.matches("1\\.[0-7]") || value.matches("[1-7]");
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2 || !UpgradeSelectedPostgresqlDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedPostgresqlDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length != 3 || parts[2].contains("@")) return parts.length > 3 || coordinate.contains("@") ? VARIANT : OWNER;
        if (UpgradeSelectedPostgresqlDependency.TARGET.equals(parts[2])) return null;
        return FIXED.matcher(parts[2]).matches() ? OUTSIDE : OWNER;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        if (!mapTarget(invocation)) return null;
        if (UpgradeSelectedPostgresqlDependency.hasVariant(invocation)) return VARIANT;
        String version = UpgradeSelectedPostgresqlDependency.mapValue(invocation, "version");
        if (UpgradeSelectedPostgresqlDependency.TARGET.equals(version)) return null;
        return version != null && FIXED.matcher(version).matches() ? OUTSIDE : OWNER;
    }

    private static String mapMessage(G.MapLiteral map) {
        if (!UpgradeSelectedPostgresqlDependency.GROUP.equals(
                UpgradeSelectedPostgresqlDependency.mapValue(map, "group")) ||
            !UpgradeSelectedPostgresqlDependency.ARTIFACT.equals(
                UpgradeSelectedPostgresqlDependency.mapValue(map, "name"))) return null;
        if (UpgradeSelectedPostgresqlDependency.hasVariant(map)) return VARIANT;
        String version = UpgradeSelectedPostgresqlDependency.mapValue(map, "version");
        if (UpgradeSelectedPostgresqlDependency.TARGET.equals(version)) return null;
        return version != null && FIXED.matcher(version).matches() ? OUTSIDE : OWNER;
    }

    private static boolean mapTarget(J.MethodInvocation invocation) {
        return UpgradeSelectedPostgresqlDependency.GROUP.equals(
                UpgradeSelectedPostgresqlDependency.mapValue(invocation, "group")) &&
               UpgradeSelectedPostgresqlDependency.ARTIFACT.equals(
                       UpgradeSelectedPostgresqlDependency.mapValue(invocation, "name"));
    }

    private static boolean mapTarget(G.MapLiteral map) {
        return UpgradeSelectedPostgresqlDependency.GROUP.equals(
                UpgradeSelectedPostgresqlDependency.mapValue(map, "group")) &&
               UpgradeSelectedPostgresqlDependency.ARTIFACT.equals(
                       UpgradeSelectedPostgresqlDependency.mapValue(map, "name"));
    }

    private static boolean mentionsCoordinate(Object value) {
        if (!(value instanceof String string)) return false;
        String prefix = UpgradeSelectedPostgresqlDependency.GROUP + ":" +
                        UpgradeSelectedPostgresqlDependency.ARTIFACT;
        return string.equals(prefix) || string.startsWith(prefix + ":");
    }

    private static J.MethodInvocation markManagedArguments(J.MethodInvocation invocation) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument ->
                managedArgument(argument) ? mark(argument, OWNER) : argument).toList());
    }

    private static boolean managedArgument(J argument) {
        if (argument instanceof G.GString string) {
            return string.getStrings().stream().anyMatch(FindPostgresql42BuildRisks::literalMentionsPrefix);
        }
        if (argument instanceof K.StringTemplate string) {
            return string.getStrings().stream().anyMatch(FindPostgresql42BuildRisks::literalMentionsPrefix);
        }
        return !(argument instanceof J.Literal) && argument.printTrimmed().contains("libs.postgresql");
    }

    private static boolean argumentMentionsTarget(J argument) {
        return managedArgument(argument) || argument instanceof J.Literal literal && mentionsCoordinate(literal.getValue());
    }

    private static boolean literalMentionsPrefix(J part) {
        return part instanceof J.Literal literal && literal.getValue() instanceof String value &&
               value.contains(UpgradeSelectedPostgresqlDependency.GROUP + ":" +
                              UpgradeSelectedPostgresqlDependency.ARTIFACT);
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private record Key(String scope, String name) {
    }

    private record Model(Map<Key, Integer> counts, Map<Key, String> values, Map<String, String> management) {
        String resolve(String scope, String version) {
            if (FIXED.matcher(version).matches()) return version;
            Matcher matcher = PROPERTY.matcher(version);
            if (!matcher.matches()) return null;
            Key local = new Key(scope, matcher.group(1));
            Key root = new Key("ROOT", matcher.group(1));
            Key owner = !"ROOT".equals(scope) && counts.containsKey(local) ? local : root;
            return counts.getOrDefault(owner, 0) == 1 ? values.get(owner) : null;
        }

        boolean managed(String scope, String expected) {
            String value = management.get(scope);
            return expected.equals(value) || !"ROOT".equals(scope) && expected.equals(management.get("ROOT"));
        }
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
