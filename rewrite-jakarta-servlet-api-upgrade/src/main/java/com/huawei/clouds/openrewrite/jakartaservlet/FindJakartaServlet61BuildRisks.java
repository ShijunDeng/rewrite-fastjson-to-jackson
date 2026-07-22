package com.huawei.clouds.openrewrite.jakartaservlet;

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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Build and runtime platform compatibility markers for Jakarta Servlet 6.1. */
public final class FindJakartaServlet61BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final Set<String> PACKAGED_GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "runtime", "runtimeOnly");
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Pattern MAJOR = Pattern.compile("(\\d+)(?:[.].*)?");

    @Override
    public String getDisplayName() {
        return "Find Jakarta Servlet 6.1 build and container risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java versions below 17, external or unlisted Servlet API management, packaged API JARs, " +
               "mixed javax dependencies, and Servlet container or Jakarta web-stack alignment decisions.";
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
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName) && servletBuild(document.printAll())) {
                    return pomRisks(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle") && servletBuild(groovy.printAll())) {
                    return gradleGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts") && servletBuild(kotlin.printAll())) {
                    return gradleKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document pomRisks(Xml.Document document, ExecutionContext ctx) {
        Xml.Tag root = document.getRoot();
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                String value = t.getValue().map(String::trim).orElse("");
                if (JAVA_PROPERTIES.contains(t.getName()) && belowJava17(value)) {
                    return SearchResult.found(t,
                            "Jakarta Servlet 6.1 requires Java 17+ for compilation, runtime, CI image and container");
                }
                if ("parent".equals(t.getName()) && "org.springframework.boot".equals(t.getChildValue("groupId").orElse(""))) {
                    return SearchResult.found(t,
                            "The platform owns the embedded Servlet implementation; verify this Spring Boot line resolves a Servlet 6.1-compatible container instead of overriding only the API JAR");
                }
                if (!"dependency".equals(t.getName())) {
                    return t;
                }
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String rawVersion = t.getChildValue("version").map(String::trim).orElse("");
                String version = resolve(root, rawVersion);
                if (UpgradeSelectedJakartaServletApiDependency.GROUP.equals(group) &&
                    UpgradeSelectedJakartaServletApiDependency.ARTIFACT.equals(artifact)) {
                    if (rawVersion.isBlank()) {
                        return SearchResult.found(t,
                                "Jakarta Servlet API is externally managed; upgrade the owning Jakarta EE/container BOM and verify the resolved API is exactly 6.1.0");
                    }
                    if (!UpgradeSelectedJakartaServletApiDependency.TARGET_VERSION.equals(version) &&
                        !UpgradeSelectedJakartaServletApiDependency.SOURCE_VERSIONS.contains(version)) {
                        return SearchResult.found(t,
                                "This Servlet API version is outside the spreadsheet's four explicit sources or cannot be resolved and was not upgraded automatically");
                    }
                    if (UpgradeSelectedJakartaServletApiDependency.TARGET_VERSION.equals(version) &&
                        !insideDependencyManagement(getCursor()) &&
                        !"provided".equals(t.getChildValue("scope").map(String::trim).orElse(""))) {
                        return SearchResult.found(t,
                                "Servlet API is container-provided; use Maven provided scope unless an explicitly verified packaging model requires otherwise");
                    }
                    return t;
                }
                if ("javax.servlet".equals(group) || group.startsWith("javax.servlet.")) {
                    return SearchResult.found(t,
                            "A javax Servlet artifact remains beside the Jakarta 6.1 migration; select a Jakarta-compatible replacement for the whole dependency chain");
                }
                if (isJakartaWebSibling(group, artifact)) {
                    return SearchResult.found(t,
                            "Align Jakarta Pages, WebSocket, EL, JSTL and related web APIs with one Jakarta EE 11 / Servlet 6.1 platform");
                }
                if (isTomcat(group, artifact) && knownMajorBelow(version, 11)) {
                    return SearchResult.found(t,
                            "Tomcat versions below 11 do not implement Servlet 6.1; upgrade the container as a platform, not only the API JAR");
                }
                if (isServletContainer(group, artifact)) {
                    return SearchResult.found(t,
                            "Container dependency detected; verify this exact Tomcat/Jetty/Undertow line implements Servlet 6.1 and supports Java 17");
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit gradleGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return legacyJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "Jakarta Servlet 6.1 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (legacyToolchainCall(m)) {
                    return SearchResult.found(m, "Jakarta Servlet 6.1 requires a Java 17+ Gradle toolchain and runtime");
                }
                String group = mapValue(m, "group");
                String name = mapValue(m, "name");
                String version = mapValue(m, "version");
                String message = dependencyRisk(group, name, version, m.getSimpleName());
                return message == null ? m : SearchResult.found(m, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = UpgradeSelectedJakartaServletApiDependency.isDirectGradleDependencyLiteral(getCursor());
                String configuration = direct && getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation invocation
                        ? invocation.getSimpleName() : "";
                J.Literal l = super.visitLiteral(literal, executionContext);
                String message = direct && l.getValue() instanceof String coordinate
                        ? coordinateRisk(coordinate, configuration) : null;
                return message == null ? l : SearchResult.found(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit gradleKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return legacyJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "Jakarta Servlet 6.1 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return legacyToolchainCall(m)
                        ? SearchResult.found(m, "Jakarta Servlet 6.1 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = UpgradeSelectedJakartaServletApiDependency.isDirectGradleDependencyLiteral(getCursor());
                String configuration = direct && getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation invocation
                        ? invocation.getSimpleName() : "";
                J.Literal l = super.visitLiteral(literal, executionContext);
                String message = direct && l.getValue() instanceof String coordinate
                        ? coordinateRisk(coordinate, configuration) : null;
                return message == null ? l : SearchResult.found(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String coordinateRisk(String coordinate, String configuration) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) {
            return null;
        }
        String version = parts.length >= 3 ? parts[2] : "";
        return dependencyRisk(parts[0], parts[1], version, configuration);
    }

    private static String dependencyRisk(String group, String artifact, String version, String configuration) {
        if (UpgradeSelectedJakartaServletApiDependency.GROUP.equals(group) &&
            UpgradeSelectedJakartaServletApiDependency.ARTIFACT.equals(artifact)) {
            if (version.isBlank()) {
                return "Jakarta Servlet API is externally managed; verify the platform resolves exactly 6.1.0";
            }
            if (!UpgradeSelectedJakartaServletApiDependency.TARGET_VERSION.equals(version) &&
                !UpgradeSelectedJakartaServletApiDependency.SOURCE_VERSIONS.contains(version)) {
                return "This Servlet API version is outside the spreadsheet's explicit source set and was not upgraded automatically";
            }
            if (UpgradeSelectedJakartaServletApiDependency.TARGET_VERSION.equals(version) &&
                PACKAGED_GRADLE_CONFIGURATIONS.contains(configuration)) {
                return "Servlet API is container-provided; prefer compileOnly/providedCompile unless packaging it was explicitly verified";
            }
            return null;
        }
        if ("javax.servlet".equals(group) || group.startsWith("javax.servlet.")) {
            return "A javax Servlet artifact remains beside the Jakarta 6.1 migration; migrate the entire dependency chain";
        }
        if (isJakartaWebSibling(group, artifact)) {
            return "Align Jakarta web APIs with one Jakarta EE 11 / Servlet 6.1 platform";
        }
        if (isTomcat(group, artifact) && knownMajorBelow(version, 11)) {
            return "Tomcat versions below 11 do not implement Servlet 6.1; upgrade the container platform";
        }
        if (isServletContainer(group, artifact)) {
            return "Verify this exact container line implements Servlet 6.1 and supports Java 17";
        }
        return null;
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse("");
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String value) {
            return value;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : "";
    }

    private static boolean servletBuild(String source) {
        return source.contains("servlet-api") || source.contains("tomcat-") || source.contains("jetty-") ||
               source.contains("undertow-") || source.contains("spring-boot-starter-web");
    }

    private static boolean insideDependencyManagement(Cursor cursor) {
        for (Cursor parent = cursor.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String resolve(Xml.Tag root, String raw) {
        if (!raw.startsWith("${") || !raw.endsWith("}")) {
            return raw;
        }
        String property = raw.substring(2, raw.length() - 1);
        return root.getChild("properties").flatMap(properties -> properties.getChildValue(property))
                .map(String::trim).orElse(raw);
    }

    private static boolean isJakartaWebSibling(String group, String artifact) {
        return !UpgradeSelectedJakartaServletApiDependency.ARTIFACT.equals(artifact) &&
               (group.startsWith("jakarta.servlet.") || "jakarta.websocket".equals(group) ||
                "jakarta.el".equals(group) || "jakarta.servlet.jsp.jstl".equals(group));
    }

    private static boolean isTomcat(String group, String artifact) {
        return (group.startsWith("org.apache.tomcat") || group.startsWith("org.apache.tomcat.embed")) &&
               (artifact.contains("servlet") || artifact.contains("tomcat") || artifact.contains("embed-core"));
    }

    private static boolean isServletContainer(String group, String artifact) {
        return isTomcat(group, artifact) ||
               (group.startsWith("org.eclipse.jetty") && (artifact.contains("servlet") || artifact.contains("server"))) ||
               (group.startsWith("io.undertow") && (artifact.contains("servlet") || artifact.contains("core")));
    }

    private static boolean knownMajorBelow(String version, int minimum) {
        Matcher matcher = MAJOR.matcher(version.trim());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < minimum;
    }

    private static boolean belowJava17(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value.trim());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 17;
    }

    private static boolean legacyToolchainCall(J.MethodInvocation method) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof Number number) || number.intValue() >= 17) {
            return false;
        }
        return method.getSelect() != null && method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) {
            return false;
        }
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        Matcher numeric = JAVA_LEVEL.matcher(value);
        if (numeric.matches()) {
            return Integer.parseInt(numeric.group(1)) < 17;
        }
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 17;
    }
}
