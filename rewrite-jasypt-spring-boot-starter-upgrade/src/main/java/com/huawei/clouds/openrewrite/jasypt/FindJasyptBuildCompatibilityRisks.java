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
    static final String UNSELECTED_MESSAGE =
            "Starter remains on an unselected, ranged, dynamic, property-managed, or non-target version; choose 4.0.3 explicitly or migrate its central owner";
    static final String CUSTOM_ARTIFACT_MESSAGE =
            "This classified or non-JAR starter artifact is outside deterministic runtime upgrade scope; verify that 4.0.3 publishes the same artifact shape before migrating it";

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
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !JasyptVersions.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return visitPom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    boolean hasStarter = hasGroovyStarter(cu, ctx);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return inspectGradleLiteral(super.visitLiteral(literal, p), getCursor(), hasStarter);
                        }

                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                            J.FieldAccess visited = super.visitFieldAccess(fieldAccess, p);
                            return hasStarter ? inspectJavaVersion(visited,
                                    getCursor().getParentTreeCursor().getValue()) : visited;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                            if (hasStarter) {
                                visited = inspectJavaLanguageVersion(visited,
                                        getCursor().getParentTreeCursor().getValue());
                            }
                            return inspectGroovyStarterMap(visited, getCursor());
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    boolean hasStarter = hasKotlinStarter(cu, ctx);
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return inspectGradleLiteral(super.visitLiteral(literal, p), getCursor(), hasStarter);
                        }

                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                            J.FieldAccess visited = super.visitFieldAccess(fieldAccess, p);
                            return hasStarter ? inspectJavaVersion(visited,
                                    getCursor().getParentTreeCursor().getValue()) : visited;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                            return hasStarter ? inspectJavaLanguageVersion(visited,
                                    getCursor().getParentTreeCursor().getValue()) : visited;
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document visitPom(Xml.Document document, ExecutionContext ctx) {
        boolean hasStarter = hasMavenStarter(document, ctx);
        Map<String, String> properties = localProperties(document, ctx);
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
                if (hasStarter && UpgradeSelectedJasyptStarterDependency.isRootProperty(getCursor(), t) &&
                    JAVA_PROPERTIES.contains(t.getName()) && belowJava17(t.getValue().orElse(null))) {
                    return SearchResult.found(t, "Jasypt 4.0.3 requires Java 17 or newer across build, runtime, container, and CI toolchains");
                }
                if (hasStarter && isProjectRootChild(getCursor(), t, "parent") &&
                    "org.springframework.boot".equals(t.getChildValue("groupId").orElse(null)) &&
                    "spring-boot-starter-parent".equals(t.getChildValue("artifactId").orElse(null)) &&
                    belowBoot35(resolve(t.getChildValue("version").orElse(null), properties))) {
                    return SearchResult.found(t, "Jasypt 4.0.3 requires Spring Boot 3.5.0 or newer");
                }
                if (UpgradeSelectedJasyptStarterDependency.isProjectDependency(getCursor(), t)) {
                    String group = t.getChildValue("groupId").orElse(null);
                    String artifact = t.getChildValue("artifactId").orElse(null);
                    String visibleVersion = resolve(t.getChildValue("version").orElse(null), properties);
                    if (hasStarter && "org.springframework.boot".equals(group) &&
                        "spring-boot-dependencies".equals(artifact) &&
                        belowBoot35(visibleVersion)) {
                        return SearchResult.found(t, "Imported Spring Boot BOM is below the Jasypt 4.0.3 minimum of 3.5.0");
                    }
                    if (GROUP.equals(group) && ARTIFACT.equals(artifact)) {
                        if (!UpgradeSelectedJasyptStarterDependency.isStandardStarter(getCursor(), t)) {
                            return SearchResult.found(t, CUSTOM_ARTIFACT_MESSAGE);
                        }
                        if (t.getChildValue("version").isEmpty()) {
                            return SearchResult.found(t, "Starter version is externally managed; the strict upgrade recipe will not override an invisible parent/BOM value");
                        }
                        if ("2.1.1".equals(visibleVersion) || "2.1.2".equals(visibleVersion)) {
                            return SearchResult.found(t, "Jasypt 2.x ciphertext used PBEWithMD5AndDES and NoIvGenerator by default; declare the legacy pair explicitly for compatibility, then re-encrypt with the target defaults");
                        }
                        if (!JasyptVersions.TARGET.equals(visibleVersion) &&
                            !JasyptVersions.isSource(visibleVersion)) {
                            return SearchResult.found(t, UNSELECTED_MESSAGE);
                        }
                    }
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static J.Literal inspectGradleLiteral(J.Literal literal, Cursor cursor, boolean checkBaseline) {
        Object parent = cursor.getParentTreeCursor().getValue();
        if (literal.getValue() instanceof String value) {
            if (value.contains("-Djasypt.encryptor.password=") ||
                value.contains("--jasypt.encryptor.password=")) {
                return SearchResult.found(literal,
                        "Jasypt password on a Gradle command line may leak through process listings, CI logs, and diagnostics; use masked secret injection");
            }
            if (isDirectDependencyLiteral(cursor) && value.equals(PREFIX)) {
                return SearchResult.found(literal,
                        "Starter version is externally managed; the strict upgrade recipe will not override an invisible platform/catalog value");
            }
            if (isDirectDependencyLiteral(cursor) && value.startsWith(PREFIX + ":")) {
                String[] parts = value.split(":", -1);
                if (parts.length != 3 || parts[2].contains("@")) {
                    return SearchResult.found(literal, CUSTOM_ARTIFACT_MESSAGE);
                }
                if ("2.1.1".equals(parts[2]) || "2.1.2".equals(parts[2])) {
                    return SearchResult.found(literal,
                            "Jasypt 2.x ciphertext used PBEWithMD5AndDES and NoIvGenerator by default; preserve that pair explicitly before re-encryption");
                }
                if (!JasyptVersions.TARGET.equals(parts[2]) && !JasyptVersions.isSource(parts[2])) {
                    return SearchResult.found(literal, UNSELECTED_MESSAGE);
                }
            }
            if (checkBaseline && parent instanceof J.MethodInvocation invocation &&
                "version".equals(invocation.getSimpleName()) &&
                invocation.printTrimmed().contains("org.springframework.boot") && belowBoot35(value)) {
                return SearchResult.found(literal, "Jasypt 4.0.3 requires Spring Boot 3.5.0 or newer");
            }
        }
        if (checkBaseline && parent instanceof J.Assignment assignment &&
            isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            belowJava17(String.valueOf(literal.getValue()))) {
            return SearchResult.found(literal, "Jasypt 4.0.3 requires Java 17 or newer");
        }
        return literal;
    }

    private static J.MethodInvocation inspectGroovyStarterMap(J.MethodInvocation invocation, Cursor cursor) {
        if (!JasyptVersions.isGradleDependencyInvocation(cursor, invocation) ||
            !GROUP.equals(mapValue(invocation, "group")) || !ARTIFACT.equals(mapValue(invocation, "name"))) {
            return invocation;
        }
        if (hasVariant(invocation)) return SearchResult.found(invocation, CUSTOM_ARTIFACT_MESSAGE);
        String version = mapValue(invocation, "version");
        if (version == null) {
            if (hasMapKey(invocation, "version")) {
                return SearchResult.found(invocation, UNSELECTED_MESSAGE);
            }
            return SearchResult.found(invocation,
                    "Starter version is externally managed; the strict upgrade recipe will not override an invisible platform/catalog value");
        }
        if ("2.1.1".equals(version) || "2.1.2".equals(version)) {
            return SearchResult.found(invocation,
                    "Jasypt 2.x ciphertext used PBEWithMD5AndDES and NoIvGenerator by default; preserve that pair explicitly before re-encryption");
        }
        if (!JasyptVersions.TARGET.equals(version) && !JasyptVersions.isSource(version)) {
            return SearchResult.found(invocation, UNSELECTED_MESSAGE);
        }
        return invocation;
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

    private static Map<String, String> localProperties(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property -> {
                    definitions.merge(property.getName(), 1, Integer::sum);
                    property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()));
                }));
        Set<String> shadowed = new java.util.HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Cursor propertiesCursor = getCursor().getParentTreeCursor();
                if (propertiesCursor.getValue() instanceof Xml.Tag propertiesTag &&
                    "properties".equals(propertiesTag.getName())) {
                    Cursor profile = propertiesCursor.getParentTreeCursor();
                    Cursor profiles = profile == null ? null : profile.getParentTreeCursor();
                    Cursor project = profiles == null ? null : profiles.getParentTreeCursor();
                    if (profile != null && profile.getValue() instanceof Xml.Tag profileTag &&
                        "profile".equals(profileTag.getName()) && profiles != null &&
                        profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
                        project != null && project.getValue() instanceof Xml.Tag projectTag &&
                        projectTag == document.getRoot()) {
                        shadowed.add(tag.getName());
                    }
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        properties.keySet().removeIf(name -> definitions.getOrDefault(name, 0) != 1 || shadowed.contains(name));
        return properties;
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

    private static boolean hasMavenStarter(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (UpgradeSelectedJasyptStarterDependency.isStarter(getCursor(), tag) &&
                    UpgradeSelectedJasyptStarterDependency.isStandardStarter(getCursor(), tag)) {
                    found[0] = true;
                }
                return found[0] ? tag : super.visitTag(tag, p);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static boolean hasGroovyStarter(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (JasyptVersions.isGradleDependencyInvocation(getCursor(), method) &&
                    (isStarterLiteral(method) || isStarterMap(method))) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean hasKotlinStarter(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (JasyptVersions.isGradleDependencyInvocation(getCursor(), method) &&
                    isStarterLiteral(method)) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean isStarterLiteral(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> {
                    String[] parts = value.split(":", -1);
                    return (parts.length == 2 || parts.length == 3) && GROUP.equals(parts[0]) &&
                           ARTIFACT.equals(parts[1]);
                });
    }

    private static boolean isStarterMap(J.MethodInvocation invocation) {
        return GROUP.equals(mapValue(invocation, "group")) && ARTIFACT.equals(mapValue(invocation, "name")) &&
               !hasVariant(invocation) &&
               (mapValue(invocation, "version") != null || !hasMapKey(invocation, "version"));
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return hasMapKey(invocation, "classifier") || hasMapKey(invocation, "ext") ||
               hasMapKey(invocation, "type");
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance)
                .map(G.MapEntry.class::cast).filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        if (direct != null) {
            return direct;
        }
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .flatMap(map -> map.getElements().stream()).filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasMapKey(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream()
                        .anyMatch(entry -> key.equals(mapKey(entry))));
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               JasyptVersions.isGradleDependencyInvocation(parent, invocation);
    }

    private static boolean isProjectRootChild(Cursor cursor, Xml.Tag tag, String name) {
        Cursor project = cursor.getParentTreeCursor();
        Cursor document = project.getParentTreeCursor();
        return name.equals(tag.getName()) && project.getValue() instanceof Xml.Tag projectTag &&
               "project".equals(projectTag.getName()) && document != null &&
               document.getValue() instanceof Xml.Document;
    }
}
