package com.huawei.clouds.openrewrite.jsch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Applies the one behavior-preserving configuration-key canonicalization in this migration. */
public final class CanonicalizeJschConfigKeys extends Recipe {
    private static final String OLD_KEY = "PubkeyAcceptedKeyTypes";
    private static final String NEW_KEY = "PubkeyAcceptedAlgorithms";

    @Override
    public String getDisplayName() {
        return "Canonicalize the JSch public-key algorithm configuration key";
    }

    @Override
    public String getDescription() {
        return "Rename the legacy PubkeyAcceptedKeyTypes alias to PubkeyAcceptedAlgorithms only when it is the " +
               "literal key argument of an attributed JSch or Session configuration API.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJschDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null || !("setConfig".equals(type.getName()) || "getConfig".equals(type.getName())) ||
                    !(TypeUtils.isOfClassType(type.getDeclaringType(), "com.jcraft.jsch.JSch") ||
                      TypeUtils.isOfClassType(type.getDeclaringType(), "com.jcraft.jsch.Session")) ||
                    m.getArguments().isEmpty() || !(m.getArguments().get(0) instanceof J.Literal literal) ||
                    !OLD_KEY.equals(literal.getValue())) return m;
                String source = literal.getValueSource();
                J.Literal replacement = literal.withValue(NEW_KEY).withValueSource(
                        source == null ? null : source.replace(OLD_KEY, NEW_KEY));
                return m.withArguments(org.openrewrite.internal.ListUtils.mapFirst(m.getArguments(), ignored -> replacement));
            }
        };
    }
}
