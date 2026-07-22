# Apache Kafka Clients 迁移到 4.1.2

本模块对应 `开源软件升级.xlsx` 中的 `org.apache.kafka:kafka-clients`。推荐入口是：

```text
com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2
```

只修改依赖版本、不修改源码和配置时使用：

```text
com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2
```

## 表格版本边界

目标版本固定为 `4.1.2`。工作簿当前仍能读取到以下 10 个精确源版本，配方只接受这些可证明值：

```text
2.4.1  2.5.1  3.1.2  3.4.0  3.4.1  3.5.1  3.6.0  3.6.1  3.6.2  3.7.0
```

表格用 `3.7.0 ...（共16个版本）` 压缩显示了总数，但文件没有保留其余 6 个精确值。Maven Central 的发布列表只能证明版本存在，不能证明它被本次表格选中，因此本模块不臆测补全。`3.7.1`、`4.1.1`、`2.7.0` 等未明确保存的版本、版本范围、`LATEST`、Gradle 插值/变量、version catalog、无显式版本的外部 BOM 声明、目标版本及更新版本均不升级；取得剩余精确清单后再加入集合和参数化测试。

Maven property 只有在值属于上表时才处理：若只服务于 `kafka-clients`，更新 property；若同时服务于 `kafka-streams` 等其他坐标，仅在 `kafka-clients` 上内联 `4.1.2`，不改变共享 property。Maven `dependencyManagement`、Gradle Groovy/Kotlin 字符串坐标和 Groovy map notation 均覆盖；不会修改 `kafka-streams`、broker/server、Spring Kafka 或其他客户端。

## 处理状态

| 状态 | 不兼容点 | 配方行为 |
| --- | --- | --- |
| AUTO | `DescribeTopicsResult.values()` / `all()` 删除 | 改为 `topicNameValues()` / `allTopicNames()` |
| AUTO | `DeleteTopicsResult.values()` 删除 | 改为 `topicNameValues()`；仍有效的 `all()` 不动 |
| AUTO | `MockConsumer.setException(KafkaException)` 删除 | 改为 `setPollException(...)` |
| AUTO | `UpdateFeaturesOptions.dryRun(boolean)` 删除 | 改为 `validateOnly(boolean)` |
| AUTO | secured 包中的 OAuth login/validator callback handler 删除 | 迁移到 `org.apache.kafka.common.security.oauthbearer` 包 |
| AUTO | `metrics.jmx.blacklist` / `whitelist` 重命名 | 精确改为 `metrics.jmx.exclude` / `include` |
| AUTO | `auto.include.jmx.reporter` 删除且 JMX reporter 默认启用 | 从 `.properties` 精确删除该键 |
| MARK | `Admin.alterConfigs(...)` 删除 | 标记；需将完整 Config 语义拆成 `incrementalAlterConfigs` 的 `AlterConfigOp` |
| MARK | `Producer.sendOffsetsToTransaction(Map,String)` 删除 | 标记；需取得匹配的 `ConsumerGroupMetadata` |
| MARK | `TopicListing(String,boolean)`、`FeatureUpdate(short,boolean)`、`allowDowngrade()` 删除 | 标记；真实 topic UUID 和 upgrade type 不能从语法推断 |
| MARK | `ListConsumerGroupOffsetsOptions.topicPartitions(...)` 删除 | 标记；partition 选择要迁到 Admin 调用的 Map 参数 |
| MARK | `JmxReporter(String)` 删除 | 标记；改用无参构造并复核 include/exclude 配置 |
| MARK | `describeConsumerGroups` 对不存在 group 的行为变化 | 标记；4.x 抛 `GroupIdNotFoundException`，不再返回 DEAD group |
| MARK | OAuth token/JWKS endpoint URL | 标记；JVM 必须通过 `org.apache.kafka.sasl.oauthbearer.allowed.urls` 放行 |
| MARK | idempotence 默认开启且 `max.in.flight.requests.per.connection > 5` | 仅在 `enable.idempotence` 缺失或为 true 时标记；显式 false、值不大于 5、占位符均不误报 |
| MARK | properties 值中出现被删除的 metrics | 标记 `bufferpool-wait-time-total`、`io-waittime-total`、`iotime-total`；新指标为 ns 单位，阈值不可机械替换 |
| NO-OP | 表格外版本、范围、动态/未解析版本、外部 BOM、其他 Kafka artifact | 保持不变，防止越权升级或脱离平台兼容矩阵 |
| MANUAL | Java 11 最低版本、producer 默认 `linger.ms` 从 0 变 5、rebalance/transaction 行为 | 配方无法从局部源码安全判断，必须做构建和集成验证 |
| MANUAL | broker KRaft、Kafka Streams/Connect、Log4j2、dashboard/yaml/json | 不属于 `kafka-clients` 模块或当前 properties AST 范围，按对应升级指南独立处理 |

