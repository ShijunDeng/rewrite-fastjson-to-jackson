package com.huawei.clouds.openrewrite.shedlockspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class ShedLockSpringBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindShedLockSpring7BuildRisks());
    }

    @Test
    void marksJavaSpringJakartaAndShedLockAlignment() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><java.version>11</java.version></properties><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-core</artifactId><version>4.44.0</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.39</version></dependency>
                    <dependency><groupId>javax.annotation</groupId><artifactId>javax.annotation-api</artifactId><version>1.3.2</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><!--~~(ShedLock 7 requires a Java 17+ compiler and runtime)~~>--><java.version>11</java.version></properties><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency>
                    <!--~~(Align ShedLock core, Spring integration, BOM and every provider to one compatible 7.2.1 line)~~>--><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-core</artifactId><version>4.44.0</version></dependency>
                    <!--~~(ShedLock 7 supports the Spring Framework 6.2/7 line; align the framework or Boot BOM as a unit)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.39</version></dependency>
                    <!--~~(Legacy javax dependency detected beside Spring 6.2/7; select the corresponding Jakarta API and compatible framework stack)~~>--><dependency><groupId>javax.annotation</groupId><artifactId>javax.annotation-api</artifactId><version>1.3.2</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void marksExternallyManagedAndUnlistedSpringSeparately() {
        rewriteRun(
                pomXml(managedPom("<dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId></dependency>"),
                        managedPom("<!--~~(ShedLock Spring is externally managed; verify the resolved version is exactly 7.2.1 without overriding the platform locally)~~>--><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId></dependency>")),
                pomXml(pom("<dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.42.0</version></dependency>"),
                        pom("<!--~~(This ShedLock Spring version is outside the spreadsheet's five explicit sources or cannot be resolved and was not upgraded automatically)~~>--><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.42.0</version></dependency>")));
    }

    @Test
    void marksUnsupportedSpringBootParent() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.9</version></parent><groupId>example</groupId><artifactId>boot</artifactId><version>1</version><dependencies>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><!--~~(ShedLock 7 supports Spring Boot 3.4/3.5/4.x and Spring Framework 6.2/7; upgrade the platform deliberately)~~>--><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.9</version></parent><groupId>example</groupId><artifactId>boot</artifactId><version>1</version><dependencies>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency>
                </dependencies></project>
                """));
    }

    @Test
    void preservesCompatibleBootAndAlignedStack() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.8</version></parent><groupId>example</groupId><artifactId>ready</artifactId><version>1</version>
                  <properties><java.version>17</java.version></properties><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-core</artifactId><version>7.2.1</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.8</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void marksGradleBaselineAndAlignmentRisks() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = '11'
                repositories { mavenCentral() }
                dependencies {
                    implementation 'net.javacrumbs.shedlock:shedlock-spring:7.2.1'
                    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.44.0'
                    implementation 'org.springframework:spring-context:6.1.21'
                    implementation 'javax.annotation:javax.annotation-api:1.3.2'
                }
                """,
                """
                plugins { id 'java' }
                /*~~(ShedLock 7 requires a Java 17+ Gradle toolchain and runtime)~~>*/sourceCompatibility = '11'
                repositories { mavenCentral() }
                dependencies {
                    implementation 'net.javacrumbs.shedlock:shedlock-spring:7.2.1'
                    implementation /*~~(Align ShedLock core, Spring integration, BOM and every provider to one compatible 7.2.1 line)~~>*/'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.44.0'
                    implementation /*~~(ShedLock 7 supports the Spring Framework 6.2/7 line; align framework dependencies together)~~>*/'org.springframework:spring-context:6.1.21'
                    implementation /*~~(Legacy javax dependency detected beside Spring 6.2/7; select the corresponding Jakarta API)~~>*/'javax.annotation:javax.annotation-api:1.3.2'
                }
                """));
    }

    @Test
    void preservesCompatibleGradleStack() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'net.javacrumbs.shedlock:shedlock-spring:7.2.1'
                    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1'
                    implementation 'org.springframework:spring-context:7.0.1'
                    implementation 'jakarta.annotation:jakarta.annotation-api:3.0.0'
                }
                """));
    }

    @Test
    void marksVirtualThreadApplicationConfiguration() {
        rewriteRun(text(
                "spring.threads.virtual.enabled=true\n",
                "~~(Virtual-thread scheduling detected; verify ShedLock proxy boundaries and ThreadLocal LockAssert/LockExtender usage)~~>spring.threads.virtual.enabled=true\n",
                source -> source.path("src/main/resources/application.properties")));
    }

    @Test
    void marksNestedAndDottedVirtualThreadYamlButNotFalseOrUnrelatedFiles() {
        rewriteRun(
                yaml(
                        """
                        spring:
                          threads:
                            virtual:
                              enabled: true
                        """,
                        """
                        spring:
                          threads:
                            virtual:
                              ~~(Virtual-thread scheduling detected; verify ShedLock proxy boundaries and ThreadLocal LockAssert/LockExtender usage)~~>enabled: true
                        """,
                        source -> source.path("src/main/resources/application.yml")),
                yaml(
                        "spring.threads.virtual.enabled: true\n",
                        "~~(Virtual-thread scheduling detected; verify ShedLock proxy boundaries and ThreadLocal LockAssert/LockExtender usage)~~>spring.threads.virtual.enabled: true\n",
                        source -> source.path("src/test/resources/application-test.yaml")),
                yaml(
                        "spring:\n  threads:\n    virtual:\n      enabled: false\n",
                        source -> source.path("src/main/resources/application.yml")),
                yaml(
                        "spring:\n  threads:\n    virtual:\n      enabled: true\n",
                        source -> source.path("src/main/resources/fixture.yml")));
    }

    @Test
    void marksObsoleteSpringXmlButLeavesUnrelatedXmlUntouched() {
        rewriteRun(
                xml("<beans><bean class=\"net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactoryBean\"/></beans>",
                        "<!--~~(ShedLock Spring XML configuration has been unsupported since 3.x; replace it with Java configuration before 7.2.1)~~>--><beans><bean class=\"net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactoryBean\"/></beans>"),
                xml("<beans><bean class=\"example.Scheduler\"/></beans>"));
    }

    @Test
    void leavesUnrelatedLegacyBuildUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>other</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.39</version></dependency></dependencies>
                </project>
                """));
    }

    private static String pom(String dependencies) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>
                 %s
               </dependencies></project>
               """.formatted(dependencies);
    }

    private static String managedPom(String dependencies) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                 <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-bom</artifactId><version>7.2.1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                 <dependencies>%s</dependencies>
               </project>
               """.formatted(dependencies);
    }
}
