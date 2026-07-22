# ShedLock Spring 升级到 7.2.1

本模块对应 `开源软件升级.xlsx` 中的 `net.javacrumbs.shedlock:shedlock-spring`，覆盖 `2.2.0`、`4.29.0`、`4.33.0`、`4.41.0`、`4.44.0` 到 `7.2.1` 的升级。

仅升级构建声明的窄配方：

```text
com.huawei.clouds.openrewrite.shedlockspring.UpgradeShedLockSpringDependencyTo7_2_1
```

同时处理 ShedLock 2.x 注解包名和字符串 duration 属性的组合配方：

```text
com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1
```

## 自动处理范围

- 将 Maven/Gradle 中显式的 `net.javacrumbs.shedlock:shedlock-spring` 版本升级到 `7.2.1`，包括 Maven 属性与 dependencyManagement；不会降级高于目标的版本。
- 将 2.x 的 `net.javacrumbs.shedlock.core.SchedulerLock` 改为 `net.javacrumbs.shedlock.spring.annotation.SchedulerLock`。
- 将迁移后注解中的 `lockAtMostForString`、`lockAtLeastForString` 分别改为 `lockAtMostFor`、`lockAtLeastFor`。
- 不升级 `shedlock-core` 或任何 provider。表格为这些 artifact 分别安排了模块；同一应用必须最终把 Spring、core、provider、BOM 对齐到同一 ShedLock 版本。
- 不给 Spring Boot/BOM 管理的无版本依赖添加版本，不重写 lock table，不调整定时表达式、锁名或 duration 值。
- Gradle Kotlin DSL 在没有 Gradle Tooling Model 的单文件扫描中会安全保持不变；应在真实 Gradle 工程中执行 recipe。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Java/Spring 基线跨越三代 | 官方 POM 显示 4.44.0 为 JDK 8 + Spring 5.3，5.0.0 起为 JDK 17 + Spring 6，7.2.1 为 JDK 17 + Spring 7.0.1。目标通常意味着 Spring Framework 7 / Spring Boot 4；先统一 JDK、Spring、Jakarta、测试框架和容器基线 |
| `SchedulerLock` 从 core 移到 Spring annotation 包 | 组合配方会迁移 import；反射字符串、XML、文档片段和自建 annotation wrapper 仍需人工搜索 |
| 2.x 数字毫秒属性被字符串 duration 取代 | `lockAtMostForString`/`lockAtLeastForString` 可安全改名；但旧 `lockAtMostFor = 120000` 这类数值必须人工改为 `"120000"`、`"2m"` 或 `"PT2M"`。配方不会猜测单位或改写表达式 |
| `@EnableSchedulerLock.mode` 在 3.0 改义 | 2.x 的 `mode` 是 ShedLock `InterceptMode`，新版 `mode` 是 Spring `AdviceMode`，ShedLock 选择项改名为 `interceptMode`。检查旧 `PROXY_SCHEDULER` 配置，优先使用默认 `PROXY_METHOD` |
| `PROXY_SCHEDULER` 在目标版本标记 for-removal | 该模式依赖包装 TaskScheduler，并在 Spring 6.2+ 需要反射兼容；改为 method proxy 后，直接方法调用也会尝试加锁，AOP self-invocation/final/private 方法与 advisor 顺序要回归 |
| 6.0 支持多个 `LockProvider` | 有多个 provider bean 时，应在方法、类或 package 上用 `@LockProviderToUse("beanName")` 消歧；未选择时可能在任务执行时失败，而不是仅靠启动检查发现 |
| 锁不会等待 | 另一个节点持锁时，任务会被跳过而非排队。升级不能改变业务对此语义的依赖；为跳过、异常、长任务和重试补充指标与告警 |
| `lockAtMostFor` 是故障上限，不是任务超时 | 若任务超过该值，其他节点可能再次执行同一任务。按最坏运行时长设置，并验证节点时钟同步；不要用较小值代替超时/取消机制 |
| `lockAtLeastFor` 依赖时钟 | 它适合短任务限频，但节点时钟偏差可能造成重复。JDBC provider 优先评估 `usingDbTime()`，其他 provider 按官方能力验证 |
| `LockConfiguration` 构造器变化 | 2.x 使用 name + absolute `Instant`，目标使用 `createdAt, name, Duration, Duration`；手写 `LockProvider`、`LockingTaskExecutor` 与动态任务必须重新编译并明确创建时间 |
| `SimpleLock` 增加 extend 生命周期 | `extend` 返回新的 `Optional<SimpleLock>`，调用后旧 lock 不能再 unlock/extend；自定义 provider 和 wrapper 要实现或明确不支持 extension，并验证异常路径只释放一次 |
| `KeepAliveLockProvider` 有额外线程与下限 | 它在 `lockAtMostFor` 中点续租，最小支持 30 秒；调度器关闭、续租失败、网络分区和应用停机必须专项测试，不能把它当作无限租约 |
| duration 支持多种格式 | 注解可接受 `10m`、毫秒字符串和 ISO-8601，也可能包含 Spring placeholder/SpEL。统一格式并验证解析失败、负值、atLeast > atMost 与缺省值 |
| Spring AOP 边界仍然存在 | final/private 方法、同类自调用、非 Spring 实例、异常传播和 advisor 顺序都会影响加锁；在方法内使用 `LockAssert.assertLocked()` 可帮助测试代理确实生效 |
| JPMS/反射边界 | 7.x JAR 提供 module descriptor；module-path、native image、AOT 与强封装环境应检查 Spring AOP 反射和包开放配置 |

