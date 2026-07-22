package com.huawei.clouds.openrewrite.ng2fileupload;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Marks target export, Angular scope, uploader transport, callback, and extension-point risks. */
public final class FindNg2FileUploadTypeScriptRisks extends Recipe {
    private static final Set<String> DIRECTIVES = Set.of("FileSelectDirective", "FileDropDirective");
    private static final Set<String> TRANSPORT_METHODS = Set.of(
            "uploadAll", "uploadItem", "cancelAll", "cancelItem", "addToQueue", "removeFromQueue",
            "clearQueue", "setOptions", "getNotUploadedItems", "getReadyItems");
    private static final Set<String> CALLBACKS = Set.of(
            "onAfterAddingFile", "onBeforeUploadItem", "onBuildItemForm", "onWhenAddingFileFailed",
            "onAfterAddingAll", "onProgressItem", "onProgressAll", "onSuccessItem", "onErrorItem",
            "onCancelItem", "onCompleteItem", "onCompleteAll");
    private static final Set<String> SECURITY_OPTIONS = Set.of(
            "url", "method", "authToken", "authTokenHeader", "headers", "withCredentials");
    private static final Set<String> BODY_OPTIONS = Set.of(
            "disableMultipart", "formatDataFunction", "formatDataFunctionIsAsync", "itemAlias",
            "additionalParameter", "parametersBeforeFiles");
    private static final Set<String> FILTER_OPTIONS = Set.of(
            "filters", "allowedFileType", "allowedMimeType", "allowedFileExtensions", "maxFileSize", "queueLimit");
    private static final Set<String> INTERNAL_APIS = Set.of(
            "_file", "_parseHeaders", "_transformResponse", "_isSuccessCode", "_onSuccessItem",
            "_onErrorItem", "_onCancelItem", "_onCompleteItem", "_failFilterIndex");
    private static final String ESM =
            "ng2-file-upload 10 publishes only an ESM root entry; replace CommonJS require with a supported static ESM named import and verify test mocks/bundler/SSR resolution";
    private static final String DEEP =
            "ng2-file-upload 10 exports only its root and package.json; use the deterministic root import when proven, otherwise select an explicitly exported public symbol";
    private static final String DIRECTIVE =
            "These target directives are standalone:false; import FileUploadModule in the owning NgModule or standalone Component/TestBed imports rather than importing/declaring the directive directly";

