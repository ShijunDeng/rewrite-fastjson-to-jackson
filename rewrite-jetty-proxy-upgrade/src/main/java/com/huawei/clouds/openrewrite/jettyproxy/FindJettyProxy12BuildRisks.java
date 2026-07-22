package com.huawei.clouds.openrewrite.jettyproxy;

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

/** Locate build ownership and environment choices that cannot safely be inferred for Jetty Proxy 12.1.8. */
public final class FindJettyProxy12BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Set<String> SERVLET_ARTIFACTS = Set.of(
            "javax.servlet:javax.servlet-api", "jakarta.servlet:jakarta.servlet-api",
            "org.eclipse.jetty:jetty-servlet", "org.eclipse.jetty:jetty-servlets", "org.eclipse.jetty:jetty-webapp",
            "org.eclipse.jetty.ee8:jetty-ee8-proxy", "org.eclipse.jetty.ee9:jetty-ee9-proxy",
            "org.eclipse.jetty.ee10:jetty-ee10-proxy", "org.eclipse.jetty.ee11:jetty-ee11-proxy"
    );
    private static final String JAVA_MESSAGE =
            "Jetty 12.1 requires Java 17 or newer; align compiler, toolchain, CI, container and runtime JDKs";
    private static final String MANAGED_MESSAGE =
            "jetty-proxy is versionless or dynamically/external managed; migrate the owning Jetty BOM, parent, platform or catalog to 12.1.8";
    private static final String ALIGNMENT_MESSAGE =
            "Jetty modules must be aligned on the 12.1.8 line; migrate the owning BOM/property and renamed HTTP2/HTTP3 artifacts instead of mixing Jetty generations";
    private static final String SERVLET_MESSAGE =
            "Jetty 12 separates core ProxyHandler from EE8/EE9/EE10/EE11 ProxyServlet artifacts; choose one Servlet namespace deliberately and align its Servlet API and deployment modules";
    private static final String VARIANT_MESSAGE =
            "This jetty-proxy classifier, custom type, extension, variant or four-part coordinate is not the standard runtime artifact; select and migrate the intended Jetty 12 artifact explicitly";

    @Override
    public String getDisplayName() {
        return "Find Jetty Proxy 12.1 build risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java baselines, external version owners, mixed Jetty modules, and explicit Servlet/Core proxy " +
               "choices only when a build owns a standard jetty-proxy declaration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedJettyProxyDependency.isProjectPath(source.getSourcePath())) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document pom && "pom.xml".equals(name)) return markPom(pom, ctx);
                if (tree instanceof G.CompilationUnit groovy && name.endsWith(".gradle")) {
                    return hasGroovyTarget(groovy, ctx) ? markGroovy(groovy, ctx) : groovy;
                }
                if (tree instanceof K.CompilationUnit kotlin && name.endsWith(".gradle.kts")) {
                    return hasKotlinTarget(kotlin, ctx) ? markKotlin(kotlin, ctx) : kotlin;
                }
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        boolean[] hasTarget = {false};
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .forEach(tag -> tag.getValue().ifPresent(value -> properties.put(tag.getName(), value.trim()))));
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (UpgradeSelectedJettyProxyDependency.isTargetDependency(getCursor(), tag)) hasTarget[0] = true;
                return hasTarget[0] ? tag : super.visitTag(tag, p);
            }
        }.visit(document, ctx);
        if (!hasTarget[0]) return document;

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (UpgradeSelectedJettyProxyDependency.isTargetDependency(getCursor(), visited)) {
                    if (!isStandardTarget(getCursor(), visited)) return mark(visited, VARIANT_MESSAGE);
                    String raw = visited.getChildValue("version").map(String::trim).orElse("");
                    if (raw.isEmpty()) return mark(visited, MANAGED_MESSAGE);
                    String resolved = resolve(raw, properties);
                    if (isProperty(raw)) {
                        if (resolved == null || isDynamic(resolved)) return mark(visited, MANAGED_MESSAGE);
                    } else if (isDynamic(raw)) return mark(visited, MANAGED_MESSAGE);
                }
                if (UpgradeSelectedJettyProxyDependency.isProjectDependency(getCursor(), visited)) {
                    String group = visited.getChildValue("groupId").orElse("");
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    String coordinate = group + ":" + artifact;
                    if (SERVLET_ARTIFACTS.contains(coordinate)) return mark(visited, SERVLET_MESSAGE);
                    if (isJettyGroup(group) && !UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(artifact)) {
                        String version = resolve(visited.getChildValue("version").orElse(""), properties);
                        if (version != null && !version.isEmpty() && !UpgradeSelectedJettyProxyDependency.TARGET.equals(version)) {
                            return mark(visited, ALIGNMENT_MESSAGE);
                        }
                    }
                }
                if (isMavenJavaLevel(getCursor(), visited) && belowJava17(visited.getValue().orElse(""))) {
                    return mark(visited, JAVA_MESSAGE);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markGradleLiteral(super.visitLiteral(literal, p), getCursor());
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                return markJavaVersion(super.visitFieldAccess(fieldAccess, p), getCursor());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (isLegacyToolchainCall(visited, getCursor())) return mark(visited, JAVA_MESSAGE);
                return markMapDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markGradleLiteral(super.visitLiteral(literal, p), getCursor());
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                return markJavaVersion(super.visitFieldAccess(fieldAccess, p), getCursor());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return isLegacyToolchainCall(visited, getCursor()) ? mark(visited, JAVA_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovyTarget(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isAnyGradleTarget(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasKotlinTarget(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isAnyGradleTarget(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean isStandardTarget(Cursor cursor, Xml.Tag dependency) {
        return UpgradeSelectedJettyProxyDependency.isTargetDependency(cursor, dependency) &&
               dependency.getChild("classifier").isEmpty() &&
               dependency.getChildValue("type").map(String::trim).filter(type -> !"jar".equals(type)).isEmpty();
    }

    private static boolean isAnyGradleTarget(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedJettyProxyDependency.isGradleDependencyInvocation(cursor, invocation)) return false;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String value) {
                String[] parts = value.split(":", -1);
                if (parts.length >= 2 && UpgradeSelectedJettyProxyDependency.GROUP.equals(parts[0]) &&
                    UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(parts[1])) return true;
            }
        }
        return UpgradeSelectedJettyProxyDependency.GROUP.equals(mapValue(invocation, "group")) &&
               UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(mapValue(invocation, "name"));
    }

    private static boolean isMavenJavaLevel(Cursor cursor, Xml.Tag tag) {
        if (UpgradeSelectedJettyProxyDependency.isRootProperty(cursor, tag) && JAVA_PROPERTIES.contains(tag.getName())) return true;
        if (!Set.of("source", "target", "release").contains(tag.getName())) return false;
        Cursor configuration = cursor.getParentTreeCursor();
        if (!(configuration.getValue() instanceof Xml.Tag configurationTag) ||
            !"configuration".equals(configurationTag.getName())) return false;
        Cursor plugin = configuration.getParentTreeCursor();
        if (!(plugin.getValue() instanceof Xml.Tag pluginTag) || !"plugin".equals(pluginTag.getName()) ||
            !"maven-compiler-plugin".equals(pluginTag.getChildValue("artifactId").orElse(null)) ||
            !Set.of("", "org.apache.maven.plugins").contains(pluginTag.getChildValue("groupId").orElse(""))) return false;
        Cursor plugins = plugin.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("build".equals(ownerTag.getName())) {
            return UpgradeSelectedJettyProxyDependency.isProjectOrProfile(owner.getParentTreeCursor());
        }
        if (!"pluginManagement".equals(ownerTag.getName())) return false;
        Cursor build = owner.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               UpgradeSelectedJettyProxyDependency.isProjectOrProfile(build.getParentTreeCursor());
    }

    private static J.MethodInvocation markMapDependency(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedJettyProxyDependency.isGradleDependencyInvocation(cursor, invocation)) return invocation;
        String group = mapValue(invocation, "group");
        String artifact = mapValue(invocation, "name");
        String version = mapValue(invocation, "version");
        if (UpgradeSelectedJettyProxyDependency.GROUP.equals(group) && UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(artifact)) {
            if (hasMapKey(invocation, "classifier") || hasMapKey(invocation, "type") ||
                hasMapKey(invocation, "ext") || hasMapKey(invocation, "variant")) return mark(invocation, VARIANT_MESSAGE);
            if (version == null || isDynamic(version)) return mark(invocation, MANAGED_MESSAGE);
        }
        if (SERVLET_ARTIFACTS.contains(group + ":" + artifact)) return mark(invocation, SERVLET_MESSAGE);
        if (isJettyGroup(group) && !UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(artifact) &&
            version != null && !UpgradeSelectedJettyProxyDependency.TARGET.equals(version)) return mark(invocation, ALIGNMENT_MESSAGE);
        return invocation;
    }

    private static J.Literal markGradleLiteral(J.Literal literal, Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        Object value = literal.getValue();
        if (value instanceof String coordinate && parent.getValue() instanceof J.MethodInvocation invocation &&
            UpgradeSelectedJettyProxyDependency.isGradleDependencyInvocation(parent, invocation)) {
            String[] parts = coordinate.split(":", -1);
            if (parts.length >= 2) {
                String group = parts[0];
                String artifact = parts[1];
                if (UpgradeSelectedJettyProxyDependency.GROUP.equals(group) &&
                    UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(artifact)) {
                    if (parts.length > 3) return mark(literal, VARIANT_MESSAGE);
                    if (parts.length == 2 || parts.length == 3 && isDynamic(parts[2])) {
                        return mark(literal, MANAGED_MESSAGE);
                    }
                }
                if (SERVLET_ARTIFACTS.contains(group + ":" + artifact)) return mark(literal, SERVLET_MESSAGE);
                if (isJettyGroup(group) && !UpgradeSelectedJettyProxyDependency.ARTIFACT.equals(artifact) &&
                    parts.length == 3 && !UpgradeSelectedJettyProxyDependency.TARGET.equals(parts[2])) {
                    return mark(literal, ALIGNMENT_MESSAGE);
                }
            }
        }
        if (parent.getValue() instanceof J.Assignment assignment && isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            belowJava17(String.valueOf(value))) return mark(literal, JAVA_MESSAGE);
        return literal;
    }

    private static J.FieldAccess markJavaVersion(J.FieldAccess fieldAccess, Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        String value = fieldAccess.printTrimmed();
        if (parent.getValue() instanceof J.Assignment assignment && isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            value.startsWith("JavaVersion.VERSION_") && belowJava17(value.substring("JavaVersion.VERSION_".length()))) {
            return mark(fieldAccess, JAVA_MESSAGE);
        }
        return fieldAccess;
    }

    private static boolean isLegacyToolchainCall(J.MethodInvocation invocation, Cursor cursor) {
        if (!"of".equals(invocation.getSimpleName()) || invocation.getSelect() == null ||
            !"JavaLanguageVersion".equals(invocation.getSelect().printTrimmed()) || invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !belowJava17(String.valueOf(literal.getValue()))) return false;
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.Assignment assignment &&
               assignment.getVariable().printTrimmed().endsWith("languageVersion");
    }

    private static boolean isJavaCompatibility(String value) {
        return value.equals("sourceCompatibility") || value.equals("targetCompatibility") ||
               value.endsWith(".sourceCompatibility") || value.endsWith(".targetCompatibility") ||
               value.endsWith("languageVersion");
    }

    private static String resolve(String value, Map<String, String> properties) {
        return isProperty(value) ? properties.get(value.substring(2, value.length() - 1)) : value;
    }

    private static boolean isProperty(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    private static boolean isDynamic(String value) {
        return value.contains("+") || value.contains("[") || value.contains("(") || value.contains("]") ||
               value.contains(")") || value.contains("$");
    }

    private static boolean belowJava17(String value) {
        String normalized = value.trim().replace("VERSION_", "");
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        try {
            return Integer.parseInt(normalized.replaceAll("[^0-9].*", "")) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isJettyGroup(String group) {
        return group != null && (group.equals("org.eclipse.jetty") || group.startsWith("org.eclipse.jetty."));
    }

    private static boolean hasMapKey(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream().anyMatch(entry -> key.equals(mapKey(entry))));
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal &&
                literal.getValue() instanceof String value) return value;
            if (argument instanceof G.MapLiteral map) {
                for (G.MapEntry entry : map.getElements()) {
                    if (key.equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal &&
                        literal.getValue() instanceof String value) return value;
                }
            }
        }
        return null;
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
