package com.huawei.clouds.openrewrite.jettyproxy;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.J;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Apply only package moves where the Jetty 12 target retains the same public type role and name. */
public final class MigrateJetty12DeterministicTypes extends Recipe {
    private static final Map<String, String> TYPE_MOVES = typeMoves();
    private static final List<Recipe> JAVA_MIGRATIONS = TYPE_MOVES.entrySet().stream()
            .map(move -> (Recipe) new ChangeType(move.getKey(), move.getValue(), null)).toList();

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Jetty 12 type moves";
    }

    @Override
    public String getDescription() {
        return "Move ConnectHandler and unchanged-name client API/helper types to their documented Jetty 12 packages " +
               "in Java and structured configuration without selecting a Servlet/Jakarta EE environment.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !UpgradeSelectedJettyProxyDependency
                        .isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) {
                    Tree migrated = java;
                    for (Recipe recipe : JAVA_MIGRATIONS) migrated = recipe.getVisitor().visitNonNull(migrated, ctx);
                    return migrated;
                }
                if (tree instanceof Properties.File properties) return migrateProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return migrateYaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && source.getSourcePath().getFileName() != null &&
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString())) return migrateXml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File migrateProperties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry visited = super.visitEntry(entry, p);
                String value = migrateText(visited.getValue().getText());
                return value.equals(visited.getValue().getText()) ? visited : visited.withValue(visited.getValue().withText(value));
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents migrateYaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext p) {
                Yaml.Scalar visited = super.visitScalar(scalar, p);
                String value = migrateText(visited.getValue());
                return value.equals(visited.getValue()) ? visited : visited.withValue(value);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migrateXml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                Xml.CharData visited = super.visitCharData(charData, p);
                String value = migrateText(visited.getText());
                return value.equals(visited.getText()) ? visited : visited.withText(value);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                Xml.Attribute visited = super.visitAttribute(attribute, p);
                String value = migrateText(visited.getValueAsString());
                return value.equals(visited.getValueAsString()) ? visited :
                        visited.withValue(visited.getValue().withValue(value));
            }
        }.visitNonNull(source, ctx);
    }

    static String migrateText(String value) {
        String migrated = value;
        for (Map.Entry<String, String> move : TYPE_MOVES.entrySet()) {
            migrated = migrated.replaceAll("(?<![\\w$.])" + Pattern.quote(move.getKey()) + "(?![\\w$])",
                    Matcher.quoteReplacement(move.getValue()));
        }
        return migrated;
    }

    private static Map<String, String> typeMoves() {
        Map<String, String> moves = new LinkedHashMap<>();
        moves.put("org.eclipse.jetty.proxy.ConnectHandler", "org.eclipse.jetty.server.handler.ConnectHandler");
        for (String type : List.of("Authentication", "AuthenticationStore", "Connection", "ContentResponse",
                "Destination", "Request", "Response", "Result")) {
            moves.put("org.eclipse.jetty.client.api." + type, "org.eclipse.jetty.client." + type);
        }
        for (String type : List.of("AbstractAuthentication", "BasicAuthentication", "BufferingResponseListener",
                "DigestAuthentication", "InputStreamResponseListener", "SPNEGOAuthentication")) {
            moves.put("org.eclipse.jetty.client.util." + type, "org.eclipse.jetty.client." + type);
        }
        moves.put("org.eclipse.jetty.client.http.HttpClientTransportOverHTTP",
                "org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP");
        moves.put("org.eclipse.jetty.client.http.HttpClientConnectionFactory",
                "org.eclipse.jetty.client.transport.HttpClientConnectionFactory");
        return moves;
    }
}
