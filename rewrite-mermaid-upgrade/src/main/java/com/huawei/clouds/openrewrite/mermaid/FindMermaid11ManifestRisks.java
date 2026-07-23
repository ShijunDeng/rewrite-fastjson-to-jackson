package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks package-manager and ESM decisions which cannot safely be rewritten. */
public final class FindMermaid11ManifestRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Mermaid 11 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped Mermaid declarations, central overrides/resolutions, browser aliases, and CommonJS " +
               "package mode without editing owner files or lockfiles.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean mermaidManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!MermaidSupport.isPackageJson(document.getSourcePath())) return document;
                boolean old = mermaidManifest;
                mermaidManifest = containsMermaid(document);
                Json.Document visited = super.visitDocument(document, ctx);
                mermaidManifest = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!mermaidManifest) return visited;
                String key = MermaidSupport.key(visited);
                String value = MermaidSupport.stringValue(visited);
                String section = MermaidSupport.directDependencySection(getCursor());
                if (MermaidSupport.PACKAGE.equals(key) && !section.isEmpty() &&
                    value != null && !isTarget(value)) {
                    return mark(visited, "Strict migration skipped this complex range, protocol, alias, tag, or unlisted Mermaid version; choose a tested 11.15.0 constraint and regenerate the lockfile with the owning package manager");
                }
                String parent = parentKey();
                if (MermaidSupport.PACKAGE.equals(key) &&
                    ("overrides".equals(parent) || "resolutions".equals(parent) ||
                     "pnpm".equals(parent) || "browser".equals(parent))) {
                    return mark(visited, "This central override, resolution, pnpm policy, or browser alias owns Mermaid independently; reconcile it with 11.15.0 and regenerate rather than patching generated lockfile entries");
                }
                if ("type".equals(key) && "commonjs".equals(value) && isRootMember()) {
                    return mark(visited, "Mermaid 10+ is ESM-only; update require-based code, Jest/Vitest transforms, bundler, SSR, Electron, and Node entry points before changing package module mode");
                }
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParent();
                Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? MermaidSupport.key(member) : "";
            }

            private boolean isRootMember() {
                return getCursor().getParent() != null && getCursor().getParent().getParent() != null &&
                       getCursor().getParent().getParent().getValue() instanceof Json.Document;
            }

            private Json.Member mark(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean isTarget(String value) {
        return MermaidSupport.TARGET_VERSION.equals(value) || ("^" + MermaidSupport.TARGET_VERSION).equals(value) ||
               ("~" + MermaidSupport.TARGET_VERSION).equals(value);
    }

    private static boolean containsMermaid(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) return false;
        for (Json child : root.getMembers()) {
            if (!(child instanceof Json.Member section) ||
                !MermaidSupport.DEPENDENCY_SECTIONS.contains(MermaidSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) continue;
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member &&
                    MermaidSupport.PACKAGE.equals(MermaidSupport.key(member))) return true;
            }
        }
        return false;
    }
}
