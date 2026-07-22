package com.huawei.clouds.openrewrite.fsextra;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Marks exact module-boundary, option, and changed filesystem-semantics risks. */
public final class FindFsExtraJavaScriptRisks extends Recipe {
    private static final Set<String> COPY_APIS = Set.of("copy", "copySync");
    private static final Set<String> LINK_APIS = Set.of(
            "ensureLink", "ensureLinkSync", "ensureSymlink", "ensureSymlinkSync",
            "createLink", "createLinkSync", "createSymlink", "createSymlinkSync");
    private static final Set<String> REMOVE_APIS = Set.of("remove", "removeSync");
    private static final Set<String> OUTPUT_JSON_APIS = Set.of(
            "outputJson", "outputJSON", "outputJsonSync", "outputJSONSync");
    private static final Set<String> READ_JSON_APIS = Set.of(
            "readJson", "readJSON", "readJsonSync", "readJSONSync");
    private static final Set<String> WRITE_JSON_APIS = Set.of(
            "writeJson", "writeJSON", "writeJsonSync", "writeJSONSync",
            "outputJson", "outputJSON", "outputJsonSync", "outputJSONSync");
    private static final Set<String> NATIVE_FS_APIS = Set.of(
            "access", "accessSync", "appendFile", "appendFileSync", "chmod", "chmodSync", "chown",
            "chownSync", "close", "closeSync", "constants", "createReadStream", "createWriteStream",
            "exists", "existsSync", "fchmod", "fchmodSync", "fchown", "fchownSync", "fdatasync",
            "fdatasyncSync", "fstat", "fstatSync", "fsync", "fsyncSync", "ftruncate", "ftruncateSync",
            "futimes", "futimesSync", "lchmod", "lchmodSync", "lchown", "lchownSync", "link",
            "linkSync", "lstat", "lstatSync", "lutimes", "lutimesSync", "mkdir", "mkdirSync",
            "mkdtemp", "mkdtempSync", "open", "openAsBlob", "openSync", "opendir", "opendirSync",
            "read", "readFile", "readFileSync", "readdir", "readdirSync", "readlink", "readlinkSync",
            "readSync", "readv", "readvSync", "realpath", "realpathSync", "rename", "renameSync",
            "rm", "rmSync", "rmdir", "rmdirSync", "stat", "statSync", "statfs", "statfsSync",
            "symlink", "symlinkSync", "truncate", "truncateSync", "unlink", "unlinkSync", "unwatchFile",
            "utimes", "utimesSync", "watch", "watchFile", "write", "writeFile", "writeFileSync",
            "writeSync", "writev", "writevSync");

