package com.huawei.clouds.openrewrite.springcloudcontext;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringCloudContextDependencyTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springcloudcontext.UpgradeSpringCloudContextTo4_3_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE));
    }

    @Test
    void recipeIsDiscoverableAndContainsStrictRecipe() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertEquals("Upgrade Spring Cloud Context to 4.3.2", recipe.getDisplayName());
        assertTrue(recipe.getRecipeList().stream().anyMatch(UpgradeSelectedSpringCloudContextDependency.class::isInstance));
    }

    @Test
    void upgradesAllWorkbookMavenVersions() {
        rewriteRun(
                pomXml(pom("2.1.5.RELEASE"), pom("4.3.2")),
                pomXml(pom("3.1.1"), pom("4.3.2"), source -> source.path("v311/pom.xml")),
                pomXml(pom("3.1.6"), pom("4.3.2"), source -> source.path("v316/pom.xml")),
                pomXml(pom("3.1.7"), pom("4.3.2"), source -> source.path("v317/pom.xml"))
        );
    }

    @Test
    void upgradesOwnedPropertyAndManagedDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <properties><context.version>2.1.5.RELEASE</context.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <properties><context.version>4.3.2</context.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesProfileOwnedPropertyAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>context</id><properties><context.version>3.1.1</context.version></properties><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version><scope>runtime</scope><optional>true</optional>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>context</id><properties><context.version>4.3.2</context.version></properties><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version><scope>runtime</scope><optional>true</optional>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void resolvesRootPropertiesInsideProfiles() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-visible</artifactId><version>1</version>
                  <properties><context.version> 2.1.5.RELEASE </context.version></properties>
                  <profiles><profile><id>runtime</id><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version> ${context.version} </version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-visible</artifactId><version>1</version>
                  <properties><context.version> 4.3.2 </context.version></properties>
                  <profiles><profile><id>runtime</id><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version> ${context.version} </version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void profileOverrideWinsAndDoesNotLeakOutsideProfile() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                          <properties><context.version>9.9.9</context.version></properties>
                          <profiles><profile><id>runtime</id><properties><context.version>3.1.1</context.version></properties><dependencies><dependency>
                            <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                          <properties><context.version>9.9.9</context.version></properties>
                          <profiles><profile><id>runtime</id><properties><context.version>4.3.2</context.version></properties><dependencies><dependency>
                            <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>no-leak</artifactId><version>1</version>
                          <dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies>
                          <profiles><profile><id>runtime</id><properties><context.version>2.1.5.RELEASE</context.version></properties></profile></profiles>
                        </project>
                        """, source -> source.path("no-leak/pom.xml"))
        );
    }

    @Test
    void sameNamedPropertiesInDifferentProfilesHaveIndependentOwnership() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version><profiles>
                  <profile><id>one</id><properties><context.version>2.1.5.RELEASE</context.version></properties><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies></profile>
                  <profile><id>two</id><properties><context.version>3.1.6</context.version></properties><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies></profile>
                </profiles></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version><profiles>
                  <profile><id>one</id><properties><context.version>4.3.2</context.version></properties><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies></profile>
                  <profile><id>two</id><properties><context.version>4.3.2</context.version></properties><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies></profile>
                </profiles></project>
                """
        ));
    }

    @Test
    void upgradesEveryWorkbookSourceAcrossGradleDslForms() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' }",
                        "dependencies { runtimeOnly 'org.springframework.cloud:spring-cloud-context:4.3.2' }"),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework.cloud:spring-cloud-context:3.1.1\") }",
                        "dependencies { implementation(\"org.springframework.cloud:spring-cloud-context:4.3.2\") }"),
                buildGradle(
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.6' }",
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:4.3.2' }",
                        source -> source.path("streams.gradle")),
                buildGradle(
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.7' }",
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:4.3.2' }",
                        source -> source.path("config-download.gradle"))
        );
    }

    @Test
    void appliesRealPublicRepositoryFixturesWithoutStealingExternalOwnership() {
        rewriteRun(
                // edgarrth/java-kafka-streams@8de835.../pom.xml:116-120
                pomXml(pom("3.1.6"), pom("4.3.2"), source -> source.path("java-kafka-streams/pom.xml")),
                // Azure/azure-sdk-for-java@521bf18.../spring-cloud-azure-appconfiguration-config/pom.xml:31-35
                pomXml(pom("3.1.7"), pom("4.3.2"), source -> source.path("azure-appconfiguration/pom.xml")),
                // alibaba/spring-cloud-alibaba@c5e2723... uses a versionless BOM-owned dependency.
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>com.alibaba.cloud</groupId><artifactId>nacos-config</artifactId><version>1</version><dependencies><dependency>
                          <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId>
                        </dependency></dependencies></project>
                        """, source -> source.path("spring-cloud-alibaba-nacos-config/pom.xml")),
                // juhewu/juhewu-openfeign-spring-cloud-project@8e5dff... owns 3.1.1 in its parent POM.
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>com.juhewu</groupId><artifactId>parent</artifactId><version>1</version>
                          <properties><spring-cloud-context.version>3.1.1</spring-cloud-context.version></properties>
                        </project>
                        """, source -> source.path("juhewu/pom.xml")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>com.juhewu</groupId><artifactId>starter</artifactId><version>1</version><dependencies><dependency>
                          <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${spring-cloud-context.version}</version><scope>provided</scope>
                        </dependency></dependencies></project>
                        """, source -> source.path("juhewu/starter/pom.xml"))
        );
    }

    @Test
    void upgradesGroovyMapForms() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework.cloud', name: 'spring-cloud-context', version: '2.1.5.RELEASE' }",
                        "dependencies { runtimeOnly group: 'org.springframework.cloud', name: 'spring-cloud-context', version: '4.3.2' }"),
                buildGradle(
                        "dependencies { implementation([group: 'org.springframework.cloud', name: 'spring-cloud-context', version: '3.1.1']) }",
                        "dependencies { implementation([group: 'org.springframework.cloud', name: 'spring-cloud-context', version: '4.3.2']) }",
                        source -> source.path("map.gradle"))
        );
    }

    @Test
    void onlyChangesTopLevelRootGradleDependencyDsl() {
        rewriteRun(
                buildGradle("""
                        buildscript { dependencies { implementation 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' } }
                        subprojects { dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.1' } }
                        helper.dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.6' }
                        dependencies { helper.implementation 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' }
                        """),
                buildGradleKts("""
                        buildscript { dependencies { implementation("org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE") } }
                        subprojects { dependencies { implementation("org.springframework.cloud:spring-cloud-context:3.1.1") } }
                        project.dependencies { implementation("org.springframework.cloud:spring-cloud-context:3.1.6") }
                        """, source -> source.path("nested.gradle.kts"))
        );
    }

    @Test
    void leavesVersionsOutsideExactWorkbookSetUntouched() {
        rewriteRun(
                pomXml(pom("2.1.4.RELEASE")),
                pomXml(pom("3.1.5"), source -> source.path("v401/pom.xml")),
                pomXml(pom("4.3.2"), source -> source.path("target-version/pom.xml")),
                pomXml(pom("4.3.3"), source -> source.path("newer/pom.xml")),
                xml(pom("[2.3,4.1)"), source -> source.path("range/pom.xml")),
                xml(pom("LATEST"), source -> source.path("dynamic/pom.xml"))
        );
    }

    @Test
    void leavesSharedOrAmbiguousPropertiesUntouched() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                      <properties><shared.version>2.1.5.RELEASE</shared.version></properties><name>compat-${shared.version}</name>
                      <dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${shared.version}</version></dependency></dependencies>
                    </project>
                    """),
                pomXml(
                    """
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scoped</artifactId><version>1</version>
                      <properties><context.version>2.1.5.RELEASE</context.version></properties>
                      <profiles><profile><id>x</id><properties><context.version>3.1.1</context.version></properties></profile></profiles>
                      <dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies>
                    </project>
                    """,
                    """
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scoped</artifactId><version>1</version>
                      <properties><context.version>4.3.2</context.version></properties>
                      <profiles><profile><id>x</id><properties><context.version>3.1.1</context.version></properties></profile></profiles>
                      <dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("scoped/pom.xml"))
        );
    }

    @Test
    void leavesVariantsVersionlessAndInterpolatedCoordinatesUntouched() {
        rewriteRun(
                xml("""
                    <project><dependencies>
                      <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId></dependency>
                      <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>2.1.5.RELEASE</version><classifier>sources</classifier></dependency>
                      <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>3.1.1</version><type>pom</type></dependency>
                    </dependencies></project>
                    """, source -> source.path("variants/pom.xml")),
                buildGradle("""
                    def v = '2.1.5.RELEASE'
                    dependencies {
                        implementation "org.springframework.cloud:spring-cloud-context:${v}"
                        runtimeOnly 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE:tests'
                        runtimeOnly 'org.springframework.cloud:spring-cloud-context:3.1.1@zip'
                        implementation group: 'org.springframework.cloud', name: 'spring-cloud-context', version: '3.1.6', classifier: 'sources'
                    }
                    """)
        );
    }

    @Test
    void leavesPluginDependenciesAndLookalikesUntouched() {
        rewriteRun(
                xml("""
                    <project><build><plugins><plugin><groupId>example</groupId><artifactId>tool</artifactId><dependencies><dependency>
                      <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>2.1.5.RELEASE</version>
                    </dependency></dependencies></plugin></plugins></build></project>
                    """, source -> source.path("plugin/pom.xml")),
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>lookalike</artifactId><version>1</version><dependencies>
                      <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-cloud-context</artifactId><version>2.1.5.RELEASE</version></dependency>
                      <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-commons</artifactId><version>3.1.1</version></dependency>
                    </dependencies></project>
                    """, source -> source.path("lookalike/pom.xml")),
                buildGradle("def coordinate = 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE'\nprintln coordinate\n")
        );
    }

    @Test
    void parentDirectoryFilterDoesNotExcludeLeafNames() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' }",
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:4.3.2' }",
                        source -> source.path("install.gradle")),
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.1' }",
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:4.3.2' }",
                        source -> source.path("generated.gradle")),
                pomXml(pom("3.1.6"), pom("4.3.2"), source -> source.path("module-target/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' }",
                        source -> source.path("install/build.gradle")),
                pomXml(pom("3.1.1"), source -> source.path("target/pom.xml"))
        );
    }

    @Test
    void ignoresGeneratedPrefixesCachesAndCaseVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' }",
                        source -> source.path("generated-client/build.gradle")),
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.1' }",
                        source -> source.path("INSTALLER/build.gradle")),
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:3.1.6' }",
                        source -> source.path(".pnpm/pkg/build.gradle")),
                pomXml(pom("2.1.5.RELEASE"), source -> source.path("coverage/pom.xml"))
        );
    }

    @Test
    void ignoresVersionCatalogConfiguration() {
        rewriteRun(text("""
                [versions]
                cloudContext = "2.1.5.RELEASE"
                [libraries]
                spring-cloud-context = { module = "org.springframework.cloud:spring-cloud-context", version.ref = "cloudContext" }
                """, source -> source.path("gradle/libs.versions.toml")));
    }

    @Test
    void upgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("3.1.6"), pom("4.3.2")),
                buildGradle("dependencies { implementation 'org.springframework.cloud:spring-cloud-context:2.1.5.RELEASE' }",
                        "dependencies { implementation 'org.springframework.cloud:spring-cloud-context:4.3.2' }")
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static String pom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>%s</version>
                </dependency></dependencies></project>
                """.formatted(version);
    }
}
