package com.huawei.clouds.openrewrite.hikaricp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Marks HikariCP 6 compatibility choices that cannot be decided from syntax alone. */
public final class FindHikari6MigrationRisks extends Recipe {
    private static final String SQL_EXCEPTION_OVERRIDE = "com.zaxxer.hikari.SQLExceptionOverride";
    private static final String CONFIG_MXBEAN = "com.zaxxer.hikari.HikariConfigMXBean";
    private static final String LEGACY_CREDENTIAL_PROPERTY =
            "com.zaxxer.hikari.legacy.supportUserPassDataSourceOverride";

    @Override
    public String getDisplayName() {
        return "Find HikariCP 6 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark custom SQLExceptionOverride policies, direct HikariConfigMXBean implementations, " +
               "anonymous MXBean implementations, and the temporary legacy credential system property for review.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (!UpgradeSelectedHikariCPDependency.isProjectPath(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (directlyImplements(c, SQL_EXCEPTION_OVERRIDE)) {
                    return SearchResult.found(c,
                            "HikariCP 6.2 no longer evicts SQLTimeoutException by default; decide whether this policy must return MUST_EVICT");
                }
                if (directlyImplements(c, CONFIG_MXBEAN) && !declaresMethod(c, "setCredentials", 1)) {
                    return SearchResult.found(c,
                            "HikariCP 6 adds HikariConfigMXBean.setCredentials(Credentials); implement an atomic credential update");
                }
                return c;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (n.getBody() != null && TypeUtils.isOfClassType(n.getType(), CONFIG_MXBEAN)) {
                    return SearchResult.found(n,
                            "Anonymous HikariConfigMXBean must implement setCredentials(Credentials) on HikariCP 6");
                }
                return n;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (LEGACY_CREDENTIAL_PROPERTY.equals(l.getValue())) {
                    return SearchResult.found(l,
                            "Temporary HikariCP legacy credential override detected; migrate the DataSource subclass to getCredentials()");
                }
                return l;
            }
        };
    }

    private static boolean directlyImplements(J.ClassDeclaration classDecl, String typeName) {
        return classDecl.getImplements() != null && classDecl.getImplements().stream()
                .anyMatch(implemented -> TypeUtils.isOfClassType(implemented.getType(), typeName));
    }

    private static boolean declaresMethod(J.ClassDeclaration classDecl, String name, int parameterCount) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .anyMatch(method -> name.equals(method.getSimpleName()) &&
                                    method.getParameters().size() == parameterCount);
    }
}
