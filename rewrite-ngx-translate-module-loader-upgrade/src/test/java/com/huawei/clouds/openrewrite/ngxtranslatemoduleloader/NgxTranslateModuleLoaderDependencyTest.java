package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class NgxTranslateModuleLoaderDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.UpgradeNgxTranslateModuleLoaderTo5_1_0";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.MigrateNgxTranslateModuleLoaderTo5_1_0";

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("versionsAndSections")
    void upgradesEveryWorkbookVersionInEveryDirectSection(String section, String version) {
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"" + section + "\":{\"@larscom/ngx-translate-module-loader\":\"" + version + "\"}}",
                "{\"" + section + "\":{\"@larscom/ngx-translate-module-loader\":\"5.1.0\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.1.1", "^3.1.1", "~3.1.1", "3.1.2", "^3.1.2", "~3.1.2"})
    void preservesExactCaretOrTildeAnchor(String declaration) {
        String target = declaration.startsWith("^") ? "^5.1.0" : declaration.startsWith("~") ? "~5.1.0" : "5.1.0";
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"" + target + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void sourceWhitelistExactlyMatchesWorkbook() {
        assertEquals(Set.of("3.1.1", "3.1.2"), NgxTranslateModuleLoaderSupport.SOURCES);
    }

    @Test
    void realKonsumGandalfManifestAtFixedCommit() {
        // KonsumGandalf/rsdp@cd46193bab67a9790ac8837869f872ae2e918c69
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"dependencies\":{\"@angular/core\":\"~16.0.0\",\"@larscom/ngx-translate-module-loader\":\"^3.1.1\"}}",
                "{\"dependencies\":{\"@angular/core\":\"~16.0.0\",\"@larscom/ngx-translate-module-loader\":\"^5.1.0\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void realKhalifaTemplateManifestAtFixedCommit() {
        // khalifa005/khalifa-angular-template@6ad63fa55fd6d50ae5cef5b8f1d63bcbd9af3be9
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"dependencies\":{\"@angular/router\":\"^14.2.12\",\"@larscom/ngx-translate-module-loader\":\"^3.1.1\"}}",
                "{\"dependencies\":{\"@angular/router\":\"^14.2.12\",\"@larscom/ngx-translate-module-loader\":\"^5.1.0\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void realAngularTranslateManifestAtFixedCommit() {
        // thomas-chu-30/angular-translate@68f5bab9009cbc5a2d346ca5c7ee2e95e3672a9f
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"^3.1.2\",\"@ngx-translate/core\":\"^14.0.0\"}}",
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"^5.1.0\",\"@ngx-translate/core\":\"^14.0.0\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3.1.0", "3.1.3", "3.2.0", "4.0.0", "5.0.0", "5.1.0", "^5.1.0", "~5.1.0",
            ">=3.1.1 <6", "3.1.1 || 3.1.2", "3.1.1 - 5.1.0", "3.x", "*", "latest", "next",
            "workspace:^3.1.1", "workspace:*", "catalog:loader", "npm:@company/loader@3.1.1",
            "file:../loader", "link:../loader", "portal:../loader", "github:larscom/ngx-translate-module-loader#v3.1.2",
            "git+https://github.com/larscom/ngx-translate-module-loader.git#96924b0",
            "https://registry.example/loader-3.1.2.tgz", "3.1.2-beta.1", "3.1.2+build.1"
    })
    void leavesUnsupportedVersionsRangesAndProtocolsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"" + declaration + "\"}}",
                source -> source.path("unsupported/" + Math.abs(declaration.hashCode()) + "/package.json")));
    }

    @Test
    void ignoresCentralOwnersNestedLookalikesAndNonStrings() {
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json("""
                {
                  "dependencies":{"@larscom/ngx-translate-module-loader":null},
                  "devDependencies":{"@larscom/ngx-translate-module-loader":{"version":"3.1.2"}},
                  "overrides":{"@larscom/ngx-translate-module-loader":"3.1.2"},
                  "resolutions":{"parent>@larscom/ngx-translate-module-loader":"3.1.1"},
                  "pnpm":{"overrides":{"@larscom/ngx-translate-module-loader":"3.1.2"}},
                  "custom":{"dependencies":{"@larscom/ngx-translate-module-loader":"3.1.1"}}
                }
                """, source -> source.path("package.json")));
    }

    @Test
    void ignoresLockfilesOrdinaryJsonAndSimilarPackages() {
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader-next\":\"3.1.2\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"3.1.2\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"3.1.1\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                text("@larscom/ngx-translate-module-loader@^3.1.2:\n  version 3.1.2\n",
                        source -> source.path("yarn.lock")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "node_modules/pkg/package.json", ".pnpm/pkg/package.json", ".yarn/cache/package.json",
            ".npm/cache/package.json", ".gradle/cache/package.json", ".mvn/cache/package.json",
            ".m2/repository/package.json", ".angular/cache/package.json", ".output/server/package.json",
            "generated/package.json", "generated-sources/package.json", "generatedClient/package.json",
            "install/package.json", "installation/package.json", "installer/package.json", "dist/package.json",
            "build/package.json", "target/package.json", "coverage/package.json", "reports/package.json",
            "test-results/package.json", "storybook-static/package.json"
    })
    void excludesGeneratedInstallAndCacheParentPaths(String path) {
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency()), json(
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"3.1.2\"}}",
                source -> source.path(path)));
    }

    @Test
    void upgradesWorkspaceChildAndIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new UpgradeSelectedNgxTranslateModuleLoaderDependency())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"~3.1.2\"}}",
                        "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"~5.1.0\"}}",
                        source -> source.path("apps/web/package.json")));
    }

    @Test
    void publicRecipesAreDiscoverableAndRecommendedComposesPublicUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslatemoduleloader")
                .scanYamlResources().build();
        var upgrade = environment.activateRecipes(UPGRADE);
        var migrate = environment.activateRecipes(MIGRATE);
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
        assertEquals(UPGRADE, migrate.getRecipeList().get(0).getName());
    }

    private static Stream<Arguments> versionsAndSections() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("3.1.1", "3.1.2").map(version -> Arguments.of(section, version)));
    }
}
