package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

/** Deterministically migrates JPA setting names and Jakarta Persistence 3.2 XML metadata. */
public final class MigrateLegacyPersistenceConfiguration extends Recipe {
    private static final String OLD_PREFIX = "javax.persistence.";
    private static final String NEW_PREFIX = "jakarta.persistence.";
    private static final Set<String> OLD_SCHEMA_VERSIONS = Set.of(
            "1.0", "2.0", "2.1", "2.2", "3.0", "3.1"
    );

    @Override
    public String getDisplayName() {
        return "Migrate legacy persistence configuration to Jakarta Persistence 3.2";
    }

    @Override
    public String getDescription() {
        return "Updates JPA setting names in parsed properties/YAML scalars and updates only persistence.xml/orm.xml " +
               "namespace, schema, version, attribute, and text values without rewriting comments.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedHibernateCoreDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document &&
                    ("persistence.xml".equals(fileName) || "orm.xml".equals(fileName))) {
                    return migrateXml(document, ctx, fileName);
                }
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                            Properties.Entry visited = super.visitEntry(entry, executionContext);
                            String key = replacePrefix(visited.getKey());
                            String value = replacePrefix(visited.getValue().getText());
                            if (!key.equals(visited.getKey())) visited = visited.withKey(key);
                            if (!value.equals(visited.getValue().getText())) {
                                visited = visited.withValue(visited.getValue().withText(value));
                            }
                            return visited;
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                            Yaml.Scalar visited = super.visitScalar(scalar, executionContext);
                            String replacement = replacePrefix(visited.getValue());
                            return replacement.equals(visited.getValue()) ? visited : visited.withValue(replacement);
                        }
                    }.visitNonNull(yaml, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migrateXml(Xml.Document document, ExecutionContext ctx, String fileName) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                String value = normalizeXmlValue(visited.getValueAsString(), fileName);
                if ("version".equals(visited.getKeyAsString()) && OLD_SCHEMA_VERSIONS.contains(value) &&
                    getCursor().getParentTreeCursor().getValue() instanceof Xml.Tag owner &&
                    (("persistence.xml".equals(fileName) && "persistence".equals(owner.getName())) ||
                     ("orm.xml".equals(fileName) && "entity-mappings".equals(owner.getName())))) {
                    value = "3.2";
                }
                return value.equals(visited.getValueAsString()) ? visited :
                        visited.withValue(visited.getValue().withValue(value));
            }

            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                String replacement = replacePrefix(visited.getText());
                return replacement.equals(visited.getText()) ? visited : visited.withText(replacement);
            }
        }.visitNonNull(document, ctx);
    }

    private static String normalizeXmlValue(String value, String fileName) {
        String replacement = replacePrefix(value);
        if ("persistence.xml".equals(fileName)) {
            replacement = replacement
                    .replace("http://java.sun.com/xml/ns/persistence", "https://jakarta.ee/xml/ns/persistence")
                    .replace("http://xmlns.jcp.org/xml/ns/persistence", "https://jakarta.ee/xml/ns/persistence")
                    .replaceAll("persistence_(?:1_0|2_[012]|3_[01])\\.xsd", "persistence_3_2.xsd");
        } else {
            replacement = replacement
                    .replace("http://java.sun.com/xml/ns/persistence/orm", "https://jakarta.ee/xml/ns/persistence/orm")
                    .replace("http://xmlns.jcp.org/xml/ns/persistence/orm", "https://jakarta.ee/xml/ns/persistence/orm")
                    .replaceAll("orm_(?:1_0|2_[012]|3_[01])\\.xsd", "orm_3_2.xsd");
        }
        return replacement;
    }

    private static String replacePrefix(String value) {
        return value.contains(OLD_PREFIX) ? value.replace(OLD_PREFIX, NEW_PREFIX) : value;
    }
}
