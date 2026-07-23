package com.huawei.clouds.openrewrite.springretry;

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

/**
 * Project-local precondition for the official Java baseline recipe. It deliberately
 * recognizes only an exact, locally resolved 1.3.4 declaration.
 */
public final class FindSpringRetry134BuildFiles extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");

    @Override
    public String getDisplayName() {
        return "Find build files with an exact Spring Retry 1.3.4 dependency";
    }

    @Override
    public String getDescription() {
        return "Locate standard Maven or root Gradle declarations that resolve locally and exactly to Spring Retry " +
               "1.3.4, so the official Java 17 build recipe is scoped to approved inputs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringRetrySupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                boolean found = tree instanceof Xml.Document document && "pom.xml".equals(file)
                        ? maven(document, ctx)
                        : tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")
                                ? groovy(groovy, ctx)
                                : tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts") &&
                                  kotlin(kotlin, ctx);
                return found ? SearchResult.found(tree) : tree;
            }
        };
    }

    private static boolean maven(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isSpringRetryDependency(getCursor(), visited) &&
                    SpringRetrySupport.standardJar(visited)) {
                    String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                    if (SpringRetrySupport.SOURCE.equals(resolve(raw, getCursor(), counts, values))) found[0] = true;
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static String resolve(String raw, Cursor cursor, Map<PropertyKey, Integer> counts,
                                  Map<PropertyKey, String> values) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw;
        PropertyKey local = new PropertyKey(scope(cursor), matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = counts.getOrDefault(local, 0) == 1 ? local : root;
        return counts.getOrDefault(owner, 0) == 1 ? values.get(owner) : null;
    }

    private static boolean groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && !SpringRetrySupport.hasVariant(visited) &&
                    SpringRetrySupport.GROUP.equals(SpringRetrySupport.mapValue(visited, "group")) &&
                    SpringRetrySupport.ARTIFACT.equals(SpringRetrySupport.mapValue(visited, "name")) &&
                    SpringRetrySupport.SOURCE.equals(SpringRetrySupport.mapValue(visited, "version"))) {
                    found[0] = true;
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct && exactCoordinate(visited.getValue())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct && exactCoordinate(visited.getValue())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean exactCoordinate(Object value) {
        return (SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT + ":" +
                SpringRetrySupport.SOURCE).equals(value);
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
}
