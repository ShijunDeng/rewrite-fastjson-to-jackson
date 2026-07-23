package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeTomcatEmbedCoreDependencyTest implements RewriteTest {
    private static final String[] SOURCES = {
            "10.1.15", "10.1.16", "10.1.20", "10.1.25", "10.1.28", "10.1.35", "10.1.36",
            "10.1.40", "10.1.41", "10.1.42", "10.1.43", "10.1.44", "10.1.46", "10.1.47",
            "10.1.48", "10.1.49", "10.1.52", "10.1.54", "9.0.102",
            "9.0.104", "9.0.105", "9.0.106", "9.0.107", "9.0.108", "9.0.111", "9.0.115",
            "9.0.117", "9.0.54", "9.0.69", "9.0.71", "9.0.75", "9.0.82", "9.0.83", "9.0.86",
            "9.0.87", "9.0.91", "9.0.96", "9.0.98"};

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedTomcatEmbedCoreDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @MethodSource("sourceVersions")
    void upgradesEveryRecoverableWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("10.1.57"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesHighestPrioritySourceList() {
        assertEquals(Set.of(SOURCES), UpgradeSelectedTomcatEmbedCoreDependency.SOURCE_VERSIONS);
    }

    static Stream<String> sourceVersions() {
        return Stream.of(SOURCES);
    }

    @ParameterizedTest(name = "exclusive property {0}")
    @MethodSource("sourceVersions")
    void upgradesExclusiveRootProperty(String version) {
        rewriteRun(xml(
                project("<properties><tomcat.version>" + version + "</tomcat.version></properties><dependencies>" +
                        dep("${tomcat.version}") + "</dependencies>"),
                project("<properties><tomcat.version>10.1.57</tomcat.version></properties><dependencies>" +
                        dep("${tomcat.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyVisibleInProfileAndProfileOverrideWins() {
        rewriteRun(
                xml(project("<properties><r>9.0.54</r></properties><profiles><profile><id>it</id><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"),
                        project("<properties><r>10.1.57</r></properties><profiles><profile><id>it</id><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")),
                xml(project("<properties><r>9.0.1</r></properties><profiles><profile><id>it</id><properties><r>10.1.15</r></properties><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"),
                        project("<properties><r>9.0.1</r></properties><profiles><profile><id>it</id><properties><r>10.1.57</r></properties><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"), source -> source.path("profile/pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("9.0.102") + "</dependencies></dependencyManagement>" +
                        "<profiles><profile><id>p</id><dependencyManagement><dependencies>" + dep("10.1.41") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("10.1.57") + "</dependencies></dependencyManagement>" +
                        "<profiles><profile><id>p</id><dependencyManagement><dependencies>" + dep("10.1.57") +
                        "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void ambiguousPropertiesAreNoop(String label, String source) {
        rewriteRun(xml(source, specification -> specification.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><r>10.0.27</r></properties>")),
                Arguments.of("plugin", project("<properties><r>10.0.27</r></properties><dependencies>" + dep("${r}") + "</dependencies><build><finalName>${r}</finalName></build>")),
                Arguments.of("other dependency", project("<properties><r>10.1.15</r></properties><dependencies>" + dep("${r}") + rawDep("x", "y", "${r}", "") + "</dependencies>")),
                Arguments.of("duplicate", project("<properties><r>10.1.41</r><r>10.1.41</r></properties><dependencies>" + dep("${r}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><r>10.1.40</r></properties><dependencies>" + dep("${r}") + "</dependencies><x value=\"${r}\"/>")),
                Arguments.of("profile shared", project("<profiles><profile><id>x</id><properties><r>10.1.36</r></properties><dependencies>" + dep("${r}") + rawDep("x", "y", "${r}", "") + "</dependencies></profile></profiles>")),
                Arguments.of("embedded expression", project("<properties><r>10.0.27</r></properties><dependencies>" + dep("${r}-custom") + "</dependencies>"))
        );
    }

    @ParameterizedTest(name = "invalid Maven owner {0}")
    @MethodSource("invalidMavenOwners")
    void nestedOrPluginMavenOwnersAreNoop(String label, String source) {
        rewriteRun(xml(source, specification -> specification.path("pom.xml")));
    }

    static Stream<Arguments> invalidMavenOwners() {
        String dependency = dep("10.0.27");
        return Stream.of(
                Arguments.of("empty root", "<project/>"),
                Arguments.of("dependency directly under project", project(dependency)),
                Arguments.of("build", project("<build><dependencies>" + dependency + "</dependencies></build>")),
                Arguments.of("reporting", project("<reporting><dependencies>" + dependency + "</dependencies></reporting>")),
                Arguments.of("plugin dependency", project("<build><plugins><plugin><dependencies>" + dependency + "</dependencies></plugin></plugins></build>")),
                Arguments.of("nested module", project("<module><dependencies>" + dependency + "</dependencies></module>")),
                Arguments.of("profiles wrapper", project("<profiles><dependencies>" + dependency + "</dependencies></profiles>")),
                Arguments.of("profile build", project("<profiles><profile><id>x</id><build><dependencies>" + dependency + "</dependencies></build></profile></profiles>")),
                Arguments.of("dependency management wrong child", project("<dependencyManagement>" + dependency + "</dependencyManagement>")),
                Arguments.of("arbitrary owner", project("<company><dependencies>" + dependency + "</dependencies></company>"))
        );
    }

    @ParameterizedTest(name = "Gradle source {0}")
    @MethodSource("sourceVersions")
    void upgradesRootGradleGroovyAndKotlin(String version) {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:" + version + "' }",
                        "dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.1.57' }"),
                buildGradleKts("dependencies { compileOnly(\"org.apache.tomcat.embed:tomcat-embed-core:" + version + "\") }",
                        "dependencies { compileOnly(\"org.apache.tomcat.embed:tomcat-embed-core:10.1.57\") }")
        );
    }

    @Test
    void upgradesGroovyMapNotations() {
        rewriteRun(
                buildGradle("dependencies { implementation group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '9.0.54', transitive: false }",
                        "dependencies { implementation group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '10.1.57', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '10.1.41']) }",
                        "dependencies { implementation([group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '10.1.57']) }")
        );
    }

    @ParameterizedTest(name = "nested Gradle owner {0}")
    @MethodSource("nestedGradle")
    void nestedGradleScopesAreNoop(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String dependency = "'org.apache.tomcat.embed:tomcat-embed-core:10.0.27'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + dependency + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + dependency + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + dependency + " } }"),
                Arguments.of("project", "project(':api') { dependencies { implementation " + dependency + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + dependency + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + dependency + " } }"),
                Arguments.of("selected call", "dependencies { helper.implementation " + dependency + " }"),
                Arguments.of("double nested", "subprojects { project(':api') { dependencies { implementation " + dependency + " } } }")
        );
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"9.0.53", "9.0.103", "10.0.27", "10.1.0", "10.1.8", "10.1.14", "10.1.17", "10.1.19", "10.1.21", "10.1.24", "10.1.26", "10.1.27", "10.1.29", "10.1.34", "10.1.37", "10.1.39", "10.1.45", "10.1.55", "10.1.56", "10.1.57", "11.0.0", "11.0.19"})
    void outOfWorkbookVersionsAreNoop(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "blocked target conflict {0}")
    @ValueSource(strings = {"11.0.18", "11.0.21"})
    void higherMajorSourcesAreNeverDowngraded(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "external/dynamic {0}")
    @ValueSource(strings = {"${tomcat.version}", "${revision}", "[10.0,11)", "[10.1,)", "10.1.+", "+", "latest.release", "RELEASE"})
    void externalDynamicOwnersAreNoop(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "wrong coordinate {0}")
    @MethodSource("wrongCoordinates")
    void wrongCoordinatesAndVariantsAreNoop(String label, String dependency) {
        rewriteRun(xml(project("<dependencies>" + dependency + "</dependencies>"), source -> source.path("pom.xml")));
    }

    static Stream<Arguments> wrongCoordinates() {
        return Stream.of(
                Arguments.of("wrong group", rawDep("org.apache.tomcat", "tomcat-embed-core", "10.0.27", "")),
                Arguments.of("lookalike group", rawDep("org.apache.tomcat.embedx", "tomcat-embed-core", "10.0.27", "")),
                Arguments.of("embed el", rawDep("org.apache.tomcat.embed", "tomcat-embed-el", "10.0.27", "")),
                Arguments.of("websocket", rawDep("org.apache.tomcat.embed", "tomcat-embed-websocket", "10.1.15", "")),
                Arguments.of("lookalike artifact", rawDep("org.apache.tomcat.embed", "tomcat-embed-corex", "10.1.41", "")),
                Arguments.of("sources", rawDep("org.apache.tomcat.embed", "tomcat-embed-core", "10.0.27", "<classifier>sources</classifier>")),
                Arguments.of("tests", rawDep("org.apache.tomcat.embed", "tomcat-embed-core", "10.1.15", "<classifier>tests</classifier>")),
                Arguments.of("zip", rawDep("org.apache.tomcat.embed", "tomcat-embed-core", "10.1.41", "<type>zip</type>"))
        );
    }

    @ParameterizedTest(name = "generated/cache {0}")
    @ValueSource(strings = {"target", "build", "out", "dist", "generated", "generated-code", "generatedSources", "GENERATED", "install", "installation", ".gradle", ".mvn", ".m2", ".idea", "node_modules", "vendor", ".cache", ".git", ".vite", "reports", "test-results", "tmp", "TEMP"})
    void generatedCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("10.0.27"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void versionlessAndGradleVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies><dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId></dependency></dependencies>"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.0.27:tests' }"),
                buildGradle("dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.1.15@zip' }")
        );
    }

    @Test
    void installLeafProcessedAndTwoCyclesIdempotent() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("9.0.54"), pom("10.1.57"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.1.41' }",
                        "dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.1.57' }", source -> source.path("install.gradle")));
    }

    @Test
    void recommendedCapturesEligibilityBeforePublicUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatembedcore").build();
        var migration = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57");
        assertEquals(List.of(
                        MarkSelectedTomcatEmbedCoreProjects.class.getName(),
                        "com.huawei.clouds.openrewrite.tomcatembedcore.UpgradeTomcatEmbedCoreTo10_1_57"),
                migration.getRecipeList().stream().limit(2)
                        .map(org.openrewrite.Recipe::getName).toList());
    }

    static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    static String dep(String version) {
        return rawDep("org.apache.tomcat.embed", "tomcat-embed-core", version, "");
    }

    static String rawDep(String group, String artifact, String version, String extra) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version>" + extra + "</dependency>";
    }
}
