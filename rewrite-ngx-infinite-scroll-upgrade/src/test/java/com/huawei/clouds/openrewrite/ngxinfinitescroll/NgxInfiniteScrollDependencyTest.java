package com.huawei.clouds.openrewrite.ngxinfinitescroll;

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

import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class NgxInfiniteScrollDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNgxInfiniteScrollDependency());
    }

    @ParameterizedTest(name = "upgrades XLSX {0} {1}")
    @MethodSource("spreadsheetVersionsAndSections")
    void upgradesEveryVisibleSpreadsheetVersionInEveryDirectSection(String section, String version) {
        rewriteRun(packageVersion(section, version, "matrix/" + section + "/" + version + "/package.json"));
    }

    @ParameterizedTest(name = "upgrades anchored declaration {0}")
    @ValueSource(strings = {
            "^9.1.0", "~9.1.0", "^10.0.1", "~10.0.1", "^13.0.1", "~13.0.1",
            "^13.1.0", "~13.1.0", "^14.0.1", "~14.0.1", "^16.0.0", "~16.0.0"
    })
    void upgradesOnlyAnchoredSingleVersions(String declaration) {
        rewriteRun(packageVersion("dependencies", declaration, "prefix/" + declaration.hashCode() + "/package.json"));
    }

    @Test
    void upgradesAllDirectSectionsAndPreservesFormatting() {
        rewriteRun(json(
                """
                {
                    "dependencies" : {"ngx-infinite-scroll" : "9.1.0"},
                    "devDependencies" : {"ngx-infinite-scroll" : "^10.0.1"},
                    "peerDependencies" : {"ngx-infinite-scroll" : "~13.1.0"},
                    "optionalDependencies" : {"ngx-infinite-scroll" : "16.0.0"}
                }
                """,
                """
                {
                    "dependencies" : {"ngx-infinite-scroll" : "17.0.1"},
                    "devDependencies" : {"ngx-infinite-scroll" : "17.0.1"},
                    "peerDependencies" : {"ngx-infinite-scroll" : "17.0.1"},
                    "optionalDependencies" : {"ngx-infinite-scroll" : "17.0.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves non-whitelist declaration {0}")
    @ValueSource(strings = {
            "9.0.0", "9.1.1", "10.0.0", "10.0.2", "11.0.0", "12.0.0", "13.0.0",
            "13.0.2", "13.1.1", "14.0.0", "14.0.2", "15.0.0", "16.0.1", "17.0.0",
            "17.0.1", "^17.0.1", "~17.0.1", "17.0.2", "18.0.0", "22.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "unlisted/" + declaration.hashCode() + "/package.json"));
    }

    @ParameterizedTest(name = "leaves unsupported declaration {0}")
    @ValueSource(strings = {
            "=9.1.0", "=10.0.1", "=13.0.1", "=13.1.0", "=14.0.1", "=16.0.0",
            ">=9.1.0", ">10.0.1", "<=16.0.0", "9.1.0 || 16.0.0", "10.0.1 - 16.0.0",
            "13.x", "*", "latest", "next", "13.1.0-beta.1", "14.0.1+company.2",
            "${NGX_SCROLL_VERSION}", "$scrollVersion", "workspace:9.1.0", "workspace:^16.0.0",
            "npm:@example/scroll@13.1.0", "file:../ngx-infinite-scroll", "link:../ngx-infinite-scroll",
            "portal:../ngx-infinite-scroll", "catalog:ngx-infinite-scroll",
            "github:orizens/ngx-infinite-scroll#v14.0.1",
            "git+https://github.com/orizens/ngx-infinite-scroll.git#bf09910",
            "https://registry.example/ngx-infinite-scroll-13.0.1.tgz"
    })
    void leavesRangesProtocolsAliasesTagsAndVariablesUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "unsupported/" + declaration.hashCode() + "/package.json"));
    }

    @Test
    void ignoresCentralOwnersNonStringValuesAndNestedLookalikes() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-infinite-scroll": null},
                  "devDependencies": {"ngx-infinite-scroll": {"version": "16.0.0"}},
                  "peerDependencies": {"ngx-infinite-scroll": ["14.0.1"]},
                  "optionalDependencies": {"ngx-infinite-scroll": false},
                  "overrides": {"ngx-infinite-scroll": "16.0.0"},
                  "resolutions": {"ngx-infinite-scroll": "14.0.1"},
                  "pnpm": {"overrides": {"ngx-infinite-scroll": "13.1.0"}},
                  "dependenciesMeta": {"ngx-infinite-scroll": {"built": false}},
                  "custom": {"dependencies": {"ngx-infinite-scroll": "9.1.0"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void ignoresSimilarPackagesLockfilesAndOtherFiles() {
        rewriteRun(
                json("""
                     {"dependencies":{"@types/ngx-infinite-scroll":"16.0.0","@example/ngx-infinite-scroll":"14.0.1","angular2-infinite-scroll":"10.0.1"}}
                     """, spec -> spec.path("package.json")),
                json("""
                     {"packages":{"":{"dependencies":{"ngx-infinite-scroll":"16.0.0"}},"node_modules/ngx-infinite-scroll":{"version":"16.0.0"}}}
                     """, spec -> spec.path("package-lock.json")),
                json("{\"dependencies\":{\"ngx-infinite-scroll\":\"16.0.0\"}}",
                        spec -> spec.path("node_modules/ngx-infinite-scroll/package.json")),
                json("{" + "\"dependencies\":{\"ngx-infinite-scroll\":\"14.0.1\"}}",
                        spec -> spec.path("fixtures/dependencies.json")),
                text("ngx-infinite-scroll@^13.1.0:\n  version \"13.1.0\"\n", spec -> spec.path("yarn.lock")),
                text("ngx-infinite-scroll@16.0.0:\n  resolution: {integrity: sha512-example}\n", spec -> spec.path("pnpm-lock.yaml"))
        );
    }

    @Test
    void upgradesWorkspaceChildIndependently() {
        rewriteRun(
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", spec -> spec.path("package.json")),
                json("{\"dependencies\":{\"ngx-infinite-scroll\":\"~16.0.0\"}}",
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                        spec -> spec.path("apps/feed/package.json"))
        );
    }

    @Test
    void yamlRecipesAreDiscoverableAndValid() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxinfinitescroll")
                .scanYamlResources().build();
        for (String name : new String[]{
                "com.huawei.clouds.openrewrite.ngxinfinitescroll.UpgradeNgxInfiniteScrollTo17_0_1",
                "com.huawei.clouds.openrewrite.ngxinfinitescroll.AuditNgxInfiniteScroll17Compatibility",
                "com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateNgxInfiniteScrollTo17_0_1"
        }) {
            org.junit.jupiter.api.Assertions.assertTrue(environment.activateRecipes(name).validate().isValid(), name);
        }
    }

    private static Stream<Arguments> spreadsheetVersionsAndSections() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("9.1.0", "10.0.1", "13.0.1", "13.1.0", "14.0.1", "16.0.0")
                        .map(version -> Arguments.of(section, version)));
    }

    private static SourceSpecs packageVersion(String section, String declaration, String path) {
        return json("{\"" + section + "\":{\"ngx-infinite-scroll\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"ngx-infinite-scroll\":\"17.0.1\"}}", spec -> spec.path(path));
    }

    private static SourceSpecs noChangeVersion(String declaration, String path) {
        return json("{\"dependencies\":{\"ngx-infinite-scroll\":\"" + declaration + "\"}}",
                spec -> spec.path(path));
    }
}
