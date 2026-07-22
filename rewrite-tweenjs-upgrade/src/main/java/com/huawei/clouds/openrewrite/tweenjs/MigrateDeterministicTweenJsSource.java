package com.huawei.clouds.openrewrite.tweenjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Apply only entry-point and main-group configuration changes that preserve target behavior. */
public final class MigrateDeterministicTweenJsSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Tween.js 23 source and configuration";
    }

    @Override
    public String getDescription() {
        return "Normalize exact physical Tween.js distribution imports/require calls to the exported package root " +
               "and remove a redundant explicit exported main group from proven Tween constructor calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> tweenObjects = Set.of();
            private Set<String> tweenConstructors = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previousObjects = tweenObjects;
                Set<String> previousConstructors = tweenConstructors;
                Map<String, Integer> previousDeclarations = declarationCounts;
                tweenObjects = new HashSet<>();
                tweenConstructors = new HashSet<>();
                declarationCounts = TweenJsSupport.declarationCounts(cu, ctx);
                collectBindings(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                tweenObjects = previousObjects;
                tweenConstructors = previousConstructors;
                declarationCounts = previousDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!TweenJsSupport.isDistributionEntry(TweenJsSupport.moduleName(visited)) ||
                    !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                return visited.withModuleSpecifier(rootLiteral(literal));
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String function = TweenJsSupport.expressionName(visited.getFunction());
                if (!Set.of("require", "import").contains(function) ||
                    "require".equals(function) && declarationCounts.getOrDefault("require", 0) != 0) return visited;
                return visited.withArguments(normalizeSoleDistributionArgument(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && Set.of("require", "import").contains(visited.getSimpleName())) {
                    if ("require".equals(visited.getSimpleName()) &&
                        declarationCounts.getOrDefault("require", 0) != 0) return visited;
                    return visited.withArguments(normalizeSoleDistributionArgument(visited.getArguments()));
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (visited.getArguments().size() != 2 || !isOwnedTweenConstructor(visited.getClazz()) ||
                    !(visited.getArguments().get(1) instanceof J.Identifier group) ||
                    !isUnshadowedImport(tweenObjects, group.getSimpleName())) return visited;
                return visited.withArguments(List.of(visited.getArguments().get(0)));
            }

            private boolean isOwnedTweenConstructor(TypeTree clazz) {
                String rendered = clazz == null ? "" : clazz.toString().trim();
                if (isUnshadowedImport(tweenConstructors, rendered)) return true;
                for (String object : tweenObjects) {
                    if (isUnshadowedImport(tweenObjects, object) && (object + ".Tween").equals(rendered)) return true;
                }
                return false;
            }

            private boolean isUnshadowedImport(Set<String> bindings, String name) {
                return bindings.contains(name) && declarationCounts.getOrDefault(name, 0) == 0;
            }

            private List<Expression> normalizeSoleDistributionArgument(List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module) || !TweenJsSupport.isDistributionEntry(module)) {
                    return arguments;
                }
                return ListUtils.map(arguments, argument -> argument == literal ? rootLiteral(literal) : argument);
            }

            private J.Literal rootLiteral(J.Literal literal) {
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return literal.withValue(TweenJsSupport.PACKAGE)
                        .withValueSource(quote + TweenJsSupport.PACKAGE + quote);
            }

            private void collectBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = TweenJsSupport.moduleName(visited);
                        if (!TweenJsSupport.PACKAGE.equals(module) && !TweenJsSupport.isDistributionEntry(module)) {
                            return visited;
                        }
                        String defaultBinding = TweenJsSupport.defaultBinding(visited);
                        if (defaultBinding != null) tweenObjects.add(defaultBinding);
                        String namespaceBinding = TweenJsSupport.namespaceBinding(visited);
                        if (namespaceBinding != null) tweenObjects.add(namespaceBinding);
                        String tween = TweenJsSupport.importedBinding(visited, "Tween");
                        if (tween != null) tweenConstructors.add(tween);
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
