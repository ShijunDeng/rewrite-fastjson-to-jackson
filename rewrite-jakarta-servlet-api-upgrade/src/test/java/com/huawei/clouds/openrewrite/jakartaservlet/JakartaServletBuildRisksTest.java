package com.huawei.clouds.openrewrite.jakartaservlet;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;

class JakartaServletBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJakartaServlet61BuildRisks());
    }

    @Test
    void marksJava17AndContainerProvidedScope() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>war</artifactId><version>1</version>
                  <properties><java.version>11</java.version></properties>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>war</artifactId><version>1</version>
                  <properties><!--~~(Jakarta Servlet 6.1 requires Java 17+ for compilation, runtime, CI image and container)~~>--><java.version>11</java.version></properties>
                  <dependencies><!--~~(Servlet API is container-provided; use Maven provided scope unless an explicitly verified packaging model requires otherwise)~~>--><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void marksExternalAndUnlistedVersionsSeparately() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><scope>provided</scope></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies></dependencyManagement>
                          <dependencies><!--~~(Jakarta Servlet API is externally managed; upgrade the owning Jakarta EE/container BOM and verify the resolved API is exactly 6.1.0)~~>--><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><scope>provided</scope></dependency></dependencies>
                        </project>
                        """),
                pomXml(pom("<dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>4.0.2</version><scope>provided</scope></dependency>"),
                        pom("<!--~~(This Servlet API version is outside the spreadsheet's four explicit sources or cannot be resolved and was not upgraded automatically)~~>--><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>4.0.2</version><scope>provided</scope></dependency>"),
                        spec -> spec.path("unlisted/pom.xml")));
    }

    @Test
    void marksJavaxSiblingWebApisAndContainerAlignment() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>stack</artifactId><version>1</version><dependencies>
                  <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope></dependency>
                  <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                  <dependency><groupId>jakarta.servlet.jsp</groupId><artifactId>jakarta.servlet.jsp-api</artifactId><version>3.1.1</version></dependency>
                  <dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId><version>10.1.42</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>12.0.20</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>stack</artifactId><version>1</version><dependencies>
                  <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope></dependency>
                  <!--~~(A javax Servlet artifact remains beside the Jakarta 6.1 migration; select a Jakarta-compatible replacement for the whole dependency chain)~~>--><dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                  <!--~~(Align Jakarta Pages, WebSocket, EL, JSTL and related web APIs with one Jakarta EE 11 / Servlet 6.1 platform)~~>--><dependency><groupId>jakarta.servlet.jsp</groupId><artifactId>jakarta.servlet.jsp-api</artifactId><version>3.1.1</version></dependency>
                  <!--~~(Tomcat versions below 11 do not implement Servlet 6.1; upgrade the container as a platform, not only the API JAR)~~>--><dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId><version>10.1.42</version></dependency>
                  <!--~~(Container dependency detected; verify this exact Tomcat/Jetty/Undertow line implements Servlet 6.1 and supports Java 17)~~>--><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>12.0.20</version></dependency>
                </dependencies></project>
                """));
    }

    @Test
    void marksSpringBootAsPlatformOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.3</version></parent><groupId>example</groupId><artifactId>boot</artifactId><version>1</version>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><!--~~(The platform owns the embedded Servlet implementation; verify this Spring Boot line resolves a Servlet 6.1-compatible container instead of overriding only the API JAR)~~>--><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.3</version></parent><groupId>example</groupId><artifactId>boot</artifactId><version>1</version>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void preservesCompatibleMavenAndDependencyManagementScope() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ready</artifactId><version>1</version>
                  <properties><maven.compiler.release>17</maven.compiler.release></properties>
                  <dependencyManagement><dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void marksGradleBaselinePackagingJavaxAndTomcat() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = '11'
                dependencies {
                    implementation 'jakarta.servlet:jakarta.servlet-api:6.1.0'
                    compileOnly 'javax.servlet:javax.servlet-api:4.0.1'
                    runtimeOnly 'org.apache.tomcat.embed:tomcat-embed-core:10.1.42'
                }
                """,
                """
                plugins { id 'java' }
                /*~~(Jakarta Servlet 6.1 requires a Java 17+ Gradle toolchain and runtime)~~>*/sourceCompatibility = '11'
                dependencies {
                    implementation /*~~(Servlet API is container-provided; prefer compileOnly/providedCompile unless packaging it was explicitly verified)~~>*/'jakarta.servlet:jakarta.servlet-api:6.1.0'
                    compileOnly /*~~(A javax Servlet artifact remains beside the Jakarta 6.1 migration; migrate the entire dependency chain)~~>*/'javax.servlet:javax.servlet-api:4.0.1'
                    runtimeOnly /*~~(Tomcat versions below 11 do not implement Servlet 6.1; upgrade the container platform)~~>*/'org.apache.tomcat.embed:tomcat-embed-core:10.1.42'
                }
                """));
    }

    @Test
    void preservesCompatibleGradleCompileOnlyStack() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
                dependencies { compileOnly 'jakarta.servlet:jakarta.servlet-api:6.1.0' }
                """));
    }

    @Test
    void marksKotlinToolchainAndUnlistedLiteral() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                java { toolchain { languageVersion.set(JavaLanguageVersion.of(11)) } }
                dependencies { compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.1") }
                """,
                """
                plugins { java }
                java { toolchain { languageVersion.set(/*~~(Jakarta Servlet 6.1 requires a Java 17+ Gradle toolchain and runtime)~~>*/JavaLanguageVersion.of(11)) } }
                dependencies { compileOnly(/*~~(This Servlet API version is outside the spreadsheet's explicit source set and was not upgraded automatically)~~>*/"jakarta.servlet:jakarta.servlet-api:6.0.1") }
                """));
    }

    @Test
    void leavesUnrelatedLegacyBuildUntouched() {
        rewriteRun(pomXml(
                "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>other</artifactId><version>1</version><properties><java.version>8</java.version></properties></project>"));
    }

    private static String pom(String dependencies) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>" +
               dependencies + "</dependencies></project>";
    }
}
