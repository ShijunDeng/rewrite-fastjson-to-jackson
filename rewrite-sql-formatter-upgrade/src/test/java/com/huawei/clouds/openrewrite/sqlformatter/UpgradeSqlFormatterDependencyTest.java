package com.huawei.clouds.openrewrite.sqlformatter;

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

class UpgradeSqlFormatterDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedSqlFormatterDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"12.0.6", "12.2.0", "2.0.4", "3.1.0"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("15.6.5"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesRows414Through417() {
        assertEquals(Set.of("12.0.6", "12.2.0", "2.0.4", "3.1.0"),
                UpgradeSelectedSqlFormatterDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><sql.formatter.version>2.0.4</sql.formatter.version></properties>" +
                        "<dependencies>" + dep("${sql.formatter.version}") + "</dependencies>"),
                project("<properties><sql.formatter.version>15.6.5</sql.formatter.version></properties>" +
                        "<dependencies>" + dep("${sql.formatter.version}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInDirectProfile() {
        rewriteRun(xml(
                project("<properties><fmt.version>12.2.0</fmt.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                project("<properties><fmt.version>15.6.5</fmt.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(
                project("<properties><fmt.version>2.0.5</fmt.version></properties><profiles><profile><id>owned</id>" +
                        "<properties><fmt.version>3.1.0</fmt.version></properties><dependencies>" +
                        dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                project("<properties><fmt.version>2.0.5</fmt.version></properties><profiles><profile><id>owned</id>" +
                        "<properties><fmt.version>15.6.5</fmt.version></properties><dependencies>" +
                        dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("12.0.6") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("3.1.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("15.6.5") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("15.6.5") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property ownership: {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><f>2.0.4</f></properties>")),
                Arguments.of("shared plugin", project("<properties><f>2.0.4</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies><build><finalName>${f}</finalName></build>")),
                Arguments.of("shared dependency", project("<properties><f>2.0.4</f></properties><dependencies>" +
                        dep("${f}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${f}</version>" +
                        "</dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><f>2.0.4</f><f>2.0.4</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><f>2.0.4</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies><x value=\"${f}\"/>")));
    }

    @Test
    void upgradesRealStarRocksMavenPattern() {
        // Reduced from StarRocks/starrocks@aab21898c1cb0991e261dfb0bbf43f78969c0633.
        rewriteRun(xml(pom("2.0.4"), pom("15.6.5"), source -> source.path("fe/fe-core/pom.xml")));
    }

    @Test
    void upgradesRealStarRocksKotlinGradlePattern() {
        // Reduced from the same fixed StarRocks commit, which declares this coordinate in both build systems.
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"com.github.vertical-blank:sql-formatter:2.0.4\") }",
                "dependencies { implementation(\"com.github.vertical-blank:sql-formatter:15.6.5\") }"));
    }

    @Test
    void upgradesGroovyStringAndMapNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:12.2.0' }",
                        "dependencies { implementation 'com.github.vertical-blank:sql-formatter:15.6.5' }"),
                buildGradle("dependencies { runtimeOnly group: 'com.github.vertical-blank', name: 'sql-formatter', version: '3.1.0', transitive: false }",
                        "dependencies { runtimeOnly group: 'com.github.vertical-blank', name: 'sql-formatter', version: '15.6.5', transitive: false }"));
    }

    @ParameterizedTest(name = "nested Gradle scope NOOP: {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'com.github.vertical-blank:sql-formatter:2.0.4'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':client') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected invocation", "dependencies { helper.implementation " + d + " }"));
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"1.0", "2.0.3", "2.0.5", "3.0.9", "12.0.5", "12.2.1", "15.6.4", "16.0.0"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned or nonfixed {0}")
    @ValueSource(strings = {"${sql.formatter.version}", "[2,16)", "[12.2.0,)", "12.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedAndVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"), source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("2.0.4", "<classifier>tests</classifier>") + "</dependencies>"), source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("2.0.4", "<type>zip</type>") + "</dependencies>"), source -> source.path("zip/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:2.0.4:tests' }"),
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:2.0.4@zip' }"));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache",
            ".gradle", ".m2", "reports", ".output", "test-results", "storybook-static", "tmp", "TEMP", ".vite",
            ".nuxt", "node_modules"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("2.0.4"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafFilenamesBeginningInstallAreStillProcessed() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:2.0.4' }",
                        "dependencies { implementation 'com.github.vertical-blank:sql-formatter:15.6.5' }",
                        source -> source.path("install.gradle")),
                xml(pom("3.1.0"), pom("15.6.5"), source -> source.path("pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("12.0.6"), pom("15.6.5"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'com.github.vertical-blank:sql-formatter:2.0.4' }",
                        "dependencies { implementation 'com.github.vertical-blank:sql-formatter:15.6.5' }"));
    }

    @Test
    void publicLowLevelRecipeContainsOnlyStrictUpgradeAndRecommendedReusesIt() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.sqlformatter").build();
        var migrate = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.sqlformatter.MigrateSqlFormatterTo15_6_5");
        assertEquals("com.huawei.clouds.openrewrite.sqlformatter.UpgradeSqlFormatterTo15_6_5",
                migrate.getRecipeList().get(0).getName());
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.sqlformatter.UpgradeSqlFormatterTo15_6_5")),
                xml(pom("2.0.4"), pom("15.6.5"), source -> source.path("pom.xml")));
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
        return "<dependency><groupId>com.github.vertical-blank</groupId><artifactId>sql-formatter</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>com.github.vertical-blank</groupId><artifactId>sql-formatter</artifactId></dependency>";
    }
}
