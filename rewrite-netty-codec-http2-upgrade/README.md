# Netty `netty-codec-http2` 迁移到 4.1.136.Final

本模块对应 `开源软件升级.xlsx` 中的 `io.netty:netty-codec-http2`。README 是不兼容点规格；配方会执行能够由 AST 证明等价的依赖和 Java 源码改写，并把不能安全猜测的协议、资源与分支兼容决策标记到准确节点，而不是只改版本号。

推荐入口：

```text
com.huawei.clouds.openrewrite.nettycodechttp2.MigrateNettyCodecHttp2To4_1_136
```

仅执行严格依赖升级时使用：

```text
com.huawei.clouds.openrewrite.nettycodechttp2.UpgradeNettyCodecHttp2To4_1_136
```

推荐入口按安全顺序组合四个阶段：

| 阶段 | 配方 | 行为 |
| ---: | --- | --- |
| 1 | `FindNettyCodecHttp2BuildRisks` | 修改前标记目标冲突、版本所有者、Netty 家族、TLS/ALPN 与打包风险 |
| 2 | `UpgradeNettyCodecHttp2To4_1_136` | 只把工作簿批准的十三个 4.1.x 版本升级为 `4.1.136.Final` |
| 3 | `MigrateDeprecatedHttp2Constructors` | 显式化三个有官方等价关系的 deprecated constructor |
| 4 | `FindNettyCodecHttp2SourceRisks` | 标记 header、HPACK、滥用防护、流控、升级、生命周期和 4.2-only API 决策 |

## 版本边界：只升级，不降级

自动配方只接受下表十三个精确 4.1.x 源版本。`4.2.10.Final` 与 `4.2.12.Final` 来自更新的 4.2 分支，因此 `4.1.136.Final` 不是它们的可用升级目标；配方保持原版本不变，并给出精确的“目标版本冲突（禁止降级）”标记。

