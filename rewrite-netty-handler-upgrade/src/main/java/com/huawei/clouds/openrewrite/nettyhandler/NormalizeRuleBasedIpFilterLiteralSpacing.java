package com.huawei.clouds.openrewrite.nettyhandler;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Repairs the argument prefixes produced when OpenRewrite 8.87.5 inserts a literal at constructor index zero.
 *
 * <p>The semantic transformation remains owned by the official {@code AddLiteralMethodArgument} recipe. This
 * visitor is intentionally limited to the one typed Netty constructor and only normalizes the two prefixes
 * affected by that recipe.</p>
 */
public final class NormalizeRuleBasedIpFilterLiteralSpacing extends Recipe {
    private static final String RULE_FILTER = "io.netty.handler.ipfilter.RuleBasedIpFilter";
    private static final String IP_FILTER_RULE = "io.netty.handler.ipfilter.IpFilterRule";

    @Override
    public String getDisplayName() {
        return "Normalize inserted RuleBasedIpFilter policy spacing";
    }

    @Override
    public String getDescription() {
        return "Normalize only the argument prefixes affected when the official AddLiteralMethodArgument recipe " +
               "inserts RuleBasedIpFilter's policy at constructor index zero.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return NettyHandlerSupport.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (!migratedRuleFilterConstructor(visited) || visited.getArguments().isEmpty()) return visited;

                Expression first = visited.getArguments().get(0);
                if (!(first instanceof J.Literal literal) || !Boolean.TRUE.equals(literal.getValue())) return visited;

                List<Expression> arguments = new ArrayList<>(visited.getArguments());
                if (first.getPrefix().getComments().isEmpty() &&
                    " ".equals(first.getPrefix().getWhitespace())) {
                    arguments.set(0, first.withPrefix(Space.EMPTY));
                }

                if (arguments.size() > 1) {
                    Expression second = arguments.get(1);
                    if (second.getPrefix().getComments().isEmpty() &&
                        second.getPrefix().getWhitespace().isEmpty()) {
                        arguments.set(1, second.withPrefix(Space.SINGLE_SPACE));
                    }
                }
                return visited.withArguments(arguments);
            }
        };
    }

    private static boolean migratedRuleFilterConstructor(J.NewClass newClass) {
        if (!TypeUtils.isOfClassType(newClass.getType(), RULE_FILTER)) return false;
        JavaType.Method constructor = newClass.getConstructorType();
        if (constructor == null || constructor.getParameterTypes().size() != 2 ||
            constructor.getParameterTypes().get(0) != JavaType.Primitive.Boolean ||
            !(constructor.getParameterTypes().get(1) instanceof JavaType.Array rules)) return false;
        return TypeUtils.isOfClassType(rules.getElemType(), IP_FILTER_RULE);
    }
}
