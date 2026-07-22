package com.huawei.clouds.openrewrite.markdownit;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies only source rewrites that have an unambiguous markdown-it 14 equivalent. */
public final class MigrateDeterministicMarkdownItSource extends Recipe {
    private static final Set<String> RULE_NAME_METHODS = Set.of(
            "before", "after", "at", "enable", "disable", "enableOnly");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic markdown-it 14 source constructs";
    }

    @Override
    public String getDescription() {
        return "Normalizes removed index subpaths, adds .mjs to known static/dynamic ESM deep imports, and " +
               "renames the owned inline ruler2 text_collapse rule reference to fragments_join.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private Set<String> esmConstructors = Set.of();
            private Set<String> cjsConstructors = Set.of();
            private Set<String> instances = Set.of();
            private Map<String, Integer> declarations = Map.of();
            private Set<String> writtenNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldActive = active;
                Set<String> oldEsm = esmConstructors;
                Set<String> oldCjs = cjsConstructors;
                Set<String> oldInstances = instances;
                Map<String, Integer> oldDeclarations = declarations;
                Set<String> oldWrittenNames = writtenNames;
                active = MarkdownItSupport.isProjectPath(cu.getSourcePath());
                esmConstructors = new HashSet<>();
                cjsConstructors = new HashSet<>();
                instances = new HashSet<>();
                declarations = inventoryDeclarations(cu, ctx);
                writtenNames = inventoryWrites(cu, ctx);
                if (active) {
                    scanConstructors(cu, ctx);
                    scanInstances(cu, ctx);
                }
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = oldActive;
                esmConstructors = oldEsm;
                cjsConstructors = oldCjs;
                instances = oldInstances;
                declarations = oldDeclarations;
                writtenNames = oldWrittenNames;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String module = MarkdownItSupport.moduleName(visited);
                String replacement = MarkdownItSupport.migratedStaticModule(module);
                return module.equals(replacement) ? visited :
                        visited.withModuleSpecifier(MarkdownItSupport.replaceString(literal, replacement));
            }

            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext ctx) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return visited;
                String replacement = MarkdownItSupport.migratedStaticModule(module);
                return module.equals(replacement) ? visited :
                        visited.withModuleSpecifier(MarkdownItSupport.replaceString(literal, replacement));
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!active || !"import".equals(visited.getFunction().toString().trim())) return visited;
                return visited.withArguments(migrateDynamicImportArguments(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active) return visited;
                String required = MarkdownItSupport.requireModule(visited);
                if (!required.equals(MarkdownItSupport.normalizedRoot(required))) {
                    J.Literal literal = (J.Literal) visited.getArguments().get(0);
                    return visited.withArguments(java.util.List.of(
                            MarkdownItSupport.replaceString(literal, MarkdownItSupport.PACKAGE)));
                }
                if (visited.getSelect() == null && "import".equals(visited.getSimpleName())) {
                    return visited.withArguments(migrateDynamicImportArguments(visited.getArguments()));
                }
                if (RULE_NAME_METHODS.contains(visited.getSimpleName()) && ownedInlineRuler2(visited.getSelect()) &&
                    !visited.getArguments().isEmpty()) {
                    Expression migrated = migrateRuleReference(
                            visited.getArguments().get(0),
                            Set.of("enable", "disable", "enableOnly").contains(visited.getSimpleName()));
                    if (migrated == visited.getArguments().get(0)) return visited;
                    java.util.List<Expression> arguments = new java.util.ArrayList<>(visited.getArguments());
                    arguments.set(0, migrated);
                    return visited.withArguments(arguments);
                }
                return visited;
            }

            private Expression migrateRuleReference(Expression argument, boolean allowArray) {
                if (argument instanceof J.Literal literal && "text_collapse".equals(literal.getValue())) {
                    return MarkdownItSupport.replaceString(literal, "fragments_join");
                }
                if (!allowArray || !(argument instanceof J.NewArray array) || array.getInitializer() == null) {
                    return argument;
                }
                boolean changed = false;
                java.util.List<Expression> initializer = new java.util.ArrayList<>(array.getInitializer().size());
                for (Expression element : array.getInitializer()) {
                    Expression migrated = migrateRuleReference(element, false);
                    changed |= migrated != element;
                    initializer.add(migrated);
                }
                return changed ? array.withInitializer(initializer) : argument;
            }

            private boolean ownedInlineRuler2(Expression select) {
                if (!(select instanceof J.FieldAccess ruler) || !"ruler2".equals(ruler.getSimpleName()) ||
                    !(ruler.getTarget() instanceof J.FieldAccess inline) || !"inline".equals(inline.getSimpleName())) {
                    return false;
                }
                String root = MarkdownItSupport.rootIdentifier(inline.getTarget());
                return instances.contains(root) && declarations.getOrDefault(root, 0) == 1 &&
                       !writtenNames.contains(root);
            }

            private void scanConstructors(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (MarkdownItSupport.PACKAGE.equals(
                                MarkdownItSupport.normalizedRoot(MarkdownItSupport.moduleName(visited))) &&
                            visited.getImportClause() != null && !visited.getImportClause().isTypeOnly()) {
                            if (visited.getImportClause().getName() != null) {
                                esmConstructors.add(visited.getImportClause().getName().getSimpleName());
                            }
                            if (visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) {
                                named.getElements().stream()
                                        .filter(specifier -> !specifier.getImportType() &&
                                                "default".equals(MarkdownItSupport.importedName(specifier)))
                                        .map(MarkdownItSupport::localName)
                                        .filter(name -> !name.isEmpty())
                                        .forEach(esmConstructors::add);
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.MethodInvocation call &&
                            MarkdownItSupport.PACKAGE.equals(
                                    MarkdownItSupport.normalizedRoot(MarkdownItSupport.requireModule(call)))) {
                            cjsConstructors.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private void scanInstances(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (constructedByMarkdownIt(visited.getInitializer())) instances.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean constructedByMarkdownIt(Expression initializer) {
                String constructor = "";
                if (initializer instanceof J.NewClass created && created.getClazz() instanceof J.Identifier identifier) {
                    constructor = identifier.getSimpleName();
                } else if (initializer instanceof J.MethodInvocation call && call.getSelect() == null) {
                    constructor = call.getSimpleName();
                }
                return !writtenNames.contains(constructor) &&
                       ((esmConstructors.contains(constructor) && declarations.getOrDefault(constructor, 0) == 0) ||
                        (cjsConstructors.contains(constructor) && declarations.getOrDefault(constructor, 0) == 1));
            }

            private java.util.List<Expression> migrateDynamicImportArguments(
                    java.util.List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return arguments;
                String replacement = MarkdownItSupport.migratedStaticModule(module);
                if (module.equals(replacement)) return arguments;
                return java.util.List.of(MarkdownItSupport.replaceString(literal, replacement));
            }

            private Map<String, Integer> inventoryDeclarations(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, Integer> result = new HashMap<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        result.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                return result;
            }

            private Set<String> inventoryWrites(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> result = new HashSet<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    private void record(Expression variable) {
                        if (variable instanceof J.Identifier identifier) result.add(identifier.getSimpleName());
                    }

                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext scanCtx) {
                        J.Assignment visited = super.visitAssignment(assignment, scanCtx);
                        record(visited.getVariable());
                        return visited;
                    }

                    @Override
                    public J.AssignmentOperation visitAssignmentOperation(
                            J.AssignmentOperation assignment, ExecutionContext scanCtx) {
                        J.AssignmentOperation visited = super.visitAssignmentOperation(assignment, scanCtx);
                        record(visited.getVariable());
                        return visited;
                    }

                    @Override
                    public JS.AssignmentOperation visitAssignmentOperationExtensions(
                            JS.AssignmentOperation assignment, ExecutionContext scanCtx) {
                        JS.AssignmentOperation visited = super.visitAssignmentOperationExtensions(assignment, scanCtx);
                        record(visited.getVariable());
                        return visited;
                    }
                }.visit(cu, ctx);
                return result;
            }
        };
    }
}
