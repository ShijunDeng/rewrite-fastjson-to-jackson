package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Apply only CDK migrations whose binding and result are deterministic. */
public final class MigrateDeterministicAngularCdkSource extends Recipe {
    private static final Map<String, String> RENAMED_IMPORTS = Map.of(
            "DomPortalHost", "DomPortalOutlet",
            "PortalHost", "PortalOutlet",
            "BasePortalHost", "BasePortalOutlet",
            "ConnectedPositionStrategy", "FlexibleConnectedPositionStrategy",
            "CKD_COPY_TO_CLIPBOARD_CONFIG", "CDK_COPY_TO_CLIPBOARD_CONFIG"
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular CDK 20 source";
    }

    @Override
    public String getDescription() {
        return "Rename legacy CDK APIs only when an import alias preserves every local binding, rename " +
               "OverlayPositionBuilder.connectedTo on proven variables, and remove the obsolete DialogConfig " +
               "componentFactoryResolver property from typed object literals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> positionBuilderTypes = Set.of();
            private Set<String> positionBuilders = Set.of();
            private Set<String> dialogConfigTypes = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldTypes = positionBuilderTypes;
                Set<String> oldVariables = positionBuilders;
                Set<String> oldDialogs = dialogConfigTypes;
                positionBuilderTypes = new HashSet<>();
                positionBuilders = new HashSet<>();
                dialogConfigTypes = new HashSet<>();
                collectSymbols(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                positionBuilderTypes = oldTypes;
                positionBuilders = oldVariables;
                dialogConfigTypes = oldDialogs;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!moduleName(visited).startsWith("@angular/cdk/")) return visited;
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                JS.NamedImports migrated = named.withElements(ListUtils.map(named.getElements(), element -> {
                    if (element.getSpecifier() instanceof JS.Alias alias) {
                        String replacement = RENAMED_IMPORTS.get(alias.getPropertyName().getSimpleName());
                        if (replacement != null) {
                            return element.withSpecifier(alias.withPropertyName(
                                    alias.getPropertyName().withSimpleName(replacement)));
                        }
                    }
                    return element;
                }));
                return visited.withImportClause(clause.withNamedBindings(migrated));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if ("connectedTo".equals(visited.getSimpleName()) && ownedPositionBuilder(visited.getSelect())) {
                    return visited.withName(visited.getName().withSimpleName("flexibleConnectedTo"));
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if ("componentFactoryResolver".equals(expressionName(visited.getName())) &&
                    isTypedDialogConfigObject()) return null;
                return visited;
            }

            private boolean ownedPositionBuilder(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return positionBuilders.contains(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess access && positionBuilders.contains(access.getSimpleName());
            }

            private boolean isTypedDialogConfigObject() {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                return object != null && object.getClazz() == null && declarations != null &&
                       dialogConfigTypes.contains(typeName(declarations.getTypeExpression())) &&
                       declarations.getVariables().stream().anyMatch(variable -> variable.getInitializer() != null &&
                               variable.getInitializer().getId().equals(object.getId()));
            }

            private void collectSymbols(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("@angular/cdk/overlay".equals(moduleName(visited))) {
                            addAlias(visited, "OverlayPositionBuilder", positionBuilderTypes);
                        }
                        if ("@angular/cdk/dialog".equals(moduleName(visited))) {
                            addAlias(visited, "DialogConfig", dialogConfigTypes);
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                              ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        if (positionBuilderTypes.contains(typeName(visited.getTypeExpression()))) {
                            for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                                positionBuilders.add(variable.getSimpleName());
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (isInjectPositionBuilder(visited.getInitializer())) {
                            positionBuilders.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean isInjectPositionBuilder(Expression initializer) {
                if (initializer instanceof JS.FunctionCall call) {
                    return "inject".equals(expressionName(call.getFunction())) && !call.getArguments().isEmpty() &&
                           positionBuilderTypes.contains(expressionName(call.getArguments().get(0)));
                }
                if (initializer instanceof J.MethodInvocation invocation) {
                    return "inject".equals(invocation.getSimpleName()) && invocation.getSelect() == null &&
                           !invocation.getArguments().isEmpty() &&
                           positionBuilderTypes.contains(expressionName(invocation.getArguments().get(0)));
                }
                return false;
            }
        };
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static boolean importsAny(JS.Import declaration, Set<String> candidates) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getNamedBindings() instanceof JS.NamedImports named &&
               named.getElements().stream().anyMatch(element -> candidates.contains(importedName(element)));
    }

    static String importedAlias(JS.Import declaration, String wanted) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            if (wanted.equals(importedName(element))) {
                if (element.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (element.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier id) {
                    return id.getSimpleName();
                }
            }
        }
        return null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess access) return access.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return "";
    }

    static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (type instanceof J.FieldAccess access) return access.getSimpleName();
        if (type instanceof JS.TypeTreeExpression expression) return expressionName(expression.getExpression());
        if (type instanceof JS.TypeInfo info) return typeName(info.getTypeIdentifier());
        return "";
    }

    private static void addAlias(JS.Import declaration, String imported, Set<String> aliases) {
        String alias = importedAlias(declaration, imported);
        if (alias != null) aliases.add(alias);
    }
}
