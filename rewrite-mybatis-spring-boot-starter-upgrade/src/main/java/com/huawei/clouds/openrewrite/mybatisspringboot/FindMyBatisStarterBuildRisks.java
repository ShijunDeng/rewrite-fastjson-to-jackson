package com.huawei.clouds.openrewrite.mybatisspringboot;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Mark build declarations that the strict dependency upgrade intentionally cannot resolve. */
public final class FindMyBatisStarterBuildRisks extends Recipe {
    static final String UNRESOLVED_VERSION_MESSAGE =
            "This MyBatis Starter version is outside the spreadsheet gate or resolves through a shared property, range, catalog, or dynamic expression; select an approved path to 4.0.0";
    static final String VERSIONLESS_MESSAGE =
            "This MyBatis Starter has no local managed version; upgrade the owning parent, BOM, platform, or catalog and verify that it resolves to 4.0.0";
    static final String CUSTOM_ARTIFACT_MESSAGE =
            "This classified or non-JAR MyBatis Starter artifact was not changed; verify that 4.0.0 publishes the same artifact shape before migrating it";
    static final String COMPANION_MESSAGE =
            "This MyBatis Starter companion is not aligned to 4.0.0; align the family only after verifying its test or auto-configuration role";
    static final String GRADLE_JAVA_MESSAGE =
            "MyBatis Spring Boot Starter 4 requires Java 17 or newer; align the Gradle toolchain, compiler, test JVM, and production runtime";

    @Override
    public String getDisplayName() {
        return "Find MyBatis Spring Boot Starter 4 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved, externally managed, custom, or unaligned Starter declarations and unsupported " +
               "Gradle Java baselines, only in build files that directly own the core Starter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedMyBatisSpringBootStarterDependency.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    return inspectPom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return inspectGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return inspectKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document inspectPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = projectProperties(document);
        boolean[] ownsCore = {false};
        boolean[] localManagedTarget = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedMyBatisSpringBootStarterDependency.isMavenDependencyBlock(getCursor(), tag) &&
                    UpgradeSelectedMyBatisSpringBootStarterDependency.hasCoreCoordinates(tag)) {
                    ownsCore[0] = true;
                    if (isRootDependencyManagementEntry(getCursor()) &&
                        UpgradeSelectedMyBatisSpringBootStarterDependency.isStandardJar(tag) &&
                        UpgradeSelectedMyBatisSpringBootStarterDependency.TARGET.equals(
                                resolve(tag.getChildValue("version").orElse(""), properties))) {
                        localManagedTarget[0] = true;
                    }
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        if (!ownsCore[0]) {
            return document;
        }

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (!UpgradeSelectedMyBatisSpringBootStarterDependency.isMavenDependencyBlock(getCursor(), t) ||
                    !UpgradeSelectedMyBatisSpringBootStarterDependency.hasFamilyCoordinates(t)) {
                    return t;
                }
                boolean core = UpgradeSelectedMyBatisSpringBootStarterDependency.hasCoreCoordinates(t);
                if (!UpgradeSelectedMyBatisSpringBootStarterDependency.isStandardJar(t)) {
                    return SearchResult.found(t, CUSTOM_ARTIFACT_MESSAGE);
                }
                String declared = t.getChildValue("version").map(String::trim).orElse("");
                if (declared.isEmpty()) {
                    if (core && isRootDirectDependency(getCursor()) && localManagedTarget[0]) {
                        return t;
                    }
                    return SearchResult.found(t, core ? VERSIONLESS_MESSAGE : COMPANION_MESSAGE);
                }
                if (!UpgradeSelectedMyBatisSpringBootStarterDependency.TARGET.equals(resolve(declared, properties))) {
                    return SearchResult.found(t, core ? UNRESOLVED_VERSION_MESSAGE : COMPANION_MESSAGE);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit inspectGroovy(G.CompilationUnit cu, ExecutionContext ctx) {
        if (!containsGroovyCore(cu, ctx)) {
            return cu;
        }
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (javaBaselineCall(m)) {
                    return SearchResult.found(m, GRADLE_JAVA_MESSAGE);
                }
                return markGradleDependency(m, getCursor());
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return javaBaselineAssignment(a, getCursor())
                        ? SearchResult.found(a, GRADLE_JAVA_MESSAGE) : a;
            }
        }.visitNonNull(cu, ctx);
    }