    @Override
    public String getDisplayName() {
        return "Find fs-extra 11 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed deep imports, unsupported ESM native-fs imports, null JSON options, removed remove options, and changed copy, link, and JSON-output semantics.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> namedApis = Map.of();
            private Map<String, String> namespaces = Map.of();
            private Set<String> commonJsNamespaces = Set.of();
            private Set<String> declarations = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!FsExtraSupport.isProjectPath(cu.getSourcePath())) return cu;
                Map<String, String> oldNamed = namedApis;
                Map<String, String> oldNamespaces = namespaces;
                Set<String> oldCommonJs = commonJsNamespaces;
                Set<String> oldDeclarations = declarations;
                Map<String, Integer> oldDeclarationCounts = declarationCounts;
                namedApis = new HashMap<>();
                namespaces = new HashMap<>();
                commonJsNamespaces = new HashSet<>();
                declarationCounts = inventory(cu, ctx);
                declarations = declarationCounts.keySet();
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                namedApis = oldNamed;
                namespaces = oldNamespaces;
                commonJsNamespaces = oldCommonJs;
                declarations = oldDeclarations;
                declarationCounts = oldDeclarationCounts;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = FsExtraSupport.moduleName(visited);
                if (!FsExtraSupport.PACKAGE.equals(module) && !FsExtraSupport.ESM_PACKAGE.equals(module)) {
                    return visited;
                }
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null) return visited;
                if (clause.getName() != null) namespaces.put(clause.getName().getSimpleName(), module);
                if (clause.getNamedBindings() instanceof JS.NamedImports named) {
                    for (JS.ImportSpecifier specifier : named.getElements()) {
                        namedApis.put(FsExtraSupport.localName(specifier), FsExtraSupport.importedName(specifier));
                    }
                } else if (clause.getNamedBindings() instanceof JS.Alias alias &&
                           alias.getAlias() instanceof J.Identifier identifier) {
                    namespaces.put(identifier.getSimpleName(), module);
                }
                if (FsExtraSupport.ESM_PACKAGE.equals(module) && clause.getName() != null) {
                    return SearchResult.found(visited,
                            "The fs-extra/esm default object contains only fs-extra-specific methods, not native Node fs methods; verify every property access or import native APIs from node:fs or node:fs/promises");
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration == null) return visited;
                String module = FsExtraSupport.moduleName(declaration);
                String imported = FsExtraSupport.importedName(visited);
                if ((FsExtraSupport.PACKAGE.equals(module) || FsExtraSupport.ESM_PACKAGE.equals(module)) &&
                    !FsExtraSupport.EXTRA_EXPORTS.contains(imported)) {
                    String message = NATIVE_FS_APIS.contains(imported)
                            ? imported + " is a native Node fs API, not a named fs-extra/esm export; choose node:fs for callbacks/sync or node:fs/promises for promises and verify call semantics"
                            : imported + " is not a documented runtime named export of fs-extra/esm; keep type-only imports on the matching @types/fs-extra surface or select an explicit public runtime API";
                    return SearchResult.found(visited, message);
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (rootRequire(visited.getInitializer())) commonJsNamespaces.add(visited.getSimpleName());
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = functionName(visited.getFunction());
                String api = ownedNamedApi(name);
                return api == null ? visited : markApi(visited, api, visited.getArguments());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String api = null;
                if (visited.getSelect() == null) {
                    api = ownedNamedApi(visited.getSimpleName());
                } else if (visited.getSelect() instanceof J.Identifier identifier) {
                    String owner = identifier.getSimpleName();
                    if ((commonJsNamespaces.contains(owner) && declarationCounts.getOrDefault(owner, 0) == 1) ||
                        (namespaces.containsKey(owner) && !declarations.contains(owner))) {
                        api = visited.getSimpleName();
                        if (FsExtraSupport.ESM_PACKAGE.equals(namespaces.get(owner)) &&
                            !FsExtraSupport.EXTRA_EXPORTS.contains(api)) {
                            return SearchResult.found(visited,
                                    api + " is a native Node fs API and is absent from fs-extra/esm; import it from node:fs or node:fs/promises with the intended callback, sync, or promise contract");
                        }
                    }
                }
                return api == null ? visited : markApi(visited, api, visited.getArguments());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (visited.getValue() instanceof String value &&
                    (value.equals("fs-extra/lib") || value.startsWith("fs-extra/lib/"))) {
                    return SearchResult.found(visited,
                            "fs-extra 11 blocks deep imports with its exports map; replace this physical lib path with fs-extra or a documented fs-extra/esm export and verify mocks, bundling, and runtime resolution");
                }
                return visited;
            }

            private String ownedNamedApi(String local) {
                return declarations.contains(local) ? null : namedApis.get(local);
            }

            private <T extends J> T markApi(T call, String api, List<Expression> arguments) {
                if (COPY_APIS.contains(api)) {
                    return SearchResult.found(call,
                            "copy behavior changed across v9-v11 for timestamps, broken symlinks, unknown file types, filter invocation and concurrency, and directory traversal; test ordering, side effects, links, special files, metadata, errors, and scale");
                }
                if (LINK_APIS.contains(api)) {
                    return SearchResult.found(call,
                            api + " now validates an existing destination's type more strictly; test files, directories, relative symlinks, dangling links, idempotency, and Windows permissions");
                }
                if (REMOVE_APIS.contains(api) && arguments.size() > 1 && objectLiteral(arguments.get(1))) {
                    return SearchResult.found(call,
                            "fs-extra 10 removed undocumented remove/removeSync options; delete this options object only after confirming retry, busy-file, glob, error, and platform expectations");
                }
                int nullOption = READ_JSON_APIS.contains(api) ? 1 : WRITE_JSON_APIS.contains(api) ? 2 : -1;
                if (nullOption >= 0 && arguments.size() > nullOption && nullLiteral(arguments.get(nullOption))) {
                    return SearchResult.found(call,
                            "fs-extra 9 no longer accepts null for JSON options; omit the argument or use undefined/an explicit jsonfile options object while preserving callback and formatting behavior");
                }
                if (OUTPUT_JSON_APIS.contains(api)) {
                    return SearchResult.found(call,
                            "Since fs-extra 9, outputJson snapshots the object when called rather than after later mutation; verify mutation timing, formatting, callbacks/promises, atomicity expectations, and readers");
                }
                return call;
            }

            private boolean rootRequire(Expression expression) {
                if (expression instanceof JS.FunctionCall call && "require".equals(functionName(call.getFunction())) &&
                    !call.getArguments().isEmpty()) {
                    return FsExtraSupport.PACKAGE.equals(FsExtraSupport.stringLiteral(call.getArguments().get(0)));
                }
                if (expression instanceof J.MethodInvocation call && call.getSelect() == null &&
                    "require".equals(call.getSimpleName()) && !call.getArguments().isEmpty()) {
                    return FsExtraSupport.PACKAGE.equals(FsExtraSupport.stringLiteral(call.getArguments().get(0)));
                }
                return false;
            }

            private boolean objectLiteral(Expression expression) {
                return expression instanceof J.NewClass object && object.getClazz() == null;
            }

            private boolean nullLiteral(Expression expression) {
                return expression instanceof J.Literal literal && literal.getValue() == null;
            }

            private String functionName(Expression expression) {
                if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (expression instanceof J.FieldAccess field) return field.getSimpleName();
                return "";
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
}
