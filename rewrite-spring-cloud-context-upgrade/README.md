# Spring Cloud Context 4.3.2 升级配方

本模块把工作簿明确选择的 `org.springframework.cloud:spring-cloud-context` 版本升级到 `4.3.2`。它不只修改版本号：推荐配方会自动迁移可以一对一证明的 Boot 2 文档 profile 选择器，并在实际 Java、Maven、Gradle、YAML 和 properties 节点上标记需要人工决策的 Boot 3.5、Cloud 2025.0、bootstrap、Config Data、refresh、Actuator、加密、AOT/native 与 CRaC 风险。

模块坐标为 `com.huawei.clouds.openrewrite:rewrite-spring-cloud-context-upgrade`，Java 包和配方命名空间均为 `com.huawei.clouds.openrewrite.springcloudcontext`。

## 工作簿范围

来源是仓库根目录 `开源软件升级.xlsx`。行号按 Excel 可见行计数，序号为表内序号：

| Excel 行 | 表内序号 | 源版本 | 目标版本 |
|---:|---:|---:|---:|
| 393 | 392 | `2.1.5.RELEASE` | `4.3.2` |
| 1502 | 1501 | `3.1.1` | `4.3.2` |
| 1503 | 1502 | `3.1.6` | `4.3.2` |
| 1504 | 1503 | `3.1.7` | `4.3.2` |

公开低层升级配方严格使用这四个源版本，不会顺带修改 `2.1.6.RELEASE`、`3.1.5`、`4.3.1`、版本范围、动态版本或未来版本。

## 配方

### 严格低层配方

`com.huawei.clouds.openrewrite.springcloudcontext.UpgradeSpringCloudContextTo4_3_2`

只修改可以证明归属的标准 JAR 依赖：

- Maven 项目/profile 及各自 `dependencyManagement` 中的直接字面量版本；scope、optional、exclusions 等元数据保持不变。
- Maven property 必须在当前 project/profile 可见、定义唯一、值在白名单内，并且只被目标依赖的版本节点引用。根属性可供 profile 使用，profile 覆盖优先且不会泄漏到其它 profile 或项目根。
- Gradle Groovy/Kotlin 只处理根级 `dependencies {}` 中已支持 configuration 的完整字符串字面量；Groovy 还支持 map notation。
- `buildscript`、`subprojects`、`project.dependencies`、带 select 的 configuration、插件依赖、version catalog、变量插值、BOM/父 POM 管理、无版本依赖、classifier/type/ext 变体和相似坐标均不自动修改。

路径过滤只检查父目录组件且大小写不敏感。`target`、`build`、缓存以及以 `generated`、`install` 开头的目录被跳过；`install.gradle`、`generated.java` 等叶文件本身仍可处理。

### 推荐迁移配方

`com.huawei.clouds.openrewrite.springcloudcontext.MigrateSpringCloudContextTo4_3_2`

执行顺序固定为 AUTO 在前、MARK 在后：

1. AUTO：复用公开严格升级配方。
2. AUTO：只在 `application*.yml|yaml|properties` 和 `bootstrap*.yml|yaml|properties` 中把精确的文档选择器 `spring.profiles` 改为 `spring.config.activate.on-profile`；`spring.profiles.active` 和 `spring.profiles.include` 保持不变。
3. MARK：对 Java API/扩展点、Maven/Gradle 平台归属、配置和运维行为做精确 `SearchResult` 标记。

推荐配方不会猜测应该启用 legacy bootstrap 还是迁移到 Config Data，不会自动暴露 management endpoint，也不会为用户选择加密 provider、refreshable bean、BOM 或 native hints。

## 不兼容修改点与配方行为

