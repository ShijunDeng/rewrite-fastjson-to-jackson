package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Marks Maven platform declarations that cannot run MyBatis Spring Boot Starter 4. */
public final class FindMyBatisStarterMavenPlatformRisks extends Recipe {
    private static final Set<String> JAVA_LEVEL_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Maven platform blockers for MyBatis Starter 4";
    }

    @Override
    public String getDescription() {
        return "Mark Spring Boot parents/BOMs below 4 and Java compiler levels below 17 in Maven POMs.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (JAVA_LEVEL_PROPERTIES.contains(t.getName()) && t.getValue().filter(
                        FindMyBatisStarterMavenPlatformRisks::isJavaBelow17).isPresent()) {
                    return SearchResult.found(t,
                            "MyBatis Spring Boot Starter 4 requires Java 17 or newer; upgrade the compiler and runtime together");
                }
                if ("parent".equals(t.getName()) && hasCoordinates(t,
                        "org.springframework.boot", "spring-boot-starter-parent") && hasVersionBelow4(t)) {
                    return SearchResult.found(t,
                            "MyBatis Spring Boot Starter 4 requires Spring Boot 4.0 or newer; upgrade the parent before the starter");
                }
                if ("dependency".equals(t.getName()) && hasCoordinates(t,
                        "org.springframework.boot", "spring-boot-dependencies") &&
                    "import".equals(t.getChildValue("scope").orElse("")) && hasVersionBelow4(t)) {
                    return SearchResult.found(t,
                            "MyBatis Spring Boot Starter 4 requires Spring Boot 4.0 or newer; upgrade the imported BOM before the starter");
                }
                return t;
            }
        };
    }

    private static boolean hasCoordinates(Xml.Tag tag, String groupId, String artifactId) {
        return groupId.equals(tag.getChildValue("groupId").orElse("")) &&
               artifactId.equals(tag.getChildValue("artifactId").orElse(""));
    }

    private static boolean hasVersionBelow4(Xml.Tag tag) {
        return tag.getChildValue("version").filter(FindMyBatisStarterMavenPlatformRisks::isMajorBelow4).isPresent();
    }

    private static boolean isMajorBelow4(String version) {
        String trimmed = version.trim();
        if (trimmed.startsWith("${")) {
            return true;
        }
        try {
            return Integer.parseInt(trimmed.split("[.-]", 2)[0]) < 4;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private static boolean isJavaBelow17(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("${")) {
            return false;
        }
        try {
            int major = trimmed.startsWith("1.")
                    ? Integer.parseInt(trimmed.substring(2).split("[.-]", 2)[0])
                    : Integer.parseInt(trimmed.split("[.-]", 2)[0]);
            return major < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
