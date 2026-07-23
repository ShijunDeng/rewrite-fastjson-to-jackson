package com.huawei.clouds.openrewrite.nettyhandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeNettyHandlerDependencyTest implements RewriteTest {
    private static final String[] APPROVED = {
            "4.1.49.Final", "4.1.63.Final", "4.1.100.Final", "4.1.101.Final", "4.1.107.Final",
            "4.1.108.Final", "4.1.109.Final", "4.1.115.Final", "4.1.118.Final", "4.1.119.Final",
            "4.1.121.Final", "4.1.124.Final", "4.1.125.Final", "4.1.126.Final", "4.1.127.Final",
            "4.1.128.Final", "4.1.129.Final", "4.1.130.Final", "4.1.132.Final", "4.1.133.Final"
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNettyHandlerDependency());
    }

    @ParameterizedTest(name = "approved 4.1 source {0}")
    @ValueSource(strings = {
            "4.1.49.Final", "4.1.63.Final", "4.1.100.Final", "4.1.101.Final", "4.1.107.Final",
            "4.1.108.Final", "4.1.109.Final", "4.1.115.Final", "4.1.118.Final", "4.1.119.Final",
            "4.1.121.Final", "4.1.124.Final", "4.1.125.Final", "4.1.126.Final", "4.1.127.Final",
            "4.1.128.Final", "4.1.129.Final", "4.1.130.Final", "4.1.132.Final", "4.1.133.Final"
    })
    void upgradesEveryApproved41Source(String version) {
        rewriteRun(xml(pom(version), pom(NettyHandlerSupport.TARGET), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistSeparatesTargetConflictsFromAutoSources() {
        assertEquals(Set.of(APPROVED), NettyHandlerSupport.AUTO_SOURCES);
        assertEquals(Set.of("4.2.1.Final", "4.2.10.Final"),
                NettyHandlerSupport.WORKBOOK_SOURCES.stream()
                        .filter(NettyHandlerSupport::higherThanTarget).collect(java.util.stream.Collectors.toSet()));
        assertEquals(22, NettyHandlerSupport.WORKBOOK_SOURCES.size());
    }

    @ParameterizedTest(name = "never downgrades {0}")
    @ValueSource(strings = {
            "4.1.137.Final", "4.2.0.Final", "4.2.1.Final", "4.2.10.Final", "4.3.0.Final",
            "5.0.0.Alpha1"
    })
    void neverDowngradesHigherSources(String version) {
        rewriteRun(xml(pom(version), source -> source.path(version + "/pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-handler:" + version + "' }"),
                buildGradleKts("dependencies { implementation(\"io.netty:netty-handler:" + version + "\") }"));
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><netty.version>4.1.100.Final</netty.version></properties><dependencies>" +
                        dep("${netty.version}") + "</dependencies>"),
                project("<properties><netty.version>4.1.136.Final</netty.version></properties><dependencies>" +
                        dep("${netty.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "target-conflict property {0}")
    @ValueSource(strings = {"4.2.1.Final", "4.2.10.Final"})
    void doesNotRewriteTargetConflictProperty(String version) {
        rewriteRun(xml(project("<properties><netty.version>" + version +
                "</netty.version></properties><dependencies>" + dep("${netty.version}") +
                "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(
                project("<properties><v>4.2.10.Final</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>4.1.125.Final</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"),
                project("<properties><v>4.2.10.Final</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>4.1.136.Final</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous owner {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared companion", project("<properties><v>4.1.100.Final</v></properties><dependencies>" +
                        dep("${v}") + "<dependency><groupId>io.netty</groupId><artifactId>netty-codec-http</artifactId>" +
                        "<version>${v}</version></dependency></dependencies>")),
                Arguments.of("shared build", project("<properties><v>4.1.118.Final</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project("<properties><v>4.1.125.Final</v><v>4.1.125.Final</v></properties>" +
                        "<dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>4.1.129.Final</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><x value=\"${v}\"/>")),
                Arguments.of("profile shadow", project("<properties><v>4.1.130.Final</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><profiles><profile><id>x</id><properties>" +
                        "<v>4.1.132.Final</v></properties></profile></profiles>")));
    }

    @Test
    void upgradesGroovyStringMapAndMapLiteralNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.netty:netty-handler:4.1.100.Final' }",
                        "dependencies { implementation 'io.netty:netty-handler:4.1.136.Final' }"),
                buildGradle("dependencies { runtimeOnly group: 'io.netty', name: 'netty-handler', version: '4.1.128.Final', transitive: false }",
                        "dependencies { runtimeOnly group: 'io.netty', name: 'netty-handler', version: '4.1.136.Final', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'io.netty', name: 'netty-handler', version: '4.1.132.Final']) }",
                        "dependencies { implementation([group: 'io.netty', name: 'netty-handler', version: '4.1.136.Final']) }"));
    }

    @ParameterizedTest(name = "Groovy source {0}")
    @ValueSource(strings = {
            "4.1.49.Final", "4.1.63.Final", "4.1.100.Final", "4.1.101.Final", "4.1.107.Final",
            "4.1.108.Final", "4.1.109.Final", "4.1.115.Final", "4.1.118.Final", "4.1.119.Final",
            "4.1.121.Final", "4.1.124.Final", "4.1.125.Final", "4.1.126.Final", "4.1.127.Final",
            "4.1.128.Final", "4.1.129.Final", "4.1.130.Final", "4.1.132.Final", "4.1.133.Final"
    })
    void upgradesEveryApprovedSourceInGroovy(String version) {
        rewriteRun(buildGradle("dependencies { implementation 'io.netty:netty-handler:" + version + "' }",
                "dependencies { implementation 'io.netty:netty-handler:4.1.136.Final' }"));
    }

    @Test
    void upgradesKotlinGradleNotation() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"io.netty:netty-handler:4.1.130.Final\") }",
                "dependencies { implementation(\"io.netty:netty-handler:4.1.136.Final\") }"));
    }

    @ParameterizedTest(name = "nested Gradle scope {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'io.netty:netty-handler:4.1.100.Final'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom DSL", "company { dependencies { implementation " + d + " } }"));
    }

    @ParameterizedTest(name = "out-of-scope fixed version {0}")
    @ValueSource(strings = {"4.1.48.Final", "4.1.50.Final", "4.1.62.Final", "4.1.64.Final",
            "4.1.99.Final", "4.1.102.Final", "4.1.110.Final", "4.1.111.Final", "4.1.112.Final",
            "4.1.113.Final", "4.1.114.Final", "4.1.116.Final", "4.1.117.Final", "4.1.120.Final",
            "4.1.122.Final", "4.1.123.Final", "4.1.131.Final", "4.1.134.Final", "4.1.135.Final",
            "4.1.137.Final", "4.2.0.Final", "5.0.0.Alpha1"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned version {0}")
    @ValueSource(strings = {"${netty.version}", "[4.1,4.2)", "[4.1,)", "4.1.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedVariantsAndPluginDependenciesAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"),
                        source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("4.1.100.Final", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("4.1.118.Final", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>p</artifactId><dependencies>" +
                        dep("4.1.125.Final") + "</dependencies></plugin></plugins></build>"),
                        source -> source.path("plugin/pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-handler:4.1.129.Final:tests' }"),
                buildGradle("dependencies { implementation 'io.netty:netty-handler:4.1.130.Final@zip' }"));
    }

    @Test
    void upgradesLocalMavenBomThatOwnsVersionlessHandler() {
        String before = project("<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() + "</dependencies>");
        String after = project("<dependencyManagement><dependencies>" + bom("4.1.136.Final") +
                "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() + "</dependencies>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusivePropertyOwnedByBomAndHandler() {
        String before = project("<properties><netty.version>4.1.119.Final</netty.version></properties>" +
                "<dependencyManagement><dependencies>" + bom("${netty.version}") +
                "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() + "</dependencies>");
        String after = project("<properties><netty.version>4.1.136.Final</netty.version></properties>" +
                "<dependencyManagement><dependencies>" + bom("${netty.version}") +
                "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() + "</dependencies>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void doesNotTouchUnrelatedBomWithoutHandler() {
        rewriteRun(xml(project("<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                "</dependencies></dependencyManagement>"), source -> source.path("pom.xml")));
    }

    @Test
    void doesNotGuessAmongMultipleRootBomOwners() {
        rewriteRun(xml(project("<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                bom("4.1.118.Final") + "</dependencies></dependencyManagement><dependencies>" +
                depWithoutVersion() + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesOnlyProfileBomWithLocalHandler() {
        String before = project("<profiles>" +
                profile("selected", "<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>") +
                profile("sibling", "<dependencyManagement><dependencies>" + bom("4.1.118.Final") +
                        "</dependencies></dependencyManagement>") +
                "</profiles>");
        String after = project("<profiles>" +
                profile("selected", "<dependencyManagement><dependencies>" + bom("4.1.136.Final") +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>") +
                profile("sibling", "<dependencyManagement><dependencies>" + bom("4.1.118.Final") +
                        "</dependencies></dependencyManagement>") +
                "</profiles>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesIndependentProfileOwnedBoms() {
        String before = project("<profiles>" +
                profile("one", "<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>") +
                profile("two", "<dependencyManagement><dependencies>" + bom("4.1.118.Final") +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>") +
                "</profiles>");
        String after = project("<profiles>" +
                profile("one", "<dependencyManagement><dependencies>" + bom("4.1.136.Final") +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>") +
                profile("two", "<dependencyManagement><dependencies>" + bom("4.1.136.Final") +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>") +
                "</profiles>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void rootAndProfileBomMergeIsNotGuessed() {
        rewriteRun(xml(project("<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                "</dependencies><profiles>" + profile("override", "<dependencyManagement><dependencies>" +
                bom("4.1.118.Final") + "</dependencies></dependencyManagement>") + "</profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void profileBomDoesNotOwnRootHandlerBySyntaxAlone() {
        rewriteRun(xml(project("<dependencies>" + depWithoutVersion() + "</dependencies><profiles>" +
                profile("optional", "<dependencyManagement><dependencies>" + bom("4.1.100.Final") +
                        "</dependencies></dependencyManagement>") + "</profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootGradlePlatformThatOwnsHandler() {
        rewriteRun(
                buildGradle(
                        "dependencies {\n    implementation platform('io.netty:netty-bom:4.1.124.Final')\n" +
                        "    implementation 'io.netty:netty-handler'\n}",
                        "dependencies {\n    implementation platform('io.netty:netty-bom:4.1.136.Final')\n" +
                        "    implementation 'io.netty:netty-handler'\n}"),
                buildGradleKts(
                        "dependencies {\n    implementation(platform(\"io.netty:netty-bom:4.1.132.Final\"))\n" +
                        "    implementation(\"io.netty:netty-handler\")\n}",
                        "dependencies {\n    implementation(platform(\"io.netty:netty-bom:4.1.136.Final\"))\n" +
                        "    implementation(\"io.netty:netty-handler\")\n}"));
    }

    @ParameterizedTest(name = "never downgrades local BOM {0}")
    @ValueSource(strings = {"4.1.137.Final", "4.2.1.Final", "4.2.10.Final", "5.0.0.Final"})
    void neverDowngradesLocalBom(String version) {
        rewriteRun(
                xml(project("<dependencyManagement><dependencies>" + bom(version) +
                        "</dependencies></dependencyManagement><dependencies>" + depWithoutVersion() +
                        "</dependencies>"), source -> source.path("maven/pom.xml")),
                buildGradle("dependencies { implementation platform('io.netty:netty-bom:" + version +
                        "'); implementation 'io.netty:netty-handler' }"));
    }

    @ParameterizedTest(name = "generated parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache",
            ".gradle", ".m2", "reports", "test-results", "tmp", "TEMP", "node_modules"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("4.1.100.Final"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("4.1.108.Final"), pom("4.1.136.Final"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-handler:4.1.132.Final' }",
                        "dependencies { implementation 'io.netty:netty-handler:4.1.136.Final' }"));
    }

    @Test
    void publicStrictRecipeContainsOnlySelectedUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.nettyhandler").build();
        var recipe = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.nettyhandler.UpgradeNettyHandlerTo4_1_136");
        assertEquals("com.huawei.clouds.openrewrite.nettyhandler.UpgradeSelectedNettyHandlerDependency",
                recipe.getRecipeList().get(0).getName());
    }

    static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    static String dep(String version) {
        return dep(version, "");
    }

    static String dep(String version, String extra) {
        return "<dependency><groupId>io.netty</groupId><artifactId>netty-handler</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>io.netty</groupId><artifactId>netty-handler</artifactId></dependency>";
    }

    static String bom(String version) {
        return "<dependency><groupId>io.netty</groupId><artifactId>netty-bom</artifactId><version>" + version +
               "</version><type>pom</type><scope>import</scope></dependency>";
    }

    static String profile(String id, String body) {
        return "<profile><id>" + id + "</id>" + body + "</profile>";
    }
}
