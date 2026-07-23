package com.huawei.clouds.openrewrite.springkafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Type-attributed markers for Spring Kafka 2.x to 3.3 behavior boundaries. */
public final class FindSpringKafka3315SourceRisks extends Recipe {
    static final String LEGACY_ERROR_HANDLER =
            "旧 ErrorHandler/BatchErrorHandler 层次在 3.x 已移除；按监听器类型迁移到 CommonErrorHandler，" +
            "逐项验证 seekAfterError、ackAfterHandle、BackOff、fatal 分类、恢复提交与批处理回放";
    static final String DEFAULT_ERROR_HANDLER =
            "DefaultErrorHandler 的 record/batch、seek 或 retain、BackOff、recoverer、fatal 分类及事务交互需要行为测试；" +
            "尤其确认失败次数、提交已恢复 offset 与 BatchListenerFailedException 路径";
    static final String FUTURE =
            "KafkaTemplate/ReplyingKafkaTemplate 在 3.x 返回 CompletableFuture；检查 addCallback 到 whenComplete 的异常分支、" +
            "线程上下文、取消、超时、组合与同步等待，避免只做到可编译";
    static final String JSON =
            "JsonSerializer/JsonDeserializer、ErrorHandlingDeserializer 或 Jackson type mapper 边界需要核对 type headers、" +
            "trusted packages、默认类型、映射、setter/fluent 与 configure 混用及失败值恢复，禁止无边界信任 '*'";
    static final String CONTAINER =
            "监听容器/工厂配置会影响 ack mode、asyncAcks/nack、batch/record、pause/seek、授权重试、并发、poll、" +
            "interceptor 与停止时序；请用真实 rebalance 和故障注入验证";
    static final String TRANSACTION =
            "Spring Kafka 3.x 仅支持 EOSMode.V2，broker 至少 2.5，transactional.id 每实例必须唯一；" +
            "ChainedKafkaTransactionManager 已弃用，请验证 Kafka/数据库提交顺序、fencing、回滚恢复与幂等";
    static final String RETRY_DLT =
            "RetryableTopic、RetryTopicConfigurationSupport、DeadLetterPublishingRecoverer 或 AfterRollbackProcessor " +
            "跨越 3.0 启动/API 与默认复制因子变化；验证 topic 命名、并发、header、fatal 分类、DLT 策略及事务互斥";
    static final String OBSERVATION =
            "3.x 的 Micrometer Observation 会接管 timer，batch listener 行为不同；核对启用开关、Registry/Convention、" +
            "低/高基数标签、trace 传播、敏感数据与基数上限";
    static final String EXTENSION =
            "该自定义 Spring Kafka 扩展实现了容器、Consumer/ProducerFactory、converter/interceptor 或 listener SPI；" +
            "请按 3.3.15 重新编译并验证生命周期、泛型、线程安全、异常与二进制描述符";
    static final String KAFKA_CLIENT =
            "该代码直接使用 Kafka client API；随 Spring Kafka 3.3.15 对齐到 kafka-clients 3.8.1 后，" +
            "请验证配置默认值、Admin/Consumer/Producer/Streams API、序列化、协议与 broker 混合版本";
    static final String LISTENER =
            "@KafkaListener/@KafkaHandler 方法签名、批/单条、Acknowledgment、ConsumerRecordMetadata、异步返回、" +
            "reply/correlation 与异常处理共同决定交付语义；请做重复、乱序、rebalance、超时及 DLT 回归";

