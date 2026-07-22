package com.huawei.clouds.openrewrite.swiper;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark manifest and JSON build choices that constrain a Swiper 12 migration. */
public final class FindSwiperJsonRisks extends Recipe {
    private static final Set<String> WRAPPERS = Set.of(
            "angular2-useful-swiper", "ngx-swiper-wrapper", "vue-awesome-swiper", "react-id-swiper",
            "svelte-swiper", "swiper-solid"
    );
    private static final Set<String> TOOLING = Set.of(
            "typescript", "webpack", "webpack-cli", "webpack-dev-server", "rollup", "vite", "parcel",
            "jest", "ts-jest", "babel-jest"
    );

    @Override
    public String getDisplayName() {
        return "Find Swiper 12 manifest and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved Swiper declarations, third-party wrappers, legacy types/tooling, CommonJS package " +
               "mode, old browser targets and physical package paths in build, test and TypeScript JSON.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value) || !physicalEntry(value)) return visited;
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                if (!SwiperSupport.isProjectPath(path) || path.getFileName() == null) return visited;
                String file = path.getFileName().toString();
                if ("package.json".equals(file) && containsDirectSwiper(document.getValue())) {
                    return SearchResult.found(visited, "This manifest/build value pins a Swiper physical/internal file; use a public root, bundle, modules, CSS, Element, React, Vue or types export");
                }
                if (auditedConfig(file) && containsSwiperReference(document.getValue())) {
                    return SearchResult.found(visited, "This JSON resolver/copy/test configuration pins Swiper package internals; map it to an explicit Swiper 12 public export");
                }
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                if (!SwiperSupport.isProjectPath(path)) return visited;
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsDirectSwiper(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if (containsSwiperReference(document.getValue()) &&
                    (file.startsWith("tsconfig") || file.startsWith("jsconfig") || file.contains("jest") ||
                     file.contains("webpack") || file.contains("rollup") || file.contains("vite") ||
                     file.contains("parcel"))) return inspectConfig(visited);
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedSwiperDependency.key(member);
                String parent = parentKey();
                boolean dependency = UpgradeSelectedSwiperDependency.SECTIONS.contains(parent);
                if (dependency && SwiperSupport.PACKAGE.equals(name) && !target(member)) {
                    return mark(member, unresolvedMessage(member));
                }
                if (dependency && WRAPPERS.contains(name)) {
                    return mark(member, "This third-party Swiper wrapper spans incompatible framework and Swiper APIs; verify its Swiper 12 support or migrate the owning template/lifecycle to an official target entry");
                }
                if (dependency && "@types/swiper".equals(name)) {
                    return mark(member, "Swiper publishes its own TypeScript declarations; remove the legacy stub only after resolving declaration augmentation and compiler compatibility");
                }
                if (dependency && TOOLING.contains(name) && legacyTool(name, member)) {
                    return mark(member, "This older toolchain needs explicit proof for Swiper 12 pure ESM, .mjs/package exports, CSS exports and framework/SSR test transforms");
                }
                if ("type".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    "commonjs".equals(literal.getValue())) {
                    return mark(member, "The project is CommonJS while Swiper 7+ is ESM-only; define an ESM or supported async bundler boundary and remove direct require calls");
                }
                if ("browserslist".equals(name) && containsAny(member.getValue(), "ie 11", "ie <=", "Internet Explorer")) {
                    return mark(member, "Swiper 7+ ships modern ESM/ES2015 and Swiper 9 uses Pointer Events; this Internet Explorer target needs a product decision, not only transpilation");
                }
                return member;
            }

            private Json.Member inspectConfig(Json.Member member) {
                String name = UpgradeSelectedSwiperDependency.key(member);
                if ("moduleResolution".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Set.of("classic", "node", "node10").contains(String.valueOf(literal.getValue()).toLowerCase())) {
                    return mark(member, "This Swiper-aware config uses legacy module resolution; verify package export, .mjs and type conditions with bundler, node16 or nodenext resolution as appropriate");
                }
                if ("transformIgnorePatterns".equals(name) && containsAny(member.getValue(), "swiper")) {
                    return mark(member, "Jest transform exclusions that mention Swiper must parse pure ESM/.mjs and framework code in the actual test environment");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedSwiperDependency.key(enclosing) : "";
            }
        };
    }

    private static boolean containsDirectSwiper(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedSwiperDependency.SECTIONS.contains(UpgradeSelectedSwiperDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        SwiperSupport.PACKAGE.equals(UpgradeSelectedSwiperDependency.key(dependency))));
    }

    private static boolean containsSwiperReference(Json tree) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains("swiper");
        }
        if (tree instanceof Json.Member member) return containsSwiperReference(member.getValue());
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindSwiperJsonRisks::containsSwiperReference);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindSwiperJsonRisks::containsSwiperReference);
        }
        return false;
    }

    private static boolean physicalEntry(String value) {
        return value.contains("node_modules/swiper/") || value.contains("vendor/swiper/") ||
               value.matches(".*swiper/(?:dist|components|core|shared|modules)/.*") ||
               value.matches(".*swiper/(?:swiper|bundle)[-.].*");
    }

    private static boolean auditedConfig(String file) {
        return file.startsWith("tsconfig") || file.startsWith("jsconfig") || file.contains("jest") ||
               file.contains("webpack") || file.contains("rollup") || file.contains("vite") ||
               file.contains("parcel");
    }

    private static boolean containsAny(Json tree, String... needles) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            for (String needle : needles) if (value.contains(needle)) return true;
        }
        if (tree instanceof Json.Member member) return containsAny(member.getValue(), needles);
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(value -> containsAny(value, needles));
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(value -> containsAny(value, needles));
        }
        return false;
    }

    private static boolean target(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && SwiperSupport.TARGET.equals(literal.getValue());
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) {
            return "Non-string Swiper declaration was not changed; resolve it to an owned target scalar";
        }
        if (value.contains(":") || value.contains("${") || value.contains("{{") ||
            value.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic Swiper declaration was not changed; update its catalog/workspace/source owner deliberately";
        }
        if (value.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target Swiper scalar was not changed; stage its actual major-version migration before selecting 12.1.2";
        }
        return "Complex Swiper range was not changed; resolve the intended npm range and supported release path manually";
    }

    private static boolean legacyTool(String name, Json.Member member) {
        int[] version = scalar(member);
        if (version == null) return true;
        return switch (name) {
            case "typescript" -> version[0] < 4 || version[0] == 4 && version[1] < 7;
            case "webpack", "webpack-cli", "webpack-dev-server" -> version[0] < 5;
            case "rollup" -> version[0] < 3;
            case "vite" -> version[0] < 4;
            case "parcel" -> version[0] < 2;
            case "jest", "ts-jest", "babel-jest" -> version[0] < 29;
            default -> false;
        };
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) return null;
        String candidate = value.startsWith("^") || value.startsWith("~") ? value.substring(1) : value;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Json.Member mark(Json.Member member, String message) {
        return member.withValue(SearchResult.found(member.getValue(), message));
    }
}
