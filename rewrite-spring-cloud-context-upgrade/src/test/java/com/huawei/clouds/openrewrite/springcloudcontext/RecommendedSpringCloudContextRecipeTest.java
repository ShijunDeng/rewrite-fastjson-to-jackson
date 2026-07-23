package com.huawei.clouds.openrewrite.springcloudcontext;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedSpringCloudContextRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springcloudcontext.MigrateSpringCloudContextTo4_3_2";
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springcloudcontext.UpgradeSpringCloudContextTo4_3_2";
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS =
            new PrintOutputCapture.MarkerPrinter() {
                @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
                @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
                @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
            };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion())
                .markerPrinter(SILENT_MARKERS)
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void recommendedRecipeIsDiscoverableAndReusesPublicUpgrade() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertEquals("Migrate Spring Cloud Context applications to 4.3.2", recipe.getDisplayName());
        assertTrue(recipe.getRecipeList().stream().anyMatch(child -> UPGRADE.equals(child.getName())));
    }

    @Test
    void autoChangesRunBeforePreciseRiskFindings() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RECIPE)).expectedCyclesThatMakeChanges(1),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><properties><java.version>17</java.version></properties><dependencies><dependency>
                          <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>3.1.7</version>
                        </dependency></dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><properties><java.version>17</java.version></properties><dependencies><dependency>
                          <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>4.3.2</version>
                        </dependency></dependencies></project>
                        """,
                        source -> source.afterRecipe(after -> assertMarkerCount(after, 0))),
                yaml(
                        "spring.profiles: prod\nspring.cloud.refresh.enabled: true\n",
                        "spring.config.activate.on-profile: prod\nspring.cloud.refresh.enabled: true\n",
                        source -> source.path("application.yml")
                                .afterRecipe(after -> assertMarkerCount(after, 1))),
                java("import javax.servlet.Servlet; class Legacy {}",
                        source -> source.afterRecipe(after -> assertMarkerCount(after, 1)))
        );
    }

    @Test
    void publicUpgradeStaysStrictWhileRecommendedMarksExternalOwner() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RECIPE)).expectedCyclesThatMakeChanges(1),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owned</artifactId><version>1</version><dependencies><dependency>
                          <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${cloud.context.version}</version>
                        </dependency></dependencies></project>
                        """, source -> source.path("pom.xml").afterRecipe(after -> assertMarkerCount(after, 1)))
        );
    }

    @Test
    void recommendedRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties("spring.profiles=qa\nspring.cloud.refresh.enabled=false\n",
                        "spring.config.activate.on-profile=qa\nspring.cloud.refresh.enabled=false\n",
                        source -> source.path("application.properties")
                                .afterRecipe(after -> assertMarkerCount(after, 1)))
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static void assertMarkerCount(SourceFile source, int expected) {
        AtomicInteger count = new AtomicInteger();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                count.addAndGet(tree.getMarkers().findAll(SearchResult.class).size());
                return tree;
            }
        }.visit(source, 0);
        assertEquals(expected, count.get());
    }
}
