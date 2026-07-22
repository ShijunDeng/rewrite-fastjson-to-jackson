package com.huawei.clouds.openrewrite.hibernatevalidator;

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

    @Test
    void strictDependencyUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(directPom("6.2.5.Final"), directPom("8.0.3.Final"))
        );
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
    void leavesGradleRangesAndKotlinInterpolationUntouched() {
        rewriteRun(
                buildGradle(gradle("implementation 'org.hibernate.validator:hibernate-validator:[6.0,7.0)'")),
                buildGradleKts("""
                        plugins { java }
                        val hvVersion = "6.2.5.Final"
                        dependencies { implementation("org.hibernate.validator:hibernate-validator:$hvVersion") }
                        """)
        );
    }

    @ParameterizedTest(name = "Kotlin Gradle upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
            "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
    })
    void upgradesEverySpreadsheetKotlinGradleVersion(String version) {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.hibernate.validator:hibernate-validator:%s") }
                """.formatted(version),
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.hibernate.validator:hibernate-validator:8.0.3.Final") }
                """
        ));
    }

    @Test
    void alignsLiteralFamilyInKotlinGradle() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                dependencies {
                    implementation("org.hibernate.validator:hibernate-validator:6.2.5.Final")
                    runtimeOnly("org.hibernate.validator:hibernate-validator-cdi:6.1.7.Final")
                    kapt("org.hibernate.validator:hibernate-validator-annotation-processor:6.2.4.Final")
                }
                """,
                """
                plugins { java }
                dependencies {
                    implementation("org.hibernate.validator:hibernate-validator:8.0.3.Final")
                    runtimeOnly("org.hibernate.validator:hibernate-validator-cdi:8.0.3.Final")
                    kapt("org.hibernate.validator:hibernate-validator-annotation-processor:8.0.3.Final")
                }
                """
        ));
    }

    @Test
    void alignsGroovyNamedAndMapLiteralFamilyDeclarations() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                dependencies {
                    implementation group: 'org.hibernate.validator', name: 'hibernate-validator', version: '6.2.5.Final'
                    runtimeOnly([group: 'org.hibernate.validator', name: 'hibernate-validator-cdi', version: '6.1.7.Final'])
                    annotationProcessor group: 'org.hibernate.validator', name: 'hibernate-validator-annotation-processor', version: '6.2.4.Final'
                }
                """,
                """
                plugins { id 'java' }
                dependencies {
                    implementation group: 'org.hibernate.validator', name: 'hibernate-validator', version: '8.0.3.Final'
                    runtimeOnly([group: 'org.hibernate.validator', name: 'hibernate-validator-cdi', version: '8.0.3.Final'])
                    annotationProcessor group: 'org.hibernate.validator', name: 'hibernate-validator-annotation-processor', version: '8.0.3.Final'
                }
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
    void upgradesAnExclusiveProfilePropertyAndFamilyDeclarations() {
        rewriteRun(pomXml(
                profilePropertyPom("6.2.3.Final"),
                profilePropertyPom("8.0.3.Final")
        ));
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
    void leavesPropertyReferencedByProjectMetadataUntouched() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>metadata-property</artifactId><version>1</version>
                  <name>${hv.version}</name>
                  <properties><hv.version>6.2.5.Final</hv.version></properties>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${hv.version}</version>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesDuplicatePropertyDefinitionsUntouched() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>duplicate-property</artifactId><version>1</version>
                  <properties><hv.version>6.2.5.Final</hv.version></properties>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${hv.version}</version>
                  </dependency></dependencies>
                  <profiles><profile><id>alternate</id><properties><hv.version>6.1.7.Final</hv.version></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void alignsOnlyListedLiteralFamilyMembersWhenCoreIsSelected() {
        rewriteRun(pomXml(
                literalFamilyPom("6.2.5.Final", "6.1.7.Final", "6.2.4.Final"),
                literalFamilyPom("8.0.3.Final", "8.0.3.Final", "8.0.3.Final")
        ));
    }

    @Test
    void doesNotDowngradeOrInferUnlistedCompanionVersions() {
        rewriteRun(pomXml(
                literalFamilyPom("6.2.5.Final", "8.0.4.Final", "6.2.2.Final"),
                literalFamilyPom("8.0.3.Final", "8.0.4.Final", "6.2.2.Final")
        ));
    }

    @Test
    void companionWithoutSelectedCoreIsNoOp() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cdi-only</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-cdi</artifactId><version>6.2.5.Final</version>
                  </dependency></dependencies>
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
    void ignoresGeneratedBuildDescriptors() {
        rewriteRun(
                pomXml(directPom("6.2.5.Final"), source -> source.path("target/pom.xml")),
                buildGradle(
                        gradle("implementation 'org.hibernate.validator:hibernate-validator:6.2.5.Final'"),
                        source -> source.path("build/generated/build.gradle")
                )
        );
    }

    @Test
    void preservesPluginDependenciesClassifiersNonJarArtifactsAndUnownedPaths() {
        rewriteRun(org.openrewrite.xml.Assertions.xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ownership</artifactId><version>1</version>
                  <properties><plugin.hv.version>6.2.5.Final</plugin.hv.version></properties>
                  <dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>6.2.5.Final</version></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-cdi</artifactId><version>6.1.7.Final</version><classifier>sources</classifier></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-annotation-processor</artifactId><version>6.2.4.Final</version><type>test-jar</type></dependency>
                  </dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId>
                    <dependencies><dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${plugin.hv.version}</version></dependency></dependencies>
                    <configuration><paths><path><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-annotation-processor</artifactId><version>6.2.4.Final</version></path></paths></configuration>
                  </plugin></plugins></build>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ownership</artifactId><version>1</version>
                  <properties><plugin.hv.version>6.2.5.Final</plugin.hv.version></properties>
                  <dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>8.0.3.Final</version></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-cdi</artifactId><version>6.1.7.Final</version><classifier>sources</classifier></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-annotation-processor</artifactId><version>6.2.4.Final</version><type>test-jar</type></dependency>
                  </dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId>
                    <dependencies><dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${plugin.hv.version}</version></dependency></dependencies>
                    <configuration><paths><path><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-annotation-processor</artifactId><version>6.2.4.Final</version></path></paths></configuration>
                  </plugin></plugins></build>
                </project>
                """, source -> source.path("ownership/pom.xml")
        ));
    }

    @Test
    void doesNotTreatGroovyAndKotlinMethodsOutsideDependenciesAsDeclarations() {
        rewriteRun(
                buildGradle("""
                        plugins { id 'java' }
                        void implementation(String coordinate) { println coordinate }
                        implementation('org.hibernate.validator:hibernate-validator:6.2.5.Final')
                        """, source -> source.path("groovy/build.gradle")),
                buildGradleKts("""
                        plugins { java }
                        fun implementation(coordinate: String) = println(coordinate)
                        implementation("org.hibernate.validator:hibernate-validator:6.2.5.Final")
                        """, source -> source.path("kotlin/build.gradle.kts"))
        );
    }

    @Test
    void sourceVersionWhitelistExactlyMatchesTheSpreadsheet() {
        assertEquals(Set.of(
                        "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
                        "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
                ), UpgradeSelectedHibernateValidatorDependency.SOURCE_VERSIONS);
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

    private static String profilePropertyPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profile-property</artifactId><version>1</version>
                  <profiles><profile><id>validation</id>
                    <properties><hv.version>%s</hv.version></properties>
                    <dependencyManagement><dependencies>
                      <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>${hv.version}</version></dependency>
                      <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-cdi</artifactId><version>${hv.version}</version></dependency>
                    </dependencies></dependencyManagement>
                  </profile></profiles>
                </project>
                """.formatted(version);
    }

    private static String literalFamilyPom(String core, String cdi, String processor) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>literal-family</artifactId><version>1</version>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>%s</version></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-cdi</artifactId><version>%s</version></dependency>
                  </dependencies></dependencyManagement>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration>
                    <annotationProcessorPaths><path>
                      <groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator-annotation-processor</artifactId><version>%s</version>
                    </path></annotationProcessorPaths>
                  </configuration></plugin></plugins></build>
                </project>
                """.formatted(core, cdi, processor);
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
