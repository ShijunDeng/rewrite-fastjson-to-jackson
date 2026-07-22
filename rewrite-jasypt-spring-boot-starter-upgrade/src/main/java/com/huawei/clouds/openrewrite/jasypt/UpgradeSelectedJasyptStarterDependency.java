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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrades only spreadsheet-selected, locally visible Jasypt starter versions. */
public final class UpgradeSelectedJasyptStarterDependency extends Recipe {
    private static final String GROUP = "com.github.ulisesbocchio";
    private static final String ARTIFACT = "jasypt-spring-boot-starter";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Jasypt Spring Boot Starter dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrade only the five exact spreadsheet source versions when the value is visible in the current Maven or Gradle file, without overriding external management or shared properties.";
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
                    return upgradePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, p);
                            if (!JasyptVersions.isGradleDependencyInvocation(getCursor(), m) || hasClassifier(m)) {
                                return m;
                            }
                            if (GROUP.equals(mapValue(m, "group")) && ARTIFACT.equals(mapValue(m, "name")) &&
                                JasyptVersions.isSource(mapValue(m, "version"))) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) :
                                        argument instanceof G.MapLiteral map && isStarterMap(map) && !hasClassifier(map)
                                                ? upgradeVersion(map) : argument).toList());
                            }
                            return m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property -> {
                    definitions.merge(property.getName(), 1, Integer::sum);
                    property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()));
                }));
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> starterReferences = new HashMap<>();
        Set<String> shadowedProperties = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, p);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, p);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (isProfileProperty(getCursor(), tag)) {
                    shadowedProperties.add(tag.getName());
                }
                if (isUpgradeableStarter(getCursor(), tag)) {
                    propertyName(tag.getChildValue("version").orElse(null))
                            .ifPresent(name -> starterReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, p);
            }
        }.visit(document, ctx);

        Set<String> eligibleOwnedProperties = new HashSet<>();
        starterReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && definitions.getOrDefault(name, 0) == 1 &&
                !shadowedProperties.contains(name) && JasyptVersions.isSource(properties.get(name))) {
                eligibleOwnedProperties.add(name);
            }
        });
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (isUpgradeableStarter(getCursor(), t)) {
                    String version = t.getChildValue("version").orElse(null);
                    if (JasyptVersions.isSource(version)) {
                        return t.withChildValue("version", JasyptVersions.TARGET);
                    }
                }
                if (isRootProperty(getCursor(), t) && eligibleOwnedProperties.contains(t.getName()) &&
                    JasyptVersions.isSource(t.getValue().orElse(null))) {
                    return t.withValue(JasyptVersions.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) {
            return false;
        }
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) || !"dependencies".equals(container.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) {
            return false;
        }
        if (isProjectOwner(owner) || isProfileOwner(owner)) {
            return true;
        }
        if (!"dependencyManagement".equals(ownerTag.getName())) {
            return false;
        }
        Cursor managedOwner = owner.getParentTreeCursor();
        return managedOwner != null && (isProjectOwner(managedOwner) || isProfileOwner(managedOwner));
    }

    static boolean isStarter(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean isUpgradeableStarter(Cursor cursor, Xml.Tag tag) {
        return isStandardStarter(cursor, tag);
    }

    static boolean isStandardStarter(Cursor cursor, Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean standardType = tag.getChildValue("type").map(String::trim)
                .filter(value -> !value.isEmpty()).map("jar"::equals).orElse(true);
        return isStarter(cursor, tag) && noClassifier && standardType;
    }

    private static Optional<String> propertyName(String version) {
        return version != null && version.startsWith("${") && version.endsWith("}")
                ? Optional.of(version.substring(2, version.length() - 1)) : Optional.empty();
    }

    private static void collectReferences(String source, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(source);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               JasyptVersions.isGradleDependencyInvocation(parent, invocation);
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
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

    private static boolean hasClassifier(J.MethodInvocation invocation) {
        return hasMapKey(invocation, "classifier") || hasMapKey(invocation, "ext") ||
               hasMapKey(invocation, "type");
    }

    private static boolean hasClassifier(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> Set.of("classifier", "ext", "type")
                .contains(mapKey(entry)));
    }

    private static boolean isStarterMap(G.MapLiteral map) {
        return GROUP.equals(mapValue(map, "group")) && ARTIFACT.equals(mapValue(map, "name")) &&
               JasyptVersions.isSource(mapValue(map, "version"));
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static boolean hasMapKey(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream()
                        .anyMatch(entry -> key.equals(mapKey(entry))));
    }

    private static G.MapLiteral upgradeVersion(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    static boolean isRootProperty(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag propertiesTag) ||
            !"properties".equals(propertiesTag.getName())) {
            return false;
        }
        Cursor project = properties.getParentTreeCursor();
        Cursor document = project == null ? null : project.getParentTreeCursor();
        return project != null && project.getValue() instanceof Xml.Tag projectTag &&
               "project".equals(projectTag.getName()) && document != null &&
               document.getValue() instanceof Xml.Document;
    }

    private static boolean isPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        return properties != null && properties.getValue() instanceof Xml.Tag propertiesTag &&
               "properties".equals(propertiesTag.getName()) && !"properties".equals(tag.getName());
    }

    private static boolean isProfileProperty(Cursor cursor, Xml.Tag tag) {
        if (!isPropertyDefinition(cursor, tag)) return false;
        Cursor properties = cursor.getParentTreeCursor();
        return isProfileOwner(properties.getParentTreeCursor());
    }

    private static boolean isProjectOwner(Cursor cursor) {
        if (cursor == null || !(cursor.getValue() instanceof Xml.Tag project) ||
            !"project".equals(project.getName())) return false;
        Cursor document = cursor.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }

    private static boolean isProfileOwner(Cursor cursor) {
        if (cursor == null || !(cursor.getValue() instanceof Xml.Tag profile) ||
            !"profile".equals(profile.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        if (profiles == null || !(profiles.getValue() instanceof Xml.Tag profilesTag) ||
            !"profiles".equals(profilesTag.getName())) return false;
        return isProjectOwner(profiles.getParentTreeCursor());
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !GROUP.equals(parts[0]) || !ARTIFACT.equals(parts[1]) ||
            !JasyptVersions.isSource(parts[2])) {
            return literal;
        }
        String replacement = PREFIX + JasyptVersions.TARGET;
        return literal.withValue(replacement).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(value, replacement));
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !JasyptVersions.isSource(value)) {
            return literal;
        }
        return literal.withValue(JasyptVersions.TARGET).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(value, JasyptVersions.TARGET));
    }
}
