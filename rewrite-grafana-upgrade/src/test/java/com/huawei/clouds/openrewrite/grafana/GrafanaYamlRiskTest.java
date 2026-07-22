package com.huawei.clouds.openrewrite.grafana;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.yaml.Assertions.yaml;

class GrafanaYamlRiskTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindGrafana12YamlRisks());
    }

    @Test
    void marksLegacyAlertingOptOut() {
        assertMarker("environment:\n  GF_UNIFIED_ALERTING_ENABLED: 'false'\n", "Grafana 10.4");
    }

    @Test
    void marksAngularSupport() {
        assertMarker("environment:\n  GF_PLUGINS_ANGULAR_SUPPORT_ENABLED: 'true'\n", "AngularJS");
    }

    @Test
    void marksComplexPluginInstall() {
        assertMarker("environment:\n  GF_INSTALL_PLUGINS: https://example.test/x.zip;custom-plugin\n", "cannot be migrated mechanically");
    }

    @Test
    void marksUnofficialPluginInstallAndForceOwners() {
        rewriteRun(yaml("grafana.ini:\n  plugins:\n    install: grafana-clock-panel\nenvironment:\n  GF_INSTALL_PLUGINS_FORCE: true\n",
                source -> source.path("grafana-values.yaml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(occurrences(printed, "cannot be migrated mechanically") == 2, printed);
                })));
    }

    @Test
    void falsePluginForceIsNoOp() {
        rewriteRun(yaml("environment:\n  GF_INSTALL_PLUGINS_FORCE: false\n",
                source -> source.path("grafana-values.yaml")));
    }

    @Test
    void marksRemovedEditorPrivilege() {
        assertMarker("grafana.ini:\n  users:\n    editors_can_admin: true\n", "explicit RBAC");
    }

    @Test
    void marksOAuthIdentityRisk() {
        assertMarker("environment:\n  GF_AUTH_OAUTH_ALLOW_INSECURE_EMAIL_LOOKUP: 'true'\n", "identity conflicts");
    }

    @Test
    void marksAnonymousEnterpriseRisk() {
        assertMarker("environment:\n  GF_AUTH_ANONYMOUS_ENABLED: 'true'\n", "billed as active users");
    }

    @Test
    void marksInvalidDatasourceUidAtExactEntry() {
        assertMarker("datasources:\n  - name: Elastic\n    type: elasticsearch\n    uid: invalid.uid!\n", "1-40 character");
    }

    @Test
    void validDatasourceUidIsNoOp() {
        rewriteRun(yaml("datasources:\n  - name: Prometheus\n    type: prometheus\n    uid: prometheus-main_1\n",
                source -> source.path("grafana/provisioning/datasources/main.yaml")));
    }

    @Test
    void genericDatasourceYamlIsNotAssumedToBeGrafana() {
        rewriteRun(yaml("datasources:\n  - type: elasticsearch\n    uid: invalid.uid!\n    jsonData:\n      database: 7.9.3\n",
                source -> source.path("config/application.yaml")));
    }

    @Test
    void officialProvisioningSchemaIsRecognizedWithoutGrafanaPath() {
        rewriteRun(yaml("apiVersion: 1\ndatasources:\n  - type: elasticsearch\n    uid: invalid.uid!\n",
                source -> source.path("config/datasources.yaml").after(actual -> actual).afterRecipe(document ->
                        assertTrue(document.printAll().contains("1-40 character"), document.printAll()))));
    }

    @Test
    void doesNotTreatPluginUidAsDatasourceUid() {
        rewriteRun(yaml("plugins:\n  - type: panel\n    uid: invalid.plugin.uid!\n",
                source -> source.path("grafana/plugin.yaml")));
    }

    @Test
    void datasourceUidMarkerIsAttachedToUidEntry() {
        rewriteRun(yaml("datasources:\n  - name: Elastic\n    type: elasticsearch\n    uid: invalid.uid!\n",
                source -> source.path("grafana/provisioning/datasources/main.yaml")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            int message = printed.indexOf("1-40 character");
                            int uid = printed.indexOf("uid: invalid.uid!");
                            assertTrue(message >= 0 && message < uid && uid - message < 500, printed);
                        })));
    }

    @Test
    void marksUnsupportedElasticsearchVersion() {
        assertMarker("datasources:\n  - name: Elastic\n    type: elasticsearch\n    jsonData:\n      database: 7.9.3\n", "before 7.10");
    }

    @Test
    void supportedElasticsearchVersionIsNoOp() {
        rewriteRun(yaml("datasources:\n  - name: Elastic\n    type: elasticsearch\n    jsonData:\n      database: 7.10.0\n",
                source -> source.path("grafana/provisioning/datasources/elastic.yaml")));
    }

    @Test
    void marksUnlistedImageOwner() {
        assertMarker("services:\n  grafana:\n    image: grafana/grafana:${GRAFANA_VERSION}\n", "ownership is dynamic");
    }

    @Test
    void marksEachAmbiguousHelmTagOwnerInsteadOfChoosingOne() {
        rewriteRun(yaml("image:\n  repository: grafana/grafana\n  tag: 7.4.5\n  tag: 12.1.1\n",
                source -> source.path("grafana-values.yaml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(occurrences(printed, "ownership is dynamic") == 2, printed);
                })));
    }

    @Test
    void marksSecretKeyOwnership() {
        assertMarker("grafana.ini:\n  security:\n    secret_key: ${GRAFANA_SECRET_KEY}\n", "KMS providers");
    }

    @Test
    void marksSubfolderNotificationMatcher() {
        assertMarker("policies:\n  - object_matchers:\n      grafana_folder: Team/Production\n", "subfolders");
    }

    @Test
    void nearMatchFolderKeyIsNoOp() {
        rewriteRun(yaml("policies:\n  - grafana_folder_backup: Team/Production\n",
                source -> source.path("grafana/provisioning/policies.yaml")));
    }

    @Test
    void excludesGeneratedTrees() {
        rewriteRun(yaml("environment:\n  GF_AUTH_ANONYMOUS_ENABLED: 'true'\n",
                source -> source.path("generated-client/grafana-values.yaml")));
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                yaml("environment:\n  GF_AUTH_ANONYMOUS_ENABLED: 'true'\n",
                        source -> source.path("grafana-values.yaml").after(actual -> actual)));
    }

    private void assertMarker(String before, String expected) {
        rewriteRun(yaml(before, source -> source.path("grafana/provisioning/config.yaml")
                .after(actual -> actual).afterRecipe(document ->
                        assertTrue(document.printAll().contains(expected), document.printAll()))));
    }

    private static int occurrences(String value, String expected) {
        int count = 0;
        for (int index = value.indexOf(expected); index >= 0; index = value.indexOf(expected, index + expected.length())) {
            count++;
        }
        return count;
    }
}
