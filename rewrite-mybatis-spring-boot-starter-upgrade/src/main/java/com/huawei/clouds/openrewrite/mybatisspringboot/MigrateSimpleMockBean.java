package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

/** Migrates the no-attribute MockBean form used by the official starter sample. */
public final class MigrateSimpleMockBean extends Recipe {
    private static final String OLD_TYPE = "org.springframework.boot.test.mock.mockito.MockBean";
    private static final String NEW_TYPE = "org.springframework.test.context.bean.override.mockito.MockitoBean";

    @Override
    public String getDisplayName() {
        return "Migrate simple Spring Boot MockBean annotations";
    }

    @Override
    public String getDescription() {
        return "Replace no-attribute @MockBean declarations with Spring Framework @MockitoBean; annotations with " +
               "attributes are left for review because the attribute sets are not equivalent.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final AnnotationMatcher mockBean = new AnnotationMatcher("@" + OLD_TYPE);
            private final JavaTemplate replacement = JavaTemplate.builder("@MockitoBean")
                    .imports(NEW_TYPE)
                    .javaParser(JavaParser.fromJavaVersion().dependsOn(
                            "package org.springframework.test.context.bean.override.mockito; " +
                            "public @interface MockitoBean {}"
                    ))
                    .build();

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!mockBean.matches(a) || a.getArguments() != null && !a.getArguments().isEmpty()) {
                    return a;
                }
                maybeRemoveImport(OLD_TYPE);
                maybeAddImport(NEW_TYPE);
                return replacement.apply(updateCursor(a), a.getCoordinates().replace());
            }
        };
    }
}
