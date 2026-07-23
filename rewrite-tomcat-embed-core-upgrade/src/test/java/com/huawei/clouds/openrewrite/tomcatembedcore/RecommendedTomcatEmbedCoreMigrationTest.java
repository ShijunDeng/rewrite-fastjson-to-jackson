package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedTomcatEmbedCoreMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(TomcatEmbedCoreTestApi.sources()));
    }

    @Test
    void upgradesDependencyAndMigratesApiInSameRun() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.15"),
                        UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.57"), source -> source.path("pom.xml")),
                java("import jakarta.servlet.http.HttpSession; class T { Object user(HttpSession s){return s.getValue(\"user\");} }",
                        "import jakarta.servlet.http.HttpSession; class T { Object user(HttpSession s){return s.getAttribute(\"user\");} }")
        );
    }

    @Test
    void removesListenerAttributeBeforeRiskFinder() {
        rewriteRun(xml(
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\" appContextProtection=\"true\"/></Server>",
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" appContextProtection=\"true\"/></Server>",
                source -> source.path("conf/server.xml").afterRecipe(after ->
                        assertFalse(after.printAll().contains("has no Tomcat 10.1 setter"), after::printAll))));
    }

    @Test
    void officialServletRemovalRunsBeforeRiskFinder() {
        rewriteRun(java(
                "import jakarta.servlet.http.*; class T { Object x(HttpSession s,HttpServletResponse r){ r.encodeUrl(\"/\"); return s.getValueNames(); } }",
                "import jakarta.servlet.http.*; class T { Object x(HttpSession s,HttpServletResponse r){ r.encodeURL(\"/\"); return s.getAttributeNames(); } }"));
    }

    @Test
    void fullClusterRestartMarkerSurvivesDependencyUpgrade() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.41"),
                        UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.57"), source -> source.path("pom.xml")),
                xml("<Server><Cluster><Interceptor className=\"org.apache.catalina.tribes.group.interceptors.EncryptInterceptor\"/></Cluster></Server>",
                        source -> source.path("conf/server.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("stop every cluster node"), after::printAll)))
        );
    }

    @Test
    void realJfinalShapeAndParameterLimitAreHandledTogether() {
        // Java shape reduced from jfinal/jfinal@a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349.
        rewriteRun(
                java("import jakarta.servlet.http.HttpServletRequest; class JsonRequest { boolean x(HttpServletRequest req){return req.isRequestedSessionIdFromUrl();} }",
                        "import jakarta.servlet.http.HttpServletRequest; class JsonRequest { boolean x(HttpServletRequest req){return req.isRequestedSessionIdFromURL();} }"),
                xml("<Server><Service><Connector port=\"8080\"/></Service></Server>", source -> source.path("server.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("reduced Connector maxParameterCount"), after::printAll)))
        );
    }

    @Test
    void recommendedCompositionOrderIsExact() {
        var recipe = environment().activateRecipes(RECIPE);
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.tomcatembedcore.UpgradeTomcatEmbedCoreTo10_1_57",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcat9JakartaApiDependencies",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcat9JakartaNamespaces",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCore101Java",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCore101Configuration",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreBuildRisks",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreJavaRisks",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreConfigurationRisks",
                        "com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreResourceRisks"),
                recipe.getRecipeList().stream().map(org.openrewrite.Recipe::getName).toList());
    }

    @Test
    void publicUpgradeAndDirectUpgradeHaveParity() {
        var publicUpgrade = environment().activateRecipes(
                "com.huawei.clouds.openrewrite.tomcatembedcore.UpgradeTomcatEmbedCoreTo10_1_57");
        assertEquals(List.of(FindTomcatEmbedCoreBranchTransitionRisks.class.getName(),
                        UpgradeSelectedTomcatEmbedCoreDependency.class.getName()),
                publicUpgrade.getRecipeList().stream().map(org.openrewrite.Recipe::getName).toList());
        assertEquals(SetHolder.SOURCES, UpgradeSelectedTomcatEmbedCoreDependency.SOURCE_VERSIONS);
        assertEquals("10.1.57", UpgradeSelectedTomcatEmbedCoreDependency.TARGET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.0.18", "11.0.21"})
    void tomcat11ConflictIsMarkedButNeverDowngraded(String version) {
        rewriteRun(xml(UpgradeTomcatEmbedCoreDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains(FindTomcatEmbedCoreBranchTransitionRisks.TOMCAT_11), after::printAll);
                    assertTrue(after.printAll().contains("<version>" + version + "</version>"), after::printAll);
                    assertFalse(after.printAll().contains("<version>10.1.57</version>"), after::printAll);
                })));
    }

    @Test
    void tomcat9DependencyAliasAndNamespaceMigrateTogether() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("9.0.117"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains(FindTomcatEmbedCoreBranchTransitionRisks.TOMCAT_9), after::printAll);
                            assertTrue(after.printAll().contains("<version>10.1.57</version>"), after::printAll);
                            assertFalse(after.printAll().contains("<version>9.0.117</version>"), after::printAll);
                        })),
                java(
                        "import javax.servlet.http.HttpSession; class T { Object value(HttpSession session){return session.getValue(\"user\");} }",
                        "import jakarta.servlet.http.HttpSession; class T { Object value(HttpSession session){return session.getAttribute(\"user\");} }"));
    }

    @Test
    void aggregateIsIdempotentAcrossTwoCycles() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/x\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeURL(\"/x\");} }"),
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.15"),
                        UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.57"), source -> source.path("pom.xml")));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatembedcore",
                                      "org.openrewrite.java.migrate.jakarta")
                .build();
    }

    private static final class SetHolder {
        private static final java.util.Set<String> SOURCES = java.util.Set.of(
                "10.1.15", "10.1.16", "10.1.20", "10.1.25", "10.1.28", "10.1.35", "10.1.36",
                "10.1.40", "10.1.41", "10.1.42", "10.1.43", "10.1.44", "10.1.46", "10.1.47",
                "10.1.48", "10.1.49", "10.1.52", "10.1.54", "9.0.102",
                "9.0.104", "9.0.105", "9.0.106", "9.0.107", "9.0.108", "9.0.111", "9.0.115",
                "9.0.117", "9.0.54", "9.0.69", "9.0.71", "9.0.75", "9.0.82", "9.0.83", "9.0.86",
                "9.0.87", "9.0.91", "9.0.96", "9.0.98");
    }
}
