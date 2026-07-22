package com.huawei.clouds.openrewrite.jaxen;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrades only the two source versions selected by the workbook. */
public final class UpgradeSelectedJaxenDependency extends Recipe {
    private static final String PREFIX = JaxenSupport.GROUP + ":" + JaxenSupport.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern ROOT_GRADLE_DEFINITION = Pattern.compile(
            "(?m)^\\s*(?:(?:def|val|var)\\s+)?(?:(?:rootProject\\.)?ext\\.)?" +
            "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(['\"])(1[.]2[.]0|2[.]0[.]0)\\2\\s*$");
    private static final Pattern GRADLE_REFERENCE = Pattern.compile(
            "jaxen:jaxen:\\$(?:\\{(?:rootProject[.]ext[.])?)?([A-Za-z_$][A-Za-z0-9_$]*)}?");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Jaxen dependencies to 2.0.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact jaxen:jaxen versions 1.2.0 and 2.0.0 in owned Maven/Gradle declarations, " +
               "including uniquely owned Maven profile properties and root Gradle DSL version properties.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxenSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    Set<String> safeProperties = JaxenSupport.rootGradle(source.getSourcePath())
                            ? safeGradleProperties(groovy, ctx) : Set.of();
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                            J.Assignment a = super.visitAssignment(assignment, executionContext);
                            String name = propertyName(a.getVariable().printTrimmed(getCursor()));
                            if (!safeProperties.contains(name) || !(a.getAssignment() instanceof J.Literal literal) ||
                                !JaxenSupport.SOURCES.contains(literal.getValue())) return a;
                            return a.withAssignment(JaxenSupport.replaceLiteral(literal, JaxenSupport.TARGET));
                        }

                        @Override
                        public J.VariableDeclarations.NamedVariable visitVariable(
                                J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
                            J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, executionContext);
                            Expression initializer = v.getInitializer();
                            if (!safeProperties.contains(v.getSimpleName()) || !(initializer instanceof J.Literal literal) ||
                                !JaxenSupport.SOURCES.contains(literal.getValue())) return v;
                            return v.withInitializer(JaxenSupport.replaceLiteral(literal, JaxenSupport.TARGET));
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!dependency) return m;
                            if (coordinate(JaxenSupport.mapValue(m, "group"), JaxenSupport.mapValue(m, "name")) &&
                                JaxenSupport.SOURCES.contains(JaxenSupport.mapValue(m, "version")) &&
                                !JaxenSupport.hasVariant(m)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(JaxenSupport.mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(JaxenSupport.replaceLiteral(literal, JaxenSupport.TARGET))
                                                : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            if (dependency) return upgradeCoordinate(l);
                            return propertyDefinitionLiteral(getCursor(), safeProperties) &&
                                   JaxenSupport.SOURCES.contains(l.getValue())
                                    ? JaxenSupport.replaceLiteral(l, JaxenSupport.TARGET) : l;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    Set<String> safeProperties = JaxenSupport.rootGradle(source.getSourcePath())
                            ? safeGradleProperties(kotlin, ctx) : Set.of();
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.VariableDeclarations.NamedVariable visitVariable(
                                J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
                            J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, executionContext);
                            Expression initializer = v.getInitializer();
                            if (!safeProperties.contains(v.getSimpleName()) || !(initializer instanceof J.Literal literal) ||
                                !JaxenSupport.SOURCES.contains(literal.getValue())) return v;
                            return v.withInitializer(JaxenSupport.replaceLiteral(literal, JaxenSupport.TARGET));
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = JaxenSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            if (dependency) return upgradeCoordinate(l);
                            return propertyDefinitionLiteral(getCursor(), safeProperties) &&
                                   JaxenSupport.SOURCES.contains(l.getValue())
                                    ? JaxenSupport.replaceLiteral(l, JaxenSupport.TARGET) : l;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, List<Definition>> definitions = new HashMap<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, List<String>> targetReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                UpgradeSelectedJaxenDependency.collect(charData.getText(), allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                UpgradeSelectedJaxenDependency.collect(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (JaxenSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    definitions.computeIfAbsent(t.getName(), ignored -> new ArrayList<>()).add(new Definition(
                            JaxenSupport.mavenScope(getCursor()), t.getValue().map(String::trim).orElse("")));
                }
                if (JaxenSupport.isJaxenDependency(getCursor(), t) && JaxenSupport.standardJar(t)) {
                    propertyName(t).ifPresent(name -> targetReferences.computeIfAbsent(name, ignored -> new ArrayList<>())
                            .add(JaxenSupport.mavenScope(getCursor())));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        definitions.forEach((name, candidates) -> {
            if (candidates.size() != 1) return;
            Definition definition = candidates.get(0);
            List<String> references = targetReferences.getOrDefault(name, List.of());
            if (!JaxenSupport.SOURCES.contains(definition.value()) || references.isEmpty() ||
                allReferences.getOrDefault(name, 0) != references.size()) return;
            if (!"project".equals(definition.scope()) && references.stream().anyMatch(scope -> !scope.equals(definition.scope()))) {
                return;
            }
            safeProperties.add(name);
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (JaxenSupport.isMavenPropertyDefinition(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(JaxenSupport.SOURCES::contains).isPresent()) {
                    return t.withValue(JaxenSupport.TARGET);
                }
                if (JaxenSupport.isJaxenDependency(getCursor(), t) && JaxenSupport.standardJar(t) &&
                    t.getChildValue("version").map(String::trim).filter(JaxenSupport.SOURCES::contains).isPresent()) {
                    return t.withChildValue("version", JaxenSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static Set<String> safeGradleProperties(SourceFile unit, ExecutionContext ctx) {
        String source = unit.printAll();
        Map<String, Integer> definitions = new HashMap<>();
        Matcher definitionsMatcher = ROOT_GRADLE_DEFINITION.matcher(source);
        while (definitionsMatcher.find()) definitions.merge(definitionsMatcher.group(1), 1, Integer::sum);
        Map<String, Integer> targetReferences = new HashMap<>();
        if (unit instanceof G.CompilationUnit groovy) {
            new GroovyIsoVisitor<ExecutionContext>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                  ExecutionContext executionContext) {
                    boolean dependency = JaxenSupport.isGradleDependencyInvocation(getCursor(), method);
                    J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                    if (dependency) collectGradleReferences(m.printTrimmed(getCursor()), targetReferences);
                    return m;
                }
            }.visitNonNull(groovy, ctx);
        } else if (unit instanceof K.CompilationUnit kotlin) {
            new KotlinIsoVisitor<ExecutionContext>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                  ExecutionContext executionContext) {
                    boolean dependency = JaxenSupport.isGradleDependencyInvocation(getCursor(), method);
                    J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                    if (dependency) collectGradleReferences(m.printTrimmed(getCursor()), targetReferences);
                    return m;
                }
            }.visitNonNull(kotlin, ctx);
        }
        Set<String> safe = new HashSet<>();
        definitions.forEach((name, count) -> {
            int refs = targetReferences.getOrDefault(name, 0);
            if (count == 1 && refs > 0 && wordCount(source, name) == count + refs) safe.add(name);
        });
        return safe;
    }

    private static void collectGradleReferences(String source, Map<String, Integer> references) {
        Matcher matcher = GRADLE_REFERENCE.matcher(source);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    private static boolean propertyDefinitionLiteral(Cursor cursor, Set<String> safeProperties) {
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            Object value = current.getValue();
            if (value instanceof J.Assignment assignment) {
                return safeProperties.contains(propertyName(assignment.getVariable().printTrimmed(current)));
            }
            if (value instanceof J.VariableDeclarations.NamedVariable variable) {
                return safeProperties.contains(variable.getSimpleName());
            }
            if (value instanceof J.MethodInvocation) return false;
        }
        return false;
    }

    private static int wordCount(String source, String name) {
        Matcher matcher = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])")
                .matcher(source);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String propertyName(String expression) {
        Matcher matcher = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)$").matcher(expression);
        return matcher.find() ? matcher.group(1) : expression;
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collect(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    private static boolean coordinate(String group, String artifact) {
        return JaxenSupport.GROUP.equals(group) && JaxenSupport.ARTIFACT.equals(artifact);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!coordinate(JaxenSupport.mapValue(map, "group"), JaxenSupport.mapValue(map, "name")) ||
            !JaxenSupport.SOURCES.contains(JaxenSupport.mapValue(map, "version")) || JaxenSupport.hasVariant(map)) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(JaxenSupport.mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(JaxenSupport.replaceLiteral(literal, JaxenSupport.TARGET)) : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !JaxenSupport.SOURCES.contains(value.substring(PREFIX.length()))) return literal;
        return JaxenSupport.replaceLiteral(literal, PREFIX + JaxenSupport.TARGET);
    }

    private record Definition(String scope, String value) {
    }
}
