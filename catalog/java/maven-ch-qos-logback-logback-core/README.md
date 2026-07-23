# ch.qos.logback:logback-core / logback-core 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-logback-core-upgrade`](../../../rewrite-logback-core-upgrade)，
> 覆盖精确依赖升级、官方 core/XML 配方复用和不兼容风险定位。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-ch-qos-logback-logback-core` |
| Maven artifactId | `migration-spec-java-maven-ch-qos-logback-logback-core` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `ch.qos.logback:logback-core`<br>`logback-core` |
| Catalog canonical identity | `ch.qos.logback:logback-core`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `1.5.34` |
| Excel 迁移边 | 4 |
| 涉及微服务数 | 最大可见值 `2`；不同版本行不累加 |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-logback-core-upgrade` |

## Excel 事实快照

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 4198 | 4197 | `ch.qos.logback:logback-core` | java | `1.2.5` | `1.5.34` | 2 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4199 | 4198 | `ch.qos.logback:logback-core` | java | `1.2.9` | `1.5.34` | 2 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4837 | 4836 | `logback-core` | java | `1.2.5` | `1.5.34` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4838 | 4837 | `logback-core` | java | `1.2.9` | `1.5.34` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |

## 升级方向与禁止降级

- AUTO 白名单严格等于 `1.2.5`、`1.2.9`，目标固定为 `1.5.34`。
- 已是目标时 NOOP；其他低版本不猜测升级。`1.5.35+`、1.6 和未来发布线保持原文，
  并在真实 owner 标记 `目标版本冲突（禁止降级）`。
- Maven/Gradle 的外部 parent/BOM/platform/catalog、动态/范围、共享或歧义属性、
  classifier/type/variant、插件依赖和生成目录都不做猜测式修改。
- 本目标是用户指定的固定版本，不代表自动选择最新安全版本；若安全基线要求更高版本，
  应由安全负责人变更目标并重新生成证据，不能由本配方越权漂移。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | Java / 依赖族 | 1.5.x 要求 Java 11、SLF4J 2；可选组件使用 Jakarta，classic/provider/bridge 需要一致 | 严格升级 core；Java、classic、SLF4J、bridge、Jakarta 和 Spring Boot owner 精确 MARK |
| C-002 | Java 重命名 | shutdown hook、size/time policy、Joran constants 和 watch-list URL API 有确定性替代 | 直接参数化复用官方 `ChangeType`×3、`ChangeMethodName`×2 |
| C-003 | XML 类名 | Logback XML 中两个旧类的 `class` 属性可确定性替换 | 删除手写 XML walker，直接复用官方 `ChangeTagAttribute`×2，并保留文件 owner/generated guard |
| C-004 | Joran | 1.3+ 改为两阶段 Model 管线，Action、Interpreter、RuleStore、implicit action 等扩展无一一对应迁移 | 在具体类型、继承和调用上 MARK，要求在目标 SPI 重写和行为回归 |
| C-005 | Groovy / DB | Groovy/Gaffer 配置因安全原因移除，内置 DB/JDBC 代码分离到独立 release line | 配置、系统属性、DB 类型与依赖 MARK；不把任意 Groovy 程序翻译成 XML |
| C-006 | rolling | Instant/ZoneId、保留策略、压缩、重启 index、prudent mode 和文件碰撞可能变化 | 确定性类名 AUTO；其余精确 MARK，使用真实文件系统/时钟测试 |
| C-007 | scan/JNDI/XML | WatchList、扫描、远程 include、JNDI、外部实体和 escape 涉及配置供应链与解析安全 | 具体 XML/属性/API MARK；不自动放宽协议、namespace 或实体策略 |
| C-008 | receiver / serialization | receiver/server-socket 被移除，object stream 与 proxy 反序列化有安全边界 | 类型和 XML MARK；替代传输、allow-list、鉴权限流与旧 peer 兼容人工处理 |
| C-009 | lifecycle / packaging | Context lock/event、listener、shutdown、servlet、JPMS/OSGi/MR-JAR、shade/native 与 optional module 变化 | 源码/构建配置 MARK，要求并发、redeploy、资源排空和打包验收 |
| C-010 | 条件表达式 | Janino condition 在指定目标仍存在后续安全修复与弃用演进 | 所有 `<if condition>` / evaluator 精确 MARK；优先迁到受控条件，不擅自改写业务表达式 |

`VERIFIED` 表示固定源码、release news 和制品支持上述事实；日志级别、保留周期、条件
策略、DB sink、远程传输、部署生命周期和安全风险接受仍由业务决定。

