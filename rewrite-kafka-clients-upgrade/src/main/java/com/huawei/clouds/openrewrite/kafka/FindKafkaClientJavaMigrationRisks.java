package com.huawei.clouds.openrewrite.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Marks Kafka 4.1 API changes whose replacement requires application intent. */
public final class FindKafkaClientJavaMigrationRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Kafka client 4.1 Java migrations";
    }

    @Override
    public String getDescription() {
        return "Mark removed Kafka client APIs and changed error behavior that cannot be migrated safely from syntax alone.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (methodOn(m, "org.apache.kafka.clients.admin.Admin", "alterConfigs")) {
                    return SearchResult.found(m, "alterConfigs was removed; convert complete Config maps into explicit incrementalAlterConfigs AlterConfigOp operations");
                }
                if (methodOn(m, "org.apache.kafka.clients.producer.Producer", "sendOffsetsToTransaction") &&
                    m.getMethodType().getParameterTypes().size() == 2 &&
                    isString(m.getMethodType().getParameterTypes().get(1))) {
                    return SearchResult.found(m, "The consumerGroupId overload was removed; obtain the matching ConsumerGroupMetadata before choosing the replacement");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions", "topicPartitions")) {
                    return SearchResult.found(m, "topicPartitions was removed; move the partition selection to the Map argument of listConsumerGroupOffsets");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.FeatureUpdate", "allowDowngrade")) {
                    return SearchResult.found(m, "allowDowngrade was removed; choose an explicit FeatureUpdate.UpgradeType");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.Admin", "describeConsumerGroups")) {
                    return SearchResult.found(m, "Missing groups now fail with GroupIdNotFoundException instead of returning a DEAD group; review error handling");
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.admin.TopicListing") &&
                    n.getArguments().size() == 2) {
                    return SearchResult.found(n, "The two-argument TopicListing constructor was removed; supply the real topic Uuid");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.admin.FeatureUpdate") &&
                    n.getArguments().size() == 2 && isBoolean(n.getArguments().get(1).getType())) {
                    return SearchResult.found(n, "The boolean allowDowngrade constructor was removed; choose an explicit FeatureUpdate.UpgradeType");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.common.metrics.JmxReporter") &&
                    n.getArguments().size() == 1) {
                    return SearchResult.found(n, "JmxReporter(String) was removed; use the no-arg reporter and configure include/exclude rules");
                }
                return n;
            }
        };
    }

    private static boolean methodOn(J.MethodInvocation method, String owner, String name) {
        return name.equals(method.getSimpleName()) && method.getMethodType() != null &&
               TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }

    private static boolean isString(JavaType type) {
        return TypeUtils.isOfClassType(type, "java.lang.String");
    }

    private static boolean isBoolean(JavaType type) {
        return type == JavaType.Primitive.Boolean || TypeUtils.isOfClassType(type, "java.lang.Boolean");
    }
}
