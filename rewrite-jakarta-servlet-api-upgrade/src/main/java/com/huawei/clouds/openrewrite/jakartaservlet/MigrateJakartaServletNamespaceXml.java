package com.huawei.clouds.openrewrite.jakartaservlet;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Replace Servlet package names in XML values where the namespace change is textual and exact. */
public final class MigrateJakartaServletNamespaceXml extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate javax Servlet package names in XML configuration";
    }

    @Override
    public String getDescription() {
        return "Replace exact javax.servlet package prefixes in non-POM XML text and attribute values with " +
               "jakarta.servlet, as required by the Servlet 4.0 to 5.0 namespace transition.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                String fileName = document.getSourcePath().getFileName().toString();
                if ("pom.xml".equals(fileName) || !document.printAll().contains("javax.servlet.")) {
                    return document;
                }
                return super.visitDocument(document, ctx);
            }

            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ctx) {
                Xml.CharData c = super.visitCharData(charData, ctx);
                return c.getText().contains("javax.servlet.")
                        ? c.withText(c.getText().replace("javax.servlet.", "jakarta.servlet.")) : c;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ctx) {
                Xml.Attribute a = super.visitAttribute(attribute, ctx);
                String value = a.getValueAsString();
                return value.contains("javax.servlet.")
                        ? a.withValue(a.getValue().withValue(value.replace("javax.servlet.", "jakarta.servlet."))) : a;
            }
        };
    }
}
