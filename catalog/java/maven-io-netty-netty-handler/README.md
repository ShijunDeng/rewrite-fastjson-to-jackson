# io.netty:netty-handler / netty-handler 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-netty-handler-upgrade`](../../../rewrite-netty-handler-upgrade)，
> 覆盖严格依赖白名单、可证明等价的 API 修改、精确风险定位和禁止降级守卫。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-io-netty-netty-handler` |
| Maven artifactId | `migration-spec-java-maven-io-netty-netty-handler` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `io.netty:netty-handler`<br>`netty-handler` |
| Catalog canonical identity | `io.netty:netty-handler`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `4.1.136.Final` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `4`；不同版本行不累加 |
| 分桶 | `B1_Patch直升` |
| 难度 | `低` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-netty-handler-upgrade` |

## Excel 事实快照

本节逐字保留工作簿事实。Excel #3775 是截断聚合文本，不能作为配方输入；用户随后提供的
22 个原子版本通过机器清单中的 `U-001`、`U-002` 单独记账。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 3766 | 3765 | `io.netty:netty-handler` | java | `4.1.100.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3767 | 3766 | `io.netty:netty-handler` | java | `4.1.101.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3768 | 3767 | `io.netty:netty-handler` | java | `4.1.107.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3769 | 3768 | `io.netty:netty-handler` | java | `4.1.108.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3770 | 3769 | `io.netty:netty-handler` | java | `4.1.109.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3771 | 3770 | `io.netty:netty-handler` | java | `4.1.115.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3772 | 3771 | `io.netty:netty-handler` | java | `4.1.118.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3773 | 3772 | `io.netty:netty-handler` | java | `4.1.119.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3774 | 3773 | `io.netty:netty-handler` | java | `4.1.121.Final` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3775 | 3774 | `io.netty:netty-handler` | java | `4.1.124.Final ... (共22个版本)` | `4.1.136.Final` | 4 | B1_Patch直升 | 低 | unknown/mark | 仅patch变更，无breaking change |
| 4843 | 4842 | `netty-handler` | java | `4.1.100.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4844 | 4843 | `netty-handler` | java | `4.1.101.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4845 | 4844 | `netty-handler` | java | `4.1.107.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4846 | 4845 | `netty-handler` | java | `4.1.108.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4847 | 4846 | `netty-handler` | java | `4.1.109.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4848 | 4847 | `netty-handler` | java | `4.1.115.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4849 | 4848 | `netty-handler` | java | `4.1.118.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4850 | 4849 | `netty-handler` | java | `4.1.119.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4851 | 4850 | `netty-handler` | java | `4.1.121.Final` | `4.1.136.Final` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |

## 升级方向与禁止降级

- AUTO 精确白名单：`4.1.49.Final`、`4.1.63.Final`、`4.1.100.Final`、
  `4.1.101.Final`、`4.1.107.Final`、`4.1.108.Final`、`4.1.109.Final`、
  `4.1.115.Final`、`4.1.118.Final`、`4.1.119.Final`、`4.1.121.Final`、
  `4.1.124.Final`～`4.1.130.Final`、`4.1.132.Final`、`4.1.133.Final`。
- Excel #3775 的聚合文本始终保持 MARK；配方不会从“共22个版本”猜测任何输入。
- 用户清单中的 `4.2.1.Final`、`4.2.10.Final` 高于 4.1 目标线，保持原文并精确标记
  `目标版本冲突（禁止降级）`；不存在 `4.2 → 4.1` 回退动作。
- `4.1.137+`、未来主版本、动态版本、范围、外部 parent/BOM、歧义 property owner
  都不改写；只有当前文件可证明拥有且精确命中白名单的版本才会 AUTO。

## 不兼容点规格

| ID | 维度 | 已验证事实 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | 依赖 owner / Netty 家族对齐 | handler 与 common、buffer、transport、codec 共享二进制契约；BOM、property、profile 与 Gradle platform 可能是真实 owner | 严格 Maven/Gradle/BOM 白名单 AUTO；歧义 owner、版本偏斜、classifier、插件依赖与表外版本精确 MARK |
| C-002 | `SslHandler.isEncrypted` | 旧一参数实现等价委托二参数重载并使用 `false` | 直接组合官方 `AddLiteralMethodArgument`；typed before/after、生成目录负例与两周期幂等测试 |
| C-003 | `RuleBasedIpFilter` | deprecated constructor 等价委托 `this(true, rules)` | 小型 typed visitor 显式增加 `true`；数组、expanded varargs、求值顺序和业务同名类均有测试 |
| C-004 | TLS / SNI / ALPN / trust | provider、协议、cipher、hostname、session、ClientHello、pinning 和 native engine 依赖业务安全策略 | 在具体调用与配置节点 MARK；不伪造 CA、fingerprint、protocol 或 provider 决策 |
| C-005 | pipeline / timeout / flow / resource | handler 顺序、异步事件、backpressure、ByteBuf 引用计数和 close/remove 路径具有运行时语义 | pipeline、idle/write timeout、traffic shaping、flow control、proxy、PCAP、chunked write 等精确 MARK |
| C-006 | 4.2-only API | 4.2 输入可能使用 4.1.136 不存在的 API | 保持源码与依赖不变并标记 `目标版本冲突（禁止降级）`，要求选择新的向前目标 |

