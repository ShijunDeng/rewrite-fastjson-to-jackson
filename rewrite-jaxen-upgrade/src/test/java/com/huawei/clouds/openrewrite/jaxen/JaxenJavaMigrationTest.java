package com.huawei.clouds.openrewrite.jaxen;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
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
import static org.openrewrite.java.Assertions.java;

class JaxenJavaMigrationTest implements RewriteTest {
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS = new PrintOutputCapture.MarkerPrinter() {
        @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
        @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
        @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("jaxen"))
                .markerPrinter(SILENT_MARKERS)
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesOfficiallyDocumentedXPathValueOfReplacement() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxenDeterministicJava()),
                java(
                        """
                        import org.jaxen.XPath;
                        class Query { String text(XPath xpath, Object node) throws Exception { return xpath.valueOf(node); } }
                        """,
                        """
                        import org.jaxen.XPath;
                        class Query { String text(XPath xpath, Object node) throws Exception { return xpath.stringValueOf(node); } }
                        """
                )
        );
    }

    @Test
    void migratesOfficiallyDocumentedNestedExceptionReplacement() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxenDeterministicJava()),
                java(
                        """
                        import org.jaxen.FunctionCallException;
                        class Failure { Throwable cause(FunctionCallException failure) { return failure.getNestedException(); } }
                        """,
                        """
                        import org.jaxen.FunctionCallException;
                        class Failure { Throwable cause(FunctionCallException failure) { return failure.getCause(); } }
                        """
                )
        );
    }

    @Test
    void leavesSameNamedApplicationMethodsUntouched() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxenDeterministicJava()),
                java("""
                        class Business {
                            String valueOf(Object value) { return value.toString(); }
                            Throwable getNestedException() { return null; }
                            void use() { valueOf(1); getNestedException(); }
                        }
                        """)
        );
    }

    @Test
    void autoMigrationPathFilterUsesParentComponentsOnly() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxenDeterministicJava()),
                java("import org.jaxen.XPath; class A { String x(XPath p,Object n)throws Exception{return p.valueOf(n);} }",
                        source -> source.path("generated/src/A.java")),
                java("import org.jaxen.XPath; class B { String x(XPath p,Object n)throws Exception{return p.valueOf(n);} }",
                        "import org.jaxen.XPath; class B { String x(XPath p,Object n)throws Exception{return p.stringValueOf(n);} }",
                        source -> source.path("install.java")),
                java("import org.jaxen.XPath; class C { String x(XPath p,Object n)throws Exception{return p.valueOf(n);} }",
                        "import org.jaxen.XPath; class C { String x(XPath p,Object n)throws Exception{return p.stringValueOf(n);} }",
                        source -> source.path("target.java"))
        );
    }

    @Test
    void deterministicJavaAutoIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxenDeterministicJava())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import org.jaxen.XPath; class Q { String q(XPath x,Object n)throws Exception{return x.valueOf(n);} }",
                        "import org.jaxen.XPath; class Q { String q(XPath x,Object n)throws Exception{return x.stringValueOf(n);} }")
        );
    }

    @Test
    void marksRemovedAndHiddenImplementationTypes() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.jaxen.expr.DefaultXPathExpr;
                        import org.jaxen.pattern.AnyChildNodeTest;
                        import org.jaxen.util.LinkedIterator;
                        class Internals { DefaultXPathExpr expression; AnyChildNodeTest test; LinkedIterator iterator; }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 3, "removed or made package-private")))
        );
    }

    @Test
    void marksOfficialDomXPathBehaviorFixture() {
        // Reduced from jaxen-xpath/jaxen@21b6f5f4a85c61964e79eabadbf32451fa2a14ec,
        // core/src/java/test/org/jaxen/test/BaseXPathTest.java.
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.jaxen.dom.DOMXPath;
                        class Query { DOMXPath query() throws Exception { return new DOMXPath("(//item)[@class='a'][@type='x']"); } }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1,
                                "number parsing", "chained predicates", "DOM fragment")))
        );
    }

    @Test
    void marksExternalObjectModelEngineAndOptionalDependency() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.jaxen.dom4j.Dom4jXPath;
                        class Query { Dom4jXPath query() throws Exception { return new Dom4jXPath("//order"); } }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1,
                                "model libraries became optional")))
        );
    }

    @Test
    void marksCustomNamespaceContextAndSetter() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.jaxen.NamespaceContext;
                        import org.jaxen.XPath;
                        class Namespaces implements NamespaceContext {
                            public String translateNamespacePrefixToUri(String prefix) { return "urn:" + prefix; }
                            void configure(XPath xpath) { xpath.setNamespaceContext(this); }
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2,
                                "Custom Jaxen namespace/function/variable context")))
        );
    }

    @Test
    void marksAnonymousVariableContextAndSetter() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.jaxen.UnresolvableException;
                        import org.jaxen.VariableContext;
                        import org.jaxen.XPath;
                        class Variables { void configure(XPath xpath) {
                            xpath.setVariableContext(new VariableContext() {
                                public Object getVariableValue(String uri, String prefix, String name) throws UnresolvableException { return 1; }
                            });
                        } }
                        """, source -> source.afterRecipe(after -> assertTrue(markDescriptions(after).stream()
                                .anyMatch(message -> message.contains("variable context")))))
        );
    }

    @Test
    void marksDocumentLoadingAndExceptionHandling() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.jaxen.FunctionCallException;
                        import org.jaxen.Navigator;
                        class Documents {
                            Object load(Navigator navigator, String uri) throws FunctionCallException {
                                try { return navigator.getDocument(uri); }
                                catch (FunctionCallException failure) { throw failure; }
                            }
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2,
                                "document() loading", "exception handling")))
        );
    }

    @Test
    void marksSerializationOfJaxenXPathOnly() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import java.io.ObjectOutputStream;
                        import org.jaxen.XPath;
                        class Store { void save(ObjectOutputStream out, XPath xpath, String text) throws Exception {
                            out.writeObject(xpath);
                            out.writeObject(text);
                        } }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1, "serialized")))
        );
    }

    @Test
    void marksExactReflectionNamesButLeavesBusinessTextAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        class Reflection {
                            String removed = "org.jaxen.util.StackedIterator";
                            String engine = "org.jaxen.dom.DOMXPath";
                            String business = "load org.jaxen.dom.DOMXPath when enabled";
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2,
                                "removed or made package-private", "edge behavior")))
        );
    }

    @Test
    void leavesUnrelatedContextsEnginesExceptionsAndSerializationUntouched() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks()),
                java("""
                        import java.io.ObjectOutputStream;
                        class Ordinary {
                            static class DOMXPath { DOMXPath(String expression) {} }
                            static class NamespaceContext {}
                            void run(ObjectOutputStream out) throws Exception {
                                new DOMXPath("number(+1)");
                                out.writeObject("text");
                            }
                        }
                        """)
        );
    }

    @Test
    void JavaRiskMarkersAreIdempotentAndGeneratedPathsAreSkipped() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenJavaMigrationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import org.jaxen.dom.DOMXPath; class A { DOMXPath x()throws Exception{return new DOMXPath(\"//a\");} }",
                        source -> source.path("install.java").afterRecipe(after -> assertMarks(after, 1, "edge behavior"))),
                java("import org.jaxen.dom.DOMXPath; class B { DOMXPath x()throws Exception{return new DOMXPath(\"//a\");} }",
                        source -> source.path("target/generated/B.java").afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    private static void assertMarks(SourceFile source, int expected, String... fragments) {
        List<String> descriptions = markDescriptions(source);
        assertEquals(expected, descriptions.size(), descriptions.toString());
        for (String fragment : fragments) {
            assertTrue(descriptions.stream().anyMatch(message -> message != null && message.contains(fragment)),
                    () -> "Missing '" + fragment + "' in " + descriptions);
        }
    }

    private static List<String> markDescriptions(SourceFile source) {
        List<String> descriptions = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                tree.getMarkers().findAll(SearchResult.class).stream()
                        .map(SearchResult::getDescription).forEach(descriptions::add);
                return tree;
            }
        }.visit(source, 0);
        return descriptions;
    }
}
