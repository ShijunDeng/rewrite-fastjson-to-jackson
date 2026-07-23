package com.huawei.clouds.openrewrite.feignokhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class FeignOkHttp13BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeignOkHttp13BuildRisks());
    }

    @Test
    void marksVersionlessPropertyRangeDynamicAndCatalogOwners() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>owners</artifactId><version>1</version>
                      <properties><external.feign>12.4</external.feign></properties><dependencies>
                        <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${external.feign}</version></dependency>
                        <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId></dependency>
                        <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>[10,13)</version></dependency>
                      </dependencies>
                    </project>
                    """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "migrate the actual owner deliberately"))),
                buildGradle("""
                    def v='12.4'
                    dependencies {
                        implementation "io.github.openfeign:feign-okhttp:${v}"
                        runtimeOnly 'io.github.openfeign:feign-okhttp:+'
                    }
                    """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "migrate the actual owner deliberately"))),
                buildGradleKts("dependencies { implementation(libs.feign.okhttp) }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), "migrate the actual owner deliberately"))));
    }

    @Test
    void marksFixedVersionOutsideWorkbookWithoutWideningUpgrade() {
        rewriteRun(pomXml(pom("13.5"), source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), "not one of the workbook-selected sources"))));
    }

    @Test
    void marksNonstandardVariantAndJava7Baseline() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies><dependency>
                    <groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>12.4</version><classifier>tests</classifier>
                  </dependency></dependencies>
                </project>
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "require Java 8 or newer");
                    assertContains(after.printAll(), "nonstandard Feign OkHttp classifier");
                })));
    }

    @Test
    void marksMisalignedFeignFamilyInMavenGroovyAndKotlin() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>family</artifactId><version>1</version><dependencies>
                      <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.6</version></dependency>
                      <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>12.4</version></dependency>
                      <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-jackson</artifactId><version>${external.feign}</version></dependency>
                    </dependencies></project>
                    """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "companion module is not aligned"))),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-okhttp:13.6'; implementation 'io.github.openfeign:feign-core:12.4' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), "companion module is not aligned"))),
                buildGradleKts("dependencies { implementation(\"io.github.openfeign:feign-okhttp:13.6\"); implementation(\"io.github.openfeign:feign-jackson:11.1\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), "companion module is not aligned"))));
    }

    @Test
    void marksEveryDirectOkHttpModuleNotOn412() {
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>okhttp-family</artifactId><version>1</version><dependencies>
                  <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.6</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>3.6.0</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>logging-interceptor</artifactId><version>4.6.0</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-tls</artifactId><version>4.11.0</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-brotli</artifactId><version>${okhttp.version}</version></dependency>
                </dependencies></project>
                """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "OkHttp 4.12.0 BOM");
                    assertTrue(count(after.printAll(), "OkHttp 4.12.0 BOM") >= 4, after.printAll());
                })));
    }

    @Test
    void exactTargetFamiliesAndUnrelatedSameNamesAreClean() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>clean</artifactId><version>1</version>
                      <properties><java.version>17</java.version></properties><dependencies>
                        <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.6</version></dependency>
                        <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency>
                        <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.12.0</version></dependency>
                        <dependency><groupId>example</groupId><artifactId>feign-okhttp</artifactId><version>12.4</version></dependency>
                      </dependencies>
                    </project>
                    """, source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-okhttp:13.6'; implementation 'com.squareup.okhttp3:okhttp:4.12.0' }"));
    }

    @Test
    void marksExternalGradlePlatformsWithoutMutatingThem() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-okhttp'; implementation platform('io.github.openfeign:feign-bom:12.4') }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), "migrate the actual owner deliberately"))),
                buildGradleKts("dependencies { implementation(\"io.github.openfeign:feign-okhttp:13.6\"); implementation(platform(\"com.squareup.okhttp3:okhttp-bom:4.11.0\")) }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), "OkHttp 4.12.0 BOM"))));
    }

    @Test
    void localDependencyManagementMakesVersionlessConsumersClean() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>managed</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version><okhttp.version>4.12.0</okhttp.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency>
                    <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-bom</artifactId><version>${okhttp.version}</version><type>pom</type><scope>import</scope></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId></dependency>
                    <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                    <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>logging-interceptor</artifactId></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void rootManagementIsVisibleInProfileAndProfileOverrideWins() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>visible</artifactId><version>1</version>
                      <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.6</version></dependency></dependencies></dependencyManagement>
                      <profiles><profile><id>ci</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId></dependency></dependencies></profile></profiles>
                    </project>
                    """),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>override</artifactId><version>1</version>
                      <properties><feign.version>13.6</feign.version></properties>
                      <profiles><profile><id>legacy</id><properties><feign.version>12.4</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                    </project>
                    """, source -> source.path("override/pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "not one of the workbook-selected sources"))));
    }

    @Test
    void recommendedRecipeUpgradesBeforeAuditing() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignokhttp").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignokhttp.MigrateFeignOkHttpTo13_6")),
                pomXml(pom("12.4"), pom("13.6")));
    }

    @Test
    void markerIsIdempotentAndGeneratedParentsAreSkipped() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("13.5"), source -> source.after(actual -> actual).afterRecipe(after ->
                    assertContains(after.printAll(), "not one of the workbook-selected sources"))),
                pomXml(pom("13.5"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-okhttp:13.6'; implementation 'com.squareup.okhttp3:okhttp:4.11.0' }",
                        source -> source.path("install.gradle").after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), "OkHttp 4.12.0 BOM"))));
    }

    @Test
    void nestedGradlePluginMavenAndFakeXmlBoundariesAreNotAudited() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'io.github.openfeign:feign-okhttp:13.5' } }",
                        source -> source.afterRecipe(after ->
                            assertFalse(after.printAll().contains("workbook-selected"), after.printAll()))),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin><groupId>x</groupId><artifactId>tool</artifactId><version>1</version><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.5</version></dependency></dependencies></plugin></plugins></build></project>
                    """),
                xml("<catalog><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>13.5</version></dependency></catalog>", source -> source.path("catalog.xml")));
    }

    @Test
    void unrelatedOkHttpAndFeignOwnersWithoutTheAdapterAreNotAudited() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>ordinary-okhttp</artifactId><version>1</version>
                      <properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies>
                        <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency>
                        <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>12.4</version></dependency>
                      </dependencies>
                    </project>
                    """),
                buildGradle("dependencies { implementation 'com.squareup.okhttp3:okhttp:4.11.0'; implementation 'io.github.openfeign:feign-core:12.4' }"),
                buildGradleKts("dependencies { implementation(\"com.squareup.okhttp3:logging-interceptor:4.11.0\") }"));
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>client</artifactId><version>1</version><dependencies><dependency>
                 <groupId>io.github.openfeign</groupId><artifactId>feign-okhttp</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static int count(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
