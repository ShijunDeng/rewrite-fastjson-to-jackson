package com.huawei.clouds.openrewrite.jaxen;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.Set;

/** Marks JPMS, reflection, native-image, OSGi and parser/document factory configuration decisions. */
public final class FindJaxenConfigurationRisks extends Recipe {
    private static final Set<String> REMOVED_OR_HIDDEN = Set.of(
            "org.jaxen.util.LinkedIterator", "org.jaxen.util.StackedIterator",
            "org.jaxen.pattern.AnyChildNodeTest", "org.jaxen.pattern.NoNodeTest",
            "org.jaxen.expr.DefaultExpr", "org.jaxen.expr.DefaultXPathExpr",
            "org.jaxen.expr.DefaultAbsoluteLocationPath", "org.jaxen.expr.DefaultAllNodeStep",
            "org.jaxen.expr.DefaultCommentNodeStep", "org.jaxen.expr.DefaultFilterExpr",
            "org.jaxen.expr.DefaultFunctionCallExpr", "org.jaxen.expr.DefaultNameStep",
            "org.jaxen.expr.DefaultProcessingInstructionNodeStep", "org.jaxen.expr.DefaultRelativeLocationPath",
            "org.jaxen.expr.DefaultStep", "org.jaxen.expr.DefaultTextNodeStep",
            "org.jaxen.expr.DefaultUnionExpr");
    private static final Set<String> ENGINES = Set.of(
            "org.jaxen.dom.DOMXPath", "org.jaxen.dom4j.Dom4jXPath", "org.jaxen.jdom.JDOMXPath",
            "org.jaxen.xom.XOMXPath", "org.jaxen.javabean.JavaBeanXPath");
    private static final String MODULE =
            "Jaxen 2 publishes Automatic-Module-Name org.jaxen; change legacy 'requires jaxen' module descriptors and verify module-path/OSGi split-package and reflective access";
    private static final String REMOVED =
            "Configuration references a Jaxen type removed or hidden in 2.0; replace reflection/native-image/OSGi metadata with a public API or application-owned implementation";
    private static final String ENGINE =
            "Configuration selects a Jaxen object-model engine; JDOM/dom4j/XOM dependencies are optional in 2.x, so make the model library explicit and verify reflection/classloader and XPath behavior";
    private static final String DOCUMENT =
            "Jaxen document loading/parser factory configuration detected; verify URI/base resolution, parser implementation, XXE/DTD/entity policy, encoding, caching and error causes";

    @Override
    public String getDisplayName() {
        return "Find Jaxen 2.0.1 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark legacy JPMS module names and exact removed/hidden, engine or document-factory references in " +
               "parsed resources, manifests, OSGi and native-image metadata.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxenSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                String lower = source.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if ("module-info.java".equals(fileName) && source.printAll().matches("(?s).*\\brequires\\s+jaxen\\s*;.*")) {
                    return JaxenSupport.mark(source, MODULE);
                }
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                            Properties.Entry e = super.visitEntry(entry, executionContext);
                            String message = risk(e.getValue().getText());
                            return message == null ? e : JaxenSupport.mark(e, message);
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                            Yaml.Scalar s = super.visitScalar(scalar, executionContext);
                            String message = risk(s.getValue());
                            return message == null ? s : JaxenSupport.mark(s, message);
                        }
                    }.visitNonNull(yaml, ctx);
                }
                if (tree instanceof Xml.Document xml && !"pom.xml".equalsIgnoreCase(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                            Xml.Attribute a = super.visitAttribute(attribute, executionContext);
                            String message = risk(a.getValueAsString());
                            return message == null ? a : JaxenSupport.mark(a, message);
                        }

                        @Override
                        public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                            Xml.CharData c = super.visitCharData(charData, executionContext);
                            String message = risk(c.getText());
                            return message == null ? c : JaxenSupport.mark(c, message);
                        }
                    }.visitNonNull(xml, ctx);
                }
                if (tree instanceof PlainText text && (lower.endsWith("manifest.mf") || lower.endsWith("bnd.bnd") ||
                                                       lower.endsWith(".bnd") || lower.endsWith(".json") ||
                                                       lower.contains("native-image") || lower.endsWith(".conf") ||
                                                       lower.endsWith(".cfg"))) {
                    return new PlainTextVisitor<ExecutionContext>() {
                        @Override
                        public PlainText visitText(PlainText plainText, ExecutionContext executionContext) {
                            PlainText t = (PlainText) super.visitText(plainText, executionContext);
                            String message = risk(t.getText());
                            return message == null ? t : JaxenSupport.mark(t, message);
                        }
                    }.visitNonNull(text, ctx);
                }
                return tree;
            }
        };
    }

    private static String risk(String value) {
        if (REMOVED_OR_HIDDEN.stream().anyMatch(value::contains)) return REMOVED;
        if (ENGINES.stream().anyMatch(value::contains)) return ENGINE;
        if (value.matches("(?s).*\\brequires\\s+jaxen\\s*;.*")) return MODULE;
        return value.contains("org.jaxen") &&
               (value.contains("DocumentBuilderFactory") || value.contains("SAXParserFactory") ||
                value.contains("DocumentNavigator")) ? DOCUMENT : null;
    }
}
