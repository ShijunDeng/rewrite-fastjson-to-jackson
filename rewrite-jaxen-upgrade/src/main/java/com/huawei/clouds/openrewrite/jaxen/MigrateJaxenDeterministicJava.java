package com.huawei.clouds.openrewrite.jaxen;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Applies only replacements explicitly documented by the removed 1.2 APIs themselves. */
public final class MigrateJaxenDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Jaxen 2 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Replace XPath.valueOf(Object) with stringValueOf(Object), and FunctionCallException." +
               "getNestedException() with Throwable.getCause(), as directed by the official 1.2 deprecations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxenSupport.generated(source.getSourcePath())) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, context);
                        JavaType.Method type = m.getMethodType();
                        if (type == null) return m;
                        String owner = owner(type);
                        String replacement = null;
                        if ("valueOf".equals(type.getName()) && m.getArguments().size() == 1 &&
                            Set.of("org.jaxen.XPath", "org.jaxen.BaseXPath").contains(owner)) {
                            replacement = "stringValueOf";
                        } else if ("getNestedException".equals(type.getName()) && noArguments(m) &&
                                   "org.jaxen.FunctionCallException".equals(owner)) {
                            replacement = "getCause";
                        }
                        if (replacement == null) return m;
                        return m.withName(m.getName().withSimpleName(replacement))
                                .withMethodType(type.withName(replacement));
                    }
                }.visitNonNull(source, ctx);
            }
        };
    }

    private static String owner(JavaType.Method method) {
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(method.getDeclaringType());
        return owner == null ? "" : owner.getFullyQualifiedName();
    }

    private static boolean noArguments(J.MethodInvocation method) {
        return method.getArguments().isEmpty() ||
               (method.getArguments().size() == 1 && method.getArguments().get(0) instanceof J.Empty);
    }
}
