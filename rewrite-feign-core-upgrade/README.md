# Feign Core 13.6 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `io.github.openfeign:feign-core` 的全部明确记录：工作表行 390–392 的 `10.4.0`、`11.1`、`11.9`，以及行 1499–1501 的 `12`、`12.1`、`12.4`，统一迁移到 `13.6`。

推荐入口：

```text
com.huawei.clouds.openrewrite.feigncore.MigrateFeignCoreTo13_6
```

该入口不只是修改版本号：它先严格升级依赖，再执行类型归属可证明且保持语义的 Java API 迁移，最后在不能替业务做决定的准确 AST 或构建节点上添加带原因的 `SearchResult`。例如 `/*~~(RetryableException.retryAfter() returns...)~~>*/` 表示该处已被发现但需要人工设计，并不表示已修复。

如果只需要版本选择，可使用低层入口：

```text
com.huawei.clouds.openrewrite.feigncore.UpgradeFeignCoreTo13_6
```

它只包含严格依赖升级，绝不隐式启用源码迁移或审计。

## 配方能力与不兼容点

| 不兼容点或边界 | 配方行为 | 状态 | 测试依据 |
| --- | --- | --- | --- |
| 表格 6 个来源版本 | 只将 `10.4.0`、`11.1`、`11.9`、`12`、`12.1`、`12.4` 的 `feign-core` 升到 `13.6`；`12` 是表格中的字面值，即使 Maven Central 实际发布使用 `12.0`，也不擅自扩展白名单 | **AUTO** | 6 版本参数化测试 + `Set` 等值锁定 |
| Maven direct / dependencyManagement / profile | 仅 project 或其直接 profile 的 dependency；root 属性对 profile 可见，profile override 优先且不会泄漏到 root 或 sibling | **AUTO** | direct、DM、root/profile visibility、override、sibling NOOP |
| Maven 本地版本属性 | 唯一定义、至少一个目标引用、且所有引用均是受支持 `feign-core` version 时才更新；重复、未使用、plugin/XML attribute/其他消费者保持不变 | **AUTO / NOOP** | 独占/空白引用 before→after，重复、跨用途、profile 非所有者负例 |
| Gradle Groovy/Kotlin DSL | 仅顶层、无 select 的真实 `dependencies {}` 中标准 configuration 直接坐标；支持 string、Groovy map，保留其他依赖 | **AUTO** | Groovy/Kotlin 固定仓库夹具、map notation、root/nested 对照 |
| 外部 BOM、versionless、property/catalog/变量、range/dynamic、classifier/type/ext、未列固定版本、目标/后续版本 | 不猜版本所有者、不扩大范围、不降级、不改变变体；推荐入口在真实拥有者节点 MARK | **NOOP / MARK** | versionless、`${...}`、`[10,13)`、`+`、13.5/13.7、variant、plugin/fake XML |
| Feign 10 的 `Contract.parseAndValidatateMetadata` 拼写错误 | 类型确认属于 `feign.Contract` 时改为目标名称 `parseAndValidateMetadata`；同名业务方法不改 | **AUTO** | 单独/组合 before→after、同名 NOOP、两轮幂等 |
| `Feign.Builder.decode404()` 弃用 | 目标官方说明是同语义改名时，按类型归属改为 `dismiss404()`，保留 builder chain | **AUTO** | chain before→after、同名 NOOP、两轮幂等 |
| `RetryableException.retryAfter()` 从 `Date` 改为 nullable epoch-millis `Long` | 精确的 `retryAfter().getTime()` 改成 `retryAfter()`，保持毫秒语义并更新方法类型；其他 Date/Instant/空值使用不猜 | **AUTO / MARK** | epoch before→after、Date 赋值 marker、同名业务 API NOOP、两轮幂等 |
| `RetryableException` 的 Date 构造器仅兼容保留 | 在具体 `new RetryableException(... Date ...)` 上 MARK；需业务决定 null/no-retry、clock 和 epoch 转换 | **MARK** | 构造调用节点 marker |
| `@QueryMap(encoded=true)` 不再决定 map 编码 | 在实际注解上 MARK；由项目选择 `QueryMapEncoder` 并回归 percent encoding、null、集合、嵌套及已编码输入 | **MARK** | true marker、默认/false NOOP |
| response interceptor 从单值变为有序链 | 在实际 `responseInterceptor` 配置调用上 MARK；复核多次调用由覆盖变为追加后的顺序、短路、decode 与异常 | **MARK** | 类型归属调用 marker、同名业务 API NOOP |
| `BaseBuilder` 泛型、`build()`/`internalBuild()` 与 enrichment | 在自定义 `BaseBuilder` / `Feign.Builder` 的 extends 类型节点 MARK，不武断推导第二泛型和构建产物 | **MARK** | builder extends 节点 marker |
| 自定义 `Contract`、`AsyncClient`、`Response.Body`、`Retryer`、`RetryAfterDecoder`、`FeignException` | 在 implements/extends 的准确类型节点 MARK，要求按 13.6 重新编译并验证对应生命周期与异常合同 | **MARK** | Contract/Retryer marker；相似业务类型 NOOP |
| Feign family companion 版本不一致 | 对 `io.github.openfeign:feign-*` 的非 core 固定/无版本声明在真实依赖节点 MARK；不假设每个模块都成功发布了同版本 | **MARK** | Maven `feign-jackson`、Gradle `feign-okhttp`、13.6 aligned NOOP |
| Java 低于 8 | 只在 Maven project/profile 的标准 compiler 属性值上 MARK；不扫描任意 XML 文本 | **MARK** | release 7 marker、17 NOOP |
| `target/build/out/dist/generated*/install*/.gradle/.mvn/.m2/.idea/node_modules/vendor` 等 | 仅检查父目录组件，生成、安装与缓存产物不做 AUTO/MARK；名为 `install.java` / `install.gradle` 的叶文件仍处理 | **NOOP** | 大小写 generated/install/cache 路径与叶文件对照 |

