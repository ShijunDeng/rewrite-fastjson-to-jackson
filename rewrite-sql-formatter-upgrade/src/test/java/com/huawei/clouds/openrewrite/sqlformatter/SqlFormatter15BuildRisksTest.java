package com.huawei.clouds.openrewrite.sqlformatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class SqlFormatter15BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSqlFormatter15BuildRisks());
    }

    @Test
    void marksLiteralUnpublishedTargetAtVersionNode() {
        rewriteRun(xml(UpgradeSqlFormatterDependencyTest.pom("15.6.5"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, FindSqlFormatter15BuildRisks.UNPUBLISHED);
                    assertContains(out, "<!--~~(" + FindSqlFormatter15BuildRisks.UNPUBLISHED +
                            ")~~>--><version>15.6.5</version>");
                })));
    }

    @ParameterizedTest(name = "recommended source {0} upgrades then exposes invalid target")
    @ValueSource(strings = {"12.0.6", "12.2.0", "2.0.4", "3.1.0"})
    void recommendedRecipeUpgradesAndMarksEveryWorkbookSource(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.sqlformatter").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.sqlformatter.MigrateSqlFormatterTo15_6_5")),
                xml(UpgradeSqlFormatterDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertContains(out, "15.6.5");
                            assertContains(out, FindSqlFormatter15BuildRisks.UNPUBLISHED);
                            assertFalse(out.contains(sourceVersion), out);
                        })));
    }

    @Test
    void resolvesExclusiveRootPropertyToUnpublishedTarget() {
        rewriteRun(xml(UpgradeSqlFormatterDependencyTest.project(
                "<properties><fmt.version>15.6.5</fmt.version></properties><dependencies>" +
                UpgradeSqlFormatterDependencyTest.dep("${fmt.version}") + "</dependencies>"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED);
                    assertFalse(after.printAll().contains(FindSqlFormatter15BuildRisks.OWNER), after.printAll());
                })));
    }

    @Test
    void profileLocalOverrideWinsDuringRiskResolution() {
        rewriteRun(xml(UpgradeSqlFormatterDependencyTest.project(
                "<properties><fmt.version>2.0.5</fmt.version></properties><profiles><profile><id>one</id>" +
                "<properties><fmt.version>15.6.5</fmt.version></properties><dependencies>" +
                UpgradeSqlFormatterDependencyTest.dep("${fmt.version}") + "</dependencies></profile></profiles>"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED);
                    assertFalse(after.printAll().contains(FindSqlFormatter15BuildRisks.OUTSIDE), after.printAll());
                })));
    }

    @Test
    void recommendedRecipeMarksSharedSelectedPropertyAtActualDependencyOwner() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.sqlformatter").build();
        String pom = UpgradeSqlFormatterDependencyTest.project(
                "<properties><fmt.version>2.0.4</fmt.version></properties><dependencies>" +
                UpgradeSqlFormatterDependencyTest.dep("${fmt.version}") +
                "</dependencies><build><finalName>${fmt.version}</finalName></build>");
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.sqlformatter.MigrateSqlFormatterTo15_6_5")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, FindSqlFormatter15BuildRisks.OWNER);
                    assertContains(out, "<fmt.version>2.0.4</fmt.version>");
                    assertFalse(out.contains("15.6.5"), out);
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[2,16)", "12.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeSqlFormatterDependencyTest.depWithoutVersion() :
                UpgradeSqlFormatterDependencyTest.dep(version);
        rewriteRun(xml(UpgradeSqlFormatterDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSqlFormatter15BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeSqlFormatterDependencyTest.pom("2.0.5"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSqlFormatter15BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenClassifierAndTypeVariants() {
        rewriteRun(
                xml(UpgradeSqlFormatterDependencyTest.project("<dependencies>" +
                        UpgradeSqlFormatterDependencyTest.dep("15.6.5", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindSqlFormatter15BuildRisks.VARIANT))),
                xml(UpgradeSqlFormatterDependencyTest.project("<dependencies>" +
                        UpgradeSqlFormatterDependencyTest.dep("15.6.5", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("type/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindSqlFormatter15BuildRisks.VARIANT))));
    }

    @Test
    void marksGradleStringAndMapTargets() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:15.6.5' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED))),
                buildGradle("dependencies { runtimeOnly group: 'com.github.vertical-blank', name: 'sql-formatter', version: '15.6.5' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED))),
                buildGradleKts("dependencies { implementation(\"com.github.vertical-blank:sql-formatter:15.6.5\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED))));
    }

    @Test
    void marksGradleDynamicTemplateOwner() {
        rewriteRun(
                buildGradle("def v = '2.0.4'\ndependencies { implementation \"com.github.vertical-blank:sql-formatter:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSqlFormatter15BuildRisks.OWNER))),
                buildGradleKts("val v = \"2.0.4\"\ndependencies { implementation(\"com.github.vertical-blank:sql-formatter:$v\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSqlFormatter15BuildRisks.OWNER))));
    }

    @Test
    void nestedGradleAndUnrelatedCoordinatesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'com.github.vertical-blank:sql-formatter:15.6.5' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'org.hibernate:hibernate-core:15.6.5' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeSqlFormatterDependencyTest.project("<build><plugins><plugin><groupId>com.github.vertical-blank</groupId>" +
                        "<artifactId>sql-formatter</artifactId><version>15.6.5</version></plugin></plugins></build>"),
                        source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedParentsAreNoopAndInstallLeafIsProcessed() {
        rewriteRun(
                xml(UpgradeSqlFormatterDependencyTest.pom("15.6.5"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:15.6.5' }",
                        source -> source.path("installation/build.gradle").afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeSqlFormatterDependencyTest.pom("15.6.5"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeSqlFormatterDependencyTest.pom("15.6.5"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertCount(after.printAll(), FindSqlFormatter15BuildRisks.UNPUBLISHED, 1);
                        })));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("<!--~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
