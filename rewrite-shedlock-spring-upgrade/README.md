# ShedLock Spring 7.2.1 migration

本模块迁移 `net.javacrumbs.shedlock:shedlock-spring`。推荐配方同时执行严格依赖升级、能够证明语义等价的注解迁移，以及构建、Spring AOP、provider 和运行时风险扫描：

```text
com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1
```

只修改依赖、不扫描兼容性时使用：

```text
com.huawei.clouds.openrewrite.shedlockspring.UpgradeShedLockSpringDependencyTo7_2_1
```

只迁移旧注解契约时使用：

```text
com.huawei.clouds.openrewrite.shedlockspring.MigrateLegacySchedulerLockAnnotations
```

## 精确版本边界

目标版本为 `7.2.1`。`开源软件升级.xlsx` 对该 artifact 实际给出且仅给出以下五个源版本：

```text
2.2.0, 4.29.0, 4.33.0, 4.41.0, 4.44.0
```

配方不使用 `2.x`、`4.x` 或 `latest.release` 之类范围，也不会猜测任何未列出的版本。因此 `4.5.0`、`4.42.0`、`6.3.1`、动态 Gradle 表达式、外部 BOM 管理以及已经为 `7.2.1`/更高版本的声明都不会被依赖配方改写。

## 不兼容处理规范

`AUTO` 表示有官方源码或旧实现证明语义等价，配方自动修改；`MARK` 表示保留业务意图并在准确 AST/配置位置生成 `SearchResult`；`NO-OP` 表示刻意保持不变。

| 迁移点 | 状态 | 行为 | 主要测试 |
| --- | --- | --- | --- |
| Maven direct、`dependencyManagement`、profile 中的五个精确版本 | AUTO | 只把 `shedlock-spring` 改为 `7.2.1` | 五版本参数化、managed/profile |
| 只供 Spring artifact 使用的本地 Maven property | AUTO | 将属性值改为 `7.2.1` | exclusive property |
| property 同时管理 Spring、core 或 provider | AUTO | 只把 Spring dependency 隔离为字面量 `7.2.1`，共享属性和其他 artifact 不动 | shared property |
| Maven 无版本、外部 BOM 管理 | NO-OP/MARK | 严格配方不局部覆盖；推荐配方提示核对实际解析版本 | ShedLock BOM fixture |
| Gradle Groovy/Kotlin 的直接安全字面量或 Groovy map | AUTO | 只改 dependency DSL 的精确坐标；注释、说明字符串和变量插值不动 | Spinnaker、map、Kotlin tooling |
| 未列出、目标或更高版本；相似 group/artifact | NO-OP | 不扩大表格范围、不降级、不误改 | Baeldung、sg-exam、strict no-op |
| `net.javacrumbs.shedlock.core.SchedulerLock` | AUTO | 改为 `net.javacrumbs.shedlock.spring.annotation.SchedulerLock` | EPAM、Toxiproxy |
| `lockAtMostForString`/`lockAtLeastForString` | AUTO | 改名为 7.2.1 的 `lockAtMostFor`/`lockAtLeastFor` | ISO、placeholder fixture |
| 旧注解的非负数字毫秒 literal | AUTO | 转成无单位字符串，例如 `120000` → `"120000"`；目标 converter 对无单位数字仍按毫秒处理 | numeric before→after |
| 旧注解同时设置数字和 String duration | AUTO | 按旧 extractor 的优先级保留数字；负数表示 fallback，改用对应 String 值 | precedence/fallback |
| 旧注解的数字常量或表达式 | MARK | 不猜常量值/单位，保留表达式并提示人工转成 duration String | non-literal marker |
| ShedLock 2.x `@EnableSchedulerLock(mode = InterceptMode.PROXY_*)` | AUTO | 属性名改为 `interceptMode`；Spring `AdviceMode mode` 保持不变 | legacy mode、AdviceMode no-op |
| Java < 17、Spring Framework < 6.2、Spring Boot < 3.4 | MARK | 标在 Maven/Gradle 的具体版本声明上 | Java/Spring/Boot fixtures |
| ShedLock artifact 与 core/provider/BOM 未对齐 | MARK | 不越权升级其他表格模块，提示统一到兼容的 `7.2.1` line | Maven/Gradle alignment |
| ShedLock 相关源码/构建仍使用 framework-facing `javax.*` | MARK | 提示按 Spring 6.2/7 平台迁移 Jakarta API | SIGLUS import、Maven/Gradle dependency |
| 旧 ShedLock Spring XML | MARK | 3.x 起已不支持，不自动生成可能改变 bean 生命周期的 Java config | XML marker |
| 默认或显式 `PROXY_METHOD` | MARK | 提示直接调用也加锁，并检查 proxyability、self-invocation 与 advisor order | default mode |
| `PROXY_SCHEDULER` | MARK | 7.2.1 已标记 for-removal，且 Spring 6.2 依赖反射 workaround | SIGLUS marker |
| `@SchedulerLock` 位于 private/static/final 方法或 final class | MARK | 标在方法边界；不擅自改变可见性、继承或 bean API | proxy boundary |
| 同类 self-invocation 调用被锁方法 | MARK | 标在调用点；要求通过代理协作者或重划锁边界 | self invocation |
| 多个 `@Bean LockProvider` 且没有 `@Primary` | MARK | 标记 provider bean；无 `@LockProviderToUse` 的任务也标记 | multiple providers |
| `@LockProviderToUse` | MARK | 核对 bean name 以及 method/type/package 继承路径；官方实现是在执行时解析 | provider selection |
| lock name/duration 中的 placeholder 或 SpEL | MARK | 核对参数名、bean access、解析失败、负值和唯一性 | dynamic name/duration |
| 显式 `lockAtMostFor` | MARK | 强调它是进程故障安全上限而非任务 timeout，必须大于最坏运行时长 | at-most marker |
| 显式 `lockAtLeastFor` | MARK | 核对 `atLeast <= atMost`、节点时钟和短任务 skip 语义 | at-least/invalid marker |
| `KeepAliveLockProvider` | MARK | 核对 30 秒下限、provider extension、续租线程、shutdown 和续租失败 | SIGLUS/constructor marker |
| reactive return 或锁内调用 `subscribe()` | MARK | 同步 proxy 可能只覆盖 publisher 创建/订阅发起，而非异步完成 | PagoPA fixture |
| `@Async`/`@Transactional`/`@Retryable` 与锁共用 join point | MARK | 核对 advisor 顺序、线程切换与异常传播 | multi-advice marker |
| virtual-thread executor/config | MARK | 核对实际工作完成边界，以及 `LockAssert`/`LockExtender` 的 ThreadLocal 生命周期 | Java/properties/YAML marker |
| primitive non-void 的锁方法 | MARK | 7.2.1 method proxy 明确拒绝该返回类型 | primitive return |
| 直接 `LockProvider.lock`、自定义 provider | MARK | 区分 empty（未取得锁）与 `LockException`（provider 异常），核对 retry/catch | provider/error marker |
| 手写 `LockConfiguration`、`SimpleLock.extend`、`LockExtender` | MARK | 检查新四参数构造、createdAt/clock、替换后的 lock 生命周期和一次性 unlock | manual API marker |
| `LockAssert.assertLocked()` | NO-OP | 官方仍推荐用于验证 proxy 确实持锁 | stable API no-op |

