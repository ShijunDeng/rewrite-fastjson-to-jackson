package com.huawei.clouds.openrewrite.springkafka;

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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structured Spring Boot/native Kafka configuration markers. */
public final class FindSpringKafka3315ConfigurationRisks extends Recipe {
    static final String JSON =
            "JSON 序列化配置会影响 type header、trusted packages、默认目标类型与映射；核对 producer/consumer 两端，" +
            "禁止将不可信输入配置为 '*'，并测试 ErrorHandlingDeserializer 恢复路径";
    static final String CONTAINER =
            "该 listener container 配置会改变 ack、batch/record、asyncAcks、并发、poll、缺失 topic、授权失败与停止行为；" +
            "请用 rebalance、重复/乱序、长处理和故障注入回归";
    static final String TRANSACTION =
            "该 Kafka 事务/EOS 配置必须满足 3.x 仅 EOSMode.V2、broker>=2.5 与每实例唯一 transactional.id；" +
            "同时验证 enable.idempotence、acks、isolation.level、超时、fencing 与回滚";
    static final String RETRY_DLT =
            "该 retry topic/DLT 配置跨越 3.0 启动时序、默认复制因子 -1、topic 命名/复用、并发与 header 变化；" +
            "请验证 fatal 分类、耗尽、DLT 失败和事务不兼容路径";
    static final String OBSERVATION =
            "该 Micrometer timer/observation 配置会改变指标和 trace 生成；核对 batch listener、Registry/Convention、" +
            "标签基数、敏感字段、采样与 dashboard/alert 连续性";
    static final String NATIVE_CLIENT =
            "该原生 Kafka client 配置随 kafka-clients 3.8.1 重新解释；请对照客户端文档验证默认值、协议、超时、" +
            "rebalance、压缩、安全与 broker 混合版本";
    static final String XML =
            "Spring XML 中的 Kafka bean/property 跨越 3.x API；请迁移已移除 handler/EOS 类型，并验证容器、" +
            "serializer、transaction、retry 与 observation 的实际装配";

    private static final Set<String> CONTAINER_KEYS = Set.of(
            "spring.kafka.listener.ack-mode",
            "spring.kafka.listener.type",
            "spring.kafka.listener.async-acks",
            "spring.kafka.listener.concurrency",
            "spring.kafka.listener.poll-timeout",
            "spring.kafka.listener.idle-between-polls",
            "spring.kafka.listener.no-poll-threshold",
            "spring.kafka.listener.missing-topics-fatal",
            "spring.kafka.listener.immediate-stop",
            "spring.kafka.listener.auth-exception-retry-interval",
            "spring.kafka.listener.monitor-interval");
    private static final Set<String> OBSERVATION_KEYS = Set.of(
            "spring.kafka.listener.observation-enabled",
            "spring.kafka.listener.micrometer-enabled",
            "spring.kafka.template.observation-enabled",
            "spring.kafka.template.micrometer.enabled",
            "spring.kafka.template.micrometer.tags");
    private static final Set<String> TRANSACTION_SUFFIXES = Set.of(
            "enable.idempotence", "transactional.id", "isolation.level", "transaction.timeout.ms",
            "delivery.timeout.ms", "max.in.flight.requests.per.connection", "acks");
    private static final Set<String> XML_PROPERTY_NAMES = Set.of(
            "errorHandler", "batchErrorHandler", "commonErrorHandler", "afterRollbackProcessor",
            "ackMode", "asyncAcks", "batchListener", "transactionManager", "kafkaAwareTransactionManager",
            "transactionIdPrefix", "eosMode", "observationEnabled", "observationConvention",
            "micrometerEnabled", "recordMessageConverter", "batchMessageConverter");

    @Override
    public String getDisplayName() {
        return "Find Spring Kafka 3.3.15 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact JSON, listener/container, transaction/EOS, retry/DLT, observation and native client " +
               "properties/YAML paths, plus Spring XML Kafka bean boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringKafkaUpgradeSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml &&
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString())) return xml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String message = propertyMessage(normalize(visited.getKey()),
                        visited.getValue().getText().trim());
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                if (!(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                String message = propertyMessage(normalize(path()), scalar.getValue());
                return message == null ? visited : mark(visited, message);
            }

            private String path() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                String className = attribute(visited, "class");
                if (className != null && springKafkaClass(className)) return mark(visited, XML);
                if ("property".equals(visited.getName())) {
                    String name = attribute(visited, "name");
                    if (XML_PROPERTY_NAMES.contains(name) && springKafkaBean(getCursor())) {
                        return mark(visited, XML);
                    }
                }
                String value = visited.getValue().map(String::trim).orElse("");
                return springKafkaClass(value) ? mark(visited, XML) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    static String propertyMessage(String rawKey, String value) {
        String key = normalize(rawKey);
        if (!key.startsWith("spring.kafka.") && !key.startsWith("spring.json.") &&
            !key.startsWith("spring.deserializer.") && !key.startsWith("spring.serializer.")) return null;
        if (jsonKey(key)) return JSON;
        if (CONTAINER_KEYS.contains(key)) return CONTAINER;
        if ("spring.kafka.listener.eos-mode".equals(key) ||
            "spring.kafka.producer.transaction-id-prefix".equals(key) ||
            TRANSACTION_SUFFIXES.stream().anyMatch(suffix -> key.endsWith("." + suffix))) return TRANSACTION;
        if (key.startsWith("spring.kafka.retry.topic.") ||
            key.contains(".retry-topic.") || key.contains(".dlt.")) return RETRY_DLT;
        if (OBSERVATION_KEYS.contains(key) || key.contains(".observation.") ||
            key.contains(".micrometer.")) return OBSERVATION;
        if (key.contains(".properties.")) return NATIVE_CLIENT;
        return null;
    }

    private static boolean jsonKey(String key) {
        return key.startsWith("spring.json.") || key.startsWith("spring.deserializer.") ||
               key.startsWith("spring.serializer.") || key.contains(".spring.json.") ||
               key.endsWith(".json-deserializer") || key.endsWith(".json-serializer") ||
               key.endsWith(".trusted.packages") || key.endsWith(".type.mapping") ||
               key.endsWith(".value.default.type") || key.endsWith(".key.default.type");
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean springKafkaClass(String value) {
        return value.startsWith("org.springframework.kafka.") &&
               (value.contains("ErrorHandler") || value.contains("JsonSerializer") ||
                value.contains("JsonDeserializer") || value.contains("KafkaTransactionManager") ||
                value.contains("ListenerContainer") || value.contains("RetryTopic") ||
                value.contains("DeadLetter"));
    }

    private static boolean springKafkaBean(org.openrewrite.Cursor cursor) {
        for (org.openrewrite.Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "bean".equals(tag.getName())) {
                String className = attribute(tag, "class");
                return className != null && className.startsWith("org.springframework.kafka.");
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
