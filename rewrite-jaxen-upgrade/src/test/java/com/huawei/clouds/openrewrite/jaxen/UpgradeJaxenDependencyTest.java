package com.huawei.clouds.openrewrite.jaxen;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeJaxenDependencyTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.jaxen.UpgradeJaxenTo2_0_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE));
    }

    @Test
    void recipeIsDiscoverableAndStrict() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertEquals("Upgrade Jaxen to 2.0.1", recipe.getDisplayName());
        assertTrue(recipe.getRecipeList().stream().anyMatch(UpgradeSelectedJaxenDependency.class::isInstance));
    }

    @Test
    void upgradesBothWorkbookMavenVersions() {
        rewriteRun(
                pomXml(pom("1.2.0"), pom("2.0.1")),
                pomXml(pom("2.0.0"), pom("2.0.1"), source -> source.path("v200/pom.xml"))
        );
    }

    @Test
    void upgradesRootPropertyAcrossProjectAndProfileScopes() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-property</artifactId><version>1</version>
                  <properties><jaxen.version>1.2.0</jaxen.version></properties>
                  <profiles><profile><id>xpath</id><dependencies><dependency>
                    <groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${jaxen.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-property</artifactId><version>1</version>
                  <properties><jaxen.version>2.0.1</jaxen.version></properties>
                  <profiles><profile><id>xpath</id><dependencies><dependency>
                    <groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${jaxen.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void upgradesProfilePropertyOnlyWithinOwningProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id><properties><jaxen.version>2.0.0</jaxen.version></properties>
                    <dependencyManagement><dependencies><dependency>
                      <groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${jaxen.version}</version>
                    </dependency></dependencies></dependencyManagement>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id><properties><jaxen.version>2.0.1</jaxen.version></properties>
                    <dependencyManagement><dependencies><dependency>
                      <groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${jaxen.version}</version>
                    </dependency></dependencies></dependencyManagement>
                  </profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void leavesCrossProfileDuplicateAndSharedPropertiesUntouched() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cross</artifactId><version>1</version>
                      <profiles>
                        <profile><id>definition</id><properties><jaxen.version>1.2.0</jaxen.version></properties></profile>
                        <profile><id>consumer</id><dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${jaxen.version}</version></dependency></dependencies></profile>
                      </profiles>
                    </project>
                    """),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate</artifactId><version>1</version>
                      <properties><jaxen.version>1.2.0</jaxen.version></properties>
                      <profiles><profile><id>x</id><properties><jaxen.version>2.0.0</jaxen.version></properties></profile></profiles>
                      <dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${jaxen.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("duplicate/pom.xml")),
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                      <properties><shared.version>1.2.0</shared.version></properties><name>compat-${shared.version}</name>
                      <dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>${shared.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("shared/pom.xml"))
        );
    }

    @Test
    void upgradesRealPublicGradleFixtures() {
        rewriteRun(
                // spring-projects/spring-framework@224522244f7698673584910ee805415673227a7e
                buildGradle(
                        "dependencies { api(\"jaxen:jaxen:1.2.0\") }",
                        "dependencies { api(\"jaxen:jaxen:2.0.1\") }"),
                // spring-projects/spring-ws@2692136fca6d3f89aea8478ae5dd106bde5e785e
                buildGradle(
                        "dependencies { api(\"jaxen:jaxen:2.0.0\") }",
                        "dependencies { api(\"jaxen:jaxen:2.0.1\") }",
                        source -> source.path("spring-ws-platform.gradle")),
                // AndBible/and-bible@0b03d63d07d5f1626f2cd7fdae05e026e0eecda8
                buildGradleKts(
                        "dependencies { implementation(\"jaxen:jaxen:2.0.0\") }",
                        "dependencies { implementation(\"jaxen:jaxen:2.0.1\") }",
                        source -> source.path("app.gradle.kts"))
        );
    }

    @Test
    void upgradesGroovyMapNotation() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly group: 'jaxen', name: 'jaxen', version: '1.2.0' }",
                        "dependencies { runtimeOnly group: 'jaxen', name: 'jaxen', version: '2.0.1' }"),
                buildGradle(
                        "dependencies { implementation([group: 'jaxen', name: 'jaxen', version: '2.0.0']) }",
                        "dependencies { implementation([group: 'jaxen', name: 'jaxen', version: '2.0.1']) }",
                        source -> source.path("map.gradle"))
        );
    }

    @Test
    void upgradesUniquelyOwnedRootGroovyProperties() {
        rewriteRun(
                buildGradle(
                        """
                        ext.jaxenVersion = '1.2.0'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """,
                        """
                        ext.jaxenVersion = '2.0.1'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """),
                buildGradle(
                        """
                        ext {
                            jaxenVersion = "2.0.0"
                        }
                        dependencies { runtimeOnly "jaxen:jaxen:${jaxenVersion}" }
                        """,
                        """
                        ext {
                            jaxenVersion = "2.0.1"
                        }
                        dependencies { runtimeOnly "jaxen:jaxen:${jaxenVersion}" }
                        """,
                        source -> source.path("build.gradle"))
        );
    }

    @Test
    void upgradesUniquelyOwnedRootKotlinProperty() {
        rewriteRun(buildGradleKts(
                """
                val jaxenVersion = "2.0.0"
                dependencies { implementation("jaxen:jaxen:$jaxenVersion") }
                """,
                """
                val jaxenVersion = "2.0.1"
                dependencies { implementation("jaxen:jaxen:$jaxenVersion") }
                """
        ));
    }

    @Test
    void leavesSharedAndNonRootGradlePropertiesUntouched() {
        rewriteRun(
                buildGradle("""
                        ext.jaxenVersion = '1.2.0'
                        println "testing $jaxenVersion"
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """),
                buildGradle("""
                        ext.jaxenVersion = '2.0.0'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """, source -> source.path("module/build.gradle")),
                buildGradleKts("""
                        val jaxenVersion = "1.2.0"
                        println(jaxenVersion)
                        dependencies { implementation("jaxen:jaxen:$jaxenVersion") }
                        """, source -> source.path("shared.gradle.kts"))
        );
    }

    @Test
    void leavesNestedSelectedAndQualifiedGradleDeclarationsUntouched() {
        rewriteRun(
                buildGradle("""
                        ext.jaxenVersion = '1.2.0'
                        subprojects {
                            dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        }
                        dependencies {
                            constraints { implementation 'jaxen:jaxen:1.2.0' }
                            project.dependencies.implementation 'jaxen:jaxen:2.0.0'
                        }
                        """),
                buildGradleKts("""
                        subprojects {
                            dependencies { implementation("jaxen:jaxen:2.0.0") }
                        }
                        dependencies {
                            constraints { implementation("jaxen:jaxen:1.2.0") }
                        }
                        """)
        );
    }

    @Test
    void leavesVersionsOutsideWorkbookDynamicAndVersionlessUntouched() {
        rewriteRun(
                pomXml(pom("1.1.6")),
                pomXml(pom("2.0.1"), source -> source.path("target-version/pom.xml")),
                pomXml(pom("2.0.2"), source -> source.path("newer/pom.xml")),
                xml(pom("[1.2,2.1)"), source -> source.path("range/pom.xml")),
                xml("<project><dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId></dependency></dependencies></project>",
                        source -> source.path("versionless/pom.xml")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:2.+' }")
        );
    }

    @Test
    void leavesVariantsPluginsLookalikesAndOutsideDependenciesUntouched() {
        rewriteRun(
                xml("""
                    <project><build><plugins><plugin><artifactId>tool</artifactId><dependencies><dependency>
                      <groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>1.2.0</version>
                    </dependency></dependencies></plugin></plugins></build></project>
                    """, source -> source.path("plugin/pom.xml")),
                xml("""
                    <project><dependencies>
                      <dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>1.2.0</version><classifier>sources</classifier></dependency>
                      <dependency><groupId>other</groupId><artifactId>jaxen</artifactId><version>2.0.0</version></dependency>
                    </dependencies></project>
                    """, source -> source.path("variants/pom.xml")),
                buildGradle("""
                        def note = 'jaxen:jaxen:1.2.0'
                        dependencies {
                            implementation 'jaxen:jaxen:2.0.0:tests'
                            runtimeOnly 'jaxen:jaxen:1.2.0@zip'
                            implementation group: 'jaxen', name: 'jaxen', version: '2.0.0', classifier: 'sources'
                        }
                        """)
        );
    }

    @Test
    void parentDirectoryFilterKeepsLeafNamesEligible() {
        rewriteRun(
                buildGradle("dependencies { implementation 'jaxen:jaxen:1.2.0' }",
                        "dependencies { implementation 'jaxen:jaxen:2.0.1' }",
                        source -> source.path("install.gradle")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:2.0.0' }",
                        "dependencies { implementation 'jaxen:jaxen:2.0.1' }",
                        source -> source.path("target.gradle")),
                pomXml(pom("1.2.0"), pom("2.0.1"), source -> source.path("module-target/pom.xml")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:1.2.0' }",
                        source -> source.path("install/build.gradle")),
                pomXml(pom("2.0.0"), source -> source.path("target/pom.xml")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:1.2.0' }",
                        source -> source.path("GeneratedSources/build.gradle")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:2.0.0' }",
                        source -> source.path("installation/build.gradle")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:1.2.0' }",
                        source -> source.path(".M2/build.gradle")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:2.0.0' }",
                        source -> source.path("REPORTS/build.gradle"))
        );
    }

    @Test
    void leavesVersionCatalogUntouched() {
        rewriteRun(text("""
                [versions]
                jaxen = "1.2.0"
                [libraries]
                jaxen = { module = "jaxen:jaxen", version.ref = "jaxen" }
                """, source -> source.path("gradle/libs.versions.toml")));
    }

    @Test
    void upgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.2.0"), pom("2.0.1")),
                buildGradle("dependencies { implementation 'jaxen:jaxen:2.0.0' }",
                        "dependencies { implementation 'jaxen:jaxen:2.0.1' }")
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static String pom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                  <groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>%s</version>
                </dependency></dependencies></project>
                """.formatted(version);
    }
}
