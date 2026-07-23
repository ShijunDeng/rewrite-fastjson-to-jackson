# Log4j 1.2 API Bridge 2.25.5 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 实现模块：
> [`rewrite-log4j-1-2-api-upgrade`](../../../rewrite-log4j-1-2-api-upgrade)。

推荐的安全默认配方：

```text
com.huawei.clouds.openrewrite.log4j12api.MigrateLog4j12ApiTo2_25_5
```

它将六个明确源版本严格升级到 `2.25.5`，同时在真实 build、Java 与配置节点标记不能
静态证明等价的迁移决策。它不会选择日志 backend，也不会自动添加 `log4j-core`。

## 模块身份

| 字段 | 值 |
| --- | --- |
| 规范坐标 | `org.apache.logging.log4j:log4j-1.2-api` |
| groupId | `com.huawei.clouds.openrewrite` |
| 目标版本 | `2.25.5` |
| AUTO 白名单 | `2.13.2`, `2.17.1`, `2.17.2`, `2.18.0`, `2.19.0`, `2.20.0` |
| Excel 物理行 | `2749`～`2754`, `4867`～`4872` |
| 实现模块 | `rewrite-log4j-1-2-api-upgrade` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |

工作簿用完整坐标和 artifact 简写各记录一次，因此 12 个物理行形成 6 条唯一升级边。
它们均已进入用户高优先级精确白名单。表外低版本、动态值、范围、BOM/platform、
version catalog、parent、共享属性、classifier 和非 JAR 变体不会被猜测式改写。