    @Override
    public String getDisplayName() {
        return "Find ng2-file-upload 10 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks ESM/deep entries, non-standalone directives, uploader options and calls, callbacks, custom subclasses, internals, and the changed transform hook.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> imports = Map.of();
            private Map<String, Integer> declarations = Map.of();
            private Set<String> uploaderInstances = Set.of();
            private Set<UUID> optionObjects = Set.of();
            private Set<UUID> uploaderSubclasses = Set.of();
            private boolean ownsPackage;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!Ng2FileUploadSupport.projectPath(cu.getSourcePath())) return cu;
                Map<String, String> oldImports = imports;
                Map<String, Integer> oldDeclarations = declarations;
                Set<String> oldInstances = uploaderInstances;
                Set<UUID> oldOptions = optionObjects;
                Set<UUID> oldSubclasses = uploaderSubclasses;
                boolean oldOwns = ownsPackage;
                imports = new HashMap<>();
                declarations = inventory(cu, ctx);
                uploaderInstances = new HashSet<>();
                optionObjects = new HashSet<>();
                uploaderSubclasses = new HashSet<>();
                ownsPackage = scanImports(cu, ctx);
                if (ownsPackage) scanOwners(cu, ctx);
                JS.CompilationUnit visited = ownsPackage ? super.visitJsCompilationUnit(cu, ctx) : cu;
                imports = oldImports;
                declarations = oldDeclarations;
                uploaderInstances = oldInstances;
                optionObjects = oldOptions;
                uploaderSubclasses = oldSubclasses;
                ownsPackage = oldOwns;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = Ng2FileUploadSupport.moduleName(visited);
                if (module.startsWith(Ng2FileUploadSupport.PACKAGE + "/")) {
                    return Ng2FileUploadSupport.mark(visited, DEEP);
                }
                if (Ng2FileUploadSupport.PACKAGE.equals(module) && visited.getImportClause() != null &&
                    visited.getImportClause().getName() != null) {
                    return Ng2FileUploadSupport.mark(visited,
                            "ng2-file-upload 10 has named exports and no documented default export; replace this default binding with exact named imports");
                }
                return visited;
            }

            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext ctx) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, ctx);
                if (visited.getModuleSpecifier() instanceof J.Literal literal &&
                    literal.getValue() instanceof String module &&
                    module.startsWith(Ng2FileUploadSupport.PACKAGE + "/")) {
                    return visited.withModuleSpecifier(Ng2FileUploadSupport.mark(literal, DEEP));
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String module) || !Ng2FileUploadSupport.packageModule(module)) {
                    return visited;
                }
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (invocation != null && module.equals(Ng2FileUploadSupport.requireModule(invocation))) {
                    return Ng2FileUploadSupport.mark(visited, ESM);
                }
                JS.FunctionCall dynamicImport = getCursor().firstEnclosing(JS.FunctionCall.class);
                boolean functionImport = dynamicImport != null &&
                        "import".equals(dynamicImport.getFunction().toString().trim()) &&
                        dynamicImport.getArguments().stream().anyMatch(argument -> argument.getId().equals(literal.getId()));
                boolean methodImport = invocation != null && invocation.getSelect() == null &&
                        "import".equals(invocation.getSimpleName()) &&
                        invocation.getArguments().stream().anyMatch(argument -> argument.getId().equals(literal.getId()));
                if (module.startsWith(Ng2FileUploadSupport.PACKAGE + "/") && (functionImport || methodImport)) {
                    return Ng2FileUploadSupport.mark(visited, DEEP);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String local = Ng2FileUploadSupport.expressionName(visited.getClazz());
                if (!"FileUploader".equals(imports.get(local)) || declarations.getOrDefault(local, 0) != 0) {
                    return visited;
                }
                return Ng2FileUploadSupport.mark(visited,
                        "Review this FileUploader construction under Angular 21: endpoint trust, auth/header exposure, CORS credentials, multipart/body format, filters, size/count limits, retry/cancel, and server-side enforcement");
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = Ng2FileUploadSupport.propertyName(visited);
                if (Set.of("imports", "declarations").contains(name) && isAngularScopeProperty(visited, getCursor()) &&
                    visited.getInitializer() instanceof J.NewArray array && array.getInitializer() != null &&
                    array.getInitializer().stream().anyMatch(expression -> expression instanceof J.Identifier identifier &&
                            DIRECTIVES.contains(imports.get(identifier.getSimpleName())))) {
                    return Ng2FileUploadSupport.mark(visited, DIRECTIVE + " (Angular " + name + " metadata)");
                }
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || !optionObjects.contains(object.getId()) || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return visited;
                }
                if (SECURITY_OPTIONS.contains(name)) return Ng2FileUploadSupport.mark(visited,
                        name + " controls upload endpoint/authentication/CORS trust; prevent token leakage, require HTTPS, constrain origins, and enforce authorization server-side");
                if (BODY_OPTIONS.contains(name)) return Ng2FileUploadSupport.mark(visited,
                        name + " controls multipart/raw request construction; verify content type, field ordering/names, async body errors, backend parsing, and large-file memory behavior");
                if (FILTER_OPTIONS.contains(name)) return Ng2FileUploadSupport.mark(visited,
                        name + " is a client-side filter only; retest rejection callbacks and edge cases, and enforce MIME, extension, size, count, and content validation on the server");
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if ("import".equals(visited.getFunction().toString().trim())) {
                    return visited.withArguments(markDynamicImportArguments(visited.getArguments()));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && "import".equals(visited.getSimpleName())) {
                    return visited.withArguments(markDynamicImportArguments(visited.getArguments()));
                }
                if (TRANSPORT_METHODS.contains(visited.getSimpleName()) && ownedUploader(visited.getSelect())) {
                    return Ng2FileUploadSupport.mark(visited,
                            visited.getSimpleName() + " mutates or sends the upload queue; verify concurrency, duplicate submission, cancellation, progress, failures, cleanup, navigation, and server idempotency");
                }
                if (INTERNAL_APIS.contains(visited.getSimpleName()) && insideUploaderSubclass()) {
                    return Ng2FileUploadSupport.mark(visited,
                            "This custom uploader calls protected/internal " + visited.getSimpleName() +
                            "; compile against 10.0.0 and verify XHR status, response parsing, headers, callbacks, and upstream implementation drift");
                }
                return visited;
            }

            private java.util.List<Expression> markDynamicImportArguments(java.util.List<Expression> arguments) {
                return arguments.stream().map(argument -> {
                    if (argument instanceof J.Literal literal && literal.getValue() instanceof String module &&
                        module.startsWith(Ng2FileUploadSupport.PACKAGE + "/")) {
                        return (Expression) Ng2FileUploadSupport.mark(literal, DEEP);
                    }
                    return argument;
                }).toList();
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (!(visited.getVariable() instanceof J.FieldAccess field)) return visited;
                if (CALLBACKS.contains(field.getSimpleName()) && ownedUploader(field.getTarget())) {
                    return Ng2FileUploadSupport.mark(visited,
                            field.getSimpleName() + " owns upload lifecycle behavior; verify callback arguments, ordering, error/cancel paths, change detection, teardown, and sensitive response/header handling");
                }
                if ("withCredentials".equals(field.getSimpleName()) && ownsPackage) {
                    return Ng2FileUploadSupport.mark(visited,
                            "withCredentials changes cross-origin cookie/auth behavior; verify exact origins, SameSite/CSRF controls, preflight headers, and that credentials are never sent to an untrusted URL");
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if ("_transformResponse".equals(visited.getSimpleName()) && insideUploaderSubclass() &&
                    visited.getParameters().size() > 1) {
                    return Ng2FileUploadSupport.mark(visited,
                            "ng2-file-upload 10 changed protected _transformResponse(response, headers) to _transformResponse(response); remove header dependence or own an explicit adapter and regression-test success/error/cancel parsing");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if (INTERNAL_APIS.contains(visited.getSimpleName()) && insideUploaderSubclass() &&
                    getCursor().firstEnclosing(J.MethodInvocation.class) == null) {
                    return Ng2FileUploadSupport.mark(visited,
                            "This custom uploader reads/writes protected internal " + visited.getSimpleName() +
                            "; replace private coupling where possible and regression-test queue/XHR behavior against 10.0.0");
                }
                return visited;
            }

            private boolean ownedUploader(Expression expression) {
                String name = Ng2FileUploadSupport.expressionName(expression);
                return uploaderInstances.contains(name) && declarations.getOrDefault(name, 0) == 1;
            }

            private boolean insideUploaderSubclass() {
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return enclosing != null && uploaderSubclasses.contains(enclosing.getId());
            }

            private boolean scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean[] found = {false};
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = Ng2FileUploadSupport.moduleName(visited);
                        if (!Ng2FileUploadSupport.packageModule(module)) return visited;
                        found[0] = true;
                        if (!Ng2FileUploadSupport.PACKAGE.equals(module) || visited.getImportClause() == null ||
                            !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named)) return visited;
                        for (JS.ImportSpecifier specifier : named.getElements()) {
                            imports.put(Ng2FileUploadSupport.localName(specifier),
                                    Ng2FileUploadSupport.importedName(specifier));
                        }
                        return visited;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
                                                                    ExecutionContext scanCtx) {
                        J.MethodInvocation visited = super.visitMethodInvocation(invocation, scanCtx);
                        if (Ng2FileUploadSupport.packageModule(Ng2FileUploadSupport.requireModule(visited))) {
                            found[0] = true;
                        }
                        if (visited.getSelect() == null && "import".equals(visited.getSimpleName()) &&
                            visited.getArguments().stream().anyMatch(FindNg2FileUploadTypeScriptRisks::packageLiteral)) {
                            found[0] = true;
                        }
                        return visited;
                    }

                    @Override
                    public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext scanCtx) {
                        JS.FunctionCall visited = super.visitFunctionCall(call, scanCtx);
                        if ("import".equals(visited.getFunction().toString().trim()) &&
                            visited.getArguments().stream().anyMatch(FindNg2FileUploadTypeScriptRisks::packageLiteral)) {
                            found[0] = true;
                        }
                        return visited;
                    }

                    @Override
                    public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                         ExecutionContext scanCtx) {
                        JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, scanCtx);
                        if (visited.getModuleSpecifier() instanceof J.Literal literal &&
                            literal.getValue() instanceof String module && Ng2FileUploadSupport.packageModule(module)) {
                            found[0] = true;
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                return found[0];
            }

            private void scanOwners(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(
                            J.VariableDeclarations declarationsNode, ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarationsNode, scanCtx);
                        String type = Ng2FileUploadSupport.expressionName(visited.getTypeExpression());
                        if ("FileUploader".equals(imports.get(type))) {
                            for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                                uploaderInstances.add(variable.getSimpleName());
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.NewClass created &&
                            "FileUploader".equals(imports.get(Ng2FileUploadSupport.expressionName(created.getClazz())))) {
                            uploaderInstances.add(visited.getSimpleName());
                        }
                        return visited;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext scanCtx) {
                        J.NewClass visited = super.visitNewClass(newClass, scanCtx);
                        String local = Ng2FileUploadSupport.expressionName(visited.getClazz());
                        if ("FileUploader".equals(imports.get(local)) && declarations.getOrDefault(local, 0) == 0 &&
                            !visited.getArguments().isEmpty() &&
                            visited.getArguments().get(0) instanceof J.NewClass object && object.getClazz() == null) {
                            optionObjects.add(object.getId());
                        }
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                     ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        String base = Ng2FileUploadSupport.expressionName(visited.getExtends());
                        if ("FileUploader".equals(imports.get(base))) uploaderSubclasses.add(visited.getId());
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private Map<String, Integer> inventory(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, Integer> names = new HashMap<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        names.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                return names;
            }
        };
    }

    private static boolean isAngularScopeProperty(JS.PropertyAssignment property, Cursor cursor) {
        String name = Ng2FileUploadSupport.propertyName(property);
        if (!"imports".equals(name) && !"declarations".equals(name)) return false;
        Cursor ancestor = cursor.getParent();
        while (ancestor != null && !(ancestor.getValue() instanceof J.NewClass)) {
            if (ancestor.getValue() instanceof JS.PropertyAssignment || ancestor.getValue() instanceof J.Annotation ||
                ancestor.getValue() instanceof J.MethodInvocation || ancestor.getValue() instanceof JS.CompilationUnit) {
                return false;
            }
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) return false;
        ancestor = ancestor.getParent();
        while (ancestor != null &&
               (ancestor.getValue() instanceof JRightPadded<?> || ancestor.getValue() instanceof JContainer<?>)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) return false;
        if (ancestor.getValue() instanceof J.Annotation annotation) {
            return "NgModule".equals(annotation.getSimpleName()) || "Component".equals(annotation.getSimpleName());
        }
        return ancestor.getValue() instanceof J.MethodInvocation invocation &&
               "configureTestingModule".equals(invocation.getSimpleName()) &&
               "TestBed".equals(Ng2FileUploadSupport.expressionName(invocation.getSelect()));
    }

    private static boolean packageLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String module &&
               Ng2FileUploadSupport.packageModule(module);
    }
}
