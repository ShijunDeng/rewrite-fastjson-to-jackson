# Feign OkHttp 迁移到 13.6

本模块把 `io.github.openfeign:feign-okhttp` 的表格指定版本迁移到 `13.6`。它不是只修改版本号的 YAML：公开低层配方只做可证明安全的版本升级；推荐配方复用低层配方，并用类型归属明确的 OpenRewrite visitor 在 Maven、Gradle 和 Java AST 的精确节点上留下可执行审计标记。

## 表格白名单

对 `开源软件升级.xlsx` 的所有 worksheet XML 和共享字符串做全量扫描，只发现三次该坐标：

| 表格行 | 序号 | 精确源版本 | 目标版本 | Feign 自带 OkHttp 线 |
|---:|---:|---:|---:|---:|
| 397 | 396 | `10.4.0` | `13.6` | `3.6.0` → `4.12.0` |
| 398 | 397 | `11.1` | `13.6` | `4.6.0` → `4.12.0` |
| 1518 | 1517 | `12.4` | `13.6` | `4.11.0` → `4.12.0` |

`10.4.0`、`11.1`、`12.4` 是完整而非示例白名单。`10.4.1`、`11.2`、`12`、`12.5`、范围、动态版本和任意其他固定版本都不会被自动升级。

## 配方

| 配方 | 定位 | 行为 |
|---|---|---|
| `com.huawei.clouds.openrewrite.feignokhttp.UpgradeFeignOkHttpTo13_6` | 公开低层 `Upgrade` | 只把普通 jar 的精确白名单声明升级到 `13.6`；不改 Java，不猜 BOM/平台/版本目录 |
| `com.huawei.clouds.openrewrite.feignokhttp.MigrateFeignOkHttpTo13_6` | 推荐入口 | 先复用公开 `Upgrade`，再执行源码和构建兼容性 visitor，把需要业务决策的精确节点标为 `SearchResult` |
| `UpgradeSelectedFeignOkHttpDependency` | Java 实现 | Maven root/profile/local dependencyManagement/自有 property 和根 Gradle DSL 的确定性升级 |
| `FindFeignOkHttp13SourceRisks` | Java 实现 | 按真实类型标记 Feign 适配器、OkHttp 配置、请求/响应与资源生命周期 |
| `FindFeignOkHttp13BuildRisks` | Java 实现 | 标记版本所有权、非标准变体、Feign 家族、OkHttp 4.12 家族和 Java 基线风险 |

