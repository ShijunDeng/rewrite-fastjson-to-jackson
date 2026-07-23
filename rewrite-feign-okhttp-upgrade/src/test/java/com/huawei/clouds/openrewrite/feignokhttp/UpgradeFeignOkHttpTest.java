package com.huawei.clouds.openrewrite.feignokhttp;

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

class UpgradeFeignOkHttpTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.feignokhttp.UpgradeFeignOkHttpTo13_6";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.feignokhttp.MigrateFeignOkHttpTo13_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedFeignOkHttpDependency());
    }

    @ParameterizedTest(name = "workbook source {0} -> 13.6")
    @ValueSource(strings = {"10.4.0", "11.1", "12.4"})
    void upgradesEveryAndOnlyWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("13.6"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesAllWorkbookOccurrences() {
        assertEquals(Set.of("10.4.0", "11.1", "12.4"),
                UpgradeSelectedFeignOkHttpDependency.SOURCE_VERSIONS);
    }

    @Test
    void real10_4PropertyFixtureFromFeignValidation() {
        // Reduced from mwiede/feign-validation at 2967d856c3349b8f4c59e4ef8a0b6df4dac6f3e2:
        // https://github.com/mwiede/feign-validation/blob/2967d856c3349b8f4c59e4ef8a0b6df4dac6f3e2/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>net.mwiede</groupId><artifactId>feign-validation</artifactId><version>1</version>
                  <properties><feign.version>10.4.0</feign.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><scope>test</scope></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>net.mwiede</groupId><artifactId>feign-validation</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><scope>test</scope></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void real11_1DependencyManagementFixtureFromNutzboot() {
        // Reduced from nutzam/nutzboot at ffe77a39777b6f3021bd3dafbd3a23810d871785:
        // https://github.com/nutzam/nutzboot/blob/ffe77a39777b6f3021bd3dafbd3a23810d871785/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.nutz</groupId><artifactId>nutzboot</artifactId><version>1</version>
                  <properties><feign.version>11.1</feign.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency>
                    <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.nutz</groupId><artifactId>nutzboot</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency>
                    <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """));
    }

    @Test
    void real12_4MavenFixtureFromCumulocity() {
        // Reduced from Cumulocity-IoT/cumulocity-lora at ade95723d1fdbb317217a66999dfb820af9b1ee2:
        // https://github.com/Cumulocity-IoT/cumulocity-lora/blob/ade95723d1fdbb317217a66999dfb820af9b1ee2/java/lora-ns-netmore/pom.xml
        rewriteRun(pomXml(pom("12.4"), pom("13.6")));
    }

    @Test
    void real12_4GradleFixtureFromGitScore() {
        // Reduced from Kseon14/git-score at 367f4492529c4afcab7a9d0345b3f4f99cf12379:
        // https://github.com/Kseon14/git-score/blob/367f4492529c4afcab7a9d0345b3f4f99cf12379/build.gradle
        rewriteRun(buildGradle(
                "dependencies { implementation 'io.github.openfeign:feign-okhttp:12.4' }",
                "dependencies { implementation 'io.github.openfeign:feign-okhttp:13.6' }"));
    }

    @ParameterizedTest(name = "Gradle {0} source {1}")
    @MethodSource("gradleSources")
    void upgradesRootGroovyAndKotlinGradle(String dialect, String version) {
        if ("groovy".equals(dialect)) {
            rewriteRun(buildGradle(
                    "dependencies { implementation 'io.github.openfeign:feign-okhttp:%s' }".formatted(version),
                    "dependencies { implementation 'io.github.openfeign:feign-okhttp:13.6' }"));
        } else {
            rewriteRun(buildGradleKts(
                    "dependencies { implementation(\"io.github.openfeign:feign-okhttp:%s\") }".formatted(version),
                    "dependencies { implementation(\"io.github.openfeign:feign-okhttp:13.6\") }"));
        }
    }

    static Stream<Arguments> gradleSources() {
        return Stream.of("10.4.0", "11.1", "12.4").flatMap(version ->
                Stream.of(Arguments.of("groovy", version), Arguments.of("kotlin", version)));
    }

    @Test
    void upgradesBothGroovyMapNotations() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    runtimeOnly group: 'io.github.openfeign', name: 'feign-okhttp', version: '11.1'
                    testImplementation([group: 'io.github.openfeign', name: 'feign-okhttp', version: '12.4'])
                }
                """,
                """
                dependencies {
                    runtimeOnly group: 'io.github.openfeign', name: 'feign-okhttp', version: '13.6'
                    testImplementation([group: 'io.github.openfeign', name: 'feign-okhttp', version: '13.6'])
                }
                """));
    }

    @Test
    void rootPropertyIsVisibleInProfiles() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><feign.okhttp.version>11.1</feign.okhttp.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.okhttp.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.okhttp.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><feign.okhttp.version>13.6</feign.okhttp.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.okhttp.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.okhttp.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profileOverrideWinsAndOwnersStayIndependent() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><feign.version>10.4.0</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><feign.version>12.4</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><feign.version>13.6</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profilePropertyDoesNotLeakAndSharedPropertyIsProtected() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                      <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies>
                      <profiles><profile><id>owner</id><properties><feign.version>12.4</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                    </project>
                    """, """
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                      <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies>
                      <profiles><profile><id>owner</id><properties><feign.version>13.6</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                    </project>
                    """, source -> source.path("pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>shared</artifactId><version>1</version>
                      <properties><feign.version>11.1</feign.version><banner>${feign.version}</banner></properties>
                      <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("shared/pom.xml")));
    }

    @ParameterizedTest(name = "unsupported fixed version {0} is NOOP")
    @ValueSource(strings = {"9.7.0", "10.3", "10.4.1", "11.0", "11.2", "12", "12.3", "12.5", "13.5", "13.6", "13.7"})
    void doesNotWidenWorkbookWhitelist(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void variablesRangesVersionlessBomAndVariantsAreNoop() {
        rewriteRun(
                xml(pom("[10,13)"), source -> source.path("range/pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                      <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-bom</artifactId><version>12.4</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                      <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId></dependency></dependencies>
                    </project>
                    """, source -> source.path("bom/pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>12.4</version><classifier>tests</classifier></dependency></dependencies></project>
                    """, source -> source.path("variant/pom.xml")),
                buildGradle("def v='12.4'; dependencies { implementation \"io.github.openfeign:feign-okhttp:${v}\" }"));
    }

    @ParameterizedTest(name = "protected Gradle boundary {0}")
    @MethodSource("nestedGradleBlocks")
    void onlyRootDependenciesDslIsOwned(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradleBlocks() {
        String c = "io.github.openfeign:feign-okhttp:12.4";
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
        rewriteRun(xml(pom("12.4"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafNamesRemainOwned() {
        rewriteRun(buildGradle("dependencies { implementation 'io.github.openfeign:feign-okhttp:12.4' }",
                "dependencies { implementation 'io.github.openfeign:feign-okhttp:13.6' }",
                source -> source.path("install.gradle")));
    }

    @Test
    void idempotentAndLowLevelDoesNotTouchSourceOrCompanionOkHttp() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                      <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>12.4</version></dependency>
                      <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency>
                    </dependencies></project>
                    """, """
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                      <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.6</version></dependency>
                      <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency>
                    </dependencies></project>
                    """));
    }

    @Test
    void discoversValidPublicRecipesInDeclaredOrder() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignokhttp").build();
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
                 <groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }
}