### `java` 生态最低核查项

- 使用 Java 11+ 重新编译并统一 `logback-core`、`logback-classic`、SLF4J API/provider/
  bridge 与 Spring Boot BOM 的实际 owner。
- 覆盖 Joran 自定义扩展、scan/reconfigure、rolling 边界、压缩/磁盘异常、listener/
  shutdown、receiver 替代和配置安全。
- 验证 JPMS、OSGi、multi-release、shade/native、可选模块和容器 redeploy。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | Logback `1.5.34` commit [`e62272ac`](https://github.com/qos-ch/logback/tree/e62272ac152469aec1ede056c3c7d0d7314e7bfe)；Maven Central JAR SHA-256 `42eda264...caae`、POM SHA-256 `5230a2f3...2f95` |
| E-002 API/配置/行为 | `VERIFIED` | [官方 release news](https://logback.qos.ch/news.html)、[rolling manual](https://logback.qos.ch/manual/appenders.html#SizeAndTimeBasedRollingPolicy) 与固定目标源码 |
| E-003 真实用法 | `VERIFIED` | Apache Shenyu `ea4f1770`、Twitter the-algorithm `c54bec0d`、OpenTSDB `0f681b75` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | OpenRewrite core/XML `8.87.5` 固定提交 [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)；logging frameworks `3.30.0` 固定提交 [`c357a720`](https://github.com/openrewrite/rewrite-logging-frameworks/tree/c357a7209d721078dc942a777b1d8cc95941f722) |

真实仓库只证明用法存在；兼容性结论由 Logback 固定源码、文档和目标制品支持。

## 官方能力复用审计

- Java AUTO 实际复用官方 core `ChangeType` 三次和 `ChangeMethodName` 两次；XML AUTO
  实际复用 `ChangeTagAttribute` 两次，已删除重复的自研 XML visitor。
- 组合测试断言七个 delegate 的真实 class、关键参数、generated 路径排除、执行顺序和
  两周期幂等，不接受只在 YAML/README 名义声明复用。
- 官方 `Log4jToLogback` 可被测试 classpath 激活，但方向是 Log4j→Logback，并会使用
  `latest.release` 添加 companion、移除 Log4j，违反精确 `1.5.34`、owner 和“不改
  companion”边界，因此不进入生产组合。
- `ConfigureLoggerLevel` 与 logging best practices 会改变业务日志级别、内容或策略，
  保持独立 opt-in；官方当前没有 Logback 1.2→1.5 专用 aggregate。
- 通用依赖升级无法表达两个离散源版本、profile shadow、variant 与禁止降级 marker，
  因此严格 owner/版本守卫仍为本地缺口。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把 `1.2.5`、`1.2.9` 的明确本地 owner 改为 `1.5.34`。
- 只执行七个有固定证据的一一对应官方 core/XML 变换，保留相邻结构并支持幂等。
- 所有 visitor 跳过 target/build/generated/cache/install/vendor 等路径。

### MARK

- 在具体依赖、属性、类型、方法、XML/配置和构建节点标记 Java/依赖族、Joran、DB、
  rolling、scan/JNDI、receiver/serialization、lifecycle、条件安全和 packaging 风险。
- 高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- 日志级别和内容、配置供应链、DB/远程 sink、rolling 保留策略、条件表达式、安全风险
  接受、容器生命周期与回滚由业务证据决定；无法证明等价的语义保持原样。

## 测试与真实用例验收

- 125 个测试覆盖两个白名单版本、Maven/Gradle owner/profile/property/catalog/platform/
  variant、目标 NOOP、表外低版本、所有高版本禁止降级和生成目录。
- 覆盖七个官方 delegate 的 runtime class/参数和实际 AST/XML 变换、官方 logging
  aggregate 激活但不误纳、本地推荐组合顺序和两周期幂等。
- 覆盖 Joran、DB、rolling、watch list、Context、status、receiver、serialization、
  Groovy/JNDI/condition/scan、lifecycle 与 JPMS/OSGi，并使用三组固定真实仓库形态。
- 业务最低门禁包括 Java 11 编译、依赖族一致性、文件系统/时钟、配置安全、容器部署、
  并发/性能、安全扫描和回滚。

## 当前阶段结论

该模块的规格、固定证据和可执行实现均已完成。通用类型、方法和 XML 修改尽可能直接
复用官方能力；官方没有 Logback 1.2→1.5 专用配方的部分才由本地严格守卫与风险定位补齐。
