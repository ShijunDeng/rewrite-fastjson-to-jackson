package com.huawei.clouds.openrewrite.jultoslf4j;

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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Selects Log4j's SLF4J 2 provider only after the local bridge has reached the target generation. */
public final class MigrateLog4jBindingForListedJulBridge extends Recipe {
    private static final String GROUP = "org.apache.logging.log4j";
    private static final String OLD_ARTIFACT = "log4j-slf4j-impl";
    private static final String NEW_ARTIFACT = "log4j-slf4j2-impl";
    private static final String OLD_COORDINATE = GROUP + ":" + OLD_ARTIFACT + ":";
    private static final String NEW_COORDINATE = GROUP + ":" + NEW_ARTIFACT + ":";
    private static final Pattern EXACT_VERSION = Pattern.compile(
            "^\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?$"
    );

    @Override
    public String getDisplayName() {
        return "Select the Log4j provider for SLF4J 2";
    }

    @Override
    public String getDescription() {
        return "After an owned JUL-to-SLF4J declaration has reached 2.0.17, replace project Maven and Gradle " +
               "log4j-slf4j-impl declarations with log4j-slf4j2-impl only when an explicit literal or local " +
               "Maven property proves Log4j 2.19 or newer.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !AbstractSelectedSlf4jDependencyRecipe.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migrateMaven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle") &&
                    hasTargetGroovyBridge(groovy, ctx)) {
                    return migrateGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts") &&
                    hasTargetKotlinBridge(kotlin, ctx)) {
                    return migrateKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migrateMaven(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = rootProperties(document);
        if (!hasTargetMavenBridge(document, properties, ctx)) return document;
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (!AbstractSelectedSlf4jDependencyRecipe.isActiveProjectDependency(getCursor(), visited) ||
                    AbstractSelectedSlf4jDependencyRecipe.hasMavenVariant(visited) ||
                    !GROUP.equals(visited.getChildValue("groupId").orElse(null)) ||
                    !OLD_ARTIFACT.equals(visited.getChildValue("artifactId").orElse(null))) {
                    return visited;
                }
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                return supportsSlf4j2Binding(resolve(declared, properties))
                        ? visited.withChildValue("artifactId", NEW_ARTIFACT) : visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean hasTargetMavenBridge(Xml.Document document, Map<String, String> properties,
                                                 ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (AbstractSelectedSlf4jDependencyRecipe.isActiveProjectDependency(getCursor(), tag) &&
                    !AbstractSelectedSlf4jDependencyRecipe.hasMavenVariant(tag) &&
                    "org.slf4j".equals(tag.getChildValue("groupId").orElse(null)) &&
                    "jul-to-slf4j".equals(tag.getChildValue("artifactId").orElse(null)) &&
                    AbstractSelectedSlf4jDependencyRecipe.TARGET.equals(resolve(
                            tag.getChildValue("version").map(String::trim).orElse(""), properties))) {
                    found[0] = true;
                }
                return found[0] ? tag : super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), visited)) {
                    return visited;
                }
                return migrateGroovyMap(visited);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = AbstractSelectedSlf4jDependencyRecipe.isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return dependency ? replaceCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = AbstractSelectedSlf4jDependencyRecipe.isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return dependency ? replaceCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static boolean hasTargetGroovyBridge(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), method) &&
                    isTargetBridge(method)) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean hasTargetKotlinBridge(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), method) &&
                    isTargetBridge(method)) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean isTargetBridge(J.MethodInvocation invocation) {
        if (AbstractSelectedSlf4jDependencyRecipe.hasGradleVariant(invocation)) {
            return false;
        }
        if (invocation.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> ("org.slf4j:jul-to-slf4j:" + AbstractSelectedSlf4jDependencyRecipe.TARGET)
                        .equals(value))) {
            return true;
        }
        return "org.slf4j".equals(mapValue(invocation, "group")) &&
               "jul-to-slf4j".equals(mapValue(invocation, "name")) &&
               AbstractSelectedSlf4jDependencyRecipe.TARGET.equals(mapValue(invocation, "version"));
    }

    private static J.MethodInvocation migrateGroovyMap(J.MethodInvocation invocation) {
        if (AbstractSelectedSlf4jDependencyRecipe.hasGradleVariant(invocation)) {
            return invocation;
        }
        String group = mapValue(invocation, "group");
        String artifact = mapValue(invocation, "name");
        String version = mapValue(invocation, "version");
        if (!GROUP.equals(group) || !OLD_ARTIFACT.equals(artifact) || !supportsSlf4j2Binding(version)) {
            return invocation;
        }
        return invocation.withArguments(invocation.getArguments().stream().map(argument -> {
            if (argument instanceof G.MapEntry entry) return replaceNameEntry(entry);
            if (argument instanceof G.MapLiteral map) {
                return map.withElements(map.getElements().stream()
                        .map(MigrateLog4jBindingForListedJulBridge::replaceNameEntry).toList());
            }
            return argument;
        }).toList());
    }

    private static G.MapEntry replaceNameEntry(G.MapEntry entry) {
        if (!"name".equals(mapKey(entry)) || !(entry.getValue() instanceof J.Literal literal) ||
            !OLD_ARTIFACT.equals(literal.getValue())) {
            return entry;
        }
        return entry.withValue(replaceLiteral(literal, OLD_ARTIFACT, NEW_ARTIFACT));
    }

    private static J.Literal replaceCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(OLD_COORDINATE) ||
            !supportsSlf4j2Binding(value.substring(OLD_COORDINATE.length()))) {
            return literal;
        }
        return replaceLiteral(literal, value, NEW_COORDINATE + value.substring(OLD_COORDINATE.length()));
    }

    static boolean supportsSlf4j2Binding(String version) {
        if (version == null || !EXACT_VERSION.matcher(version).matches()) return false;
        String[] parts = version.split("[.-]", 3);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 2 || major == 2 && minor >= 19;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Map<String, String> rootProperties(Xml.Document document) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property -> property.getValue()
                        .ifPresent(value -> properties.put(property.getName(), value.trim()))));
        return properties;
    }

    private static String resolve(String declaration, Map<String, String> properties) {
        if (declaration.startsWith("${") && declaration.endsWith("}") &&
            declaration.indexOf('}') == declaration.length() - 1) {
            return properties.getOrDefault(declaration.substring(2, declaration.length() - 1), declaration);
        }
        return declaration;
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance)
                .map(G.MapEntry.class::cast).filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        if (direct != null) return direct;
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .flatMap(map -> map.getElements().stream()).filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }
}
