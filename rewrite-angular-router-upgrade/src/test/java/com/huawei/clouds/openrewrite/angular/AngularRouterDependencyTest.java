package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class AngularRouterDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build()
                .activateRecipes("com.huawei.clouds.openrewrite.angular.UpgradeAngularRouterTo20_3_26"));
    }

    static Stream<String> visibleSingleVersionDeclarations() {
        String[] versions = {
                "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
                "12.2.16", "12.2.17", "13.1.3", "13.2.6"
        };
        return Stream.of("", "^", "~").flatMap(prefix -> Stream.of(versions).map(version -> prefix + version));
    }

    @ParameterizedTest(name = "spreadsheet single declaration {0}")
    @MethodSource("visibleSingleVersionDeclarations")
    void upgradesEveryVisibleExactCaretAndTildeDeclaration(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/router\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@angular/router\":\"20.3.26\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void supportsNpmYarnAndPnpmWorkspacePackageLocations() {
        rewriteRun(
                json("{\"packageManager\":\"npm@10.9.0\",\"dependencies\":{\"@angular/router\":\"^10.0.14\"}}",
                        "{\"packageManager\":\"npm@10.9.0\",\"dependencies\":{\"@angular/router\":\"20.3.26\"}}",
                        source -> source.path("package.json")),
                json("{\"packageManager\":\"yarn@4.9.2\",\"devDependencies\":{\"@angular/router\":\"~11.2.14\"}}",
                        "{\"packageManager\":\"yarn@4.9.2\",\"devDependencies\":{\"@angular/router\":\"20.3.26\"}}",
                        source -> source.path("packages/demo/package.json")),
                json("{\"packageManager\":\"pnpm@10.13.1\",\"peerDependencies\":{\"@angular/router\":\"12.2.17\"}}",
                        "{\"packageManager\":\"pnpm@10.13.1\",\"peerDependencies\":{\"@angular/router\":\"20.3.26\"}}",
                        source -> source.path("apps/web/package.json"))
        );
    }

    @Test
    void leavesComplexRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/router":">=10.0.14 <14"},
                  "devDependencies":{"@angular/router":"10.0.14 || 12.2.17"},
                  "peerDependencies":{"@angular/router":"10.0.14 - 13.2.6"},
                  "optionalDependencies":{"@angular/router":"^10.0.14 || ^12.2.17"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesProtocolsVariablesTagsUnlistedNewerAndTargetUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/router":"workspace:*"},
                  "devDependencies":{"@angular/router":"npm:@scope/router@10.2.5"},
                  "peerDependencies":{"@angular/router":"${ANGULAR_VERSION}"},
                  "optionalDependencies":{"@angular/router":"latest"},
                  "metadata":{"unlisted":"12.2.6","newer":"19.2.25","target":"20.3.26"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesCentralOwnersAndNestedMetadataUntouched() {
        rewriteRun(json(
                """
                {
                  "resolutions":{"@angular/router":"^10.2.5"},
                  "overrides":{"@angular/router":"11.2.14"},
                  "pnpm":{"overrides":{"@angular/router":"12.2.17"}},
                  "catalog":{"@angular/router":"13.1.3"},
                  "metadata":{"dependencies":{"@angular/router":"10.0.14"}}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void directSectionsMoveTogetherAndRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"dependencies\":{\"@angular/router\":\"^11.2.14\"},\"optionalDependencies\":{\"@angular/router\":\"~13.2.6\"}}",
                        "{\"dependencies\":{\"@angular/router\":\"20.3.26\"},\"optionalDependencies\":{\"@angular/router\":\"20.3.26\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void apacheNifiPinnedFixtureMigratesOnlyRouter() {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/common\":\"11.2.14\",\"@angular/core\":\"11.2.14\",\"@angular/platform-browser\":\"11.2.14\",\"@angular/router\":\"11.2.14\"}}",
                "{\"dependencies\":{\"@angular/common\":\"11.2.14\",\"@angular/core\":\"11.2.14\",\"@angular/platform-browser\":\"11.2.14\",\"@angular/router\":\"20.3.26\"}}",
                source -> source.path("nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json")
        ));
    }
}
