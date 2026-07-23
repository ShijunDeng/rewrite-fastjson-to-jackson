# bcpkix-jdk18on / org.bouncycastle:bcpkix-jdk18on 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-bcpkix-jdk18on-upgrade`](../../../rewrite-bcpkix-jdk18on-upgrade)，
> 覆盖精确依赖升级、确定性 API 迁移、风险定位和禁止降级守卫。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-bouncycastle-bcpkix-jdk18on` |
| Maven artifactId | `migration-spec-java-maven-org-bouncycastle-bcpkix-jdk18on` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `bcpkix-jdk18on`<br>`org.bouncycastle:bcpkix-jdk18on` |
| Catalog canonical identity | `org.bouncycastle:bcpkix-jdk18on`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `1.81.1` |
| Excel 迁移边 | 4 |
| 涉及微服务数 | 最大可见值 `17`；不同版本行不累加 |
| 分桶 | `B3_Minor联动` |
| 难度 | `中` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-bcpkix-jdk18on-upgrade` |

## Excel 事实快照

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1353 | 1352 | `org.bouncycastle:bcpkix-jdk18on` | java | `1.74` | `1.81.1` | 17 | B3_Minor联动 | 中 | upgrade-candidate/auto | bouncycastle联动组需同步版本，minor升级需保持一致 |
| 1354 | 1353 | `org.bouncycastle:bcpkix-jdk18on` | java | `1.75` | `1.81.1` | 17 | B3_Minor联动 | 中 | upgrade-candidate/auto | bouncycastle联动组需同步版本，minor升级需保持一致 |
| 2217 | 2216 | `bcpkix-jdk18on` | java | `1.74` | `1.81.1` | 0 | B3_Minor联动 | 中 | upgrade-candidate/auto | bouncycastle联动组需同步版本，minor升级需保持一致 |
| 2218 | 2217 | `bcpkix-jdk18on` | java | `1.75` | `1.81.1` | 0 | B3_Minor联动 | 中 | upgrade-candidate/auto | bouncycastle联动组需同步版本，minor升级需保持一致 |

## 升级方向与禁止降级

- AUTO 白名单严格为 `1.74`、`1.75`，目标固定为 `1.81.1`。
- 目标版本 NOOP；表外低版本、动态/范围、外部 BOM/parent/catalog 和歧义 property owner
  保持不变并 MARK。
- `1.81.2+`、1.82/1.84、2.x 和未来发布线保持原文，并标记
  `目标版本冲突（禁止降级）`；不存在任何回退路径。
- classifier、非 JAR、Android、FIPS、`jdk15on`、`jdk15to18` 等不同 lineage 不被猜测式改写。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | 依赖族 / 制品 lineage | bcpkix 依赖 bcprov/bcutil/bctls 等家族；Android/FIPS/jdk15 lineage 不能混装 | 严格 owner 白名单 AUTO；家族错位、BOM/catalog、variant、signed JAR、JPMS/OSGi 精确 MARK |
| C-002 | delta certificate | `DeltaCertificateRequestAttribute` 迁移为 `DeltaCertificateRequestAttributeValue` | 直接复用官方 core `ChangeType`，覆盖 import、构造器和使用点 |
| C-003 | CRMF PKMAC | inline `PKMACValueGenerator(builder)` 在目标 API 可等价解包为 builder | 小型 typed visitor 只处理精确方法和 inline wrapper；变量/共享 wrapper 保持并 MARK |
| C-004 | PKIX/CMS/PKCS/PEM/OCSP/TSP | ASN.1、算法、验证、证书路径、CRL、CMS、PEM、OCSP/TSP 跨版本行为和拒绝边界变化 | 在具体类型、调用和配置节点 MARK，要求 golden corpus 与互操作测试 |
| C-005 | JCA/JCE Provider | provider 注册顺序、算法选择和 family 兼容性具有进程级影响 | 标记 BC/BCPQC/BCFIPS/BCJSSE 注册与显式 provider 调用，不修改策略 |
| C-006 | TLS/DTLS/JSSE | bctls/bcutil/bcprov/bcpkix 版本、协议/cipher/signature、ALPN/SNI 和证书路径必须联动 | Bouncy Castle TLS/JSSE 类型精确 MARK，要求最终镜像互操作和恶意 peer 测试 |
| C-007 | LDAP / 编码 / 序列化 | DN/filter、DER/BER、Java serialization 和跨版本持久化没有通用安全自动替换 | 精确 MARK；要求标准编码、旧数据读取、滚动升级和回滚证据 |

