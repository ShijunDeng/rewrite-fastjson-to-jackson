# Bouncy Castle `bcpkix-jdk18on` 迁移到 1.81.1

本模块把工作簿指定的 `org.bouncycastle:bcpkix-jdk18on` 1.74、1.75 严格升级到
1.81.1。它不仅修改版本号：配方会执行有官方源码证据的 Java API 改写，并把需要
协议、证书、密钥、编码、Provider、JCA/JCE、TLS/DTLS/JSSE、LDAP、运行数据或兼容矩阵才能判断的事项精确
标记到构建、源码和配置节点。

Maven group 与 Java package 前缀均为 `com.huawei.clouds.openrewrite`；模块 artifact
为 `rewrite-bcpkix-jdk18on-upgrade`，Java package 为
`com.huawei.clouds.openrewrite.bcpkixjdk18on`。

## 严格版本边界

```text
1.74  1.75  ->  1.81.1
```

| 坐标 | 源版本白名单 | 目标 |
| --- | --- | --- |
| `org.bouncycastle:bcpkix-jdk18on` | `1.74`, `1.75` | `1.81.1` |

只有上述两个固定源版本会被 AUTO。已经是 1.81.1 的声明 NOOP；1.76～1.81 等表外
版本 NOOP + MARK；1.81.2、1.82、1.84、2.x 等更高版本 NOOP + “目标版本冲突
（禁止降级）” MARK。范围、动态版本、BOM/platform、version catalog、共享 property、
classifier、非 JAR 变体和外部 owner 均不被猜测式修改。

## 配方入口

| 配方 | 模式 | 功能 |
| --- | --- | --- |
| `com.huawei.clouds.openrewrite.bcpkixjdk18on.UpgradeBcPkixJdk18onTo1_81_1` | AUTO | 只升级 1.74、1.75 且 owner 明确的标准依赖 |
| `com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateDeterministicBcPkix1_81_1Java` | AUTO | 执行有上游替代关系的 delta-certificate 类型迁移和 inline PKMAC wrapper 消除 |
| `com.huawei.clouds.openrewrite.bcpkixjdk18on.FindBcPkix1_81_1BuildRisks` | MARK | 定位 owner、NO-DOWNGRADE、variant、BOM/catalog、家族、lineage、JPMS/OSGi/签名打包风险 |
| `com.huawei.clouds.openrewrite.bcpkixjdk18on.FindBcPkix1_81_1SourceAndConfigurationRisks` | MARK | 定位 PKIX/CMS/PKCS/PEM/OCSP/TSP/TLS/ASN.1/operator/JCA/JCE/provider/LDAP/序列化风险 |
| `com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateBcPkixJdk18onTo1_81_1` | 推荐入口 | 依次执行严格升级、确定性源码迁移、构建审计和源码/配置审计 |

`SearchResult` 是必须由 owner 处理的迁移事项，不是可忽略的告警噪声。先 dry-run，
逐项审批 AUTO diff 与 MARK，再在业务分支应用。

## 真正自动处理的内容

### 依赖声明

Maven 仅处理根 `project` 或其第一层 `profile` 中：

- 直接 `dependencies`；
- `dependencyManagement/dependencies`；
- 唯一且只被目标依赖引用的 root/profile property。

同名 profile property 遮蔽、重复定义、属性也被 bcutil/bcprov 等其他依赖使用，或属性
出现在 XML attribute/其他位置时，property 不会改变。scope、optional、exclusions 等
元数据原样保留。plugin dependency、嵌套伪 project 和生成目录不处理。

Gradle 仅处理根 `dependencies` 中标准 Groovy/Kotlin 三段字符串及 Groovy map。
`buildscript`、`subprojects`、`project(...)`、自定义嵌套 scope、四段坐标、classifier、
ext、platform、catalog alias 和动态 GString 保持不变。动态字符串识别只检查首个
literal fragment 的精确
`org.bouncycastle:bcpkix-jdk18on:` 前缀，`xorg.bouncycastle...` 不会误匹配。

