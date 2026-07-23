package com.huawei.clouds.openrewrite.log4j12api;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Locale;

/** Locate Log4j 1 configuration compatibility and conversion decisions. */
public final class FindLog4j12ApiConfigurationRisks extends Recipe {
    static final String COMPATIBILITY =
            "log4j1.compatibility controls classpath discovery of Log4j 1 configuration and, since 2.24.0, " +
            "PropertyConfigurator/DOMConfigurator. Decide explicitly whether to enable this temporary bridge mode, " +
            "protect configuration inputs and test startup/reload";
    static final String CONFIGURATION =
            "Log4j 1 configuration syntax is only partially converted by the bridge; convert it to an owned Log4j 2 " +
            "configuration or validate every appender, layout, filter, threshold, additivity and rollover option";
    static final String INTERPOLATION =
            "Log4j 1 ${name} interpolation means a system/config property, while Log4j 2 uses named lookups; convert " +
            "to an explicit ${sys:name} or another approved lookup and test missing/untrusted values";
    static final String CUSTOM_COMPONENT =
            "This Log4j 1 configuration names a custom or implementation-specific class; prove it is in the bridge " +
            "support list or port it to a Log4j 2 plugin and verify plugin metadata in the packaged artifact";
    static final String RELOAD =
            "Log4j 1 watch/reload behavior crosses file permissions, malformed updates and Log4j 2 reconfiguration; " +
            "replace polling with an owned mechanism or test change/delete/rollback and concurrent logging";
    static final String XML_SECURITY =
            "Log4j 1 XML/DTD processing and custom entity behavior must not introduce external entity or network " +
            "resolution; convert the file and validate parsing with network access disabled";

    @Override
    public String getDisplayName() {
        return "Find Log4j 1.2 API bridge configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark compatibility mode, Log4j 1 properties/XML conversion, interpolation, custom components, " +
               "reload and XML entity security decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLog4j12ApiDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                if (tree instanceof Properties.File properties) return properties(properties, file, ctx);
                if (tree instanceof Xml.Document xml && log4jXml(xml, file)) return xml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(
            Properties.File source, String file, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String key = visited.getKey().trim();
                String value = visited.getValue().getText().trim();
                if ("log4j1.compatibility".equals(key)) return mark(visited, COMPATIBILITY);
                if ("log4j.configuration".equals(key) ||
                    key.startsWith("log4j.root") || key.startsWith("log4j.logger.") ||
                    key.startsWith("log4j.category.") || key.startsWith("log4j.additivity.")) {
                    return mark(visited, CONFIGURATION);
                }
                if (key.startsWith("log4j.") && value.contains("${") &&
                    !value.contains("${sys:")) return mark(visited, INTERPOLATION);
                if (key.startsWith("log4j.appender.") &&
                    (key.endsWith(".layout") || !key.substring("log4j.appender.".length()).contains(".")) &&
                    value.contains(".")) return mark(visited, CUSTOM_COMPONENT);
                if (key.contains("configureAndWatch") || key.contains("monitorInterval") ||
                    file.contains("watch")) return mark(visited, RELOAD);
                if ((file.equals("log4j.properties") || file.equals("log4j-test.properties")) &&
                    key.startsWith("log4j.")) return mark(visited, CONFIGURATION);
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.DocTypeDecl visitDocTypeDecl(Xml.DocTypeDecl docTypeDecl, ExecutionContext ec) {
                return mark(super.visitDocTypeDecl(docTypeDecl, ec), XML_SECURITY);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (visited == source.getRoot() ||
                    visited.getName().toLowerCase(Locale.ROOT).endsWith("configuration")) {
                    return mark(visited, CONFIGURATION);
                }
                String className = visited.getAttributes().stream()
                        .filter(attribute -> "class".equals(attribute.getKeyAsString()))
                        .map(Xml.Attribute::getValueAsString).findFirst().orElse("");
                if (!className.isBlank() && !className.startsWith("org.apache.log4j.")) {
                    return mark(visited, CUSTOM_COMPONENT);
                }
                String value = visited.getValue().orElse("");
                if (value.contains("${") && !value.contains("${sys:")) return mark(visited, INTERPOLATION);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                String value = visited.getValueAsString();
                if (value.contains("${") && !value.contains("${sys:")) {
                    return mark(visited, INTERPOLATION);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    static boolean log4jXml(Xml.Document document, String file) {
        return file.equals("log4j.xml") || file.equals("log4j-test.xml") ||
               document.printAll().contains("log4j:configuration");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
