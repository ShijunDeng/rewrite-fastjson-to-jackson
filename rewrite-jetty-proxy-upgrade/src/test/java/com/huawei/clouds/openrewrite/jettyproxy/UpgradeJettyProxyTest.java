package com.huawei.clouds.openrewrite.jettyproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeJettyProxyTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.jettyproxy.UpgradeJettyProxyTo12_1_8";
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.jettyproxy.MigrateJettyProxyTo12_1_8";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {"9.4.39.v20210325", "9.4.45.v20220203"})
    void upgradesEveryVisibleSpreadsheetVersionInMaven(String oldVersion) {
        rewriteRun(pomXml(pom(oldVersion), pom("12.1.8")));
    }

    @ParameterizedTest(name = "Gradle upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {"9.4.39.v20210325", "9.4.45.v20220203"})
    void upgradesEveryVisibleSpreadsheetVersionInGradle(String oldVersion) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.eclipse.jetty:jetty-proxy:%s' }".formatted(oldVersion),
                "dependencies { implementation 'org.eclipse.jetty:jetty-proxy:12.1.8' }"));
    }

    @Test
    void upgradesLiteralAndPreservesOrdinaryDependencyMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>proxy</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>proxy</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void upgradesExplicitStandardJarType() {
        rewriteRun(pomXml(
                pom("9.4.39.v20210325").replace("</version>\n               </dependency>",
                        "</version><type>jar</type>\n               </dependency>"),
                pom("12.1.8").replace("</version>\n               </dependency>",
                        "</version><type>jar</type>\n               </dependency>")));
    }

    @Test
    void upgradesDependencyManagementLiteralAndLeavesVersionlessUse() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.39.v20210325</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesOfficialJettyVersionlessTestDependency() {
        // Reduced from Jetty's own HTTP/2 client POM at fixed commit 4a0c91c0be53805e3fcffdcdcc9587d5301863db.
        // https://github.com/jetty/jetty.project/blob/4a0c91c0be53805e3fcffdcdcc9587d5301863db/jetty-http2/http2-client/pom.xml
        rewriteRun(xml("""
               <project><modelVersion>4.0.0</modelVersion><groupId>org.eclipse.jetty.http2</groupId><artifactId>http2-client-test</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><scope>test</scope>
               </dependency></dependencies></project>
               """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesProfileDependency() {
        rewriteRun(pomXml(
                profilePom("9.4.45.v20220203"),
                profilePom("12.1.8")));
    }

    @Test
    void upgradesExclusivelyOwnedRootPropertyAcrossMainAndProfile() {
        rewriteRun(pomXml(
                propertyPom("9.4.39.v20210325"),
                propertyPom("12.1.8")));
    }

    @Test
    void preservesSharedJettyPropertyUsedByAnotherModule() {
        // Mirrors Apache Geaflow's fixed POM shape, where one property owns server, servlet and proxy modules.
        // https://github.com/apache/geaflow/blob/f8be72222ee1ddbbf66a87fc351cd727a4d13b03/geaflow/pom.xml
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><jetty.version>9.4.45.v20220203</jetty.version></properties><dependencies>
                    <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.version}</version></dependency>
                    <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-server</artifactId><version>${jetty.version}</version></dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void preservesPropertyReferencedByAttributeDeclaredTwiceOrShadowed() {
        rewriteRun(
                xml("""
                    <project audit="${jetty.version}"><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>attribute</artifactId><version>1</version>
                      <properties><jetty.version>9.4.45.v20220203</jetty.version></properties><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("pom.xml")),
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate</artifactId><version>1</version>
                      <properties><jetty.version>9.4.45.v20220203</jetty.version><jetty.version>9.4.45.v20220203</jetty.version></properties><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("duplicate/pom.xml")),
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shadow</artifactId><version>1</version>
                      <properties><jetty.version>9.4.45.v20220203</jetty.version></properties><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.version}</version></dependency></dependencies>
                      <profiles><profile><id>old</id><properties><jetty.version>9.4.39.v20210325</jetty.version></properties></profile></profiles>
                    </project>
                    """, source -> source.path("shadow/pom.xml"))
        );
    }

    @Test
    void upgradesOnlyProjectDependencyNotPluginOrConfigurationLookalikes() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owner</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version></dependency></dependencies><configuration><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version></dependency></dependencies></configuration></plugin></plugins></build>
                  <dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owner</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version></dependency></dependencies><configuration><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version></dependency></dependencies></configuration></plugin></plugins></build>
                  <dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void preservesClassifierTypeUnlistedManagedDynamicTargetAndFuture() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safety</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version><classifier>sources</classifier></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version><type>test-jar</type></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>9.4.44.v20210927</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>[9.4,13)</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${missing.version}</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>12.1.8</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>13.0.0</version></dependency>
                </dependencies></project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void preservesSimilarCoordinates() {
        rewriteRun(xml("""
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version><dependencies>
                 <dependency><groupId>org.eclipse.jetty.ee10</groupId><artifactId>jetty-ee10-proxy</artifactId><version>9.4.45.v20220203</version></dependency>
                 <dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy-extra</artifactId><version>9.4.45.v20220203</version></dependency>
                 <dependency><groupId>example</groupId><artifactId>jetty-proxy</artifactId><version>9.4.45.v20220203</version></dependency>
               </dependencies></project>
               """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesGroovyStringAndMapShapesAndKotlinString() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly 'org.eclipse.jetty:jetty-proxy:9.4.39.v20210325' }",
                        "dependencies { runtimeOnly 'org.eclipse.jetty:jetty-proxy:12.1.8' }",
                        source -> source.path("string.gradle")),
                buildGradle(
                        "dependencies { implementation group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '9.4.45.v20220203' }",
                        "dependencies { implementation group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '12.1.8' }",
                        source -> source.path("map.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '9.4.39.v20210325']) }",
                        "dependencies { testImplementation([group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '12.1.8']) }",
                        source -> source.path("literal.gradle")),
                buildGradleKts(
                        "dependencies { implementation(\"org.eclipse.jetty:jetty-proxy:9.4.45.v20220203\") }",
                        "dependencies { implementation(\"org.eclipse.jetty:jetty-proxy:12.1.8\") }"));
    }

    @Test
    void preservesGradleInterpolationCatalogFourPartAndVariantMaps() {
        rewriteRun(
                buildGradle("""
                        def jettyVersion = '9.4.45.v20220203'
                        dependencies {
                          implementation "org.eclipse.jetty:jetty-proxy:${jettyVersion}"
                          implementation libs.jetty.proxy
                          implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203:sources'
                          implementation group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '9.4.45.v20220203', classifier: 'sources'
                          implementation([group: 'org.eclipse.jetty', name: 'jetty-proxy', version: '9.4.45.v20220203', ext: 'zip'])
                        }
                        """),
                buildGradleKts("""
                        val jettyVersion = "9.4.45.v20220203"
                        dependencies {
                            implementation("org.eclipse.jetty:jetty-proxy:$jettyVersion")
                            implementation(libs.jetty.proxy)
                        }
                        """));
    }

    @Test
    void ignoresNestedDependenciesAndBuildscript() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' } }
                dependencies { generated { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' } }
                publishing { dependencies { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' } }
                subprojects { dependencies { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' } }
                """));
    }

    @Test
    void skipsGeneratedAndInstalledBuildFiles() {
        rewriteRun(
                xml(pom("9.4.45.v20220203"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' }",
                        source -> source.path("install/build.gradle")),
                buildGradle("dependencies { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' }",
                        source -> source.path(".mvn/generated-client/build.gradle")),
                buildGradle("dependencies { implementation 'org.eclipse.jetty:jetty-proxy:9.4.45.v20220203' }",
                        source -> source.path(".yarn/cache/build.gradle")));
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("9.4.45.v20220203"), pom("12.1.8")));
    }

    @Test
    void discoversAllPublicRecipesAndRecommendedComposition() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.jettyproxy.MigrateDeterministicJetty12Types",
                "com.huawei.clouds.openrewrite.jettyproxy.FindJettyProxy12BuildMigrationRisks",
                "com.huawei.clouds.openrewrite.jettyproxy.FindJettyProxy12SourceAndConfigRisks",
                MIGRATE
        };
        for (String name : names) {
            Recipe activated = environment.activateRecipes(name);
            assertEquals(name, activated.getName());
            assertTrue(activated.validate().isValid(), () -> activated.validate().failures().toString());
        }
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(migrate.getRecipeList().size() >= 4);
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>proxy</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static String profilePom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>proxy</id><dependencies><dependency>
                 <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>%s</version>
               </dependency></dependencies></profile></profiles></project>
               """.formatted(version);
    }

    private static String propertyPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                 <properties><jetty.proxy.version>%s</jetty.proxy.version></properties>
                 <dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.proxy.version}</version></dependency></dependencies>
                 <profiles><profile><id>test</id><dependencies><dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>${jetty.proxy.version}</version></dependency></dependencies></profile></profiles>
               </project>
               """.formatted(version);
    }
}
