package com.huawei.clouds.openrewrite.sqlformatter;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark exact build nodes that cannot be resolved or safely owned by this recipe. */
public final class FindSqlFormatter15BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    static final String UNPUBLISHED =
            "com.github.vertical-blank:sql-formatter:15.6.5 is not published to Maven Central and the official Java " +
            "repository stops at 2.0.5; npm sql-formatter:15.6.5 comes from sql-formatter-org/sql-formatter. " +
            "Correct the inventory/ecosystem before merge because this Maven dependency cannot resolve";
    static final String OWNER =
            "This SQL Formatter version is absent, inherited, variable, ranged, dynamic, catalog/platform/BOM-managed, " +
            "or externally owned; migrate the real owner explicitly and verify an actually published Maven coordinate";
    static final String OUTSIDE =
            "This fixed SQL Formatter version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration path and formatter-output compatibility baseline explicitly";
    static final String VARIANT =
            "This classified or non-JAR SQL Formatter artifact is outside deterministic scope; verify that the intended " +
            "published Java release has the required artifact shape before changing it";

    @Override
    public String getDisplayName() {
        return "Find Vertical Blank SQL Formatter 15.6.5 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark the unpublished target, external owners, dynamic/ranged versions, unsupported fixed versions, " +
               "and Maven/Gradle variants on exact owned dependency nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedSqlFormatterDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (!UpgradeSelectedSqlFormatterDependency.isProjectDependency(getCursor(), t) ||
                                !UpgradeSelectedSqlFormatterDependency.GROUP.equals(
                                        t.getChildValue("groupId").orElse(null)) ||
                                !UpgradeSelectedSqlFormatterDependency.ARTIFACT.equals(
                                        t.getChildValue("artifactId").orElse(null))) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            String resolved = resolve(version, getCursor(), properties);
                            if (UpgradeSelectedSqlFormatterDependency.TARGET.equals(resolved)) {
                                return markVersionOrOwner(t, UNPUBLISHED);
                            }
                            if (resolved == null || !FIXED.matcher(resolved).matches()) {
                                return markVersionOrOwner(t, OWNER);
                            }
                            if (UpgradeSelectedSqlFormatterDependency.SOURCE_VERSIONS.contains(resolved)) {
                                return markVersionOrOwner(t, OWNER);
                            }
                            return markVersionOrOwner(t, OUTSIDE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedSqlFormatterDependency
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
                            boolean dependency = UpgradeSelectedSqlFormatterDependency.isDirectDependencyLiteral(getCursor());
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
                            boolean dependency = UpgradeSelectedSqlFormatterDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedSqlFormatterDependency.isDirectDependencyLiteral(getCursor());
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
        if (parts.length < 2 || !UpgradeSelectedSqlFormatterDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedSqlFormatterDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return OWNER;
        if (UpgradeSelectedSqlFormatterDependency.SOURCE_VERSIONS.contains(parts[2])) return OWNER;
        return UpgradeSelectedSqlFormatterDependency.TARGET.equals(parts[2]) ? UNPUBLISHED : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        return dependencyMessage(UpgradeSelectedSqlFormatterDependency.mapValue(invocation, "group"),
                UpgradeSelectedSqlFormatterDependency.mapValue(invocation, "name"),
                UpgradeSelectedSqlFormatterDependency.mapValue(invocation, "version"),
                UpgradeSelectedSqlFormatterDependency.hasVariant(invocation));
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(UpgradeSelectedSqlFormatterDependency.mapValue(map, "group"),
                UpgradeSelectedSqlFormatterDependency.mapValue(map, "name"),
                UpgradeSelectedSqlFormatterDependency.mapValue(map, "version"),
                UpgradeSelectedSqlFormatterDependency.hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (!UpgradeSelectedSqlFormatterDependency.GROUP.equals(group) ||
            !UpgradeSelectedSqlFormatterDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches()) return OWNER;
        if (UpgradeSelectedSqlFormatterDependency.SOURCE_VERSIONS.contains(version)) return OWNER;
        return UpgradeSelectedSqlFormatterDependency.TARGET.equals(version) ? UNPUBLISHED : OUTSIDE;
    }

    private static ScopedProperties scopedProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedSqlFormatterDependency.isMavenPropertyDefinition(getCursor(), t)) {
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
                .anyMatch(value -> value.contains(UpgradeSelectedSqlFormatterDependency.GROUP + ":" +
                                                  UpgradeSelectedSqlFormatterDependency.ARTIFACT + ":"));
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
