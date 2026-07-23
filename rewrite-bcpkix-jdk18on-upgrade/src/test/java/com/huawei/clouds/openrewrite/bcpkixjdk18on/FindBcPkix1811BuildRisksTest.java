package com.huawei.clouds.openrewrite.bcpkixjdk18on;

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

class FindBcPkix1811BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBcPkix1811BuildRisks());
    }

    @ParameterizedTest(name = "selected or target version {0}")
    @ValueSource(strings = {"1.74", "1.75", "1.81.1"})
    void selectedAndTargetVersionsAreNotBuildRisks(String version) {
        cleanXml(pom(version));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[1.74,1.81.1)", "[1.80,)", "1.+", "+", "latest.release"})
    void marksVersionlessExternalDynamicAndRangeOwners(String version) {
        markedXml(project("<dependencies>" + (version.isEmpty() ? target(null, "") : target(version, "")) +
                          "</dependencies>"), FindBcPkix1811BuildRisks.OWNER);
    }

    @ParameterizedTest(name = "outside version {0}")
    @ValueSource(strings = {"1.70", "1.73", "1.76", "1.77", "1.78", "1.81"})
    void marksFixedVersionsOutsideSelectedSet(String version) {
        markedXml(pom(version), FindBcPkix1811BuildRisks.OUTSIDE);
    }

    @ParameterizedTest(name = "higher version {0} is never downgraded")
    @ValueSource(strings = {"1.81.2", "1.82", "1.83", "1.84", "2.0.0",
            "999999999999999999999.0"})
    void marksHigherVersionsAsNoDowngradeConflicts(String version) {
        markedXml(pom(version), FindBcPkix1811BuildRisks.DOWNGRADE_FORBIDDEN);
    }

    @Test
    void marksSharedDuplicateAndProfileShadowedPropertyOwners() {
        rewriteRun(
                markedXmlSpec(project("<properties><bc.version>1.75</bc.version></properties><dependencies>" +
                        target("${bc.version}", "") + dep("org.bouncycastle", "bcutil-jdk18on", "${bc.version}") +
                        "</dependencies>"), "shared/pom.xml", FindBcPkix1811BuildRisks.OWNER),
                markedXmlSpec(project("<properties><bc.version>1.75</bc.version><bc.version>1.75</bc.version></properties>" +
                        "<dependencies>" + target("${bc.version}", "") + "</dependencies>"),
                        "duplicate/pom.xml", FindBcPkix1811BuildRisks.OWNER),
                markedXmlSpec(project("<properties><bc.version>1.75</bc.version></properties><dependencies>" +
                        target("${bc.version}", "") + "</dependencies><profiles><profile><id>p</id><properties>" +
                        "<bc.version>1.74</bc.version></properties></profile></profiles>"),
                        "shadow/pom.xml", FindBcPkix1811BuildRisks.OWNER));
    }

    @Test
    void exclusiveLiteralPropertyResolvesWithoutMarker() {
        cleanXml(project("<properties><bc.version>1.81.1</bc.version></properties><dependencies>" +
                         target("${bc.version}", "") + "</dependencies>"));
    }

    @Test
    void marksMavenClassifierAndNonJarVariants() {
        rewriteRun(
                markedXmlSpec(project("<dependencies>" + target("1.81.1", "<classifier>sources</classifier>") +
                        "</dependencies>"), "classifier/pom.xml", FindBcPkix1811BuildRisks.VARIANT),
                markedXmlSpec(project("<dependencies>" + target("1.81.1", "<type>zip</type>") +
                        "</dependencies>"), "zip/pom.xml", FindBcPkix1811BuildRisks.VARIANT));
    }

    @ParameterizedTest(name = "family skew {0}")
    @ValueSource(strings = {"bc-bom", "bcprov-jdk18on", "bcutil-jdk18on", "bcpg-jdk18on",
            "bctls-jdk18on", "bcmail-jdk18on"})
    void marksJdk18onFamilySkew(String artifact) {
        markedXml(withCompanion(artifact, "1.81"), FindBcPkix1811BuildRisks.FAMILY);
    }

    @Test
    void alignedJdk18onFamilyIsNoop() {
        cleanXml(withCompanion("bcutil-jdk18on", "1.81.1"));
    }

    @Test
    void bcprov184UserTargetIsConflictMarkOnlyAndNeverChanged() {
        markedXml(withCompanion("bcprov-jdk18on", "1.84"), FindBcPkix1811BuildRisks.BCPROV_184_CONFLICT);
    }

    @ParameterizedTest(name = "provider collision {0}")
    @ValueSource(strings = {"bcpkix-jdk14", "bcpkix-jdk15on", "bcpkix-jdk15to18", "bcpkix-lts8on",
            "bcpkix-fips", "bcprov-jdk14", "bcprov-jdk15on", "bcprov-jdk15to18", "bcprov-lts8on",
            "bcprov-ext-jdk15on", "bcprov-ext-jdk15to18", "bcprov-ext-jdk18on", "bc-fips"})
    void marksProviderLineageCollisions(String artifact) {
        markedXml(withCompanion(artifact, "1.81"), FindBcPkix1811BuildRisks.PROVIDER_COLLISION);
    }

    @ParameterizedTest(name = "signed-provider packaging {0}")
    @ValueSource(strings = {"maven-shade-plugin", "bnd-maven-plugin", "native-maven-plugin"})
    void marksPackagingThatMentionsBouncyCastle(String plugin) {
        String build = "<build><plugins><plugin><groupId>x</groupId><artifactId>" + plugin +
                "</artifactId><configuration><relocation>org.bouncycastle</relocation>" +
                "</configuration></plugin></plugins></build>";
        markedXml(project("<dependencies>" + target("1.81.1", "") + "</dependencies>" + build),
                FindBcPkix1811BuildRisks.PACKAGING);
    }

    @Test
    void marksDirectProfileBuildPackagingButNotArbitraryNestedBuild() {
        String profileBuild = "<profiles><profile><id>crypto</id><dependencies>" + target("1.81.1", "") +
                "</dependencies><build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><relocation>org.bouncycastle</relocation></configuration>" +
                "</plugin></plugins></build></profile></profiles>";
        markedXml(project(profileBuild), FindBcPkix1811BuildRisks.PACKAGING);

        String nested = "<dependencies>" + target("1.81.1", "") + "</dependencies><configuration>" +
                "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><relocation>org.bouncycastle</relocation></configuration>" +
                "</plugin></plugins></build></configuration>";
        cleanXml(project(nested));
    }

    @Test
    void unrelatedPackagingIsNoop() {
        cleanXml(project("<dependencies>" + target("1.81.1", "") + "</dependencies><build><plugins><plugin>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration><relocation>com.example</relocation>" +
                "</configuration></plugin></plugins></build>"));
    }

    @Test
    void selectedProfileDoesNotInspectSiblingButRootInspectsProfiles() {
        cleanXml(project("<profiles><profile><id>selected</id><dependencies>" + target("1.81.1", "") +
                "</dependencies></profile><profile><id>sibling</id><dependencies>" +
                dep("org.bouncycastle", "bcutil-jdk18on", "1.81") +
                "</dependencies></profile></profiles>"));
        markedXml(project("<dependencies>" + target("1.81.1", "") + "</dependencies><profiles><profile><id>it</id>" +
                "<dependencies>" + dep("org.bouncycastle", "bcutil-jdk18on", "1.81") +
                "</dependencies></profile></profiles>"), FindBcPkix1811BuildRisks.FAMILY);
    }

    @Test
    void marksGradleOwnerVariantFamilyCollisionCatalogBomAndPackaging() {
        rewriteRun(
                markedGradle("dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on:1.83' }",
                        FindBcPkix1811BuildRisks.DOWNGRADE_FORBIDDEN),
                markedGradle("def v='1.81.1'\ndependencies { implementation \"org.bouncycastle:bcpkix-jdk18on:$v\" }",
                        FindBcPkix1811BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on:1.81.1:sources' }",
                        FindBcPkix1811BuildRisks.VARIANT),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on:1.81.1'; runtimeOnly 'org.bouncycastle:bcutil-jdk18on:1.81' }",
                        FindBcPkix1811BuildRisks.FAMILY),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on:1.81.1'; runtimeOnly 'org.bouncycastle:bcpkix-jdk15on:1.70' }",
                        FindBcPkix1811BuildRisks.PROVIDER_COLLISION),
                markedGradle("dependencies { implementation libs.bouncycastle.bcpkix.jdk18on }",
                        FindBcPkix1811BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on'; implementation platform('org.bouncycastle:bc-bom:1.81') }",
                        FindBcPkix1811BuildRisks.FAMILY),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on:1.81.1' }\n" +
                        "shadowJar { relocate 'org.bouncycastle', 'internal.bc' }", FindBcPkix1811BuildRisks.PACKAGING));
    }

    @Test
    void marksGroovyMapsAndKotlinDynamicCatalogAndFamily() {
        rewriteRun(
                markedGradle("dependencies { implementation group: 'org.bouncycastle', name: 'bcpkix-jdk18on', version: '1.83' }",
                        FindBcPkix1811BuildRisks.DOWNGRADE_FORBIDDEN),
                markedGradle("dependencies { implementation([group: 'org.bouncycastle', name: 'bcpkix-jdk18on']) }",
                        FindBcPkix1811BuildRisks.OWNER),
                markedGradle("dependencies { implementation group: 'org.bouncycastle', name: 'bcpkix-jdk18on', version: '1.81.1', classifier: 'tests' }",
                        FindBcPkix1811BuildRisks.VARIANT),
                markedKotlin("val v=\"1.81.1\"\ndependencies { implementation(\"org.bouncycastle:bcpkix-jdk18on:$v\") }",
                        FindBcPkix1811BuildRisks.OWNER),
                markedKotlin("dependencies { implementation(libs.bouncycastle.bcpkix.jdk18on) }",
                        FindBcPkix1811BuildRisks.OWNER),
                markedKotlin("dependencies { implementation(\"org.bouncycastle:bcpkix-jdk18on:1.81.1\"); runtimeOnly(\"org.bouncycastle:bcutil-jdk18on:1.81\") }",
                        FindBcPkix1811BuildRisks.FAMILY));
    }

    @Test
    void dynamicCoordinatesRequireExactLeadingCoordinatePrefix() {
        rewriteRun(
                markedGradle("def v='1.81.1'\ndependencies { implementation \"  org.bouncycastle:bcpkix-jdk18on:$v\" }",
                        FindBcPkix1811BuildRisks.OWNER),
                buildGradle("def v='1.81.1'\ndependencies { implementation \"xorg.bouncycastle:bcpkix-jdk18on:$v\" }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradleKts("val v=\"1.81.1\"\ndependencies { implementation(\"xorg.bouncycastle:bcpkix-jdk18on:$v\") }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksExactVersionCatalogDefinitionsAndIgnoresCommentsOrLookalikes() {
        rewriteRun(
                text("""
                        [libraries]
                        bcpkix = { module = "org.bouncycastle:bcpkix-jdk18on", version.ref = "bc" }
                        """, source -> source.path("gradle/libs.versions.toml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindBcPkix1811BuildRisks.OWNER))),
                text("""
                        # module = "org.bouncycastle:bcpkix-jdk18on"
                        [libraries]
                        docs = { module = "example:bcpkix-jdk18on", version = "1.75" }
                        """, source -> source.path("gradle/libs.versions.toml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void noPrimaryNestedLookalikesAndGeneratedBuildsAreNoop() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.bouncycastle:bcutil-jdk18on:1.81' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("subprojects { dependencies { implementation 'org.bouncycastle:bcpkix-jdk18on:1.83' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:bcpkix-jdk18on:1.83' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(pom("1.83"), source -> source.path("target/generated/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.83"), source -> source.path("pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(),
                                FindBcPkix1811BuildRisks.DOWNGRADE_FORBIDDEN, 1))));
    }

    private static String withCompanion(String artifact, String version) {
        return project("<dependencies>" + target("1.81.1", "") +
                       dep("org.bouncycastle", artifact, version) + "</dependencies>");
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return dep("org.bouncycastle", "bcpkix-jdk18on", version, metadata);
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
