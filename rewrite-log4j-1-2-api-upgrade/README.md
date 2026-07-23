# Apache Log4j 1.2 API Bridge 2.25.5 升级配方

本模块把工作簿明确列出的
`org.apache.logging.log4j:log4j-1.2-api`
六个源版本升级到 `2.25.5`。它不只修改版本号：对能够由固定官方配方证明安全的
`Logger.getLogger/getRootLogger + Logger.setLevel(Level)` 独立源码，提供直接复用
OpenRewrite 官方叶子的显式 opt-in；安全默认入口不会擅自选择或添加日志后端。其余配置、
后端 API、运行时 classpath 和版本 owner 问题在精确节点留下 `SearchResult`。

Maven group 与 Java package 均以 `com.huawei.clouds.openrewrite` 开头；模块 package 是
`com.huawei.clouds.openrewrite.log4j12api`。

## 工作簿边界

catalog 规格位于
`catalog/java/maven-org-apache-logging-log4j-log4j-1-2-api`。工作簿的 12 行由完整坐标和
artifact 简写各重复一组，形成六个唯一源版本：

```text
{2.13.2, 2.17.1, 2.17.2, 2.18.0, 2.19.0, 2.20.0} -> 2.25.5
```

| Excel 物理行 | 标识 | 源版本 | 目标版本 |
|---:|---|---|---|
| 2749 / 4867 | `org.apache.logging.log4j:log4j-1.2-api` / `log4j-1.2-api` | `2.13.2` | `2.25.5` |
| 2750 / 4868 | 同上 | `2.17.1` | `2.25.5` |
| 2751 / 4869 | 同上 | `2.17.2` | `2.25.5` |
| 2752 / 4870 | 同上 | `2.18.0` | `2.25.5` |
| 2753 / 4871 | 同上 | `2.19.0` | `2.25.5` |
| 2754 / 4872 | 同上 | `2.20.0` | `2.25.5` |

其他固定版本、范围、动态版本、BOM/platform、version catalog、parent、共享属性、
classifier 和非 JAR 变体不会被猜测式改写。任何高于 `2.25.5` 的固定版本保持原值，并在
真实声明上精确标记：

```text
目标版本冲突（禁止降级）
```

## 配方入口

- `com.huawei.clouds.openrewrite.log4j12api.UpgradeLog4j12ApiTo2_25_5`：
  只做六个源版本的严格依赖升级。
- `com.huawei.clouds.openrewrite.log4j12api.MigrateSafeLog4j12SetLevel`：
  只对完整 Log4j 1 使用面属于窄白名单的 authored Java 执行官方源码叶子；调用方必须先
  证明工程直接拥有并对齐 `log4j-core:2.25.5`。
- `com.huawei.clouds.openrewrite.log4j12api.MigrateLog4j12ApiTo2_25_5`：
  安全推荐入口，依次执行严格升级、构建 MARK、源码 MARK、配置 MARK，不引入 optional
  `log4j-core` API。
