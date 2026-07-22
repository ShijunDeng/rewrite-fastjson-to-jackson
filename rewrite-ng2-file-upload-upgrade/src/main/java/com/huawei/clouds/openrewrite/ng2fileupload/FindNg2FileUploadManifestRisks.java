package com.huawei.clouds.openrewrite.ng2fileupload;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark unresolved package and Angular 21 ecosystem ownership in package.json. */
public final class FindNg2FileUploadManifestRisks extends Recipe {
    private static final Pattern SIMPLE = Pattern.compile("[~^]?(\\d+)(?:[.](\\d+|x))?(?:[.](\\d+|x))?");
    private static final Set<String> ANGULAR_PACKAGES = Set.of(
            "@angular/animations", "@angular/cdk", "@angular/cli", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/core", "@angular/forms", "@angular/platform-browser",
            "@angular/platform-browser-dynamic", "@angular/router", "@angular-devkit/build-angular");
    private static final String OWNER =
            "This direct ng2-file-upload owner is outside the exact workbook AUTO target; resolve ranges, protocols, aliases, forks, catalogs, and unlisted versions deliberately";
    private static final String ANGULAR =
            "ng2-file-upload 10.0.0 peers on Angular core/common ^21.x.x; align the complete Angular CLI/compiler/runtime/CDK set to one Angular 21 release before installing";
    private static final String NODE =
            "Angular 21 requires Node ^20.19.0, ^22.12.0, or >=24.0.0; align local, CI, container, package-manager, and deployment runtimes";

    @Override
    public String getDisplayName() {
        return "Find ng2-file-upload 10 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved dependency owners, overrides, Angular 21 peers, Node, RxJS, TypeScript, tslib, and obsolete types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean old = active;
                active = Ng2FileUploadSupport.packageJson(document.getSourcePath()) &&
                         Ng2FileUploadSupport.containsPackageReference(document.printAll());
                Json.Document visited = active ? super.visitDocument(document, ctx) : document;
                active = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!active) return visited;
                String key = Ng2FileUploadSupport.key(visited);
                String value = Ng2FileUploadSupport.string(visited);
                String section = Ng2FileUploadSupport.directSection(getCursor());
                if (Ng2FileUploadSupport.PACKAGE.equals(key)) {
                    if (Ng2FileUploadSupport.overrideOwner(getCursor())) {
                        return Ng2FileUploadSupport.mark(visited,
                                "This override/resolution independently owns ng2-file-upload; align the package-manager graph and regenerate exactly one selected lockfile");
                    }
                    if (Ng2FileUploadSupport.SECTIONS.contains(section) && !Ng2FileUploadSupport.target(value)) {
                        return Ng2FileUploadSupport.mark(visited, OWNER);
                    }
                }
                if (Ng2FileUploadSupport.SECTIONS.contains(section) && ANGULAR_PACKAGES.contains(key) &&
                    !simpleMajor(value, 21)) return Ng2FileUploadSupport.mark(visited, ANGULAR + " (" + key + ")");
                if ("engines".equals(section) && "node".equals(key) && !angular21Node(value)) {
                    return Ng2FileUploadSupport.mark(visited, NODE);
                }
                if (Ng2FileUploadSupport.SECTIONS.contains(section) && "rxjs".equals(key) &&
                    !angular21Rxjs(value)) {
                    return Ng2FileUploadSupport.mark(visited,
                            "Angular 21 peers on RxJS ^6.5.3 or ^7.4.0; select one supported line and verify upload callbacks, subscriptions, errors, completion, and teardown");
                }
                if (Ng2FileUploadSupport.SECTIONS.contains(section) && "typescript".equals(key) &&
                    !angular21TypeScript(value)) {
                    return Ng2FileUploadSupport.mark(visited,
                            "Angular 21 compiler-cli requires TypeScript >=5.9 <6.0; align compiler, builders, test tooling, tsconfig strictness, and emitted targets");
                }
                if (Ng2FileUploadSupport.SECTIONS.contains(section) && "tslib".equals(key) &&
                    !targetTslib(value)) {
                    return Ng2FileUploadSupport.mark(visited,
                            "ng2-file-upload 10 and Angular 21 require tslib ^2.3.0; align the runtime helper owner and deduplicated install graph");
                }
                if (Ng2FileUploadSupport.SECTIONS.contains(section) && "@types/ng2-file-upload".equals(key)) {
                    return Ng2FileUploadSupport.mark(visited,
                            "ng2-file-upload publishes its own declarations; remove this obsolete external types owner after checking tsconfig types/typeRoots and editor resolution");
                }
                return visited;
            }
        };
    }

    private static boolean simpleMajor(String declaration, int wanted) {
        Matcher matcher = simple(declaration);
        return matcher != null && Integer.parseInt(matcher.group(1)) == wanted;
    }

    private static boolean angular21Rxjs(String declaration) {
        Matcher matcher = simple(declaration);
        if (matcher == null) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = number(matcher.group(2));
        int patch = number(matcher.group(3));
        return major == 6 && (minor > 5 || minor == 5 && patch >= 3) || major == 7 && minor >= 4;
    }

    private static boolean angular21TypeScript(String declaration) {
        Matcher matcher = simple(declaration);
        return matcher != null && Integer.parseInt(matcher.group(1)) == 5 && number(matcher.group(2)) >= 9;
    }

    private static boolean targetTslib(String declaration) {
        Matcher matcher = simple(declaration);
        return matcher != null && Integer.parseInt(matcher.group(1)) == 2 && number(matcher.group(2)) >= 3;
    }

    private static boolean angular21Node(String declaration) {
        if (declaration == null) return false;
        String compact = declaration.replaceAll("\\s+", "");
        if ("^20.19.0||^22.12.0||>=24.0.0".equals(compact)) return true;
        Matcher matcher = simple(declaration);
        if (matcher == null && declaration.matches(">=\\s*24(?:[.]0(?:[.]0)?)?")) return true;
        if (matcher == null) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = number(matcher.group(2));
        return major == 20 && minor >= 19 || major == 22 && minor >= 12 || major >= 24;
    }

    private static Matcher simple(String declaration) {
        if (declaration == null) return null;
        Matcher matcher = SIMPLE.matcher(declaration.trim());
        return matcher.matches() ? matcher : null;
    }

    private static int number(String value) {
        return value == null || "x".equals(value) ? 0 : Integer.parseInt(value);
    }
}
