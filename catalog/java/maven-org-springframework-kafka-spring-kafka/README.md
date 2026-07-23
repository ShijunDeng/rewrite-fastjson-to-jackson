# org.springframework.kafka:spring-kafka / spring-kafka 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-spring-kafka-upgrade`](../../../rewrite-spring-kafka-upgrade)，
> 覆盖精确版本升级、官方 Java API 迁移、风险定位和禁止降级守卫。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-kafka-spring-kafka` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-kafka-spring-kafka` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.springframework.kafka:spring-kafka`<br>`spring-kafka` |
| Catalog canonical identity | `org.springframework.kafka:spring-kafka`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `3.3.15` |
| Excel 迁移边 | 4 |
| 涉及微服务数 | 最大可见值 `13`；不同版本行不累加 |
| 分桶 | `B4_Major单包` |
| 难度 | `中` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-spring-kafka-upgrade` |

## Excel 事实快照

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1458 | 1457 | `org.springframework.kafka:spring-kafka` | java | `2.8.11` | `3.3.15` | 13 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 1459 | 1458 | `org.springframework.kafka:spring-kafka` | java | `2.9.5` | `3.3.15` | 13 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 2240 | 2239 | `spring-kafka` | java | `2.8.11` | `3.3.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 2241 | 2240 | `spring-kafka` | java | `2.9.5` | `3.3.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |

## 升级方向与禁止降级

- AUTO 白名单仅包含 `2.8.11`、`2.9.5`，目标固定为 `3.3.15`。
- 已是目标版本时 NOOP；其他较低版本不猜测升级，标记为白名单外输入。
- `3.3.16+`、4.x 和未来主版本保持原文，并在真实版本 owner 上标记
  `目标版本冲突（禁止降级）`。
- Maven parent/BOM、Gradle platform/version catalog、动态表达式、classifier/variant
  和歧义共享属性保持不变并精确 MARK；不存在任何回退路径。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | Java / 依赖族 | Java 17；Spring 6.2、Kafka clients 3.8、Retry 2.0、Micrometer/Jackson/test 需要一致 | 严格依赖 AUTO；compiler、BOM/platform 和依赖族错位精确 MARK |
| C-002 | error handler | 旧 `ErrorHandler` / `BatchErrorHandler` 层次和方法被替代 | 直接复用官方 2.8 error-handler 配方；随后标记 seek、ack、recoverer、BackOff 与 batch/record 语义 |
| C-003 | Future / Header / 测试 API | send API 使用 `CompletableFuture`；四个 Header 常量移除；测试超时改为 `Duration` | 直接组合官方 Future、KafkaOperations2、Header 与 KafkaTestUtils recipes |
| C-004 | JSON SerDes | type header、trusted packages、默认类型和 mapper 配置影响反序列化信任边界 | 类型归因到具体调用/配置节点后 MARK，不擅自生成信任列表 |
| C-005 | listener / container | ack、batch/record、asyncAcks、pause/seek、rebalance 与停止时序影响交付语义 | 精确 MARK，要求重复/乱序/异常/rebalance 故障注入 |
| C-006 | transaction / EOS | 3.x 仅支持 EOS V2；chained transaction、fencing、rollback 与 transaction id 需要重审 | 精确 MARK，不自动决定跨资源提交顺序 |
| C-007 | retry / DLT / observation | retry topic、DLT、复制因子、启动顺序、指标与 trace 默认行为跨主版本变化 | 源码和 Properties/YAML/XML 结构化 MARK |
| C-008 | SPI / 原生 client | 自定义 factory、converter、interceptor 和 Kafka client 配置存在二进制及生命周期边界 | 可赋值类型与具体配置节点 MARK，要求在目标依赖族重新编译验证 |

`VERIFIED` 只覆盖固定源码、文档和制品支持的事实。broker 滚动升级、offset/EOS、
信任域、业务重试、DLT、指标 cardinality 和生产回滚仍属于业务验收。

