package com.huawei.clouds.openrewrite.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

class Slf4jBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSlf4jBuildRisks());
    }

    @Test
    void marksJavaBelow8AndLegacyProvider() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><java.version>1.7</java.version></properties>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.2.13</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><!--~~(SLF4J 2 requires Java 8 or newer; upgrade compiler and runtime before changing the logging stack)~~>--><java.version>1.7</java.version></properties>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                    <!--~~(This provider targets SLF4J 1.x; select a provider explicitly compatible with SLF4J 2)~~>--><dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.2.13</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void marksUnlistedSlf4j1ApiInsteadOfUpgradingIt() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unlisted</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.31</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unlisted</artifactId><version>1</version><dependencies>
                  <!--~~(This SLF4J 1.x API version is outside the spreadsheet's explicit source set and was not upgraded automatically)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.31</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void marksExternallyManagedProviderForVerification() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-provider</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.2.12</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-provider</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.2.12</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <!--~~(This provider version is externally managed or computed; verify that it explicitly supports SLF4J 2)~~>--><dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void marksEveryExplicitProviderWhenMultipleArePresent() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multiple</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>2.0.17</version></dependency>
                  <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.5.18</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multiple</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <!--~~(Multiple SLF4J providers are declared; retain exactly one runtime provider after dependency resolution)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>2.0.17</version></dependency>
                  <!--~~(Multiple SLF4J providers are declared; retain exactly one runtime provider after dependency resolution)~~>--><dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.5.18</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void marksJulBridgeRecursionLoop() {
        rewriteRun(pomXml(
                loopPom(
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>",
                        "<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>2.0.17</version></dependency>"),
                loopPom(
                        "<!--~~(jul-to-slf4j and slf4j-jdk14 form a JUL/SLF4J recursion loop; retain only the required direction)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>",
                        "<!--~~(jul-to-slf4j and slf4j-jdk14 form a JUL/SLF4J recursion loop; retain only the required direction)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>2.0.17</version></dependency>")
        ));
    }

    @Test
    void marksLog4jBridgeRecursionLoop() {
        rewriteRun(pomXml(
                loopPom(
                        "<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-to-slf4j</artifactId><version>2.24.3</version></dependency>",
                        "<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j2-impl</artifactId><version>2.24.3</version></dependency>",
                        "<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>2.24.3</version></dependency>"),
                loopPom(
                        "<!--~~(Log4j-to-SLF4J and SLF4J-to-Log4j are both present and can recurse; select one routing direction)~~>--><dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-to-slf4j</artifactId><version>2.24.3</version></dependency>",
                        "<!--~~(Log4j-to-SLF4J and SLF4J-to-Log4j are both present and can recurse; select one routing direction)~~>--><dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j2-impl</artifactId><version>2.24.3</version></dependency>",
                        "<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>2.24.3</version></dependency>")
        ));
    }

    @Test
    void marksCommonsLoggingBridgeRecursionLoop() {
        rewriteRun(pomXml(
                loopPom(
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>2.0.17</version></dependency>",
                        "<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jcl</artifactId><version>1.7.36</version></dependency>"),
                loopPom(
                        "<!--~~(jcl-over-slf4j and slf4j-jcl form a Commons Logging recursion loop; retain only one direction)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>2.0.17</version></dependency>",
                        "<!--~~(jcl-over-slf4j and slf4j-jcl form a Commons Logging recursion loop; retain only one direction)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jcl</artifactId><version>1.7.36</version></dependency>")
        ));
    }

    @Test
    void marksReload4jBridgeRecursionLoop() {
        rewriteRun(pomXml(
                loopPom(
                        "<dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>2.0.17</version></dependency>",
                        "<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-reload4j</artifactId><version>2.0.17</version></dependency>"),
                loopPom(
                        "<!--~~(log4j-over-slf4j with an SLF4J-to-Log4j provider can recurse; retain only one direction)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>2.0.17</version></dependency>",
                        "<!--~~(log4j-over-slf4j with an SLF4J-to-Log4j provider can recurse; retain only one direction)~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-reload4j</artifactId><version>2.0.17</version></dependency>")
        ));
    }

    @Test
    void marksShadePluginWithoutServiceResourceTransformer() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shade</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><version>3.6.0</version></plugin></plugins></build>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shade</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency></dependencies>
                  <build><plugins><!--~~(Shaded jars must merge META-INF/services; add and verify ServicesResourceTransformer for the SLF4J provider descriptor)~~>--><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><version>3.6.0</version></plugin></plugins></build>
                </project>
                """
        ));
    }

    @Test
    void leavesConfiguredShadeAndCompatiblePlatformUnmarked() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ready</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.4.14</version></dependency>
                  </dependencies>
                  <build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration><transformers><transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/></transformers></configuration></plugin></plugins></build>
                </project>
                """
        ));
    }

    @Test
    void marksGradleJavaAndProviderRisks() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = '1.7'
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.17'
                    runtimeOnly 'org.slf4j:slf4j-simple:1.7.31'
                }
                """,
                """
                plugins { id 'java' }
                sourceCompatibility = /*~~(SLF4J 2 requires Java 8 or newer; upgrade Gradle toolchain and runtime)~~>*/'1.7'
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.17'
                    runtimeOnly /*~~(This provider targets SLF4J 1.x; select a provider explicitly compatible with SLF4J 2)~~>*/'org.slf4j:slf4j-simple:1.7.31'
                }
                """
        ));
    }

    @Test
    void marksGradleJavaVersionConstantBelow8() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = JavaVersion.VERSION_1_7
                repositories { mavenCentral() }
                dependencies { implementation 'org.slf4j:slf4j-api:2.0.17' }
                """,
                """
                plugins { id 'java' }
                sourceCompatibility = /*~~(SLF4J 2 requires Java 8 or newer; upgrade Gradle toolchain and runtime)~~>*/JavaVersion.VERSION_1_7
                repositories { mavenCentral() }
                dependencies { implementation 'org.slf4j:slf4j-api:2.0.17' }
                """
        ));
    }

    @Test
    void leavesUnrelatedLegacyPomUnmarked() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unrelated</artifactId><version>1</version>
                  <properties><java.version>1.7</java.version></properties>
                  <dependencies><dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.2.13</version></dependency></dependencies>
                </project>
                """
        ));
    }

    private static String loopPom(String... dependencies) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>loop</artifactId><version>1</version><dependencies>
                 <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                 %s
               </dependencies></project>
               """.formatted(String.join("\n  ", dependencies));
    }
}
