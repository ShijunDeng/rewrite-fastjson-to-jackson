# LMAX Disruptor 4.0.0 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `com.lmax:disruptor` 的全部明确升级记录。README 是不兼容点 spec；推荐配方会执行可证明安全的依赖与 Java AST 改写，并在无法仅凭语法确定并发语义的位置留下带原因的 `SearchResult`，不是只修改版本号。

推荐入口：

```text
com.huawei.clouds.openrewrite.disruptor.MigrateDisruptorTo4_0_0
```

只执行严格版本选择时使用：

```text
com.huawei.clouds.openrewrite.disruptor.UpgradeDisruptorTo4_0_0
```

`Migrate` 的第一项明确复用公开 `Upgrade`，之后才执行确定性 Java 迁移、源码风险审计和构建审计；单独运行 `Upgrade` 不会夹带源码变更。

## 工作簿边界

| 工作簿序号 | Excel 行 | 源版本 | 目标版本 |
| ---: | ---: | --- | --- |
| 1071 | 1072 | `3.4.2` | `4.0.0` |
| 1072 | 1073 | `3.4.4` | `4.0.0` |

底层配方的白名单严格等于 `{3.4.2, 3.4.4}`。`3.4.0`、`3.4.1`、`3.4.3`、`4.0.1` 等表外固定版本不会因为“看起来接近”而被猜测升级；变量、范围、动态版本、BOM/platform/version catalog、classifier/type/ext 也不会被覆盖。推荐配方会把真实但无法安全升级的声明标在准确所有者节点上。

