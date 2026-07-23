# Spring Retry 1.3.4 → 2.0.13

本模块把 `org.springframework.retry:spring-retry` **精确从 1.3.4 升级到 2.0.13**。它不是只改版本号的占位模块：推荐配方会先用官方 OpenRewrite 配方升级 Java 构建基线，再升级依赖、自动迁移确定性 API，最后把不能在缺少业务证据时安全决定的源码和构建边界标到具体位置。

主配方：

```text
com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13
```

## 严格版本策略

| 输入版本 | 动作 |
|---|---|
| `1.3.4` | 自动升级为 `2.0.13` |
| `2.0.13` | 保持不变 |
| `2.0.14+`、`2.1+`、`3+` | 保持原文本，并精确标记 `目标版本冲突（禁止降级）` |
| 其他固定低版本，例如 `1.3.3`、`2.0.0`、`2.0.12` | 不猜测跨版本路径，保持原文本并标记不在白名单 |
| 父 POM、BOM、platform、version catalog、动态表达式、范围、缺失版本 | 不抢夺 owner，标记真实版本所有者 |
| classifier、非 JAR type、Gradle variant | 不改变制品形状，标记人工确认 |

配方只修改项目直接拥有的标准声明：

- Maven 根项目/直接 profile 的 `dependencies` 和 `dependencyManagement`；
- Maven 仅被目标依赖引用、定义唯一且没有 profile 遮蔽的版本属性；
- 根 Gradle `dependencies` 中的字符串坐标、Groovy map/map literal 和 Kotlin 字符串坐标；
- 不处理 `buildscript`、`subprojects`、`allprojects`、嵌套 `project`、constraints、自定义 DSL、生成目录或缓存目录。

因此不会出现从比 `2.0.13` 更高的版本向下改写，也不会把表格以外的 Spring Retry 版本顺带升级。

## 推荐配方实际执行顺序

1. `UpgradeSpringRetryBuildToJava17`
   - 先由本模块的精确 precondition 找出本地解析为 `1.3.4` 的构建文件；
   - 然后实际执行官方 `org.openrewrite.java.migrate.UpgradeJavaVersion(version: 17)`。
2. `UpgradeSpringRetryTo2_0_13`
   - 执行严格白名单依赖升级。
3. `MigrateDeterministicSpringRetry20Java`
   - 执行 6 个官方注解属性改名和 1 个官方方法改名。
4. `FindSpringRetry2_0_13BuildRisks`
   - 标记版本 owner、禁止降级、Java/Spring/Micrometer/AOP/打包风险。
5. `FindSpringRetry2_0SourceRisks`
   - 标记二进制断点与必须用业务测试确认的行为边界。

Java 17 必须在依赖升级之前执行，因为 precondition 只接受精确 `1.3.4`；测试专门证明了官方 Java 配方真的修改 Maven 和 Gradle 构建，而不只是出现在 YAML 中。

## 自动迁移能力

### 1. Java 构建基线

Spring Retry 2.0.13 的 class major version 是 `61`，即 Java 17。推荐配方对命中精确 `1.3.4` 的构建复用官方：

```yaml
org.openrewrite.java.migrate.UpgradeJavaVersion:
  version: 17
```

未命中 `1.3.4` 的构建不会因为本模块被宽泛地改到 Java 17。

### 2. 依赖版本

自动处理：

```xml
<dependency>
  <groupId>org.springframework.retry</groupId>
  <artifactId>spring-retry</artifactId>
  <version>1.3.4</version>
</dependency>
```

变为：

```xml
<dependency>
  <groupId>org.springframework.retry</groupId>
  <artifactId>spring-retry</artifactId>
  <version>2.0.13</version>
</dependency>
```

Gradle Groovy/Kotlin 的直接字符串坐标和安全 Groovy map 形式也会迁移。

### 3. `@Retryable` 异常分类别名

2.0 增加了更清晰的别名。固定 Core 配方自动完成：

| 1.3.4 写法 | 2.0.13 写法 | 官方配方 |
|---|---|---|
| `@Retryable(value = X.class)` | `@Retryable(retryFor = X.class)` | `ChangeAnnotationAttributeName` |
| `@Retryable(include = X.class)` | `@Retryable(retryFor = X.class)` | `ChangeAnnotationAttributeName` |
| `@Retryable(exclude = X.class)` | `@Retryable(noRetryFor = X.class)` | `ChangeAnnotationAttributeName` |

