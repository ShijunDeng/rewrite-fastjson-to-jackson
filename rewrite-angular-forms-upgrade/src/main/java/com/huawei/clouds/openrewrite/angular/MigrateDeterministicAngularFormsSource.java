package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Deterministic source changes backed by Angular's official typed-forms migrations and changelog. */
public final class MigrateDeterministicAngularFormsSource extends Recipe {
    private static final Map<String, String> UNTYPED = Map.of(
            "FormControl", "UntypedFormControl",
            "FormGroup", "UntypedFormGroup",
            "FormArray", "UntypedFormArray",
            "FormBuilder", "UntypedFormBuilder"
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular Forms source";
    }

    @Override
    public String getDescription() {
        return "Applies the safe compatibility subset of Angular's typed-forms migration, replaces the deprecated " +
               "initialValueIsDefault option with nonNullable, and removes redundant always-disabled-state configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> controlAliases = Map.of();
            private Set<String> moduleAliases = Set.of();
            private Set<String> genericAliases = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> previousControls = controlAliases;
                Set<String> previousModules = moduleAliases;
                Set<String> previousGenerics = genericAliases;
                controlAliases = new HashMap<>();
                moduleAliases = new HashSet<>();
                collectImports(cu, ctx);
                genericAliases = new HashSet<>();
                String source = cu.printAll();
                for (String alias : controlAliases.keySet()) {
                    if (source.matches("(?s).*\\b" + java.util.regex.Pattern.quote(alias) + "\\s*<.*")) {
                        genericAliases.add(alias);
                    }
                }
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                controlAliases = previousControls;
                moduleAliases = previousModules;
                genericAliases = previousGenerics;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!"@angular/forms".equals(moduleName(visited))) {
                    return visited;
                }
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
                    return visited;
                }
                JS.NamedImports migrated = named.withElements(ListUtils.map(named.getElements(), element -> {
                    if (element.getSpecifier() instanceof JS.Alias alias) {
                        String replacement = UNTYPED.get(alias.getPropertyName().getSimpleName());
                        String local = alias.getAlias() instanceof J.Identifier identifier
                                ? identifier.getSimpleName() : "";
                        if (replacement != null && !genericAliases.contains(local)) {
                            return element.withSpecifier(alias.withPropertyName(
                                    alias.getPropertyName().withSimpleName(replacement)));
                        }
                    }
                    return element;
                }));
                return visited.withImportClause(clause.withNamedBindings(migrated));
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = expressionName(visited.getName());
                if ("initialValueIsDefault".equals(name) && isControlConstructorOption() &&
                    !optionObjectContains("nonNullable")) {
                    return visited.withName(rename(visited.getName(), "nonNullable"));
                }
                if ("callSetDisabledState".equals(name) && isAlways(visited.getInitializer()) &&
                    isFormsModuleWithConfig()) {
                    return null;
                }
                return visited;
            }

            private void collectImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("@angular/forms".equals(moduleName(visited))) {
                            for (String imported : UNTYPED.keySet()) {
                                String alias = importedAlias(visited, imported);
                                if (alias != null) {
                                    controlAliases.put(alias, imported);
                                }
                            }
                            for (String imported : Set.of("UntypedFormControl", "FormsModule", "ReactiveFormsModule")) {
                                String alias = importedAlias(visited, imported);
                                if (alias != null) {
                                    if ("UntypedFormControl".equals(imported)) {
                                        controlAliases.put(alias, imported);
                                    } else {
                                        moduleAliases.add(alias);
                                    }
                                }
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean isControlConstructorOption() {
                J.NewClass options = getCursor().firstEnclosing(J.NewClass.class);
                if (options == null || options.getClazz() != null) {
                    return false;
                }
                org.openrewrite.Cursor cursor = getCursor().getParent();
                boolean passedOptions = false;
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass object) {
                        if (object.getId().equals(options.getId())) {
                            passedOptions = true;
                        } else if (passedOptions && object.getClazz() != null) {
                            String imported = controlAliases.get(expressionName(object.getClazz()));
                            return imported != null && Set.of("FormControl", "UntypedFormControl").contains(imported) &&
                                   object.getArguments().size() >= 2 &&
                                   object.getArguments().get(1).getId().equals(options.getId());
                        }
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean optionObjectContains(String propertyName) {
                Cursor cursor = getCursor();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass object && object.getClazz() == null) {
                        String printed = object.printTrimmed(cursor);
                        return printed.matches("(?s).*?(?:^|[,{])\\s*['\"]?" +
                                               java.util.regex.Pattern.quote(propertyName) +
                                               "['\"]?\\s*:.*");
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean isFormsModuleWithConfig() {
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                return invocation != null && object != null && object.getClazz() == null &&
                       invocation.getArguments().stream().anyMatch(a -> a.getId().equals(object.getId())) &&
                       "withConfig".equals(invocation.getSimpleName()) &&
                       invocation.getSelect() instanceof J.Identifier identifier &&
                       moduleAliases.contains(identifier.getSimpleName());
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
            Expression specifier = element.getSpecifier();
            if (specifier instanceof J.Identifier identifier && importedName.equals(identifier.getSimpleName())) {
                return identifier.getSimpleName();
            }
            if (specifier instanceof JS.Alias alias && importedName.equals(alias.getPropertyName().getSimpleName()) &&
                alias.getAlias() instanceof J.Identifier identifier) {
                return identifier.getSimpleName();
            }
        }
        return null;
    }

    static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.FieldAccess access) {
            return access.getSimpleName();
        }
        if (expression instanceof J.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        if (expression instanceof JS.TypeTreeExpression type) {
            return expressionName(type.getExpression());
        }
        return "";
    }

    private static Expression rename(Expression expression, String replacement) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.withSimpleName(replacement);
        }
        if (expression instanceof J.Literal literal) {
            String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
            return literal.withValue(replacement).withValueSource(quote + replacement + quote);
        }
        return expression;
    }

    private static boolean isAlways(Expression expression) {
        return expression instanceof J.Literal literal && "always".equals(literal.getValue());
    }
}
