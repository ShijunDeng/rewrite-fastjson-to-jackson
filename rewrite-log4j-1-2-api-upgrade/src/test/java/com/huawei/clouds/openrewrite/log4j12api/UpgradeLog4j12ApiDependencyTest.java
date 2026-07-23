package com.huawei.clouds.openrewrite.log4j12api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeLog4j12ApiDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedLog4j12ApiDependency());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.13.2", "2.17.1", "2.17.2", "2.18.0", "2.19.0", "2.20.0"})
    void upgradesEveryWorkbookMavenSource(String source) {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pomDependency(source), pomDependency("2.25.5")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.13.2", "2.17.1", "2.17.2", "2.18.0", "2.19.0", "2.20.0"})
    void upgradesEveryWorkbookGradleGroovySource(String source) {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle(
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-1.2-api:" + source + "' }",
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-1.2-api:2.25.5' }"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.13.2", "2.17.1", "2.17.2", "2.18.0", "2.19.0", "2.20.0"})
    void upgradesEveryWorkbookGradleKotlinSource(String source) {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradleKts(
                        "dependencies { implementation(\"org.apache.logging.log4j:log4j-1.2-api:" + source + "\") }",
                        "dependencies { implementation(\"org.apache.logging.log4j:log4j-1.2-api:2.25.5\") }"));
    }

    @Test
    void upgradesOwnedDependencyManagementAndPreservesMetadata() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-1.2-api</artifactId>
                    <version>2.17.2</version>
                    <scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-1.2-api</artifactId>
                    <version>2.25.5</version>
                    <scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """));
    }

    @Test
    void upgradesExclusivelyOwnedMavenProperty() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><log4j12.version>2.18.0</log4j12.version></properties>
                  <dependencies><dependency>
                    <groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId>
                    <version>${log4j12.version}</version>
                  </dependency></dependencies>
                </project>
                """, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><log4j12.version>2.25.5</log4j12.version></properties>
                  <dependencies><dependency>
                    <groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId>
                    <version>${log4j12.version}</version>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesSharedMavenPropertyUnchanged() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><logging.version>2.20.0</logging.version></properties>
                  <dependencies>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId><version>${logging.version}</version></dependency>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>${logging.version}</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.12.4", "2.17.0", "2.24.3", "2.25.4", "2.25.5", "2.26.0", "3.0.0-beta2"})
    void leavesOutsideTargetAndHigherMavenVersionsUnchanged(String version) {
        rewriteRun(pomXml(pomDependency(version)));
    }

    @Test
    void leavesClassifierAndNonJarVariantsUnchanged() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId><version>2.20.0</version><classifier>sources</classifier></dependency>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId><version>2.20.0</version><type>test-jar</type></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void leavesNestedGradleOwnershipAndTemplatesUnchanged() {
        rewriteRun(buildGradle("""
                def v = '2.20.0'
                subprojects {
                    dependencies {
                        implementation "org.apache.logging.log4j:log4j-1.2-api:${v}"
                        implementation 'org.apache.logging.log4j:log4j-1.2-api:2.20.0'
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedMavenAndGradleFiles() {
        rewriteRun(
                pomXml(pomDependency("2.20.0"), source -> source.path("target/generated/pom.xml")),
                buildGradle(
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-1.2-api:2.20.0' }",
                        source -> source.path("build/generated/build.gradle")));
    }

    @Test
    void doesNotTouchWrongCoordinatesOrCompanions() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>2.20.0</version></dependency>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>2.20.0</version></dependency>
                  </dependencies>
                </project>
                """));
        rewriteRun(buildGradle(
                "dependencies { implementation 'example:log4j-1.2-api:2.20.0' }"));
    }

    @Test
    void realSciviewGradleKotlinFixture() {
        // scenerygraphics/sciview@246aa7a6ad71b148669a10b281fd82727d672457, build.gradle.kts:88
        rewriteRun(buildGradleKts(
                """
                dependencies {
                    implementation("org.apache.logging.log4j:log4j-1.2-api:2.20.0")
                }
                """,
                """
                dependencies {
                    implementation("org.apache.logging.log4j:log4j-1.2-api:2.25.5")
                }
                """));
    }

    @Test
    void publishesExactSixVersionWhitelist() {
        assertTrue(UpgradeSelectedLog4j12ApiDependency.SOURCE_VERSIONS.containsAll(
                java.util.Set.of("2.13.2", "2.17.1", "2.17.2", "2.18.0", "2.19.0", "2.20.0")));
        org.junit.jupiter.api.Assertions.assertEquals(
                6, UpgradeSelectedLog4j12ApiDependency.SOURCE_VERSIONS.size());
    }

    private static String pomDependency(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-1.2-api</artifactId>
                    <version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }
}
