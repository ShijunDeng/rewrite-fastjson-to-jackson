package com.huawei.clouds.openrewrite.jaxbimpl;

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

class UpgradeJaxbImplDependencyTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.jaxbimpl.UpgradeJaxbImplTo4_0_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE));
    }

    @Test
    void recipeIsDiscoverableAndContainsStrictRecipe() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertEquals("Upgrade JAXB implementation to 4.0.6", recipe.getDisplayName());
        assertTrue(recipe.getRecipeList().stream().anyMatch(UpgradeSelectedJaxbImplDependency.class::isInstance));
    }

    @Test
    void upgradesAllWorkbookMavenVersions() {
        rewriteRun(
                pomXml(pom("2.3.8"), pom("4.0.6")),
                pomXml(pom("4.0.2"), pom("4.0.6"), source -> source.path("v402/pom.xml")),
                pomXml(pom("4.0.3"), pom("4.0.6"), source -> source.path("v403/pom.xml"))
        );
    }

    @Test
    void upgradesOwnedPropertyAndManagedDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <properties><jaxb.impl.version>2.3.8</jaxb.impl.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${jaxb.impl.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <properties><jaxb.impl.version>4.0.6</jaxb.impl.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${jaxb.impl.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesProfileOwnedPropertyAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>ri</id><properties><ri.version>4.0.2</ri.version></properties><dependencies><dependency>
                    <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version><scope>runtime</scope><optional>true</optional>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>ri</id><properties><ri.version>4.0.6</ri.version></properties><dependencies><dependency>
                    <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version><scope>runtime</scope><optional>true</optional>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void resolvesRootPropertiesInsideProfiles() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-visible</artifactId><version>1</version>
                  <properties><ri.version> 2.3.8 </ri.version></properties>
                  <profiles><profile><id>runtime</id><dependencies><dependency>
                    <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version> ${ri.version} </version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-visible</artifactId><version>1</version>
                  <properties><ri.version> 4.0.6 </ri.version></properties>
                  <profiles><profile><id>runtime</id><dependencies><dependency>
                    <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version> ${ri.version} </version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void profileOverrideWinsAndDoesNotLeakOutsideProfile() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                          <properties><ri.version>9.9.9</ri.version></properties>
                          <profiles><profile><id>runtime</id><properties><ri.version>4.0.2</ri.version></properties><dependencies><dependency>
                            <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                          <properties><ri.version>9.9.9</ri.version></properties>
                          <profiles><profile><id>runtime</id><properties><ri.version>4.0.6</ri.version></properties><dependencies><dependency>
                            <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>no-leak</artifactId><version>1</version>
                          <dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies>
                          <profiles><profile><id>runtime</id><properties><ri.version>2.3.8</ri.version></properties></profile></profiles>
                        </project>
                        """, source -> source.path("no-leak/pom.xml"))
        );
    }

    @Test
    void sameNamedPropertiesInDifferentProfilesHaveIndependentOwnership() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version><profiles>
                  <profile><id>one</id><properties><ri.version>2.3.8</ri.version></properties><dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies></profile>
                  <profile><id>two</id><properties><ri.version>4.0.3</ri.version></properties><dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies></profile>
                </profiles></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version><profiles>
                  <profile><id>one</id><properties><ri.version>4.0.6</ri.version></properties><dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies></profile>
                  <profile><id>two</id><properties><ri.version>4.0.6</ri.version></properties><dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies></profile>
                </profiles></project>
                """
        ));
    }

    @Test
    void upgradesRealGradleFixturesForEverySourceVersion() {
        rewriteRun(
                // dbwlgns777/HMIToMESBridgeServer@ac52e08f316060cd919aaacaf232570b9b009413, build.gradle:32
                buildGradle(
                        "dependencies { runtimeOnly 'com.sun.xml.bind:jaxb-impl:2.3.8' }",
                        "dependencies { runtimeOnly 'com.sun.xml.bind:jaxb-impl:4.0.6' }"),
                // PBX-Manager/pbx-manager@1dbbfe4a9db76100a59ac7c7726099105582ebc0, build.gradle.kts:37
                buildGradleKts(
                        "dependencies { implementation(\"com.sun.xml.bind:jaxb-impl:4.0.2\") }",
                        "dependencies { implementation(\"com.sun.xml.bind:jaxb-impl:4.0.6\") }"),
                // pegasystems/pega-logviewer@bb7692ae9d06a910ceff09061580e457a56a076a, build.gradle
                buildGradle(
                        "dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.3' }",
                        "dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.6' }",
                        source -> source.path("viewer.gradle"))
        );
    }

    @Test
    void upgradesGroovyMapForms() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly group: 'com.sun.xml.bind', name: 'jaxb-impl', version: '2.3.8' }",
                        "dependencies { runtimeOnly group: 'com.sun.xml.bind', name: 'jaxb-impl', version: '4.0.6' }"),
                buildGradle(
                        "dependencies { implementation([group: 'com.sun.xml.bind', name: 'jaxb-impl', version: '4.0.2']) }",
                        "dependencies { implementation([group: 'com.sun.xml.bind', name: 'jaxb-impl', version: '4.0.6']) }",
                        source -> source.path("map.gradle"))
        );
    }

    @Test
    void onlyChangesTopLevelRootGradleDependencyDsl() {
        rewriteRun(
                buildGradle("""
                        buildscript { dependencies { implementation 'com.sun.xml.bind:jaxb-impl:2.3.8' } }
                        subprojects { dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.2' } }
                        helper.dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.3' }
                        dependencies { helper.implementation 'com.sun.xml.bind:jaxb-impl:2.3.8' }
                        """),
                buildGradleKts("""
                        buildscript { dependencies { implementation("com.sun.xml.bind:jaxb-impl:2.3.8") } }
                        subprojects { dependencies { implementation("com.sun.xml.bind:jaxb-impl:4.0.2") } }
                        project.dependencies { implementation("com.sun.xml.bind:jaxb-impl:4.0.3") }
                        """, source -> source.path("nested.gradle.kts"))
        );
    }

    @Test
    void leavesVersionsOutsideExactWorkbookSetUntouched() {
        rewriteRun(
                pomXml(pom("2.3.7")),
                pomXml(pom("4.0.1"), source -> source.path("v401/pom.xml")),
                pomXml(pom("4.0.6"), source -> source.path("target-version/pom.xml")),
                pomXml(pom("4.0.7"), source -> source.path("newer/pom.xml")),
                xml(pom("[2.3,4.1)"), source -> source.path("range/pom.xml")),
                xml(pom("LATEST"), source -> source.path("dynamic/pom.xml"))
        );
    }

    @Test
    void leavesSharedOrAmbiguousPropertiesUntouched() {
        rewriteRun(
                pomXml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                      <properties><shared.version>2.3.8</shared.version></properties><name>compat-${shared.version}</name>
                      <dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${shared.version}</version></dependency></dependencies>
                    </project>
                    """),
                pomXml(
                    """
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scoped</artifactId><version>1</version>
                      <properties><ri.version>2.3.8</ri.version></properties>
                      <profiles><profile><id>x</id><properties><ri.version>4.0.2</ri.version></properties></profile></profiles>
                      <dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies>
                    </project>
                    """,
                    """
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scoped</artifactId><version>1</version>
                      <properties><ri.version>4.0.6</ri.version></properties>
                      <profiles><profile><id>x</id><properties><ri.version>4.0.2</ri.version></properties></profile></profiles>
                      <dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("scoped/pom.xml"))
        );
    }

    @Test
    void leavesVariantsVersionlessAndInterpolatedCoordinatesUntouched() {
        rewriteRun(
                xml("""
                    <project><dependencies>
                      <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId></dependency>
                      <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>2.3.8</version><classifier>sources</classifier></dependency>
                      <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>4.0.2</version><type>pom</type></dependency>
                    </dependencies></project>
                    """, source -> source.path("variants/pom.xml")),
                buildGradle("""
                    def v = '2.3.8'
                    dependencies {
                        implementation "com.sun.xml.bind:jaxb-impl:${v}"
                        runtimeOnly 'com.sun.xml.bind:jaxb-impl:2.3.8:tests'
                        runtimeOnly 'com.sun.xml.bind:jaxb-impl:4.0.2@zip'
                        implementation group: 'com.sun.xml.bind', name: 'jaxb-impl', version: '4.0.3', classifier: 'sources'
                    }
                    """)
        );
    }

    @Test
    void leavesPluginDependenciesAndLookalikesUntouched() {
        rewriteRun(
                xml("""
                    <project><build><plugins><plugin><groupId>example</groupId><artifactId>tool</artifactId><dependencies><dependency>
                      <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>2.3.8</version>
                    </dependency></dependencies></plugin></plugins></build></project>
                    """, source -> source.path("plugin/pom.xml")),
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>lookalike</artifactId><version>1</version><dependencies>
                      <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-impl</artifactId><version>2.3.8</version></dependency>
                      <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-runtime</artifactId><version>4.0.2</version></dependency>
                    </dependencies></project>
                    """, source -> source.path("lookalike/pom.xml")),
                buildGradle("def coordinate = 'com.sun.xml.bind:jaxb-impl:2.3.8'\nprintln coordinate\n")
        );
    }

    @Test
    void parentDirectoryFilterDoesNotExcludeLeafNames() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:2.3.8' }",
                        "dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.6' }",
                        source -> source.path("install.gradle")),
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.2' }",
                        "dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.6' }",
                        source -> source.path("generated.gradle")),
                pomXml(pom("4.0.3"), pom("4.0.6"), source -> source.path("module-target/pom.xml")),
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:2.3.8' }",
                        source -> source.path("install/build.gradle")),
                pomXml(pom("4.0.2"), source -> source.path("target/pom.xml"))
        );
    }

    @Test
    void ignoresGeneratedPrefixesCachesAndCaseVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:2.3.8' }",
                        source -> source.path("generated-client/build.gradle")),
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.2' }",
                        source -> source.path("INSTALLER/build.gradle")),
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.3' }",
                        source -> source.path(".pnpm/pkg/build.gradle")),
                pomXml(pom("2.3.8"), source -> source.path("coverage/pom.xml"))
        );
    }

    @Test
    void ignoresVersionCatalogConfiguration() {
        rewriteRun(text("""
                [versions]
                jaxb = "2.3.8"
                [libraries]
                jaxb-impl = { module = "com.sun.xml.bind:jaxb-impl", version.ref = "jaxb" }
                """, source -> source.path("gradle/libs.versions.toml")));
    }

    @Test
    void upgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("4.0.3"), pom("4.0.6")),
                buildGradle("dependencies { implementation 'com.sun.xml.bind:jaxb-impl:2.3.8' }",
                        "dependencies { implementation 'com.sun.xml.bind:jaxb-impl:4.0.6' }")
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static String pom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>%s</version>
                </dependency></dependencies></project>
                """.formatted(version);
    }
}
