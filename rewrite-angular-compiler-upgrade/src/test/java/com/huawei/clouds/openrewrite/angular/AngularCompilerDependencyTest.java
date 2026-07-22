package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class AngularCompilerDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build()
                .activateRecipes("com.huawei.clouds.openrewrite.angular.UpgradeAngularCompilerTo20_3_26"));
    }

    static Stream<String> visibleDeclarations() {
        String[] versions = {"10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
                "12.2.16", "12.2.17", "13.1.3", "13.2.6"};
        return Stream.of("", "^", "~").flatMap(p -> Stream.of(versions).map(v -> p + v));
    }

    @ParameterizedTest(name = "spreadsheet declaration {0}")
    @MethodSource("visibleDeclarations")
    void migratesAllAndOnlyVisibleSingleVersions(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/compiler\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@angular/compiler\":\"20.3.26\"}}", s -> s.path("package.json")));
    }

    @Test
    void supportsEveryDirectSectionAndWorkspaceLocation() {
        rewriteRun(
                json("{\"devDependencies\":{\"@angular/compiler\":\"10.2.5\"}}", "{\"devDependencies\":{\"@angular/compiler\":\"20.3.26\"}}", s -> s.path("apps/a/package.json")),
                json("{\"peerDependencies\":{\"@angular/compiler\":\"^12.2.13\"}}", "{\"peerDependencies\":{\"@angular/compiler\":\"20.3.26\"}}", s -> s.path("libs/b/package.json")),
                json("{\"optionalDependencies\":{\"@angular/compiler\":\"~13.2.6\"}}", "{\"optionalDependencies\":{\"@angular/compiler\":\"20.3.26\"}}", s -> s.path("packages/c/package.json"))
        );
    }

    @Test
    void preservesComplexProtocolDynamicUnlistedNewerAndTargetDeclarations() {
        rewriteRun(json("""
                {"dependencies":{"@angular/compiler":">=10 <14"},"devDependencies":{"@angular/compiler":"10.0.14 || 13.2.6"},
                 "peerDependencies":{"@angular/compiler":"workspace:*"},"optionalDependencies":{"@angular/compiler":"${ANGULAR_VERSION}"},
                 "meta":{"unlisted":"12.2.15","newer":"19.2.0","target":"20.3.26"}}
                """, s -> s.path("package.json")));
    }

    @Test
    void preservesCentralOwnersNestedMetadataLockfilesAndOtherJson() {
        rewriteRun(
                json("{\"overrides\":{\"@angular/compiler\":\"10.0.14\"},\"resolutions\":{\"@angular/compiler\":\"^12.2.17\"},\"metadata\":{\"dependencies\":{\"@angular/compiler\":\"13.1.3\"}}}", s -> s.path("package.json")),
                json("{\"dependencies\":{\"@angular/compiler\":\"10.0.14\"}}", s -> s.path("package-lock.json")),
                json("{\"dependencies\":{\"@angular/compiler\":\"10.0.14\"}}", s -> s.path("fixtures.json"))
        );
    }

    @Test
    void pinnedApacheNifiStyleFixtureChangesOnlyCompiler() {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/common\":\"11.2.14\",\"@angular/compiler\":\"11.2.14\",\"@angular/core\":\"11.2.14\",\"@angular/router\":\"11.2.14\"}}",
                "{\"dependencies\":{\"@angular/common\":\"11.2.14\",\"@angular/compiler\":\"20.3.26\",\"@angular/core\":\"11.2.14\",\"@angular/router\":\"11.2.14\"}}",
                s -> s.path("nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json")));
    }

    @Test
    void strictRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@angular/compiler\":\"^12.2.17\"}}",
                        "{\"dependencies\":{\"@angular/compiler\":\"20.3.26\"}}", s -> s.path("package.json")));
    }
}
