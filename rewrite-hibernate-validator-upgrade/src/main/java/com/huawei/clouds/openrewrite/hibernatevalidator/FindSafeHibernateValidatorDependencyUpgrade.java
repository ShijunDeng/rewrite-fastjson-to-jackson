package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guards the declarative upgrade from changing ambiguous shared build-version properties. */
public final class FindSafeHibernateValidatorDependencyUpgrade extends Recipe {
    private static final Set<String> SOURCE_VERSIONS = Set.of(
            "6.0.23.Final", "6.1.6.Final", "6.1.7.Final", "6.2.0.Final",
            "6.2.1.Final", "6.2.3.Final", "6.2.4.Final", "6.2.5.Final"
    );
    private static final Set<String> FAMILY_ARTIFACTS = Set.of(
            "hibernate-validator", "hibernate-validator-cdi", "hibernate-validator-annotation-processor"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Pattern DIRECT_GRADLE_CORE = Pattern.compile(
            "org\\.hibernate\\.validator:hibernate-validator:(?:" +
            String.join("|", SOURCE_VERSIONS).replace(".", "\\.") + ")"
    );

    @Override
    public String getDisplayName() {
        return "Find safe Hibernate Validator dependency upgrade sources";
    }

    @Override
    public String getDescription() {
        return "Select build files only when a listed Hibernate Validator version is literal, or its local " +
               "Maven property is referenced exclusively by Hibernate Validator family artifacts.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return safeMavenDocument(document) ? SearchResult.found(document) : document;
                }
                if ((fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts")) &&
                    DIRECT_GRADLE_CORE.matcher(source.printAll()).find()) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }

    private static boolean safeMavenDocument(Xml.Document document) {
        List<Xml.Tag> allTags = new ArrayList<>();
        collect(document.getRoot(), allTags);
        List<Xml.Tag> familyBlocks = allTags.stream()
                .filter(FindSafeHibernateValidatorDependencyUpgrade::isFamilyBlock)
                .toList();
        List<Xml.Tag> coreBlocks = familyBlocks.stream()
                .filter(tag -> "hibernate-validator".equals(tag.getChildValue("artifactId").orElse(null)))
                .toList();
        if (coreBlocks.isEmpty()) {
            return false;
        }

        Map<String, String> localProperties = localProperties(document.getRoot());
        String source = document.printAll();
        boolean foundSelectedCore = false;
        for (Xml.Tag core : coreBlocks) {
            Optional<String> declaredVersion = core.getChildValue("version");
            if (declaredVersion.isEmpty()) {
                continue;
            }
            String version = declaredVersion.get().trim();
            if (SOURCE_VERSIONS.contains(version)) {
                foundSelectedCore = true;
                continue;
            }
            if ("8.0.3.Final".equals(version)) {
                continue;
            }
            Matcher reference = PROPERTY_REFERENCE.matcher(version);
            if (!reference.matches()) {
                return false;
            }
            String property = reference.group(1);
            String resolved = localProperties.get(property);
            if (SOURCE_VERSIONS.contains(resolved)) {
                if (!referencedOnlyByFamily(property, source, familyBlocks)) {
                    return false;
                }
                foundSelectedCore = true;
            } else if (!"8.0.3.Final".equals(resolved)) {
                return false;
            }
        }
        return foundSelectedCore;
    }

    private static Map<String, String> localProperties(Xml.Tag root) {
        Map<String, String> properties = new HashMap<>();
        root.getChild("properties").ifPresent(container -> container.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        return properties;
    }

    private static boolean referencedOnlyByFamily(String property, String source, List<Xml.Tag> familyBlocks) {
        String placeholder = "${" + property + "}";
        int total = occurrences(source, placeholder);
        int family = familyBlocks.stream().mapToInt(block -> occurrences(block, placeholder)).sum();
        return total > 0 && total == family;
    }

    private static int occurrences(Xml.Tag tag, String value) {
        int count = tag.getValue().map(text -> occurrences(text, value)).orElse(0);
        return count + tag.getChildren().stream().mapToInt(child -> occurrences(child, value)).sum();
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) {
            count++;
        }
        return count;
    }

    private static boolean isFamilyBlock(Xml.Tag tag) {
        return ("dependency".equals(tag.getName()) || "path".equals(tag.getName())) &&
               "org.hibernate.validator".equals(tag.getChildValue("groupId").orElse(null)) &&
               tag.getChildValue("artifactId").filter(FAMILY_ARTIFACTS::contains).isPresent();
    }

    private static void collect(Xml.Tag tag, List<Xml.Tag> tags) {
        tags.add(tag);
        tag.getChildren().forEach(child -> collect(child, tags));
    }
}
