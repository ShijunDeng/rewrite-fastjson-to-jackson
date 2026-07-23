package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Marks exact Mermaid API, configuration, security, and runtime boundaries. */
public final class FindMermaid11JavaScriptRisks extends Recipe {
    private static final Set<String> CONFIG_BOUNDARIES = Set.of(
            "securityLevel", "theme", "themeVariables", "themeCSS", "htmlLabels", "curve",
            "hierarchicalNamespaces", "deterministicIds", "deterministicIDSeed", "maxTextSize",
            "maxEdges", "suppressErrorRendering", "fontFamily", "useMaxWidth");

    @Override
    public String getDisplayName() {
        return "Find Mermaid 11 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks ESM/deep loading, Promise render/parse, deprecated initialization, internal API, callback, " +
               "sanitization, theme, renderer, identifier, bundler, and SSR boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> aliases = Set.of();
            private Set<String> declared = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!MermaidSupport.isSource(cu.getSourcePath())) return cu;
                Set<String> oldAliases = aliases;
                Set<String> oldDeclared = declared;
                aliases = new HashSet<>();
                declared = declarations(cu);
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                aliases = oldAliases;
                declared = oldDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MermaidJavaScriptSupport.moduleName(visited);
                if (MermaidJavaScriptSupport.isMermaidModule(module) && !MermaidSupport.PACKAGE.equals(module)) {
                    return SearchResult.found(visited,
                            "This Mermaid distribution/deep import binds to v9 layout; use the public ESM package root only after verifying full/core diagram loading, bundler chunking, SSR, CSP, and browser CDN behavior");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String function = MermaidJavaScriptSupport.expressionName(visited.getFunction());
                if (("require".equals(function) || "import".equals(function)) && !visited.getArguments().isEmpty() &&
                    MermaidJavaScriptSupport.isMermaidModule(
                            MermaidJavaScriptSupport.stringLiteral(visited.getArguments().get(0)))) {
                    return SearchResult.found(visited, loadingMessage(function));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && ("require".equals(visited.getSimpleName()) ||
                    "import".equals(visited.getSimpleName())) && !visited.getArguments().isEmpty() &&
                    MermaidJavaScriptSupport.isMermaidModule(
                            MermaidJavaScriptSupport.stringLiteral(visited.getArguments().get(0)))) {
                    return SearchResult.found(visited, loadingMessage(visited.getSimpleName()));
                }
                if (!MermaidJavaScriptSupport.isOwnedSelect(visited.getSelect(), aliases)) return visited;
                String method = visited.getSimpleName();
                if ("parse".equals(method) || "parseAsync".equals(method)) {
                    return SearchResult.found(visited,
                            "Mermaid parse is Promise-based and its second argument is now ParseOptions, not an error callback; await/catch it and test invalid, unknown, frontmatter, directive, and lazy-loaded diagram cases");
                }
                if ("render".equals(method) || "renderAsync".equals(method)) {
                    return SearchResult.found(visited,
                            "Mermaid render is Promise-based and returns {svg, bindFunctions}; the third argument is now a container, not a callback, so update callers, DOM ownership, event binding, error paths, and snapshot/CSS expectations");
                }
                if (Set.of("init", "initThrowsErrors", "initThrowsErrorsAsync").contains(method)) {
                    return SearchResult.found(visited,
                            "Legacy init APIs are deprecated or removed; compose initialize(config) with await run({querySelector|nodes, postRenderCallback, suppressErrors}) and preserve per-node callbacks and error policy explicitly");
                }
                if ("initialize".equals(method)) {
                    return SearchResult.found(visited,
                            "initialize establishes site-wide configuration; verify one-time call ordering, startOnLoad, securityLevel, theme, html labels, diagram defaults, tests, HMR, SSR, and concurrent rendering");
                }
                if ("run".equals(method)) {
                    return SearchResult.found(visited,
                            "run marks processed DOM nodes and is asynchronous; verify querySelector/nodes ownership, re-render cleanup, postRenderCallback, suppressErrors, hydration, route remount, and bindFunctions behavior");
                }
                if (Set.of("setParseErrorHandler", "registerExternalDiagrams", "registerLayoutLoaders",
                           "registerIconPacks").contains(method)) {
                    return SearchResult.found(visited,
                            "This process-wide Mermaid registration/error hook crosses lazy loading, initialization order, duplicate registration, test isolation, HMR, SSR concurrency, and error reporting");
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (!(visited.getVariable() instanceof J.FieldAccess field) ||
                    !MermaidJavaScriptSupport.isOwnedSelect(field.getTarget(), aliases)) return visited;
                if ("startOnLoad".equals(field.getSimpleName())) {
                    return SearchResult.found(visited,
                            "Direct startOnLoad mutation is deprecated; move it into the single initialize call and verify DOMContentLoaded, framework mount, SSR, tests, and duplicate rendering");
                }
                if ("parseError".equals(field.getSimpleName())) {
                    return SearchResult.found(visited,
                            "Global parseError mutation affects all diagrams; prefer Promise error handling or coordinate the process-wide handler with test/HMR/SSR isolation");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if ("mermaidAPI".equals(visited.getSimpleName()) &&
                    MermaidJavaScriptSupport.isOwnedSelect(visited.getTarget(), aliases)) {
                    return SearchResult.found(visited,
                            "mermaidAPI is deprecated/internal in Mermaid 11; migrate to public mermaid.parse/render/initialize/run APIs and verify configuration isolation and lazy diagram loading");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!insideOwnedConfig(property)) return visited;
                String name = MermaidJavaScriptSupport.propertyName(visited.getName());
                if (!CONFIG_BOUNDARIES.contains(name)) return visited;
                String message;
                if ("securityLevel".equals(name) || "htmlLabels".equals(name)) {
                    message = "Mermaid securityLevel/htmlLabels controls sanitization, raw HTML, links, click callbacks, sandbox iframes, accessibility, CSP, and DOMPurify behavior; threat-model untrusted diagrams and regression-test output";
                } else if ("curve".equals(name)) {
                    message = "Mermaid 11.13 changed the default flowchart curve from basis to rounded; retain an explicit curve only when required and visually compare edge routing, labels, markers, and dimensions";
                } else if ("hierarchicalNamespaces".equals(name)) {
                    message = "Mermaid 11.15 changed nested class namespace behavior; choose hierarchicalNamespaces deliberately and compare qualified names, relations, labels, and links";
                } else {
                    message = "This Mermaid configuration crosses v11 theme, renderer, identifier, sizing, error-rendering, or CSS behavior; compare real SVG/visual snapshots and application selectors before accepting the target default";
                }
                return SearchResult.found(visited, message);
            }

            private boolean insideOwnedConfig(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null) return false;
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.MethodInvocation call &&
                        ("initialize".equals(call.getSimpleName()) || "init".equals(call.getSimpleName())) &&
                        MermaidJavaScriptSupport.isOwnedSelect(call.getSelect(), aliases) &&
                        call.getArguments().stream().anyMatch(argument -> containsObject(argument, object))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean containsObject(Expression argument, J.NewClass object) {
                if (argument.getId().equals(object.getId())) return true;
                final boolean[] found = {false};
                new JavaScriptIsoVisitor<boolean[]>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass candidate, boolean[] accumulator) {
                        if (candidate.getId().equals(object.getId())) accumulator[0] = true;
                        return candidate;
                    }
                }.visit(argument, found);
                return found[0];
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ignored) {
                        if (MermaidSupport.PACKAGE.equals(MermaidJavaScriptSupport.moduleName(declaration)) &&
                            declaration.getImportClause() != null && declaration.getImportClause().getName() != null) {
                            String alias = declaration.getImportClause().getName().getSimpleName();
                            if (!declared.contains(alias)) aliases.add(alias);
                        }
                        return declaration;
                    }
                }.visit(cu, ctx);
            }

            private Set<String> declarations(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        accumulator.add(variable.getSimpleName());
                        return variable;
                    }
                }.visit(cu, names);
                return names;
            }
        };
    }

    private static String loadingMessage(String function) {
        return "require".equals(function)
                ? "Mermaid 10+ is ESM-only; replace require/interoperability loading and verify Node, tests, bundler, SSR, Electron, CSP, and lazy chunks"
                : "Dynamic Mermaid loading is an async bundler/SSR boundary; verify default interop, client-only execution, chunk failure, preload, CSP, and hydration";
    }
}
