package com.huawei.clouds.openrewrite.jaxbimpl;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Locale;
import java.util.Set;

/** Migrates only standard JAXB binding declarations in known XML binding file types. */
public final class MigrateJaxbBindingFiles extends Recipe {
    private static final String OLD_NAMESPACE = "http://java.sun.com/xml/ns/jaxb";
    private static final String NEW_NAMESPACE = "https://jakarta.ee/xml/ns/jaxb";
    private static final String OLD_SCHEMA = OLD_NAMESPACE + "/bindingschema_2_0.xsd";
    private static final String NEW_SCHEMA = NEW_NAMESPACE + "/bindingschema_3_0.xsd";
    private static final String XJC_NAMESPACE = OLD_NAMESPACE + "/xjc";
    private static final String XJC_SENTINEL = "urn:openrewrite:jaxb-xjc-preserved";
    private static final Set<String> OLD_VERSIONS = Set.of("1.0", "2.0", "2.1");

    @Override
    public String getDisplayName() {
        return "Migrate JAXB binding declarations to the Jakarta 3.0 binding language";
    }

    @Override
    public String getDescription() {
        return "Update standard JAXB namespace, binding schema URL, and language version in xjb, jxb, and xsd " +
               "files while preserving the still-valid /xjc vendor extension namespace.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) || !(tree instanceof SourceFile source) ||
                    JaxbImplSupport.generated(source.getSourcePath()) || !bindingFile(source)) return tree;
                boolean legacyBinding = document.printAll().replace(OLD_NAMESPACE + "/xjc", "")
                        .contains(OLD_NAMESPACE);
                return new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                        Xml.Attribute a = super.visitAttribute(attribute, executionContext);
                        String value = a.getValueAsString();
                        String replaced = value.replace(OLD_SCHEMA, NEW_SCHEMA)
                                .replace(XJC_NAMESPACE, XJC_SENTINEL)
                                .replace(OLD_NAMESPACE, NEW_NAMESPACE)
                                .replace(XJC_SENTINEL, XJC_NAMESPACE);
                        if (legacyBinding && OLD_VERSIONS.contains(replaced) && isBindingVersion(getCursor(), a)) {
                            replaced = "3.0";
                        }
                        return replaced.equals(value) ? a : a.withValue(a.getValue().withValue(replaced));
                    }
                }.visitNonNull(document, ctx);
            }
        };
    }

    private static boolean bindingFile(SourceFile source) {
        String name = source.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xjb") || name.endsWith(".jxb") || name.endsWith(".xsd");
    }

    private static boolean isBindingVersion(Cursor cursor, Xml.Attribute attribute) {
        String key = attribute.getKeyAsString();
        if (key.endsWith(":version")) return true;
        Cursor parent = cursor.getParentTreeCursor();
        return "version".equals(key) && parent.getValue() instanceof Xml.Tag tag &&
               ("bindings".equals(tag.getName()) || tag.getName().endsWith(":bindings"));
    }
}
