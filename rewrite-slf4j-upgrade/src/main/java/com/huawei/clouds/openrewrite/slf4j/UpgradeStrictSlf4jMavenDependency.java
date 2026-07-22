package com.huawei.clouds.openrewrite.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strictly upgrades locally declared Maven SLF4J API versions without crossing management boundaries. */
public final class UpgradeStrictSlf4jMavenDependency extends Recipe {
    static final String TARGET_VERSION = "2.0.17";
    static final Set<String> LISTED_VERSIONS = Set.of(
            "1.7.25", "1.7.26", "1.7.30", "1.7.32", "1.7.34", "1.7.35", "1.7.36",
            "2.0.0", "2.0.0-alpha1", "2.0.6");

    @Override
    public String getDisplayName() {
        return "Strictly upgrade Maven SLF4J API declarations to 2.0.17";
    }

    @Override
    public String getDescription() {
        return "Upgrade only spreadsheet-listed org.slf4j:slf4j-api versions declared in the current POM; " +
               "isolate shared properties and leave externally managed dependencies without a version untouched.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                Xml.Document document = getCursor().firstEnclosing(Xml.Document.class);
                if (document == null || !"pom.xml".equals(document.getSourcePath().getFileName().toString())) {
                    return t;
                }
                Xml.Tag root = document.getRoot();
                if ("dependency".equals(t.getName()) && isSlf4jApi(t)) {
                    String rawVersion = t.getChildValue("version").map(String::trim).orElse("");
                    if (LISTED_VERSIONS.contains(rawVersion)) {
                        return withDependencyVersion(t, TARGET_VERSION);
                    }
                    String propertyName = propertyName(rawVersion);
                    if (propertyName != null) {
                        String propertyValue = propertyValue(root, propertyName);
                        if (LISTED_VERSIONS.contains(propertyValue) && countPropertyUses(root, rawVersion) > 1) {
                            return withDependencyVersion(t, TARGET_VERSION);
                        }
                    }
                    return t;
                }
                Xml.Tag parent = getCursor().getParentOrThrow().firstEnclosing(Xml.Tag.class);
                if (parent != null && "properties".equals(parent.getName()) && LISTED_VERSIONS.contains(
                        t.getValue().map(String::trim).orElse(""))) {
                    String reference = "${" + t.getName() + "}";
                    if (countPropertyUses(root, reference) == 1 && apiUsesProperty(root, reference)) {
                        return t.withValue(TARGET_VERSION);
                    }
                }
                return t;
            }
        };
    }

    static boolean isSlf4jApi(Xml.Tag dependency) {
        return "org.slf4j".equals(dependency.getChildValue("groupId").orElse("")) &&
               "slf4j-api".equals(dependency.getChildValue("artifactId").orElse(""));
    }

    static String propertyValue(Xml.Tag root, String propertyName) {
        return root.getChild("properties")
                .flatMap(properties -> properties.getChildValue(propertyName))
                .map(String::trim)
                .orElse("");
    }

    static String propertyName(String rawVersion) {
        return rawVersion.startsWith("${") && rawVersion.endsWith("}")
                ? rawVersion.substring(2, rawVersion.length() - 1) : null;
    }

    static int countPropertyUses(Xml.Tag tag, String reference) {
        int count = countOccurrences(tag.getValue().orElse(""), reference);
        for (Xml.Attribute attribute : tag.getAttributes()) {
            count += countOccurrences(attribute.getValueAsString(), reference);
        }
        for (Xml.Tag child : tag.getChildren()) {
            count += countPropertyUses(child, reference);
        }
        return count;
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        for (int offset = source.indexOf(token); offset >= 0;
             offset = source.indexOf(token, offset + token.length())) {
            count++;
        }
        return count;
    }

    private static boolean apiUsesProperty(Xml.Tag tag, String reference) {
        if ("dependency".equals(tag.getName()) && isSlf4jApi(tag) &&
            reference.equals(tag.getChildValue("version").map(String::trim).orElse(""))) {
            return true;
        }
        return tag.getChildren().stream().anyMatch(child -> apiUsesProperty(child, reference));
    }

    static Xml.Tag withDependencyVersion(Xml.Tag dependency, String version) {
        List<Content> content = new ArrayList<>(dependency.getContent().size());
        for (Content child : dependency.getContent()) {
            if (child instanceof Xml.Tag childTag && "version".equals(childTag.getName())) {
                content.add(childTag.withValue(version));
            } else {
                content.add(child);
            }
        }
        return dependency.withContent(content);
    }
}
