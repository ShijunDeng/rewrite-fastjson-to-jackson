package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Moves the two starter-owned auto-configuration types while excluding generated source trees. */
public final class MigrateJasyptAutoConfigurationTypes extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Jasypt starter auto-configuration packages";
    }

    @Override
    public String getDescription() {
        return "Move the two starter auto-configuration types renamed to avoid a Java 9 split package, without " +
               "rewriting generated sources.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangeType(
                        "com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration",
                        "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration", false)),
                projectSourcesOnly(new ChangeType(
                        "com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration",
                        "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringCloudBootstrapConfiguration", false))
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
                               JasyptVersions.isProjectPath(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
