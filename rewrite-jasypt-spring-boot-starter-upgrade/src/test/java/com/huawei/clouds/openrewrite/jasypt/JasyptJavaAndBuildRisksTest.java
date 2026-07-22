package com.huawei.clouds.openrewrite.jasypt;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
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

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jasypt")
                .scanYamlResources()
                .build();
    }
}