目标版本源码/POM依据：ShedLock 官方 [`shedlock-parent-7.2.1`](https://github.com/lukas-krecan/ShedLock/tree/shedlock-parent-7.2.1)、[7.2.1 parent POM](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/pom.xml)、[Spring module POM](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/spring/shedlock-spring/pom.xml)、[官方 README](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/README.md) 和 [RELEASES](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/RELEASES.md)。

## Provider 与存储验证

`shedlock-spring` 只提供 Spring 集成，锁的原子性由 provider 和存储决定。升级前盘点实际 provider，保持所有 ShedLock artifact 版本一致，并分别验证：

- JDBC 的 schema/table/column 大小写、时区、事务隔离、`usingDbTime()`、主键和数据库故障切换；不要手工删除已缓存的锁记录；
- Redis/Mongo/Elasticsearch 等 provider 的驱动大版本、序列化、TTL、主从切换和网络分区语义；
- 锁表数据从旧版本升级前后的可读性，任务运行中滚动发布时的新旧节点互操作；
- 应用 shutdown、任务异常、进程被杀、租约到期、延长失败和重复执行的幂等性。

配方不会修改数据库或锁记录，也不会自动添加 provider，这是为了避免把依赖升级变成不可逆的运行状态变更。

## 真实仓库测试来源

- [eugenp/tutorials](https://github.com/eugenp/tutorials/blob/245cf4c3d0f1b20b5e5748f6bbfe4d03c688f481/spring-boot-modules/spring-boot-libraries/pom.xml) 的 Maven `shedlock.version` 属性，同时被 spring 与 JDBC provider 使用；测试确认共享属性升级的实际影响。
- [wells2333/sg-exam](https://github.com/wells2333/sg-exam/blob/4a7215ace7f56555bc683e4a4c0188f86986fd9f/sg-job/build.gradle) 的 Groovy Gradle 字符串声明和 core/spring/provider 三件套。
- [spinnaker/spinnaker](https://github.com/spinnaker/spinnaker/blob/ab45221c7fea10e567d009a63980f18a154118e5/orca/orca-sql/orca-sql.gradle) 的 parenthesized Gradle 声明及 4.44.0 spring/provider 组合。
- [epam/cloud-pipeline](https://github.com/epam/cloud-pipeline/blob/17daf5f68ba893b067c6846a5dfaba93f8f964bc/api/src/main/java/com/epam/pipeline/manager/access/AccessCodeCleaner.java) 的旧 core 注解 import 与 `lockAtMostForString`，用于验证源码迁移。

测试风格参考 OpenRewrite 官方 `UpgradeDependencyVersionTest`、`ChangeTypeTest` 与 `ChangeAnnotationAttributeNameTest`。17 个测试覆盖表格全部版本、Maven direct/property/dependencyManagement、Groovy Gradle 两种写法、Kotlin DSL 无模型安全边界、旧注解包和两个 string 属性、目标/高版本防降级、相邻 artifact/相似坐标 no-op，以及两个 recipe 的 discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-shedlock-spring-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1
```

审核 patch 后，对齐 core/provider/BOM，迁移 Spring 与数值 duration，运行 JDK 17 编译、Spring context、AOP/self-invocation、多 provider、并发、长任务、失败恢复、滚动发布和真实存储集成测试。

模块自身验证：

```bash
mvn -f rewrite-shedlock-spring-upgrade/pom.xml clean verify
```
