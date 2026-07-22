package com.huawei.clouds.openrewrite.netflixeureka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Map;
import java.util.Set;

/** Marks dependency-management and transport-stack choices that cannot be made by a raw client version recipe. */
public final class FindNetflixEurekaBuildRisks extends Recipe {
    private static final String GROUP = "com.netflix.eureka";
    private static final String CLIENT = "eureka-client";
    private static final Set<String> REMOVED_EUREKA_ARTIFACTS = Set.of(
            "eureka-client-jersey2", "eureka-core-jersey2", "eureka-server-governator"
    );
    private static final Set<String> JERSEY1_ARTIFACTS = Set.of(
            "jersey-client", "jersey-core", "jersey-apache-client4"
    );
    private static final Set<String> COMPANION_ARTIFACTS = Set.of(
            "eureka-core", "eureka-client-archaius2", "eureka-server"
    );

    @Override
    public String getDisplayName() {
        return "Find Netflix Eureka 2 build and transport risks";
    }

    @Override
    public String getDescription() {
        return "Mark invisible client management, removed Jersey 2/Governator modules, Jersey 1 dependencies, and visible companion versions that require an explicit 2.0.4 stack decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return inspectPom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return inspectCoordinate(super.visitLiteral(literal, p));
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            return inspectMap(super.visitMethodInvocation(method, p));
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return inspectCoordinate(super.visitLiteral(literal, p));
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document inspectPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new java.util.HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (!"dependency".equals(t.getName())) {
                    return t;
                }
                String group = t.getChildValue("groupId").orElse(null);
                String artifact = t.getChildValue("artifactId").orElse(null);
                String rawVersion = t.getChildValue("version").orElse(null);
                String version = resolve(rawVersion, properties);
                if (GROUP.equals(group) && CLIENT.equals(artifact) && rawVersion == null) {
                    return SearchResult.found(t,
                            "Eureka client version is managed outside this POM; change the owning parent/BOM instead of overriding an invisible value");
                }
                if (GROUP.equals(group) && REMOVED_EUREKA_ARTIFACTS.contains(artifact)) {
                    return SearchResult.found(t,
                            "This Eureka 1.x Jersey 2/Governator module is absent from 2.0.4; choose Jersey 3, Spring Cloud, or a custom transport/server bootstrap before removing it");
                }
                if (("com.sun.jersey".equals(group) || "com.sun.jersey.contribs".equals(group)) &&
                    JERSEY1_ARTIFACTS.contains(artifact)) {
                    return SearchResult.found(t,
                            "Jersey 1 is no longer the built-in Eureka transport; migrate filters, TLS, proxy, authentication, and pooling to the selected transport before removing this dependency");
                }
                if (GROUP.equals(group) && COMPANION_ARTIFACTS.contains(artifact) &&
                    EurekaVersions.SOURCE.equals(version)) {
                    return SearchResult.found(t,
                            "A companion Eureka module remains at 1.10.18; verify whether it participates in the runtime and align or remove it without changing a shared property blindly");
                }
                if (GROUP.equals(group) && ("eureka-client-jersey3".equals(artifact) ||
                    "eureka-core-jersey3".equals(artifact)) && version != null &&
                    !EurekaVersions.TARGET.equals(version)) {
                    return SearchResult.found(t,
                            "Jersey 3 transport module is not aligned to Eureka client 2.0.4; use one coherent Eureka release after confirming this is the chosen transport");
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static J.Literal inspectCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String coordinate)) {
            return literal;
        }
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) {
            return literal;
        }
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length > 2 ? parts[2] : null;
        if (GROUP.equals(group) && CLIENT.equals(artifact) && (version == null || version.isBlank())) {
            return SearchResult.found(literal,
                    "Eureka client version is supplied by a platform/catalog; change the owning declaration rather than guessing it here");
        }
        if (GROUP.equals(group) && REMOVED_EUREKA_ARTIFACTS.contains(artifact)) {
            return SearchResult.found(literal,
                    "This Eureka Jersey 2/Governator module is absent from 2.0.4; select the target transport/bootstrap explicitly");
        }
        if (("com.sun.jersey".equals(group) || "com.sun.jersey.contribs".equals(group)) &&
            JERSEY1_ARTIFACTS.contains(artifact)) {
            return SearchResult.found(literal,
                    "Jersey 1 is no longer Eureka's built-in transport; migrate filters, TLS, proxy, authentication, and pooling explicitly");
        }
        if (GROUP.equals(group) && COMPANION_ARTIFACTS.contains(artifact) && EurekaVersions.SOURCE.equals(version)) {
            return SearchResult.found(literal,
                    "A companion Eureka module remains at 1.10.18; align or remove it only after identifying its runtime role");
        }
        return literal;
    }

    private static J.MethodInvocation inspectMap(J.MethodInvocation invocation) {
        if (!EurekaVersions.GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) {
            return invocation;
        }
        String group = mapValue(invocation, "group");
        String artifact = mapValue(invocation, "name");
        String version = mapValue(invocation, "version");
        if (GROUP.equals(group) && CLIENT.equals(artifact) && version == null) {
            return SearchResult.found(invocation,
                    "Eureka client version is externally managed; update the owning platform/catalog instead of inserting a guessed version");
        }
        if (GROUP.equals(group) && REMOVED_EUREKA_ARTIFACTS.contains(artifact)) {
            return SearchResult.found(invocation,
                    "This Eureka Jersey 2/Governator module is absent from 2.0.4; select a target transport/bootstrap explicitly");
        }
        return invocation;
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static String resolve(String version, Map<String, String> properties) {
        if (version != null && version.startsWith("${") && version.endsWith("}")) {
            return properties.get(version.substring(2, version.length() - 1));
        }
        return version;
    }
}
