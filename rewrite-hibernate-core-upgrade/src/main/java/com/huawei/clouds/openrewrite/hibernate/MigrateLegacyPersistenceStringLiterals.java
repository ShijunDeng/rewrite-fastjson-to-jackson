package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/** Update JPA package and setting names held in Java string literals. */
public final class MigrateLegacyPersistenceStringLiterals extends Recipe {
    private static final String OLD_PREFIX = "javax.persistence.";
    private static final String NEW_PREFIX = "jakarta.persistence.";

    @Override
    public String getDisplayName() {
        return "Migrate legacy persistence names in Java string literals";
    }

    @Override
    public String getDescription() {
        return "Replace the Java EE javax.persistence prefix with the Jakarta jakarta.persistence prefix in Java string literals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedHibernateCoreDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value) || !value.contains(OLD_PREFIX)) {
                    return l;
                }
                String replacement = value.replace(OLD_PREFIX, NEW_PREFIX);
                String valueSource = l.getValueSource();
                return l.withValue(replacement).withValueSource(
                        valueSource == null ? null : valueSource.replace(OLD_PREFIX, NEW_PREFIX));
            }
        };
    }
}
