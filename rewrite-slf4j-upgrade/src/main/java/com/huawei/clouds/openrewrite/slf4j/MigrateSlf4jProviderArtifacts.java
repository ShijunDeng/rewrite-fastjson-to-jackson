package com.huawei.clouds.openrewrite.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Recipe;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Migrates provider coordinates whose SLF4J 2 replacement is unambiguous. */
public final class MigrateSlf4jProviderArtifacts extends Recipe {
    private static final Set<String> FIRST_PARTY_PROVIDERS = Set.of(
            "slf4j-simple", "slf4j-nop", "slf4j-jdk14", "slf4j-reload4j");
    private static final Set<String> GRADLE_DEPENDENCY_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic SLF4J 2 provider artifacts";
    }

    @Override
    public String getDescription() {
        return "Align explicitly listed first-party SLF4J providers, follow the official slf4j-log4j12 relocation, " +
               "and select Log4j's SLF4J 2 provider artifact when its literal version is 2.19 or newer.";
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
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName) &&
                    hasMigratedMavenApi(document.getRoot(), document.getRoot())) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            if (!"dependency".equals(t.getName())) {
                                return t;
                            }
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            String rawVersion = t.getChildValue("version").map(String::trim).orElse("");
                            String version = resolveLocalVersion(document.getRoot(), rawVersion);
                            if ("org.slf4j".equals(group) && FIRST_PARTY_PROVIDERS.contains(artifact) &&
                                UpgradeStrictSlf4jMavenDependency.LISTED_VERSIONS.contains(version)) {
                                return UpgradeStrictSlf4jMavenDependency.withDependencyVersion(
                                        t, UpgradeStrictSlf4jMavenDependency.TARGET_VERSION);
                            }
                            if ("org.slf4j".equals(group) && "slf4j-log4j12".equals(artifact) &&
                                UpgradeStrictSlf4jMavenDependency.LISTED_VERSIONS.contains(version)) {
                                return UpgradeStrictSlf4jMavenDependency.withDependencyVersion(
                                        t.withChildValue("artifactId", "slf4j-reload4j"),
                                        UpgradeStrictSlf4jMavenDependency.TARGET_VERSION);
                            }
                            if ("org.apache.logging.log4j".equals(group) &&
                                "log4j-slf4j-impl".equals(artifact) && supportsSlf4j2Binding(version)) {
                                return t.withChildValue("artifactId", "log4j-slf4j2-impl");
                            }
                            return t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle") &&
                    hasMigratedGradleApi(compilationUnit.printAll())) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return dependency ? migrateCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts") &&
                    hasMigratedGradleApi(compilationUnit.printAll())) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return dependency ? migrateCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    static boolean isDirectGradleDependencyLiteral(org.openrewrite.Cursor cursor) {
        org.openrewrite.Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_DEPENDENCY_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal migrateCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String[] parts = value.split(":", -1);
        if (parts.length < 3) {
            return literal;
        }
        if ("org.slf4j".equals(parts[0]) && FIRST_PARTY_PROVIDERS.contains(parts[1]) &&
            UpgradeStrictSlf4jMavenDependency.LISTED_VERSIONS.contains(parts[2])) {
            parts[2] = UpgradeStrictSlf4jMavenDependency.TARGET_VERSION;
        } else if ("org.slf4j".equals(parts[0]) && "slf4j-log4j12".equals(parts[1]) &&
                   UpgradeStrictSlf4jMavenDependency.LISTED_VERSIONS.contains(parts[2])) {
            parts[1] = "slf4j-reload4j";
            parts[2] = UpgradeStrictSlf4jMavenDependency.TARGET_VERSION;
        } else if ("org.apache.logging.log4j".equals(parts[0]) && "log4j-slf4j-impl".equals(parts[1]) &&
                   supportsSlf4j2Binding(parts[2])) {
            parts[1] = "log4j-slf4j2-impl";
        } else {
            return literal;
        }
        String replacement = String.join(":", parts);
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }

    static boolean supportsSlf4j2Binding(String version) {
        if (version == null || version.isBlank() || version.startsWith("${")) {
            return false;
        }
        String[] parts = version.split("[.-]", 3);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 2 || major == 2 && minor >= 19;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String resolveLocalVersion(Xml.Tag root, String rawVersion) {
        String propertyName = UpgradeStrictSlf4jMavenDependency.propertyName(rawVersion);
        return propertyName == null ? rawVersion :
                UpgradeStrictSlf4jMavenDependency.propertyValue(root, propertyName);
    }

    private static boolean hasMigratedMavenApi(Xml.Tag tag, Xml.Tag root) {
        if ("dependency".equals(tag.getName()) && UpgradeStrictSlf4jMavenDependency.isSlf4jApi(tag)) {
            String rawVersion = tag.getChildValue("version").map(String::trim).orElse("");
            String version = resolveLocalVersion(root, rawVersion);
            if (UpgradeStrictSlf4jMavenDependency.TARGET_VERSION.equals(version)) {
                return true;
            }
        }
        return tag.getChildren().stream().anyMatch(child -> hasMigratedMavenApi(child, root));
    }

    private static boolean hasMigratedGradleApi(String source) {
        return source.contains("org.slf4j:slf4j-api:" + UpgradeStrictSlf4jMavenDependency.TARGET_VERSION);
    }
}
