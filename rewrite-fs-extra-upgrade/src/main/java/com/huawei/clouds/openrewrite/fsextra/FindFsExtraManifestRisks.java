package com.huawei.clouds.openrewrite.fsextra;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks package, runtime, type, lockfile, and JSON configuration decisions. */
public final class FindFsExtraManifestRisks extends Recipe {
    private static final Set<String> OVERRIDE_CONTAINERS = Set.of("overrides", "resolutions");
    @Override
    public String getDisplayName() {
        return "Find fs-extra 11 manifest and JSON lockfile risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped constraints, Node 14.14 runtime gaps, types, overrides, deep paths, and npm lockfile entries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean manifest;
            private boolean fsExtraManifest;
            private boolean compatibleNode;
            private boolean lockfile;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!FsExtraSupport.isProjectPath(document.getSourcePath())) return document;
                boolean oldManifest = manifest;
                boolean oldFsExtra = fsExtraManifest;
                boolean oldCompatible = compatibleNode;
                boolean oldLock = lockfile;
                manifest = FsExtraSupport.isPackageJson(document.getSourcePath());
                fsExtraManifest = manifest && containsFsExtra(document);
                compatibleNode = fsExtraManifest && compatibleNode(document);
                lockfile = FsExtraSupport.isLockfile(document.getSourcePath());
                Json.Document visited = super.visitDocument(document, ctx);
                manifest = oldManifest;
                fsExtraManifest = oldFsExtra;
                compatibleNode = oldCompatible;
                lockfile = oldLock;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = FsExtraSupport.key(visited);
                String value = FsExtraSupport.stringValue(visited);
                if (lockfile && FsExtraSupport.npmLockPackageKey(key)) {
                    return visited.withKey(SearchResult.found(visited.getKey(),
                            "Regenerate this npm lockfile with the repository package manager; resolved artifacts, integrity, dependency snapshots, and package-manager metadata cannot be synthesized safely"));
                }
                if (!fsExtraManifest) return visited;
                String section = FsExtraSupport.directSection(getCursor());
                String container = FsExtraSupport.containerName(getCursor());
                if (!section.isEmpty() && FsExtraSupport.PACKAGE.equals(key)) {
                    if (!FsExtraSupport.target(value)) {
                        return mark(visited, "Strict migration skipped this complex, prerelease, dynamic, protocol, or unlisted fs-extra declaration; select and test an 11.3.4 constraint");
                    }
                    if (!compatibleNode) {
                        return mark(visited, "fs-extra 11 requires Node.js >=14.14, but package.json does not prove that every supported runtime satisfies that floor; align engines, CI, containers, deployment, and developer tooling");
                    }
                }
                if (!section.isEmpty() && "@types/fs-extra".equals(key)) {
                    return mark(visited, "Select an @types/fs-extra release compatible with fs-extra 11 and verify callback, promise, ESM, Node fs, and compiler module-resolution types");
                }
                if (FsExtraSupport.PACKAGE.equals(key) &&
                    FsExtraSupport.insideTopLevelContainer(getCursor(), OVERRIDE_CONTAINERS)) {
                    return mark(visited, "This package-manager override or resolution is outside the strict direct-dependency edit; reconcile it with 11.3.4 and regenerate the lockfile");
                }
                if ("node".equals(key) && "engines".equals(container) && !FsExtraSupport.clearlySupportsNode14_14(value)) {
                    return mark(visited, "fs-extra 11 requires Node.js >=14.14; this engine declaration still admits or ambiguously describes older runtimes");
                }
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (fsExtraManifest && visited.getValue() instanceof String value &&
                    (value.contains("fs-extra/lib/") || value.equals("fs-extra/lib"))) {
                    return SearchResult.found(visited,
                            "fs-extra 11 exports only the package root and fs-extra/esm; replace this deep path with a documented public entry and verify mocks, aliases, bundling, and runtime resolution");
                }
                return visited;
            }

            private Json.Member mark(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }

            private boolean containsFsExtra(Json.Document document) {
                if (!(document.getValue() instanceof Json.JsonObject root)) return false;
                for (Json value : root.getMembers()) {
                    if (!(value instanceof Json.Member section) ||
                        !FsExtraSupport.SECTIONS.contains(FsExtraSupport.key(section)) ||
                        !(section.getValue() instanceof Json.JsonObject dependencies)) continue;
                    for (Json dependency : dependencies.getMembers()) {
                        if (dependency instanceof Json.Member entry &&
                            FsExtraSupport.PACKAGE.equals(FsExtraSupport.key(entry))) return true;
                    }
                }
                return false;
            }

            private boolean compatibleNode(Json.Document document) {
                if (!(document.getValue() instanceof Json.JsonObject root)) return false;
                for (Json value : root.getMembers()) {
                    if (!(value instanceof Json.Member member) || !"engines".equals(FsExtraSupport.key(member)) ||
                        !(member.getValue() instanceof Json.JsonObject engines)) continue;
                    for (Json engine : engines.getMembers()) {
                        if (engine instanceof Json.Member node && "node".equals(FsExtraSupport.key(node))) {
                            return FsExtraSupport.clearlySupportsNode14_14(FsExtraSupport.stringValue(node));
                        }
                    }
                }
                return false;
            }
        };
    }
}
