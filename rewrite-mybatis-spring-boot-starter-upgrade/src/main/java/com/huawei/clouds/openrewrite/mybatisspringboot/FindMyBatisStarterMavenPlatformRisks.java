package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Cursor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
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
            private Map<String, String> projectProperties = Map.of();

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (document.getSourcePath().getFileName() == null ||
                    !"pom.xml".equals(document.getSourcePath().getFileName().toString()) ||
                    !UpgradeSelectedMyBatisSpringBootStarterDependency.isProjectPath(document.getSourcePath()) ||
                    !ownsStarter(document, ctx)) {
                    return document;
                }
                Map<String, String> values = new HashMap<>();
                document.getRoot().getChild("properties").ifPresent(properties -> properties.getChildren().stream()
                        .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> property.getValue()
                                .ifPresent(value -> values.put(property.getName(), value.trim()))));
                projectProperties = values;
                Xml.Document d = super.visitDocument(document, ctx);
                projectProperties = Map.of();
                return d;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if ((JAVA_LEVEL_PROPERTIES.contains(t.getName()) || isCompilerLevel(getCursor(), t)) &&
                    t.getValue().isPresent()) {
                    VersionStatus status = javaStatus(resolve(t.getValue().orElse(""), projectProperties));
                    if (status == VersionStatus.BELOW) {
                        return SearchResult.found(t,
                                "MyBatis Spring Boot Starter 4 requires Java 17 or newer; upgrade the compiler and runtime together");
                    }
                    if (status == VersionStatus.UNKNOWN) {
                        return SearchResult.found(t,
                                "Cannot prove that this Maven Java level resolves to 17 or newer; resolve the value before upgrading the starter");
                    }
                }
                if ("parent".equals(t.getName()) && hasCoordinates(t,
                        "org.springframework.boot", "spring-boot-starter-parent")) {
                    return markBootVersion(t, "parent");
                }
                if ("dependency".equals(t.getName()) && hasCoordinates(t,
                        "org.springframework.boot", "spring-boot-dependencies") &&
                    "import".equals(t.getChildValue("scope").orElse(""))) {
                    return markBootVersion(t, "imported BOM");
                }
                return t;
            }

            private Xml.Tag markBootVersion(Xml.Tag tag, String declaration) {
                VersionStatus status = majorStatus(resolve(tag.getChildValue("version").orElse(""), projectProperties), 4);
                if (status == VersionStatus.BELOW) {
                    return SearchResult.found(tag,
                            "MyBatis Spring Boot Starter 4 requires Spring Boot 4.0 or newer; upgrade the " +
                            declaration + " before the starter");
                }
                if (status == VersionStatus.UNKNOWN) {
                    return SearchResult.found(tag,
                            "Cannot prove that the Spring Boot " + declaration +
                            " resolves to 4.0 or newer; resolve its version before upgrading the starter");
                }
                return tag;
            }
        };
    }

    private static boolean ownsStarter(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedMyBatisSpringBootStarterDependency.isMavenDependencyBlock(getCursor(), tag) &&
                    UpgradeSelectedMyBatisSpringBootStarterDependency.hasCoreCoordinates(tag)) {
                    found[0] = true;
                    return tag;
                }
                return found[0] ? tag : super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static boolean hasCoordinates(Xml.Tag tag, String groupId, String artifactId) {
        return groupId.equals(tag.getChildValue("groupId").orElse("")) &&
               artifactId.equals(tag.getChildValue("artifactId").orElse(""));
    }

    private static boolean isCompilerLevel(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("source", "target", "release").contains(tag.getName())) {
            return false;
        }
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof Xml.Tag ancestor && "plugin".equals(ancestor.getName())) {
                return "maven-compiler-plugin".equals(ancestor.getChildValue("artifactId").orElse(null));
            }
        }
        return false;
    }

    private static String resolve(String raw, Map<String, String> properties) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}") && trimmed.indexOf("${", 2) < 0) {
            return properties.getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }

    private static VersionStatus majorStatus(String version, int minimum) {
        String trimmed = version.trim();
        if (trimmed.isEmpty() || trimmed.contains("${") || trimmed.contains("[") || trimmed.contains("(")) {
            return VersionStatus.UNKNOWN;
        }
        try {
            return Integer.parseInt(trimmed.split("[.-]", 2)[0]) < minimum
                    ? VersionStatus.BELOW : VersionStatus.READY;
        } catch (NumberFormatException ignored) {
            return VersionStatus.UNKNOWN;
        }
    }

    private static VersionStatus javaStatus(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.contains("${")) {
            return VersionStatus.UNKNOWN;
        }
        try {
            int major = trimmed.startsWith("1.")
                    ? Integer.parseInt(trimmed.substring(2).split("[.-]", 2)[0])
                    : Integer.parseInt(trimmed.split("[.-]", 2)[0]);
            return major < 17 ? VersionStatus.BELOW : VersionStatus.READY;
        } catch (NumberFormatException ignored) {
            return VersionStatus.UNKNOWN;
        }
    }

    private enum VersionStatus { BELOW, READY, UNKNOWN }
}
