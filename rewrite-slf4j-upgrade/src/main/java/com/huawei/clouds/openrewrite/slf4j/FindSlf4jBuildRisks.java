package com.huawei.clouds.openrewrite.slf4j;

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

import java.util.HashSet;
import java.util.Set;

/** Marks build configuration that cannot be safely selected during an SLF4J 2 migration. */
public final class FindSlf4jBuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final Set<String> FIRST_PARTY_PROVIDERS = Set.of(
            "slf4j-simple", "slf4j-nop", "slf4j-jdk14", "slf4j-reload4j", "slf4j-log4j12",
            "slf4j-jcl", "slf4j-android");

    @Override
    public String getDisplayName() {
        return "Find SLF4J 2 build and provider risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java baselines below 8, incompatible or unresolved providers, multiple providers, and known " +
               "logging bridge recursion loops in Maven and direct Gradle dependency declarations.";
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
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName) &&
                    containsSlf4jApi(document.getRoot())) {
                    Set<Coordinate> dependencies = new HashSet<>();
                    collectMavenDependencies(document.getRoot(), document.getRoot(), false, dependencies);
                    Topology topology = Topology.from(dependencies);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            String value = t.getValue().map(String::trim).orElse("");
                            if (JAVA_PROPERTIES.contains(t.getName()) && isJavaBelow8(value)) {
                                return SearchResult.found(t,
                                        "SLF4J 2 requires Java 8 or newer; upgrade compiler and runtime before changing the logging stack");
                            }
                            if ("plugin".equals(t.getName()) &&
                                "maven-shade-plugin".equals(t.getChildValue("artifactId").orElse("")) &&
                                !containsValue(t,
                                        "org.apache.maven.plugins.shade.resource.ServicesResourceTransformer")) {
                                return SearchResult.found(t,
                                        "Shaded jars must merge META-INF/services; add and verify ServicesResourceTransformer for the SLF4J provider descriptor");
                            }
                            if (!"dependency".equals(t.getName())) {
                                return t;
                            }
                            Coordinate coordinate = mavenCoordinate(document.getRoot(), t);
                            String message = topology.riskFor(coordinate);
                            return message == null ? t : SearchResult.found(t, message);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle") &&
                    compilationUnit.printAll().contains("org.slf4j:slf4j-api:")) {
                    Set<Coordinate> dependencies = collectGroovyDependencies(compilationUnit, ctx);
                    Topology topology = Topology.from(dependencies);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = MigrateSlf4jProviderArtifacts.isDirectGradleDependencyLiteral(getCursor());
                            boolean javaVersion = isJavaCompatibilityLiteral(getCursor(), literal);
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            if (javaVersion && l.getValue() instanceof String version && isJavaBelow8(version)) {
                                return SearchResult.found(l,
                                        "SLF4J 2 requires Java 8 or newer; upgrade Gradle toolchain and runtime");
                            }
                            if (!dependency || !(l.getValue() instanceof String value)) {
                                return l;
                            }
                            String message = topology.riskFor(Coordinate.parse(value));
                            return message == null ? l : SearchResult.found(l, message);
                        }

                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext executionContext) {
                            J.FieldAccess f = super.visitFieldAccess(fieldAccess, executionContext);
                            return isJavaCompatibilityAssignment(getCursor()) &&
                                   f.printTrimmed(getCursor()).matches("JavaVersion\\.VERSION_1_[0-7]")
                                    ? SearchResult.found(f,
                                    "SLF4J 2 requires Java 8 or newer; upgrade Gradle toolchain and runtime") : f;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts") &&
                    compilationUnit.printAll().contains("org.slf4j:slf4j-api:")) {
                    Set<Coordinate> dependencies = collectKotlinDependencies(compilationUnit, ctx);
                    Topology topology = Topology.from(dependencies);
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = MigrateSlf4jProviderArtifacts.isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            if (!dependency || !(l.getValue() instanceof String value)) {
                                return l;
                            }
                            String message = topology.riskFor(Coordinate.parse(value));
                            return message == null ? l : SearchResult.found(l, message);
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Set<Coordinate> collectGroovyDependencies(G.CompilationUnit source, ExecutionContext ctx) {
        Set<Coordinate> dependencies = new HashSet<>();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (MigrateSlf4jProviderArtifacts.isDirectGradleDependencyLiteral(getCursor()) &&
                    literal.getValue() instanceof String value) {
                    dependencies.add(Coordinate.parse(value));
                }
                return super.visitLiteral(literal, executionContext);
            }
        }.visit(source, ctx);
        dependencies.remove(null);
        return dependencies;
    }

    private static Set<Coordinate> collectKotlinDependencies(K.CompilationUnit source, ExecutionContext ctx) {
        Set<Coordinate> dependencies = new HashSet<>();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (MigrateSlf4jProviderArtifacts.isDirectGradleDependencyLiteral(getCursor()) &&
                    literal.getValue() instanceof String value) {
                    dependencies.add(Coordinate.parse(value));
                }
                return super.visitLiteral(literal, executionContext);
            }
        }.visit(source, ctx);
        dependencies.remove(null);
        return dependencies;
    }

    private static boolean isJavaCompatibilityLiteral(Cursor cursor, J.Literal literal) {
        return literal.getValue() instanceof String && isJavaCompatibilityAssignment(cursor);
    }

    private static boolean isJavaCompatibilityAssignment(Cursor cursor) {
        J.Assignment assignment = cursor.firstEnclosing(J.Assignment.class);
        if (assignment == null) {
            return false;
        }
        String variable = assignment.getVariable().printTrimmed(cursor);
        return variable.endsWith("sourceCompatibility") || variable.endsWith("targetCompatibility");
    }

    private static boolean containsSlf4jApi(Xml.Tag tag) {
        if ("dependency".equals(tag.getName()) && UpgradeStrictSlf4jMavenDependency.isSlf4jApi(tag)) {
            return true;
        }
        return tag.getChildren().stream().anyMatch(FindSlf4jBuildRisks::containsSlf4jApi);
    }

    private static void collectMavenDependencies(Xml.Tag tag, Xml.Tag root, boolean managed,
                                                 Set<Coordinate> dependencies) {
        boolean insideManagement = managed || "dependencyManagement".equals(tag.getName());
        if ("dependency".equals(tag.getName()) && !insideManagement) {
            dependencies.add(mavenCoordinate(root, tag));
        }
        tag.getChildren().forEach(child -> collectMavenDependencies(child, root, insideManagement, dependencies));
    }

    private static Coordinate mavenCoordinate(Xml.Tag root, Xml.Tag dependency) {
        String rawVersion = dependency.getChildValue("version").map(String::trim).orElse("");
        String propertyName = UpgradeStrictSlf4jMavenDependency.propertyName(rawVersion);
        String version = propertyName == null ? rawVersion :
                UpgradeStrictSlf4jMavenDependency.propertyValue(root, propertyName);
        return new Coordinate(dependency.getChildValue("groupId").orElse(""),
                dependency.getChildValue("artifactId").orElse(""), version);
    }

    private static boolean isJavaBelow8(String version) {
        if (version.isBlank() || version.startsWith("${")) {
            return false;
        }
        String normalized = version.startsWith("1.") ? version.substring(2) : version;
        try {
            return Integer.parseInt(normalized.split("[.-]", 2)[0]) < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean containsValue(Xml.Tag tag, String value) {
        if (value.equals(tag.getValue().map(String::trim).orElse(""))) {
            return true;
        }
        if (tag.getAttributes().stream().anyMatch(attribute -> value.equals(attribute.getValueAsString()))) {
            return true;
        }
        return tag.getChildren().stream().anyMatch(child -> containsValue(child, value));
    }

    private record Coordinate(String group, String artifact, String version) {
        static Coordinate parse(String coordinate) {
            String[] parts = coordinate.split(":", -1);
            return parts.length < 3 ? null : new Coordinate(parts[0], parts[1], parts[2]);
        }

        String identity() {
            return group + ":" + artifact;
        }
    }

    private record Topology(Set<String> identities, Set<String> providers) {
        static Topology from(Set<Coordinate> dependencies) {
            Set<String> identities = new HashSet<>();
            Set<String> providers = new HashSet<>();
            for (Coordinate coordinate : dependencies) {
                if (coordinate == null) {
                    continue;
                }
                identities.add(coordinate.identity());
                if (isProvider(coordinate)) {
                    providers.add(coordinate.identity());
                }
            }
            return new Topology(identities, providers);
        }

        String riskFor(Coordinate coordinate) {
            if (coordinate == null) {
                return null;
            }
            String identity = coordinate.identity();
            if (has("org.slf4j:jul-to-slf4j") && has("org.slf4j:slf4j-jdk14") &&
                ("org.slf4j:jul-to-slf4j".equals(identity) || "org.slf4j:slf4j-jdk14".equals(identity))) {
                return "jul-to-slf4j and slf4j-jdk14 form a JUL/SLF4J recursion loop; retain only the required direction";
            }
            if (has("org.apache.logging.log4j:log4j-to-slf4j") &&
                (has("org.apache.logging.log4j:log4j-slf4j-impl") ||
                 has("org.apache.logging.log4j:log4j-slf4j2-impl")) &&
                ("org.apache.logging.log4j:log4j-to-slf4j".equals(identity) ||
                 "org.apache.logging.log4j:log4j-slf4j-impl".equals(identity) ||
                 "org.apache.logging.log4j:log4j-slf4j2-impl".equals(identity))) {
                return "Log4j-to-SLF4J and SLF4J-to-Log4j are both present and can recurse; select one routing direction";
            }
            if (has("org.slf4j:jcl-over-slf4j") && has("org.slf4j:slf4j-jcl") &&
                ("org.slf4j:jcl-over-slf4j".equals(identity) || "org.slf4j:slf4j-jcl".equals(identity))) {
                return "jcl-over-slf4j and slf4j-jcl form a Commons Logging recursion loop; retain only one direction";
            }
            if (has("org.slf4j:log4j-over-slf4j") &&
                (has("org.slf4j:slf4j-log4j12") || has("org.slf4j:slf4j-reload4j")) &&
                ("org.slf4j:log4j-over-slf4j".equals(identity) ||
                 "org.slf4j:slf4j-log4j12".equals(identity) ||
                 "org.slf4j:slf4j-reload4j".equals(identity))) {
                return "log4j-over-slf4j with an SLF4J-to-Log4j provider can recurse; retain only one direction";
            }
            if (providers.size() > 1 && providers.contains(identity)) {
                return "Multiple SLF4J providers are declared; retain exactly one runtime provider after dependency resolution";
            }
            if ("org.slf4j:slf4j-api".equals(identity) && coordinate.version.startsWith("1.")) {
                return "This SLF4J 1.x API version is outside the spreadsheet's explicit source set and was not upgraded automatically";
            }
            if ("org.slf4j:slf4j-log4j12".equals(identity) || "org.slf4j:slf4j-jcl".equals(identity) ||
                "org.slf4j:slf4j-android".equals(identity)) {
                return "This legacy SLF4J binding requires an application-specific SLF4J 2 provider replacement";
            }
            if ("org.apache.logging.log4j:log4j-slf4j-impl".equals(identity)) {
                return "log4j-slf4j-impl targets SLF4J 1.x; upgrade Log4j to 2.19+ and use log4j-slf4j2-impl";
            }
            if (isProvider(coordinate) && (coordinate.version.isBlank() || coordinate.version.contains("$"))) {
                return "This provider version is externally managed or computed; verify that it explicitly supports SLF4J 2";
            }
            if (targetsSlf4j1(coordinate)) {
                return "This provider targets SLF4J 1.x; select a provider explicitly compatible with SLF4J 2";
            }
            return null;
        }

        private boolean has(String identity) {
            return identities.contains(identity);
        }

        private static boolean isProvider(Coordinate coordinate) {
            return "org.slf4j".equals(coordinate.group) && FIRST_PARTY_PROVIDERS.contains(coordinate.artifact) ||
                   "ch.qos.logback".equals(coordinate.group) && "logback-classic".equals(coordinate.artifact) ||
                   "org.apache.logging.log4j".equals(coordinate.group) &&
                   ("log4j-slf4j-impl".equals(coordinate.artifact) ||
                    "log4j-slf4j2-impl".equals(coordinate.artifact));
        }

        private static boolean targetsSlf4j1(Coordinate coordinate) {
            if ("org.slf4j".equals(coordinate.group) && FIRST_PARTY_PROVIDERS.contains(coordinate.artifact)) {
                return coordinate.version.startsWith("1.");
            }
            if ("ch.qos.logback".equals(coordinate.group) && "logback-classic".equals(coordinate.artifact)) {
                String[] parts = coordinate.version.split("[.-]", 3);
                try {
                    return Integer.parseInt(parts[0]) == 1 && parts.length > 1 && Integer.parseInt(parts[1]) <= 2;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }
    }
}
