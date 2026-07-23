package com.huawei.clouds.openrewrite.springkafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Limit official source migrations to files owned by the application. */
public final class FindSpringKafkaAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find application-authored Spring Kafka source files";
    }

    @Override
    public String getDescription() {
        return "Use as a precondition so official Spring Kafka recipes skip generated, build, cache, and vendor trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !SpringKafkaUpgradeSupport.generated(source.getSourcePath())) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }
}
