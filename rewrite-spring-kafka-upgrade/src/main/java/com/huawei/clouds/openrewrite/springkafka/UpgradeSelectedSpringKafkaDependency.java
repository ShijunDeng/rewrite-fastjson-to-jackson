package com.huawei.clouds.openrewrite.springkafka;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Upgrade only the two workbook-selected Spring Kafka versions. Generic
 * UpgradeDependencyVersion is deliberately not used because its version range
 * and ownership behavior are wider than this module's contract.
 */
public final class UpgradeSelectedSpringKafkaDependency extends Recipe {
    private static final String PREFIX =
            SpringKafkaUpgradeSupport.GROUP + ":" + SpringKafkaUpgradeSupport.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade selected Spring Kafka dependencies to 3.3.15";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact org.springframework.kafka:spring-kafka versions 2.8.11 and 2.9.5 in " +
               "unambiguously owned standard Maven or root Gradle declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringKafkaUpgradeSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringKafkaUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profilePropertyNames.add(owner.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyOwner, Integer> references = new HashMap<>();
        Map<PropertyOwner, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        targetVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        Set<PropertyOwner> safe = ownedReferences.keySet().stream()
                .filter(owner -> values.containsKey(owner))
                .filter(owner -> SpringKafkaUpgradeSupport.SOURCE_VERSIONS.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> references.getOrDefault(owner, 0).equals(ownedReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) || !profilePropertyNames.contains(owner.name()))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringKafkaUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    safe.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim)
                            .filter(SpringKafkaUpgradeSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(SpringKafkaUpgradeSupport.TARGET);
                }
                if (SpringKafkaUpgradeSupport.isSpringKafkaDependency(getCursor(), visited) &&
                    SpringKafkaUpgradeSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(SpringKafkaUpgradeSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", SpringKafkaUpgradeSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || SpringKafkaUpgradeSupport.hasVariant(visited)) return visited;
                if (coordinate(SpringKafkaUpgradeSupport.mapValue(visited, "group"),
                               SpringKafkaUpgradeSupport.mapValue(visited, "name")) &&
                    SpringKafkaUpgradeSupport.SOURCE_VERSIONS.contains(
                            SpringKafkaUpgradeSupport.mapValue(visited, "version"))) {
                    return visited.withArguments(visited.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringKafkaUpgradeSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringKafkaUpgradeSupport.replaceLiteral(
                                    literal, SpringKafkaUpgradeSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeable(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean coordinate(String group, String artifact) {
        return SpringKafkaUpgradeSupport.GROUP.equals(group) &&
               SpringKafkaUpgradeSupport.ARTIFACT.equals(artifact);
    }

    private static boolean upgradeable(G.MapLiteral map) {
        return coordinate(SpringKafkaUpgradeSupport.mapValue(map, "group"),
                          SpringKafkaUpgradeSupport.mapValue(map, "name")) &&
               SpringKafkaUpgradeSupport.SOURCE_VERSIONS.contains(
                       SpringKafkaUpgradeSupport.mapValue(map, "version")) &&
               !SpringKafkaUpgradeSupport.hasVariant(map);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringKafkaUpgradeSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringKafkaUpgradeSupport.replaceLiteral(
                                literal, SpringKafkaUpgradeSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !SpringKafkaUpgradeSupport.GROUP.equals(parts[0]) ||
            !SpringKafkaUpgradeSupport.ARTIFACT.equals(parts[1]) ||
            !SpringKafkaUpgradeSupport.SOURCE_VERSIONS.contains(parts[2])) return literal;
        return SpringKafkaUpgradeSupport.replaceLiteral(literal, PREFIX + SpringKafkaUpgradeSupport.TARGET);
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) ||
            !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               SpringKafkaUpgradeSupport.isSpringKafkaDependency(dependencyCursor, dependency) &&
               SpringKafkaUpgradeSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor,
                                          Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name,
                                               Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local)
                ? local : new PropertyOwner("ROOT", name);
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private record PropertyOwner(String scope, String name) {
    }
}
