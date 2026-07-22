package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prevents declarative dependency recipes from repurposing shared Maven version properties. */
public final class FindSafeMyBatisStarterDependencyUpgrade extends Recipe {
    private static final Set<String> SOURCE_VERSIONS = Set.of(
            "1.1.1", "1.3.2", "2.0.0", "2.1.2", "2.1.3", "2.1.4",
            "2.2.0", "2.2.2", "2.3.0", "2.3.1"
    );
    private static final Set<String> FAMILY_ARTIFACTS = Set.of(
            "mybatis-spring-boot-starter", "mybatis-spring-boot-autoconfigure",
            "mybatis-spring-boot-test-autoconfigure", "mybatis-spring-boot-starter-test"
    );
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Pattern DIRECT_GRADLE = Pattern.compile(
            "org[.]mybatis[.]spring[.]boot:mybatis-spring-boot-starter:(?:" +
            String.join("|", SOURCE_VERSIONS).replace(".", "[.]") + ")"
    );

    @Override
    public String getDisplayName() {
        return "Find safe MyBatis Spring Boot Starter dependency upgrade sources";
    }

    @Override
    public String getDescription() {
        return "Select only literal selected versions or local Maven properties referenced exclusively by " +
               "the MyBatis Spring Boot Starter family, preventing unrelated dependencies from changing.";
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
                    return safeMaven(document) ? SearchResult.found(document) : document;
                }
                if ((fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts")) &&
                    DIRECT_GRADLE.matcher(source.printAll()).find()) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }

    private static boolean safeMaven(Xml.Document document) {
        List<Xml.Tag> tags = new ArrayList<>();
        collect(document.getRoot(), tags);
        List<Xml.Tag> family = tags.stream().filter(FindSafeMyBatisStarterDependencyUpgrade::isFamily).toList();
        List<Xml.Tag> core = family.stream()
                .filter(tag -> "mybatis-spring-boot-starter".equals(tag.getChildValue("artifactId").orElse(null)))
                .toList();
        if (core.isEmpty()) {
            return false;
        }

        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        String source = document.printAll();
        boolean selectedCore = false;
        for (Xml.Tag block : family) {
            Optional<String> declared = block.getChildValue("version");
            if (declared.isEmpty()) {
                continue;
            }
            String version = declared.get().trim();
            if (SOURCE_VERSIONS.contains(version)) {
                if ("mybatis-spring-boot-starter".equals(block.getChildValue("artifactId").orElse(null))) {
                    selectedCore = true;
                }
                continue;
            }
            Matcher matcher = PROPERTY.matcher(version);
            if (!matcher.matches()) {
                continue;
            }
            String property = matcher.group(1);
            String resolved = properties.get(property);
            if (SOURCE_VERSIONS.contains(resolved)) {
                if (!referencedOnlyByFamily(property, source, family)) {
                    return false;
                }
                if ("mybatis-spring-boot-starter".equals(block.getChildValue("artifactId").orElse(null))) {
                    selectedCore = true;
                }
            }
        }
        return selectedCore;
    }

    private static boolean referencedOnlyByFamily(String property, String source, List<Xml.Tag> family) {
        String placeholder = "${" + property + "}";
        int total = occurrences(source, placeholder);
        int allowed = family.stream().mapToInt(tag -> occurrences(tag, placeholder)).sum();
        return total > 0 && total == allowed;
    }

    private static boolean isFamily(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               "org.mybatis.spring.boot".equals(tag.getChildValue("groupId").orElse(null)) &&
               tag.getChildValue("artifactId").filter(FAMILY_ARTIFACTS::contains).isPresent();
    }

    private static int occurrences(Xml.Tag tag, String value) {
        int own = tag.getValue().map(text -> occurrences(text, value)).orElse(0);
        return own + tag.getChildren().stream().mapToInt(child -> occurrences(child, value)).sum();
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) count++;
        return count;
    }

    private static void collect(Xml.Tag tag, List<Xml.Tag> tags) {
        tags.add(tag);
        tag.getChildren().forEach(child -> collect(child, tags));
    }
}
