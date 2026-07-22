package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Marks ambiguous MyBatis-Spring mapper scanning in XML bean definitions. */
public final class FindMyBatisStarterXmlRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find MyBatis Starter 4 XML risks";
    }

    @Override
    public String getDescription() {
        return "Mark mybatis:scan declarations that specify both a SqlSessionFactory and a SqlSessionTemplate.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!("scan".equals(t.getName()) || t.getName().endsWith(":scan"))) {
                    return t;
                }
                boolean hasFactory = t.getAttributes().stream()
                        .anyMatch(attribute -> "factory-ref".equals(attribute.getKeyAsString()));
                boolean hasTemplate = t.getAttributes().stream()
                        .anyMatch(attribute -> "template-ref".equals(attribute.getKeyAsString()));
                return hasFactory && hasTemplate
                        ? SearchResult.found(t,
                        "mybatis:scan specifies both factory-ref and template-ref; select one session boundary explicitly")
                        : t;
            }
        };
    }
}