| 修改点 | 行为 | 迁移要求 |
|---|---|---|
| Java / Boot / Cloud 基线 | MARK | `4.3.2` 对齐 Spring Cloud `2025.0.2`、Spring Boot `3.5.x` 和 Java 17+；同步 BOM/parent、compiler/toolchain、CI、容器与生产 JVM，避免只覆盖一个 Cloud 模块。 |
| Boot 2 profile 文档激活 | AUTO | 精确迁移 `spring.profiles` 为 `spring.config.activate.on-profile`；active/include 属于不同语义，不修改。 |
| Javax → Jakarta | MARK | Boot 3/Framework 6 使用 Jakarta API；Servlet、Persistence、Validation、Annotation 等依赖、import、描述符、反射和测试必须成套迁移。 |
| legacy bootstrap | MARK | `bootstrap.*`、`spring.cloud.bootstrap.enabled`、`spring-cloud-starter-bootstrap` 和 `BootstrapConfiguration` 表示父上下文架构选择；验证显式启用方式、属性源优先级、日志、上下文隔离和失败行为。 |
| Config Data | MARK | 对 `spring.config.import=configserver:` 检查 optional/fail-fast、retry、凭据、profile 激活、优先级以及 refresh 时的重新获取。 |
| `PropertySourceLocator` | MARK | 目标文档说明 locator 会进行无 profile 和有 active profile 的两阶段获取；自定义实现必须验证 source 名称、顺序、重复网络调用、异常与 refresh。 |
| `@RefreshScope` / rebinding | MARK | refresh scope 是 lazy proxy/cache，不会自动让配置类中的所有 bean 可刷新；验证对象身份、生命周期、删除值、record/immutable properties、DataSource 及 extra/never-refreshable。 |
| AOT / native | MARK | context refresh 不支持 AOT/native，必须令 `spring.cloud.refresh.enabled=false`；迁移 Spring Native hints 到 RuntimeHints，并验证 bootstrap、加密和反射可达性。 |
| CRaC / restart refresh | MARK | `4.3.2` 默认安装 `RefreshScopeLifecycle`，checkpoint/restore 后触发 refresh；验证重复副作用、密钥/配置新鲜度，必要时设置 `spring.cloud.refresh.on-restart.enabled=false`。 |
| environment/refresh/restart endpoint | MARK | endpoint 可改变运行态；验证显式 exposure、认证授权、CSRF、审计、secret sanitization、pause/resume/restart 耦合和回滚。 |
| Writable Environment 构造器 | MARK | 构造器已跟随 Boot Actuator 增加 `SanitizingFunction`、`Show`、roles；优先使用自动配置，定制代码需人工适配并测试脱敏。 |
| `ContextRefresher` 扩展 | MARK | `REFRESH_ARGS_PROPERTY_SOURCE` 从 protected 成员移到 `LegacyContextRefresher` private 实现；自定义子类和对该字段的引用不能安全自动重写。 |
| `NamedContextFactory` | MARK | child context 从固定 `AnnotationConfigApplicationContext` 扩展为 `GenericApplicationContext`/AOT initializer 流程；检查覆盖方法、lazy creation、parent inheritance、隔离、销毁和 native registration。 |
| `RestartEndpoint` | MARK | listener event 和 lifecycle 同步方式变化，restart 仍默认关闭；检查自定义 listener、pause handler、timeout、shutdown hook 与安全策略。 |
| 加密 provider | MARK | 旧线可选依赖 `spring-security-rsa`，目标使用 Spring Security Crypto 并可选 Bouncy Castle；验证 key 格式/provider、indexed property、失败策略、RuntimeHints 和 secret redaction。 |
| `EnvironmentUtils` | MARK | 该类自 `4.3.0` 起 `forRemoval=true`；把 `getSubProperties` 依赖迁到应用自有的 `Environment`/`PropertyResolver`/`Binder` 边界。 |
| 版本/BOM/变体归属 | MARK | Maven/Gradle 无版本、外部 property/catalog/platform、Cloud/Boot BOM 不对齐和 classifier/type/ext 变体只标记；修改实际 owner，不在局部强塞版本。 |

## spec → recipe → test 对应

| 规格 | 实现 | 主要测试 |
|---|---|---|
| XLSX 四条精确版本路径 | `UpgradeSelectedSpringCloudContextDependency` | 四个源版本、目标和相邻/范围/动态版本正反例 |
| Maven project/profile/DM/property 归属 | `SpringCloudContextSupport`、严格升级配方 | 根属性供 profile 使用、profile override、不泄漏、同名 profile 独立、共享引用和跨 POM owner 保护 |
| Gradle 根 DSL、map、Kotlin、路径、变体 | 同上 | root/nested/select、string/map、Groovy/Kotlin、classifier/ext、catalog、生成/缓存目录和叶文件 |
| profile 文档激活 | `MigrateLegacyProfileActivation` | YAML/properties、flat/nested、application/bootstrap、active/include 负例、路径和两轮幂等 |
| Java API 与扩展点 | `FindSpringCloudContextJavaRisks` | Javax、Spring Native、EnvironmentUtils、RefreshScope、locator/refresher 子类、移动字段和生成路径 |
| 平台及构建归属 | `FindSpringCloudContextBuildRisks` | 同一标准 POM/根 Gradle owner 存在 context consumer 时，检查 Java、Boot/Cloud BOM、profile property、platform/map、无版本/外部/变体、native、RSA、bootstrap starter；sibling owner 不误标 |
| bootstrap/Config Data/refresh/endpoint/encryption | properties/YAML risk recipes | 精确 entry/scalar marker、普通业务键负例、bootstrap 文件、生成路径和 marker 幂等 |
| AUTO-before-MARK 与公开配方复用 | `rewrite.yml` | 配方发现、组合顺序、升级后无误报、外部 owner 只标记、两周期幂等 |

