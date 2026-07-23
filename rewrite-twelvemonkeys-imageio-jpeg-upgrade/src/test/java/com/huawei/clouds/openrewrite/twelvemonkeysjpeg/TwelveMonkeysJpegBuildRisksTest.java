package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.xml.Assertions.xml;

class TwelveMonkeysJpegBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTwelveMonkeysJpegBuildRisks());
    }

    @Test
    void marksShadeWithoutServiceTransformerAndResourceStripping() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") + "</dependencies>" +
                        "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-shade-plugin</artifactId><configuration><filters><filter>" +
                        "<artifact>*:*</artifact><excludes><exclude>META-INF/services/**</exclude>" +
                        "</excludes></filter></filters></configuration></plugin></plugins></build>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("ServicesResourceTransformer"));
                    assertTrue(printed.contains("resource rule can remove ImageIO SPI"));
                })));
    }

    @Test
    void doesNotFlagShadeThatMergesServices() {
        String source = UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") + "</dependencies>" +
                "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration><transformers><transformer " +
                "implementation=\"org.apache.maven.plugins.shade.resource.ServicesResourceTransformer\"/>" +
                "</transformers></configuration></plugin></plugins></build>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml").afterRecipe(document ->
                assertFalse(document.printAll().contains("Shaded JPEG plugin has no")))));
    }

    @Test
    void marksOsgiNativeAndNestedJarBoundaries() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") + "</dependencies><build><plugins>" +
                        plugin("org.apache.felix", "maven-bundle-plugin") +
                        plugin("org.graalvm.buildtools", "native-maven-plugin") +
                        plugin("org.springframework.boot", "spring-boot-maven-plugin") +
                        "</plugins></build>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("OSGi packaging detected"));
                    assertTrue(printed.contains("Native-image packaging detected"));
                    assertTrue(printed.contains("Executable/nested JAR packaging detected"));
                })));
    }

    @Test
    void marksOnlyMixedCompanionsInTheSameOwner() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<profiles>" +
                        "<profile><id>jpeg</id><dependencies>" + UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        AlignTwelveMonkeysJpegCompanionsTest.dep("com.twelvemonkeys.imageio", "imageio-core", "3.8.0") +
                        "</dependencies></profile>" +
                        "<profile><id>other</id><dependencies>" +
                        AlignTwelveMonkeysJpegCompanionsTest.dep("com.twelvemonkeys.imageio", "imageio-metadata", "3.8.0") +
                        "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("Mixed explicit TwelveMonkeys versions"));
                    assertTrue(printed.indexOf("Mixed explicit TwelveMonkeys versions") ==
                               printed.lastIndexOf("Mixed explicit TwelveMonkeys versions"));
                })));
    }

    @Test
    void marksExternalUnlistedAndVariantTargetOwnership() {
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("${tm.version}"),
                        source -> source.path("property/pom.xml").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("property ownership")))),
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.10.1"),
                        source -> source.path("unlisted/pom.xml").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("outside the workbook")))),
                xml(UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                                UpgradeTwelveMonkeysJpegDependencyTest.dep("3.9.3", "<classifier>tests</classifier>") + "</dependencies>"),
                        source -> source.path("variant/pom.xml").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Classifier/type variants")))));
    }

    @Test
    void buildMarkersRequireAVisibleTargetDependency() {
        String source = UpgradeTwelveMonkeysJpegDependencyTest.project("<build><plugins>" +
                plugin("org.apache.maven.plugins", "maven-shade-plugin") +
                plugin("org.apache.felix", "maven-bundle-plugin") + "</plugins></build>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml").afterRecipe(document -> {
            assertFalse(document.printAll().contains("ServicesResourceTransformer"));
            assertFalse(document.printAll().contains("OSGi packaging detected"));
        })));
    }

    @Test
    void rootBuildConfigurationIsRelevantToProfileDependencyButSiblingProfileIsNot() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<build><plugins>" +
                        plugin("org.apache.maven.plugins", "maven-shade-plugin") + "</plugins></build><profiles>" +
                        "<profile><id>jpeg</id><dependencies>" + UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        "</dependencies></profile><profile><id>other</id><build><plugins>" +
                        plugin("org.apache.felix", "maven-bundle-plugin") + "</plugins></build></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("ServicesResourceTransformer"));
                    assertFalse(printed.contains("OSGi packaging detected"));
                })));
    }

    @Test
    void marksGradleShadowAndServiceExclusionOnlyWithRootDependency() {
        rewriteRun(
                buildGradle("""
                        plugins { id 'com.github.johnrengelman.shadow' version '8.1.1' }
                        dependencies { runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.12.0' }
                        shadowJar { exclude 'META-INF/services/**' }
                        """, source -> source.path("app/build.gradle").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("Shadow packaging detected") || printed.contains("service descriptors"));
                    assertTrue(printed.contains("Do not strip ImageIO service descriptors"));
                })),
                buildGradle("""
                        plugins { id 'com.github.johnrengelman.shadow' version '8.1.1' }
                        shadowJar { exclude 'META-INF/services/**' }
                        """, source -> source.path("other/build.gradle")));
    }

    private static String plugin(String group, String artifact) {
        return "<plugin><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId></plugin>";
    }
}
