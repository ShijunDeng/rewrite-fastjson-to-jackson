package com.huawei.clouds.openrewrite.postgresql;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class Postgresql42BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindPostgresql42BuildRisks());
    }

    @Test
    void exactTargetAndLocalTargetPropertyAreClean() {
        rewriteRun(
                pomXml(pom("42.7.13")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>property</artifactId><version>1</version>
                      <properties><postgresql.version>42.7.13</postgresql.version><java.version>17</java.version></properties>
                      <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("property/pom.xml")),
                buildGradle("dependencies { runtimeOnly 'org.postgresql:postgresql:42.7.13' }"),
                buildGradleKts("dependencies { runtimeOnly(\"org.postgresql:postgresql:42.7.13\") }"));
    }

    @Test
    void marksOutsideUndefinedRangeVersionlessAndDynamicOwners() {
        rewriteRun(
                pomXml(pom("42.7.12"), source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindPostgresql42BuildRisks.OUTSIDE))),
                xml(pom("${external.postgresql}"), source -> source.path("variable/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OWNER))),
                xml(pom("[42.2,42.8)"), source -> source.path("range/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OWNER))),
                buildGradle("dependencies { runtimeOnly 'org.postgresql:postgresql' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OWNER))),
                buildGradle("def v='42.6.1'; dependencies { runtimeOnly \"org.postgresql:postgresql:$v\" }",
                        source -> source.path("dynamic.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OWNER))),
                buildGradleKts("val v=\"42.6.1\"; dependencies { runtimeOnly(\"org.postgresql:postgresql:$v\") }",
                        source -> source.path("dynamic.gradle.kts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OWNER))));
    }

    @Test
    void localDependencyManagementMakesVersionlessConsumerClean() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>managed</artifactId><version>1</version>
                  <properties><postgresql.version>42.7.13</postgresql.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void rootManagementIsVisibleInProfileAndProfileOverrideIsScoped() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>visible</artifactId><version>1</version>
                      <dependencyManagement><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.13</version></dependency></dependencies></dependencyManagement>
                      <profiles><profile><id>ci</id><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency></dependencies></profile></profiles>
                    </project>
                    """),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>override</artifactId><version>1</version>
                      <properties><postgresql.version>42.7.13</postgresql.version></properties>
                      <profiles><profile><id>legacy</id><properties><postgresql.version>42.6.2</postgresql.version></properties><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>${postgresql.version}</version></dependency></dependencies></profile></profiles>
                    </project>
                    """, source -> source.path("override/pom.xml").after(actual -> actual).afterRecipe(after ->
                            assertContains(after.printAll(), FindPostgresql42BuildRisks.OUTSIDE))));
    }

    @Test
    void marksJava7AndVariantAtExactNodes() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies><dependency>
                    <groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.13</version><classifier>tests</classifier>
                  </dependency></dependencies>
                </project>
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindPostgresql42BuildRisks.JAVA);
                    assertContains(after.printAll(), FindPostgresql42BuildRisks.VARIANT);
                })));
    }

    @Test
    void marksGradleVariantsCatalogAndOutsideMap() {
        rewriteRun(
                buildGradle("dependencies { runtimeOnly 'org.postgresql:postgresql:42.7.13@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.VARIANT))),
                buildGradle("dependencies { runtimeOnly group: 'org.postgresql', name: 'postgresql', version: '42.7.12' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OUTSIDE))),
                buildGradle("dependencies { runtimeOnly([group: 'org.postgresql', name: 'postgresql', version: '42.7.12']) }",
                        source -> source.path("map.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OUTSIDE))),
                buildGradleKts("dependencies { runtimeOnly(libs.postgresql) }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OWNER))));
    }

    @Test
    void recommendedRecipeUpgradesBeforeAuditing() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.postgresql").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.postgresql.MigratePostgresqlTo42_7_13")),
                pomXml(pom("42.5.6"), pom("42.7.13")));
    }

    @Test
    void siblingModulesAndNestedDslAreNotAudited() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>no-driver</artifactId><version>1</version>
                      <properties><maven.compiler.release>7</maven.compiler.release></properties>
                      <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>5.1.0</version></dependency></dependencies>
                    </project>
                    """, source -> source.afterRecipe(after ->
                            assertFalse(after.printAll().contains(FindPostgresql42BuildRisks.JAVA), after.printAll()))),
                buildGradle("subprojects { dependencies { runtimeOnly 'org.postgresql:postgresql:42.7.12' } }",
                        source -> source.afterRecipe(after ->
                                assertFalse(after.printAll().contains(FindPostgresql42BuildRisks.OUTSIDE), after.printAll()))));
    }

    @Test
    void pluginDependencyFakeXmlAndGeneratedParentsAreIgnored() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin><groupId>x</groupId><artifactId>tool</artifactId><version>1</version><dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.12</version></dependency></dependencies></plugin></plugins></build></project>
                    """),
                pomXml(pom("42.7.12"), source -> source.path("generated-code/pom.xml")));
    }

    @Test
    void markerIsIdempotentAndLeafInstallGradleRemainsOwned() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("42.7.12"), source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(count(after.printAll(), FindPostgresql42BuildRisks.OUTSIDE) == 1,
                                after.printAll()))),
                buildGradle("dependencies { runtimeOnly 'org.postgresql:postgresql:42.7.12' }",
                        source -> source.path("install.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindPostgresql42BuildRisks.OUTSIDE))));
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>driver</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static int count(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
