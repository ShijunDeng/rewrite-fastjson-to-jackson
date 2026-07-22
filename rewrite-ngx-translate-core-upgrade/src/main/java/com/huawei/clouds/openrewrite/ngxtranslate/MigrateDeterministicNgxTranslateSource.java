package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rename only imported public symbols and methods on LST-proven TranslateService receivers. */
public final class MigrateDeterministicNgxTranslateSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic ngx-translate 17 source APIs";
    }

    @Override
    public String getDescription() {
        return "Rename v17 public fallback/default implementations and deprecated language methods/events only when " +
               "imports, declarations and TranslateService injection are proven by the JavaScript LST.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> identifierRenames = Map.of();
            private Map<UUID, String> importRenames = Map.of();
            private Set<String> bareServiceReceivers = Set.of();
            private Set<String> fieldServiceReceivers = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxTranslateSupport.isProjectPath(cu.getSourcePath())) return cu;
                Map<String, String> previousIdentifiers = identifierRenames;
                Map<UUID, String> previousImports = importRenames;
                Set<String> previousBare = bareServiceReceivers;
                Set<String> previousFields = fieldServiceReceivers;
                Map<String, Integer> previousDeclarations = declarationCounts;
                identifierRenames = new HashMap<>();
                importRenames = new HashMap<>();
                bareServiceReceivers = new HashSet<>();
                fieldServiceReceivers = new HashSet<>();
                declarationCounts = new HashMap<>();
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                identifierRenames = previousIdentifiers;
                importRenames = previousImports;
                bareServiceReceivers = previousBare;
                fieldServiceReceivers = previousFields;
                declarationCounts = previousDeclarations;
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                String replacement = importRenames.get(visited.getId());
                if (replacement == null) return visited;
                if (visited.getSpecifier() instanceof J.Identifier identifier) {
                    return visited.withSpecifier(identifier.withSimpleName(replacement));
                }
                if (visited.getSpecifier() instanceof JS.Alias alias) {
                    return visited.withSpecifier(alias.withPropertyName(
                            alias.getPropertyName().withSimpleName(replacement)));
                }
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                String replacement = identifierRenames.get(visited.getSimpleName());
                return replacement == null || isSyntaxName(visited) ? visited : visited.withSimpleName(replacement);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!ownedReceiver(visited.getSelect())) return visited;
                String target = switch (visited.getSimpleName()) {
                    case "setDefaultLang" -> "setFallbackLang";
                    case "getDefaultLang" -> "getFallbackLang";
                    default -> null;
                };
                return target == null ? visited : visited.withName(visited.getName().withSimpleName(target));
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if ("onDefaultLangChange".equals(visited.getSimpleName()) && ownedReceiver(visited.getTarget())) {
                    return visited.withName(visited.getName().withSimpleName("onFallbackLangChange"));
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                collectDeclarations(cu, ctx);
                Set<String> serviceTypes = new HashSet<>();
                Set<String> runtimeServiceTypes = new HashSet<>();
                Set<String> injectFunctions = new HashSet<>();
                Set<String> localImports = new HashSet<>();
                Set<String> importedCoreSymbols = new HashSet<>();
                Set<String> shorthandBindings = new HashSet<>();
                List<ImportCandidate> candidates = new ArrayList<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (visited.getImportClause() == null ||
                            !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named)) {
                            return visited;
                        }
                        String module = NgxTranslateSupport.moduleName(visited);
                        for (JS.ImportSpecifier specifier : named.getElements()) {
                            String imported = NgxTranslateSupport.importedName(specifier);
                            String local = NgxTranslateSupport.localBinding(specifier);
                            if (!local.isEmpty()) localImports.add(local);
                            if (NgxTranslateSupport.PACKAGE.equals(module)) {
                                importedCoreSymbols.add(imported);
                                if ("TranslateService".equals(imported)) {
                                    serviceTypes.add(local);
                                    if (!visited.getImportClause().isTypeOnly()) runtimeServiceTypes.add(local);
                                }
                                String replacement = NgxTranslateSupport.PUBLIC_RENAMES.get(imported);
                                if (replacement != null) {
                                    candidates.add(new ImportCandidate(specifier.getId(), imported, local,
                                            specifier.getSpecifier() instanceof JS.Alias, replacement));
                                }
                            }
                            if ("@angular/core".equals(module) && "inject".equals(imported) &&
                                !visited.getImportClause().isTypeOnly()) {
                                injectFunctions.add(local);
                            }
                        }
                        return visited;
                    }

                    @Override
                    public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                          ExecutionContext scanCtx) {
                        JS.PropertyAssignment visited = super.visitPropertyAssignment(property, scanCtx);
                        if (visited.getName() instanceof J.Identifier name &&
                            (visited.getInitializer() == null ||
                             visited.getInitializer() instanceof J.Identifier initializer &&
                             name.getSimpleName().equals(initializer.getSimpleName()))) {
                            shorthandBindings.add(name.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                for (ImportCandidate candidate : candidates) {
                    if (importedCoreSymbols.contains(candidate.replacement)) continue;
                    if (candidate.aliased) {
                        importRenames.put(candidate.id, candidate.replacement);
                    } else if (declarationCounts.getOrDefault(candidate.imported, 0) == 0 &&
                               declarationCounts.getOrDefault(candidate.replacement, 0) == 0 &&
                               !localImports.contains(candidate.replacement) &&
                               !shorthandBindings.contains(candidate.local)) {
                        importRenames.put(candidate.id, candidate.replacement);
                        identifierRenames.put(candidate.local, candidate.replacement);
                    }
                }
                collectServiceReceivers(cu, ctx, serviceTypes, runtimeServiceTypes, injectFunctions);
            }

            private void collectDeclarations(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                        declarationCounts.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration declaration,
                                                                       ExecutionContext scanCtx) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(declaration, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private void collectServiceReceivers(JS.CompilationUnit cu, ExecutionContext ctx,
                                                 Set<String> serviceTypes, Set<String> runtimeServiceTypes,
                                                 Set<String> injectFunctions) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (declarationCounts.getOrDefault(visited.getSimpleName(), 0) != 1) return visited;
                        J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                        boolean typedService = declarations != null &&
                                               serviceTypes.contains(NgxTranslateSupport.typeName(
                                                       declarations.getTypeExpression()));
                        boolean injectedService = isServiceInjection(visited.getInitializer(), runtimeServiceTypes,
                                injectFunctions);
                        if (typedService || injectedService) {
                            if (isClassField(declarations)) {
                                fieldServiceReceivers.add(visited.getSimpleName());
                            } else {
                                bareServiceReceivers.add(visited.getSimpleName());
                            }
                        }
                        return visited;
                    }

                    private boolean isClassField(J.VariableDeclarations declarations) {
                        if (declarations == null) return false;
                        if (!declarations.getModifiers().isEmpty()) return true;
                        return getCursor().firstEnclosing(J.ClassDeclaration.class) != null &&
                               getCursor().firstEnclosing(J.MethodDeclaration.class) == null;
                    }
                }.visit(cu, ctx);
            }

            private boolean isServiceInjection(Expression initializer, Set<String> serviceTypes,
                                               Set<String> injectFunctions) {
                if (initializer instanceof J.MethodInvocation call) {
                    return call.getSelect() == null && injectFunctions.contains(call.getSimpleName()) &&
                           declarationCounts.getOrDefault(call.getSimpleName(), 0) == 0 &&
                           hasServiceArgument(call.getArguments(), serviceTypes);
                }
                if (initializer instanceof JS.FunctionCall call && call.getFunction() instanceof J.Identifier function) {
                    return injectFunctions.contains(function.getSimpleName()) &&
                           declarationCounts.getOrDefault(function.getSimpleName(), 0) == 0 &&
                           hasServiceArgument(call.getArguments(), serviceTypes);
                }
                return false;
            }

            private boolean hasServiceArgument(List<Expression> arguments, Set<String> serviceTypes) {
                return !arguments.isEmpty() && serviceTypes.contains(NgxTranslateSupport.typeName(arguments.get(0)));
            }

            private boolean isSyntaxName(J.Identifier identifier) {
                if (getCursor().firstEnclosing(JS.ImportSpecifier.class) != null) return true;
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent == null) return false;
                Object value = parent.getValue();
                return value instanceof JS.PropertyAssignment property && property.getName() == identifier ||
                       value instanceof J.FieldAccess access && access.getName() == identifier ||
                       value instanceof J.MethodInvocation invocation && invocation.getName() == identifier ||
                       value instanceof J.VariableDeclarations.NamedVariable variable && variable.getName() == identifier ||
                       value instanceof J.MethodDeclaration method && method.getName() == identifier ||
                       value instanceof J.ClassDeclaration declaration && declaration.getName() == identifier ||
                       value instanceof JS.TypeDeclaration declaration && declaration.getName() == identifier;
            }

            private boolean ownedReceiver(Object receiver) {
                if (receiver instanceof J.Identifier identifier) {
                    return bareServiceReceivers.contains(identifier.getSimpleName());
                }
                if (receiver instanceof J.FieldAccess access && access.getTarget() instanceof J.Identifier target) {
                    return "this".equals(target.getSimpleName()) &&
                           fieldServiceReceivers.contains(access.getSimpleName());
                }
                return false;
            }
        };
    }

    private record ImportCandidate(UUID id, String imported, String local, boolean aliased, String replacement) { }
}
