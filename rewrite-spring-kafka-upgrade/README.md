# Spring Kafka 2.8.11 / 2.9.5 → 3.3.15 自动迁移

本模块将 `org.springframework.kafka:spring-kafka` 从工作簿指定的
`2.8.11`、`2.9.5` 严格迁移到 `3.3.15`。它不是只修改版本号：
推荐配方会依次执行依赖升级、官方可确定 Java API 迁移，并在无法安全替业务
做决定的位置写入可检索的 `SearchResult` 标记。

推荐入口：

```text
com.huawei.clouds.openrewrite.springkafka.MigrateSpringKafkaTo3_3_15
```

## 自动化契约

| 输入 | 结果 |
|---|---|
| 固定版本 `2.8.11`、`2.9.5` | `AUTO`：改为 `3.3.15` |
| 已是 `3.3.15` | 不改、不标记 |
| 高于 `3.3.15` 的三段式固定版本 | 保留原文，并且只写入精确标记 `目标版本冲突（禁止降级）` |
| 其他固定版本 | 保留原文，标记“不在自动白名单” |
| 父 POM、BOM/platform、version catalog、动态表达式或不明确的共享属性 | 保留原文，标记真实版本 owner |
| classifier、非 JAR type、Gradle variant | 保留原文，标记制品变体边界 |

高版本比较使用无长度限制的十进制段，不会因为整数溢出而把
`999999999999999999999.0.0` 错判为低版本。限定符不参与 Maven 风格的
隐式猜测：配方只按 `major.minor.patch` 判断是否禁止降级。

### 可用配方

| 配方 | 类型 | 作用 |
|---|---|---|
| `UpgradeSpringKafkaTo3_3_15` | AUTO | 仅升级白名单版本及其可证明唯一归属的声明 |
| `MigrateDeterministicSpringKafka3Java` | AUTO | 组合官方 Spring Kafka 3.0 Java API 迁移能力 |
| `FindSpringKafka3_3_15BuildRisks` | MARK | 标记版本 owner、禁止降级、Java 与依赖族对齐问题 |
| `FindSpringKafka3_3_15SourceRisks` | MARK | 标记错误处理、Future、JSON、容器、事务、重试等语义边界 |
| `FindSpringKafka3_3_15ConfigurationRisks` | MARK | 分析 `.properties`、YAML 和 Spring XML 配置 |
| `MigrateSpringKafkaTo3_3_15` | AUTO + MARK | 按上述顺序执行全部能力 |

`AUTO` 表示变换具有确定的一对一目标；`MARK` 表示配方已经定位到真实语法树
节点，但正确结果取决于交付语义、部署方式或运行环境，必须由业务确认。

## 构建文件的所有权规则

Maven 自动修改范围：

- 根 `<project>` 或 `<profile>` 中的普通 `<dependencies>`；
- 根 `<project>` 或 `<profile>` 中的 `<dependencyManagement>`；
- 无 classifier 且 type 缺省或为 `jar` 的 `spring-kafka`；
- 只被目标依赖引用、只定义一次、未被 profile 遮蔽的版本属性。

Gradle 自动修改范围：

- 根 `dependencies {}` 中直接出现的 Groovy/Kotlin 字符串坐标；
- Groovy 的 `group/name/version` map 与 map literal；
- `api`、`implementation`、`runtimeOnly`、测试、fixture、`kapt`、`ksp`
  等标准依赖 configuration。

配方不会猜测 `buildscript`、嵌套 `subprojects`、constraint、插件依赖、
version catalog、platform/BOM、插值版本或变体坐标的真实 owner。生成目录
（例如 `target`、`build`、`generated`、`.gradle`）也不会被修改。

## 不兼容修改点与自动处理

### Java、Spring 与依赖基线

Spring Kafka `3.3.15` 的发布源码使用 Java 17 toolchain，并以以下版本构建：

