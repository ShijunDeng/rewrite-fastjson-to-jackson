package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/** Prevent official source migrations from rewriting generated or cached Java files. */
public final class FindAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Java source files";
    }

    @Override
    public String getDescription() {
        return "Match Java source outside generated, build, cache, vendor and report directories.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return SpringRetrySupport.generated(cu.getSourcePath()) ? cu : SearchResult.found(cu);
            }
        };
    }
}
