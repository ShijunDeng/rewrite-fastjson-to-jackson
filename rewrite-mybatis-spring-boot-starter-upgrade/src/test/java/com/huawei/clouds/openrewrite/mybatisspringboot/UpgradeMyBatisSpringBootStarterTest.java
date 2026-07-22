package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeMyBatisSpringBootStarterTest implements RewriteTest {
    static final String UPGRADE =
            "com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4_0_0";
    static final String MIGRATE =
            "com.huawei.clouds.openrewrite.mybatisspringboot.MigrateMyBatisSpringBootStarterTo4_0_0";
    private static final String[] LISTED_VERSIONS = {
            "1.1.1", "1.3.2", "2.0.0", "2.1.2", "2.1.3", "2.1.4",
            "2.2.0", "2.2.2", "2.3.0", "2.3.1"
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void upgradesEverySpreadsheetSourceVersionAndIsIdempotent() {
        for (String version : LISTED_VERSIONS) {
            rewriteRun(
                    spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                    pomXml(pomWithVersion(version), pomWithVersion("4.0.0"))
            );
        }
    }

    @Test
    void upgradesLocalManagedVersionWithoutAddingAnOverride() {
        rewriteRun(
                pomXml(
                        managedPom("2.3.1"),
                        managedPom("4.0.0")
                )
        );
    }

    @Test
    void upgradesGradleStringNotation() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.1' }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0' }
                        """
                )
        );
    }

    @Test
    void upgradesGradleGroovyMapKotlinDslAndFamilyCoordinates() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        dependencies {
                            api group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter', version: '2.2.0'
                            testImplementation group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter-test', version: '2.2.0'
                        }
                        """,
                        """
                        plugins { id 'java-library' }
                        dependencies {
                            api group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter', version: '4.0.0'
                            testImplementation group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter-test', version: '4.0.0'
                        }
                        """,
                        source -> source.path("map/build.gradle")
                ),
                buildGradleKts(
                        """
                        plugins { java }
                        dependencies {
                            implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:2.1.4")
                            testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.1.4")
                        }
                        """,
                        """
                        plugins { java }
                        dependencies {
                            implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0")
                            testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.0")
                        }
                        """,
                        source -> source.path("kotlin/build.gradle.kts")
                )
        );
    }

    @Test
    void leavesGradleVariablesRangesUnlistedCompanionsAndUnrelatedArtifactsUntouched() {
        rewriteRun(
                buildGradle("""
                        plugins { id 'java' }
                        def mybatisVersion = '2.3.1'
                        dependencies {
                            implementation "org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisVersion"
                            implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:[2.2,3.0)'
                            implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1'
                            implementation 'org.mybatis:mybatis:2.3.1'
                        }
                        """),
                buildGradleKts("""
                        plugins { java }
                        val mybatisVersion = "2.3.1"
                        dependencies { implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisVersion") }
                        """, source -> source.path("variables/build.gradle.kts"))
        );
    }

    @Test
    void alignsExplicitCompanionModulesWhenStarterIsListed() {
        rewriteRun(
                pomXml(
                        companionPom("2.3.1"),
                        companionPom("4.0.0")
                )
        );
    }

    @Test
    void doesNotDowngradeUnlistedCompanionWhenSelectedStarterUpgrades() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed-family</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version></dependency>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>3.0.4</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed-family</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>4.0.0</version></dependency>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>3.0.4</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesFamilyOwnedMavenVersionProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>family-property</artifactId><version>1</version>
                  <properties><mybatis-starter.version>2.3.1</mybatis-starter.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis-starter.version}</version></dependency>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>${mybatis-starter.version}</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>family-property</artifactId><version>1</version>
                  <properties><mybatis-starter.version>4.0.0</mybatis-starter.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis-starter.version}</version></dependency>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>${mybatis-starter.version}</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesMavenPropertySharedOutsideStarterFamily() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-property</artifactId><version>1</version>
                  <name>${mybatis-starter.version}</name>
                  <properties><mybatis-starter.version>2.3.1</mybatis-starter.version></properties>
                  <dependencies><dependency>
                    <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis-starter.version}</version>
                  </dependency></dependencies>
                </project>
        """));
    }

    @Test
    void preservesPropertySharedWithAnotherDependency() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-dependency-property</artifactId><version>1</version>
                  <properties><shared.version>2.0.0</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${shared.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void upgradesSelectedStarterInsideMavenProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>integration</id><dependencies><dependency>
                    <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>1.3.2</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>integration</id><dependencies><dependency>
                    <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>4.0.0</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void leavesUnlistedTargetAndNewerVersionsUnchanged() {
        rewriteRun(
                pomXml(pomWithVersion("2.3.2")),
                pomXml(pomWithVersion("3.0.0")),
                pomXml(pomWithVersion("3.0.4")),
                pomXml(pomWithVersion("4.0.0")),
                pomXml(pomWithVersion("4.1.0"))
        );
    }

    @Test
    void doesNotOverrideExternallyManagedStarter() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>externally-managed</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.mybatis.spring.boot</groupId>
                                <artifactId>mybatis-spring-boot</artifactId>
                                <version>2.3.2</version>
                                <type>pom</type>
                                <scope>import</scope>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>org.mybatis.spring.boot</groupId>
                              <artifactId>mybatis-spring-boot-starter</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void leavesGeneratedBuildDescriptorsUntouched() {
        rewriteRun(
                pomXml(pomWithVersion("2.3.1"), source -> source.path("target/pom.xml")),
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.1' }",
                        source -> source.path("build/generated/build.gradle")
                )
        );
    }

    @Test
    void excludesPluginDependenciesAndCustomArtifactShapesFromMavenOwnership() {
        rewriteRun(
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-owned</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version>
                          </dependency></dependencies>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><version>1</version>
                            <dependencies><dependency>
                              <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>2.3.1</version>
                            </dependency></dependencies>
                          </plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-owned</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>4.0.0</version>
                          </dependency></dependencies>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><version>1</version>
                            <dependencies><dependency>
                              <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter-test</artifactId><version>2.3.1</version>
                            </dependency></dependencies>
                          </plugin></plugins></build>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                ),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>custom</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version><classifier>tests</classifier></dependency>
                            <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version><type>pom</type></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("custom/pom.xml"))
                ,
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unowned</artifactId><version>1</version>
                          <configuration><dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>2.3.1</version>
                          </dependency></dependencies></configuration>
                        </project>
                        """, source -> source.path("unowned/pom.xml"))
                ,
                buildGradle("""
                        plugins { id 'java' }
                        dependencies {
                            implementation group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter', version: '2.3.1', classifier: 'tests'
                        }
                        """, source -> source.path("variant/build.gradle"))
        );
    }

    @Test
    void leavesProfileShadowedAndSharedMavenPropertiesUntouched() {
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shadowed</artifactId><version>1</version>
                  <properties><mybatis.version>2.3.1</mybatis.version></properties>
                  <profiles><profile><id>newer</id>
                    <properties><mybatis.version>3.0.4</mybatis.version></properties>
                    <dependencies><dependency>
                      <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis.version}</version>
                    </dependency></dependencies>
                  </profile></profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void onlyChangesCoordinatesOwnedByGradleDependencyConfigurations() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                def documentation = 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1'
                dependencies {
                    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.1'
                    testImplementation group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter-test', version: '2.3.1', classifier: 'tests'
                }
                implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1'
                println('org.mybatis.spring.boot:mybatis-spring-boot-autoconfigure:2.3.1')
                """,
                """
                plugins { id 'java' }
                def documentation = 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1'
                dependencies {
                    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0'
                    testImplementation group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter-test', version: '2.3.1', classifier: 'tests'
                }
                implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.3.1'
                println('org.mybatis.spring.boot:mybatis-spring-boot-autoconfigure:2.3.1')
                """
        ));
    }

    @Test
    void gothinksterRealworldGradleShapeAlignsStarterAndTestModule() {
        // Reduced from gothinkster/spring-boot-realworld-example-app at ee17e31aafe733d98c4853c8b9a74d7f2f6c924a:
        // https://github.com/gothinkster/spring-boot-realworld-example-app/blob/ee17e31aafe733d98c4853c8b9a74d7f2f6c924a/build.gradle#L38-L56
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                dependencies {
                    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.2.2'
                    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.2.2'
                }
                """,
                """
                plugins { id 'java' }
                dependencies {
                    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0'
                    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.0'
                }
                """
        ));
    }

    @Test
    void springVulnBootRealPomShapeAlignsStarterAndTestModule() {
        // Reduced from bansh2eBreak/SpringVulnBoot-backend at 7b21c495eb5b39e803d3cf4a40f1ac31b12b979f:
        // https://github.com/bansh2eBreak/SpringVulnBoot-backend/blob/7b21c495eb5b39e803d3cf4a40f1ac31b12b979f/pom.xml#L53-L109
        rewriteRun(pomXml(companionPom("2.3.1"), companionPom("4.0.0")));
    }

    @Test
    void layImRealGradleCompileShapeUpgrades() {
        // Reduced from scalad/LayIM at c6affbbaccfd0afd4f8dda54d9b196c226463ac2:
        // https://github.com/scalad/LayIM/blob/c6affbbaccfd0afd4f8dda54d9b196c226463ac2/build.gradle#L66-L69
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies { compile (\"org.mybatis.spring.boot:mybatis-spring-boot-starter:1.1.1\") }",
                "plugins { id 'java' }\ndependencies { compile (\"org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0\") }"
        ));
    }

    @Test
    void marksMavenJavaAndSpringBootPlatformBlockersPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindMyBatisStarterMavenPlatformRisks()),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.5.4</version>
                          </parent>
                          <groupId>example</groupId>
                          <artifactId>platform-blocked</artifactId>
                          <version>1.0.0</version>
                          <properties>
                            <java.version>11</java.version>
                          </properties>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId>
                            <artifactId>mybatis-spring-boot-starter</artifactId>
                            <version>4.0.0</version>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <!--~~(MyBatis Spring Boot Starter 4 requires Spring Boot 4.0 or newer; upgrade the parent before the starter)~~>--><parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.5.4</version>
                          </parent>
                          <groupId>example</groupId>
                          <artifactId>platform-blocked</artifactId>
                          <version>1.0.0</version>
                          <properties>
                            <!--~~(MyBatis Spring Boot Starter 4 requires Java 17 or newer; upgrade the compiler and runtime together)~~>--><java.version>11</java.version>
                          </properties>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId>
                            <artifactId>mybatis-spring-boot-starter</artifactId>
                            <version>4.0.0</version>
                          </dependency></dependencies>
                        </project>
                        """
                ),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>4.0.0</version>
                          </parent>
                          <groupId>example</groupId>
                          <artifactId>platform-ready</artifactId>
                          <version>1.0.0</version>
                          <properties><java.version>17</java.version></properties>
                          <dependencies><dependency>
                            <groupId>org.mybatis.spring.boot</groupId>
                            <artifactId>mybatis-spring-boot-starter</artifactId>
                            <version>4.0.0</version>
                          </dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesPublicRecipes() {
        Environment environment = environment();
        assertEquals(10, UpgradeSelectedMyBatisSpringBootStarterDependency.SOURCE_VERSIONS.size());
        for (String name : new String[]{
                UPGRADE,
                MIGRATE,
                "com.huawei.clouds.openrewrite.mybatisspringboot.AuditMyBatisSpringBootStarter4Build",
                "com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4"
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    private static String pomWithVersion(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId>
                 <artifactId>mybatis-app</artifactId>
                 <version>1.0.0</version>
                 <dependencies>
                   <dependency>
                     <groupId>org.mybatis.spring.boot</groupId>
                     <artifactId>mybatis-spring-boot-starter</artifactId>
                     <version>%s</version>
                   </dependency>
                 </dependencies>
               </project>
               """.formatted(version);
    }

    private static String managedPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId>
                 <artifactId>managed-mybatis-app</artifactId>
                 <version>1.0.0</version>
                 <dependencyManagement>
                   <dependencies>
                     <dependency>
                       <groupId>org.mybatis.spring.boot</groupId>
                       <artifactId>mybatis-spring-boot-starter</artifactId>
                       <version>%s</version>
                     </dependency>
                   </dependencies>
                 </dependencyManagement>
                 <dependencies>
                   <dependency>
                     <groupId>org.mybatis.spring.boot</groupId>
                     <artifactId>mybatis-spring-boot-starter</artifactId>
                   </dependency>
                 </dependencies>
               </project>
               """.formatted(version);
    }

    private static String companionPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId>
                 <artifactId>aligned-mybatis-app</artifactId>
                 <version>1.0.0</version>
                 <dependencies>
                   <dependency>
                     <groupId>org.mybatis.spring.boot</groupId>
                     <artifactId>mybatis-spring-boot-starter</artifactId>
                     <version>%1$s</version>
                   </dependency>
                   <dependency>
                     <groupId>org.mybatis.spring.boot</groupId>
                     <artifactId>mybatis-spring-boot-autoconfigure</artifactId>
                     <version>%1$s</version>
                   </dependency>
                   <dependency>
                     <groupId>org.mybatis.spring.boot</groupId>
                     <artifactId>mybatis-spring-boot-test-autoconfigure</artifactId>
                     <version>%1$s</version>
                     <scope>test</scope>
                   </dependency>
                   <dependency>
                     <groupId>org.mybatis.spring.boot</groupId>
                     <artifactId>mybatis-spring-boot-starter-test</artifactId>
                     <version>%1$s</version>
                     <scope>test</scope>
                   </dependency>
                 </dependencies>
               </project>
               """.formatted(version);
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.mybatisspringboot")
                .scanYamlResources()
                .build();
    }
}
