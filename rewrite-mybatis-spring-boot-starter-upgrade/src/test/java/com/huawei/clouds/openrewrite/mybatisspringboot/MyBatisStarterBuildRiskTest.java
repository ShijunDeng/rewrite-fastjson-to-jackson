package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class MyBatisStarterBuildRiskTest implements RewriteTest {
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.mybatisspringboot.AuditMyBatisSpringBootStarter4Build";

    @Test
    void recommendedRecipePerformsDependencyAutoAndPrecisePlatformMarksTogether() {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(
                        UpgradeMyBatisSpringBootStarterTest.MIGRATE)),
                xml(
                        project("""
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.5.4</version>
                          </parent>
                          <properties><java.version>11</java.version></properties>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId>
                            <artifactId>mybatis-spring-boot-starter</artifactId>
                            <version>2.3.1</version>
                          </dependency></dependencies>
                          """),
                        project("""
                          <!--~~(MyBatis Spring Boot Starter 4 requires Spring Boot 4.0 or newer; upgrade the parent before the starter)~~>--><parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.5.4</version>
                          </parent>
                          <properties><!--~~(MyBatis Spring Boot Starter 4 requires Java 17 or newer; upgrade the compiler and runtime together)~~>--><java.version>11</java.version></properties>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId>
                            <artifactId>mybatis-spring-boot-starter</artifactId>
                            <version>4.0.0</version>
                          </dependency></dependencies>
                          """),
                        source -> source.path("pom.xml")
                )
        );
    }

    @ParameterizedTest(name = "marks unresolved Maven Starter version {0}")
    @ValueSource(strings = {"1.1.1", "2.3.1", "2.3.2", "3.0.4", "4.1.0", "[2.3,4.0)", "LATEST", "${shared.version}"})
    void marksMavenVersionsNotResolvedToTarget(String version) {
        assertPomMarker("<dependencies><dependency><groupId>org.mybatis.spring.boot</groupId>" +
                        "<artifactId>mybatis-spring-boot-starter</artifactId><version>" + version +
                        "</version></dependency></dependencies>",
                FindMyBatisStarterBuildRisks.UNRESOLVED_VERSION_MESSAGE);
    }

    @Test
    void marksVersionlessExternalManagementButAcceptsLocalTargetManagement() {
        assertPomMarker("<dependencies><dependency><groupId>org.mybatis.spring.boot</groupId>" +
                        "<artifactId>mybatis-spring-boot-starter</artifactId></dependency></dependencies>",
                FindMyBatisStarterBuildRisks.VERSIONLESS_MESSAGE);

        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                xml(project("""
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.mybatis.spring.boot</groupId>
                    <artifactId>mybatis-spring-boot-starter</artifactId>
                    <version>4.0.0</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.mybatis.spring.boot</groupId>
                    <artifactId>mybatis-spring-boot-starter</artifactId>
                  </dependency></dependencies>
                  """), source -> source.path("pom.xml"))
        );
    }

    @ParameterizedTest(name = "marks Maven custom artifact shape {0}")
    @ValueSource(strings = {"<classifier>tests</classifier>", "<classifier>sources</classifier>",
            "<type>test-jar</type>", "<type>pom</type>", "<type>zip</type>"})
    void marksCustomMavenArtifactShapes(String custom) {
        assertPomMarker("<dependencies><dependency><groupId>org.mybatis.spring.boot</groupId>" +
                        "<artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version>" + custom +
                        "</dependency></dependencies>", FindMyBatisStarterBuildRisks.CUSTOM_ARTIFACT_MESSAGE);
    }

    @ParameterizedTest(name = "marks unaligned family module {0}")
    @ValueSource(strings = {"mybatis-spring-boot-autoconfigure", "mybatis-spring-boot-test-autoconfigure",
            "mybatis-spring-boot-starter-test"})
    void marksUnalignedCompanionModules(String artifact) {
        assertPomMarker(targetDependency() + "<dependencies><dependency><groupId>org.mybatis.spring.boot</groupId>" +
                        "<artifactId>" + artifact + "</artifactId><version>3.0.4</version>" +
                        "</dependency></dependencies>", FindMyBatisStarterBuildRisks.COMPANION_MESSAGE);
    }

    @ParameterizedTest(name = "marks Gradle Java {0} baseline {1}")
    @MethodSource("gradleJavaBaselines")
    void marksUnsupportedGradleJavaBaselines(String language, String build) {
        if ("kotlin".equals(language)) {
            assertKotlinMarker(build, FindMyBatisStarterBuildRisks.GRADLE_JAVA_MESSAGE);
        } else {
            assertGroovyMarker(build, FindMyBatisStarterBuildRisks.GRADLE_JAVA_MESSAGE);
        }
    }

    static Stream<Arguments> gradleJavaBaselines() {
        Stream<Arguments> assignments = IntStream.rangeClosed(8, 16).boxed().flatMap(version -> Stream.of(
                Arguments.of("groovy", "sourceCompatibility = '" + version + "'"),
                Arguments.of("groovy", "targetCompatibility = " +
                        (version <= 10 ? "'" + version + "'" : "JavaVersion.VERSION_" + version)),
                Arguments.of("kotlin", "kotlin { jvmToolchain(" + version + ") }")
        ));
        return Stream.concat(assignments, Stream.of(
                Arguments.of("groovy", "sourceCompatibility = JavaVersion.VERSION_1_8"),
                Arguments.of("groovy", "java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }"),
                Arguments.of("kotlin", "java { toolchain { languageVersion.set(JavaLanguageVersion.of(16)) } }")
        ));
    }

    @ParameterizedTest(name = "marks Maven compiler plugin {0}={1}")
    @MethodSource("mavenCompilerBaselines")
    void marksUnsupportedMavenCompilerPluginBaselines(String element, String version) {
        assertPomMarker("<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-compiler-plugin</artifactId><configuration><" + element + ">" +
                        version + "</" + element + "></configuration></plugin></plugins></build>" + targetDependency(),
                "MyBatis Spring Boot Starter 4 requires Java 17 or newer");
    }

    static Stream<Arguments> mavenCompilerBaselines() {
        return Stream.of("source", "target", "release").flatMap(element ->
                Stream.of("1.8", "8", "11", "16").map(version -> Arguments.of(element, version)));
    }

    @Test
    void resolvesMavenPlatformPropertiesAndMarksOnlyProvenBlockers() {
        assertPomMarker("""
                        <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${boot.version}</version></parent>
                        <properties><boot.version>3.5.4</boot.version><java.version>${compiler.version}</java.version><compiler.version>11</compiler.version></properties>
                        """ + targetDependency(),
                "MyBatis Spring Boot Starter 4 requires Spring Boot 4.0 or newer");

        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                xml(project("""
                        <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${boot.version}</version></parent>
                        <properties><boot.version>4.0.0</boot.version><java.version>17</java.version></properties>
                        """ + targetDependency()), source -> source.path("pom.xml"))
        );
    }

    @ParameterizedTest(name = "marks unresolved Gradle declaration {0}")
    @ValueSource(strings = {"2.3.1", "2.3.2", "3.0.4", "4.1.0", "2.+", "${mybatisVersion}", "2.3.1:tests", "2.3.1@zip"})
    void marksUnresolvedAndCustomGradleDeclarations(String version) {
        String message = version.contains(":") || version.contains("@")
                ? FindMyBatisStarterBuildRisks.CUSTOM_ARTIFACT_MESSAGE
                : FindMyBatisStarterBuildRisks.UNRESOLVED_VERSION_MESSAGE;
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                buildGradle(
                        "plugins { id 'java' }\ndef mybatisVersion = '2.3.1'\ndependencies { implementation \"org.mybatis.spring.boot:mybatis-spring-boot-starter:" +
                        version + "\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(message), after.printAll()))
                )
        );
    }

    @Test
    void marksGroovyMapVariableAndUnalignedCompanionAtExactInvocations() {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                buildGradle(
                        """
                        plugins { id 'java' }
                        def mybatisVersion = '4.0.0'
                        dependencies {
                            implementation group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter', version: mybatisVersion
                            testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.4'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindMyBatisStarterBuildRisks.UNRESOLVED_VERSION_MESSAGE), printed);
                            assertTrue(printed.contains(FindMyBatisStarterBuildRisks.COMPANION_MESSAGE), printed);
                        })
                )
        );
    }

    @Test
    void marksKotlinInterpolatedCoreAndUnalignedCompanionAtExactInvocations() {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                buildGradleKts(
                        """
                        plugins { java }
                        val mybatisVersion = "3.0.4"
                        dependencies {
                            implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisVersion")
                            testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.4")
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindMyBatisStarterBuildRisks.UNRESOLVED_VERSION_MESSAGE), printed);
                            assertTrue(printed.contains(FindMyBatisStarterBuildRisks.COMPANION_MESSAGE), printed);
                        })
                )
        );
    }

    @ParameterizedTest(name = "unowned build stays unmarked {index}")
    @ValueSource(strings = {
            "<properties><java.version>8</java.version></properties>",
            "<dependencies><dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>2.3.1</version></dependency></dependencies>",
            "<dependencies><dependency><groupId>com.example</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version></dependency></dependencies>"
    })
    void leavesUnownedMavenBuildValuesUnmarked(String body) {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                xml(project(body), source -> source.path("pom.xml"))
        );
    }

    @Test
    void leavesUnownedGradleBaselinesAndCompanionsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                buildGradle("""
                        plugins { id 'java' }
                        sourceCompatibility = '8'
                        dependencies { testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1' }
                        """),
                buildGradleKts("""
                        plugins { java }
                        kotlin { jvmToolchain(11) }
                        dependencies { testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1") }
                        """, source -> source.path("kotlin/build.gradle.kts")),
                buildGradle("""
                        plugins { id 'java' }
                        implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.1'
                        sourceCompatibility = '8'
                        """, source -> source.path("dsl/build.gradle"))
        );
    }

    @Test
    void leavesGeneratedBuildFilesUnaudited() {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                xml(project("<properties><java.version>8</java.version></properties>" + targetDependency()),
                        source -> source.path("target/pom.xml")),
                buildGradle("sourceCompatibility = '8'\ndependencies { implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0' }",
                        source -> source.path("build/generated/build.gradle"))
        );
    }

    private void assertPomMarker(String body, String message) {
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                xml(project(body), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll())))
        );
    }

    private void assertGroovyMarker(String body, String message) {
        String source = "plugins { id 'java' }\n" + body + "\ndependencies { implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0' }";
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                buildGradle(source, build -> build.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll())))
        );
    }

    private void assertKotlinMarker(String body, String message) {
        String source = "plugins { java }\n" + body + "\ndependencies { implementation(\"org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0\") }";
        rewriteRun(
                spec -> spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(AUDIT)),
                buildGradleKts(source, build -> build.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll())))
        );
    }

    private static String targetDependency() {
        return "<dependencies><dependency><groupId>org.mybatis.spring.boot</groupId>" +
               "<artifactId>mybatis-spring-boot-starter</artifactId><version>4.0.0</version>" +
               "</dependency></dependencies>";
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>mybatis-app</artifactId><version>1</version>" + body + "</project>";
    }
}
