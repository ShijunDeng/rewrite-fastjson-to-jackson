package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Marks JDBC drivers and URLs whose Flyway 11 database companion is absent. */
public final class FindFlywayDatabaseModuleRisks extends ScanningRecipe<Set<String>> {
    private static final Map<String, String> DRIVERS = new LinkedHashMap<>();
    private static final Map<String, String> URLS = new LinkedHashMap<>();

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
        URLS.put("jdbc:postgresql:", "flyway-database-postgresql");
        URLS.put("jdbc:mysql:", "flyway-mysql");
        URLS.put("jdbc:mariadb:", "flyway-mysql");
        URLS.put("jdbc:sqlserver:", "flyway-sqlserver");
        URLS.put("jdbc:oracle:", "flyway-database-oracle");
        URLS.put("jdbc:db2:", "flyway-database-db2");
    }

    @Override
    public String getDisplayName() {
        return "Find missing Flyway database modules";
    }

    @Override
    public String getDescription() {
        return "Mark JDBC driver declarations and Flyway URLs when the Flyway 11 companion module is absent from the source set.";
    }

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new java.util.HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source) {
                    String text = source.printAll();
                    for (String module : Set.copyOf(URLS.values())) {
                        if (text.contains("org.flywaydb:" + module) || text.contains("<artifactId>" + module + "</artifactId>")) {
                            acc.add(module);
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    Set<String> available = new java.util.HashSet<>(acc);
                    String currentPom = document.printAll();
                    for (String module : Set.copyOf(URLS.values())) {
                        if (currentPom.contains("<artifactId>" + module + "</artifactId>")) {
                            available.add(module);
                        }
                    }
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                            Xml.Tag t = super.visitTag(tag, p);
                            if (!"dependency".equals(t.getName())) {
                                return t;
                            }
                            String module = DRIVERS.get(t.getChildValue("groupId").orElse("") + ":" +
                                                        t.getChildValue("artifactId").orElse(""));
                            return module != null && !available.contains(module) ? SearchResult.found(t, message(module)) : t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return markDriver(super.visitLiteral(literal, p), acc);
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            return markDriver(super.visitLiteral(literal, p), acc);
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof Properties.File file) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                            Properties.Entry e = super.visitEntry(entry, p);
                            if (!("flyway.url".equals(e.getKey()) || "spring.flyway.url".equals(e.getKey()))) {
                                return e;
                            }
                            String value = e.getValue().getText().trim();
                            String module = URLS.entrySet().stream().filter(candidate -> value.startsWith(candidate.getKey()))
                                    .map(Map.Entry::getValue).findFirst().orElse(null);
                            return module != null && !acc.contains(module) ? SearchResult.found(e, message(module)) : e;
                        }
                    }.visitNonNull(file, ctx);
                }
                return tree;
            }
        };
    }

    private static J.Literal markDriver(J.Literal literal, Set<String> modules) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String module = DRIVERS.entrySet().stream().filter(entry -> value.startsWith(entry.getKey() + ":"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        return module != null && !modules.contains(module) ? SearchResult.found(literal, message(module)) : literal;
    }

    private static String message(String module) {
        return "Flyway 11 requires org.flywaydb:" + module + " on the relevant application/plugin runtime classpath";
    }
}
