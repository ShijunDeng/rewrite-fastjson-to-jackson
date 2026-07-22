# Jasypt Spring Boot Starter 4.0.3 迁移

本模块对应 `开源软件升级.xlsx` 中 `com.github.ulisesbocchio:jasypt-spring-boot-starter` 的 5 个**可见源版本**：`2.1.1`、`2.1.2`、`3.0.3`、`3.0.4`、`3.0.5`，统一目标为 `4.0.3`。没有把表格折叠单元格、相邻行或当前网络上的最新版本推断为额外输入。

推荐配方：

```text
com.huawei.clouds.openrewrite.jasypt.MigrateJasyptSpringBootStarterTo4_0_3
```

仅升级依赖、不扫描迁移风险时可使用：

```text
com.huawei.clouds.openrewrite.jasypt.UpgradeJasyptSpringBootStarterTo4_0_3
```

## 执行契约

| 领域 | 处理方式 | 精确行为 |
| --- | --- | --- |
| Maven 直接版本和 `dependencyManagement` | AUTO | 只把目标 starter 的上述 5 个字面量版本改为 `4.0.3` |
| Maven 本地版本属性 | AUTO | 属性只被目标 starter 使用时更新属性；属性被多个坐标共享时只在 starter 上内联 `4.0.3`，不改变属性及其他消费者 |
| Gradle Groovy/Kotlin 字面量 | AUTO | 支持常用 dependency configuration 的字符串坐标；Groovy 还支持 `group/name/version` map 写法 |
| Java 自动配置类包移动 | AUTO | 将两个可归因类型迁到 `com.ulisesbocchio.jasyptspringbootstarter`；同时改写 `spring.factories` 等 `.properties` 值中的同名类 |
| 旧版 `.properties` camelCase 键 | AUTO | 只规范化官方可证明等价的键，如 `poolSize`→`pool-size`、`ivGeneratorClassname`→`iv-generator-classname`；相似键不改 |
| Java 17、Spring Boot 3.5 基线 | MARK | 只标记当前 POM/Gradle 文件中可见且低于最低要求的值；不自动升级平台、JDK、容器或 CI |
| 2.x 默认算法迁移 | MARK | 标记 `PBEWithMD5AndDES`/`NoIvGenerator` 兼容工作及没有同文件算法声明的 `ENC(...)`；不假定密文由哪个 profile/工具生成 |
| 密码、私钥、GCM secret | MARK | 标记仓库内具体值、有明文默认值或空 fallback、classpath 私钥、Java 硬编码及脚本/工作流命令行暴露；精确 `${SECRET_NAME}` 引用不报明文 |
| 自定义 encryptor/detector/resolver/filter | MARK | 标记配置 bean、Java 实现和手工 `SimpleStringPBEConfig`，要求验证 bean 名、顺序、递归、线程安全和完整密码学参数元组 |
| wrapper/proxy/filter/skip | MARK | 标记 proxy 模式和精确 Jasypt filter/skip 路径；不把 YAML 中无关的 `include-*`/`exclude-*` 当成 Jasypt 配置 |
| 懒初始化、缓存和刷新 | MARK | 标记全局 lazy init、refresh event 配置及直接依赖 wrapper/cache 内部类型的 Java 代码 |
| CLI、测试与运维脚本 | MARK | 标记 `-Djasypt.encryptor.password`、`--jasypt.encryptor.password`、具体环境变量赋值和 decrypt/reencrypt；提示进程列表、history、CI log、临时文件风险 |
| 外部 parent/BOM、version catalog、Gradle 变量 | NO-OP/MARK | 不覆盖不可见版本；推荐配方会标记无版本 starter，要求到真正拥有版本的文件处理 |
| 非 starter artifact 和 Maven plugin | NO-OP | 不改 `jasypt-spring-boot`、`org.jasypt:jasypt`、`jasypt-maven-plugin`，也不把 starter 版本规则套到插件上 |
| 未列版本、范围、动态版本、已达目标或更高版本 | NO-OP | `1.18`、`3.0.2`、`3.0.6`、版本范围、`LATEST`、`4.0.3+` 均不猜测、不降级 |

`MARK` 使用 OpenRewrite `SearchResult`，不会偷偷选择密码学参数或安全策略。标记是待办，不代表迁移已安全完成。

## 不兼容点与人工验证

