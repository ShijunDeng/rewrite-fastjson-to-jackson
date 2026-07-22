package com.huawei.clouds.openrewrite.jedis;

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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.Set;

/** Upgrade only Jedis versions that are named explicitly by the spreadsheet. */
public final class UpgradeSelectedJedisDependency extends Recipe {
    private static final String COORDINATE_PREFIX = "redis.clients:jedis:";
    private static final Set<String> VERSIONS = Set.of(
            "2.8.0", "2.9.3", "2.10.2", "3.1.0", "3.5.2",
            "3.6.3", "3.7.0", "3.7.1", "3.8.0", "3.10.0"
    );
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime",
            "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "testFixturesApi",
            "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected Jedis dependency declarations to 7.2.1";
    }

    @Override
    public String getDescription() {
        return "Update direct Maven and Gradle redis.clients:jedis declarations only when their literal version is explicitly named by the spreadsheet.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            if ("dependency".equals(t.getName()) &&
                                "redis.clients".equals(t.getChildValue("groupId").orElse(null)) &&
                                "jedis".equals(t.getChildValue("artifactId").orElse(null)) &&
                                t.getChildValue("version").filter(VERSIONS::contains).isPresent()) {
                                return t.withChildValue("version", "7.2.1");
                            }
                            return t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean directDependency = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return directDependency ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean directDependency = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return directDependency ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean isDirectGradleDependencyLiteral(org.openrewrite.Cursor cursor) {
        org.openrewrite.Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(COORDINATE_PREFIX)) {
            return literal;
        }
        String version = value.substring(COORDINATE_PREFIX.length());
        if (!VERSIONS.contains(version)) {
            return literal;
        }
        String replacement = COORDINATE_PREFIX + "7.2.1";
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }
}