目标身份固定到 Apache Log4j
[`2e1d9c6284af1da1dec189f4b5b98ac0f32a7645`](https://github.com/apache/logging-log4j2/tree/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645)：

- JAR SHA-256：
  `4dd812dc5a6343f542a9e0046b1ec78ecf10bdd5a8c15745101cdd8b9aa24974`
- POM SHA-256：
  `709b4faa962253f2a673412b40d244c8f2c23c00e2b93e40ed366f5b9557c461`

## Excel 事实快照

下表保留工作簿的 12 个物理行；AUTO 是用户最新高优先级指令和已验证实现得出的当前动作，
不改写 Excel 原文。

| Excel 行 | 软件标识 | 源版本 | 目标版本 | 当前动作 |
| ---: | --- | --- | --- | --- |
| 2749 | `org.apache.logging.log4j:log4j-1.2-api` | `2.13.2` | `2.25.5` | AUTO + MARK |
| 2750 | `org.apache.logging.log4j:log4j-1.2-api` | `2.17.1` | `2.25.5` | AUTO + MARK |
| 2751 | `org.apache.logging.log4j:log4j-1.2-api` | `2.17.2` | `2.25.5` | AUTO + MARK |
| 2752 | `org.apache.logging.log4j:log4j-1.2-api` | `2.18.0` | `2.25.5` | AUTO + MARK |
| 2753 | `org.apache.logging.log4j:log4j-1.2-api` | `2.19.0` | `2.25.5` | AUTO + MARK |
| 2754 | `org.apache.logging.log4j:log4j-1.2-api` | `2.20.0` | `2.25.5` | AUTO + MARK |
| 4867 | `log4j-1.2-api` | `2.13.2` | `2.25.5` | AUTO + MARK |
| 4868 | `log4j-1.2-api` | `2.17.1` | `2.25.5` | AUTO + MARK |
| 4869 | `log4j-1.2-api` | `2.17.2` | `2.25.5` | AUTO + MARK |
| 4870 | `log4j-1.2-api` | `2.18.0` | `2.25.5` | AUTO + MARK |
| 4871 | `log4j-1.2-api` | `2.19.0` | `2.25.5` | AUTO + MARK |
| 4872 | `log4j-1.2-api` | `2.20.0` | `2.25.5` | AUTO + MARK |

## 升级方向与禁止降级

| 输入 | 自动行为 |
| --- | --- |
| 六个白名单版本且版本由当前文件唯一拥有 | 升级到 `2.25.5` |
| `2.25.5` | NOOP |
| 高于目标的固定版本 | 保持原值并精确标记 `目标版本冲突（禁止降级）` |
| 表外固定低版本 | 保持原值并标记“需要独立批准的迁移边” |
| 外部、共享、动态或无法证明的 owner | 保持原值并标记真实 owner |
| classified、非 JAR 或 Gradle variant | 保持制品形状并 MARK |

Maven 覆盖 root/profile 的直接依赖、`dependencyManagement` 和可证明唯一拥有的本地属性；
Gradle 只覆盖根 Groovy/Kotlin `dependencies {}` 中的安全字面量或 map 声明。生成目录、
缓存、安装产物和报告目录保持不变。

## 不兼容点规格

安全默认入口依次执行：

1. 严格升级明确拥有的白名单依赖；
2. 定位外部 owner、变体、重复 Log4j 1 实现、bridge 环和 Log4j 家族偏斜；
3. 定位 programmatic configuration、Appender/Layout/Filter、repository/SPI、JMX、
   MDC/NDC、序列化和 backend API；
4. 定位 `log4j.properties` / `log4j.xml` 的 compatibility mode、插值、自定义 class、
   reload、DTD/entity 和 pattern 风险。

精确 MARK 是配方实际输出，不只是 README 提示。默认入口不执行可能引入新 backend 的
源码修改。

## 官方 OpenRewrite 能力复用审计

审计固定到 OpenRewrite Core `8.87.5` commit
[`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)
和 `rewrite-logging-frameworks:3.30.0` commit
[`c357a720`](https://github.com/openrewrite/rewrite-logging-frameworks/tree/c357a7209d721078dc942a777b1d8cc95941f722)。

在工程已经直接拥有并对齐 `log4j-core:2.25.5` 时，可显式启用：

```text
com.huawei.clouds.openrewrite.log4j12api.MigrateLog4j12ApiTo2_25_5WithOwnedCore
```

该 opt-in 直接组合官方 `ChangeMethodTargetToStatic`、
`LoggerSetLevelToConfiguratorRecipe` 和 `ChangeType`，只处理使用面被证明局限于
`Logger`、`Level`、`getLogger/getRootLogger` 与 `setLevel` 的 authored Java。

不能把这组叶子放进无条件默认入口：目标 bridge POM 将 `log4j-core` 声明为 optional，
而官方 `setLevel` 叶子会生成 Core 的 `Configurator`。默认执行会造成编译失败；自动添加
Core 又会擅自选择 backend。

以下官方 aggregate 已经过运行时树审计并明确排除：

| 官方能力 | 排除原因 |
| --- | --- |
| `UpgradeLog4J2DependencyVersion` | 使用 `org.apache.logging.log4j:* → 2.x` 且覆盖 managed version，突破单 artifact、精确目标和 owner 边界 |
| `Log4j1ToLog4j2` | 添加/删除多个依赖、改变 binding/backend、通包改写和参数化日志，超出 bridge patch 授权 |
| Log4j→SLF4J/Logback 或反向 bridge aggregate | 改变 facade、路由方向和 provider，不是同一制品升级 |

测试递归解析实际 recipe tree，证明默认入口不含 Core 源码叶子，opt-in 只含接受的五个
官方叶子，两个入口均不含宽泛 dependency/package aggregate。

## 证据台账

| Claim | 状态 | 固定证据 |
| --- | --- | --- |
| 坐标、六个源版本与目标制品身份 | VERIFIED | 七个不可变 Apache Log4j tag commit、目标 JAR/POM SHA-256 |
| 配置、bridge、API 和默认行为变化 | VERIFIED | 固定目标迁移指南、2.24.0 release notes、目标源码与 POM |
| OpenRewrite 可复用与拒绝边界 | VERIFIED | Core `b3008cc4`、logging-frameworks `c357a720`、实际 recipe tree 测试 |
| 真实正例、风险用法和注释负例 | VERIFIED | sciview、Gobblin、LTTng 固定 commit fixture |

## 需要业务验收的不兼容点

- `PropertyConfigurator` / `DOMConfigurator`：2.24.0 起只有
  `log4j1.compatibility=true` 时才修改 Core 配置；优先转换配置，并验证 reload 与不可信输入。
- Appender/Layout/Filter：bridge 只支持有限 Log4j 1 component；自定义实现可能需要改写为
  Log4j 2 plugin，并验证 `Log4j2Plugins.dat`。
- `log4j:log4j`、reload4j、`log4j-over-slf4j`：都会提供相同 `org.apache.log4j` 类，
  最终 classpath 只能保留一个实现。
- `log4j-api/core/bom` 与其他 bridge：必须按批准的精确版本对齐，并验证 provider、
  classloader、shade 和容器共享库。
- MDC/NDC、`LoggingEvent`、JMX、network/JMS/JDBC、renderer 和 repository SPI：
  需要验证线程上下文、权限、失败路径、序列化和 wire format。
- 配置插值、DTD/entity、pattern、rotation、async、filter、error handler 和 secrets：
  需要配置 golden test、断网解析测试和安全审计。

固定迁移指南：
[`migrate-from-log4j1`](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/site/antora/modules/ROOT/pages/migrate-from-log4j1.adoc)；
固定 2.24.0 行为证据：
[`release notes`](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/changelog/2.24.0/.release-notes.adoc.ftl)。

## 后续 OpenRewrite 配方契约

### AUTO

- 只迁移六个精确白名单版本和当前文件明确拥有的标准 Maven/Gradle 声明；
- 保留 scope、optional、exclusions、相邻配置和 artifact shape；
- 仅在直接拥有 Core 已被证明时启用官方源码 opt-in；
- 每个变换必须通过 before/after、类型归属、真实 fixture 和两周期幂等测试。

### MARK

- 高版本原样保留并使用精确 `目标版本冲突（禁止降级）`；
- 表外版本、外部 owner、BOM/platform/catalog、变体、家族偏斜和 bridge 环定位到真实节点；
- programmatic configuration、backend API 与配置风险不能只停留在 README。

### MANUAL

- backend/provider 选择、bridge 拓扑、classloader、shade、容器共享库和滚动部署由业务决定；
- 日志文本、MDC/NDC、序列化、网络/JMS/JDBC、JMX、性能容量和安全行为需运行时验收；
- 无法静态证明语义等价的操作保持原样。

## 测试与真实用例验收

模块 `clean verify` 执行 63 项测试，覆盖六个版本、Maven/Groovy/Kotlin owner、任意精度
禁止降级、生成目录、变体、配置、Java 类型归属、两周期幂等、默认与 opt-in 运行时树。

固定夹具包括：

- `scenerygraphics/sciview@246aa7a6ad71b148669a10b281fd82727d672457` 的真实 Kotlin DSL；
- `apache/gobblin@fcfb06b41d041cb797622264cf5322296753fdea` 的
  `PropertyConfigurator.configure(Properties)`；
- `lttng/lttng-ust@e65b8914742a2b3aaf8d2fd3c24404b63062b1de` 的注释负例；
- 官方 `rewrite-logging-frameworks@c357a720` 的 `loggerSetLevel` 测试形态。

真实仓库只证明用法存在；AUTO 的语义边界仍由固定上游源码、制品和 OpenRewrite 运行时树
共同约束。
