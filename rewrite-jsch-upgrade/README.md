# mwiede JSch 升级到 2.27.7

本模块对应表格坐标 `com.github.mwiede:jsch`，只处理表格明确列出的五行：

| Excel 行 | 序号 | 源版本 | 目标版本 |
| ---: | ---: | ---: | ---: |
| 365 | 364 | `0.1.55` | `2.27.7` |
| 366 | 365 | `0.1.70` | `2.27.7` |
| 367 | 366 | `0.2.3` | `2.27.7` |
| 368 | 367 | `0.2.7` | `2.27.7` |
| 369 | 368 | `0.2.9` | `2.27.7` |

不会把版本范围扩展成“所有旧版本”，也不会修改已经是目标版本、更新版本、范围、动态版本或无法证明归属的外部 BOM/catalog/property。推荐入口为：

```text
com.huawei.clouds.openrewrite.jsch.MigrateJschTo2_27_7
```

只升级依赖版本时使用：

```text
com.huawei.clouds.openrewrite.jsch.UpgradeJschTo2_27_7
```

## 配方处理边界

| 不兼容点 | 行为 | 原因 |
| --- | --- | --- |
| 五个表格源版本的 Maven/Gradle 声明 | **AUTO**：升级到 `2.27.7` | 版本目标确定且不改变依赖形态 |
| Maven root/profile 本地独占属性 | **AUTO**：按 Maven 可见性解析属性；root 对未覆盖它的 profile 可见，profile 属性不向 root/兄弟 profile 泄漏，同名 profile override 优先 | 只有该作用域唯一属性定义的全部有效引用都属于标准 JSch dependency，且至少有一个这种引用时才原位升级 |
| Gradle 根依赖 | **AUTO**：只处理脚本根级 `dependencies {}` 的直接 configuration 调用 | `buildscript`、`subprojects`、`allprojects`、`project(...)`、custom DSL 与 `constraints` 中的嵌套 dependencies 全部保持不变 |
| `PubkeyAcceptedKeyTypes` | **AUTO**：仅在类型归属明确的 `JSch`/`Session.setConfig`、`getConfig` 字面量参数中改为 `PubkeyAcceptedAlgorithms` | 目标版仍接受旧 alias，但新名称是当前规范名称；普通字符串和同名业务 API 不改 |
| 原始 `com.jcraft:jsch` 与 fork 共存 | **MARK** | 两者都提供 `com.jcraft.jsch.*`，运行时 classpath 顺序可能选择错误实现 |
| ssh-rsa/DSA/SHA-1 KEX/CBC/弱 MAC 等显式算法 | **MARK** | 需要服务端能力、安全基线和例外审批，不能自动重新启用 |
| `StrictHostKeyChecking=no/ask/accept-new`、known_hosts、自定义 HostKeyRepository | **MARK** | 主机身份信任是安全决策，不能生成 accept-all 修复 |
| `ConnectTimeout`、`ServerAliveInterval` | **MARK** | 本次跨度内 OpenSSH 配置值改按秒解释，需核实历史单位和超时预算 |
| 密钥加载、代理、socket、连接、fingerprint、扩展点 | **MARK** | 涉及部署 JDK、可选 crypto provider、异常、资源和网络行为 |
| 目标版弃用 overload | **MARK** | 精确标记 `removeIdentity(String)`、`setEnv(Hashtable)`、两个 SFTP `get(..., int)`、`setFilenameEncoding(String)`、`Identity.decrypt()` 和 `KeyPair.setPassphrase` |
| Java 7 或更低的显式 Maven baseline | **MARK** | 2.27.7 发布物最低 Java 8 |

SearchResult 会加在真实依赖节点、配置 entry、类型声明或类型归属明确的方法调用上。配方不全局替换 Java 字符串，不修改注释，不扫描 private key 内容，也不向 versionless 依赖强行写版本。