所有 MARK 测试直接遍历 AST 检查 `SearchResult` 数量和消息片段，marker 打印被隐藏，避免把注释渲染格式误当成语义断言。

## 可复现的官方依据

### 固定 tag 和提交

| 版本 | tag 解引用 commit | 用途 |
|---|---|---|
| `2.1.5.RELEASE` | [`9970a06c7bb2d8053bcf9ff7063ba16433c29c14`](https://github.com/spring-cloud/spring-cloud-commons/commit/9970a06c7bb2d8053bcf9ff7063ba16433c29c14) | 工作簿旧线基线 |
| `3.1.1` | [`8925ab5e3fbbc056cfcc3136715e67e77d3ea43c`](https://github.com/spring-cloud/spring-cloud-commons/commit/8925ab5e3fbbc056cfcc3136715e67e77d3ea43c) | 工作簿源版本 |
| `3.1.6` | [`0668ebf67d279b47b8503bd201805972bc7b5a96`](https://github.com/spring-cloud/spring-cloud-commons/commit/0668ebf67d279b47b8503bd201805972bc7b5a96) | 工作簿源版本 |
| `3.1.7` | [`a0b8a51742c3bbaeb8bd65a9511d5a55dc7ca9e8`](https://github.com/spring-cloud/spring-cloud-commons/commit/a0b8a51742c3bbaeb8bd65a9511d5a55dc7ca9e8) | 工作簿源版本 |
| `4.3.2` | [`c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6`](https://github.com/spring-cloud/spring-cloud-commons/commit/c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6) | 目标源码、文档和依赖基线 |

目标 parent [`spring-cloud-build 4.3.3`](https://github.com/spring-cloud/spring-cloud-build/blob/f3a5430644549cccae2edf8e8077ceaade88c490/pom.xml) 固定 Java `17` 与 Boot `3.5.13`；[`spring-cloud-release v2025.0.2`](https://github.com/spring-cloud/spring-cloud-release/blob/1622bc024e91af0bbd1d4ac071a1fcd579f81f6a/spring-cloud-dependencies/pom.xml) 固定 commons/context `4.3.2`。

### 对应不兼容修改的官方证据

- 目标固定文档 [`application-context-services.adoc`](https://github.com/spring-cloud/spring-cloud-commons/blob/c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6/docs/modules/ROOT/pages/spring-cloud-commons/application-context-services.adoc) 记录 bootstrap/Config Data、locator 两阶段调用、RefreshScope、AOT/native 限制和 CRaC restart refresh。
- [`34687af5106bd5d1f31c9167875f2d893b8ada65`](https://github.com/spring-cloud/spring-cloud-commons/commit/34687af5106bd5d1f31c9167875f2d893b8ada65) 是 Boot 3 的 `spring.profiles` → `spring.config.activate.on-profile` 一对一依据。
- [`9eb3a08c148762502fbb35aa458031d556d1bc62`](https://github.com/spring-cloud/spring-cloud-commons/commit/9eb3a08c148762502fbb35aa458031d556d1bc62) 调整 writable environment endpoint 构造器；目标类见 [`WritableEnvironmentEndpoint`](https://github.com/spring-cloud/spring-cloud-commons/blob/c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6/spring-cloud-context/src/main/java/org/springframework/cloud/context/environment/WritableEnvironmentEndpoint.java)。
- [`ee490fe7c8cd4a3d7cc2b0b4bda7cf2becdf0ec8`](https://github.com/spring-cloud/spring-cloud-commons/commit/ee490fe7c8cd4a3d7cc2b0b4bda7cf2becdf0ec8) 把 refresh 参数源常量移入 legacy 实现；目标 [`ContextRefresher`](https://github.com/spring-cloud/spring-cloud-commons/blob/c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6/spring-cloud-context/src/main/java/org/springframework/cloud/context/refresh/ContextRefresher.java) 不再暴露它。
- [`78eb9a0fd6d956187b306cff77b14242a2c61e41`](https://github.com/spring-cloud/spring-cloud-commons/commit/78eb9a0fd6d956187b306cff77b14242a2c61e41) 为 named child contexts 引入 AOT initializer；目标实现固定在 [`NamedContextFactory`](https://github.com/spring-cloud/spring-cloud-commons/blob/c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6/spring-cloud-context/src/main/java/org/springframework/cloud/context/named/NamedContextFactory.java)。
- [`d0f6d2b2e94025df6ea4a129bdb0d1c01c0195ab`](https://github.com/spring-cloud/spring-cloud-commons/commit/d0f6d2b2e94025df6ea4a129bdb0d1c01c0195ab) 更换 restart lifecycle event；[`9449edf93b64c691fc8511601402b7114bd6e3e0`](https://github.com/spring-cloud/spring-cloud-commons/commit/9449edf93b64c691fc8511601402b7114bd6e3e0) 增加 restart refresh。
- [`d87c755c929352109e9ed030e485f1d7d2c684ed`](https://github.com/spring-cloud/spring-cloud-commons/commit/d87c755c929352109e9ed030e485f1d7d2c684ed) 移除 `spring-security-rsa`；目标 [`spring-cloud-context/pom.xml`](https://github.com/spring-cloud/spring-cloud-commons/blob/c58eb472dbeb0fabbd3def291dc0e9cb2320f7a6/spring-cloud-context/pom.xml) 显示 Spring Security Crypto 和可选 Bouncy Castle。
- [`72294c24fba885542dfa97fa6585057df6cde181`](https://github.com/spring-cloud/spring-cloud-commons/commit/72294c24fba885542dfa97fa6585057df6cde181) 将 `EnvironmentUtils` 标为 `since=4.3.0, forRemoval=true`。

## 真实公开仓库固定用例

测试样本固定到不可变 commit，并缩减为单一 owner/行为：

- `3.1.1` parent property + child provided dependency：[`juhewu/juhewu-openfeign-spring-cloud-project@8e5dff.../pom.xml`](https://github.com/juhewu/juhewu-openfeign-spring-cloud-project/blob/8e5dff0a6dd3dca53216a226bfc8a08a617b5127/pom.xml#L28) 与 [`starter/pom.xml`](https://github.com/juhewu/juhewu-openfeign-spring-cloud-project/blob/8e5dff0a6dd3dca53216a226bfc8a08a617b5127/juhewu-openfeign-spring-cloud-starter/pom.xml#L50)。跨 POM owner 不由低层配方抢占。
- `3.1.6` 直接版本：[`edgarrth/java-kafka-streams@8de835.../pom.xml`](https://github.com/edgarrth/java-kafka-streams/blob/8de835b42b4e6e6471e4d6d88674b7c64f8101f8/pom.xml#L116)。
- `3.1.7` 直接版本和外部依赖标记：[`Azure/azure-sdk-for-java@521bf18.../pom.xml`](https://github.com/Azure/azure-sdk-for-java/blob/521bf18d377f945c509874c73051e43fb0766279/sdk/spring/spring-cloud-azure-appconfiguration-config/pom.xml#L31)。
- BOM 管理的无版本依赖：[`alibaba/spring-cloud-alibaba@c5e2723.../nacos-config/pom.xml`](https://github.com/alibaba/spring-cloud-alibaba/blob/c5e27237fb53832e5758e15f8eeca27cf4126b06/spring-cloud-alibaba-nacos-config/pom.xml#L31)；严格升级保持不变，build risk 指向 owner。
- 官方项目中的无版本依赖：[`spring-cloud/spring-cloud-netflix@72bffe.../eureka-server/pom.xml`](https://github.com/spring-cloud/spring-cloud-netflix/blob/72bffe037f315a818e33099e9844f87c871cd660/spring-cloud-netflix-eureka-server/pom.xml#L41)，用于验证不会局部强塞版本。

测试结构参考仓库当前 OpenRewrite `8.87.5` 对应的固定提交 [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，包括 [`ChangeDependencyTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-gradle/src/test/java/org/openrewrite/gradle/ChangeDependencyTest.java)、[`ChangePropertyKeyTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-yaml/src/test/java/org/openrewrite/yaml/ChangePropertyKeyTest.java) 及 OpenRewrite marker 测试方式，并增加 owner、近似负例、路径边界和两轮幂等测试。

## 运行

```bash
mvn -f rewrite-spring-cloud-context-upgrade/pom.xml clean verify
```

在已加载模块 recipe JAR 的工程中：

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springcloudcontext.MigrateSpringCloudContextTo4_3_2
```

建议在独立分支运行，先审查所有 `~~(...)~~>` 标记，再执行 clean build、配置客户端/refresh/endpoint 集成测试、native/AOT 构建（如适用）和实际 restart/CRaC 演练。
