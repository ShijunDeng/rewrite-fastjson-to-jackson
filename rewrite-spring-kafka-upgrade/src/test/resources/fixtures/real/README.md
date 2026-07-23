# 固定真实仓库用例

这些 fixture 只保留触发迁移边界所需的最小代码，类名和业务载荷做了缩减；测试不会依赖仓库默认分支的后续变化。

| fixture | 固定仓库与 commit | 原始文件 | 验证 |
|---|---|---|---|
| `alexei-error-handler.java` | `AlexeiZenin/sb-gp-testing@5b1edf2dbaa45ce16058d11d04e10c5c284019ad` | `src/main/java/com/zenin/genericproto/config/KafkaConfig.java` | `SeekToCurrentErrorHandler`、`FixedBackOff` 自动迁移和错误处理语义 MARK |
| `kk-ddd-future-bridge.java` | `kingkh1995/kk-ddd@7d8e1b8e9355daf3a8259d02abb048428337f176` | `ddd-support/ddd-support-dependencies/src/main/java/com/kk/ddd/support/messaging/KafkaMessageProducer.java` | `KafkaOperations2` 与 `usingCompletableFuture()` 自动迁移 |
| `baeldung-json.java` | `eugenp/tutorials@5e4114a9482d68b6766ca738c087f0f9a87a7bd2` | `spring-kafka/src/main/java/com/baeldung/spring/kafka/KafkaConsumerConfig.java` | `JsonDeserializer` 与 Jackson type mapper 信任边界 MARK |
| `baeldung-retry-topic.java` | `eugenp/tutorials@5e4114a9482d68b6766ca738c087f0f9a87a7bd2` | `spring-kafka/src/main/java/com/baeldung/dlt/listener/PaymentListenerNoDlt.java` | `@RetryableTopic`、`@KafkaListener`、`@DltHandler` 与 DLT 策略 MARK |
| `dsyer-transaction.java` | `dsyer/dist-tx@88bc07b9c0f2a100d67fec2d283d883d908013fa` | `best-kafka-db/src/main/java/com/springsource/open/foo/ListenerApplication.java` | `ChainedKafkaTransactionManager` 与 `DefaultAfterRollbackProcessor` MARK |

原仓库许可证分别适用；这里的最小摘录仅用于兼容性测试，并在每个文件中保留来源说明。
