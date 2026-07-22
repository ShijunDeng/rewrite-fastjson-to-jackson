package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

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
    void alignsExplicitCompanionModulesWhenStarterIsListed() {
        rewriteRun(
                pomXml(
                        companionPom("2.3.1"),
                        companionPom("4.0.0")
                )
        );
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
                        </project>
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesPublicRecipes() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> MIGRATE.equals(candidate.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
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
