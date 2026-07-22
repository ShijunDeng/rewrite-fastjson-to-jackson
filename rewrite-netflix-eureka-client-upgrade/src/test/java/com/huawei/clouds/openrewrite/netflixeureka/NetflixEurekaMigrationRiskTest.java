package com.huawei.clouds.openrewrite.netflixeureka;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class NetflixEurekaMigrationRiskTest implements RewriteTest {
    private static final String NAMESPACE_RECIPE =
            "com.huawei.clouds.openrewrite.netflixeureka.MigrateEurekaExtensionJakartaNamespaces";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.netflixeureka.MigrateNetflixEurekaClientTo2_0_4";

    @Test
    void migratesInjectOnlyInsideAnEurekaCompilationUnit() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(NAMESPACE_RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package com.netflix.discovery; public interface EurekaClientConfig {}",
                                "package javax.inject; public interface Provider<T> { T get(); }")),
                java(
                        """
                        package example;
                        import com.netflix.discovery.EurekaClientConfig;
                        import javax.inject.Provider;
                        class Extension {
                            EurekaClientConfig config;
                            Provider<String> token;
                        }
                        """,
                        """
                        package example;
                        import com.netflix.discovery.EurekaClientConfig;
                        import jakarta.inject.Provider;
                        class Extension {
                            EurekaClientConfig config;
                            Provider<String> token;
                        }
                        """
                )
        );
    }

    @Test
    void marksAnnotationForClassificationInsteadOfBlindPackageChange() {
        rewriteRun(
                spec -> spec.recipe(new FindNetflixEurekaJavaRisks())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package com.netflix.discovery; public interface EurekaClientConfig {}",
                                "package javax.annotation; public @interface Nullable {}")),
                java(
                        """
                        package example;
                        import com.netflix.discovery.EurekaClientConfig;
                        import javax.annotation.Nullable;
                        class Extension {
                            EurekaClientConfig config;
                            @Nullable String zone;
                        }
                        """,
                        """
                        package example;
                        import com.netflix.discovery.EurekaClientConfig;
                        import javax.annotation.Nullable;
                        class Extension {
                            EurekaClientConfig config;
                            /*~~(Eureka 2 moved lifecycle/injection annotations to Jakarta, but nullness annotations may still come from JSR-305; classify this exact annotation before changing its package)~~>*/@Nullable String zone;
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedRegistryAndInternalMetricAccess() {
        rewriteRun(
                spec -> spec.recipe(new FindNetflixEurekaJavaRisks())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package com.netflix.discovery;
                                public class DiscoveryClient { public int localRegistrySize() { return 0; } }
                                """)),
                java(
                        """
                        package example;
                        import com.netflix.discovery.DiscoveryClient;
                        class RegistryGauge {
                            int size(DiscoveryClient client) { return client.localRegistrySize(); }
                        }
                        """,
                        """
                        package example;
                        import com.netflix.discovery.DiscoveryClient;
                        class RegistryGauge {
                            int size(DiscoveryClient client) { return /*~~(DiscoveryClient.localRegistrySize was removed when registry metrics moved from Servo to Spectator; derive size from the public registry view or a supported metric)~~>*/client.localRegistrySize(); }
                        }
                        """
                )
        );
    }

    @Test
    void marksCustomDiscoveryClientSubclass() {
        rewriteRun(
                spec -> spec.recipe(new FindNetflixEurekaJavaRisks())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package com.netflix.discovery; public class DiscoveryClient {}")),
                java(
                        """
                        package example;
                        import com.netflix.discovery.DiscoveryClient;
                        class InstrumentedClient extends DiscoveryClient {}
                        """,
                        """
                        package example;
                        import com.netflix.discovery.DiscoveryClient;
                        /*~~(Custom DiscoveryClient subclasses are coupled to changed constructors, transport fields, scheduling, metrics, and shutdown; prefer composition or port every override against 2.0.4)~~>*/class InstrumentedClient extends DiscoveryClient {}
                        """
                )
        );
    }

    @Test
    void marksMisalignedCompanionAndJersey3Modules() {
        rewriteRun(
                spec -> spec.recipe(new FindNetflixEurekaBuildRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-core</artifactId><version>1.10.18</version></dependency>
                            <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client-jersey3</artifactId><version>2.0.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies>
                            <!--~~(A companion Eureka module remains at 1.10.18; verify whether it participates in the runtime and align or remove it without changing a shared property blindly)~~>--><dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-core</artifactId><version>1.10.18</version></dependency>
                            <!--~~(Jersey 3 transport module is not aligned to Eureka client 2.0.4; use one coherent Eureka release after confirming this is the chosen transport)~~>--><dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client-jersey3</artifactId><version>2.0.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void recommendedRecipeIsIdempotentForASafeLiteralUpgrade() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies><dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>1.10.18</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies><dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>2.0.4</version></dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }
}