### Java API

| 不兼容点 | AUTO | 证据与剩余边界 |
| --- | --- | --- |
| `org.bouncycastle.pkcs.DeltaCertificateRequestAttribute` 删除 | 改为 `DeltaCertificateRequestAttributeValue`，覆盖 import、声明、构造、完全限定引用 | 上游把文件重命名并保留公开构造器和四个 getter；新类型还实现 `ASN1Encodable`。草案和 tagged encoding 同时演进，因此迁移后继续留下 `DELTA_DRAFT` MARK，并要求逐字节验证 |
| `ProofOfPossessionSigningKeyBuilder#setPublicKeyMac` 参数从 `PKMACValueGenerator` 改为 `PKMACBuilder` | 仅把 `setPublicKeyMac(new PKMACValueGenerator(builder), password)` 改为 `setPublicKeyMac(builder, password)` | 旧 wrapper 只保存 builder 并调用 `builder.build(password)`；新上游方法直接做同一调用。局部变量、共享、继承或重复使用的 generator 不猜测，改为 MARK |

AUTO 必须具有 Bouncy Castle 类型归因；同名业务类型和字符串不会被修改。`target`、
`build`、`generated`、cache、install、report 等生成目录全部跳过。

以下内容故意没有自动“修到能编译”：

- `DeltaCertificateTool` 的五个 include flag 和三参数
  `makeDeltaCertificateExtension(boolean,int,X509CertificateHolder)`；
- `DeltaCertAttributeUtils.makeDeltaCertificateExtension(...)`；
- 非 inline 的 `PKMACValueGenerator`；
- 证书/CRL、CMS、PKCS、OCSP、TSP 的编码、算法、Provider 或 draft 选择。

这些 API 没有语义等价的一对一替换。机械删除参数可能生成格式不同但仍能编译的
证书或 CMS 数据，因此只能 MARK。

## 不兼容点与可执行 MARK

### Delta certificate 与 CRMF/CMP

- 1.77 引入 delta-certificate request；1.79 又更新到新 draft。
- 目标版本删除旧 request attribute 类型，改用
  `DeltaCertificateRequestAttributeValue` 和 descriptor 模型。
- `DeltaCertificateTool` 删除 `signature/issuer/validity/subject/extensions` flags，
  工厂从三参数改为两参数；这不是“删除一个多余 int”，而是 draft 编码模型变化。
- `CertificateRepMessage.toASN1Structure()` 的字节码返回 descriptor 从
  `ASN1Encodable` 变为 `CertRepMessage`。Java 源码通常可重新编译，但预编译 consumer、
  reflection、method handle 与 ABI 检查必须重建。
- `setPublicKeyMac` 不再暴露 `PKMACValueGenerator`。共享 wrapper 需要业务明确生命周期，
  并用固定密码、salt、iteration、SubjectPublicKeyInfo 做 CRMF MAC golden vector。

对应 visitor 会标记旧/新 delta 类型、已删除 field/method、剩余 PKMAC wrapper 和
`CertificateRepMessage` ABI 边界。

### CMS、PKCS 与 operator

| 版本边界 | 官方变更 | 必须验证 |
| --- | --- | --- |
| 1.77 | PQC CMS SignedData 的 signed attributes 默认 digest 从 SHAKE-256 变为 SHA-256；历史 SHA-1 OID 兼容 | digest OID、signed attributes、旧端验证 |
| 1.78 | CMS HKDF、初始 composite signature；PKCS12 新变体 | recipient、KDF、MAC、精确 DER |
| 1.79 | `CMSSignedData.replaceSigners` 保留原 digest AlgorithmIdentifier，不再重编码/丢失带 NULL 的项 | 原/新 digest set、NULL parameters、exact bytes |
| 1.79 | CMS EnvelopedData/AuthEnvelopedData version、OtherKeyAttribute optional、RFC 9269 KEM | KEM ciphertext、version、optional attribute、解密互操作 |
| 1.80 | `SignerInfoGenerator` copy constructor 保留 certHolder；KEMRecipientInfos `kemct` size 修复；RFC 9579 PBMAC1 | certificate embedding、recipient selection、PBMAC1 PRF |
| 1.81 | PBMAC1 从 protectionAlgorithm 初始化时不再丢 PRF；CMS/SMIME 增加 ChaCha20-Poly1305 和 ML-DSA SignedData | PRF、MAC、AEAD/AAD、算法标识、旧端兼容 |
| 1.81.1 | generic composite verifier 至少必须校验一个 component signature | empty/missing/partial/all component signatures |