`VERIFIED` 只覆盖固定 tag、制品和源码支持的事实。密码学策略、证书/密钥、生产 Provider
顺序、协议互通、历史数据和回滚仍属于业务验收。

### `java` 生态最低核查项

- 核对最终 dependency tree、Provider/service、签名 JAR、JPMS/OSGi 与 shaded 制品。
- 使用证书、CRL、CMS、PKCS、PEM、OCSP、TSP、ASN.1、TLS/DTLS 和 LDAP golden corpus。
- 验证 1.74/1.75 与 1.81.1 的双向读取、签名/MAC、解密、证书路径和回滚。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | [BC 1.81.1 tag/commit](https://github.com/bcgit/bc-java/tree/dd0e7f83eef6b5d157139c9da21852d44bfcef71)；Maven Central JAR SHA-256 `c4376862...9782`、POM SHA-256 `b5dbe9ea...c14` |
| E-002 API/行为变化 | `VERIFIED` | 1.74 `434cab9b`、1.75 `739a5316`、1.81.1 `dd0e7f83` 固定源码与 release notes |
| E-003 真实用法 | `VERIFIED` | Netty `e64a6b5`、PDFBox `2928260`、KeyStore Explorer `740ff3c`、MOSIP `f88d19e`、Jitsi `6c95bf2` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | OpenRewrite Core `8.87.5` [`b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)；rewrite-migrate-java `3.40.0` [`6584812`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877) |

## 官方能力复用审计

- 已在 YAML 中直接组合官方
  [`org.openrewrite.java.ChangeType`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java)
  处理 delta-certificate 类型迁移；自定义 Java 类不再实例化或复制该能力。
- 已审计官方
  [`BounceCastleFromJdk15OntoJdk18On`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/bouncycastle-jdk18on.yml)：
  它处理旧 lineage 到 `jdk18on` 且使用 `latest.release`，不适用于已经是 `bcpkix-jdk18on`
  的 1.74/1.75→1.81.1 精确升级，故不组合。
- 官方依赖 recipes 无法表达本任务的严格 owner、两个源版本、variant 和全量禁降级 marker，
  因此保留严格依赖 visitor；官方没有 PKMAC wrapper 配方，才保留该小型 typed gap recipe。
- 运行时 recipe-tree 测试断言官方 `ChangeType` 在 gap recipe 前，并用 before/after、
  完全限定类型、生成目录和两周期幂等证明实际激活。

## 后续 OpenRewrite 配方契约

### AUTO

- 只升级精确 1.74/1.75 的明确 Maven/Gradle owner。
- 只执行官方类型迁移和有固定源码证明的 inline PKMAC 等价变换。
- 所有 visitor 忽略生成目录、构建输出、缓存和安装产物。

### MARK

- 在具体依赖、属性、类型、调用和配置节点标记家族、Provider、TLS、PKIX/CMS、
  ASN.1、LDAP、序列化、打包与运行时风险。
- 高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- 算法/Provider 策略、证书/密钥、协议互通、历史数据、LDAP 服务、生产部署和回滚由业务证据决定。
- 无法静态证明等价的密码学行为保持原样。

## 测试与真实用例验收

- 145 个测试覆盖严格依赖升级、构建/源码/配置 MARK、Java AUTO 和推荐组合。
- 覆盖 Maven/Gradle、property/profile/BOM/catalog/variant、目标 NOOP、表外输入和高版本禁降级。
- 覆盖官方 recipe tree、类型归因、before/after、真实 fixture、生成目录和两周期幂等。
- 最终验收必须在实际 Provider、JDK、TLS/DTLS peer、LDAP、signed/shaded 制品和历史数据上执行。

## 当前阶段结论

该模块的规格、证据和可执行实现均已完成。AUTO 只覆盖两个精确依赖版本和已证明等价的
源码变换；其余密码学和运行时风险由配方精确定位，任何高版本都不会被降级。