运行推荐配方：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-feign-okhttp-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.feignokhttp.MigrateFeignOkHttpTo13_6
```

## 自动处理与人工边界

| 不兼容点或迁移风险 | 处理方式 | 精确节点 | 验证重点 |
|---|---|---|---|
| 白名单依赖版本 | AUTO | Maven `<version>` 或独占本地 property；根 Gradle dependency literal/map `version` | 实际解析为 `13.6` |
| `feign-*` 家族混用 | MARK | 不为 `13.6` 的同族依赖版本节点 | `Client`/`AsyncClient`、`Request.Options`、响应、重试、日志、codec 二进制一致性 |
| OkHttp/MockWebServer/拦截器等混用 | MARK | `com.squareup.okhttp3:*` 非 `4.12.0` 版本节点 | Okio/Kotlin runtime、测试服务器、TLS、Brotli、日志拦截器一致性 |
| 默认 `new feign.okhttp.OkHttpClient()` | MARK | 精确构造表达式 | 是否复用 adapter；连接池、dispatcher、线程、shutdown hook 归属 |
| 注入自有 `okhttp3.OkHttpClient` | MARK | 精确构造表达式 | singleton 共享、cache/pool/dispatcher 关闭、异步取消和指标 |
| Feign 每请求选项 | MARK | 精确 `new Request.Options(...)` | Feign 会覆盖 connect/read/followRedirects；write/call/ping 仍来自 delegate |
| connect/read/write/call timeout | MARK | `OkHttpClient.Builder` 的精确调用 | 单位、覆盖顺序、重试、取消、SLA |
| HTTP/2/ALPN | MARK | `protocols`、`pingInterval` 精确调用 | 协议顺序、ALPN fallback、多路复用、HTTP/2 error、日志/指标 |
| TLS | MARK | `sslSocketFactory`、`hostnameVerifier`、`certificatePinner`、`connectionSpecs` 等精确调用 | trust manager 配对、pin 轮换、TLS/cipher、SNI/ALPN |
| retry/redirect/proxy/DNS/auth | MARK | 对应 `OkHttpClient.Builder` 精确调用 | Feign option 优先级、307/308、认证重试、body replay、取消 |
| interceptor 顺序 | MARK | application/network interceptor 的添加/列表精确调用 | gzip、认证、重试、日志、response close 顺序 |
| 手工压缩 | MARK | Feign/OkHttp 的 `Accept-Encoding`/`Content-Encoding` header 调用；WebSocket 压缩阈值 | transparent gzip 是否失效、双重压缩、content-length、CPU/内存 |
| request body | MARK | `RequestBody.create`、`Request.Builder` body method、`MultipartBody.Builder`、`RequestTemplate.body*` | 空 POST/PUT/PATCH、media type/charset、one-shot/duplex、重试可重放性 |
| response body | MARK | `ResponseBody` 的 stream/bytes/string/source/close 调用 | 恰好关闭一次、流所有权、解压、空体、连接复用、异步异常 |
| client lifecycle | MARK | pool/dispatcher/cache/newBuilder/newCall/evictAll 精确调用 | 线程退出、空闲连接、cache close、取消、容器重部署 |
| `OkHttpClient` 子类/Mock | MARK | 精确 `extends` 类型 | OkHttp 4 accessor 为 final；改用 composition/`Call.Factory` |
| `okhttp3.internal.*` | MARK | 精确 import | internal API 无兼容承诺，必须换成 public API |
| `Credentials.basic(null, ...)` | MARK | 精确方法调用 | OkHttp 4 参数不可空，选择显式凭证或 fail-fast |
| Java 7 或更低 | MARK | Maven Java property 精确节点 | Feign/OkHttp 最低 Java 8；重测 TLS/ALPN |

构建审计要求同一个标准 POM 或根 Gradle `dependencies {}` 中确实存在 `feign-okhttp` consumer，才检查其旁边的 Feign family、OkHttp BOM/modules 和 Java baseline。普通 sibling 模块只使用 OkHttp、`feign-core` 或平台 BOM 时不会被误标。相关性通过 Maven dependency AST、Gradle 静态坐标/map/template 和明确的 `libs.feign.okhttp` alias 判断；仅有说明文字或 nested DSL 不算 consumer。

没有添加虚假的 Java AUTO：官方明确说明 OkHttp 4 尽量保持 Java 源码和二进制兼容，Feign 的两个公开 adapter 构造器从三个源版本到 `13.6` 也保持不变。在没有官方唯一等价替换时自动重写请求、超时、TLS 或生命周期代码会改变业务语义，因此这些能力通过可执行 marker recipe 落到具体 AST 节点，而不是只写在本文档里。

## 三条迁移路径的真实差异

### `10.4.0` → `13.6`

- Feign `10.4.0` 的根 POM管理 OkHttp `3.6.0`，目标管理 `4.12.0`。这是跨 OkHttp 3/4 的路径。
- OkHttp 官方升级文档列出的少量 Java 不兼容包括：`OkHttpClient` accessor 变为 final、`okhttp3.internal` 大改、`Credentials.basic()` 非空约束；Kotlin 调用还涉及 property、extension、SAM 和 companion import 变化。
- OkHttp 4 引入 Kotlin runtime；必须检查直接固定的 OkHttp/Okio/Kotlin 依赖是否把目标传递依赖降级。
- Feign adapter 从 `Request.requestBody().asBytes()` 演进到 `Request.body()`，但它是 Feign 模块内部实现，不应盲改业务调用。

### `11.1` → `13.6`

- OkHttp 从 `4.6.0` 到 `4.12.0`。
- Feign adapter 在该范围内实现 `AsyncClient<Object>`；异步调用通过 OkHttp enqueue 完成，需要覆盖成功、网络失败、取消、response close 和 executor 生命周期测试。
- Feign adapter 开始把 OkHttp response protocol 写入 Feign `Response.ProtocolVersion`。
- Feign 对空 body 的方法判断改为 `HttpMethod.isWithBody()`；业务自定义 `Client`、interceptor 和重试代码必须回归空 POST/PUT/PATCH 与非标准方法。

### `12.4` → `13.6`

- OkHttp 从 `4.11.0` 到 `4.12.0`。
- Feign `13.6` 修复协议映射：使用 `response.protocol().name()`，而不是不适合枚举解析的 `toString()`。HTTP/2 日志、指标和依赖 `Response.protocolVersion()` 的逻辑应做快照测试。
- adapter 构造器和同步/异步入口保持兼容，因此不存在需要 AUTO 的唯一源码替换；风险 visitor 仍会定位协议、body、timeout、TLS 和生命周期配置。

## 构建边界

### Maven

- 只处理文件名为 `pom.xml` 的 project 根 `dependencies`、`dependencyManagement`，以及直接 profile 下的同类节点。
- root property 对 profile 可见；profile property 不泄漏到 root 或兄弟 profile；profile 同名定义覆盖 root。
- property 必须在所属 scope 唯一定义、当前值属于白名单、全部引用都只属于普通 `feign-okhttp` 目标版本，才会 AUTO。
- 本地 dependencyManagement 中的白名单会升级；它管理的 versionless consumer 保持 versionless。推荐审计能识别本地 target DM 和本地 OkHttp `4.12.0` BOM，避免误报。
- 外部 parent/BOM、版本目录、未定义 property、范围、动态版本、versionless 且本地 owner 不可证明、classifier、非 jar type 都不猜测；推荐配方会在可见 owner 上标记。
- plugin dependencies 和任意形似 Maven 的其他 XML 不处理。

### Gradle

- 只处理 `build.gradle` / `build.gradle.kts` 的根 `dependencies {}`，并要求配置调用没有 select。
- 支持固定字符串坐标和 Groovy map notation。
- `buildscript`、`subprojects`、`allprojects`、`project(...)`、`constraints`、custom DSL、plugin DSL、selected invocation、GString/Kotlin template、catalog/platform/BOM 均不自动修改。

### 生成目录

父目录组件按大小写不敏感排除：`target`、`build`、`out`、`dist`、`.gradle`、`.mvn`、`.m2`、`.idea`、`.angular`、`.nx`、`.next`、`.nuxt`、`.cache`、`.output`、`.git`、`.vscode`、`.turbo`、`.parcel-cache`、`.vite`、`node_modules`、`bower_components`、`vendor`、`.pnpm`、`.yarn`、`.npm`、`coverage`、`tmp`、`temp`、`report(s)`、`test-results`、`storybook-static`，以及任意以 `generated` 或 `install` 开头的父目录。叶文件 `install.java` 和 `install.gradle` 仍然处理。

## 官方证据（固定 tag/commit）

- Feign `10.4.0`：[`44d76840b80417068a7b97b16a7b8a9a3d082fd3`](https://github.com/OpenFeign/feign/tree/44d76840b80417068a7b97b16a7b8a9a3d082fd3/okhttp)
- Feign `11.1`：[`f6f5ff814c9bcc3918abb6e39764ad2c96536faa`](https://github.com/OpenFeign/feign/tree/f6f5ff814c9bcc3918abb6e39764ad2c96536faa/okhttp)
- Feign `12.4`：[`602f588ca538e0f7cc1b06840e5be6bb06f619d2`](https://github.com/OpenFeign/feign/tree/602f588ca538e0f7cc1b06840e5be6bb06f619d2/okhttp)
- Feign `13.6`：[`abd43f761071653587ec10e98c03e749879485cc`](https://github.com/OpenFeign/feign/tree/abd43f761071653587ec10e98c03e749879485cc/okhttp)
- Feign async OkHttp adapter：[`988b38963ba55ecc497dbcc124881847846d559a`](https://github.com/OpenFeign/feign/commit/988b38963ba55ecc497dbcc124881847846d559a)
- Feign response protocol：[`206193d2730b4c086771b5151889e4131e6a10cb`](https://github.com/OpenFeign/feign/commit/206193d2730b4c086771b5151889e4131e6a10cb)
- Feign `Protocol.name()` 修复：[`dc83fe36187f012031826cfdf7cb7c481caddca8`](https://github.com/OpenFeign/feign/commit/dc83fe36187f012031826cfdf7cb7c481caddca8)
- Feign 空 request body：[`e9606362954f343962797f4dc03ef65fcf381f29`](https://github.com/OpenFeign/feign/commit/e9606362954f343962797f4dc03ef65fcf381f29)
- OkHttp `3.6.0`：[`9dc1bbad245a325ffb0cd1bd88d2c439a1c2a5bd`](https://github.com/square/okhttp/tree/9dc1bbad245a325ffb0cd1bd88d2c439a1c2a5bd)
- OkHttp `4.6.0`：[`0deadd5611c4bc793a776aaaa68710012e456b9f`](https://github.com/square/okhttp/tree/0deadd5611c4bc793a776aaaa68710012e456b9f)
- OkHttp `4.11.0`：[`68a106d3269b0ad02136a1bfa81c07a6c006fffe`](https://github.com/square/okhttp/tree/68a106d3269b0ad02136a1bfa81c07a6c006fffe)
- OkHttp `4.12.0` 与官方 3→4 说明：[`4984568367caaf359b82c452bd28b5e192824d1c`](https://github.com/square/okhttp/blob/4984568367caaf359b82c452bd28b5e192824d1c/docs/upgrading_to_okhttp_4.md)
- OpenRewrite 固定参考：[`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/rewrite-maven/src/test/java/org/openrewrite/maven/UpgradeDependencyVersionTest.java)

