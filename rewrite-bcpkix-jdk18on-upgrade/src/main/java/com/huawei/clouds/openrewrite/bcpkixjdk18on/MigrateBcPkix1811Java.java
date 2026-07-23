package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/** Apply only source changes whose equivalence is demonstrated by the official 1.74 to 1.81.1 source diff. */
public final class MigrateBcPkix1811Java extends Recipe {
    private static final String OLD_DELTA =
            "org.bouncycastle.pkcs.DeltaCertificateRequestAttribute";
    private static final String NEW_DELTA =
            "org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue";
    private static final String PKMAC_GENERATOR =
            "org.bouncycastle.cert.crmf.PKMACValueGenerator";
    private static final String PKMAC_BUILDER =
            "org.bouncycastle.cert.crmf.PKMACBuilder";
    private static final MethodMatcher SET_PUBLIC_KEY_MAC = new MethodMatcher(
            "org.bouncycastle.cert.crmf.ProofOfPossessionSigningKeyBuilder " +
            "setPublicKeyMac(org.bouncycastle.cert.crmf.PKMACValueGenerator, char[])");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Bouncy Castle PKIX 1.81.1 Java API changes";
    }

    @Override
    public String getDescription() {
        return "Rename DeltaCertificateRequestAttribute to DeltaCertificateRequestAttributeValue and unwrap an " +
               "inline PKMACValueGenerator passed to setPublicKeyMac. The latter is byte-for-byte equivalent to " +
               "the official 1.81.1 implementation; variables and all draft-dependent delta-certificate APIs remain review-only.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit source) ||
                    UpgradeSelectedBcPkixDependency.generated(source.getSourcePath())) return tree;

                Tree migrated = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ec) {
                        J.MethodInvocation visited = super.visitMethodInvocation(invocation, ec);
                        if (!SET_PUBLIC_KEY_MAC.matches(visited) || visited.getArguments().size() != 2 ||
                            !(visited.getArguments().get(0) instanceof J.NewClass wrapper) ||
                            !TypeUtils.isOfClassType(wrapper.getType(), PKMAC_GENERATOR) ||
                            wrapper.getArguments().size() != 1 ||
                            !TypeUtils.isOfClassType(wrapper.getArguments().get(0).getType(), PKMAC_BUILDER)) return visited;

                        Expression builder = wrapper.getArguments().get(0);
                        List<Expression> arguments = new ArrayList<>(visited.getArguments());
                        arguments.set(0, builder.withPrefix(wrapper.getPrefix()));
                        maybeRemoveImport(PKMAC_GENERATOR);
                        J.MethodInvocation unwrapped = visited.withArguments(arguments);
                        JavaType.Method method = unwrapped.getMethodType();
                        if (method != null && method.getParameterTypes().size() == 2) {
                            List<JavaType> parameters = new ArrayList<>(method.getParameterTypes());
                            parameters.set(0, JavaType.ShallowClass.build(PKMAC_BUILDER));
                            JavaType.Method target = method.withParameterTypes(parameters);
                            unwrapped = unwrapped.withMethodType(target)
                                    .withName(unwrapped.getName().withType(target));
                        }
                        return unwrapped;
                    }
                }.visit(tree, ctx);
                if (migrated == null) migrated = tree;
                Tree renamed = new ChangeType(OLD_DELTA, NEW_DELTA, true)
                        .getVisitor().visit(migrated, ctx);
                return renamed == null ? migrated : renamed;
            }
        };
    }
}
