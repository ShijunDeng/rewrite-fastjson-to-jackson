package com.huawei.clouds.openrewrite.nettycodechttp2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindNettyCodecHttp2BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyCodecHttp2BuildRisks());
    }

    @ParameterizedTest(name = "target conflict {0} in {1}")
    @MethodSource("targetConflictDeclarations")
    void marksTargetConflictsWithoutChangingVersion(String version, String format,
                                                     org.openrewrite.test.SourceSpecs sources) {
        rewriteRun(sources);
    }

    static Stream<Arguments> targetConflictDeclarations() {
        return Stream.of("4.2.10.Final", "4.2.12.Final").flatMap(version -> {
            String message = conflictMessage(version);
            return Stream.of(
                    Arguments.of(version, "Maven", xml(UpgradeNettyCodecHttp2DependencyTest.pom(version), source ->
                            source.path(version + "/pom.xml").after(actual -> actual).afterRecipe(after -> {
                                String printed = after.printAll();
                                assertTrue(printed.contains(message), printed);
                                assertTrue(printed.contains(version), printed);
                            }))),
                    Arguments.of(version, "Groovy", buildGradle(
                            "dependencies { implementation 'io.netty:netty-codec-http2:" + version + "' }", source ->
                                    source.after(actual -> actual).afterRecipe(after -> {
                                        String printed = after.printAll();
                                        assertTrue(printed.contains(message), printed);
                                        assertTrue(printed.contains(version), printed);
                                    }))),
                    Arguments.of(version, "Kotlin", buildGradleKts(
                            "dependencies { implementation(\"io.netty:netty-codec-http2:" + version + "\") }", source ->
                                    source.after(actual -> actual).afterRecipe(after -> {
                                        String printed = after.printAll();
                                        assertTrue(printed.contains(message), printed);
                                        assertTrue(printed.contains(version), printed);
                                    }))));
        });
    }

    @ParameterizedTest(name = "resolves target-conflict property {0}")
    @MethodSource("targetConflictProperties")
    void resolvesOwnedPropertyBeforeMarkingConflict(String version, String message) {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project(
                "<properties><netty.version>" + version + "</netty.version></properties><dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("${netty.version}") + "</dependencies>"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(message), printed);
                    assertTrue(printed.contains("${netty.version}"), printed);
                })));
    }

    static Stream<Arguments> targetConflictProperties() {
        return Stream.of(
                Arguments.of("4.2.10.Final", FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_10),
                Arguments.of("4.2.12.Final", FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12));
    }

    @Test
    void selectedProfileDoesNotLeakIntoSiblingProfile() {
        String siblingCompanion = companion("netty-buffer", "4.1.100.Final");
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<profiles>" +
                "<profile><id>selected</id><dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies></profile>" +
                "<profile><id>sibling</id><dependencies>" + siblingCompanion + "</dependencies></profile>" +
                "</profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPrimaryMakesProfileCompanionVisible() {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies><profiles>" +
                "<profile><id>runtime</id><dependencies>" + companion("netty-handler", "4.1.100.Final") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp2BuildRisks.FAMILY), after.printAll()))));
    }

    @Test
    void profilePropertyShadowsRootOwner() {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project(
                "<properties><netty.version>4.1.100.Final</netty.version></properties><profiles>" +
                "<profile><id>newer</id><properties><netty.version>4.2.12.Final</netty.version></properties>" +
                "<dependencies>" + UpgradeNettyCodecHttp2DependencyTest.dep("${netty.version}") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> assertTrue(
                        after.printAll().contains(FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12),
                        after.printAll()))));
    }

    @Test
    void duplicateProfilePropertyIsAnUnresolvedOwner() {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<profiles><profile><id>duplicate</id>" +
                "<properties><netty.version>4.2.12.Final</netty.version>" +
                "<netty.version>4.1.100.Final</netty.version></properties><dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("${netty.version}") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindNettyCodecHttp2BuildRisks.OWNER), printed);
                    assertFalse(printed.contains(FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12), printed);
                })));
    }

    @Test
    void approvedSourcesAndTargetAreCleanBeforeUpgrade() {
        rewriteRun(
                xml(UpgradeNettyCodecHttp2DependencyTest.pom("4.1.100.Final"), source -> source.path("old/pom.xml")),
                xml(UpgradeNettyCodecHttp2DependencyTest.pom("4.1.136.Final"), source -> source.path("target/pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http2:4.1.129.Final' }"));
    }

    @ParameterizedTest(name = "primary risk {0}")
    @MethodSource("primaryRisks")
    void marksExactPrimaryBuildRisks(String label, String dependency, String message) {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> primaryRisks() {
        return Stream.of(
                Arguments.of("outside", UpgradeNettyCodecHttp2DependencyTest.dep("4.1.135.Final"),
                        FindNettyCodecHttp2BuildRisks.OUTSIDE),
                Arguments.of("range", UpgradeNettyCodecHttp2DependencyTest.dep("[4.1,4.2)"),
                        FindNettyCodecHttp2BuildRisks.OWNER),
                Arguments.of("missing", UpgradeNettyCodecHttp2DependencyTest.depWithoutVersion(),
                        FindNettyCodecHttp2BuildRisks.OWNER),
                Arguments.of("classifier", UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final", "<classifier>tests</classifier>"),
                        FindNettyCodecHttp2BuildRisks.VARIANT),
                Arguments.of("non-JAR", UpgradeNettyCodecHttp2DependencyTest.dep("4.1.118.Final", "<type>zip</type>"),
                        FindNettyCodecHttp2BuildRisks.VARIANT));
    }

    @ParameterizedTest(name = "companion family {0}")
    @MethodSource("companions")
    void marksNettyFamilySkewWhenPrimaryIsPresent(String artifact, String version) {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + companion(artifact, version) +
                "</dependencies>"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(FindNettyCodecHttp2BuildRisks.FAMILY), after.printAll()))));
    }

    static Stream<Arguments> companions() {
        return Stream.of(
                Arguments.of("netty-buffer", "4.1.100.Final"),
                Arguments.of("netty-codec", "4.1.129.Final"),
                Arguments.of("netty-codec-http", "4.1.132.Final"),
                Arguments.of("netty-handler", "4.1.112.Final"),
                Arguments.of("netty-transport-native-epoll", "4.1.130.Final"),
                Arguments.of("netty-all", "4.1.128.Final"),
                Arguments.of("netty-bom", "4.1.125.Final"));
    }

    @Test
    void alignedCompanionAndStandaloneNettyAreClean() {
        rewriteRun(
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") +
                        companion("netty-buffer", "4.1.136.Final") + "</dependencies>"),
                        source -> source.path("aligned/pom.xml")),
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                        companion("netty-handler", "4.1.100.Final") + "</dependencies>"),
                        source -> source.path("standalone/pom.xml")));
    }

    @ParameterizedTest(name = "TLS/ALPN integration {0}")
    @MethodSource("tlsIntegrations")
    void marksTlsAndAlpnIntegrations(String label, String dependency) {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp2BuildRisks.TLS), after.printAll()))));
    }

    static Stream<Arguments> tlsIntegrations() {
        return Stream.of(
                Arguments.of("tcnative", "<dependency><groupId>io.netty</groupId>" +
                        "<artifactId>netty-tcnative-boringssl-static</artifactId><version>2.0.70.Final</version></dependency>"),
                Arguments.of("Conscrypt", "<dependency><groupId>org.conscrypt</groupId>" +
                        "<artifactId>conscrypt-openjdk-uber</artifactId><version>2.5.2</version></dependency>"),
                Arguments.of("Jetty ALPN", "<dependency><groupId>org.eclipse.jetty.alpn</groupId>" +
                        "<artifactId>alpn-api</artifactId><version>1.1.3.v20160715</version></dependency>"));
    }

    @Test
    void marksDirectRootAndProfileShadePlugins() {
        String plugin = shadePlugin();
        String executionPlugin = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<executions><execution><configuration><relocations><relocation><pattern>io.netty.handler</pattern>" +
                "</relocation></relocations></configuration></execution></executions></plugin></plugins></build>";
        rewriteRun(
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin),
                        source -> source.path("root/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindNettyCodecHttp2BuildRisks.PACKAGING), after.printAll()))),
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<profiles><profile><id>shaded</id><dependencies>" +
                        UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin +
                        "</profile></profiles>"), source -> source.path("profile/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyCodecHttp2BuildRisks.PACKAGING), after.printAll()))),
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies>" +
                        executionPlugin), source -> source.path("execution/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyCodecHttp2BuildRisks.PACKAGING), after.printAll()))));
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
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies>" + nested),
                        source -> source.path("nested/pom.xml")),
                xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                        UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies>" +
                        pluginDependency), source -> source.path("plugin-dependency/pom.xml")));
    }

    @Test
    void shadePluginCommentsAndUnrelatedNotesDoNotCreatePackagingRisk() {
        String plugin = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                        "<!-- old example: relocate io.netty -->" +
                        "<configuration><note>do not relocate io.netty here</note></configuration>" +
                        "<dependencies><dependency><groupId>io.netty</groupId><artifactId>netty-buffer</artifactId>" +
                        "<version>4.1.100.Final</version></dependency></dependencies>" +
                        "</plugin></plugins></build>";
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.project("<dependencies>" +
                UpgradeNettyCodecHttp2DependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin),
                source -> source.path("pom.xml")));
    }

    @Test
    void marksOnlyTopLevelShadowRelocation() {
        rewriteRun(
                buildGradle("""
                        dependencies { implementation 'io.netty:netty-codec-http2:4.1.100.Final' }
                        shadowJar { relocate 'io.netty', 'hidden.netty' }
                        """, source -> source.path("positive.gradle").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp2BuildRisks.PACKAGING), after.printAll()))),
                buildGradle("""
                        dependencies { implementation 'io.netty:netty-codec-http2:4.1.100.Final' }
                        customBundle { relocate 'io.netty', 'hidden.netty' }
                        """, source -> source.path("negative.gradle")));
    }

    @ParameterizedTest(name = "non-primary Gradle scope {0}")
    @MethodSource("nonPrimaryGradleScopes")
    void commentsNestedScopesAndLookalikesDoNotActivateAudit(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nonPrimaryGradleScopes() {
        String companion = "dependencies { implementation 'io.netty:netty-buffer:4.1.100.Final' }";
        return Stream.of(
                Arguments.of("comment", "// implementation 'io.netty:netty-codec-http2:4.1.100.Final'\n" + companion),
                Arguments.of("buildscript", "buildscript { dependencies { implementation " +
                        "'io.netty:netty-codec-http2:4.1.100.Final' } }\n" + companion),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " +
                        "'io.netty:netty-codec-http2:4.1.100.Final' } }\n" + companion),
                Arguments.of("group lookalike", "dependencies { implementation " +
                        "'xio.netty:netty-codec-http2:4.1.100.Final'; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"),
                Arguments.of("artifact lookalike", "def v = '4.1.100.Final'\ndependencies { implementation " +
                        "\"io.netty:xnetty-codec-http2:$v\"; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"),
                Arguments.of("map lookalike", "dependencies { implementation group: 'xio.netty', " +
                        "name: 'netty-codec-http2', version: '4.1.100.Final'; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"));
    }

    @Test
    void exactGroovyAndKotlinTemplatesActivateOwnerAudit() {
        rewriteRun(
                buildGradle("""
                        def nettyVersion = '4.1.100.Final'
                        dependencies { implementation "io.netty:netty-codec-http2:$nettyVersion" }
                        """, source -> source.path("build.gradle").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyCodecHttp2BuildRisks.OWNER), after.printAll()))),
                buildGradleKts("""
                        val nettyVersion = "4.1.100.Final"
                        dependencies { implementation("io.netty:netty-codec-http2:$nettyVersion") }
                        """, source -> source.path("kotlin/build.gradle.kts").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyCodecHttp2BuildRisks.OWNER), after.printAll()))));
    }

    @Test
    void markerIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeNettyCodecHttp2DependencyTest.pom("4.2.12.Final"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertEquals(1,
                                occurrences(after.printAll(), FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12)))));
    }

    private static String conflictMessage(String version) {
        return "4.2.10.Final".equals(version) ? FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_10 :
                FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12;
    }

    private static String companion(String artifact, String version) {
        return "<dependency><groupId>io.netty</groupId><artifactId>" + artifact +
               "</artifactId><version>" + version + "</version></dependency>";
    }

    private static String shadePlugin() {
        return "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>" +
               "<relocations><relocation><pattern>io.netty</pattern><shadedPattern>hidden.netty</shadedPattern>" +
               "</relocation></relocations></configuration></plugin></plugins></build>";
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
