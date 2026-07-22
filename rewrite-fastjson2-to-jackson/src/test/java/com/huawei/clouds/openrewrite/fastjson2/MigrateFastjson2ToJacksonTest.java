package com.huawei.clouds.openrewrite.fastjson2;

import com.huawei.clouds.openrewrite.fastjson.internal.FastjsonMigrationConfiguration;
import com.huawei.clouds.openrewrite.fastjson.internal.JacksonJsonSupport;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateFastjson2ToJacksonTest implements RewriteTest {
    private static final JacksonJsonSupport SUPPORT =
            new JacksonJsonSupport(FastjsonMigrationConfiguration.fastjson2());

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFastjson2ToJackson())
                .parser(JavaParser.fromJavaVersion().classpath("fastjson2", "jackson-databind"));
    }

    @Test
    void migratesSourceAndGeneratesVersionSpecificFacade() {
        rewriteRun(
                java(
                        """
                        package example;

                        import com.alibaba.fastjson2.JSON;
                        import com.alibaba.fastjson2.JSONObject;

                        class Example {
                            JSONObject read(String json) {
                                JSONObject object = JSON.parseObject(json);
                                object.put("migrated", true);
                                return object;
                            }
                        }
                        """,
                        """
                        package example;

                        import com.fasterxml.jackson.databind.node.ObjectNode;
                        import com.huawei.clouds.openrewrite.fastjson2.JacksonJson;

                        class Example {
                            ObjectNode read(String json) {
                                ObjectNode object = JacksonJson.readObject(json);
                                JacksonJson.put(object, "migrated", true);
                                return object;
                            }
                        }
                        """,
                        spec -> spec.path("src/main/java/example/Example.java")
                ),
                java(
                        null,
                        SUPPORT.helperSource(),
                        spec -> spec.path("src/main/java/" + SUPPORT.helperRelativePath())
                )
        );
    }

    @Test
    void migratesJsonFieldAndTypeReference() {
        rewriteRun(
                java(
                        """
                        package example;

                        import com.alibaba.fastjson2.JSON;
                        import com.alibaba.fastjson2.TypeReference;
                        import com.alibaba.fastjson2.annotation.JSONField;

                        import java.util.List;

                        class UserService {
                            @JSONField(name = "user_name", format = "yyyy-MM-dd")
                            java.util.Date name;

                            List<String> read(String json) {
                                return JSON.parseObject(json, new TypeReference<List<String>>() {});
                            }
                        }
                        """,
                        """
                        package example;

                        import com.fasterxml.jackson.annotation.JsonFormat;
                        import com.fasterxml.jackson.annotation.JsonProperty;
                        import com.fasterxml.jackson.core.type.TypeReference;
                        import com.huawei.clouds.openrewrite.fastjson2.JacksonJson;

                        import java.util.List;

                        class UserService {
                            @JsonProperty(value = "user_name")
                            @JsonFormat(pattern = "yyyy-MM-dd")
                            java.util.Date name;

                            List<String> read(String json) {
                                return JacksonJson.fromJson(json, new TypeReference<List<String>>() {
                                });
                            }
                        }
                        """,
                        spec -> spec.path("src/main/java/example/UserService.java")
                ),
                java(
                        null,
                        SUPPORT.helperSource(),
                        spec -> spec.path("src/main/java/" + SUPPORT.helperRelativePath())
                )
        );
    }

    @Test
    void replacesFastjson2MavenDependencyWhenMigrationIsComplete() {
        rewriteRun(
                mavenProject(
                        "app",
                        pomXml(
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.alibaba.fastjson2</groupId>
                                      <artifactId>fastjson2</artifactId>
                                      <version>2.0.62</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """,
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.fasterxml.jackson.core</groupId>
                                      <artifactId>jackson-databind</artifactId>
                                      <version>2.22.1</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                        ),
                        srcMainJava(
                                java(
                                        """
                                        package example;

                                        import com.alibaba.fastjson2.JSON;

                                        class JsonService {
                                            String write(Object value) {
                                                return JSON.toJSONString(value);
                                            }
                                        }
                                        """,
                                        """
                                        package example;

                                        import com.huawei.clouds.openrewrite.fastjson2.JacksonJson;

                                        class JsonService {
                                            String write(Object value) {
                                                return JacksonJson.toJson(value);
                                            }
                                        }
                                        """
                                ),
                                java(
                                        null,
                                        SUPPORT.helperSource(),
                                        spec -> spec.path(SUPPORT.helperRelativePath())
                                )
                        )
                )
        );
    }

    @Test
    void retainsFastjson2WhenUnsupportedUsageRemains() {
        rewriteRun(
                mavenProject(
                        "legacy-app",
                        pomXml(
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>legacy-app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.alibaba.fastjson2</groupId>
                                      <artifactId>fastjson2</artifactId>
                                      <version>2.0.62</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """,
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>legacy-app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.alibaba.fastjson2</groupId>
                                      <artifactId>fastjson2</artifactId>
                                      <version>2.0.62</version>
                                    </dependency>
                                    <dependency>
                                      <groupId>com.fasterxml.jackson.core</groupId>
                                      <artifactId>jackson-databind</artifactId>
                                      <version>2.22.1</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                        ),
                        srcMainJava(
                                java(
                                        """
                                        package example;

                                        import com.alibaba.fastjson2.JSON;
                                        import com.alibaba.fastjson2.JSONWriter;

                                        class LegacyJsonService {
                                            String write(Object value) {
                                                return JSON.toJSONString(value, JSONWriter.Feature.PrettyFormat);
                                            }
                                        }
                                        """
                                )
                        )
                )
        );
    }

    @Test
    void preservesAdvancedJsonFieldUsageAndItsDependency() {
        rewriteRun(
                mavenProject(
                        "annotated-app",
                        pomXml(
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>annotated-app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.alibaba.fastjson2</groupId>
                                      <artifactId>fastjson2</artifactId>
                                      <version>2.0.62</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """,
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>annotated-app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.alibaba.fastjson2</groupId>
                                      <artifactId>fastjson2</artifactId>
                                      <version>2.0.62</version>
                                    </dependency>
                                    <dependency>
                                      <groupId>com.fasterxml.jackson.core</groupId>
                                      <artifactId>jackson-databind</artifactId>
                                      <version>2.22.1</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                        ),
                        srcMainJava(
                                java(
                                        """
                                        package example;

                                        import com.alibaba.fastjson2.JSONWriter;
                                        import com.alibaba.fastjson2.annotation.JSONField;

                                        class User {
                                            @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
                                            String name;
                                        }
                                        """
                                )
                        )
                )
        );
    }
}
