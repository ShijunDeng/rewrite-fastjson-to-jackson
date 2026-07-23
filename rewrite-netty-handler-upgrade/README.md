# Netty Handler 升级到 4.1.136.Final

本模块把清单中 **20 个较低的 Netty 4.1 `io.netty:netty-handler` 版本**自动升级到
`4.1.136.Final`，同时处理可证明等价的源码 API 修改，并把 TLS、SNI、ALPN、native
生命周期、timeout、流控和 pipeline 等不能脱离业务上下文自动决定的问题标到准确节点。

它坚持“只升级、不降级”：`4.2.x`、`4.1.137+`、未来主版本以及任何高于目标的版本都保持
原样，并产生包含精确短语 **`目标版本冲突（禁止降级）`** 的 MARK。不存在
`4.2 → 4.1` 回退路径。

## 配方入口与执行顺序

| 配方 | 用途 |
| --- | --- |
| `com.huawei.clouds.openrewrite.nettyhandler.UpgradeNettyHandlerTo4_1_136` | 仅执行严格的依赖/BOM 白名单升级 |
| `com.huawei.clouds.openrewrite.nettyhandler.MigrateNettyHandlerTo4_1_136` | 推荐入口：依赖升级、禁降级审计、AUTO、源码与配置 MARK |

推荐入口按以下顺序执行：

1. `UpgradeSelectedNettyHandlerDependency`：先升级精确白名单，避免把正常旧版本误报为风险；
2. `FindNettyHandler41136BuildRisks`：检查版本 owner、禁止降级、Netty 家族偏斜、native/TLS 和打包；
3. `MigrateDeprecatedNettyHandlerApis`：迁移 `RuleBasedIpFilter` 的 deprecated 构造器；
4. 带 authored-source 前置条件的官方 `org.openrewrite.java.AddLiteralMethodArgument`：迁移
   `SslHandler.isEncrypted(ByteBuf)`；
5. `FindNettyHandler41136SourceRisks`：标记 handler 行为与 4.2-only API；
6. `FindNettyHandlerConfigurationRisks`：标记 Properties/YAML 中的 Netty/JDK TLS 设置。

所有 visitor 都忽略常见生成物、缓存与安装目录。AUTO 和 MARK 均有两周期幂等测试。

## 版本白名单与不可变发布证据

下表使用 Netty tag 解引用后的固定 commit，不依赖可移动分支。
目标 `netty-handler:4.1.136.Final` 已从 Maven Central 重新下载并校验：
JAR SHA-256 `54bb1a59f46a3aefd117942e6acee09e555b756776bad731fc06159d89c2da28`，
POM SHA-256 `584c16cb512756a16101bade5a62ce51dba26b4150b33aa705dd887da5f0e26e`。

