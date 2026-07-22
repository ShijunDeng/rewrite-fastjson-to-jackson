package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Apply only source/config changes whose binding and result are deterministic. */
public final class MigrateDeterministicDiagramJsMinimapSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic diagram-js-minimap 5 source and configuration";
    }

    @Override
    public String getDescription() {
        return "Normalize explicit ESM/CommonJS distribution imports to the package's stable public root and remove " +
               "duplicate occurrences of the proven minimap module from direct additionalModules arrays.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> minimapBindings = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previous = minimapBindings;
                minimapBindings = new HashSet<>();
                collectBindings(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                minimapBindings = previous;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = DiagramJsMinimapSupport.moduleName(visited);
                if (DiagramJsMinimapSupport.PACKAGE.equals(module) ||
                    !DiagramJsMinimapSupport.isRootOrDistribution(module) ||
                    !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(DiagramJsMinimapSupport.PACKAGE)
                        .withValueSource(quote + DiagramJsMinimapSupport.PACKAGE + quote));
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!"additionalModules".equals(DiagramJsMinimapSupport.propertyName(visited.getName())) ||
                    !(visited.getInitializer() instanceof J.NewArray modules)) return visited;
                Set<String> seen = new HashSet<>();
                java.util.List<Expression> deduplicated = ListUtils.map(modules.getInitializer(), module -> {
                    if (module instanceof J.Identifier identifier &&
                        minimapBindings.contains(identifier.getSimpleName()) &&
                        !seen.add(identifier.getSimpleName())) return null;
                    return module;
                });
                if (deduplicated.size() == modules.getInitializer().size()) return visited;
                JContainer<Expression> original = modules.getPadding().getInitializer();
                JContainer<Expression> migrated = JContainer.withElements(original, deduplicated);
                java.util.List<JRightPadded<Expression>> padded = new java.util.ArrayList<>(
                        migrated.getPadding().getElements());
                if (!padded.isEmpty()) {
                    int last = padded.size() - 1;
                    padded.set(last, padded.get(last).withAfter(original.getLastSpace()));
                    migrated = migrated.getPadding().withElements(padded);
                }
                return visited.withInitializer(modules.getPadding().withInitializer(migrated));
            }

            private void collectBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (DiagramJsMinimapSupport.isRootOrDistribution(
                                DiagramJsMinimapSupport.moduleName(visited))) {
                            String binding = DiagramJsMinimapSupport.defaultBinding(visited);
                            if (binding != null) minimapBindings.add(binding);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
