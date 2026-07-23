package com.huawei.clouds.openrewrite.springkafka;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringKafkaDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedSpringKafkaDependency());
    }

    @ParameterizedTest(name = "Maven {0}")
    @ValueSource(strings = {"2.8.11", "2.9.5"})
    void upgradesOnlyApprovedMavenSources(String version) {
        rewriteRun(xml(pom(version), pom("3.3.15"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistIsExactlyTheWorkbookSelection() {
        assertEquals(Set.of("2.8.11", "2.9.5"), SpringKafkaUpgradeSupport.SOURCE_VERSIONS);
        assertEquals("3.3.15", SpringKafkaUpgradeSupport.TARGET);
    }

    @Test
    void upgradesOwnedDependencyManagementAndProfileDeclarations() {
        rewriteRun(
                xml(project("<dependencyManagement><dependencies>" + dep("2.8.11") +
                            "</dependencies></dependencyManagement>"),
                    project("<dependencyManagement><dependencies>" + dep("3.3.15") +
                            "</dependencies></dependencyManagement>"),
                    source -> source.path("dm/pom.xml")),
                xml(project("<profiles><profile><id>it</id><dependencies>" + dep("2.9.5") +
                            "</dependencies></profile></profiles>"),
                    project("<profiles><profile><id>it</id><dependencies>" + dep("3.3.15") +
                            "</dependencies></profile></profiles>"),
                    source -> source.path("profile/pom.xml")));
    }

    @Test
    void upgradesOnlyAnExclusiveOwnedProperty() {
        rewriteRun(xml(
                project("<properties><spring-kafka.version>2.9.5</spring-kafka.version></properties>" +
                        "<dependencies>" + dep("${spring-kafka.version}") + "</dependencies>"),
                project("<properties><spring-kafka.version>3.3.15</spring-kafka.version></properties>" +
                        "<dependencies>" + dep("${spring-kafka.version}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous Maven owner: {0}")
    @MethodSource("ambiguousOwners")
    void leavesSharedDuplicateAndShadowedPropertiesUnchanged(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")));
    }

    static Stream<Arguments> ambiguousOwners() {
        return Stream.of(
                Arguments.of("shared-dependency", project(
                        "<properties><v>2.8.11</v></properties><dependencies>" + dep("${v}") +
                        "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency>" +
                        "</dependencies>")),
                Arguments.of("shared-build", project(
                        "<properties><v>2.9.5</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project(
                        "<properties><v>2.9.5</v><v>2.9.5</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies>")),
                Arguments.of("profile-shadow", project(
                        "<properties><v>2.8.11</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><profiles><profile><id>x</id><properties><v>2.9.5</v></properties>" +
                        "</profile></profiles>")));
    }

    @Test
    void profileOwnerOverridesRootWithoutLeaking() {
        rewriteRun(xml(
                project("<properties><v>4.0.0</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>2.8.11</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"),
                project("<properties><v>4.0.0</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>3.3.15</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesGroovyStringMapMapLiteralAndKotlinString() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.kafka:spring-kafka:2.8.11' }",
                        "dependencies { implementation 'org.springframework.kafka:spring-kafka:3.3.15' }"),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework.kafka', name: 'spring-kafka', version: '2.9.5', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.springframework.kafka', name: 'spring-kafka', version: '3.3.15', transitive: false }"),
                buildGradle(
                        "dependencies { implementation([group: 'org.springframework.kafka', name: 'spring-kafka', version: '2.8.11']) }",
                        "dependencies { implementation([group: 'org.springframework.kafka', name: 'spring-kafka', version: '3.3.15']) }"),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework.kafka:spring-kafka:2.9.5\") }",
                        "dependencies { implementation(\"org.springframework.kafka:spring-kafka:3.3.15\") }"));
    }

    @ParameterizedTest(name = "fixed/dynamic no-op {0}")
    @ValueSource(strings = {
            "2.7.18", "2.8.10", "2.9.4", "3.0.0", "3.3.14", "3.3.15",
            "3.3.16", "3.4.0-M1", "4.0.0", "999999999999999999999.0.0",
            "${spring-kafka.version}", "[2.8,3)", "2.+", "+", "latest.release"
    })
    void neverGuessesOrDowngradesOtherVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("maven-" + Math.abs(version.hashCode()) + "/pom.xml")));
    }

    @Test
    void higherVersionComparisonUsesUnboundedNumericSegments() {
        assertTrue(SpringKafkaUpgradeSupport.targetConflict("3.3.16"));
        assertTrue(SpringKafkaUpgradeSupport.targetConflict("3.4.0-M1"));
        assertTrue(SpringKafkaUpgradeSupport.targetConflict("999999999999999999999.0.0"));
        assertFalse(SpringKafkaUpgradeSupport.targetConflict("3.3.15"));
        assertFalse(SpringKafkaUpgradeSupport.targetConflict("3.3.15-RC1"));
        assertFalse(SpringKafkaUpgradeSupport.targetConflict("2.9.5"));
    }

    @Test
    void variantsPluginDependenciesNestedScopesAndGeneratedFilesAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + dep("2.8.11", "<classifier>tests</classifier>") +
                            dep("2.9.5", "<type>zip</type>") + "</dependencies>"),
                        source -> source.path("variant/pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" +
                            dep("2.9.5") + "</dependencies></plugin></plugins></build>"),
                        source -> source.path("plugin/pom.xml")),
                buildGradle("buildscript { dependencies { classpath 'org.springframework.kafka:spring-kafka:2.8.11' } }"),
                buildGradle("subprojects { dependencies { implementation 'org.springframework.kafka:spring-kafka:2.9.5' } }"),
                buildGradle("dependencies { constraints { implementation 'org.springframework.kafka:spring-kafka:2.8.11' } }"),
                buildGradle("dependencies { implementation 'org.springframework.kafka:spring-kafka:2.8.11:tests' }"),
                buildGradle("dependencies { implementation 'org.springframework.kafka:spring-kafka:2.9.5@zip' }"),
                xml(pom("2.8.11"), source -> source.path("target/generated/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("2.8.11"), pom("3.3.15"), source -> source.path("pom.xml")),
                buildGradle(
                        "dependencies { implementation 'org.springframework.kafka:spring-kafka:2.9.5' }",
                        "dependencies { implementation 'org.springframework.kafka:spring-kafka:3.3.15' }"));
    }

    @Test
    void publicUpgradeRecipeContainsOnlyTheStrictRecipe() {
        var recipe = environment().activateRecipes(
                "com.huawei.clouds.openrewrite.springkafka.UpgradeSpringKafkaTo3_3_15");
        assertEquals(1, recipe.getRecipeList().size());
        assertEquals("com.huawei.clouds.openrewrite.springkafka.UpgradeSelectedSpringKafkaDependency",
                recipe.getRecipeList().get(0).getName());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springkafka")
                .build();
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
        return "<dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }
}
