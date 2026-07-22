package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Marks i18next source and type boundaries that require runtime or business decisions. */
public final class FindI18nextJavaScriptRisks extends Recipe {
    private static final Set<String> REMOVED_TYPES = Set.of(
            "StringMap", "KeysWithSeparator", "TFuncKey", "NormalizeByTypeOptions",
            "DefaultTFuncReturnWithObject", "DefaultTFuncReturn", "NormalizeReturn");

    @Override
    public String getDisplayName() {
        return "Find i18next 25 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed public types/logger APIs, old JSON compatibility, return behavior, selector opt-in, " +
               "language switching, object existence, deep imports, and unresolved initImmediate references.";
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
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = I18nextJavaScriptSupport.moduleName(visited);
                if (module.startsWith("i18next/dist/") || module.startsWith("i18next/src/")) {
                    return SearchResult.found(visited,
                            "Deep i18next implementation imports bypass the target package's root conditional exports and import/require type split; use the public i18next entry point");
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                String imported = I18nextJavaScriptSupport.importedName(visited);
                if (declaration != null && "i18next".equals(I18nextJavaScriptSupport.moduleName(declaration)) &&
                    REMOVED_TYPES.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " was removed, internalized, or replaced by the i18next 23 type redesign; migrate to current public types such as ParseKeys/DefaultTReturn or a local helper and run strict TypeScript checks");
                }
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
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!i18nextFile) {
                    return visited;
                }
                if ("setDebug".equals(visited.getSimpleName()) && rootedInOwner(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "The internal logger setDebug function was removed in i18next 23; configure debug during initialization and avoid internal service contracts");
                }
                if ("isWhitelisted".equals(visited.getSimpleName()) && rootedInOwner(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "languageUtils.isWhitelisted was removed; use isSupportedCode and verify supportedLngs/nonExplicitSupportedLngs resolution");
                }
                if ("changeLanguage".equals(visited.getSimpleName()) && isOwner(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "i18next 25 changed concurrent changeLanguage completion and best-match/script fallback; await the Promise and verify routing, caches, SSR hydration, events, and rapid switches");
                }
                if ("exists".equals(visited.getSimpleName()) && isOwner(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "i18next 25.6 makes exists() return false for object keys when returnObjects is false; verify leaf/object navigation and fallback logic");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!i18nextFile || !isOwnedInitOption(property)) {
                    return visited;
                }
                String name = I18nextJavaScriptSupport.propertyName(visited.getName());
                if ("initImmediate".equals(name)) {
                    return SearchResult.found(visited,
                            "initImmediate remains on an i18next option because the deterministic rename was unsafe (for example an existing initAsync sibling); consolidate the keys and confirm sync/async backend expectations");
                }
                if ("jsonFormat".equals(name)) {
                    return SearchResult.found(visited,
                            "jsonFormat was removed in i18next 24; convert resources to JSON v4 and remove this option after validating every locale/plural/context key");
                }
                if ("compatibilityJSON".equals(name) && !isStringValue(visited, "v4")) {
                    return SearchResult.found(visited,
                            "Only compatibilityJSON:'v4' remains; convert old plural resources, remove the old compatibility value, and supply required Intl polyfills");
                }
                if ("returnNull".equals(name)) {
                    return SearchResult.found(visited,
                            "returnNull now defaults false and must align with CustomTypeOptions, rendering/API contracts, missing-key behavior, mocks, and snapshots");
                }
                if ("returnObjects".equals(name) && isBooleanValue(visited, true)) {
                    return SearchResult.found(visited,
                            "Global returnObjects:true must also be declared in CustomTypeOptions; verify key inference and object/leaf exists() behavior");
                }
                if ("enableSelector".equals(name)) {
                    return SearchResult.found(visited,
                            "Selector API is opt-in in 25.x; use the official codemod/Vite plugin and verify dynamic keys, namespace/keyPrefix, plural/context, and test mocks before enabling it");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (i18nextFile && "initImmediate".equals(visited.getValue()) && isInitOptionsOmitKey(visited)) {
                    return SearchResult.found(visited,
                            "This initImmediate property/type key remains after the deterministic rename; update it to initAsync at the owning InitOptions/Omit/key boundary");
                }
                return visited;
            }

            private boolean isInitOptionsOmitKey(J.Literal literal) {
                JS.LiteralType literalType = getCursor().firstEnclosing(JS.LiteralType.class);
                J.ParameterizedType parameterized = getCursor().firstEnclosing(J.ParameterizedType.class);
                if (literalType == null || literalType.getLiteral() == null ||
                    !literalType.getLiteral().getId().equals(literal.getId()) || parameterized == null ||
                    !(parameterized.getClazz() instanceof J.Identifier utility) ||
                    !"Omit".equals(utility.getSimpleName()) ||
                    parameterized.getTypeParameters() == null || parameterized.getTypeParameters().size() < 2) {
                    return false;
                }
                Expression ownerType = parameterized.getTypeParameters().get(0);
                return ownerType instanceof TypeTree type &&
                       initOptionsAliases.contains(I18nextJavaScriptSupport.typeName(type));
            }

            private boolean isStringValue(JS.PropertyAssignment property, String expected) {
                return property.getInitializer() instanceof J.Literal literal && expected.equals(literal.getValue());
            }

            private boolean isBooleanValue(JS.PropertyAssignment property, boolean expected) {
                return property.getInitializer() instanceof J.Literal literal &&
                       Boolean.valueOf(expected).equals(literal.getValue());
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

            private boolean rootedInOwner(Expression expression) {
                if (isOwner(expression)) {
                    return true;
                }
                return expression instanceof J.FieldAccess access && rootedInOwner(access.getTarget());
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
                        String module = I18nextJavaScriptSupport.moduleName(visited);
                        boolean rootImport = "i18next".equals(module);
                        boolean deepImport = module.startsWith("i18next/dist/") || module.startsWith("i18next/src/");
                        if ((rootImport || deepImport) && visited.getImportClause() != null) {
                            i18nextFile = true;
                            if (visited.getImportClause().getName() != null) {
                                defaultAliases.add(visited.getImportClause().getName().getSimpleName());
                            }
                            if (rootImport) {
                                I18nextJavaScriptSupport.collectNamed(visited, "InitOptions", initOptionsAliases);
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
