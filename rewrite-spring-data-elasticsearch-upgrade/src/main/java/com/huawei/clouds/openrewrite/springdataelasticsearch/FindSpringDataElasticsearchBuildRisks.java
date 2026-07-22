package com.huawei.clouds.openrewrite.springdataelasticsearch;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact build declarations that must move with Spring Data Elasticsearch 6.0.5. */
public final class FindSpringDataElasticsearchBuildRisks extends Recipe {
    static final String UNSELECTED_MESSAGE =
            "Spring Data Elasticsearch remains unselected, ranged, property-managed, patched, or outside the spreadsheet whitelist; choose 6.0.5 explicitly or migrate its central owner and verify the resolved dependency graph";
    static final String VERSIONLESS_MESSAGE =
            "Versionless Spring Data Elasticsearch is owned by a BOM/parent; upgrade that single owner to the Spring Data 2025.1/Spring Boot 4 line instead of adding a leaf override, then inspect the resolved graph";
    static final String CUSTOM_ARTIFACT_MESSAGE =
            "This classified or non-JAR Spring Data Elasticsearch artifact was not changed; verify that 6.0.5 publishes the same artifact shape before migrating it";
    static final String BOOT_MESSAGE =
            "Spring Data Elasticsearch 6.0.5 belongs to Spring Data 2025.1/Spring Framework 7; align this Spring Boot parent/starter/BOM to the 4.x generation and run its migrations rather than mixing generations";
    static final String SPRING_DATA_MESSAGE =
            "Align Spring Data modules/BOM with the 2025.1 train (Spring Data Commons 4.x); mixed Spring Data generations can fail through binary linkage, repository factories, mapping, converters, and transactions";
    static final String ELASTICSEARCH_MESSAGE =
            "Spring Data Elasticsearch 6.0 uses Elasticsearch 9 and the Rest5Client; remove RHLC/ES7-8 client pinning or align this exact client intentionally, then verify server compatibility, TLS, serialization, plugins, and direct APIs";
    private static final String GRADLE_BOOT_MESSAGE =
            "Align this Spring Boot dependency/platform to 4.x for the Spring Data 2025.1/Spring Framework 7 generation; do not mix Boot 2/3 with Spring Data Elasticsearch 6.0.5";
    private static final String GRADLE_SPRING_DATA_MESSAGE =
            "Align this Spring Data module/platform with the 2025.1 train; verify repository factories, mapping, converters, transactions, and binary linkage as one generation";
    private static final String GRADLE_ELASTICSEARCH_MESSAGE =
            "Spring Data Elasticsearch 6.0 uses Elasticsearch 9 Rest5Client; replace RHLC/ES7-8 client pinning or align this exact direct client deliberately and test server/protocol/security/serialization compatibility";

