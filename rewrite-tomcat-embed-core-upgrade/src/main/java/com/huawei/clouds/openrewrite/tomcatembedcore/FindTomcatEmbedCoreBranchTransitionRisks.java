package com.huawei.clouds.openrewrite.tomcatembedcore;

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

/** Preserve explicit warnings when the requested target crosses a Tomcat major-version branch. */
public final class FindTomcatEmbedCoreBranchTransitionRisks extends Recipe {
    static final String TOMCAT_9 =
            "Tomcat 9 to 10.1 crosses Java EE javax.* to Jakarta EE jakarta.* and Servlet 4 to Servlet 6; " +
            "migrate every Servlet/EL dependency, source type, descriptor, service provider and framework integration, " +
            "then run container-level compatibility tests";
    static final String TOMCAT_11 =
            "目标版本冲突（禁止降级）：This Tomcat 11 source conflicts with the supplied 10.1.57 target; " +
            "the upgrade-only policy keeps the " +
            "source unchanged until an approved Tomcat 11 target is supplied";
    private static final String PREFIX = UpgradeSelectedTomcatEmbedCoreDependency.GROUP + ":" +
                                         UpgradeSelectedTomcatEmbedCoreDependency.ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Find Tomcat Embed Core branch-transition risks";
    }

    @Override
    public String getDescription() {
        return "Marks selected Tomcat 9 to 10.1 namespace transitions and Tomcat 11 target conflicts; Tomcat 11 " +
               "dependency literals remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedTomcatEmbedCoreDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return visitPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedTomcatEmbedCoreDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            return direct ? markBranch(visited, version(visited)) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedTomcatEmbedCoreDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            return direct ? markBranch(visited, version(visited)) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document visitPom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedTomcatEmbedCoreDependency.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = owner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().map(String::trim).ifPresent(value -> values.put(owner, value));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!UpgradeSelectedTomcatEmbedCoreDependency.isTomcatEmbedCoreDependency(getCursor(), visited)) {
                    return visited;
                }
                String version = visited.getChildValue("version").map(String::trim).orElse(null);
                if (version != null && version.startsWith("${") && version.endsWith("}")) {
                    String name = version.substring(2, version.length() - 1);
                    PropertyOwner resolved = resolvedOwner(getCursor(), name, definitions);
                    version = definitions.getOrDefault(resolved, 0) == 1 ? values.get(resolved) : null;
                }
                return markBranch(visited, version);
            }
        }.visitNonNull(document, ctx);
    }

    private static String version(J.MethodInvocation invocation) {
        String mapped = UpgradeSelectedTomcatEmbedCoreDependency.mapValue(invocation, "version");
        if (mapped != null && UpgradeSelectedTomcatEmbedCoreDependency.GROUP.equals(
                UpgradeSelectedTomcatEmbedCoreDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedTomcatEmbedCoreDependency.ARTIFACT.equals(
                UpgradeSelectedTomcatEmbedCoreDependency.mapValue(invocation, "name"))) return mapped;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate &&
                coordinate.startsWith(PREFIX)) return plainVersion(coordinate.substring(PREFIX.length()));
            if (argument instanceof G.MapLiteral map && UpgradeSelectedTomcatEmbedCoreDependency.GROUP.equals(
                    UpgradeSelectedTomcatEmbedCoreDependency.mapValue(map, "group")) &&
                UpgradeSelectedTomcatEmbedCoreDependency.ARTIFACT.equals(
                    UpgradeSelectedTomcatEmbedCoreDependency.mapValue(map, "name"))) {
                return UpgradeSelectedTomcatEmbedCoreDependency.mapValue(map, "version");
            }
        }
        return null;
    }

    private static String plainVersion(String value) {
        return value.contains(":") || value.contains("@") ? null : value;
    }

    private static <T extends Tree> T markBranch(T tree, String version) {
        String message = branchMessage(version);
        if (message == null || tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))) return tree;
        return SearchResult.found(tree, message);
    }

    private static String branchMessage(String version) {
        if (version == null) return null;
        if (version.equals("11.0.18") || version.equals("11.0.21")) return TOMCAT_11;
        if (!UpgradeSelectedTomcatEmbedCoreDependency.SOURCE_VERSIONS.contains(version)) return null;
        if (version.startsWith("9.0.")) return TOMCAT_9;
        return null;
    }

    private static PropertyOwner owner(Cursor cursor, String name) {
        String profile = profile(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name,
                                               Map<PropertyOwner, Integer> definitions) {
        String profile = profile(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static String profile(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private record PropertyOwner(String scope, String name) { }
}