配方精确标记 `CMSSignedData.replaceSigners`、CMS AuthEnveloped/KEM recipient、
`SignerInfoGenerator` copy、PKCS12/PBMAC1，以及带 composite key 的
`JcaContentSignerBuilder`/`JcaContentVerifierProviderBuilder`。普通同名方法不标记。

### PEM、ASN.1 与 X.500

- 1.77：未转义 `=` 的 X.500 RDN 从静默截断变为异常；零长度 OID 和空 Extensions
  被拒绝。
- 1.78：PEM whitespace 更符合 RFC 7468、header 必须从行首开始；OID 内容限制为
  4096 bytes。
- 1.79：新增 `org.bouncycastle.pemreader.lax`，用于显式兼容更宽松的旧 PEM。
- 1.80：新增 `org.bouncycastle.asn1.allow_wrong_oid_enc`，只能在威胁模型批准后使用。

visitor 标记 `PEMParser` 构造/`readObject`、ASN.1 parse/encode 以及精确配置键。必须覆盖：
空/截断/超长 OID、非规范 tag、BER/DL/DER、leading whitespace、多对象 PEM、加密私钥、
strict/lax 双路径。不要为了“让旧文件能读”而全局开启 lax/wrong-OID。

### OCSP、TSP/ERS、PKIX/CRL 与 LDAP

- 1.78 修复 `OcspCache` dangling weak reference；1.79 修复 malformed
  AlgorithmIdentifier 的 cache 问题。
- 1.76 增加 SHA-3 timestamp；1.79 修复 ERS 同一输入第二次使用不同 digest 时的 hash。
- 1.79/1.80 修复 FTP CRL 被 downcast 忽略的问题。
- 1.81.1 patch 包含 LDAP certificate-store refactor。

配方标记 OCSP request/response/signature、TSP request/token/ERS、PKIX path、CRL builder/
converter、LDAP `CertStore.getInstance("LDAP",...)` 和 Bouncy Castle LDAP 类型。回归至少包括：

- good/revoked/unknown、nonce、malformed response、cache 命中/失效/并发；
- RFC 3161 policy、nonce、TSA chain、digest OID、stored evidence；
- trust anchor、name constraints、policy、delta/indirect CRL、AIA/CDP、FTP；
- LDAP DN/filter escaping、attribute mapping、referral、timeout、empty result、cache 与注入负例。

### Provider、JCA/JCE、TLS、编码与序列化

operator builder、证书 converter 和显式 BC/BCPQC/BCFIPS/BCJSSE JCA/JCE factory
会被标记，因为它们依赖最终解析到的 provider/family。类型归属覆盖
`AlgorithmParameters`、`KeyFactory`、`KeyPairGenerator`、`KeyStore`、`MessageDigest`、
`SecureRandom`、`Signature`、证书/路径 factory、`Cipher`、`KeyAgreement`、
`KeyGenerator`、`Mac`、`SecretKeyFactory`、`SSLContext`、`KeyManagerFactory` 和
`TrustManagerFactory`。`Security.addProvider`/`insertProviderAt` 传入具体 Bouncy Castle
provider，以及对 `BC`/`BCPQC`/`BCFIPS`/`BCJSSE` 的 `getProvider`/`removeProvider`，
会得到独立的进程级 provider 顺序 MARK；SunJCE/SUN 等同名 JDK 调用不会误标。