### 4. `@CircuitBreaker` 异常分类别名

同样自动迁移：

| 1.3.4 写法 | 2.0.13 写法 |
|---|---|
| `value` | `retryFor` |
| `include` | `retryFor` |
| `exclude` | `noRetryFor` |

### 5. `RetryTemplateBuilder.withinMillis`

2.0.13 中 `withinMillis(long)` 已弃用并计划移除，其实现直接委托给 `withTimeout(long)`。固定 Core `ChangeMethodName` 自动改为：

```java
RetryTemplate.builder().withTimeout(1000).build();
```

类型归属必须是 Spring Retry 的 `RetryTemplateBuilder`；应用中同名方法不会被修改。

## 不能盲目自动化的不兼容点

下面不是“README 提醒后就结束”。`FindSpringRetry2_0SourceRisks` 会在对应 import、注解、类、override、构造或方法调用上产生 OpenRewrite search marker，便于 IDE/报告逐项处理。

### Java 17 与 Spring Framework 6

- 2.0.13 为 Java 17 字节码；
- 发布 POM 的可选 `spring-context` 是 `6.2.19`；
- Spring 5.x、Boot 2.x 与 Spring 6 混装会产生二进制问题；
- 编译、测试、运行时、容器基础镜像与 CI 的 Java 必须一致。

构建配方会标记残留的 Java `<17`、旧 Spring/Boot、父/BOM owner。

### `RetryTemplate.rethrow` 二进制断点

1.3.4：

```java
protected <E extends Throwable> void rethrow(RetryContext context, String message)
```

2.0.13：

```java
protected <E extends Throwable> void rethrow(
    RetryContext context, String message, boolean wrapInExhaustedRetryException)
```

旧 override/调用会编译失败。这里没有自动追加一个猜测的 boolean：1.3.4 的实现读取 `RetryTemplate` 私有的 `throwLastExceptionOnExhausted` 状态，子类无法从旧源码可靠恢复同一决策。配方精确标记 `RetryTemplate` 子类和两参数 override，由迁移者根据异常契约选择第三参数并补测试。

### `RetryConfiguration.buildAdvice()` 返回类型收窄

返回类型从 `org.aopalliance.aop.Advice` 收窄为：

```text
org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor
```

旧子类使用宽返回类型的 override 会编译失败。配方标记 `RetryConfiguration` 子类与 `buildAdvice` override，要求迁移返回类型并验证 advisor 顺序。

### `RetryListener` 新默认方法与 `onSuccess`

2.0 中原有 listener 方法变为 default，并增加：

```java
onSuccess(RetryContext, RetryCallback<T, E>, T result)
```

需要验证：

- `open` 的注册顺序以及 `onError`/`close` 的逆序调用；
- `onSuccess` 对结果分类和其自身抛异常时的重试行为；
- listener 动态注册、共享实例的线程安全；
- listener 与事务、metrics、业务审计的顺序。

配方标记 `RetryListener` 实现、callback override、`setListeners`/`registerListener` 调用。

### `RetryListenerSupport` 弃用

`RetryListenerSupport` 自 2.0.1 起弃用并计划移除。看似可以用 `ChangeType` 改成 `RetryListener`，但两者分别是 class 和 interface；自动替换可能需要同时修改：

- `extends` → `implements`；
- 多层继承和构造；
- `super.onError(...)` 调用；
- `instanceof`、反射、序列化和框架 bean 类型判断。

因此本模块不使用盲目的 `ChangeType`，而是把每个使用点标记出来。迁移后应优先直接 `implements RetryListener` 并利用默认方法。

### `StatisticsListener` 层次变化

2.0 的 `StatisticsListener` 不再继承 `RetryListenerSupport`。源码调用可能仍编译，但依赖旧继承关系的 `instanceof`、反射、bean 查找或序列化会改变。配方标记其 import、构造和子类。

### 注解表达式求值

2.0 明确区分：

- `#{...}`：初始化时一次求值；
- 不带分隔符：运行时求值。