### `java` 生态最低核查项

- 使用 Java 17 和统一的 Spring/Kafka/Retry/Micrometer/Jackson 依赖族重新编译。
- 覆盖 producer/consumer、record/batch、rebalance、JSON 失败、retry/DLT、事务与 fencing。
- 在真实 broker 或生产等价集成环境验证，不以 recipe 单元测试代替协议和交付语义测试。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | [Spring Kafka 3.3.15 commit](https://github.com/spring-projects/spring-kafka/tree/5572a82b1a6c931d9a1656cadf8ba3df59102492)；Maven Central JAR SHA-256 `61ebbc52...3c948`、POM SHA-256 `381afe54...405f8` |
| E-002 API/配置/行为 | `VERIFIED` | 固定提交下的 [change history](https://github.com/spring-projects/spring-kafka/blob/5572a82b1a6c931d9a1656cadf8ba3df59102492/spring-kafka-docs/src/main/antora/modules/ROOT/pages/appendix/change-history.adoc)、error handling、EOS 与 transaction 文档 |
| E-003 真实用法 | `VERIFIED` | AlexeiZenin/sb-gp-testing `5b1edf2`、kingkh1995/kk-ddd `7d8e1b8`、eugenp/tutorials `5e4114a`、dsyer/dist-tx `88bc07b` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | rewrite-spring `6.35.0` 固定提交 [`d28afcb`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)，JAR SHA-256 `27df4442...e98b` |

## 官方能力复用审计

- 已直接复用官方 `KafkaOperationsSendReturnType`、`KafkaTestUtilsDuration`、
  `RemoveUsingCompletableFuture`、`UpgradeSpringKafka_2_8_ErrorHandlers`。
- 已按官方参数复用 core `ChangeType` 和四次 `ReplaceConstantWithAnotherConstant`。
- 已审计官方
  [`UpgradeSpringKafka_3_0`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-kafka-30.yml)；
  其中宽泛 `UpgradeDependencyVersion` 会改变本任务的精确目标和白名单，因此只复用确定性
  Java 子配方，不组合官方宽泛依赖升级。
- 组合测试读取运行时 recipe tree，要求所有选定官方配方实际存在，并断言宽泛依赖配方未进入；
  另有 before/after 测试证明官方配方确实产生预期 AST 变换。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把精确 `2.8.11`、`2.9.5` 的明确 owner 改为 `3.3.15`。
- 只执行官方已验证的一对一 Java API 迁移，保留相邻结构并支持两周期幂等。
- 所有 visitor 忽略生成目录、构建输出、缓存和安装产物。

### MARK

- 在具体依赖、属性、调用、类型和配置节点标记 Java/依赖族、JSON、listener、
  transaction/EOS、retry/DLT、observation 与 SPI 风险。
- 高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- broker/client 滚动策略、offset 和交付语义、事务提交顺序、JSON 信任域、
  DLT 业务策略、指标标签和生产回滚由业务证据决定。
- 无法静态证明等价的配置与行为保持原样。

## 测试与真实用例验收

- 93 个测试覆盖 Maven、Gradle Groovy/Kotlin、property/profile/owner/variant 和生成目录。
- 覆盖两个白名单源版本、目标 NOOP、所有高版本禁降级与超大数字段比较。
- 覆盖官方 recipe tree、各官方组件 before/after、推荐组合顺序和两周期幂等。
- 覆盖 Java、Properties、YAML、Spring XML 风险以及五组固定真实仓库 fixture。
- 业务最低验收包括 Java 17 编译、真实 broker 集成、JSON 错误路径、rebalance、
  retry/DLT、transaction/fencing、metrics/trace 和回滚。

## 当前阶段结论

该模块的规格、证据和可执行实现均已完成。自动化限定在两个精确依赖版本和官方确定性
Java API 迁移；业务语义不确定的位置由配方精确定位，任何高版本都不会被降级。
