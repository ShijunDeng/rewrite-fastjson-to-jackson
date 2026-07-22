package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Runs deterministic Hibernate/Jakarta Java migrations only against maintained project sources. */
public final class MigrateDeterministicHibernateJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Hibernate 7 Java APIs in project sources";
    }

    @Override
    public String getDescription() {
        return "Migrate deterministic Jakarta packages, descriptor contracts, Session.load overloads, strings, " +
               "and cascade constants while leaving generated source trees untouched.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangePackage("javax.persistence", "jakarta.persistence", true)),
                projectSourcesOnly(new MigrateLegacyPersistenceStringLiterals()),
                projectSourcesOnly(new ChangeType(
                        "org.hibernate.type.descriptor.java.JavaTypeDescriptor",
                        "org.hibernate.type.descriptor.java.JavaType", null)),
                projectSourcesOnly(new ChangeType(
                        "org.hibernate.type.descriptor.sql.SqlTypeDescriptor",
                        "org.hibernate.type.descriptor.jdbc.JdbcType", null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.hibernate.Session load(java.lang.Class, java.io.Serializable)",
                        "getReference", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.hibernate.Session load(java.lang.String, java.io.Serializable)",
                        "getReference", null, null)),
                projectSourcesOnly(new ReplaceConstantWithAnotherConstant(
                        "org.hibernate.annotations.CascadeType.DELETE",
                        "org.hibernate.annotations.CascadeType.REMOVE"))
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
                               !UpgradeSelectedHibernateCoreDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
