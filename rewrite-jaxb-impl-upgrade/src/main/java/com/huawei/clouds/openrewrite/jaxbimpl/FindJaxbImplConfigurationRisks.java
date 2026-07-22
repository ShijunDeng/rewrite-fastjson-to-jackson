package com.huawei.clouds.openrewrite.jaxbimpl;

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

import java.util.List;
import java.util.Locale;

/** Marks provider, OSGi, native-image and configuration resources that need a JAXB 4 decision. */
public final class FindJaxbImplConfigurationRisks extends Recipe {
    private static final String DISCOVERY =
            "JAXB 4 removed legacy jaxb.properties/service discovery; register jakarta.xml.bind.JAXBContextFactory with ServiceLoader or module provides and verify the runtime classloader";
    private static final String LEGACY =
            "Configuration contains a legacy Javax or JAXB RI name; distinguish provider/reflection metadata from business text, migrate the exact owner, and verify OSGi/native-image/shaded discovery";
    private static final String BINDING =
            "Standard JAXB binding customization remains in a non-auto-migrated XML/WSDL file; move it to the Jakarta namespace/version 3.0 while preserving the /xjc vendor URI";

    @Override
    public String getDisplayName() {
        return "Find JAXB implementation 4.0.6 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed provider discovery, exact legacy provider/RI values in parsed resources, OSGi/native " +
               "metadata, and binding customizations outside xjb/jxb/xsd.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxbImplSupport.generated(source.getSourcePath())) return tree;
                String path = source.getSourcePath().toString().replace('\\', '/');
                String lower = path.toLowerCase(Locale.ROOT);
                String fileName = source.getSourcePath().getFileName().toString();
                if (lower.endsWith("jaxb.properties")) return JaxbImplSupport.mark(source, DISCOVERY);
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                            Properties.Entry e = super.visitEntry(entry, executionContext);
                            return legacy(e.getValue().getText()) ? JaxbImplSupport.mark(e, LEGACY) : e;
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                            Yaml.Scalar s = super.visitScalar(scalar, executionContext);
                            return legacy(s.getValue()) ? JaxbImplSupport.mark(s, LEGACY) : s;
                        }
                    }.visitNonNull(yaml, ctx);
                }
                if (tree instanceof Xml.Document xml && !"pom.xml".equalsIgnoreCase(fileName) &&
                    !bindingAutoFile(lower)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                            Xml.Attribute a = super.visitAttribute(attribute, executionContext);
                            if (standardBinding(a.getValueAsString())) return JaxbImplSupport.mark(a, BINDING);
                            return legacy(a.getValueAsString()) ? JaxbImplSupport.mark(a, LEGACY) : a;
                        }

                        @Override
                        public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                            Xml.CharData c = super.visitCharData(charData, executionContext);
                            return legacy(c.getText()) ? JaxbImplSupport.mark(c, LEGACY) : c;
                        }
                    }.visitNonNull(xml, ctx);
                }
                if (tree instanceof PlainText text) {
                    return new PlainTextVisitor<ExecutionContext>() {
                        @Override
                        public PlainText visitText(PlainText plainText, ExecutionContext executionContext) {
                            PlainText t = (PlainText) super.visitText(plainText, executionContext);
                            if (lower.contains("meta-inf/services/javax.xml.bind.jaxbcontext") ||
                                lower.contains("meta-inf/services/javax.xml.bind.jaxbcontextfactory")) {
                                return JaxbImplSupport.mark(t, DISCOVERY);
                            }
                            if (lower.endsWith("meta-inf/services/jakarta.xml.bind.jaxbcontextfactory")) {
                                List<String> providers = t.getText().lines().map(line -> line.replaceFirst("#.*$", "").trim())
                                        .filter(line -> !line.isEmpty()).toList();
                                return providers.size() == 1 && providers.get(0).matches(
                                        "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+") ? t
                                        : JaxbImplSupport.mark(t, DISCOVERY);
                            }
                            if ((lower.endsWith("manifest.mf") || lower.endsWith("bnd.bnd") ||
                                 lower.endsWith(".bnd") || lower.endsWith("reflect-config.json") ||
                                 lower.contains("native-image") || lower.endsWith(".wsdl")) && legacy(t.getText())) {
                                return JaxbImplSupport.mark(t, LEGACY);
                            }
                            return lower.endsWith(".wsdl") && standardBinding(t.getText())
                                    ? JaxbImplSupport.mark(t, BINDING) : t;
                        }
                    }.visitNonNull(text, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean bindingAutoFile(String lower) {
        return lower.endsWith(".xjb") || lower.endsWith(".jxb") || lower.endsWith(".xsd");
    }

    private static boolean legacy(String value) {
        return value.contains("javax.xml.bind") || value.contains("javax.activation") ||
               value.contains("com.sun.xml.bind") || value.contains("com.sun.xml.internal.bind") ||
               value.contains("java.xml.bind") || value.contains("java.activation");
    }

    private static boolean standardBinding(String value) {
        return value.replace("http://java.sun.com/xml/ns/jaxb/xjc", "")
                .contains("http://java.sun.com/xml/ns/jaxb");
    }
}
