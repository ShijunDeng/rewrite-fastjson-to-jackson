package com.huawei.clouds.openrewrite.jaxbimpl;

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

/** Marks exact build ownership, Java baseline, duplicate provider and companion alignment risks. */
public final class FindJaxbImplBuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("com.sun.xml.bind:jaxb-core", "4.0.6"),
            Map.entry("com.sun.xml.bind:jaxb-xjc", "4.0.6"),
            Map.entry("com.sun.xml.bind:jaxb-jxc", "4.0.6"),
            Map.entry("org.glassfish.jaxb:jaxb-bom", "4.0.6"),
            Map.entry("jakarta.xml.bind:jakarta.xml.bind-api", "4.0.4"),
            Map.entry("jakarta.activation:jakarta.activation-api", "2.1.4"),
            Map.entry("org.eclipse.angus:angus-activation", "2.0.3"));
    private static final String JAVA_MESSAGE =
            "JAXB RI 4.0.6 requires Java 11 or newer for build, XJC and runtime; align compiler/toolchain, CI, container and deployment JVM";
    private static final String OWNER_MESSAGE =
            "This jaxb-impl version is absent, property/BOM/catalog-managed, ranged or dynamic; migrate the actual owner and verify that 4.0.6 resolves instead of injecting a local guess";
    private static final String VERSION_MESSAGE =
            "This fixed jaxb-impl version is outside the workbook source set or still not 4.0.6; choose its migration path explicitly rather than widening the automatic recipe";
    private static final String VARIANT_MESSAGE =
            "This classified or non-JAR jaxb-impl artifact is outside deterministic scope; verify that 4.0.6 publishes the required artifact shape";
    private static final String LEGACY_MESSAGE =
            "Legacy Javax JAXB/Activation API cannot be mixed with JAXB 4; migrate coordinates, imports, generated sources and all consumers together";
    private static final String DUPLICATE_MESSAGE =
            "jaxb-impl is the bundled RI implementation; an additional jaxb-runtime/provider can duplicate implementation classes or providers, so select one deployment shape and verify classloader discovery";
    private static final String ALIGN_MESSAGE =
            "This explicit JAXB/Activation companion is not aligned with the JAXB RI 4.0.6 BOM; align its real owner and regenerate/recompile consumers";
    private static final String PLUGIN_MESSAGE =
            "JAXB/XJC build plugin detected; select a Java 11/JAXB 4 compatible plugin, align XJC/JXC 4.0.6, clean generated sources, then diff and recompile generated code";

    @Override
    public String getDisplayName() {
        return "Find JAXB implementation 4.0.6 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark only owned Maven/Gradle declarations with Java below 11, unresolved jaxb-impl ownership, " +
               "nonstandard variants, legacy/misaligned companions, duplicate providers, or JAXB code generation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxbImplSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            String value = t.getValue().map(String::trim).orElse("");
                            if (JaxbImplSupport.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_PROPERTIES.contains(t.getName()) && below11(value)) {
                                return JaxbImplSupport.mark(t, JAVA_MESSAGE);
                            }
                            if (Set.of("source", "target", "release").contains(t.getName()) && below11(value) &&
                                insideCompilerPlugin(getCursor())) return JaxbImplSupport.mark(t, JAVA_MESSAGE);
                            if ("plugin".equals(t.getName()) && isJaxbPlugin(getCursor(), t)) {
                                return JaxbImplSupport.mark(t, PLUGIN_MESSAGE);
                            }
                            if (!JaxbImplSupport.isProjectDependency(getCursor(), t)) return t;
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
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                            J.Assignment a = super.visitAssignment(assignment, executionContext);
                            return legacyJavaAssignment(a, getCursor()) ? JaxbImplSupport.mark(a, JAVA_MESSAGE) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean dependency = JaxbImplSupport.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!dependency) return m;
                            String message = risk(JaxbImplSupport.mapValue(m, "group"),
                                    JaxbImplSupport.mapValue(m, "name"), JaxbImplSupport.mapValue(m, "version"),
                                    JaxbImplSupport.hasVariant(m));
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                if (map != null) message = risk(JaxbImplSupport.mapValue(map, "group"),
                                        JaxbImplSupport.mapValue(map, "name"), JaxbImplSupport.mapValue(map, "version"),
                                        JaxbImplSupport.hasVariant(map));
                            }
                            return message == null ? m : JaxbImplSupport.mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = JaxbImplSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            String message = dependency ? coordinateRisk(l.getValue()) : null;
                            return message == null ? l : JaxbImplSupport.mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = JaxbImplSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            String message = dependency ? coordinateRisk(l.getValue()) : null;
                            return message == null ? l : JaxbImplSupport.mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String coordinateRisk(Object literal) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) {
            return JaxbImplSupport.GROUP.equals(parts[0]) && JaxbImplSupport.ARTIFACT.equals(parts[1])
                    ? VARIANT_MESSAGE : null;
        }
        return risk(parts[0], parts[1], parts.length == 3 ? parts[2] : null, false);
    }

    private static String risk(String group, String artifact, String version, boolean variant) {
        if (group == null || artifact == null) return null;
        String coordinate = group + ":" + artifact;
        if (JaxbImplSupport.GROUP.equals(group) && JaxbImplSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT_MESSAGE;
            if (version == null || !FIXED.matcher(version).matches()) return OWNER_MESSAGE;
            return JaxbImplSupport.TARGET.equals(version) ? null : VERSION_MESSAGE;
        }
        if ("javax.xml.bind".equals(group) || "javax.activation".equals(group)) return LEGACY_MESSAGE;
        if ("org.glassfish.jaxb:jaxb-runtime".equals(coordinate)) return DUPLICATE_MESSAGE;
        String expected = EXPECTED.get(coordinate);
        if (expected == null) return null;
        return expected.equals(version) ? null : ALIGN_MESSAGE;
    }

    private static boolean below11(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 11;
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        if (below11(value)) return true;
        Matcher matcher = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 11;
    }

    private static boolean insideCompilerPlugin(Cursor cursor) {
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof Xml.Tag tag && "plugin".equals(tag.getName())) {
                return "org.apache.maven.plugins".equals(tag.getChildValue("groupId").orElse("org.apache.maven.plugins")) &&
                       "maven-compiler-plugin".equals(tag.getChildValue("artifactId").orElse(""));
            }
        }
        return false;
    }

    private static boolean isJaxbPlugin(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag plugins) || !"plugins".equals(plugins.getName())) return false;
        String artifact = tag.getChildValue("artifactId").orElse("").toLowerCase(java.util.Locale.ROOT);
        return artifact.contains("jaxb") || artifact.contains("xjc");
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag tag, String message) {
        return tag.getChild("version").map(version -> {
            Xml.Tag marked = JaxbImplSupport.mark(version, message);
            if (marked == version) return tag;
            return tag.withContent(tag.getContent().stream().map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> JaxbImplSupport.mark(tag, message));
    }
}
