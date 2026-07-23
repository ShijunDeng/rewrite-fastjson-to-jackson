package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class FindLogbackCore1534BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindLogbackCore1534BuildRisks());
    }

    @ParameterizedTest(name = "selected/target {0} is clean")
    @ValueSource(strings = {"1.2.5", "1.2.9", "1.5.34"})
    void selectedAndTargetVersionsAreClean(String version) {
        cleanXml(pom(version));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[1.2.5,1.5.34)", "[1.5,)", "1.+", "latest.release"})
    void marksVersionlessVariableRangeAndDynamicOwners(String version) {
        markedXml(project("<dependencies>" +
                          (version.isEmpty() ? target(null, "") : target(version, "")) +
                          "</dependencies>"), FindLogbackCore1534BuildRisks.OWNER);
    }

    @ParameterizedTest(name = "outside fixed version {0}")
    @ValueSource(strings = {"1.1.11", "1.2.4", "1.2.10", "1.3.0", "1.4.14", "1.5.33"})
    void marksFixedVersionsOutsideWorkbookSelection(String version) {
        markedXml(pom(version), FindLogbackCore1534BuildRisks.OUTSIDE);
    }

    @ParameterizedTest(name = "higher version {0}")
    @ValueSource(strings = {"1.5.35", "1.6.0", "2.0.0", "99.0.0", "999999999999999999999.0"})
    void marksHigherVersionsWithExactNoDowngradePhrase(String version) {
        markedXml(pom(version), FindLogbackCore1534BuildRisks.DOWNGRADE_FORBIDDEN);
        assertTrue(FindLogbackCore1534BuildRisks.DOWNGRADE_FORBIDDEN
                .contains("目标版本冲突（禁止降级）"));
    }

    @Test
    void marksAmbiguousPropertiesButAcceptsExclusiveOwnedTargetProperty() {
        rewriteRun(
                markedXmlSpec(project("<properties><logback.version>1.2.9</logback.version></properties>" +
                        "<dependencies>" + target("${logback.version}", "") +
                        dep("ch.qos.logback", "logback-classic", "${logback.version}") +
                        "</dependencies>"), "shared/pom.xml", FindLogbackCore1534BuildRisks.OWNER),
                markedXmlSpec(project("<properties><logback.version>1.2.9</logback.version>" +
                        "<logback.version>1.2.9</logback.version></properties><dependencies>" +
                        target("${logback.version}", "") + "</dependencies>"),
                        "duplicate/pom.xml", FindLogbackCore1534BuildRisks.OWNER),
                xml(project("<properties><core.version>1.5.34</core.version></properties><dependencies>" +
                        target("${core.version}", "") + "</dependencies>"), source -> source.path("owned/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksClassifierAndNonJarVariants() {
        rewriteRun(
                markedXmlSpec(project("<dependencies>" +
                        target("1.5.34", "<classifier>sources</classifier>") + "</dependencies>"),
                        "classifier/pom.xml", FindLogbackCore1534BuildRisks.VARIANT),
                markedXmlSpec(project("<dependencies>" +
                        target("1.5.34", "<type>test-jar</type>") + "</dependencies>"),
                        "type/pom.xml", FindLogbackCore1534BuildRisks.VARIANT));
    }

    @Test
    void auditsLogbackFamilyWithoutChangingIt() {
        cleanXml(withCompanion("ch.qos.logback", "logback-classic", "1.5.34"));
        rewriteRun(
                markedXmlSpec(withCompanion("ch.qos.logback", "logback-classic", "1.2.9"),
                        "classic/pom.xml", FindLogbackCore1534BuildRisks.FAMILY),
                markedXmlSpec(withCompanion("ch.qos.logback", "logback-access", "2.0.6"),
                        "access/pom.xml", FindLogbackCore1534BuildRisks.FAMILY),
                markedXmlSpec(withCompanion("ch.qos.logback.db", "logback-core-db", "0.1.0"),
                        "db/pom.xml", FindLogbackCore1534BuildRisks.FAMILY));
    }

    @Test
    void auditsSlf4jApiBridgesAndCompetingProviders() {
        cleanXml(withCompanion("org.slf4j", "slf4j-api", "2.0.17"));
        rewriteRun(
                markedXmlSpec(withCompanion("org.slf4j", "slf4j-api", "1.7.36"),
                        "old-api/pom.xml", FindLogbackCore1534BuildRisks.SLF4J),
                markedXmlSpec(withCompanion("org.slf4j", "jul-to-slf4j", "2.0.17"),
                        "bridge/pom.xml", FindLogbackCore1534BuildRisks.SLF4J),
                markedXmlSpec(withCompanion("org.slf4j", "slf4j-simple", "2.0.17"),
                        "simple/pom.xml", FindLogbackCore1534BuildRisks.BINDING_COLLISION),
                markedXmlSpec(withCompanion("org.apache.logging.log4j", "log4j-slf4j2-impl", "2.25.3"),
                        "log4j-provider/pom.xml", FindLogbackCore1534BuildRisks.BINDING_COLLISION));
    }

    @ParameterizedTest(name = "optional integration {0}")
    @ValueSource(strings = {
            "org.codehaus.janino:janino", "org.codehaus.janino:commons-compiler",
            "org.fusesource.jansi:jansi", "org.tukaani:xz",
            "javax.mail:mail", "jakarta.mail:jakarta.mail-api",
            "javax.servlet:javax.servlet-api", "jakarta.servlet:jakarta.servlet-api"
    })
    void marksOptionalIntegrationBoundaries(String coordinate) {
        String[] parts = coordinate.split(":");
        markedXml(withCompanion(parts[0], parts[1], "1.0"), FindLogbackCore1534BuildRisks.OPTIONAL);
    }

    @ParameterizedTest(name = "Java baseline {0}")
    @ValueSource(strings = {"1.8", "8", "9", "10"})
    void marksMavenJavaBaselinesBelowEleven(String version) {
        markedXml(project("<properties><java.version>" + version + "</java.version></properties>" +
                "<dependencies>" + target("1.5.34", "") + "</dependencies>"),
                FindLogbackCore1534BuildRisks.JAVA_BASELINE);
    }

    @Test
    void marksCompilerPluginBaselineAndLeavesJavaElevenClean() {
        markedXml(project("<dependencies>" + target("1.5.34", "") + "</dependencies>" +
                "<build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>" +
                "<configuration><release>${unresolved.release}</release></configuration>" +
                "</plugin></plugins></build>"), FindLogbackCore1534BuildRisks.JAVA_BASELINE);
        cleanXml(project("<properties><java.version>11</java.version></properties><dependencies>" +
                target("1.5.34", "") + "</dependencies>"));
    }

    @ParameterizedTest(name = "packaging plugin {0}")
    @ValueSource(strings = {"maven-shade-plugin", "bnd-maven-plugin", "native-maven-plugin"})
    void marksJpmsOsgiAndMultiReleasePackaging(String artifact) {
        markedXml(project("<dependencies>" + target("1.5.34", "") + "</dependencies><build><plugins>" +
                "<plugin><artifactId>" + artifact + "</artifactId><configuration>" +
                "<relocation>ch.qos.logback</relocation></configuration></plugin>" +
                "</plugins></build>"), FindLogbackCore1534BuildRisks.PACKAGING);
    }

    @Test
    void exercisesGradleOwnerVariantFamilySlf4jJavaAndPackagingBoundaries() {
        rewriteRun(
                markedGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.6.0' }",
                        FindLogbackCore1534BuildRisks.DOWNGRADE_FORBIDDEN),
                markedGradle("def v='1.5.34'\ndependencies { implementation \"ch.qos.logback:logback-core:$v\" }",
                        FindLogbackCore1534BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.5.34:sources' }",
                        FindLogbackCore1534BuildRisks.VARIANT),
                markedGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.5.34'; runtimeOnly 'ch.qos.logback:logback-classic:1.2.9' }",
                        FindLogbackCore1534BuildRisks.FAMILY),
                markedGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.5.34'; implementation 'org.slf4j:slf4j-api:1.7.36' }",
                        FindLogbackCore1534BuildRisks.SLF4J),
                markedGradle("sourceCompatibility = '1.8'\ndependencies { implementation 'ch.qos.logback:logback-core:1.5.34' }",
                        FindLogbackCore1534BuildRisks.JAVA_BASELINE),
                markedGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.5.34' }\n" +
                        "shadowJar { relocate 'ch.qos.logback', 'internal.logging' }",
                        FindLogbackCore1534BuildRisks.PACKAGING));
    }

    @Test
    void exercisesGroovyMapKotlinAndCatalogOwners() {
        rewriteRun(
                markedGradle("dependencies { implementation group: 'ch.qos.logback', name: 'logback-core', version: '1.6.0' }",
                        FindLogbackCore1534BuildRisks.DOWNGRADE_FORBIDDEN),
                markedGradle("dependencies { implementation group: 'ch.qos.logback', name: 'logback-core', version: '1.5.34', classifier: 'tests' }",
                        FindLogbackCore1534BuildRisks.VARIANT),
                markedKotlin("val v=\"1.5.34\"\ndependencies { implementation(\"ch.qos.logback:logback-core:$v\") }",
                        FindLogbackCore1534BuildRisks.OWNER),
                markedKotlin("dependencies { implementation(libs.logback.core) }",
                        FindLogbackCore1534BuildRisks.OWNER),
                text("""
                        [versions]
                        logback = "1.5.34"
                        [libraries]
                        logback-core = { module = "ch.qos.logback:logback-core", version.ref = "logback" }
                        """, source -> source.path("gradle/libs.versions.toml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534BuildRisks.OWNER))));
    }

    @Test
    void ignoresLookalikesNestedOwnersCatalogCommentsAndGeneratedBuilds() {
        rewriteRun(
                buildGradle("dependencies { implementation 'example:logback-core:1.6.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("subprojects { dependencies { implementation 'ch.qos.logback:logback-core:1.6.0' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("def v='1.5.34'\ndependencies { implementation \"xch.qos.logback:logback-core:$v\" }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("# module = \"ch.qos.logback:logback-core\"\n",
                        source -> source.path("gradle/libs.versions.toml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(pom("1.6.0"), source -> source.path("target/generated/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.6.0"), source -> source.path("pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(),
                                FindLogbackCore1534BuildRisks.DOWNGRADE_FORBIDDEN, 1))));
    }

    private static String withCompanion(String group, String artifact, String version) {
        return project("<dependencies>" + target("1.5.34", "") +
                       dep(group, artifact, version) + "</dependencies>");
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return dep("ch.qos.logback", "logback-core", version, metadata);
    }

    private static String dep(String group, String artifact, String version) {
        return dep(group, artifact, version, "");
    }

    private static String dep(String group, String artifact, String version, String metadata) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + metadata + "</dependency>";
    }

    private void markedXml(String pom, String message) {
        rewriteRun(markedXmlSpec(pom, "pom.xml", message));
    }

    private void cleanXml(String pom) {
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private static SourceSpecs markedXmlSpec(String pom, String path, String message) {
        return xml(pom, source -> source.path(path).after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
    }

    private static SourceSpecs markedGradle(String gradle, String message) {
        return buildGradle(gradle, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
    }

    private static SourceSpecs markedKotlin(String gradle, String message) {
        return buildGradleKts(gradle, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("~~("), actual);
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
