package com.huawei.clouds.openrewrite.feigncore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeFeignCoreTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.feigncore.UpgradeFeignCoreTo13_6";
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.feigncore.MigrateFeignCoreTo13_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedFeignCoreDependency());
    }

    @ParameterizedTest(name = "upgrades workbook source {0}")
    @ValueSource(strings = {"10.4.0", "11.1", "11.9", "12", "12.1", "12.4"})
    void upgradesEveryWorkbookSource(String version) {
        if ("10.4.0".equals(version) || "12".equals(version)) {
            rewriteRun(xml(pom(version), pom("13.6"), source -> source.path("pom.xml")));
        } else {
            rewriteRun(pomXml(pom(version), pom("13.6")));
        }
    }

    @Test
    void whitelistExactlyMatchesWorkbook() {
        assertEquals(Set.of("10.4.0", "11.1", "11.9", "12", "12.1", "12.4"),
                UpgradeSelectedFeignCoreDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesDirectPropertyOnlyWhenExclusivelyOwnedByFeignCore() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sftp</artifactId><version>1</version>
                  <properties><feign.version>12.1</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sftp</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void upgradesWhitespacePaddedExclusivePropertyReference() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>spaced</artifactId><version>1</version>
                  <properties><feign.version>12.4</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version> ${feign.version} </version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>spaced</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version> ${feign.version} </version></dependency></dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementAndProfileOwnedDeclarations() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>11.1</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>old-server</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>11.9</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>old-server</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void rootPropertyIsVisibleToProfileDependencies() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>visible</artifactId><version>1</version>
                  <properties><feign.version>11.1</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>visible</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profileOverrideWinsAndDoesNotPoisonSafeRootOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                  <properties><feign.version>11.1</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>shared</id><properties><feign.version>11.9</feign.version></properties><name>shared-${feign.version}</name>
                    <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>shared</id><properties><feign.version>11.9</feign.version></properties><name>shared-${feign.version}</name>
                    <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """));
    }

    @Test
    void independentRootAndProfileOwnersBothUpgrade() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>both</artifactId><version>1</version>
                  <properties><feign.version>12.1</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><feign.version>12.4</feign.version></properties>
                    <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>both</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><feign.version>13.6</feign.version></properties>
                    <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """));
    }

    @Test
    void profilePropertyDoesNotLeakToRootOrSiblingProfile() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>no-leak</artifactId><version>1</version>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles>
                    <profile><id>owner</id><properties><feign.version>12.1</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies></profile>
                    <profile><id>sibling</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies></profile>
                  </profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>no-leak</artifactId><version>1</version>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles>
                    <profile><id>owner</id><properties><feign.version>13.6</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies></profile>
                    <profile><id>sibling</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies></profile>
                  </profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void sharedRootReferenceFromProfilePreventsPropertyAuto() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-profile</artifactId><version>1</version>
                  <properties><feign.version>12.4</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles><profile><id>docs</id><properties><banner>${feign.version}</banner></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void unusedAndDuplicateScopedPropertiesRemainUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ambiguous</artifactId><version>1</version>
                  <properties><unused.feign>12.4</unused.feign></properties>
                  <profiles><profile><id>dup</id><properties><feign.version>12.1</feign.version><feign.version>12.1</feign.version></properties>
                    <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${feign.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """));
    }

    @Test
    void preservesScopeOptionalAndExclusions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>12.4</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>13.6</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """));
    }

    @Test
    void upgradesRealGradleGroovyFixtureFromMiraiFleetAmiya() {
        // Reduced from hundun000/mirai-fleet-amiya at b835b869cc8ab21df921fc784b3cf720c293b305:
        // https://github.com/hundun000/mirai-fleet-amiya/blob/b835b869cc8ab21df921fc784b3cf720c293b305/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'io.github.openfeign:feign-core:11.1' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'io.github.openfeign:feign-core:13.6' }
                """));
    }

    @Test
    void upgradesRealKotlinFixtureFromTerry() {
        // Reduced from boclips/terry at 930ac723c605e975b01ba2bbe351608b06543f26:
        // https://github.com/boclips/terry/blob/930ac723c605e975b01ba2bbe351608b06543f26/build.gradle.kts
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("io.github.openfeign:feign-core:11.9") }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("io.github.openfeign:feign-core:13.6") }
                """));
    }

    @Test
    void upgradesRealGroovyFixtureFromApidocx() {
        // Reduced from lkqm/apidocx at b9f8f4bb5db416f5a22549799c06e7a78446bc70:
        // https://github.com/lkqm/apidocx/blob/b9f8f4bb5db416f5a22549799c06e7a78446bc70/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation("io.github.openfeign:feign-core:12.1") }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation("io.github.openfeign:feign-core:13.6") }
                """));
    }

    @Test
    void upgradesRealRootGroovyFixtureFromGrayalert() {
        // Reduced from grayalert/grayalert at f68525c4ab18d3b43ffa8b1fa985e09ae52fb71b:
        // https://github.com/grayalert/grayalert/blob/f68525c4ab18d3b43ffa8b1fa985e09ae52fb71b/build.gradle
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation 'io.gorse:gorse-client:0.4.0'
                    implementation 'io.github.openfeign:feign-core:12.4'
                    implementation 'org.bouncycastle:bcpkix-jdk15to18:1.75'
                }
                """,
                """
                dependencies {
                    implementation 'io.gorse:gorse-client:0.4.0'
                    implementation 'io.github.openfeign:feign-core:13.6'
                    implementation 'org.bouncycastle:bcpkix-jdk15to18:1.75'
                }
                """));
    }

    @Test
    void nestedProjectDependencyIsDeliberatelyNoop() {
        // The declaration is nested under project(...) and is not owned by this root-only Gradle recipe.
        rewriteRun(buildGradle("""
                project('programming-extensions:programming-extension-vfsprovider') {
                    dependencies {
                        compile 'io.github.openfeign:feign-core:12.4'
                        compile 'commons-io:commons-io:2.16.1'
                    }
                }
                """));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { runtimeOnly group: 'io.github.openfeign', name: 'feign-core', version: '12.1' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { runtimeOnly group: 'io.github.openfeign', name: 'feign-core', version: '13.6' }
                """));
    }

    @ParameterizedTest(name = "nested Gradle DSL is NOOP: {0}")
    @MethodSource("nestedGradleDependencyBlocks")
    void doesNotUpgradeNestedGradleDependencyDsl(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradleDependencyBlocks() {
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath 'io.github.openfeign:feign-core:12.4' } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation 'io.github.openfeign:feign-core:12.4' } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation 'io.github.openfeign:feign-core:12.4' } }"),
                Arguments.of("custom", "company { dependencies { implementation 'io.github.openfeign:feign-core:12.4' } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation 'io.github.openfeign:feign-core:12.4' } }"),
                Arguments.of("plugins", "plugins { dependencies { implementation 'io.github.openfeign:feign-core:12.4' } }"),
                Arguments.of("selected invocation", "dependencies { helper.implementation 'io.github.openfeign:feign-core:12.4' }"));
    }

    @Test
    void kotlinNestedGradleDslIsAlsoNoop() {
        rewriteRun(buildGradleKts("subprojects { dependencies { implementation(\"io.github.openfeign:feign-core:12.4\") } }"));
    }

    @ParameterizedTest(name = "does not guess out-of-workbook version {0}")
    @ValueSource(strings = {"10.3", "10.5", "11.0", "11.2", "12.2", "13.5", "13.7"})
    void leavesExternalVersionsUntouched(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void leavesTargetRangeVersionlessAndVariablesUntouched() {
        rewriteRun(
                pomXml(pom("13.6")),
                xml(pom("[10,13)"), source -> source.path("range/pom.xml")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version><dependencies><dependency>
                          <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId>
                        </dependency></dependencies></project>
                        """, source -> source.path("managed/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        def feignVersion = '12.4'
                        dependencies { implementation "io.github.openfeign:feign-core:${feignVersion}" }
                        """, source -> source.path("variable.gradle")));
    }

    @Test
    void externalFeignBomAndItsVersionlessConsumerRemainUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-owned</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>io.github.openfeign</groupId><artifactId>feign-bom</artifactId><version>12.4</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesSharedPropertyPluginDependencyAndVariantsUntouched() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                          <properties><shared.version>12.1</shared.version></properties><name>ssh-${shared.version}</name>
                          <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>${shared.version}</version></dependency></dependencies>
                        </project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>example</groupId><artifactId>tool</artifactId><version>1</version><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>12.1</version></dependency></dependencies>
                        </plugin></plugins></build></project>
                        """, source -> source.path("plugin/pom.xml")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variant</artifactId><version>1</version><dependencies><dependency>
                          <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>12.1</version><classifier>sources</classifier>
                        </dependency></dependencies></project>
                        """, source -> source.path("variant/pom.xml")));
    }

    @Test
    void requiresRealDependencyDslAndSkipsGeneratedTrees() {
        rewriteRun(
                buildGradle("implementation 'io.github.openfeign:feign-core:12.1'", source -> source.path("outside.gradle")),
                pomXml(pom("12.4"), source -> source.path("target/generated/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'io.github.openfeign:feign-core:12.4' }
                        """, source -> source.path("build/generated/build.gradle")));
    }

    @Test
    void pathFilterUsesCaseInsensitiveParentComponentsButNotLeafName() {
        rewriteRun(
                pomXml(pom("12.4"), source -> source.path("GeneratedSources/pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-core:12.4' }",
                        source -> source.path("INSTALL-cache/build.gradle")),
                pomXml(pom("12.4"), source -> source.path("Node_Modules/tool/pom.xml")),
                pomXml(pom("12.4"), source -> source.path(".pnpm/store/pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-core:12.4' }",
                        "dependencies { implementation 'io.github.openfeign:feign-core:13.6' }",
                        source -> source.path("install.gradle")));
    }

    @Test
    void autoUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("12.4"), pom("13.6")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'io.github.openfeign:feign-core:12.1' }
                        """, """
                        plugins { id 'java' }
                        dependencies { implementation 'io.github.openfeign:feign-core:13.6' }
                        """));
    }

    @Test
    void discoversAndValidatesRecipes() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feigncore").build();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertEquals(UPGRADE, upgrade.getName());
        assertEquals(MIGRATE, migrate.getName());
        assertEquals(UPGRADE, migrate.getRecipeList().get(0).getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>feign-app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>io.github.openfeign</groupId><artifactId>feign-core</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }
}