所有 `org.bouncycastle.tls.*`、`org.bouncycastle.jsse.*` 和 legacy
`org.bouncycastle.crypto.tls.*` 类型/import/调用会得到 TLS MARK。必须把 bctls、
bcutil、bcprov、bcpkix 的实际解析版本一起验证，并覆盖协议/cipher/signature、
ALPN/SNI、证书路径、DTLS-SRTP、session resumption、key update、恶意 peer 和最终制品。
目标固定源码可见 [BC TLS package @ 1.81.1](https://github.com/bcgit/bc-java/tree/dd0e7f83eef6b5d157139c9da21852d44bfcef71/tls/src/main/java/org/bouncycastle/tls)。

所有 `org.bouncycastle.*` 对象的直接
`ObjectOutputStream.writeObject` 也会被标记。Java serialization 不是 Bouncy Castle
跨版本稳定契约；证书、CMS、PKCS、key 应改用带版本的标准 DER/PEM/PKCS/CMS 表示，
或证明旧数据读取、滚动升级与回滚。

`getEncoded`/`toASN1Structure`/`toCMSSignedData` 是持久化或签名边界。测试不能只判断
“未抛异常”，必须比较 OID、AlgorithmIdentifier parameters、DER bytes、签名/MAC 和
双向读写。

## 家族版本与 bcprov 1.84 冲突

目标 [bcpkix 1.81.1 POM](https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.81.1/bcpkix-jdk18on-1.81.1.pom)
直接依赖 `bcutil-jdk18on:1.81.1`；[bcutil 1.81.1 POM](https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.81.1/bcutil-jdk18on-1.81.1.pom)
再依赖 `bcprov-jdk18on:1.81.1`。目标 bcpkix JAR 的 OSGi manifest 对 Bouncy Castle
package 的 import range 是 `[1.81.1,1.82)`。

用户另一个模块的 bcprov 目标是 1.84。对此本模块的行为固定为：

```text
bcpkix-jdk18on 1.74/1.75 -> 1.81.1
bcprov-jdk18on 1.84      -> 保持 1.84，绝不降到 1.81.1
                           + “目标版本冲突（禁止降级）” FAMILY MARK
```

Maven classpath 上，直接声明的 1.84 通常会覆盖传递的 1.81.1；但 OSGi range 明确不接受
1.84，JPMS/普通 classpath 也不能据此推断 binary/behavior compatibility。必须用实际
最终制品验证。配方同样只 MARK `bcutil`、`bcpg`、`bcmail`、`bctls`、`bc-bom` 和 legacy/
FIPS lineage，永不跨模块修改 companion 版本。

## 构建与配置门禁

构建 recipe 会定位：

- versionless、property、range、dynamic、BOM/platform 与外部 owner；
- `libs.versions.toml`/`*.versions.toml` 中精确 module 或 group/name 定义；
- catalog alias 使用、classifier、type、四段 Gradle 坐标；
- companion skew、legacy jdk15*/LTS/FIPS lineage；
- Maven Shade/bnd/native 与 Gradle Shadow relocation；
- signed multi-release JAR 的 manifest、OSGi、JPMS/module 打包。

1.81.1 JAR 包含 `META-INF/BCRSA204.SF`/`.RSA`、Java 9 `module-info.class` 和 OSGi
manifest。不要在最终 fat JAR 中无审查地 relocate、剥离签名、合并 package 或 service。

配置 recipe 只按结构化精确键识别：

```text
org.bouncycastle.asn1.allow_wrong_oid_enc
org.bouncycastle.pemreader.lax
org.bouncycastle.drbg.effective_256bits_entropy
org.bouncycastle.x509.allow_absent_equiv_NULL
org.bouncycastle.x509.allow_ca_without_crl_sign
security.provider.N = org.bouncycastle.jce.provider.BouncyCastleProvider
```

properties 看 entry key，YAML 的 `provider.N` 只在 `security` 下识别，XML 看目标 tag 或
`name`/`key` + `value`，plain text 仅解析 `java.security`/policy 非注释行。README 中
出现同样文字、value 中的说明文字、docs XML/YAML 不会误标。

## NOOP 是安全边界

以下输入保持不变：

- 白名单外、目标或更高版本；
- parent/BOM/catalog/lockfile/动态或范围 owner；
- plugin dependency、嵌套 Gradle project/buildscript/custom scope；
- classifier、test-jar、zip、非标准 artifact；
- generated/cache/install/report 目录；
- 缺失类型归因、同名业务类和普通说明字符串；
- 需要证书、密钥、HSM、LDAP、Provider 顺序、draft 或真实持久化数据才能决定的变换。

生产审批前建议额外搜索间接/反射边界：

```bash
rg -n 'org\.bouncycastle|CMSSignedData|AuthEnveloped|PKCS12|PEMParser|OCSP|TimeStamp' src .
rg -n 'DeltaCertificate|PKMAC|CertPath|CRL|LDAP|ContentSigner|ContentVerifier' src .
rg -n 'Object(Input|Output)Stream|getEncoded|toASN1|Security\.(setProperty|getProviders)' src .
```

没有 marker 不等于运行时兼容，特别是通过 `Object`、反射、资源、数据库、缓存框架或
消息中间件间接使用时。

## 集成方式

先安装 recipe artifact：

```bash
mvn -f rewrite-bcpkix-jdk18on-upgrade/pom.xml clean install
```

在业务 Maven 工程先生成 dry-run patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-bcpkix-jdk18on-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateBcPkixJdk18onTo1_81_1
```

审批后把 `dryRun` 改为 `run`。建议门禁：

1. 保存升级前 dependency tree、Provider/service、JAR signer、module/OSGi resolution。
2. 保存 certificate/CRL/CMS/PKCS/PEM/OCSP/TSP/ASN.1 golden corpus。
3. 处理每个 MARK，并记录 owner、证据、结论和回滚方案。
4. 用最终 WAR/JAR/container image 重新验证，不只看 IDE classpath。
5. 做 1.74/1.75 与 1.81.1 双向读写、签名、MAC、decrypt、path、LDAP 和 rollback。
6. 对 bcprov 1.84 组合单独跑 classpath、JPMS、OSGi 与互操作矩阵。

## 官方能力复用审计

审计基线为 OpenRewrite Core `8.87.5` 的固定提交
[`b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)
和 `rewrite-migrate-java:3.40.0` 的固定提交
[`6584812`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877)。
测试会从发布 JAR manifest 同时校验版本与完整 commit，而不是只相信 POM 字符串。
`rewrite-migrate-java` 使用 Moderne Source Available License，本模块仅把固定的 3.40.0
artifact 放在 test scope 做 catalog 审计；它不进入发布物的运行时依赖或推荐配方树。

