package com.huawei.clouds.openrewrite.slf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSlf4jTest implements RewriteTest {
    static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.slf4j.UpgradeSlf4jApiTo2_0_17";
    static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.slf4j.MigrateSlf4jTo2_0_17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @ParameterizedTest(name = "upgrades exact Maven source {0}")
    @ValueSource(strings = {
            "1.7.25", "1.7.26", "1.7.30", "1.7.32", "1.7.34", "1.7.35", "1.7.36",
            "2.0.0", "2.0.0-alpha1", "2.0.6"
    })
    void upgradesEveryExplicitSpreadsheetVersionInMaven(String version) {
        rewriteRun(pomXml(
                directPom(version),
                directPom("2.0.17")
        ));
    }

    @Test
    void upgradesManagedMavenDependency() {
        rewriteRun(pomXml(
                managedPom("1.7.26"),
                managedPom("2.0.17")
        ));
    }

    @Test
    void upgradesExclusiveMavenProperty() {
        rewriteRun(pomXml(
                propertyPom("1.7.30"),
                propertyPom("2.0.17")
        ));
    }

    @Test
    void isolatesSharedPropertyFromApacheShardingSphereShape() {
        // Reduced from apache/shardingsphere at 1668c9378b84b2ad8b27c7535daaf99cff120b34:
        // https://github.com/apache/shardingsphere/blob/1668c9378b84b2ad8b27c7535daaf99cff120b34/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shardingsphere</artifactId><version>1</version>
                  <properties><slf4j.version>1.7.36</slf4j.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>${slf4j.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>${slf4j.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shardingsphere</artifactId><version>1</version>
                  <properties><slf4j.version>1.7.36</slf4j.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>${slf4j.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>${slf4j.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void isolatesPropertyEmbeddedInUnrelatedProjectMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>embedded-property</artifactId><version>1</version>
                  <name>logging-${slf4j.version}</name>
                  <properties><slf4j.version>1.7.36</slf4j.version></properties>
                  <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>embedded-property</artifactId><version>1</version>
                  <name>logging-${slf4j.version}</name>
                  <properties><slf4j.version>1.7.36</slf4j.version></properties>
                  <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesExternalSpringBootBomManagement() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>boot-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>2.7.18</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesSdkmanGradleDependency() {
        // Reduced from sdkman/sdkman-cli at 1c8f4cb9101a7cbc2da6453c12bd547531bde29f:
        // https://github.com/sdkman/sdkman-cli/blob/1c8f4cb9101a7cbc2da6453c12bd547531bde29f/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { testImplementation('org.slf4j:slf4j-api:1.7.32') }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { testImplementation('org.slf4j:slf4j-api:2.0.17') }
                """
        ));
    }

    @Test
    void upgradesBftSmartGradleDependency() {
        // Reduced from bft-smart/library at 681cec8cf83e1cabe55f45c6edd3d80bd8ad156d:
        // https://github.com/bft-smart/library/blob/681cec8cf83e1cabe55f45c6edd3d80bd8ad156d/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies { implementation 'org.slf4j:slf4j-api:1.7.32' }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies { implementation 'org.slf4j:slf4j-api:2.0.17' }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation group: 'org.slf4j', name: 'slf4j-api', version: '2.0.6' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation group: 'org.slf4j', name: 'slf4j-api', version: '2.0.17' }
                """
        ));
    }

    @Test
    void upgradesKotlinDslWithToolingModel() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies { implementation("org.slf4j:slf4j-api:1.7.36") }
                        """,
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies { implementation("org.slf4j:slf4j-api:2.0.17") }
                        """
                )
        );
    }

    @Test
    void leavesHiddenAndUnlistedVersionsUntouched() {
        rewriteRun(
                pomXml(directPom("1.7.31")),
                pomXml(directPom("2.0.5"), spec -> spec.path("2.0.5-pom.xml")),
                pomXml(directPom("2.0.7"), spec -> spec.path("2.0.7-pom.xml"))
        );
    }

    @Test
    void leavesTargetVersionUntouched() {
        rewriteRun(pomXml(directPom("2.0.17")));
    }

    @Test
    void leavesSimilarArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-ext</artifactId><version>1.7.36</version></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>1.7.36</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void leavesUnresolvedGradleInterpolationUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def slf4jVersion = providers.gradleProperty('slf4jVersion')
                dependencies { implementation "org.slf4j:slf4j-api:${slf4jVersion}" }
                """
        ));
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(r -> DEPENDENCY_RECIPE.equals(r.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(r -> MIGRATION_RECIPE.equals(r.getName())));
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.slf4j")
                .scanYamlResources()
                .build();
    }

    private static String directPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>direct</artifactId><version>1</version>
                 <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>%s</version></dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static String managedPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                 <dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>%s</version></dependency></dependencies></dependencyManagement>
               </project>
               """.formatted(version);
    }

    private static String propertyPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                 <properties><slf4j.version>%s</slf4j.version></properties>
                 <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version></dependency></dependencies>
               </project>
               """.formatted(version);
    }
}
