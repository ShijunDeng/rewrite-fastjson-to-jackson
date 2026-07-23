package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class TwelveMonkeysJpegRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.twelvemonkeysjpeg.MigrateTwelveMonkeysImageIoJpegTo3_12_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.twelvemonkeysjpeg")
                        .build().activateRecipes(RECIPE))
                .parser(JavaParser.fromJavaVersion().classpath("imageio-jpeg"));
    }

    @Test
    void strictUpgradeFamilyAlignmentAndExecutableReviewRunTogether() {
        String before = UpgradeTwelveMonkeysJpegDependencyTest.project("<dependencies>" +
                UpgradeTwelveMonkeysJpegDependencyTest.dep("3.9.3") +
                AlignTwelveMonkeysJpegCompanionsTest.dep("com.twelvemonkeys.imageio", "imageio-core", "3.9.3") +
                "</dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                "<artifactId>maven-shade-plugin</artifactId></plugin></plugins></build>");
        String after = before.replace("<version>3.9.3</version>", "<version>3.12.0</version>");
        rewriteRun(
                xml(before, after, source -> source.path("app/pom.xml").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("3.12.0"));
                    assertTrue(printed.contains("ServicesResourceTransformer"));
                })),
                java("""
                        import javax.imageio.ImageIO;
                        class Thumbnailer {
                            Object read(java.io.File file) throws Exception { return ImageIO.read(file); }
                        }
                        """, source -> source.path("app/src/Thumbnailer.java").after(actual -> actual).afterRecipe(cu ->
                        assertTrue(cu.printAll().contains("JPEG read behavior changed")))));
    }

    @Test
    void declarativeRecipeHasSafetyOrderedExecutableComponents() {
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.twelvemonkeysjpeg")
                .build().activateRecipes(RECIPE);
        assertEquals(4, recipe.getRecipeList().size());
        assertEquals("com.huawei.clouds.openrewrite.twelvemonkeysjpeg.UpgradeTwelveMonkeysImageIoJpegTo3_12_0",
                recipe.getRecipeList().get(0).getName());
        assertEquals(AlignTwelveMonkeysJpegCompanions.class, recipe.getRecipeList().get(1).getClass());
        assertEquals(FindTwelveMonkeysJpegSourceRisks.class, recipe.getRecipeList().get(2).getClass());
        assertEquals(FindTwelveMonkeysJpegBuildRisks.class, recipe.getRecipeList().get(3).getClass());
    }
}