## 确定性源码转换

```java
// before: Feign 10 拼写 + 旧 builder 名称
contract.parseAndValidatateMetadata(Api.class);
Feign.builder().decode404();

// after
contract.parseAndValidateMetadata(Api.class);
Feign.builder().dismiss404();
```

```java
// before: Date -> epoch millis
long nextAttemptAt = failure.retryAfter().getTime();

// after: Feign 13 已直接返回 nullable Long，赋给 long 时保持原先非空要求
long nextAttemptAt = failure.retryAfter();
```

配方不会把下面的代码直接改成 `Long`，因为后续调用期待的时间模型必须由业务决定：

```java
Date retryDate = failure.retryAfter(); // MARK
```

## 仍需重点验证的行为变化

- Feign 13 将 retry-after 的公开时间表示改成 epoch milliseconds；重试器需回归 null/no-retry、HTTP-date、秒数 header、时钟漂移、溢出、backoff 和中断。
- 多个 `responseInterceptor` 形成链；重复配置不再只是“最后一次覆盖”，需验证顺序、短路、body 关闭、decoder 与异常传播。
- 自定义 builder 需适配 `BaseBuilder<B,T>`、final `build()`、`internalBuild()` 和 enrichment clone，尤其是 capability 与 interceptor 列表是否被重复加入。
- QueryMap、URI template、slash/plus/percent 编码在跨大版本升级中有多次修正；请用真实服务端回归空值、集合、map、already-encoded、matrix/query/path 参数。
- 自定义 synchronous/async client 需回归 method-specific `Request.Options`、连接/读取超时、取消、executor/context 生命周期、压缩与空响应。
- 自定义 decoder/error decoder/exception 需验证 response body 关闭、charset、headers/request/cause 保留、404 dismissal、void/optional/typed response。
- 目标主源码仍编译为 Java 8 bytecode；同时要用部署 JDK、JPMS/shading/GraalVM/native image、反射与序列化矩阵验证真实运行时。

## 子配方

| 配方 | 作用 |
| --- | --- |
| `UpgradeSelectedFeignCoreDependency` | 表格字面白名单、Maven scoped property 与 Gradle root DSL 的严格升级 |
| `MigrateFeign13DeterministicApis` | Contract 拼写、decode404 改名、epoch-millis 链式读取的确定性 AUTO |
| `FindFeign13SourceRisks` | retry、QueryMap、interceptor 与公共扩展点的节点级 MARK |
| `FindFeign13BuildRisks` | 版本所有权、变体、Java baseline 与 Feign family alignment MARK |
| `UpgradeFeignCoreTo13_6` | 仅严格依赖升级的公开入口 |
| `MigrateFeignCoreTo13_6` | 推荐的 AUTO + MARK 组合入口 |

完整名称均以 `com.huawei.clouds.openrewrite.feigncore.` 开头。

## 官方固定依据

