package com.huawei.clouds.openrewrite.antvx6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks manifest, JSON lockfile, and JSON configuration decisions. */
public final class FindAntvX6JsonRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find AntV X6 3 manifest, lockfile, and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped dependency constraints, consolidated packages, shape-package alignment, lockfile regeneration, Node 20, and internal JSON aliases.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean manifest;
            private boolean x6Manifest;
            private boolean lockfile;
            private boolean config;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!AntvX6Support.isProjectPath(document.getSourcePath())) return document;
                boolean oldManifest = manifest;
                boolean oldX6Manifest = x6Manifest;
                boolean oldLockfile = lockfile;
                boolean oldConfig = config;
                manifest = AntvX6Support.isPackageJson(document.getSourcePath());
                x6Manifest = manifest && containsX6Package(document);
                lockfile = AntvX6Support.isLockfile(document.getSourcePath());
                config = !manifest && AntvX6Support.isConfig(document.getSourcePath());
                Json.Document visited = super.visitDocument(document, ctx);
                manifest = oldManifest;
                x6Manifest = oldX6Manifest;
                lockfile = oldLockfile;
                config = oldConfig;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = AntvX6Support.key(visited);
                String value = AntvX6Support.stringValue(visited);
                if (lockfile && (AntvX6Support.PACKAGE.equals(key) ||
                                 ("node_modules/" + AntvX6Support.PACKAGE).equals(key))) {
                    return mark(visited, "Regenerate this npm lockfile with the selected package manager; resolved URLs and integrity hashes cannot be derived safely by a source recipe");
                }
                if (!manifest || !x6Manifest) return visited;
                String section = AntvX6Support.directSection(getCursor());
                if (!section.isEmpty() && AntvX6Support.PACKAGE.equals(key) && !AntvX6Support.target(value)) {
                    return mark(visited, "Strict migration skipped this complex, prerelease, dynamic, protocol, or unlisted @antv/x6 declaration; select and test a 3.1.7 constraint");
                }
                if (!section.isEmpty() && AntvX6Support.CONSOLIDATED.contains(key)) {
                    return mark(visited, key + " is merged into @antv/x6 in 3.x; verify all static and dynamic consumers, migrate imports, then remove it and regenerate the lockfile");
                }
                if (!section.isEmpty() && AntvX6Support.SHAPES.contains(key)) {
                    return mark(visited, key + " must use a compatible 3.x release with X6 3; verify framework peers, rendering ownership, SSR/hydration, and provider APIs");
                }
                if ("node".equals(key) && "engines".equals(AntvX6Support.containingMember(getCursor())) &&
                    !supportsNode20(value)) {
                    return mark(visited, "@antv/x6 3.1.7 declares Node.js >=20.0.0; align local, CI, build-image, and deployment toolchains");
                }
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (config && visited.getValue() instanceof String value &&
                    (AntvX6Support.internalReference(value) || AntvX6Support.CONSOLIDATED.contains(value))) {
                    return SearchResult.found(visited,
                            "This X6 alias points at a 2.x package or internal layout; use the public @antv/x6 entry point and verify bundler, types, ESM/CJS, tree-shaking, and tests");
                }
                return visited;
            }

            private Json.Member mark(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }

            private boolean containsX6Package(Json.Document document) {
                if (!(document.getValue() instanceof Json.JsonObject root)) return false;
                for (Json value : root.getMembers()) {
                    if (!(value instanceof Json.Member section) ||
                        !AntvX6Support.SECTIONS.contains(AntvX6Support.key(section)) ||
                        !(section.getValue() instanceof Json.JsonObject dependencies)) continue;
                    for (Json dependency : dependencies.getMembers()) {
                        if (dependency instanceof Json.Member entry) {
                            String key = AntvX6Support.key(entry);
                            if (AntvX6Support.PACKAGE.equals(key) || AntvX6Support.CONSOLIDATED.contains(key) ||
                                AntvX6Support.SHAPES.contains(key)) return true;
                        }
                    }
                }
                return false;
            }

            private boolean supportsNode20(String declaration) {
                if (declaration == null || declaration.contains("||") || declaration.contains(" - ")) return false;
                String normalized = declaration.trim().replaceFirst("^(?:>=|\\^|~)\\s*", "");
                int end = normalized.indexOf('.');
                String major = end < 0 ? normalized.replaceFirst("[^0-9].*$", "") : normalized.substring(0, end);
                try {
                    return Integer.parseInt(major) >= 20;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        };
    }
}
