package com.huawei.clouds.openrewrite.nettyhandler;

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

class FindNettyHandler41136BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyHandler41136BuildRisks());
    }

    @ParameterizedTest(name = "target conflict {0} in {1}")
    @MethodSource("targetConflictDeclarations")
    void marksTargetConflictsWithoutChangingVersion(String version, String format,
                                                     org.openrewrite.test.SourceSpecs sources) {
        rewriteRun(sources);
    }

    static Stream<Arguments> targetConflictDeclarations() {
        return Stream.of("4.1.137.Final", "4.2.0.Final", "4.2.1.Final", "4.2.10.Final",
                        "4.3.0.Final", "5.0.0.Alpha1")
                .flatMap(version -> {
            String message = conflictMessage(version);
            return Stream.of(
                    Arguments.of(version, "Maven", xml(UpgradeNettyHandlerDependencyTest.pom(version), source ->
                            source.path(version + "/pom.xml").after(actual -> actual).afterRecipe(after -> {
                                String printed = after.printAll();
                                assertTrue(printed.contains(message), printed);
                                assertTrue(printed.contains(version), printed);
                            }))),
                    Arguments.of(version, "Groovy", buildGradle(
                            "dependencies { implementation 'io.netty:netty-handler:" + version + "' }", source ->
                                    source.after(actual -> actual).afterRecipe(after -> {
                                        String printed = after.printAll();
                                        assertTrue(printed.contains(message), printed);
                                        assertTrue(printed.contains(version), printed);
                                    }))),
                    Arguments.of(version, "Kotlin", buildGradleKts(
                            "dependencies { implementation(\"io.netty:netty-handler:" + version + "\") }", source ->
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
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project(
                "<properties><netty.version>" + version + "</netty.version></properties><dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("${netty.version}") + "</dependencies>"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(message), printed);
                    assertTrue(printed.contains("${netty.version}"), printed);
                })));
    }

    static Stream<Arguments> targetConflictProperties() {
        return Stream.of(
                Arguments.of("4.1.137.Final",
                        FindNettyHandler41136BuildRisks.targetConflictMessage("4.1.137.Final")),
                Arguments.of("4.2.0.Final",
                        FindNettyHandler41136BuildRisks.targetConflictMessage("4.2.0.Final")),
                Arguments.of("4.2.1.Final",
                        FindNettyHandler41136BuildRisks.targetConflictMessage("4.2.1.Final")),
                Arguments.of("4.2.10.Final",
                        FindNettyHandler41136BuildRisks.targetConflictMessage("4.2.10.Final")));
    }

    @Test
    void selectedProfileDoesNotLeakIntoSiblingProfile() {
        String siblingCompanion = companion("netty-buffer", "4.1.100.Final");
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<profiles>" +
                "<profile><id>selected</id><dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies></profile>" +
                "<profile><id>sibling</id><dependencies>" + siblingCompanion + "</dependencies></profile>" +
                "</profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPrimaryMakesProfileCompanionVisible() {
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies><profiles>" +
                "<profile><id>runtime</id><dependencies>" + companion("netty-common", "4.1.100.Final") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.FAMILY), after.printAll()))));
    }

    @Test
    void profilePropertyShadowsRootOwner() {
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project(
                "<properties><netty.version>4.1.100.Final</netty.version></properties><profiles>" +
                "<profile><id>newer</id><properties><netty.version>4.2.10.Final</netty.version></properties>" +
                "<dependencies>" + UpgradeNettyHandlerDependencyTest.dep("${netty.version}") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> assertTrue(
                        after.printAll().contains(FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX),
                        after.printAll()))));
    }

    @Test
    void duplicateProfilePropertyIsAnUnresolvedOwner() {
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<profiles><profile><id>duplicate</id>" +
                "<properties><netty.version>4.2.10.Final</netty.version>" +
                "<netty.version>4.1.100.Final</netty.version></properties><dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("${netty.version}") +
                "</dependencies></profile></profiles>"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindNettyHandler41136BuildRisks.OWNER), printed);
                    assertFalse(printed.contains(FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX), printed);
                })));
    }

    @Test
    void approvedSourcesAndTargetAreCleanBeforeUpgrade() {
        rewriteRun(
                xml(UpgradeNettyHandlerDependencyTest.pom("4.1.100.Final"), source -> source.path("old/pom.xml")),
                xml(UpgradeNettyHandlerDependencyTest.pom("4.1.136.Final"), source -> source.path("target/pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-handler:4.1.129.Final' }"));
    }

    @ParameterizedTest(name = "primary risk {0}")
    @MethodSource("primaryRisks")
    void marksExactPrimaryBuildRisks(String label, String dependency, String message) {
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> primaryRisks() {
        return Stream.of(
                Arguments.of("outside", UpgradeNettyHandlerDependencyTest.dep("4.1.135.Final"),
                        FindNettyHandler41136BuildRisks.OUTSIDE),
                Arguments.of("range", UpgradeNettyHandlerDependencyTest.dep("[4.1,4.2)"),
                        FindNettyHandler41136BuildRisks.OWNER),
                Arguments.of("missing", UpgradeNettyHandlerDependencyTest.depWithoutVersion(),
                        FindNettyHandler41136BuildRisks.OWNER),
                Arguments.of("classifier", UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final", "<classifier>tests</classifier>"),
                        FindNettyHandler41136BuildRisks.VARIANT),
                Arguments.of("non-JAR", UpgradeNettyHandlerDependencyTest.dep("4.1.118.Final", "<type>zip</type>"),
                        FindNettyHandler41136BuildRisks.VARIANT));
    }

    @ParameterizedTest(name = "companion family {0}")
    @MethodSource("companions")
    void marksNettyFamilySkewWhenPrimaryIsPresent(String artifact, String version) {
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + companion(artifact, version) +
                "</dependencies>"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.FAMILY), after.printAll()))));
    }

    static Stream<Arguments> companions() {
        return Stream.of(
                Arguments.of("netty-buffer", "4.1.100.Final"),
                Arguments.of("netty-codec", "4.1.129.Final"),
                Arguments.of("netty-codec-http", "4.1.132.Final"),
                Arguments.of("netty-common", "4.1.112.Final"),
                Arguments.of("netty-transport-native-epoll", "4.1.130.Final"),
                Arguments.of("netty-all", "4.1.128.Final"),
                Arguments.of("netty-bom", "4.1.125.Final"));
    }

    @Test
    void alignedCompanionAndStandaloneNettyAreClean() {
        rewriteRun(
                xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                        UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") +
                        companion("netty-buffer", "4.1.136.Final") + "</dependencies>"),
                        source -> source.path("aligned/pom.xml")),
                xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                        companion("netty-handler", "4.1.100.Final") + "</dependencies>"),
                        source -> source.path("standalone/pom.xml")));
    }

    @Test
    void targetBomMakesVersionlessHandlerAndCompanionClean() {
        rewriteRun(
                xml(UpgradeNettyHandlerDependencyTest.project(
                        "<dependencyManagement><dependencies>" +
                        UpgradeNettyHandlerDependencyTest.bom("4.1.136.Final") +
                        "</dependencies></dependencyManagement><dependencies>" +
                        UpgradeNettyHandlerDependencyTest.depWithoutVersion() +
                        companionWithoutVersion("netty-buffer") + "</dependencies>"),
                        source -> source.path("pom.xml")),
                buildGradle("""
                        dependencies {
                            implementation platform('io.netty:netty-bom:4.1.136.Final')
                            implementation 'io.netty:netty-handler'
                            implementation 'io.netty:netty-buffer'
                        }
                        """));
    }

    @ParameterizedTest(name = "BOM no-downgrade conflict {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "4.1.137.Final", "4.2.1.Final", "4.2.10.Final", "5.0.0.Final"
    })
    void marksHigherBomAndVersionlessHandlerWithoutRewriting(String version) {
        rewriteRun(
                xml(UpgradeNettyHandlerDependencyTest.project(
                        "<dependencyManagement><dependencies>" +
                        UpgradeNettyHandlerDependencyTest.bom(version) +
                        "</dependencies></dependencyManagement><dependencies>" +
                        UpgradeNettyHandlerDependencyTest.depWithoutVersion() + "</dependencies>"),
                        source -> source.path("maven/pom.xml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains(FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX),
                                            printed);
                                    assertTrue(printed.contains(version), printed);
                                })),
                buildGradle("dependencies { implementation platform('io.netty:netty-bom:" + version +
                        "'); implementation 'io.netty:netty-handler' }", source ->
                        source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX), printed);
                            assertTrue(printed.contains(version), printed);
                        })));
    }

    @ParameterizedTest(name = "TLS/ALPN integration {0}")
    @MethodSource("tlsIntegrations")
    void marksTlsAndAlpnIntegrations(String label, String dependency) {
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.TLS), after.printAll()))));
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

    @ParameterizedTest(name = "TLS launch property {0}")
    @MethodSource("tlsLaunchSettings")
    void marksTlsLaunchSettings(String key) {
        String maven = UpgradeNettyHandlerDependencyTest.project(
                "<properties><" + key + ">true</" + key + "></properties><dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>");
        rewriteRun(
                xml(maven, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.TLS_LAUNCH),
                                after.printAll()))),
                buildGradle("dependencies { implementation 'io.netty:netty-handler:4.1.100.Final' }\n" +
                        "applicationDefaultJvmArgs = ['-D" + key + "=true']", source ->
                        source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.TLS_LAUNCH),
                                        after.printAll()))));
    }

    static Stream<String> tlsLaunchSettings() {
        return Stream.of(
                "io.netty.handler.ssl.noOpenSsl",
                "io.netty.handler.ssl.openssl.engine",
                "io.netty.handler.ssl.openssl.useTasks",
                "jdk.tls.client.protocols",
                "jdk.tls.namedGroups",
                "javax.net.ssl.sessionCacheSize");
    }

    @Test
    void marksDirectRootAndProfileShadePlugins() {
        String plugin = shadePlugin();
        String executionPlugin = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<executions><execution><configuration><relocations><relocation><pattern>io.netty.handler</pattern>" +
                "</relocation></relocations></configuration></execution></executions></plugin></plugins></build>";
        rewriteRun(
                xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                        UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin),
                        source -> source.path("root/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.PACKAGING), after.printAll()))),
                xml(UpgradeNettyHandlerDependencyTest.project("<profiles><profile><id>shaded</id><dependencies>" +
                        UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin +
                        "</profile></profiles>"), source -> source.path("profile/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyHandler41136BuildRisks.PACKAGING), after.printAll()))),
                xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                        UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>" +
                        executionPlugin), source -> source.path("execution/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyHandler41136BuildRisks.PACKAGING), after.printAll()))));
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
                xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                        UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>" + nested),
                        source -> source.path("nested/pom.xml")),
                xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                        UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>" +
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
        rewriteRun(xml(UpgradeNettyHandlerDependencyTest.project("<dependencies>" +
                UpgradeNettyHandlerDependencyTest.dep("4.1.100.Final") + "</dependencies>" + plugin),
                source -> source.path("pom.xml")));
    }

    @Test
    void marksOnlyTopLevelShadowRelocation() {
        rewriteRun(
                buildGradle("""
                        dependencies { implementation 'io.netty:netty-handler:4.1.100.Final' }
                        shadowJar { relocate 'io.netty', 'hidden.netty' }
                        """, source -> source.path("positive.gradle").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.PACKAGING), after.printAll()))),
                buildGradle("""
                        dependencies { implementation 'io.netty:netty-handler:4.1.100.Final' }
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
                Arguments.of("comment", "// implementation 'io.netty:netty-handler:4.1.100.Final'\n" + companion),
                Arguments.of("buildscript", "buildscript { dependencies { implementation " +
                        "'io.netty:netty-handler:4.1.100.Final' } }\n" + companion),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " +
                        "'io.netty:netty-handler:4.1.100.Final' } }\n" + companion),
                Arguments.of("group lookalike", "dependencies { implementation " +
                        "'xio.netty:netty-handler:4.1.100.Final'; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"),
                Arguments.of("artifact lookalike", "def v = '4.1.100.Final'\ndependencies { implementation " +
                        "\"io.netty:xnetty-handler:$v\"; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"),
                Arguments.of("map lookalike", "dependencies { implementation group: 'xio.netty', " +
                        "name: 'netty-handler', version: '4.1.100.Final'; " +
                        "implementation 'io.netty:netty-buffer:4.1.100.Final' }"));
    }

    @Test
    void exactGroovyAndKotlinTemplatesActivateOwnerAudit() {
        rewriteRun(
                buildGradle("""
                        def nettyVersion = '4.1.100.Final'
                        dependencies { implementation "io.netty:netty-handler:$nettyVersion" }
                        """, source -> source.path("build.gradle").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandler41136BuildRisks.OWNER), after.printAll()))),
                buildGradleKts("""
                        val nettyVersion = "4.1.100.Final"
                        dependencies { implementation("io.netty:netty-handler:$nettyVersion") }
                        """, source -> source.path("kotlin/build.gradle.kts").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindNettyHandler41136BuildRisks.OWNER), after.printAll()))));
    }

    @Test
    void markerIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeNettyHandlerDependencyTest.pom("4.2.10.Final"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertEquals(1,
                                occurrences(after.printAll(),
                                        FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX)))));
    }

    private static String conflictMessage(String version) {
        return FindNettyHandler41136BuildRisks.targetConflictMessage(version);
    }

    private static String companion(String artifact, String version) {
        return "<dependency><groupId>io.netty</groupId><artifactId>" + artifact +
               "</artifactId><version>" + version + "</version></dependency>";
    }

    private static String companionWithoutVersion(String artifact) {
        return "<dependency><groupId>io.netty</groupId><artifactId>" + artifact + "</artifactId></dependency>";
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
