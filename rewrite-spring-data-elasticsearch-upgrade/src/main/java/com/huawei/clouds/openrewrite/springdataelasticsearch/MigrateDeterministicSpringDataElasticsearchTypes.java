package com.huawei.clouds.openrewrite.springdataelasticsearch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Applies only official one-to-one public type moves whose target API is present in 6.0.5. */
public final class MigrateDeterministicSpringDataElasticsearchTypes extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Spring Data Elasticsearch public type moves";
    }

    @Override
    public String getDescription() {
        return "Migrates NativeQuery, Range, Queries, completion, property-converter, and RuntimeField public " +
               "types whose 6.0.5 replacements preserve the represented abstraction.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(change("org.springframework.data.elasticsearch.core.query.NativeSearchQuery",
                        "org.springframework.data.elasticsearch.client.elc.NativeQuery")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder",
                        "org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.core.Range",
                        "org.springframework.data.domain.Range")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.ELCQueries",
                        "org.springframework.data.elasticsearch.client.elc.Queries")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.client.elc.QueryBuilders",
                        "org.springframework.data.elasticsearch.client.elc.Queries")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.core.completion.Completion",
                        "org.springframework.data.elasticsearch.core.suggest.Completion")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentPropertyConverter",
                        "org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter")),
                projectSourcesOnly(change("org.springframework.data.elasticsearch.core.RuntimeField",
                        "org.springframework.data.elasticsearch.core.query.RuntimeField"))
        );
    }

    private static ChangeType change(String oldType, String newType) {
        return new ChangeType(oldType, newType, true);
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
                               !UpgradeSelectedSpringDataElasticsearchDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
