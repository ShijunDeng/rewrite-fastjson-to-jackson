package com.huawei.clouds.openrewrite.springdataelasticsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenRepositoryMirror;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringDataElasticsearchTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.springdataelasticsearch.UpgradeSpringDataElasticsearchDependencyTo6_0_5";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.springdataelasticsearch.MigrateSpringDataElasticsearchTo6_0_5";

    @Override
    public void defaults(RecipeSpec spec) {
        InMemoryExecutionContext context = new InMemoryExecutionContext(Throwable::printStackTrace);
        MavenExecutionContextView.view(context)
                .setRepositories(List.of(MavenRepository.MAVEN_CENTRAL))
                .setMirrors(List.of(new MavenRepositoryMirror(
                        "central-only", "https://repo.maven.apache.org/maven2", "external:*", true, false, null)));
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
        spec.executionContext(context);
    }

    @ParameterizedTest(name = "Maven upgrades spreadsheet version {0}")
    @ValueSource(strings = {"4.2.4", "4.2.8", "4.2.12", "4.4.8", "4.4.12", "4.4.14"})
    void upgradesEverySpreadsheetVersionInMaven(String oldVersion) {
        rewriteRun(pomXml(directPom(oldVersion), directPom("6.0.5")));
    }

    @ParameterizedTest(name = "Gradle upgrades spreadsheet version {0}")
    @ValueSource(strings = {"4.2.4", "4.2.8", "4.2.12", "4.4.8", "4.4.12", "4.4.14"})
    void upgradesEverySpreadsheetVersionInGradle(String oldVersion) {
        rewriteRun(buildGradle(
                gradleBuild(oldVersion, "implementation"),
                gradleBuild("6.0.5", "implementation")
        ));
    }

    @ParameterizedTest(name = "preserves Gradle configuration {0}")
    @ValueSource(strings = {"api", "implementation", "compileOnly", "testImplementation"})
    void preservesGradleConfiguration(String configuration) {
        rewriteRun(buildGradle(
                gradleBuild("4.4.8", configuration),
                gradleBuild("6.0.5", configuration)
        ));
    }

    @Test
    void upgradesMavenVersionProperty() {
        rewriteRun(pomXml(
                propertyPom("4.2.8", "spring-data-elasticsearch.version"),
                propertyPom("6.0.5", "spring-data-elasticsearch.version")
        ));
    }

    @Test
    void upgradesDependencyManagementVersionProperty() {
        rewriteRun(pomXml(
                managedPropertyPom("4.4.8"),
                managedPropertyPom("6.0.5")
        ));
    }

    @Test
    void upgradesDirectDependencyManagementEntry() {
        rewriteRun(pomXml(
                managedPom("4.4.14"),
                managedPom("6.0.5")
        ));
    }

    @Test
    void upgradesDependencyInsideActiveMavenProfile() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profiled-search</artifactId><version>1</version>
                  <profiles><profile><id>elasticsearch</id><activation><activeByDefault>true</activeByDefault></activation>
                    <dependencies><dependency>
                      <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>4.2.12</version>
                    </dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profiled-search</artifactId><version>1</version>
                  <profiles><profile><id>elasticsearch</id><activation><activeByDefault>true</activeByDefault></activation>
                    <dependencies><dependency>
                      <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>6.0.5</version>
                    </dependency></dependencies>
                  </profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation group: 'org.springframework.data', name: 'spring-data-elasticsearch', version: '4.2.8'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation group: 'org.springframework.data', name: 'spring-data-elasticsearch', version: '6.0.5'
                }
                """
        ));
    }

    @Test
    void leavesGradleVersionVariableUntouchedWhenExactVersionCannotBeProven() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def springDataElasticsearchVersion = '4.4.8'
                        dependencies {
                            api "org.springframework.data:spring-data-elasticsearch:$springDataElasticsearchVersion"
                        }
                        """
                )
        );
    }

    @Test
    void preservesMavenDependencyDetails() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>4.4.12</version>
                    <scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>org.elasticsearch</groupId><artifactId>elasticsearch</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>6.0.5</version>
                    <scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>org.elasticsearch</groupId><artifactId>elasticsearch</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesResftulElasticsearchAndMarksRhlcConfiguration() {
        // Reduced from bben636/Resftul_Elasticsearch at 0931cd4a:
        // https://github.com/bben636/Resftul_Elasticsearch/blob/0931cd4aa3e1dd19859b7d577361cb323d030164/build.gradle.kts
        // https://github.com/bben636/Resftul_Elasticsearch/tree/0931cd4aa3e1dd19859b7d577361cb323d030164/src/main/java/com/practical/restful/training
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser())
                        .beforeRecipe(withToolingApi())
                        .typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch:3.2.0")
                            implementation("org.springframework.data:spring-data-elasticsearch:4.2.4")
                            implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.17.1")
                        }
                        """,
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch:3.2.0")
                            implementation("org.springframework.data:spring-data-elasticsearch:6.0.5")
                            implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.17.1")
                        }
                        """
                ),
                java(
                        """
                        package com.practical.restful.training.entity;

                        import org.springframework.data.annotation.Id;
                        import org.springframework.data.elasticsearch.annotations.DateFormat;
                        import org.springframework.data.elasticsearch.annotations.Document;
                        import org.springframework.data.elasticsearch.annotations.Field;
                        import org.springframework.data.elasticsearch.annotations.FieldType;
                        import java.time.LocalDate;

                        @Document(indexName = "mycars")
                        public class Car {
                            @Id String id;
                            @Field(type = FieldType.Date, format = DateFormat.date)
                            LocalDate firstReleaseDate;
                        }
                        """,
                        source -> source.path("src/main/java/com/practical/restful/training/entity/Car.java")
                ),
                java(
                        """
                        package com.practical.restful.training.repository;

                        import com.practical.restful.training.entity.Car;
                        import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

                        public interface CarElasticRepository extends ElasticsearchRepository<Car, String> {
                        }
                        """,
                        source -> source.path("src/main/java/com/practical/restful/training/repository/CarElasticRepository.java")
                ),
                java(
                        """
                        package com.practical.restful.training.common;

                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.springframework.data.elasticsearch.client.ClientConfiguration;
                        import org.springframework.data.elasticsearch.client.RestClients;
                        import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;

                        public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {
                            public RestHighLevelClient elasticsearchClient() {
                                ClientConfiguration configuration = ClientConfiguration.builder().connectedTo("localhost:9200").build();
                                return RestClients.create(configuration).rest();
                            }
                        }
                        """,
                        """
                        package com.practical.restful.training.common;

                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.springframework.data.elasticsearch.client.ClientConfiguration;
                        import org.springframework.data.elasticsearch.client.RestClients;
                        import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;

                        public class /*~~>*/ElasticsearchConfig extends /*~~>*/AbstractElasticsearchConfiguration {
                            public /*~~>*/RestHighLevelClient elasticsearchClient() {
                                ClientConfiguration configuration = ClientConfiguration.builder().connectedTo("localhost:9200").build();
                                return /*~~>*/RestClients.create(configuration).rest();
                            }
                        }
                        """,
                        source -> source.path("src/main/java/com/practical/restful/training/common/ElasticsearchConfig.java")
                )
        );
    }

    @Test
    void upgradesElasticsearchStudyAndMigratesNativeQueryTypeWhileMarkingOldDsl() {
        // Reduced from Withbini/ElasticsearchStudy at 9aaa0022:
        // https://github.com/Withbini/ElasticsearchStudy/blob/9aaa00220d0a5abf1b4a25988a269a120e1312e2/build.gradle
        // https://github.com/Withbini/ElasticsearchStudy/blob/9aaa00220d0a5abf1b4a25988a269a120e1312e2/src/main/java/com/example/demo/repository/BoardRepositoryOperations.java
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser())
                        .typeValidationOptions(TypeValidation.none()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch:2.4.13'
                            implementation 'org.springframework.data:spring-data-elasticsearch:4.2.12'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch:2.4.13'
                            implementation 'org.springframework.data:spring-data-elasticsearch:6.0.5'
                        }
                        """
                ),
                java(
                        """
                        package com.example.demo.entity;

                        import org.springframework.data.annotation.Id;
                        import org.springframework.data.elasticsearch.annotations.Document;

                        @Document(indexName = "jaebin", createIndex = false)
                        public class Board {
                            @Id String id;
                        }
                        """,
                        source -> source.path("src/main/java/com/example/demo/entity/Board.java")
                ),
                java(
                        """
                        package com.example.demo.repository;

                        import com.example.demo.entity.Board;
                        import org.elasticsearch.index.query.QueryBuilders;
                        import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
                        import org.springframework.data.elasticsearch.core.SearchHits;
                        import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
                        import org.springframework.data.elasticsearch.core.query.Query;

                        public class BoardRepositoryOperations {
                            private final ElasticsearchOperations operations;

                            public BoardRepositoryOperations(ElasticsearchOperations operations) {
                                this.operations = operations;
                            }

                            SearchHits<Board> searchAll() {
                                Query query = new NativeSearchQueryBuilder()
                                        .withQuery(QueryBuilders.matchAllQuery())
                                        .build();
                                return operations.search(query, Board.class);
                            }
                        }
                        """,
                        """
                        package com.example.demo.repository;

                        import com.example.demo.entity.Board;
                        import org.elasticsearch.index.query.QueryBuilders;
                        import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
                        import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
                        import org.springframework.data.elasticsearch.core.SearchHits;
                        import org.springframework.data.elasticsearch.core.query.Query;

                        public class BoardRepositoryOperations {
                            private final ElasticsearchOperations operations;

                            public BoardRepositoryOperations(ElasticsearchOperations operations) {
                                this.operations = operations;
                            }

                            SearchHits<Board> searchAll() {
                                Query query = new NativeQueryBuilder()
                                        .withQuery(/*~~>*/QueryBuilders.matchAllQuery())
                                        .build();
                                return operations.search(query, Board.class);
                            }
                        }
                        """,
                        source -> source.path("src/main/java/com/example/demo/repository/BoardRepositoryOperations.java")
                )
        );
    }

    @Test
    void upgradesHaebangAndMarksRhlcWhileLeavingJpaNamespaceForDedicatedMigration() {
        // Reduced from HaeBangProject/HAEBANG at 087c93b6:
        // https://github.com/HaeBangProject/HAEBANG/blob/087c93b667006612803721c4d9c27da31f081d98/build.gradle
        // https://github.com/HaeBangProject/HAEBANG/tree/087c93b667006612803721c4d9c27da31f081d98/src/main/java/com/haebang/haebang
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser())
                        .typeValidationOptions(TypeValidation.none()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.springframework.boot:spring-boot-starter-data-jpa:2.7.3'
                            implementation 'org.springframework.data:spring-data-elasticsearch:4.4.12'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.springframework.boot:spring-boot-starter-data-jpa:2.7.3'
                            implementation 'org.springframework.data:spring-data-elasticsearch:6.0.5'
                        }
                        """
                ),
                java(
                        """
                        package com.haebang.haebang.document;

                        import javax.persistence.Id;
                        import org.springframework.data.elasticsearch.annotations.Document;

                        @Document(indexName = "apt")
                        public class AptDocument {
                            @Id Long id;
                        }
                        """,
                        source -> source.path("src/main/java/com/haebang/haebang/document/AptDocument.java")
                ),
                java(
                        """
                        package com.haebang.haebang.repository;

                        import com.haebang.haebang.document.AptDocument;
                        import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
                        import java.util.List;

                        public interface AptSearchRepository extends ElasticsearchRepository<AptDocument, Long> {
                            List<AptDocument> findAptDocumentsByRoadAddressOrDp(String roadAddress, String dp);
                        }
                        """,
                        source -> source.path("src/main/java/com/haebang/haebang/repository/AptSearchRepository.java")
                ),
                java(
                        """
                        package com.haebang.haebang.configuration;

                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.springframework.data.elasticsearch.client.ClientConfiguration;
                        import org.springframework.data.elasticsearch.client.RestClients;

                        public class ElasticSearchConfig {
                            RestHighLevelClient elasticsearchClient(String host, int port) {
                                ClientConfiguration configuration = ClientConfiguration.builder()
                                        .connectedTo(host + ":" + port).build();
                                return RestClients.create(configuration).rest();
                            }
                        }
                        """,
                        """
                        package com.haebang.haebang.configuration;

                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.springframework.data.elasticsearch.client.ClientConfiguration;
                        import org.springframework.data.elasticsearch.client.RestClients;

                        public class ElasticSearchConfig {
                            /*~~>*/RestHighLevelClient elasticsearchClient(String host, int port) {
                                ClientConfiguration configuration = ClientConfiguration.builder()
                                        .connectedTo(host + ":" + port).build();
                                return /*~~>*/RestClients.create(configuration).rest();
                            }
                        }
                        """,
                        source -> source.path("src/main/java/com/haebang/haebang/configuration/ElasticSearchConfig.java")
                )
        );
    }

    @Test
    void upgradesNxConductorAndMarksDirectElasticsearch7ClientCode() {
        // Reduced from sudhiry/nx-conductor at 24b9e190:
        // https://github.com/sudhiry/nx-conductor/blob/24b9e1909517180703e78cf1d3dfd25c33e902b7/elasticsearch-persistence/build.gradle
        // https://github.com/sudhiry/nx-conductor/blob/24b9e1909517180703e78cf1d3dfd25c33e902b7/elasticsearch-persistence/src/main/java/com/netflix/conductor/elasticsearch/dao/index/ElasticSearchRestDAOV7.java
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser())
                        .typeValidationOptions(TypeValidation.none()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.springframework.data:spring-data-elasticsearch:4.4.14'
                            implementation 'org.springframework.retry:spring-retry:1.3.4'
                        }
                        """,
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.springframework.data:spring-data-elasticsearch:6.0.5'
                            implementation 'org.springframework.retry:spring-retry:1.3.4'
                        }
                        """
                ),
                java(
                        """
                        package com.netflix.conductor.elasticsearch.dao.index;

                        import org.elasticsearch.action.search.SearchRequest;
                        import org.elasticsearch.action.search.SearchResponse;
                        import org.elasticsearch.client.RequestOptions;
                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.elasticsearch.search.SearchHits;
                        import java.io.IOException;

                        public class ElasticSearchRestDAOV7 {
                            private final RestHighLevelClient elasticSearchClient;

                            public ElasticSearchRestDAOV7(RestHighLevelClient elasticSearchClient) {
                                this.elasticSearchClient = elasticSearchClient;
                            }

                            long count(String index) throws IOException {
                                SearchResponse response = elasticSearchClient.search(new SearchRequest(index), RequestOptions.DEFAULT);
                                SearchHits searchHits = response.getHits();
                                return searchHits.getTotalHits().value;
                            }
                        }
                        """,
                        """
                        package com.netflix.conductor.elasticsearch.dao.index;

                        import org.elasticsearch.action.search.SearchRequest;
                        import org.elasticsearch.action.search.SearchResponse;
                        import org.elasticsearch.client.RequestOptions;
                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.elasticsearch.search.SearchHits;
                        import java.io.IOException;

                        public class ElasticSearchRestDAOV7 {
                            private final /*~~>*/RestHighLevelClient elasticSearchClient;

                            public ElasticSearchRestDAOV7(/*~~>*/RestHighLevelClient elasticSearchClient) {
                                /*~~>*/this.elasticSearchClient = elasticSearchClient;
                            }

                            long count(String index) throws IOException {
                                /*~~>*/SearchResponse response = elasticSearchClient.search(new /*~~>*/SearchRequest(index), RequestOptions.DEFAULT);
                                /*~~>*/SearchHits searchHits = response.getHits();
                                return searchHits.getTotalHits().value;
                            }
                        }
                        """,
                        source -> source.path("elasticsearch-persistence/src/main/java/com/netflix/conductor/elasticsearch/dao/index/ElasticSearchRestDAOV7.java")
                )
        );
    }

    @ParameterizedTest(name = "leaves unlisted version {0} untouched")
    @ValueSource(strings = {"4.2.5", "4.3.0", "4.4.13", "5.0.0", "5.5.5", "6.0.4", "6.0.5", "6.1.0"})
    void leavesUnlistedVersionsUntouched(String version) {
        rewriteRun(pomXml(directPom(version)));
    }

    @Test
    void leavesUnlistedGradleVersionVariableUntouched() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def springDataElasticsearchVersion = '4.4.13'
                        dependencies {
                            api "org.springframework.data:spring-data-elasticsearch:$springDataElasticsearchVersion"
                        }
                        """
                )
        );
    }

    @Test
    void leavesUnlistedGradleVariableUntouchedWhenUnrelatedLiteralMatchesSpreadsheetVersion() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def springDataElasticsearchVersion = '4.4.13'
                        dependencies {
                            api "org.springframework.data:spring-data-elasticsearch:$springDataElasticsearchVersion"
                            implementation 'com.example:unrelated-library:4.4.8'
                        }
                        """
                )
        );
    }

    @Test
    void leavesBomManagedVersionlessDependencyUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>bom-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.data</groupId><artifactId>spring-data-bom</artifactId><version>2025.1.5</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotChangeSimilarCoordinatesOrElasticsearchClients() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.springframework.data</groupId><artifactId>spring-data-commons</artifactId><version>2.7.14</version></dependency>
                    <dependency><groupId>org.springframework.data</groupId><artifactId>spring-data-mongodb</artifactId><version>3.4.14</version></dependency>
                    <dependency><groupId>org.elasticsearch.client</groupId><artifactId>elasticsearch-rest-high-level-client</artifactId><version>7.17.3</version></dependency>
                    <dependency><groupId>co.elastic.clients</groupId><artifactId>elasticsearch-java</artifactId><version>8.18.1</version></dependency>
                    <dependency><groupId>org.testcontainers</groupId><artifactId>elasticsearch</artifactId><version>1.20.4</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotTreatMavenPluginAsDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version>
                  <build><plugins><plugin>
                    <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>4.2.4</version>
                  </plugin></plugins></build>
                </project>
                """
        ));
    }

    @Test
    void migratesOfficiallyRenamedSpringDataTypes() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser()),
                java(
                        """
                        package example;

                        import org.springframework.data.elasticsearch.ELCQueries;
                        import org.springframework.data.elasticsearch.client.elc.QueryBuilders;
                        import org.springframework.data.elasticsearch.core.Range;
                        import org.springframework.data.elasticsearch.core.RuntimeField;
                        import org.springframework.data.elasticsearch.core.completion.Completion;
                        import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentPropertyConverter;
                        import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
                        import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

                        class LegacyTypes {
                            NativeSearchQuery query;
                            NativeSearchQueryBuilder builder;
                            Range<Integer> range = Range.closed(1, 10);
                            Range.Bound<Integer> bound;
                            Completion completion;
                            ElasticsearchPersistentPropertyConverter converter;
                            RuntimeField runtimeField;

                            Object helpers() {
                                return ELCQueries.matchAllQueryAsQuery() == QueryBuilders.matchAllQueryAsQuery();
                            }
                        }
                        """,
                        """
                        package example;

                        import org.springframework.data.domain.Range;
                        import org.springframework.data.elasticsearch.client.elc.NativeQuery;
                        import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
                        import org.springframework.data.elasticsearch.client.elc.Queries;
                        import org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter;
                        import org.springframework.data.elasticsearch.core.query.RuntimeField;
                        import org.springframework.data.elasticsearch.core.suggest.Completion;

                        class LegacyTypes {
                            NativeQuery query;
                            NativeQueryBuilder builder;
                            Range<Integer> range = Range.closed(1, 10);
                            Range.Bound<Integer> bound;
                            Completion completion;
                            PropertyValueConverter converter;
                            RuntimeField runtimeField;

                            Object helpers() {
                                return Queries.matchAllQueryAsQuery() == Queries.matchAllQueryAsQuery();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesNativeBuilderButMarksTheElasticsearch7QueryDsl() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser()),
                java(
                        """
                        package example;

                        import org.elasticsearch.index.query.QueryBuilders;
                        import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
                        import org.springframework.data.elasticsearch.core.query.Query;

                        class SearchService {
                            Query all() {
                                return new NativeSearchQueryBuilder()
                                        .withQuery(QueryBuilders.matchAllQuery())
                                        .build();
                            }
                        }
                        """,
                        """
                        package example;

                        import org.elasticsearch.index.query.QueryBuilders;
                        import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
                        import org.springframework.data.elasticsearch.core.query.Query;

                        class SearchService {
                            Query all() {
                                return new NativeQueryBuilder()
                                        .withQuery(/*~~>*/QueryBuilders.matchAllQuery())
                                        .build();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedOperationsMethodsPrecisely() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser()),
                java(
                        """
                        package example;

                        import org.springframework.data.elasticsearch.core.DocumentOperations;
                        import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
                        import org.springframework.data.elasticsearch.core.ReactiveDocumentOperations;
                        import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
                        import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
                        import org.springframework.data.elasticsearch.core.query.IndexQuery;
                        import org.springframework.data.elasticsearch.core.query.Query;

                        class RemovedCalls {
                            void use(ElasticsearchOperations operations,
                                     ReactiveElasticsearchOperations reactive,
                                     DocumentOperations documents,
                                     ReactiveDocumentOperations reactiveDocuments,
                                     Query query, IndexCoordinates index, IndexQuery indexQuery) {
                                operations.stringIdRepresentation(42L);
                                reactive.execute(callback -> null);
                                documents.delete(query, Object.class, index);
                                reactiveDocuments.delete(query, Object.class);
                                indexQuery.getParentId();
                                indexQuery.setParentId("parent");
                            }
                        }
                        """,
                        """
                        package example;

                        import org.springframework.data.elasticsearch.core.DocumentOperations;
                        import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
                        import org.springframework.data.elasticsearch.core.ReactiveDocumentOperations;
                        import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
                        import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
                        import org.springframework.data.elasticsearch.core.query.IndexQuery;
                        import org.springframework.data.elasticsearch.core.query.Query;

                        class RemovedCalls {
                            void use(ElasticsearchOperations operations,
                                     ReactiveElasticsearchOperations reactive,
                                     DocumentOperations documents,
                                     ReactiveDocumentOperations reactiveDocuments,
                                     Query query, IndexCoordinates index, IndexQuery indexQuery) {
                                /*~~>*/operations.stringIdRepresentation(42L);
                                /*~~>*/reactive.execute(callback -> null);
                                /*~~>*/documents.delete(query, Object.class, index);
                                /*~~>*/reactiveDocuments.delete(query, Object.class);
                                /*~~>*/indexQuery.getParentId();
                                /*~~>*/indexQuery.setParentId("parent");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedAnnotationsAndDateFormats() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser()),
                java(
                        """
                        package example;

                        import org.springframework.data.elasticsearch.annotations.DateFormat;
                        import org.springframework.data.elasticsearch.annotations.DynamicMapping;
                        import org.springframework.data.elasticsearch.annotations.DynamicMappingValue;

                        @DynamicMapping(DynamicMappingValue.Strict)
                        class LegacyMapping {
                            DateFormat first = DateFormat.none;
                            DateFormat second = DateFormat.custom;
                        }
                        """,
                        """
                        package example;

                        import org.springframework.data.elasticsearch.annotations.DateFormat;
                        import org.springframework.data.elasticsearch.annotations.DynamicMapping;
                        import org.springframework.data.elasticsearch.annotations.DynamicMappingValue;

                        @/*~~>*/DynamicMapping(/*~~>*/DynamicMappingValue.Strict)
                        class LegacyMapping {
                            DateFormat first = /*~~>*/DateFormat.none;
                            DateFormat second = /*~~>*/DateFormat.custom;
                        }
                        """
                )
        );
    }

    @Test
    void marksRepositoryQueryAndCacheAnnotationsOnlyInElasticsearchSource() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser()),
                java(
                        """
                        package example;

                        import org.springframework.cache.annotation.Cacheable;
                        import org.springframework.data.elasticsearch.annotations.Query;
                        import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

                        interface ProductRepository extends ElasticsearchRepository<Product, String> {
                            @Cacheable("products")
                            @Query("{ \\"match\\": { \\"name\\": \\"?0\\" } }")
                            Product findCached(String name);
                        }
                        class Product {}
                        """,
                        """
                        package example;

                        import org.springframework.cache.annotation.Cacheable;
                        import org.springframework.data.elasticsearch.annotations.Query;
                        import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

                        interface ProductRepository extends ElasticsearchRepository<Product, String> {
                            /*~~>*/@Cacheable("products")
                            /*~~>*/@Query("{ \\"match\\": { \\"name\\": \\"?0\\" } }")
                            Product findCached(String name);
                        }
                        class Product {}
                        """
                ),
                java(
                        """
                        package example;
                        import org.springframework.cache.annotation.Cacheable;
                        class UnrelatedCache {
                            @Cacheable("values") String value() { return "value"; }
                        }
                        """,
                        source -> source.path("src/main/java/example/UnrelatedCache.java")
                )
        );
    }

    @Test
    void marksRhlcOldTemplatesAndDirectElasticsearchRequests() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(migrationApiParser()),
                java(
                        """
                        package example;

                        import org.elasticsearch.action.search.SearchRequest;
                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.springframework.data.elasticsearch.client.RestClients;
                        import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
                        import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
                        import org.springframework.data.elasticsearch.core.ReactiveElasticsearchTemplate;

                        class LegacyConfig extends AbstractElasticsearchConfiguration {
                            RestHighLevelClient client;
                            RestClients clients;
                            ElasticsearchTemplate transportTemplate;
                            ReactiveElasticsearchTemplate reactiveTemplate;
                            SearchRequest request;
                        }
                        """,
                        """
                        package example;

                        import org.elasticsearch.action.search.SearchRequest;
                        import org.elasticsearch.client.RestHighLevelClient;
                        import org.springframework.data.elasticsearch.client.RestClients;
                        import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
                        import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
                        import org.springframework.data.elasticsearch.core.ReactiveElasticsearchTemplate;

                        class /*~~>*/LegacyConfig extends /*~~>*/AbstractElasticsearchConfiguration {
                            /*~~>*/RestHighLevelClient client;
                            /*~~>*/RestClients clients;
                            /*~~>*/ElasticsearchTemplate transportTemplate;
                            /*~~>*/ReactiveElasticsearchTemplate reactiveTemplate;
                            /*~~>*/SearchRequest request;
                        }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesPublicRecipes() {
        Environment environment = environment();
        Recipe dependencyRecipe = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migrationRecipe = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATION_RECIPE.equals(recipe.getName())));
        assertTrue(dependencyRecipe.validate().isValid(), () -> dependencyRecipe.validate().failures().toString());
        assertTrue(migrationRecipe.validate().isValid(), () -> migrationRecipe.validate().failures().toString());
    }

    private static String directPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>search-app</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static String propertyPom(String version, String propertyName) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                 <properties><%1$s>%2$s</%1$s></properties>
                 <dependencies><dependency>
                   <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>${%1$s}</version>
                 </dependency></dependencies>
               </project>
               """.formatted(propertyName, version);
    }

    private static String managedPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>managed-parent</artifactId><version>1</version>
                 <dependencyManagement><dependencies><dependency>
                   <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId><version>%s</version>
                 </dependency></dependencies></dependencyManagement>
               </project>
               """.formatted(version);
    }

    private static String managedPropertyPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>managed-property-parent</artifactId><version>1</version>
                 <properties><spring-data-elasticsearch.version>%s</spring-data-elasticsearch.version></properties>
                 <dependencyManagement><dependencies><dependency>
                   <groupId>org.springframework.data</groupId><artifactId>spring-data-elasticsearch</artifactId>
                   <version>${spring-data-elasticsearch.version}</version>
                 </dependency></dependencies></dependencyManagement>
               </project>
               """.formatted(version);
    }

    private static String gradleBuild(String version, String configuration) {
        return """
               plugins { id 'java-library' }
               repositories { mavenCentral() }
               dependencies { %s 'org.springframework.data:spring-data-elasticsearch:%s' }
               """.formatted(configuration, version);
    }

    private static JavaParser.Builder<?, ?> migrationApiParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.springframework.data.elasticsearch.core.query;
                public interface Query {}
                """,
                """
                package org.springframework.data.elasticsearch.core.query;
                public class NativeSearchQuery implements Query {}
                """,
                """
                package org.springframework.data.elasticsearch.core.query;
                import org.elasticsearch.index.query.QueryBuilder;
                public class NativeSearchQueryBuilder {
                    public NativeSearchQueryBuilder withQuery(QueryBuilder query) { return this; }
                    public NativeSearchQuery build() { return new NativeSearchQuery(); }
                }
                """,
                """
                package org.springframework.data.elasticsearch.core.query;
                public class IndexQuery {
                    public String getParentId() { return null; }
                    public void setParentId(String parentId) {}
                }
                """,
                """
                package org.springframework.data.elasticsearch.core;
                public class Range<T> {
                    public static <T> Range<T> closed(T from, T to) { return new Range<>(); }
                    public static class Bound<T> {}
                }
                """,
                """
                package org.springframework.data.elasticsearch.core;
                public class RuntimeField {}
                """,
                """
                package org.springframework.data.elasticsearch.core.completion;
                public class Completion {}
                """,
                """
                package org.springframework.data.elasticsearch.core.mapping;
                public interface ElasticsearchPersistentPropertyConverter {}
                """,
                """
                package org.springframework.data.elasticsearch;
                public final class ELCQueries {
                    public static Object matchAllQueryAsQuery() { return null; }
                }
                """,
                """
                package org.springframework.data.elasticsearch.client.elc;
                public final class QueryBuilders {
                    public static Object matchAllQueryAsQuery() { return null; }
                }
                """,
                """
                package org.elasticsearch.index.query;
                public interface QueryBuilder {}
                """,
                """
                package org.elasticsearch.index.query;
                public final class QueryBuilders {
                    public static QueryBuilder matchAllQuery() { return null; }
                }
                """,
                """
                package org.springframework.data.elasticsearch.core;
                import org.springframework.data.elasticsearch.core.query.Query;
                public interface ElasticsearchOperations {
                    String stringIdRepresentation(Object id);
                    <T> SearchHits<T> search(Query query, Class<T> type);
                }
                """,
                """
                package org.springframework.data.elasticsearch.core;
                public interface SearchHits<T> {}
                """,
                """
                package org.springframework.data.elasticsearch.core;
                public interface ReactiveElasticsearchOperations {
                    Object execute(java.util.function.Function<Object, Object> callback);
                }
                """,
                """
                package org.springframework.data.elasticsearch.core;
                import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
                import org.springframework.data.elasticsearch.core.query.Query;
                public interface DocumentOperations {
                    Object delete(Query query, Class<?> type);
                    Object delete(Query query, Class<?> type, IndexCoordinates index);
                }
                """,
                """
                package org.springframework.data.elasticsearch.core;
                import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
                import org.springframework.data.elasticsearch.core.query.Query;
                public interface ReactiveDocumentOperations {
                    Object delete(Query query, Class<?> type);
                    Object delete(Query query, Class<?> type, IndexCoordinates index);
                }
                """,
                """
                package org.springframework.data.elasticsearch.core.mapping;
                public class IndexCoordinates {}
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public @interface DynamicMapping { DynamicMappingValue value(); }
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public enum DynamicMappingValue { Strict, True, False }
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public enum DateFormat { none, custom, date }
                """,
                """
                package org.springframework.data.annotation;
                public @interface Id {}
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public @interface Document {
                    String indexName();
                    boolean createIndex() default true;
                }
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public @interface Field {
                    FieldType type();
                    DateFormat format();
                }
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public enum FieldType { Date, Text, Keyword }
                """,
                """
                package org.springframework.data.elasticsearch.annotations;
                public @interface Query { String value(); }
                """,
                """
                package org.springframework.data.elasticsearch.repository;
                public interface ElasticsearchRepository<T, ID> {}
                """,
                """
                package org.springframework.cache.annotation;
                public @interface Cacheable { String value(); }
                """,
                """
                package org.springframework.cache.annotation;
                public @interface CachePut { String value(); }
                """,
                """
                package org.springframework.cache.annotation;
                public @interface CacheEvict { String value(); }
                """,
                """
                package org.elasticsearch.client;
                import java.io.IOException;
                import org.elasticsearch.action.search.SearchRequest;
                import org.elasticsearch.action.search.SearchResponse;
                public class RestHighLevelClient {
                    public SearchResponse search(SearchRequest request, RequestOptions options) throws IOException { return null; }
                }
                """,
                """
                package org.elasticsearch.action.search;
                public class SearchRequest { public SearchRequest(String index) {} }
                """,
                """
                package org.springframework.data.elasticsearch.client;
                import org.elasticsearch.client.RestHighLevelClient;
                public final class RestClients {
                    public static RestClients create(ClientConfiguration configuration) { return new RestClients(); }
                    public RestHighLevelClient rest() { return null; }
                }
                """,
                """
                package org.springframework.data.elasticsearch.client;
                public final class ClientConfiguration {
                    public static Builder builder() { return new Builder(); }
                    public static class Builder {
                        public Builder connectedTo(String host) { return this; }
                        public ClientConfiguration build() { return new ClientConfiguration(); }
                    }
                }
                """,
                """
                package org.springframework.data.elasticsearch.config;
                public abstract class AbstractElasticsearchConfiguration {}
                """,
                """
                package org.springframework.data.elasticsearch.core;
                public class ElasticsearchTemplate {}
                """,
                """
                package org.springframework.data.elasticsearch.core;
                public class ReactiveElasticsearchTemplate {}
                """,
                """
                package javax.persistence;
                public @interface Id {}
                """,
                """
                package org.elasticsearch.client;
                public class RequestOptions { public static final RequestOptions DEFAULT = new RequestOptions(); }
                """,
                """
                package org.elasticsearch.action.search;
                public class SearchResponse {
                    public org.elasticsearch.search.SearchHits getHits() { return null; }
                }
                """,
                """
                package org.elasticsearch.search;
                public class SearchHits {
                    public TotalHits getTotalHits() { return null; }
                    public static class TotalHits { public long value; }
                }
                """
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springdataelasticsearch")
                .scanYamlResources()
                .build();
    }
}