路径过滤只检查父目录组件，并且不区分大小写。`target`、`build`、`out`、`dist`、任意 `generated*`、任意 `install*`、`.gradle`、`.mvn`、`.idea`、`node_modules` 和 `vendor` 父目录全部跳过；叶文件名不参与排除，所以 `src/install.java`、根级 `install.gradle` 仍会正常处理。该规则同时用于 dependency AUTO、build/configuration MARK、Java finder 和 canonicalizer。

## 表格中 0.1.55 的特殊情况

Maven Central 的 `com.github.mwiede:jsch` 从 `0.1.56` 开始，没有发布 `com.github.mwiede:jsch:0.1.55`。官方 fork 的首个提交 [`635c0bc`](https://github.com/mwiede/jsch/commit/635c0bcfcce571d6ada1d368cb7abf568810aaed) 是从原始 JSch `0.1.55` 导入的源码，此时 POM 仍是 `com.jcraft:jsch:0.1.55`；切换到 `com.github.mwiede` group 的开发提交是 [`ca53880`](https://github.com/mwiede/jsch/commit/ca5388062aa879d437f74151c257d1188bc9a260)，版本已是 `0.1.56-SNAPSHOT`。

本模块仍严格支持表格给出的 `com.github.mwiede:jsch:0.1.55`，因为内部仓库可能重发布了这个坐标；测试使用 XML LST，不伪装成可以从 Central 下载的 artifact。迁移前应先用企业制品库确认它的真实二进制来源。若工程实际使用的是官方原始坐标 `com.jcraft:jsch:0.1.55`，当前配方会 **MARK**，不会擅自改 group；先确认所有传递依赖和 classpath 中只保留一个 JSch 实现。

## 官方固定版本证据

| 版本 | 官方不可变提交 |
| --- | --- |
| 原始 0.1.55 导入锚点 | [`635c0bcfcce571d6ada1d368cb7abf568810aaed`](https://github.com/mwiede/jsch/commit/635c0bcfcce571d6ada1d368cb7abf568810aaed) |
| 0.1.70 | [`f085fcff089dfdb5defa611853d7e8542da24a3f`](https://github.com/mwiede/jsch/commit/f085fcff089dfdb5defa611853d7e8542da24a3f) |
| 0.2.3 | [`55f5c7786f8b33df7301dde76f9f664b24383e6a`](https://github.com/mwiede/jsch/commit/55f5c7786f8b33df7301dde76f9f664b24383e6a) |
| 0.2.7 | [`5973624fccabccd8100389f99a2655508b50e07a`](https://github.com/mwiede/jsch/commit/5973624fccabccd8100389f99a2655508b50e07a) |
| 0.2.9 | [`40cb1319b78086a25b1d3809e09149149206bb03`](https://github.com/mwiede/jsch/commit/40cb1319b78086a25b1d3809e09149149206bb03) |
| 2.27.7 | [`79f96cbf391735677028e0e2f3ae3bd82e6e71a1`](https://github.com/mwiede/jsch/commit/79f96cbf391735677028e0e2f3ae3bd82e6e71a1) |

JSch fork 是 drop-in replacement：依赖 group 改了，但 Java package 仍是 `com.jcraft.jsch`。官方 [2.27.7 README](https://github.com/mwiede/jsch/blob/79f96cbf391735677028e0e2f3ae3bd82e6e71a1/Readme.md) 明确要求 classpath 里只保留一个 JSch；因此这次升级不需要改 import，却必须检查 `com.jcraft:jsch`、旧 agentproxy 集成、shade、应用服务器共享库和插件 classloader。

## 本次跨度内的重要行为变化

这些变化并不都发生在目标 patch，但均位于一个或多个表格源版本到 `2.27.7` 的路径中：

| 版本节点 | 变化 | 迁移影响 |
| --- | --- | --- |
| `0.2.0` | RSA/SHA-1 `ssh-rsa` 默认禁用 | RSA key 不等于必须用 SHA-1；优先让服务端支持 `rsa-sha2-256/512`，不要机械追加 `ssh-rsa` |
| `0.2.7` | PuTTY key 解析和 ECDSA/EdDSA 支持继续演进 | 用真实历史 key、换行格式、口令和 provider 做回归，不只测试新生成 key |
| `0.2.9` | 正确实现 Multi-Release JAR，JGSS 变 optional，OSGi manifest 调整 | shade/OSGi/jlink/老旧解包工具必须保留 `META-INF/versions/*` 与 manifest 语义 |
| `0.2.10` | `Identity.decrypt()` 默认实现抛 `UnsupportedOperationException` | 自定义 Identity 和显式 decrypt 调用必须重编译并改用 `setPassphrase` 驱动的解密流程 |
| `0.2.15` | 增加 strict KEX，缓解 CVE-2023-48795 | 对旧 SSH server、网关、跳板机和算法覆盖配置做真实握手测试 |
| `0.2.16` | Multi-Release OSGi 支持调整；Proxy 引入更专用异常 | 检查 OSGi imports、异常分类、重试和监控告警，不要只 catch message |
| `0.2.20` | HostKey fingerprint 输出改成 OpenSSH 6.8 起的现代格式 | 不要解析展示字符串；核对数据库、UI、审计记录和比较逻辑 |
| `0.2.23` | OpenSSH `ConnectTimeout`/`ServerAliveInterval` 按秒解释 | 原值若曾按毫秒理解，升级后可能相差 1000 倍 |
| `0.2.26` | 默认 cipher 顺序优先 AES-GCM 而非 AES-CTR | 检查硬件/CPU、网络设备、FIPS/provider 与服务端协商结果 |
| `2.27.0` | 切换到语义化版本；增强 OpenSSH V1 AEAD key 读写 | 重新验证 key 文件兼容、加密 cipher/KDF rounds 和密钥落盘权限 |
| `2.27.5` | 错误 passphrase 的 `addIdentity` 抛 `JSchException` | 修正异常分类、用户提示、重试次数和敏感信息日志 |

以上来自官方固定提交的 [ChangeLog](https://github.com/mwiede/jsch/blob/79f96cbf391735677028e0e2f3ae3bd82e6e71a1/ChangeLog.md)。升级还跨越大量专用异常、KEX、signature、key parser、channel 和 agent 修复；应按实际使用范围阅读每个中间 release，而不是把“大版本 API 稳定”理解成“行为完全不变”。

## 算法和主机密钥安全

官方 FAQ 说明：`ssh-rsa` 名称在这里指 RSA/SHA-1 signature；RSA key 本身仍可用 RFC 8332 的 SHA-2 signature。遇到 `Algorithm negotiation fail` 时，应先升级服务端或启用 `rsa-sha2-256/512`，再根据安全审批决定是否临时回退。

推荐验证：

1. 记录客户端和服务端最终协商的 KEX、host-key、public-key signature、cipher、MAC 和 compression，不只看配置字符串。
2. 对 known_hosts 已存在、首次连接、key rotation、key mismatch、hashed hostname、别名/端口和跳板机逐一测试。
3. 禁止用 `StrictHostKeyChecking=no` 或无条件 `HostKeyRepository.check() == OK` 解决升级失败。
4. 如果必须兼容 SHA-1，限定 server/session、写明到期时间和监控；不要把弱算法追加到 JVM 全局配置。
5. 验证 0.2.15 后 strict KEX 与旧服务器、中间 SSH proxy、堡垒机和流量审计设备的组合。

配方会精确 MARK Java `JSch/Session.setConfig` 的算法 key，以及 properties/YAML/XML 中 key 归属明确且值含 `ssh-rsa`、`ssh-dss`、SHA-1 KEX、CBC 或弱 MAC 的节点。安全值、注释、描述文本和相似业务 key 为负例，不会被标记。

## Java、Multi-Release JAR 和可选依赖

2.27.7 的 [POM](https://github.com/mwiede/jsch/blob/79f96cbf391735677028e0e2f3ae3bd82e6e71a1/pom.xml) 发布 Java 8 baseline，并包含 Java 9/10/11/15/16/24 的 Multi-Release 实现。构建项目本身使用更新 toolchain，不等于应用必须运行 Java 25。

算法能力取决于实际运行 JDK 和 provider：

- Ed25519/Ed448：Java 15+，或加入 Bouncy Castle；
- curve25519/curve448：Java 11+，或加入 Bouncy Castle；
- chacha20-poly1305：需要 Bouncy Castle；
- 2.27.7 POM 的 `bcprov-jdk18on:1.83`、`junixsocket-common:2.10.1`、JNA JPMS `5.18.1`、Log4j API 和 SLF4J API 均为 optional，不应假设会自动出现在最终运行时。

使用 SSH agent/Pageant/Unix domain socket、Log4j/SLF4J adapter、BC provider、OSGi、JPMS、native-image 或 shaded fat JAR 时，必须显式检查 runtime dependency tree 和最终归档内容。shade 不能丢失 `Multi-Release: true`、versioned classes、OSGi supplemental manifest 或 service resources。

## API 人工迁移清单

配方对下列调用只 MARK，不猜业务替换：

- `JSch.removeIdentity(String)`：改用持有的 `Identity` 对象；确认同名 key、多 repository、agent 和并发移除语义。
- `ChannelSession.setEnv(Hashtable<byte[], byte[]>)`：逐项改用 `setEnv(String,String)` 或 byte-array overload；确认字符集和敏感环境变量生命周期。
- `ChannelSftp.get(String,int)` / `get(String,monitor,int)`：目标 overload 的 `mode` 已无意义并废弃，选择无 mode 或 `long skip` overload；回归 RESUME/OVERWRITE、进度和断点位置。
- `ChannelSftp.setFilenameEncoding(String)`：改用 `Charset` overload；对非 UTF-8 服务端、文件名 round-trip 和异常做测试。
- `Identity.decrypt()`：由 `setPassphrase(byte[])` 触发解密；自定义实现需按 2.27.7 contract 重编译。
- `KeyPair.setPassphrase(...)`：目标实现抛 `UnsupportedOperationException`；应把 passphrase 传给 `writePrivateKey` 等明确操作，并及时清理 byte array。
- `HostKey.getFingerPrint/getFingerprint`：禁止依赖展示格式；显式选择批准的 hash 并迁移持久化比较值。

自定义 `Identity`、`HostKeyRepository`、`ConfigRepository`、`Proxy`、`SocketFactory`、`Logger` 会在 class declaration 上 MARK。实现需用 2.27.7 重编译，并验证 default method、异常类型、线程安全、关闭顺序、classloader 和可选 provider。

## 构建版本所有权

AUTO 支持：

- Maven project/profile 的直接 dependencies 和 dependencyManagement；
- 按 Maven 作用域解析且唯一定义、仅被标准 JSch dependency 引用的本地属性：root 可被 profile 继承，profile 不外泄，profile override 优先；
- Gradle Groovy string/Map 和 Kotlin DSL 根级 `dependencies {}` 的直接字面量坐标；
- 标准 JAR 形态，并保留 scope/configuration、optional、exclusion 等已有 metadata。

保持不变并由推荐配方 MARK：

- parent/BOM/platform/version catalog 管理的 versionless 依赖；
- `${...}`、Gradle interpolation、range、`+`、`LATEST` 等动态值；
- 表格外固定版本；
- classifier、type、ext 等非标准 artifact；
- plugin dependency、伪造相似 XML、`dependencies {}` 外普通字符串。
- `buildscript/subprojects/allprojects/project/custom DSL/constraints` 内嵌的 Gradle dependencies；这些 owner 可能属于插件 classpath、其他 project 或生成 DSL，不能由根项目配方代替修改。

建议运行：

```bash
mvn dependency:tree -Dincludes=com.github.mwiede:jsch,com.jcraft:jsch,org.bouncycastle
```

或 Gradle `dependencyInsight --dependency jsch`，确认所有运行配置只有一个 JSch 实现。特别检查 Jenkins/JGit/Spring Integration/Ant 等上层组件传递的原始 `com.jcraft:jsch`，需要在真正引入它的 dependency 上 exclusion。

## 测试样本

测试参考 OpenRewrite 官方 [`rewrite-java-dependencies` 在 `decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，采用 before/after、类型归属、负例、两轮 cycle 和 recipe discovery/validation 风格。真实仓样本固定到不可变提交，并缩减为保留原声明或调用形态的最小 fixture：

- [DavixDevelop/bte-mover `7af9f75`](https://github.com/DavixDevelop/bte-mover/blob/7af9f7592d104cc5b4a8eecc4e44dc6bc6d59f35/build.gradle)：Gradle `0.1.70`；
- [Darren4641/Baroka `1e0bd1c`](https://github.com/Darren4641/Baroka/blob/1e0bd1c1a10dcd47a5ce559e0734dc678dfea203/build.gradle.kts)：Kotlin DSL `0.2.3`；
- [doubleclip118/kkoejoejoe-Capstone `0855c10`](https://github.com/doubleclip118/kkoejoejoe-Capstone/blob/0855c10229c6548632a5d8a7597cb505297d2b39/back/build.gradle)：Gradle `0.2.7`；
- [IssacL891/CSCI-320-Final-Project `af63084`](https://github.com/IssacL891/CSCI-320-Final-Project/blob/af630840c4be4bf255799c1e53a40498f83d6c59/CLI/build.gradle)：根级 Gradle `0.2.9` 正向 AUTO；
- [ow2-proactive/programming `f1de44d`](https://github.com/ow2-proactive/programming/blob/f1de44d9643f85c32cbe9d4e6e8147ca4a618f6a/build.gradle)：`project(...) { dependencies { ... } }` 内嵌 Gradle `0.2.9`，作为必须保持不变的真实反例；
- [zstackio/zstack `5a29293`](https://github.com/zstackio/zstack/blob/5a29293bd53c98a38488fb8b71d54531012d3dae/utils/src/main/java/org/zstack/utils/ssh/Ssh.java)：JSch algorithm 配置调用；
- [mendhak/gpslogger `32994bf`](https://github.com/mendhak/gpslogger/blob/32994bfc7c03d406aadbf038d0f4813849d0b250/gpslogger/src/main/java/com/mendhak/gpslogger/senders/sftp/SFTPWorker.java)：真实 `session.getHostKey().getFingerPrint(jsch)` 使用形态。

当前 66 个测试执行用例覆盖：五个源值；Maven direct/property/managed/profile，以及 root 可见、profile 不泄漏、profile override 优先、空白引用、共享/重复/未使用属性分层负例；Gradle Groovy string/Map、Kotlin DSL、根级正向与嵌套/带 select DSL 反例；五个真实版本仓和两个真实 Java 用例；metadata 保留、表格外/范围/动态/变量/versionless/plugin/variant 负例；大小写、常见缓存及 `generated*/install*` 父目录过滤、`install.java/install.gradle` 叶文件正例；AUTO/MARK 幂等。Java 还覆盖配置 key AUTO、算法/信任/identity/session/fingerprint/扩展点 MARK、七类弃用调用及同名 API 负例；配置覆盖 properties/YAML/XML 精确 MARK、必须具备 SSH/JSch 路径或 owner、泛型同名键/安全值/注释/相似 key 负例；推荐配方 discovery、validation 和 AUTO-before-MARK 顺序也有测试。

## 执行与验收

先 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jsch-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jsch.MigrateJschTo2_27_7
```

审查 patch 和全部 SearchResult 后再执行 `run`。模块自身验证：

```bash
mvn -f rewrite-jsch-upgrade/pom.xml clean verify
```

业务验收至少包含：真实 SSH/SFTP server matrix、旧/新 key 格式与错误口令、known_hosts 首连/轮换/冲突、弱算法拒绝、strict KEX、代理/堡垒机、超时/keepalive、断网重连、channel 并发/关闭、SFTP 断点续传、大文件、非 UTF-8 文件名、OSGi/JPMS/shade/classloader 和滚动发布回滚。
