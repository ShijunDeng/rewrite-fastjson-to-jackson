package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark package/workspace choices that constrain a CDK 20 upgrade. */
public final class FindAngularCdkJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_PEERS = Set.of("@angular/core", "@angular/common");
    private static final Set<String> TOOLING = Set.of(
            "@angular/cli", "@angular-devkit/build-angular", "@angular/build", "@angular-devkit/core",
            "@angular-devkit/schematics", "@schematics/angular"
    );

    @Override
    public String getDisplayName() { return "Find Angular CDK 20 project compatibility risks"; }

    @Override
    public String getDescription() {
        return "Mark unresolved CDK declarations, Material/framework/toolchain alignment, TypeScript/Node/RxJS, " +
               "builders, global styles, SSR, Protractor and strict template configuration.";
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
                    return containsDirectCdk(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if ("angular.json".equals(file) || "workspace.json".equals(file)) return inspectWorkspace(visited);
                if (file.startsWith("tsconfig") && file.endsWith(".json")) return inspectTsConfig(visited);
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedAngularCdkDependency.key(member);
                String parent = parentKey();
                boolean dependency = UpgradeSelectedAngularCdkDependency.SECTIONS.contains(parent);
                if (dependency && "@angular/cdk".equals(name)) {
                    if (!isTarget(member)) return mark(member, unresolvedMessage(member));
                    return member;
                }
                if (dependency && "@angular/material".equals(name) && !isTarget(member)) {
                    return mark(member, "Angular Material should align with CDK 20.2.14; verify its package-group migrations and theme/API changes");
                }
                if (dependency && ANGULAR_PEERS.contains(name) && !atLeastMajor(member, 20)) {
                    return mark(member, "CDK 20 requires compatible Angular core/common peers; upgrade the Angular package group in major order");
                }
                if (dependency && TOOLING.contains(name) && !atLeastMajor(member, 20)) {
                    return mark(member, "Align Angular CLI/build/schematics tooling with Angular 20 and run official CDK migrations in major-version order");
                }
                if (dependency && "typescript".equals(name) && !supportedTypeScript(member)) {
                    return mark(member, "Angular 20 compiler tooling requires TypeScript >=5.8 and <6.0; align local, editor and CI compilation");
                }
                if (dependency && "rxjs".equals(name) && !supportedRxjs(member)) {
                    return mark(member, "CDK 20 supports RxJS ^6.5.3 or ^7.4.0; verify observable interop and avoid unsupported ranges");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(member)) {
                    return mark(member, "Angular 20 requires supported Node 20.19+, 22.12+ or 24+ runtimes across local, CI and images");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularCdkDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String builder &&
                    !builder.startsWith("@angular-devkit/build-angular:") && !builder.startsWith("@angular/build:")) {
                    return mark(member, "Custom builder must preserve Angular 20 templates, Sass module resolution, CDK prebuilt styles, assets and SSR output");
                }
                if ("styles".equals(name) && containsString(member.getValue(), "@angular/cdk")) {
                    return mark(member, "Global CDK/prebuilt stylesheet order affects overlay and accessibility CSS; verify deduplication, load order and specificity");
                }
                if (Set.of("server", "ssr", "prerender").contains(name)) {
                    return mark(member, "CDK DOM, overlay, focus and viewport services need browser guards and hydration-safe initial markup under SSR/prerender");
                }
                if ("builder".equals(name) && containsString(member.getValue(), "protractor")) {
                    return mark(member, "CDK Protractor harness support was removed; migrate the e2e target and HarnessEnvironment deliberately");
                }
                return member;
            }

            private Json.Member inspectTsConfig(Json.Member member) {
                String name = UpgradeSelectedAngularCdkDependency.key(member);
                if ("strictTemplates".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return mark(member, "Enable strictTemplates before relying on CDK 20 virtual-scroll/table template checks and fix inferred context errors");
                }
                if ("paths".equals(name) && containsString(member.getValue(), "@angular/cdk")) {
                    return mark(member, "TypeScript path mapping can bypass the installed public CDK package; remove private/deep aliases before migration");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedAngularCdkDependency.key(enclosing) : "";
            }
        };
    }

    private static boolean containsDirectCdk(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedAngularCdkDependency.SECTIONS.contains(UpgradeSelectedAngularCdkDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        "@angular/cdk".equals(UpgradeSelectedAngularCdkDependency.key(dependency))));
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) {
            return "Non-string @angular/cdk declaration was not changed; resolve it to an owned scalar target";
        }
        if (declaration.contains(":") || declaration.contains("${") || declaration.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic @angular/cdk declaration was not changed; update its owning catalog/workspace deliberately";
        }
        if (declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target @angular/cdk scalar was not changed; run supported major migrations before selecting 20.2.14";
        }
        return "Complex @angular/cdk range was not changed; resolve the supported Angular/Material/CDK peer matrix manually";
    }

    private static boolean containsString(Json tree, String wanted) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) return value.contains(wanted);
        if (tree instanceof Json.Member member) {
            return UpgradeSelectedAngularCdkDependency.key(member).contains(wanted) ||
                   containsString(member.getValue(), wanted);
        }
        if (tree instanceof Json.JsonObject object) return object.getMembers().stream().anyMatch(value -> containsString(value, wanted));
        if (tree instanceof Json.Array array) return array.getValues().stream().anyMatch(value -> containsString(value, wanted));
        return false;
    }

    private static boolean isTarget(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal &&
               UpgradeSelectedAngularCdkDependency.TARGET.equals(literal.getValue());
    }

    private static boolean supportedTypeScript(Json.Member member) {
        int[] version = scalar(member);
        return version != null && version[0] == 5 && version[1] >= 8;
    }

    private static boolean supportedRxjs(Json.Member member) {
        int[] version = scalar(member);
        return version != null && ((version[0] == 6 && version[1] >= 5) || version[0] == 7);
    }

    private static boolean supportedNode(Json.Member member) {
        int[] version = scalar(member);
        return version != null && ((version[0] == 20 && version[1] >= 19) ||
                (version[0] == 22 && version[1] >= 12) || version[0] >= 24);
    }

    private static boolean atLeastMajor(Json.Member member, int major) {
        int[] version = scalar(member);
        return version != null && version[0] >= major;
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) return null;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~") ? declaration.substring(1) : declaration;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Json.Member mark(Json.Member member, String message) { return SearchResult.found(member, message); }
}
