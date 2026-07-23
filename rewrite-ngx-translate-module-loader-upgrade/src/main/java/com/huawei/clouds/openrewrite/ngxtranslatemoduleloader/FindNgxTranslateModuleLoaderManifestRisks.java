package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks manifest nodes that must be coordinated with the 5.1.0 peer and toolchain floor. */
public final class FindNgxTranslateModuleLoaderManifestRisks extends Recipe {
    private static final Set<String> ANGULAR = Set.of(
            "@angular/animations", "@angular/cdk", "@angular/cli", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/core", "@angular/forms", "@angular/material",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/router",
            "@angular-devkit/build-angular");

    @Override
    public String getDisplayName() {
        return "Find ngx-translate-module-loader 5.1.0 manifest risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact package.json values for unresolved loader ownership, Angular/core peer floors, and " +
               "the minimum Angular 16 TypeScript and Node toolchain.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!NgxTranslateModuleLoaderSupport.isPackageJson(document.getSourcePath()) ||
                    !containsManagedPackage(document.getValue()) ||
                    !(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) return visited;
                String name = NgxTranslateModuleLoaderSupport.key(visited);
                boolean direct = !NgxTranslateModuleLoaderSupport.directDependencySection(getCursor()).isEmpty();
                if (direct && NgxTranslateModuleLoaderSupport.PACKAGE.equals(name) &&
                    !NgxTranslateModuleLoaderSupport.target(declaration)) {
                    String reason = NgxTranslateModuleLoaderSupport.selected(declaration)
                            ? "The workbook-selected loader declaration remains; run the strict upgrade"
                            : "This loader constraint is outside the workbook exact/caret/tilde source set; choose 5.1.0 deliberately";
                    return markValue(visited, reason + " and regenerate the lockfile");
                }
                if (direct && ANGULAR.contains(name) && !atLeastMajor(declaration, 16)) {
                    return markValue(visited, name + " must be aligned to an Angular 16+ compatible framework/CLI/compiler set for loader 5.1.0");
                }
                if (direct && "@ngx-translate/core".equals(name) && !atLeastMajor(declaration, 16)) {
                    return markValue(visited, "ngx-translate-module-loader 5.1.0 peers on @ngx-translate/core >=16; align core and validate TranslationObject/provider behavior");
                }
                if (direct && "typescript".equals(name) && !atLeast(declaration, 4, 9, 3)) {
                    return markValue(visited, "Angular 16 starts at TypeScript 4.9.3; select the exact compiler range for the chosen Angular minor and align builders/tests/editors");
                }
                if ("engines".equals(parentKey()) && "node".equals(name) && !atLeast(declaration, 16, 14, 0)) {
                    return markValue(visited, "Angular 16 starts at Node 16.14.0; align local, CI, SSR, container, and deployment runtimes with the chosen Angular minor");
                }
                if (centralSelector(getCursor())) {
                    return markValue(visited, "Central package-manager ownership detected for ngx-translate-module-loader; update the selector and every consumer atomically, then regenerate all lockfiles");
                }
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? NgxTranslateModuleLoaderSupport.key((Json.Member) parent.getValue()) : "";
            }
        };
    }

    private static boolean centralSelector(Cursor cursor) {
        if (!NgxTranslateModuleLoaderSupport.underOverrideSection(cursor)) return false;
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Json.Member member &&
                NgxTranslateModuleLoaderSupport.overrideSelector(NgxTranslateModuleLoaderSupport.key(member))) {
                return true;
            }
        }
        return false;
    }

    private static Json.Member markValue(Json.Member member, String message) {
        return member.withValue(SearchResult.found(member.getValue(), message));
    }

    private static boolean containsManagedPackage(Json tree) {
        if (tree instanceof Json.Member member) {
            return NgxTranslateModuleLoaderSupport.PACKAGE.equals(NgxTranslateModuleLoaderSupport.key(member)) ||
                   NgxTranslateModuleLoaderSupport.overrideSelector(NgxTranslateModuleLoaderSupport.key(member)) ||
                   containsManagedPackage(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindNgxTranslateModuleLoaderManifestRisks::containsManagedPackage);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindNgxTranslateModuleLoaderManifestRisks::containsManagedPackage);
        }
        return false;
    }

    private static boolean atLeastMajor(String declaration, int minimum) {
        int[] version = scalar(declaration);
        return version != null && version[0] >= minimum;
    }

    private static boolean atLeast(String declaration, int major, int minor, int patch) {
        int[] version = scalar(declaration);
        if (version == null) return false;
        if (version[0] != major) return version[0] > major;
        if (version[1] != minor) return version[1] > minor;
        return version[2] >= patch;
    }

    private static int[] scalar(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) candidate = candidate.substring(1);
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
