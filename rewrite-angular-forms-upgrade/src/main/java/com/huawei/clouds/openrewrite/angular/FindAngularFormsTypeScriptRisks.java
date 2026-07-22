package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact Angular Forms nodes whose migration depends on application semantics. */
public final class FindAngularFormsTypeScriptRisks extends Recipe {
    private static final Set<String> CONTROL_TYPES = Set.of(
            "FormControl", "FormGroup", "FormArray", "FormRecord", "AbstractControl",
            "UntypedFormControl", "UntypedFormGroup", "UntypedFormArray", "FormBuilder", "NonNullableFormBuilder"
    );
    private static final Set<String> VALUE_METHODS = Set.of(
            "setValue", "patchValue", "reset", "getRawValue"
    );
    private static final Set<String> STATE_METHODS = Set.of(
            "disable", "enable", "markAsTouched", "markAllAsTouched", "markAsDirty", "markAsPending",
            "updateValueAndValidity"
    );
    private static final Set<String> ASYNC_METHODS = Set.of(
            "setAsyncValidators", "addAsyncValidators", "removeAsyncValidators", "clearAsyncValidators"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Forms 20 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks typed forms, CVA disabled state, value/raw-value, validators, events, standalone providers, " +
               "and legacy module configuration at exact TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> controls = Map.of();
            private Set<String> controlVariables = Set.of();
            private Set<String> formModules = Set.of();
            private Set<String> cvaTypes = Set.of();
            private Set<String> asyncValidatorTypes = Set.of();
            private boolean hasForms;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> oldControls = controls;
                Set<String> oldVariables = controlVariables;
                Set<String> oldModules = formModules;
                Set<String> oldCva = cvaTypes;
                Set<String> oldAsync = asyncValidatorTypes;
                boolean oldForms = hasForms;
                controls = new HashMap<>();
                controlVariables = new HashSet<>();
                formModules = new HashSet<>();
                cvaTypes = new HashSet<>();
                asyncValidatorTypes = new HashSet<>();
                hasForms = false;
                collect(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                controls = oldControls;
                controlVariables = oldVariables;
                formModules = oldModules;
                cvaTypes = oldCva;
                asyncValidatorTypes = oldAsync;
                hasForms = oldForms;
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String imported = controls.get(MigrateDeterministicAngularFormsSource.expressionName(visited.getClazz()));
                if (imported == null || !Set.of("FormControl", "FormGroup", "FormArray", "FormBuilder").contains(imported)) {
                    return visited;
                }
                if ("FormControl".equals(imported) && visited.getArguments().size() >= 3 &&
                    isOptionsObject(visited.getArguments().get(1))) {
                    return SearchResult.found(visited, "FormControl combines AbstractControlOptions with a third async-validator argument; Angular drops/deprecates the third argument, so merge asyncValidators into the options and test pending/error/cancellation behavior");
                }
                return SearchResult.found(visited, imported + " is untyped under the pre-v14 model; select a typed generic/nonNullable model or apply Angular's Untyped compatibility migration and verify null/reset/value shape");
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String method = visited.getSimpleName();
                if (isControl(visited.getSelect())) {
                    if ("getRawValue".equals(method)) {
                        return SearchResult.found(visited, "getRawValue includes disabled controls and may expose a different typed DTO than value; assert disabled, optional, nested, reset, and serialization behavior");
                    }
                    if (VALUE_METHODS.contains(method)) {
                        return SearchResult.found(visited, method + " uses the typed form value/nullability shape; verify optional controls, partial/exact objects, reset defaults, disabled controls, and emitted events");
                    }
                    if (STATE_METHODS.contains(method)) {
                        return SearchResult.found(visited, method + " changes control state and event ordering; verify CVA disabled propagation, parent aggregation, emitEvent/onlySelf, status/value/events timing, and change detection");
                    }
                    if (ASYNC_METHODS.contains(method)) {
                        return SearchResult.found(visited, method + " changes async validation lifetime; verify pending state, cancellation, first/late emissions, errors, disabled controls, and parent status propagation");
                    }
                }
                if (("forRoot".equals(method) || "withConfig".equals(method)) &&
                    visited.getSelect() instanceof J.Identifier identifier &&
                    formModules.contains(identifier.getSimpleName())) {
                    return SearchResult.found(visited, "Forms module configuration controls CVA setDisabledState compatibility; remove legacy opt-outs only after custom accessors handle initial/enabled/disabled calls and provider order is verified");
                }
                if ("composeAsync".equals(method) && visited.getSelect() instanceof J.Identifier identifier &&
                    "Validators".equals(identifier.getSimpleName()) && !asyncValidatorTypes.isEmpty()) {
                    return SearchResult.found(visited, "Composed async validators need explicit cancellation, pending, empty/error, disabled-control, and late-emission tests");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (!isControl(visited.getTarget())) {
                    return visited;
                }
                if ("value".equals(visited.getSimpleName())) {
                    return SearchResult.found(visited, "control.value excludes disabled descendants and has a typed partial/nullability shape; compare getRawValue and verify DTO serialization/reset behavior");
                }
                if (Set.of("valueChanges", "statusChanges", "events").contains(visited.getSimpleName())) {
                    return SearchResult.found(visited, visited.getSimpleName() + " emission timing relative to parent value/status, async validation, enable/disable, reset, and CVA callbacks must be tested");
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                String name = visited.getSimpleName();
                if (implementsAny(cvaTypes) && Set.of("writeValue", "registerOnChange", "registerOnTouched", "setDisabledState").contains(name)) {
                    String message = "setDisabledState".equals(name)
                            ? "CVA setDisabledState is called for initial enabled state by default; verify DOM state, idempotency, change detection, null values, and destroy/rebind cycles"
                            : "CVA " + name + " participates in the value/touched callback contract; verify callback replacement, disabled state, null/reset values, event ordering, and feedback-loop prevention";
                    return SearchResult.found(visited, message);
                }
                if (implementsAny(asyncValidatorTypes) && "validate".equals(name)) {
                    return SearchResult.found(visited, "AsyncValidator.validate must complete/emit predictably and tolerate cancellation, disable/enable, rapid value changes, errors, and parent pending propagation");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = MigrateDeterministicAngularFormsSource.expressionName(visited.getName());
                if ("initialValueIsDefault".equals(name) && isControlConstructorOption()) {
                    return SearchResult.found(visited, "initialValueIsDefault remains because nonNullable is also declared or the option needs review; consolidate the duplicate/default-value semantics explicitly");
                }
                if ("callSetDisabledState".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    "whenDisabledForLegacyCode".equals(literal.getValue())) {
                    return SearchResult.found(visited, "Legacy CVA disabled-state opt-out preserves pre-v15 behavior; remove it only after every accessor accepts the initial enabled call and state/event tests pass");
                }
                if (hasForms && isComponentMetadata() && "standalone".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    Boolean.TRUE.equals(literal.getValue())) {
                    return SearchResult.found(visited, "Standalone component/forms provider scope needs explicit FormsModule/ReactiveFormsModule or provider imports and typed template diagnostics");
                }
                return visited;
            }

            private boolean isControlConstructorOption() {
                J.NewClass options = getCursor().firstEnclosing(J.NewClass.class);
                if (options == null || options.getClazz() != null) return false;
                org.openrewrite.Cursor cursor = getCursor().getParent();
                boolean passedOptions = false;
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass object) {
                        if (object.getId().equals(options.getId())) {
                            passedOptions = true;
                        } else if (passedOptions && object.getClazz() != null) {
                            String imported = controls.get(
                                    MigrateDeterministicAngularFormsSource.expressionName(object.getClazz()));
                            return imported != null &&
                                   Set.of("FormControl", "UntypedFormControl").contains(imported) &&
                                   object.getArguments().size() >= 2 &&
                                   object.getArguments().get(1).getId().equals(options.getId());
                        }
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean isComponentMetadata() {
                J.Annotation annotation = getCursor().firstEnclosing(J.Annotation.class);
                return annotation != null && "Component".equals(annotation.getSimpleName());
            }

            private void collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("@angular/forms".equals(MigrateDeterministicAngularFormsSource.moduleName(visited))) {
                            hasForms = true;
                            for (String type : CONTROL_TYPES) {
                                String alias = MigrateDeterministicAngularFormsSource.importedAlias(visited, type);
                                if (alias != null) controls.put(alias, type);
                            }
                            for (String module : Set.of("FormsModule", "ReactiveFormsModule")) {
                                String alias = MigrateDeterministicAngularFormsSource.importedAlias(visited, module);
                                if (alias != null) formModules.add(alias);
                            }
                            String cva = MigrateDeterministicAngularFormsSource.importedAlias(visited, "ControlValueAccessor");
                            if (cva != null) cvaTypes.add(cva);
                            String async = MigrateDeterministicAngularFormsSource.importedAlias(visited, "AsyncValidator");
                            if (async != null) asyncValidatorTypes.add(async);
                            String asyncFn = MigrateDeterministicAngularFormsSource.importedAlias(visited, "AsyncValidatorFn");
                            if (asyncFn != null) asyncValidatorTypes.add(asyncFn);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                            ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        if (controls.containsKey(typeName(visited.getTypeExpression()))) {
                            visited.getVariables().forEach(v -> controlVariables.add(v.getSimpleName()));
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.NewClass nc &&
                            controls.containsKey(MigrateDeterministicAngularFormsSource.expressionName(nc.getClazz()))) {
                            controlVariables.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean isControl(Expression expression) {
                if (expression instanceof J.Identifier identifier) {
                    return controlVariables.contains(identifier.getSimpleName());
                }
                if (expression instanceof J.FieldAccess access) {
                    String name = access.getSimpleName();
                    return controlVariables.contains(name) || name.startsWith("form") ||
                           name.endsWith("Form") || name.endsWith("Control") ||
                           name.endsWith("FormGroup") || name.endsWith("FormArray");
                }
                return false;
            }

            private boolean implementsAny(Set<String> types) {
                J.ClassDeclaration declaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (declaration == null || declaration.getImplements() == null) return false;
                return declaration.getImplements().stream().anyMatch(type -> types.contains(typeName(type)));
            }
        };
    }

    private static boolean isOptionsObject(Expression expression) {
        return expression instanceof J.NewClass nc && nc.getClazz() == null;
    }

    private static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (type instanceof J.FieldAccess access) return access.getSimpleName();
        if (type instanceof JS.TypeTreeExpression expression) {
            return MigrateDeterministicAngularFormsSource.expressionName(expression.getExpression());
        }
        if (type instanceof JS.TypeInfo info) return typeName(info.getTypeIdentifier());
        return "";
    }
}
