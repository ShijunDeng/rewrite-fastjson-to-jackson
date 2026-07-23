package com.huawei.clouds.openrewrite.log4j12api;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/**
 * Limits the official setLevel migration to source files whose entire attributed
 * Log4j 1 surface is covered by the selected official leaves.
 */
public final class FindSafeLog4j12SetLevelSources extends Recipe {
    private static final Set<String> ALLOWED_TYPES =
            Set.of("org.apache.log4j.Logger", "org.apache.log4j.Level");

    @Override
    public String getDisplayName() {
        return "Find safe authored Log4j 1 Logger.setLevel sources";
    }

    @Override
    public String getDescription() {
        return "Find non-generated Java files whose complete attributed Log4j 1 use is limited to Logger, " +
               "Level, Logger.getLogger/getRootLogger, and Logger.setLevel.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit source) ||
                    UpgradeSelectedLog4j12ApiDependency.generated(source.getSourcePath()) ||
                    source.printAll().contains("import org.apache.log4j.*;")) return tree;

                State state = new State();
                new JavaIsoVisitor<State>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(
                            J.MethodInvocation method, State accumulator) {
                        J.MethodInvocation visited = super.visitMethodInvocation(method, accumulator);
                        JavaType.Method type = visited.getMethodType();
                        String owner = type == null ? "" : fqn(type.getDeclaringType());
                        if (!owner.startsWith("org.apache.log4j.")) return visited;
                        String name = visited.getSimpleName();
                        if (("org.apache.log4j.Logger".equals(owner) &&
                             ("getLogger".equals(name) || "getRootLogger".equals(name))) ||
                            (("org.apache.log4j.Logger".equals(owner) ||
                              "org.apache.log4j.Category".equals(owner)) &&
                             "setLevel".equals(name))) {
                            if ("setLevel".equals(name)) accumulator.hasSetLevel = true;
                        } else {
                            accumulator.unsafe = true;
                        }
                        return visited;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, State accumulator) {
                        J.Identifier visited = super.visitIdentifier(identifier, accumulator);
                        String type = fqn(visited.getType());
                        if (type.startsWith("org.apache.log4j.") && !ALLOWED_TYPES.contains(type)) {
                            accumulator.unsafe = true;
                        }
                        return visited;
                    }
                }.visitNonNull(source, state);

                return state.hasSetLevel && !state.unsafe ? SearchResult.found((SourceFile) source) : tree;
            }
        };
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static final class State {
        private boolean hasSetLevel;
        private boolean unsafe;
    }
}