    private static final Set<String> LEGACY_HANDLER_TYPES = Set.of(
            "org.springframework.kafka.listener.ErrorHandler",
            "org.springframework.kafka.listener.BatchErrorHandler",
            "org.springframework.kafka.listener.GenericErrorHandler",
            "org.springframework.kafka.listener.ConsumerAwareErrorHandler",
            "org.springframework.kafka.listener.ConsumerAwareBatchErrorHandler",
            "org.springframework.kafka.listener.ContainerAwareErrorHandler",
            "org.springframework.kafka.listener.ContainerAwareBatchErrorHandler",
            "org.springframework.kafka.listener.RemainingRecordsErrorHandler",
            "org.springframework.kafka.listener.SeekToCurrentBatchErrorHandler",
            "org.springframework.kafka.listener.RetryingBatchErrorHandler",
            "org.springframework.kafka.listener.RecoveringBatchErrorHandler",
            "org.springframework.kafka.listener.LoggingErrorHandler",
            "org.springframework.kafka.listener.BatchLoggingErrorHandler",
            "org.springframework.kafka.listener.ContainerStoppingErrorHandler",
            "org.springframework.kafka.listener.ContainerStoppingBatchErrorHandler",
            "org.springframework.kafka.listener.ConditionalDelegatingErrorHandler",
            "org.springframework.kafka.listener.ConditionalDelegatingBatchErrorHandler");
    private static final Set<String> ERROR_HANDLER_TYPES = Set.of(
            "org.springframework.kafka.listener.DefaultErrorHandler",
            "org.springframework.kafka.listener.CommonErrorHandler",
            "org.springframework.kafka.listener.CommonContainerStoppingErrorHandler",
            "org.springframework.kafka.listener.CommonDelegatingErrorHandler",
            "org.springframework.kafka.listener.CommonLoggingErrorHandler",
            "org.springframework.kafka.listener.CommonMixedErrorHandler",
            "org.springframework.kafka.listener.FallbackBatchErrorHandler");
    private static final Set<String> JSON_TYPES = Set.of(
            "org.springframework.kafka.support.serializer.JsonSerializer",
            "org.springframework.kafka.support.serializer.JsonDeserializer",
            "org.springframework.kafka.support.serializer.JsonSerde",
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
            "org.springframework.kafka.support.serializer.DelegatingByTopicDeserializer",
            "org.springframework.kafka.support.serializer.DelegatingByTopicSerializer",
            "org.springframework.kafka.support.converter.JsonMessageConverter",
            "org.springframework.kafka.support.converter.StringJsonMessageConverter",
            "org.springframework.kafka.support.converter.BytesJsonMessageConverter",
            "org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter",
            "org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper",
            "org.springframework.kafka.support.mapping.Jackson2JavaTypeMapper");
    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "org.springframework.kafka.transaction.KafkaTransactionManager",
            "org.springframework.kafka.transaction.ChainedKafkaTransactionManager",
            "org.springframework.kafka.listener.ContainerProperties.EOSMode",
            "org.springframework.kafka.core.KafkaResourceHolder");
    private static final Set<String> RETRY_TYPES = Set.of(
            "org.springframework.kafka.annotation.RetryableTopic",
            "org.springframework.kafka.annotation.DltHandler",
            "org.springframework.kafka.retrytopic.RetryTopicConfiguration",
            "org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder",
            "org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport",
            "org.springframework.kafka.retrytopic.RetryTopicConfigurer",
            "org.springframework.kafka.retrytopic.DltStrategy",
            "org.springframework.kafka.retrytopic.DestinationTopicResolver",
            "org.springframework.kafka.listener.DeadLetterPublishingRecoverer",
            "org.springframework.kafka.listener.DefaultAfterRollbackProcessor",
            "org.springframework.kafka.listener.AfterRollbackProcessor");
    private static final Set<String> CONTAINER_TYPES = Set.of(
            "org.springframework.kafka.config.AbstractKafkaListenerContainerFactory",
            "org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory",
            "org.springframework.kafka.listener.ContainerProperties",
            "org.springframework.kafka.listener.AbstractMessageListenerContainer",
            "org.springframework.kafka.listener.KafkaMessageListenerContainer",
            "org.springframework.kafka.listener.ConcurrentMessageListenerContainer",
            "org.springframework.kafka.listener.MessageListenerContainer");
    private static final Set<String> EXTENSION_TYPES = Set.of(
            "org.springframework.kafka.core.ConsumerFactory",
            "org.springframework.kafka.core.ProducerFactory",
            "org.springframework.kafka.listener.RecordInterceptor",
            "org.springframework.kafka.listener.BatchInterceptor",
            "org.springframework.kafka.support.converter.RecordMessageConverter",
            "org.springframework.kafka.support.converter.BatchMessageConverter",
            "org.springframework.kafka.listener.GenericMessageListener",
            "org.springframework.kafka.config.KafkaListenerContainerFactory");
    private static final Set<String> OBSERVATION_TYPES = Set.of(
            "org.springframework.kafka.support.micrometer.KafkaListenerObservation",
            "org.springframework.kafka.support.micrometer.KafkaListenerObservationConvention",
            "org.springframework.kafka.support.micrometer.KafkaTemplateObservation",
            "org.springframework.kafka.support.micrometer.KafkaTemplateObservationConvention");
    private static final Set<String> CONTAINER_METHODS = Set.of(
            "setAckMode", "setAsyncAcks", "setBatchListener", "setCommonErrorHandler",
            "setErrorHandler", "setBatchErrorHandler", "setAfterRollbackProcessor",
            "setConcurrency", "setPollTimeout", "setIdleBetweenPolls", "setNoPollThreshold",
            "setAuthExceptionRetryInterval", "setRestartAfterAuthExceptions", "setPauseImmediate",
            "setStopImmediate", "setMissingTopicsFatal", "setInterceptBeforeTx", "setRecordInterceptor",
            "setBatchInterceptor", "setContainerCustomizer", "setContainerPostProcessor");
    private static final Set<String> JSON_METHODS = Set.of(
            "addTrustedPackages", "trustedPackages", "ignoreTypeHeaders", "dontRemoveTypeHeaders",
            "noTypeInfo", "forKeys", "typeFunction", "typeResolver", "setTypeMapper",
            "setTypePrecedence", "setIdClassMapping", "configure");
    private static final Set<String> TRANSACTION_METHODS = Set.of(
            "setTransactionIdPrefix", "setTransactionManager", "setKafkaAwareTransactionManager",
            "executeInTransaction", "sendOffsetsToTransaction", "setAllowNonTransactional",
            "setProducerPerConsumerPartition", "setEOSMode", "setEosMode");
    private static final Set<String> RETRY_METHODS = Set.of(
            "setCommitRecovered", "setKafkaTemplate", "setFailIfSendResultIsError",
            "setAppendOriginalHeaders", "setStripPreviousExceptionHeaders",
            "setRetainAllRetryHeaderValues", "setVerifyPartition", "setThrowIfNoDestinationReturned",
            "setClassifications", "addNotRetryableExceptions", "addRetryableExceptions",
            "setResetStateOnExceptionChange", "setResetStateOnRecoveryFailure",
            "customBackoff", "exponentialBackoff", "fixedBackOff", "maxAttempts", "dltProcessingFailureStrategy");
    private static final Set<String> OBSERVATION_METHODS = Set.of(
            "setObservationEnabled", "setObservationConvention", "setObservationRegistry",
            "setMicrometerEnabled", "setMicrometerTags", "setMicrometerTagsProvider");

    @Override
    public String getDisplayName() {
        return "Find Spring Kafka 3.3.15 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark legacy/common error handlers, futures, JSON, containers, transactions/EOS, retries/DLT, " +
               "observation, listener semantics, extension SPIs and direct Kafka client boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return SpringKafkaUpgradeSupport.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String message = typeMessage(visited.getTypeName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String type = fqn(visited.getType());
                if ("org.springframework.kafka.annotation.RetryableTopic".equals(type) ||
                    "org.springframework.kafka.annotation.DltHandler".equals(type)) {
                    return mark(visited, RETRY_DLT);
                }
                if ("org.springframework.kafka.annotation.KafkaListener".equals(type) ||
                    "org.springframework.kafka.annotation.KafkaHandler".equals(type)) {
                    return mark(visited, LISTENER);
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType type = visited.getType();
                for (String legacy : LEGACY_HANDLER_TYPES) {
                    if (assignable(legacy, type)) return mark(visited, LEGACY_ERROR_HANDLER);
                }
                for (String extension : EXTENSION_TYPES) {
                    if (assignable(extension, type)) return mark(visited, EXTENSION);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String message = typeMessage(fqn(visited.getType()));
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String name = visited.getSimpleName();
                if (legacyOwner(owner)) return mark(visited, LEGACY_ERROR_HANDLER);
                if (errorHandlerOwner(owner)) return mark(visited, DEFAULT_ERROR_HANDLER);
                if (jsonOwner(owner) && (JSON_METHODS.contains(name) || "configure".equals(name))) {
                    return mark(visited, JSON);
                }
                if (futureOwner(owner) && (name.startsWith("send") || name.startsWith("requestReply") ||
                    "usingCompletableFuture".equals(name))) return mark(visited, FUTURE);
                if (CONTAINER_METHODS.contains(name) && containerOwner(owner)) return mark(visited, CONTAINER);
                if (owner.startsWith("org.springframework.kafka.transaction.") ||
                    TRANSACTION_METHODS.contains(name) && springKafkaOwner(owner)) {
                    return mark(visited, TRANSACTION);
                }
                if (RETRY_METHODS.contains(name) && (retryOwner(owner) || errorHandlerOwner(owner))) {
                    return mark(visited, RETRY_DLT);
                }
                if (OBSERVATION_METHODS.contains(name) && springKafkaOwner(owner)) {
                    return mark(visited, OBSERVATION);
                }
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = visited.getFieldType();
                if (field == null) return visited;
                String owner = fqn(field.getOwner());
                if ("org.springframework.kafka.listener.ContainerProperties.EOSMode".equals(owner)) {
                    return mark(visited, TRANSACTION);
                }
                return visited;
            }
        };
    }

    private static String typeMessage(String type) {
        if (type == null || type.isEmpty()) return null;
        if (LEGACY_HANDLER_TYPES.contains(type)) return LEGACY_ERROR_HANDLER;
        if (ERROR_HANDLER_TYPES.contains(type)) return DEFAULT_ERROR_HANDLER;
        if (JSON_TYPES.contains(type) || type.startsWith("org.springframework.kafka.support.serializer.")) return JSON;
        if (TRANSACTION_TYPES.contains(type) || type.startsWith("org.springframework.kafka.transaction.")) {
            return TRANSACTION;
        }
        if (RETRY_TYPES.contains(type) || type.startsWith("org.springframework.kafka.retrytopic.")) return RETRY_DLT;
        if (OBSERVATION_TYPES.contains(type) || type.startsWith("org.springframework.kafka.support.micrometer.")) {
            return OBSERVATION;
        }
        if (CONTAINER_TYPES.contains(type)) return CONTAINER;
        if (type.startsWith("org.apache.kafka.clients.") || type.startsWith("org.apache.kafka.streams.")) {
            return KAFKA_CLIENT;
        }
        return null;
    }

    private static boolean legacyOwner(String owner) {
        return LEGACY_HANDLER_TYPES.contains(owner);
    }

    private static boolean errorHandlerOwner(String owner) {
        return ERROR_HANDLER_TYPES.contains(owner) ||
               owner.startsWith("org.springframework.kafka.listener.DefaultAfterRollbackProcessor") ||
               owner.startsWith("org.springframework.kafka.listener.DeadLetterPublishingRecoverer");
    }

    private static boolean jsonOwner(String owner) {
        return JSON_TYPES.contains(owner) ||
               owner.startsWith("org.springframework.kafka.support.serializer.") ||
               owner.startsWith("org.springframework.kafka.support.mapping.") ||
               owner.startsWith("org.springframework.kafka.support.converter.");
    }

    private static boolean futureOwner(String owner) {
        return owner.startsWith("org.springframework.kafka.core.KafkaOperations") ||
               owner.startsWith("org.springframework.kafka.core.KafkaTemplate") ||
               owner.startsWith("org.springframework.kafka.requestreply.ReplyingKafka");
    }

    private static boolean containerOwner(String owner) {
        return owner.startsWith("org.springframework.kafka.config.") ||
               owner.startsWith("org.springframework.kafka.listener.");
    }

    private static boolean retryOwner(String owner) {
        return owner.startsWith("org.springframework.kafka.retrytopic.") ||
               owner.startsWith("org.springframework.kafka.listener.DeadLetter") ||
               owner.startsWith("org.springframework.kafka.listener.DefaultAfterRollback");
    }

    private static boolean springKafkaOwner(String owner) {
        return owner.startsWith("org.springframework.kafka.");
    }

    private static boolean assignable(String target, JavaType type) {
        return type != null && TypeUtils.isAssignableTo(target, type);
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
