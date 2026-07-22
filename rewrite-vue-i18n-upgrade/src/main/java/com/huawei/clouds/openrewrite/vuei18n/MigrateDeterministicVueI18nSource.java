package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Applies only context-proven Vue I18n source migrations with equivalent target semantics. */
public final class MigrateDeterministicVueI18nSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Vue I18n 11 source constructs";
    }

    @Override
    public String getDescription() {
        return "Renames dateTimeFormats in owned I18n option objects and tc/$tc calls whose explicit numeric " +
               "plural argument maps directly to t/$t without overload selection.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean i18nFile;
            private Set<String> defaultAliases = Set.of();
            private Set<String> createAliases = Set.of();
            private Set<String> i18nVariables = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldFile = i18nFile;
                Set<String> oldDefault = defaultAliases;
                Set<String> oldCreate = createAliases;
                Set<String> oldVariables = i18nVariables;
                i18nFile = false;
                defaultAliases = new HashSet<>();
                createAliases = new HashSet<>();
                i18nVariables = new HashSet<>();
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                i18nFile = oldFile;
                defaultAliases = oldDefault;
                createAliases = oldCreate;
                i18nVariables = oldVariables;
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (isI18nConstruction(visited.getInitializer())) {
                    i18nVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (i18nFile && "dateTimeFormats".equals(VueI18nJavaScriptSupport.propertyName(visited.getName())) &&
                    isDirectI18nOption(property) && !hasSibling(property, "datetimeFormats")) {
                    return visited.withName(renameProperty(visited.getName(), "datetimeFormats"));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!i18nFile || visited.getArguments().size() != 2 ||
                    !(visited.getArguments().get(1) instanceof J.Literal plural) ||
                    !(plural.getValue() instanceof Number) || !isPluralOwner(visited)) {
                    return visited;
                }
                if ("$tc".equals(visited.getSimpleName())) {
                    return visited.withName(visited.getName().withSimpleName("$t"));
                }
                if ("tc".equals(visited.getSimpleName())) {
                    return visited.withName(visited.getName().withSimpleName("t"));
                }
                return visited;
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

            private boolean isDirectI18nOption(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return false;
                }
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass created && created.getClazz() != null &&
                        created.getArguments().stream().anyMatch(argument -> argument.getId().equals(object.getId()))) {
                        return created.getClazz() instanceof J.Identifier identifier &&
                               defaultAliases.contains(identifier.getSimpleName());
                    }
                    if (cursor.getValue() instanceof J.MethodInvocation call &&
                        call.getArguments().stream().anyMatch(argument -> argument.getId().equals(object.getId()))) {
                        return call.getSelect() == null && createAliases.contains(call.getSimpleName());
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean hasSibling(JS.PropertyAssignment property, String name) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                return object != null && object.getBody() != null && object.getBody().getStatements().stream()
                        .filter(statement -> !statement.getId().equals(property.getId()))
                        .filter(JS.PropertyAssignment.class::isInstance)
                        .map(JS.PropertyAssignment.class::cast)
                        .anyMatch(sibling -> name.equals(
                                VueI18nJavaScriptSupport.propertyName(sibling.getName())));
            }

            private boolean isPluralOwner(J.MethodInvocation invocation) {
                if ("$tc".equals(invocation.getSimpleName())) {
                    return invocation.getSelect() instanceof J.Identifier identifier &&
                           "this".equals(identifier.getSimpleName());
                }
                if (!"tc".equals(invocation.getSimpleName())) {
                    return false;
                }
                Expression select = invocation.getSelect();
                if (select instanceof J.Identifier identifier) {
                    return i18nVariables.contains(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess field && "global".equals(field.getSimpleName()) &&
                       field.getTarget() instanceof J.Identifier identifier &&
                       i18nVariables.contains(identifier.getSimpleName());
            }

            private boolean isI18nConstruction(Expression expression) {
                if (expression instanceof J.NewClass created && created.getClazz() instanceof J.Identifier identifier) {
                    return defaultAliases.contains(identifier.getSimpleName());
                }
                return expression instanceof J.MethodInvocation call && call.getSelect() == null &&
                       createAliases.contains(call.getSimpleName());
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = VueI18nJavaScriptSupport.moduleName(visited);
                        if ("vue-i18n".equals(module) || module.startsWith("vue-i18n/") ||
                            module.contains("vue-i18n-bridge")) {
                            i18nFile = true;
                        }
                        if ("vue-i18n".equals(module) && visited.getImportClause() != null) {
                            if (visited.getImportClause().getName() != null) {
                                defaultAliases.add(visited.getImportClause().getName().getSimpleName());
                            }
                            VueI18nJavaScriptSupport.collectNamed(visited, "createI18n", createAliases);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
