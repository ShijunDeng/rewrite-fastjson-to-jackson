# SLF4J 2.0.17 migration

本模块对应 `开源软件升级.xlsx` 中的 `org.slf4j:slf4j-api` 升级项。推荐配方不仅修改 API 版本，还迁移能够由官方资料证明为等价的 provider 坐标，并把必须由项目负责人判断的 Java、provider、桥接和打包风险标成 `SearchResult`。

```text
com.huawei.clouds.openrewrite.slf4j.MigrateSlf4jTo2_0_17
```

只需要严格升级 API、不需要兼容性扫描时，可以使用：

```text
com.huawei.clouds.openrewrite.slf4j.UpgradeSlf4jApiTo2_0_17
```

## 版本边界

目标版本固定为 `2.0.17`。配方只接受表格单元格中实际可见的十个源版本：

```text
1.7.25, 1.7.26, 1.7.30, 1.7.32, 1.7.34, 1.7.35, 1.7.36,
2.0.0, 2.0.0-alpha1, 2.0.6
```

表格在 `2.0.6` 后显示“共 11 个版本”，但没有给出其余版本值；本模块不会猜测隐藏值。因此，例如 `1.7.33`、`2.0.7` 和动态版本不会被静默升级，而会保留给人工确认。这个边界也避免通用的 `1.7.x`/`2.0.x` 选择器扩大本次升级范围。

## 处理规范

状态含义：`AUTO` 表示配方自动修改；`MARK` 表示保留源码并在准确位置生成 `SearchResult`；`NO-OP` 表示有意保持不变。

| 不兼容点或输入形态 | 状态 | 配方行为 | 测试 |
| --- | --- | --- | --- |
| Maven 直接、`dependencyManagement`、profile 中的十个精确 API 版本 | AUTO | 改为 `org.slf4j:slf4j-api:2.0.17` | 十版本参数化、managed dependency |
| Maven 只供 API 使用的本地属性 | AUTO | 将属性值改为 `2.0.17` | exclusive property |
| Maven 属性同时管理 API 与 provider/其他依赖 | AUTO | 只把 API 版本隔离成字面量 `2.0.17`，共享属性不变 | Apache ShardingSphere 形态 |
| Gradle Groovy/Kotlin DSL 的直接、可解析字面量依赖 | AUTO | 精确升级 API；不触碰注释、文档或任意字符串 | SDKMAN、BFT-SMaRt、map notation、Kotlin DSL |
| 外部 BOM 管理且当前 POM 没有本地版本 | NO-OP/MARK | API 不局部覆盖；显式 provider 若仍由外部管理则标记验证 | Spring Boot BOM、managed provider |
| `slf4j-simple`、`slf4j-nop`、`slf4j-jdk14`、`slf4j-reload4j` 与 API 同属精确源版本 | AUTO | 与已迁移 API 对齐到 `2.0.17` | SDKMAN、共享属性 provider |
| `slf4j-log4j12` 的精确源版本 | AUTO | 按 SLF4J 2.0.17 官方 relocation 改为 `slf4j-reload4j:2.0.17` | Maven、Gradle |
| `log4j-slf4j-impl` 且显式 Log4j 版本不低于 2.19 | AUTO | 只改 artifact 为 `log4j-slf4j2-impl`，保留版本、scope/classifier | Maven、Kotlin DSL |
| Log4j 低于 2.19、版本由外部管理或无法解析 | MARK | 不擅自升级整个 Log4j 栈；提示先确定兼容版本 | IONOS-Core/dim 2.17.1、managed provider |
| SLF4J 1.x binding、Logback 1.2、自定义或无法判定 provider | MARK | 标在依赖声明上，要求选择明确支持 SLF4J 2 的 provider | legacy provider、unresolved provider |
| 同时声明多个 provider | MARK | 标记每个显式 provider，要求最终依赖解析只保留一个 | multiple providers |
| `StaticLoggerBinder`/`StaticMDCBinder`/`StaticMarkerBinder` 的实现、访问或反射 | MARK | 指向 SLF4J 2 的 `SLF4JServiceProvider`/公共 API 迁移 | DBeaver binder、访问与反射 |
| 自定义 `SLF4JServiceProvider` | MARK | 要求核对 API version、factory、MDC、`initialize()` 与注册文件 | custom provider |
| `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` | NO-OP/MARK | 单个合法 provider 行保持不变；空、非法或多实现时标记 | 官方 simple descriptor、malformed/multiple |
| 旧 `META-INF/services/org.slf4j.spi.LoggerFactoryBinder` | MARK | 不猜 provider 实现类，要求改用新 SPI 的服务文件 | legacy service descriptor |
| Maven Shade 未配置 `ServicesResourceTransformer` | MARK | 提醒合并而不是覆盖 `META-INF/services` | 有/无 transformer |
| Java 编译基线低于 8 | MARK | 标在 Maven property 或 Gradle compatibility 值上 | Maven Java 7、Gradle Java 7 |
| 相反方向的 JUL、Log4j、JCL 或 reload4j bridge 同时存在 | MARK | 在构成环的依赖上提示只保留一个路由方向 | JUL、Log4j、JCL bridge loops |
| `logger.at*()` 结果作为语句丢弃且链上没有 `log()` | MARK | 提醒该语句不会发出日志事件 | discarded fluent chain |
| 以 `log()` 结束或保存后再使用的 fluent builder | NO-OP | 不改变调用意图 | terminal/stored builder |
| `System.setProperty("slf4j.provider", ...)` | MARK | 要求核对 provider 契约和 class-loader 可见性 | explicit provider property |
| 普通 `Logger`/`LoggerFactory` 客户端调用、相似 artifact、目标版本、非白名单版本 | NO-OP | 不做无证据的源码重写或越界升级 | no-op 与幂等用例 |

