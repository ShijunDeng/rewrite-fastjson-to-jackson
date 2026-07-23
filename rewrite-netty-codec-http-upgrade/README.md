# Netty `netty-codec-http` 迁移到 4.1.136.Final

本模块对应 `开源软件升级.xlsx` 中的 `io.netty:netty-codec-http`。README 是不兼容点 spec；配方会真正执行能够由 AST 证明安全的依赖和 Java 源码改写，并把需要业务证据的兼容性决策精确标记到构建或源码节点，而不是只修改版本号。

推荐入口：

```text
com.huawei.clouds.openrewrite.nettycodechttp.MigrateNettyCodecHttpTo4_1_136
```

仅执行严格依赖版本升级时使用：

```text
com.huawei.clouds.openrewrite.nettycodechttp.UpgradeNettyCodecHttpTo4_1_136
```

推荐入口按以下安全顺序组合公开配方：

| 阶段 | 配方 | 行为 |
| ---: | --- | --- |
| 1 | `FindNettyCodecHttp41136BuildRisks` | 在修改依赖前标记禁止降级、版本所有者、Netty 家族对齐、shade/relocation 和 RFC 9112 逃生开关 |
| 2 | `UpgradeNettyCodecHttpTo4_1_136` | 只把工作簿批准的十三个 4.1.x 字面量升级到 `4.1.136.Final` |
| 3 | `MigrateValidatedHttpDecoderConstructors` | 把可证明等价的旧 decoder 构造器迁移为 `HttpDecoderConfig` |
| 4 | `FindNettyCodecHttp41136SourceRisks` | 标记解析、安全、资源生命周期、4.2 分支 API 等需要应用验证的节点 |

## 工作簿边界与禁止降级

工作簿给出的源版本共十四个。十三个 4.1.x 版本允许自动前向升级；`4.2.10.Final` 属于更新的 4.2 分支，绝不自动降级到 4.1。工作簿单元格在持久化文件中虽可能显示为折叠文本，本模块使用需求中明确给出的完整白名单，不使用版本范围猜测。

