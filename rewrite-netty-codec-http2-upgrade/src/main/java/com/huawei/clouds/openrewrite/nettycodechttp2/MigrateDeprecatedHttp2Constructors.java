package com.huawei.clouds.openrewrite.nettycodechttp2;

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

/** Make behavior-preserving target constructors explicit and remove a literal no-op Huffman capacity. */
public final class MigrateDeprecatedHttp2Constructors extends Recipe {
    private static final String CONNECTION_DECODER =
            "io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder";
    private static final String DECOMPRESSOR =
            "io.netty.handler.codec.http2.DelegatingDecompressorFrameListener";
    private static final String HEADERS_DECODER =
            "io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder";

    @Override
    public String getDisplayName() {
        return "Migrate behavior-preserving deprecated Netty HTTP/2 constructors";
    }

    @Override
    public String getDescription() {
        return "Append target parameters with the exact defaults used by deprecated connection-decoder and " +
               "decompressor constructors, and remove the documented no-op Huffman capacity only when it is an " +
               "integer literal with no observable evaluation.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return NettyCodecHttp2Support.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (visited.getBody() != null) return visited;
                List<Expression> args = arguments(visited);
                JavaType.Method constructor = visited.getConstructorType();
                if (constructor == null) return visited;

                if (TypeUtils.isOfClassType(visited.getType(), CONNECTION_DECODER) &&
                    args.size() == 6 && sixArgumentConnectionDecoder(constructor)) {
                    return append(visited, booleanLiteral(true), "validateHeaders", JavaType.Primitive.Boolean);
                }

                if (TypeUtils.isOfClassType(visited.getType(), DECOMPRESSOR)) {
                    if (args.size() == 2 && constructor.getParameterTypes().size() == 2) {
                        return append(visited, intLiteral(0), "maxAllocation", JavaType.Primitive.Int);
                    }
                    if (args.size() == 3 && constructor.getParameterTypes().size() == 3 &&
                        constructor.getParameterTypes().get(2) == JavaType.Primitive.Boolean) {
                        return append(visited, intLiteral(0), "maxAllocation", JavaType.Primitive.Int);
                    }
                }

                if (TypeUtils.isOfClassType(visited.getType(), HEADERS_DECODER) &&
                    args.size() == 3 && noOpHuffmanSignature(constructor) &&
                    args.get(2) instanceof J.Literal literal && literal.getValue() instanceof Integer) {
                    return removeLast(visited);
                }
                return visited;
            }
        };
    }

    private static boolean sixArgumentConnectionDecoder(JavaType.Method constructor) {
        List<JavaType> parameters = constructor.getParameterTypes();
        return parameters.size() == 6 && parameters.get(4) == JavaType.Primitive.Boolean &&
               parameters.get(5) == JavaType.Primitive.Boolean;
    }

    private static boolean noOpHuffmanSignature(JavaType.Method constructor) {
        List<JavaType> parameters = constructor.getParameterTypes();
        return parameters.size() == 3 && parameters.get(0) == JavaType.Primitive.Boolean &&
               parameters.get(1) == JavaType.Primitive.Long && parameters.get(2) == JavaType.Primitive.Int;
    }

    private static List<Expression> arguments(J.NewClass newClass) {
        return newClass.getArguments().stream().filter(argument -> !(argument instanceof J.Empty)).toList();
    }

    private static J.NewClass append(J.NewClass newClass, J.Literal literal, String parameterName,
                                     JavaType parameterType) {
        List<Expression> arguments = new ArrayList<>(newClass.getArguments());
        arguments.add(literal);
        J.NewClass migrated = newClass.withArguments(arguments);
        JavaType.Method constructor = migrated.getConstructorType();
        if (constructor == null) return migrated;
        List<JavaType> parameterTypes = new ArrayList<>(constructor.getParameterTypes());
        parameterTypes.add(parameterType);
        List<String> parameterNames = new ArrayList<>(constructor.getParameterNames());
        parameterNames.add(parameterName);
        return migrated.withConstructorType(constructor.withParameterTypes(parameterTypes)
                .withParameterNames(parameterNames));
    }

    private static J.NewClass removeLast(J.NewClass newClass) {
        List<Expression> arguments = new ArrayList<>(newClass.getArguments());
        arguments.remove(arguments.size() - 1);
        J.NewClass migrated = newClass.withArguments(arguments);
        JavaType.Method constructor = migrated.getConstructorType();
        if (constructor == null) return migrated;
        List<JavaType> parameterTypes = new ArrayList<>(constructor.getParameterTypes());
        parameterTypes.remove(parameterTypes.size() - 1);
        List<String> parameterNames = new ArrayList<>(constructor.getParameterNames());
        parameterNames.remove(parameterNames.size() - 1);
        return migrated.withConstructorType(constructor.withParameterTypes(parameterTypes)
                .withParameterNames(parameterNames));
    }

    private static J.Literal booleanLiteral(boolean value) {
        return new J.Literal(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, value,
                Boolean.toString(value), List.of(), JavaType.Primitive.Boolean);
    }

    private static J.Literal intLiteral(int value) {
        return new J.Literal(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, value,
                Integer.toString(value), List.of(), JavaType.Primitive.Int);
    }
}
