package com.huawei.clouds.openrewrite.feignjackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Mark exact codec construction and extension points whose behavior cannot be inferred mechanically. */
public final class FindFeignJackson13SourceRisks extends Recipe {
    private static final String DECODER_DEFAULT =
            "Feign Jackson 12+ maps HTTP 404/204 to Util.emptyValueOf(type), while 10/11 did not; also revalidate " +
            "empty bodies, response charset, unknown properties, Optional/collection defaults, and Jackson 2.18 coercion";
    private static final String DECODER_CUSTOM =
            "This JacksonDecoder receives caller-owned modules/ObjectMapper; verify its Jackson 2.18 module versions, " +
            "polymorphic typing security, coercion, naming, date/time, unknown-property, charset, and 404/204 behavior";
    private static final String ENCODER_DEFAULT =
            "JacksonEncoder's default mapper still omits nulls and enables indentation, but the managed Jackson line " +
            "moves to 2.18.3; snapshot JSON property names, inclusion, dates, enums, numbers, records, and escaping";
    private static final String ENCODER_CUSTOM =
            "This JacksonEncoder receives caller-owned modules/ObjectMapper; verify Jackson 2.18 module alignment, " +
            "inclusion, naming, date/time, enum/number formats, polymorphic typing, escaping, and content type";
    private static final String ITERATOR =
            "JacksonIteratorDecoder changed Iterator.next()/hasNext behavior after 10.4 and maps 404/204 to empty values " +
            "in 12+; verify direct next(), exhaustion, NoSuchElementException, early close, parse failure, and charset";
    private static final String SUBCLASS =
            "Custom Feign Jackson codec subclass detected; recompile against Feign 13.6/Jackson 2.18.3 and verify " +
            "decode/encode exception contracts, empty responses, body ownership, mapper configuration, and thread safety";

    @Override
    public String getDisplayName() {
        return "Find Feign Jackson 13 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact JacksonDecoder, JacksonEncoder, JacksonIteratorDecoder, and codec subclass nodes that need " +
               "application-specific behavioral regression decisions after the selected upgrade.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedFeignJacksonDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null) return visited;
                String name = type.getFullyQualifiedName();
                if ("feign.jackson.JacksonDecoder".equals(name)) {
                    return mark(visited, noArguments(visited) ? DECODER_DEFAULT : DECODER_CUSTOM);
                }
                if ("feign.jackson.JacksonEncoder".equals(name)) {
                    return mark(visited, noArguments(visited) ? ENCODER_DEFAULT : ENCODER_CUSTOM);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                return MigrateFeignJackson13Apis.methodOn(method, "feign.jackson.JacksonIteratorDecoder", "create")
                        ? mark(visited, ITERATOR) : visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                TypeTree base = visited.getExtends();
                if (base == null || !codecType(base.getType())) return visited;
                return visited.withExtends(mark(base, SUBCLASS));
            }
        };
    }

    private static boolean codecType(JavaType type) {
        return TypeUtils.isAssignableTo("feign.jackson.JacksonDecoder", type) ||
               TypeUtils.isAssignableTo("feign.jackson.JacksonEncoder", type);
    }

    private static boolean noArguments(J.NewClass newClass) {
        return newClass.getArguments().isEmpty() ||
               newClass.getArguments().stream().allMatch(J.Empty.class::isInstance);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
