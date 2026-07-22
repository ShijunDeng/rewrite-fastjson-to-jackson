package com.huawei.clouds.openrewrite.jasypt;

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

/** Marks build baselines and externally managed versions that block a safe Jasypt 4 run. */
public final class FindJasyptBuildCompatibilityRisks extends Recipe {
    private static final String GROUP = "com.github.ulisesbocchio";
    private static final String ARTIFACT = "jasypt-spring-boot-starter";
    private static final String PREFIX = GROUP + ":" + ARTIFACT;
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.target", "maven.compiler.source"
    );

    @Override
    public String getDisplayName() {
        return "Find Jasypt 4 build compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark visible Java versions below 17, Spring Boot versions below 3.5, externally managed starter declarations, and 2.x default-encryption compatibility work.";
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
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return visitPom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return inspectGradleLiteral(super.visitLiteral(literal, p), getCursor().getParentTreeCursor().getValue());
                        }

                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                            return inspectJavaVersion(super.visitFieldAccess(fieldAccess, p),
                                    getCursor().getParentTreeCursor().getValue());
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            return inspectJavaLanguageVersion(super.visitMethodInvocation(method, p),
                                    getCursor().getParentTreeCursor().getValue());
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return inspectGradleLiteral(super.visitLiteral(literal, p), getCursor().getParentTreeCursor().getValue());
                        }

                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                            return inspectJavaVersion(super.visitFieldAccess(fieldAccess, p),
                                    getCursor().getParentTreeCursor().getValue());
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            return inspectJavaLanguageVersion(super.visitMethodInvocation(method, p),
                                    getCursor().getParentTreeCursor().getValue());
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document visitPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                String tagValue = t.getValue().orElse("").trim();
                if (tagValue.contains("-Djasypt.encryptor.password=") ||
                    tagValue.contains("--jasypt.encryptor.password=")) {
                    return SearchResult.found(t,
                            "Jasypt password on a build command line may leak through process listings, CI logs, and diagnostics; use masked secret injection");
                }
                if (isJasyptMavenPluginSetting(t, getCursor()) &&
                    !externalReference(tagValue)) {
                    return SearchResult.found(t,
                            "Jasypt Maven plugin key material is visible in the POM; move it to a secret-store reference, suppress command echo, and rotate exposed values");
                }
                if (JAVA_PROPERTIES.contains(t.getName()) && belowJava17(t.getValue().orElse(null))) {
                    return SearchResult.found(t, "Jasypt 4.0.3 requires Java 17 or newer across build, runtime, container, and CI toolchains");
                }
                if ("parent".equals(t.getName()) && "org.springframework.boot".equals(t.getChildValue("groupId").orElse(null)) &&
                    "spring-boot-starter-parent".equals(t.getChildValue("artifactId").orElse(null)) &&
                    belowBoot35(resolve(t.getChildValue("version").orElse(null), properties))) {
                    return SearchResult.found(t, "Jasypt 4.0.3 requires Spring Boot 3.5.0 or newer");
                }
                if ("dependency".equals(t.getName())) {
                    String group = t.getChildValue("groupId").orElse(null);
                    String artifact = t.getChildValue("artifactId").orElse(null);
                    String visibleVersion = resolve(t.getChildValue("version").orElse(null), properties);
                    if ("org.springframework.boot".equals(group) && "spring-boot-dependencies".equals(artifact) &&
                        belowBoot35(visibleVersion)) {
                        return SearchResult.found(t, "Imported Spring Boot BOM is below the Jasypt 4.0.3 minimum of 3.5.0");
                    }
                    if (GROUP.equals(group) && ARTIFACT.equals(artifact)) {
                        if (t.getChildValue("version").isEmpty()) {
                            return SearchResult.found(t, "Starter version is externally managed; the strict upgrade recipe will not override an invisible parent/BOM value");
                        }
                        if ("2.1.1".equals(visibleVersion) || "2.1.2".equals(visibleVersion)) {
                            return SearchResult.found(t, "Jasypt 2.x ciphertext used PBEWithMD5AndDES and NoIvGenerator by default; declare the legacy pair explicitly for compatibility, then re-encrypt with the target defaults");
                        }
                    }
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static J.Literal inspectGradleLiteral(J.Literal literal, Object parent) {
        if (literal.getValue() instanceof String value) {
            if (value.contains("-Djasypt.encryptor.password=") ||
                value.contains("--jasypt.encryptor.password=")) {
                return SearchResult.found(literal,
                        "Jasypt password on a Gradle command line may leak through process listings, CI logs, and diagnostics; use masked secret injection");
            }
            if (value.equals(PREFIX)) {
                return SearchResult.found(literal,
                        "Starter version is externally managed; the strict upgrade recipe will not override an invisible platform/catalog value");
            }
            if ((value.equals(PREFIX + ":2.1.1") || value.equals(PREFIX + ":2.1.2"))) {
                return SearchResult.found(literal,
                        "Jasypt 2.x ciphertext used PBEWithMD5AndDES and NoIvGenerator by default; preserve that pair explicitly before re-encryption");
            }
            if (parent instanceof J.MethodInvocation invocation && "version".equals(invocation.getSimpleName()) &&
                invocation.printTrimmed().contains("org.springframework.boot") && belowBoot35(value)) {
                return SearchResult.found(literal, "Jasypt 4.0.3 requires Spring Boot 3.5.0 or newer");
            }
        }
        if (parent instanceof J.Assignment assignment && isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            belowJava17(String.valueOf(literal.getValue()))) {
            return SearchResult.found(literal, "Jasypt 4.0.3 requires Java 17 or newer");
        }
        return literal;
    }

    private static J.FieldAccess inspectJavaVersion(J.FieldAccess fieldAccess, Object parent) {
        String text = fieldAccess.printTrimmed();
        if (parent instanceof J.Assignment assignment && isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            text.startsWith("JavaVersion.VERSION_") && belowJava17(text.substring("JavaVersion.VERSION_".length()))) {
            return SearchResult.found(fieldAccess, "Jasypt 4.0.3 requires Java 17 or newer");
        }
        return fieldAccess;
    }

    private static J.MethodInvocation inspectJavaLanguageVersion(J.MethodInvocation invocation, Object parent) {
        if (!(parent instanceof J.Assignment assignment) ||
            !assignment.getVariable().printTrimmed().endsWith("languageVersion") ||
            !"of".equals(invocation.getSimpleName()) || invocation.getSelect() == null ||
            !"JavaLanguageVersion".equals(invocation.getSelect().printTrimmed()) || invocation.getArguments().size() != 1) {
            return invocation;
        }
        if (invocation.getArguments().get(0) instanceof J.Literal literal &&
            belowJava17(String.valueOf(literal.getValue()))) {
            return SearchResult.found(invocation, "Jasypt 4.0.3 requires Java 17 or newer");
        }
        return invocation;
    }

    private static boolean isJavaCompatibility(String name) {
        return "sourceCompatibility".equals(name) || "targetCompatibility".equals(name) ||
               name.endsWith("languageVersion");
    }

    private static String resolve(String version, Map<String, String> properties) {
        if (version != null && version.startsWith("${") && version.endsWith("}")) {
            return properties.get(version.substring(2, version.length() - 1));
        }
        return version;
    }

    private static boolean belowJava17(String value) {
        if (value == null || value.contains("${")) {
            return false;
        }
        String normalized = value.trim().replace("VERSION_", "");
        if (normalized.startsWith("1.")) {
            normalized = normalized.substring(2);
        }
        try {
            return Integer.parseInt(normalized.replaceAll("[^0-9].*", "")) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean belowBoot35(String value) {
        if (value == null || value.contains("${")) {
            return false;
        }
        String[] parts = value.split("[.-]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major < 3 || major == 3 && minor < 5;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isJasyptMavenPluginSetting(Xml.Tag tag, Cursor cursor) {
        if (!Set.of("password", "oldPassword", "newPassword").contains(tag.getName())) {
            return false;
        }
        return cursor.getPathAsStream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .anyMatch(ancestor -> "plugin".equals(ancestor.getName()) &&
                        "com.github.ulisesbocchio".equals(ancestor.getChildValue("groupId").orElse(null)) &&
                        "jasypt-maven-plugin".equals(ancestor.getChildValue("artifactId").orElse(null)));
    }

    private static boolean externalReference(String value) {
        return value.matches("\\$\\{[A-Za-z0-9_.-]+}");
    }
}
