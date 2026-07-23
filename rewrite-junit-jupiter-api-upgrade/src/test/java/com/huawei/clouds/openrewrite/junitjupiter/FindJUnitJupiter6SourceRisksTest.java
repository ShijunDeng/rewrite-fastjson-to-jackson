package com.huawei.clouds.openrewrite.junitjupiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJUnitJupiter6SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterTestApi.sources()))
                .recipe(new FindJUnitJupiter6SourceRisks());
    }

    @ParameterizedTest(name = "pre-17 condition {0}")
    @MethodSource("oldConditions")
    void marksEveryPre17Condition(String label, String importName, String annotation) {
        String source = "import org.junit.jupiter.api.condition." + importName + ";\n" +
                        "import org.junit.jupiter.api.condition.JRE;\n" +
                        "class Tests {\n    " + annotation + "\n    void test() {}\n}\n";
        String expected = source.replace(annotation, marker(FindJUnitJupiter6SourceRisks.JRE) + annotation);
        rewriteRun(java(source, expected));
    }

    static Stream<Arguments> oldConditions() {
        return Stream.of(
                Arguments.of("enabled Java 8", "EnabledOnJre", "@EnabledOnJre(JRE.JAVA_8)"),
                Arguments.of("disabled Java 11", "DisabledOnJre", "@DisabledOnJre(JRE.JAVA_11)"),
                Arguments.of("enabled numeric 16", "EnabledOnJre", "@EnabledOnJre(versions = 16)"),
                Arguments.of("disabled numeric array", "DisabledOnJre", "@DisabledOnJre(versions = { 8, 17 })"),
                Arguments.of("enabled range", "EnabledForJreRange", "@EnabledForJreRange(min = JRE.JAVA_9, max = JRE.JAVA_21)"),
                Arguments.of("disabled range", "DisabledForJreRange", "@DisabledForJreRange(min = JRE.JAVA_14)"),
                Arguments.of("enabled numeric range", "EnabledForJreRange", "@EnabledForJreRange(minVersion = 11, maxVersion = 21)"),
                Arguments.of("disabled numeric range", "DisabledForJreRange", "@DisabledForJreRange(maxVersion = 16)")
        );
    }

    @Test
    void Java17And18ConditionsAreNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;
                  class Tests {
                      @EnabledOnJre({JRE.JAVA_17, JRE.JAVA_18})
                      void test() {}
                  }
                  """));
    }

    @Test
    void commentsMentioningOldJresDoNotCreateAConditionRisk() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;
                  class Tests {
                      @EnabledOnJre(/* JAVA_8 is documented here only */ JRE.JAVA_17)
                      void test() {}
                  }
                  """));
    }

    @Test
    void marksCsvSourceFastCsvReview() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvSource;
                  class Tests {
                      @CsvSource(value = {"'foo'INVALID,bar"}, useHeadersInDisplayName = true)
                      void test(String first, String second) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvSource;
                  class Tests {
                      /*~~(JUnit 6 uses FastCSV: malformed quoting, characters after a closing quote, headers, whitespace/null handling, exception types/messages, and parameterized display names may change. Re-run this data set and review assertions)~~>*/@CsvSource(value = {"'foo'INVALID,bar"}, useHeadersInDisplayName = true)
                      void test(String first, String second) {}
                  }
                  """));
    }

    @Test
    void marksCsvFileLineSeparatorAtAnnotation() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      @CsvFileSource(resources = "/data.csv", lineSeparator = "|")
                      void test(String first) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      /*~~(CsvFileSource.lineSeparator was removed in JUnit 6; it now auto-detects CR, LF, or CRLF. Remove the attribute only after confirming the referenced resource uses one of those separators)~~>*/@CsvFileSource(resources = "/data.csv", lineSeparator = "|")
                      void test(String first) {}
                  }
                  """));
    }

    @Test
    void marksCsvFileWithoutRemovedAttributeForParserReview() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      @CsvFileSource(resources = "/data.csv")
                      void test(String first) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      /*~~(JUnit 6 uses FastCSV: malformed quoting, characters after a closing quote, headers, whitespace/null handling, exception types/messages, and parameterized display names may change. Re-run this data set and review assertions)~~>*/@CsvFileSource(resources = "/data.csv")
                      void test(String first) {}
                  }
                  """));
    }

    @Test
    void lineSeparatorTextInsideAnotherArgumentDoesNotImpersonateTheRemovedAttribute() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      @CsvFileSource(resources = "/lineSeparator.csv")
                      void test(String first) {}
                  }
                  """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains(FindJUnitJupiter6SourceRisks.CSV), out);
                    assertFalse(out.contains(FindJUnitJupiter6SourceRisks.LINE_SEPARATOR), out);
                })));
    }

    @Test
    void marksNestedOrdering() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.Nested;
                  class Outer {
                      @Nested
                      class First {}
                  }
                  """,
                """
                  import org.junit.jupiter.api.Nested;
                  class Outer {
                      /*~~(JUnit 6 deterministically reorders sibling @Nested classes and inherits @TestMethodOrder into nested classes; review stateful/order-sensitive tests and add explicit orderers where order is contractual)~~>*/@Nested
                      class First {}
                  }
                  """));
    }

    @Test
    void marksNullableExpressionCreator() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) { return store.computeIfAbsent("x", key -> null); }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) { return /*~~(JUnit 6 computeIfAbsent contracts require a non-null created value and expose JSpecify nullness; this creator can return null, so define an explicit absence strategy before enabling nullness checks)~~>*/store.computeIfAbsent("x", key -> null); }
                  }
                  """));
    }

    @Test
    void marksNullableBlockCreator() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return store.computeIfAbsent("x", key -> { if (key == null) return new Object(); return null; });
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return /*~~(JUnit 6 computeIfAbsent contracts require a non-null created value and expose JSpecify nullness; this creator can return null, so define an explicit absence strategy before enabling nullness checks)~~>*/store.computeIfAbsent("x", key -> { if (key == null) return new Object(); return null; });
                      }
                  }
                  """));
    }

    @Test
    void nonNullCreatorIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension { Object value(ExtensionContext.Store store) { return store.computeIfAbsent("x", key -> new Object()); } }
                  """));
    }

    @Test
    void nullTextAndNestedLambdaDoNotImpersonateANullCreatorResult() {
        rewriteRun(java(
                """
                  import java.util.function.Function;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object text(ExtensionContext.Store store) {
                          return store.computeIfAbsent("key -> null", key -> "return null;");
                      }
                      Object nested(ExtensionContext.Store store) {
                          return store.computeIfAbsent("x", key -> (Function<Object, Object>) nested -> null);
                      }
                  }
                  """));
    }

    @Test
    void marksCustomStoreImplementation() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class FakeStore implements ExtensionContext.Store {}
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  /*~~(This custom ExtensionContext.Store implementation must implement/verify the JUnit 6 computeIfAbsent family, non-null contracts, ancestor lookup, and AutoCloseable resource lifecycle)~~>*/class FakeStore implements ExtensionContext.Store {}
                  """));
    }

    @Test
    void sameNamedBusinessAnnotationsAreNoop() {
        rewriteRun(java("@interface Nested {} @interface CsvSource {} @Nested @CsvSource class Business {}"));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.Nested;
                  @Nested class Generated {}
                  """, source -> source.path("target/generated/Generated.java")));
    }

    private static String marker(String message) {
        return "/*~~(" + message + ")~~>*/";
    }
}
