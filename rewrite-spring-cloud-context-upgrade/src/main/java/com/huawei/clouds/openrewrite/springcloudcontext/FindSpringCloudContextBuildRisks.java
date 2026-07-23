package com.huawei.clouds.openrewrite.springcloudcontext;

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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark build-owner and platform alignment decisions for Cloud 4.3 / Boot 3.5 / Java 17. */
public final class FindSpringCloudContextBuildRisks extends Recipe {
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Pattern VERSION = Pattern.compile("(\\d+)[.](\\d+)(?:[.].*)?");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Spring Cloud Context 4.3 build risks";
    }

    @Override
    public String getDescription() {
        return "Marks Java below 17, Boot outside 3.5, Cloud BOMs outside 2025.0.2, external/versionless owners, " +
               "variants, Javax/Spring Native/RSA dependencies, and AOT/native build boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringCloudContextSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    return containsContext(document, ctx) ? maven(document, ctx) : tree;
                }
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return containsContext(groovy, ctx) ? groovy(groovy, ctx) : tree;
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return containsContext(kotlin, ctx) ? kotlin(kotlin, ctx) : tree;
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<String, String> rootProperties = new HashMap<>();
        Map<UUID, Map<String, String>> profileProperties = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (!SpringCloudContextSupport.isMavenPropertyDefinition(getCursor(), visited)) return visited;
                UUID profile = profileId(getCursor());
                Map<String, String> properties = profile == null ? rootProperties :
                        profileProperties.computeIfAbsent(profile, ignored -> new HashMap<>());
                visited.getValue().ifPresent(value -> properties.put(visited.getName(), value.trim()));
                return visited;
            }
        }.visitNonNull(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                String value = visited.getValue().orElse("").trim();
                if (SpringCloudContextSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    if (JAVA_PROPERTIES.contains(visited.getName()) && belowJava17(value)) {
                        return SearchResult.found(visited, "Spring Cloud Context 4.3.2 and its Boot 3.5 baseline require a Java 17+ compiler, toolchain, runtime, and container image");
                    }
                    if (("spring-boot.version".equals(visited.getName()) ||
                         "spring.boot.version".equals(visited.getName())) && literalVersion(value) && !boot35(value)) {
                        return SearchResult.found(visited, "Spring Cloud Context 4.3.2 belongs to the Spring Cloud 2025.0.2 / Spring Boot 3.5.x line; align the complete Boot platform");
                    }
                    if (("spring-cloud.version".equals(visited.getName()) ||
                         "spring.cloud.version".equals(visited.getName())) && literalVersion(value) &&
                        !"2025.0.2".equals(value)) {
                        return SearchResult.found(visited, "The exact 4.3.2 target is managed by Spring Cloud 2025.0.2; align the whole BOM instead of overriding one Cloud module");
                    }
                }
                if (projectParent(getCursor(), visited) &&
                    "org.springframework.boot".equals(visited.getChildValue("groupId").orElse(null))) {
                    String declaredParent = visited.getChildValue("version").orElse("").trim();
                    String parentVersion = resolve(declaredParent, rootProperties);
                    if (parentVersion == null) parentVersion = declaredParent;
                    if (literalVersion(parentVersion) && !boot35(parentVersion)) {
                        return SearchResult.found(visited, "This Spring Boot parent is outside the 3.5.x baseline aligned with Spring Cloud 2025.0.2 / Context 4.3.2");
                    }
                    if (!literalVersion(parentVersion)) {
                        return SearchResult.found(visited, "The Boot parent version is externally owned; resolve it and prove Boot 3.5.x alignment with Spring Cloud Context 4.3.2");
                    }
                }
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor())) {
                    String group = visited.getChildValue("groupId").orElse("");
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    if (("org.graalvm.buildtools".equals(group) && "native-maven-plugin".equals(artifact)) ||
                        ("org.springframework.boot".equals(group) && "spring-boot-maven-plugin".equals(artifact) &&
                         visited.printTrimmed(getCursor()).contains("process-aot"))) {
                        return SearchResult.found(visited, "AOT/native builds cannot use context refresh; set spring.cloud.refresh.enabled=false and verify RuntimeHints, bootstrap/encryption reflection, and build-time configuration");
                    }
                }
                if (!"dependency".equals(visited.getName()) ||
                    !SpringCloudContextSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String declared = visited.getChildValue("version").orElse("").trim();
                Map<String, String> visibleProperties = new HashMap<>(rootProperties);
                UUID profile = profileId(getCursor());
                if (profile != null) visibleProperties.putAll(profileProperties.getOrDefault(profile, Map.of()));
                String resolved = resolve(declared, visibleProperties);
                if (SpringCloudContextSupport.GROUP.equals(group) &&
                    SpringCloudContextSupport.ARTIFACT.equals(artifact)) {
                    if (!SpringCloudContextSupport.standardJar(visited)) {
                        return SearchResult.found(visited, "Classifier/type variants are outside the workbook's ordinary spring-cloud-context jar target and require an explicit artifact decision");
                    }
                    if (declared.isEmpty()) {
                        return SearchResult.found(visited, "This versionless spring-cloud-context dependency is controlled by a parent/BOM/platform; update that owner to Spring Cloud 2025.0.2 rather than adding a local version");
                    }
                    if (resolved == null && !SpringCloudContextSupport.TARGET.equals(declared)) {
                        return SearchResult.found(visited, "This spring-cloud-context version is externally, transitively, or ambiguously owned; resolve its property/parent/catalog and align that owner to 4.3.2");
                    }
                }
                if ("org.springframework.cloud".equals(group) && "spring-cloud-dependencies".equals(artifact)) {
                    if (resolved == null || !"2025.0.2".equals(resolved)) {
                        return SearchResult.found(visited, "Use the Spring Cloud 2025.0.2 BOM for the exact spring-cloud-context 4.3.2 target; shared/external BOM owners are not rewritten automatically");
                    }
                }
                if ("org.springframework.security".equals(group) && "spring-security-rsa".equals(artifact)) {
                    return SearchResult.found(visited, "Spring Cloud Context removed its spring-security-rsa dependency and uses Bouncy Castle internals; verify whether this direct RSA dependency is still application-owned and retest key parsing/decryption");
                }
                if (group.startsWith("javax.") && !"javax.inject".equals(group)) {
                    return SearchResult.found(visited, "Boot 3.5 / Framework 6 uses Jakarta APIs; migrate this Javax dependency and all imports, descriptors, validation, reflection, and tests together");
                }
                if (group.startsWith("org.springframework.experimental") || artifact.contains("spring-native")) {
                    return SearchResult.found(visited, "Spring Native-era build/runtime dependencies are obsolete under Boot 3 AOT; port to RuntimeHints and disable context refresh in native images");
                }
                if ("org.springframework.cloud".equals(group) && "spring-cloud-starter-bootstrap".equals(artifact)) {
                    return SearchResult.found(visited, "The bootstrap starter intentionally selects the legacy parent-context path; verify Config Data alternatives, property precedence, logging, refresh timing, and AOT/native support");
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return legacyJavaAssignment(visited, getCursor())
                        ? SearchResult.found(visited, "Spring Cloud Context 4.3.2 requires a Java 17+ Gradle toolchain and runtime") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (legacyToolchain(visited)) return SearchResult.found(visited,
                        "Spring Cloud Context 4.3.2 requires a Java 17+ Gradle toolchain and runtime");
                return markGradleDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return legacyJavaAssignment(visited, getCursor())
                        ? SearchResult.found(visited, "Spring Cloud Context 4.3.2 requires a Java 17+ Gradle toolchain and runtime") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (legacyToolchain(visited)) return SearchResult.found(visited,
                        "Spring Cloud Context 4.3.2 requires a Java 17+ Gradle toolchain and runtime");
                return markGradleDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method, Cursor cursor) {
        if (gradlePlatformWrapper(cursor, method) && !method.getArguments().isEmpty() &&
            method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof String coordinate) {
            if (coordinate.startsWith("org.springframework.cloud:spring-cloud-dependencies:") &&
                !coordinate.endsWith(":2025.0.2")) {
                return SearchResult.found(method, "Use the Spring Cloud 2025.0.2 platform for the exact spring-cloud-context 4.3.2 target");
            }
            if (coordinate.startsWith("org.springframework.boot:spring-boot-dependencies:") &&
                !boot35(coordinate.substring(coordinate.lastIndexOf(':') + 1))) {
                return SearchResult.found(method, "Spring Cloud 2025.0.2 / Context 4.3.2 requires a Boot 3.5.x platform");
            }
            return method;
        }
        if (!SpringCloudContextSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) {
            return method;
        }
        if (method.getArguments().stream().anyMatch(FindSpringCloudContextBuildRisks::managedContextArgument)) {
            return SearchResult.found(method, "This spring-cloud-context dependency is controlled by a Gradle variable/catalog; align that owner to Spring Cloud 2025.0.2 and prove 4.3.2 resolution");
        }
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                .map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String mapGroup = map == null ? SpringCloudContextSupport.mapValue(method, "group") :
                SpringCloudContextSupport.mapValue(map, "group");
        String mapArtifact = map == null ? SpringCloudContextSupport.mapValue(method, "name") :
                SpringCloudContextSupport.mapValue(map, "name");
        String mapVersion = map == null ? SpringCloudContextSupport.mapValue(method, "version") :
                SpringCloudContextSupport.mapValue(map, "version");
        if (SpringCloudContextSupport.GROUP.equals(mapGroup) && SpringCloudContextSupport.ARTIFACT.equals(mapArtifact)) {
            if (map == null ? SpringCloudContextSupport.hasVariant(method) : SpringCloudContextSupport.hasVariant(map)) {
                return SearchResult.found(method, "Classifier/type/extension variants are outside the workbook's ordinary spring-cloud-context jar target and require an explicit artifact decision");
            }
            if (mapVersion == null || mapVersion.isBlank()) {
                return SearchResult.found(method, "This versionless spring-cloud-context dependency is controlled by a Gradle platform/catalog; align that owner to Spring Cloud 2025.0.2");
            }
            if (mapVersion.contains("$") || (!SpringCloudContextSupport.TARGET.equals(mapVersion) &&
                !SpringCloudContextSupport.SOURCES.contains(mapVersion))) {
                return SearchResult.found(method, "This spring-cloud-context version is outside or ambiguously owned by the workbook selection; resolve its platform/catalog/property and prove 4.3.2 alignment");
            }
        }
        if ("org.springframework.security".equals(mapGroup) && "spring-security-rsa".equals(mapArtifact)) {
            return SearchResult.found(method, "Verify direct spring-security-rsa ownership after Context 4.3 removed its dependency and retest encryption key/provider behavior");
        }
        if ((mapGroup != null && (mapGroup.startsWith("javax.") || mapGroup.startsWith("org.springframework.experimental"))) ||
            (mapArtifact != null && mapArtifact.contains("spring-native"))) {
            return SearchResult.found(method, "Legacy Javax/Spring Native dependencies require Jakarta or Boot 3 RuntimeHints migration for the Context 4.3.2 baseline");
        }
        if ("org.springframework.cloud".equals(mapGroup) && "spring-cloud-starter-bootstrap".equals(mapArtifact)) {
            return SearchResult.found(method, "The bootstrap starter intentionally selects the legacy parent-context path; verify Config Data alternatives, precedence, refresh timing, and AOT/native support");
        }
        if (!(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String coordinate)) return method;
        if (coordinate.equals(SpringCloudContextSupport.GROUP + ":" + SpringCloudContextSupport.ARTIFACT)) {
            return SearchResult.found(method, "This versionless spring-cloud-context dependency is controlled by a Gradle platform/catalog; align that owner to Spring Cloud 2025.0.2");
        }
        String contextPrefix = SpringCloudContextSupport.GROUP + ":" + SpringCloudContextSupport.ARTIFACT + ":";
        if (coordinate.startsWith(contextPrefix)) {
            String version = coordinate.substring(contextPrefix.length());
            if (version.contains(":") || version.contains("@")) {
                return SearchResult.found(method, "Classifier/type/extension variants are outside the workbook's ordinary spring-cloud-context jar target and require an explicit artifact decision");
            }
            if (version.contains("$") || (!SpringCloudContextSupport.TARGET.equals(version) &&
                !SpringCloudContextSupport.SOURCES.contains(version))) {
                return SearchResult.found(method, "This spring-cloud-context version is outside or ambiguously owned by the workbook selection; resolve its platform/catalog/property and prove 4.3.2 alignment");
            }
        }
        if (coordinate.startsWith("org.springframework.cloud:spring-cloud-dependencies:") &&
            !coordinate.endsWith(":2025.0.2")) {
            return SearchResult.found(method, "Use the Spring Cloud 2025.0.2 platform for the exact spring-cloud-context 4.3.2 target");
        }
        if (coordinate.startsWith("org.springframework.boot:spring-boot-dependencies:") &&
            !boot35(coordinate.substring(coordinate.lastIndexOf(':') + 1))) {
            return SearchResult.found(method, "Spring Cloud 2025.0.2 / Context 4.3.2 requires a Boot 3.5.x platform");
        }
        if (coordinate.startsWith("org.springframework.security:spring-security-rsa:")) {
            return SearchResult.found(method, "Verify direct spring-security-rsa ownership after Context 4.3 removed its dependency and retest encryption key/provider behavior");
        }
        if (coordinate.startsWith("javax.") || coordinate.startsWith("org.springframework.experimental:") ||
            coordinate.contains(":spring-native")) {
            return SearchResult.found(method, "Legacy Javax/Spring Native dependencies require Jakarta or Boot 3 RuntimeHints migration for the Context 4.3.2 baseline");
        }
        if (coordinate.startsWith("org.springframework.cloud:spring-cloud-starter-bootstrap:")) {
            return SearchResult.found(method, "The bootstrap starter intentionally selects the legacy parent-context path; verify Config Data alternatives, precedence, refresh timing, and AOT/native support");
        }
        return method;
    }

    private static boolean containsContext(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (SpringCloudContextSupport.isSpringCloudContextDependency(getCursor(), visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static boolean containsContext(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (SpringCloudContextSupport.isGradleDependencyInvocation(getCursor(), method) &&
                    contextArgument(method)) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean containsContext(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (SpringCloudContextSupport.isGradleDependencyInvocation(getCursor(), method) &&
                    contextArgument(method)) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean contextArgument(J.MethodInvocation method) {
        String group = SpringCloudContextSupport.mapValue(method, "group");
        String artifact = SpringCloudContextSupport.mapValue(method, "name");
        if (SpringCloudContextSupport.GROUP.equals(group) && SpringCloudContextSupport.ARTIFACT.equals(artifact)) {
            return true;
        }
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                .map(G.MapLiteral.class::cast).findFirst().orElse(null);
        if (map != null && SpringCloudContextSupport.GROUP.equals(SpringCloudContextSupport.mapValue(map, "group")) &&
            SpringCloudContextSupport.ARTIFACT.equals(SpringCloudContextSupport.mapValue(map, "name"))) return true;
        return method.getArguments().stream().anyMatch(argument -> {
            if (managedContextArgument(argument)) return true;
            return argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate &&
                   (coordinate.equals(SpringCloudContextSupport.GROUP + ":" + SpringCloudContextSupport.ARTIFACT) ||
                    coordinate.startsWith(SpringCloudContextSupport.GROUP + ":" +
                                          SpringCloudContextSupport.ARTIFACT + ":"));
        });
    }

    private static boolean managedContextArgument(J argument) {
        String printed = argument.printTrimmed();
        if (printed.contains(SpringCloudContextSupport.GROUP + ":" + SpringCloudContextSupport.ARTIFACT + ":")) {
            return !(argument instanceof J.Literal);
        }
        String normalized = printed.replace("_", "").replace("-", "").toLowerCase();
        return printed.contains("libs.") && normalized.contains("spring") && normalized.contains("cloud") &&
               normalized.contains("context");
    }

    private static boolean gradlePlatformWrapper(Cursor cursor, J.MethodInvocation method) {
        if (!("platform".equals(method.getSimpleName()) || "enforcedPlatform".equals(method.getSimpleName())) ||
            method.getSelect() != null) return false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation owner) {
                return SpringCloudContextSupport.isGradleDependencyInvocation(current, owner);
            }
        }
        return false;
    }

    private static UUID profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId();
            if (current.getValue() instanceof Xml.Document) return null;
        }
        return null;
    }

    private static boolean projectParent(Cursor cursor, Xml.Tag tag) {
        if (!"parent".equals(tag.getName())) return false;
        Cursor owner = cursor.getParentTreeCursor();
        return owner.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               owner.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor pluginsCursor = cursor.getParentTreeCursor();
        if (!(pluginsCursor.getValue() instanceof Xml.Tag plugins) || !"plugins".equals(plugins.getName())) return false;
        Cursor buildCursor = pluginsCursor.getParentTreeCursor();
        if (!(buildCursor.getValue() instanceof Xml.Tag build) || !"build".equals(build.getName())) return false;
        Cursor owner = buildCursor.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName()) &&
            owner.getParentTreeCursor().getValue() instanceof Xml.Document) return true;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag profilesTag) || !"profiles".equals(profilesTag.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (literalVersion(value) || value.matches("\\d+[.]\\d+[.]\\d+[.]RELEASE")) return value;
        Matcher matcher = PROPERTY.matcher(value);
        if (!matcher.matches()) return null;
        String resolved = properties.get(matcher.group(1));
        return resolved != null && (literalVersion(resolved) || resolved.endsWith(".RELEASE")) ? resolved : null;
    }

    private static boolean belowJava17(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 17;
    }

    private static boolean boot35(String value) {
        Matcher matcher = VERSION.matcher(value);
        return matcher.matches() && "3".equals(matcher.group(1)) && "5".equals(matcher.group(2));
    }

    private static boolean literalVersion(String value) {
        return VERSION.matcher(value).matches();
    }

    private static boolean legacyToolchain(J.MethodInvocation method) {
        return "of".equals(method.getSimpleName()) && method.getArguments().size() == 1 &&
               method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof Number number &&
               number.intValue() < 17 && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        Matcher numeric = JAVA_LEVEL.matcher(value);
        if (numeric.matches()) return Integer.parseInt(numeric.group(1)) < 17;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 17;
    }
}
