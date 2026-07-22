package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds target-version Flyway database companions when a Maven driver makes the choice deterministic. */
public final class AddFlywayDatabaseModules extends Recipe {
    private static final Map<String, String> DRIVERS = new LinkedHashMap<>();

    static {
        DRIVERS.put("org.postgresql:postgresql", "flyway-database-postgresql");
        DRIVERS.put("com.mysql:mysql-connector-j", "flyway-mysql");
        DRIVERS.put("mysql:mysql-connector-java", "flyway-mysql");
        DRIVERS.put("org.mariadb.jdbc:mariadb-java-client", "flyway-mysql");
        DRIVERS.put("com.microsoft.sqlserver:mssql-jdbc", "flyway-sqlserver");
        DRIVERS.put("com.oracle.database.jdbc:ojdbc8", "flyway-database-oracle");
        DRIVERS.put("com.oracle.database.jdbc:ojdbc11", "flyway-database-oracle");
        DRIVERS.put("com.oracle:ojdbc8", "flyway-database-oracle");
        DRIVERS.put("com.ibm.db2:jcc", "flyway-database-db2");
    }

    @Override
    public String getDisplayName() {
        return "Add deterministic Flyway database modules to Maven builds";
    }

    @Override
    public String getDescription() {
        return "Add the Flyway 11 PostgreSQL, MySQL/MariaDB, SQL Server, Oracle, or DB2 module when a direct Maven Flyway Core dependency and matching JDBC driver make the choice unambiguous.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"dependencies".equals(t.getName()) ||
                    !(getCursor().getParentTreeCursor().getValue() instanceof Xml.Tag parent) ||
                    !"project".equals(parent.getName())) {
                    return t;
                }
                List<Xml.Tag> dependencies = t.getChildren().stream()
                        .filter(candidate -> "dependency".equals(candidate.getName())).toList();
                Xml.Tag core = dependencies.stream().filter(AddFlywayDatabaseModules::isCore)
                        .filter(this::hasTargetVersion).findFirst().orElse(null);
                if (core == null) {
                    return t;
                }
                Set<String> presentModules = dependencies.stream()
                        .filter(candidate -> "org.flywaydb".equals(candidate.getChildValue("groupId").orElse(null)))
                        .map(candidate -> candidate.getChildValue("artifactId").orElse(""))
                        .collect(java.util.stream.Collectors.toSet());
                List<String> needed = dependencies.stream().map(AddFlywayDatabaseModules::coordinate)
                        .map(DRIVERS::get).filter(java.util.Objects::nonNull).distinct()
                        .filter(module -> !presentModules.contains(module)).toList();
                if (needed.isEmpty()) {
                    return t;
                }
                String version = core.getChildValue("version").orElse(FlywayVersions.TARGET);
                String scope = core.getChildValue("scope").orElse(null);
                List<Content> content = new ArrayList<>(t.getContent().size() + needed.size());
                for (Content child : t.getContent()) {
                    content.add(child);
                    if (child == core) {
                        needed.forEach(module -> content.add(autoFormat(
                                moduleTag(module, version, scope).withPrefix(core.getPrefix()), ctx, getCursor())));
                    }
                }
                return t.withContent(content);
            }

            private boolean hasTargetVersion(Xml.Tag dependency) {
                String version = dependency.getChildValue("version").orElse(null);
                if (FlywayVersions.TARGET.equals(version)) {
                    return true;
                }
                if (version != null && version.startsWith("${") && version.endsWith("}")) {
                    String property = version.substring(2, version.length() - 1);
                    Xml.Document document = getCursor().firstEnclosing(Xml.Document.class);
                    return document != null && FlywayVersions.TARGET.equals(document.getRoot().getChild("properties")
                            .flatMap(properties -> properties.getChildValue(property)).orElse(null));
                }
                return false;
            }
        };
    }

    private static boolean isCore(Xml.Tag dependency) {
        return "org.flywaydb".equals(dependency.getChildValue("groupId").orElse(null)) &&
               "flyway-core".equals(dependency.getChildValue("artifactId").orElse(null));
    }

    private static String coordinate(Xml.Tag dependency) {
        return dependency.getChildValue("groupId").orElse("") + ":" +
               dependency.getChildValue("artifactId").orElse("");
    }

    private static Xml.Tag moduleTag(String module, String version, String scope) {
        String scopeXml = scope == null ? "" : "\n  <scope>" + scope + "</scope>";
        return Xml.Tag.build("""
                <dependency>
                  <groupId>org.flywaydb</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>%s
                </dependency>
                """.formatted(module, version, scopeXml));
    }
}
