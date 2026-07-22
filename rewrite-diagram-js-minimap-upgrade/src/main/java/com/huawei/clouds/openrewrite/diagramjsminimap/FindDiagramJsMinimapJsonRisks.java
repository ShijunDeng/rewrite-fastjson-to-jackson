package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark manifest and build choices that constrain a minimap 5 migration. */
public final class FindDiagramJsMinimapJsonRisks extends Recipe {
    private static final Set<String> SECTIONS = UpgradeSelectedDiagramJsMinimapDependency.SECTIONS;
    private static final Set<String> BUNDLERS = Set.of(
            "webpack", "webpack-cli", "webpack-dev-server", "rollup", "vite", "parcel",
            "@angular-devkit/build-angular"
    );

    @Override
    public String getDisplayName() {
        return "Find diagram-js-minimap 5 manifest and build risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved minimap declarations, diagram-js/bpmn-js compatibility, HammerJS, legacy bundlers, " +
               "ES2018 browser targets, CSS tree-shaking and JSON test/build configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                    return containsDirectMinimap(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if (Set.of("angular.json", "workspace.json", "babel.config.json", "jest.config.json").contains(file)) {
                    return inspectBuildConfig(visited);
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedDiagramJsMinimapDependency.key(member);
                String parent = parentKey();
                boolean dependency = SECTIONS.contains(parent);
                if (dependency && DiagramJsMinimapSupport.PACKAGE.equals(name) && !isTarget(member)) {
                    return mark(member, unresolvedMessage(member));
                }
                if (dependency && "diagram-js".equals(name) && !atLeastDiagramJs15_1(member)) {
                    return mark(member, "diagram-js-minimap 5.2.0 targets diagram-js 15.1 facilities; align direct diagram-js and every plugin before relying on one resolved runtime");
                }
                if (dependency && "bpmn-js".equals(name)) {
                    return mark(member, "bpmn-js owns the transitive diagram-js runtime; verify its resolved diagram-js is compatible with minimap 5.2.0 and all additional modules");
                }
                if (dependency && "hammerjs".equals(name)) {
                    return mark(member, "Minimap 5 no longer needs HammerJS and does not restore touch support; remove it only after proving no application-owned gesture usage");
                }
                if (dependency && BUNDLERS.contains(name) && legacyBundler(name, member)) {
                    return mark(member, "Legacy bundler/test parsing may not process diagram-js 9+ ES2018 and target dual main/module assets; verify production resolution and node_modules transpilation");
                }
                if ("browserslist".equals(name) && containsAny(member.getValue(), "ie 11", "ie <=", "Internet Explorer")) {
                    return mark(member, "diagram-js 9+ ships ES2018; an Internet Explorer browser target cannot be satisfied without an explicit transpilation/polyfill strategy");
                }
                if ("sideEffects".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return mark(member, "sideEffects:false can remove the required minimap CSS import; whitelist *.css or verify emitted production styles");
                }
                return member;
            }

            private Json.Member inspectBuildConfig(Json.Member member) {
                String name = UpgradeSelectedDiagramJsMinimapDependency.key(member);
                if ("styles".equals(name) && containsAny(member.getValue(), "diagram-js-minimap")) {
                    return mark(member, "Verify the public minimap CSS asset remains ordered, copied and reachable in every production/SSR/micro-frontend build");
                }
                if ("transformIgnorePatterns".equals(name) &&
                    containsAny(member.getValue(), "diagram-js", "bpmn-js", "bpmn-io")) {
                    return mark(member, "Test transform exclusions may fail to parse diagram-js ES2018 or resolve its ESM distribution; include the relevant bpmn-io packages when required");
                }
                if ("assets".equals(name) && containsAny(member.getValue(), "diagram-js-minimap")) {
                    return mark(member, "Copied minimap assets rely on node_modules layout; prefer importing the public CSS asset and verify output paths/cache busting");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedDiagramJsMinimapDependency.key(enclosing) : "";
            }
        };
    }

    private static boolean containsDirectMinimap(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                SECTIONS.contains(UpgradeSelectedDiagramJsMinimapDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        DiagramJsMinimapSupport.PACKAGE.equals(
                                UpgradeSelectedDiagramJsMinimapDependency.key(dependency))));
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) {
            return "Non-string diagram-js-minimap declaration was not changed; resolve it to an owned scalar target";
        }
        if (declaration.contains(":") || declaration.contains("${") ||
            declaration.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic diagram-js-minimap declaration was not changed; update its owning catalog/workspace deliberately";
        }
        if (declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target diagram-js-minimap scalar was not changed; stage the supported diagram-js/plugin migration before selecting 5.2.0";
        }
        return "Complex diagram-js-minimap range was not changed; resolve the supported diagram-js/bpmn-js/plugin matrix manually";
    }

    private static boolean isTarget(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal &&
               DiagramJsMinimapSupport.TARGET.equals(literal.getValue());
    }

    private static boolean atLeastDiagramJs15_1(Json.Member member) {
        int[] version = scalar(member);
        return version != null && (version[0] > 15 || version[0] == 15 && version[1] >= 1);
    }

    private static boolean legacyBundler(String name, Json.Member member) {
        int[] version = scalar(member);
        if (version == null) return true;
        return switch (name) {
            case "webpack", "webpack-cli", "webpack-dev-server" -> version[0] < 5;
            case "rollup" -> version[0] < 4;
            case "vite" -> version[0] < 5;
            case "parcel" -> version[0] < 2;
            case "@angular-devkit/build-angular" -> version[0] < 17;
            default -> false;
        };
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) return null;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static boolean containsAny(Json tree, String... needles) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            for (String needle : needles) if (value.contains(needle)) return true;
        }
        if (tree instanceof Json.Member member) return containsAny(member.getValue(), needles);
        if (tree instanceof Json.JsonObject object) return object.getMembers().stream().anyMatch(v -> containsAny(v, needles));
        if (tree instanceof Json.Array array) return array.getValues().stream().anyMatch(v -> containsAny(v, needles));
        return false;
    }

    private static Json.Member mark(Json.Member member, String message) {
        return SearchResult.found(member, message);
    }
}
