package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class AlignTwelveMonkeysJpegCompanionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AlignTwelveMonkeysJpegCompanions());
    }

    @Test
    void alignsAllExplicitFamilyPinsInTheSameMavenOwner() {
        String before = UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                dep("com.twelvemonkeys.imageio", "imageio-core", "3.9.3") +
                dep("com.twelvemonkeys.imageio", "imageio-metadata", "3.9.3") +
                dep("com.twelvemonkeys.common", "common-lang", "3.9.3") +
                dep("com.twelvemonkeys.common", "common-io", "3.9.3") +
                dep("com.twelvemonkeys.common", "common-image", "3.9.3") + "</dependencies>");
        String after = before.replace("<version>3.9.3</version>", "<version>3.12.0</version>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesAPropertySharedOnlyByTheTargetFamily() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<properties><tm.version>3.9.3</tm.version></properties><dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("${tm.version}") +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "${tm.version}") +
                        dep("com.twelvemonkeys.imageio", "imageio-metadata", "${tm.version}") + "</dependencies>"),
                UpgradeTwelveMonkeysJpegDependencyTest.project("<properties><tm.version>3.12.0</tm.version></properties><dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("${tm.version}") +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "${tm.version}") +
                        dep("com.twelvemonkeys.imageio", "imageio-metadata", "${tm.version}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesACompanionOnlyPropertyWhenTheSameOwnerHasTarget() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<properties><core.version>3.9.3</core.version></properties><dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "${core.version}") + "</dependencies>"),
                UpgradeTwelveMonkeysJpegDependencyTest.project("<properties><core.version>3.12.0</core.version></properties><dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "${core.version}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void neverCrossesSiblingProfilesOrRootOwnership() {
        String source = UpgradeTwelveMonkeysJpegDependencyTest.project("<profiles>" +
                "<profile><id>jpeg</id><dependencies>" + UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") + "</dependencies></profile>" +
                "<profile><id>other</id><dependencies>" + dep("com.twelvemonkeys.imageio", "imageio-core", "3.9.3") + "</dependencies></profile>" +
                "</profiles><dependencies>" + dep("com.twelvemonkeys.imageio", "imageio-metadata", "3.9.3") + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    @Test
    void rootTargetAlignsOnlyRootWhileProfileTargetAlignsItsProfile() {
        rewriteRun(xml(
                UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "3.9.3") + "</dependencies>" +
                        "<profiles><profile><id>jpeg</id><dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        dep("com.twelvemonkeys.imageio", "imageio-metadata", "3.9.3") + "</dependencies></profile></profiles>"),
                UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "3.12.0") + "</dependencies>" +
                        "<profiles><profile><id>jpeg</id><dependencies>" +
                        UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                        dep("com.twelvemonkeys.imageio", "imageio-metadata", "3.12.0") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void preservesSharedPropertiesVariantsAndUnlistedVersions() {
        String source = UpgradeTwelveMonkeysJpegDependencyTest.project("<properties><tm.version>3.9.3</tm.version></properties><dependencies>" +
                UpgradeTwelveMonkeysJpegDependencyTest.dep("3.12.0") +
                dep("com.twelvemonkeys.imageio", "imageio-core", "${tm.version}") +
                dep("x", "y", "${tm.version}") +
                dep("com.twelvemonkeys.imageio", "imageio-metadata", "3.9.4") +
                dep("com.twelvemonkeys.common", "common-lang", "3.9.3", "<classifier>tests</classifier>") + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    @Test
    void alignsRealGradleFamilyShapesOnlyWhenJpegIsOwned() {
        // sksamuel/scrimage@a922d935b182b526803f9a8902cba5a0dbeeca06 pins core and JPEG together.
        rewriteRun(
                buildGradleKts("""
                        dependencies {
                            implementation("com.twelvemonkeys.imageio:imageio-core:3.9.3")
                            implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.12.0")
                        }
                        """, """
                        dependencies {
                            implementation("com.twelvemonkeys.imageio:imageio-core:3.12.0")
                            implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.12.0")
                        }
                        """),
                buildGradle("""
                        dependencies {
                            runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.12.0'
                            implementation group: 'com.twelvemonkeys.imageio', name: 'imageio-metadata', version: '3.9.3'
                            implementation([group: 'com.twelvemonkeys.common', name: 'common-io', version: '3.9.3'])
                        }
                        """, """
                        dependencies {
                            runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.12.0'
                            implementation group: 'com.twelvemonkeys.imageio', name: 'imageio-metadata', version: '3.12.0'
                            implementation([group: 'com.twelvemonkeys.common', name: 'common-io', version: '3.12.0'])
                        }
                        """));
    }

    @Test
    void noTargetMeansNoCompanionRewriteAndTwoCyclesAreStable() {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.twelvemonkeys.imageio:imageio-core:3.9.3' }"),
                xml(UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                        dep("com.twelvemonkeys.imageio", "imageio-core", "3.9.3") + "</dependencies>"),
                        source -> source.path("pom.xml")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.12.0'; implementation 'com.twelvemonkeys.imageio:imageio-core:3.9.3' }",
                        "dependencies { runtimeOnly 'com.twelvemonkeys.imageio:imageio-jpeg:3.12.0'; implementation 'com.twelvemonkeys.imageio:imageio-core:3.12.0' }"));
    }

    static String dep(String group, String artifact, String version) {
        return dep(group, artifact, version, "");
    }

    static String dep(String group, String artifact, String version, String extra) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }
}
