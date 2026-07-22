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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only Hibernate Core versions whose literal value is visible in the spreadsheet. */
public final class UpgradeSelectedHibernateCoreDependency extends Recipe {
    private static final String LEGACY_PREFIX = "org.hibernate:hibernate-core:";
    private static final String CURRENT_PREFIX = "org.hibernate.orm:hibernate-core:";
    private static final String TARGET = "7.2.12.Final";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> VERSIONS = Set.of(
            "5.4.15.Final", "5.4.24.Final", "5.4.25.Final", "5.4.28.Final", "5.5.6",
            "5.6.5.Final", "5.6.7.Final", "5.6.9.Final", "5.6.14.Final", "5.6.15.Final"
    );
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime",
            "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "testFixturesApi",
            "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected Hibernate Core declarations to 7.2.12.Final";
    }

    @Override
    public String getDescription() {
        return "Move selected direct org.hibernate or org.hibernate.orm Hibernate Core declarations to org.hibernate.orm:hibernate-core:7.2.12.Final without widening version ranges or overriding BOM-managed declarations.";
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
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream()
                        .filter(Xml.Tag.class::isInstance)
                        .map(Xml.Tag.class::cast)
                        .forEach(property -> property.getValue().ifPresent(value ->
                                propertyValues.put(property.getName(), value))));

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Matcher matcher = PROPERTY_REFERENCE.matcher(charData.getText());
                while (matcher.find()) {
                    allReferences.merge(matcher.group(1), 1, Integer::sum);
                }
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isHibernateCore(tag)) {
                    propertyName(tag).filter(name -> propertyValues.get(name) != null &&
                                                      VERSIONS.contains(propertyValues.get(name)))
                            .ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isPropertiesChild(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().filter(VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (!isHibernateCore(t)) {
                    return t;
                }
                String version = t.getChildValue("version").orElse(null);
                boolean eligibleLiteral = version != null && VERSIONS.contains(version);
                boolean eligibleProperty = propertyName(t).filter(safeProperties::contains).isPresent();
                if (!eligibleLiteral && !eligibleProperty) {
                    return t;
                }
                Xml.Tag migrated = t.withChildValue("groupId", "org.hibernate.orm");
                return eligibleLiteral ? migrated.withChildValue("version", TARGET) : migrated;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isHibernateCore(Xml.Tag tag) {
        String group = tag.getChildValue("groupId").orElse(null);
        return "dependency".equals(tag.getName()) &&
               ("org.hibernate".equals(group) || "org.hibernate.orm".equals(group)) &&
               "hibernate-core".equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static java.util.Optional<String> propertyName(Xml.Tag dependency) {
        String version = dependency.getChildValue("version").orElse("");
        Matcher matcher = PROPERTY_REFERENCE.matcher(version);
        return matcher.matches() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName());
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String prefix;
        if (value.startsWith(LEGACY_PREFIX)) {
            prefix = LEGACY_PREFIX;
        } else if (value.startsWith(CURRENT_PREFIX)) {
            prefix = CURRENT_PREFIX;
        } else {
            return literal;
        }
        if (!VERSIONS.contains(value.substring(prefix.length()))) {
            return literal;
        }
        String replacement = CURRENT_PREFIX + TARGET;
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }
}
