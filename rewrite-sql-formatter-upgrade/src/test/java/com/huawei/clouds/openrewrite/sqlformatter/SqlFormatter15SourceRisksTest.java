package com.huawei.clouds.openrewrite.sqlformatter;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SqlFormatter15SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSqlFormatter15SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("sql-formatter"));
    }

    @Test
    void marksStaticFormatAtExactInvocation() {
        rewriteRun(java(
                "import com.github.vertical_blank.sqlformatter.SqlFormatter; class F { String f(String sql) { return SqlFormatter.format(sql); } }",
                "import com.github.vertical_blank.sqlformatter.SqlFormatter; class F { String f(String sql) { return /*~~(%s)~~>*/SqlFormatter.format(sql); } }"
                        .formatted(FindSqlFormatter15SourceRisks.OUTPUT)));
    }

    @Test
    void marksDialectSelectionAndFormattingSeparately() {
        rewriteRun(java(
                """
                import com.github.vertical_blank.sqlformatter.SqlFormatter;
                import com.github.vertical_blank.sqlformatter.languages.Dialect;
                class F { String f(String sql) { return SqlFormatter.of(Dialect.MySql).format(sql); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, FindSqlFormatter15SourceRisks.DIALECT);
                    assertContains(out, FindSqlFormatter15SourceRisks.OUTPUT);
                })));
    }

    @Test
    void marksDefaultDialectFactory() {
        rewriteRun(java(
                "import com.github.vertical_blank.sqlformatter.SqlFormatter; class F { Object f() { return SqlFormatter.standard(); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSqlFormatter15SourceRisks.DIALECT))));
    }

    @Test
    void marksAllFormatConfigSemantics() {
        rewriteRun(java(
                """
                import com.github.vertical_blank.sqlformatter.core.FormatConfig;
                import java.util.Arrays;
                class C { FormatConfig c() { return FormatConfig.builder().indent("    ").maxColumnLength(100)
                    .uppercase(true).linesBetweenQueries(2).params(Arrays.asList("a", "b")).build(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertCount(out, FindSqlFormatter15SourceRisks.CONFIG, 7);
                    assertContains(out, FindSqlFormatter15SourceRisks.JAVA_API);
                })));
    }

    @Test
    void marksFormatterExtension() {
        rewriteRun(java(
                """
                import com.github.vertical_blank.sqlformatter.SqlFormatter;
                import com.github.vertical_blank.sqlformatter.languages.Dialect;
                class E { Object e() { return SqlFormatter.of(Dialect.MySql).extend(cfg -> cfg.plusOperators("=>")); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSqlFormatter15SourceRisks.EXTENSION))));
    }

    @Test
    void marksRealStarRocksStoredFormatterBoundary() {
        // Reduced from StarRocks/starrocks@aab21898c1cb0991e261dfb0bbf43f78969c0633.
        rewriteRun(java(
                """
                import com.github.vertical_blank.sqlformatter.SqlFormatter;
                import com.github.vertical_blank.sqlformatter.languages.Dialect;
                class QueryProfileAction {
                    private static final SqlFormatter.Formatter MYSQL_FORMATTER = SqlFormatter.of(Dialect.MySql);
                    String format(String sql) { return MYSQL_FORMATTER.format(sql); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, FindSqlFormatter15SourceRisks.JAVA_API);
                    assertContains(out, FindSqlFormatter15SourceRisks.DIALECT);
                    assertContains(out, FindSqlFormatter15SourceRisks.OUTPUT);
                })));
    }

    @Test
    void marksRealBeeExtDialectCachePattern() {
        // Reduced from automvc/bee-ext@6bfc6f2f654adcb9b27b9b1d82bd3029f8a40825.
        rewriteRun(java(
                """
                import com.github.vertical_blank.sqlformatter.SqlFormatter;
                import com.github.vertical_blank.sqlformatter.languages.Dialect;
                class BeeSqlFormatter { String f(String sql, boolean mysql) {
                    SqlFormatter.Formatter formatter = mysql ? SqlFormatter.of(Dialect.MySql) : SqlFormatter.standard();
                    return formatter.format(sql);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), FindSqlFormatter15SourceRisks.DIALECT, 2);
                    assertContains(after.printAll(), FindSqlFormatter15SourceRisks.OUTPUT);
                })));
    }

    @Test
    void marksRealSqluckyStaticFormattingPattern() {
        // Reduced from tenie/SQLucky@799e5c09b8f2b4842b3f5498ccce54f0079a3d60.
        rewriteRun(java(
                "import com.github.vertical_blank.sqlformatter.SqlFormatter; class SqlUtils { String format(String sql) { return SqlFormatter.format(sql); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSqlFormatter15SourceRisks.OUTPUT))));
    }

    @Test
    void marksRealDatacapDialectPattern() {
        // Reduced from devlive-community/datacap@e0d081d01bb0297732ab2e8a4a5f192c061c6fb4.
        rewriteRun(java(
                """
                import com.github.vertical_blank.sqlformatter.SqlFormatter;
                import com.github.vertical_blank.sqlformatter.languages.Dialect;
                class FormatServiceImpl { String formatterSql(String sql) { return SqlFormatter.of(Dialect.MySql).format(sql); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindSqlFormatter15SourceRisks.DIALECT);
                    assertContains(after.printAll(), FindSqlFormatter15SourceRisks.OUTPUT);
                })));
    }

    @Test
    void marksPublicMethodThatLeaksFormatterType() {
        rewriteRun(java(
                "import com.github.vertical_blank.sqlformatter.SqlFormatter; class Api { SqlFormatter.Formatter formatter() { return SqlFormatter.standard(); } }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindSqlFormatter15SourceRisks.JAVA_API);
                    assertContains(after.printAll(), FindSqlFormatter15SourceRisks.DIALECT);
                })));
    }

    @Test
    void sameNamedApplicationApisAreNoop() {
        rewriteRun(java("""
                class SqlFormatter { static SqlFormatter standard() { return new SqlFormatter(); }
                    static String format(String s) { return s; } SqlFormatter extend(Object x) { return this; } }
                class Use { String x(String s) { return SqlFormatter.format(s); } }
                """, source -> source.afterRecipe(after -> {
                    String out = after.printAll();
                    assertFalse(out.contains("/*~~("), out);
                })));
    }

    @Test
    void generatedInstallAndCachesAreNoop() {
        rewriteRun(
                java("import com.github.vertical_blank.sqlformatter.SqlFormatter; class GeneratedF { String f(String s){ return SqlFormatter.format(s); } }", spec -> spec.path("generated-code/GeneratedF.java")),
                java("import com.github.vertical_blank.sqlformatter.SqlFormatter; class InstalledF { String f(String s){ return SqlFormatter.format(s); } }", spec -> spec.path("installation/lib/InstalledF.java")),
                java("import com.github.vertical_blank.sqlformatter.SqlFormatter; class CachedF { String f(String s){ return SqlFormatter.format(s); } }", spec -> spec.path(".m2/cache/CachedF.java")));
    }

    @Test
    void installLeafFilenameIsStillMarkedAndIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import com.github.vertical_blank.sqlformatter.SqlFormatter; class install { String f(String s){ return SqlFormatter.format(s); } }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSqlFormatter15SourceRisks.OUTPUT))));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
