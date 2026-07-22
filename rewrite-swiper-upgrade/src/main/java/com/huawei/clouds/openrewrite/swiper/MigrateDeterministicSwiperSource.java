package com.huawei.clouds.openrewrite.swiper;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Apply only public-entry and constructor-literal changes that preserve Swiper behavior. */
public final class MigrateDeterministicSwiperSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Swiper 12 JavaScript and TypeScript source";
    }

    @Override
    public String getDescription() {
        return "Normalize exact legacy core/bundle entry points, move named-only built-in module imports to " +
               "swiper/modules, and rename the legacy container selector only in proven Swiper constructors.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> constructors = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!SwiperSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> previous = constructors;
                Map<String, Integer> previousDeclarations = declarationCounts;
                constructors = new HashSet<>();
                declarationCounts = new HashMap<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = SwiperSupport.moduleName(visited);
                        if ((SwiperSupport.PACKAGE.equals(module) || "swiper/bundle".equals(module) ||
                             SwiperSupport.JS_ENTRIES.containsKey(module)) &&
                            !visited.printTrimmed(getCursor()).startsWith("import type ")) {
                            String binding = SwiperSupport.defaultBinding(visited);
                            if (binding != null) constructors.add(binding);
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                constructors = previous;
                declarationCounts = previousDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = SwiperSupport.moduleName(visited);
                String target = SwiperSupport.JS_ENTRIES.get(module);
                if (target != null) {
                    if (isTypeOnly(visited)) return visited;
                    if (namedModulesOnly(visited)) {
                        return withModule(visited, "swiper/modules");
                    }
                    if (hasNamedBindings(visited)) return visited;
                    return withModule(visited, target);
                }
                target = SwiperSupport.cssTarget(module);
                if (target != null) return withModule(visited, target);
                if (SwiperSupport.PACKAGE.equals(module) && !isTypeOnly(visited) && namedModulesOnly(visited)) {
                    return withModule(visited, "swiper/modules");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String function = visited.getFunction().toString().trim();
                if (!"import".equals(function)) return visited;
                return visited.withArguments(normalizeLoaderArgument(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && "import".equals(visited.getSimpleName())) {
                    return visited.withArguments(normalizeLoaderArgument(visited.getArguments()));
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String clazz = visited.getClazz() == null ? "" : visited.getClazz().toString().trim();
                if (!constructors.contains(clazz) || declarationCounts.getOrDefault(clazz, 0) != 0 ||
                    visited.getArguments().isEmpty() ||
                    !(visited.getArguments().get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String selector)) return visited;
                String migrated = selector.replaceAll("(?<![A-Za-z0-9_-])\\.swiper-container(?![A-Za-z0-9_-])", ".swiper");
                if (migrated.equals(selector)) return visited;
                return visited.withArguments(ListUtils.map(visited.getArguments(), argument ->
                        argument == literal ? stringLiteral(literal, migrated) : argument));
            }

            private boolean namedModulesOnly(JS.Import declaration) {
                JS.ImportClause clause = declaration.getImportClause();
                if (clause == null || clause.getName() != null ||
                    !(clause.getNamedBindings() instanceof JS.NamedImports named) || named.getElements().isEmpty()) {
                    return false;
                }
                return named.getElements().stream().allMatch(specifier ->
                        SwiperSupport.MODULES.contains(SwiperSupport.importedName(specifier)));
            }

            private boolean hasNamedBindings(JS.Import declaration) {
                return declaration.getImportClause() != null &&
                       declaration.getImportClause().getNamedBindings() instanceof JS.NamedImports named &&
                       !named.getElements().isEmpty();
            }

            private boolean isTypeOnly(JS.Import declaration) {
                return declaration.printTrimmed(getCursor()).startsWith("import type ");
            }

            private List<Expression> normalizeLoaderArgument(List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return arguments;
                String target = SwiperSupport.JS_ENTRIES.get(module);
                if (target == null) return arguments;
                return ListUtils.map(arguments, argument -> argument == literal ? stringLiteral(literal, target) : argument);
            }

            private JS.Import withModule(JS.Import declaration, String target) {
                if (!(declaration.getModuleSpecifier() instanceof J.Literal literal)) return declaration;
                return declaration.withModuleSpecifier(stringLiteral(literal, target));
            }

            private J.Literal stringLiteral(J.Literal literal, String target) {
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return literal.withValue(target).withValueSource(quote + target + quote);
            }
        };
    }
}
