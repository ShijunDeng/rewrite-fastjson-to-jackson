package com.huawei.clouds.openrewrite.grafana;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class GrafanaImageUpgradeTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.grafana.UpgradeGrafanaImageTo12_1_1";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.grafana.MigrateGrafanaTo12_1_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.4.5", "7.5.16", "8.5.14", "9.1.7"})
    void upgradesEveryXlsxVersionInYamlImage(String version) {
        rewriteRun(yaml("services:\n  grafana:\n    image: grafana/grafana:" + version + "\n",
                "services:\n  grafana:\n    image: grafana/grafana:12.1.1\n",
                source -> source.path("docker-compose.yml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.4.5", "7.5.16", "8.5.14", "9.1.7"})
    void upgradesEveryXlsxVersionInDockerfile(String version) {
        rewriteRun(text("FROM grafana/grafana:" + version + "\n",
                "FROM grafana/grafana:12.1.1\n", source -> source.path("Dockerfile")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.4.5", "7.5.16", "8.5.14", "9.1.7"})
    void upgradesEveryXlsxVersionInHelmRepositoryTagOwner(String version) {
        rewriteRun(yaml("image:\n  repository: grafana/grafana\n  tag: " + version + "\n",
                "image:\n  repository: grafana/grafana\n  tag: 12.1.1\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void supportsOfficialEditionAndRegistryNames() {
        rewriteRun(yaml("services:\n  oss:\n    image: grafana/grafana-oss:7.4.5\n  enterprise:\n    image: docker.io/grafana/grafana-enterprise:8.5.14\n",
                "services:\n  oss:\n    image: grafana/grafana-oss:12.1.1\n  enterprise:\n    image: docker.io/grafana/grafana-enterprise:12.1.1\n",
                source -> source.path("compose.yaml")));
    }

    @Test
    void preservesDockerfilePlatformAndStage() {
        rewriteRun(text("FROM --platform=linux/amd64 grafana/grafana:9.1.7 AS runtime\n",
                "FROM --platform=linux/amd64 grafana/grafana:12.1.1 AS runtime\n",
                source -> source.path("containers/Grafana.dockerfile")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.4.4", "7.5.15", "8.5.13", "9.1.6", "10.0.0", "11.5.2", "12.1", "12.1.1", "13.0.0"})
    void leavesUnlistedTargetAndFutureVersions(String version) {
        rewriteRun(yaml("services:\n  grafana:\n    image: grafana/grafana:" + version + "\n",
                source -> source.path("docker-compose.yml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"latest", "${GRAFANA_VERSION}", "9.1.7-ubuntu", "v9.1.7", "9.1.7@sha256:abc"})
    void leavesDynamicVariantAndDigestTags(String tag) {
        rewriteRun(yaml("services:\n  grafana:\n    image: grafana/grafana:" + tag + "\n",
                source -> source.path("docker-compose.yml")));
    }

    @Test
    void ignoresSimilarImagesAndUnownedTag() {
        rewriteRun(yaml("services:\n  loki:\n    image: grafana/loki:9.1.7\nimage:\n  repository: example/grafana\n  tag: 7.4.5\n",
                source -> source.path("docker-compose.yml")));
    }

    @Test
    void leavesAmbiguousDuplicateImageOwnersUntouched() {
        rewriteRun(yaml("services:\n  grafana:\n    image: grafana/grafana:7.4.5\n    image: grafana/grafana:9.1.7\n",
                source -> source.path("docker-compose.yml")));
    }

    @Test
    void leavesAmbiguousHelmRepositoryAndTagOwnersUntouched() {
        rewriteRun(yaml("image:\n  repository: grafana/grafana\n  repository: grafana/grafana-oss\n  tag: 7.5.16\n  tag: 9.1.7\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void doesNotJoinDockerfileInstructionsAcrossLines() {
        rewriteRun(text("FROM\ngrafana/grafana:9.1.7\n", source -> source.path("Dockerfile")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/Dockerfile", "build/Dockerfile", "generated/Dockerfile", "generated-fixtures/Dockerfile",
            "install/Dockerfile", "installation/Dockerfile", "vendor/Dockerfile", "node_modules/x/Dockerfile",
            "bower_components/x/Dockerfile", ".m2/cache/Dockerfile", ".pnpm/store/Dockerfile", ".yarn/cache/Dockerfile",
            ".npm/cache/Dockerfile", ".angular/cache/Dockerfile", ".nx/cache/Dockerfile", ".next/cache/Dockerfile",
            ".cache/Dockerfile"})
    void excludesGeneratedInstalledAndBuiltTrees(String path) {
        rewriteRun(text("FROM grafana/grafana:9.1.7\n", source -> source.path(path)));
    }

    @Test
    void installNamedDockerfileLeafRemainsInProjectScope() {
        rewriteRun(
                text("FROM grafana/grafana:9.1.7\n", "FROM grafana/grafana:12.1.1\n",
                        source -> source.path("containers/install.Dockerfile")),
                text("FROM grafana/grafana:9.1.7\n", source -> source.path("install-cache/Dockerfile"))
        );
    }

    @Test
    void doesNotTreatOrdinaryTextAsDockerfile() {
        rewriteRun(text("FROM grafana/grafana:9.1.7\n", source -> source.path("README.md")));
    }

    @Test
    void whitelistIsExactAndVisible() {
        assertEquals(Set.of("7.4.5", "7.5.16", "8.5.14", "9.1.7"),
                GrafanaMigrationSupport.SOURCE_VERSIONS);
    }

    @Test
    void recipeIsDiscoverableValidAndIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                yaml("image: grafana/grafana:7.5.16\n", "image: grafana/grafana:12.1.1\n",
                        source -> source.path("grafana-deployment.yaml")));
        Recipe recipe = environment().activateRecipes(UPGRADE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.grafana")
                .scanYamlResources()
                .build();
    }
}
