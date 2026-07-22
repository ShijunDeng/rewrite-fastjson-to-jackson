package com.huawei.clouds.openrewrite.prometheus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.test.SourceSpecs.text;

class PrometheusDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.prometheus.UpgradePrometheusTo0_311_3";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.prometheus.MigratePrometheusTo0_311_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades exact workbook source {0}")
    @ValueSource(strings = {"v2.22.1+incompatible", "v1.8.3", "v0.44.0", "v0.46.0", "v0.48.1", "v0.49.1", "v0.50.1", "v0.54.1"})
    void upgradesEveryVisibleWorkbookSource(String version) {
        rewriteRun(goMod("module example.com/app\n\ngo 1.25\n\nrequire github.com/prometheus/prometheus " + version + "\n",
                         "module example.com/app\n\ngo 1.25\n\nrequire github.com/prometheus/prometheus v0.311.3\n"));
    }

    @Test
    void whitelistIsExactlyAllEightVisibleWorkbookCells() {
        assertEquals(Set.of("v2.22.1+incompatible", "v1.8.3", "v0.44.0", "v0.46.0", "v0.48.1", "v0.49.1", "v0.50.1", "v0.54.1"),
                PrometheusSupport.SOURCE_VERSIONS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v2.22.0+incompatible", "v0.43.0", "v0.44.1", "v0.45.0", "v0.47.0", "v0.48.0", "v0.50.0", "v0.55.0", "v0.300.0", "v0.311.3", "latest", "master"})
    void leavesUnlistedVersions(String version) {
        rewriteRun(goMod("module example.com/app\n\nrequire github.com/prometheus/prometheus " + version + "\n"));
    }

    @Test
    void upgradesBlockRequirementAndPreservesIndirectComment() {
        rewriteRun(goMod(
                "module example.com/app\n\nrequire (\n\tgithub.com/prometheus/prometheus v0.44.0 // indirect\n\tgithub.com/stretchr/testify v1.8.0\n)\n",
                "module example.com/app\n\nrequire (\n\tgithub.com/prometheus/prometheus v0.311.3 // indirect\n\tgithub.com/stretchr/testify v1.8.0\n)\n"));
    }

    @Test
    void preservesTabsAndCrLf() {
        rewriteRun(goMod("module example.com/app\r\nrequire\tgithub.com/prometheus/prometheus\tv0.46.0\t// pin\r\n",
                         "module example.com/app\r\nrequire\tgithub.com/prometheus/prometheus\tv0.311.3\t// pin\r\n"));
    }

    @Test
    void upgradesNestedMaintainedModule() {
        rewriteRun(text("module example.com/tool\nrequire github.com/prometheus/prometheus v0.48.1\n",
                "module example.com/tool\nrequire github.com/prometheus/prometheus v0.311.3\n",
                source -> source.path("tools/analyzer/go.mod")));
    }

    @Test
    void updatesBothRequireOwnersWithoutTouchingReplace() {
        rewriteRun(goMod(
                "module example.com/app\nrequire github.com/prometheus/prometheus v0.49.1\nreplace github.com/prometheus/prometheus => ../prometheus-v0.49.1\n",
                "module example.com/app\nrequire github.com/prometheus/prometheus v0.311.3\nreplace github.com/prometheus/prometheus => ../prometheus-v0.49.1\n"));
    }

    @Test
    void leavesReplaceAndExcludeVersionOperands() {
        rewriteRun(goMod("module example.com/app\nreplace github.com/prometheus/prometheus v0.44.0 => github.com/acme/prometheus v0.44.0\nexclude github.com/prometheus/prometheus v0.44.0\n"));
    }

    @Test
    void leavesReplaceAndExcludeBlocksUntouched() {
        rewriteRun(goMod(
                "module example.com/app\nreplace (\n\tgithub.com/prometheus/prometheus v0.44.0 => github.com/acme/prometheus v0.44.0\n)\nexclude (\n\tgithub.com/prometheus/prometheus v0.46.0\n)\n"));
    }

    @Test
    void closesNonRequireBlockWithCommentBeforeDirectRequire() {
        rewriteRun(goMod(
                "module example.com/app\nreplace (\n\tgithub.com/prometheus/prometheus v0.44.0 => ../fork\n) // replacements\nrequire github.com/prometheus/prometheus v0.46.0\n",
                "module example.com/app\nreplace (\n\tgithub.com/prometheus/prometheus v0.44.0 => ../fork\n) // replacements\nrequire github.com/prometheus/prometheus v0.311.3\n"));
    }

    @Test
    void leavesBareModuleLineOutsideRequireBlock() {
        rewriteRun(goMod("module example.com/app\ngithub.com/prometheus/prometheus v0.44.0\n"));
    }

    @Test
    void leavesCommentedRequirement() {
        rewriteRun(goMod("module example.com/app\n// require github.com/prometheus/prometheus v0.44.0\n"));
    }

    @Test
    void leavesGoSumChecksums() {
        rewriteRun(text("github.com/prometheus/prometheus v0.44.0 h1:abc\ngithub.com/prometheus/prometheus v0.44.0/go.mod h1:def\n",
                source -> source.path("go.sum")));
    }

    @Test
    void leavesArbitraryTextNamedDifferently() {
        rewriteRun(text("require github.com/prometheus/prometheus v0.44.0\n", source -> source.path("README.md")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vendor/github.com/acme/go.mod", "Vendor/github.com/acme/go.mod", "target/generated/go.mod", "build/go.mod", "dist/go.mod", "generated/go.mod", "generated-test-sources/go.mod"})
    void skipsGeneratedAndVendoredModules(String path) {
        rewriteRun(text("module example.com/app\nrequire github.com/prometheus/prometheus v0.44.0\n", source -> source.path(path)));
    }

    @ParameterizedTest(name = "real fixed-commit fixture {0}")
    @ValueSource(strings = {"v0.44.0", "v0.46.0", "v0.48.1", "v0.49.1", "v0.50.1", "v0.54.1"})
    void upgradesRealRepositoryDeclarationShapes(String version) {
        // Fixed fixtures: byrnedo/prometheus-gsheet@221e56d, googleforgames/open-match@d781be1,
        // lindb/lindb@612070e, uptrace/uptrace@f617f6c, pingcap/tidb@e35ad93,
        // juicedata/juicefs@b475bc0.
        rewriteRun(goMod("module example.com/fixture\n\nrequire (\n\tgithub.com/prometheus/prometheus " + version + "\n)\n",
                         "module example.com/fixture\n\nrequire (\n\tgithub.com/prometheus/prometheus v0.311.3\n)\n"));
    }

    private static org.openrewrite.test.SourceSpecs goMod(String before) {
        return text(before, source -> source.path("go.mod"));
    }

    private static org.openrewrite.test.SourceSpecs goMod(String before, String after) {
        return text(before, after, source -> source.path("go.mod"));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.prometheus").build();
    }
}
