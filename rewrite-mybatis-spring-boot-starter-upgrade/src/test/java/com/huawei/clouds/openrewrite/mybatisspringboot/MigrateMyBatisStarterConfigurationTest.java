package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateMyBatisStarterConfigurationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeMyBatisSpringBootStarterTest.environment().activateRecipes(
                UpgradeMyBatisSpringBootStarterTest.MIGRATE));
    }

    @Test
    void migratesStudentSysDefaultScriptingLanguageFromFixedCommit() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        """
                        spring.datasource.url=jdbc:mysql://localhost/student
                        mybatis.configuration.default-scripting-language=org.apache.ibatis.scripting.xmltags.XMLLanguageDriver
                        """,
                        """
                        spring.datasource.url=jdbc:mysql://localhost/student
                        mybatis.default-scripting-language-driver=org.apache.ibatis.scripting.xmltags.XMLLanguageDriver
                        """,
                        source -> source.path("src/main/resources/application.properties")
                )
        );
    }

    @Test
    void migratesNestedYamlAndVelocityReplacement() {
        rewriteRun(
                yaml(
                        """
                        mybatis:
                          configuration:
                            default-scripting-language: org.apache.ibatis.scripting.xmltags.XMLLanguageDriver
                          scripting-language-driver:
                            velocity:
                              userdirective: com.example.Directive
                        """,
                        """
                        mybatis:
                          scripting-language-driver:
                            velocity:
                              velocity-settings.runtime.custom_directives: com.example.Directive
                          default-scripting-language-driver: org.apache.ibatis.scripting.xmltags.XMLLanguageDriver
                        """,
                        source -> source.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void normalizesEasyHousingMyBatisSpringSchemaFromFixedCommit() {
        rewriteRun(
                xml(
                        """
                        <beans xmlns="http://www.springframework.org/schema/beans"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xmlns:mybatis="http://mybatis.org/schema/mybatis-spring"
                               xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                                                   http://mybatis.org/schema/mybatis-spring http://mybatis.org/schema/mybatis-spring-1.2.xsd">
                            <mybatis:scan base-package="com.easyhousing.mapper"/>
                        </beans>
                        """,
                        """
                        <beans xmlns="http://www.springframework.org/schema/beans"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xmlns:mybatis="http://mybatis.org/schema/mybatis-spring"
                               xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                                                   http://mybatis.org/schema/mybatis-spring http://mybatis.org/schema/mybatis-spring.xsd">
                            <mybatis:scan base-package="com.easyhousing.mapper"/>
                        </beans>
                        """,
                        source -> source.path("config/bean.xml")
                )
        );
    }

    @Test
    void marksPropertiesBehaviorRisksAndLeavesSafeExecutorUnmarked() {
        rewriteRun(
                properties(
                        """
                        mybatis.executor-type=BATCH
                        mybatis.inject-sql-session-on-mapper-scan=false
                        mybatis.configuration.vfs-impl=com.example.CustomVfs
                        mybatis.configuration.multiple-result-sets-enabled=true
                        """,
                        """
                        ~~(BATCH executor changes flush and transaction boundaries; verify generated keys, rollback, and partial failures)~~>mybatis.executor-type=BATCH
                        ~~(false restores pre-2.2.2 mapper injection and is incompatible with the documented native/AOT path)~~>mybatis.inject-sql-session-on-mapper-scan=false
                        ~~(A custom VFS overrides the starter's SpringBootVFS selection; test executable/layered jars and non-ASCII paths)~~>mybatis.configuration.vfs-impl=com.example.CustomVfs
                        ~~(MyBatis 3.5.19 no longer uses multipleResultSetsEnabled; remove it after confirming driver behavior)~~>mybatis.configuration.multiple-result-sets-enabled=true
                        """
                ),
                properties("mybatis.executor-type=SIMPLE")
        );
    }

    @Test
    void marksPropertiesXmlAndCoreConfigurationConflict() {
        rewriteRun(
                properties(
                        """
                        mybatis.config-location=classpath:mybatis-config.xml
                        mybatis.configuration.map-underscore-to-camel-case=true
                        """,
                        """
                        ~~(mybatis.config-location and mybatis.configuration.* cannot be used together; choose XML or bound CoreConfiguration)~~>mybatis.config-location=classpath:mybatis-config.xml
                        ~~(mybatis.config-location and mybatis.configuration.* cannot be used together; choose XML or bound CoreConfiguration)~~>mybatis.configuration.map-underscore-to-camel-case=true
                        """
                )
        );
    }

    @Test
    void marksYamlAotAndConfigurationRisks() {
        rewriteRun(
                yaml(
                        """
                        mybatis:
                          executor-type: BATCH
                          inject-sql-session-on-mapper-scan: false
                          config-location: classpath:mybatis-config.xml
                          configuration:
                            vfs-impl: com.example.CustomVfs
                        """,
                        """
                        mybatis:
                          ~~(BATCH executor changes flush and transaction boundaries; verify generated keys, rollback, and partial failures)~~>executor-type: BATCH
                          ~~(false restores pre-2.2.2 mapper injection and is incompatible with the documented native/AOT path)~~>inject-sql-session-on-mapper-scan: false
                          ~~(config-location selects XML configuration; do not combine it with mybatis.configuration.* bound CoreConfiguration settings)~~>config-location: classpath:mybatis-config.xml
                          configuration:
                            ~~(A custom VFS overrides the starter's SpringBootVFS selection; test executable/layered jars and non-ASCII paths)~~>vfs-impl: com.example.CustomVfs
                        """
                )
        );
    }

    @Test
    void marksOnlyAmbiguousXmlMapperScan() {
        rewriteRun(
                xml(
                        """
                        <beans xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <mybatis:scan base-package="com.example.mapper"
                                          factory-ref="sqlSessionFactory"
                                          template-ref="sqlSessionTemplate"/>
                        </beans>
                        """,
                        """
                        <beans xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <!--~~(mybatis:scan specifies both factory-ref and template-ref; select one session boundary explicitly)~~>--><mybatis:scan base-package="com.example.mapper"
                                          factory-ref="sqlSessionFactory"
                                          template-ref="sqlSessionTemplate"/>
                        </beans>
                        """
                ),
                xml(
                        """
                        <beans xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <mybatis:scan base-package="com.example.mapper" factory-ref="sqlSessionFactory"/>
                        </beans>
                        """
                )
        );
    }
}