| 依赖族 | 3.3.15 发布基线 | 配方行为 |
|---|---:|---|
| Spring Framework | `6.2.18` | MARK 旧 Spring/Boot 混装 |
| `kafka-clients` | `3.8.1` | MARK clients/streams/test/server 不一致 |
| Spring Retry | `2.0.12` | MARK 旧重试 API |
| Micrometer | `1.14.14` | MARK observation/core 不一致 |
| Micrometer Tracing | `1.4.13` | MARK tracing BOM 不一致 |
| Jackson BOM | `2.18.6` | MARK JSON 依赖族不一致 |
| `spring-kafka-test` | `3.3.15` | MARK 测试组件错位 |

Java 8～16 的 Maven compiler 属性、compiler plugin 配置及常见 Gradle
toolchain/source compatibility 表达式会被标记。模块不会擅自升级 Spring Boot
BOM 或 Java 镜像，因为它们通常是多模块工程的全局决策。

### 错误处理器

Spring Kafka 3.x 移除了旧 `ErrorHandler` / `BatchErrorHandler` 层次。
官方配方会自动执行：

- `SeekToCurrentErrorHandler` → `DefaultErrorHandler`；
- `setErrorHandler(..)` → `setCommonErrorHandler(..)`；
- `handle(..)` → `handleRemaining(..)`。

随后本模块标记旧/新 error handler、recoverer、BackOff、fatal 分类、
`setCommitRecovered` 以及 batch/record 路径。验收时必须检查
`seekAfterError`、`ackAfterHandle`、失败次数、恢复 offset 提交和
`BatchListenerFailedException` 行为。

### `CompletableFuture`

3.x 的 `KafkaTemplate`、`KafkaOperations` 与 reply API 使用
`CompletableFuture`。官方能力自动处理：

- send 返回类型由 `ListenableFuture` 改为 `CompletableFuture`；
- `addCallback` 改为 `whenComplete` 的成功/异常分支；
- `KafkaOperations2` 改为 `KafkaOperations`；
- 删除 `usingCompletableFuture()` 过渡调用。

本模块继续标记发送和 request/reply 边界，用于检查线程上下文、取消、超时、
异常传播、组合以及同步等待；“能够编译”不等于行为等价。

### Header 与测试工具

官方能力自动迁移四个被移除常量：

| 旧常量 | 新常量 |
|---|---|
| `KafkaHeaders.MESSAGE_KEY` | `KafkaHeaders.KEY` |
| `KafkaHeaders.PARTITION_ID` | `KafkaHeaders.PARTITION` |
| `KafkaHeaders.RECEIVED_MESSAGE_KEY` | `KafkaHeaders.RECEIVED_KEY` |
| `KafkaHeaders.RECEIVED_PARTITION_ID` | `KafkaHeaders.RECEIVED_PARTITION` |

`KafkaTestUtils` 的毫秒超时参数会自动包装为 `Duration.ofMillis(..)`。
`spring-kafka-test`、embedded broker、Kafka test/server 版本错位会被标记；
需要单独验证 KRaft/ZooKeeper、全局 broker、端口与测试生命周期。

### JSON SerDes 与 type mapper

配方对 `JsonSerializer`、`JsonDeserializer`、`JsonSerde`、
`ErrorHandlingDeserializer`、JSON message converter 和 Jackson type mapper
做类型归因标记，并识别以下高风险调用或配置：

- type header 的写入、保留、忽略与优先级；
- trusted packages、默认 key/value 类型和 type mapping；
- setter/fluent API 与 `configure(Map, boolean)` 混用；
- deserialization 失败值和 recoverer 路径。

`trusted.packages=*` 不会被静默“修好”，因为允许的业务类型集合无法从局部
代码可靠推断；配方会把它标在准确节点上，要求收窄信任边界。

### Listener 与容器

容器配置会影响 offset 和交付语义。配方会定位 ack mode、`asyncAcks`、
batch/record、并发、poll、pause/seek、授权失败重试、interceptor、停止时序、
container customizer，以及 `@KafkaListener` / `@KafkaHandler` 方法。

验收至少覆盖重复、乱序、长处理、rebalance、异常、手动 ack、reply/correlation
与 DLT。特别注意 `asyncAcks`、`nack` 和错误处理器 seek/retain 策略的组合。

