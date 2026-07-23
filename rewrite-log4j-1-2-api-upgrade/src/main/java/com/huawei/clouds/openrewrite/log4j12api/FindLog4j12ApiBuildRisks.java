package com.huawei.clouds.openrewrite.log4j12api;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find ownership, version direction, duplicate implementation, and family-alignment risks. */
public final class FindLog4j12ApiBuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> INCOMPATIBLE = Set.of(
            "log4j:log4j", "ch.qos.reload4j:reload4j", "org.slf4j:log4j-over-slf4j");
    private static final Set<String> FAMILY = Set.of(
            "log4j-api", "log4j-core", "log4j-bom", "log4j-slf4j-impl",
            "log4j-slf4j2-impl", "log4j-jcl", "log4j-jul", "log4j-to-slf4j");

    static final String OWNER =
            "log4j-1.2-api is versionless, variable, ranged, dynamic, catalog/BOM/platform-managed, shared, " +
            "or externally owned; migrate the actual owner and verify that 2.25.5 resolves";
    static final String OUTSIDE =
            "This fixed log4j-1.2-api version is outside the six workbook source versions and target; " +
            "it remains unchanged and requires a separately approved migration edge";
    static final String DOWNGRADE_FORBIDDEN =
            "目标版本冲突（禁止降级）：this fixed log4j-1.2-api version is higher than 2.25.5; it remains unchanged";
    static final String VARIANT =
            "This classified or non-JAR log4j-1.2-api declaration is outside deterministic scope; verify that " +
            "2.25.5 publishes the required artifact shape";
    static final String DUPLICATE =
            "The Log4j 1-to-2 bridge replaces org.apache.log4j classes and is incompatible with log4j:log4j, " +
            "reload4j, and log4j-over-slf4j; remove duplicate implementations and test the final classloader graph";
    static final String ALIGNMENT =
            "Align log4j-api and the selected Log4j implementation/bridges through an owned exact 2.25.5 family " +
            "version; this module does not rewrite companion artifacts or choose a backend";
    static final String CATALOG =
            "This version catalog owns log4j-1.2-api; migrate the catalog entry explicitly to 2.25.5 and verify " +
            "all aliases/bundles because the strict recipe never guesses catalog ownership";

    @Override
    public String getDisplayName() {
        return "Find Log4j 1.2 API bridge build risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved, outside and higher dependency versions, variants, incompatible duplicate " +
               "Log4j 1 implementations, version catalogs and Log4j family alignment.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLog4j12ApiDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(file)) return maven(xml, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                if (tree instanceof PlainText text && file.endsWith(".toml") &&
                    text.getText().contains("log4j-1.2-api")) return mark(text, CATALOG);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        if (!source.printAll().contains("<artifactId>log4j-1.2-api</artifactId>")) return source;
        Map<String, Set<String>> properties = propertyValues(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!UpgradeSelectedLog4j12ApiDependency.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String coordinate = group + ":" + artifact;
                if (UpgradeSelectedLog4j12ApiDependency.GROUP.equals(group) &&
                    UpgradeSelectedLog4j12ApiDependency.ARTIFACT.equals(artifact)) {
                    if (!UpgradeSelectedLog4j12ApiDependency.isStandardArtifact(visited)) {
                        return mark(visited, VARIANT);
                    }
                    String declared = visited.getChildValue("version").map(String::trim).orElse("");
                    String resolved = resolve(declared, properties);
                    String message = versionMessage(resolved);
                    return message == null ? visited : markVersion(visited, message);
                }
                if (INCOMPATIBLE.contains(coordinate)) return markVersion(visited, DUPLICATE);
                if (UpgradeSelectedLog4j12ApiDependency.GROUP.equals(group) &&
                    FAMILY.contains(artifact)) {
                    String declared = visited.getChildValue("version").map(String::trim).orElse("");
                    String resolved = resolve(declared, properties);
                    return UpgradeSelectedLog4j12ApiDependency.TARGET.equals(resolved)
                            ? visited : markVersion(visited, ALIGNMENT);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!source.printAll().contains("log4j-1.2-api")) return source;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLog4j12ApiDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String group = UpgradeSelectedLog4j12ApiDependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedLog4j12ApiDependency.mapValue(visited, "name");
                if (UpgradeSelectedLog4j12ApiDependency.GROUP.equals(group) &&
                    UpgradeSelectedLog4j12ApiDependency.ARTIFACT.equals(artifact)) {
                    if (UpgradeSelectedLog4j12ApiDependency.hasVariant(visited)) return mark(visited, VARIANT);
                    String message = versionMessage(
                            UpgradeSelectedLog4j12ApiDependency.mapValue(visited, "version"));
                    return message == null ? visited : mark(visited, message);
                }
                if (UpgradeSelectedLog4j12ApiDependency.GROUP.equals(group) &&
                    FAMILY.contains(artifact)) {
                    return UpgradeSelectedLog4j12ApiDependency.TARGET.equals(
                            UpgradeSelectedLog4j12ApiDependency.mapValue(visited, "version"))
                            ? visited : mark(visited, ALIGNMENT);
                }
                String printed = visited.printTrimmed(getCursor());
                String companion = companionMessage(printed);
                return companion == null ? visited : mark(visited, companion);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLog4j12ApiDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (!direct || !(visited.getValue() instanceof String coordinate)) return visited;
                String message = coordinateMessage(coordinate);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!source.printAll().contains("log4j-1.2-api")) return source;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedLog4j12ApiDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (!direct || !(visited.getValue() instanceof String coordinate)) return visited;
                String message = coordinateMessage(coordinate);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Map<String, Set<String>> propertyValues(
            Xml.Document source, ExecutionContext ctx) {
        Map<String, Set<String>> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedLog4j12ApiDependency
                        .isMavenPropertyDefinition(getCursor(), visited)) {
                    visited.getValue().ifPresent(value ->
                            values.computeIfAbsent(visited.getName(), ignored -> new HashSet<>())
                                    .add(value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return values;
    }

    private static String resolve(String declared, Map<String, Set<String>> properties) {
        Matcher matcher = PROPERTY.matcher(declared);
        if (!matcher.matches()) return declared;
        Set<String> values = properties.getOrDefault(matcher.group(1), Set.of());
        return values.size() == 1 ? values.iterator().next() : null;
    }

    private static String coordinateMessage(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 3) return companionMessage(coordinate);
        if (UpgradeSelectedLog4j12ApiDependency.GROUP.equals(parts[0]) &&
            UpgradeSelectedLog4j12ApiDependency.ARTIFACT.equals(parts[1])) {
            return versionMessage(parts[2]);
        }
        return companionMessage(coordinate);
    }

    private static String companionMessage(String coordinate) {
        if (INCOMPATIBLE.stream().anyMatch(coordinate::contains)) return DUPLICATE;
        String[] parts = coordinate.split(":", -1);
        if (parts.length == 3 &&
            UpgradeSelectedLog4j12ApiDependency.GROUP.equals(parts[0]) &&
            FAMILY.contains(parts[1])) {
            return UpgradeSelectedLog4j12ApiDependency.TARGET.equals(parts[2]) ? null : ALIGNMENT;
        }
        if (coordinate.contains(UpgradeSelectedLog4j12ApiDependency.GROUP + ":") &&
            FAMILY.stream().anyMatch(artifact -> coordinate.contains(":" + artifact)) &&
            !coordinate.contains(":" + UpgradeSelectedLog4j12ApiDependency.TARGET)) {
            return ALIGNMENT;
        }
        return null;
    }

    private static String versionMessage(String version) {
        if (version == null || version.isBlank() || !FIXED.matcher(version).matches()) return OWNER;
        if (UpgradeSelectedLog4j12ApiDependency.TARGET.equals(version) ||
            UpgradeSelectedLog4j12ApiDependency.SOURCE_VERSIONS.contains(version)) return null;
        return compare(version, UpgradeSelectedLog4j12ApiDependency.TARGET) > 0
                ? DOWNGRADE_FORBIDDEN : OUTSIDE;
    }

    private static int compare(String left, String right) {
        List<BigInteger> a = numericPrefix(left);
        List<BigInteger> b = numericPrefix(right);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            BigInteger av = i < a.size() ? a.get(i) : BigInteger.ZERO;
            BigInteger bv = i < b.size() ? b.get(i) : BigInteger.ZERO;
            int comparison = av.compareTo(bv);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static List<BigInteger> numericPrefix(String version) {
        List<BigInteger> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("[0-9]+").matcher(version);
        while (matcher.find()) result.add(new BigInteger(matcher.group()));
        return result;
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        for (int i = 0; i < dependency.getContent().size(); i++) {
            if (!(dependency.getContent().get(i) instanceof Xml.Tag version) ||
                !"version".equals(version.getName())) continue;
            if (version.getMarkers().findAll(SearchResult.class).stream()
                    .anyMatch(result -> message.equals(result.getDescription()))) return dependency;
            List<org.openrewrite.xml.tree.Content> content =
                    new ArrayList<>(dependency.getContent());
            content.set(i, mark(version, message));
            return dependency.withContent(content);
        }
        return mark(dependency, message);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
