package com.huawei.clouds.openrewrite.jultoslf4j;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact build declarations that leave an unsafe or unresolved logging route after migration. */
public final class FindUnsafeJulToSlf4jTopology extends Recipe {
    private static final Set<String> BRIDGE_VERSIONS = Set.of("1.7.30", "1.7.32", "1.7.36", "2.0.17");
    private static final Set<String> FIRST_PARTY_COMPONENTS = Set.of(
            "slf4j-api", "slf4j-simple", "slf4j-nop", "slf4j-reload4j"
    );
    private static final Set<String> PROVIDERS = Set.of(
            "org.slf4j:slf4j-simple", "org.slf4j:slf4j-nop", "org.slf4j:slf4j-jdk14",
            "org.slf4j:slf4j-reload4j", "org.slf4j:slf4j-log4j12", "org.slf4j:slf4j-jcl",
            "ch.qos.logback:logback-classic", "org.apache.logging.log4j:log4j-slf4j-impl",
            "org.apache.logging.log4j:log4j-slf4j2-impl"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Pattern EXACT_VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9]+)*$");

    static final String LOOP_MESSAGE =
            "JUL-to-SLF4J and slf4j-jdk14 create a recursive logging route; remove one direction";
    static final String LOGBACK_MESSAGE =
            "Logback 1.2 is an SLF4J 1.7 provider; choose a provider compatible with SLF4J 2";
    static final String LOG4J_MESSAGE =
            "log4j-slf4j-impl targets SLF4J 1.x; upgrade Log4j and use log4j-slf4j2-impl";
    static final String OLD_PROVIDER_MESSAGE =
            "This SLF4J 1.x API/provider is incompatible with the SLF4J 2 bridge; align its version authority";
    static final String MANAGED_PROVIDER_MESSAGE =
            "This managed or dynamic logging component has no proven SLF4J 2 version; verify its BOM, catalog, or property authority";
    static final String LOG4J12_MESSAGE =
            "slf4j-log4j12 is an SLF4J 1.x binding; select one SLF4J 2 compatible provider";
    static final String BRIDGE_AUTHORITY_MESSAGE =
            "This JUL-to-SLF4J version is managed, ranged, dynamic, or interpolated; update its BOM, catalog, or property owner to 2.0.17";
    static final String CUSTOM_ARTIFACT_MESSAGE =
            "This JUL-to-SLF4J declaration selects a custom classifier or artifact type; verify and migrate that variant explicitly";
    static final String MULTIPLE_PROVIDERS_MESSAGE =
            "Multiple SLF4J providers are declared in this build; retain exactly one provider per runtime/test classpath";
    static final String LOG4J_LOOP_MESSAGE =
            "log4j-to-slf4j and a Log4j SLF4J provider form a Log4j/SLF4J recursion loop; keep only one direction";
    static final String JCL_LOOP_MESSAGE =
            "jcl-over-slf4j and slf4j-jcl form a Commons Logging/SLF4J recursion loop; keep only one direction";
    static final String RELOAD4J_LOOP_MESSAGE =
            "log4j-over-slf4j and an SLF4J-to-Log4j/reload4j provider form a recursion loop; keep only one direction";

    @Override
    public String getDisplayName() {
        return "Find unsafe JUL-to-SLF4J 2 logging topology";
    }

    @Override
    public String getDescription() {
        return "Marks exact project Maven or Gradle declarations for unresolved bridge authority, JUL/Log4j/JCL " +
               "bidirectional loops, old or managed providers, and multiple provider selection.";
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
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return markMaven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return markGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return markKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document markMaven(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = rootProperties(document);
        List<Coordinate> coordinates = new ArrayList<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (AbstractSelectedSlf4jDependencyRecipe.isProjectDependency(getCursor(), tag)) {
                    String group = tag.getChildValue("groupId").orElse("");
                    String artifact = tag.getChildValue("artifactId").orElse("");
                    String declared = tag.getChildValue("version").map(String::trim).orElse("");
                    coordinates.add(new Coordinate(group, artifact, resolve(declared, properties), declared,
                            AbstractSelectedSlf4jDependencyRecipe.isActiveProjectDependency(getCursor(), tag),
                            AbstractSelectedSlf4jDependencyRecipe.hasMavenVariant(tag)));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        Topology topology = Topology.from(coordinates);
        if (!topology.hasCoreReference) return document;

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (!AbstractSelectedSlf4jDependencyRecipe.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                Coordinate coordinate = new Coordinate(group, artifact, resolve(declared, properties), declared,
                        AbstractSelectedSlf4jDependencyRecipe.isActiveProjectDependency(getCursor(), visited),
                        AbstractSelectedSlf4jDependencyRecipe.hasMavenVariant(visited));
                String message = riskMessage(coordinate, topology);
                return message == null ? visited : SearchResult.found(visited, message);
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        Topology topology = gradleTopology(compilationUnit, ctx);
        if (!topology.hasCoreReference) return compilationUnit;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), visited)) {
                    return visited;
                }
                Coordinate coordinate = invocationCoordinate(visited, getCursor());
                String message = coordinate == null ? dynamicLoggingMessage(visited, getCursor(), topology) :
                        riskMessage(coordinate, topology);
                return message == null ? visited : SearchResult.found(visited, message);
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        Topology topology = gradleTopology(compilationUnit, ctx);
        if (!topology.hasCoreReference) return compilationUnit;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), visited)) {
                    return visited;
                }
                Coordinate coordinate = invocationCoordinate(visited, getCursor());
                String message = coordinate == null ? dynamicLoggingMessage(visited, getCursor(), topology) :
                        riskMessage(coordinate, topology);
                return message == null ? visited : SearchResult.found(visited, message);
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static Topology gradleTopology(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        List<Coordinate> coordinates = new ArrayList<>();
        boolean[] dynamicCore = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), method)) {
                    Coordinate coordinate = invocationCoordinate(method, getCursor());
                    if (coordinate != null) coordinates.add(coordinate);
                    else if (containsDynamicCore(method, getCursor())) dynamicCore[0] = true;
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return Topology.from(coordinates).withDynamicCore(dynamicCore[0]);
    }

    private static Topology gradleTopology(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        List<Coordinate> coordinates = new ArrayList<>();
        boolean[] dynamicCore = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (AbstractSelectedSlf4jDependencyRecipe.isGradleDependencyInvocation(getCursor(), method)) {
                    Coordinate coordinate = invocationCoordinate(method, getCursor());
                    if (coordinate != null) coordinates.add(coordinate);
                    else if (containsDynamicCore(method, getCursor())) dynamicCore[0] = true;
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return Topology.from(coordinates).withDynamicCore(dynamicCore[0]);
    }

    private static String riskMessage(Coordinate coordinate, Topology topology) {
        String key = coordinate.key();
        if ("org.slf4j:jul-to-slf4j".equals(key)) {
            if (coordinate.variant) return CUSTOM_ARTIFACT_MESSAGE;
            return unresolvedBridge(coordinate) ? BRIDGE_AUTHORITY_MESSAGE : null;
        }
        if (!topology.hasKnownBridge) return null;

        if ("org.slf4j:slf4j-jdk14".equals(key)) return LOOP_MESSAGE;
        if (topology.hasLog4jLoop() && ("org.apache.logging.log4j:log4j-to-slf4j".equals(key) ||
            key.startsWith("org.apache.logging.log4j:log4j-slf4j"))) return LOG4J_LOOP_MESSAGE;
        if (topology.hasJclLoop() && ("org.slf4j:jcl-over-slf4j".equals(key) ||
            "org.slf4j:slf4j-jcl".equals(key))) return JCL_LOOP_MESSAGE;
        if (topology.hasReload4jLoop() && ("org.slf4j:log4j-over-slf4j".equals(key) ||
            "org.slf4j:slf4j-reload4j".equals(key) || "org.slf4j:slf4j-log4j12".equals(key))) {
            return RELOAD4J_LOOP_MESSAGE;
        }
        if ("ch.qos.logback:logback-classic".equals(key)) {
            if (coordinate.version.isEmpty() || !EXACT_VERSION.matcher(coordinate.version).matches()) {
                return MANAGED_PROVIDER_MESSAGE;
            }
            if (coordinate.version.startsWith("1.2.")) return LOGBACK_MESSAGE;
        }
        if ("org.slf4j:slf4j-log4j12".equals(key)) return LOG4J12_MESSAGE;
        if ("org.apache.logging.log4j:log4j-slf4j-impl".equals(key)) return LOG4J_MESSAGE;
        if (key.startsWith("org.slf4j:") && FIRST_PARTY_COMPONENTS.contains(coordinate.artifact)) {
            if (coordinate.version.isEmpty() || !EXACT_VERSION.matcher(coordinate.version).matches()) {
                return MANAGED_PROVIDER_MESSAGE;
            }
            if (coordinate.version.startsWith("1.")) return OLD_PROVIDER_MESSAGE;
        }
        if (topology.providerCount > 1 && PROVIDERS.contains(key)) return MULTIPLE_PROVIDERS_MESSAGE;
        return null;
    }

    private static boolean unresolvedBridge(Coordinate coordinate) {
        if (coordinate.declared.isEmpty() || coordinate.version.isEmpty()) return true;
        if (AbstractSelectedSlf4jDependencyRecipe.TARGET.equals(coordinate.version)) return false;
        if (coordinate.version.contains("${") || coordinate.declared.contains("${")) return true;
        return !EXACT_VERSION.matcher(coordinate.version).matches();
    }

    private static boolean containsDynamicCore(J.MethodInvocation invocation, Cursor cursor) {
        return invocation.getArguments().stream().anyMatch(argument ->
                (argument instanceof G.GString || argument instanceof K.StringTemplate) &&
                argument.printTrimmed(cursor).contains("org.slf4j:jul-to-slf4j"));
    }

    private static String dynamicLoggingMessage(J.MethodInvocation invocation, Cursor cursor, Topology topology) {
        for (J argument : invocation.getArguments()) {
            if (!(argument instanceof G.GString || argument instanceof K.StringTemplate)) continue;
            String printed = argument.printTrimmed(cursor);
            if (printed.contains("org.slf4j:jul-to-slf4j")) return BRIDGE_AUTHORITY_MESSAGE;
            if (!topology.hasKnownBridge) continue;
            if (printed.contains("org.apache.logging.log4j:log4j-slf4j-impl")) return LOG4J_MESSAGE;
            if (printed.contains("org.slf4j:slf4j-api") || printed.contains("org.slf4j:slf4j-simple") ||
                printed.contains("org.slf4j:slf4j-nop") || printed.contains("org.slf4j:slf4j-reload4j") ||
                printed.contains("ch.qos.logback:logback-classic") ||
                printed.contains("org.apache.logging.log4j:log4j-slf4j2-impl")) {
                return MANAGED_PROVIDER_MESSAGE;
            }
        }
        return null;
    }

    private static Coordinate invocationCoordinate(J.MethodInvocation invocation, Cursor cursor) {
        Coordinate string = invocation.getArguments().stream().filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue).filter(String.class::isInstance)
                .map(String.class::cast).map(FindUnsafeJulToSlf4jTopology::parseCoordinate)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (string != null) return string;
        String group = mapValue(invocation, "group");
        String artifact = mapValue(invocation, "name");
        if (group == null || artifact == null) return null;
        String version = mapValue(invocation, "version");
        if (version == null) version = "";
        return new Coordinate(group, artifact, version, version, true,
                AbstractSelectedSlf4jDependencyRecipe.hasGradleVariant(invocation));
    }

    private static Coordinate parseCoordinate(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length == 2) return new Coordinate(parts[0], parts[1], "", "", true, false);
        if (parts.length >= 3) {
            return new Coordinate(parts[0], parts[1], parts[2], parts[2], true, parts.length > 3);
        }
        return null;
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

    private static Map<String, String> rootProperties(Xml.Document document) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property -> property.getValue()
                        .ifPresent(value -> properties.put(property.getName(), value.trim()))));
        return properties;
    }

    private static String resolve(String declared, Map<String, String> properties) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(declared);
        return matcher.matches() ? properties.getOrDefault(matcher.group(1), declared) : declared;
    }

    private record Coordinate(String group, String artifact, String version, String declared, boolean active,
                              boolean variant) {
        String key() {
            return group + ":" + artifact;
        }
    }

    private record Topology(boolean hasCoreReference, boolean hasKnownBridge, Set<String> artifacts,
                            int providerCount) {
        static Topology from(List<Coordinate> coordinates) {
            Set<String> artifacts = new HashSet<>();
            boolean core = false;
            boolean known = false;
            for (Coordinate coordinate : coordinates) {
                if (coordinate.active) artifacts.add(coordinate.key());
                if ("org.slf4j:jul-to-slf4j".equals(coordinate.key())) {
                    core = true;
                    if (coordinate.active && !coordinate.variant && BRIDGE_VERSIONS.contains(coordinate.version)) {
                        known = true;
                    }
                }
            }
            int providers = (int) artifacts.stream().filter(PROVIDERS::contains).count();
            return new Topology(core, known, Set.copyOf(artifacts), providers);
        }

        Topology withDynamicCore(boolean dynamicCore) {
            return dynamicCore ? new Topology(true, hasKnownBridge, artifacts, providerCount) : this;
        }

        boolean hasLog4jLoop() {
            return artifacts.contains("org.apache.logging.log4j:log4j-to-slf4j") &&
                   (artifacts.contains("org.apache.logging.log4j:log4j-slf4j-impl") ||
                    artifacts.contains("org.apache.logging.log4j:log4j-slf4j2-impl"));
        }

        boolean hasJclLoop() {
            return artifacts.contains("org.slf4j:jcl-over-slf4j") && artifacts.contains("org.slf4j:slf4j-jcl");
        }

        boolean hasReload4jLoop() {
            return artifacts.contains("org.slf4j:log4j-over-slf4j") &&
                   (artifacts.contains("org.slf4j:slf4j-reload4j") ||
                    artifacts.contains("org.slf4j:slf4j-log4j12"));
        }
    }
}
