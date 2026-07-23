package com.huawei.clouds.openrewrite.springcloudcontext;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Mark Java API and lifecycle boundaries that cannot be migrated from syntax alone. */
public final class FindSpringCloudContextJavaRisks extends Recipe {
    private static final Set<String> DIRECT_API_TYPES = Set.of(
            "org.springframework.cloud.context.refresh.ContextRefresher",
            "org.springframework.cloud.context.refresh.LegacyContextRefresher",
            "org.springframework.cloud.context.refresh.ConfigDataContextRefresher",
            "org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder",
            "org.springframework.cloud.context.environment.WritableEnvironmentEndpoint",
            "org.springframework.cloud.context.environment.WritableEnvironmentEndpointWebExtension",
            "org.springframework.cloud.context.environment.EnvironmentManager",
            "org.springframework.cloud.context.restart.RestartEndpoint",
            "org.springframework.cloud.context.named.NamedContextFactory",
            "org.springframework.cloud.context.encrypt.EncryptorFactory");
    private static final Set<String> LIFECYCLE_METHODS = Set.of(
            "refresh", "refreshEnvironment", "refreshAll", "rebind", "restart", "doRestart", "write", "reset");

    @Override
    public String getDisplayName() {
        return "Find Spring Cloud Context 4.3 Java risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy bootstrap, removed Javax/Spring Native APIs, refresh/custom scope code, moved protected " +
               "members, endpoint constructors, live environment mutation, encryption, and named child contexts.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private Set<String> imports = Set.of();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (SpringCloudContextSupport.generated(cu.getSourcePath())) return cu;
                Set<String> previous = imports;
                imports = new HashSet<>();
                for (J.Import anImport : cu.getImports()) imports.add(anImport.getTypeName());
                J.CompilationUnit visited = super.visitCompilationUnit(cu, ctx);
                imports = previous;
                return visited;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String type = visited.getTypeName();
                if (type.startsWith("javax.servlet.") || type.startsWith("javax.persistence.") ||
                    type.startsWith("javax.validation.") || type.startsWith("javax.annotation.")) {
                    return SearchResult.found(visited, "Spring Cloud Context 4.3.2 aligns with Boot 3.5 / Framework 6 and Jakarta EE; migrate this Javax API, dependency, reflection/configuration, and tests together");
                }
                if (type.startsWith("org.springframework.nativex.")) {
                    return SearchResult.found(visited, "Spring Native-era hints are not the Boot 3 AOT RuntimeHints model; port hints and verify refresh is disabled in AOT/native images");
                }
                if (type.startsWith("org.springframework.cloud.bootstrap.") ||
                    type.endsWith(".PropertySourceLocator")) {
                    return SearchResult.found(visited, "Legacy bootstrap extension detected; verify spring.factories registration, ordering, parent-context isolation, Config Data alternative, refresh fetch timing, and AOT/native reachability");
                }
                if ("org.springframework.cloud.env.EnvironmentUtils".equals(type)) {
                    return SearchResult.found(visited, "EnvironmentUtils is deprecated for removal since 4.3.0; replace getSubProperties use with application-owned Environment/PropertyResolver access");
                }
                if (DIRECT_API_TYPES.contains(type)) {
                    return SearchResult.found(visited, apiMessage(type));
                }
                return visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                String fqn = type == null ? importedType(visited.getSimpleName()) : type.getFullyQualifiedName();
                if ("org.springframework.cloud.context.config.annotation.RefreshScope".equals(fqn)) {
                    return SearchResult.found(visited, "RefreshScope uses lazy proxies and does not make every @Bean in an annotated @Configuration refreshable; verify identity/lifecycle, removed values, records/immutable properties, never-refreshable types, and disable refresh for AOT/native");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                String declaration = visited.printTrimmed(getCursor());
                if (imports.contains("org.springframework.cloud.context.refresh.ContextRefresher") &&
                    declaration.matches("(?s).*\\bextends\\s+ContextRefresher\\b.*")) {
                    return SearchResult.found(visited, "Custom ContextRefresher subclasses cross protected constructor/source-retention/updateEnvironment contracts; REFRESH_ARGS_PROPERTY_SOURCE moved and is no longer protected, so redesign the subclass against 4.3.2 deliberately");
                }
                if (imports.stream().anyMatch(name -> name.endsWith(".PropertySourceLocator")) &&
                    declaration.matches("(?s).*\\bimplements\\s+[^\\{]*\\bPropertySourceLocator\\b.*")) {
                    return SearchResult.found(visited, "Custom PropertySourceLocator participates in bootstrap/Config Data ordering and a two-phase profile fetch; retest source names, precedence, failures, refresh, and parent-context isolation");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if ("REFRESH_ARGS_PROPERTY_SOURCE".equals(visited.getName().getSimpleName()) &&
                    "ContextRefresher".equals(visited.getTarget().printTrimmed(getCursor()))) {
                    return SearchResult.found(visited, "REFRESH_ARGS_PROPERTY_SOURCE moved out of ContextRefresher and became a private LegacyContextRefresher implementation detail; replace this coupling with an application-owned source name/refresh design");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                String fqn = type == null || visited.getClazz() == null ? "" : importedType(visited.getClazz().toString().trim());
                if (type != null) fqn = type.getFullyQualifiedName();
                if (DIRECT_API_TYPES.contains(fqn)) {
                    return SearchResult.found(visited, apiMessage(fqn));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (!LIFECYCLE_METHODS.contains(visited.getSimpleName())) return visited;
                JavaType.Method methodType = visited.getMethodType();
                JavaType.FullyQualified owner = methodType == null ? null :
                        TypeUtils.asFullyQualified(methodType.getDeclaringType());
                if (owner != null && (owner.getFullyQualifiedName().startsWith("org.springframework.cloud.context.") ||
                                      owner.getFullyQualifiedName().startsWith("org.springframework.cloud.endpoint."))) {
                    return SearchResult.found(visited, "Direct Spring Cloud context mutation/lifecycle API detected; retest property-source diffs, rebinding errors, proxy recreation, endpoint security, concurrency, shutdown/restart ordering, and failure rollback");
                }
                return visited;
            }

            private String importedType(String simpleName) {
                return imports.stream().filter(name -> name.endsWith("." + simpleName)).findFirst().orElse("");
            }

            private String apiMessage(String type) {
                if (type.contains("WritableEnvironmentEndpoint") || type.endsWith("EnvironmentManager")) {
                    return "Writable environment endpoint constructors and sanitization contracts follow Boot 3.5 Actuator; prefer auto-configuration and verify authorization, Show policy, sanitizers, mutation events, and rollback";
                }
                if (type.endsWith("RestartEndpoint")) {
                    return "RestartEndpoint event/lifecycle integration changed and restart is disabled by default; prefer auto-configuration and verify pause/resume coupling, timeout, shutdown hooks, CRaC, and operational security";
                }
                if (type.endsWith("NamedContextFactory")) {
                    return "NamedContextFactory child contexts gained AOT initializers and GenericApplicationContext hooks; verify lazy creation, parent inheritance, configuration isolation, destruction, and native registration";
                }
                if (type.endsWith("EncryptorFactory")) {
                    return "Encryption implementation/provider ownership changed; verify key parsing, Bouncy Castle versus spring-security-rsa, failure policy, indexed properties, RuntimeHints, and secret redaction";
                }
                return "Direct refresh/rebind implementation API changed across Boot 2/3 and Cloud 4.3; prefer auto-configured contracts and retest constructors, source retention, errors, proxy lifecycle, AOT/native restrictions, and refresh ordering";
            }
        };
    }
}
