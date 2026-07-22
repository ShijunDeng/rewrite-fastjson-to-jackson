package com.huawei.clouds.openrewrite.jasypt;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class JasyptJavaAndBuildRisksTest implements RewriteTest {
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.jasypt.MigrateJasyptSpringBootStarterTo4_0_3";

    @Test
    void migratesBothStarterAutoConfigurationPackages() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package com.ulisesbocchio.jasyptspringboot;
                                public class JasyptSpringBootAutoConfiguration {}
                                """,
                                """
                                package com.ulisesbocchio.jasyptspringboot;
                                public class JasyptSpringCloudBootstrapConfiguration {}
                                """)),
                java(
                        """
                        package example;

                        import com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration;
                        import com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration;

                        class Imports {
                            JasyptSpringBootAutoConfiguration auto;
                            JasyptSpringCloudBootstrapConfiguration bootstrap;
                        }
                        """,
                        """
                        package example;

                        import com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration;
                        import com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringCloudBootstrapConfiguration;

                        class Imports {
                            JasyptSpringBootAutoConfiguration auto;
                            JasyptSpringCloudBootstrapConfiguration bootstrap;
                        }
                        """
                )
        );
    }

    @Test
    void marksCustomExtensionAndManualPbeTuple() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptJavaRisks())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package com.ulisesbocchio.jasyptspringboot;
                                public interface EncryptablePropertyDetector {}
                                """,
                                """
                                package org.jasypt.encryption.pbe.config;
                                public class SimpleStringPBEConfig {
                                    public void setAlgorithm(String value) {}
                                    public void setPassword(String value) {}
                                }
                                """)),
                java(
                        """
                        package example;

                        import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
                        import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

                        class Detector implements EncryptablePropertyDetector {
                            void configure(SimpleStringPBEConfig config) {
                                config.setAlgorithm("PBEWithMD5AndDES");
                                config.setPassword("test-only-placeholder");
                            }
                        }
                        """,
                        """
                        package example;

                        import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
                        import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

                        /*~~(Custom Jasypt detector/resolver/filter owns decryption semantics; recompile on Java 17/Boot 3.5 and test bean selection, ordering, negative matches, and recursion)~~>*/class Detector implements EncryptablePropertyDetector {
                            void configure(SimpleStringPBEConfig config) {
                                /*~~(Manual PBE setting is part of one compatibility tuple; test algorithm, IV, salt, iterations, provider, pool, output encoding, and old ciphertext together)~~>*/config.setAlgorithm("PBEWithMD5AndDES");
                                /*~~(Hardcoded Jasypt password in Java source; move it to injected secret material and rotate it)~~>*/config.setPassword("test-only-placeholder");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksOnlyVisibleMavenBaselinesAndLegacyStarter() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                xml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.4.9</version>
                          </parent>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><java.version>11</java.version></properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>2.1.2</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <!--~~(Jasypt 4.0.3 requires Spring Boot 3.5.0 or newer)~~>--><parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.4.9</version>
                          </parent>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><!--~~(Jasypt 4.0.3 requires Java 17 or newer across build, runtime, container, and CI toolchains)~~>--><java.version>11</java.version></properties>
                          <dependencies>
                            <!--~~(Jasypt 2.x ciphertext used PBEWithMD5AndDES and NoIvGenerator by default; declare the legacy pair explicitly for compatibility, then re-encrypt with the target defaults)~~>--><dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>2.1.2</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void marksGradleJavaBaselinesWithoutMarkingUnrelatedJavaVersionConstants() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        sourceCompatibility = JavaVersion.VERSION_11
                        java {
                            toolchain {
                                languageVersion = JavaLanguageVersion.of(8)
                            }
                        }
                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
                        }
                        def unrelated = JavaVersion.VERSION_1_8
                        """,
                        """
                        plugins { id 'java' }
                        sourceCompatibility = /*~~(Jasypt 4.0.3 requires Java 17 or newer)~~>*/JavaVersion.VERSION_11
                        java {
                            toolchain {
                                languageVersion = /*~~(Jasypt 4.0.3 requires Java 17 or newer)~~>*/JavaLanguageVersion.of(8)
                            }
                        }
                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
                        }
                        def unrelated = JavaVersion.VERSION_1_8
                        """
                )
        );
    }

    @Test
    void auditsOnlyConcreteJasyptPluginKeysAndBuildCommandExposure() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                xml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <build><plugins><plugin>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-maven-plugin</artifactId>
                            <configuration>
                              <password>${JASYPT_PASSWORD}</password>
                              <oldPassword>migration-placeholder</oldPassword>
                            </configuration>
                          </plugin><plugin>
                            <groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>
                            <configuration><argLine>-Djasypt.encryptor.password=${JASYPT_PASSWORD}</argLine></configuration>
                          </plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <build><plugins><plugin>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-maven-plugin</artifactId>
                            <configuration>
                              <password>${JASYPT_PASSWORD}</password>
                              <!--~~(Jasypt Maven plugin key material is visible in the POM; move it to a secret-store reference, suppress command echo, and rotate exposed values)~~>--><oldPassword>migration-placeholder</oldPassword>
                            </configuration>
                          </plugin><plugin>
                            <groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>
                            <configuration><!--~~(Jasypt password on a build command line may leak through process listings, CI logs, and diagnostics; use masked secret injection)~~>--><argLine>-Djasypt.encryptor.password=${JASYPT_PASSWORD}</argLine></configuration>
                          </plugin></plugins></build>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void recommendedRecipePerformsBuildConfigAndSourceAutoTogether() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION))
                        .cycles(2).expectedCyclesThatMakeChanges(1)
                        .parser(JavaParser.fromJavaVersion().dependsOn("""
                                package com.ulisesbocchio.jasyptspringboot;
                                public class JasyptSpringBootAutoConfiguration {}
                                """)),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>complete-migration</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId><version>3.0.5</version>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>complete-migration</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId><version>4.0.3</version>
                          </dependency></dependencies>
                        </project>
                        """),
                properties(
                        "jasypt.encryptor.poolSize=2\n",
                        "jasypt.encryptor.pool-size=2\n"),
                java(
                        """
                        package example;
                        import com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration;
                        class App { JasyptSpringBootAutoConfiguration configuration; }
                        """,
                        """
                        package example;

                        import com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration;

                        class App { JasyptSpringBootAutoConfiguration configuration; }
                        """)
        );
    }

    @Test
    void generatedJavaIsNeitherMigratedNorMarked() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION))
                        .parser(JavaParser.fromJavaVersion().dependsOn("""
                                package com.ulisesbocchio.jasyptspringboot;
                                public class JasyptSpringBootAutoConfiguration {}
                                """)),
                java(
                        """
                        package generated;
                        import com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration;
                        class Generated { JasyptSpringBootAutoConfiguration configuration; }
                        """,
                        source -> source.path("target/generated-sources/Generated.java"))
        );
    }

    @Test
    void marksExactReflectiveMovedTypeButNotDocumentationContainingIt() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptJavaRisks()),
                java(
                        """
                        class Reflection {
                            String exact = "com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration";
                            String prose = "See com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration for history";
                        }
                        """,
                        """
                        class Reflection {
                            String exact = /*~~(Reflective Jasypt auto-configuration reference uses the pre-4.0 package; update the exact class name and test metadata loading)~~>*/"com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration";
                            String prose = "See com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration for history";
                        }
                        """)
        );
    }

    @Test
    void marksManualGcmAndAsymmetricConfigurationFromOfficialExamples() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptJavaRisks())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package com.ulisesbocchio.jasyptspringboot.encryptor;
                                public class SimpleGCMConfig {
                                    public void setSecretKey(String value) {}
                                    public void setSecretKeyIterations(int value) {}
                                }
                                """,
                                """
                                package com.ulisesbocchio.jasyptspringboot.encryptor;
                                public class SimpleAsymmetricConfig {
                                    public void setPrivateKey(String value) {}
                                }
                                """)),
                java(
                        """
                        import com.ulisesbocchio.jasyptspringboot.encryptor.SimpleAsymmetricConfig;
                        import com.ulisesbocchio.jasyptspringboot.encryptor.SimpleGCMConfig;
                        class Crypto {
                            void configure(SimpleGCMConfig gcm, SimpleAsymmetricConfig asymmetric) {
                                gcm.setSecretKey("test-key-placeholder");
                                gcm.setSecretKeyIterations(1000);
                                asymmetric.setPrivateKey("test-private-key-placeholder");
                            }
                        }
                        """,
                        """
                        import com.ulisesbocchio.jasyptspringboot.encryptor.SimpleAsymmetricConfig;
                        import com.ulisesbocchio.jasyptspringboot.encryptor.SimpleGCMConfig;
                        class Crypto {
                            void configure(SimpleGCMConfig gcm, SimpleAsymmetricConfig asymmetric) {
                                /*~~(Hardcoded GCM/asymmetric key material in Java source; move it to protected injected material and rotate exposed values)~~>*/gcm.setSecretKey("test-key-placeholder");
                                /*~~(Manual GCM/asymmetric configuration must be verified as one key/IV/salt/iterations/algorithm/format tuple with existing ciphertext)~~>*/gcm.setSecretKeyIterations(1000);
                                /*~~(Hardcoded GCM/asymmetric key material in Java source; move it to protected injected material and rotate exposed values)~~>*/asymmetric.setPrivateKey("test-private-key-placeholder");
                            }
                        }
                        """)
        );
    }

    @Test
    void doesNotMarkUnrelatedLowJavaBaselineWithoutStarter() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                buildGradle("""
                        plugins { id 'java' }
                        sourceCompatibility = JavaVersion.VERSION_11
                        """)
        );
    }

    @Test
    void marksOnlyActualVersionlessGradleStarterDependency() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        def docs = 'com.github.ulisesbocchio:jasypt-spring-boot-starter'
                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter'
                            testImplementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.6'
                            runtimeOnly group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '3.0.5', ext: 'zip'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        def docs = 'com.github.ulisesbocchio:jasypt-spring-boot-starter'
                        dependencies {
                            implementation /*~~(Starter version is externally managed; the strict upgrade recipe will not override an invisible platform/catalog value)~~>*/'com.github.ulisesbocchio:jasypt-spring-boot-starter'
                            testImplementation /*~~(Starter remains on an unselected, ranged, dynamic, property-managed, or non-target version; choose 4.0.3 explicitly or migrate its central owner)~~>*/'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.6'
                            /*~~(This classified or non-JAR starter artifact is outside deterministic runtime upgrade scope; verify that 4.0.3 publishes the same artifact shape before migrating it)~~>*/runtimeOnly group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '3.0.5', ext: 'zip'
                        }
                        """)
        );
    }

    @Test
    void marksVersionlessStarterAndOldBootBomAtTheirExactMavenNodes() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                xml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>managed-app</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>
                            <version>3.4.9</version><type>pom</type><scope>import</scope>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>managed-app</artifactId><version>1</version>
                          <dependencyManagement><dependencies><!--~~(Imported Spring Boot BOM is below the Jasypt 4.0.3 minimum of 3.5.0)~~>--><dependency>
                            <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>
                            <version>3.4.9</version><type>pom</type><scope>import</scope>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><!--~~(Starter version is externally managed; the strict upgrade recipe will not override an invisible parent/BOM value)~~>--><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId>
                          </dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")),
                xml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>custom-artifact</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId>
                            <version>3.0.5</version><type>test-jar</type>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>custom-artifact</artifactId><version>1</version>
                          <dependencies><!--~~(This classified or non-JAR starter artifact is outside deterministic runtime upgrade scope; verify that 4.0.3 publishes the same artifact shape before migrating it)~~>--><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId>
                            <version>3.0.5</version><type>test-jar</type>
                          </dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("custom/pom.xml"))
        );
    }

    @Test
    void marksOldGradleBootPluginOnlyWhenStarterIsPresent() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                buildGradle(
                        """
                        plugins {
                            id 'org.springframework.boot' version '3.4.9'
                            id 'java'
                        }
                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
                        }
                        """,
                        """
                        plugins {
                            id 'org.springframework.boot' version /*~~(Jasypt 4.0.3 requires Spring Boot 3.5.0 or newer)~~>*/'3.4.9'
                            id 'java'
                        }
                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
                        }
                        """)
        );
    }

    @Test
    void dynamicAndClassifierGradleDeclarationsDoNotOwnBaselineReview() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptBuildCompatibilityRisks()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        sourceCompatibility = JavaVersion.VERSION_11
                        def jasyptVersion = '3.0.5'
                        dependencies {
                            implementation "com.github.ulisesbocchio:jasypt-spring-boot-starter:$jasyptVersion"
                            testImplementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5:tests'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        sourceCompatibility = JavaVersion.VERSION_11
                        def jasyptVersion = '3.0.5'
                        dependencies {
                            implementation "com.github.ulisesbocchio:jasypt-spring-boot-starter:$jasyptVersion"
                            testImplementation /*~~(This classified or non-JAR starter artifact is outside deterministic runtime upgrade scope; verify that 4.0.3 publishes the same artifact shape before migrating it)~~>*/'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5:tests'
                        }
                        """)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jasypt")
                .scanYamlResources()
                .build();
    }
}
