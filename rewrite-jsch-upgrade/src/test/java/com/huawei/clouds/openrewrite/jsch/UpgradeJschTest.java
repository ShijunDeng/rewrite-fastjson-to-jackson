package com.huawei.clouds.openrewrite.jsch;

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

class UpgradeJschTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.jsch.UpgradeJschTo2_27_7";
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.jsch.MigrateJschTo2_27_7";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedJschDependency());
    }

    @ParameterizedTest(name = "upgrades workbook source {0}")
    @ValueSource(strings = {"0.1.55", "0.1.70", "0.2.3", "0.2.7", "0.2.9"})
    void upgradesEveryWorkbookSource(String version) {
        if ("0.1.55".equals(version)) {
            rewriteRun(xml(pom(version), pom("2.27.7"), source -> source.path("pom.xml")));
        } else {
            rewriteRun(pomXml(pom(version), pom("2.27.7")));
        }
    }

    @Test
    void whitelistExactlyMatchesWorkbook() {
        assertEquals(Set.of("0.1.55", "0.1.70", "0.2.3", "0.2.7", "0.2.9"),
                UpgradeSelectedJschDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesDirectPropertyOnlyWhenExclusivelyOwnedByJsch() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sftp</artifactId><version>1</version>
                  <properties><jsch.version>0.2.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sftp</artifactId><version>1</version>
                  <properties><jsch.version>2.27.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void upgradesWhitespacePaddedExclusivePropertyReference() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>spaced</artifactId><version>1</version>
                  <properties><jsch.version>0.2.9</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version> ${jsch.version} </version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>spaced</artifactId><version>1</version>
                  <properties><jsch.version>2.27.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version> ${jsch.version} </version></dependency></dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementAndProfileOwnedDeclarations() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.1.70</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>old-server</id><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.2.3</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>2.27.7</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>old-server</id><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>2.27.7</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void rootPropertyIsVisibleToProfileDependencies() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>visible</artifactId><version>1</version>
                  <properties><jsch.version>0.1.70</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>visible</artifactId><version>1</version>
                  <properties><jsch.version>2.27.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>ci</id><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profileOverrideWinsAndDoesNotPoisonSafeRootOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                  <properties><jsch.version>0.1.70</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>shared</id><properties><jsch.version>0.2.3</jsch.version></properties><name>shared-${jsch.version}</name>
                    <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                  <properties><jsch.version>2.27.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>shared</id><properties><jsch.version>0.2.3</jsch.version></properties><name>shared-${jsch.version}</name>
                    <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """));
    }

    @Test
    void independentRootAndProfileOwnersBothUpgrade() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>both</artifactId><version>1</version>
                  <properties><jsch.version>0.2.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><jsch.version>0.2.9</jsch.version></properties>
                    <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>both</artifactId><version>1</version>
                  <properties><jsch.version>2.27.7</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>legacy</id><properties><jsch.version>2.27.7</jsch.version></properties>
                    <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """));
    }

    @Test
    void profilePropertyDoesNotLeakToRootOrSiblingProfile() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>no-leak</artifactId><version>1</version>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles>
                    <profile><id>owner</id><properties><jsch.version>0.2.7</jsch.version></properties><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies></profile>
                    <profile><id>sibling</id><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies></profile>
                  </profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>no-leak</artifactId><version>1</version>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles>
                    <profile><id>owner</id><properties><jsch.version>2.27.7</jsch.version></properties><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies></profile>
                    <profile><id>sibling</id><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies></profile>
                  </profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void sharedRootReferenceFromProfilePreventsPropertyAuto() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-profile</artifactId><version>1</version>
                  <properties><jsch.version>0.2.9</jsch.version></properties>
                  <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  <profiles><profile><id>docs</id><properties><banner>${jsch.version}</banner></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void unusedAndDuplicateScopedPropertiesRemainUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ambiguous</artifactId><version>1</version>
                  <properties><unused.jsch>0.2.9</unused.jsch></properties>
                  <profiles><profile><id>dup</id><properties><jsch.version>0.2.7</jsch.version><jsch.version>0.2.7</jsch.version></properties>
                    <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """));
    }

    @Test
    void preservesScopeOptionalAndExclusions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.2.9</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>2.27.7</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """));
    }

    @Test
    void upgradesRealGradleGroovyFixtureFromBteMover() {
        // Reduced from DavixDevelop/bte-mover at 7af9f7592d104cc5b4a8eecc4e44dc6bc6d59f35:
        // https://github.com/DavixDevelop/bte-mover/blob/7af9f7592d104cc5b4a8eecc4e44dc6bc6d59f35/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'com.github.mwiede:jsch:0.1.70' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'com.github.mwiede:jsch:2.27.7' }
                """));
    }

    @Test
    void upgradesRealKotlinFixtureFromBaroka() {
        // Reduced from Darren4641/Baroka at 1e0bd1c1a10dcd47a5ce559e0734dc678dfea203:
        // https://github.com/Darren4641/Baroka/blob/1e0bd1c1a10dcd47a5ce559e0734dc678dfea203/build.gradle.kts
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("com.github.mwiede:jsch:0.2.3") }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("com.github.mwiede:jsch:2.27.7") }
                """));
    }

    @Test
    void upgradesRealGroovyFixtureFromCapstone() {
        // Reduced from doubleclip118/kkoejoejoe-Capstone at 0855c10229c6548632a5d8a7597cb505297d2b39:
        // https://github.com/doubleclip118/kkoejoejoe-Capstone/blob/0855c10229c6548632a5d8a7597cb505297d2b39/back/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation("com.github.mwiede:jsch:0.2.7") }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation("com.github.mwiede:jsch:2.27.7") }
                """));
    }

    @Test
    void upgradesRealRootGroovyFixtureFromCsciProject() {
        // Reduced from IssacL891/CSCI-320-Final-Project at af630840c4be4bf255799c1e53a40498f83d6c59:
        // https://github.com/IssacL891/CSCI-320-Final-Project/blob/af630840c4be4bf255799c1e53a40498f83d6c59/CLI/build.gradle
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation 'io.gorse:gorse-client:0.4.0'
                    implementation 'com.github.mwiede:jsch:0.2.9'
                    implementation 'org.bouncycastle:bcpkix-jdk15to18:1.75'
                }
                """,
                """
                dependencies {
                    implementation 'io.gorse:gorse-client:0.4.0'
                    implementation 'com.github.mwiede:jsch:2.27.7'
                    implementation 'org.bouncycastle:bcpkix-jdk15to18:1.75'
                }
                """));
    }

    @Test
    void realOw2NestedProjectDependencyIsDeliberatelyNoop() {
        // Reduced from ow2-proactive/programming at f1de44d9643f85c32cbe9d4e6e8147ca4a618f6a:
        // the declaration is nested under project(...) and is not owned by this root-only Gradle recipe.
        rewriteRun(buildGradle("""
                project('programming-extensions:programming-extension-vfsprovider') {
                    dependencies {
                        compile 'com.github.mwiede:jsch:0.2.9'
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
                dependencies { runtimeOnly group: 'com.github.mwiede', name: 'jsch', version: '0.2.7' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { runtimeOnly group: 'com.github.mwiede', name: 'jsch', version: '2.27.7' }
                """));
    }

    @ParameterizedTest(name = "nested Gradle DSL is NOOP: {0}")
    @MethodSource("nestedGradleDependencyBlocks")
    void doesNotUpgradeNestedGradleDependencyDsl(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradleDependencyBlocks() {
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath 'com.github.mwiede:jsch:0.2.9' } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation 'com.github.mwiede:jsch:0.2.9' } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation 'com.github.mwiede:jsch:0.2.9' } }"),
                Arguments.of("custom", "company { dependencies { implementation 'com.github.mwiede:jsch:0.2.9' } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation 'com.github.mwiede:jsch:0.2.9' } }"),
                Arguments.of("plugins", "plugins { dependencies { implementation 'com.github.mwiede:jsch:0.2.9' } }"),
                Arguments.of("selected invocation", "dependencies { helper.implementation 'com.github.mwiede:jsch:0.2.9' }"));
    }

    @Test
    void kotlinNestedGradleDslIsAlsoNoop() {
        rewriteRun(buildGradleKts("subprojects { dependencies { implementation(\"com.github.mwiede:jsch:0.2.9\") } }"));
    }

    @ParameterizedTest(name = "does not guess out-of-workbook version {0}")
    @ValueSource(strings = {"0.1.54", "0.1.56", "0.2.2", "0.2.10", "2.27.6", "2.28.0"})
    void leavesExternalVersionsUntouched(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void leavesTargetRangeVersionlessAndVariablesUntouched() {
        rewriteRun(
                pomXml(pom("2.27.7")),
                xml(pom("[0.2,2.0)"), source -> source.path("range/pom.xml")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId>
                        </dependency></dependencies></project>
                        """, source -> source.path("managed/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        def jschVersion = '0.2.9'
                        dependencies { implementation "com.github.mwiede:jsch:${jschVersion}" }
                        """, source -> source.path("variable.gradle")));
    }

    @Test
    void leavesSharedPropertyPluginDependencyAndVariantsUntouched() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                          <properties><shared.version>0.2.7</shared.version></properties><name>ssh-${shared.version}</name>
                          <dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${shared.version}</version></dependency></dependencies>
                        </project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>example</groupId><artifactId>tool</artifactId><version>1</version><dependencies><dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.2.7</version></dependency></dependencies>
                        </plugin></plugins></build></project>
                        """, source -> source.path("plugin/pom.xml")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variant</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.2.7</version><classifier>sources</classifier>
                        </dependency></dependencies></project>
                        """, source -> source.path("variant/pom.xml")));
    }

    @Test
    void requiresRealDependencyDslAndSkipsGeneratedTrees() {
        rewriteRun(
                buildGradle("implementation 'com.github.mwiede:jsch:0.2.7'", source -> source.path("outside.gradle")),
                pomXml(pom("0.2.9"), source -> source.path("target/generated/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'com.github.mwiede:jsch:0.2.9' }
                        """, source -> source.path("build/generated/build.gradle")));
    }

    @Test
    void pathFilterUsesCaseInsensitiveParentComponentsButNotLeafName() {
        rewriteRun(
                pomXml(pom("0.2.9"), source -> source.path("GeneratedSources/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.mwiede:jsch:0.2.9' }",
                        source -> source.path("INSTALL-cache/build.gradle")),
                pomXml(pom("0.2.9"), source -> source.path("Node_Modules/tool/pom.xml")),
                pomXml(pom("0.2.9"), source -> source.path(".pnpm/store/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.mwiede:jsch:0.2.9' }",
                        "dependencies { implementation 'com.github.mwiede:jsch:2.27.7' }",
                        source -> source.path("install.gradle")));
    }

    @Test
    void autoUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("0.2.9"), pom("2.27.7")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'com.github.mwiede:jsch:0.2.7' }
                        """, """
                        plugins { id 'java' }
                        dependencies { implementation 'com.github.mwiede:jsch:2.27.7' }
                        """));
    }

    @Test
    void discoversAndValidatesRecipes() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jsch").build();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertEquals(UPGRADE, upgrade.getName());
        assertEquals(MIGRATE, migrate.getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jsch-app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }
}
