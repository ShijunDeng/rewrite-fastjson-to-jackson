package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark manifest and JSON configuration constraints for HTTP loader 17. */
public final class FindHttpLoaderJsonRisks extends Recipe {
    private static final Set<String> ANGULAR = Set.of(
            "@angular/core", "@angular/common", "@angular/cli", "@angular/compiler-cli",
            "@angular-devkit/build-angular"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-translate HTTP loader 17 project risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved loader declarations, core/Angular/TypeScript alignment, internal package " +
               "paths and TypeScript strict/module-resolution constraints.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                if (!HttpLoaderSupport.isProjectPath(path)) return visited;
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsDirectLoader(document.getValue()) ? inspectManifest(visited) : visited;
                }
                if ((file.startsWith("tsconfig") || file.startsWith("jsconfig")) &&
                    containsLoaderReference(document.getValue())) return inspectConfig(visited);
                return visited;
            }

            private Json.Member inspectManifest(Json.Member member) {
                String name = UpgradeSelectedNgxTranslateHttpLoaderDependency.key(member);
                boolean dependency = UpgradeSelectedNgxTranslateHttpLoaderDependency
                        .directDependencySection(getCursor()) != null;
                if (dependency && HttpLoaderSupport.HTTP_LOADER.equals(name) && !exact(member, HttpLoaderSupport.TARGET)) {
                    return mark(member, "This @ngx-translate/http-loader declaration was not migrated; resolve its protocol, range, catalog or unlisted version deliberately");
                }
                if (dependency && HttpLoaderSupport.CORE.equals(name) && !targetCore(member)) {
                    return mark(member, "Align @ngx-translate/core with HTTP loader 17 and review its independent API/provider migration");
                }
                if (dependency && ANGULAR.contains(name) && belowMajor(member, 16)) {
                    return mark(member, "HTTP loader 17 requires Angular common/core >=16; align the complete Angular CLI/compiler/build and TypeScript matrix");
                }
                if (dependency && "typescript".equals(name) && belowVersion(member, 4, 9)) {
                    return mark(member, "Use the exact TypeScript range supported by the selected Angular 16+ release before compiling HTTP loader 17");
                }
                return member;
            }

            private Json.Member inspectConfig(Json.Member member) {
                String name = UpgradeSelectedNgxTranslateHttpLoaderDependency.key(member);
                if ("strict".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return mark(member, "Run strict typecheck for the v17 TranslateLoader TranslationObject contract even if project-wide strict remains false");
                }
                if ("moduleResolution".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    "classic".equalsIgnoreCase(String.valueOf(literal.getValue()))) {
                    return mark(member, "Classic module resolution is unsuitable for modern Angular package exports");
                }
                return member;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value) || !isInternal(value)) return visited;
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                if (!HttpLoaderSupport.isProjectPath(path) || path.getFileName() == null) return visited;
                String file = path.getFileName().toString();
                if ("package.json".equals(file) && containsDirectLoader(document.getValue())) {
                    return SearchResult.found(visited,
                            "This manifest/build value references ngx-translate HTTP loader internals; use root public exports");
                }
                if ((file.startsWith("tsconfig") || file.startsWith("jsconfig")) &&
                    containsLoaderReference(document.getValue())) {
                    return SearchResult.found(visited,
                            "This resolver path pins HTTP loader internals; resolve @ngx-translate/http-loader from its public root export");
                }
                return visited;
            }
        };
    }

    private static boolean containsDirectLoader(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedNgxTranslateHttpLoaderDependency.SECTIONS.contains(
                        UpgradeSelectedNgxTranslateHttpLoaderDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        HttpLoaderSupport.HTTP_LOADER.equals(
                                UpgradeSelectedNgxTranslateHttpLoaderDependency.key(dependency))));
    }

    private static boolean containsLoaderReference(Json tree) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains(HttpLoaderSupport.HTTP_LOADER);
        }
        if (tree instanceof Json.Member member) return containsLoaderReference(member.getValue());
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindHttpLoaderJsonRisks::containsLoaderReference);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindHttpLoaderJsonRisks::containsLoaderReference);
        }
        return false;
    }

    private static boolean isInternal(String value) {
        return value.contains("node_modules/@ngx-translate/http-loader/") ||
               value.contains("@ngx-translate/http-loader/lib/") ||
               value.contains("@ngx-translate/http-loader/src/");
    }

    private static boolean exact(Json.Member member, String version) {
        return member.getValue() instanceof Json.Literal literal && version.equals(literal.getValue());
    }

    private static boolean targetCore(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value &&
               value.matches("[~^]?17\\.0\\.0");
    }

    private static boolean belowMajor(Json.Member member, int major) {
        int[] version = scalar(member);
        return version == null || version[0] < major;
    }

    private static boolean belowVersion(Json.Member member, int major, int minor) {
        int[] version = scalar(member);
        return version == null || version[0] < major || version[0] == major && version[1] < minor;
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) return null;
        String candidate = value.startsWith("^") || value.startsWith("~") ? value.substring(1) : value;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Json.Member mark(Json.Member member, String message) {
        return SearchResult.found(member, message);
    }
}