    @Override
    public String getDisplayName() {
        return "Find Spring Data Elasticsearch 6 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unselected/managed target declarations and incompatible Spring Boot, Spring Data, " +
               "Elasticsearch client, Maven Java baseline, and central property boundaries at exact Maven/Gradle nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    UpgradeSelectedSpringDataElasticsearchDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return inspectPom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return inspectGroovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    if (compilationUnit.getMarkers().findFirst(GradleProject.class).isEmpty()) return tree;
                    return inspectKotlin(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document inspectPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = localProperties(document);
        Set<String> shadowedProperties = shadowedProperties(document, ctx);
        boolean[] ownsCore = {false};
        boolean[] localTargetPlatform = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedSpringDataElasticsearchDependency.isMavenDependencyBlock(getCursor(), tag)) {
                    if (UpgradeSelectedSpringDataElasticsearchDependency.hasTargetCoordinates(tag)) {
                        ownsCore[0] = true;
                        if (isRootDependencyManagementEntry(getCursor()) &&
                            UpgradeSelectedSpringDataElasticsearchDependency.isStandardJar(tag) &&
                            UpgradeSelectedSpringDataElasticsearchDependency.TARGET.equals(
                                    resolve(tag.getChildValue("version").orElse(""), properties,
                                            shadowedProperties))) {
                            localTargetPlatform[0] = true;
                        }
                    }
                    if (isRootDependencyManagementEntry(getCursor()) &&
                        targetBom(tag, properties, shadowedProperties)) {
                        localTargetPlatform[0] = true;
                    }
                }
                if (isRootParent(getCursor(), tag) &&
                    targetBootPlatform(tag, properties, shadowedProperties)) {
                    localTargetPlatform[0] = true;
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        if (!ownsCore[0]) return document;

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isProjectPropertiesChild(getCursor(), visited) && isJavaBaselineProperty(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(FindSpringDataElasticsearchBuildRisks::belowJava17)
                            .isPresent()) {
                    return mark(visited,
                            "Spring Data Elasticsearch 6.0.5 requires Java 17 or newer; raise this exact Maven Java baseline and recompile all production, test, annotation-processor, plugin, and generated sources on the chosen JDK");
                }
                if (UpgradeSelectedSpringDataElasticsearchDependency.isMavenDependencyBlock(getCursor(), visited) &&
                    UpgradeSelectedSpringDataElasticsearchDependency.hasTargetCoordinates(visited)) {
                    if (!UpgradeSelectedSpringDataElasticsearchDependency.isStandardJar(visited)) {
                        return mark(visited, CUSTOM_ARTIFACT_MESSAGE);
                    }
                    String declared = visited.getChildValue("version").map(String::trim).orElse("");
                    if (declared.isEmpty()) {
                        if (isDirectDependency(getCursor()) && localTargetPlatform[0]) return visited;
                        return mark(visited, VERSIONLESS_MESSAGE);
                    }
                    return UpgradeSelectedSpringDataElasticsearchDependency.TARGET.equals(
                            resolve(declared, properties, shadowedProperties))
                            ? visited : markVersion(visited, UNSELECTED_MESSAGE);
                }
                if (!isMavenPeerOrParent(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String version = resolve(visited.getChildValue("version").orElse(""), properties, shadowedProperties);
                if ("org.springframework.boot".equals(group) && !major(version, 4)) {
                    return markVersion(visited, BOOT_MESSAGE);
                }
                if ("org.springframework.data".equals(group) &&
                    !UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT.equals(artifact) &&
                    !springDataTarget(version, artifact)) {
                    return markVersion(visited, SPRING_DATA_MESSAGE);
                }
                if (elasticsearchGroup(group) && (!major(version, 9) || artifact.contains("rest-high-level-client"))) {
                    return markVersion(visited, ELASTICSEARCH_MESSAGE);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit inspectGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        if (!containsGroovyCore(compilationUnit, ctx)) return compilationUnit;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                return UpgradeSelectedSpringDataElasticsearchDependency.isGradleDependencyInvocation(
                        getCursor(), visited) ? markGradleDependency(visited, getCursor()) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit inspectKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        if (!containsKotlinCore(compilationUnit, ctx)) return compilationUnit;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                return UpgradeSelectedSpringDataElasticsearchDependency.isGradleDependencyInvocation(
                        getCursor(), visited) ? markGradleDependency(visited, getCursor()) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static boolean containsGroovyCore(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedSpringDataElasticsearchDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    containsArtifact(compact(method, getCursor()), UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT)) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean containsKotlinCore(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedSpringDataElasticsearchDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    containsArtifact(compact(method, getCursor()), UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT)) {
                    found[0] = true;
                }
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method, Cursor cursor) {
        String compact = compact(method, cursor);
        if (containsArtifact(compact, UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT)) {
            if (UpgradeSelectedSpringDataElasticsearchDependency.hasGradleVariant(method) ||
                customGradleArtifact(compact)) return mark(method, CUSTOM_ARTIFACT_MESSAGE);
            if (!containsTarget(compact, UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT)) {
                return mark(method, UNSELECTED_MESSAGE);
            }
        }
        if (containsGroup(compact, "org.springframework.boot") && !containsMajor(compact, 4)) {
            return mark(method, GRADLE_BOOT_MESSAGE);
        }
        if (containsGroup(compact, "org.springframework.data") &&
            !containsArtifact(compact, UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT) &&
            !springDataTargetInGradle(compact)) {
            return mark(method, GRADLE_SPRING_DATA_MESSAGE);
        }
        if ((containsGroup(compact, "org.elasticsearch") || containsGroup(compact, "org.elasticsearch.client") ||
             containsGroup(compact, "co.elastic.clients")) &&
            (!containsMajor(compact, 9) || compact.contains("rest-high-level-client"))) {
            return mark(method, GRADLE_ELASTICSEARCH_MESSAGE);
        }
        return method;
    }

    private static String compact(J.MethodInvocation method, Cursor cursor) {
        return method.printTrimmed(cursor).replaceAll("\\s+", "");
    }

    private static boolean containsArtifact(String compact, String artifact) {
        String coordinate = UpgradeSelectedSpringDataElasticsearchDependency.GROUP + ":" + artifact + ":";
        String versionless = UpgradeSelectedSpringDataElasticsearchDependency.GROUP + ":" + artifact;
        return compact.contains("'" + coordinate) || compact.contains("\"" + coordinate) ||
               compact.contains("'" + versionless + "'") || compact.contains("\"" + versionless + "\"") ||
               containsMapPair(compact, "group", UpgradeSelectedSpringDataElasticsearchDependency.GROUP) &&
               containsMapPair(compact, "name", artifact);
    }

    private static boolean containsTarget(String compact, String artifact) {
        String coordinate = UpgradeSelectedSpringDataElasticsearchDependency.GROUP + ":" + artifact + ":" +
                            UpgradeSelectedSpringDataElasticsearchDependency.TARGET;
        return compact.contains("'" + coordinate + "'") || compact.contains("\"" + coordinate + "\"") ||
               containsMapPair(compact, "group", UpgradeSelectedSpringDataElasticsearchDependency.GROUP) &&
               containsMapPair(compact, "name", artifact) &&
               containsMapPair(compact, "version", UpgradeSelectedSpringDataElasticsearchDependency.TARGET);
    }

    private static boolean containsGroup(String compact, String group) {
        return compact.contains("'" + group + ":") || compact.contains("\"" + group + ":") ||
               containsMapPair(compact, "group", group);
    }

    private static boolean containsMapPair(String compact, String key, String value) {
        return compact.contains(key + ":'" + value + "'") || compact.contains(key + ":\"" + value + "\"") ||
               compact.contains("'" + key + "':'" + value + "'") ||
               compact.contains("\"" + key + "\":\"" + value + "\"");
    }

    private static boolean containsMajor(String compact, int expected) {
        return compact.matches(".*[:=][\\\"']?[~^]?" + expected + "(?:\\.\\d+){1,2}(?:[-.][^\\\"']*)?[\\\"']?.*");
    }

    private static boolean springDataTargetInGradle(String compact) {
        if (compact.contains("spring-data-bom")) return compact.contains("2025.1.");
        return containsMajor(compact, 4);
    }

    private static boolean customGradleArtifact(String compact) {
        String prefix = UpgradeSelectedSpringDataElasticsearchDependency.GROUP + ":" +
                        UpgradeSelectedSpringDataElasticsearchDependency.ARTIFACT + ":";
        int start = compact.indexOf(prefix);
        if (start < 0) return false;
        int single = compact.indexOf('\'', start);
        int doubleQuote = compact.indexOf('"', start);
        int end = single < 0 ? doubleQuote : doubleQuote < 0 ? single : Math.min(single, doubleQuote);
        String coordinate = end < 0 ? compact.substring(start) : compact.substring(start, end);
        return coordinate.contains("@") || coordinate.substring(prefix.length()).contains(":");
    }

    private static Map<String, String> localProperties(Xml.Document document) {
        Map<String, String> properties = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container ->
                container.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> {
                            definitions.merge(property.getName(), 1, Integer::sum);
                            property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()));
                        }));
        properties.keySet().removeIf(name -> definitions.getOrDefault(name, 0) != 1);
        return properties;
    }

