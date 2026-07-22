package com.huawei.clouds.openrewrite.feigncore;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class Feign13BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeign13BuildRisks());
    }

    @Test
    void marksVersionlessPropertyRangeAndDynamicOwners() {
        rewriteRun(
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version>
                          <properties><feign.version>12.4</feign.version></properties><dependencies>
                            <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency>
                            <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "migrate the actual version owner"))),
                buildGradle("""
                        plugins { id 'java' }
                        def feignVersion = '12.4'
                        dependencies {
                            implementation "io.github.openfeign:feign-core:${feignVersion}"
                            runtimeOnly 'io.github.openfeign:feign-core:+'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "migrate the actual version owner"))));
    }

    @Test
    void marksFixedOutOfScopeVersionWithoutWideningAutoSelection() {
        rewriteRun(pomXml(pom("13.5"), source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), "outside the workbook source set"))));
    }

    @Test
    void marksNonstandardVariantAndExplicitPreJava8Baseline() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies><dependency>
                    <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>12.4</version><classifier>sources</classifier>
                  </dependency></dependencies>
                </project>
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "requires Java 8 or newer");
                    assertContains(after.printAll(), "classified or non-JAR Feign Core artifact");
                })));
    }

    @Test
    void marksMisalignedFeignCompanionModules() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>family</artifactId><version>1</version><dependencies>
                          <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency>
                          <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-jackson</artifactId><version>12.4</version></dependency>
                        </dependencies></project>
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "companion module is not aligned"))),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies {
                            implementation 'io.github.openfeign:feign-core:13.6'
                            implementation 'io.github.openfeign:feign-okhttp:11.9'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "companion module is not aligned"))));
    }

    @Test
    void alignedTargetAndSimilarCoordinatesAreClean() {
        rewriteRun(
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>clean</artifactId><version>1</version>
                          <properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies>
                            <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency>
                            <dependency><groupId>io.github.openfeign</groupId><artifactId>feign-jackson</artifactId><version>13.6</version></dependency>
                            <dependency><groupId>example</groupId><artifactId>feign-core</artifactId><version>12.4</version></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-core:13.6' }"));
    }

    @Test
    void recommendedRecipeUpgradesSelectedVersionBeforeBuildAudit() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.feigncore")
                        .build().activateRecipes("com.huawei.clouds.openrewrite.feigncore.MigrateFeignCoreTo13_6")),
                pomXml(pom("12.4"), pom("13.6")));
    }

    @Test
    void buildMarkersAreIdempotentAndGeneratedTreesAreSkipped() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("13.5"), source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "outside the workbook source set"))),
                pomXml(pom("13.5"), source -> source.path("target/generated/pom.xml")));
    }

    @Test
    void finderOnlyAuditsRootGradleDependenciesAndUsesParentPathFilter() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'io.github.openfeign:feign-core:13.5' } }",
                        source -> source.afterRecipe(after ->
                                assertFalse(after.printAll().contains("outside the workbook"), after.printAll()))),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-core:13.5' }",
                        source -> source.path("GeneratedSources/build.gradle").afterRecipe(after ->
                                assertFalse(after.printAll().contains("outside the workbook"), after.printAll()))),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-core:13.5' }",
                        source -> source.path("install.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "outside the workbook"))));
    }

    @Test
    void pluginDependenciesAndFakeXmlAreNotAudited() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>example</groupId><artifactId>tool</artifactId><version>1</version><dependencies><dependency>
                            <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.5</version>
                          </dependency></dependencies>
                        </plugin></plugins></build></project>
                        """),
                xml("""
                        <catalog><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.5</version></dependency></catalog>
                        """, source -> source.path("catalog.xml")));
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>feign-app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
