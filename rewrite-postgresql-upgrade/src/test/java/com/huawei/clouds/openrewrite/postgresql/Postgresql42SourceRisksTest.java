package com.huawei.clouds.openrewrite.postgresql;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class Postgresql42SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindPostgresql42SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("postgresql"));
    }

    @Test
    void marksLiteralPostgresqlDriverManagerUrlOnly() {
        rewriteRun(java(
                """
                import java.sql.*;
                class Connect { void open() throws Exception {
                    DriverManager.getConnection("jdbc:postgresql://db/app", "u", "p");
                    DriverManager.getConnection("jdbc:mysql://db/app", "u", "p");
                } }
                """,
                """
                import java.sql.*;
                class Connect { void open() throws Exception {
                    /*~~(%s)~~>*/DriverManager.getConnection("jdbc:postgresql://db/app", "u", "p");
                    DriverManager.getConnection("jdbc:mysql://db/app", "u", "p");
                } }
                """.formatted(FindPostgresql42SourceRisks.URL)));
    }

    @Test
    void marksPgPropertyAtExactCalls() {
        rewriteRun(java(
                """
                import java.util.Properties;
                import org.postgresql.PGProperty;
                class Props { void configure(Properties p) throws Exception {
                    PGProperty.SSL_MODE.set(p, "verify-full");
                    PGProperty.REWRITE_BATCHED_INSERTS.set(p, true);
                    PGProperty.AUTOSAVE.get(p);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(count(out, FindPostgresql42SourceRisks.PROPERTY) == 3, out);
                })));
    }

    @Test
    void marksDataSourceAndXaConstructionAndSetters() {
        rewriteRun(java(
                """
                import org.postgresql.ds.PGSimpleDataSource;
                import org.postgresql.xa.PGXADataSource;
                class Pools { void configure() {
                    PGSimpleDataSource simple = new PGSimpleDataSource();
                    simple.setServerNames(new String[] {"primary", "standby"});
                    simple.setSslMode("verify-full");
                    PGXADataSource xa = new PGXADataSource();
                    xa.setUrl("jdbc:postgresql://db/app");
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(count(out, FindPostgresql42SourceRisks.DATASOURCE) >= 5, out);
                })));
    }

    @Test
    void marksCopyLobReplicationAndNotificationsByExtensionEntryPoint() {
        rewriteRun(java(
                """
                import org.postgresql.PGConnection;
                class Extensions { void use(PGConnection c) throws Exception {
                    c.getCopyAPI();
                    c.getLargeObjectAPI();
                    c.getReplicationAPI();
                    c.getNotifications(1000);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains(FindPostgresql42SourceRisks.COPY), out);
                    assertTrue(out.contains(FindPostgresql42SourceRisks.LOB), out);
                    assertTrue(count(out, FindPostgresql42SourceRisks.REPLICATION) == 2, out);
                })));
    }

    @Test
    void marksStatementTuningCalls() {
        rewriteRun(java(
                """
                import org.postgresql.PGStatement;
                class Tune { void configure(PGStatement s) throws Exception {
                    s.setPrepareThreshold(5);
                    s.setAdaptiveFetch(true);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(count(after.printAll(), FindPostgresql42SourceRisks.STATEMENT) == 2,
                                after.printAll()))));
    }

    @Test
    void marksUpdatableResultSetRequestButNotReadOnlyRequest() {
        rewriteRun(java(
                """
                import java.sql.*;
                class Results { void query(Connection c) throws Exception {
                    c.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    c.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(count(after.printAll(), FindPostgresql42SourceRisks.UPDATABLE) == 1,
                                after.printAll()))));
    }

    @Test
    void marksOtherPgConnectionExtensionOperations() {
        rewriteRun(java(
                """
                import org.postgresql.PGConnection;
                class Pg { void use(PGConnection c) throws Exception {
                    c.cancelQuery();
                    c.setPrepareThreshold(4);
                    c.escapeIdentifier("order");
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(count(after.printAll(), FindPostgresql42SourceRisks.EXTENSION) == 3,
                                after.printAll()))));
    }

    @Test
    void marksInternalImportsAndPublishedImportStaysClean() {
        rewriteRun(java(
                """
                import org.postgresql.PGConnection;
                import org.postgresql.core.BaseConnection;
                class Internal { PGConnection good; BaseConnection risky; }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(count(out, FindPostgresql42SourceRisks.INTERNAL) == 1, out);
                    assertFalse(out.contains("/*~~(" + FindPostgresql42SourceRisks.INTERNAL + ")~~>*/import org.postgresql.PGConnection"), out);
                })));
    }

    @Test
    void typeAttributionRejectsSameNamedApplicationApis() {
        rewriteRun(java(
                """
                class PGProperty { void set(Object p, Object v) {} }
                class PGConnection { void getCopyAPI() {} }
                class SameNames { void use(PGProperty p, PGConnection c) {
                    p.set(new Object(), true); c.getCopyAPI();
                } }
                """));
    }

    @Test
    void generatedParentsAreSkippedButLeafNameIsOwned() {
        rewriteRun(
                java("import java.sql.*; class A { void x() throws Exception { DriverManager.getConnection(\"jdbc:postgresql:x\"); } }",
                        source -> source.path("generated-sources/A.java")),
                java("import java.sql.*; class B { void x() throws Exception { DriverManager.getConnection(\"jdbc:postgresql:x\"); } }",
                        source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindPostgresql42SourceRisks.URL), after.printAll()))));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import java.sql.*; class Idempotent { void x() throws Exception { DriverManager.getConnection(\"jdbc:postgresql:x\"); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(count(after.printAll(), FindPostgresql42SourceRisks.URL) == 1,
                                after.printAll()))));
    }

    private static int count(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
