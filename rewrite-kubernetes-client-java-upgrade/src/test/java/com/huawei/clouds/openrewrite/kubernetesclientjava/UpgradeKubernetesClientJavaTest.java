package com.huawei.clouds.openrewrite.kubernetesclientjava;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.toml.Assertions.toml;

class UpgradeKubernetesClientJavaTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.UpgradeKubernetesClientJavaDependencyTo25_0_0_Legacy";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo25_0_0_Legacy";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @Test
    void upgradesSpreadsheetVersion11_0_2InMaven() {
        rewriteRun(pomXml(directPom("11.0.2"), directPom("25.0.0-legacy")));
    }

    @Test
    void upgradesSpreadsheetVersion16_0_2InMaven() {
        rewriteRun(pomXml(directPom("16.0.2"), directPom("25.0.0-legacy")));
    }

    @Test
    void upgradesSpreadsheetVersion16_0_3InMaven() {
        rewriteRun(pomXml(directPom("16.0.3"), directPom("25.0.0-legacy")));
    }

    @Test
    void upgradesSpreadsheetVersion17_0_2InMaven() {
        rewriteRun(pomXml(directPom("17.0.2"), directPom("25.0.0-legacy")));
    }

    @Test
    void upgradesSpreadsheetVersion18_0_1InMaven() {
        rewriteRun(pomXml(directPom("18.0.1"), directPom("25.0.0-legacy")));
    }

    @Test
    void upgradesSpringCloudKubernetesStyleManagedProperty() {
        // Reduced from spring-cloud/spring-cloud-kubernetes v2.0.3 at 305c1658:
        // https://github.com/spring-cloud/spring-cloud-kubernetes/blob/305c16585471514b528de61b0e3f7dc202dc9ae5/spring-cloud-kubernetes-dependencies/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-kubernetes-dependencies</artifactId><version>2.0.3</version>
                  <properties><kubernetes-java-client.version>11.0.2</kubernetes-java-client.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>${kubernetes-java-client.version}</version></dependency>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-extended</artifactId><version>${kubernetes-java-client.version}</version></dependency>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-spring-integration</artifactId><version>${kubernetes-java-client.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-kubernetes-dependencies</artifactId><version>2.0.3</version>
                  <properties><kubernetes-java-client.version>25.0.0-legacy</kubernetes-java-client.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>${kubernetes-java-client.version}</version></dependency>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-extended</artifactId><version>${kubernetes-java-client.version}</version></dependency>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-spring-integration</artifactId><version>${kubernetes-java-client.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesApacheShenyuStylePropertyBackedDirectDependency() {
        // Reduced from apache/shenyu v2.7.0 at 2728a79c:
        // https://github.com/apache/shenyu/blob/2728a79c8a283bd4076eed7ebb93f0e9e0442aa2/shenyu-admin/pom.xml
        rewriteRun(pomXml(
                propertyPom("17.0.2", "k8s-client.version"),
                propertyPom("25.0.0-legacy", "k8s-client.version")
        ));
    }

    @Test
    void upgradesApacheSubmarineStyleDependencyManagement() {
        // Reduced from apache/submarine rel/release-0.8.0 at 389c8fd9:
        // https://github.com/apache/submarine/blob/389c8fd919411f1edb2aec05e6ed2bca83dcf15d/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.submarine</groupId><artifactId>submarine</artifactId><version>0.8.0</version>
                  <properties><k8s.client-java.version>17.0.2</k8s.client-java.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>${k8s.client-java.version}</version></dependency>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-api-fluent</artifactId><version>${k8s.client-java.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.submarine</groupId><artifactId>submarine</artifactId><version>0.8.0</version>
                  <properties><k8s.client-java.version>25.0.0-legacy</k8s.client-java.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>${k8s.client-java.version}</version></dependency>
                    <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-api-fluent</artifactId><version>${k8s.client-java.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesGroovyGradleStringNotationForEverySpreadsheetVersion() {
        rewriteRun(
                gradleDependency("11.0.2", "v11.gradle"),
                gradleDependency("16.0.2", "v16-0-2.gradle"),
                gradleDependency("16.0.3", "v16-0-3.gradle"),
                gradleDependency("17.0.2", "v17.gradle"),
                gradleDependency("18.0.1", "v18.gradle")
        );
    }

    @Test
    void upgradesGroovyGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation group: 'io.kubernetes', name: 'client-java', version: '16.0.3' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation group: 'io.kubernetes', name: 'client-java', version: '25.0.0-legacy' }
                """
        ));
    }

    @Test
    void leavesGroovyGradleVersionPropertyWithoutResolvedRequestedVersionUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                ext { kubernetesClientVersion = '18.0.1' }
                dependencies { implementation "io.kubernetes:client-java:${kubernetesClientVersion}" }
                """,
                source -> source.path("build.gradle")
        ));
    }

    @Test
    void leavesButterCamVersionCatalogForManualReview() {
        // Reduced from ButterCam/sisyphus 1.7.14 at f8d72b42. The generic dependency recipe
        // cannot safely infer which [versions] alias a version.ref row owns.
        // https://github.com/ButterCam/sisyphus/blob/f8d72b422f21ed718c778bc94dbe192e4892a405/gradle/libs.versions.toml
        rewriteRun(toml(
                """
                [versions]
                kubernetes = "16.0.3"

                [libraries]
                kubernetes = { module = "io.kubernetes:client-java", version.ref = "kubernetes" }
                """,
                source -> source.path("gradle/libs.versions.toml")
        ));
    }

    @Test
    void leavesKotlinDslWithoutGradleSemanticModelUntouched() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("io.kubernetes:client-java:18.0.1") }
                """
        ));
    }

    @Test
    void upgradesLocallyManagedVersionForVersionlessDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>16.0.2</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>25.0.0-legacy</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesUnmanagedVersionlessDependencyUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'io.kubernetes:client-java' }
                """
        ));
    }

    @Test
    void leavesTargetVersionUntouched() {
        rewriteRun(pomXml(directPom("25.0.0-legacy")));
    }

    @Test
    void leavesModernNonLegacyTargetUntouched() {
        rewriteRun(pomXml(directPom("25.0.0")));
    }

    @Test
    void leavesLaterModernMajorUntouched() {
        rewriteRun(pomXml(directPom("26.0.0")));
    }

    @Test
    void leavesUnlistedAdjacentMavenVersionsUntouched() {
        rewriteRun(
                pomXml(directPom("11.0.3"), source -> source.path("v11-0-3-pom.xml")),
                pomXml(directPom("16.0.1"), source -> source.path("v16-0-1-pom.xml")),
                pomXml(directPom("17.0.1"), source -> source.path("v17-0-1-pom.xml")),
                pomXml(directPom("19.0.1"), source -> source.path("v19-0-1-pom.xml"))
        );
    }

    @Test
    void leavesUnlistedGradleVersionUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'io.kubernetes:client-java:18.0.0' }
                """
        ));
    }

    @Test
    void leavesPropertyWithoutClientJavaReferenceUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property-only</artifactId><version>1</version>
                  <properties><kubernetes-client.version>17.0.2</kubernetes-client.version></properties>
                  <dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>6.13.5</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesSameArtifactFromAnotherGroupUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'com.example:client-java:18.0.1' }
                """
        ));
    }

    @Test
    void leavesCompanionArtifactsWithIndependentVersionsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>companions</artifactId><version>1</version><dependencies>
                  <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-api</artifactId><version>18.0.1</version></dependency>
                  <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-api-fluent</artifactId><version>18.0.1</version></dependency>
                  <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-extended</artifactId><version>18.0.1</version></dependency>
                  <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-proto</artifactId><version>18.0.1</version></dependency>
                  <dependency><groupId>io.kubernetes</groupId><artifactId>client-java-spring-integration</artifactId><version>18.0.1</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void preservesScopeOptionalClassifierAndExclusions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>16.0.3</version><classifier>tests</classifier><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>25.0.0-legacy</version><classifier>tests</classifier><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void migrationRecipePreservesApacheShenyuStyleLegacyApiSource() {
        // Reduced from apache/shenyu v2.7.0 KubernetesScaler.java. The target is the legacy
        // generator on purpose; changing positional generated calls without cluster context is unsafe.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .typeValidationOptions(TypeValidation.none()),
                pomXml(directPom("17.0.2"), directPom("25.0.0-legacy")),
                java(
                        """
                        package org.apache.shenyu.admin.scale.scaler;

                        import io.kubernetes.client.openapi.ApiException;
                        import io.kubernetes.client.openapi.apis.AppsV1Api;
                        import io.kubernetes.client.openapi.models.V1Scale;

                        class KubernetesScaler {
                            private final AppsV1Api api;
                            KubernetesScaler(AppsV1Api api) { this.api = api; }

                            V1Scale read(String name, String namespace) throws ApiException {
                                return api.readNamespacedDeploymentScale(name, namespace, null);
                            }

                            void replace(String name, String namespace, V1Scale scale) throws ApiException {
                                api.replaceNamespacedDeploymentScale(name, namespace, scale, null, null, null, null);
                            }
                        }
                        """,
                        source -> source.path("src/main/java/org/apache/shenyu/admin/scale/scaler/KubernetesScaler.java")
                )
        );
    }

    @Test
    void migrationRecipePreservesWatchCallPatchAuthAndSerializationHotspots() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .typeValidationOptions(TypeValidation.none()),
                pomXml(directPom("18.0.1"), directPom("25.0.0-legacy")),
                java(
                        """
                        package example;

                        import com.google.gson.reflect.TypeToken;
                        import io.kubernetes.client.custom.V1Patch;
                        import io.kubernetes.client.openapi.ApiClient;
                        import io.kubernetes.client.openapi.ApiException;
                        import io.kubernetes.client.openapi.apis.CoreV1Api;
                        import io.kubernetes.client.openapi.models.V1Pod;
                        import io.kubernetes.client.openapi.models.V1PodList;
                        import io.kubernetes.client.util.Config;
                        import io.kubernetes.client.util.Watch;
                        import okhttp3.Call;

                        class KubernetesHotspots {
                            ApiClient authenticate() throws Exception { return Config.defaultClient(); }
                            V1PodList list(CoreV1Api api) throws ApiException {
                                return api.listNamespacedPod("default", null, null, null, null, null, null, null, null, null, null);
                            }
                            Call listCall(CoreV1Api api) throws ApiException {
                                return api.listNamespacedPodCall("default", null, null, null, null, null, null, null, null, true, null, null);
                            }
                            Watch<V1Pod> watch(ApiClient client, Call call) throws Exception {
                                return Watch.createWatch(client, call, new TypeToken<Watch.Response<V1Pod>>() {}.getType());
                            }
                            V1Patch patch(String json) { return new V1Patch(json); }
                        }
                        """,
                        source -> source.path("src/main/java/example/KubernetesHotspots.java")
                )
        );
    }

    @Test
    void discoversAndValidatesRecipes() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATION_RECIPE.equals(recipe.getName())));
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
        assertEquals(1, migration.getRecipeList().size());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.kubernetesclientjava")
                .scanYamlResources()
                .build();
    }

    private static String directPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>kubernetes-client-app</artifactId><version>1</version>
                  <dependencies><dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>%s</version></dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String propertyPom(String version, String property) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><%1$s>%2$s</%1$s></properties>
                  <dependencies><dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>${%1$s}</version></dependency></dependencies>
                </project>
                """.formatted(property, version);
    }

    private static org.openrewrite.test.SourceSpecs gradleDependency(String version, String path) {
        return buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'io.kubernetes:client-java:%s' }
                """.formatted(version),
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'io.kubernetes:client-java:25.0.0-legacy' }
                """,
                source -> source.path(path)
        );
    }
}
