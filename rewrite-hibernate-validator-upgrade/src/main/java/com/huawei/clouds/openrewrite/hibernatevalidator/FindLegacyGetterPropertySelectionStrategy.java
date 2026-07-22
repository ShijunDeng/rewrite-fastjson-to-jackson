package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Finds the Hibernate Validator 6/7 getter SPI signature whose return type changed in 8. */
public final class FindLegacyGetterPropertySelectionStrategy extends Recipe {
    private static final String SPI =
            "org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy";
    private static final String MESSAGE =
            "Hibernate Validator 8 changes this SPI return type from Set<String> to List<String>; " +
            "update the implementation and preserve candidate order";

    @Override
    public String getDisplayName() {
        return "Find legacy GetterPropertySelectionStrategy implementations";
    }

    @Override
    public String getDescription() {
        return "Mark only Hibernate Validator GetterPropertySelectionStrategy implementations that still " +
               "declare the Hibernate Validator 6/7 Set<String> return contract.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                JavaType.Method methodType = m.getMethodType();
                if (owner == null || !TypeUtils.isAssignableTo(SPI, owner.getType()) || methodType == null ||
                    !"getGetterMethodNameCandidates".equals(m.getSimpleName()) ||
                    methodType.getParameterTypes().size() != 1 ||
                    !TypeUtils.isOfClassType(methodType.getParameterTypes().get(0), "java.lang.String") ||
                    !TypeUtils.isOfClassType(methodType.getReturnType(), "java.util.Set")) {
                    return m;
                }
                return SearchResult.found(m, MESSAGE);
            }
        };
    }
}
