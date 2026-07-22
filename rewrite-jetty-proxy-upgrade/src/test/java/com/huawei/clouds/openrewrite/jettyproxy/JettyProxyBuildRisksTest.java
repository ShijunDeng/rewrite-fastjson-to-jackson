package com.huawei.clouds.openrewrite.jettyproxy;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class JettyProxyBuildRisksTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.jettyproxy.FindJettyProxy12BuildMigrationRisks";
    private static final String JAVA =
            "Jetty 12.1 requires Java 17 or newer; align compiler, toolchain, CI, container and runtime JDKs";
    private static final String MANAGED =
            "jetty-proxy is versionless or dynamically/external managed; migrate the owning Jetty BOM, parent, platform or catalog to 12.1.8";
    private static final String ALIGNMENT =
            "Jetty modules must be aligned on the 12.1.8 line; migrate the owning BOM/property and renamed HTTP2/HTTP3 artifacts instead of mixing Jetty generations";
    private static final String SERVLET =
            "Jetty 12 separates core ProxyHandler from EE8/EE9/EE10/EE11 ProxyServlet artifacts; choose one Servlet namespace deliberately and align its Servlet API and deployment modules";
    private static final String VARIANT =
            "This jetty-proxy classifier, custom type, extension, variant or four-part coordinate is not the standard runtime artifact; select and migrate the intended Jetty 12 artifact explicitly";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE));
    }

    @Test
    void marksMavenJavaBaselineMixedJettyAndServletEnvironmentChoices() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risks</artifactId><version>1</version>
                  <properties><java.version>1.8</java.version><jetty.version>9.4.45.v20220203</jetty.version></properties>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>11</release></configuration></plugin></plugins></build>
                  <dependencies>
                    <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency>
                    <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>${jetty.version}</version></dependency>
                    <dependency><groupId>org.eclipse.jetty.http2</groupId><artifactId>http2-server</artifactId><version>9.4.45.v20220203</version></dependency>
                    <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                    <dependency><groupId>org.eclipse.jetty.ee10</groupId><artifactId>jetty-ee10-proxy</artifactId><version>12.1.8</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risks</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><java.version>1.8</java.version><jetty.version>9.4.45.v20220203</jetty.version></properties>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><!--~~(%s)~~>--><release>11</release></configuration></plugin></plugins></build>
                  <dependencies>
                    <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>${jetty.version}</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>org.eclipse.jetty.http2</groupId><artifactId>http2-server</artifactId><version>9.4.45.v20220203</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>org.eclipse.jetty.ee10</groupId><artifactId>jetty-ee10-proxy</artifactId><version>12.1.8</version></dependency>
                  </dependencies>
                </project>
                """.formatted(JAVA, JAVA, ALIGNMENT, ALIGNMENT, SERVLET, SERVLET), source -> source.path("pom.xml")));
    }

    @Test
    void marksVersionlessUnresolvedAndDynamicTargetOwners() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${missing.version}</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>[9.4,13)</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version><dependencies>
                  <!--~~(%s)~~>--><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId></dependency>
                  <!--~~(%s)~~>--><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${missing.version}</version></dependency>
                  <!--~~(%s)~~>--><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>[9.4,13)</version></dependency>
                </dependencies></project>
                """.formatted(MANAGED, MANAGED, MANAGED), source -> source.path("pom.xml")));
    }

    @Test
    void resolvedRootPropertyIsNotReportedAsExternalOwner() {
        rewriteRun(xml("""
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                 <properties><jetty.proxy.version>12.1.8</jetty.proxy.version></properties><dependencies><dependency>
                   <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.proxy.version}</version>
                 </dependency></dependencies>
               </project>
               """, source -> source.path("pom.xml")));
    }

    @Test
    void doesNothingWithoutStandardTargetDependency() {
        rewriteRun(xml("""
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unrelated</artifactId><version>1</version>
                 <properties><java.version>8</java.version></properties><dependencies>
                   <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>9.4.45.v20220203</version></dependency>
                   <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                 </dependencies>
               </project>
               """, source -> source.path("pom.xml")));
    }

    @Test
    void classifierOrCustomTypeTargetIsMarkedInsteadOfSilentlySkipped() {
        rewriteRun(xml("""
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variant</artifactId><version>1</version>
                 <properties><java.version>8</java.version></properties><dependencies>
                   <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version><classifier>sources</classifier></dependency>
                   <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version><type>test-jar</type></dependency>
                   <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>9.4.45.v20220203</version></dependency>
                 </dependencies>
               </project>
               """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(VARIANT));
            assertTrue(printed.contains(JAVA));
            assertTrue(printed.contains(ALIGNMENT));
        })));
    }

    @Test
    void explicitJarTypeStillGatesStandardBuildChecks() {
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jar</artifactId><version>1</version>
                  <properties><java.version>11</java.version></properties><dependencies><dependency>
                    <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version><type>jar</type>
                  </dependency></dependencies>
                </project>
                """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(JAVA)))));
    }

    @Test
    void marksGroovyJavaLevelsMixedJettyServletAndManagedTarget() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = '11'
                java { toolchain { languageVersion = JavaLanguageVersion.of(8) } }
                dependencies {
                  implementation 'org.eclipse.jetty:jetty-proxy:12.1.8'
                  implementation 'org.eclipse.jetty:jetty-server:9.4.45.v20220203'
                  implementation 'org.eclipse.jetty.http2:http2-server:9.4.45.v20220203'
                  compileOnly 'javax.servlet:javax.servlet-api:4.0.1'
                  runtimeOnly group: 'org.eclipse.jetty.ee11', name: 'jetty-ee11-proxy', version: '12.1.8'
                  testImplementation 'org.eclipse.jetty:jetty-proxy:12.+'
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(JAVA));
                    assertTrue(printed.contains(ALIGNMENT));
                    assertTrue(printed.contains(SERVLET));
                    assertTrue(printed.contains(MANAGED));
                })));
    }

    @Test
    void marksKotlinJavaBaselineAndServletChoice() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                java {
                    sourceCompatibility = JavaVersion.VERSION_11
                    toolchain { languageVersion = JavaLanguageVersion.of(8) }
                }
                dependencies {
                    implementation("org.eclipse.jetty:jetty-proxy:12.1.8")
                    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(JAVA));
                    assertTrue(printed.contains(SERVLET));
                })));
    }

    @Test
    void marksGradleVariantTargetsPrecisely() {
        rewriteRun(buildGradle(
                """
                sourceCompatibility = JavaVersion.VERSION_11
                dependencies {
                  implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203:sources'
                  implementation group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '9.4.45.v20220203', ext: 'zip'
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(VARIANT));
                    assertTrue(printed.contains(JAVA));
                })));
    }

    @Test
    void targetInsideProfileDependencyManagementStillGatesOwningPom() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <properties><maven.compiler.release>11</maven.compiler.release></properties>
                  <profiles><profile><id>jetty</id><dependencyManagement><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><maven.compiler.release>11</maven.compiler.release></properties>
                  <profiles><profile><id>jetty</id><dependencyManagement><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """.formatted(JAVA), source -> source.path("pom.xml")));
    }

    @Test
    void ignoresNestedBuildscriptAndSimilarGradleCoordinates() {
        rewriteRun(buildGradle("""
                sourceCompatibility = JavaVersion.VERSION_1_8
                buildscript { dependencies { implementation 'org.eclipse.jetty:jetty-proxy:12.1.8' } }
                dependencies { generated { implementation 'org.eclipse.jetty:jetty-proxy:12.1.8' } }
                subprojects { dependencies { implementation 'org.eclipse.jetty:jetty-proxy:12.1.8' } }
                dependencies { runtimeOnly 'example:jetty-proxy:12.1.8' }
                """));
    }

    @Test
    void ignoresCompilerPluginLookalikesOutsideOwnedBuild() {
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>lookalike</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies>
                  <configuration><project><build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>8</release></configuration></plugin></plugins></build></project></configuration>
                  <reporting><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>8</release></configuration></plugin></plugins></reporting>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void marksOnlyExactServletCoordinates() {
        rewriteRun(buildGradle(
                """
                dependencies {
                  implementation 'org.eclipse.jetty:jetty-proxy:12.1.8'
                  compileOnly 'javax.servlet:javax.servlet-api:4.0.1'
                  compileOnly 'example:javax.servlet-api:4.0.1'
                  runtimeOnly 'org.eclipse.jetty.ee10:jetty-ee10-proxy-extra:12.1.8'
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(SERVLET));
                    assertFalse(printed.contains("*/'example:javax.servlet-api"));
                    assertFalse(printed.contains("*/'org.eclipse.jetty.ee10:jetty-ee10-proxy-extra"));
                })));
    }

    @Test
    void skipsGeneratedAndInstalledBuildFiles() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>generated</artifactId><version>1</version>
                      <properties><java.version>8</java.version></properties><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("target/generated/pom.xml")),
                buildGradle("sourceCompatibility = '8'\ndependencies { implementation 'org.eclipse.jetty:jetty-proxy:12.1.8' }",
                        source -> source.path("installed/build.gradle")),
                buildGradle("sourceCompatibility = '8'\ndependencies { implementation 'org.eclipse.jetty:jetty-proxy:12.1.8' }",
                        source -> source.path(".mvn/generated/build.gradle")));
    }

    @Test
    void buildMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                  <properties><java.version>11</java.version></properties><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><java.version>11</java.version></properties><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies>
                </project>
                """.formatted(JAVA), source -> source.path("pom.xml")));
    }
}
