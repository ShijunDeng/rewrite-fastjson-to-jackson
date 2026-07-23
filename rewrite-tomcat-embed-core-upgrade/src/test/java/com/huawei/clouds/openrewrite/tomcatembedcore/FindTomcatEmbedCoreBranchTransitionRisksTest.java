package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindTomcatEmbedCoreBranchTransitionRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTomcatEmbedCoreBranchTransitionRisks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.0.54", "9.0.102", "9.0.117"})
    void marksTomcat9NamespaceTransition(String version) {
        rewriteRun(xml(UpgradeTomcatEmbedCoreDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.0.18", "11.0.21"})
    void marksAndPreservesBlockedTomcat11Conflict(String version) {
        rewriteRun(xml(UpgradeTomcatEmbedCoreDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("automatic migration is intentionally blocked"), after::printAll))));
    }

    @Test
    void resolvesOwnedRootAndProfileProperties() {
        rewriteRun(xml(UpgradeTomcatEmbedCoreDependencyTest.project(
                        "<properties><rootTomcat>9.0.69</rootTomcat></properties><dependencies>" +
                        UpgradeTomcatEmbedCoreDependencyTest.dep("${rootTomcat}") + "</dependencies>" +
                        "<profiles><profile><id>it</id><properties><profileTomcat>11.0.18</profileTomcat></properties><dependencies>" +
                        UpgradeTomcatEmbedCoreDependencyTest.dep("${profileTomcat}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll);
                    assertTrue(after.printAll().contains("automatic migration is intentionally blocked"), after::printAll);
                })));
    }

    @Test
    void marksRootGradleGroovyKotlinAndMapCoordinates() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:9.0.98' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll))),
                buildGradleKts("dependencies { implementation(\"org.apache.tomcat.embed:tomcat-embed-core:11.0.21\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("automatic migration is intentionally blocked"), after::printAll))),
                buildGradle("dependencies { implementation group: 'org.apache.tomcat.embed', name: 'tomcat-embed-core', version: '9.0.91' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll)))
        );
    }

    @Test
    void targetPatchOtherCoordinatesDynamicNestedAndGeneratedAreUnmarked() {
        rewriteRun(
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("10.1.54"), source -> source.path("pom.xml")),
                xml(UpgradeTomcatEmbedCoreDependencyTest.project("<dependencies>" +
                        UpgradeTomcatEmbedCoreDependencyTest.rawDep("example", "tomcat-embed-core", "9.0.54", "") +
                        "</dependencies>"), source -> source.path("pom.xml")),
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("${tomcat.version}"), source -> source.path("pom.xml")),
                buildGradle("subprojects { dependencies { implementation 'org.apache.tomcat.embed:tomcat-embed-core:9.0.54' } }"),
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("9.0.54"), source -> source.path("target/pom.xml"))
        );
    }

    @Test
    void publicUpgradeKeepsBranchMarkerAfterVersionRewrite() {
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatembedcore")
                .build().activateRecipes("com.huawei.clouds.openrewrite.tomcatembedcore.UpgradeTomcatEmbedCoreTo10_1_57");
        rewriteRun(specification -> specification.recipe(recipe),
                xml(UpgradeTomcatEmbedCoreDependencyTest.pom("9.0.54"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll);
                            assertTrue(after.printAll().contains("<version>10.1.57</version>"), after::printAll);
                            assertFalse(after.printAll().contains("<version>9.0.54</version>"), after::printAll);
                        })));
    }
}
