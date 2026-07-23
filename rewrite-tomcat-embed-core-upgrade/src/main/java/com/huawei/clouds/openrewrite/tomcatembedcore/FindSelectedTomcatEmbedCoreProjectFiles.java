package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Project-level precondition tied to an exact pre-upgrade remediation source version. */
public final class FindSelectedTomcatEmbedCoreProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in selected Tomcat Embed Core projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest build root proved one exact, non-conflicting " +
               "Tomcat Embed Core remediation source version before dependency edits.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedTomcatEmbedCoreDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(TomcatEmbedCoreProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
