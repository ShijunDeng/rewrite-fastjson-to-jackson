package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.openrewrite.Cursor;
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
import java.util.Map;
import java.util.Set;

/** Migrates the validation and EL coordinates needed by Hibernate Validator 8 without repurposing shared properties. */
public final class MigrateValidationAndExpressionLanguageDependencies extends Recipe {
    private static final Map<String, Target> TARGETS = Map.of(
            "javax.validation:validation-api", new Target("jakarta.validation", "jakarta.validation-api", "3.0.2"),
            "javax.el:javax.el-api", new Target("jakarta.el", "jakarta.el-api", "5.0.0"),
            "org.glassfish:javax.el", new Target("org.glassfish.expressly", "expressly", "5.0.0"),
            "org.glassfish:jakarta.el", new Target("org.glassfish.expressly", "expressly", "5.0.0")
    );
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Migrate Jakarta Validation and Expression Language dependencies";
    }

    @Override
    public String getDescription() {
        return "Change explicit Validation 2.0 and EL dependencies to the Jakarta Validation 3.0 and EL 5 " +
               "coordinates required by Hibernate Validator 8, replacing property-backed versions inline so " +
               "unrelated consumers of the original property remain unchanged.";
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
                            if (!"dependency".equals(t.getName())) {
                                return t;
                            }
                            String key = t.getChildValue("groupId").orElse("") + ":" +
                                         t.getChildValue("artifactId").orElse("");
                            Target target = TARGETS.get(key);
                            if (target == null) {
                                return t;
                            }
                            Xml.Tag migrated = t.withChildValue("groupId", target.groupId())
                                    .withChildValue("artifactId", target.artifactId());
                            return t.getChild("version").isPresent()
                                    ? migrated.withChildValue("version", target.version())
                                    : migrated;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? migrateCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? migrateCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal migrateCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String[] parts = value.split(":", -1);
        if (parts.length < 3) {
            return literal;
        }
        Target target = TARGETS.get(parts[0] + ":" + parts[1]);
        if (target == null) {
            return literal;
        }
        parts[0] = target.groupId();
        parts[1] = target.artifactId();
        parts[2] = target.version();
        String replacement = String.join(":", parts);
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }

    private record Target(String groupId, String artifactId, String version) {
    }
}
