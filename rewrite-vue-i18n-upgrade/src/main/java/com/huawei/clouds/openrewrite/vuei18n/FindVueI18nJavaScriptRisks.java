package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Marks Vue I18n source changes that require application or message-semantics decisions. */
public final class FindVueI18nJavaScriptRisks extends Recipe {
    private static final Set<String> REMOVED_OPTIONS = Set.of(
            "formatter", "preserveDirectiveContent", "allowComposition");
    private static final Set<String> MODE_OPTIONS = Set.of(
            "silentTranslationWarn", "silentFallbackWarn", "formatFallbackMessages",
            "warnHtmlInMessage", "pluralizationRules");

    @Override
    public String getDisplayName() {
        return "Find Vue I18n 11 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks Vue 2 construction/install, deprecated Legacy mode, removed plural APIs/options, changed " +
               "translation overloads/return shape, bridge imports, and static VueI18n class APIs.";
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
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = VueI18nJavaScriptSupport.moduleName(visited);
                if (module.contains("vue-i18n-bridge")) {
                    return SearchResult.found(visited,
                            "vue-i18n-bridge ended at v9 and is absent from Vue I18n 11; complete Vue 3 migration, retarget imports to vue-i18n, and remove bridge-only arguments/casts");
                }
                if (module.startsWith("@intlify/unplugin-vue-i18n")) {
                    return SearchResult.found(visited,
                            "Vue I18n 10 enabled JIT compilation by default; rebuild and verify this unplugin's include/runtimeOnly/CSP/dynamic-message configuration against the v11 message compiler");
                }
                if ("vue-i18n".equals(module) && visited.getImportClause() != null &&
                    visited.getImportClause().getName() != null) {
                    return SearchResult.found(visited,
                            "Vue I18n v8's default class import was replaced by named createI18n/useI18n APIs; coordinate this with Vue 3 createApp and app.use(i18n)");
                }
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
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (visited.getClazz() instanceof J.Identifier identifier &&
                    defaultAliases.contains(identifier.getSimpleName())) {
                    return SearchResult.found(visited,
                            "new VueI18n(options) was removed; migrate the Vue 2 bootstrap to createI18n(options), createApp, and app.use(i18n), then choose Legacy or Composition scope explicitly");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!i18nFile) {
                    return visited;
                }
                if ("use".equals(visited.getSimpleName()) && hasDefaultAliasArgument(visited.getArguments())) {
                    return SearchResult.found(visited,
                            "Vue.use(VueI18n) is a Vue 2 global install; create a Vue 3 app and install the createI18n result with app.use(i18n)");
                }
                if (visited.getSelect() == null && createAliases.contains(visited.getSimpleName()) &&
                    !hasLegacyFalse(visited)) {
                    return SearchResult.found(visited,
                            "Vue I18n 11 deprecates Legacy API mode and createI18n defaults to legacy:true; plan legacy:false + useI18n scope/globalInjection migration before v12");
                }
                if (("$tc".equals(visited.getSimpleName()) || "tc".equals(visited.getSimpleName())) &&
                    isPluralOwner(visited)) {
                    return SearchResult.found(visited,
                            "tc/$tc was removed in v11; migrate to t/$t plural overloads and explicitly resolve locale, list/named values, default message, and plural count");
                }
                if (("$t".equals(visited.getSimpleName()) || "t".equals(visited.getSimpleName())) &&
                    isTranslationOwner(visited) && hasStringSecondArgument(visited)) {
                    return SearchResult.found(visited,
                            "A string second argument is ambiguous after v10 removed the Legacy locale positional overload; use a list/named value plus { locale }, or confirm it is intentionally a default message");
                }
                if (("$t".equals(visited.getSimpleName()) || "t".equals(visited.getSimpleName())) &&
                    isTranslationOwner(visited) && hasArrayLikeSecondArgument(visited)) {
                    return SearchResult.found(visited,
                            "Vue I18n 9 removed array-like objects for list interpolation; pass a real array and verify positional values and plural/options overload selection");
                }
                if (("$t".equals(visited.getSimpleName()) || "t".equals(visited.getSimpleName())) &&
                    isTranslationOwner(visited) && resultUsedAsStructure(invocation)) {
                    return SearchResult.found(visited,
                            "t/$t returns strings only since v9; use tm/$tm to obtain object or array messages and rt/$rt to resolve the selected leaf");
                }
                if ("getChoiceIndex".equals(visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "getChoiceIndex was removed; move custom plural selection to pluralizationRules in Legacy mode or pluralRules in Composition mode and test every locale/count boundary");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (visited.getTarget() instanceof J.Identifier identifier &&
                    defaultAliases.contains(identifier.getSimpleName()) &&
                    ("version".equals(visited.getSimpleName()) || "availability".equals(visited.getSimpleName()))) {
                    return SearchResult.found(visited, "version moved to the named VERSION export and Intl availability was removed; use platform Intl capability checks only when the supported runtime matrix requires them");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!i18nFile) {
                    return visited;
                }
                String name = VueI18nJavaScriptSupport.propertyName(visited.getName());
                if ("__INTLIFY_JIT_COMPILATION__".equals(name)) {
                    return SearchResult.found(visited,
                            "The __INTLIFY_JIT_COMPILATION__ compatibility flag is obsolete because v10+ enables JIT by default; remove it after validating CSP and runtime/full builds");
                }
                if (!insideI18nOptions(property)) {
                    return visited;
                }
                if ("dateTimeFormats".equals(name)) {
                    return SearchResult.found(visited,
                            "dateTimeFormats remains because option ownership or a conflicting datetimeFormats key prevents a deterministic rename; resolve the conflict and retain only datetimeFormats");
                }
                if (REMOVED_OPTIONS.contains(name)) {
                    return SearchResult.found(visited, name + " was removed before Vue I18n 11; remove the compatibility option and migrate its formatter/directive/bridge behavior explicitly");
                }
                if (MODE_OPTIONS.contains(name)) {
                    return SearchResult.found(visited,
                            name + " has Legacy-versus-Composition naming or value semantics; choose missingWarn/fallbackWarn/fallbackFormat/warnHtmlMessage/pluralRules while migrating scope and security behavior");
                }
                return visited;
            }

            private boolean hasDefaultAliasArgument(List<Expression> arguments) {
                return arguments.stream().anyMatch(argument -> argument instanceof J.Identifier identifier &&
                        defaultAliases.contains(identifier.getSimpleName()));
            }

            private boolean hasLegacyFalse(J.MethodInvocation call) {
                if (call.getArguments().isEmpty() || !(call.getArguments().get(0) instanceof J.NewClass object) ||
                    object.getBody() == null) {
                    return false;
                }
                return object.getBody().getStatements().stream().anyMatch(statement ->
                        statement instanceof JS.PropertyAssignment property &&
                        "legacy".equals(VueI18nJavaScriptSupport.propertyName(property.getName())) &&
                        property.getInitializer() instanceof J.Literal literal && Boolean.FALSE.equals(literal.getValue()));
            }

            private boolean hasStringSecondArgument(J.MethodInvocation call) {
                return call.getArguments().size() > 1 && call.getArguments().get(1) instanceof J.Literal literal &&
                       literal.getValue() instanceof String;
            }

            private boolean hasArrayLikeSecondArgument(J.MethodInvocation call) {
                if (call.getArguments().size() < 2 || !(call.getArguments().get(1) instanceof J.NewClass object) ||
                    object.getClazz() != null || object.getBody() == null || object.getBody().getStatements().isEmpty()) {
                    return false;
                }
                return object.getBody().getStatements().stream().allMatch(statement ->
                        statement instanceof JS.PropertyAssignment property &&
                        VueI18nJavaScriptSupport.propertyName(property.getName()).matches("\\d+"));
            }

            private boolean resultUsedAsStructure(J.MethodInvocation original) {
                Object parent = getCursor().getParent() == null ? null : getCursor().getParent().getValue();
                if (parent instanceof J.FieldAccess field) {
                    return field.getTarget().getId().equals(original.getId());
                }
                return parent instanceof J.ArrayAccess access && access.getIndexed().getId().equals(original.getId());
            }

            private boolean isPluralOwner(J.MethodInvocation call) {
                return "$tc".equals(call.getSimpleName()) ? thisOwner(call.getSelect()) :
                        "tc".equals(call.getSimpleName()) && composerOwner(call.getSelect());
            }

            private boolean isTranslationOwner(J.MethodInvocation call) {
                return "$t".equals(call.getSimpleName()) ? thisOwner(call.getSelect()) :
                        "t".equals(call.getSimpleName()) && composerOwner(call.getSelect());
            }

            private boolean thisOwner(Expression select) {
                return select instanceof J.Identifier identifier && "this".equals(identifier.getSimpleName());
            }

            private boolean composerOwner(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return i18nVariables.contains(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess field && "global".equals(field.getSimpleName()) &&
                       field.getTarget() instanceof J.Identifier identifier &&
                       i18nVariables.contains(identifier.getSimpleName());
            }

            private boolean insideI18nOptions(JS.PropertyAssignment property) {
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
                            module.contains("vue-i18n-bridge") || module.startsWith("@intlify/unplugin-vue-i18n")) {
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
