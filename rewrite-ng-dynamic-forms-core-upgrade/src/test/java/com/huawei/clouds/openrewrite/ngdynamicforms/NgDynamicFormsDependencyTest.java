package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class NgDynamicFormsDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNgDynamicFormsCoreDependency());
    }

    @ParameterizedTest(name = "upgrades visible spreadsheet declaration {0}")
    @MethodSource("visibleDeclarations")
    void upgradesEveryVisibleExactCaretAndTildeDeclaration(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"18.0.0\"}}",
                s -> s.path("package.json")));
    }

    static Stream<String> visibleDeclarations() {
        return Stream.of("14.0.0", "15.0.0", "16.0.0", "17.0.0")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json("""
                {"dependencies":{"@ng-dynamic-forms/core":"14.0.0"},
                 "devDependencies":{"@ng-dynamic-forms/core":"^15.0.0"},
                 "peerDependencies":{"@ng-dynamic-forms/core":"~16.0.0"},
                 "optionalDependencies":{"@ng-dynamic-forms/core":"17.0.0"}}
                """, """
                {"dependencies":{"@ng-dynamic-forms/core":"18.0.0"},
                 "devDependencies":{"@ng-dynamic-forms/core":"18.0.0"},
                 "peerDependencies":{"@ng-dynamic-forms/core":"18.0.0"},
                 "optionalDependencies":{"@ng-dynamic-forms/core":"18.0.0"}}
                """, s -> s.path("apps/forms/package.json")));
    }

    @ParameterizedTest(name = "leaves unlisted or complex declaration {0}")
    @ValueSource(strings = {
            "13.0.0", "14.0.1", "14.2.9", "15.0.1", "16.0.7", "17.0.1", "17.4.2", "18.0.0", "19.0.0",
            ">=14.0.0 <18", "14.x", "15", "14.0.0 - 17.0.0", "^14.0.0 || ^16.0.0",
            "v14.0.0", "=15.0.0", " 16.0.0", "17.0.0 ", "16.0.0-beta.1", "17.0.0+corp.2",
            "workspace:^16.0.0", "npm:@example/forms@16.0.0", "file:../core", "link:../core",
            "github:udos86/ng-dynamic-forms#v16.0.0", "git+ssh://git@github.com/udos86/ng-dynamic-forms.git#v17.0.0",
            "https://registry.example/core-16.0.0.tgz", "latest", "next", "*", "$DYNAMIC_FORMS_VERSION"
    })
    void leavesUnlistedPatchesRangesAndNonRegistryDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@ng-dynamic-forms/core\":\"" + declaration + "\"}}",
                s -> s.path("package.json")));
    }

    @Test
    void leavesCentralOwnersNestedMetadataLookalikesAndGeneratedFilesAlone() {
        rewriteRun(
                json("{\"overrides\":{\"@ng-dynamic-forms/core\":\"16.0.0\"},\"resolutions\":{\"@ng-dynamic-forms/core\":\"17.0.0\"}}", s -> s.path("package.json")),
                json("{\"dependencies\":{\"nested\":{\"@ng-dynamic-forms/core\":\"16.0.0\"}},\"metadata\":{\"@ng-dynamic-forms/core\":\"17.0.0\"}}", s -> s.path("package.json")),
                json("{\"dependencies\":{\"@ng-dynamic-forms/core-testing\":\"16.0.0\",\"@example/ng-dynamic-forms-core\":\"17.0.0\",\"@ng-dynamic-forms/ui-basic\":\"16.0.0\"}}", s -> s.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@ng-dynamic-forms/core\":\"16.0.0\"}}}}", s -> s.path("package-lock.json")),
                json("{\"dependencies\":{\"@ng-dynamic-forms/core\":\"17.0.0\"}}", s -> s.path("fixtures/dependencies.json"))
        );
    }

    @Test
    void upgradesPinnedElectronMailerPackageOnly() {
        rewriteRun(json("""
                {"name":"mailer-poc","dependencies":{"@angular/core":"^12.2.0","@angular/forms":"^12.2.0",
                 "@ng-dynamic-forms/core":"^14.0.0","@ng-dynamic-forms/ui-material":"^14.0.0","rxjs":"~6.6.0"},
                 "devDependencies":{"typescript":"~4.3.5"}}
                """, """
                {"name":"mailer-poc","dependencies":{"@angular/core":"^12.2.0","@angular/forms":"^12.2.0",
                 "@ng-dynamic-forms/core":"18.0.0","@ng-dynamic-forms/ui-material":"^14.0.0","rxjs":"~6.6.0"},
                 "devDependencies":{"typescript":"~4.3.5"}}
                """, s -> s.path("dhrn/electron-mailer-poc/package.json")));
    }

    @Test
    void upgradesPinnedAngularFormBuilderPackageOnly() {
        rewriteRun(json("""
                {"dependencies":{"@angular/core":"~13.3.10","@angular/forms":"~13.3.10",
                 "@ng-dynamic-forms/core":"^15.0.0","@ng-dynamic-forms/ui-basic":"^15.0.0","rxjs":"~6.6.0"}}
                """, """
                {"dependencies":{"@angular/core":"~13.3.10","@angular/forms":"~13.3.10",
                 "@ng-dynamic-forms/core":"18.0.0","@ng-dynamic-forms/ui-basic":"^15.0.0","rxjs":"~6.6.0"}}
                """, s -> s.path("Patrick5078/Angular-form-builder/package.json")));
    }

    @Test
    void upgradesPinnedMdsoarPackageWithoutInferringAngularCompatibility() {
        rewriteRun(json("""
                {"dependencies":{"@angular/core":"^17.3.11","@angular/forms":"^17.3.11",
                 "@ng-dynamic-forms/core":"^16.0.0","rxjs":"^7.8.2"},"devDependencies":{"typescript":"~5.4.5"}}
                """, """
                {"dependencies":{"@angular/core":"^17.3.11","@angular/forms":"^17.3.11",
                 "@ng-dynamic-forms/core":"18.0.0","rxjs":"^7.8.2"},"devDependencies":{"typescript":"~5.4.5"}}
                """, s -> s.path("umd-lib/mdsoar-angular/package.json")));
    }

    @Test
    void dependencyMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), json(
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"^17.0.0\"}}",
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"18.0.0\"}}", s -> s.path("package.json")));
    }
}