`VERIFIED` 只覆盖实现 README 中固定证据支持的事实。TLS 策略、生产镜像 native ABI、
协议互通、性能容量和业务回滚仍然是 MARK/MANUAL，而不是静态配方可以宣布完成的事项。

### `java` 生态最低核查项

- 核对最终 dependency tree / dependency insight，保证所有 Netty 4.1 companion 对齐。
- 在生产等价 JDK、OpenSSL/Conscrypt、CPU/OS classifier 和 shaded 包中验证服务加载。
- 覆盖 SNI/ALPN、startTLS、session resumption、timeout、pipeline removal 和 ByteBuf leak。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | [目标 tag/commit](https://github.com/netty/netty/tree/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6)；Maven Central JAR SHA-256 `54bb1a59...da28`、POM SHA-256 `584c16cb...f0e26e` |
| E-002 API/行为变化 | `VERIFIED` | Netty 固定提交：[`isEncrypted`](https://github.com/netty/netty/commit/dc30c3337a)、[`RuleBasedIpFilter`](https://github.com/netty/netty/commit/74b2fcf85724fc955fd4562ff3ef0bb703afa287) 以及实现 README 中 TLS/SNI/flow 修复证据 |
| E-003 真实用法 | `VERIFIED` | Apache Flume `579b77c`、Dubbo `eb1d8ab`、RocketMQ `577b89f` 固定提交的裁剪 fixture |
| E-004 官方能力复用 | `VERIFIED` | OpenRewrite Core [`b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 和 rewrite-netty [`9546824`](https://github.com/openrewrite/rewrite-netty/tree/9546824604ff662eb73bd4cabdd9a9d54bc0ae63) |

## 官方能力复用审计

- 已直接复用官方
  [`org.openrewrite.java.AddLiteralMethodArgument`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/AddLiteralMethodArgument.java)
  完成 `SslHandler.isEncrypted(buf) → isEncrypted(buf, false)`。
- 已审计官方 `UpgradeDependencyVersion` / `ChangeDependency`：它们不能同时表达 20 个精确输入、
  root/profile owner 隔离、独占属性、仅本地 BOM 和所有高版本精确 MARK，因此依赖边界保留严格 visitor。
- 已审计官方
  [`UpgradeNetty_4_1_to_4_2`](https://github.com/openrewrite/rewrite-netty/blob/9546824604ff662eb73bd4cabdd9a9d54bc0ae63/src/main/resources/META-INF/rewrite/netty-4_1_to_4_2.yml)；
  其目标是统一升级整个 `io.netty:*` 家族到 4.2，与本任务固定 4.1.136 的目标冲突，故不组合。
- 推荐 YAML 的组合测试读取运行时 recipe tree，证明官方参数配方确实被激活，并与自研 constructor
  visitor 同时生效；不是仅在 README 中提及官方能力。

## 后续 OpenRewrite 配方契约

### AUTO

- 只升级机器清单的 20 个原子版本，保留 scope、classifier/type、optional、exclusions 和相邻内容。
- 只执行上述两项有固定上游源码证明、上下文无歧义且幂等的 API 变换。
- 所有 visitor 忽略生成目录、构建输出、缓存和安装产物。

### MARK

- 在真实依赖 owner、调用、配置键和资源节点标记未决事项。
- 高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。
- MARK 是待办定位，不代表业务兼容性已经通过。

### MANUAL

- TLS 安全策略、证书/pinning、native ABI、协议互通、资源生命周期、性能容量和回滚由业务证据决定。
- 无法由静态上下文证明等价的行为保持原样。

## 测试与真实用例验收

- 模块有 248 个测试执行实例，覆盖 20 个源版本的 Maven/Gradle AUTO 和目标版本 NOOP。
- 覆盖 Maven property/profile/BOM、Gradle Groovy/Kotlin platform、歧义 owner、动态值和表外版本。
- 覆盖 `4.2.1`、`4.2.10`、`4.1.137+`、4.3/5.x 禁降级，且验证依赖保持原文。
- 覆盖官方与自研配方组合顺序、typed AST、真实 classpath、生成目录负例和两周期幂等。
- 覆盖 Flume、Dubbo、RocketMQ 固定提交用例以及 TLS/SNI/ALPN/native/pipeline/timeout 风险节点。

## 当前阶段结论

该模块的规格、证据和可执行实现均已完成。自动化范围严格限定在 20 个低版本和两项可证明等价
的 API 修改；所有更高版本保持不变，剩余不兼容风险由配方精确定位并交给业务验收。
