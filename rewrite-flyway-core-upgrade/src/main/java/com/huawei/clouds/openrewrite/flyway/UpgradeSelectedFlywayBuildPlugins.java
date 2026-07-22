package com.huawei.clouds.openrewrite.flyway;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Upgrades only explicitly spreadsheet-selected OSS Flyway build plugins. */
public final class UpgradeSelectedFlywayBuildPlugins extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Flyway build plugins";
    }

    @Override
    public String getDescription() {
        return "Upgrade explicitly versioned org.flywaydb Flyway Maven and Gradle plugins only from spreadsheet-selected versions.";
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
                    return upgradePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            return upgradePluginInvocation(super.visitMethodInvocation(method, p));
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            return upgradePluginInvocation(super.visitMethodInvocation(method, p));
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        Set<String> eligible = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (isPlugin(t)) {
                    propertyName(t.getChildValue("version").orElse(null))
                            .filter(name -> FlywayVersions.isSource(properties.get(name))).ifPresent(eligible::add);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        String source = document.printAll();
        Map<String, Boolean> exclusive = new HashMap<>();
        eligible.forEach(name -> exclusive.put(name, count(source, "${" + name + "}") == 1));

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (isPlugin(t)) {
                    String version = t.getChildValue("version").orElse(null);
                    if (FlywayVersions.isSource(version)) {
                        return t.withChildValue("version", FlywayVersions.TARGET);
                    }
                    String property = propertyName(version).orElse(null);
                    if (property != null && eligible.contains(property) && !exclusive.getOrDefault(property, false)) {
                        return t.withChildValue("version", FlywayVersions.TARGET);
                    }
                }
                if (eligible.contains(t.getName()) && exclusive.getOrDefault(t.getName(), false) &&
                    FlywayVersions.isSource(t.getValue().orElse(null))) {
                    return t.withValue(FlywayVersions.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isPlugin(Xml.Tag tag) {
        return "plugin".equals(tag.getName()) &&
               "org.flywaydb".equals(tag.getChildValue("groupId").orElse(null)) &&
               "flyway-maven-plugin".equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static J.MethodInvocation upgradePluginInvocation(J.MethodInvocation invocation) {
        if (!"version".equals(invocation.getSimpleName()) || invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) || !FlywayVersions.isSource(version) ||
            !invocation.printTrimmed().contains("org.flywaydb.flyway")) {
            return invocation;
        }
        J.Literal replacement = literal.withValue(FlywayVersions.TARGET).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(version, FlywayVersions.TARGET));
        return invocation.withArguments(java.util.List.of(replacement));
    }

    private static Optional<String> propertyName(String version) {
        return version != null && version.startsWith("${") && version.endsWith("}")
                ? Optional.of(version.substring(2, version.length() - 1)) : Optional.empty();
    }

    private static int count(String source, String token) {
        int result = 0;
        for (int offset = source.indexOf(token); offset >= 0; offset = source.indexOf(token, offset + token.length())) {
            result++;
        }
        return result;
    }
}
