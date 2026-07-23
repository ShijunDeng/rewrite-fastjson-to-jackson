package com.huawei.clouds.openrewrite.nettycodechttp2;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddLiteralMethodArgument;
import org.openrewrite.java.DeleteMethodArgument;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Make behavior-preserving target constructors explicit and remove a literal no-op Huffman capacity. */
public final class MigrateDeprecatedHttp2Constructors extends Recipe {
    private static final String CONNECTION_DECODER =
            "io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder";
    private static final String DECOMPRESSOR =
            "io.netty.handler.codec.http2.DelegatingDecompressorFrameListener";
    private static final String HEADERS_DECODER =
            "io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder";
    private static final Recipe ADD_CONNECTION_DECODER_VALIDATE_HEADERS =
            new AddLiteralMethodArgument(
                    CONNECTION_DECODER + " <constructor>(" +
                    "io.netty.handler.codec.http2.Http2Connection," +
                    "io.netty.handler.codec.http2.Http2ConnectionEncoder," +
                    "io.netty.handler.codec.http2.Http2FrameReader," +
                    "io.netty.handler.codec.http2.Http2PromisedRequestVerifier,boolean,boolean)",
                    6, true, "boolean");
    private static final Recipe ADD_DECOMPRESSOR_MAX_ALLOCATION =
            new AddLiteralMethodArgument(
                    DECOMPRESSOR + " <constructor>(" +
                    "io.netty.handler.codec.http2.Http2Connection," +
                    "io.netty.handler.codec.http2.Http2FrameListener)",
                    2, 0, "int");
    private static final Recipe ADD_STRICT_DECOMPRESSOR_MAX_ALLOCATION =
            new AddLiteralMethodArgument(
                    DECOMPRESSOR + " <constructor>(" +
                    "io.netty.handler.codec.http2.Http2Connection," +
                    "io.netty.handler.codec.http2.Http2FrameListener,boolean)",
                    3, 0, "int");
    private static final Recipe DELETE_NO_OP_HUFFMAN_CAPACITY =
            new DeleteMethodArgument(
                    HEADERS_DECODER + " <constructor>(boolean,long,int)",
                    2);

    /**
     * Official core recipes that perform every AST mutation after the local safety guards select a node.
     */
    static List<Recipe> officialCoreRecipes() {
        return List.of(
                ADD_CONNECTION_DECODER_VALIDATE_HEADERS,
                ADD_DECOMPRESSOR_MAX_ALLOCATION,
                ADD_STRICT_DECOMPRESSOR_MAX_ALLOCATION,
                DELETE_NO_OP_HUFFMAN_CAPACITY);
    }

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
                    return applyOfficial(
                            ADD_CONNECTION_DECODER_VALIDATE_HEADERS, visited, ctx,
                            getCursor().getParentTreeCursor());
                }

                if (TypeUtils.isOfClassType(visited.getType(), DECOMPRESSOR)) {
                    if (args.size() == 2 && constructor.getParameterTypes().size() == 2) {
                        return applyOfficial(
                                ADD_DECOMPRESSOR_MAX_ALLOCATION, visited, ctx,
                                getCursor().getParentTreeCursor());
                    }
                    if (args.size() == 3 && constructor.getParameterTypes().size() == 3 &&
                        constructor.getParameterTypes().get(2) == JavaType.Primitive.Boolean) {
                        return applyOfficial(
                                ADD_STRICT_DECOMPRESSOR_MAX_ALLOCATION, visited, ctx,
                                getCursor().getParentTreeCursor());
                    }
                }

                if (TypeUtils.isOfClassType(visited.getType(), HEADERS_DECODER) &&
                    args.size() == 3 && noOpHuffmanSignature(constructor) &&
                    args.get(2) instanceof J.Literal literal && literal.getValue() instanceof Integer) {
                    return applyOfficial(
                            DELETE_NO_OP_HUFFMAN_CAPACITY, visited, ctx,
                            getCursor().getParentTreeCursor());
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

    private static J.NewClass applyOfficial(Recipe recipe, J.NewClass newClass, ExecutionContext ctx,
                                            Cursor parent) {
        TreeVisitor<?, ExecutionContext> visitor = recipe.getVisitor();
        return (J.NewClass) visitor.visitNonNull(newClass, ctx, parent);
    }
}