- `com.huawei.clouds.openrewrite.log4j12api.MigrateLog4j12ApiTo2_25_5WithOwnedCore`：
  仅在直接拥有 `log4j-core:2.25.5` 已由 dependency tree/lockfile 证明后使用；在推荐入口
  基础上执行窄官方源码 AUTO。

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.log4j12api.MigrateLog4j12ApiTo2_25_5
```

目标 `log4j-1.2-api` POM 把 `log4j-core` 声明为 optional，而官方
`LoggerSetLevelToConfiguratorRecipe` 会生成
`org.apache.logging.log4j.core.config.Configurator`。因此把该叶子放进无条件默认入口会
制造编译失败，或通过自动添加 Core 擅自改变 backend；本模块明确把这项决策留给 opt-in。

## 官方能力复用审计

审计固定到实际加载的制品及不可变提交，不跟随上游默认分支：

| 制品 | 固定版本 / commit | 本地验证 |
|---|---|---|
| OpenRewrite Core / Java | `8.87.5` / [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) | `rewrite-core` JAR SHA-256 `a7ff59eebc8072353ec5c3aee3e2033bc69a844b3c9ce2e9be8d4adaec10cbf8` |
| `rewrite-logging-frameworks` | `3.30.0` / [`c357a720`](https://github.com/openrewrite/rewrite-logging-frameworks/tree/c357a7209d721078dc942a777b1d8cc95941f722) | JAR SHA-256 `366a1cd43ee8e0f4378cac52036831df07d74d1648222d0664de2c63f7e26827` |
| `rewrite-apache` | `2.28.0` / [`b0424eb1`](https://github.com/openrewrite/rewrite-apache/tree/b0424eb13da62085a34a7e84a3987ac78227b70b) | JAR SHA-256 `1841723a57e3dad3a47777a311275f1d18fed8e197c99aa3526503e7c8a06d17`；运行时 catalog 无 Log4j 配方 |
| 目标 `log4j-1.2-api` | `2.25.5` / [`2e1d9c62`](https://github.com/apache/logging-log4j2/tree/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645) | 目标 JAR SHA-256 `4dd812dc5a6343f542a9e0046b1ec78ecf10bdd5a8c15745101cdd8b9aa24974` |

`rewrite-logging-frameworks:3.30.0` 和 `rewrite-apache:2.28.0` 的 manifest 声明
Moderne Source Available License；Core 和本项目是 Apache License 2.0。前者作为所选官方
class recipe 的运行时依赖，后者只以 test scope 审计 catalog。

### 接受并直接执行的官方叶子

本地没有复制这些变换，也没有添加“只返回官方 Recipe 列表”的纯 wrapper：

| 顺序 | 官方能力 | 固定参数 / 作用 |
|---:|---|---|
| 1 | Core [`ChangeMethodTargetToStatic`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeMethodTargetToStatic.java) | `org.apache.log4j.Logger getLogger(..)` → `org.apache.logging.log4j.LogManager` |
| 2 | 同上 | `org.apache.log4j.Logger getRootLogger()` → `org.apache.logging.log4j.LogManager` |
| 3 | [`LoggerSetLevelToConfiguratorRecipe`](https://github.com/openrewrite/rewrite-logging-frameworks/blob/c357a7209d721078dc942a777b1d8cc95941f722/src/main/java/org/openrewrite/java/logging/log4j/LoggerSetLevelToConfigurator.java) | `logger.setLevel(level)` → `Configurator.setLevel(logger, level)` |
| 4 | Core [`ChangeType`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java) | `org.apache.log4j.Logger` → `org.apache.logging.log4j.Logger`，`ignoreDefinition=true` |
| 5 | 同上 | `org.apache.log4j.Level` → `org.apache.logging.log4j.Level`，`ignoreDefinition=true` |

五个叶子只进入 `WithOwnedCore` opt-in，并受 `FindSafeLog4j12SetLevelSources` 源码前置
条件保护。只有非 generated Java，并且整个有类型归属的 Log4j 1 使用面仅包含 `Logger`、
`Level`、`getLogger/getRootLogger` 和 `setLevel` 时才执行。通配 import、同文件中的
`info(...)`、Appender、MDC、配置器或任一未知 Log4j 1 API 会令源码 AUTO 整体 NOOP，再由
MARK 暴露。源码白名单不替代构建证据：opt-in 前仍必须证明直接拥有 `log4j-core:2.25.5`。

### 明确拒绝的官方能力

固定的官方清单位于
[`log4j.yml`](https://github.com/openrewrite/rewrite-logging-frameworks/blob/c357a7209d721078dc942a777b1d8cc95941f722/src/main/resources/META-INF/rewrite/log4j.yml)；
运行时树测试同时激活官方、默认入口和 opt-in，证明以下能力没有泄漏到任何本地入口：

| 官方能力 | 拒绝原因 |
|---|---|
| `UpgradeLog4J2DependencyVersion` | `groupId=org.apache.logging.log4j`、`artifactId=*`、`newVersion=2.x`、`overrideManagedVersion=true`；突破单 artifact、六个源版本、精确目标和 owner/禁止降级边界 |
| `Log4j1ToLog4j2` 完整 aggregate | 添加 `log4j-api/core:2.x`、删除 Log4j/reload4j、改 SLF4J binding、执行通包 `ChangePackage`，并再次包含宽泛升级；这些跨依赖图决策不能由本模块代替业务批准 |
| aggregate 中未选择的 `ChangePackage`、`ParameterizedLogging`、Lombok 修改 | 会扩大到当前文件全部 Log4j 1 API、改变日志文本/性能或未覆盖的 backend 方法，不是本次 bridge patch 的无条件等价变换 |
| `InlineLog4jApiMethods` | 针对 `log4j-api` 的生成式 inline 迁移，不是 `log4j-1.2-api:2.25.5` 兼容修复 |
| Log4j→SLF4J/Logback、JUL/JCL/SLF4J→Log4j | 改变日志 facade/backend 和路由方向，不是同一 bridge 制品升级 |
| `rewrite-apache:2.28.0` catalog | 固定制品运行时 `recipes.csv` 没有 Log4j recipe，不能伪称复用 |

### 默认与 opt-in 的实际运行时树

```text
MigrateLog4j12ApiTo2_25_5
├── UpgradeLog4j12ApiTo2_25_5
│   └── UpgradeSelectedLog4j12ApiDependency              # 本地严格 owner/版本边界
├── FindLog4j12ApiBuildRisks
├── FindLog4j12ApiSourceRisks
└── FindLog4j12ApiConfigurationRisks