    private static K.CompilationUnit inspectKotlin(K.CompilationUnit cu, ExecutionContext ctx) {
        if (!containsKotlinCore(cu, ctx)) {
            return cu;
        }
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (javaBaselineCall(m)) {
                    return SearchResult.found(m, GRADLE_JAVA_MESSAGE);
                }
                return markGradleDependency(m, getCursor());
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return javaBaselineAssignment(a, getCursor())
                        ? SearchResult.found(a, GRADLE_JAVA_MESSAGE) : a;
            }
        }.visitNonNull(cu, ctx);
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method, Cursor cursor) {
        if (!UpgradeSelectedMyBatisSpringBootStarterDependency.isGradleDependencyInvocation(cursor, method)) {
            return method;
        }
        String compact = compact(method, cursor);
        if (isCore(compact) && !isTarget(compact, UpgradeSelectedMyBatisSpringBootStarterDependency.CORE)) {
            return SearchResult.found(method, UpgradeSelectedMyBatisSpringBootStarterDependency.hasGradleVariant(method) ||
                    customGradleArtifact(compact)
                    ? CUSTOM_ARTIFACT_MESSAGE : UNRESOLVED_VERSION_MESSAGE);
        }
        for (String artifact : UpgradeSelectedMyBatisSpringBootStarterDependency.FAMILY_ARTIFACTS) {
            if (!UpgradeSelectedMyBatisSpringBootStarterDependency.CORE.equals(artifact) && isArtifact(compact, artifact) &&
                !isTarget(compact, artifact)) {
                return SearchResult.found(method, COMPANION_MESSAGE);
            }
        }
        return method;
    }

    private static boolean containsGroovyCore(G.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedMyBatisSpringBootStarterDependency.isGradleDependencyInvocation(
                        getCursor(), method) && isCore(compact(method, getCursor()))) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(cu, ctx);
        return found[0];
    }

    private static boolean containsKotlinCore(K.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedMyBatisSpringBootStarterDependency.isGradleDependencyInvocation(
                        getCursor(), method) && isCore(compact(method, getCursor()))) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(cu, ctx);
        return found[0];
    }

    private static Map<String, String> projectProperties(Xml.Document document) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .forEach(property -> property.getValue()
                        .ifPresent(value -> properties.put(property.getName(), value.trim()))));
        return properties;
    }

    private static String resolve(String raw, Map<String, String> properties) {
        String declared = raw.trim();
        if (declared.startsWith("${") && declared.endsWith("}") && declared.indexOf("${", 2) < 0) {
            return properties.getOrDefault(declared.substring(2, declared.length() - 1), declared);
        }
        return declared;
    }

    private static boolean isRootDirectDependency(Cursor cursor) {
        Cursor dependencies = cursor.getParent();
        Cursor project = dependencies == null ? null : dependencies.getParent();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && project != null &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName());
    }

    private static boolean isRootDependencyManagementEntry(Cursor cursor) {
        Cursor dependencies = cursor.getParent();
        Cursor management = dependencies == null ? null : dependencies.getParent();
        Cursor project = management == null ? null : management.getParent();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && management != null &&
               management.getValue() instanceof Xml.Tag managementTag &&
               "dependencyManagement".equals(managementTag.getName()) && project != null &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName());
    }

    private static String compact(J.MethodInvocation method, Cursor cursor) {
        return method.printTrimmed(cursor).replaceAll("\\s+", "");
    }

    private static boolean isCore(String compact) {
        return isArtifact(compact, UpgradeSelectedMyBatisSpringBootStarterDependency.CORE);
    }

    private static boolean isArtifact(String compact, String artifact) {
        return compact.contains(UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + ":" + artifact + ":") ||
               compact.contains("group:'" + UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + "'") &&
               compact.contains("name:'" + artifact + "'") ||
               compact.contains("group:\"" + UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + "\"") &&
               compact.contains("name:\"" + artifact + "\"");
    }

    private static boolean isTarget(String compact, String artifact) {
        String coordinate = UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + ":" + artifact + ":" +
                            UpgradeSelectedMyBatisSpringBootStarterDependency.TARGET;
        return compact.contains("'" + coordinate + "'") || compact.contains("\"" + coordinate + "\"") ||
               compact.contains("group:'" + UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + "'") &&
               compact.contains("name:'" + artifact + "'") && compact.contains("version:'" +
                       UpgradeSelectedMyBatisSpringBootStarterDependency.TARGET + "'") ||
               compact.contains("group:\"" + UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + "\"") &&
               compact.contains("name:\"" + artifact + "\"") && compact.contains("version:\"" +
                       UpgradeSelectedMyBatisSpringBootStarterDependency.TARGET + "\"");
    }

    private static boolean customGradleArtifact(String compact) {
        String prefix = UpgradeSelectedMyBatisSpringBootStarterDependency.GROUP + ":" +
                        UpgradeSelectedMyBatisSpringBootStarterDependency.CORE + ":";
        int start = compact.indexOf(prefix);
        if (start < 0) {
            return false;
        }
        int quote = compact.indexOf(compact.contains("'" + prefix) ? '\'' : '"', start);
        String coordinate = quote < 0 ? compact.substring(start) : compact.substring(start, quote);
        return coordinate.contains("@") || coordinate.substring(prefix.length()).contains(":");
    }

    private static boolean javaBaselineAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) {
            return false;
        }
        return javaMajor(assignment.getAssignment().printTrimmed(cursor)) < 17;
    }

    private static boolean javaBaselineCall(J.MethodInvocation method) {
        if ("jvmToolchain".equals(method.getSimpleName()) && method.getArguments().size() == 1) {
            return javaMajor(method.getArguments().get(0).printTrimmed()) < 17;
        }
        return "of".equals(method.getSimpleName()) && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion") &&
               method.getArguments().size() == 1 && javaMajor(method.getArguments().get(0).printTrimmed()) < 17;
    }

    private static int javaMajor(String source) {
        String value = source.replace("'", "").replace("\"", "").trim();
        if (value.startsWith("JavaVersion.VERSION_1_")) {
            value = value.substring("JavaVersion.VERSION_1_".length());
        } else if (value.startsWith("JavaVersion.VERSION_")) {
            value = value.substring("JavaVersion.VERSION_".length());
        } else if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        try {
            int major = Integer.parseInt(value);
            return major > 0 ? major : Integer.MAX_VALUE;
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