Java 迁移依赖类型归因，因此普通 `Map.values()`、业务类 `all()` 和其他 Admin result 的同名方法不会被修改。SearchResult 只提示人工决策，不伪造可能错误的替换。

## 官方依据

目标 `4.1.2` annotated tag 固定到 Apache Kafka commit [`c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c`](https://github.com/apache/kafka/tree/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c)。实现和测试对照以下固定源码与官方指南：

- [Kafka 4.1 upgrade guide（固定 commit）](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/docs/upgrade.html)
- [Kafka compatibility guide（固定 commit）](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/docs/compatibility.html)
- [DescribeTopicsResult 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/admin/DescribeTopicsResult.java)
- [DeleteTopicsResult 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/admin/DeleteTopicsResult.java)
- [MockConsumer 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/consumer/MockConsumer.java)
- [UpdateFeaturesOptions 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/admin/UpdateFeaturesOptions.java)

## 真实仓库与测试证据

测试采用 OpenRewrite 官方固定提交中的 [`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java) 与 [`ChangePropertyKeyTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-properties/src/test/java/org/openrewrite/properties/ChangePropertyKeyTest.java) 的 before/after、no-op、marker 和 recipe validation 结构，并从固定公共 commit 抽取形态：

- [codingmiao/hppt@509da821](https://github.com/codingmiao/hppt/blob/509da821a3cc33e8049d6037d90637e2274a0016/addons-kafka/src/main/java/org/wowtools/hppt/addons/kafka/KafkaUtil.java)：真实 `DescribeTopicsResult.values()`，验证类型安全的自动迁移。
- [conductor-oss/conductor@54f8369f](https://github.com/conductor-oss/conductor/blob/54f8369fa8875a2bad4ed5baa8a66f89720b1594/kafka/build.gradle)：`${revKafka}` 动态 Gradle 坐标，验证严格 no-op 门禁。
- [jhipster/generator-jhipster@41d71af1](https://github.com/jhipster/generator-jhipster/blob/41d71af1eb85ae7c94e0e9b05acab968c4d047e3/generators/spring-boot/resources/spring-boot-dependencies.pom)：共享 `kafka.version` 形态，验证只内联 client、不改其他消费者。
- [openGauss datachecker@3099b9db](https://github.com/opengauss-mirror/openGauss-tools-datachecker-performance/blob/3099b9db802cf0b09d4f1ad1a556b1cd5f5c6988/datachecker-extract/src/main/java/org/opengauss/datachecker/extract/kafka/KafkaAdminService.java)：已使用 `topicNameValues()`，验证幂等。
- [vert-x3/vertx-kafka-client@57cdfd5e](https://github.com/vert-x3/vertx-kafka-client/tree/57cdfd5e63cb45dccc18cacb0d19d69972675a90)：其他 result 的 `values()` 与仍有效的删除等待形态，验证不误改。

当前 33 个测试执行项包括 10 个可证明源版本逐项正例，以及两个处于压缩区间但精确值未知的负例、未列版本、目标/更新版本、范围、动态版本、BOM、共享属性、相邻 artifact、普通同名方法、安全配置、marker 精度、幂等和 recipe discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-kafka-clients-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2
```

应用 patch 后至少运行 Java 11+ 编译、producer/consumer/admin 集成测试、Testcontainers 或真实 broker 跨版本矩阵、TLS/SASL、rebalance、transaction/fencing、retry/idempotence、metrics/告警和滚动升级故障注入测试。

模块自检：

```bash
mvn -pl rewrite-kafka-clients-upgrade -am clean verify
```
