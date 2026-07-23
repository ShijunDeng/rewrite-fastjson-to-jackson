package com.huawei.clouds.openrewrite.logbackcore;

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

class UpgradeLogbackCoreDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.logbackcore.UpgradeLogbackCoreTo1_5_34";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.logbackcore.MigrateLogbackCoreTo1_5_34";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades selected source {0}")
    @ValueSource(strings = {"1.2.5", "1.2.9"})
    void upgradesEveryVisibleWorkbookVersion(String version) {
        rewriteRun(xml(pom(version), pom("1.5.34"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDirectDependencyAndPreservesMetadata() {
        rewriteRun(pomXml(
                dependency("1.2.9", "<scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>"),
                dependency("1.5.34", "<scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>")));
    }

    @Test
    void upgradesDependencyManagementAndExplicitJar() {
        rewriteRun(pomXml(
                project("<dependencyManagement><dependencies>" + target("1.2.5", "<type>jar</type>") +
                        "</dependencies></dependencyManagement><dependencies>" + target(null, "") + "</dependencies>"),
                project("<dependencyManagement><dependencies>" + target("1.5.34", "<type>jar</type>") +
                        "</dependencies></dependencyManagement><dependencies>" + target(null, "") + "</dependencies>")));
    }

    @Test
    void upgradesRootAndProfileLiteralOwners() {
        rewriteRun(pomXml(
                project("<dependencies>" + target("1.2.9", "") + "</dependencies><profiles><profile><id>p</id><dependencies>" + target("1.2.5", "") + "</dependencies></profile></profiles>"),
                project("<dependencies>" + target("1.5.34", "") + "</dependencies><profiles><profile><id>p</id><dependencies>" + target("1.5.34", "") + "</dependencies></profile></profiles>")));
    }

    @Test
    void upgradesExclusiveRootPropertyAcrossRootAndProfile() {
        rewriteRun(pomXml(
                propertyProject("1.2.9", "logback.version"),
                propertyProject("1.5.34", "logback.version")));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutChangingRoot() {
        rewriteRun(pomXml(
                project("<properties><bc.version>1.3.0</bc.version></properties><profiles><profile><id>p</id><properties><provider.version>1.2.5</provider.version></properties><dependencies>" + target("${provider.version}", "") + "</dependencies></profile></profiles>"),
                project("<properties><bc.version>1.3.0</bc.version></properties><profiles><profile><id>p</id><properties><provider.version>1.5.34</provider.version></properties><dependencies>" + target("${provider.version}", "") + "</dependencies></profile></profiles>")));
    }

    @Test
    void preservesSharedAttributeDuplicateAndShadowedProperties() {
        rewriteRun(
                xml("<project marker=\"${bc.version}\"><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version><properties><bc.version>1.2.9</bc.version></properties><dependencies>" + target("${bc.version}", "") + "</dependencies></project>", source -> source.path("pom.xml")),
                xml(project("<properties><bc.version>1.2.9</bc.version><bc.version>1.2.9</bc.version></properties><dependencies>" + target("${bc.version}", "") + "</dependencies>"), source -> source.path("duplicate/pom.xml")),
                xml(project("<properties><bc.version>1.2.9</bc.version></properties><dependencies>" + target("${bc.version}", "") + "<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>${bc.version}</version></dependency></dependencies>"), source -> source.path("shared/pom.xml")),
                xml(project("<properties><bc.version>1.2.9</bc.version></properties><dependencies>" + target("${bc.version}", "") + "</dependencies><profiles><profile><id>p</id><properties><bc.version>1.2.5</bc.version></properties></profile></profiles>"), source -> source.path("shadow/pom.xml"))
        );
    }

    @Test
    void preservesExternalOwnersRangesOutsideTargetAndFuture() {
        rewriteRun(xml(project("<dependencies>" +
                target(null, "") + target("${missing}", "") + target("[1.2.5,1.5.34)", "") +
                target("1.2.4", "") + target("1.3.14", "") + target("1.5.34", "") +
                target("1.6.0", "") + target("2.0.0", "") +
                "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesCoreButNeverChangesIndependentClassicVersion() {
        String before = project("<dependencies>" + target("1.2.9", "") +
                "<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId>" +
                "<version>1.2.13</version></dependency></dependencies>");
        String after = project("<dependencies>" + target("1.5.34", "") +
                "<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId>" +
                "<version>1.2.13</version></dependency></dependencies>");
        rewriteRun(pomXml(before, after));
    }

    @Test
    void preservesVariantsPluginDependenciesAndNestedLookalikes() {
        rewriteRun(xml(project("<dependencies>" + target("1.2.9", "<classifier>sources</classifier>") +
                        target("1.2.5", "<type>test-jar</type>") +
                        "<dependency><groupId>example</groupId><artifactId>logback-core</artifactId><version>1.2.9</version></dependency>" +
                        "<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-core-extra</artifactId><version>1.2.9</version></dependency>" +
                        "</dependencies><build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><version>1</version><dependencies>" +
                        target("1.2.9", "") + "</dependencies></plugin></plugins></build>"), source -> source.path("pom.xml")),
                xml("<root>" + project("<dependencies>" + target("1.2.9", "") + "</dependencies>") + "</root>",
                        source -> source.path("nested/pom.xml")));
    }

    @Test
    void upgradesGroovyStringsMapsAndKotlinStrings() {
        rewriteRun(
                buildGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.2.9' }",
                        "dependencies { implementation 'ch.qos.logback:logback-core:1.5.34' }", s -> s.path("string.gradle")),
                buildGradle("dependencies { runtimeOnly group: 'ch.qos.logback', name: 'logback-core', version: '1.2.5' }",
                        "dependencies { runtimeOnly group: 'ch.qos.logback', name: 'logback-core', version: '1.5.34' }", s -> s.path("map.gradle")),
                buildGradle("dependencies { testImplementation([group: 'ch.qos.logback', name: 'logback-core', version: '1.2.9']) }",
                        "dependencies { testImplementation([group: 'ch.qos.logback', name: 'logback-core', version: '1.5.34']) }", s -> s.path("map-literal.gradle")),
                buildGradleKts("dependencies { implementation(\"ch.qos.logback:logback-core:1.2.9\") }",
                        "dependencies { implementation(\"ch.qos.logback:logback-core:1.5.34\") }")
        );
    }

    @Test
    void preservesInterpolationCatalogPlatformsFourPartAndVariants() {
        rewriteRun(
                buildGradle("""
                        def v = '1.2.9'
                        dependencies {
                          implementation "ch.qos.logback:logback-core:${v}"
                          implementation libs.logback.core
                          implementation 'ch.qos.logback:logback-core:1.2.9:sources'
                          implementation group: 'ch.qos.logback', name: 'logback-core', version: '1.2.9', classifier: 'sources'
                          implementation([group: 'ch.qos.logback', name: 'logback-core', version: '1.2.9', ext: 'zip'])
                          implementation platform('ch.qos.logback:logback-bom:1.2.9')
                        }
                        """),
                buildGradleKts("""
                        val v = "1.2.9"
                        dependencies {
                            implementation("ch.qos.logback:logback-core:$v")
                            implementation(libs.logback.core)
                        }
                        """));
    }

    @Test
    void ignoresBuildscriptCustomAndNestedProjectDependencies() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'ch.qos.logback:logback-core:1.2.9' } }
                dependencies { generated { implementation 'ch.qos.logback:logback-core:1.2.9' } }
                project(':child') { dependencies { implementation 'ch.qos.logback:logback-core:1.2.9' } }
                fake { dependencies { implementation 'ch.qos.logback:logback-core:1.2.9' } }
                """));
    }

    @Test
    void skipsGeneratedBuildFilesAndIsIdempotent() {
        rewriteRun(
                xml(pom("1.2.9"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'ch.qos.logback:logback-core:1.2.9' }", s -> s.path("install/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.2.9"), pom("1.5.34")));
    }

    @Test
    void preservesDynamicStringCoordinatesForOwnerResolution() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                          implementation "ch.qos.logback:logback-core:1.2.9${''}"
                        }
                        """),
                buildGradle("""
                        dependencies {
                          implementation "xch.qos.logback:logback-core:1.2.9${''}"
                        }
                        """));
    }

    @Test
    void discoversAllPublicRecipesAndAggregateParity() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.logbackcore.MigrateDeterministicLogbackCore1_5_34",
                "com.huawei.clouds.openrewrite.logbackcore.FindLogbackCore1_5_34BuildRisks",
                "com.huawei.clouds.openrewrite.logbackcore.FindLogbackCore1_5_34SourceRisks",
                "com.huawei.clouds.openrewrite.logbackcore.FindLogbackCore1_5_34ConfigurationRisks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertTrue(aggregate.getRecipeList().size() >= 4);
        assertEquals(UPGRADE, aggregate.getRecipeList().get(0).getName());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String dependency(String version, String metadata) {
        return project("<dependencies>" + target(version, metadata) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-core</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + metadata + "</dependency>";
    }

    private static String propertyProject(String version, String property) {
        return project("<properties><" + property + ">" + version + "</" + property + "></properties>" +
                "<dependencies>" + target("${" + property + "}", "") + "</dependencies>" +
                "<profiles><profile><id>p</id><dependencies>" + target("${" + property + "}", "") +
                "</dependencies></profile></profiles>");
    }
}
