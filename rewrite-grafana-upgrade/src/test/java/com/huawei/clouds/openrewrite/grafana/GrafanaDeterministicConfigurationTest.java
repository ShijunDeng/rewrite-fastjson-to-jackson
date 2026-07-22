package com.huawei.clouds.openrewrite.grafana;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class GrafanaDeterministicConfigurationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateGrafana12DeterministicConfiguration());
    }

    @Test
    void migratesElasticsearchBrowserAccessToServerProxy() {
        rewriteRun(yaml("apiVersion: 1\ndatasources:\n  - name: Elastic\n    type: elasticsearch\n    access: direct\n    url: http://elastic:9200\n",
                "apiVersion: 1\ndatasources:\n  - name: Elastic\n    type: elasticsearch\n    access: proxy\n    url: http://elastic:9200\n",
                source -> source.path("grafana/provisioning/datasources/elasticsearch.yaml")));
    }

    @Test
    void recognizesOfficialProvisioningSchemaWithoutGrafanaPath() {
        rewriteRun(yaml("apiVersion: 1\ndatasources:\n  - name: Elastic\n    type: elasticsearch\n    access: direct\n",
                "apiVersion: 1\ndatasources:\n  - name: Elastic\n    type: elasticsearch\n    access: proxy\n",
                source -> source.path("config/elasticsearch.yaml")));
    }

    @Test
    void doesNotTreatGenericDatasourceListAsGrafanaProvisioning() {
        rewriteRun(yaml("datasources:\n  - type: elasticsearch\n    access: direct\n",
                source -> source.path("config/application.yaml")));
    }

    @Test
    void doesNotChangeDirectAccessForAnotherDatasource() {
        rewriteRun(yaml("datasources:\n  - type: prometheus\n    access: direct\n",
                source -> source.path("grafana/provisioning/datasources/prometheus.yaml")));
    }

    @Test
    void doesNotTreatPluginMetadataAsElasticsearchDatasource() {
        rewriteRun(yaml("plugins:\n  - type: elasticsearch\n    access: direct\n",
                source -> source.path("grafana/plugin.yaml")));
    }

    @Test
    void leavesDuplicateElasticsearchOwnersForReview() {
        rewriteRun(yaml("datasources:\n  - type: elasticsearch\n    type: elasticsearch\n    access: direct\n    access: direct\n",
                source -> source.path("grafana/provisioning/datasources/elasticsearch.yaml")));
    }

    @Test
    void renamesSimplePluginEnvironmentMap() {
        rewriteRun(yaml("services:\n  grafana:\n    image: grafana/grafana:12.1.1\n    environment:\n      GF_INSTALL_PLUGINS: grafana-clock-panel,redis-datasource\n",
                "services:\n  grafana:\n    image: grafana/grafana:12.1.1\n    environment:\n      GF_PLUGINS_PREINSTALL_SYNC: grafana-clock-panel,redis-datasource\n",
                source -> source.path("docker-compose.yml")));
    }

    @Test
    void renamesSimplePluginEnvironmentSequenceFromRealRepositoryShape() {
        rewriteRun(yaml("services:\n  grafana:\n    image: grafana/grafana:12.1\n    environment:\n      - GF_INSTALL_PLUGINS=vertamedia-clickhouse-datasource\n",
                "services:\n  grafana:\n    image: grafana/grafana:12.1\n    environment:\n      - GF_PLUGINS_PREINSTALL_SYNC=vertamedia-clickhouse-datasource\n",
                source -> source.path("compose/kcg/docker-compose.yml")));
    }

    @Test
    void leavesUnofficialPluginsInstallEnvironmentForReview() {
        rewriteRun(yaml("env:\n  GF_PLUGINS_INSTALL: grafana-oncall-app,redis-datasource\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void leavesYamlPluginTargetCollisionUntouched() {
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS: grafana-clock-panel\n  GF_PLUGINS_PREINSTALL_SYNC: redis-datasource\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void leavesYamlSequencePluginTargetCollisionUntouched() {
        rewriteRun(yaml("environment:\n  - GF_INSTALL_PLUGINS=grafana-clock-panel\n  - GF_PLUGINS_PREINSTALL_SYNC=redis-datasource\n",
                source -> source.path("grafana-compose.yaml")));
    }

    @Test
    void migratesOfficialLegacyOwnerButLeavesUnofficialLookalike() {
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS: grafana-clock-panel\n  GF_PLUGINS_INSTALL: redis-datasource\n",
                "environment:\n  GF_PLUGINS_PREINSTALL_SYNC: grafana-clock-panel\n  GF_PLUGINS_INSTALL: redis-datasource\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void leavesDuplicateOfficialLegacyPluginOwnersUntouched() {
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS: grafana-clock-panel\n  GF_INSTALL_PLUGINS: redis-datasource\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void pluginForceTrueBlocksAutoButFalseDoesNot() {
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS: grafana-clock-panel\n  GF_INSTALL_PLUGINS_FORCE: true\n",
                source -> source.path("grafana-compose.yaml")));
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS: grafana-clock-panel\n  GF_INSTALL_PLUGINS_FORCE: false\n",
                "environment:\n  GF_PLUGINS_PREINSTALL_SYNC: grafana-clock-panel\n  GF_INSTALL_PLUGINS_FORCE: false\n",
                source -> source.path("grafana-compose.yaml")));
    }

    @Test
    void leavesComplexPluginUrlAndVersionSyntaxForReview() {
        rewriteRun(yaml("env:\n  GF_INSTALL_PLUGINS: https://example.test/plugin.zip;custom-plugin,grafana-clock-panel 1.0.1\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void removesOnlyDocumentedObsoleteFeatureTogglesInHelmIni() {
        rewriteRun(yaml("grafana.ini:\n  feature_toggles:\n    enable: dashboardPreviews,envelopeEncryption,publicDashboards\n",
                "grafana.ini:\n  feature_toggles:\n    enable: publicDashboards\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void preservesEmergencyDisableEnvelopeToggle() {
        rewriteRun(yaml("grafana.ini:\n  feature_toggles:\n    enable: disableEnvelopeEncryption\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void migratesIniFeatureToggles() {
        rewriteRun(text("[feature_toggles]\nenable = dashboardPreviews envelopeEncryption publicDashboards\n",
                "[feature_toggles]\nenable = publicDashboards\n", source -> source.path("conf/grafana.ini")));
    }

    @Test
    void preservesQuotedIniFeatureToggleValue() {
        rewriteRun(text("[feature_toggles]\nenable = 'dashboardPreviews publicDashboards'\n",
                "[feature_toggles]\nenable = 'publicDashboards'\n", source -> source.path("conf/grafana.ini")));
    }

    @Test
    void leavesInlineCommentedToggleValueForReview() {
        rewriteRun(text("[feature_toggles]\nenable = dashboardPreviews,publicDashboards # deployment note\n",
                source -> source.path("conf/grafana.ini")));
    }

    @Test
    void leavesInterpolatedToggleOwnerForReview() {
        rewriteRun(yaml("environment:\n  GF_FEATURE_TOGGLES_ENABLE: ${BASE_TOGGLES},dashboardPreviews\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void leavesDuplicateFeatureToggleOwnersUntouched() {
        rewriteRun(yaml("grafana.ini:\n  feature_toggles:\n    enable: dashboardPreviews,publicDashboards\nenvironment:\n  GF_FEATURE_TOGGLES_ENABLE: envelopeEncryption,publicDashboards\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void leavesUnofficialIniPluginInstallForReview() {
        rewriteRun(text("[plugins]\ninstall = grafana-clock-panel,redis-datasource\n",
                source -> source.path("conf/custom.ini")));
    }

    @Test
    void leavesIniPluginTargetCollisionUntouched() {
        rewriteRun(text("[plugins]\ninstall = grafana-clock-panel\npreinstall_sync = redis-datasource\n",
                source -> source.path("conf/custom.ini")));
    }

    @Test
    void migratesEnvFile() {
        rewriteRun(text("GF_INSTALL_PLUGINS=grafana-clock-panel\nGF_FEATURE_TOGGLES_ENABLE=dashboardPreviews,publicDashboards\n",
                "GF_PLUGINS_PREINSTALL_SYNC=grafana-clock-panel\nGF_FEATURE_TOGGLES_ENABLE=publicDashboards\n",
                source -> source.path("grafana/.env")));
    }

    @Test
    void migratesExportedAndQuotedEnvValuesWithoutChangingTheirValueStyle() {
        rewriteRun(text("export GF_INSTALL_PLUGINS=\"grafana-clock-panel\"\nexport GF_FEATURE_TOGGLES_ENABLE='dashboardPreviews publicDashboards'\n",
                "export GF_PLUGINS_PREINSTALL_SYNC=\"grafana-clock-panel\"\nexport GF_FEATURE_TOGGLES_ENABLE='publicDashboards'\n",
                source -> source.path("grafana/.env")));
    }

    @Test
    void exportedPluginForceTrueBlocksAuto() {
        rewriteRun(text("export GF_INSTALL_PLUGINS=\"grafana-clock-panel\"\nexport GF_INSTALL_PLUGINS_FORCE=true\n",
                source -> source.path("grafana/.env")));
    }

    @Test
    void migratesDotEnvProfileFile() {
        rewriteRun(text("GF_INSTALL_PLUGINS=grafana-clock-panel\n",
                "GF_PLUGINS_PREINSTALL_SYNC=grafana-clock-panel\n",
                source -> source.path("deploy/.env.production")));
    }

    @Test
    void leavesCommentsAndUnrelatedConfigUntouched() {
        rewriteRun(text("[feature_toggles]\n; enable = dashboardPreviews\n# GF_INSTALL_PLUGINS=grafana-clock-panel\nother = envelopeEncryption\n",
                source -> source.path("conf/grafana.ini")));
    }

    @Test
    void doesNotRenameDockerBuildArgument() {
        rewriteRun(text("ARG GF_INSTALL_PLUGINS=grafana-clock-panel\nFROM grafana/grafana:12.1.1\n",
                source -> source.path("Dockerfile")));
    }

    @Test
    void migratesDockerfileEnvironmentPluginAndFeatureToggles() {
        rewriteRun(text("FROM grafana/grafana:12.1.1\nENV GF_INSTALL_PLUGINS=\"grafana-clock-panel\"\nENV GF_FEATURE_TOGGLES_ENABLE='dashboardPreviews publicDashboards'\n",
                "FROM grafana/grafana:12.1.1\nENV GF_PLUGINS_PREINSTALL_SYNC=\"grafana-clock-panel\"\nENV GF_FEATURE_TOGGLES_ENABLE='publicDashboards'\n",
                source -> source.path("Dockerfile")));
    }

    @Test
    void migratesDockerfileLegacySpaceForm() {
        rewriteRun(text("ENV GF_INSTALL_PLUGINS grafana-clock-panel\n",
                "ENV GF_PLUGINS_PREINSTALL_SYNC grafana-clock-panel\n",
                source -> source.path("deploy/grafana.Dockerfile")));
    }

    @Test
    void dockerfileForceTrueBlocksAutoButFalseDoesNot() {
        rewriteRun(text("ENV GF_INSTALL_PLUGINS=grafana-clock-panel\nENV GF_INSTALL_PLUGINS_FORCE=true\n",
                source -> source.path("Dockerfile")));
        rewriteRun(text("ENV GF_INSTALL_PLUGINS=grafana-clock-panel\nENV GF_INSTALL_PLUGINS_FORCE=false\n",
                "ENV GF_PLUGINS_PREINSTALL_SYNC=grafana-clock-panel\nENV GF_INSTALL_PLUGINS_FORCE=false\n",
                source -> source.path("Dockerfile.grafana")));
    }

    @Test
    void leavesMultiOwnerDockerEnvironmentInstructionForReview() {
        rewriteRun(text("ENV GF_INSTALL_PLUGINS=grafana-clock-panel OTHER=value\n",
                source -> source.path("Dockerfile")));
    }

    @Test
    void excludesGeneratedConfigurationTrees() {
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS: grafana-clock-panel\n",
                source -> source.path("generated-client/grafana-values.yaml")));
        rewriteRun(text("GF_INSTALL_PLUGINS=grafana-clock-panel\n",
                source -> source.path(".cache/grafana.env")));
    }

    @Test
    void textMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                text("GF_INSTALL_PLUGINS=grafana-clock-panel\n",
                        "GF_PLUGINS_PREINSTALL_SYNC=grafana-clock-panel\n",
                        source -> source.path("grafana/.env")));
    }

    @Test
    void isIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                yaml("grafana.ini:\n  feature_toggles:\n    enable: dashboardPreviews,publicDashboards\n",
                        "grafana.ini:\n  feature_toggles:\n    enable: publicDashboards\n",
                        source -> source.path("grafana-values.yaml")));
    }
}
