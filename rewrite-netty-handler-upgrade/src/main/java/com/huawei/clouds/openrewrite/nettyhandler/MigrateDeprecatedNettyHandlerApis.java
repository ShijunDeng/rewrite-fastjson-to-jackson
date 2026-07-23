package com.huawei.clouds.openrewrite.nettyhandler;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

/**
 * Apply the constructor migration that the official method-invocation building block cannot express.
 */
public final class MigrateDeprecatedNettyHandlerApis extends Recipe {
    private static final String RULE_FILTER = "io.netty.handler.ipfilter.RuleBasedIpFilter";
    private static final String IP_FILTER_RULE = "io.netty.handler.ipfilter.IpFilterRule";
    @Override
    public String getDisplayName() {
        return "Migrate the deprecated RuleBasedIpFilter constructor";
    }

    @Override
    public String getDescription() {
        return "Make RuleBasedIpFilter's documented accept-if-no-rule default explicit; SslHandler's equivalent " +
               "overload is handled by OpenRewrite's official AddLiteralMethodArgument recipe.";
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
                JavaType.Method constructor = visited.getConstructorType();
                if (!TypeUtils.isOfClassType(visited.getType(), RULE_FILTER) ||
                    constructor == null || !deprecatedRuleFilterConstructor(constructor)) return visited;

                List<Expression> existing = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                List<Expression> arguments = new ArrayList<>();
                if (existing.isEmpty()) {
                    arguments.add(booleanLiteral(true));
                } else {
                    Expression first = existing.get(0);
                    arguments.add(booleanLiteral(true).withPrefix(first.getPrefix()));
                    arguments.add(first.withPrefix(Space.SINGLE_SPACE));
                    arguments.addAll(existing.subList(1, existing.size()));
                }
                J.NewClass migrated = visited.withArguments(arguments);
                List<JavaType> types = new ArrayList<>();
                types.add(JavaType.Primitive.Boolean);
                types.addAll(constructor.getParameterTypes());
                return migrated.withConstructorType(constructor.withParameterTypes(types)
                        .withParameterNames(List.of("acceptIfNotFound", "rules")));
            }

        };
    }

    private static boolean deprecatedRuleFilterConstructor(JavaType.Method constructor) {
        List<JavaType> parameters = constructor.getParameterTypes();
        if (parameters.size() != 1 || !(parameters.get(0) instanceof JavaType.Array array)) return false;
        return TypeUtils.isOfClassType(array.getElemType(), IP_FILTER_RULE);
    }

    private static J.Literal booleanLiteral(boolean value) {
        return new J.Literal(Tree.randomId(), Space.EMPTY, Markers.EMPTY, value,
                Boolean.toString(value), List.of(), JavaType.Primitive.Boolean);
    }
}
