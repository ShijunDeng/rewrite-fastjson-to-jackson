package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeTwelveMonkeysJpegDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedTwelveMonkeysJpegDependency());
    }

    @Test
    void upgradesTheExactWorkbookRow() {
        rewriteRun(xml(pom("3.9.3"), pom("3.12.0"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("3.9.3") + "</dependencies></dependencyManagement>" +
                        "<profiles><profile><id>jpeg</id><dependencyManagement><dependencies>" + dep("3.9.3") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("3.12.0") + "</dependencies></dependencyManagement>" +
                        "<profiles><profile><id>jpeg</id><dependencyManagement><dependencies>" + dep("3.12.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesAnExclusivePropertyAndRespectsProfileOverride() {
        rewriteRun(xml(
                project("<properties><tm.version>3.8.3</tm.version></properties><profiles><profile><id>jpeg</id>" +
                        "<properties><tm.version>3.9.3</tm.version></properties><dependencies>" + dep("${tm.version}") +
                        "</dependencies></profile></profiles>"),
                project("<properties><tm.version>3.8.3</tm.version></properties><profiles><profile><id>jpeg</id>" +
                        "<properties><tm.version>3.12.0</tm.version></properties><dependencies>" + dep("${tm.version}") +
                        "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void preservesAPropertySharedOutsideTheTargetDependency() {
        rewriteRun(xml(project("<properties><tm.version>3.9.3</tm.version></properties><dependencies>" +
                dep("${tm.version}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId>" +
                "<version>${tm.version}</version></dependency></dependencies>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.9", "3.9.2", "3.9.4", "3.10.0", "3.11.0", "3.12.0", "4.0.0", "[3.9,4)", "3.+"})
    void doesNotWidenTheWorkbookSelection(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void protectsVariantsVersionlessAndPluginDependencies() {
        rewriteRun(
                xml(project("<dependencies>" + dep("3.9.3", "<classifier>tests</classifier>") +
                        dep("3.9.3", "<type>zip</type>") +
                        "<dependency><groupId>com.twelvemonkeys.imageio</groupId><artifactId>imageio-jpeg</artifactId></dependency>" +
                        "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><dependencies>" +
                        dep("3.9.3") + "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @Test
    void upgradesRealGradleGroovyAndKotlinShapes() {
        // ethereum-lists/chains@30f9d5450c836e2d8e64f0e886c1489e52f54bcd, processor/build.gradle
        rewriteRun(
                buildGradle("dependencies { implementation \"com.twelvemonkeys.imageio:imageio-jpeg:3.9.3\" }",
                        "dependencies { implementation \"com.twelvemonkeys.imageio:imageio-jpeg:3.12.0\" }",
                        source -> source.path("processor/build.gradle")),
                // sksamuel/scrimage@a922d935b182b526803f9a8902cba5a0dbeeca06, scrimage-core/build.gradle.kts
                buildGradleKts("dependencies { implementation(\"com.twelvemonkeys.imageio:imageio-jpeg:3.9.3\") }",
                        "dependencies { implementation(\"com.twelvemonkeys.imageio:imageio-jpeg:3.12.0\") }"));
    }

    @Test
    void upgradesGroovyMapNotations() {
        rewriteRun(
                buildGradle("dependencies { runtimeOnly group: 'com.twelvemonkeys.imageio', name: 'imageio-jpeg', version: '3.9.3' }",
                        "dependencies { runtimeOnly group: 'com.twelvemonkeys.imageio', name: 'imageio-jpeg', version: '3.12.0' }"),
                buildGradle("dependencies { implementation([group: 'com.twelvemonkeys.imageio', name: 'imageio-jpeg', version: '3.9.3']) }",
                        "dependencies { implementation([group: 'com.twelvemonkeys.imageio', name: 'imageio-jpeg', version: '3.12.0']) }"));
    }

    @Test
    void rejectsNestedGradleOwnersVariantsCatalogsAndVariables() {
        String coordinate = "'com.twelvemonkeys.imageio:imageio-jpeg:3.9.3'";
        rewriteRun(
                buildGradle("buildscript { dependencies { classpath " + coordinate + " } }"),
                buildGradle("subprojects { dependencies { implementation " + coordinate + " } }"),
                buildGradle("project(':app') { dependencies { implementation " + coordinate + " } }"),
                buildGradle("dependencies { constraints { implementation " + coordinate + " } }"),
                buildGradle("dependencies { implementation 'com.twelvemonkeys.imageio:imageio-jpeg:3.9.3:tests' }"),
                buildGradle("dependencies { implementation 'com.twelvemonkeys.imageio:imageio-jpeg:3.9.3@zip' }"),
                buildGradle("dependencies { implementation libs.twelvemonkeys.jpeg }"),
                buildGradle("dependencies { implementation \"com.twelvemonkeys.imageio:imageio-jpeg:$tmVersion\" }"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target", "build", "generated", "generatedSources", "installation", "INSTALL-cache", ".gradle", ".m2", "vendor"})
    void ignoresGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(pom("3.9.3"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void processesGeneratedOrInstallLeafNamesAndIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.9.3' }",
                        "dependencies { runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.12.0' }",
                        source -> source.path("generated.gradle")));
    }

    @Test
    void publicAndRecommendedRecipesAreDiscoverableAndStrictFirst() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.twelvemonkeysjpeg").build();
        var upgrade = environment.activateRecipes("com.huawei.clouds.openrewrite.twelvemonkeysjpeg.UpgradeTwelveMonkeysImageIoJpegTo3_12_0");
        var migrate = environment.activateRecipes("com.huawei.clouds.openrewrite.twelvemonkeysjpeg.MigrateTwelveMonkeysImageIoJpegTo3_12_0");
        assertEquals(1, upgrade.getRecipeList().size());
        assertEquals(UpgradeSelectedTwelveMonkeysJpegDependency.class, upgrade.getRecipeList().get(0).getClass());
        assertEquals("com.huawei.clouds.openrewrite.twelvemonkeysjpeg.UpgradeTwelveMonkeysImageIoJpegTo3_12_0", migrate.getRecipeList().get(0).getName());
    }

    static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>jpeg-client</artifactId><version>1</version>" + body + "</project>";
    }

    static String dep(String version) {
        return dep(version, "");
    }

    static String dep(String version, String extra) {
        return "<dependency><groupId>com.twelvemonkeys.imageio</groupId><artifactId>imageio-jpeg</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }
}