| 源版本 | 行为 | 固定官方源码 |
| --- | --- | --- |
| `4.1.49.Final` | AUTO | [`d0ec961`](https://github.com/netty/netty/tree/d0ec961cce19646519d6a0d59e7604b0eacd9bf2) |
| `4.1.100.Final` | AUTO | [`58df783`](https://github.com/netty/netty/tree/58df783eb4fc50f95a1061dc4274020d6804caf4) |
| `4.1.107.Final` | AUTO | [`1908d3a`](https://github.com/netty/netty/tree/1908d3a8b02b6ebd00b6c3c0f60cf31a8e31d2ca) |
| `4.1.108.Final` | AUTO | [`3a3f9d1`](https://github.com/netty/netty/tree/3a3f9d13b129555802de5652667ca0af662f554e) |
| `4.1.109.Final` | AUTO | [`43455df`](https://github.com/netty/netty/tree/43455dfe98202b8dc63d89ac3f04027c3e8f9f54) |
| `4.1.118.Final` | AUTO | [`36f95cf`](https://github.com/netty/netty/tree/36f95cfaeed0c1313b21f1b5350c19436ae7fb45) |
| `4.1.125.Final` | AUTO | [`56ea976`](https://github.com/netty/netty/tree/56ea9763c6ac550f0f8ab7849ef0af21532643cb) |
| `4.1.126.Final` | AUTO | [`2d6a16a`](https://github.com/netty/netty/tree/2d6a16a0cc6522ded6cbba06e938cd32fb0e629e) |
| `4.1.127.Final` | AUTO | [`6bb1a6b`](https://github.com/netty/netty/tree/6bb1a6bcae503423343b9155ac48b161f60368b5) |
| `4.1.128.Final` | AUTO | [`afae49c`](https://github.com/netty/netty/tree/afae49cd621a22a22139af5c5ee384e8cfd4ae48) |
| `4.1.129.Final` | AUTO | [`1729bf3`](https://github.com/netty/netty/tree/1729bf313c82845096c8b57755858b17f13db34e) |
| `4.1.130.Final` | AUTO | [`41ff1eb`](https://github.com/netty/netty/tree/41ff1eb45a4acc9976150330e105211ecde36399) |
| `4.1.132.Final` | AUTO | [`ec119d4`](https://github.com/netty/netty/tree/ec119d487b3a27e4ac118e7e1d97f0c96a85f4a3) |
| `4.2.10.Final` | **目标版本冲突（禁止降级）+ MARK** | [`4cc9873`](https://github.com/netty/netty/tree/4cc98736c3947bc93122e0b64e0bd8fc970c6437) |
| 目标 `4.1.136.Final` | NO-OP | [`fca0764`](https://github.com/netty/netty/tree/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6) |

目标发布页为 [Netty 4.1.136.Final](https://github.com/netty/netty/releases/tag/netty-4.1.136.Final)。`4.2.10.Final` 是目标版本冲突（禁止降级），不是一条 4.2→4.1 迁移路径。命中时推荐配方把 `NO_DOWNGRADE` 原因贴到准确版本节点并保持文件不变；使用方必须明确选择“继续 4.2”或另行设计分支迁移，不能为了满足表格目标而制造回退。

## AUTO / MARK / NO-OP

| 类别 | 本模块实际行为 |
| --- | --- |
| **AUTO** | 严格升级十三个批准版本；把三类 decoder 的 4–7 参数、`validateHeaders == true` 构造器迁移为单个 `HttpDecoderConfig`，保留表达式顺序和求值次数 |
| **MARK** | 4.2 禁止降级、外部/动态版本所有者、artifact 变体、Netty 家族偏斜、shade/relocation、RFC 9112 逃生开关、header/parser、multipart/query、aggregation/CORS/upgrade、compression/WebSocket、DateFormatter、SPDY 生命周期和 4.2-only API |
| **NO-OP** | 目标版本、表格外版本、4.2 版本、范围/动态/目录/BOM/platform 管理版本、共享或重复 Maven 属性、非标准 artifact、插件依赖、嵌套 Gradle DSL、同名业务类型、匿名子类以及生成/缓存目录 |

`SearchResult` 是有意保留的迁移任务，不是执行失败。例如 dry-run 会产生类似结果：

```java
new HttpDecoderConfig()
    ./*~~(RFC 9112 transfer-encoding enforcement is disabled or runtime-selected; ...)~~>*/
    setUseRfc9112TransferEncoding(false);
```

相同 marker 不会在重复周期中叠加；版本升级、构造器改写和推荐组合均有两周期幂等测试。

## 严格依赖升级能力

Maven 支持根 `project` 和直接 `profile` 中的 `dependencies`、`dependencyManagement`、精确 `<version>`，以及能够证明仅由标准 JAR 形态 `io.netty:netty-codec-http` 使用的根/profile 属性。profile 本地属性优先于根属性；scope、optional、exclusions 等元数据不变。

构建审计按有效 scope 传播：profile 内 primary 只能看到根节点和同一 profile，不会污染 sibling profile；根 primary 在任一 profile 激活时仍然有效，因此可审计 profile companion。属性解析同时记录定义 owner、引用 owner、重复定义和 profile shadow；无法唯一证明所有权时只 MARK，不借用 sibling/root 的偶然值。Maven 打包检测只接受根 project 或一级 profile 的直接 `build/plugins/plugin`，嵌套配置中的同名 XML 和 plugin dependency 均不触发。

以下 Maven 情况不自动修改：

- 属性还被其他依赖、插件、XML attribute 或普通文本引用；
- 同一 scope 重复定义属性，或版本由 parent/BOM/外部属性提供；
- classifier、非 `jar` type、plugin 内 dependency；
- 范围、动态值、未知固定版本和 `4.2.10.Final`。

Gradle 支持根级真实 `dependencies {}` 中的 Groovy/Kotlin 字符串坐标，以及 Groovy `group/name/version` Map 字面量。`buildscript`、`subprojects`、`allprojects`、`project(...)`、`constraints`、version catalog、platform/BOM、插值、带 select 的自定义方法和 classifier/ext 变体不会被猜测改写。

构建审计同样从 AST 激活：注释、普通字符串、`xio.netty`/`xnetty-codec-http`、嵌套 `dependencies` 均不能让 companion/shade 审计误启动；动态 GString/Kotlin template 只有首段在去除前导空白后以精确 `io.netty:netty-codec-http:` 开头才按外部 owner MARK。Gradle relocation 只认顶层 `shadowJar { relocate ... }`。

路径隔离按父目录判断：`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、`.m2`、IDE/cache/report、`node_modules` 等产物目录不会修改；根目录文件名偶然包含 `install` 不受影响。

## 已自动处理的 Java 不兼容点

Netty 用 [`HttpDecoderConfig`](https://github.com/netty/netty/commit/84a13a92e1d757965438dc41a5611b19dd47ec44) 统一 decoder 配置并弃用带 `validateHeaders` 的旧构造器。对类型归属明确的 `HttpRequestDecoder`、`HttpResponseDecoder`、`HttpServerCodec`，本模块只在第四个参数是字面量 `true` 时做如下映射：

| 旧参数（按位置） | 新配置 |
| --- | --- |
| 1 `maxInitialLineLength` | `setMaxInitialLineLength(...)` |
| 2 `maxHeaderSize` | `setMaxHeaderSize(...)` |
| 3 `maxChunkSize` | `setMaxChunkSize(...)` |
| 4 `validateHeaders == true` | 省略，保留 4.1.136 的安全默认值 |
| 5 `initialBufferSize` | `setInitialBufferSize(...)` |
| 6 `allowDuplicateContentLengths` | `setAllowDuplicateContentLengths(...)` |
| 7 `allowPartialChunks` | `setAllowPartialChunks(...)` |

```java
// before
new HttpRequestDecoder(lineLimit(), headerLimit(), chunkLimit(), true,
        initialBuffer(), allowDuplicate(), allowPartial());

// after
new HttpRequestDecoder(new HttpDecoderConfig()
        .setMaxInitialLineLength(lineLimit())
        .setMaxHeaderSize(headerLimit())
        .setMaxChunkSize(chunkLimit())
        .setInitialBufferSize(initialBuffer())
        .setAllowDuplicateContentLengths(allowDuplicate())
        .setAllowPartialChunks(allowPartial()));
```

每个输入表达式仍按原顺序且只求值一次。`false` 或运行时选择的 validation 会保留并 MARK，因为自动关闭安全校验不是等价升级。`HttpClientCodec` 还涉及 `failOnMissingResponse` 和 CONNECT 后解析选项的位置/行为，配方只 MARK，不机械重排。匿名子类和同名业务 decoder 不改；如果应用自己声明或导入了另一个 `HttpDecoderConfig`，输出使用 `io.netty.handler.codec.http.HttpDecoderConfig` 全限定名，不生成会绑定到错误类型的 import。

## 必须复核的不兼容语义

### HTTP 语法、安全和 RFC 9112

4.1.136 包含持续收紧的消息语法和请求走私防护。配方精确标记 decoder、custom `HttpObjectDecoder`、动态 method/version、validation 关闭点和 RFC 9112 逃生开关。至少验证：

- header name/value 中空白、NUL、CR/LF 和非法控制字符；
- request-line、method、version 的分隔符和边界；
- chunk size/extension、重复或冲突 `Content-Length`、`Transfer-Encoding + Content-Length`；
- malformed/fragmented 输入的 `DecoderResult`、连接关闭、代理链和 keep-alive 行为；
- `io.netty.handler.codec.http.rfc9112TransferEncoding` 系统属性及 `setUseRfc9112TransferEncoding(false)` 已被移除，或有隔离边界和 TE+CL 拒绝测试。

固定官方依据包括 header value 修复 [`c5d3d72`](https://github.com/netty/netty/commit/c5d3d72483)、header-name whitespace [`e6a78dd`](https://github.com/netty/netty/commit/e6a78dd2c9)、NUL/start-line [`3c06dd6`](https://github.com/netty/netty/commit/3c06dd680d)、method 验证 [`214a1f1`](https://github.com/netty/netty/commit/214a1f1ea9)、version 验证 [`5c0b0d5`](https://github.com/netty/netty/commit/5c0b0d5225)、method/version 边界 [`e45c3cf`](https://github.com/netty/netty/commit/e45c3cf18a) / [`df54c37`](https://github.com/netty/netty/commit/df54c37308)、request encoder RLF 修复 [`56328f9`](https://github.com/netty/netty/commit/56328f967d) 和 chunk 解析修复 [`60e53c9`](https://github.com/netty/netty/commit/60e53c99f2) / [`0841072`](https://github.com/netty/netty/commit/0841072df4)。

### Multipart 与 query-string

配方标记 `io.netty.handler.codec.http.multipart.*` 和 `QueryStringDecoder`/`QueryStringEncoder` 的构造与调用。升级测试应覆盖：

- 无 `=` 参数、空值、重复参数、literal `+` 与空格、percent/charset 错误；
- multipart 无值字段、mixed-case metadata、磁盘 threshold 和超大文件 size；
- `destroy()`、delete/release 的正常、异常和 early-return 路径；
- 与框架封装层之间的 ownership，避免 ByteBuf/临时文件泄漏或 double release。

固定修复包括 query plus 选项 [`7fc6a23`](https://github.com/netty/netty/commit/7fc6a233a3)、no-value form [`2da1fd7`](https://github.com/netty/netty/commit/2da1fd7e98)、locale-independent token [`c4232c2`](https://github.com/netty/netty/commit/c4232c2071) 和 disk size overflow [`43fd26f`](https://github.com/netty/netty/commit/43fd26f197)。

### Aggregation、CORS 与 protocol upgrade

`HttpObjectAggregator`、CORS 和 client/server upgrade handler 会被标记。需要用真实 pipeline 覆盖 413、`AUTO_READ`、backpressure、pipelining、preflight content、upgrade 完整/不完整帧、handler removal 和 ByteBuf reference count。相关固定修复包括 aggregator [`442a8cf`](https://github.com/netty/netty/commit/442a8cf960) 与 CORS preflight content [`11346d6`](https://github.com/netty/netty/commit/11346d6cf1)。

### Compression、WebSocket 与 Date

HTTP/WebSocket compression/decompression、Brotli、Zstd、UTF-8 frame validation 和 `DateFormatter.parseHttpDate` 会被标记。验证 allocation budget、zip bomb、codec 可用性、window bits、控制帧、fragmentation、非法 UTF-8，以及 substring range 外尾随 token。固定依据包括 Brotli/Zstd 最大分配 [`51260aa`](https://github.com/netty/netty/commit/51260aa57e)、公开 max-allocation 配置 [`a155024`](https://github.com/netty/netty/commit/a155024263)、WebSocket control frames [`151dfa0`](https://github.com/netty/netty/commit/151dfa083d) 和 UTF-8 validator constructors [`3763ab7`](https://github.com/netty/netty/commit/3763ab7856)。

### SPDY 生命周期和二进制兼容

固定 API 对比显示 4.1.100 到 4.1.136 的公开 binary-incompatible codec-http 变化是 `SpdyHttpDecoder.channelInactive(ChannelHandlerContext)` 移除；清理已迁到 `handlerRemoved`（合并提交 [`bb2ff68`](https://github.com/netty/netty/commit/bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6)）。继承 `SpdyHttpDecoder`、override/super call 都会被 MARK。迁移时要重编译扩展，并在连接关闭和 pipeline removal 两条路径验证 outstanding `FullHttpMessage` 的 reference count。

### 4.2.10 中存在、4.1.136 中不存在的 API

这些是禁止降级边界，不提供“看起来相似”的自动替换：

| 4.2 API | 4.1.136 处理 |
| --- | --- |
| `WebSocketFrameMaskGenerator`、`RandomWebSocketFrameMaskGenerator` | 类型/import/变量/签名精确 MARK |
| `new WebSocket07/08/13FrameEncoder(WebSocketFrameMaskGenerator)` | 构造器精确 MARK |
| `WebSocketServerHandshakerFactory.resolveHandshaker(...)` | 调用与 method reference 精确 MARK |
| `new HttpServerUpgradeHandler(SourceCodec, UpgradeCodecFactory, int, HttpHeadersFactory, HttpHeadersFactory, boolean)` | 六参数签名精确 MARK；常规三参数构造只作为 upgrade 行为风险 |
| `SpdyFrameDecoderDelegate.readUnknownFrame(...)` | implementation/override/call 精确 MARK |
| `SpdyFrameCodec.readUnknownFrame(...)` | 4.2 public 到目标分支不可用，调用精确 MARK |

若项目实际依赖 `4.2.10.Final`，应优先保持 4.2 并针对 4.2 目标设计独立迁移；本模块不会把这些 API 删除后强行让代码“编译”。

## Netty 家族、打包和运行时边界

`netty-codec-http` 与 `netty-common`、`netty-buffer`、`netty-transport`、`netty-codec`、`netty-handler` 等共享内部和二进制契约。受管构建中发现不同版本的 `io.netty:netty-*` companion 时，配方会在准确依赖节点 MARK，建议通过 `netty-bom:4.1.136.Final` 对齐并用 dependency tree/insight 验证。独立版本线的 `netty-tcnative*` 不会被误当成同版本 companion。

shade/relocation `io.netty` 也会被标记。需要验证：

- `META-INF/services`、native transport/classifier、SSL provider 和 platform artifact 未丢失；
- fat JAR、OSGi、插件 classloader、应用服务器 shared lib 和容器镜像中不存在旧 Netty class；
- HTTP client/server、TLS、proxy、WebSocket、native transport 在最终打包物中完成 smoke test；
- 所有编译 Netty handler/extension 的内部 JAR 均 clean rebuild，避免 `NoSuchMethodError`。

## OpenRewrite 官方能力复用审计

本模块按“先固定官方实现、再证明能否精确复用”的顺序审计。实际构建与测试输入固定如下：

| 上游 | 固定证据 | 结论 |
| --- | --- | --- |
| OpenRewrite Core `8.87.5` | [`rewrite@b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)；`rewrite-core` SHA-256 `a7ff59eebc8072353ec5c3aee3e2033bc69a844b3c9ce2e9be8d4adaec10cbf8`；`rewrite-java` SHA-256 `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | 继续复用官方类型归属、visitor 与 [`JavaTemplate`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/JavaTemplate.java) API；Core 没有能表达本模块完整 decoder 构造器迁移合同的独立配方。 |
| `rewrite-netty` `0.10.3` | [`rewrite-netty@95468246`](https://github.com/openrewrite/rewrite-netty/tree/9546824604ff662eb73bd4cabdd9a9d54bc0ae63)；发布 JAR SHA-256 `f4fc380174c591200c342206d9979da99080dd0e4f13484cced6a572a0c7e1a5` | 官方 catalog 只有 3.2→4.1 与 4.1→4.2 分支聚合，没有 `HttpDecoderConfig` 配方；因此仅作为 test-scope 审计输入，不进入发布物的 runtime recipe tree。 |

以下官方节点经过固定源码和运行时展开检查，但明确不能组合到本模块：

| 排除项 | 排除原因 |
| --- | --- |
| [`org.openrewrite.netty.UpgradeNetty_4_1_to_4_2`](https://github.com/openrewrite/rewrite-netty/blob/9546824604ff662eb73bd4cabdd9a9d54bc0ae63/src/main/resources/META-INF/rewrite/netty-4_1_to_4_2.yml) | 把全部 `io.netty:*` 升到动态 `4.2.x`，同时迁移 incubator IO_uring 坐标、package/type 和 EventLoopGroup；它会越过精确 `netty-codec-http:4.1.136.Final` 目标与十三值白名单。 |
| `org.openrewrite.netty.UpgradeNetty_3_2_to_4_1` | 面向 Netty 3 API，包含 channel/buffer/bootstrap 的整代迁移；本工作簿输入全为 4.1/4.2，应用会扩大无关范围。 |
| [`EventLoopGroupToMultiThreadIoEventLoopGroupRecipes`](https://github.com/openrewrite/rewrite-netty/blob/9546824604ff662eb73bd4cabdd9a9d54bc0ae63/src/main/java/org/openrewrite/java/netty/EventLoopGroupToMultiThreadIoEventLoopGroup.java) | 输出 `MultiThreadIoEventLoopGroup` 和 `*IoHandler` 是 4.2 分支迁移，不存在于 4.1.136 目标合同。 |
| Core/recipe 的通用 `UpgradeDependencyVersion`、`ChangeDependency` | 没有“仅十三个旧版本 + owner/profile/variant/path 隔离”的单节点合同；直接采用会覆盖表外版本或扩大到整个 Netty 家族，不能替换严格本地升级器。 |
| Core 的参数增删/重排、type/method rename | 无法按 4–7 参数 arity 构造 fluent `HttpDecoderConfig`、仅接受字面量 `true`、保留表达式顺序与求值次数并处理 simple-name 冲突；本地 gap recipe 继续使用官方 `JavaTemplate` 实现这一组合变换。 |

`NettyCodecHttpOfficialRecipeReuseTest` 会展开实际运行时配方树：官方 4.2 aggregate 必须能被识别为包含通用依赖升级、incubator dependency 迁移、package/type 和 EventLoopGroup 节点，而本模块推荐入口必须不包含这些节点。这样将来升级官方 bundle 时，宽泛 4.2 能力不会静默进入 4.1.136 配方，也不会破坏禁止降级。

固定官方测试证据包括 [`UpgradeNetty_4_1_to_4_2Test`](https://github.com/openrewrite/rewrite-netty/blob/9546824604ff662eb73bd4cabdd9a9d54bc0ae63/src/test/java/org/openrewrite/java/netty/UpgradeNetty_4_1_to_4_2Test.java)、[`EventLoopGroupToMultiThreadIoEventLoopGroupTest`](https://github.com/openrewrite/rewrite-netty/blob/9546824604ff662eb73bd4cabdd9a9d54bc0ae63/src/test/java/org/openrewrite/java/netty/EventLoopGroupToMultiThreadIoEventLoopGroupTest.java) 与 Core [`JavaTemplateSubstitutionsTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/JavaTemplateSubstitutionsTest.java)。

## 真实公开仓库 fixture

测试不是只根据 README 手写示例；以下用例从固定公开提交中提取并最小化，保留真实 API 形态：

| 固定仓库 | 真实模式 | 本模块验证 |
| --- | --- | --- |
| [Netty `HttpClientCodecTest` @ `fca0764`](https://github.com/netty/netty/blob/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6/codec-http/src/test/java/io/netty/handler/codec/http/HttpClientCodecTest.java#L139) | `new HttpRequestDecoder(4096, 8192, 8192, true)` | 真实四参数 deprecated constructor 自动迁移为 `HttpDecoderConfig` |
| [Apache Dubbo `HttpCommandDecoder` @ `eb1d8ab`](https://github.com/apache/dubbo/blob/eb1d8abaebdc2ce1e15d6236cf9f9179d34e9082/dubbo-plugin/dubbo-qos/src/main/java/org/apache/dubbo/qos/command/decoder/HttpCommandDecoder.java#L37-L82) | `QueryStringDecoder`、`HttpPostRequestDecoder.getBodyHttpDatas()`、`destroy()` | query 与 multipart/lifecycle 风险标记落在真实调用节点 |
| [Apache Dubbo `HttpUtils` @ `eb1d8ab`](https://github.com/apache/dubbo/blob/eb1d8abaebdc2ce1e15d6236cf9f9179d34e9082/dubbo-remoting/dubbo-remoting-http12/src/main/java/org/apache/dubbo/remoting/http12/HttpUtils.java#L220-L227) | `new DefaultHttpHeaders(false)` 与 multipart decoder | 关闭 header validation 的安全 MARK 及 multipart 识别依据 |

JavaTemplate 的类型化替换与测试组织参考上述 OpenRewrite Core `8.87.5` 固定提交；额外测试直接把 `netty-codec-http:4.1.136.Final` 真实 JAR 放入 parser classpath，避免只依赖手写 stub 验证输出。

当前模块执行 **189 个 JUnit invocation**：

- 76 个严格依赖升级：十三版本 Maven/Groovy 矩阵、Kotlin、property/profile/dependencyManagement、所有者/variant/嵌套 DSL/路径负例和幂等；
- 23 个构造器 AUTO：三类型 × 四种 arity、表达式顺序/次数、真实 Netty fixture、真实 4.1.136 target classpath、本地/导入同名 config FQN、安全负例和幂等；
- 36 个构建 MARK：4.2 禁止降级、owner-aware root/profile、sibling 隔离、插件边界、Gradle AST 激活、所有者/variant、Netty family、shade、RFC 9112 和幂等；
- 45 个源码 MARK：validation/parser、multipart/query、aggregation/compression/date、SPDY、4.2-only API、真实 Dubbo fixture、同名负例、路径隔离和幂等；
- 6 个推荐配方组合：descriptor discovery、阶段顺序、4.1 AUTO/4.2 NO_DOWNGRADE、AUTO+MARK 组合和两周期幂等。
- 3 个官方能力审计：固定 catalog 能力、宽泛 4.2 runtime tree 展开、本模块严格 runtime tree 排除断言。

## 使用与验收

在本仓库验证模块：

```bash
mvn -f rewrite-netty-codec-http-upgrade/pom.xml clean verify
```

模块已加入聚合构建后可使用：

```bash
mvn -pl rewrite-netty-codec-http-upgrade -am test
```

在目标项目中先执行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-netty-codec-http-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.nettycodechttp.MigrateNettyCodecHttpTo4_1_136
```

提交迁移前应满足：

1. `4.2.10.Final` 没有被降级，所有 `NO_DOWNGRADE` 均有明确分支决策。
2. dependency tree/insight 最终只解析出预期 Netty 版本，companion、BOM、native artifacts 和 shaded 包已核验。
3. 所有 `SearchResult` 已转化为测试、人工决策或有期限的风险记录，而不是直接删除注释。
4. malformed HTTP、TE+CL、header injection、multipart/query 边界、backpressure、compression/WebSocket、SPDY/reference-count 场景已在真实 pipeline 验证。
5. 使用实际 JDK、TLS/native transport、fat JAR/容器镜像 clean rebuild，并完成 client/server smoke test 与回滚演练。
