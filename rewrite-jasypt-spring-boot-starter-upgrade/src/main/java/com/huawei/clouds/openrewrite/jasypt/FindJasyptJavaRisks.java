package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks application Java code whose Jasypt behavior cannot be migrated mechanically. */
public final class FindJasyptJavaRisks extends Recipe {
    private static final String STRING_ENCRYPTOR = "org.jasypt.encryption.StringEncryptor";
    private static final String PBE_CONFIG = "org.jasypt.encryption.pbe.config.SimpleStringPBEConfig";
    private static final String GCM_CONFIG = "com.ulisesbocchio.jasyptspringboot.encryptor.SimpleGCMConfig";
    private static final String ASYMMETRIC_CONFIG =
            "com.ulisesbocchio.jasyptspringboot.encryptor.SimpleAsymmetricConfig";
    private static final Set<String> MOVED_AUTO_CONFIGURATIONS = Set.of(
            "com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration",
            "com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration"
    );
    private static final Set<String> EXTENSION_POINTS = Set.of(
            "com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector",
            "com.ulisesbocchio.jasyptspringboot.EncryptablePropertyResolver",
            "com.ulisesbocchio.jasyptspringboot.EncryptablePropertyFilter"
    );
    private static final Set<String> CRYPTO_SETTERS = Set.of(
            "setPassword", "setPasswordCharArray", "setAlgorithm", "setKeyObtentionIterations",
            "setPoolSize", "setProviderName", "setProvider", "setProviderClassName",
            "setSaltGenerator", "setSaltGeneratorClassName", "setIvGenerator",
            "setIvGeneratorClassName", "setStringOutputType"
    );

