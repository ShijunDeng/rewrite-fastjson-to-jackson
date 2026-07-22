package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeHibernateValidatorTest implements RewriteTest {
    static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorDependencyTo8_0_3";
    static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.hibernatevalidator.MigrateHibernateValidatorTo8_0_3";
    static final String LEGACY_ALIAS =
            "com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorTo8";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @ParameterizedTest(name = "Maven upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
            "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
    })
    void upgradesEverySpreadsheetMavenVersion(String version) {
        rewriteRun(pomXml(directPom(version), directPom("8.0.3.Final")));
    }

    @ParameterizedTest(name = "Gradle upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
            "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
    })
    void upgradesEverySpreadsheetGradleVersion(String version) {
        rewriteRun(buildGradle(
                gradle("implementation 'org.hibernate.validator:hibernate-validator:" + version + "'"),
                gradle("implementation 'org.hibernate.validator:hibernate-validator:8.0.3.Final'")
        ));
    }

    @Test
    void leavesDynamicGradleVersionExpressionUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def hvVersion = '6.2.5.Final'
                dependencies { implementation "org.hibernate.validator:hibernate-validator:${hvVersion}" }
                """
        ));
    }

    @Test
    void leavesKotlinGradleCoreDependencyForManualReview() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.hibernate.validator:hibernate-validator:6.2.5.Final") }
                """
        ));
    }

    @ParameterizedTest(name = "managed Maven upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
            "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
    })
    void upgradesEverySpreadsheetManagedVersion(String version) {
        rewriteRun(pomXml(managedPom(version), managedPom("8.0.3.Final")));
    }

    @ParameterizedTest(name = "unlisted version {0} is a strict no-op")
    @ValueSource(strings = {
            "6.0.22.Final", "6.1.5.Final", "6.2.2.Final", "7.0.5.Final",
            "8.0.2.Final", "8.0.3.Final", "9.0.1.Final"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String version) {
        rewriteRun(pomXml(directPom(version)));
    }

    @Test
    void upgradesAResolvedMavenVersionProperty() {
        rewriteRun(pomXml(propertyPom("6.1.7.Final"), propertyPom("8.0.3.Final")));
    }

    @Test
    void leavesAmbiguouslySharedVersionPropertyUntouched() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>shared-property</artifactId><version>1</version>
                  <properties><shared.version>6.2.5.Final</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${shared.version}</version></dependency>
                    <dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void upgradesOnlyPresentCompanionArtifactsFromDremioStyleManagement() {
        // Reduced from dremio/dremio-oss at 799ccbda47e6f2e1bfacf1ccbded174e00d4150a:
        // https://github.com/dremio/dremio-oss/blob/799ccbda47e6f2e1bfacf1ccbded174e00d4150a/pom.xml#L76
        rewriteRun(pomXml(
                dremioStylePom("6.2.5.Final"),
                dremioStylePom("8.0.3.Final")
        ));
    }

    @Test
    void keepsExternalBomManagedVersionlessDependencyUntouched() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>bom-app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>
                    <version>2.7.18</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesSameArtifactInLegacyGroupUntouched() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>legacy-group</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.hibernate</groupId><artifactId>hibernate-validator</artifactId><version>6.2.5.Final</version>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void allPublishedEntryRecipesValidate() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);
        Recipe legacy = environment.activateRecipes(LEGACY_ALIAS);

        assertTrue(dependency.validateAll().stream().allMatch(validation -> validation.isValid()),
                dependency.validateAll().toString());
        assertTrue(migration.validateAll().stream().allMatch(validation -> validation.isValid()),
                migration.validateAll().toString());
        assertTrue(legacy.validateAll().stream().allMatch(validation -> validation.isValid()),
                legacy.validateAll().toString());
        assertEquals(DEPENDENCY_RECIPE, dependency.getName());
        assertEquals(MIGRATION_RECIPE, migration.getName());
        assertEquals(LEGACY_ALIAS, legacy.getName());
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.hibernatevalidator")
                .scanYamlResources()
                .build();
    }

    private static String directPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>validation-app</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String managedPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>managed-app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>%s</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String propertyPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><hibernate-validator.version>%s</hibernate-validator.version></properties>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId>
                    <version>${hibernate-validator.version}</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String dremioStylePom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>dremio-style</artifactId><version>1</version>
                  <properties><hibernate-validator.version>%s</hibernate-validator.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${hibernate-validator.version}</version></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-cdi</artifactId><version>${hibernate-validator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId>
                  </dependency></dependencies>
                  <build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
                    <configuration><annotationProcessorPaths><path>
                      <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-annotation-processor</artifactId>
                      <version>${hibernate-validator.version}</version>
                    </path></annotationProcessorPaths></configuration>
                  </plugin></plugins></build>
                </project>
                """.formatted(version);
    }

    private static String gradle(String dependency) {
        return """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { %s }
                """.formatted(dependency);
    }
}