MigrateLog4j12ApiTo2_25_5WithOwnedCore
├── UpgradeLog4j12ApiTo2_25_5
│   └── UpgradeSelectedLog4j12ApiDependency              # 本地严格 owner/版本边界
├── MigrateSafeLog4j12SetLevel
│   ├── [precondition] FindSafeLog4j12SetLevelSources
│   ├── ChangeMethodTargetToStatic                       # 官方 Core recipe
│   ├── ChangeMethodTargetToStatic                       # 官方 Core recipe
│   ├── LoggerSetLevelToConfiguratorRecipe               # 官方 logging-frameworks
│   ├── ChangeType                                       # 官方 Core recipe
│   └── ChangeType                                       # 官方 Core recipe
├── FindLog4j12ApiBuildRisks
├── FindLog4j12ApiSourceRisks
└── FindLog4j12ApiConfigurationRisks
```

运行时审计还断言默认入口不含任何 Core 源码叶子，并且两个入口都没有
`UpgradeDependencyVersion`、`AddDependency`、`RemoveDependency`、`ChangeDependency`
或 `ChangePackage`。

## 自动处理（AUTO）

| 场景 | 自动操作 | 安全边界 |
|---|---|---|
| Maven root/profile 的直接标准依赖和 `dependencyManagement` | 六个精确源版本改为 `2.25.5` | 保留 scope、optional、exclusions；classifier/非 JAR 不改 |
| Maven 本地版本属性 | 属性定义唯一、引用全部只属于目标标准依赖时改为 `2.25.5` | 共享属性、profile shadow、parent/BOM owner 不猜 |
| 根 Gradle Groovy/Kotlin `dependencies {}` | 三段式字面量和 Groovy map 的精确源版本改为目标 | `subprojects`、constraint、platform、catalog、模板、variant 不改 |
| 独立 `getLogger/getRootLogger + setLevel` 源码 | 仅 `WithOwnedCore` opt-in 执行上面的五个官方叶子 | 必须先证明直接拥有并对齐 Core，再满足类型归属、完整使用面白名单、非 generated 和两轮幂等 |

生成、构建、缓存、安装和报告目录按 path component 跳过，包括 `target`、`build`、
`generated*`、`install*`、`.gradle`、`.m2`、`.cache`、`node_modules`、`coverage` 和
`reports`。

## 已处理的不兼容点（MARK / MANUAL）

### 构建与运行时

| 不兼容点 | 配方定位 | 业务处置 |
|---|---|---|
| 外部/动态 owner | versionless、变量、范围、动态、catalog/BOM/platform | 在真实 owner 改为精确 `2.25.5`，用 dependency tree/lockfile 证明解析结果 |
| 表外固定版本 | 版本声明 | 保持不变；为该版本建立独立迁移边 |
| 更高版本 | 版本声明 | 保持字节级原值并标记 `目标版本冲突（禁止降级）` |
| artifact 变体 | classifier/non-JAR 声明 | 核对目标是否发布相同 shape、模块名和 classloader 行为 |
| 重复 Log4j 1 实现 | `log4j:log4j`、reload4j、`log4j-over-slf4j` | 官方明确 bridge 替换相同 `org.apache.log4j` 类；最终 classpath 只能保留一种实现 |
| Log4j 家族偏斜 | `log4j-api/core/bom` 和其他 bridges | 统一批准的精确家族版本；`log4j-core` 在 bridge POM 中是 optional，但配置/后端能力需要明确实现 |
| 路由环和 provider | Log4j/SLF4J bridges | 只保留一个方向和一个 backend/provider；对容器共享库、fat JAR、插件 classloader 分别检查 |

### Java API

| 不兼容点 | 配方定位 | 业务处置 |
|---|---|---|
| `PropertyConfigurator` / `DOMConfigurator` / `BasicConfigurator` | 精确方法调用 | 自 2.24.0 起前两者只有启用 `log4j1.compatibility=true` 才执行；优先转换配置，否则验证启动、reload 和输入安全 |
| `setLevel/setPriority/setAdditivity/addAppender/...` | 精确 `Logger/Category` 调用 | 它们不是稳定 logging API；孤立 `setLevel` 只有在已拥有 Core 的 opt-in 中 AUTO，其余迁移到框架集成或 Log4j 2 Core |
| 自定义 Appender/Layout/Filter | class declaration | 2.17.2 起 bridge 只有有限支持；确认 support list，或重写为 Log4j 2 plugin 并验证 `Log4j2Plugins.dat` |
| repository/SPI/JMX/net/JDBC/renderer/varia | 精确类型/调用 | 选择 Log4j 2 等价能力；验证生命周期、权限、网络失败、JMX 边界和序列化 |
| MDC/NDC/LoggingEvent/ThrowableInformation | 精确类型/调用 | 验证线程继承与清理、非字符串值、location、stack trace 和持久化/wire format |
| `getLogger()` 源兼容 | 目标发布说明 | `2.25.5` 已修复 bridge 的 `getLogger()` source incompatibility；不发明本地改写，保留编译回归 |

### `log4j.properties` / `log4j.xml`

| 不兼容点 | 配方定位 | 业务处置 |
|---|---|---|
| compatibility mode | `log4j1.compatibility` 精确键 | 默认 `false`；决定是短期开启还是完成配置转换，不能由配方擅自打开 |
| Log4j 1 配置语法 | root/logger/category/additivity/appender 和 XML root | bridge 只做有限转换；逐项验证 threshold、rotation、async、filter、error handler、reload 和 secrets |
| 插值 | `${name}` 值/attribute | Log4j 2 应显式选择 `${sys:name}` 或其他获批 lookup；验证缺失值及不可信输入 |
| 自定义 class | appender/layout class 值或 XML `class` | 证明在 bridge support list，或迁移到带插件 metadata 的 Log4j 2 component |
| DTD/entity | XML doctype | 禁止外部 entity/网络解析；转换后在断网条件测试 |
| Pattern 差异 | 文档和验收约束 | `%p/%x/%X` 的兼容格式分别对应 `%v1Level/%ndc/%properties`；做逐字节日志 golden test |

## 分源版本兼容路径

- `2.13.2`、`2.17.1 → 2.25.5`：除 2.24 的配置默认行为外，还跨过
  2.17.2 的原生 Log4j 1 component 有限支持。原先无效的自定义配置可能在 compatibility
  mode 下开始生效，必须回归副作用与性能。
- `2.17.2、2.18.0、2.19.0、2.20.0 → 2.25.5`：已有有限 component bridge，
  重点是 2.24.0 起 bridge 默认不再修改 Core 配置，以及目标线的配置、family、
  serialization 和 classloader 行为。
- 所有路径：`2.25.5` 的 `getLogger()` 修复是目标实现事实，不需要猜测式源码 AUTO；
  bridge 仍是临时兼容层，官方建议最终迁移源码和配置后移除。

## 固定上游证据

源 tag 均解析到不可变 commit：

| 版本 | commit |
|---|---|
| `2.13.2` | [`994f94d9`](https://github.com/apache/logging-log4j2/tree/994f94d9336666f2fd1a9b73e8e3943ad3cd1350) |
| `2.17.1` | [`11dafda0`](https://github.com/apache/logging-log4j2/tree/11dafda0c43eb31cca67f3b0ed0ca9b81780db76) |
| `2.17.2` | [`eedc3cdb`](https://github.com/apache/logging-log4j2/tree/eedc3cdb6be6744071f8ae6dcfb37b26b1fc0940) |
| `2.18.0` | [`a3613864`](https://github.com/apache/logging-log4j2/tree/a3613864c8b39fc12588eaaeda0627741b7cc3bb) |
| `2.19.0` | [`5a5d3aef`](https://github.com/apache/logging-log4j2/tree/5a5d3aefdc75045bb66f55a16c40a9a07a463738) |
| `2.20.0` | [`44ab0131`](https://github.com/apache/logging-log4j2/tree/44ab0131718fc8d1fcb45604b0f1a8187765910d) |
| `2.25.5` | [`2e1d9c62`](https://github.com/apache/logging-log4j2/tree/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645) |

关键目标证据：

- [官方 Log4j 1 迁移指南（固定 target commit）](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/site/antora/modules/ROOT/pages/migrate-from-log4j1.adoc)：
  bridge 限制、2.17.2 component 支持、冲突 artifact、配置转换和最终移除 bridge。
- [`log4j1.compatibility` 固定定义](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/site/antora/modules/ROOT/partials/manual/systemproperties/properties-log4j-12-api.adoc)：
  默认 `false`，2.24.0 起控制 `PropertyConfigurator/DOMConfigurator`。
- [2.24.0 固定 release notes](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/changelog/2.24.0/.release-notes.adoc.ftl)：
  Log4j 1/JUL bridge 默认不再修改 Core 配置。
- [目标 `PropertyConfigurator` 源码](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/log4j-1.2-api/src/main/java/org/apache/log4j/PropertyConfigurator.java)、
  [目标模块 POM](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/log4j-1.2-api/pom.xml)。
- [2.25.5 官方 release notes](https://logging.apache.org/log4j/2.x/release-notes.html#release-notes-2-25-5)：
  固定目标修复 `log4j-1.2-api` 的 `getLogger()` source incompatibility。
- [Maven Central 目标 JAR](https://repo.maven.apache.org/maven2/org/apache/logging/log4j/log4j-1.2-api/2.25.5/log4j-1.2-api-2.25.5.jar)、
  [目标 POM](https://repo.maven.apache.org/maven2/org/apache/logging/log4j/log4j-1.2-api/2.25.5/log4j-1.2-api-2.25.5.pom)。

## 测试和真实仓库夹具

`mvn -f rewrite-log4j-1-2-api-upgrade/pom.xml clean verify` 执行 **63** 项测试，
覆盖六个 Maven/Groovy/Kotlin 源版本、owned property、profile/dependencyManagement、
target/outside/higher（含任意精度版本）、variant、catalog、generated、两轮幂等、官方运行时
树、默认入口不引入 Core API，以及 owned-Core opt-in 完整组合。

固定真实仓库/官方用例：

- `scenerygraphics/sciview@246aa7a6` 的真实 Kotlin DSL
  `log4j-1.2-api:2.20.0`：
  [`build.gradle.kts`](https://github.com/scenerygraphics/sciview/blob/246aa7a6ad71b148669a10b281fd82727d672457/build.gradle.kts#L88)；
  验证精确升级到 `2.25.5`。
- `apache/gobblin@fcfb06b4` 的真实 `PropertyConfigurator.configure(Properties)`：
  [`Log4jConfigHelper.java`](https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-aws/src/main/java/org/apache/gobblin/aws/Log4jConfigHelper.java#L54)；
  验证精确配置风险 MARK。
- `lttng/lttng-ust@e65b8914` 的注释中 configurator 文本：
  [`HelloLog4j.java`](https://github.com/lttng/lttng-ust/blob/e65b8914742a2b3aaf8d2fd3c24404b63062b1de/doc/examples/java-log4j/HelloLog4j.java#L78)；
  作为真实负例证明注释不产生 API marker。
- 官方 `rewrite-logging-frameworks@c357a720`
  [`Log4j1ToLog4j2Test.loggerSetLevel`](https://github.com/openrewrite/rewrite-logging-frameworks/blob/c357a7209d721078dc942a777b1d8cc95941f722/src/test/java/org/openrewrite/java/logging/log4j/Log4j1ToLog4j2Test.java#L143-L171)；
  原样验证所选 class recipe，再补 static target、generated、同名业务类、混合 surface 和两轮幂等。

## 验收清单

- 用 Maven/Gradle dependency tree、lockfile 和最终容器 classpath 证明
  `log4j-1.2-api/log4j-api/backend` 已按批准矩阵对齐，且没有重复 `org.apache.log4j`。
- 对启动、重配置、配置文件缺失/损坏/替换、并发日志和滚动回退做测试；明确记录
  `log4j1.compatibility` 的取值和设置时机。
- 对日志内容做 golden snapshot：level、MDC/NDC、location、Throwable、Pattern、
  charset、rotation 和自定义 component。
- 对 JMX、network/JMS/JDBC、配置 lookup、DTD/entity 和可写配置输入做安全测试。
- 对 fat JAR、JPMS/OSGi、应用服务器共享库、插件 classloader 和 native image 分别验证。
- 只有所有源码、依赖和配置都已迁移到维护中的 logging API/backend 后，才移除 bridge。
