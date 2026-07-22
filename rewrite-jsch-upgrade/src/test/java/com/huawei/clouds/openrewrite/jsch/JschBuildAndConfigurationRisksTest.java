package com.huawei.clouds.openrewrite.jsch;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class JschBuildAndConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJsch227BuildRisks());
    }

    @Test
    void marksOriginalJcraftDependencyInMavenAndGradle() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.jcraft</groupId><artifactId>jsch</artifactId><version>0.1.55</version>
                        </dependency></dependencies></project>
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "duplicate com.jcraft.jsch classes"))),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'com.jcraft:jsch:0.1.55' }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "duplicate com.jcraft.jsch classes"))));
    }

    @Test
    void marksVersionlessPropertyRangeAndDynamicOwners() {
        rewriteRun(
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version>
                          <properties><jsch.version>0.2.9</jsch.version></properties><dependencies>
                            <dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>${jsch.version}</version></dependency>
                            <dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "migrate the actual version owner"))),
                buildGradle("""
                        plugins { id 'java' }
                        def jschVersion = '0.2.9'
                        dependencies {
                            implementation "com.github.mwiede:jsch:${jschVersion}"
                            runtimeOnly 'com.github.mwiede:jsch:+'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "migrate the actual version owner"))));
    }

    @Test
    void marksFixedOutOfScopeVersionWithoutWideningAutoSelection() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>outside</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.2.10</version>
                </dependency></dependencies></project>
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), "outside the workbook source set"))));
    }

    @Test
    void marksNonstandardVariantsAndExplicitPreJava8Baseline() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies><dependency>
                    <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>0.2.9</version><classifier>sources</classifier>
                  </dependency></dependencies>
                </project>
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "requires Java 8 or newer");
                    assertContains(after.printAll(), "classified or non-JAR JSch artifact");
                })));
    }

    @Test
    void targetAndSimilarCoordinatesAreClean() {
        rewriteRun(
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>clean</artifactId><version>1</version>
                          <properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies>
                            <dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>2.27.7</version></dependency>
                            <dependency><groupId>example</groupId><artifactId>jsch</artifactId><version>0.2.9</version></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'com.github.mwiede:jsch:2.27.7' }
                        """));
    }

    @Test
    void recommendedRecipeUpgradesSelectedVersionBeforeBuildAudit() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jsch")
                        .build().activateRecipes("com.huawei.clouds.openrewrite.jsch.MigrateJschTo2_27_7")),
                pomXml(pom("0.2.9"), pom("2.27.7")));
    }

    @Test
    void buildMarkersAreIdempotentAndGeneratedTreesAreSkipped() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.jcraft</groupId><artifactId>jsch</artifactId><version>0.1.55</version>
                        </dependency></dependencies></project>
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "duplicate com.jcraft.jsch classes"))),
                pomXml(pom("0.2.9"), source -> source.path("target/generated/pom.xml")));
    }

    @Test
    void buildFinderOnlyAuditsRootGradleDependenciesAndUsesParentPathFilter() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'com.github.mwiede:jsch:0.2.10' } }",
                        source -> source.afterRecipe(after ->
                                assertFalse(after.printAll().contains("outside the workbook"), after.printAll()))),
                buildGradle("dependencies { implementation 'com.github.mwiede:jsch:0.2.10' }",
                        source -> source.path("GeneratedSources/build.gradle").afterRecipe(after ->
                                assertFalse(after.printAll().contains("outside the workbook"), after.printAll()))),
                buildGradle("dependencies { implementation 'com.github.mwiede:jsch:0.2.10' }",
                        source -> source.path("install.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "outside the workbook"))));
    }

    @Test
    void marksExactPropertiesSecurityAndTimeoutEntries() {
        rewriteRun(
                spec -> spec.recipe(new FindJsch227ConfigurationRisks()),
                properties("""
                        jsch.server_host_key=ssh-ed25519,ssh-rsa
                        jsch.StrictHostKeyChecking=no
                        jsch.ConnectTimeout=10000
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, "Legacy/explicit SSH algorithm policy");
                    assertContains(printed, "StrictHostKeyChecking disables or weakens");
                    assertContains(printed, "interpretation changed to seconds");
                })));
    }

    @Test
    void marksNestedYamlAndNonPomXmlEntries() {
        rewriteRun(
                spec -> spec.recipe(new FindJsch227ConfigurationRisks()),
                yaml("""
                        ssh:
                          PubkeyAcceptedAlgorithms: "rsa-sha2-512,ssh-rsa"
                          ServerAliveInterval: 30
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "Legacy/explicit SSH algorithm policy");
                    assertContains(after.printAll(), "interpretation changed to seconds");
                })),
                xml("""
                        <ssh><StrictHostKeyChecking>ask</StrictHostKeyChecking><cipher.c2s>aes256-cbc</cipher.c2s></ssh>
                        """, source -> source.path("ssh.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "StrictHostKeyChecking disables or weakens");
                    assertContains(after.printAll(), "Legacy/explicit SSH algorithm policy");
                })));
    }

    @Test
    void leavesSecureValuesCommentsAndSimilarKeysUntouched() {
        rewriteRun(
                spec -> spec.recipe(new FindJsch227ConfigurationRisks()),
                properties("""
                        # jsch.server_host_key=ssh-rsa
                        jsch.server_host_key=ssh-ed25519,rsa-sha2-512
                        jsch.StrictHostKeyChecking=yes
                        business.ConnectTimeoutMs=10000
                        jsch.other=contains-ssh-rsa-but-is-not-an-algorithm-list
                        """),
                yaml("""
                        ssh:
                          server_host_key: ssh-ed25519
                          StrictHostKeyChecking: yes
                          ConnectTimeoutMs: 10000
                        """),
                xml("<ssh><description>ssh-rsa</description><StrictHostKeyChecking>yes</StrictHostKeyChecking></ssh>",
                        source -> source.path("ssh.xml")));
    }

    @Test
    void genericExactConfigurationKeysWithoutSshOwnershipAreNoop() {
        rewriteRun(
                spec -> spec.recipe(new FindJsch227ConfigurationRisks()),
                properties("server_host_key=ssh-rsa\nConnectTimeout=10000\n",
                        source -> source.path("config/application.properties")),
                yaml("server_host_key: ssh-rsa\nConnectTimeout: 10000\n",
                        source -> source.path("config/application.yaml")),
                xml("<business><cipher.c2s>aes256-cbc</cipher.c2s><StrictHostKeyChecking>no</StrictHostKeyChecking></business>",
                        source -> source.path("config/application.xml")));
    }

    @Test
    void configurationMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindJsch227ConfigurationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties("jsch.PubkeyAcceptedAlgorithms=ssh-rsa\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Legacy/explicit SSH algorithm policy"))));
    }

    @Test
    void configurationFinderSkipsCaseVariantGeneratedParentsButNotInstallLeaf() {
        rewriteRun(
                spec -> spec.recipe(new FindJsch227ConfigurationRisks()),
                properties("jsch.server_host_key=ssh-rsa\n", source -> source.path("Generated-fixtures/jsch.properties")
                        .afterRecipe(after -> assertFalse(after.printAll().contains("Legacy/explicit"), after.printAll()))),
                properties("jsch.server_host_key=ssh-rsa\n", source -> source.path("src/install.properties")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Legacy/explicit SSH algorithm policy"))));
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jsch-app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