    private static String resolve(String raw, Map<String, String> properties, Set<String> shadowedProperties) {
        String version = raw.trim();
        if (version.startsWith("${") && version.endsWith("}") && version.indexOf("${", 2) < 0) {
            String name = version.substring(2, version.length() - 1);
            return shadowedProperties.contains(name) ? version : properties.getOrDefault(name, version);
        }
        return version;
    }

    private static Set<String> shadowedProperties(Xml.Document document, ExecutionContext ctx) {
        Set<String> shadowed = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent.getValue() instanceof Xml.Tag properties && "properties".equals(properties.getName()) &&
                    !isProjectPropertiesChild(getCursor(), tag)) shadowed.add(tag.getName());
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return shadowed;
    }

    private static boolean targetBom(Xml.Tag tag, Map<String, String> properties,
                                     Set<String> shadowedProperties) {
        String group = tag.getChildValue("groupId").map(String::trim).orElse("");
        String artifact = tag.getChildValue("artifactId").map(String::trim).orElse("");
        String version = resolve(tag.getChildValue("version").orElse(""), properties, shadowedProperties);
        return "pom".equals(tag.getChildValue("type").map(String::trim).orElse("")) &&
               "import".equals(tag.getChildValue("scope").map(String::trim).orElse("")) &&
               (("org.springframework.data".equals(group) && "spring-data-bom".equals(artifact) &&
                 version.startsWith("2025.1.")) ||
                ("org.springframework.boot".equals(group) && "spring-boot-dependencies".equals(artifact) &&
                 major(version, 4)));
    }

    private static boolean targetBootPlatform(Xml.Tag tag, Map<String, String> properties,
                                              Set<String> shadowedProperties) {
        return "org.springframework.boot".equals(tag.getChildValue("groupId").map(String::trim).orElse("")) &&
               "spring-boot-starter-parent".equals(tag.getChildValue("artifactId").map(String::trim).orElse("")) &&
               major(resolve(tag.getChildValue("version").orElse(""), properties, shadowedProperties), 4);
    }

    private static boolean isMavenPeerOrParent(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedSpringDataElasticsearchDependency.isMavenDependencyBlock(cursor, tag) ||
               isRootParent(cursor, tag);
    }

    private static boolean isDirectDependency(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        if (dependencies == null || !(dependencies.getValue() instanceof Xml.Tag dependenciesTag) ||
            !"dependencies".equals(dependenciesTag.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        return owner != null && owner.getValue() instanceof Xml.Tag ownerTag &&
               ("project".equals(ownerTag.getName()) || "profile".equals(ownerTag.getName()));
    }

    private static boolean isRootDependencyManagementEntry(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor management = dependencies == null ? null : dependencies.getParentTreeCursor();
        Cursor project = management == null ? null : management.getParentTreeCursor();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && management != null &&
               management.getValue() instanceof Xml.Tag managementTag &&
               "dependencyManagement".equals(managementTag.getName()) && project != null &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName());
    }

    private static boolean isRootParent(Cursor cursor, Xml.Tag tag) {
        if (!"parent".equals(tag.getName())) return false;
        Cursor project = cursor.getParentTreeCursor();
        Cursor document = project == null ? null : project.getParentTreeCursor();
        return project != null && project.getValue() instanceof Xml.Tag projectTag &&
               "project".equals(projectTag.getName()) && document != null && document.getValue() instanceof Xml.Document;
    }

    private static boolean isProjectPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag propertiesTag) ||
            !"properties".equals(propertiesTag.getName())) return false;
        Cursor project = properties == null ? null : properties.getParentTreeCursor();
        Cursor document = project == null ? null : project.getParentTreeCursor();
        return project != null && project.getValue() instanceof Xml.Tag projectTag &&
               "project".equals(projectTag.getName()) &&
               document != null && document.getValue() instanceof Xml.Document;
    }

    private static boolean isJavaBaselineProperty(String name) {
        return "java.version".equals(name) || "maven.compiler.release".equals(name) ||
               "maven.compiler.source".equals(name) || "maven.compiler.target".equals(name);
    }

    private static boolean belowJava17(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        int dot = normalized.indexOf('.');
        if (dot >= 0) normalized = normalized.substring(0, dot);
        try {
            return Integer.parseInt(normalized) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean springDataTarget(String version, String artifact) {
        if ("spring-data-bom".equals(artifact)) return version.startsWith("2025.1.");
        return major(version, 4);
    }

    private static boolean elasticsearchGroup(String group) {
        return "org.elasticsearch".equals(group) || group.startsWith("org.elasticsearch.") ||
               "co.elastic.clients".equals(group);
    }

    private static boolean major(String version, int expected) {
        return version.matches("[~^]?" + expected + "(?:\\.\\d+){1,2}(?:[-.].*)?");
    }

    private static Xml.Tag markVersion(Xml.Tag owner, String message) {
        return owner.getChild("version").map(version -> {
            Xml.Tag marked = mark(version, message);
            if (marked == version) return owner;
            return owner.withContent(owner.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        boolean present = tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()));
        return present ? tree : SearchResult.found(tree, message);
    }
}
