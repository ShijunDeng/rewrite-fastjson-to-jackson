package com.huawei.clouds.openrewrite.logbackcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.J;

import java.util.List;

/**
 * Apply only source changes that have an exact replacement in the fixed
 * v_1.2.5/v_1.2.9 to v_1.5.34 official source range.
 */
public final class MigrateLogback1534Java extends Recipe {
    private static final List<Recipe> MIGRATIONS = List.of(
            new ChangeType(
                    "ch.qos.logback.core.hook.DelayingShutdownHook",
                    "ch.qos.logback.core.hook.DefaultShutdownHook",
                    true),
            new ChangeType(
                    "ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP",
                    "ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy",
                    true),
            new ChangeType(
                    "ch.qos.logback.core.joran.action.ActionConst",
                    "ch.qos.logback.core.joran.JoranConstants",
                    true),
            new ChangeMethodName(
                    "ch.qos.logback.core.joran.spi.ConfigurationWatchList getMainURL()",
                    "getTopURL",
                    false,
                    true),
            new ChangeMethodName(
                    "ch.qos.logback.core.joran.spi.ConfigurationWatchList setMainURL(java.net.URL)",
                    "setTopURL",
                    false,
                    true)
    );

    static List<Recipe> officialCoreRecipes() {
        return MIGRATIONS;
    }

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Logback Core 1.5.34 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Apply the official shutdown-hook, rolling-policy, Joran-constant, and configuration-watch-list " +
               "renames only when Java type attribution proves the old Logback API.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit) ||
                    !(tree instanceof SourceFile source) ||
                    UpgradeSelectedLogbackCoreDependency.generated(source.getSourcePath())) return tree;
                Tree migrated = tree;
                for (Recipe migration : MIGRATIONS) {
                    migrated = migration.getVisitor().visitNonNull(migrated, ctx);
                }
                return migrated;
            }
        };
    }
}