## 真实公开仓用例（固定 commit）

测试夹具不是凭空编造，已约简自：

- `10.4.0` property + dependencyManagement：[`mwiede/feign-validation`](https://github.com/mwiede/feign-validation/blob/2967d856c3349b8f4c59e4ef8a0b6df4dac6f3e2/pom.xml)
- `11.1` Feign 家族 property：[`nutzam/nutzboot`](https://github.com/nutzam/nutzboot/blob/ffe77a39777b6f3021bd3dafbd3a23810d871785/pom.xml)
- `12.4` Maven 家族：[`Cumulocity-IoT/cumulocity-lora`](https://github.com/Cumulocity-IoT/cumulocity-lora/blob/ade95723d1fdbb317217a66999dfb820af9b1ee2/java/lora-ns-netmore/pom.xml)
- `12.4` Gradle：[`Kseon14/git-score`](https://github.com/Kseon14/git-score/blob/367f4492529c4afcab7a9d0345b3f4f99cf12379/build.gradle)
- 默认 Feign OkHttp adapter：[`eugenp/tutorials`](https://github.com/eugenp/tutorials/blob/1023d82c27af842a9e86b4663227819db8b19d7a/feign/src/main/java/com/baeldung/feign/retry/ResilientFeignClientBuilder.java)
- caller-owned OkHttp delegate：[`twitch4j/twitch4j`](https://github.com/twitch4j/twitch4j/blob/3ea102457324357be1959bd5fd4e5e41eb3b4dd3/rest-kraken/src/main/java/com/github/twitch4j/kraken/TwitchKrakenBuilder.java)

## 测试策略与限制

测试覆盖 before→after、NOOP、marker、推荐配方顺序、幂等、三条白名单、Maven scope/property/DM、Gradle Groovy/Kotlin DSL 边界、真实仓夹具、官方 API 场景、类型同名反例和生成目录过滤。执行：

```bash
mvn -f rewrite-feign-okhttp-upgrade/pom.xml clean verify
```

当前 source visitor 处理 Java AST。OkHttp 官方说明 Kotlin 源兼容并不等同 Java 源兼容；`.kt` 中的 property/extension/SAM/companion import 应先用 OkHttp 官方迁移说明和 Kotlin 编译器完成专项检查。本模块不会把 Kotlin 代码冒充已自动迁移；构建对齐 visitor 和本文测试清单仍覆盖其依赖边界。

当前 `clean verify` 执行 **92 个测试**：61 个严格升级/所有权用例、18 个类型归属源码 marker 用例、13 个 build alignment/marker 用例。
