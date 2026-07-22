package com.huawei.clouds.openrewrite.feignjackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FeignJackson13BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeignJackson13BuildRisks());
    }

    @Test
    void marksVersionlessPropertyRangeAndDynamicOwners() {
        rewriteRun(
                xml(project("<properties><f>12.4</f></properties><dependencies>" + dep("${f}") + depNoVersion() + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), "migrate the actual owner deliberately", 2))),
                buildGradle("""
                        def f = '12.4'
                        dependencies {
                            implementation "io.github.openfeign:feign-jackson:${f}"
                            runtimeOnly 'io.github.openfeign:feign-jackson:+'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "migrate the actual owner deliberately", 2))));
    }

    @Test
    void marksFixedOutOfScopeAtVersionNode() {
        rewriteRun(xml(pom("13.5"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, "outside the workbook source set");
            assertTrue(printed.indexOf("outside the workbook source set") < printed.indexOf("13.5"), printed);
        })));
    }

    @Test
    void marksClassifierAndNonJarAtDependencyNode() {
        rewriteRun(xml(project("<dependencies>" +
                        dep("12.4", "<classifier>tests</classifier>") +
                        dep("11.1", "<type>zip</type>") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "classified or non-JAR Feign Jackson artifact", 2))));
    }

    @Test
    void marksPreJava8OnlyAtStandardProjectOrProfileProperty() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>java</artifactId><version>1</version>
                  <properties><maven.compiler.release>7</maven.compiler.release><unrelated.java>6</unrelated.java></properties>
                  <profiles><profile><id>legacy</id><properties><java.version>1.7</java.version></properties></profile></profiles>
                </project>
                """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), "requires Java 8 or newer", 2);
                    assertFalse(after.printAll().contains("unrelated.java>/*~~"), after.printAll());
                })));
    }

    @Test
    void marksMisalignedFeignFamilyAndJacksonModules() {
        rewriteRun(xml(project("<dependencies>" + dep("13.6") +
                        dependency("io.github.openfeign", "feign-core", "12.4") +
                        dependency("io.github.openfeign", "feign-okhttp", "11.1") +
                        dependency("com.fasterxml.jackson.core", "jackson-databind", "2.10.5.1") +
                        dependency("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.15.2") +
                        "</dependencies>"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), "not aligned to 13.6", 2);
                    assertCount(after.printAll(), "built and tested with Jackson 2.18.3", 2);
                })));
    }

    @Test
    void marksGradleGroovyMapAndKotlinCoordinates() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                            implementation group: 'io.github.openfeign', name: 'feign-jackson', version: '13.5'
                            implementation group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.15.2'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "outside the workbook source set");
                    assertContains(after.printAll(), "built and tested with Jackson 2.18.3");
                })),
                buildGradleKts("""
                        dependencies {
                            implementation("io.github.openfeign:feign-core:12.4")
                            implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.2")
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "not aligned to 13.6");
                    assertContains(after.printAll(), "built and tested with Jackson 2.18.3");
                })));
    }

    @Test
    void alignedTargetFamilyAndJacksonLineAreClean() {
        rewriteRun(
                xml(project("<properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies>" +
                        dep("13.6") + dependency("io.github.openfeign", "feign-core", "13.6") +
                        dependency("com.fasterxml.jackson.core", "jackson-databind", "2.18.3") +
                        dependency("example", "jackson-databind", "2.10.0") + "</dependencies>"),
                        source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-jackson:13.6'; implementation 'com.fasterxml.jackson.core:jackson-core:2.18.3' }"));
    }

    @Test
    void nestedGradlePluginDependenciesAndFakeXmlAreNotAudited() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'io.github.openfeign:feign-jackson:13.5' } }"),
                buildGradle("buildscript { dependencies { classpath 'com.fasterxml.jackson.core:jackson-databind:2.15.2' } }"),
                xml("<catalog><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-jackson</artifactId><version>13.5</version></dependency></catalog>",
                        source -> source.path("catalog.xml")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>x</groupId><artifactId>tool</artifactId><version>1</version><dependencies>%s</dependencies>
                        </plugin></plugins></build></project>
                        """.formatted(dep("13.5")), source -> source.path("pom.xml")));
    }

    @Test
    void generatedPrefixesCachesAndLeafFilenameAreFilteredPrecisely() {
        rewriteRun(
                xml(pom("13.5"), source -> source.path("generatedClients/pom.xml")),
                xml(pom("13.5"), source -> source.path("installation/cache/pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-jackson:13.5' }",
                        source -> source.path(".gradle/cache/build.gradle")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-jackson:13.5' }",
                        source -> source.path("install.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "outside the workbook source set"))));
    }

    @Test
    void markersAreTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("13.5"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "outside the workbook source set", 1))));
    }

    @Test
    void recommendedRecipeUpgradesSelectedVersionBeforeAudit() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignjackson").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignjackson.MigrateFeignJacksonTo13_6")),
                xml(pom("12.4"), pom("13.6"), source -> source.path("pom.xml")));
    }

    @Test
    void recommendedRecipeUpdatesOwnedPropertyWithoutFalsePositiveMarker() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignjackson").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignjackson.MigrateFeignJacksonTo13_6")),
                xml(
                        project("<properties><f>11.1</f></properties><dependencies>" + dep("${f}") + "</dependencies>"),
                        project("<properties><f>13.6</f></properties><dependencies>" + dep("${f}") + "</dependencies>"),
                        source -> source.path("pom.xml")));
    }

    private static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dep(String version) {
        return dep(version, "");
    }

    private static String dep(String version, String extra) {
        return "<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-jackson</artifactId><version>" + version + "</version>" + extra + "</dependency>";
    }

    private static String depNoVersion() {
        return "<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-jackson</artifactId></dependency>";
    }

    private static String dependency(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version></dependency>";
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected + "> but found " + result + " in:\n" + actual);
    }
}
