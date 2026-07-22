package com.huawei.clouds.openrewrite.jedis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeJedisTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.UpgradeJedisTo7_2_1";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.MigrateDeterministicJedisSourceTo7";
    private static final String RISK_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.FindManualJedis7MigrationRisks";
    private static final String BUILD_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.FindManualJedis7BuildBaselineRisks";
    private static final String COMPOSITE_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.MigrateJedisTo7_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
        spec.typeValidationOptions(TypeValidation.none());
        spec.parser(JavaParser.fromJavaVersion().dependsOn(
                """
                package redis.clients.jedis;
                public class BinaryJedis {
                    public BinaryJedis(java.net.URI uri) {}
                    public String set(byte[] key, byte[] value) { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class BinaryJedisCluster {}
                """,
                """
                package redis.clients.jedis;
                public class Jedis {
                    public Jedis(String host) {}
                    public Jedis(String host, int port) {}
                    public Jedis(java.net.URI uri) {}
                }
                """,
                """
                package redis.clients.jedis;
                public class JedisPool {
                    public JedisPool(String host) {}
                    public JedisPool(String host, int port) {}
                    public Jedis getResource() { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public final class Protocol { public static final int DEFAULT_PORT = 6379; }
                """,
                """
                package redis.clients.jedis;
                public class ScanParams { public static final String SCAN_POINTER_START = "0"; }
                """,
                """
                package redis.clients.jedis;
                public class ScanResult<T> {}
                """,
                """
                package redis.clients.jedis;
                public abstract class PipelineBase {}
                """,
                """
                package redis.clients.jedis;
                public abstract class TransactionBase {}
                """,
                """
                package redis.clients.jedis;
                public class Tuple {}
                """,
                """
                package redis.clients.jedis;
                public enum BitOP { AND, OR, XOR, NOT }
                """
        ));
    }

    @ParameterizedTest(name = "upgrades explicit spreadsheet Jedis version {0}")
    @ValueSource(strings = {
            "2.8.0", "2.9.3", "2.10.2", "3.1.0", "3.5.2",
            "3.6.3", "3.7.0", "3.7.1", "3.8.0", "3.10.0"
    })
    void upgradesEveryExplicitSpreadsheetVersionInMaven(String version) {
        rewriteRun(pomXml(directPom(version), directPom("7.2.1")));
    }

    @Test
    void upgradesDirectAndManagedMavenDeclarationsWithoutChangingCompanions() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cache</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.10.0</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cache</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>7.2.1</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @ParameterizedTest(name = "upgrades Gradle declaration {0}")
    @ValueSource(strings = {"2.8.0", "3.1.0", "3.8.0", "3.10.0"})
    void upgradesSelectedGradleStringDeclarations(String version) {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies { implementation 'redis.clients:jedis:" + version + "' }",
                "plugins { id 'java' }\ndependencies { implementation 'redis.clients:jedis:7.2.1' }"
        ));
    }

    @Test
    void upgradesSelectedGradleKotlinStringDeclaration() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"redis.clients:jedis:3.7.1\") }",
                        "plugins { java }\ndependencies { implementation(\"redis.clients:jedis:7.2.1\") }"
                )
        );
    }

    @ParameterizedTest(name = "preserves unlisted Jedis version {0}")
    @ValueSource(strings = {
            "2.8.1", "2.10.1", "3.0.0", "3.6.2", "3.9.0", "4.4.8",
            "5.2.0", "6.2.0", "7.0.0", "7.2.0", "7.2.1", "7.3.0"
    })
    void preservesUnlistedAndTargetVersions(String version) {
        rewriteRun(pomXml(directPom(version)));
    }

    @Test
    void preservesMavenPropertiesBomManagedDeclarationsAndOtherCoordinates() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>strict</artifactId><version>1</version>
                  <properties><jedis.version>3.8.0</jedis.version></properties>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId><version>3.8</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesGradleVariablesMapNotationAndVersionCatalog() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        def jedisVersion = '3.8.0'
                        dependencies {
                          implementation "redis.clients:jedis:$jedisVersion"
                          runtimeOnly group: 'redis.clients', name: 'jedis', version: '3.8.0'
                        }
                        """
                ),
                text(
                        """
                        [versions]
                        jedis = "3.8.0"
                        [libraries]
                        jedis = { module = "redis.clients:jedis", version.ref = "jedis" }
                        """,
                        source -> source.path("gradle/libs.versions.toml")
                )
        );
    }

    @Test
    void migratesRedisInActionLiteralHostConstructor() {
        // Reduced from josiahcarlson/redis-in-action at 9ea2f986:
        // https://github.com/josiahcarlson/redis-in-action/blob/9ea2f9862faee248f53f663a8e3f6306327d352b/java/src/main/java/Chapter04.java#L16-L18
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;

                        class Chapter04 {
                            void run() {
                                Jedis conn = new Jedis("localhost");
                                conn.select(15);
                            }
                        }
                        """,
                        """
                        import redis.clients.jedis.Jedis;

                        class Chapter04 {
                            void run() {
                                Jedis conn = new Jedis("localhost", redis.clients.jedis.Protocol.DEFAULT_PORT);
                                conn.select(15);
                            }
                        }
                        """,
                        source -> source.path("java/src/main/java/Chapter04.java")
                )
        );
    }

    @Test
    void migratesApacheSeataMovedScanTypes() {
        // Reduced from apache/incubator-seata at e6d0860a:
        // https://github.com/apache/incubator-seata/blob/e6d0860a4345b10cb59c65c78215ec51d67f59d1/discovery/seata-discovery-redis/src/main/java/org/apache/seata/discovery/registry/redis/RedisRegistryServiceImpl.java#L38-L39
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.ScanParams;
                        import redis.clients.jedis.ScanResult;

                        class RedisRegistryServiceImpl {
                            ScanParams params = new ScanParams();
                            ScanResult<String> result;
                        }
                        """,
                        """
                        import redis.clients.jedis.params.ScanParams;
                        import redis.clients.jedis.resps.ScanResult;

                        class RedisRegistryServiceImpl {
                            ScanParams params = new ScanParams();
                            ScanResult<String> result;
                        }
                        """,
                        source -> source.path("discovery/seata-discovery-redis/src/main/java/org/apache/seata/discovery/registry/redis/RedisRegistryServiceImpl.java")
                )
        );
    }

    @Test
    void migratesMemcachedSessionManagerBinaryJedis() {
        // Reduced from magro/memcached-session-manager at 716e147c:
        // https://github.com/magro/memcached-session-manager/blob/716e147c9840ab10298c4d2b9edd0662058331e6/core/src/main/java/de/javakaffee/web/msm/storage/RedisStorageClient.java
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import java.net.URI;
                        import redis.clients.jedis.BinaryJedis;

                        class RedisStorageClient {
                            BinaryJedis create(URI uri) { return new BinaryJedis(uri); }
                            byte[] get(BinaryJedis jedis, byte[] key) { return jedis.get(key); }
                        }
                        """,
                        """
                        import redis.clients.jedis.Jedis;

                        import java.net.URI;

                        class RedisStorageClient {
                            Jedis create(URI uri) { return new Jedis(uri); }
                            byte[] get(Jedis jedis, byte[] key) { return jedis.get(key); }
                        }
                        """,
                        source -> source.path("core/src/main/java/de/javakaffee/web/msm/storage/RedisStorageClient.java")
                )
        );
    }

    @Test
    void migratesPhantomThiefPipelineBase() {
        // Reduced from PhantomThief/jedis-helper at b531143e:
        // https://github.com/PhantomThief/jedis-helper/blob/b531143ee1ce6e94be6ce8e56d279f53d9faf3b6/src/main/java/com/github/phantomthief/jedis/JedisHelper.java
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.PipelineBase;
                        class JedisHelper { PipelineBase pipeline; }
                        """,
                        """
                        import redis.clients.jedis.AbstractPipeline;

                        class JedisHelper { AbstractPipeline pipeline; }
                        """,
                        source -> source.path("src/main/java/com/github/phantomthief/jedis/JedisHelper.java")
                )
        );
    }

    @Test
    void migratesAdditionalOfficialTypeMovesAndTransactionBase() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.BitOP;
                        import redis.clients.jedis.Tuple;
                        import redis.clients.jedis.TransactionBase;
                        class LegacyTypes { BitOP op; Tuple tuple; TransactionBase tx; }
                        """,
                        """
                        import redis.clients.jedis.AbstractTransaction;
                        import redis.clients.jedis.args.BitOP;
                        import redis.clients.jedis.resps.Tuple;

                        class LegacyTypes { BitOP op; Tuple tuple; AbstractTransaction tx; }
                        """
                )
        );
    }

    @Test
    void sourceRecipePreservesUrisDynamicHostsOtherJedisAndBinaryPubSub() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.JedisPool;
                        import redis.clients.jedis.BinaryJedisPubSub;
                        class Boundaries {
                            Jedis uri = new Jedis("redis://localhost:6379");
                            Jedis dynamic = new Jedis(System.getenv("REDIS_URL"));
                            JedisPool secure = new JedisPool("rediss://localhost:6380");
                            BinaryJedisPubSub listener;
                        }
                        """
                ),
                java(
                        """
                        package com.example;
                        class Jedis { Jedis(String value) {} }
                        class NotRedis { Jedis value = new Jedis("localhost"); }
                        """
                )
        );
    }

    @Test
    void sourceRecipeIsIdempotentForTargetTypesAndExplicitConstructor() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.AbstractPipeline;
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.Protocol;
                        import redis.clients.jedis.params.ScanParams;
                        import redis.clients.jedis.resps.ScanResult;
                        class Modern { Jedis jedis = new Jedis("localhost", Protocol.DEFAULT_PORT); }
                        """
                )
        );
    }

    @Test
    void marksRemovedShardingAndAmbiguousConstructors() {
        // ShardedJedisPool is reduced from PhantomThief/jedis-helper at b531143e:
        // https://github.com/PhantomThief/jedis-helper/blob/b531143ee1ce6e94be6ce8e56d279f53d9faf3b6/src/main/java/com/github/phantomthief/jedis/JedisHelper.java#L64-L66
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.ShardedJedisPool;
                        class Legacy {
                          Jedis jedis = new Jedis(redisEndpoint);
                          ShardedJedisPool pool;
                        }
                        """,
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.~~>ShardedJedisPool;
                        class Legacy {
                          Jedis jedis = ~~>new Jedis(redisEndpoint);
                          ~~>ShardedJedisPool pool;
                        }
                        """,
                        source -> source.path("src/main/java/example/Legacy.java")
                )
        );
    }

    @Test
    void marksSslTimeoutPoolAndClusterConstruction() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.*;
                        GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
                        JedisCluster cluster = new JedisCluster(nodes, connectionTimeout, socketTimeout, maxAttempts, password, config);
                        cluster.getClusterNodes();
                        cluster.getConnectionFromSlot(slot);
                        """,
                        """
                        import redis.clients.jedis.*;
                        ~~>GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
                        JedisCluster cluster = ~~>new JedisCluster(nodes, connectionTimeout, socketTimeout, maxAttempts, password, config);
                        cluster~~>.getClusterNodes();
                        cluster~~>.getConnectionFromSlot(slot);
                        """,
                        source -> source.path("src/main/java/example/ClusterConfig.java")
                )
        );
    }

    @Test
    void marksPoolResourcePipelineAndTransactionBehavior() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.*;
                        Jedis jedis = pool.getResource();
                        pipeline.multi();
                        pipeline.exec();
                        tx.execGetResponse();
                        transaction.watch("key");
                        """,
                        """
                        import redis.clients.jedis.*;
                        Jedis jedis = pool~~>.getResource();
                        ~~>pipeline.multi();
                        ~~>pipeline.exec();
                        ~~>tx.execGetResponse();
                        ~~>transaction.watch("key");
                        """,
                        source -> source.path("src/main/java/example/Batch.java")
                )
        );
    }

    @Test
    void marksChangedCommandReturnsAndBinaryScriptExists() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.Jedis;
                        List<String> popped = jedis.blpop(5, "queue");
                        List<String> config = jedis.configGet("*");
                        Set<String> union = jedis.zunion(params, "a", "b");
                        Long binary = jedis.scriptExists(scriptBytes);
                        """,
                        """
                        import redis.clients.jedis.Jedis;
                        List<String> popped = jedis~~>.blpop(5, "queue");
                        List<String> config = jedis~~>.configGet("*");
                        Set<String> union = jedis~~>.zunion(params, "a", "b");
                        Long binary = jedis~~>.scriptExists(scriptBytes);
                        """,
                        source -> source.path("src/main/java/example/Returns.java")
                )
        );
    }

    @Test
    void marksChangedExceptionsSetParamsAndXpending() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.*;
                        import redis.clients.jedis.exceptions.JedisNoReachableClusterNodeException;
                        try { command(); } catch (JedisDataException e) { recover(); }
                        SetParams params = SetParams.setParams().get();
                        jedis.xpending(key, group, start, end, 10, consumer);
                        """,
                        """
                        import redis.clients.jedis.*;
                        import redis.clients.jedis.exceptions.~~>JedisNoReachableClusterNodeException;
                        try { command(); } ~~>catch (JedisDataException e) { recover(); }
                        ~~>SetParams params = SetParams.setParams().get();
                        jedis~~>.xpending(key, group, start, end, 10, consumer);
                        """,
                        source -> source.path("src/main/java/example/Errors.java")
                )
        );
    }

    @Test
    void marksRemovedModulesSearchSemanticsAndInternalExtensions() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.graph.ResultSet;
                        import redis.clients.jedis.providers.ConnectionProvider;
                        class CustomPipeline extends Pipeline {}
                        jedis.ftSearch("idx", query);
                        """,
                        """
                        import ~~>redis.clients.jedis.graph.ResultSet;
                        import ~~>redis.clients.jedis.providers.ConnectionProvider;
                        class CustomPipeline ~~>extends Pipeline {}
                        jedis~~>.ftSearch("idx", query);
                        """,
                        source -> source.path("src/main/java/example/Extensions.java")
                )
        );
    }

    @Test
    void riskRecipeLeavesModernStraightLineCommandsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import redis.clients.jedis.*;
                        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
                        JedisPool pool = new JedisPool(poolConfig, "localhost", 6379);
                        JedisPooled jedis = JedisPooled.builder().hostAndPort("localhost", 6379).build();
                        jedis.set("key", "value");
                        String value = jedis.get("key");
                        """,
                        source -> source.path("src/main/java/example/Modern.java")
                )
        );
    }

    @Test
    void marksMavenJavaSlf4jAndCommonsPoolBaselines() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                text(
                        """
                        <project>
                          <properties><maven.compiler.source>1.7</maven.compiler.source></properties>
                          <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                            <dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <properties>~~><maven.compiler.source>1.7</maven.compiler.source></properties>
                          <dependencies>
                            <dependency>~~><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                            <dependency>~~><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void marksGradleJavaSlf4jAndCommonsPoolBaselines() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                text(
                        """
                        java { sourceCompatibility = '1.7' }
                        dependencies {
                          implementation 'org.slf4j:slf4j-api:1.7.25'
                          implementation 'org.apache.commons:commons-pool2:2.6.2'
                        }
                        """,
                        """
                        java { ~~>sourceCompatibility = '1.7' }
                        dependencies {
                          implementation ~~>'org.slf4j:slf4j-api:1.7.25'
                          implementation ~~>'org.apache.commons:commons-pool2:2.6.2'
                        }
                        """,
                        source -> source.path("build.gradle")
                )
        );
    }

    @Test
    void buildRiskRecipePreservesSupportedBaselineWithoutExplicitCompanions() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                text(
                        "<project><properties><maven.compiler.release>17</maven.compiler.release></properties></project>",
                        source -> source.path("pom.xml")
                ),
                text(
                        "java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }",
                        source -> source.path("build.gradle")
                )
        );
    }

    @Test
    void compositeUpgradesDependencyMigratesSourceAndMarksBehaviorRisk() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPOSITE_RECIPE)),
                pomXml(directPom("3.8.0"), directPom("7.2.1")),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.ScanResult;
                        class Composite {
                          Jedis jedis = new Jedis("localhost");
                          void pop() { jedis.blpop(5, "queue"); }
                          ScanResult<String> result;
                        }
                        """,
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.resps.ScanResult;

                        class Composite {
                          Jedis jedis = new Jedis("localhost", redis.clients.jedis.Protocol.DEFAULT_PORT);
                          void pop() { jedis~~>.blpop(5, "queue"); }
                          ScanResult<String> result;
                        }
                        """,
                        source -> source.path("src/main/java/example/Composite.java")
                )
        );
    }

    @Test
    void discoversAndValidatesEveryRecipe() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe source = environment.activateRecipes(SOURCE_RECIPE);
        Recipe risks = environment.activateRecipes(RISK_RECIPE);
        Recipe buildRisks = environment.activateRecipes(BUILD_RISK_RECIPE);
        Recipe composite = environment.activateRecipes(COMPOSITE_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> SOURCE_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> RISK_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> BUILD_RISK_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> COMPOSITE_RECIPE.equals(recipe.getName())));
        assertEquals("Upgrade selected Jedis declarations to 7.2.1", dependency.getDisplayName());
        assertEquals("Migrate deterministic Jedis source constructs to version 7", source.getDisplayName());
        assertEquals("Find Jedis 7 migration risks requiring manual review", risks.getDisplayName());
        assertEquals("Find Jedis 7 build baseline risks requiring manual review", buildRisks.getDisplayName());
        assertEquals("Migrate Jedis applications to 7.2.1", composite.getDisplayName());
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(source.validate().isValid(), () -> source.validate().failures().toString());
        assertTrue(risks.validate().isValid(), () -> risks.validate().failures().toString());
        assertTrue(buildRisks.validate().isValid(), () -> buildRisks.validate().failures().toString());
        assertTrue(composite.validate().isValid(), () -> composite.validate().failures().toString());
    }

    private static String directPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jedis-app</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static Recipe sourceRecipe() {
        return environment().activateRecipes(SOURCE_RECIPE);
    }

    private static Recipe riskRecipe() {
        return environment().activateRecipes(RISK_RECIPE);
    }

    private static Recipe buildRiskRecipe() {
        return environment().activateRecipes(BUILD_RISK_RECIPE);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jedis")
                .scanYamlResources()
                .build();
    }
}