服务注册文件不能被配方凭空生成：自定义 binding 的真实 provider 类、factory、MDC 行为和 class-loader 边界属于业务意图。配方选择在原始风险点标记，而不是生成一个能够编译但运行语义错误的实现。

## 官方依据（固定提交）

分析固定在 SLF4J `v_2.0.17` 对应提交 [`c233ea1932228a7fc580823289f896e97ba8a74d`](https://github.com/qos-ch/slf4j/tree/c233ea1932228a7fc580823289f896e97ba8a74d)，而不是会漂移的默认分支：

- [`README.md`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/README.md) 声明 2.0 运行时最低为 Java 8。
- [`LoggerFactory.java`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-api/src/main/java/org/slf4j/LoggerFactory.java) 使用 `ServiceLoader` 查找 provider、忽略 1.7 binding，并报告多个 provider。
- [`SLF4JServiceProvider.java`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-api/src/main/java/org/slf4j/spi/SLF4JServiceProvider.java) 定义新 provider 契约。
- [`LoggingEventBuilder.java`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-api/src/main/java/org/slf4j/spi/LoggingEventBuilder.java) 明确要求链最终调用 `log()`。
- [`slf4j-simple` 服务描述符](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-simple/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider) 是合法 ServiceLoader 文件的基准。
- [`slf4j-log4j12/pom.xml`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-log4j12/pom.xml) 将 2.0.17 artifact relocation 到 `slf4j-reload4j`。

Log4j provider 坐标的判断固定在 Log4j 2.19.0 提交 [`5a5d3aefdc75045bb66f55a16c40a9a07a463738`](https://github.com/apache/logging-log4j2/tree/5a5d3aefdc75045bb66f55a16c40a9a07a463738)，该提交包含 [`log4j-slf4j2-impl`](https://github.com/apache/logging-log4j2/blob/5a5d3aefdc75045bb66f55a16c40a9a07a463738/log4j-slf4j2-impl/pom.xml)。

## 固定真实仓用例

测试输入提取为最小、可审查的 before/after 夹具，并记录来源提交，避免仓库后续变化破坏证据链：

| 仓库与固定提交 | 提取形态 | 本模块验证 |
| --- | --- | --- |
| [sdkman/sdkman-cli `1c8f4cb`](https://github.com/sdkman/sdkman-cli/tree/1c8f4cb9101a7cbc2da6453c12bd547531bde29f) | Gradle 中 `slf4j-api` + `slf4j-simple` 1.7.32 | API/provider 同步升级 |
| [bft-smart/library `681cec8`](https://github.com/bft-smart/library/tree/681cec8cf83e1cabe55f45c6edd3d80bd8ad156d) | Gradle `api("org.slf4j:slf4j-api:1.7.32")`、Java 8 | 依赖升级且基线不误报 |
| [apache/shardingsphere `1668c93`](https://github.com/apache/shardingsphere/tree/1668c9378b84b2ad8b27c7535daaf99cff120b34) | 一个属性同时管理 API、simple、JUL bridge | 隔离 API，不破坏共享属性 |
| [dbeaver/dbeaver `e0d43b9`](https://github.com/dbeaver/dbeaver/tree/e0d43b9ec3e725f635930cba9f1a92b8d7ad46bf) | 自定义 `org.slf4j.impl.StaticLoggerBinder` | 在 class 上精确标记旧 binding |
| [IONOS-Core/dim `2bbdd1f`](https://github.com/IONOS-Core/dim/tree/2bbdd1f74731f4400465ae548142a78871922152) | Log4j 2.17.1 的 `log4j-slf4j-impl` | 不越权升级 Log4j，生成风险标记 |

配方发现、依赖升级和 before/after 风格参考 OpenRewrite 官方固定测试 [`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。测试同时覆盖 marker、no-op 与再次运行无变化的边界，而不是只验证一个理想 POM。

## 使用

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-slf4j-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.slf4j.MigrateSlf4jTo2_0_17
```

检查生成的 patch 与全部 `~~>` 标记后，再将 `dryRun` 改为 `run`。升级后的应用至少应验证：Java 8+ 运行环境、解析后唯一 provider、启动阶段 MDC、最终 fat jar 的 service descriptor、桥接无环，以及每个日志级别确实输出。

## 模块验证

```bash
mvn -pl rewrite-slf4j-upgrade -am clean verify
```

当前 58 个测试覆盖精确十版本、Maven/Gradle/Kotlin、属性（包括嵌入其他元数据的共享引用）与 BOM、官方 provider relocation、真实仓库形态、Java 与 ServiceLoader marker、bridge 环、no-op 和配方发现。
