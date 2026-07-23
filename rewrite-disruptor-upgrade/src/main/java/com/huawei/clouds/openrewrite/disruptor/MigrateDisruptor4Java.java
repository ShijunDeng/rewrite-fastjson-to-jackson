package com.huawei.clouds.openrewrite.disruptor;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Deterministic Java migrations backed by the fixed Disruptor 3.4.4-to-4.0.0 source diff. */
public final class MigrateDisruptor4Java extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Disruptor 4 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Use the BatchEventProcessor builder and fold removed handler extension interfaces into EventHandler " +
               "while excluding generated and installation trees.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangeType(
                        "com.lmax.disruptor.SequenceReportingEventHandler",
                        "com.lmax.disruptor.EventHandler", true)),
                projectSourcesOnly(new FoldDisruptorEventHandlerExtensions()),
                projectSourcesOnly(new MigrateBatchEventProcessorConstruction())
        );
    }

    private static Recipe projectSourcesOnly(Recipe delegate) {
        return new Recipe() {
            @Override
            public String getDisplayName() {
                return delegate.getDisplayName();
            }

            @Override
            public String getDescription() {
                return delegate.getDescription();
            }

            @Override
            public TreeVisitor<?, ExecutionContext> getVisitor() {
                return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree visit(Tree tree, ExecutionContext ctx) {
                        return tree instanceof SourceFile source &&
                               !UpgradeSelectedDisruptorDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
