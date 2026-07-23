package com.huawei.clouds.openrewrite.nettycodechttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindNettyCodecHttp41136BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyCodecHttp41136BuildRisks());
    }

    @ParameterizedTest(name = "forbidden downgrade in {0}")
    @MethodSource("downgradeDeclarations")
    void marks42BranchConflictWithoutChangingVersion(String label, org.openrewrite.test.SourceSpecs sources) {
        rewriteRun(sources);
    }

    static Stream<Arguments> downgradeDeclarations() {
        return Stream.of(
                Arguments.of("Maven", xml(UpgradeNettyCodecHttpDependencyTest.pom("4.2.10.Final"), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE), printed);
                            assertTrue(printed.contains("4.2.10.Final"), printed);
                        }))),
                Arguments.of("Groovy", buildGradle(
                        "dependencies { implementation 'io.netty:netty-codec-http:4.2.10.Final' }", source ->
                                source.after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE), printed);
                                    assertTrue(printed.contains("4.2.10.Final"), printed);
                                }))),
                Arguments.of("Kotlin", buildGradleKts(
                        "dependencies { implementation(\"io.netty:netty-codec-http:4.2.10.Final\") }", source ->
                                source.after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE), printed);
                                    assertTrue(printed.contains("4.2.10.Final"), printed);
                                }))));
    }

    @Test
    void resolves42PropertyBeforeMarkingConflict() {
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project(
                "<properties><netty.version>4.2.10.Final</netty.version></properties><dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("${netty.version}") + "</dependencies>"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE), printed);
                    assertTrue(printed.contains("${netty.version}"), printed);
                })));
    }

    @Test
    void selectedProfileDoesNotLeakPrimaryIntoSiblingProfile() {
        String siblingCompanion = "<dependency><groupId>io.netty</groupId><artifactId>netty-buffer</artifactId>" +
                                  "<version>4.1.100.Final</version></dependency>";
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<profiles>" +
                "<profile><id>selected</id><dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + "</dependencies></profile>" +
                "<profile><id>sibling</id><dependencies>" + siblingCompanion + "</dependencies></profile>" +
                "</profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPrimaryMakesProfileCompanionVisible() {
        String profileCompanion = "<dependency><groupId>io.netty</groupId><artifactId>netty-handler</artifactId>" +
                                  "<version>4.1.100.Final</version></dependency>";
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + "</dependencies><profiles>" +
                "<profile><id>runtime</id><dependencies>" + profileCompanion +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.FAMILY),
                                after.printAll()))));
    }

    @Test
    void profilePropertyShadowsRootOwner() {
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project(
                "<properties><netty.version>4.1.100.Final</netty.version></properties><profiles>" +
                "<profile><id>newer</id><properties><netty.version>4.2.10.Final</netty.version></properties>" +
                "<dependencies>" + UpgradeNettyCodecHttpDependencyTest.dep("${netty.version}") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE),
                                after.printAll()))));
    }

    @Test
    void duplicateProfilePropertyIsAnUnresolvedOwner() {
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<profiles><profile><id>duplicate</id>" +
                "<properties><netty.version>4.2.10.Final</netty.version>" +
                "<netty.version>4.1.100.Final</netty.version></properties><dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("${netty.version}") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindNettyCodecHttp41136BuildRisks.OWNER), printed);
                    assertTrue(!printed.contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE), printed);
                })));
    }

    @Test
    void approvedSourcesAndTargetAreCleanBeforeUpgrade() {
        rewriteRun(
                xml(UpgradeNettyCodecHttpDependencyTest.pom("4.1.100.Final"), source -> source.path("old/pom.xml")),
                xml(UpgradeNettyCodecHttpDependencyTest.pom("4.1.136.Final"), source -> source.path("target/pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http:4.1.129.Final' }"));
    }

    @ParameterizedTest(name = "primary risk {0}")
    @MethodSource("primaryRisks")
    void marksExactPrimaryBuildRisks(String label, String dependency, String message) {
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> primaryRisks() {
        return Stream.of(
                Arguments.of("outside", UpgradeNettyCodecHttpDependencyTest.dep("4.1.135.Final"),
                        FindNettyCodecHttp41136BuildRisks.OUTSIDE),
                Arguments.of("range", UpgradeNettyCodecHttpDependencyTest.dep("[4.1,4.2)"),
                        FindNettyCodecHttp41136BuildRisks.OWNER),
                Arguments.of("missing", UpgradeNettyCodecHttpDependencyTest.depWithoutVersion(),
                        FindNettyCodecHttp41136BuildRisks.OWNER),
                Arguments.of("classifier", UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final", "<classifier>tests</classifier>"),
                        FindNettyCodecHttp41136BuildRisks.VARIANT),
                Arguments.of("non-JAR", UpgradeNettyCodecHttpDependencyTest.dep("4.1.118.Final", "<type>zip</type>"),
                        FindNettyCodecHttp41136BuildRisks.VARIANT));
    }

    @ParameterizedTest(name = "companion family {0}")
    @MethodSource("companions")
    void marksNettyFamilySkewWhenPrimaryIsPresent(String artifact, String version) {
        String companion = "<dependency><groupId>io.netty</groupId><artifactId>" + artifact +
                           "</artifactId><version>" + version + "</version></dependency>";
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + companion + "</dependencies>"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.FAMILY), after.printAll()))));
    }

    static Stream<Arguments> companions() {
        return Stream.of(
                Arguments.of("netty-buffer", "4.1.100.Final"),
                Arguments.of("netty-codec", "4.1.129.Final"),
                Arguments.of("netty-handler", "4.1.132.Final"),
                Arguments.of("netty-transport-native-epoll", "4.1.130.Final"),
                Arguments.of("netty-all", "4.1.128.Final"),
                Arguments.of("netty-bom", "4.1.125.Final"));
    }

    @Test
    void alignedCompanionAndUnrelatedStandaloneNettyAreClean() {
        String aligned = "<dependency><groupId>io.netty</groupId><artifactId>netty-buffer</artifactId>" +
                         "<version>4.1.136.Final</version></dependency>";
        String standalone = "<dependency><groupId>io.netty</groupId><artifactId>netty-handler</artifactId>" +
                            "<version>4.1.100.Final</version></dependency>";
        rewriteRun(
                xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + aligned + "</dependencies>"),
                        source -> source.path("aligned/pom.xml")),
                xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" + standalone + "</dependencies>"),
                        source -> source.path("standalone/pom.xml")));
    }

    @Test
    void excludesSeparatelyVersionedTcnativeFromFamilyAlignment() {
        String nativeDependency = "<dependency><groupId>io.netty</groupId><artifactId>netty-tcnative-boringssl-static</artifactId>" +
                                  "<version>2.0.70.Final</version></dependency>";
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + nativeDependency + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void marksMavenShadeRelocationOnlyInScopedProject() {
        String plugin = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>" +
                        "<relocations><relocation><pattern>io.netty</pattern><shadedPattern>hidden.netty</shadedPattern>" +
                        "</relocation></relocations></configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.PACKAGING), after.printAll()))));
    }

    @Test
    void marksDirectProfileBuildPlugin() {
        String plugin = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>" +
                        "<relocations><relocation><pattern>io.netty</pattern><shadedPattern>hidden.netty</shadedPattern>" +
                        "</relocation></relocations></configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeNettyCodecHttpDependencyTest.project("<profiles><profile><id>shaded</id>" +
                "<dependencies>" + UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") +
                "</dependencies>" + plugin + "</profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.PACKAGING),
                                after.printAll()))));
    }

    @Test
    void ignoresNestedBuildAndPluginDependencyLookalikes() {
        String nested = "<build><plugins><plugin><artifactId>outer</artifactId><configuration>" +
                        "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                        "<configuration><pattern>io.netty</pattern></configuration></plugin></plugins></build>" +
                        "</configuration></plugin></plugins></build>";
        String pluginDependency = "<build><plugins><plugin><artifactId>launcher</artifactId><dependencies>" +
                                  "<dependency><groupId>io.netty</groupId><artifactId>maven-shade-plugin</artifactId>" +
                                  "<version>io.netty</version></dependency></dependencies></plugin></plugins></build>";
        rewriteRun(
                xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + "</dependencies>" + nested),
                        source -> source.path("nested/pom.xml")),
                xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + "</dependencies>" +
                        pluginDependency), source -> source.path("plugin-dependency/pom.xml")));
    }

    @Test
    void marksGradleShadowRelocation() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'io.netty:netty-codec-http:4.1.100.Final' }
                shadowJar { relocate 'io.netty', 'hidden.netty' }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.PACKAGING), after.printAll()))));
    }

    @Test
    void arbitraryGradleRelocateCallIsClean() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'io.netty:netty-codec-http:4.1.100.Final' }
                customBundle { relocate 'io.netty', 'hidden.netty' }
                """));
    }

    @ParameterizedTest(name = "non-primary Gradle scope {0}")
    @MethodSource("nonPrimaryGradleScopes")
    void commentsNestedScopesAndLookalikesDoNotActivateBuildAudit(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nonPrimaryGradleScopes() {
        String companion = "dependencies { implementation 'io.netty:netty-buffer:4.1.100.Final' }";
        return Stream.of(
                Arguments.of("comment", "// implementation 'io.netty:netty-codec-http:4.1.100.Final'\n" + companion),
                Arguments.of("buildscript", "buildscript { dependencies { implementation " +
                        "'io.netty:netty-codec-http:4.1.100.Final' } }\n" + companion),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " +
                        "'io.netty:netty-codec-http:4.1.100.Final' } }\n" + companion),
                Arguments.of("group lookalike", "dependencies { implementation " +
                        "'xio.netty:netty-codec-http:4.1.100.Final'; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"),
                Arguments.of("artifact lookalike", "def v = '4.1.100.Final'\ndependencies { implementation " +
                        "\"io.netty:xnetty-codec-http:$v\"; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"),
                Arguments.of("map lookalike", "dependencies { implementation group: 'xio.netty', " +
                        "name: 'netty-codec-http', version: '4.1.100.Final'; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"));
    }

    @Test
    void exactGroovyAndKotlinTemplatesActivateOwnerAudit() {
        rewriteRun(
                buildGradle("""
                        def nettyVersion = '4.1.100.Final'
                        dependencies { implementation "io.netty:netty-codec-http:$nettyVersion" }
                        """, source -> source.path("build.gradle").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.OWNER), after.printAll()))),
                buildGradleKts("""
                        val nettyVersion = "4.1.100.Final"
                        dependencies { implementation("io.netty:netty-codec-http:$nettyVersion") }
                        """, source -> source.path("kotlin/build.gradle.kts").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyCodecHttp41136BuildRisks.OWNER), after.printAll()))));
    }

    @Test
    void marksRfc9112EscapeHatchInMavenAndGradleLaunchConfiguration() {
        String plugin = "<build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><configuration>" +
                        "<argLine>-Dio.netty.handler.codec.http.rfc9112TransferEncoding=false</argLine>" +
                        "</configuration></plugin></plugins></build>";
        rewriteRun(
                xml(UpgradeNettyCodecHttpDependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttpDependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.RFC9112), after.printAll()))),
                buildGradle("""
                        dependencies { implementation 'io.netty:netty-codec-http:4.1.100.Final' }
                        test { jvmArgs '-Dio.netty.handler.codec.http.rfc9112TransferEncoding=false' }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp41136BuildRisks.RFC9112), after.printAll()))));
    }

    @Test
    void markerIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeNettyCodecHttpDependencyTest.pom("4.2.10.Final"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertEquals(1,
                                occurrences(after.printAll(), FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE)))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