    @Override
    public String getDisplayName() {
        return "Find Jasypt Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark custom encryption components, manual crypto tuples, hardcoded/system-property passwords, early environment integration, and property-source/cache internals for review.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return JasyptVersions.isProjectPath(compilationUnit.getSourcePath()) ?
                        super.visitCompilationUnit(compilationUnit, ctx) : compilationUnit;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (EXTENSION_POINTS.stream().anyMatch(type -> directlyImplements(c, type))) {
                    return SearchResult.found(c,
                            "Custom Jasypt detector/resolver/filter owns decryption semantics; recompile on Java 17/Boot 3.5 and test bean selection, ordering, negative matches, and recursion");
                }
                if (directlyImplements(c, STRING_ENCRYPTOR)) {
                    return SearchResult.found(c,
                            "Custom StringEncryptor bypasses starter defaults; verify algorithm, IV, salt, provider, cache behavior, thread safety, and secret lifecycle");
                }
                return c;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (TypeUtils.isOfClassType(m.getReturnTypeExpression() == null ? null : m.getReturnTypeExpression().getType(),
                                            STRING_ENCRYPTOR) && hasBeanAnnotation(m)) {
                    return SearchResult.found(m,
                            "Custom StringEncryptor bean overrides the default; verify its bean name and eager/lazy initialization before encrypted properties are read");
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(n.getType(),
                        "com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor")) {
                    return SearchResult.found(n,
                            "DefaultLazyEncryptor now requires ConfigurableEnvironment and binds through Boot 3.5; prefer managed starter beans and verify first-access failure timing");
                }
                if (TypeUtils.isOfClassType(n.getType(),
                        "com.ulisesbocchio.jasyptspringboot.environment.StandardEncryptableEnvironment")) {
                    return SearchResult.found(n,
                            "StandardEncryptableEnvironment enables very-early decryption for logging/bootstrap; test bootstrap context cleanup and duplicate environment processing");
                }
                return n;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (methodOn(m, PBE_CONFIG) && CRYPTO_SETTERS.contains(m.getSimpleName())) {
                    if (("setPassword".equals(m.getSimpleName()) || "setPasswordCharArray".equals(m.getSimpleName())) &&
                        m.getArguments().stream().anyMatch(J.Literal.class::isInstance)) {
                        return SearchResult.found(m,
                                "Hardcoded Jasypt password in Java source; move it to injected secret material and rotate it");
                    }
                    return SearchResult.found(m,
                            "Manual PBE setting is part of one compatibility tuple; test algorithm, IV, salt, iterations, provider, pool, output encoding, and old ciphertext together");
                }
                if ((methodOn(m, GCM_CONFIG) || methodOn(m, ASYMMETRIC_CONFIG)) &&
                    m.getSimpleName().startsWith("set")) {
                    String name = m.getSimpleName();
                    if (("setSecretKey".equals(name) || "setSecretKeyPassword".equals(name) ||
                         "setPrivateKey".equals(name)) &&
                        m.getArguments().stream().anyMatch(J.Literal.class::isInstance)) {
                        return SearchResult.found(m,
                                "Hardcoded GCM/asymmetric key material in Java source; move it to protected injected material and rotate exposed values");
                    }
                    return SearchResult.found(m,
                            "Manual GCM/asymmetric configuration must be verified as one key/IV/salt/iterations/algorithm/format tuple with existing ciphertext");
                }
                if ("setProperty".equals(m.getSimpleName()) && m.getSelect() != null &&
                    "System".equals(m.getSelect().printTrimmed()) && !m.getArguments().isEmpty() &&
                    m.getArguments().get(0) instanceof J.Literal literal &&
                    "jasypt.encryptor.password".equals(literal.getValue())) {
                    return SearchResult.found(m,
                            "Programmatic system-property password is JVM-global and can leak across tests/contexts; inject a scoped secret before environment bootstrap and clear test state");
                }
                if (m.getMethodType() != null && TypeUtils.isAssignableTo(
                        "com.ulisesbocchio.jasyptspringboot.environment.StandardEncryptableEnvironment",
                        m.getMethodType().getDeclaringType())) {
                    return SearchResult.found(m,
                            "Early encryptable-environment customization affects logging/bootstrap ordering; run full startup tests for every profile");
                }
                return m;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (TypeUtils.isOfClassType(a.getType(),
                        "com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties")) {
                    return SearchResult.found(a,
                            "Manual enablement is normally unnecessary with the starter; verify that it does not double-wrap property sources or run too late for logging/bootstrap");
                }
                if (TypeUtils.isOfClassType(a.getType(),
                        "com.ulisesbocchio.jasyptspringboot.annotation.EncryptablePropertySource")) {
                    return SearchResult.found(a,
                            "Custom encryptable PropertySource requires Boot 3.5 resource/YAML loading and initialization-order regression tests");
                }
                return a;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (i.getType() != null && (TypeUtils.isAssignableTo(
                        "com.ulisesbocchio.jasyptspringboot.EncryptablePropertySourceConverter", i.getType()) ||
                    i.getType().toString().contains("com.ulisesbocchio.jasyptspringboot.caching") ||
                    i.getType().toString().contains("com.ulisesbocchio.jasyptspringboot.wrapper"))) {
                    return SearchResult.found(i,
                            "Direct use of Jasypt property-source wrapper/cache internals is version-sensitive; use public extension points or verify refresh and proxy identity behavior");
                }
                return i;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                return visited.getValue() instanceof String value && MOVED_AUTO_CONFIGURATIONS.contains(value)
                        ? SearchResult.found(visited,
                        "Reflective Jasypt auto-configuration reference uses the pre-4.0 package; update the exact class name and test metadata loading")
                        : visited;
            }
        };
    }

    private static boolean directlyImplements(J.ClassDeclaration declaration, String type) {
        return declaration.getImplements() != null && declaration.getImplements().stream()
                .anyMatch(implemented -> TypeUtils.isAssignableTo(type, implemented.getType()));
    }

    private static boolean methodOn(J.MethodInvocation method, String owner) {
        return method.getMethodType() != null && TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }

    private static boolean hasBeanAnnotation(J.MethodDeclaration method) {
        return method.getLeadingAnnotations().stream().anyMatch(annotation ->
                TypeUtils.isOfClassType(annotation.getType(), "org.springframework.context.annotation.Bean") ||
                "Bean".equals(annotation.getSimpleName()));
    }
}
