package com.huawei.clouds.openrewrite.markdownit;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Marks source constructs whose behavior cannot be migrated without application intent. */
public final class FindMarkdownItSourceRisks extends Recipe {
    private static final Set<String> RULER_METHODS = Set.of(
            "before", "after", "at", "push", "enable", "disable", "enableOnly");

    @Override
    public String getDisplayName() {
        return "Find markdown-it 14 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks deep/internal module references, CommonJS-to-ESM boundaries, removed delimiter jump state, " +
               "custom ruler registrations, renderer overrides, and unresolved text_collapse references.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private boolean executableConfig;
            private boolean importsStateInline;
            private Set<String> instances = Set.of();
            private Map<String, Integer> declarations = Map.of();
            private Set<String> writtenNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean old = active;
                boolean oldExecutableConfig = executableConfig;
                boolean oldStateInline = importsStateInline;
                Set<String> oldInstances = instances;
                Map<String, Integer> oldDeclarations = declarations;
                Set<String> oldWrittenNames = writtenNames;
                active = MarkdownItSupport.isProjectPath(cu.getSourcePath());
                executableConfig = MarkdownItSupport.isExecutableConfig(cu.getSourcePath());
                importsStateInline = false;
                instances = new HashSet<>();
                declarations = inventoryDeclarations(cu, ctx);
                writtenNames = inventoryWrites(cu, ctx);
                if (active) scanOwnership(cu, ctx);
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = old;
                executableConfig = oldExecutableConfig;
                importsStateInline = oldStateInline;
                instances = oldInstances;
                declarations = oldDeclarations;
                writtenNames = oldWrittenNames;
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !(visited.getValue() instanceof String value) ||
                    !moduleReferenceLiteral(visited)) return visited;
                if (!value.equals(MarkdownItSupport.normalizedRoot(value))) {
                    return SearchResult.found(visited,
                            "markdown-it 14 no longer resolves this legacy index subpath; use the public markdown-it entry (the deterministic recipe handles static/dynamic import and direct require owners)");
                }
                if (MarkdownItSupport.deepModule(value)) {
                    J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                    if (invocation != null && value.equals(MarkdownItSupport.requireModule(invocation)) &&
                        invocation.getArguments().stream().anyMatch(argument -> argument.getId().equals(visited.getId()))) {
                        return SearchResult.found(visited,
                                "markdown-it 14 deep lib modules are ESM .mjs files; CommonJS require cannot safely load this internal module, so migrate the caller to ESM/public APIs and verify exports");
                    }
                    return SearchResult.found(visited,
                            "This code imports a markdown-it internal lib module; v14 renamed internals to .mjs and does not promise their API, so verify the target file/export and plugin behavior");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active || !rulerChain(visited.getSelect()) || !RULER_METHODS.contains(visited.getSimpleName())) {
                    return visited;
                }
                if (inlineRuler2(visited.getSelect()) && !visited.getArguments().isEmpty()) {
                    Expression marked = markTextCollapseReference(
                            visited.getArguments().get(0),
                            Set.of("enable", "disable", "enableOnly").contains(visited.getSimpleName()));
                    if (marked != visited.getArguments().get(0)) {
                        List<Expression> arguments = new ArrayList<>(visited.getArguments());
                        arguments.set(0, marked);
                        return visited.withArguments(arguments);
                    }
                }
                if (!ownedRoot(visited.getSelect())) return visited;
                return SearchResult.found(visited,
                        "Custom markdown-it ruler integration crosses parser changes: delimiter jump was removed, text_special/text_join were added, and invalid plugin progress now throws; test rule order, token positions, escaping, and pathological input");
            }

            private Expression markTextCollapseReference(Expression argument, boolean allowArray) {
                if (argument instanceof J.Literal literal && "text_collapse".equals(literal.getValue())) {
                    return SearchResult.found(literal,
                            "markdown-it 13 renamed inline rule text_collapse to fragments_join; ownership was not strong enough for AUTO, so update this exact rule anchor and retest ordering");
                }
                if (!allowArray || !(argument instanceof J.NewArray array) || array.getInitializer() == null) {
                    return argument;
                }
                boolean changed = false;
                List<Expression> initializer = new ArrayList<>(array.getInitializer().size());
                for (Expression element : array.getInitializer()) {
                    Expression marked = markTextCollapseReference(element, false);
                    changed |= marked != element;
                    initializer.add(marked);
                }
                return changed ? array.withInitializer(initializer) : argument;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (active && rendererRule(visited.getVariable()) && ownedRoot(visited.getVariable())) {
                    return SearchResult.found(visited,
                            "Custom markdown-it renderer output must be regression-tested: v14 changed image-alt HTML/hardbreak rendering and later 14.x releases changed CommonMark edge cases and hard line breaks");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if (!active || !importsStateInline || !"jump".equals(visited.getSimpleName()) ||
                    !(visited.getTarget() instanceof J.ArrayAccess access) ||
                    !(access.getIndexed() instanceof J.FieldAccess delimiters) ||
                    !"delimiters".equals(delimiters.getSimpleName())) return visited;
                return SearchResult.found(visited,
                        "StateInline.delimiters[].jump was removed in markdown-it 12.3; redesign this delimiter pairing logic using current token/delimiter state and add nested/pathological cases");
            }

            private boolean rulerChain(Expression select) {
                if (!(select instanceof J.FieldAccess ruler) ||
                    !(Set.of("ruler", "ruler2").contains(ruler.getSimpleName())) ||
                    !(ruler.getTarget() instanceof J.FieldAccess parser)) return false;
                return ("inline".equals(parser.getSimpleName()) && Set.of("ruler", "ruler2").contains(ruler.getSimpleName())) ||
                       (Set.of("block", "core").contains(parser.getSimpleName()) && "ruler".equals(ruler.getSimpleName()));
            }

            private boolean inlineRuler2(Expression select) {
                return select instanceof J.FieldAccess ruler && "ruler2".equals(ruler.getSimpleName()) &&
                       ruler.getTarget() instanceof J.FieldAccess inline && "inline".equals(inline.getSimpleName());
            }

            private boolean rendererRule(Expression expression) {
                return expression instanceof J.FieldAccess rule &&
                       rule.getTarget() instanceof J.FieldAccess rules && "rules".equals(rules.getSimpleName()) &&
                       rules.getTarget() instanceof J.FieldAccess renderer && "renderer".equals(renderer.getSimpleName());
            }

            private boolean ownedRoot(Expression expression) {
                String root = MarkdownItSupport.rootIdentifier(expression);
                return instances.contains(root) && declarations.getOrDefault(root, 0) == 1 &&
                       !writtenNames.contains(root);
            }

            private boolean moduleReferenceLiteral(J.Literal literal) {
                JS.Import imported = getCursor().firstEnclosing(JS.Import.class);
                if (imported != null && imported.getModuleSpecifier() != null &&
                    imported.getModuleSpecifier().getId().equals(literal.getId())) return true;
                JS.ExportDeclaration exported = getCursor().firstEnclosing(JS.ExportDeclaration.class);
                if (exported != null && exported.getModuleSpecifier() != null &&
                    exported.getModuleSpecifier().getId().equals(literal.getId())) return true;
                J.MethodInvocation method = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (method != null && method.getSelect() == null &&
                    Set.of("require", "import").contains(method.getSimpleName()) &&
                    method.getArguments().stream().anyMatch(argument -> argument.getId().equals(literal.getId()))) {
                    return true;
                }
                JS.FunctionCall function = getCursor().firstEnclosing(JS.FunctionCall.class);
                if (function != null && Set.of("require", "import").contains(function.getFunction().toString().trim()) &&
                    function.getArguments().stream().anyMatch(argument -> argument.getId().equals(literal.getId()))) {
                    return true;
                }
                JS.ImportType importType = getCursor().firstEnclosing(JS.ImportType.class);
                if (importType != null && importType.getArgumentAndAttributes().stream()
                        .anyMatch(argument -> argument.getId().equals(literal.getId()))) return true;
                return executableConfig;
            }

            private void scanOwnership(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> esmConstructors = new HashSet<>();
                Set<String> cjsConstructors = new HashSet<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = MarkdownItSupport.moduleName(visited);
                        if (MarkdownItSupport.PACKAGE.equals(MarkdownItSupport.normalizedRoot(module)) &&
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
                        if (module.matches("markdown-it/lib/(?:rules_inline/)?state_inline(?:\\.m?js)?")) {
                            importsStateInline = true;
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

                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        String constructor = "";
                        Expression initializer = visited.getInitializer();
                        if (initializer instanceof J.NewClass created &&
                            created.getClazz() instanceof J.Identifier identifier) {
                            constructor = identifier.getSimpleName();
                        } else if (initializer instanceof J.MethodInvocation call && call.getSelect() == null) {
                            constructor = call.getSimpleName();
                        }
                        if (!writtenNames.contains(constructor) &&
                            ((esmConstructors.contains(constructor) && declarations.getOrDefault(constructor, 0) == 0) ||
                             (cjsConstructors.contains(constructor) && declarations.getOrDefault(constructor, 0) == 1))) {
                            instances.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
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
