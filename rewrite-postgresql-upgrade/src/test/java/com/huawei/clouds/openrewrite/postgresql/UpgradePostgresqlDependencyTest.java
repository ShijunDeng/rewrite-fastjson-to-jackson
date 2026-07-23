package com.huawei.clouds.openrewrite.postgresql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradePostgresqlDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.postgresql.UpgradePostgresqlTo42_7_13";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.postgresql.MigratePostgresqlTo42_7_13";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedPostgresqlDependency());
    }

    @ParameterizedTest(name = "workbook source {0} -> 42.7.13")
    @ValueSource(strings = {"1.17.6", "14.8", "42.2.19", "42.2.5", "42.5.1", "42.5.4", "42.5.5", "42.5.6", "42.6.0", "42.6.1"})
    void upgradesEveryAndOnlyWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("42.7.13"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesAllWorkbookOccurrences() {
        assertEquals(Set.of("1.17.6", "14.8", "42.2.19", "42.2.5", "42.5.1", "42.5.4", "42.5.5", "42.5.6", "42.6.0", "42.6.1"),
                UpgradeSelectedPostgresqlDependency.SOURCE_VERSIONS);
    }

    @Test
    void real42_2_19KotlinFixtureFromGitstarRanking() {
        // Reduced from k0kubun/gitstar-ranking at 550a399d574f2171731b4056295d881c5edd5ba5:
        // https://github.com/k0kubun/gitstar-ranking/blob/550a399d574f2171731b4056295d881c5edd5ba5/worker/build.gradle.kts
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.postgresql:postgresql:42.2.19\") }",
                "dependencies { implementation(\"org.postgresql:postgresql:42.7.13\") }"));
    }

    @Test
    void real42_5_6GroovyFixtureFromGraduationBe() {
        // Reduced from donalmun/GraduationBE at bfe66671501210425114142fa8b0827b505aefed:
        // https://github.com/donalmun/GraduationBE/blob/bfe66671501210425114142fa8b0827b505aefed/build.gradle
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.postgresql:postgresql:42.5.6' }",
                "dependencies { implementation 'org.postgresql:postgresql:42.7.13' }"));
    }

    @Test
    void real42_6_1GradleFixtureFromNasaAmmos() {
        // Reduced from NASA-AMMOS/plandev at 5c38ed678074616d935d727f05f293f3cd5719e0:
        // https://github.com/NASA-AMMOS/plandev/blob/5c38ed678074616d935d727f05f293f3cd5719e0/merlin-worker/build.gradle
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.postgresql:postgresql:42.6.1' }",
                "dependencies { implementation 'org.postgresql:postgresql:42.7.13' }"));
    }

    @ParameterizedTest(name = "Gradle {0} source {1}")
    @MethodSource("gradleSources")
    void upgradesRootGroovyAndKotlinGradle(String dialect, String version) {
        if ("groovy".equals(dialect)) {
            rewriteRun(buildGradle(
                    "dependencies { implementation 'org.postgresql:postgresql:%s' }".formatted(version),
                    "dependencies { implementation 'org.postgresql:postgresql:42.7.13' }"));
        } else {
            rewriteRun(buildGradleKts(
                    "dependencies { implementation(\"org.postgresql:postgresql:%s\") }".formatted(version),
                    "dependencies { implementation(\"org.postgresql:postgresql:42.7.13\") }"));
        }
    }

    static Stream<Arguments> gradleSources() {
        return Stream.of("42.2.19", "42.5.6", "42.6.1").flatMap(version ->
                Stream.of(Arguments.of("groovy", version), Arguments.of("kotlin", version)));
    }

    @Test
    void upgradesBothGroovyMapNotations() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    runtimeOnly group: 'org.postgresql', name: 'postgresql', version: '42.5.6'
                    testImplementation([group: 'org.postgresql', name: 'postgresql', version: '42.6.1'])
                }
                """,
                """
                dependencies {
                    runtimeOnly group: 'org.postgresql', name: 'postgresql', version: '42.7.13'
                    testImplementation([group: 'org.postgresql', name: 'postgresql', version: '42.7.13'])
                }
                """));
    }

    @Test
    void rootPropertyIsVisibleInProfiles() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><postgresql.version>42.5.6</postgresql.version></properties>
                  <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><postgresql.version>42.7.13</postgresql.version></properties>
                  <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profileOverrideWinsAndOwnersStayIndependent() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><postgresql.version>42.2.19</postgresql.version></properties>
                  <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><postgresql.version>42.6.1</postgresql.version></properties><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><postgresql.version>42.7.13</postgresql.version></properties>
                  <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><postgresql.version>42.7.13</postgresql.version></properties><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profilePropertyDoesNotLeakAndSharedPropertyIsProtected() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                      <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                      <profiles><profile><id>owner</id><properties><postgresql.version>42.6.1</postgresql.version></properties><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                    </project>
                    """, """
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                      <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                      <profiles><profile><id>owner</id><properties><postgresql.version>42.7.13</postgresql.version></properties><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                    </project>
                    """, source -> source.path("pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>shared</artifactId><version>1</version>
                      <properties><postgresql.version>42.5.6</postgresql.version><banner>${postgresql.version}</banner></properties>
                      <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("shared/pom.xml")));
    }

    @ParameterizedTest(name = "unsupported fixed version {0} is NOOP")
    @ValueSource(strings = {"9.4.1212", "42.2.18", "42.2.20", "42.5.0", "42.5.7", "42.6.2", "42.7.0", "42.7.12", "42.7.13", "42.8.0"})
    void doesNotWidenWorkbookWhitelist(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void variablesRangesVersionlessBomAndVariantsAreNoop() {
        rewriteRun(
                xml(pom("[42.2,42.8)"), source -> source.path("range/pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                      <dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.2.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                      <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency></dependencies>
                    </project>
                    """, source -> source.path("bom/pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.6.1</version><classifier>tests</classifier></dependency></dependencies></project>
                    """, source -> source.path("variant/pom.xml")),
                buildGradle("def v='42.6.1'; dependencies { implementation \"org.postgresql:postgresql:${v}\" }"));
    }

    @ParameterizedTest(name = "protected Gradle boundary {0}")
    @MethodSource("nestedGradleBlocks")
    void onlyRootDependenciesDslIsOwned(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradleBlocks() {
        String c = "org.postgresql:postgresql:42.6.1";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath '" + c + "' } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation '" + c + "' } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation '" + c + "' } }"),
                Arguments.of("project", "project(':api') { dependencies { implementation '" + c + "' } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation '" + c + "' } }"),
                Arguments.of("custom", "company { dependencies { implementation '" + c + "' } }"),
                Arguments.of("selected", "dependencies { helper.implementation '" + c + "' }"));
    }

    @ParameterizedTest(name = "excluded parent {0}")
    @ValueSource(strings = {"target", "BUILD", "out", "dist", ".gradle", ".m2", "node_modules", "vendor", ".pnpm", ".yarn", ".cache", "reports", "test-results", "generated-test", "installations", ".output", "storybook-static", "tmp", "TEMP", ".vite", ".nuxt"})
    void excludesGeneratedCachesAndInstallParentComponents(String parent) {
        rewriteRun(xml(pom("42.6.1"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafNamesRemainOwned() {
        rewriteRun(buildGradle("dependencies { implementation 'org.postgresql:postgresql:42.6.1' }",
                "dependencies { implementation 'org.postgresql:postgresql:42.7.13' }",
                source -> source.path("install.gradle")));
    }

    @Test
    void idempotentAndLowLevelDoesNotTouchCompanionHikari() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                      <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.6.1</version></dependency>
                      <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>5.1.0</version></dependency>
                    </dependencies></project>
                    """, """
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                      <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.13</version></dependency>
                      <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>5.1.0</version></dependency>
                    </dependencies></project>
                    """));
    }

    @Test
    void discoversValidPublicRecipesInDeclaredOrder() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.postgresql").build();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertEquals(UPGRADE, upgrade.getName());
        assertEquals(MIGRATE, migrate.getName());
        assertEquals(UPGRADE, migrate.getRecipeList().get(0).getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>client</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }
}
