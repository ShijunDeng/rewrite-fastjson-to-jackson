package com.huawei.clouds.openrewrite.tweenjs;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark manifest and JSON build choices that constrain a Tween.js 23 migration. */
public final class FindTweenJsJsonRisks extends Recipe {
    private static final Set<String> SECTIONS = UpgradeSelectedTweenJsDependency.SECTIONS;
    private static final Set<String> TOOLING = Set.of(
            "typescript", "webpack", "webpack-cli", "webpack-dev-server", "rollup", "vite",
            "parcel", "jest", "ts-jest", "babel-jest"
    );

    @Override
    public String getDisplayName() {
        return "Find Tween.js 23 manifest and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved Tween.js declarations, legacy package/type ownership, old module tooling and " +
               "physical dist paths in package, TypeScript, Jest and build JSON configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsDirectTween(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if (containsTweenReference(document.getValue()) &&
                    (file.startsWith("tsconfig") || file.startsWith("jsconfig") ||
                     file.contains("jest") || file.contains("webpack") || file.contains("rollup") ||
                     file.contains("vite") || file.contains("parcel"))) {
                    return inspectConfig(visited);
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedTweenJsDependency.key(member);
                String parent = parentKey();
                boolean dependency = SECTIONS.contains(parent);
                if (dependency && TweenJsSupport.PACKAGE.equals(name) && !isTarget(member)) {
                    return mark(member, unresolvedMessage(member));
                }
                if (dependency && "@types/tween.js".equals(name)) {
                    return mark(member, "@tweenjs/tween.js 23 publishes its own dist/tween.d.ts; remove this legacy tween.js stub only after resolving any project-specific augmentations");
                }
                if (dependency && "tween.js".equals(name)) {
                    return mark(member, "tween.js is a separate legacy package; resolve duplicate runtime/API ownership before standardizing on @tweenjs/tween.js");
                }
                if (dependency && TOOLING.contains(name) && legacyTool(name, member)) {
                    return mark(member, "This older module toolchain needs an explicit test of Tween.js 23 package exports, ESM import, root CommonJS require and bundled types");
                }
                if (containsPhysicalEntry(member.getValue())) {
                    return mark(member, "This manifest/build value names a Tween.js physical dist entry; package exports in v23 require the public root or a deliberately managed copied/CDN asset");
                }
                return member;
            }

            private Json.Member inspectConfig(Json.Member member) {
                String name = UpgradeSelectedTweenJsDependency.key(member);
                if (containsPhysicalEntry(member.getValue())) {
                    return mark(member, "This JSON configuration pins a Tween.js physical dist path; switch resolver mappings to @tweenjs/tween.js or document an owned asset-copy boundary");
                }
                if ("moduleResolution".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Set.of("classic", "node", "node10").contains(String.valueOf(literal.getValue()).toLowerCase())) {
                    return mark(member, "This config also references Tween.js and uses legacy module resolution; verify package exports/type conditions with bundler, node16 or nodenext resolution as appropriate");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedTweenJsDependency.key(enclosing) : "";
            }
        };
    }

    private static boolean containsDirectTween(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                SECTIONS.contains(UpgradeSelectedTweenJsDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        TweenJsSupport.PACKAGE.equals(UpgradeSelectedTweenJsDependency.key(dependency))));
    }

    private static boolean containsTweenReference(Json tree) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains(TweenJsSupport.PACKAGE);
        }
        if (tree instanceof Json.Member member) return containsTweenReference(member.getValue());
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindTweenJsJsonRisks::containsTweenReference);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindTweenJsJsonRisks::containsTweenReference);
        }
        return false;
    }

    private static boolean containsPhysicalEntry(Json tree) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains(TweenJsSupport.PACKAGE + "/dist/") ||
                   value.contains("node_modules/@tweenjs/tween.js/dist/") ||
                   value.contains("vendor/@tweenjs/tween.js/dist/");
        }
        if (tree instanceof Json.Member member) return containsPhysicalEntry(member.getValue());
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindTweenJsJsonRisks::containsPhysicalEntry);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindTweenJsJsonRisks::containsPhysicalEntry);
        }
        return false;
    }

    private static boolean isTarget(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal &&
               TweenJsSupport.TARGET.equals(literal.getValue());
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) {
            return "Non-string @tweenjs/tween.js declaration was not changed; resolve it to an owned target scalar";
        }
        if (value.contains(":") || value.contains("${") || value.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic @tweenjs/tween.js declaration was not changed; update its owning catalog/workspace deliberately";
        }
        if (value.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target @tweenjs/tween.js scalar was not changed; stage and verify its supported path before selecting 23.1.1";
        }
        return "Complex @tweenjs/tween.js range was not changed; resolve the intended npm range and release path manually";
    }

    private static boolean legacyTool(String name, Json.Member member) {
        int[] version = scalar(member);
        if (version == null) return true;
        return switch (name) {
            case "typescript" -> version[0] < 4 || version[0] == 4 && version[1] < 7;
            case "webpack", "webpack-cli", "webpack-dev-server" -> version[0] < 5;
            case "rollup" -> version[0] < 3;
            case "vite" -> version[0] < 3;
            case "parcel" -> version[0] < 2;
            case "jest", "ts-jest", "babel-jest" -> version[0] < 29;
            default -> false;
        };
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) {
            return null;
        }
        String candidate = value.startsWith("^") || value.startsWith("~") ? value.substring(1) : value;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Json.Member mark(Json.Member member, String message) {
        return SearchResult.found(member, message);
    }
}