| 源版本 | 行为 | 固定官方源码 |
| --- | --- | --- |
| `4.1.100.Final` | AUTO | [`58df783`](https://github.com/netty/netty/tree/58df783eb4fc50f95a1061dc4274020d6804caf4) |
| `4.1.101.Final` | AUTO | [`c6a6aad`](https://github.com/netty/netty/tree/c6a6aadaface1b2b66d2608dcdc6e4c04c1648cc) |
| `4.1.108.Final` | AUTO | [`3a3f9d1`](https://github.com/netty/netty/tree/3a3f9d13b129555802de5652667ca0af662f554e) |
| `4.1.109.Final` | AUTO | [`43455df`](https://github.com/netty/netty/tree/43455dfe98202b8dc63d89ac3f04027c3e8f9f54) |
| `4.1.112.Final` | AUTO | [`ebe2aa5`](https://github.com/netty/netty/tree/ebe2aa5b7cd36562a20b024d78ecff47a86874b8) |
| `4.1.118.Final` | AUTO | [`36f95cf`](https://github.com/netty/netty/tree/36f95cfaeed0c1313b21f1b5350c19436ae7fb45) |
| `4.1.124.Final` | AUTO | [`0a74231`](https://github.com/netty/netty/tree/0a742315b9c9c392a28eabdcb95c2740e1729c8e) |
| `4.1.125.Final` | AUTO | [`56ea976`](https://github.com/netty/netty/tree/56ea9763c6ac550f0f8ab7849ef0af21532643cb) |
| `4.1.126.Final` | AUTO | [`2d6a16a`](https://github.com/netty/netty/tree/2d6a16a0cc6522ded6cbba06e938cd32fb0e629e) |
| `4.1.128.Final` | AUTO | [`afae49c`](https://github.com/netty/netty/tree/afae49cd621a22a22139af5c5ee384e8cfd4ae48) |
| `4.1.129.Final` | AUTO | [`1729bf3`](https://github.com/netty/netty/tree/1729bf313c82845096c8b57755858b17f13db34e) |
| `4.1.130.Final` | AUTO | [`41ff1eb`](https://github.com/netty/netty/tree/41ff1eb45a4acc9976150330e105211ecde36399) |
| `4.1.132.Final` | AUTO | [`ec119d4`](https://github.com/netty/netty/tree/ec119d487b3a27e4ac118e7e1d97f0c96a85f4a3) |
| `4.2.10.Final` | **目标版本冲突（禁止降级）+ MARK** | [`4cc9873`](https://github.com/netty/netty/tree/4cc98736c3947bc93122e0b64e0bd8fc970c6437) |
| `4.2.12.Final` | **目标版本冲突（禁止降级）+ MARK** | [`67ce541`](https://github.com/netty/netty/tree/67ce541e4692853e24fc506466960db35bb64914) |
| 目标 `4.1.136.Final` | NO-OP | [`fca0764`](https://github.com/netty/netty/tree/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6) |

目标发布页是 [Netty 4.1.136.Final](https://github.com/netty/netty/releases/tag/netty-4.1.136.Final)。逐一用 JApiCmp 对比十三个 4.1.x JAR 与目标 JAR，没有发现 public binary-incompatible API；这不代表运行时行为不变，因此本模块仍处理 deprecated API，并对协议语义和资源边界做精确 MARK。4.2 两个版本到 4.1 目标存在明确的公开 API 逆向不兼容，见后文。

## AUTO、MARK 与 NO-OP

| 类别 | 本模块实际行为 |
| --- | --- |
| **AUTO** | 严格依赖白名单；三个可证明等价的 constructor 改写 |
| **MARK** | 目标冲突、版本所有权、artifact 变体、Netty 家族、TLS/ALPN、shade、header/HPACK、滥用限制、解压、流控、ACK/preface、h2c/ALPN、HTTP 转换、multiplex、PUSH/CONNECT、encoder 关闭、自定义 codec 和 4.2-only API |
| **NO-OP** | 目标版本、表外版本、4.2 版本、范围/动态/catalog/BOM/platform 管理、共享或重复属性、classifier/非 JAR、插件依赖、嵌套 Gradle DSL、同名业务 API、匿名子类、带求值副作用的待删除参数和生成目录 |

`SearchResult` 是待验证的迁移任务，不是执行失败。相同 marker 在第二个 recipe cycle 不会叠加；依赖、Java AUTO、构建/源码 MARK 和推荐组合均有幂等测试。

## 严格依赖所有权

Maven 只处理根 `project` 或一级 `profile` 的直接 `dependencies` / `dependencyManagement`，且仅处理标准 JAR 形态。直接版本必须精确命中白名单。属性版本只有在以下条件同时成立时才 AUTO：

- 属性定义和使用处属于同一可见 scope；profile 本地定义优先于根定义；
- 定义唯一，且全部引用都只服务于目标依赖的标准 JAR 版本；
- 根属性没有被 profile 同名属性 shadow；
- 值是精确白名单版本，而不是范围、占位、动态或目录/BOM 管理。

profile primary 只激活根和本 profile 的审计，不读取 sibling profile；根 primary 可以审计 profile companion。Maven shade 只认根 project 或一级 profile 的直接 `build/plugins/plugin`，嵌套配置和 plugin dependency 的同名 XML 不触发。

Gradle 只处理根级真实 `dependencies {}` 中的 Groovy/Kotlin 字符串坐标，以及 Groovy `group/name/version` Map。`buildscript`、`subprojects`、`allprojects`、`project(...)`、`constraints`、version catalog、platform、插值、带 receiver 的自定义调用、classifier/ext/type 和嵌套 lookalike 均不猜测修改。Gradle shade 标记只认顶层 `shadowJar { relocate "io.netty", ... }`。

`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、`.m2`、IDE/cache/report、`node_modules` 等产物目录被排除。

## 已自动处理的 Java 迁移

所有改写要求 OpenRewrite 类型归属精确命中 `io.netty.handler.codec.http2`，匿名子类和同名业务类型不改。

### `DefaultHttp2ConnectionDecoder`

目标源码中的 deprecated 六参数构造器明确委托给七参数构造器并传入 `validateHeaders=true`（[固定源码](https://github.com/netty/netty/blob/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6/codec-http2/src/main/java/io/netty/handler/codec/http2/DefaultHttp2ConnectionDecoder.java)）。配方把这个隐含默认值显式化：

```java
// before
new DefaultHttp2ConnectionDecoder(connection, encoder, reader, verifier,
        autoAckSettings, autoAckPing);

// after
new DefaultHttp2ConnectionDecoder(connection, encoder, reader, verifier,
        autoAckSettings, autoAckPing, true);
```

原参数顺序、表达式和求值次数不变。七/八参数 overload 不改；`validateRequiredPseudoHeaders` 的选择另行 MARK。

### `DelegatingDecompressorFrameListener`

目标 deprecated 两参数和三参数 `boolean strict` 构造器均明确委托到新 overload，`maxAllocation=0` 表示“不限制”（[固定源码](https://github.com/netty/netty/blob/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6/codec-http2/src/main/java/io/netty/handler/codec/http2/DelegatingDecompressorFrameListener.java)）。配方保留旧行为并显式加 `0`：

```java
new DelegatingDecompressorFrameListener(connection, listener);
// becomes
new DelegatingDecompressorFrameListener(connection, listener, 0);

new DelegatingDecompressorFrameListener(connection, listener, strict);
// becomes
new DelegatingDecompressorFrameListener(connection, listener, strict, 0);
```

`0` 只是等价迁移，不是生产安全预算；配方同时在构造节点 MARK，要求业务根据压缩炸弹威胁模型决定有限上限。

### `DefaultHttp2HeadersDecoder`

目标源码把三参 overload 的 `initialHuffmanDecodeCapacity` 明确记为 “does nothing”（[固定源码](https://github.com/netty/netty/blob/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6/codec-http2/src/main/java/io/netty/handler/codec/http2/DefaultHttp2HeadersDecoder.java)）。只有第三个参数是无可观察求值的整数 `J.Literal` 时，配方才删除它：

```java
new DefaultHttp2HeadersDecoder(true, 8192L, 32);
// becomes
new DefaultHttp2HeadersDecoder(true, 8192L);
```

变量、方法调用、递增、cast 等保留，避免删除副作用。`(boolean, boolean, long)` 等语义不同的 overload 不改。

## 必须复核的不兼容语义

### Header validation、pseudo-header 与 HPACK

4.1.136 对非法 header name/value、pseudo-header 布局和 trailer 更严格。配方标记 `DefaultHttp2Headers*`、`DefaultHttp2HeadersDecoder`、`DefaultHttp2ConnectionDecoder`、builder validation 开关及 `Http2Headers.add/set/path/...`。至少覆盖：

- 大小写、非 token 字符、NUL/CR/LF、重复和 trailer pseudo-header；
- request、response、PUSH_PROMISE、普通 CONNECT 与 extended CONNECT 的必需 pseudo-header；
- `SETTINGS_MAX_HEADER_LIST_SIZE`、HPACK table、CONTINUATION 分片和 GOAWAY 阈值；
- validation 为 `false` 或运行时值时的拒绝策略。

固定依据包括 [拒绝非 token header name](https://github.com/netty/netty/commit/d7dcf6c11a9767ba90edc1006a9226674c9fbf76)、[required pseudo-header validation](https://github.com/netty/netty/commit/a1e9aaccbc219c0f0c53ae470d6d5a159fa96acc)、[lowercase pseudo-header](https://github.com/netty/netty/commit/3b81f2c294ea81a4f2bb959d2797aa523dcb2055)、[trailer pseudo-header](https://github.com/netty/netty/commit/2657079302ef7a63a002feccc1492418c0e6934a)、[header-state 修复](https://github.com/netty/netty/commit/b611fdebdf7787c214732991fb9142662f2583eb) 和 [headers encoder 资源修复](https://github.com/netty/netty/commit/73dc1e671b6f226703bc0bb771c12e671dd2d540)。

### Rapid Reset、控制帧与并发流限制

配方标记 `decoder/encoderEnforceMax*`、`maxConcurrentStreams`、`maxReservedStreams`、queued control frames、empty DATA、small CONTINUATION 等调用。升级必须用生产阈值验证 RST window、SETTINGS 变化、拒绝码、GOAWAY、stream buffer 和正常高并发误杀。固定依据包括 [max concurrent streams enforcement](https://github.com/netty/netty/commit/f5da73e6fcde976020b336dc7d44808f61b3e70c)、[默认并发流 100](https://github.com/netty/netty/commit/f89176a7b6097d5ab4c254b2b0a5b52c6d13f949)、[CONTINUATION 限制](https://github.com/netty/netty/commit/9f47a7b6846e6c7cb0481789be51788944042b85)、[RST encoder 配置](https://github.com/netty/netty/commit/b953048ca6abd31f5463f10dc0a1fee66914e9ef) 和 [安全 builder 开关](https://github.com/netty/netty/commit/717ea7e555af870b6ede25596c9d6a5ecd0ef7f4)。

### 解压、流控与 ByteBuf 生命周期

配方标记 decompressor、local/remote flow controller、`StreamBufferingEncoder`、window 设置和 `AUTO_STREAM_FLOW_CONTROL`。至少验证：

- 有限 `maxAllocation` 下的 gzip/deflate bomb、padding、reset/remove 和异常清理；
- connection/stream window、manual consume、`AUTO_READ=false`、child writability 和 cancellation；
- queued DATA/control frame 在 close、RST、GOAWAY 与 handler removal 后恰好释放一次。

固定依据包括 [公开 maxAllocation](https://github.com/netty/netty/commit/a155024263510afba66f19e727b70a00e5cfc0af)、[decompressor cleanup](https://github.com/netty/netty/commit/db6138b168699736a6463c367e12ad0a4c36a25e)、[decompression padding](https://github.com/netty/netty/commit/2387f3217439c24d90f68702ec5733d8ac6498fe)、[manual window update](https://github.com/netty/netty/commit/35cf6ec2bb9ae63a7776cc373a7abed897346739)、[coalescing queue/stuck DATA](https://github.com/netty/netty/commit/26bb273a2c6ea6c0cf79bf0cdae022bf7d886d4d) 和 [buffer leak mapping](https://github.com/netty/netty/commit/1cfd3a62ca8633cc6d1729222214c64c5b50fd89)。

### SETTINGS/PING ACK、preface 与关闭

`autoAckSettingsFrame`、`autoAckPingFrame`、`writeSettingsAck`、`flushPreface`、`decoupleCloseAndGoAway` 和 graceful timeout 会被 MARK。验证 client preface 是首批 outbound bytes、manual ACK 次序、未 ACK SETTINGS、PING、close race、GOAWAY last-stream-id 和 timeout。固定依据包括 [preface ordering](https://github.com/netty/netty/commit/503e720f2365dae5bedde511a1e43f31a80e7c3b)、[preface flush](https://github.com/netty/netty/commit/60c1c9f477e6d38a6cdac589064e382b2ecd8ab8)、[auto ACK ping](https://github.com/netty/netty/commit/64c52255c073d4a308d3dbb56a12d3cca7692c47) 和 [StreamBuffering SETTINGS ACK](https://github.com/netty/netty/commit/0503b50a32ebdb576e3064f67b01081665f0f15e)。

### h2c、TLS/ALPN 与 HTTP/1↔HTTP/2 转换

配方标记 client/server upgrade codec、`ApplicationProtocolNegotiationHandler`、`Http2SecurityUtil`、HTTP conversion/adapter/aggregation。构建侧同时标记 tcnative、Conscrypt、Jetty ALPN 和 `io.netty` relocation。至少验证 ALPN h2/fallback、h2c upgrade headers/settings、prior knowledge、handler 顺序/移除、absolute/origin/CONNECT path、cookie、413 stream error 和最终 fat JAR/native classifier。固定依据包括 [path parsing](https://github.com/netty/netty/commit/a220e65b4579cfa51b46870aba633e37ac716fe4)、[maxContentLength stream error](https://github.com/netty/netty/commit/d8dd8052918979fd1562bd40c7c959bd70ccfae7) 和 [invalid cookie](https://github.com/netty/netty/commit/a06dd26683d5a30b94bb1422cd38d32561be7a7c)。

### Multiplex、unknown/priority frame、PUSH 与 CONNECT

`Http2FrameCodec`、multiplex codec/handler、stream channel、unknown/priority frame、PUSH_PROMISE、verifier、`pushEnabled`、`connectProtocolEnabled` 和 CONNECT settings field 会被 MARK。验证真实 stream id、event routing、promised stream association、handler sharability、disabled push、extended CONNECT negotiation 和所有 reference count。固定依据包括 [real stream id](https://github.com/netty/netty/commit/d01673aeb2e979ca88495b69a130aee88c951093)、[unknown frame API](https://github.com/netty/netty/commit/a70829f17a8fd59910cd8beaafce3b94fc06108f)、[priority event](https://github.com/netty/netty/commit/d1f22c2235ba912ac83715a3341ea98d0a3fa12b)、[PUSH_PROMISE association](https://github.com/netty/netty/commit/13ff5ca75f33a8e4a129584b417949c817244007) 和 [CONNECT setting default](https://github.com/netty/netty/commit/b3f213ea9820236d0befe50a8d55c7d52b328483)。

### `DefaultHttp2HeadersEncoder` 和自定义 codec

目标中的 `DefaultHttp2HeadersEncoder` 实现 `AutoCloseable` 并持有 HPACK encoder 状态。构造和 `close()` 均会 MARK，要求 owner 在正常、构造失败、handler removal 和 connection shutdown 路径恰好关闭一次。继承 HTTP/2 builder、handler、frame listener 的代码也会 MARK，要求在 4.1.136 上重新编译，并验证 protected override、user event、error propagation 和 release。

## 4.2 中存在、4.1.136 中不可用的公开 API

JApiCmp 对 `4.2.10.Final`、`4.2.12.Final` 到目标执行逆向比较时，两者得到相同的不兼容项。配方精确按类型和签名 MARK，不提供伪造替换：

| 4.2 API | 4.1.136 状态 | 本模块 |
| --- | --- | --- |
| `DefaultHttp2Headers.defaultHtt2NameValidator()` | 不存在 | 调用和 method reference 标记 `BRANCH_API` |
| `DefaultHttp2Headers.defaultHttp2ValueValidator()` | 不存在 | 调用和 method reference 标记 `BRANCH_API` |
| `Http2ServerUpgradeCodec(String, Http2ConnectionHandler, ChannelHandler...)` | 构造器是 private | 精确三参数签名标记 `BRANCH_API` |

官方演进依据是 [validator 公开](https://github.com/netty/netty/commit/467c432601ed65a20af202d1a1acc87c77d4bfc9)、[validator accessor 调整](https://github.com/netty/netty/commit/85a3a0ee8d67bac9f6a85aa6444e0805564fffc6) 和 [upgrade constructor 公开](https://github.com/netty/netty/commit/e42c05ca3c9265cb73e874171c682bb278b08b1f)。若项目使用这些 API，正确处理是保留 4.2 并指定 4.2 的前向目标，不能删除调用后强行“兼容”4.1。

## 真实公开仓库 fixture

测试从固定公开提交提取最小片段，保留真实调用链和风险形态：

| 固定仓库 | 真实模式 | 本模块验证 |
| --- | --- | --- |
| [Apache CXF `NettyHttpClientPipelineFactory` @ `eca4abb`](https://github.com/apache/cxf/blob/eca4abb9966e2d2eb5e987a080d059bf9e0ff47a/rt/transports/http-netty/netty-client/src/main/java/org/apache/cxf/transport/http/netty/client/NettyHttpClientPipelineFactory.java) | 两参数 decompressor 包裹 inbound adapter | AUTO 显式加 `maxAllocation=0`，并 MARK 解压/转换预算 |
| [Aleph `AlephHttp2FrameCodecBuilder` @ `b1a2522`](https://github.com/clj-commons/aleph/blob/b1a2522f4e11c4b6a8423cebf66031f9d8fced02/src-java/aleph/http/AlephHttp2FrameCodecBuilder.java) | 自定义 frame codec builder 和两参数 decompressor | AUTO + custom codec/decompression MARK |
| [Vert.x `Http2CustomFrameCodecBuilder` @ `3ef9c8f`](https://github.com/eclipse-vertx/vert.x/blob/3ef9c8fef187a4a604953f0914e76de97cf28a45/vertx-core/src/main/java/io/vertx/core/http/impl/http2/multiplex/Http2CustomFrameCodecBuilder.java) | 自定义 builder、RST/CONTINUATION 设置、有限解压 | custom/abuse/decompression 精确 MARK |
| [Vert.x `Http2MultiplexServerChannelInitializer` @ `3ef9c8f`](https://github.com/eclipse-vertx/vert.x/blob/3ef9c8fef187a4a604953f0914e76de97cf28a45/vertx-core/src/main/java/io/vertx/core/http/impl/http2/multiplex/Http2MultiplexServerChannelInitializer.java) | server builder 安全设置与 h2c upgrade | abuse、ACK、upgrade pipeline MARK |
| [Netflix Zuul `Http2OrHttpHandler` @ `9bea4a7`](https://github.com/Netflix/zuul/blob/9bea4a7244711104e965214b99152baee065ede8/zuul-core/src/main/java/com/netflix/zuul/netty/server/http2/Http2OrHttpHandler.java) | ALPN、settings、validation、RST/CONTINUATION | validation、limits、CONNECT、ALPN MARK |
| [Armeria `Http2ServerConnectionHandlerBuilder` @ `04fbb67`](https://github.com/line/armeria/blob/04fbb6725d1c86162ec1444efc70435015859e30/core/src/main/java/com/linecorp/armeria/server/Http2ServerConnectionHandlerBuilder.java) | Rapid Reset decoder window | abuse-limit MARK |
| [grpc-java `NettyClientHandler` @ `0585d48`](https://github.com/grpc/grpc-java/blob/0585d481a705a6d22d1dfecfb94b888bdfaaba45/netty/src/main/java/io/grpc/netty/NettyClientHandler.java) | connection decoder、flow/header settings | validation、ACK、flow/header MARK |

测试结构固定参考与本工程 OpenRewrite `8.87.5` 对应提交中的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)、[Maven `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-maven/src/test/java/org/openrewrite/maven/UpgradeDependencyVersionTest.java) 和 [Gradle `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-gradle/src/test/java/org/openrewrite/gradle/UpgradeDependencyVersionTest.java)：before→after、精确 marker、类型归属、真实 AST scope、NO-OP 边界、recipe discovery 和 two-cycle idempotency 都必须通过。

当前执行 **257 个 JUnit invocation**：

- 83 个严格依赖升级：十三版本 Maven/Groovy 矩阵、Kotlin/Map、属性/profile/dependencyManagement、所有权/variant/嵌套 DSL/路径边界和幂等；
- 41 个构建 MARK：两个 4.2 目标冲突、root/profile 可见性、owner/family/TLS/shade、真实 Maven/Gradle AST 激活和误报边界；
- 18 个 Java AUTO：三个 constructor、求值顺序、真实最早版本 JAR、CXF/Aleph fixture、side-effect/同名/匿名/生成目录负例和幂等；
- 107 个源码 MARK：全部 marker 类别与具体 API、4.2-only API、CXF/Vert.x/Zuul fixture、精确类型反例、生成目录和两周期幂等；
- 8 个推荐配方组合：descriptor discovery、阶段顺序、4.1 AUTO、两个 4.2 禁止降级、AUTO+MARK 与组合幂等。

## 使用与验收

本仓库内验证：

```bash
mvn -f rewrite-netty-codec-http2-upgrade/pom.xml clean verify
```

模块加入聚合构建后可使用：

```bash
mvn -pl rewrite-netty-codec-http2-upgrade -am test
```

目标项目先执行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-netty-codec-http2-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.nettycodechttp2.MigrateNettyCodecHttp2To4_1_136
```

提交迁移前应满足：

1. `4.2.10.Final`、`4.2.12.Final` 均未降级，每个目标冲突已有明确的前向分支方案。
2. dependency tree/insight 只解析预期 Netty family；BOM、native TLS provider、classifier 和 shaded 最终包已核验。
3. 所有 `SearchResult` 已转化为测试、人工决策或有期限的风险记录，而不是直接删除。
4. malformed headers/pseudo-headers、HPACK/CONTINUATION、Rapid Reset、decompression、flow control、ACK/preface、h2c/ALPN、multiplex、PUSH/CONNECT 和 ByteBuf lifecycle 已在真实 pipeline 压测。
5. 使用实际 JDK、TLS/native transport、fat JAR/容器镜像 clean rebuild，并完成 HTTP/2 client/server smoke test、指标对比和回滚演练。
