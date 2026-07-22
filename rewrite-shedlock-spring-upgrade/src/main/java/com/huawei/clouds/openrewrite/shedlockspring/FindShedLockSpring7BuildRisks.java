package com.huawei.clouds.openrewrite.shedlockspring;

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
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Build-time compatibility markers for ShedLock Spring 7.2.1. */
public final class FindShedLockSpring7BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1\\.)?(\\d+)");
    private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d+)[.](\\d+).*?");

    @Override
    public String getDisplayName() {
        return "Find ShedLock Spring 7 build compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java versions below 17, unsupported Spring/Boot and javax baselines, external or unlisted " +
               "ShedLock Spring management, misaligned core/providers/BOM, and obsolete Spring XML configuration.";
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
                if (tree instanceof Xml.Document document) {
                    if ("pom.xml".equals(fileName) && containsShedLockSpring(document.getRoot())) {
                        return pomRisks(document, ctx);
                    }
                    if (!"pom.xml".equals(fileName) && document.printAll().contains("net.javacrumbs.shedlock")) {
                        return SearchResult.found(document,
                                "ShedLock Spring XML configuration has been unsupported since 3.x; replace it with Java configuration before 7.2.1");
                    }
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle") &&
                    groovy.printAll().contains("net.javacrumbs.shedlock:shedlock-spring")) {
                    return gradleGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts") &&
                    kotlin.printAll().contains("net.javacrumbs.shedlock:shedlock-spring")) {
                    return gradleKotlin(kotlin, ctx);
                }
                if (tree instanceof PlainText text && isApplicationProperties(text) &&
                    text.getText().matches("(?s).*spring[.]threads[.]virtual[.]enabled\\s*=\\s*true.*")) {
                    return SearchResult.found(text,
                            "Virtual-thread scheduling detected; verify ShedLock proxy boundaries and ThreadLocal LockAssert/LockExtender usage");
                }
                if (tree instanceof Yaml.Documents yaml && isApplicationYaml(yaml)) {
                    return yamlRisks(yaml, ctx);
                }
                return tree;
            }
        };
    }

    private static Yaml.Documents yamlRisks(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, executionContext);
                String value = e.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue().trim() : "";
                if ("true".equalsIgnoreCase(value) && isVirtualThreadKey(e, getCursor())) {
                    return SearchResult.found(e,
                            "Virtual-thread scheduling detected; verify ShedLock proxy boundaries and ThreadLocal LockAssert/LockExtender usage");
                }
                return e;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean isVirtualThreadKey(Yaml.Mapping.Entry entry, Cursor cursor) {
        String key = entry.getKey().getValue();
        if ("spring.threads.virtual.enabled".equals(key)) {
            return true;
        }
        if (!"enabled".equals(key)) {
            return false;
        }
        String[] expectedParents = {"virtual", "threads", "spring"};
        int matched = 0;
        for (Cursor parent = cursor.getParent(); parent != null && matched < expectedParents.length;
             parent = parent.getParent()) {
            if (parent.getValue() instanceof Yaml.Mapping.Entry parentEntry) {
                if (!expectedParents[matched].equals(parentEntry.getKey().getValue())) {
                    return false;
                }
                matched++;
            }
        }
        return matched == expectedParents.length;
    }

    private static Xml.Document pomRisks(Xml.Document document, ExecutionContext ctx) {
        Xml.Tag root = document.getRoot();
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                String value = t.getValue().map(String::trim).orElse("");
                if (JAVA_PROPERTIES.contains(t.getName()) && isJavaBelow17(value)) {
                    return SearchResult.found(t, "ShedLock 7 requires a Java 17+ compiler and runtime");
                }
                if ("parent".equals(t.getName()) && "org.springframework.boot".equals(t.getChildValue("groupId").orElse("")) &&
                    isSpringBootBelow34(resolve(root, t.getChildValue("version").orElse("")))) {
                    return SearchResult.found(t,
                            "ShedLock 7 supports Spring Boot 3.4/3.5/4.x and Spring Framework 6.2/7; upgrade the platform deliberately");
                }
                if (!"dependency".equals(t.getName())) {
                    return t;
                }
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String rawVersion = t.getChildValue("version").map(String::trim).orElse("");
                String version = resolve(root, rawVersion);
                if ("net.javacrumbs.shedlock".equals(group) && "shedlock-spring".equals(artifact)) {
                    if (rawVersion.isBlank()) {
                        return SearchResult.found(t,
                                "ShedLock Spring is externally managed; verify the resolved version is exactly 7.2.1 without overriding the platform locally");
                    }
                    if (!UpgradeSelectedShedLockSpringDependency.TARGET_VERSION.equals(version)) {
                        return SearchResult.found(t,
                                "This ShedLock Spring version is outside the spreadsheet's five explicit sources or cannot be resolved and was not upgraded automatically");
                    }
                    return t;
                }
                if ("net.javacrumbs.shedlock".equals(group) &&
                    ("shedlock-core".equals(artifact) || "shedlock-bom".equals(artifact) ||
                     artifact.startsWith("shedlock-provider-")) &&
                    !UpgradeSelectedShedLockSpringDependency.TARGET_VERSION.equals(version)) {
                    return SearchResult.found(t,
                            "Align ShedLock core, Spring integration, BOM and every provider to one compatible 7.2.1 line");
                }
                if ("org.springframework".equals(group) && artifact.startsWith("spring-") &&
                    isSpringFrameworkBelow62(version)) {
                    return SearchResult.found(t,
                            "ShedLock 7 supports the Spring Framework 6.2/7 line; align the framework or Boot BOM as a unit");
                }
                if ("org.springframework.boot".equals(group) && "spring-boot-dependencies".equals(artifact) &&
                    isSpringBootBelow34(version)) {
                    return SearchResult.found(t,
                            "ShedLock 7 supports Spring Boot 3.4/3.5/4.x; upgrade the imported BOM before runtime validation");
                }
                if (group.startsWith("javax.") || "javax".equals(group)) {
                    return SearchResult.found(t,
                            "Legacy javax dependency detected beside Spring 6.2/7; select the corresponding Jakarta API and compatible framework stack");
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
                return isLegacyJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return isLegacyToolchainCall(m)
                        ? SearchResult.found(m, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = UpgradeSelectedShedLockSpringDependency.isDirectGradleDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, executionContext);
                return dependency ? markCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit gradleKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return isLegacyJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return isLegacyToolchainCall(m)
                        ? SearchResult.found(m, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = UpgradeSelectedShedLockSpringDependency.isDirectGradleDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, executionContext);
                return dependency ? markCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String[] parts = value.split(":", -1);
        if (parts.length < 3) {
            return literal;
        }
        if ("net.javacrumbs.shedlock".equals(parts[0]) && "shedlock-spring".equals(parts[1]) &&
            !UpgradeSelectedShedLockSpringDependency.TARGET_VERSION.equals(parts[2])) {
            return SearchResult.found(literal,
                    "This ShedLock Spring version is outside the spreadsheet's explicit source set and was not upgraded automatically");
        }
        if ("net.javacrumbs.shedlock".equals(parts[0]) &&
            ("shedlock-core".equals(parts[1]) || "shedlock-bom".equals(parts[1]) ||
             parts[1].startsWith("shedlock-provider-")) &&
            !UpgradeSelectedShedLockSpringDependency.TARGET_VERSION.equals(parts[2])) {
            return SearchResult.found(literal,
                    "Align ShedLock core, Spring integration, BOM and every provider to one compatible 7.2.1 line");
        }
        if ("org.springframework".equals(parts[0]) && parts[1].startsWith("spring-") &&
            isSpringFrameworkBelow62(parts[2])) {
            return SearchResult.found(literal,
                    "ShedLock 7 supports the Spring Framework 6.2/7 line; align framework dependencies together");
        }
        if (parts[0].startsWith("javax.")) {
            return SearchResult.found(literal,
                    "Legacy javax dependency detected beside Spring 6.2/7; select the corresponding Jakarta API");
        }
        return literal;
    }

    private static boolean containsShedLockSpring(Xml.Tag tag) {
        return UpgradeSelectedShedLockSpringDependency.isShedLockSpring(tag) ||
               tag.getChildren().stream().anyMatch(FindShedLockSpring7BuildRisks::containsShedLockSpring);
    }

    private static String resolve(Xml.Tag root, String raw) {
        if (!raw.startsWith("${") || !raw.endsWith("}")) {
            return raw;
        }
        String property = raw.substring(2, raw.length() - 1);
        return root.getChild("properties").flatMap(properties -> properties.getChildValue(property))
                .map(String::trim).orElse(raw);
    }

    private static boolean isJavaBelow17(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value.trim());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 17;
    }

    private static boolean isSpringBootBelow34(String value) {
        Matcher matcher = MAJOR_MINOR.matcher(value.trim());
        if (!matcher.matches()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major < 3 || major == 3 && minor < 4;
    }

    private static boolean isSpringFrameworkBelow62(String value) {
        Matcher matcher = MAJOR_MINOR.matcher(value.trim());
        if (!matcher.matches()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major < 6 || major == 6 && minor < 2;
    }

    private static boolean isLegacyToolchainCall(J.MethodInvocation method) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof Number number) || number.intValue() >= 17) {
            return false;
        }
        return method.getSelect() != null && method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean isLegacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
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

    private static boolean isApplicationProperties(PlainText text) {
        String path = text.getSourcePath().toString().replace('\\', '/');
        return path.endsWith(".properties") && path.contains("application");
    }

    private static boolean isApplicationYaml(SourceFile source) {
        String path = source.getSourcePath().toString().replace('\\', '/');
        return (path.endsWith(".yml") || path.endsWith(".yaml")) && path.contains("application");
    }
}