| 输入版本 | 动作 | Netty 固定源码 |
| --- | --- | --- |
| `4.1.49.Final` | AUTO | [`d0ec961`](https://github.com/netty/netty/tree/d0ec961cce19646519d6a0d59e7604b0eacd9bf2) |
| `4.1.63.Final` | AUTO | [`b78d8f2`](https://github.com/netty/netty/tree/b78d8f2abda50fbc0ff3f177d72d1a0b05b5ff41) |
| `4.1.100.Final` | AUTO | [`58df783`](https://github.com/netty/netty/tree/58df783eb4fc50f95a1061dc4274020d6804caf4) |
| `4.1.101.Final` | AUTO | [`c6a6aad`](https://github.com/netty/netty/tree/c6a6aadaface1b2b66d2608dcdc6e4c04c1648cc) |
| `4.1.107.Final` | AUTO | [`1908d3a`](https://github.com/netty/netty/tree/1908d3a8b02b6ebd00b6c3c0f60cf31a8e31d2ca) |
| `4.1.108.Final` | AUTO | [`3a3f9d1`](https://github.com/netty/netty/tree/3a3f9d13b129555802de5652667ca0af662f554e) |
| `4.1.109.Final` | AUTO | [`43455df`](https://github.com/netty/netty/tree/43455dfe98202b8dc63d89ac3f04027c3e8f9f54) |
| `4.1.115.Final` | AUTO | [`04f9b4a`](https://github.com/netty/netty/tree/04f9b4a827d992ad439823eeba85d65d3a89c265) |
| `4.1.118.Final` | AUTO | [`36f95cf`](https://github.com/netty/netty/tree/36f95cfaeed0c1313b21f1b5350c19436ae7fb45) |
| `4.1.119.Final` | AUTO | [`fb7c786`](https://github.com/netty/netty/tree/fb7c786f2df57867bcfe049b13a42e764606f0d5) |
| `4.1.121.Final` | AUTO | [`8c9155c`](https://github.com/netty/netty/tree/8c9155cef8805cc34862817b60befa948cf6f1a5) |
| `4.1.124.Final` | AUTO | [`0a74231`](https://github.com/netty/netty/tree/0a742315b9c9c392a28eabdcb95c2740e1729c8e) |
| `4.1.125.Final` | AUTO | [`56ea976`](https://github.com/netty/netty/tree/56ea9763c6ac550f0f8ab7849ef0af21532643cb) |
| `4.1.126.Final` | AUTO | [`2d6a16a`](https://github.com/netty/netty/tree/2d6a16a0cc6522ded6cbba06e938cd32fb0e629e) |
| `4.1.127.Final` | AUTO | [`6bb1a6b`](https://github.com/netty/netty/tree/6bb1a6bcae503423343b9155ac48b161f60368b5) |
| `4.1.128.Final` | AUTO | [`afae49c`](https://github.com/netty/netty/tree/afae49cd621a22a22139af5c5ee384e8cfd4ae48) |
| `4.1.129.Final` | AUTO | [`1729bf3`](https://github.com/netty/netty/tree/1729bf313c82845096c8b57755858b17f13db34e) |
| `4.1.130.Final` | AUTO | [`41ff1eb`](https://github.com/netty/netty/tree/41ff1eb45a4acc9976150330e105211ecde36399) |
| `4.1.132.Final` | AUTO | [`ec119d4`](https://github.com/netty/netty/tree/ec119d487b3a27e4ac118e7e1d97f0c96a85f4a3) |
| `4.1.133.Final` | AUTO | [`fb13125`](https://github.com/netty/netty/tree/fb13125f135ab53203513ff603872a3abe84d38d) |
| `4.2.1.Final` | **目标版本冲突（禁止降级）**；不修改 | [`72d0cce`](https://github.com/netty/netty/tree/72d0cce1ac3b7006eb78d42eeb56855098ccfb4a) |
| `4.2.10.Final` | **目标版本冲突（禁止降级）**；不修改 | [`4cc9873`](https://github.com/netty/netty/tree/4cc98736c3947bc93122e0b64e0bd8fc970c6437) |
| 目标 `4.1.136.Final` | NO-OP | [`fca0764`](https://github.com/netty/netty/tree/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6) |

白名单之外的较低固定版本不会被“顺手”升级，而是 MARK 为超出已批准输入范围。这样清单变化必须经过
显式 review，配方不会静默扩大影响面。

## 官方能力复用审计

审计基线是 OpenRewrite Core `8.87.5` 的固定 commit
[`b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)
和 `rewrite-netty 0.10.3` 的固定 tag
[`9546824`](https://github.com/openrewrite/rewrite-netty/tree/9546824604ff662eb73bd4cabdd9a9d54bc0ae63)。

| 官方能力 | 审计结论 | 本模块处理 |
| --- | --- | --- |
| [`org.openrewrite.netty.UpgradeNetty_4_1_to_4_2`](https://github.com/openrewrite/rewrite-netty/blob/9546824604ff662eb73bd4cabdd9a9d54bc0ae63/src/main/resources/META-INF/rewrite/netty-4_1_to_4_2.yml) | 目标是最新 4.2，并对 `io.netty:*` 统一升级；与本任务的精确 4.1.136 目标相反 | 不组合，避免把本任务改成 4.2 分支迁移 |
| 官方 `UpgradeDependencyVersion` / `ChangeDependency` | 能升级 Maven/Gradle，但不能同时表达“仅 20 个旧版本”、root/profile owner 隔离、独占属性、仅本地 BOM 和所有未来版本精确 MARK | 依赖层使用自研严格 visitor |
| [`AddLiteralMethodArgument`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/AddLiteralMethodArgument.java) | 可精确匹配一参数 `SslHandler.isEncrypted(ByteBuf)` 并在索引 1 加布尔字面量 | **组合复用**，配置 `literal:false`、`primitiveType:boolean`；外层仅加 authored-source precondition，防止显式输入的生成物被改写 |
| 同一官方 recipe 的 constructor 支持 | 可处理简单 constructor，但本模块还要求 expanded varargs、源码 prefix、constructor type、生成目录边界全部稳定 | `RuleBasedIpFilter` 保留小型 typed visitor；测试覆盖数组、expanded varargs、求值顺序和格式 |
| OpenRewrite Netty 4.1 patch API recipe | 官方 catalog 当前没有 4.1.x patch 内的 Handler/TLS/SNI/timeout 迁移 | 本模块补足两项等价 AUTO 和精确 MARK |

组合测试直接激活推荐 YAML，证明官方 `AddLiteralMethodArgument` 与自研 constructor recipe 同时生效，
并证明两者都不会修改 `target/generated-sources`。
测试结构参考官方固定提交的
[`AddLiteralMethodArgumentTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/AddLiteralMethodArgumentTest.java)：
使用类型归属、before/after、真实 parser classpath 和多周期幂等，而不是字符串替换。

## AUTO：真正自动修改的源码

### `SslHandler.isEncrypted`

```java
// before
SslHandler.isEncrypted(buffer)

// after
SslHandler.isEncrypted(buffer, false)
```

Netty 固定提交 [`dc30c33`](https://github.com/netty/netty/commit/dc30c3337a) 表明旧方法直接委托
`isEncrypted(buffer, false)`；新参数为 `false` 还避免探测 SSLv2 时的误报。因此该修改保持参数只求值一次，
并由 OpenRewrite 官方 `AddLiteralMethodArgument` 完成。

### `RuleBasedIpFilter`

```java
// before
new RuleBasedIpFilter(rules)

// after
new RuleBasedIpFilter(true, rules)
```

固定提交 [`74b2fcf`](https://github.com/netty/netty/commit/74b2fcf85724fc955fd4562ff3ef0bb703afa287)
显示 deprecated 构造器的实现就是 `this(true, rules)`。配方同时支持数组和 expanded varargs：

```java
new RuleBasedIpFilter(first, second)
// becomes
new RuleBasedIpFilter(true, first, second)
```

原有 rule 表达式仍按原顺序、各求值一次。自动显式化 `true` 不代表策略已经安全；同一节点仍会 MARK，
要求确认“无规则命中时允许”是否符合业务安全策略。

## 依赖与版本 owner 边界

### Maven

AUTO 仅处理项目或 profile 直接 `dependencies` / `dependencyManagement` 中的标准 JAR：

- 固定版本必须精确命中 20 个白名单之一；
- `${property}` 仅在定义唯一、引用全部归属于本模块的 handler/BOM、且 root/profile 没有同名歧义时修改；
- 版本省略时，仅识别明确的本地 imported `io.netty:netty-bom` owner：唯一 root BOM，或声明
  handler 的 profile 内唯一 BOM；root/profile 混合和 sibling profile 歧义不猜测；
- BOM 只有在当前 POM 确实声明 `netty-handler` 时才会升级；
- classifier、非 JAR、插件依赖、外部 parent/BOM、range 和无法解析的属性只 MARK，不猜测 owner。

### Gradle Groovy/Kotlin

AUTO 支持 root `dependencies` 中的：

- 字符串坐标；
- Groovy map 和 map literal；
- `platform("io.netty:netty-bom:...")` / `enforcedPlatform(...)`，但前提是同一 root scope 有 handler；
- Kotlin DSL 字符串坐标。

`buildscript`、`subprojects`、`allprojects`、嵌套 `project`、constraints、自定义 DSL、version catalog、
动态模板、classifier 和 `@extension` 不会被越权改写。

### Netty 家族对齐

`netty-handler` 与 `netty-common`、`netty-buffer`、`netty-transport`、`netty-codec` 及其他
`netty-*` 模块共享二进制和内部契约。发现不一致的 companion 会在准确依赖节点 MARK；建议用单一
`netty-bom:4.1.136.Final` 对齐并核对 Maven dependency tree / Gradle dependency insight。
独立版本线的 `netty-tcnative*` 不按 4.1.136 强行对齐，而作为 native/TLS 集成单独审计。

## 不兼容点与验证要求

### TLS 协议、provider、hostname 与 session

配方标记 `SslContextBuilder`、`SslContext.newHandler`、`SslHandler`、JDK/OpenSSL context、
protocol/cipher、endpoint identification、startTLS、OCSP、session cache 和 handshake/close timeout。

必须验证：

- TLSv1.2/TLSv1.3 成功，TLSv1/TLSv1.1/SSL 按政策拒绝；Netty 固定提交
  [`00fd4ea`](https://github.com/netty/netty/commit/00fd4ea2647ae3551508654ddff7dfb7e2ab0e61)
  已默认禁用 TLSv1/TLSv1.1；
- client `newHandler(alloc, peerHost, peerPort)` 的 peer host 与 endpoint identification 一致；
- JDK、OpenSSL、Conscrypt 的协议/cipher/ALPN 结果一致或差异已获批准；
- session cache、ticket、resumption、证书轮换、OCSP、renegotiation、handshake 和 close-notify timeout；
- startTLS 在首个明文消息、TLS handler 插入和失败关闭路径没有泄漏或重排。

### SNI 与 ClientHello

`SniHandler`、`AbstractSniHandler` 和 `SslClientHelloHandler` 会 MARK。固定提交
[`829c885`](https://github.com/netty/netty/commit/829c885a45) 为 ClientHello 设置约 64 KiB 长度和
10 秒握手超时的合理默认值；[`b602133`](https://github.com/netty/netty/commit/b6021330a7)
收紧 SNI hostname 验证；[`0bcc6c8`](https://github.com/netty/netty/commit/0bcc6c8a5d)
引入 SNI handshake timeout。

测试必须覆盖合法/非法 hostname、fragmented ClientHello、超长输入、慢速输入、异步 mapping 成功/失败、
timeout、handler removal 与连接关闭。

### Trust manager、pinning 与证书

以下内容会 MARK：

- `InsecureTrustManagerFactory.INSTANCE`；
- `SslContextBuilder.trustManager(...)`；
- `FingerprintTrustManagerFactory` 的旧 constructor；
- 自签名证书和 4.2-only builder。

旧 fingerprint constructor 隐含 SHA-1，配方不能在不知道现有 fingerprint 编码和轮换方案时安全改成
SHA-256，所以不会伪造 AUTO。迁移必须确认 CA/hostname/pin 三层责任、双 pin 轮换窗口、过期和撤销行为。

### Native OpenSSL 与资源生命周期

`OpenSsl`、`ReferenceCountedOpenSsl*`、native engine、tcnative 依赖和 shade/relocation 会 MARK。
验证 native classifier、CPU/OS、service loading、delegated task、key manager factory、BIO buffer、
retain/release 对称性、event-loop shutdown 和 leak detector。最终发布包必须做真实 TLS smoke test，
不能只依赖单元测试 classpath。

### ALPN 与动态 pipeline

`ApplicationProtocol*`、ALPN/NPN negotiator 及 `ChannelPipeline.add/remove/replace` 会 MARK。
固定修复 [`c423891`](https://github.com/netty/netty/commit/c423891fb5) 与
[`f229562`](https://github.com/netty/netty/commit/f2295628e9) 涉及协议协商 handler 移除/排空顺序。
测试 negotiated、unsupported、failure、no-protocol、handler replacement、剩余消息和 reference count。

### Idle/timeout

`IdleStateHandler`、`ReadTimeoutHandler`、`WriteTimeoutHandler` 及 reset 调用会 MARK。
[`f254226`](https://github.com/netty/netty/commit/f2542264c1) 引入 reset API，
[`4e7dc11`](https://github.com/netty/netty/commit/4e7dc11d90) 修复 reset 后 first-event 标志，
[`5d41611`](https://github.com/netty/netty/commit/5d41611b79) 修复 write-timeout executor/NPE 路径。
需覆盖 reader/writer/all-idle、manual reset、pending write、取消、event-loop 顺序和 handler removal。

### Traffic shaping 与 flow control

traffic shaping 和 `FlowControlHandler` 会 MARK。固定修复
[`d6be98b`](https://github.com/netty/netty/commit/d6be98bc7b) 在 close 时失败 promise 并释放队列中的
reference-counted 消息。测试 queue limit、accounting、channel writability、`AUTO_READ=false`、
manual read、re-entrancy、close/remove 和所有 ByteBuf reference count。

### IP filter

除了显式化 default accept，仍需验证 rule 顺序、首个 `null` rule、IPv4/IPv6、unresolved address、
default allow/deny、拒绝事件与 channel close。历史修复
[`e051b5f`](https://github.com/netty/netty/commit/e051b5f338) 改变了 null-rule 终止语义，因此不能仅凭
构造器编译通过判断安全策略正确。

### Proxy、logging、PCAP 与 chunked write

- proxy：认证、DNS/address、timeout、partial response、拒绝、handler removal、凭据脱敏；
- `LoggingHandler`：level、`ByteBufFormat`、payload/credential 泄漏和分配成本；
- `PcapWriteHandler`：builder、shared stream、global header、pause/resume/close、并发和敏感数据保管；
- `ChunkedWriteHandler`：backpressure、suspend/resume、zero write、discard、close/remove、progress promise
  与 reference count。

### 4.2-only API

在目标 `4.1.136.Final` 不存在、但在 4.2 输入可能出现的下列 API 会精确 MARK
**`目标版本冲突（禁止降级）`**：

- `SslContextBuilder.serverName(SNIServerName)`；
- `SelfSignedCertificate.builder()` / `SelfSignedCertificate.Builder`；
- `OpenSsl.isRenegotiationSupported()`；
- `OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES`。

配方不会寻找语义不明的“4.1 替代品”。使用这些 API 的工程必须选择向前的 4.2 目标，而不是强行回到
4.1.136。

## 配置审计

Properties 与嵌套/扁平 YAML 中以下 key 会 MARK：

- `io.netty.handler.ssl.*`：OpenSSL 开关、engine、key manager factory、delegated tasks、session cache、
  BIO buffer、Conscrypt allocator、自签名 key strength；
- `jdk.tls.*`：client/server protocol、named groups、session ticket、ephemeral DH；
- `javax.net.ssl.sessionCacheSize`。

构建脚本/POM 中的同类 JVM 参数也会 MARK。需要在最终容器/JVM 上确认“实际生效值”，因为 CI 的
测试 JVM 与生产镜像可能加载不同 provider、security policy 和 native library。

## 真实仓库固定提交用例

| 固定来源 | 抽取行为 | 覆盖结果 |
| --- | --- | --- |
| [Apache Flume `AvroSource` @ `579b77c`](https://github.com/apache/logging-flume-rpc/blob/579b77c28000e19c3ee10aca1677202dfa951f72/flume-rpc-avro/src/main/java/org/apache/flume/rpc/avro/source/AvroSource.java) | `new RuleBasedIpFilter(rules.toArray(...))` | 自研 typed AUTO 加 `true`，保持数组求值 |
| [Apache Dubbo `SslServerTlsHandler` @ `eb1d8ab`](https://github.com/apache/dubbo/blob/eb1d8abaebdc2ce1e15d6236cf9f9179d34e9082/dubbo-remoting/dubbo-remoting-netty4/src/main/java/org/apache/dubbo/remoting/transport/netty4/ssl/SslServerTlsHandler.java) | `SslHandler.isEncrypted(buf)`、`SslContext.newHandler`、pipeline 插入 | 官方 AUTO 加 `false`；TLS/pipeline MARK |
| [Apache RocketMQ `ProxyAndTlsProtocolNegotiator` @ `577b89f`](https://github.com/apache/rocketmq/blob/577b89f2cdddf0d42cfd3c1b6effdac0cd0e467c/proxy/src/main/java/org/apache/rocketmq/proxy/grpc/ProxyAndTlsProtocolNegotiator.java) | readable-bytes 边界与 `isEncrypted` | TLS detection MARK |
| [Netty handler tests @ `fca0764`](https://github.com/netty/netty/tree/fca0764703b3bb59c6e6dc5d29c6d9710d35c0e6/handler/src/test/java/io/netty/handler) | 目标版本真实 API/classpath | 类型归属、签名与 branch-only 对照 |

fixture 保留来源项目 Apache-2.0 头部的必要片段，只抽取触发行为，不复制完整业务文件。

## 测试矩阵

模块本地测试覆盖 248 个执行实例，包括：

- 20 个白名单版本在 Maven 和 Gradle 的逐一 AUTO；
- 4.2.1、4.2.10、4.1.137+、4.3/5.x 的 Maven/Groovy/Kotlin/BOM 禁止降级；
- root/profile、唯一/共享/重复属性、plugin、classifier、非 JAR、嵌套 DSL、生成目录；
- Maven 唯一/root/profile imported BOM、歧义 owner、Gradle `platform`/`enforcedPlatform` 和
  versionless handler；
- 官方 recipe 与自研 recipe 的组合、阶段顺序和两周期幂等；
- RuleBased array、expanded varargs、求值顺序、业务同名类；
- TLS、trust、SNI、ALPN、native、timeout、traffic、flow、IP、proxy、logging、PCAP、chunked、
  pipeline、transport 与 4.2-only API；
- 16 个已知 TLS Properties key、扁平/嵌套 YAML 和 lookalike 隔离；
- Flume、Dubbo、RocketMQ 固定提交 fixture。

运行：

```bash
mvn -f rewrite-netty-handler-upgrade/pom.xml test
```

## 使用与验收

在 OpenRewrite Maven/Gradle 插件中激活：

```text
com.huawei.clouds.openrewrite.nettyhandler.MigrateNettyHandlerTo4_1_136
```

验收至少包括：

1. 依赖解析结果只有预期 Netty line，`netty-handler` 与 companion 对齐；
2. 没有任何 4.2.x/更高版本被改成 4.1.136；
3. 所有 `目标版本冲突（禁止降级）` 都有明确的向前目标决策；
4. 所有 AUTO diff 已 review，所有 MARK 已转成业务测试或书面接受；
5. JDK/OpenSSL/Conscrypt、SNI/ALPN、startTLS、session resumption 和 timeout 在生产等价镜像验证；
6. native classifier、shade 后服务资源、pipeline removal、ByteBuf reference count 和 leak detector 通过；
7. proxy/logging/PCAP 的凭据与流量数据满足安全和合规要求。

MARK 是机器定位到的迁移任务，不是“升级已完成”的证明；删除 MARK 前必须保留相应回归证据。
