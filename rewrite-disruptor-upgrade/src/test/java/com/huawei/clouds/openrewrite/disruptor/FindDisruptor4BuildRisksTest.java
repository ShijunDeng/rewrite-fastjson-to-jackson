package com.huawei.clouds.openrewrite.disruptor;

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

class FindDisruptor4BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDisruptor4BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeDisruptorDependencyTest.pom("4.0.0"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"3.4.2", "3.4.4"})
    void recommendedRecipeUpgradesEverySourceWithoutBuildRisk(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.disruptor").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.disruptor.MigrateDisruptorTo4_0_0")),
                xml(UpgradeDisruptorDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>4.0.0</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void exclusiveTargetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeDisruptorDependencyTest.project(
                "<properties><disruptor.version>4.0.0</disruptor.version></properties><dependencies>" +
                UpgradeDisruptorDependencyTest.dep("${disruptor.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void sharedSelectedPropertyIsMarkedAtDependencyOwner() {
        String pom = UpgradeDisruptorDependencyTest.project(
                "<properties><disruptor.version>3.4.4</disruptor.version></properties><dependencies>" +
                UpgradeDisruptorDependencyTest.dep("${disruptor.version}") +
                "</dependencies><build><finalName>${disruptor.version}</finalName></build>");
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.disruptor").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.disruptor.MigrateDisruptorTo4_0_0")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindDisruptor4BuildRisks.OWNER);
                    assertContains(after.printAll(), "<disruptor.version>3.4.4</disruptor.version>");
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[3,5)", "4.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeDisruptorDependencyTest.depWithoutVersion() :
                UpgradeDisruptorDependencyTest.dep(version);
        rewriteRun(xml(UpgradeDisruptorDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindDisruptor4BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeDisruptorDependencyTest.pom("3.4.3"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindDisruptor4BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(
                xml(UpgradeDisruptorDependencyTest.project("<dependencies>" +
                        UpgradeDisruptorDependencyTest.dep("4.0.0", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindDisruptor4BuildRisks.VARIANT))),
                xml(UpgradeDisruptorDependencyTest.project("<dependencies>" +
                        UpgradeDisruptorDependencyTest.dep("4.0.0", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindDisruptor4BuildRisks.VARIANT))));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingCompilerBaseline() {
        String pom = UpgradeDisruptorDependencyTest.project(
                "<profiles>" +
                "<profile><id>selected</id><dependencies>" + UpgradeDisruptorDependencyTest.dep("4.0.0") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><properties><maven.compiler.release>8</maven.compiler.release>" +
                "</properties></profile>" +
                "</profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileDependencyMarksSameProfileJavaBaseline() {
        String pom = UpgradeDisruptorDependencyTest.project(
                "<profiles><profile><id>selected</id>" +
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeDisruptorDependencyTest.dep("4.0.0") +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindDisruptor4BuildRisks.JAVA))));
    }

    @Test
    void rootDependencyMarksProfileJavaBaseline() {
        String pom = UpgradeDisruptorDependencyTest.project(
                "<dependencies>" + UpgradeDisruptorDependencyTest.dep("4.0.0") + "</dependencies>" +
                "<profiles><profile><id>downstream</id>" +
                "<properties><maven.compiler.release>10</maven.compiler.release></properties>" +
                "</profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindDisruptor4BuildRisks.JAVA))));
    }

    @Test
    void profileDependencyMarksRootJavaBaseline() {
        String pom = UpgradeDisruptorDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<profiles><profile><id>selected</id><dependencies>" +
                UpgradeDisruptorDependencyTest.dep("4.0.0") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindDisruptor4BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "pre-Java-11 baseline {0}")
    @ValueSource(strings = {"1.8", "8", "9", "10"})
    void marksPreJava11Baseline(String version) {
        rewriteRun(xml(UpgradeDisruptorDependencyTest.project(
                        "<properties><java.version>" + version + "</java.version></properties><dependencies>" +
                        UpgradeDisruptorDependencyTest.dep("4.0.0") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindDisruptor4BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "supported Java baseline {0}")
    @ValueSource(strings = {"11", "17", "21", "${java.release}"})
    void acceptsSupportedOrExternallyOwnedJavaBaseline(String version) {
        rewriteRun(xml(UpgradeDisruptorDependencyTest.project(
                        "<properties><maven.compiler.release>" + version + "</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeDisruptorDependencyTest.dep("4.0.0") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleOutsideDynamicAndVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.lmax:disruptor:3.4.3' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindDisruptor4BuildRisks.OUTSIDE))),
                buildGradle("def v='4.0.0'\ndependencies { implementation \"com.lmax:disruptor:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindDisruptor4BuildRisks.OWNER))),
                buildGradle("dependencies { implementation 'com.lmax:disruptor:4.0.0@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindDisruptor4BuildRisks.VARIANT))),
                buildGradleKts("dependencies { implementation(\"com.lmax:disruptor:3.4.2\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindDisruptor4BuildRisks.OWNER))));
    }

    @Test
    void nestedGradleUnrelatedAndGeneratedFilesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'com.lmax:disruptor:3.4.3' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:disruptor:3.4.3' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeDisruptorDependencyTest.pom("3.4.3"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeDisruptorDependencyTest.pom("3.4.3"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindDisruptor4BuildRisks.OUTSIDE, 1))));
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
