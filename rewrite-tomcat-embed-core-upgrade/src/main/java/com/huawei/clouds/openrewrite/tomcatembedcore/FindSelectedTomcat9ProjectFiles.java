package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Restrict Javax-to-Jakarta changes to projects upgrading from an approved Tomcat 9 source. */
public final class FindSelectedTomcat9ProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in selected Tomcat 9 projects";
    }

    @Override
    public String getDescription() {
        return "Select only files carrying a project marker whose exact pre-upgrade source belongs to Tomcat 9.";
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
                return source.getMarkers().findFirst(TomcatEmbedCoreProjectMarker.class)
                        .filter(marker -> marker.getSourceVersion().startsWith("9.0."))
                        .<Tree>map(marker -> SearchResult.found(source))
                        .orElse(tree);
            }
        };
    }
}
