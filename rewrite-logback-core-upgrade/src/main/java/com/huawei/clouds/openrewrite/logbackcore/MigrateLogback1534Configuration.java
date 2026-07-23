package com.huawei.clouds.openrewrite.logbackcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.tree.Xml;

import java.util.List;
import java.util.regex.Pattern;

/** Apply exact official class renames in owned Logback XML configuration files. */
public final class MigrateLogback1534Configuration extends Recipe {
    private static final List<Recipe> MIGRATIONS = List.of(
            exactClassRename(
                    "ch.qos.logback.core.hook.DelayingShutdownHook",
                    "ch.qos.logback.core.hook.DefaultShutdownHook"),
            exactClassRename(
                    "ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP",
                    "ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy")
    );

    static List<Recipe> officialCoreRecipes() {
        return MIGRATIONS;
    }

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Logback Core 1.5.34 XML class names";
    }

    @Override
    public String getDescription() {
        return "Rename the official DelayingShutdownHook and SizeAndTimeBasedFNATP class attributes while " +
               "preserving every surrounding rolling and lifecycle option.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) ||
                    !(tree instanceof SourceFile source) ||
                    UpgradeSelectedLogbackCoreDependency.generated(source.getSourcePath()) ||
                    !logbackConfiguration(document)) return tree;
                Tree migrated = document;
                for (Recipe migration : MIGRATIONS) {
                    migrated = migration.getVisitor().visitNonNull(migrated, ctx);
                }
                return migrated;
            }
        };
    }

    private static Recipe exactClassRename(String oldType, String newType) {
        return new ChangeTagAttribute(
                "//*", "class", newType, Pattern.quote(oldType), true);
    }

    static boolean logbackConfiguration(Xml.Document document) {
        String file = document.getSourcePath().getFileName().toString().toLowerCase();
        if (file.endsWith(".xml") && file.contains("logback")) return true;
        return "configuration".equals(document.getRoot().getName()) &&
               document.printAll().contains("ch.qos.logback");
    }
}
