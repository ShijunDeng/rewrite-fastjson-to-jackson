package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Normalizes the versioned MyBatis-Spring schema URL to the supported stable URL. */
public final class MigrateMyBatisSpringXmlSchema extends Recipe {
    private static final String VERSIONED = "http://mybatis.org/schema/mybatis-spring-1.2.xsd";
    private static final String STABLE = "http://mybatis.org/schema/mybatis-spring.xsd";

    @Override
    public String getDisplayName() {
        return "Use the stable MyBatis-Spring XML schema";
    }

    @Override
    public String getDescription() {
        return "Replace the legacy versioned mybatis-spring-1.2.xsd schema location with the stable unversioned URL.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Attribute.Value visitAttributeValue(Xml.Attribute.Value value, ExecutionContext ctx) {
                Xml.Attribute.Value v = super.visitAttributeValue(value, ctx);
                return v.getValue().contains(VERSIONED)
                        ? v.withValue(v.getValue().replace(VERSIONED, STABLE))
                        : v;
            }
        };
    }
}
