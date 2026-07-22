package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class NgxColorPickerDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNgxColorPickerDependency());
    }

    @ParameterizedTest(name = "upgrades XLSX {0} {1}")
    @MethodSource("spreadsheetVersionsAndSections")
    void upgradesEveryVisibleSpreadsheetVersionInEveryDirectSection(String section, String version) {
        rewriteRun(packageVersion(section, version, "matrix/" + section + "/" + version + "/package.json"));
    }

    @ParameterizedTest(name = "upgrades anchored declaration {0}")
    @ValueSource(strings = {"^13.0.0", "~13.0.0", "^14.0.0", "~14.0.0"})
    void upgradesOnlyAnchoredSingleVersions(String declaration) {
        rewriteRun(packageVersion("dependencies", declaration,
                "prefix/" + Math.abs(declaration.hashCode()) + "/package.json"));
    }

    @Test
    void upgradesAllDirectSectionsAndPreservesFormatting() {
        rewriteRun(json(
                """
                {
                    "dependencies" : {"ngx-color-picker" : "13.0.0"},
                    "devDependencies" : {"ngx-color-picker" : "^13.0.0"},
                    "peerDependencies" : {"ngx-color-picker" : "~14.0.0"},
                    "optionalDependencies" : {"ngx-color-picker" : "14.0.0"}
                }
                """,
                """
                {
                    "dependencies" : {"ngx-color-picker" : "20.1.1"},
                    "devDependencies" : {"ngx-color-picker" : "20.1.1"},
                    "peerDependencies" : {"ngx-color-picker" : "20.1.1"},
                    "optionalDependencies" : {"ngx-color-picker" : "20.1.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves non-whitelist declaration {0}")
    @ValueSource(strings = {
            "12.0.1", "13.0.1", "13.1.0", "14.0.1", "14.1.0", "15.0.0", "16.0.0", "17.0.0",
            "18.0.0", "19.0.0", "20.0.0", "20.1.0", "20.1.1", "^20.1.1", "~20.1.1", "21.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "unlisted/" + Math.abs(declaration.hashCode()) + "/package.json"));
    }

    @ParameterizedTest(name = "leaves unsupported declaration {0}")
    @ValueSource(strings = {
            "=13.0.0", "=14.0.0", ">=13.0.0", ">14.0.0", "<=14.0.0", "13.0.0 || 14.0.0",
            "13.0.0 - 14.0.0", "13.x", "14.*", "*", "latest", "next", "13.0.0-beta.1",
            "14.0.0+company.2", "${COLOR_PICKER_VERSION}", "$colorPickerVersion", "workspace:13.0.0",
            "workspace:^14.0.0", "npm:@company/color-picker@14.0.0", "file:../ngx-color-picker",
            "link:../ngx-color-picker", "portal:../ngx-color-picker", "catalog:ngx-color-picker",
            "github:zefoy/ngx-color-picker#v14.0.0",
            "git+https://github.com/zefoy/ngx-color-picker.git#557140f",
            "https://registry.example/ngx-color-picker-13.0.0.tgz"
    })
    void leavesRangesProtocolsAliasesTagsAndVariablesUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "unsupported/" + Math.abs(declaration.hashCode()) + "/package.json"));
    }

    @Test
    void ignoresCentralOwnersNonStringValuesAndNestedLookalikes() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-color-picker": null},
                  "devDependencies": {"ngx-color-picker": {"version": "14.0.0"}},
                  "peerDependencies": {"ngx-color-picker": ["13.0.0"]},
                  "optionalDependencies": {"ngx-color-picker": false},
                  "overrides": {"ngx-color-picker": "14.0.0"},
                  "resolutions": {"ngx-color-picker": "13.0.0"},
                  "pnpm": {"overrides": {"ngx-color-picker": "14.0.0"}},
                  "dependenciesMeta": {"ngx-color-picker": {"built": false}},
                  "custom": {"dependencies": {"ngx-color-picker": "13.0.0"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void ignoresSimilarPackagesLockfilesBackupsAndOrdinaryFiles() {
        rewriteRun(
                json("""
                     {"dependencies":{"@iplab/ngx-color-picker":"14.0.0","@company/ngx-color-picker":"13.0.0","ngx-color-picker-next":"14.0.0"}}
                     """, spec -> spec.path("package.json")),
                json("""
                     {"packages":{"":{"dependencies":{"ngx-color-picker":"14.0.0"}},"node_modules/ngx-color-picker":{"version":"14.0.0"}}}
                     """, spec -> spec.path("package-lock.json")),
                json("{\"dependencies\":{\"ngx-color-picker\":\"13.0.0\"}}",
                        spec -> spec.path("fixtures/dependencies.json")),
                text("ngx-color-picker@^14.0.0:\n  version \"14.0.0\"\n", spec -> spec.path("yarn.lock")),
                text("ngx-color-picker@13.0.0:\n  resolution: {integrity: sha512-example}\n",
                        spec -> spec.path("pnpm-lock.yaml"))
        );
    }

    @ParameterizedTest(name = "excludes generated/install/cache path {0}")
    @ValueSource(strings = {
            "node_modules/pkg/package.json", ".pnpm/pkg/package.json", ".yarn/cache/package.json",
            ".npm/cache/package.json", ".gradle/cache/package.json", ".mvn/cache/package.json",
            ".m2/repository/package.json", ".angular/cache/package.json", ".output/server/package.json",
            "generated/package.json", "generated-sources/package.json", "generatedClient/package.json",
            "install/package.json", "installation/package.json", "installer/package.json", "dist/package.json",
            "build/package.json", "target/package.json", "coverage/package.json", "storybook-static/package.json",
            "reports/package.json", "test-results/package.json"
    })
    void ignoresGeneratedInstallAndCacheDirectories(String path) {
        rewriteRun(json("{\"dependencies\":{\"ngx-color-picker\":\"14.0.0\"}}", spec -> spec.path(path)));
    }

    @Test
    void upgradesWorkspaceChildIndependently() {
        rewriteRun(
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", spec -> spec.path("package.json")),
                json("{\"dependencies\":{\"ngx-color-picker\":\"~14.0.0\"}}",
                        "{\"dependencies\":{\"ngx-color-picker\":\"20.1.1\"}}",
                        spec -> spec.path("apps/designer/package.json"))
        );
    }

    @Test
    void dependencyUpgradeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), json(
                "{\"dependencies\":{\"ngx-color-picker\":\"^13.0.0\"}}",
                "{\"dependencies\":{\"ngx-color-picker\":\"20.1.1\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void yamlRecipesAreDiscoverableAndValid() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxcolorpicker")
                .scanYamlResources().build();
        for (String name : new String[]{
                "com.huawei.clouds.openrewrite.ngxcolorpicker.UpgradeNgxColorPickerTo20_1_1",
                "com.huawei.clouds.openrewrite.ngxcolorpicker.AuditNgxColorPicker20Compatibility",
                "com.huawei.clouds.openrewrite.ngxcolorpicker.MigrateNgxColorPickerTo20_1_1"
        }) {
            assertTrue(environment.activateRecipes(name).validate().isValid(), name);
        }
    }

    private static Stream<Arguments> spreadsheetVersionsAndSections() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("13.0.0", "14.0.0")
                        .map(version -> Arguments.of(section, version)));
    }

    private static SourceSpecs packageVersion(String section, String declaration, String path) {
        return json("{\"" + section + "\":{\"ngx-color-picker\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"ngx-color-picker\":\"20.1.1\"}}", spec -> spec.path(path));
    }

    private static SourceSpecs noChangeVersion(String declaration, String path) {
        return json("{\"dependencies\":{\"ngx-color-picker\":\"" + declaration + "\"}}",
                spec -> spec.path(path));
    }
}
