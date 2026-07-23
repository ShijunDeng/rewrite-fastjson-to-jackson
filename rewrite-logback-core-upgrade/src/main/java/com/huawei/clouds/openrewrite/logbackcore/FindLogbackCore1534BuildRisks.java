package com.huawei.clouds.openrewrite.logbackcore;

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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find dependency ownership, Java baseline, Logback/SLF4J family, and packaging risks. */
public final class FindLogbackCore1534BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern GRADLE_LOW_JAVA = Pattern.compile(
            "(?s).*(?:sourceCompatibility|targetCompatibility|jvmToolchain|JavaLanguageVersion\\.of)" +
            "\\s*(?:=|\\()?\\s*(?:JavaVersion\\.VERSION_1_[5-9]|JavaVersion\\.VERSION_10|" +
            "[\"']?1\\.[5-9][\"']?|[\"']?(?:[5-9]|10)[\"']?)(?![0-9]).*");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "jdk.version", "maven.compiler.release",
            "maven.compiler.source", "maven.compiler.target");
    private static final Set<String> LOGBACK_FAMILY = Set.of(
            "logback-bom", "logback-classic", "logback-access", "logback-core",
            "logback-core-db", "logback-classic-db", "logback-access-db");
    private static final Set<String> SLF4J_FAMILY = Set.of(
            "slf4j-api", "jul-to-slf4j", "jcl-over-slf4j", "log4j-over-slf4j",
            "slf4j-jdk14", "slf4j-simple", "slf4j-reload4j", "slf4j-log4j12", "slf4j-nop");
    private static final Set<String> COMPETING_BINDINGS = Set.of(
            "slf4j-jdk14", "slf4j-simple", "slf4j-reload4j", "slf4j-log4j12", "slf4j-nop");
    private static final Set<String> OPTIONAL_COMPONENTS = Set.of(
            "org.codehaus.janino:janino", "org.codehaus.janino:commons-compiler",
            "org.fusesource.jansi:jansi", "org.tukaani:xz",
            "javax.mail:mail", "com.sun.mail:javax.mail", "jakarta.mail:jakarta.mail-api",
            "javax.servlet:javax.servlet-api", "jakarta.servlet:jakarta.servlet-api");

    static final String OWNER =
            "logback-core is versionless, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; migrate the actual owner and verify that 1.5.34 resolves";
    static final String OUTSIDE =
            "This fixed logback-core version is outside the selected source set and target; it is intentionally " +
            "not auto-upgraded";
    static final String DOWNGRADE_FORBIDDEN =
            "目标版本冲突（禁止降级）：this fixed logback-core version is higher than 1.5.34; it remains unchanged";
    static final String VARIANT =
            "This classified or non-JAR logback-core artifact is outside deterministic scope; verify the exact " +
            "1.5.34 artifact shape manually";
    static final String FAMILY =
            "Logback family versions are coupled: logback-classic should align with logback-core 1.5.34 while " +
            "logback-access and logback-db use separate release lines; verify the approved matrix—this recipe never changes them";
    static final String SLF4J =
            "logback-classic 1.5.34 targets SLF4J 2.0.x and its MDC initialization fix requires 2.0.17; " +
            "verify API/provider alignment and bridge direction—this recipe never changes SLF4J artifacts";
    static final String BINDING_COLLISION =
            "A competing SLF4J provider/binding is present with Logback; keep one provider and verify that JUL, JCL, " +
            "Log4j bridges do not create a routing loop";
    static final String OPTIONAL =
            "This optional Logback Core integration changed across the migration (Janino, Jansi, XZ, mail or servlet); " +
            "verify exact dependency presence, namespace, JPMS readability, runtime feature path and container compatibility";
    static final String JAVA_BASELINE =
            "Logback Core 1.5.34 requires Java 11 at runtime; this build declares a lower or ambiguous Java baseline—" +
            "raise the actual toolchain/runtime owner deliberately and re-run packaged-artifact tests";
    static final String PACKAGING =
            "logback-core 1.5.34 is a multi-release explicit JPMS/OSGi artifact; shading, relocation, service/resource " +
            "merging, module-info removal or OSGi wrapping can change version discovery and optional-module resolution";

    @Override
    public String getDisplayName() {
        return "Find Logback Core 1.5.34 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved or outside dependency owners, Java 11 baseline gaps, Logback/SLF4J family skew, " +
               "competing bindings, optional integrations, variants, and JPMS/OSGi packaging boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLogbackCoreDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                if (tree instanceof PlainText text && versionCatalog(file)) return catalog(text);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Scopes scopes = scopes(source, ctx);
        if (scopes.empty()) return source;
        Properties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), scopes)) return visited;
                if (javaBaselineTag(getCursor(), visited, properties)) return mark(visited, JAVA_BASELINE);
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    packagingPlugin(visited) && mentionsPackaging(visited.printTrimmed(getCursor()))) {
                    return mark(visited, PACKAGING);
                }
                if (!UpgradeSelectedLogbackCoreDependency.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String version = visited.getChildValue("version").map(String::trim).orElse("");
                String resolved = resolve(version, getCursor(), properties);
                if (UpgradeSelectedLogbackCoreDependency.GROUP.equals(group) &&
                    UpgradeSelectedLogbackCoreDependency.ARTIFACT.equals(artifact)) {
                    if (!UpgradeSelectedLogbackCoreDependency.isStandardArtifact(visited)) return mark(visited, VARIANT);
                    if (resolved != null && (UpgradeSelectedLogbackCoreDependency.TARGET.equals(resolved) ||
                        UpgradeSelectedLogbackCoreDependency.SOURCE_VERSIONS.contains(resolved))) return visited;
                    if (resolved == null || !FIXED.matcher(resolved).matches()) return markVersion(visited, OWNER);
                    return markVersion(visited, higherThanTarget(resolved) ? DOWNGRADE_FORBIDDEN : OUTSIDE);
                }
                String companion = companionMessage(group, artifact, resolved);
                return companion == null ? visited : markVersion(visited, companion);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        if (GRADLE_LOW_JAVA.matcher(source.printAll()).matches()) source = mark(source, JAVA_BASELINE);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = UpgradeSelectedLogbackCoreDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if ("relocate".equals(visited.getSimpleName()) && topLevelOwner(getCursor(), "shadowJar") &&
                    visited.getArguments().stream().anyMatch(FindLogbackCore1534BuildRisks::mentionsLogbackLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (Set.of("platform", "enforcedPlatform").contains(visited.getSimpleName()) &&
                    visited.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                            .map(J.Literal::getValue).map(FindLogbackCore1534BuildRisks::coordinateMessage)
                            .anyMatch(FAMILY::equals)) return mark(visited, FAMILY);
                String printed = visited.getArguments().isEmpty() ? "" :
                        visited.getArguments().get(0).printTrimmed(getCursor());
                if (dependency && isCatalogAlias(printed)) return mark(visited, OWNER);
                if (!dependency) return visited;
                String group = UpgradeSelectedLogbackCoreDependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedLogbackCoreDependency.mapValue(visited, "name");
                String version = UpgradeSelectedLogbackCoreDependency.mapValue(visited, "version");
                boolean variant = UpgradeSelectedLogbackCoreDependency.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = UpgradeSelectedLogbackCoreDependency.mapValue(map, "group");
                    artifact = UpgradeSelectedLogbackCoreDependency.mapValue(map, "name");
                    version = UpgradeSelectedLogbackCoreDependency.mapValue(map, "version");
                    variant = UpgradeSelectedLogbackCoreDependency.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                if (visited.getArguments().stream().anyMatch(FindLogbackCore1534BuildRisks::dynamicCore)) {
                    return mark(visited, OWNER);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLogbackCoreDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        if (GRADLE_LOW_JAVA.matcher(source.printAll()).matches()) source = mark(source, JAVA_BASELINE);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = UpgradeSelectedLogbackCoreDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (Set.of("platform", "enforcedPlatform").contains(visited.getSimpleName()) &&
                    visited.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                            .map(J.Literal::getValue).map(FindLogbackCore1534BuildRisks::coordinateMessage)
                            .anyMatch(FAMILY::equals)) return mark(visited, FAMILY);
                if (!dependency) return visited;
                String printed = visited.getArguments().isEmpty() ? "" :
                        visited.getArguments().get(0).printTrimmed(getCursor());
                return isCatalogAlias(printed) ||
                       visited.getArguments().stream().anyMatch(FindLogbackCore1534BuildRisks::dynamicCore)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLogbackCoreDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(
            String group, String artifact, String version, boolean variant) {
        if (UpgradeSelectedLogbackCoreDependency.GROUP.equals(group) &&
            UpgradeSelectedLogbackCoreDependency.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches()) return OWNER;
            if (UpgradeSelectedLogbackCoreDependency.TARGET.equals(version) ||
                UpgradeSelectedLogbackCoreDependency.SOURCE_VERSIONS.contains(version)) return null;
            return higherThanTarget(version) ? DOWNGRADE_FORBIDDEN : OUTSIDE;
        }
        return companionMessage(group, artifact, version);
    }

    private static String companionMessage(String group, String artifact, String version) {
        String coordinate = group + ":" + artifact;
        if (("ch.qos.logback".equals(group) || "ch.qos.logback.db".equals(group) ||
             "ch.qos.logback.access".equals(group)) && LOGBACK_FAMILY.contains(artifact)) {
            if ("ch.qos.logback".equals(group) && "logback-classic".equals(artifact) &&
                UpgradeSelectedLogbackCoreDependency.TARGET.equals(version)) return null;
            if ("ch.qos.logback".equals(group) && "logback-core".equals(artifact)) return null;
            return FAMILY;
        }
        if ("org.slf4j".equals(group) && SLF4J_FAMILY.contains(artifact)) {
            if (COMPETING_BINDINGS.contains(artifact)) return BINDING_COLLISION;
            if ("slf4j-api".equals(artifact) && "2.0.17".equals(version)) return null;
            return SLF4J;
        }
        if ("org.apache.logging.log4j".equals(group) && "log4j-slf4j2-impl".equals(artifact)) {
            return BINDING_COLLISION;
        }
        if (OPTIONAL_COMPONENTS.contains(coordinate)) return OPTIONAL;
        return null;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : null;
        if (UpgradeSelectedLogbackCoreDependency.GROUP.equals(group) &&
            UpgradeSelectedLogbackCoreDependency.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || version != null && version.contains("@")) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches()) return OWNER;
            if (UpgradeSelectedLogbackCoreDependency.TARGET.equals(version) ||
                UpgradeSelectedLogbackCoreDependency.SOURCE_VERSIONS.contains(version)) return null;
            return higherThanTarget(version) ? DOWNGRADE_FORBIDDEN : OUTSIDE;
        }
        return companionMessage(group, artifact, version);
    }

    private static boolean higherThanTarget(String version) {
        if (version == null || !FIXED.matcher(version).matches()) return false;
        String[] candidate = version.split("[^0-9]+");
        String[] target = UpgradeSelectedLogbackCoreDependency.TARGET.split("\\.");
        int length = Math.max(candidate.length, target.length);
        for (int i = 0; i < length; i++) {
            BigInteger left = new BigInteger(
                    i < candidate.length && !candidate[i].isEmpty() ? candidate[i] : "0");
            BigInteger right = new BigInteger(i < target.length ? target[i] : "0");
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static boolean javaBaselineTag(Cursor cursor, Xml.Tag tag, Properties properties) {
        String value = tag.getValue().map(String::trim).orElse("");
        if (UpgradeSelectedLogbackCoreDependency.isMavenPropertyDefinition(cursor, tag) &&
            JAVA_PROPERTIES.contains(tag.getName())) return belowJava11(value);
        if (!Set.of("release", "source", "target").contains(tag.getName()) ||
            !insideMavenCompilerPlugin(cursor)) return false;
        String resolved = resolve(value, cursor, properties);
        return resolved == null || belowJava11(resolved);
    }

    private static boolean insideMavenCompilerPlugin(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "plugin".equals(tag.getName())) {
                return "maven-compiler-plugin".equals(tag.getChildValue("artifactId").orElse("")) &&
                       projectBuildPlugin(current);
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean belowJava11(String value) {
        String candidate = value.trim();
        if (!candidate.matches("(?:1\\.)?[0-9]+")) return false;
        int major = Integer.parseInt(candidate.startsWith("1.") ? candidate.substring(2) : candidate);
        return major < 11;
    }

    private static boolean versionCatalog(String file) {
        return "libs.versions.toml".equals(file) || file.endsWith(".versions.toml");
    }

    private static PlainText catalog(PlainText source) {
        for (String line : source.getText().split("\\R")) {
            String value = line.strip();
            if (value.isEmpty() || value.startsWith("#")) continue;
            boolean module = value.matches(".*\\bmodule\\s*=\\s*[\"']ch\\.qos\\.logback:logback-core[\"'].*");
            boolean groupAndName =
                    value.matches(".*\\bgroup\\s*=\\s*[\"']ch\\.qos\\.logback[\"'].*") &&
                    value.matches(".*\\bname\\s*=\\s*[\"']logback-core[\"'].*");
            if (module || groupAndName) return mark(source, OWNER);
        }
        return source;
    }

    private static Scopes scopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedLogbackCoreDependency.isTargetDependency(getCursor(), visited)) {
                    String owner = scope(getCursor());
                    if ("ROOT".equals(owner)) root[0] = true; else profiles.add(owner);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Scopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, Scopes scopes) {
        String owner = scope(cursor);
        if ("ROOT".equals(owner)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(owner);
    }

    private static Properties properties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedLogbackCoreDependency.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        Map<PropertyKey, Integer> references = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), counts, references);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), counts, references);
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Properties(counts, values, references, profileNames);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
                                          Map<PropertyKey, Integer> references) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            String currentScope = scope(cursor);
            PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
            PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
            PropertyKey owner = !"ROOT".equals(currentScope) && definitions.containsKey(local) ? local : root;
            references.merge(owner, 1, Integer::sum);
        }
    }

    private static String resolve(String version, Cursor cursor, Properties properties) {
        if (FIXED.matcher(version).matches()) return version;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return null;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        if (properties.counts().getOrDefault(owner, 0) != 1 ||
            properties.references().getOrDefault(owner, 0) != 1 ||
            "ROOT".equals(owner.scope()) && properties.profileNames().contains(owner.name())) return null;
        return properties.values().get(owner);
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private static boolean hasPrimary(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLogbackCoreDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean hasPrimary(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLogbackCoreDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (UpgradeSelectedLogbackCoreDependency.GROUP.equals(UpgradeSelectedLogbackCoreDependency.mapValue(method, "group")) &&
            UpgradeSelectedLogbackCoreDependency.ARTIFACT.equals(UpgradeSelectedLogbackCoreDependency.mapValue(method, "name"))) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                UpgradeSelectedLogbackCoreDependency.GROUP.equals(UpgradeSelectedLogbackCoreDependency.mapValue(map, "group")) &&
                UpgradeSelectedLogbackCoreDependency.ARTIFACT.equals(UpgradeSelectedLogbackCoreDependency.mapValue(map, "name"))) return true;
            if (dynamicCore(argument)) return true;
            if (isCatalogAlias(argument.printTrimmed())) return true;
        }
        return false;
    }

    private static boolean isCatalogAlias(String value) {
        String compact = value.replace("_", "").replace("-", "").toLowerCase();
        return compact.startsWith("libs.") && compact.contains("logback") && compact.contains("core");
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(UpgradeSelectedLogbackCoreDependency.GROUP + ":" + UpgradeSelectedLogbackCoreDependency.ARTIFACT) ||
                coordinate.startsWith(UpgradeSelectedLogbackCoreDependency.GROUP + ":" + UpgradeSelectedLogbackCoreDependency.ARTIFACT + ":"));
    }

    private static boolean dynamicCore(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().map(String::stripLeading).map(value -> value.startsWith(
                        UpgradeSelectedLogbackCoreDependency.GROUP + ":" +
                        UpgradeSelectedLogbackCoreDependency.ARTIFACT + ":")).orElse(false);
    }

    private static boolean mentionsLogbackLiteral(J argument) {
        return argument instanceof J.Literal literal &&
               literal.getValue() instanceof String value &&
               value.contains("ch.qos.logback");
    }

    private static boolean mentionsPackaging(String value) {
        return value.contains("ch.qos.logback") || value.contains("META-INF/") ||
               value.contains("ServicesResourceTransformer") || value.contains("module-info");
    }

    private static boolean packagingPlugin(Xml.Tag plugin) {
        String artifact = plugin.getChildValue("artifactId").orElse("");
        return artifact.contains("shade") || artifact.contains("bnd") ||
               artifact.contains("bundle") || artifact.contains("native");
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag b && "build".equals(b.getName()) &&
               UpgradeSelectedLogbackCoreDependency.isProjectOrProfile(build.getParentTreeCursor());
    }

    private static boolean topLevelOwner(Cursor cursor, String name) {
        int count = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation method) {
                count++;
                owner = method.getSimpleName();
            }
        }
        return count == 1 && name.equals(owner);
    }

    private static Xml.Tag markVersion(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record Properties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values,
                              Map<PropertyKey, Integer> references, Set<String> profileNames) {
    }

    private record Scopes(boolean root, Set<String> profiles) {
        private boolean empty() {
            return !root && profiles.isEmpty();
        }
    }
}