固定版本 catalog 扫描得到的 Bouncy Castle 专用 recipe **恰好只有两个**：
`BounceCastleFromJdk15OntoJdk18On` 和
`BouncyCastleFromJdk15OnToJdk15to18`。前者有 14 个、后者有 7 个
`ChangeDependency` 叶子，全部使用 `latest.release`，均是 lineage/artifact 迁移，
不是 `bcpkix-jdk18on` 1.74/1.75 的定点升级。

| 官方能力 | 审计结论 | 本模块处理 |
| --- | --- | --- |
| [`org.openrewrite.java.ChangeType`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java) | 能完整表达 `DeltaCertificateRequestAttribute` 到 `DeltaCertificateRequestAttributeValue` 的类型、import、构造器和使用点迁移 | **直接组合复用**；只增加 authored-source precondition，不再在自定义 Java 类中实例化官方 recipe |
| [`BounceCastleFromJdk15OntoJdk18On`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/bouncycastle-jdk18on.yml) | 运行时展开为 14 个 `ChangeDependency`：把七种 `-jdk15on` 和七种 `-jdk15to18` artifact 改为 `-jdk18on`，全部目标 `latest.release` | **审计但拒绝组合**；当前输入已经是 `bcpkix-jdk18on`，且目标必须固定为 1.81.1 |
| [`BouncyCastleFromJdk15OnToJdk15to18`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/bouncycastle-jdk15to18.yml) | 运行时展开为 7 个 `ChangeDependency`：面向 Java 8 以下项目把 `-jdk15on` 改为 `-jdk15to18`，全部目标 `latest.release` | **审计但拒绝组合**；Java lineage、artifact 和目标均不属于本任务 |
| 官方 `ChangeDependency` / `UpgradeDependencyVersion` | 能修改依赖，但不能同时表达仅 1.74/1.75、root/profile owner 隔离、独占属性、variant 边界和所有高版本精确 MARK | **拒绝通用 selector**；依赖升级保留严格 visitor，推荐树断言两个通用类型都不存在 |
| Bouncy Castle PKMAC wrapper 迁移 | 官方 catalog 没有 `setPublicKeyMac(new PKMACValueGenerator(builder), password)` 的确定性迁移 | 仅保留小型 typed visitor，并要求 inline wrapper 与精确方法归属 |

