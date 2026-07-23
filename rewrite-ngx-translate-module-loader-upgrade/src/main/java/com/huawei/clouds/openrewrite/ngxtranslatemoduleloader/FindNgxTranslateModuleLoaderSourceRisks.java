package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Marks removed public types and loader behaviors that require an application decision. */
public final class FindNgxTranslateModuleLoaderSourceRisks extends Recipe {
    private static final String TRANSLATION =
            "Translation was removed from loader 5; use @ngx-translate/core TranslationObject and run strict typecheck";
    private static final String KEY =
            "TranslationKey was removed without an equivalent public loader type; choose an application key model or keyof-compatible type explicitly";
    private static final String LOADER =
            "Loader 5 fetches responseType text and applies JSON.parse/custom fileParser, uses ngx-translate mergeDeep, and peers on core 16+; regress interceptors, errors, charset, merge, namespace, URL, cache, SSR, and custom mapper behavior";

    @Override
    public String getDisplayName() {
        return "Find ngx-translate-module-loader 5.1.0 source risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact imports, references, constructors, re-exports, deep entries, and undeclared Ramda coupling " +
               "affected by the removed types and target HTTP/parser/merge behavior.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> removedLocals = Map.of();
            private Map<String, String> loaderLocals = Map.of();
            private boolean usesPackage;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxTranslateModuleLoaderSupport.isProjectPath(cu.getSourcePath())) return cu;
                Map<String, String> oldRemoved = removedLocals;
                Map<String, String> oldLoaders = loaderLocals;
                boolean oldUses = usesPackage;
                removedLocals = new HashMap<>();
                loaderLocals = new HashMap<>();
                usesPackage = collect(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                removedLocals = oldRemoved;
                loaderLocals = oldLoaders;
                usesPackage = oldUses;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = NgxTranslateModuleLoaderSupport.moduleName(visited);
                if (module.startsWith(NgxTranslateModuleLoaderSupport.PACKAGE + "/")) {
                    return visited.withModuleSpecifier(mark(visited.getModuleSpecifier(),
                            "Private loader deep import was removed from the public target surface; import supported symbols from the package root"));
                }
                if (usesPackage && "ramda".equals(module)) {
                    return visited.withModuleSpecifier(mark(visited.getModuleSpecifier(),
                            "Loader 5 no longer declares Ramda; declare and own it directly or migrate merge logic to @ngx-translate/core mergeDeep"));
                }
                if (NgxTranslateModuleLoaderSupport.PACKAGE.equals(module) &&
                    (visited.getImportClause() == null ||
                     !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports))) {
                    return visited.withModuleSpecifier(mark(visited.getModuleSpecifier(),
                            "Namespace/default/side-effect import detected; verify every binding against loader 5's root exports"));
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration == null || !ownedModule(NgxTranslateModuleLoaderSupport.moduleName(declaration))) {
                    return visited;
                }
                return switch (NgxTranslateModuleLoaderSupport.importedName(visited)) {
                    case "Translation" -> mark(visited, TRANSLATION);
                    case "TranslationKey" -> mark(visited, KEY);
                    case "ModuleTranslateLoader" -> mark(visited, LOADER);
                    default -> visited;
                };
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(JS.ImportSpecifier.class) != null) return visited;
                Object parent = getCursor().getParentTreeCursor() == null ? null :
                        getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.FieldAccess field && field.getName().getId().equals(visited.getId()) ||
                    parent instanceof J.MethodInvocation method && method.getName().getId().equals(visited.getId()) ||
                    parent instanceof JS.PropertyAssignment property && property.getName() instanceof J.Identifier name &&
                    name.getId().equals(visited.getId())) return visited;
                String removed = removedLocals.get(visited.getSimpleName());
                return "Translation".equals(removed) ? mark(visited, TRANSLATION) :
                       "TranslationKey".equals(removed) ? mark(visited, KEY) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String local = NgxTranslateModuleLoaderSupport.expressionName(visited.getClazz());
                return "ModuleTranslateLoader".equals(loaderLocals.get(local)) ? mark(visited, LOADER) : visited;
            }

            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext ctx) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, ctx);
                if (!(visited.getModuleSpecifier() instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module) ||
                    !ownedModule(module)) return visited;
                return visited.withModuleSpecifier(mark(visited.getModuleSpecifier(),
                        "Loader 5 removed Translation/TranslationKey and private entries; verify this re-export against the target public API"));
            }

            private boolean collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean[] found = {false};
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = NgxTranslateModuleLoaderSupport.moduleName(visited);
                        if (!ownedModule(module)) return visited;
                        found[0] = true;
                        JS.ImportClause clause = visited.getImportClause();
                        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                        for (JS.ImportSpecifier element : named.getElements()) {
                            String imported = NgxTranslateModuleLoaderSupport.importedName(element);
                            String local = NgxTranslateModuleLoaderSupport.localName(element);
                            if ("Translation".equals(imported) || "TranslationKey".equals(imported)) {
                                removedLocals.put(local, imported);
                            }
                            if ("ModuleTranslateLoader".equals(imported)) loaderLocals.put(local, imported);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                Set<String> shadowed = new HashSet<>();
                Set<String> ownedLocals = new HashSet<>(removedLocals.keySet());
                ownedLocals.addAll(loaderLocals.keySet());
                String source = cu.printAll();
                for (String local : ownedLocals) {
                    if (Pattern.compile("(?m)\\b(?:class|interface|enum|type|const|let|var|function)\\s+" +
                                        Pattern.quote(local) + "\\b").matcher(source).find()) {
                        shadowed.add(local);
                    }
                }
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (ownedLocals.contains(visited.getSimpleName())) shadowed.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, ctx);
                shadowed.forEach(local -> {
                    removedLocals.remove(local);
                    loaderLocals.remove(local);
                });
                return found[0];
            }
        };
    }

    private static boolean ownedModule(String module) {
        return NgxTranslateModuleLoaderSupport.PACKAGE.equals(module) ||
               module.startsWith(NgxTranslateModuleLoaderSupport.PACKAGE + "/");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
