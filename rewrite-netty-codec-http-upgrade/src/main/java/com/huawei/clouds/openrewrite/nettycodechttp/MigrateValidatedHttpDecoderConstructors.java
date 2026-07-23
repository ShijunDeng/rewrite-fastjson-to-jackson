package com.huawei.clouds.openrewrite.nettycodechttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Cursor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Replace deprecated validation-enabled HTTP decoder constructors with HttpDecoderConfig. */
public final class MigrateValidatedHttpDecoderConstructors extends Recipe {
    private static final String CONFIG = "io.netty.handler.codec.http.HttpDecoderConfig";
    private static final List<String> TYPES = List.of(
            "io.netty.handler.codec.http.HttpRequestDecoder",
            "io.netty.handler.codec.http.HttpResponseDecoder",
            "io.netty.handler.codec.http.HttpServerCodec");

    @Override
    public String getDisplayName() {
        return "Migrate validation-enabled Netty HTTP decoder constructors";
    }

    @Override
    public String getDescription() {
        return "Replace deprecated HttpRequestDecoder, HttpResponseDecoder and HttpServerCodec constructors only " +
               "when validateHeaders is the literal true, preserving limits, option order and evaluation count.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return NettyCodecHttpSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visitedTree = super.visitNewClass(newClass, ctx);
                if (!(visitedTree instanceof J.NewClass visited) || visited.getBody() != null) return visitedTree;
                String target = TYPES.stream()
                        .filter(type -> TypeUtils.isOfClassType(visited.getType(), type)).findFirst().orElse(null);
                if (target == null) return visited;
                List<Expression> arguments = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (arguments.size() < 4 || arguments.size() > 7 ||
                    !(arguments.get(3) instanceof J.Literal literal) || !Boolean.TRUE.equals(literal.getValue())) {
                    return visited;
                }

                boolean qualifyConfig = hasSimpleNameConflict(getCursor());
                StringBuilder source = new StringBuilder("new ")
                        .append(qualifyConfig ? CONFIG : "HttpDecoderConfig")
                        .append("()")
                        .append(".setMaxInitialLineLength(#{any(int)})")
                        .append(".setMaxHeaderSize(#{any(int)})")
                        .append(".setMaxChunkSize(#{any(int)})");
                if (arguments.size() >= 5) source.append(".setInitialBufferSize(#{any(int)})");
                if (arguments.size() >= 6) source.append(".setAllowDuplicateContentLengths(#{any(boolean)})");
                if (arguments.size() == 7) source.append(".setAllowPartialChunks(#{any(boolean)})");

                Object[] parameters = switch (arguments.size()) {
                    case 4 -> new Object[]{arguments.get(0), arguments.get(1), arguments.get(2)};
                    case 5 -> new Object[]{arguments.get(0), arguments.get(1), arguments.get(2), arguments.get(4)};
                    case 6 -> new Object[]{arguments.get(0), arguments.get(1), arguments.get(2), arguments.get(4), arguments.get(5)};
                    case 7 -> new Object[]{arguments.get(0), arguments.get(1), arguments.get(2), arguments.get(4), arguments.get(5), arguments.get(6)};
                    default -> throw new IllegalStateException("unreachable");
                };
                if (!qualifyConfig) maybeAddImport(CONFIG);
                JavaTemplate template = qualifyConfig
                        ? JavaTemplate.builder(source.toString()).javaParser(targetParser()).build()
                        : JavaTemplate.builder(source.toString()).imports(CONFIG).javaParser(targetParser()).build();
                Expression config = template
                        .apply(new Cursor(updateCursor(visited), arguments.get(0)),
                                arguments.get(0).getCoordinates().replace(), parameters);
                J.NewClass migrated = visited.withArguments(List.of(config));
                JavaType.Method constructor = migrated.getConstructorType();
                if (constructor != null) {
                    JavaType configType = config.getType() == null ? JavaType.ShallowClass.build(CONFIG) : config.getType();
                    migrated = migrated.withConstructorType(constructor
                            .withParameterNames(List.of("config"))
                            .withParameterTypes(List.of(configType)));
                }
                return migrated;
            }
        };
    }

    private static boolean hasSimpleNameConflict(Cursor cursor) {
        J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
        if (cu == null) return false;
        boolean importedConflict = cu.getImports().stream().map(J.Import::getTypeName)
                .anyMatch(type -> type.endsWith(".HttpDecoderConfig") && !CONFIG.equals(type));
        if (importedConflict) return true;
        AtomicBoolean declaredConflict = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, found);
                if ("HttpDecoderConfig".equals(visited.getSimpleName())) found.set(true);
                return visited;
            }
        }.visit(cu, declaredConflict);
        return declaredConflict.get();
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package io.netty.handler.codec.http;
                public final class HttpDecoderConfig {
                    public HttpDecoderConfig setMaxInitialLineLength(int value) { return this; }
                    public HttpDecoderConfig setMaxHeaderSize(int value) { return this; }
                    public HttpDecoderConfig setMaxChunkSize(int value) { return this; }
                    public HttpDecoderConfig setInitialBufferSize(int value) { return this; }
                    public HttpDecoderConfig setAllowDuplicateContentLengths(boolean value) { return this; }
                    public HttpDecoderConfig setAllowPartialChunks(boolean value) { return this; }
                }
                """);
    }
}
