package com.huawei.clouds.openrewrite.jaxbimpl;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedJaxbImplRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.jaxbimpl.MigrateJaxbImplTo4_0_6";
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS =
            new PrintOutputCapture.MarkerPrinter() {
                @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
                @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
                @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
            };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(
                        "jaxb-api", "javax.activation-api", "jakarta.xml.bind-api",
                        "jakarta.activation-api", "jaxb-impl"))
                .markerPrinter(SILENT_MARKERS)
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void autoFixesRunBeforeRiskMarkers() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE))
                        .expectedCyclesThatMakeChanges(1),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>2.3.8</version>
                        </dependency></dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>4.0.6</version>
                        </dependency></dependencies></project>
                        """,
                        source -> source.afterRecipe(after -> assertMarkerCount(after, 0))
                ),
                java(
                        """
                        import javax.xml.bind.Marshaller;
                        class Shared { static Marshaller marshaller; String key = "com.sun.xml.bind.xmlHeaders"; }
                        """,
                        """
                        import jakarta.xml.bind.Marshaller;
                        class Shared { static Marshaller marshaller; String key = "org.glassfish.jaxb.xmlHeaders"; }
                        """,
                        source -> source.afterRecipe(after -> assertMarkerCount(after, 1))
                ),
                xml(
                        "<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.0\"/>",
                        "<jaxb:bindings xmlns:jaxb=\"https://jakarta.ee/xml/ns/jaxb\" version=\"3.0\"/>",
                        source -> source.path("bindings.xjb").afterRecipe(after -> assertMarkerCount(after, 0))
                )
        );
    }

    @Test
    void recommendedRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.activation.DataHandler; class A { DataHandler value; }",
                        "import jakarta.activation.DataHandler; class A { DataHandler value; }")
        );
    }

    private static void assertMarkerCount(SourceFile source, int expected) {
        AtomicInteger count = new AtomicInteger();
        List<String> descriptions = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                tree.getMarkers().findAll(SearchResult.class).forEach(marker -> {
                    count.incrementAndGet();
                    descriptions.add(marker.getDescription());
                });
                return tree;
            }
        }.visit(source, 0);
        assertEquals(expected, count.get(), descriptions.toString());
    }
}