ShedLock 的任务竞争语义没有变成排队：锁已被其他节点持有时，当前执行会被跳过。配方不会重写调度表达式、锁名、数据库记录、provider 或业务重试，因为这些修改无法从静态源码证明等价。

## 固定官方依据

`shedlock-parent-7.2.1` 是 annotated tag；本模块固定使用其 peeled commit [`f79462aa33d864ca7e877dc9494dbb9b7ab05518`](https://github.com/lukas-krecan/ShedLock/tree/f79462aa33d864ca7e877dc9494dbb9b7ab05518)，避免默认分支漂移。

- [根 POM](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/pom.xml) 固定 JDK 17、Spring 7.0.1；[README compatibility table](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/README.md) 说明 7.x 支持 Java 17、Spring 7.0/6.2 和 Boot 4.x/3.5/3.4。
- [`EnableSchedulerLock`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/spring/shedlock-spring/src/main/java/net/javacrumbs/shedlock/spring/annotation/EnableSchedulerLock.java) 给出默认 `PROXY_METHOD`、`PROXY_SCHEDULER` for-removal、Spring `AdviceMode` 和 `order`。
- [`SchedulerLock`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/spring/shedlock-spring/src/main/java/net/javacrumbs/shedlock/spring/annotation/SchedulerLock.java) 的 duration 属性均为 String。
- [`StringToDurationConverter`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/spring/shedlock-spring/src/main/java/net/javacrumbs/shedlock/spring/aop/StringToDurationConverter.java) 证明无单位数字按毫秒处理，并列出简单单位和 ISO-8601 规则。
- [`MethodProxyScheduledLockAdvisor`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/spring/shedlock-spring/src/main/java/net/javacrumbs/shedlock/spring/aop/MethodProxyScheduledLockAdvisor.java) 是同步 method interceptor，并明确拒绝 primitive non-void return。
- [`BeanNameSelectingLockProviderSupplier`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/spring/shedlock-spring/src/main/java/net/javacrumbs/shedlock/spring/aop/BeanNameSelectingLockProviderSupplier.java) 证明多个 provider 的选择和运行时失败路径。
- [`KeepAliveLockProvider`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/shedlock-core/src/main/java/net/javacrumbs/shedlock/support/KeepAliveLockProvider.java)、[`LockConfiguration`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/shedlock-core/src/main/java/net/javacrumbs/shedlock/core/LockConfiguration.java) 和 [`RELEASES.md`](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/RELEASES.md) 支撑续租、时钟、构造器和 `LockException` 检查。

旧语义固定到 `2.2.0` commit [`49796926f3f637d6a0590fd36579a41e9d2e3bcb`](https://github.com/lukas-krecan/ShedLock/tree/49796926f3f637d6a0590fd36579a41e9d2e3bcb)：旧 [`SchedulerLock`](https://github.com/lukas-krecan/ShedLock/blob/49796926f3f637d6a0590fd36579a41e9d2e3bcb/shedlock-core/src/main/java/net/javacrumbs/shedlock/core/SchedulerLock.java) 明确数字单位为毫秒，旧 [`EnableSchedulerLock`](https://github.com/lukas-krecan/ShedLock/blob/49796926f3f637d6a0590fd36579a41e9d2e3bcb/spring/shedlock-spring/src/main/java/net/javacrumbs/shedlock/spring/annotation/EnableSchedulerLock.java) 的 `mode` 类型是 ShedLock `InterceptMode`。这两项是自动转换的证明边界。

reactive/virtual-thread 标记是依据上述同步 interceptor 与 ThreadLocal API 做出的保守推断，必须由应用集成测试确认；README 不把它描述成 ShedLock 官方承诺的异步事务边界。

## 固定真实仓用例

测试夹具只保留与迁移相关的最小形态，但每个来源都固定到不可漂移的 commit：

| 固定仓库 | 真实形态 | 期望 |
| --- | --- | --- |
| [spinnaker/spinnaker `ab45221c`](https://github.com/spinnaker/spinnaker/blob/ab45221c7fea10e567d009a63980f18a154118e5/orca/orca-sql/orca-sql.gradle) | Gradle parenthesized `shedlock-spring:4.44.0` | AUTO → `7.2.1` |
| [epam/cloud-pipeline `17daf5f6`](https://github.com/epam/cloud-pipeline/blob/17daf5f68ba893b067c6846a5dfaba93f8f964bc/api/src/main/java/com/epam/pipeline/manager/access/AccessCodeCleaner.java) | core package annotation、两个 `*ForString` | AUTO package/attribute |
| [buckle/toxiproxy-frontend `ddddc3a`](https://github.com/buckle/toxiproxy-frontend/blob/ddddc3a1552dba0c75c85c31ed68bb68070d0605/server/src/main/java/toxiproxy/backup/BackupChecker.java) | 旧注解与 placeholder duration | AUTO，值不改写 |
| [eugenp/tutorials `245cf4c3`](https://github.com/eugenp/tutorials/blob/245cf4c3d0f1b20b5e5748f6bbfe4d03c688f481/spring-boot-modules/spring-boot-libraries/pom.xml) | `6.3.1` shared Maven property | strict NO-OP |
| [wells2333/sg-exam `4a7215a`](https://github.com/wells2333/sg-exam/blob/4a7215ace7f56555bc683e4a4c0188f86986fd9f/sg-job/build.gradle) | Gradle `4.5.0` core/spring/provider | strict NO-OP |
| [SIGLUS/siglus-api `79268df`](https://github.com/SIGLUS/siglus-api/blob/79268df6c77c1d2dd9108b0522e14b53e75ac704/src/main/java/org/siglus/siglusapi/Application.java) | `PROXY_SCHEDULER`、`javax`；[configuration](https://github.com/SIGLUS/siglus-api/blob/79268df6c77c1d2dd9108b0522e14b53e75ac704/src/main/java/org/siglus/siglusapi/config/SchedulerConfiguration.java) 使用 KeepAlive | MARK |
| [pagopa/pn-address-manager `0ddf0d6`](https://github.com/pagopa/pn-address-manager/blob/0ddf0d65d3e831d05793fc9252d9a49b313695f2/src/main/java/it/pagopa/pn/address/manager/service/PendingRequestService.java) | 锁方法内启动 reactive `subscribe()` | MARK |

OpenRewrite 测试写法固定参考 [`UpgradeDependencyVersionTest` at `decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)、[`ChangeTypeTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java) 和 [`ChangeAnnotationAttributeNameTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeAnnotationAttributeNameTest.java)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-shedlock-spring-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1
```

处理全部 `~~>` 标记并确认 patch 后再改为 `run`。至少执行 Java 17 编译、Spring context/AOP、直接调用与 self-invocation、多 provider、SpEL/duration、任务 skip、长任务越过 at-most、异常、时钟偏差、KeepAlive shutdown、reactive/async/virtual thread 以及真实存储并发测试。

模块自身验证：

```bash
mvn -f rewrite-shedlock-spring-upgrade/pom.xml clean verify
```

当前 50 个测试覆盖五个精确版本、Maven/Gradle/Kotlin、共享属性（含 XML attribute 引用）与外部管理、官方确定性注解变化、7 个固定真实仓、marker/no-op、配方发现及重复 cycle 幂等检查。