### 事务与 EOS

Spring Kafka 3.0 起只支持 `EOSMode.V2`，事务 broker 最低为 2.5。
配方会标记：

- `EOSMode.ALPHA` / V1 和 EOS 配置；
- `ChainedKafkaTransactionManager`；
- `KafkaTransactionManager`、transaction manager 注入和本地事务；
- `transactional.id`、idempotence、acks、isolation、超时；
- `AfterRollbackProcessor` 与恢复提交。

多实例部署的 `transactionIdPrefix` 必须唯一。Kafka/数据库组合事务没有一个
通用且安全的自动替换，需按提交顺序、fencing、回滚恢复和幂等策略验收。

### Retry Topic 与 DLT

3.0 跨越 retry infrastructure/API、启动顺序和默认复制因子变化；非阻塞重试
也不能与容器事务组合。配方会标记 `@RetryableTopic`、`@DltHandler`、
`RetryTopicConfigurationSupport`、topic builder、DLT recoverer、
`DefaultAfterRollbackProcessor`、fatal 分类及相关配置。

验收需覆盖 topic 命名/复用、并发、header 保留、失败耗尽、DLT 自身失败、
复制因子和 ordering 变化。

### Micrometer Observation

3.x 可由 Micrometer Observation 同时生成 timer 与 tracing。配方会标记
observation/micrometer 的启用开关、Registry、Convention 和 tags。需要检查：

- batch listener 与 record listener 的度量差异；
- trace context 是否跨 producer/consumer 传播；
- 低/高基数标签、敏感数据和 cardinality 上限；
- dashboard、告警和采样策略是否连续。

### 扩展 SPI、原生 Kafka client 与配置文件

自定义 `ConsumerFactory`、`ProducerFactory`、listener/container factory、
converter、interceptor 等实现会被按可赋值类型标记，要求使用 3.3.15
重新编译并验证生命周期、泛型、线程安全和二进制签名。

配置分析基于结构化 OpenRewrite AST，不是对全文做盲目替换：

- `.properties` 与 YAML：JSON、container、transaction/EOS、retry/DLT、
  observation 和原生 client properties；
- Spring XML：Kafka class、error handler、container、converter、transaction
  和 observation 属性；
- `pom.xml` 由构建配方处理，不会被 XML 配置扫描重复标记。

## 官方能力复用审计

审计同时固定两个上游：