目标制品已发布到 [Maven Central 4.0.0](https://repo1.maven.org/maven2/com/lmax/disruptor/4.0.0/)。源与目标 tag 分别固定到 `3.4.2@46f57d94`、`3.4.4@a87bf422` 和 `4.0.0@95c705f6`，后文链接均使用完整提交，不跟随分支漂移。

## AUTO / MARK / NO-OP

| 类别 | 实际行为 |
| --- | --- |
| **AUTO** | 精确升级工作簿两个源版本；把类型归属明确的三参数 `new BatchEventProcessor(...)` 改成 `new BatchEventProcessorBuilder().build(...)`；将 `SequenceReportingEventHandler` 换为 `EventHandler`；对已经实现 `EventHandler` 的类移除被并入它的 extension interface，并为旧 `onBatchStart(long)` 加第二个 `queueDepth` 参数 |
| **MARK** | WorkerPool/WorkProcessor/WorkHandler 及 worker-pool DSL、Executor 构造器、无法自动折叠的 handler extension、`onBatchStart` 语义、`RingBuffer.resetTo`、System.Logger 切换、`Util.log2` 输入、构建版本所有者/变体和 Java 11 以下基线 |
| **NO-OP** | 表外/目标版本、共享或外部版本所有者、BOM/versionless、非标准 artifact、嵌套 Gradle DSL、同名业务 API、不能证明是 EventHandler 的独立 extension、生成/安装/缓存目录均保持不变 |

AUTO 和 MARK 都有 two-cycle 测试：重复运行不会再次改变源码，也不会叠加相同 marker。

## 已自动处理的确定性 API 删除

### BatchEventProcessor 构造器

4.0.0 将 `BatchEventProcessor` 构造器降为包内实现，并公开 [`BatchEventProcessorBuilder`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/BatchEventProcessorBuilder.java)。本模块只处理类型归属到官方类、无匿名类体且恰好三个实参的旧构造：

```java
// before
return new BatchEventProcessor<>(provider, barrier, handler);

// after
return new BatchEventProcessorBuilder().build(provider, barrier, handler);
```

三个表达式原样保留且仍按一次、从左到右求值。未知 overload、同名业务类和已经使用 builder 的代码不猜测。`maxBatchSize`、rewind strategy 与 `RewindableEventHandler` 是新的业务选择，不由旧三参数语法推导。

### EventHandler 扩展接口合并

4.0.0 把 lifecycle、batch-start、sequence callback 和 timeout 回调作为 [`EventHandler`/`EventHandlerBase`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/EventHandler.java) 的默认能力。配方会：

- 将 `SequenceReportingEventHandler<T>` 替换为 `EventHandler<T>`；
- 仅当类已经是 `EventHandler` 时，从 implements 列表移除 `LifecycleAware`、`BatchStartAware`、`TimeoutHandler`；
- 仅对这种类中的旧 `onBatchStart(long oldValue)` 补成 `onBatchStart(long oldValue, long queueDepth)`，保留原方法体。

独立继承这些旧接口的抽象层没有足够信息决定新泛型事件类型，因此保持源码不变并 MARK，而不是制造一个错误的 `EventHandler<Object>`。

特别注意：旧单参数虽然名为 `batchSize`，实际传入的是 queue depth；新双参数才分别表示当前 batch size 和总 queue depth。AUTO 只恢复目标方法签名，不能保证原方法体继续使用第一个参数就符合业务意图，因此迁移后的双参数方法仍会 MARK。批量刷盘、阈值、延迟和 backlog 指标必须用生产语义重新判断。

## 必须人工设计的并发与运行时变更

### WorkerPool 拓扑删除

4.0.0 删除 `WorkerPool`、`WorkProcessor`、`WorkHandler` 和 `handleEventsWithWorkerPool`/`thenHandleEventsWithWorkerPool`。这不是可以机械换名的 API：worker pool 是竞争消费、每个事件由一个 worker 获取；普通 event-handler graph 可能广播给每个 handler，改变 exactly-once、顺序和吞吐合同。

配方会在真实类型和调用节点 MARK。人工替代方案至少要明确：

- 哪些消费者竞争、哪些广播，sequence/gating 如何连接；
- 单事件所有权、失败重试、异常处理和重复处理策略；
- halt/drain/shutdown 时是否丢事件，以及生产者 backpressure；
- handler 数量变化、长尾任务、公平性、线程亲和性和顺序保证。

迁移前后应记录消费 sequence，并用并发压力、失败注入、关闭中发布、ring wrap 和慢消费者场景验证，而不能只以“能够编译”作为完成条件。

### Executor 构造器删除

`Disruptor` 接受 `Executor` 的构造器被删除，目标要求 `ThreadFactory`。配方精确 MARK 第三个实参类型为 `Executor` 的官方构造调用，但不自动把共享线程池包装成 factory：旧 executor 可能控制池大小、排队、拒绝策略和所有权，而 factory 每次创建新线程。

人工迁移需确认线程名、daemon、priority/affinity、context classloader、uncaught exception、启动/关闭时机，以及旧 executor 是否仍由其他组件共享。已经使用 `ThreadFactory` 的构造是目标兼容形式，不 MARK。

### 其他删除和行为边界

- `RingBuffer.resetTo` 已删除。强行模拟 cursor 回退可能破坏 published slot、producer/consumer gating 和并发可见性；配方只 MARK。
- `FatalExceptionHandler` 与 `IgnoreExceptionHandler` 改用 JDK `System.Logger`。需要验证 JUL/System.Logger bridge、等级过滤、采集规则和 fatal 告警，不应假设旧日志后端仍能看到同一事件。
- `Util.log2` 对零或负数改为抛异常。所有配置/计算输入需先验证，并覆盖非法 ring/batch size。
- 目标新增 max batch size、batch rewind 与 rewind handler。旧程序没有一一对应的策略，模块不会擅自开启；如采用，需要回归部分批次副作用、重试上限、poison event 和 sequence 推进。
- `ConsumerRepository.getLastSequenceInChain` 等先前 deprecated API 已删除；构建后仍应以编译器和 binary linkage 检查兜底。

## 构建所有权与 Java 基线

Maven 支持当前 project 和直接 profile 中的 `dependencies`/`dependencyManagement`，以及只定义一次、所有引用都专属于标准 JAR `com.lmax:disruptor` version 的本地属性。profile 本地属性覆盖 root；root 属性对 profile 可见。属性只要还用于另一个依赖、build metadata、XML attribute 或任意文本，就保持不变并由推荐配方 MARK。plugin dependency 不是应用 dependency，不处理。

Gradle 支持根级真实 `dependencies {}` 中标准 configuration 的 Groovy/Kotlin 完整字符串坐标，以及 Groovy `group/name/version` Map 字面量。`buildscript`、`subprojects`、`allprojects`、`project(':x')`、`constraints`、自定义嵌套块、带 select 的调用、插值字符串和 catalog 均不自动改写。

路径隔离按父目录组件判断：`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.m2`、cache/report 和前端产物目录不做 AUTO/MARK；根目录名为 `install.java` 或 `install.gradle` 的叶文件仍会处理。

4.0.0 的官方 changelog 明确要求最低 Java 11。只在构建包含目标 Disruptor coordinate 且作用域可见时，配方才会 MARK `maven.compiler.release/source/target` 或 `java.version` 的明确 `1.8`、`8`、`9`、`10`；Java 11+ 不 MARK，外部属性则留给真实 owner。升级后还要检查 toolchain、CI runner、容器基础镜像、IDE、JPMS 和所有运行节点，不只看 Maven 启动所用 JDK。

## 官方固定依据

| 版本 | 固定源码 |
| --- | --- |
| `3.4.2` | [`46f57d94a188c2d9347e2aa0975e20332b0ae39a`](https://github.com/LMAX-Exchange/disruptor/tree/46f57d94a188c2d9347e2aa0975e20332b0ae39a) |
| `3.4.4` | [`a87bf422e42451b9e2d2d1f0f8de5b61ab561da2`](https://github.com/LMAX-Exchange/disruptor/tree/a87bf422e42451b9e2d2d1f0f8de5b61ab561da2) |
| `4.0.0` | [`95c705f60c1833b07f1fed6e08a08d7bee7f0971`](https://github.com/LMAX-Exchange/disruptor/tree/95c705f60c1833b07f1fed6e08a08d7bee7f0971) |

主要证据：

- 目标固定提交中的 [4.0.0 changelog](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/docs/asciidoc/en/changelog.adoc)，逐项列出 Java 11、worker pool、Executor、handler extension、logging、batch、`log2` 和 deprecated API 变化；
- [3.4.4→4.0.0 固定提交 diff](https://github.com/LMAX-Exchange/disruptor/compare/a87bf422e42451b9e2d2d1f0f8de5b61ab561da2...95c705f60c1833b07f1fed6e08a08d7bee7f0971)；
- 目标 [`dsl.Disruptor`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/dsl/Disruptor.java)、[`BatchEventProcessor`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/BatchEventProcessor.java)、[`EventHandlerBase`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/EventHandlerBase.java)、[`FatalExceptionHandler`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/FatalExceptionHandler.java) 和 [`Util`](https://github.com/LMAX-Exchange/disruptor/blob/95c705f60c1833b07f1fed6e08a08d7bee7f0971/src/main/java/com/lmax/disruptor/util/Util.java)。

## 真实仓库固定夹具

测试从公开仓库固定提交提取并缩减，只保留触发行为的真实 API 形态：

| 仓库固定提交 | 实际模式 | 本模块验证 |
| --- | --- | --- |
| [Alibaba Canal `e20e442`](https://github.com/alibaba/canal/blob/e20e4424468ef0ed60a97d921763b348eca27163/parse/src/main/java/com/alibaba/otter/canal/parse/inbound/mysql/MysqlMultiStageCoprocessor.java) | 三参数 `BatchEventProcessor`、`WorkerPool`/`WorkHandler`、`EventHandler + LifecycleAware` | builder before→after、extension AUTO、worker topology MARK |
| [Apache Myriad `9bd85f6`](https://github.com/apache/incubator-myriad/blob/9bd85f6d3c80cb7424c5886b872e2fe67d870bfa/myriad-scheduler/src/main/java/org/apache/myriad/DisruptorManager.java) | 共享 `ExecutorService` 传给多个 `Disruptor` 构造器 | Executor 构造精确 MARK、ThreadFactory 对照 NO-OP |
| [Meituan ptubes `3573788`](https://github.com/meituan/ptubes/blob/3573788403bb7fd32b6ec24ad847a5ad94545ccc/reader/src/main/java/com/meituan/ptubes/reader/producer/mysqlreplicator/component/BinlogPipeline.java) | ThreadFactory 目标构造与 worker-pool DSL 共存 | ThreadFactory 不误报、worker DSL MARK |
| [WSO2 Andes `596e601`](https://github.com/wso2/andes/blob/596e6012abf67459b0095317af840218c9a9df8e/modules/andes-core/broker/src/main/java/org/wso2/andes/kernel/disruptor/delivery/ConcurrentContentReadTaskBatchProcessor.java) | `SequenceReportingEventHandler` 与 `LifecycleAware` 扩展 | 可折叠/独立抽象的 AUTO 与 MARK 边界 |

测试结构参考 OpenRewrite 官方固定提交 [`rewrite-java-dependencies@decb8dbb` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，采用完整 before→after、严格 NO-OP、类型归属、带原因 marker、真实仓夹具和 two-cycle idempotency。

模块当前执行 **100 个 JUnit invocation**：54 个严格依赖/recipe discovery，9 个 Java AUTO，9 个源码 MARK，28 个构建 MARK。它们覆盖两源版本、Maven owner/profile/dependencyManagement、Gradle Groovy/Kotlin、真实仓库缩减夹具、同名负例、表外/动态/变体、Java 基线、路径隔离和 AUTO/MARK 幂等。

## 推荐验证顺序

1. 对推荐配方运行 dry-run，先审查依赖 patch、Java patch 与全部 `~~(...)~~>`。
2. 用 Maven dependency tree 或 Gradle dependency insight 确认最终只解析到 `com.lmax:disruptor:4.0.0`，修复真实 BOM/platform/catalog/parent owner。
3. 用 Java 11+ clean rebuild 所有直接实现/继承 Disruptor API 的模块，并执行 binary linkage 检查，避免旧内部 JAR 触发 `NoClassDefFoundError`/`NoSuchMethodError`。
4. 为 worker topology 建事件唯一性、顺序、背压、异常、halt/drain/shutdown 和 ring wrap 并发测试。
5. 为 batch-start、rewind、max batch、`resetTo` 替代和 `log2` 非法输入建立 golden/边界测试。
6. 在生产日志桥接、线程工厂、容器/JPMS/shading 和真实部署 JDK 中做启动、压力与回滚验证。

## 使用与模块验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-disruptor-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.disruptor.MigrateDisruptorTo4_0_0
```

审查完所有自动 patch 和 marker 后再将 `dryRun` 改为 `run`。模块自身不需要加入根聚合 POM即可验证：

```bash
mvn -f rewrite-disruptor-upgrade/pom.xml clean verify
```
