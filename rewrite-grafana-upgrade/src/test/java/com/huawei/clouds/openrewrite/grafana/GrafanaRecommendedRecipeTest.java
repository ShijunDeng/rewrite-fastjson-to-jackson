package com.huawei.clouds.openrewrite.grafana;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

/** Integration fixtures from fixed upstream revisions and Grafana's target-version configuration. */
class GrafanaRecommendedRecipeTest implements RewriteTest {
    private static final String GOFLOW_COMMIT = "6dee964c38ee5f6b04a38681d069427c28ee5cb3";
    private static final String OPENBMP_COMMIT = "3f38af5312ae4b99cc3b3dcc6f54f6909439579d";
    private static final String STRIMZI_COMMIT = "205ce5aa3143c5fd76cbd53da5aa966ef3d069d7";
    private static final String TEMPORAL_COMMIT = "ca1106b647c34323876bd6f221f4310271096dd8";
    private static final String DOMOTIK_COMMIT = "b4835a2b3874005103f5eb727ba51d0bad35b84e";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(GrafanaImageUpgradeTest.environment().activateRecipes(GrafanaImageUpgradeTest.MIGRATE));
    }

    @Test
    void upgradesRealGoflow2MultiStageDockerfile() {
        // netsampler/goflow2@6dee964:compose/kcg/grafana/Dockerfile
        assertTrue(GOFLOW_COMMIT.startsWith("6dee964"));
        rewriteRun(text("""
                        FROM ubuntu AS builder

                        RUN apt-get update && apt-get install -y git
                        RUN git clone https://github.com/Vertamedia/clickhouse-grafana.git

                        FROM grafana/grafana:9.1.7

                        COPY --from=builder /clickhouse-grafana /var/lib/grafana/plugins
                        """,
                """
                        FROM ubuntu AS builder

                        RUN apt-get update && apt-get install -y git
                        RUN git clone https://github.com/Vertamedia/clickhouse-grafana.git

                        FROM grafana/grafana:12.1.1

                        COPY --from=builder /clickhouse-grafana /var/lib/grafana/plugins
                        """, source -> source.path("compose/kcg/grafana/Dockerfile")));
    }

    @Test
    void upgradesRealStrimziKubernetesImageFromXlsx74Line() {
        // strimzi/strimzi-kafka-operator@205ce5a:examples/metrics/grafana-install/grafana.yaml
        assertTrue(STRIMZI_COMMIT.startsWith("205ce5a"));
        rewriteRun(yaml("""
                        apiVersion: apps/v1
                        kind: Deployment
                        metadata:
                          name: grafana
                        spec:
                          template:
                            spec:
                              containers:
                                - name: grafana
                                  image: grafana/grafana:7.4.5
                        """,
                """
                        apiVersion: apps/v1
                        kind: Deployment
                        metadata:
                          name: grafana
                        spec:
                          template:
                            spec:
                              containers:
                                - name: grafana
                                  image: grafana/grafana:12.1.1
                        """, source -> source.path("examples/metrics/grafana-install/grafana.yaml")));
    }

    @Test
    void upgradesRealTemporalDockerfileFromXlsx75Line() {
        // temporalio/samples-server@ca1106b:compose/deployment/grafana/Dockerfile
        assertTrue(TEMPORAL_COMMIT.startsWith("ca1106b"));
        rewriteRun(text("FROM grafana/grafana:7.5.16\n",
                "FROM grafana/grafana:12.1.1\n",
                source -> source.path("compose/deployment/grafana/Dockerfile")));
    }

    @Test
    void upgradesRealDomotikDockerfileFromXlsx85Line() {
        // sylvek/domotik@b4835a2:grafana/Dockerfile
        assertTrue(DOMOTIK_COMMIT.startsWith("b4835a2"));
        rewriteRun(text("FROM grafana/grafana:8.5.14\n",
                "FROM grafana/grafana:12.1.1\n",
                source -> source.path("grafana/Dockerfile")));
    }

    @Test
    void migratesRealGoflow2ComposePluginEnvironmentAndMarksImageOwner() {
        // netsampler/goflow2@6dee964:compose/kcg/docker-compose.yml
        rewriteRun(yaml("""
                        services:
                          grafana:
                            image: grafana/grafana:12.1
                            environment:
                              - GF_INSTALL_PLUGINS=vertamedia-clickhouse-datasource
                            ports:
                              - 3000:3000
                            restart: always
                        """, source -> source.path("compose/kcg/docker-compose.yml")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("GF_PLUGINS_PREINSTALL_SYNC=vertamedia-clickhouse-datasource"), printed);
                            assertTrue(printed.contains("ownership is dynamic"), printed);
                        })));
    }

    @Test
    void upgradesAndMarksSecurityRiskFromRealOpenBmpCompose() {
        // OpenBMP/obmp-docker@3f38af5:docker-compose.yml
        assertTrue(OPENBMP_COMMIT.startsWith("3f38af5"));
        rewriteRun(yaml("""
                        services:
                          grafana:
                            restart: unless-stopped
                            container_name: obmp-grafana
                            image: grafana/grafana:9.1.7
                            ports:
                              - "3000:3000"
                            environment:
                              - GF_SECURITY_ADMIN_PASSWORD=openbmp
                              - GF_AUTH_ANONYMOUS_ENABLED=true
                        """, source -> source.path("docker-compose.yml")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("grafana/grafana:12.1.1"), printed);
                            assertTrue(printed.contains("billed as active users"), printed);
                        })));
    }

    @Test
    void appliesAutomaticAndReviewStepsToOfficialProvisioningShape() {
        rewriteRun(yaml("""
                        apiVersion: 1
                        datasources:
                          - name: Elastic
                            type: elasticsearch
                            uid: elastic.prod!
                            access: direct
                            url: http://elastic:9200
                            jsonData:
                              database: 7.9.3
                        """, source -> source.path("conf/provisioning/datasources/elasticsearch.yaml")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("access: proxy"), printed);
                            assertTrue(printed.contains("1-40 character"), printed);
                            assertTrue(printed.contains("before 7.10"), printed);
                        })));
    }

    @Test
    void removesOfficiallyObsoleteTogglesButMarksOperatorOwnedSettings() {
        rewriteRun(yaml("""
                        grafana.ini:
                          feature_toggles:
                            enable: dashboardPreviews,envelopeEncryption,publicDashboards
                          plugins:
                            angular_support_enabled: true
                          users:
                            editors_can_admin: true
                        """, source -> source.path("deploy/grafana-values.yaml")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("enable: publicDashboards"), printed);
                            assertTrue(printed.contains("AngularJS"), printed);
                            assertTrue(printed.contains("explicit RBAC"), printed);
                        })));
    }

    @Test
    void preservesAndMarksInterpolatedObsoleteToggleOwner() {
        rewriteRun(yaml("environment:\n  GF_FEATURE_TOGGLES_ENABLE: ${BASE_TOGGLES},dashboardPreviews\n",
                source -> source.path("grafana-values.yaml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("${BASE_TOGGLES},dashboardPreviews"), printed);
                    assertTrue(printed.contains("cannot be rewritten safely"), printed);
                })));
    }

    @Test
    void marksLegacyDashboardRisksThroughRecommendedRecipe() {
        rewriteRun(org.openrewrite.json.Assertions.json("""
                        {
                          "panels": [
                            {
                              "type": "graph",
                              "alert": {"name": "CPU"},
                              "datasource": {"type": "prometheus", "uid": "bad.uid!"}
                            }
                          ]
                        }
                        """, source -> source.path("grafana/dashboards/cpu.json")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("Grafana 10.4"), printed);
                            assertTrue(printed.contains("Angular-era panel"), printed);
                            assertTrue(printed.contains("1-40 character"), printed);
                        })));
    }

    @Test
    void recommendedRecipeIsIdempotentAfterAutomaticMigrationAndMarks() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), yaml("""
                        services:
                          grafana:
                            image: grafana/grafana:7.4.5
                            environment:
                              GF_INSTALL_PLUGINS: grafana-clock-panel
                              GF_AUTH_ANONYMOUS_ENABLED: 'true'
                        """, source -> source.path("grafana-compose.yaml")
                        .after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("grafana/grafana:12.1.1"), printed);
                            assertTrue(printed.contains("GF_PLUGINS_PREINSTALL_SYNC"), printed);
                            assertTrue(printed.contains("billed as active users"), printed);
                        })));
    }

    @Test
    void targetConfigurationIsNoOpAndRecommendedRecipeIsDiscoverable() {
        rewriteRun(yaml("""
                services:
                  grafana:
                    image: grafana/grafana:12.1.1
                    environment:
                      GF_PLUGINS_PREINSTALL_SYNC: grafana-clock-panel
                      GF_UNIFIED_ALERTING_ENABLED: 'true'
                datasources:
                  - name: Prometheus
                    type: prometheus
                    uid: prometheus-main_1
                    access: proxy
                """, source -> source.path("grafana/modern.yaml")));

        Recipe recipe = GrafanaImageUpgradeTest.environment().activateRecipes(GrafanaImageUpgradeTest.MIGRATE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(GrafanaImageUpgradeTest.environment().listRecipes().stream()
                .anyMatch(candidate -> GrafanaImageUpgradeTest.MIGRATE.equals(candidate.getName())));
    }
}
