package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.internal.ListUtils;

import java.util.HashSet;
import java.util.Set;

/** Reproduces deterministic Angular Router migrations on TypeScript AST nodes. */
public final class MigrateDeterministicAngularRouterSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular Router 20 source";
    }

    @Override
    public String getDescription() {
        return "Migrates type-attributed Router.getCurrentNavigation references to currentNavigation and removes " +
               "the obsolete relativeLinkResolution option from RouterModule.forRoot.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> routerTypeAliases = Set.of();
            private Set<String> routerVariables = Set.of();
            private Set<String> routerModuleAliases = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previousTypes = routerTypeAliases;
                Set<String> previousVariables = routerVariables;
                Set<String> previousModules = routerModuleAliases;
                routerTypeAliases = new HashSet<>();
                routerVariables = new HashSet<>();
                routerModuleAliases = new HashSet<>();
                collectSymbols(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                routerTypeAliases = previousTypes;
                routerVariables = previousVariables;
                routerModuleAliases = previousModules;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if ("@angular/router".equals(moduleName(visited))) {
                    addAlias(visited, "Router", routerTypeAliases);
                    addAlias(visited, "RouterModule", routerModuleAliases);
                    JS.ImportClause clause = visited.getImportClause();
                    if (clause != null && clause.getNamedBindings() instanceof JS.NamedImports named) {
                        JS.NamedImports migrated = named.withElements(ListUtils.map(named.getElements(), element -> {
                            if (element.getSpecifier() instanceof JS.Alias alias &&
                                "RouterLinkWithHref".equals(alias.getPropertyName().getSimpleName())) {
                                return element.withSpecifier(alias.withPropertyName(
                                        alias.getPropertyName().withSimpleName("RouterLink")));
                            }
                            return element;
                        }));
                        visited = visited.withImportClause(clause.withNamedBindings(migrated));
                    }
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(multiVariable, ctx);
                if (routerTypeAliases.contains(typeName(visited.getTypeExpression()))) {
                    for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                        routerVariables.add(variable.getSimpleName());
                    }
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (isInjectRouter(visited.getInitializer())) {
                    routerVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if ("getCurrentNavigation".equals(visited.getSimpleName()) && isRouterSelect(visited.getSelect())) {
                    return visited.withName(visited.getName().withSimpleName("currentNavigation"));
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if ("getCurrentNavigation".equals(visited.getSimpleName()) &&
                    isRouterSelect(visited.getTarget())) {
                    return visited.withName(visited.getName().withSimpleName("currentNavigation"));
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if ("relativeLinkResolution".equals(expressionName(visited.getName())) &&
                    isRouterModuleForRoot()) {
                    return null;
                }
                if ("initialNavigation".equals(expressionName(visited.getName())) &&
                    isRouterModuleForRoot() && visited.getInitializer() instanceof J.Literal literal &&
                    "enabled".equals(literal.getValue())) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return visited.withInitializer(literal.withValue("enabledBlocking")
                            .withValueSource(quote + "enabledBlocking" + quote));
                }
                return visited;
            }

            private boolean isInjectRouter(Expression initializer) {
                if (initializer instanceof JS.FunctionCall call) {
                    return "inject".equals(expressionName(call.getFunction())) &&
                           hasRouterTypeArgument(call.getArguments());
                }
                if (initializer instanceof J.MethodInvocation invocation) {
                    return "inject".equals(invocation.getSimpleName()) && invocation.getSelect() == null &&
                           hasRouterTypeArgument(invocation.getArguments());
                }
                return false;
            }

            private boolean hasRouterTypeArgument(java.util.List<Expression> arguments) {
                return !arguments.isEmpty() && routerTypeAliases.contains(expressionName(arguments.get(0)));
            }

            private boolean isRouterSelect(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return routerVariables.contains(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess &&
                       routerVariables.contains(((J.FieldAccess) select).getSimpleName());
            }

            private boolean isRouterModuleForRoot() {
                J.MethodInvocation enclosing = getCursor().firstEnclosing(J.MethodInvocation.class);
                J.NewClass objectLiteral = getCursor().firstEnclosing(J.NewClass.class);
                return enclosing != null && objectLiteral != null && objectLiteral.getClazz() == null &&
                       enclosing.getArguments().stream().anyMatch(argument -> argument.getId().equals(objectLiteral.getId())) &&
                       "forRoot".equals(enclosing.getSimpleName()) && enclosing.getSelect() instanceof J.Identifier &&
                       routerModuleAliases.contains(((J.Identifier) enclosing.getSelect()).getSimpleName());
            }

            private void collectSymbols(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("@angular/router".equals(moduleName(visited))) {
                            addAlias(visited, "Router", routerTypeAliases);
                            addAlias(visited, "RouterModule", routerModuleAliases);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(
                            J.VariableDeclarations declarations, ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        if (matchesRouterType(visited.getTypeExpression())) {
                            for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                                routerVariables.add(variable.getSimpleName());
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (isInjectRouter(visited.getInitializer())) {
                            routerVariables.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean matchesRouterType(TypeTree type) {
                if (routerTypeAliases.contains(typeName(type))) {
                    return true;
                }
                String rendered = type == null ? "" : type.toString().trim();
                return routerTypeAliases.contains(rendered);
            }
        };
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return null;
        }
        for (JS.ImportSpecifier element : named.getElements()) {
            if (importedName.equals(importedName(element))) {
                Expression specifier = element.getSpecifier();
                if (specifier instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
                if (specifier instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
            }
        }
        return null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof JS.Alias alias) {
            return alias.getPropertyName().getSimpleName();
        }
        return "";
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        return "";
    }

    private static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (type instanceof J.FieldAccess access) {
            return access.getSimpleName();
        }
        if (type instanceof JS.TypeTreeExpression expression) {
            return expressionName(expression.getExpression());
        }
        if (type instanceof JS.TypeInfo typeInfo) {
            return typeName(typeInfo.getTypeIdentifier());
        }
        return "";
    }

    private static void addAlias(JS.Import declaration, String importedName, Set<String> aliases) {
        String alias = importedAlias(declaration, importedName);
        if (alias != null) {
            aliases.add(alias);
        }
    }
}
