package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark manifest and JSON configuration constraints for ngx-translate 17. */
public final class FindNgxTranslateJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_PACKAGES = Set.of(
            "@angular/core", "@angular/common", "@angular/platform-browser", "@angular/platform-server",
            "@angular/cli", "@angular/compiler-cli", "@angular-devkit/build-angular"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-translate 17 manifest and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved core declarations, Angular/TypeScript baselines, HTTP-loader alignment, package " +
               "internals and strict/module-resolution configuration that constrain a core 17 migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                if (!NgxTranslateSupport.isProjectPath(path)) return visited;
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsDirectCore(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if ((file.startsWith("tsconfig") || file.startsWith("jsconfig")) &&
                    containsCoreReference(document.getValue())) return inspectConfig(visited);
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value) || !containsInternalPath(value)) return visited;
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!NgxTranslateSupport.isProjectPath(document.getSourcePath())) return visited;
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString();
                if ("package.json".equals(file) && containsDirectCore(document.getValue())) {
                    return SearchResult.found(visited,
                            "This manifest/build value references ngx-translate package internals; use root public exports and supported provider helpers");
                }
                if ((file.startsWith("tsconfig") || file.startsWith("jsconfig")) &&
                    containsCoreReference(document.getValue())) {
                    return SearchResult.found(visited,
                            "This TypeScript resolver path pins ngx-translate internals; resolve @ngx-translate/core from its public root export");
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedNgxTranslateCoreDependency.key(member);
                boolean dependency = isRootDependencyMember();
                if (dependency && NgxTranslateSupport.PACKAGE.equals(name) && !target(member)) {
                    return markValue(member, unresolvedMessage(member));
                }
                if (dependency && ANGULAR_PACKAGES.contains(name) && belowMajor(member, 16)) {
                    return markValue(member, "@ngx-translate/core 17 declares Angular common/core >=16; align the complete Angular framework, CLI/compiler/build and TypeScript matrix before installing it");
                }
                if (dependency && "typescript".equals(name) && belowVersion(member, 4, 9)) {
                    return markValue(member, "This old TypeScript version cannot represent the Angular 16+ build baseline reliably; use the exact TypeScript range supported by the selected Angular release");
                }
                if (dependency && NgxTranslateSupport.HTTP_LOADER.equals(name)) {
                    return markValue(member, "Align @ngx-translate/http-loader with core 17 and migrate constructor prefix/suffix to provideTranslateHttpLoader while preserving backend/interceptor/SSR behavior");
                }
                return member;
            }

            private Json.Member inspectConfig(Json.Member member) {
                String name = UpgradeSelectedNgxTranslateCoreDependency.key(member);
                if ("strict".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return markValue(member, "ngx-translate 16+ tightened plugin types; run a strict typecheck even if the project-wide compiler option remains false");
                }
                if ("moduleResolution".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    "classic".equalsIgnoreCase(String.valueOf(literal.getValue()))) {
                    return markValue(member, "Classic module resolution is unsuitable for modern Angular package exports; choose the resolution mode required by the selected Angular builder");
                }
                return member;
            }

            private boolean isRootDependencyMember() {
                Cursor object = getCursor().getParentTreeCursor();
                if (object == null || !(object.getValue() instanceof Json.JsonObject)) return false;
                Cursor section = object.getParentTreeCursor();
                if (section == null || !(section.getValue() instanceof Json.Member enclosing) ||
                    !UpgradeSelectedNgxTranslateCoreDependency.SECTIONS.contains(
                            UpgradeSelectedNgxTranslateCoreDependency.key(enclosing))) return false;
                Cursor rootObject = section.getParentTreeCursor();
                if (rootObject == null || !(rootObject.getValue() instanceof Json.JsonObject)) return false;
                Cursor document = rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean containsDirectCore(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedNgxTranslateCoreDependency.SECTIONS.contains(
                        UpgradeSelectedNgxTranslateCoreDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        NgxTranslateSupport.PACKAGE.equals(
                                UpgradeSelectedNgxTranslateCoreDependency.key(dependency))));
    }

    private static boolean containsCoreReference(Json tree) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains(NgxTranslateSupport.PACKAGE);
        }
        if (tree instanceof Json.Member member) return containsCoreReference(member.getValue());
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindNgxTranslateJsonRisks::containsCoreReference);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindNgxTranslateJsonRisks::containsCoreReference);
        }
        return false;
    }

    private static boolean containsInternalPath(String value) {
        return value.contains("node_modules/@ngx-translate/core/") ||
               value.contains("@ngx-translate/core/lib/") || value.contains("@ngx-translate/core/src/");
    }

    private static boolean target(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && NgxTranslateSupport.TARGET.equals(literal.getValue());
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) {
            return "Non-string @ngx-translate/core declaration was not changed; resolve it to an owned target scalar";
        }
        if (value.contains(":") || value.contains("${") || value.contains("{{") ||
            value.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic @ngx-translate/core declaration was not changed; update its owning catalog/workspace/source deliberately";
        }
        if (value.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target @ngx-translate/core scalar was not changed; stage its actual Angular/core migration before selecting 17.0.0";
        }
        return "Complex @ngx-translate/core range was not changed; resolve the intended npm range and supported Angular matrix manually";
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

    private static Json.Member markValue(Json.Member member, String message) {
        return member.withValue(SearchResult.found(member.getValue(), message));
    }
}