- `org.openrewrite.recipe:rewrite-spring:6.35.0` 的制品 manifest 对应
  [`rewrite-spring@d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)，
  JAR SHA-256 为
  `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b`。
- 本工程的 OpenRewrite Core `8.87.5` 源码制品固定到
  [`rewrite@b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，
  `rewrite-java-8.87.5-sources.jar` SHA-256 为
  `57ef3f3ce17fd0e5c3688d64a8260f8802a72dd6814cdb59a4b2b807023275f9`。

官方 Spring Kafka 3.0 清单见固定
[`spring-kafka-30.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-kafka-30.yml)；
命名实现见
[`kafka` 源码目录](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/kafka)，
固定 before/after 示例见
[`examples.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/examples.yml)。

### 已直接复用

| 官方能力 | 本模块的复用方式 |
|---|---|
| `KafkaOperationsSendReturnType` | 直接激活官方 class recipe；不复制 `ListenableToCompletableFuture` 访问器 |
| `KafkaTestUtilsDuration` | 直接激活官方 class recipe，覆盖 `getRecords`、`getSingleRecord`、`getOneRecord` 的 `Duration.ofMillis` 改写 |
| `RemoveUsingCompletableFuture` | 直接激活官方 class recipe；链式调用删除由官方 Core visitor 执行 |
| `UpgradeSpringKafka_2_8_ErrorHandlers` | 直接复用完整官方组合，其中两个 `ChangeMethodName` 和一个 `ChangeType` 的固定参数由运行时 recipe-tree 测试校验 |
| `KafkaOperations2` → `KafkaOperations` | 上游没有独立命名 recipe；本地声明与固定官方 YAML 相同参数的 Core [`ChangeType`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java) |
| 四个 `KafkaHeaders` 常量 | 上游没有独立命名 recipe；本地逐项声明固定官方映射，实际 AST 改写由 Core [`ReplaceConstantWithAnotherConstant`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ReplaceConstantWithAnotherConstant.java) 执行 |

模块内不存在与这些能力重复的自研 Java AST 变换；源代码迁移全部交给上述
官方 Spring/Core recipe。本地只提供 authored-source precondition，阻止官方组件改写
`target`、`build`、缓存、vendor 等生成/依赖树。运行时测试会将本地 9 个确定性子项与官方
`UpgradeSpringKafka_3_0` 的子项逐一比较，要求二者只相差下面唯一被拒绝的
宽泛依赖升级，包含顺序、类型名和四组常量参数都不能漂移。

### 明确拒绝或保留本地边界

| 官方能力 | 结论与原因 |
|---|---|
| `UpgradeSpringKafka_3_0` 整体聚合 | 不直接激活。其首个子项是 `UpgradeDependencyVersion(group=org.springframework.kafka, artifact=spring-kafka, newVersion=3.0.x)`；会扩大源版本并把精确 `3.3.15` 目标变成浮动 3.0 线 |
| Core `UpgradeDependencyVersion` | 不用于本模块版本变换；没有工作簿两项白名单、唯一属性 owner、variant、根 Gradle scope 和高版本禁止降级契约 |
| `UpgradeSpringKafka_4_0` | 方向超前；它把 3.x JSON 类型迁到 Spring Kafka 4 / Jackson 3 名称，目标 `3.3.15` 不适用 |
| `UpgradeSpringFramework_6_0`、`UpgradeSpringBoot_3_0` 等栈级聚合 | 会改 Spring Framework、Boot、Java 基线及其它依赖，超出单一 `spring-kafka` 工作簿项的授权范围 |
| Spring Boot 3.1/3.3/3.4 Kafka property recipes | 属性所有者是 Spring Boot 而非 Spring Kafka；本任务没有 Boot 源/目标版本，不能依据局部 Kafka 依赖擅自改键 |
| 精确版本、禁止降级、owner/variant | 官方能力不满足任务契约，继续由严格本地构建配方处理 |
| 3.3.15 依赖族、交付语义和配置风险 | 官方 3.0 聚合未覆盖，继续由类型归因和结构化 MARK 配方定位，不猜业务决策 |

测试还会真实激活 3.0、错误处理器和 4.0 官方组合，解开运行时 precondition
包装后核对实际 recipe tree，并证明本地推荐入口中递归不存在
`UpgradeDependencyVersion`、`UpgradeSpringKafka_3_0` 或 `UpgradeSpringKafka_4_0`。

## 目标版本证据

Spring Kafka `3.3.15` 固定提交为
[`5572a82b1a6c931d9a1656cadf8ba3df59102492`](https://github.com/spring-projects/spring-kafka/tree/5572a82b1a6c931d9a1656cadf8ba3df59102492)。

- [`build.gradle`](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/build.gradle)
  固定了 Java 17、Spring `6.2.18`、Kafka `3.8.1`、Spring Retry `2.0.12`、
  Micrometer `1.14.14` / Tracing `1.4.13` 与 Jackson BOM `2.18.6`；
- [`gradle.properties`](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/gradle.properties)
  固定项目版本为 `3.3.15`；
- [`3.0 change history`](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/spring-kafka-docs/src/main/antora/modules/ROOT/pages/appendix/change-history.adoc)
  记录 Kafka client、EOS、Observation、retry、Future 与 Header 变化；
- [`error handling`](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/spring-kafka-docs/src/main/antora/modules/ROOT/pages/kafka/annotation-error-handling.adoc)、
  [`exactly once`](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/spring-kafka-docs/src/main/antora/modules/ROOT/pages/kafka/exactly-once.adoc)
  与
  [`transactions`](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/spring-kafka-docs/src/main/antora/modules/ROOT/pages/kafka/transactions.adoc)
  是语义标记的主要依据。

Maven Central 的 `spring-kafka-3.3.15.pom` SHA-256 为
`381afe544b5a7b2b623959473119c621b2574d6d1716553b3b01326de95405f8`，
JAR SHA-256 为
`61ebbc527b88d7b4b9e9c88657f9937e7710d607f6279e1e9724d0ff72b3c948`。

源版本证据固定为：

- `v2.8.11`：`d3b3f85a3cf6571e4b2f4b64641e281050459ca7`；
- `v2.9.5`：`2598009c5f0686b92a1409fcd801e3acfad202d5`。

## 真实仓库用例

测试 fixture 不是凭空构造，均从固定 commit 提取后缩减为最小可复现代码：

| 仓库与 commit | 覆盖能力 |
|---|---|
| [`AlexeiZenin/sb-gp-testing@5b1edf2`](https://github.com/AlexeiZenin/sb-gp-testing/tree/5b1edf2dbaa45ce16058d11d04e10c5c284019ad) | `SeekToCurrentErrorHandler`、BackOff 与语义 MARK |
| [`kingkh1995/kk-ddd@7d8e1b8`](https://github.com/kingkh1995/kk-ddd/tree/7d8e1b8e9355daf3a8259d02abb048428337f176) | `KafkaOperations2`、`usingCompletableFuture()` |
| [`eugenp/tutorials@5e4114a`](https://github.com/eugenp/tutorials/tree/5e4114a9482d68b6766ca738c087f0f9a87a7bd2) | JSON/type mapper、`@RetryableTopic`、listener 与 DLT |
| [`dsyer/dist-tx@88bc07b`](https://github.com/dsyer/dist-tx/tree/88bc07b9c0f2a100d67fec2d283d883d908013fa) | chained transaction 与 rollback processor |

fixture 清单、原始文件位置和许可证提示见
[`src/test/resources/fixtures/real/README.md`](src/test/resources/fixtures/real/README.md)。

## 使用

先在本仓库安装模块：

```bash
mvn -pl rewrite-spring-kafka-upgrade -am install
```

在待迁移工程中激活推荐配方：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-kafka-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springkafka.MigrateSpringKafkaTo3_3_15
```

建议先执行 dry run 并把生成 patch 纳入代码评审。MARK 会渲染在准确节点附近，
例如 `/*~~(目标版本冲突（禁止降级）)~~>*/`；处理完成后再次运行对应风险配方，
直到剩余标记都已有明确的接受记录。

## 测试与验收

```bash
mvn -f rewrite-spring-kafka-upgrade/pom.xml test
```

当前测试共 96 项，覆盖：

- Maven、Groovy Gradle、Kotlin Gradle 的白名单、属性 owner、profile、
  dependency management、variant 和生成目录；
- 所有高版本不降级及精确冲突文案；
- 官方 3.0 安全子树等价、错误处理器精确叶子、4.0 排除、authored-source precondition，以及每个可复用迁移组件；
- 固定官方示例的 `getOneRecord(..., long)`、四个 Header 常量和 static import before/after；
- 构建、源码、properties、YAML、XML 的风险定位；
- 五组固定真实仓库 fixture；
- AUTO、MARK 与推荐聚合配方的双周期幂等性。

自动迁移后的最低业务验收集合：

1. Java 17 编译、单元测试和应用启动；
2. producer/consumer、JSON 正常与反序列化失败路径；
3. record/batch listener、ack、retry、DLT 和 rebalance 故障注入；
4. 事务提交、回滚、fencing、实例扩缩容和 broker 混合版本；
5. metrics、trace、dashboard 与告警连续性；
6. embedded/integration test 在目标依赖族上的重复运行稳定性。

该配方不会代替真实 broker 集成测试，也不会自动决定业务的 offset、
事务提交顺序、反序列化信任域或可观测性标签。这些位置被有意设计为
“精确定位 + 明确说明”的 MARK，而不是危险的静默改写。