实际激活树被测试锁定为：

```text
MigrateBcPkixJdk18onTo1_81_1
├─ UpgradeBcPkixJdk18onTo1_81_1
│  └─ UpgradeSelectedBcPkixDependency
├─ MigrateDeterministicBcPkix1_81_1Java
│  ├─ ChangeType(DeltaCertificateRequestAttribute
│  │             -> DeltaCertificateRequestAttributeValue,
│  │             ignoreDefinition=true)
│  └─ MigrateBcPkix1811Java                  # 仅官方缺失的 inline PKMAC gap
├─ FindBcPkix1_81_1BuildRisks
└─ FindBcPkix1_81_1SourceAndConfigurationRisks
```

`OfficialBcPkixRecipeAuditTest` 会展开 declarative/delegating wrapper，逐项校验官方
两个 aggregate 的 21 个坐标叶子与全部 `latest.release` option，校验本地
`ChangeType` 的三个 option，并证明推荐树不存在两个官方 aggregate、
`ChangeDependency` 或 `UpgradeDependencyVersion`。before/after、完全限定类型、生成
目录和两周期幂等测试则证明接受的官方能力被实际执行。

## 测试与真实仓库夹具

测试参考 OpenRewrite 官方固定源码中的
[`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)，
覆盖 before/after、类型归因、positive、negative、ownership、profile shadow、Maven
dependencyManagement、Gradle Groovy/Kotlin、dynamic first fragment、BOM/catalog、variant、
NO-DOWNGRADE、generated path、aggregate 和 cycle/idempotence。

真实活跃仓库形态固定到不可变 commit：

- Bouncy Castle 官方 1.74
  [`DeltaCertTest`](https://github.com/bcgit/bc-java/blob/434cab9b79adfcc7d0313fbaec765a5bbfb27128/pkix/src/test/java/org/bouncycastle/cert/test/DeltaCertTest.java#L721-L733)
  的 request attribute 解析是官方 `ChangeType` 的真实 positive fixture，测试校验
  import、局部变量、构造器和后续 getter 使用点一起迁移；
- 同一固定版本的
  [`CertificateRequestMessageBuilder`](https://github.com/bcgit/bc-java/blob/434cab9b79adfcc7d0313fbaec765a5bbfb27128/pkix/src/main/java/org/bouncycastle/cert/crmf/CertificateRequestMessageBuilder.java#L285-L299)
  先保存再传递 `PKMACValueGenerator`，是 AUTO 必须保留的真实 negative fixture；
  测试证明配方不会把非 inline wrapper 猜测式删除；
- Netty [`OcspServerExample`](https://github.com/netty/netty/blob/e64a6b505d54cf1478b9c804f6508333626070a5/example/src/main/java/io/netty/example/ocsp/OcspServerExample.java#L178-L190)
  的 `PEMParser` 多证书读取；
- Apache PDFBox [`OcspHelper`](https://github.com/apache/pdfbox/blob/29282601d914ae1834918f388d69ec5f7483cc60/examples/src/main/java/org/apache/pdfbox/examples/signature/cert/OcspHelper.java#L401-L409)
  的 verifier，以及[请求构造](https://github.com/apache/pdfbox/blob/29282601d914ae1834918f388d69ec5f7483cc60/examples/src/main/java/org/apache/pdfbox/examples/signature/cert/OcspHelper.java#L599-L603)；
- Apache PDFBox [`TSAClient`](https://github.com/apache/pdfbox/blob/29282601d914ae1834918f388d69ec5f7483cc60/examples/src/main/java/org/apache/pdfbox/examples/signature/TSAClient.java#L97-L105)
  的 RFC 3161 request；
- KeyStore Explorer [`CmsSigner`](https://github.com/kaikramer/keystore-explorer/blob/740ff3c04eb4916dac3309754cfc58809f3d539b/kse/src/main/java/org/kse/crypto/signing/CmsSigner.java#L107-L113)
  的 `CMSSignedData.replaceSigners`。
- MOSIP [`CryptoUtility`](https://github.com/mosip/mosip-mock-services/blob/f88d19e9d41a681954086668a882a17bbc30d688/MockMDS/src/main/java/org/biometric/provider/CryptoUtility.java#L86-L90)
  的具体 `BouncyCastleProvider` 注册；
- Jitsi [`CertificateInfo`](https://github.com/jitsi/libjitsi/blob/6c95bf2236610e0d2c47109a73501b2078963f9f/src/main/java/org/jitsi/impl/neomedia/transform/dtls/CertificateInfo.java#L18-L39)
  的 Bouncy Castle DTLS certificate 类型。

模块自检：

```bash
mvn -f rewrite-bcpkix-jdk18on-upgrade/pom.xml clean verify
```

当前验证基线为 153 个测试：23 个严格依赖升级、59 个构建 MARK、9 个 Java AUTO、
38 个源码 MARK、12 个配置 MARK、6 个推荐组合、6 个官方 catalog/运行时树审计；
失败、错误和跳过均为 0。

## 供应链与官方证据

### 1.81.1 身份

- [Maven Central 1.81.1 目录](https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.81.1/)
  同时发布 main/sources/javadoc/POM、`.asc` 与 SHA 校验文件。
- main JAR SHA-256：
  `c437686255f7e202aba1bd1a07c76a2e620877da3dc407f71aa4869790ef9782`。
- POM SHA-256：
  `b5dbe9ea523fe151fac9bd52def2e158e5d3dea5b80c0a46594364327b474c14`。
- 官方 lightweight tag [`r1rv81v1`](https://github.com/bcgit/bc-java/tree/dd0e7f83eef6b5d157139c9da21852d44bfcef71)
  直接指向 commit `dd0e7f83eef6b5d157139c9da21852d44bfcef71`；该 commit 的
  [`gradle.properties`](https://github.com/bcgit/bc-java/blob/dd0e7f83eef6b5d157139c9da21852d44bfcef71/gradle.properties)
  明确写 `version=1.81.1`。
- Bouncy Castle 官方 [beta/download 页面](https://downloads.bouncycastle.org/betas/)
  把 `bc-jdk18on-1.81.1.tgz` 描述为“BC Java 1.81 的 security patch release，
  回补 1.84 修复的 CVE”。

tag、`gradle.properties` 与 Central 三者一一对应，因此没有把 1.81 与 1.81.1 混淆。
需要特别说明：该 tag 内的
[`docs/releasenotes.html`](https://github.com/bcgit/bc-java/blob/dd0e7f83eef6b5d157139c9da21852d44bfcef71/docs/releasenotes.html)
仍从 1.81 开始，没有独立的 1.81.1 小节；本模块不伪造 patch notes，而以官方
security-patch 页面、精确 tag/commit、Central 签名/校验和及 commit range 作为
1.81.1 供应链证据。

1.81→1.81.1 的关键固定提交包括：

- [generic composite 至少校验一个签名](https://github.com/bcgit/bc-java/commit/69a8d0e3514ef3f231779f9050f6e21a5e2d3734)；
- [LDAP classes 公共代码重构](https://github.com/bcgit/bc-java/commit/de49029ce64326c32d7af78a9d0047d2a5874fec)；
- [AEAD chunk-size 检查](https://github.com/bcgit/bc-java/commit/e4b957a0c2db8c0f68f0a0fdbbbe42e1bf7cfec4)；
- [Frodo error sampling constant-time](https://github.com/bcgit/bc-java/commit/b60444f828c7caafda5a2f3a456e9520a4064553)；
- [DRBG effective entropy property](https://github.com/bcgit/bc-java/commit/8b6d216e34cdcbd58f6da47e9708777222c843c6)。

### 源/目标固定源码

| 版本 | tag commit |
| --- | --- |
| 1.74 / `r1rv74` | [`434cab9b`](https://github.com/bcgit/bc-java/tree/434cab9b79adfcc7d0313fbaec765a5bbfb27128) |
| 1.75 / `r1rv75` | [`739a5316`](https://github.com/bcgit/bc-java/tree/739a5316dea4c2d05a14933ad77082e671745a7b) |
| 1.81.1 / `r1rv81v1` | [`dd0e7f83`](https://github.com/bcgit/bc-java/tree/dd0e7f83eef6b5d157139c9da21852d44bfcef71) |

关键 API 证据：

- [1.74 DeltaCertificateRequestAttribute](https://github.com/bcgit/bc-java/blob/434cab9b79adfcc7d0313fbaec765a5bbfb27128/pkix/src/main/java/org/bouncycastle/pkcs/DeltaCertificateRequestAttribute.java)
  → [1.81.1 DeltaCertificateRequestAttributeValue](https://github.com/bcgit/bc-java/blob/dd0e7f83eef6b5d157139c9da21852d44bfcef71/pkix/src/main/java/org/bouncycastle/pkcs/DeltaCertificateRequestAttributeValue.java)；
- [1.74 ProofOfPossessionSigningKeyBuilder](https://github.com/bcgit/bc-java/blob/434cab9b79adfcc7d0313fbaec765a5bbfb27128/pkix/src/main/java/org/bouncycastle/cert/crmf/ProofOfPossessionSigningKeyBuilder.java)
  → [1.81.1 实现](https://github.com/bcgit/bc-java/blob/dd0e7f83eef6b5d157139c9da21852d44bfcef71/pkix/src/main/java/org/bouncycastle/cert/crmf/ProofOfPossessionSigningKeyBuilder.java)；
- [1.74 DeltaCertificateTool](https://github.com/bcgit/bc-java/blob/434cab9b79adfcc7d0313fbaec765a5bbfb27128/pkix/src/main/java/org/bouncycastle/cert/DeltaCertificateTool.java)
  → [1.81.1 descriptor 实现](https://github.com/bcgit/bc-java/blob/dd0e7f83eef6b5d157139c9da21852d44bfcef71/pkix/src/main/java/org/bouncycastle/cert/DeltaCertificateTool.java)；
- [1.74 CMSSignedData](https://github.com/bcgit/bc-java/blob/434cab9b79adfcc7d0313fbaec765a5bbfb27128/pkix/src/main/java/org/bouncycastle/cms/CMSSignedData.java)
  → [1.81.1 CMSSignedData](https://github.com/bcgit/bc-java/blob/dd0e7f83eef6b5d157139c9da21852d44bfcef71/pkix/src/main/java/org/bouncycastle/cms/CMSSignedData.java)。

固定 release notes 覆盖 1.75～1.81 的 CMS/PKIX/PEM/OCSP/TSP/PKCS 变更；1.81.1
patch 依据如上单独说明。
