package com.huawei.clouds.openrewrite.grafana;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class GrafanaJsonAndTextRiskTest implements RewriteTest {
    @Test
    void marksLegacyDashboardAlert() {
        jsonRisk("{\"panels\":[{\"type\":\"timeseries\",\"alert\":{\"name\":\"CPU\"}}]}", "migrate it in Grafana 10.4");
    }

    @Test
    void nonPanelAlertValuesAndObjectsAreNotLegacyPanelAlerts() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"alert\":false,\"alertState\":\"alert\"}", source -> source.path("grafana/dashboard.json")),
                json("{\"alert\":\"disabled\"}", source -> source.path("grafana/other-dashboard.json")),
                json("{\"alert\":{\"name\":\"unrelated root object\"}}",
                        source -> source.path("grafana/settings.json")));
    }

    @Test
    void marksAngularPanel() {
        jsonRisk("{\"panels\":[{\"type\":\"graph\"}]}", "Angular-era panel");
    }

    @Test
    void marksAngularPluginMetadata() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"type\":\"panel\",\"name\":\"Private\",\"angular\":true}",
                        source -> source.path("src/plugin.json").after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("Angular plugin"), document.printAll()))));
    }

    @Test
    void angularOptionOutsidePluginMetadataIsNoOp() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"settings\":{\"angular\":true}}", source -> source.path("grafana/dashboard.json")));
    }

    @Test
    void modernTimeSeriesPanelIsNoOp() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"panels\":[{\"type\":\"timeseries\"}]}", source -> source.path("grafana/dashboard.json")));
    }

    @Test
    void graphTypeOutsidePanelsIsNotAnAngularPanel() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"model\":{\"type\":\"graph\"}}", source -> source.path("grafana/settings.json")));
    }

    @Test
    void genericDashboardJsonIsNotAssumedToBeGrafana() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"panels\":[{\"type\":\"graph\",\"alert\":{\"name\":\"CPU\"}}]}",
                        source -> source.path("dashboards/fixture.json")));
    }

    @Test
    void exportedGrafanaSchemaIsRecognizedWithoutGrafanaPath() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"schemaVersion\":36,\"panels\":[{\"type\":\"graph\"}]}",
                        source -> source.path("dashboards/export.json").after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("Angular-era panel"), document.printAll()))));
    }

    @Test
    void marksCloudWatchAlias() {
        jsonRisk("{\"panels\":[{\"datasource\":{\"type\":\"cloudwatch\",\"uid\":\"cw\"},\"targets\":[{\"alias\":\"$tag_Name\"}]}]}", "dynamic labels");
    }

    @Test
    void marksLokiLabelsTransformation() {
        jsonRisk("{\"panels\":[{\"datasource\":{\"type\":\"loki\",\"uid\":\"loki\"},\"transformations\":[{\"id\":\"labelsToFields\"}]}]}", "single data frame");
    }

    @Test
    void doesNotMarkAliasOrLabelsTransformationForOtherDatasources() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()), json("""
                        {
                          "panels": [
                            {"datasource":{"type":"cloudwatch","uid":"cw"},"targets":[]},
                            {"datasource":{"type":"prometheus","uid":"prom"},"targets":[{"alias":"cpu"}],"transformations":[{"id":"labelsToFields"}]}
                          ]
                        }
                        """, source -> source.path("grafana/dashboard.json")));
    }

    @Test
    void aliasAndTransformationIdRequireTheirExactOwnerArrays() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()), json("""
                        {
                          "panels": [
                            {"datasource":{"type":"cloudwatch","uid":"cw"},"options":{"alias":"not-a-target"}},
                            {"datasource":{"type":"loki","uid":"loki"},"options":{"id":"labelsToFields"}}
                          ]
                        }
                        """, source -> source.path("grafana/dashboard.json")));
    }

    @Test
    void marksInvalidDatasourceUidReference() {
        jsonRisk("{\"panels\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"bad.uid!\"}}]}", "1-40 character");
    }

    @Test
    void dashboardAndPluginRootUidsAreNotDatasourceUids() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"title\":\"Dashboard\",\"uid\":\"dashboard.uid!\"}",
                        source -> source.path("grafana/dashboards/main.json")),
                json("{\"type\":\"panel\",\"uid\":\"plugin.uid!\"}",
                        source -> source.path("grafana/plugin.json")));
    }

    @Test
    void datasourceUidMarkerIsAttachedToUidMember() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"bad.uid!\"}}",
                        source -> source.path("grafana/dashboard.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    int message = printed.indexOf("1-40 character");
                                    int uid = printed.indexOf("\"uid\"");
                                    assertTrue(message >= 0 && message < uid && uid - message < 500, printed);
                                })));
    }

    @Test
    void marksIniLegacyAlertAtExactLine() {
        textRisk("[alerting]\nenabled = true\n", "Grafana 10.4");
    }

    @Test
    void marksIniAngularAndEditorSettings() {
        textRisk("[plugins]\nangular_support_enabled = true\n[users]\neditors_can_admin = true\n", "AngularJS", "explicit RBAC");
    }

    @Test
    void marksOAuthAndAnonymousSettings() {
        textRisk("[auth]\noauth_allow_insecure_email_lookup = true\n[auth.anonymous]\nenabled = true\n", "identity conflicts", "billed as active users");
    }

    @Test
    void marksSecurityKeyAndDatabase() {
        textRisk("[security]\nsecret_key = ${SECRET}\n[database]\ntype = mysql\nhost = db:3306\n", "tested backup", "HA rolling-upgrade");
    }

    @Test
    void marksApiKeyAutomation() {
        textRisk("curl -X POST https://grafana.example/api/auth/keys\n", "service-account API");
    }

    @Test
    void marksGrafanaCliPluginInstall() {
        textRisk("grafana cli plugins install private-panel\n", "compatibility/signatures");
    }

    @Test
    void marksCliWithOptionsAtTheExactCommandLine() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text("echo before\ngrafana-cli --pluginsDir /var/lib/grafana/plugins plugins install private-panel\necho after\n",
                        source -> source.path("scripts/grafana-plugins.sh").after(actual -> actual).afterRecipe(file -> {
                            String printed = file.printAll();
                            int message = printed.indexOf("compatibility/signatures");
                            int command = printed.indexOf("grafana-cli --pluginsDir");
                            assertTrue(message >= 0 && message < command && command - message < 500, printed);
                        })));
    }

    @Test
    void genericApiPathAndEmptyPluginSettingAreNoOp() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text("curl -X POST https://example.test/api/auth/keys\n",
                        source -> source.path("scripts/create-token.sh")),
                text("GF_INSTALL_PLUGINS=\n", source -> source.path("grafana/config.env")));
    }

    @Test
    void marksUnsupportedIniAndEnvironmentPluginOwners() {
        textRisk("[plugins]\ninstall = grafana-clock-panel\nGF_PLUGINS_INSTALL=redis-datasource\nGF_INSTALL_PLUGINS_FORCE=true\n",
                "cannot be migrated mechanically");
    }

    @Test
    void marksDynamicDockerImageAtInstruction() {
        textRisk("FROM grafana/grafana:${GRAFANA_VERSION}\n", "ownership is dynamic");
    }

    @Test
    void marksStandaloneDockerEnvironmentPluginOwnerAtExactLine() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text("FROM grafana/grafana:12.1.1\nENV GF_INSTALL_PLUGINS=grafana-clock-panel OTHER=value\nRUN echo ready\n",
                        source -> source.path("Dockerfile").after(actual -> actual).afterRecipe(file -> {
                            String printed = file.printAll();
                            int message = printed.indexOf("cannot be migrated mechanically");
                            int owner = printed.indexOf("ENV GF_INSTALL_PLUGINS");
                            assertTrue(message >= 0 && message < owner && owner - message < 500, printed);
                        })));
    }

    @Test
    void marksDockerEnvironmentFeatureToggleButNotFalseForce() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text("ENV GF_FEATURE_TOGGLES_ENABLE='dashboardPreviews publicDashboards'\n" +
                     "ENV GF_INSTALL_PLUGINS_FORCE=false\n",
                        source -> source.path("Dockerfile").after(actual -> actual).afterRecipe(file -> {
                            String printed = file.printAll();
                            assertTrue(printed.contains("removed dashboardPreviews"), printed);
                            assertTrue(printed.indexOf("~~>") == printed.lastIndexOf("~~>"), printed);
                        })));
    }

    @Test
    void commentsAndModernSettingsAreNoOp() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text("[unified_alerting]\nenabled = true\n; angular_support_enabled = true\n",
                        source -> source.path("grafana/grafana.ini")));
    }

    @Test
    void riskRecipesExcludeGeneratedAndCacheTrees() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json("{\"panels\":[{\"type\":\"graph\"}]}",
                        source -> source.path("generated-dashboard/grafana/dashboard.json")));
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text("GF_AUTH_ANONYMOUS_ENABLED=true\n", source -> source.path(".cache/grafana/config.env")));
    }

    @Test
    void jsonAndTextMarkerRecipesAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"panels\":[{\"type\":\"graph\"}]}",
                        source -> source.path("grafana/dashboard.json").after(actual -> actual)));
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                text("GF_AUTH_ANONYMOUS_ENABLED=true\n",
                        source -> source.path("grafana/config.env").after(actual -> actual)));
    }

    private void jsonRisk(String before, String... expected) {
        rewriteRun(spec -> spec.recipe(new FindGrafana12JsonRisks()),
                json(before, source -> source.path("grafana/dashboards/fixture.json").after(actual -> actual)
                        .afterRecipe(document -> {
                            String printed = document.printAll();
                            for (String value : expected) assertTrue(printed.contains(value), printed);
                        })));
    }

    private void textRisk(String before, String... expected) {
        rewriteRun(spec -> spec.recipe(new FindGrafana12TextRisks()),
                text(before, source -> source.path("grafana/config.fixture").after(actual -> actual)
                        .afterRecipe(file -> {
                            String printed = file.printAll();
                            for (String value : expected) assertTrue(printed.contains(value), printed);
                        })));
    }
}
