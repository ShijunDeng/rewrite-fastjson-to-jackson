package com.huawei.clouds.openrewrite.jaxen;

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

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class JaxenRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.jaxen.MigrateJaxenTo2_0_1";
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS = new PrintOutputCapture.MarkerPrinter() {
        @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
        @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
        @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE))
                .parser(JavaParser.fromJavaVersion().classpath("jaxen"))
                .markerPrinter(SILENT_MARKERS)
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void recipeIsDiscoverableValidAndOrdersAutomaticWorkBeforeRiskMarkers() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> RECIPE.equals(candidate.getName())));
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals("com.huawei.clouds.openrewrite.jaxen.UpgradeJaxenTo2_0_1", names.get(0));
        assertEquals(MigrateJaxenDeterministicJava.class.getName(), names.get(1));
        assertTrue(names.indexOf(FindJaxenJavaMigrationRisks.class.getName()) >
                   names.indexOf(MigrateJaxenDeterministicJava.class.getName()), names.toString());
        assertTrue(names.indexOf(FindJaxenBuildMigrationRisks.class.getName()) > 0, names.toString());
    }

    @Test
    void recommendedRecipeMigratesOwnedBuildsAndDocumentedJavaApisBeforeMarking() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><maven.compiler.source>1.4</maven.compiler.source></properties>
                          <dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>1.2.0</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><maven.compiler.source>1.4</maven.compiler.source></properties>
                          <dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>2.0.1</version></dependency></dependencies>
                        </project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1, "Java 1.5"))),
                buildGradle(
                        """
                        ext.jaxenVersion = '2.0.0'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """,
                        """
                        ext.jaxenVersion = '2.0.1'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 0))),
                java(
                        """
                        import org.jaxen.FunctionCallException;
                        import org.jaxen.dom.DOMXPath;
                        class Query {
                            String text(Object node) throws Exception { return new DOMXPath("//item").valueOf(node); }
                            Throwable cause() { return new FunctionCallException("failure").getNestedException(); }
                        }
                        """,
                        """
                        import org.jaxen.FunctionCallException;
                        import org.jaxen.dom.DOMXPath;
                        class Query {
                            String text(Object node) throws Exception { return new DOMXPath("//item").stringValueOf(node); }
                            Throwable cause() { return new FunctionCallException("failure").getCause(); }
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1, "edge behavior")))
        );
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossAutomaticAndManualFindings() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" +
                        "<dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>2.0.0</version></dependency></dependencies></project>",
                        "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" +
                        "<dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>2.0.1</version></dependency></dependencies></project>"),
                java(
                        "import org.jaxen.dom.DOMXPath; class Q { String q(Object n)throws Exception{return new DOMXPath(\"//a\").valueOf(n);} }",
                        "import org.jaxen.dom.DOMXPath; class Q { String q(Object n)throws Exception{return new DOMXPath(\"//a\").stringValueOf(n);} }",
                        source -> source.afterRecipe(after -> assertMarks(after, 1, "edge behavior")))
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static void assertMarks(SourceFile source, int expected, String... fragments) {
        List<String> descriptions = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                tree.getMarkers().findAll(SearchResult.class).stream()
                        .map(SearchResult::getDescription).forEach(descriptions::add);
                return tree;
            }
        }.visit(source, 0);
        assertEquals(expected, descriptions.size(), descriptions.toString());
        for (String fragment : fragments) {
            assertTrue(descriptions.stream().anyMatch(message -> message != null && message.contains(fragment)),
                    () -> "Missing '" + fragment + "' in " + descriptions);
        }
    }
}