配方标记 `maxAttemptsExpression`、`exceptionExpression`、backoff 的 delay/maxDelay/multiplier 表达式，以及 circuit breaker 的 open/reset timeout 表达式。必须验证 bean 引用、`#root`、参数名、异常类型、结果类型和求值次数。

### stateful retry 与 context cache

`stateful = true`、`RetryState`、`RetryContextCache`、`MapRetryContextCache` 和 `SoftReferenceMapRetryContextCache` 会被标记。需要验证：

- key 的 `equals/hashCode` 稳定性；
- 容量、并发与淘汰；
- 失败跨调用重放；
- 事务边界；
- `noRetryFor`/`notRecoverable` 与 recoverer 的组合；
- 是否持久化了旧版本的 context 或策略对象。

2.0 在 map cache 家族中引入公共抽象父类，公开调用通常源码兼容，但继承和序列化假设不是可安全推导的。

### policy、backoff 与 timeout

配方标记 `RetryTemplate.execute`、policy/backoff 构造、builder 的 attempts/timeout/backoff 方法。建议使用确定性 `Sleeper`/时钟和故障注入验证：

- 首次调用是否计入 attempts；
- timeout 边界；
- fixed/exponential/random backoff；
- exception classifier；
- recovery 和最后异常传播；
- interrupt 与线程池行为。

### Micrometer

2.0.13 发布 POM使用可选 `micrometer-core:1.15.12`。`MetricsRetryListener` 使用点和不对齐的直接 Micrometer 依赖会被标记。验证 meter 名称、tag 基数、registry 生命周期、重复注册和 listener 顺序。

### AOP/AspectJ 与打包

`@EnableRetry`/`@Retryable` 依赖代理语义。配方会标记：

- Spring AOP/AspectJ 直接依赖；
- final/private/self-invocation；
- advisor 与事务顺序需要验证的位置；
- exclusions、Maven shade/assembly、Gradle shadow/relocation；
- 非标准 classifier/type/variant。

## 官方配方复用审计

本模块锁定并审计的上游：

