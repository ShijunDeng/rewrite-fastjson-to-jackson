package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

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

/** Keeps explicitly pinned TwelveMonkeys modules binary-compatible with imageio-jpeg 3.12.0. */
public final class AlignTwelveMonkeysJpegCompanions extends Recipe {
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<Coordinate> FAMILY = Set.of(
            new Coordinate("com.twelvemonkeys.imageio", "imageio-core"),
            new Coordinate("com.twelvemonkeys.imageio", "imageio-metadata"),
            new Coordinate("com.twelvemonkeys.common", "common-lang"),
            new Coordinate("com.twelvemonkeys.common", "common-io"),
            new Coordinate("com.twelvemonkeys.common", "common-image"));

    @Override
    public String getDisplayName() {
        return "Align explicitly pinned TwelveMonkeys JPEG companion modules";
    }

    @Override
    public String getDescription() {
        return "When the same Maven profile/project or root Gradle dependency owner contains imageio-jpeg 3.9.3/3.12.0, aligns exact 3.9.3 core, metadata, and common modules to 3.12.0 without crossing owner boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || TwelveMonkeysJpegSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TwelveMonkeysJpegSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyOwner, Integer> references = new HashMap<>();
        Map<PropertyOwner, Integer> familyReferences = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        Set<String> targetOwners = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                String text = visited.getText();
                boolean family = familyVersion(getCursor());
                boolean target = targetVersion(getCursor());
                Matcher matcher = PROPERTY_REFERENCE.matcher(text);
                while (matcher.find()) {
                    PropertyOwner property = resolvedOwner(getCursor(), matcher.group(1), definitions);
                    references.merge(property, 1, Integer::sum);
                    if (family || target) familyReferences.merge(property, 1, Integer::sum);
                    if (target) targetReferences.merge(property, 1, Integer::sum);
                }
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                Matcher matcher = PROPERTY_REFERENCE.matcher(visited.getValueAsString());
                while (matcher.find()) references.merge(resolvedOwner(getCursor(), matcher.group(1), definitions), 1, Integer::sum);
                return visited;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (targetDependency(getCursor(), visited)) {
                    String declared = visited.getChildValue("version").orElse("").trim();
                    String resolved = resolve(declared, getCursor(), definitions, values);
                    if (selected(resolved)) targetOwners.add(ownerId(getCursor()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyOwner> safeProperties = new HashSet<>();
        for (Map.Entry<PropertyOwner, String> definition : values.entrySet()) {
            PropertyOwner property = definition.getKey();
            if (!TwelveMonkeysJpegSupport.SOURCE.equals(definition.getValue()) || definitions.getOrDefault(property, 0) != 1) continue;
            int all = references.getOrDefault(property, 0);
            int family = familyReferences.getOrDefault(property, 0);
            if (all == 0 || all != family) continue;
            if (targetReferences.getOrDefault(property, 0) > 0 || targetOwners.contains(property.owner())) safeProperties.add(property);
        }

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TwelveMonkeysJpegSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    safeProperties.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(TwelveMonkeysJpegSupport.SOURCE::equals).isPresent()) {
                    return visited.withValue(TwelveMonkeysJpegSupport.TARGET);
                }
                if (!familyDependency(getCursor(), visited) || !targetOwners.contains(ownerId(getCursor()))) return visited;
                if (visited.getChildValue("version").map(String::trim).filter(TwelveMonkeysJpegSupport.SOURCE::equals).isPresent()) {
                    return visited.withChildValue("version", TwelveMonkeysJpegSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean hasTarget = hasTargetGroovy(source, ctx);
        if (!hasTarget) return source;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                boolean direct = TwelveMonkeysJpegSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!direct) return visited;
                if (family(TwelveMonkeysJpegSupport.mapValue(visited, "group"), TwelveMonkeysJpegSupport.mapValue(visited, "name")) &&
                    TwelveMonkeysJpegSupport.SOURCE.equals(TwelveMonkeysJpegSupport.mapValue(visited, "version")) &&
                    !TwelveMonkeysJpegSupport.hasVariant(visited)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry && "version".equals(TwelveMonkeysJpegSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal
                                    ? entry.withValue(TwelveMonkeysJpegSupport.replaceLiteral(literal, TwelveMonkeysJpegSupport.TARGET)) : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map ? alignMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = TwelveMonkeysJpegSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? alignCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasTarget = hasTargetKotlin(source, ctx);
        if (!hasTarget) return source;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = TwelveMonkeysJpegSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? alignCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasTargetGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (TwelveMonkeysJpegSupport.isDirectDependencyLiteral(getCursor()) && targetCoordinate(literal)) found[0] = true;
                return literal;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (TwelveMonkeysJpegSupport.isGradleDependencyInvocation(getCursor(), method) &&
                    TwelveMonkeysJpegSupport.GROUP.equals(TwelveMonkeysJpegSupport.mapValue(method, "group")) &&
                    TwelveMonkeysJpegSupport.ARTIFACT.equals(TwelveMonkeysJpegSupport.mapValue(method, "name")) &&
                    selected(TwelveMonkeysJpegSupport.mapValue(method, "version")) && !TwelveMonkeysJpegSupport.hasVariant(method)) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasTargetKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (TwelveMonkeysJpegSupport.isDirectDependencyLiteral(getCursor()) && targetCoordinate(literal)) found[0] = true;
                return literal;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean targetCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return false;
        String prefix = TwelveMonkeysJpegSupport.GROUP + ":" + TwelveMonkeysJpegSupport.ARTIFACT + ":";
        return value.startsWith(prefix) && selected(value.substring(prefix.length()));
    }

    private static J.Literal alignCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        for (Coordinate coordinate : FAMILY) {
            String source = coordinate.group() + ":" + coordinate.artifact() + ":" + TwelveMonkeysJpegSupport.SOURCE;
            if (source.equals(value)) return TwelveMonkeysJpegSupport.replaceLiteral(literal,
                    coordinate.group() + ":" + coordinate.artifact() + ":" + TwelveMonkeysJpegSupport.TARGET);
        }
        return literal;
    }

    private static G.MapLiteral alignMap(G.MapLiteral map) {
        if (!family(TwelveMonkeysJpegSupport.mapValue(map, "group"), TwelveMonkeysJpegSupport.mapValue(map, "name")) ||
            !TwelveMonkeysJpegSupport.SOURCE.equals(TwelveMonkeysJpegSupport.mapValue(map, "version")) ||
            TwelveMonkeysJpegSupport.hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(TwelveMonkeysJpegSupport.mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(TwelveMonkeysJpegSupport.replaceLiteral(literal, TwelveMonkeysJpegSupport.TARGET)) : entry).toList());
    }

    private static boolean targetDependency(Cursor cursor, Xml.Tag tag) {
        return TwelveMonkeysJpegSupport.isJpegDependency(cursor, tag) && TwelveMonkeysJpegSupport.standardJar(tag);
    }

    private static boolean familyDependency(Cursor cursor, Xml.Tag tag) {
        return TwelveMonkeysJpegSupport.isProjectDependency(cursor, tag) && TwelveMonkeysJpegSupport.standardJar(tag) &&
               family(tag.getChildValue("groupId").orElse(null), tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean familyVersion(Cursor cursor) {
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag tag) || !"version".equals(tag.getName())) return false;
        Cursor dependency = version.getParentTreeCursor();
        return dependency.getValue() instanceof Xml.Tag owner && familyDependency(dependency, owner);
    }

    private static boolean targetVersion(Cursor cursor) {
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag tag) || !"version".equals(tag.getName())) return false;
        Cursor dependency = version.getParentTreeCursor();
        return dependency.getValue() instanceof Xml.Tag owner && targetDependency(dependency, owner);
    }

    private static boolean family(String group, String artifact) {
        return FAMILY.contains(new Coordinate(group, artifact));
    }

    private static boolean selected(String version) {
        return TwelveMonkeysJpegSupport.SOURCE.equals(version) || TwelveMonkeysJpegSupport.TARGET.equals(version);
    }

    private static String resolve(String declared, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                  Map<PropertyOwner, String> values) {
        if (selected(declared)) return declared;
        Matcher matcher = PROPERTY_REFERENCE.matcher(declared);
        if (!matcher.matches()) return null;
        return values.get(resolvedOwner(cursor, matcher.group(1), definitions));
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        return new PropertyOwner(ownerId(cursor), name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name, Map<PropertyOwner, Integer> definitions) {
        String owner = ownerId(cursor);
        PropertyOwner local = "ROOT".equals(owner) ? null : new PropertyOwner(owner, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static String ownerId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
            if (current.getValue() instanceof Xml.Document) return "ROOT";
        }
        return "ROOT";
    }

    private record Coordinate(String group, String artifact) {
    }

    private record PropertyOwner(String owner, String name) {
    }
}