目标 tag `13.6` 解引用到固定提交 [`abd43f76`](https://github.com/OpenFeign/feign/tree/abd43f761071653587ec10e98c03e749879485cc)：

- [13.6 的 `RetryableException`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/core/src/main/java/feign/RetryableException.java) 显示 Long 主构造器/返回值与兼容保留的 Date 构造器；
- [13.6 的 `BaseBuilder`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/core/src/main/java/feign/BaseBuilder.java) 显示双泛型、final `build()`、`internalBuild()` 与 response interceptor chain；
- [13.6 的 `Contract`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/core/src/main/java/feign/Contract.java) 是目标验证方法名称；
- [13.6 的 `QueryMap`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/core/src/main/java/feign/QueryMap.java) 与 [13.6 README](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/README.md) 是注解和扩展点依据；
- [Maven Central 13.6 artifact](https://repo1.maven.org/maven2/io/github/openfeign/feign-core/13.6/) 固定目标制品。

源版本 tag 固定提交为：[`10.4.0@44d76840`](https://github.com/OpenFeign/feign/tree/44d76840b80417068a7b97b16a7b8a9a3d082fd3)、[`11.1@f6f5ff81`](https://github.com/OpenFeign/feign/tree/f6f5ff814c9bcc3918abb6e39764ad2c96536faa)、[`11.9@38f4fc7c`](https://github.com/OpenFeign/feign/tree/38f4fc7caa202595115f10e44161f4473cd2d6fe)、[`12.0@8c22fccd`](https://github.com/OpenFeign/feign/tree/8c22fccd8cdcbc875f4eede019f9d76332527d99)、[`12.1@10ce9cb6`](https://github.com/OpenFeign/feign/tree/10ce9cb66be5e0bc0a93491608d0a341c4d1955a)、[`12.4@602f588c`](https://github.com/OpenFeign/feign/tree/602f588ca538e0f7cc1b06840e5be6bb06f619d2)。关键演进固定到官方历史提交：Contract typo 修正 [`d5389a57`](https://github.com/OpenFeign/feign/commit/d5389a57db17ad9a311813bcb8539ff891d9ac3a)、retry-after epoch millis [`be25759e`](https://github.com/OpenFeign/feign/commit/be25759e93d2b94408b44270013d8379ec9b47bd)、`decode404` 弃用说明 [`dacb0869`](https://github.com/OpenFeign/feign/commit/dacb086923dac14331f014fb25728661b2901f75)、multiple response interceptors [`2c00066d`](https://github.com/OpenFeign/feign/commit/2c00066d4a7a1f1882708166f8b2cbaabe721efa) 和 builder clone/enrichment [`04b52095`](https://github.com/OpenFeign/feign/commit/04b52095b89b4863209a0a5ab686546f2acb079f)。

## 真实仓库固定夹具

| 仓库固定提交 | 实际场景 | 验证效果 |
| --- | --- | --- |
| [hundun000/mirai-fleet-amiya `b835b869`](https://github.com/hundun000/mirai-fleet-amiya/blob/b835b869cc8ab21df921fc784b3cf720c293b305/build.gradle) | Gradle Groovy `11.1` direct dependency | root DSL before→after |
| [boclips/terry `930ac723`](https://github.com/boclips/terry/blob/930ac723c605e975b01ba2bbe351608b06543f26/build.gradle.kts) | Gradle Kotlin `11.9` | Kotlin DSL before→after |
| [lkqm/apidocx `b9f8f4bb`](https://github.com/lkqm/apidocx/blob/b9f8f4bb5db416f5a22549799c06e7a78446bc70/build.gradle) | Gradle Groovy `12.1` | selected version before→after |
| [grayalert/grayalert `f68525c4`](https://github.com/grayalert/grayalert/blob/f68525c4ab18d3b43ffa8b1fa985e09ae52fb71b/build.gradle) | root dependency set containing `12.4` | 只改目标坐标、保留相邻依赖 |

测试结构参考 OpenRewrite 官方固定提交 [`rewrite-java-dependencies@decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，采用 before→after、NOOP、带原因 marker、真实仓固定提交夹具和 two-cycle idempotency。模块当前共 74 个 JUnit invocation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-feign-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.feigncore.MigrateFeignCoreTo13_6
```

审查所有 patch 与 `~~>`，刷新 dependency locks，再运行 compile、unit/integration、contract、query/path encoding、retry/backoff、sync/async client、timeout/cancel、interceptor/decoder、error mapping、body closure、proxy/TLS/compression、JPMS/shading/native-image 和 dependency-convergence 测试。

模块自身验证（无需修改根聚合 POM）：

```bash
mvn -f rewrite-feign-core-upgrade/pom.xml clean verify
```
