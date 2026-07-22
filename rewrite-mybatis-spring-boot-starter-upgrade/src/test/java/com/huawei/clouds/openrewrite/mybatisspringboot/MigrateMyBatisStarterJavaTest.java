package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateMyBatisStarterJavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(
                        UpgradeMyBatisSpringBootStarterTest.MIGRATE))
                .parser(JavaParser.fromJavaVersion().classpath(
                        "junit", "spring-core", "spring-beans", "spring-context", "spring-test",
                        "spring-boot", "spring-boot-autoconfigure", "spring-boot-test",
                        "spring-boot-test-autoconfigure", "mybatis", "mybatis-spring",
                        "mybatis-spring-boot-autoconfigure", "mybatis-spring-boot-test-autoconfigure"));
    }

    @Test
    void migratesSpringbootMybatisMockBeansFromFixedCommitAndMarksJUnit4() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import org.junit.runner.RunWith;
                        import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
                        import org.springframework.boot.test.mock.mockito.MockBean;
                        import org.springframework.test.context.junit4.SpringRunner;

                        @RunWith(SpringRunner.class)
                        @MybatisTest
                        class ApiUserControllerWebMvcTest {
                            @MockBean
                            private UserMapper userMapper;
                            @MockBean
                            private RoleMapper roleMapper;
                            @MockBean
                            private MenuMapper menuMapper;
                        }

                        interface UserMapper {}
                        interface RoleMapper {}
                        interface MenuMapper {}
                        """,
                        """
                        import org.junit.runner.RunWith;
                        import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
                        import org.springframework.test.context.bean.override.mockito.MockitoBean;
                        import org.springframework.test.context.junit4.SpringRunner;

                        /*~~(Spring Boot 4 tests use JUnit Jupiter by default; migrate this JUnit 4 runner and its rules/lifecycle together)~~>*/@RunWith(SpringRunner.class)
                        @MybatisTest
                        class ApiUserControllerWebMvcTest {
                            @MockitoBean
                            private UserMapper userMapper;
                            @MockitoBean
                            private RoleMapper roleMapper;
                            @MockitoBean
                            private MenuMapper menuMapper;
                        }

                        interface UserMapper {}
                        interface RoleMapper {}
                        interface MenuMapper {}
                        """,
                        source -> source.path("src/test/java/com/staroot/mybatis/controller/ApiUserControllerWebMvcTest.java")
                )
        );
    }

    @Test
    void leavesAttributedMockBeanAndMarksItForReview() {
        rewriteRun(
                java(
                        """
                        import org.springframework.boot.test.mock.mockito.MockBean;

                        class NamedMock {
                            @MockBean(name = "userMapper")
                            UserMapper mapper;
                        }
                        interface UserMapper {}
                        """,
                        """
                        import org.springframework.boot.test.mock.mockito.MockBean;

                        class NamedMock {
                            /*~~(Attributed @MockBean cannot be converted mechanically because @MockitoBean has a different attribute contract)~~>*/@MockBean(name = "userMapper")
                            UserMapper mapper;
                        }
                        interface UserMapper {}
                        """
                )
        );
    }

    @Test
    void movesTestRestTemplateAndAddsBoot4AutoConfiguration() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.boot.test.context.SpringBootTest;
                        import org.springframework.boot.test.web.client.TestRestTemplate;

                        @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                        class HttpIntegrationTest {
                            @Autowired
                            TestRestTemplate restTemplate;
                        }
                        """,
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.boot.resttestclient.TestRestTemplate;
                        import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
                        import org.springframework.boot.test.context.SpringBootTest;

                        @AutoConfigureTestRestTemplate
                        @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                        class HttpIntegrationTest {
                            @Autowired
                            TestRestTemplate restTemplate;
                        }
                        """
                )
        );
    }

    @Test
    void movesBoot4JdbcAutoConfigurationType() {
        rewriteRun(
                java(
                        """
                        import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

                        class AutoConfigurationReference {
                            Class<?> type = DataSourceAutoConfiguration.class;
                        }
                        """,
                        """
                        import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

                        class AutoConfigurationReference {
                            Class<?> type = DataSourceAutoConfiguration.class;
                        }
                        """
                )
        );
    }

    @Test
    void movesSpringBatchSixInfrastructureTypesAndMarksBatchConfiguration() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.springframework.batch.item; public interface ItemReader<T> {}",
                        "package org.springframework.batch.core.configuration.annotation; public @interface EnableBatchProcessing {}"
                )),
                java(
                        """
                        import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
                        import org.springframework.batch.item.ItemReader;

                        @EnableBatchProcessing
                        class BatchConfiguration {
                            ItemReader<String> reader;
                        }
                        """,
                        """
                        import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
                        import org.springframework.batch.infrastructure.item.ItemReader;

                        /*~~(Spring Batch 6 changes infrastructure defaults and repository selection; review data source, transaction manager, and restart metadata)~~>*/@EnableBatchProcessing
                        class BatchConfiguration {
                            ItemReader<String> reader;
                        }
                        """
                )
        );
    }

    @Test
    void marksMapperScanAliasAndSessionBoundaryConflicts() {
        rewriteRun(
                java(
                        """
                        import org.mybatis.spring.annotation.MapperScan;

                        @MapperScan(value = "com.example.mapper", basePackages = "com.example.other")
                        class AliasConflict {}

                        @MapperScan(basePackages = "com.example.mapper",
                                sqlSessionFactoryRef = "factory", sqlSessionTemplateRef = "template")
                        class SessionConflict {}
                        """,
                        """
                        import org.mybatis.spring.annotation.MapperScan;

                        /*~~(@MapperScan value and basePackages are @AliasFor aliases; keep only one after verifying the intended packages)~~>*/@MapperScan(value = "com.example.mapper", basePackages = "com.example.other")
                        class AliasConflict {}

                        /*~~(@MapperScan specifies both factory and template references; select one session boundary explicitly)~~>*/@MapperScan(basePackages = "com.example.mapper",
                                sqlSessionFactoryRef = "factory", sqlSessionTemplateRef = "template")
                        class SessionConflict {}
                        """
                )
        );
    }

    @Test
    void marksMultiDataSourceManualFactoryAndChangedPropertiesAccess() {
        rewriteRun(
                java(
                        """
                        import javax.sql.DataSource;
                        import org.apache.ibatis.session.Configuration;
                        import org.mybatis.spring.SqlSessionFactoryBean;
                        import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
                        import org.springframework.context.annotation.Bean;

                        class ManualMyBatisConfiguration {
                            @Bean DataSource first() { return null; }
                            @Bean DataSource second() { return null; }

                            SqlSessionFactoryBean factory() {
                                return new SqlSessionFactoryBean();
                            }

                            Configuration core(MybatisProperties properties) {
                                return properties.getConfiguration();
                            }
                        }
                        """,
                        """
                        import javax.sql.DataSource;
                        import org.apache.ibatis.session.Configuration;
                        import org.mybatis.spring.SqlSessionFactoryBean;
                        import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
                        import org.springframework.context.annotation.Bean;

                        /*~~(Multiple DataSource beans disable single-candidate MyBatis auto-configuration; bind each mapper scan, session factory, and transaction manager explicitly)~~>*/class ManualMyBatisConfiguration {
                            @Bean DataSource first() { return null; }
                            @Bean DataSource second() { return null; }

                            SqlSessionFactoryBean factory() {
                                return /*~~(Manual SqlSessionFactoryBean bypasses starter defaults; verify SpringBootVFS, mapper locations, plugins, type handlers, and the intended DataSource)~~>*/new SqlSessionFactoryBean();
                            }

                            Configuration core(MybatisProperties properties) {
                                return /*~~(MybatisProperties.configuration changed from MyBatis Configuration to CoreConfiguration in Starter 3; move runtime customization to ConfigurationCustomizer)~~>*/properties.getConfiguration();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksJakartaEeRemnantsButNotJavaSeXa() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package javax.persistence; public interface EntityManager {}"
                )),
                java(
                        """
                        import javax.persistence.EntityManager;
                        import javax.transaction.xa.XAResource;

                        class PersistenceBoundary {
                            EntityManager entityManager;
                            XAResource xaResource;
                        }
                        """,
                        """
                        import javax.persistence.EntityManager;
                        import javax.transaction.xa.XAResource;

                        class PersistenceBoundary {
                            /*~~(Spring Boot 4 uses Jakarta APIs; migrate this javax EE type and its dependency as part of the platform upgrade)~~>*/EntityManager entityManager;
                            XAResource xaResource;
                        }
                        """
                )
        );
    }
}
