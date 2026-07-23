package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class TomcatEmbedCoreProjectGateTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatembedcore",
                                              "org.openrewrite.java.migrate.jakarta")
                        .build().activateRecipes(RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(TomcatEmbedCoreTestApi.sources()));
    }

    @ParameterizedTest(name = "project gate source {0}")
    @MethodSource("sourceVersions")
    void everyApprovedSourceVersionCarriesEligibilityAcrossDependencyEdit(String version) {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom(version),
                        source -> source.path("pom.xml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertTrue(after.printAll().contains("<version>10.1.57</version>"),
                                            after::printAll);
                                    assertFalse(after.printAll().contains("<version>" + version + "</version>"),
                                            after::printAll);
                                })),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeURL(\"/\");} }")
        );
    }

    static Stream<String> sourceVersions() {
        return UpgradeTomcatEmbedCoreDependencyTest.sourceVersions();
    }

    @ParameterizedTest(name = "strict project NOOP {0}")
    @ValueSource(strings = {"10.1.57", "10.1.58", "10.0.27", "9.0.53", "11.0.22", "12.0.0"})
    void targetHigherAndOutOfListProjectsAreCompleteNoop(String version) {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom(version),
                        source -> source.path("app/pom.xml")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        source -> source.path("app/src/main/java/T.java")),
                xml(
                        "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>",
                        source -> source.path("app/conf/server.xml"))
        );
    }

    @Test
    void unrelatedCoordinateProjectIsCompleteNoop() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.project(
                                "<dependencies>" +
                                UpgradeTomcatEmbedCoreDependencyTest.rawDep(
                                        "example", "tomcat-embed-core", "9.0.54", "") +
                                "</dependencies>"),
                        source -> source.path("app/pom.xml")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        source -> source.path("app/src/main/java/T.java")),
                xml(
                        "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>",
                        source -> source.path("app/conf/server.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.0.18", "11.0.21"})
    void suppliedTomcat11ConflictOnlyMarksBuildAndNeverTouchesProjectSource(String version) {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom(version),
                        source -> source.path("app/pom.xml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertTrue(after.printAll().contains(
                                                    FindTomcatEmbedCoreBranchTransitionRisks.TOMCAT_11),
                                            after::printAll);
                                    assertTrue(after.printAll().contains("目标版本冲突（禁止降级）"),
                                            after::printAll);
                                    assertTrue(after.printAll().contains("<version>" + version + "</version>"),
                                            after::printAll);
                                    assertFalse(after.printAll().contains("<version>10.1.57</version>"),
                                            after::printAll);
                                })),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        source -> source.path("app/src/main/java/T.java")),
                xml(
                        "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>",
                        source -> source.path("app/conf/server.xml"))
        );
    }

    @Test
    void tomcat101SourceDoesNotTriggerTomcat9NamespaceMigration() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.15"),
                        UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.57"),
                        source -> source.path("app/pom.xml")),
                java(
                        "import javax.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getValue(\"x\");} }",
                        source -> source.path("app/src/main/java/T.java"))
        );
    }

    @Test
    void nearestNestedBuildRootBlocksOuterProjectEligibility() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.15"),
                        UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.57"),
                        source -> source.path("parent/pom.xml")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class Root { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class Root { String x(HttpServletResponse r){return r.encodeURL(\"/\");} }",
                        source -> source.path("parent/src/main/java/Root.java")),
                xml(UpgradeTomcatEmbedCoreDependencyTest.project(
                                "<dependencies>" +
                                UpgradeTomcatEmbedCoreDependencyTest.rawDep(
                                        "example", "other", "1.0", "") +
                                "</dependencies>"),
                        source -> source.path("parent/nested/pom.xml")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class Nested { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        source -> source.path("parent/nested/src/main/java/Nested.java"))
        );
    }

    @Test
    void exclusiveMavenPropertyCarriesEligibility() {
        rewriteRun(
                xml(
                        UpgradeTomcatEmbedCoreDependencyTest.project(
                                "<properties><tomcat.version>9.0.117</tomcat.version></properties>" +
                                "<dependencies>" +
                                UpgradeTomcatEmbedCoreDependencyTest.dep("${tomcat.version}") +
                                "</dependencies>"),
                        source -> source.path("app/pom.xml").after(actual -> actual)
                                .afterRecipe(after -> assertTrue(after.printAll()
                                        .contains("<tomcat.version>10.1.57</tomcat.version>"),
                                        after::printAll))),
                java(
                        "import javax.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getValue(\"x\");} }",
                        "import jakarta.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getAttribute(\"x\");} }",
                        source -> source.path("app/src/main/java/T.java"))
        );
    }

    @Test
    void rootGradleGroovyAndKotlinBuildsCarryEligibility() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.1.15' }",
                        "dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:10.1.57' }",
                        source -> source.path("groovy/build.gradle")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class G { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class G { String x(HttpServletResponse r){return r.encodeURL(\"/\");} }",
                        source -> source.path("groovy/src/main/java/G.java")),
                buildGradleKts(
                        "dependencies { implementation(\"org.apache.tomcat.embed:tomcat-embed-core:10.1.16\") }",
                        "dependencies { implementation(\"org.apache.tomcat.embed:tomcat-embed-core:10.1.57\") }",
                        source -> source.path("kotlin/build.gradle.kts")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class K { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class K { String x(HttpServletResponse r){return r.encodeURL(\"/\");} }",
                        source -> source.path("kotlin/src/main/java/K.java"))
        );
    }

    @Test
    void rootGroovyMapLiteralCarriesEligibility() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation([group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '10.1.15']) }",
                        "dependencies { implementation([group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '10.1.57']) }",
                        source -> source.path("app/build.gradle")),
                java(
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeURL(\"/\");} }",
                        source -> source.path("app/src/main/java/T.java"))
        );
    }
}
