package com.huawei.clouds.openrewrite.hibernate;

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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Marks exact build declarations that must be aligned with Hibernate ORM 7.2. */
public final class FindHibernate7BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Set<String> HIBERNATE_COMPANIONS = Set.of(
            "hibernate-envers", "hibernate-spatial", "hibernate-jcache", "hibernate-jpamodelgen",
            "hibernate-testing", "hibernate-community-dialects", "hibernate-enhance-maven-plugin"
    );

    @Override
    public String getDisplayName() {
        return "Find Hibernate 7 build baseline and companion risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact unselected/managed Hibernate Core, Java <17, Javax Persistence, Hibernate companion, " +
               "Spring/Quarkus/Hypersistence, and Gradle baseline nodes without changing their versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedHibernateCoreDependency.generated(source.getSourcePath())) return tree;
                String printed = source.printAll();
                if (!printed.contains("hibernate-core")) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new MavenRisks(localProperties(document, ctx), hasMavenHibernateCore(document, ctx))
                            .visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyRisks(hasGradleHibernateCore(groovy, ctx)).visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinRisks(hasGradleHibernateCore(kotlin, ctx)).visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static final class MavenRisks extends XmlIsoVisitor<ExecutionContext> {
        private final Map<String, String> properties;
        private final boolean hasProjectHibernateCore;

        private MavenRisks(Map<String, String> properties, boolean hasProjectHibernateCore) {
            this.properties = properties;
            this.hasProjectHibernateCore = hasProjectHibernateCore;
        }

        @Override
        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
            Xml.Tag visited = super.visitTag(tag, ctx);
            if (hasProjectHibernateCore &&
                UpgradeSelectedHibernateCoreDependency.isMavenPropertyDefinition(getCursor(), visited) &&
                JAVA_PROPERTIES.contains(visited.getName()) &&
                visited.getValue().filter(FindHibernate7BuildRisks::belowJava17).isPresent()) {
                return mark(visited, "Hibernate ORM 7.2 requires Java 17 or newer; raise this exact build baseline and recompile production, tests, processors, enhancement, generated sources, plugins, and deployment images on the chosen JDK");
            }
            if (UpgradeSelectedHibernateCoreDependency.isHibernateCoreCoordinate(visited) &&
                UpgradeSelectedHibernateCoreDependency.isProjectDependency(getCursor(), visited)) {
                if (!UpgradeSelectedHibernateCoreDependency.isSupportedHibernateCore(getCursor(), visited)) {
                    return mark(visited, "This classifier or non-jar Hibernate Core variant is outside deterministic runtime upgrade scope; migrate its exact artifact owner explicitly and verify publication, fixtures, enhancement, packaging, and linkage");
                }
                String group = visited.getChildValue("groupId").orElse("");
                String declared = visited.getChildValue("version").orElse("");
                if (declared.isEmpty()) {
                    return mark(visited, "Versionless Hibernate Core is owned by a BOM/parent; upgrade that central platform to a Hibernate 7.2/Jakarta Persistence 3.2 compatible generation rather than adding a leaf override, then inspect the resolved graph");
                }
                String resolved = resolve(declared);
                if (!UpgradeSelectedHibernateCoreDependency.TARGET_GROUP.equals(group) ||
                    !UpgradeSelectedHibernateCoreDependency.TARGET.equals(resolved)) {
                    return markChild(visited, "version", "Hibernate Core remains on an unselected, ranged, property-managed, patched, legacy-group, or non-target declaration; choose 7.2.12.Final explicitly or migrate its central owner and verify the resolved dependency graph");
                }
                return visited;
            }
            boolean dependency = isStandardProjectDependency(getCursor(), visited);
            boolean plugin = isProjectPlugin(getCursor(), visited);
            if (!hasProjectHibernateCore || (!dependency && !plugin)) return visited;
            String group = visited.getChildValue("groupId").orElse("");
            String artifact = visited.getChildValue("artifactId").orElse("");
            String version = resolve(visited.getChildValue("version").orElse(""));
            if (dependency && "javax.persistence".equals(group) && "javax.persistence-api".equals(artifact)) {
                return markChild(visited, "groupId", "Javax Persistence cannot coexist with Hibernate 7/Jakarta Persistence 3.2; migrate this exact dependency owner to jakarta.persistence-api 3.2 without overriding an external platform blindly");
            }
            if (("org.hibernate".equals(group) || "org.hibernate.orm".equals(group) ||
                 "org.hibernate.orm.tooling".equals(group)) &&
                HIBERNATE_COMPANIONS.contains(artifact) && !alignedCompanion(group, artifact, version)) {
                return markChild(visited, "version", "Align this exact Hibernate companion/plugin with the ORM 7.2.12.Final generation; verify artifact relocation, processor/enhancement execution, cache/dialect provider compatibility, and runtime linkage");
            }
            if (dependency && (("org.springframework".equals(group) && "spring-orm".equals(artifact)) ||
                ("io.quarkus".equals(group) && artifact.startsWith("quarkus-hibernate-orm")) ||
                ("io.hypersistence".equals(group) && artifact.startsWith("hypersistence-utils")))) {
                return markChild(visited, "version", "Framework/integration library detected at the Hibernate boundary; select a release explicitly compatible with Hibernate 7.2 and Jakarta Persistence 3.2, then verify bootstrap, transactions, proxies, native image, types, and dependency convergence");
            }
            return visited;
        }

        private String resolve(String version) {
            if (version.startsWith("${") && version.endsWith("}") && version.indexOf('}') == version.length() - 1) {
                return properties.getOrDefault(version.substring(2, version.length() - 1), version);
            }
            return version;
        }
    }

    private static final class GroovyRisks extends GroovyIsoVisitor<ExecutionContext> {
        private final boolean hasHibernateCore;

        private GroovyRisks(boolean hasHibernateCore) {
            this.hasHibernateCore = hasHibernateCore;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            boolean direct = UpgradeSelectedHibernateCoreDependency.isGradleDependencyInvocation(getCursor(), method);
            J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            if (!hasHibernateCore || !direct) return visited;
            if (hasVariant(visited)) {
                return declaresMapHibernateCore(visited) ? mark(visited,
                        "This classifier, ext, or type-qualified Hibernate Core map is outside deterministic runtime upgrade scope; migrate its exact artifact owner explicitly and verify publication, fixtures, enhancement, packaging, and linkage") : visited;
            }
            if (declaresDynamicHibernateCore(visited, getCursor())) {
                return mark(visited, "This interpolated Hibernate Core dependency has no provable workbook source version; migrate its version/catalog owner to 7.2.12.Final and verify the resolved graph");
            }
            String message = coordinateMessage(mapValue(visited, "group"), mapValue(visited, "name"),
                    mapValue(visited, "version"));
            if (message == null) {
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null && !hasVariant(map)) {
                    message = coordinateMessage(mapValue(map, "group"), mapValue(map, "name"), mapValue(map, "version"));
                }
            }
            return message == null ? visited : mark(visited, message);
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
            boolean direct = UpgradeSelectedHibernateCoreDependency.isDirectGradleDependencyLiteral(getCursor());
            J.Literal visited = super.visitLiteral(literal, ctx);
            return hasHibernateCore && direct ? markCoordinate(visited) : visited;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment visited = super.visitAssignment(assignment, ctx);
            return hasHibernateCore ? baselineAssignment(visited, getCursor()) : visited;
        }
    }

    private static final class KotlinRisks extends KotlinIsoVisitor<ExecutionContext> {
        private final boolean hasHibernateCore;

        private KotlinRisks(boolean hasHibernateCore) {
            this.hasHibernateCore = hasHibernateCore;
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
            boolean direct = UpgradeSelectedHibernateCoreDependency.isDirectGradleDependencyLiteral(getCursor());
            J.Literal visited = super.visitLiteral(literal, ctx);
            return hasHibernateCore && direct ? markCoordinate(visited) : visited;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            boolean direct = UpgradeSelectedHibernateCoreDependency.isGradleDependencyInvocation(getCursor(), method);
            J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            return hasHibernateCore && direct && declaresDynamicHibernateCore(visited, getCursor())
                    ? mark(visited, "This interpolated Hibernate Core dependency has no provable workbook source version; migrate its version/catalog owner to 7.2.12.Final and verify the resolved graph") : visited;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment visited = super.visitAssignment(assignment, ctx);
            return hasHibernateCore ? baselineAssignment(visited, getCursor()) : visited;
        }
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length > 3 && selectedHibernateCore(parts[0], parts[1])) {
            return mark(literal, "This classifier-qualified Hibernate Core coordinate is outside deterministic runtime upgrade scope; migrate its exact artifact owner explicitly and verify publication, fixtures, enhancement, packaging, and linkage");
        }
        if (parts.length != 3) return literal;
        String message = coordinateMessage(parts[0], parts[1], parts[2]);
        return message == null ? literal : mark(literal, message);
    }

    private static String coordinateMessage(String group, String artifact, String version) {
        if (group == null || artifact == null || version == null) return null;
        if ((UpgradeSelectedHibernateCoreDependency.LEGACY_GROUP.equals(group) ||
             UpgradeSelectedHibernateCoreDependency.TARGET_GROUP.equals(group)) &&
            UpgradeSelectedHibernateCoreDependency.ARTIFACT.equals(artifact) &&
            (!UpgradeSelectedHibernateCoreDependency.TARGET_GROUP.equals(group) ||
             !UpgradeSelectedHibernateCoreDependency.TARGET.equals(version))) {
            return "Hibernate Core remains on an unselected, dynamic, variable, ranged, legacy-group, or non-target declaration; choose 7.2.12.Final explicitly or migrate the catalog/platform owner and verify the resolved graph";
        }
        if ("javax.persistence".equals(group) && "javax.persistence-api".equals(artifact)) {
            return "Javax Persistence cannot coexist with Hibernate 7; migrate the explicit owner to Jakarta Persistence 3.2 and align the framework/platform rather than forcing a second API generation";
        }
        if (("org.hibernate".equals(group) || "org.hibernate.orm".equals(group) ||
             "org.hibernate.orm.tooling".equals(group)) &&
            HIBERNATE_COMPANIONS.contains(artifact) && !alignedCompanion(group, artifact, version)) {
            return "Align this Hibernate companion/plugin with ORM 7.2.12.Final and verify relocation, processor/enhancement execution, provider compatibility, and runtime linkage";
        }
        if (("org.springframework".equals(group) && "spring-orm".equals(artifact)) ||
            ("io.quarkus".equals(group) && artifact.startsWith("quarkus-hibernate-orm")) ||
            ("io.hypersistence".equals(group) && artifact.startsWith("hypersistence-utils"))) {
            return "Select an integration release explicitly compatible with Hibernate 7.2/Jakarta Persistence 3.2 and verify bootstrap, transactions, proxies, native image, types, and dependency convergence";
        }
        return null;
    }

    private static J.Assignment baselineAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!(variable.endsWith("sourceCompatibility") || variable.endsWith("targetCompatibility"))) return assignment;
        return belowJava17(assignment.getAssignment().printTrimmed(cursor))
                ? mark(assignment, "Hibernate ORM 7.2 requires Java 17 or newer; raise this exact Gradle baseline and align toolchains, test JVMs, processors, enhancement, generated sources, CI, and runtime images")
                : assignment;
    }

    private static Xml.Tag markChild(Xml.Tag owner, String childName, String message) {
        return owner.getChild(childName).map(child -> {
            Xml.Tag marked = mark(child, message);
            if (marked == child) return owner;
            return owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static boolean hasMavenHibernateCore(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedHibernateCoreDependency.isHibernateCoreCoordinate(tag) &&
                    UpgradeSelectedHibernateCoreDependency.isProjectDependency(getCursor(), tag)) {
                    found[0] = true;
                }
                return found[0] ? tag : super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static boolean hasGradleHibernateCore(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedHibernateCoreDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    methodDeclaresHibernateCore(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean hasGradleHibernateCore(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedHibernateCoreDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    methodDeclaresHibernateCore(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean methodDeclaresHibernateCore(J.MethodInvocation invocation, Cursor cursor) {
        if (declaresMapHibernateCore(invocation) || declaresDynamicHibernateCore(invocation, cursor)) return true;
        return invocation.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(FindHibernate7BuildRisks::isHibernateCoreCoordinate);
    }

    private static boolean declaresMapHibernateCore(J.MethodInvocation invocation) {
        if (selectedHibernateCore(mapValue(invocation, "group"), mapValue(invocation, "name"))) return true;
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .anyMatch(map -> selectedHibernateCore(mapValue(map, "group"), mapValue(map, "name")));
    }

    private static boolean declaresDynamicHibernateCore(J.MethodInvocation invocation, Cursor cursor) {
        return invocation.getArguments().stream().anyMatch(argument ->
                (argument instanceof G.GString || argument instanceof K.StringTemplate) &&
                isHibernateCoreCoordinate(argument.printTrimmed(cursor)));
    }

    private static boolean isHibernateCoreCoordinate(String value) {
        return value.contains(UpgradeSelectedHibernateCoreDependency.LEGACY_GROUP + ":" +
                              UpgradeSelectedHibernateCoreDependency.ARTIFACT) ||
               value.contains(UpgradeSelectedHibernateCoreDependency.TARGET_GROUP + ":" +
                              UpgradeSelectedHibernateCoreDependency.ARTIFACT);
    }

    private static boolean selectedHibernateCore(String group, String artifact) {
        return UpgradeSelectedHibernateCoreDependency.ARTIFACT.equals(artifact) &&
               (UpgradeSelectedHibernateCoreDependency.LEGACY_GROUP.equals(group) ||
                UpgradeSelectedHibernateCoreDependency.TARGET_GROUP.equals(group));
    }

    private static Map<String, String> localProperties(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> values = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedHibernateCoreDependency.isMavenPropertyDefinition(getCursor(), tag)) {
                    definitions.merge(tag.getName(), 1, Integer::sum);
                    tag.getValue().ifPresent(value -> values.put(tag.getName(), value.trim()));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visitNonNull(document, ctx);
        values.keySet().removeIf(name -> definitions.getOrDefault(name, 0) != 1);
        return values;
    }

    private static boolean isStandardProjectDependency(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedHibernateCoreDependency.isProjectDependency(cursor, tag) &&
               tag.getChild("classifier").isEmpty() &&
               "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    private static boolean isProjectPlugin(Cursor cursor, Xml.Tag tag) {
        if (!"plugin".equals(tag.getName())) return false;
        Cursor pluginsCursor = cursor.getParentTreeCursor();
        if (!(pluginsCursor.getValue() instanceof Xml.Tag plugins) || !"plugins".equals(plugins.getName())) return false;
        Cursor ownerCursor = pluginsCursor.getParentTreeCursor();
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner)) return false;
        if ("pluginManagement".equals(owner.getName())) {
            ownerCursor = ownerCursor.getParentTreeCursor();
            if (!(ownerCursor.getValue() instanceof Xml.Tag build) || !"build".equals(build.getName())) return false;
        } else if (!"build".equals(owner.getName())) {
            return false;
        }
        Cursor projectCursor = ownerCursor.getParentTreeCursor();
        return projectCursor != null &&
               (UpgradeSelectedHibernateCoreDependency.isProjectOwner(projectCursor) ||
                UpgradeSelectedHibernateCoreDependency.isProfileOwner(projectCursor));
    }

    private static boolean alignedCompanion(String group, String artifact, String version) {
        String targetGroup = "hibernate-enhance-maven-plugin".equals(artifact)
                ? "org.hibernate.orm.tooling" : UpgradeSelectedHibernateCoreDependency.TARGET_GROUP;
        return targetGroup.equals(group) && UpgradeSelectedHibernateCoreDependency.TARGET.equals(version);
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean belowJava17(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:1\\.)?(\\d+)").matcher(value);
        if (!matcher.find()) return false;
        try {
            return Integer.parseInt(matcher.group(1)) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static <T extends Tree> T mark(T tree, String message) {
        boolean present = tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()));
        return present ? tree : SearchResult.found(tree, message);
    }
}
