package com.huawei.clouds.openrewrite.nettycodechttp;

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

class UpgradeNettyCodecHttpDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNettyCodecHttpDependency());
    }

    @ParameterizedTest(name = "approved 4.1 source {0}")
    @ValueSource(strings = {"4.1.49.Final", "4.1.100.Final", "4.1.107.Final", "4.1.108.Final",
            "4.1.109.Final", "4.1.118.Final", "4.1.125.Final", "4.1.126.Final", "4.1.127.Final",
            "4.1.128.Final", "4.1.129.Final", "4.1.130.Final", "4.1.132.Final"})
    void upgradesEveryApproved41Source(String version) {
        rewriteRun(xml(pom(version), pom("4.1.136.Final"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistSeparatesWorkbookConflictFromAutoSources() {
        assertEquals(Set.of("4.1.49.Final", "4.1.100.Final", "4.1.107.Final", "4.1.108.Final",
                        "4.1.109.Final", "4.1.118.Final", "4.1.125.Final", "4.1.126.Final",
                        "4.1.127.Final", "4.1.128.Final", "4.1.129.Final", "4.1.130.Final",
                        "4.1.132.Final"), NettyCodecHttpSupport.AUTO_SOURCES);
        assertEquals(14, NettyCodecHttpSupport.WORKBOOK_SOURCES.size());
        assertEquals("4.2.10.Final", NettyCodecHttpSupport.DOWNGRADE_CONFLICT);
    }

    @Test
    void neverDowngrades42Source() {
        rewriteRun(xml(pom("4.2.10.Final"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http:4.2.10.Final' }"),
                buildGradleKts("dependencies { implementation(\"io.netty:netty-codec-http:4.2.10.Final\") }"));
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><netty.version>4.1.100.Final</netty.version></properties><dependencies>" +
                        dep("${netty.version}") + "</dependencies>"),
                project("<properties><netty.version>4.1.136.Final</netty.version></properties><dependencies>" +
                        dep("${netty.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void doesNotRewrite42Property() {
        rewriteRun(xml(project("<properties><netty.version>4.2.10.Final</netty.version></properties><dependencies>" +
                dep("${netty.version}") + "</dependencies>"), source -> source.path("pom.xml")));
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
                        dep("${v}") + "<dependency><groupId>io.netty</groupId><artifactId>netty-buffer</artifactId>" +
                        "<version>${v}</version></dependency></dependencies>")),
                Arguments.of("shared build", project("<properties><v>4.1.118.Final</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project("<properties><v>4.1.125.Final</v><v>4.1.125.Final</v></properties>" +
                        "<dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>4.1.129.Final</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @Test
    void upgradesGroovyStringMapAndMapLiteralNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http:4.1.49.Final' }",
                        "dependencies { implementation 'io.netty:netty-codec-http:4.1.136.Final' }"),
                buildGradle("dependencies { runtimeOnly group: 'io.netty', name: 'netty-codec-http', version: '4.1.128.Final', transitive: false }",
                        "dependencies { runtimeOnly group: 'io.netty', name: 'netty-codec-http', version: '4.1.136.Final', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'io.netty', name: 'netty-codec-http', version: '4.1.132.Final']) }",
                        "dependencies { implementation([group: 'io.netty', name: 'netty-codec-http', version: '4.1.136.Final']) }"));
    }

    @ParameterizedTest(name = "Groovy source {0}")
    @ValueSource(strings = {"4.1.49.Final", "4.1.100.Final", "4.1.107.Final", "4.1.108.Final",
            "4.1.109.Final", "4.1.118.Final", "4.1.125.Final", "4.1.126.Final", "4.1.127.Final",
            "4.1.128.Final", "4.1.129.Final", "4.1.130.Final", "4.1.132.Final"})
    void upgradesEveryApprovedSourceInGroovy(String version) {
        rewriteRun(buildGradle("dependencies { implementation 'io.netty:netty-codec-http:" + version + "' }",
                "dependencies { implementation 'io.netty:netty-codec-http:4.1.136.Final' }"));
    }

    @Test
    void upgradesKotlinGradleNotation() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"io.netty:netty-codec-http:4.1.130.Final\") }",
                "dependencies { implementation(\"io.netty:netty-codec-http:4.1.136.Final\") }"));
    }

    @ParameterizedTest(name = "nested Gradle scope {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'io.netty:netty-codec-http:4.1.100.Final'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom DSL", "company { dependencies { implementation " + d + " } }"));
    }

    @ParameterizedTest(name = "out-of-scope fixed version {0}")
    @ValueSource(strings = {"4.1.48.Final", "4.1.50.Final", "4.1.99.Final", "4.1.101.Final",
            "4.1.117.Final", "4.1.119.Final", "4.1.131.Final", "4.1.133.Final", "4.1.135.Final",
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
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http:4.1.129.Final:tests' }"),
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http:4.1.130.Final@zip' }"));
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
                xml(pom("4.1.107.Final"), pom("4.1.136.Final"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.netty:netty-codec-http:4.1.132.Final' }",
                        "dependencies { implementation 'io.netty:netty-codec-http:4.1.136.Final' }"));
    }

    @Test
    void publicStrictRecipeContainsOnlySelectedUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.nettycodechttp").build();
        var recipe = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.nettycodechttp.UpgradeNettyCodecHttpTo4_1_136");
        assertEquals("com.huawei.clouds.openrewrite.nettycodechttp.UpgradeSelectedNettyCodecHttpDependency",
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
        return "<dependency><groupId>io.netty</groupId><artifactId>netty-codec-http</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>io.netty</groupId><artifactId>netty-codec-http</artifactId></dependency>";
    }
}