| 上游 | 固定版本 | 固定 commit | 结论 |
|---|---:|---|---|
| OpenRewrite Core | `8.87.5` | [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/commit/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) | 复用 `ChangeAnnotationAttributeName`、`ChangeMethodName` |
| rewrite-spring | `6.35.0` | [`d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/commit/d28afcb6661ad413539056de0936c5489ff9d8ee) | 扫描 recipe catalog/YAML/class 后，没有 Spring Retry 1.x→2.x 专用配方 |
| rewrite-migrate-java | `3.40.0` | [`658481254a6ee678f5f162e51d8d49ee01c75877`](https://github.com/openrewrite/rewrite-migrate-java/commit/658481254a6ee678f5f162e51d8d49ee01c75877) | 复用低层 `UpgradeJavaVersion(17)` |

没有使用以下宽配方：

- `UpgradeDependencyVersion`：版本范围、owner 与制品选择比本模块的“只允许 1.3.4”合同更宽；
- 完整 `UpgradeToJava17`：会同时升级无关插件/依赖并执行语言现代化，不符合单软件模块边界；
- 任意 Spring Framework/Boot 大版本聚合配方：本模块不能替用户决定整个 Spring 平台；
- `ChangeType(RetryListenerSupport → RetryListener)`：class→interface 与继承语义不安全；
- 对两参数 `RetryTemplate.rethrow` 的猜测改写：无法恢复私有 flag 的业务语义。

这也是为什么本模块既复用官方确定性能力，又保留严格自研 dependency owner 与风险 scanner。

## 固定 Spring Retry 上游证据

| 证据 | 固定 commit |
|---|---|
| `v1.3.4` | [`65cb556fd312095e0d1d5af98b0acf483b549fef`](https://github.com/spring-projects/spring-retry/commit/65cb556fd312095e0d1d5af98b0acf483b549fef) |
| `v2.0.0` | [`ef297179e3e1ba88a7b4abef1a9faca5deb1f03a`](https://github.com/spring-projects/spring-retry/commit/ef297179e3e1ba88a7b4abef1a9faca5deb1f03a) |
| `v2.0.13` | [`f1012127f6084800ef5d3b8f8f2bc3b51c53997a`](https://github.com/spring-projects/spring-retry/commit/f1012127f6084800ef5d3b8f8f2bc3b51c53997a) |
| Java 17 / Spring 6 baseline | [`b33671239bf0b2b0efcd77a95bd7920f52425878`](https://github.com/spring-projects/spring-retry/commit/b33671239bf0b2b0efcd77a95bd7920f52425878) |
| runtime expression evaluation | [`47c1e52900b32dc29fbf54699cfa4c60007b3c01`](https://github.com/spring-projects/spring-retry/commit/47c1e52900b32dc29fbf54699cfa4c60007b3c01) |
| `RetryListener.onSuccess` / result-based retry | [`aed39de54bed72306503c9d1bfaf849caba00442`](https://github.com/spring-projects/spring-retry/commit/aed39de54bed72306503c9d1bfaf849caba00442) |
| `retryFor` / `noRetryFor` / `notRecoverable` | [`860bd0db4b8cb534d99ff696c9119d3ad80df645`](https://github.com/spring-projects/spring-retry/commit/860bd0db4b8cb534d99ff696c9119d3ad80df645) |
| Spring Framework 6 final alignment | [`df3fa24bb06f2402a54a463505898e4947eab2a2`](https://github.com/spring-projects/spring-retry/commit/df3fa24bb06f2402a54a463505898e4947eab2a2) |

发布 POM 的直接可选依赖以 `v2.0.13` 固定源码及 Maven Central 发布物交叉核对：`spring-context:6.2.19`、`micrometer-core:1.15.12`。

## 真实仓库 fixtures

测试不是只用人为构造的 happy path。固定并精简了三个真实 Apache-2.0 仓库片段：

| 仓库与 commit | 实际覆盖 |
|---|---|
| [`Netflix/genie@923ea15f963849b3594e1403c4a47ea8c80ac151`](https://github.com/Netflix/genie/commit/923ea15f963849b3594e1403c4a47ea8c80ac151) | `ArchivedJobServiceImpl` 的 `@Retryable(include=...)` 和 retry/backoff 表达式 |
| [`oneops/oneops@54780ad3de35a285f3d00baeb9be49e54f47619e`](https://github.com/oneops/oneops/commit/54780ad3de35a285f3d00baeb9be49e54f47619e) | `CMSClient.DefaultListenerSupport` 的 listener 继承与 callbacks |
| [`spring-projects/spring-retry@f1012127f6084800ef5d3b8f8f2bc3b51c53997a`](https://github.com/spring-projects/spring-retry/commit/f1012127f6084800ef5d3b8f8f2bc3b51c53997a) | 官方 builder 测试中的 `withinMillis` |

fixture 的固定路径、许可证和缩减说明见 `src/test/resources/fixtures/real/README.md`。三份 fixture 都由配方测试实际执行。

## 测试范围

当前模块执行 161 个 JUnit/Jupiter test invocations，包括：

- Maven literal、dependencyManagement、profile、独占属性；
- 属性共享、重复、attribute 引用、profile shadow 与外部 owner；
- Gradle Groovy string/map/map literal、Kotlin string；
- 动态版本、catalog、platform、variant、四段坐标；
- root 与嵌套/foreign Gradle DSL 所有权；
- 白名单、其他低版本、目标、高版本与超大数字版本；
- 精确禁止降级 marker；
- 官方 Java 17 配方在 Maven/Gradle 中真实激活；
- 6 个注解 alias 与 builder rename；
- proxy、expression、listener、两处编译断点、stateful/cache、policy/backoff、stats、serialization、metrics；
- 真实仓库 fixtures；
- generated/cache 排除与两周期幂等。

执行：

```bash
mvn -f rewrite-spring-retry-upgrade/pom.xml clean verify
```

## 使用

在发布本模块后，可通过 OpenRewrite Maven plugin 激活推荐配方：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-retry-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13
```

若只需要某一阶段，也可分别激活：

```text
com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryBuildToJava17
com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryTo2_0_13
com.huawei.clouds.openrewrite.springretry.MigrateDeterministicSpringRetry20Java
com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0_13BuildRisks
com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0SourceRisks
```

建议把 search marker 导出为数据表或在 IDE 中逐项处理；marker 是可执行扫描结果，不是仅供阅读的 README 清单。