固定目标源码为 [`e8d16bdcc92f3fd85f5d4ea05944bc0c46b9bd91`](https://github.com/ulisesbocchio/jasypt-spring-boot/tree/e8d16bdcc92f3fd85f5d4ea05944bc0c46b9bd91)，对应 `jasypt-spring-boot-parent-4.0.3`。其父 POM明确使用 Java 17、Spring Boot 3.5.0；迁移不能只提升 starter 后继续用 Java 8/11 或 Boot 2.x/3.4。

1. **历史密文与默认值。** 2.x 的默认 PBE 为 `PBEWithMD5AndDES`，没有随机 IV；[3.0.0 固定提交](https://github.com/ulisesbocchio/jasypt-spring-boot/tree/a82c45caf5d910d7e7684a50144b5127125b437c)起默认改为 `PBEWITHHMACSHA512ANDAES_256` 和 `RandomIvGenerator`。兼容旧密文时可在隔离迁移阶段显式恢复旧算法及 `org.jasypt.iv.NoIvGenerator`，解密成功后用组织批准的强方案重加密。算法、IV、salt、迭代次数、provider、pool 和输出编码必须作为一个元组验证，配方不会全局写入旧弱默认值。
2. **密码来源。** 目标仍要求 password、非对称私钥或 GCM secret 三者之一。密码应来自受控 secret 注入；即便官方允许 system property、environment 或 command line，命令行仍可能出现在进程列表、shell history、CI 日志和诊断信息中。`${ENV:plaintext-default}` 不是安全外部引用；缺失 secret 应明确失败。
3. **包名变化。** [固定提交 `6c958775...`](https://github.com/ulisesbocchio/jasypt-spring-boot/commit/6c958775b544e94be9b650723e6f6bd2f7467be3)为避免 Java 9+ split package，将 `JasyptSpringBootAutoConfiguration` 和 `JasyptSpringCloudBootstrapConfiguration` 移到 `com.ulisesbocchio.jasyptspringbootstarter`。配方自动处理可归因 Java 类型和 `.properties` 注册值，但生成代码、字符串反射、XML 或第三方元数据仍需搜索验证。
4. **自定义扩展。** 自定义 `StringEncryptor` 会绕过 starter 默认密码学配置；自定义 resolver 还会接管检测、filter 和解密全流程。必须覆盖正负匹配、循环依赖、bean 名、初始化顺序、多 context、并发和异常路径。
5. **属性源代理。** wrapper 与 CGLIB proxy 对具体 `PropertySource` 类型、identity 和 `getSource()` 的行为不同。自定义 property source、Actuator environment 暴露和 Spring Cloud bootstrap 都要逐 profile 启动验证。
6. **懒加载与早期启动。** `DefaultLazyEncryptor` 目标构造器使用 `ConfigurableEnvironment`；全局 lazy init 可能将缺失密码延迟到第一次属性访问。使用 `StandardEncryptableEnvironment` 处理 logging/bootstrap 时，要验证日志初始化、bootstrap context 清理和重复包装。
7. **缓存与轮换。** 目标注册 `RefreshScopeRefreshedEventListener` 清理解密值缓存，并允许 `jasypt.encryptor.refreshed-event-classes` 扩展事件。应测试事件顺序、并发读取、配置中心 refresh、parent/child context 和密钥轮换期间的新旧密文窗口。
8. **底层密码学库。** starter `4.0.3` 不等于 Jasypt core 4.x；算法/provider/密钥长度/FIPS 与历史密文保留期仍需单独安全评审。

目标默认参数、自定义 detector/resolver/filter、proxy、skip、缓存刷新、非对称与 GCM 配置均可在[固定目标 README](https://github.com/ulisesbocchio/jasypt-spring-boot/blob/e8d16bdcc92f3fd85f5d4ea05944bc0c46b9bd91/README.md)和[目标配置类](https://github.com/ulisesbocchio/jasypt-spring-boot/blob/e8d16bdcc92f3fd85f5d4ea05944bc0c46b9bd91/jasypt-spring-boot/src/main/java/com/ulisesbocchio/jasyptspringboot/properties/JasyptEncryptorConfigurationProperties.java)复核；2.x 行为以[固定 2.1.2 源码](https://github.com/ulisesbocchio/jasypt-spring-boot/tree/e492638d084c0c7d4c3b4da6004dbf592df5ca0d)为对照。

## 测试与真实样本

测试采用 OpenRewrite `RewriteTest` 的 before→after、marker、no-op、两周期幂等和配方 validation 模式；结构参考固定的 [`UpgradeDependencyVersionTest` 提交](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，没有跟随易漂移的 `main` 分支。

固定真实仓库样本包括：

- [pig-mesh/pig `f4e5a3a4...`](https://github.com/pig-mesh/pig/blob/f4e5a3a4b902dc00c192b878d7587cec93698803/pom.xml)：Maven 独占版本属性，自动更新属性。
- [OpenSPG/openspg `ceeb3ef5...`](https://github.com/OpenSPG/openspg/blob/ceeb3ef549df79ca4c4878e7ff452c73584991f3/pom.xml)：`dependencyManagement` 中的 `3.0.4`。
- [checkmarx-ltd/cx-flow `00b24fa4...`](https://github.com/checkmarx-ltd/cx-flow/blob/00b24fa410257d154403778f48758d2b474f8977/build.gradle)：带括号的 Gradle Groovy `3.0.5` 声明。
- [wells2333/sg-exam `4a7215ac...`](https://github.com/wells2333/sg-exam/blob/4a7215ace7f56555bc683e4a4c0188f86986fd9f/sg-common/build.gradle)：表格外 `1.18` 的严格 no-op。
- [dyc87112/SpringBoot-Learning `4212d163...`](https://github.com/dyc87112/SpringBoot-Learning/blob/4212d163da816c6fa5b28d59130318dac2379a73/2.x/chapter1-5/pom.xml)：同一 POM 中 starter 与 Maven plugin 都为 `3.0.3`，只升级 starter。
- [Jasypt 官方固定目标 README](https://github.com/ulisesbocchio/jasypt-spring-boot/blob/e8d16bdcc92f3fd85f5d4ea05944bc0c46b9bd91/README.md)：依赖坐标、配置键、默认参数、CLI 和扩展点用例。

此外覆盖 5 个表格源版本、未列/范围/动态/未来版本、共享 Maven 属性隔离、外部管理、Groovy map、Kotlin literal、Java 包迁移、POM/Gradle 基线、properties/YAML、CLI、custom detector 与手工 PBE 配置。测试中的 secret 均为占位符，不包含可用凭据。

## 使用与验收

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jasypt-spring-boot-starter-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jasypt.MigrateJasyptSpringBootStarterTo4_0_3
```

评审 patch 与全部 `SearchResult` 后再执行 `run`。生产验收至少包括：JDK 17/Boot 3.5 构建与镜像、每个 profile 的完整启动、历史密文读取、强算法重加密、secret 缺失快速失败、日志/bootstrap、Actuator、配置刷新、并发轮换、自定义组件和 CLI 日志审计。

模块验证命令：

```bash
mvn -pl rewrite-jasypt-spring-boot-starter-upgrade -am clean verify
```
