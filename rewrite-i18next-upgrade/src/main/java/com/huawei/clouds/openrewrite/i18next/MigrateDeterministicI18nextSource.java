package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Applies the official initImmediate rename only where i18next option ownership is established. */
public final class MigrateDeterministicI18nextSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic i18next 25 source options";
    }

    @Override
    public String getDescription() {
        return "Renames initImmediate to initAsync in direct options passed to an owned i18next init call or in " +
               "an object explicitly typed with i18next InitOptions; unrelated properties remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean i18nextFile;
            private Set<String> defaultAliases = Set.of();
            private Set<String> initOptionsAliases = Set.of();
            private Set<String> instanceVariables = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();
            private Set<String> declaredTypes = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldFile = i18nextFile;
                Set<String> oldDefault = defaultAliases;
                Set<String> oldOptions = initOptionsAliases;
                Set<String> oldInstances = instanceVariables;
                Map<String, Integer> oldDeclarations = declarationCounts;
                Set<String> oldTypes = declaredTypes;
                i18nextFile = false;
                defaultAliases = new HashSet<>();
                initOptionsAliases = new HashSet<>();
                instanceVariables = new HashSet<>();
                scanImports(cu, ctx);
                scanDeclarations(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                i18nextFile = oldFile;
                defaultAliases = oldDefault;
                initOptionsAliases = oldOptions;
                instanceVariables = oldInstances;
                declarationCounts = oldDeclarations;
                declaredTypes = oldTypes;
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (isCreateInstance(visited.getInitializer())) {
                    instanceVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!i18nextFile || !"initImmediate".equals(I18nextJavaScriptSupport.propertyName(visited.getName())) ||
                    !isOwnedInitOption(property) || hasSibling(property, "initAsync")) {
                    return visited;
                }
                return visited.withName(renameProperty(visited.getName(), "initAsync"));
            }

            private Expression renameProperty(Expression name, String replacement) {
                if (name instanceof J.Identifier identifier) {
                    return identifier.withSimpleName(replacement);
                }
                if (name instanceof J.Literal literal) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return literal.withValue(replacement).withValueSource(quote + replacement + quote);
                }
                return name;
            }

            private boolean isOwnedInitOption(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return false;
                }
                J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                if (declarations != null &&
                    isUnshadowedInitOptionsType(I18nextJavaScriptSupport.typeName(declarations.getTypeExpression())) &&
                    declarations.getVariables().stream().anyMatch(variable -> variable.getInitializer() != null &&
                            variable.getInitializer().getId().equals(object.getId()))) {
                    return true;
                }
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.MethodInvocation call &&
                        call.getArguments().stream().anyMatch(argument -> argument.getId().equals(object.getId()))) {
                        return "init".equals(call.getSimpleName()) && isOwner(call.getSelect());
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean isCreateInstance(Expression expression) {
                if (!(expression instanceof J.MethodInvocation call)) {
                    return false;
                }
                if ("createInstance".equals(call.getSimpleName())) {
                    return isOwner(call.getSelect());
                }
                return "use".equals(call.getSimpleName()) && isCreateInstance(call.getSelect());
            }

            private boolean isOwner(Expression expression) {
                if (expression instanceof J.Identifier identifier) {
                    String name = identifier.getSimpleName();
                    return defaultAliases.contains(name) && declarationCounts.getOrDefault(name, 0) == 0 ||
                           instanceVariables.contains(name) && declarationCounts.getOrDefault(name, 0) == 1;
                }
                return expression instanceof J.MethodInvocation call &&
                       Set.of("createInstance", "use").contains(call.getSimpleName()) && isOwner(call.getSelect());
            }

            private boolean isUnshadowedInitOptionsType(String name) {
                return initOptionsAliases.contains(name) && !declaredTypes.contains(name);
            }

            private boolean hasSibling(JS.PropertyAssignment property, String name) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                return object != null && object.getBody() != null && object.getBody().getStatements().stream()
                        .filter(statement -> !statement.getId().equals(property.getId()))
                        .filter(JS.PropertyAssignment.class::isInstance)
                        .map(JS.PropertyAssignment.class::cast)
                        .anyMatch(sibling -> name.equals(
                                I18nextJavaScriptSupport.propertyName(sibling.getName())));
            }

            private void scanDeclarations(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, Integer> variables = new HashMap<>();
                Set<String> types = new HashSet<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        variables.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                        types.add(visited.getName().getSimpleName());
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        types.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, ctx);
                declarationCounts = variables;
                declaredTypes = types;
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (!"i18next".equals(I18nextJavaScriptSupport.moduleName(visited)) ||
                            visited.getImportClause() == null) {
                            return visited;
                        }
                        i18nextFile = true;
                        if (visited.getImportClause().getName() != null) {
                            defaultAliases.add(visited.getImportClause().getName().getSimpleName());
                        }
                        I18nextJavaScriptSupport.collectNamed(visited, "InitOptions", initOptionsAliases);
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
