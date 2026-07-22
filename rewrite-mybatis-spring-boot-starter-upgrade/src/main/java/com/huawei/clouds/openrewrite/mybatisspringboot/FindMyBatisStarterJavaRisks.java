package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Marks application-specific Java decisions in a Starter 1/2/3 to 4 migration. */
public final class FindMyBatisStarterJavaRisks extends Recipe {
    private static final String MAPPER_SCAN = "org.mybatis.spring.annotation.MapperScan";
    private static final String SQL_SESSION_FACTORY_BEAN = "org.mybatis.spring.SqlSessionFactoryBean";
    private static final String MYBATIS_PROPERTIES = "org.mybatis.spring.boot.autoconfigure.MybatisProperties";
    private static final String MOCK_BEAN = "org.springframework.boot.test.mock.mockito.MockBean";
    private static final String RUN_WITH = "org.junit.runner.RunWith";
    private static final String ENABLE_BATCH_PROCESSING =
            "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing";
    private static final String BEAN = "org.springframework.context.annotation.Bean";
    private static final String DATA_SOURCE = "javax.sql.DataSource";

    @Override
    public String getDisplayName() {
        return "Find MyBatis Spring Boot Starter 4 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark conflicting mapper scan aliases/session references, multi-DataSource configuration, manual session " +
               "factories, changed MybatisProperties Java access, Jakarta remnants, Spring Batch configuration, and " +
               "test annotations that require application decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final AnnotationMatcher mapperScan = new AnnotationMatcher("@" + MAPPER_SCAN);
            private final AnnotationMatcher bean = new AnnotationMatcher("@" + BEAN);
            private final AnnotationMatcher mockBean = new AnnotationMatcher("@" + MOCK_BEAN);
            private final AnnotationMatcher runWith = new AnnotationMatcher("@" + RUN_WITH);
            private final AnnotationMatcher enableBatch = new AnnotationMatcher("@" + ENABLE_BATCH_PROCESSING);

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (mapperScan.matches(a)) {
                    Set<String> attributes = annotationAttributes(a);
                    if (attributes.contains("value") && attributes.contains("basePackages")) {
                        return SearchResult.found(a,
                                "@MapperScan value and basePackages are @AliasFor aliases; keep only one after verifying the intended packages");
                    }
                    if (attributes.contains("sqlSessionFactoryRef") && attributes.contains("sqlSessionTemplateRef")) {
                        return SearchResult.found(a,
                                "@MapperScan specifies both factory and template references; select one session boundary explicitly");
                    }
                }
                if (mockBean.matches(a) && a.getArguments() != null && !a.getArguments().isEmpty()) {
                    return SearchResult.found(a,
                            "Attributed @MockBean cannot be converted mechanically because @MockitoBean has a different attribute contract");
                }
                if (runWith.matches(a)) {
                    return SearchResult.found(a,
                            "Spring Boot 4 tests use JUnit Jupiter by default; migrate this JUnit 4 runner and its rules/lifecycle together");
                }
                if (enableBatch.matches(a)) {
                    return SearchResult.found(a,
                            "Spring Batch 6 changes infrastructure defaults and repository selection; review data source, transaction manager, and restart metadata");
                }
                return a;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                long dataSourceBeans = c.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(method -> method.getLeadingAnnotations().stream().anyMatch(bean::matches))
                        .filter(method -> method.getReturnTypeExpression() != null &&
                                          TypeUtils.isOfClassType(method.getReturnTypeExpression().getType(), DATA_SOURCE))
                        .count();
                return dataSourceBeans > 1
                        ? SearchResult.found(c,
                        "Multiple DataSource beans disable single-candidate MyBatis auto-configuration; bind each mapper scan, session factory, and transaction manager explicitly")
                        : c;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                return TypeUtils.isOfClassType(n.getType(), SQL_SESSION_FACTORY_BEAN)
                        ? SearchResult.found(n,
                        "Manual SqlSessionFactoryBean bypasses starter defaults; verify SpringBootVFS, mapper locations, plugins, type handlers, and the intended DataSource")
                        : n;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType == null || !TypeUtils.isOfClassType(methodType.getDeclaringType(), MYBATIS_PROPERTIES)) {
                    return m;
                }
                return "getConfiguration".equals(methodType.getName()) || "setConfiguration".equals(methodType.getName())
                        ? SearchResult.found(m,
                        "MybatisProperties.configuration changed from MyBatis Configuration to CoreConfiguration in Starter 3; move runtime customization to ConfigurationCustomizer")
                        : m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) {
                    return id;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(id.getType());
                if (type == null || !id.getSimpleName().equals(type.getClassName())) {
                    return id;
                }
                String fqn = type.getFullyQualifiedName();
                if (isJakartaEeType(fqn)) {
                    return SearchResult.found(id,
                            "Spring Boot 4 uses Jakarta APIs; migrate this javax EE type and its dependency as part of the platform upgrade");
                }
                return id;
            }
        };
    }

    private static Set<String> annotationAttributes(J.Annotation annotation) {
        Set<String> names = new HashSet<>();
        if (annotation.getArguments() == null) {
            return names;
        }
        for (Expression argument : annotation.getArguments()) {
            if (argument instanceof J.Assignment assignment && assignment.getVariable() instanceof J.Identifier id) {
                names.add(id.getSimpleName());
            } else {
                names.add("value");
            }
        }
        return names;
    }

    private static boolean isJakartaEeType(String fqn) {
        return fqn.startsWith("javax.persistence.") || fqn.startsWith("javax.validation.") ||
               fqn.startsWith("javax.servlet.") ||
               fqn.startsWith("javax.transaction.") && !fqn.startsWith("javax.transaction.xa.");
    }
}
