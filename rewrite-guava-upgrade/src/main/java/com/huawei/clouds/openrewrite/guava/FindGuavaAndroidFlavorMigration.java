package com.huawei.clouds.openrewrite.guava;

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
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;

/** Marks the spreadsheet-required Android-to-JRE flavor switch at the exact dependency declaration. */
public final class FindGuavaAndroidFlavorMigration extends Recipe {
    private static final String COORDINATE = "com.google.guava:guava:32.1.1-android";
    private static final String MESSAGE =
            "The spreadsheet target switches Guava from android to jre; verify Android minSdk, desugaring, and runtime variants before accepting the upgrade";

    @Override
    public String getDisplayName() {
        return "Find Guava Android-to-JRE flavor switches";
    }

    @Override
    public String getDescription() {
        return "Mark exact 32.1.1-android Maven and Gradle declarations because the spreadsheet target changes their runtime flavor.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !UpgradeSelectedGuavaDependency.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            return isAndroidGuava(getCursor(), t) ? mark(t) : t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!UpgradeSelectedGuavaDependency.isGradleDependencyInvocation(getCursor(), m)) {
                                return m;
                            }
                            boolean map = "com.google.guava".equals(mapValue(m, "group")) &&
                                          "guava".equals(mapValue(m, "name")) &&
                                          "32.1.1-android".equals(mapValue(m, "version"));
                            return map ? mark(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct && COORDINATE.equals(l.getValue()) ? mark(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct && COORDINATE.equals(l.getValue()) ? mark(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean isAndroidGuava(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedGuavaDependency.isSupportedGuavaDependency(cursor, tag) &&
               "com.google.guava".equals(tag.getChildValue("groupId").orElse(null)) &&
               "guava".equals(tag.getChildValue("artifactId").orElse(null)) &&
               "32.1.1-android".equals(tag.getChildValue("version").orElse(null));
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        return UpgradeSelectedGuavaDependency.isDirectGradleDependencyLiteral(cursor);
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

    private static <T extends Tree> T mark(T tree) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> MESSAGE.equals(result.getDescription())) ? tree : SearchResult.found(tree, MESSAGE);
    }
}
