package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

/** Adds the explicit Boot 4 test-rest-template auto-configuration after its package move. */
public final class AddAutoConfigureTestRestTemplate extends Recipe {
    private static final String TEST_REST_TEMPLATE = "org.springframework.boot.resttestclient.TestRestTemplate";
    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String AUTO_CONFIGURE =
            "org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate";

    @Override
    public String getDisplayName() {
        return "Configure Boot 4 TestRestTemplate explicitly";
    }

    @Override
    public String getDescription() {
        return "Add @AutoConfigureTestRestTemplate to @SpringBootTest classes that use the Boot 4 TestRestTemplate, " +
               "matching the target MyBatis starter samples.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(TEST_REST_TEMPLATE, false), new JavaIsoVisitor<ExecutionContext>() {
            private final AnnotationMatcher springBootTest = new AnnotationMatcher("@" + SPRING_BOOT_TEST);
            private final AnnotationMatcher autoConfigure = new AnnotationMatcher("@" + AUTO_CONFIGURE);
            private final JavaTemplate annotation = JavaTemplate.builder("@AutoConfigureTestRestTemplate")
                    .imports(AUTO_CONFIGURE)
                    .javaParser(JavaParser.fromJavaVersion().dependsOn(
                            "package org.springframework.boot.resttestclient.autoconfigure; " +
                            "public @interface AutoConfigureTestRestTemplate {}"
                    ))
                    .build();

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                boolean isSpringBootTest = c.getLeadingAnnotations().stream().anyMatch(springBootTest::matches);
                boolean alreadyConfigured = c.getLeadingAnnotations().stream().anyMatch(autoConfigure::matches);
                if (!isSpringBootTest || alreadyConfigured) {
                    return c;
                }
                maybeAddImport(AUTO_CONFIGURE);
                return annotation.apply(updateCursor(c), c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }
        });
    }
}
