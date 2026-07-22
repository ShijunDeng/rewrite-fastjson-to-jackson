package com.huawei.clouds.openrewrite.netflixeureka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class NetflixEurekaPrecisionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNetflixEurekaClientDependency());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.9.27", "1.10.17", "1.10.19", "2.0.0", "2.0.4", "2.0.6", "[1.10,2.0)", "LATEST"})
    void leavesEveryUnlistedRangeDynamicTargetAndFutureVersionUntouched(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void updatesTheNearestProfilePropertyOnly() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                          <properties><eureka.version>1.10.17</eureka.version></properties>
                          <profiles><profile><id>legacy-eureka</id>
                            <properties><eureka.version>1.10.18</eureka.version></properties>
                            <dependencies><dependency>
                              <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                              <version>${eureka.version}</version>
                            </dependency></dependencies>
                          </profile></profiles>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                          <properties><eureka.version>1.10.17</eureka.version></properties>
                          <profiles><profile><id>legacy-eureka</id>
                            <properties><eureka.version>2.0.4</eureka.version></properties>
                            <dependencies><dependency>
                              <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                              <version>${eureka.version}</version>
                            </dependency></dependencies>
                          </profile></profiles>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void leavesUndefinedAndParentOwnedPropertiesUntouched() {
        rewriteRun(xml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>example</groupId><artifactId>parent</artifactId><version>1</version></parent>
                  <artifactId>child</artifactId>
                  <dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                    <version>${eureka.version}</version>
                  </dependency></dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void leavesGradleInterpolationAndCatalogAliasesUntouched() {
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                def eurekaVersion = '1.10.18'
                dependencies {
                    implementation "com.netflix.eureka:eureka-client:$eurekaVersion"
                    testImplementation libs.netflix.eureka.client
                }
                """));
    }

    @Test
    void buildAuditMarksExternalManagementWithoutInventingAVersion() {
        rewriteRun(
                spec -> spec.recipe(new FindNetflixEurekaBuildRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies><!--~~(Eureka client version is managed outside this POM; change the owning parent/BOM instead of overriding an invisible value)~~>--><dependency>
                            <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                          </dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void configurationAuditMarksRemovedBootstrapAndEmbeddedCredentials() {
        rewriteRun(
                spec -> spec.recipe(new FindNetflixEurekaConfigurationRisks()),
                text(
                        "bootstrap.module=com.netflix.discovery.guice.EurekaModule\n",
                        "~~(Eureka 2.0.4 removed built-in Guice/Governator bootstrap classes; replace this descriptor with explicit application, transport, and lifecycle wiring)~~>bootstrap.module=com.netflix.discovery.guice.EurekaModule\n",
                        source -> source.path("src/main/resources/eureka-bootstrap.properties")
                ),
                text(
                        "eureka.serviceUrl.default=https://service-account:${EUREKA_PASSWORD}@registry.example/eureka/\n",
                        "~~(Eureka service URL embeds credentials; move them to a protected authentication mechanism and rotate the exposed value before transport migration)~~>eureka.serviceUrl.default=https://service-account:${EUREKA_PASSWORD}@registry.example/eureka/\n",
                        source -> source.path("src/test/resources/eureka-client.properties")
                )
        );
    }

    private static String pom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }
}
