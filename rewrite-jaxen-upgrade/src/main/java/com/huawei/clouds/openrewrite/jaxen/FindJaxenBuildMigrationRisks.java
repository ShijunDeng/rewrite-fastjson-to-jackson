package com.huawei.clouds.openrewrite.jaxen;

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

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds exact version ownership, artifact-shape and Java baseline risks. */
public final class FindJaxenBuildMigrationRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:[.][0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Pattern TARGET_GRADLE_PROPERTY = Pattern.compile(
            "(?m)^\\s*(?:(?:def|val|var)\\s+)?(?:(?:rootProject\\.)?ext\\.)?" +
            "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(['\"])2[.]0[.]1\\2\\s*$");
    private static final Pattern INTERPOLATED = Pattern.compile(
            "jaxen:jaxen:\\$(?:\\{(?:rootProject[.]ext[.])?)?([A-Za-z_$][A-Za-z0-9_$]*)}?");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final String JAVA_MESSAGE =
            "Jaxen 2 requires Java 1.5 or newer; align compiler/toolchain, test and runtime JVMs (the 2.0.1 release artifact was built with JDK 8)";
    private static final String OWNER_MESSAGE =
            "This Jaxen version is absent, property/BOM/catalog-managed, ranged, dynamic or outside a standard owned dependency declaration; migrate the actual owner and verify that 2.0.1 resolves";
    private static final String VERSION_MESSAGE =
            "This fixed Jaxen version is outside the workbook source set or still not 2.0.1; choose its migration path explicitly rather than widening AUTO";
    private static final String VARIANT_MESSAGE =
            "This classified or non-JAR Jaxen artifact is outside deterministic scope; verify that 2.0.1 publishes the required artifact shape";

    @Override
    public String getDisplayName() {
        return "Find Jaxen 2.0.1 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java levels below 1.5 and unresolved, out-of-scope or nonstandard Jaxen dependency ownership.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxenSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            String value = t.getValue().map(String::trim).orElse("");
                            if (JaxenSupport.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_PROPERTIES.contains(t.getName()) && below5(value)) {
                                return JaxenSupport.mark(t, JAVA_MESSAGE);
                            }
                            if (Set.of("source", "target", "release").contains(t.getName()) && below5(value) &&
                                insideCompilerPlugin(getCursor())) return JaxenSupport.mark(t, JAVA_MESSAGE);
                            if (!JaxenSupport.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            String message = risk(group, artifact, version,
                                    t.getChild("classifier").isPresent() ||
                                    !"jar".equals(t.getChildValue("type").orElse("jar")));
                            return message == null ? t : markVersionOrOwner(t, message);
                        }
                    }.visitNonNull(document, ctx);
                }
                Set<String> resolvedProperties = resolvedTargetProperties(source);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                            J.Assignment a = super.visitAssignment(assignment, executionContext);
                            return legacyJavaAssignment(a, getCursor()) ? JaxenSupport.mark(a, JAVA_MESSAGE) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!dependency) return m;
                            String message = risk(JaxenSupport.mapValue(m, "group"), JaxenSupport.mapValue(m, "name"),
                                    JaxenSupport.mapValue(m, "version"), JaxenSupport.hasVariant(m));
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                if (map != null) message = risk(JaxenSupport.mapValue(map, "group"),
                                        JaxenSupport.mapValue(map, "name"), JaxenSupport.mapValue(map, "version"),
                                        JaxenSupport.hasVariant(map));
                            }
                            if (message == null) message = interpolatedRisk(m.printTrimmed(getCursor()), resolvedProperties);
                            return message == null ? m : JaxenSupport.mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            String message = dependency ? coordinateRisk(l.getValue()) : null;
                            return message == null ? l : JaxenSupport.mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!dependency) return m;
                            String message = interpolatedRisk(m.printTrimmed(getCursor()), resolvedProperties);
                            return message == null ? m : JaxenSupport.mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            String message = dependency ? coordinateRisk(l.getValue()) : null;
                            return message == null ? l : JaxenSupport.mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Set<String> resolvedTargetProperties(SourceFile source) {
        if (!JaxenSupport.rootGradle(source.getSourcePath())) return Set.of();
        java.util.Set<String> properties = new java.util.HashSet<>();
        Matcher matcher = TARGET_GRADLE_PROPERTY.matcher(source.printAll());
        while (matcher.find()) properties.add(matcher.group(1));
        return properties;
    }

    private static String interpolatedRisk(String source, Set<String> resolvedProperties) {
        Matcher matcher = INTERPOLATED.matcher(source);
        if (!matcher.find()) return null;
        return resolvedProperties.contains(matcher.group(1)) ? null : OWNER_MESSAGE;
    }

    private static String coordinateRisk(Object literal) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) {
            return JaxenSupport.GROUP.equals(parts[0]) && JaxenSupport.ARTIFACT.equals(parts[1])
                    ? VARIANT_MESSAGE : null;
        }
        return risk(parts[0], parts[1], parts.length == 3 ? parts[2] : null, false);
    }

    private static String risk(String group, String artifact, String version, boolean variant) {
        if (!JaxenSupport.GROUP.equals(group) || !JaxenSupport.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT_MESSAGE;
        if (version == null || !FIXED.matcher(version).matches()) return OWNER_MESSAGE;
        return JaxenSupport.TARGET.equals(version) ? null : VERSION_MESSAGE;
    }

    private static boolean below5(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 5;
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        if (below5(value)) return true;
        Matcher matcher = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 5;
    }

    private static boolean insideCompilerPlugin(Cursor cursor) {
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof Xml.Tag tag && "plugin".equals(tag.getName())) {
                return "org.apache.maven.plugins".equals(tag.getChildValue("groupId")
                               .orElse("org.apache.maven.plugins")) &&
                       "maven-compiler-plugin".equals(tag.getChildValue("artifactId").orElse(""));
            }
        }
        return false;
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag tag, String message) {
        return tag.getChild("version").map(version -> {
            Xml.Tag marked = JaxenSupport.mark(version, message);
            if (marked == version) return tag;
            return tag.withContent(tag.getContent().stream().map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> JaxenSupport.mark(tag, message));
    }
}
