package com.huawei.clouds.openrewrite.jakartaservlet;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Locale;

/** Deployment descriptor, service loader and text-configuration risks for Servlet 6.1. */
public final class FindJakartaServlet61ResourceRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Jakarta Servlet 6.1 descriptor and configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark deployment descriptors that still require schema/container decisions, error-dispatch rules, " +
               "ServletContainerInitializer service files, string-based javax references, and SecurityManager settings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                if (tree instanceof Xml.Document document && descriptor(source)) {
                    return descriptorRisks(document, ctx);
                }
                if (tree instanceof PlainText text) {
                    return plainTextRisks(text);
                }
                return tree;
            }
        };
    }

    private static Xml.Document descriptorRisks(Xml.Document document, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if ("web-app".equals(t.getName()) || "web-fragment".equals(t.getName())) {
                    String version = attributeValue(t, "version");
                    String printed = t.printTrimmed(getCursor());
                    if (!"6.1".equals(version) || !printed.contains(
                            "https://jakarta.ee/xml/ns/jakartaee/web-" +
                            ("web-app".equals(t.getName()) ? "app" : "fragment") + "_6_1.xsd")) {
                        return SearchResult.found(t,
                                "Select the Servlet 6.1 Jakarta descriptor schema with the target container; do not change only version/schemaLocation while removed elements or old ecosystem classes remain");
                    }
                    if ("true".equalsIgnoreCase(attributeValue(t, "metadata-complete"))) {
                        return SearchResult.found(t,
                                "metadata-complete disables annotation discovery; verify @WebServlet/@WebFilter/@WebListener and initializer registration after the Jakarta migration");
                    }
                }
                if ("error-page".equals(t.getName())) {
                    return SearchResult.found(t,
                            "Servlet 6.1 error dispatches execute as GET; read original method/query from RequestDispatcher.ERROR_METHOD and ERROR_QUERY_STRING and retest auth, CSRF and caching");
                }
                if ("dispatcher".equals(t.getName()) && "ERROR".equals(t.getValue().map(String::trim).orElse(""))) {
                    return SearchResult.found(t,
                            "ERROR filter dispatch now observes an HTTP GET in Servlet 6.1; audit method-sensitive filter behavior");
                }
                return t;
            }

        }.visitNonNull(document, ctx);
    }

    private static PlainText plainTextRisks(PlainText text) {
        String path = text.getSourcePath().toString().replace('\\', '/');
        String lowerPath = path.toLowerCase(Locale.ROOT);
        String value = text.getText();
        if (path.endsWith("META-INF/services/javax.servlet.ServletContainerInitializer")) {
            return SearchResult.found(text,
                    "Rename this service file to META-INF/services/jakarta.servlet.ServletContainerInitializer, preserve provider ordering and verify duplicate target resources before merging");
        }
        if (lowerPath.contains("meta-inf/services") && value.contains("javax.servlet")) {
            return SearchResult.found(text,
                    "Service-loader metadata contains a javax Servlet type; migrate the service contract and every provider together");
        }
        if ((lowerPath.endsWith("manifest.mf") || lowerPath.endsWith("bnd.bnd") ||
             lowerPath.endsWith(".properties") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml") ||
             lowerPath.endsWith(".json") || lowerPath.endsWith(".conf")) && value.contains("javax.servlet")) {
            return SearchResult.found(text,
                    "String/configuration metadata still names javax.servlet; identify its reflection, OSGi, scanner or framework owner before changing it");
        }
        if (value.contains("java.security.manager") || value.contains("-Djava.security.policy") ||
            value.contains("-Djava.security.manager")) {
            return SearchResult.found(text,
                    "Servlet 6.1 removed SecurityManager requirements; replace policy/startup flags with process, container and platform isolation controls");
        }
        return text;
    }

    private static boolean descriptor(SourceFile source) {
        String path = source.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.endsWith("web.xml") || path.endsWith("web-fragment.xml") || path.endsWith(".tld");
    }

    private static String attributeValue(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse("");
    }
}
