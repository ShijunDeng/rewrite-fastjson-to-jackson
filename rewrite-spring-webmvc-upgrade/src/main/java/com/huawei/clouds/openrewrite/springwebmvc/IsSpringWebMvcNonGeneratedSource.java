package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Scope official Spring migration recipes to source owned by the application. */
public final class IsSpringWebMvcNonGeneratedSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Check for an application-owned Spring Web MVC source";
    }

    @Override
    public String getDescription() {
        return "Use as a precondition so official Spring migration recipes skip generated and cache trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }
}
