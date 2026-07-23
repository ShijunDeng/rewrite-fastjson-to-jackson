package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class SpringRetryBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0_13BuildRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringRetryTestSupport.recipe(RECIPE));
    }

    @ParameterizedTest(name = "higher Maven version {0} gets exact no-downgrade marker")
    @ValueSource(strings = {
            "2.0.14", "2.0.15", "2.1.0", "2.10.0", "3.0.0", "10.0.0",
            "100.200.300", "999999999999999999999.0.0", "2.0.14-SNAPSHOT", "3.0.0-RC1"
    })
    void marksEveryHigherFixedVersionWithoutChangingIt(String version) {
        rewriteRun(xml(SpringRetryTestSupport.pom(version),
                source -> source.path(version.replace('.', '_') + "/pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                            assertTrue(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains("<version>2.0.13</version>"), printed);
                        })));
    }

    @ParameterizedTest(name = "unapproved fixed version {0} is marked outside whitelist")
    @ValueSource(strings = {
            "1.0.0", "1.1.4", "1.2.5.RELEASE", "1.3.0", "1.3.1", "1.3.2",
            "1.3.3", "1.3.5", "1.4.0", "2.0.0", "2.0.1", "2.0.12"
    })
    void marksFixedVersionsOutsideTheApprovedPath(String version) {
        rewriteRun(markedPom(version, FindSpringRetry2013BuildRisks.OUTSIDE));
    }

    @ParameterizedTest(name = "approved or target version {0} has no ownership marker")
    @ValueSource(strings = {"1.3.4", "2.0.13"})
    void selectedSourceAndTargetDoNotGetVersionMarkers(String version) {
        rewriteRun(xml(SpringRetryTestSupport.pom(version),
                source -> source.path(version + "/pom.xml")));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @MethodSource("ownerVersions")
    void marksUnresolvedOrDynamicMavenOwners(String label, String version) {
        rewriteRun(markedPom(version, FindSpringRetry2013BuildRisks.OWNER, label + "/pom.xml"));
    }

    static Stream<Arguments> ownerVersions() {
        return Stream.of(
                Arguments.of("missing-property", "${missing}"),
                Arguments.of("range", "[1.3.4,2.0.13]"),
                Arguments.of("latest", "LATEST"),
                Arguments.of("release", "RELEASE"),
                Arguments.of("wildcard", "1.+"),
                Arguments.of("timestamp-expression", "${revision}${changelist}"));
    }

    @Test
    void marksVersionlessParentOrBomOwner() {
        rewriteRun(xml(SpringRetryTestSupport.project("<dependencies>" +
                        SpringRetryTestSupport.dependency(null, "") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringRetry2013BuildRisks.OWNER),
                                after.printAll()))));
    }

    @ParameterizedTest(name = "Maven variant {0}")
    @MethodSource("mavenVariants")
    void marksNonstandardMavenVariants(String label, String extra) {
        rewriteRun(xml(SpringRetryTestSupport.project("<dependencies>" +
                        SpringRetryTestSupport.dependency("1.3.4", extra) + "</dependencies>"),
                source -> source.path(label + "/pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringRetry2013BuildRisks.VARIANT),
                                after.printAll()))));
    }

    static Stream<Arguments> mavenVariants() {
        return Stream.of(
                Arguments.of("classifier", "<classifier>tests</classifier>"),
                Arguments.of("sources", "<classifier>sources</classifier>"),
                Arguments.of("zip", "<type>zip</type>"),
                Arguments.of("test-jar", "<type>test-jar</type>"));
    }

    @ParameterizedTest(name = "companion boundary {0}")
    @MethodSource("companions")
    void marksCompanionAlignmentAndProxyBoundaries(String label, String dependency, String marker) {
        String source = SpringRetryTestSupport.project("<dependencies>" +
                SpringRetryTestSupport.dependency("2.0.13", "") + dependency + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(marker), after.printAll()))));
    }

    static Stream<Arguments> companions() {
        return Stream.of(
                Arguments.of("spring-context-5",
                        dep("org.springframework", "spring-context", "5.3.39"),
                        FindSpringRetry2013BuildRisks.SPRING_BASELINE),
                Arguments.of("spring-aop-5",
                        dep("org.springframework", "spring-aop", "5.3.39"),
                        FindSpringRetry2013BuildRisks.SPRING_BASELINE),
                Arguments.of("spring-boot-2",
                        dep("org.springframework.boot", "spring-boot-starter-aop", "2.7.18"),
                        FindSpringRetry2013BuildRisks.SPRING_BASELINE),
                Arguments.of("micrometer-old",
                        dep("io.micrometer", "micrometer-core", "1.10.13"),
                        FindSpringRetry2013BuildRisks.MICROMETER_ALIGNMENT),
                Arguments.of("aspectj",
                        dep("org.aspectj", "aspectjweaver", "1.9.22"),
                        FindSpringRetry2013BuildRisks.PROXY_STACK),
                Arguments.of("spring-aop-target",
                        dep("org.springframework", "spring-aop", "6.2.19"),
                        FindSpringRetry2013BuildRisks.PROXY_STACK));
    }

    @ParameterizedTest(name = "Java baseline property {0}")
    @ValueSource(strings = {"8", "1.8", "11", "12", "16"})
    void marksMavenJavaBaselinesBelow17(String javaVersion) {
        String source = SpringRetryTestSupport.project("<properties><java.version>" + javaVersion +
                "</java.version></properties><dependencies>" +
                SpringRetryTestSupport.dependency("2.0.13", "") + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path(javaVersion.replace('.', '_') + "/pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringRetry2013BuildRisks.JAVA_BASELINE),
                                after.printAll()))));
    }

    @Test
    void Java17AndNewerDoNotGetBaselineMarker() {
        String source = SpringRetryTestSupport.project(
                "<properties><java.version>17</java.version><maven.compiler.release>21</maven.compiler.release>" +
                "</properties><dependencies>" + SpringRetryTestSupport.dependency("2.0.13", "") +
                "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    @Test
    void marksExclusionsAndShadeRelocation() {
        String source = SpringRetryTestSupport.project("<dependencies>" +
                SpringRetryTestSupport.dependency("2.0.13",
                        "<exclusions><exclusion><groupId>org.springframework</groupId>" +
                        "<artifactId>spring-context</artifactId></exclusion></exclusions>") +
                "</dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration><relocations><relocation>" +
                "<pattern>org.springframework.retry</pattern><shadedPattern>internal.retry</shadedPattern>" +
                "</relocation></relocations></configuration></plugin></plugins></build>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(FindSpringRetry2013BuildRisks.PACKAGING), after.printAll()))));
    }

    @ParameterizedTest(name = "Gradle higher version {0}")
    @ValueSource(strings = {"2.0.14", "2.1.0", "3.0.0", "10.0.0"})
    void marksGroovyAndKotlinHigherVersionsWithoutDowngrading(String version) {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.springframework.retry:spring-retry:" +
                        version + "' }", source -> source.path("groovy/" + version + "/build.gradle")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(version), printed);
                            assertTrue(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains("2.0.13"), printed);
                        })),
                buildGradleKts("dependencies { implementation(\"org.springframework.retry:spring-retry:" +
                        version + "\") }", source -> source.path("kotlin/" + version + "/build.gradle.kts")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(version), printed);
                            assertTrue(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                        })));
    }

    @Test
    void marksGradleDynamicCatalogVariantJavaAndPackagingOwners() {
        rewriteRun(
                buildGradle("""
                        sourceCompatibility = '11'
                        def v = '1.3.4'
                        dependencies {
                          implementation "org.springframework.retry:spring-retry:${v}"
                          implementation libs.spring.retry
                          implementation group: 'org.springframework.retry', name: 'spring-retry',
                                         version: '1.3.4', classifier: 'sources'
                        }
                        shadowJar { relocate 'org.springframework.retry', 'internal.retry' }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.OWNER), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.JAVA_BASELINE), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.PACKAGING), printed);
                })),
                buildGradleKts("""
                        java { toolchain { languageVersion.set(JavaLanguageVersion.of(11)) } }
                        val v = "1.3.4"
                        dependencies { implementation("org.springframework.retry:spring-retry:$v") }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.OWNER), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.JAVA_BASELINE), printed);
                })));
    }

    @ParameterizedTest(name = "generated/cache build {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", ".gradle", ".m2", ".idea",
            "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void generatedBuildFilesAreNotMarked(String parent) {
        rewriteRun(xml(SpringRetryTestSupport.pom("3.0.0"),
                source -> source.path(parent + "/pom.xml")));
    }

    private static org.openrewrite.test.SourceSpecs markedPom(
            String version, String marker) {
        return markedPom(version, marker, version.replace('.', '_') + "/pom.xml");
    }

    private static org.openrewrite.test.SourceSpecs markedPom(
            String version, String marker, String path) {
        return xml(SpringRetryTestSupport.pom(version),
                source -> source.path(path).after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(marker), after.printAll())));
    }

    private static String dep(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
               "</artifactId><version>" + version + "</version></dependency>";
    }
}
