package com.huawei.clouds.openrewrite.nettyhandler;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/**
 * Limits official building-block recipes to authored inputs when callers explicitly supply
 * generated output to OpenRewrite.
 */
public final class FindAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Netty migration source files";
    }

    @Override
    public String getDescription() {
        return "Find source files outside generated, build, cache and installation directories before applying " +
               "official OpenRewrite Netty migration building blocks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !NettyHandlerSupport.generated(source.getSourcePath())) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }
}
