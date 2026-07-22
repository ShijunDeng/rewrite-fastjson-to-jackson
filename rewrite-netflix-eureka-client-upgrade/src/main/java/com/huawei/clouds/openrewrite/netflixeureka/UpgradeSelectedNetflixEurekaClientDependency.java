package com.huawei.clouds.openrewrite.netflixeureka;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Upgrades only the spreadsheet-selected Eureka client version visible in the current build file. */
public final class UpgradeSelectedNetflixEurekaClientDependency extends Recipe {
    private static final String GROUP = "com.netflix.eureka";
    private static final String ARTIFACT = "eureka-client";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade the spreadsheet-selected Netflix Eureka Client dependency";
    }

    @Override
    public String getDescription() {
        return "Upgrade only visible com.netflix.eureka:eureka-client 1.10.18 literals to 2.0.4 while preserving external management, dynamic versions, Gradle variables, and shared Maven properties.";
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
                            J.MethodInvocation m = super.visitMethodInvocation(method, p);
                            if (!EurekaVersions.GRADLE_CONFIGURATIONS.contains(m.getSimpleName())) {
                                return m;
                            }
                            if (GROUP.equals(mapValue(m, "group")) && ARTIFACT.equals(mapValue(m, "name")) &&
                                EurekaVersions.SOURCE.equals(mapValue(m, "version"))) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                            }
                            return m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Set<UUID> updateProperties = new HashSet<>();
        Set<UUID> inlineDependencies = new HashSet<>();
        String source = document.printAll();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (!isClient(t)) {
                    return t;
                }
                String version = t.getChildValue("version").orElse(null);
                String property = propertyName(version).orElse(null);
                if (property == null) {
                    return t;
                }
                Xml.Tag definition = resolveProperty(document, getCursor(), property);
                if (definition != null && EurekaVersions.SOURCE.equals(definition.getValue().orElse("").trim())) {
                    if (count(source, "${" + property + "}") == 1) {
                        updateProperties.add(definition.getId());
                    } else {
                        inlineDependencies.add(t.getId());
                    }
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag t = super.visitTag(tag, p);
                if (isClient(t)) {
                    String version = t.getChildValue("version").orElse(null);
                    if (EurekaVersions.SOURCE.equals(version == null ? null : version.trim()) ||
                        inlineDependencies.contains(t.getId())) {
                        return t.withChildValue("version", EurekaVersions.TARGET);
                    }
                }
                if (updateProperties.contains(t.getId()) &&
                    EurekaVersions.SOURCE.equals(t.getValue().orElse("").trim())) {
                    return t.withValue(EurekaVersions.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static Xml.Tag resolveProperty(Xml.Document document, Cursor cursor, String name) {
        Xml.Tag profile = cursor.getPathAsStream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .filter(tag -> "profile".equals(tag.getName())).findFirst().orElse(null);
        if (profile != null) {
            Xml.Tag local = profile.getChild("properties").flatMap(properties -> properties.getChild(name)).orElse(null);
            if (local != null) {
                return local;
            }
        }
        return document.getRoot().getChild("properties").flatMap(properties -> properties.getChild(name)).orElse(null);
    }

    private static boolean isClient(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
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

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        return cursor.getParentTreeCursor().getValue() instanceof J.MethodInvocation invocation &&
               EurekaVersions.GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.equals(PREFIX + EurekaVersions.SOURCE)) {
            return literal;
        }
        String replacement = PREFIX + EurekaVersions.TARGET;
        return literal.withValue(replacement).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(value, replacement));
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        if (!EurekaVersions.SOURCE.equals(literal.getValue())) {
            return literal;
        }
        return literal.withValue(EurekaVersions.TARGET).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(EurekaVersions.SOURCE, EurekaVersions.TARGET));
    }
}
