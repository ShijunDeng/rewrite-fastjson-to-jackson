# Kubernetes Java Client 升级到 25.0.0-legacy

本模块处理表格坐标 `io.kubernetes:client-java` 的全部指定记录：`11.0.2`、`16.0.2`、`16.0.3`、`17.0.2` 和 `18.0.1`，目标版本为 `25.0.0-legacy`。

只升级依赖的窄配方：

```text
com.huawei.clouds.openrewrite.kubernetesclientjava.UpgradeKubernetesClientJavaDependencyTo25_0_0_Legacy
```

用于完整迁移流程的保守组合配方：

```text
com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo25_0_0_Legacy
```

组合配方目前有意只组合依赖升级。官方客户端的 Java 类大量由对应 Kubernetes OpenAPI 重新生成；同名方法是否还能迁移取决于资源的 API group/version、集群版本、请求语义和序列化约定。没有这些上下文时机械修改调用会把编译错误变成更隐蔽的运行时错误，因此本模块把源码热点保留给类型检查和人工验证。

## 自动处理范围与安全边界

配方会处理 Maven 与带有 Gradle 语义模型的 Gradle Groovy 构建，覆盖直接版本、Maven 属性、`dependencyManagement` 和 Gradle 字符串/Map 写法。它具有以下边界：

- 只匹配表格列出的 5 个精确旧版本；`11.0.3`、`16.0.1`、`17.0.1`、`18.0.0`、`19.0.1`、`25.0.0`、`26.0.0` 等未列版本均不改，避免把非 legacy 25.x 降为 `25.0.0-legacy`。
- 不覆盖由外部 parent/BOM/platform 管理、且本工程没有显式版本的依赖；应在真正拥有版本的位置运行配方或升级平台。
- 本地 `dependencyManagement` 会升级；直接依赖继续保持无版本声明。
- 如果同一个 Maven 属性同时管理 `client-java`、`client-java-api`、`client-java-extended`、`client-java-api-fluent` 或 Spring integration，修改属性会让这些模块一起对齐到目标版本。这通常能避免混版，但必须检查这些模块在目标版本是否仍适合当前运行时。
- 不修改具有独立字面量版本的 companion artifact，也不自动添加缺少的 `client-java-api-fluent` 等模块。
- 不修改 Gradle version catalog。`version.ref` 的别名可被多个不相关库共享，单靠配置式配方无法安全推断 `[versions]` 中应该改哪一个 key；请在 `gradle/libs.versions.toml` 人工把仅由该坐标使用的版本改为 `25.0.0-legacy`。
- 如果 parser/tooling marker 不能把 Gradle `ext`/外部脚本属性解析成该依赖的 requested version，配方同样安全 no-op；在拥有该属性的文件中人工升级，或先让 Gradle Tooling API 提供完整模型。这样可避免因为同一文件存在一个旧版本字符串而误降另一个普通 25.x 依赖。
- 解析器测试中的 Kotlin DSL 没有 Gradle Tooling API 语义模型时安全 no-op；生产运行应让 OpenRewrite Gradle 插件取得完整 `GradleProject` marker。
- 不更新 lockfile、依赖校验文件、SBOM、容器镜像或部署清单，也不会改业务源码。

## `-legacy` 的准确含义

官方从 `20.0.0`（Kubernetes 1.28）开始提供两套生成接口：

- 普通 `20.0.0+` 把生成 API 的可选参数收拢为 option/request 对象，并停止支持 Java 8。
- `20.0.0-legacy` 及后续 `-legacy` 继续保留 20 之前的多参数 SDK 形状，并保持 Java 8 bytecode 目标。

因此 `25.0.0-legacy` 不是“旧 Kubernetes 模型”。它仍是面向 Kubernetes 1.34 生成的模型/API，只保留旧式 Java 调用界面。legacy 也不保证每一个 11/16/17/18 的方法、模型、枚举或服务器行为不变。官方说明见 [v25 README](https://github.com/kubernetes-client/java/blob/4f51c2b18bd66fa61e01e8d5190bc24e9ce324a3/README.md)，目标发布物可在 [Maven Central](https://repo1.maven.org/maven2/io/kubernetes/client-java/25.0.0-legacy/) 和 [Javadocs](https://javadoc.io/doc/io.kubernetes/client-java/25.0.0-legacy/index.html) 核对。

## Kubernetes 与客户端版本跨度

官方每次为新的 Kubernetes OpenAPI 重新生成客户端就提升 Java client major；major 升级必须预期 generated stub 变化。本次跨度中的关键对应关系如下：

| Java client | 对应的 Kubernetes OpenAPI |
| --- | --- |
| 11.x | 1.19 |
| 16.x | 1.24 |
| 17.x | 1.25 |
| 18.x | 1.26 |
| 19.x | 1.27 |
| 20.x | 1.28 |
| 21.x | 1.30 |
| 22.x | 1.31 |
| 23.x | 1.32 |
| 24.x | 1.33 |
| 25.x | 1.34 |

`25.0.x` 对旧集群可能包含服务器不存在的新资源/字段；公共资源通常可以工作，但向旧集群发送新字段、枚举或 API version 仍可能失败。反方向，11.x 客户端无法表达新集群的新增 API。部署组合应逐项对照官方 [Versioning and Compatibility](https://github.com/kubernetes-client/java/wiki/2.-Versioning-and-Compatibility)，不能把“客户端能建立 HTTP 连接”当成兼容证明。

## 需要处理的不兼容修改点

| 领域 | 不兼容点与迁移要求 |
| --- | --- |
| OpenAPI 模型与 API class | 1.19→1.34 期间生成代码多次增加、删除和重命名 model、API class、字段、枚举与方法。所有 `io.kubernetes.client.openapi.apis.*`、`models.*`、builder 和 fluent 调用都要重新编译；不要用反射、字符串类名或 JSON 字段绕过编译检查 |
| 已移除的 Kubernetes API | 旧的 Ingress/CRD/webhook `v1beta1` 在 1.22 移除；PodSecurityPolicy、CronJob `v1beta1`、EndpointSlice/Event `v1beta1`、HPA `v2beta1` 等在 1.25 前后移除；HPA `v2beta2` 在 1.26 移除；flow-control beta 版本又在 1.29/1.32 演进。改用稳定 API，并同时迁移 YAML/Helm/Operator CRD，而不只是 Java import |
| 普通 API 调用 | legacy 保留“必填参数 + 多个 nullable 可选参数”的形状，但各 Kubernetes release 会新增/删除 query 参数和操作。逐个核对目标 [client-java-api 25.0.0-legacy Javadoc](https://javadoc.io/doc/io.kubernetes/client-java-api/25.0.0-legacy/index.html)，特别是 `pretty`、`dryRun`、`fieldManager`、`fieldValidation`、`resourceVersion`、selector、timeout 和 pagination |
| `Call` 与异步回调 | `{operation}Call`、`WithHttpInfo`、async callback 的参数顺序和异常路径来自生成器。不要按参数个数全局替换；检查 callback 在 HTTP 错误、反序列化错误、取消和 timeout 时是否仍完成业务 future/latch，并验证 response body 被关闭 |
| Watch | list-watch 必须带 `watch=true`，正确传 `resourceVersion`/timeout/allowWatchBookmarks，并处理 `410 Gone` 后重新 list。校验 `Watch.createWatch` 的 `TypeToken<Watch.Response<T>>`、`V1Status` 错误事件、关闭/取消、断线重连、过期 RV 和 informer resync，避免线程与 OkHttp response 泄漏 |
| Informer/controller | 新模型可能改变 key、generation、conditions 和 status；验证 shared informer factory 生命周期、缓存复用、transform、异常 handler、resync 与 leader election。不能假设旧 handler 对新 enum/unknown 字段穷尽 |
| Authentication | 重新验证 kubeconfig、in-cluster token 文件刷新、exec credential `client.authentication.k8s.io` 版本、OIDC、客户端证书链和代理。目标 legacy 仍带 AWS SDK 1.x STS，而普通 25.0.0 已迁到 AWS SDK 2.x；不要照普通版文档把 auth classpath 混入 legacy |
| `ApiClient`/OkHttp 定制 | 11.0.2 的 OkHttp 3.14.9 跨到目标的 OkHttp 5.3.2。直接访问 `getHttpClient()`、自定义 interceptor/authenticator/DNS/proxy/TLS/dispatcher/connection pool 的源码和二进制兼容性必须单独验证；Maven 解析还要确认实际获得 JVM class artifact，而不是仅有 multiplatform metadata |
| Patch 与 apply | `V1Patch` 的 JSON Patch、Merge Patch、Strategic Merge Patch、Server-Side Apply content type 不可互换。核对 `PATCH_FORMAT_*`、field manager、force/conflict、field validation、subresource/status 和 CRD 是否支持 strategic merge；用真实 apiserver 验证请求头和响应 |
| 删除语义 | Kubernetes OpenAPI 历史上对 delete response 的 schema 与实际响应可能不一致，可能返回对象或 `Status`。不要只依赖一个固定 Gson type；验证 propagation policy、grace period、preconditions 和 404/409 的业务处理 |
| 日期时间 | 从 client 12 起由 Joda-Time 转到 JDK `OffsetDateTime`。从 11.0.2 升级的代码必须改造类型、formatter、时区/offset、毫秒精度、数据库列和 JSON golden file，不能仅靠 IDE 自动 import |
| Gson/Jackson | 目标 parent 管理 Gson 2.13.2，并为生成 API 引入 Jackson 2.19.2。检查自定义 `Gson`、`JSON` helper、TypeAdapter、unknown field、null/default、byte array、`Quantity`、`IntOrString`、`additionalProperties`、CRD extension 与 OffsetDateTime 的 round-trip |
| YAML/SnakeYAML | SnakeYAML 从 11.0.2 的 1.27/16.0.3 的 1.33 跨到 2.5，构造器 API、安全默认值、tag/类型加载和重复 key 处理均可能变化。不要恢复不安全的任意类构造；对多文档 YAML、CRD、quantity/int-or-string 做 golden test |
| Fluent builder 拆分 | client 13 起 fluent builder 与生成器拆到 `client-java-api-fluent`/`client-java-api-fluent-gen`。直接使用 `*Builder`/`*Fluent` 的工程必须显式对齐 companion module；本配方不会自动推断或添加它 |
| 注解与模块系统 | 目标 API 同时涉及 `javax.annotation` 与 Jakarta annotation 依赖。检查 JPMS/OSGi import、shade/relocate、native-image reflection config 与 split package；不能盲目把业务中的全部 `javax.*` 改成 `jakarta.*` |
| 传递依赖大跨度 | 目标 legacy parent 还管理 SLF4J 2.0.17、protobuf 4.33.1、Bouncy Castle 1.82、commons-compress 1.28.0 等。排除/锁定这些库的工程可能出现 `NoSuchMethodError`、日志绑定冲突、protobuf ABI 或安全 provider 差异，应运行 dependency convergence 和链接测试 |
| Java 与 Spring | `25.0.0-legacy` 核心仍以 Java 8 为目标，但目标 parent 的 Spring integration 线已到 Spring 6.2/Spring Boot 3.5，实际要求更高 JDK/Jakarta 栈。共享 Maven 属性会一起升级 companion 时，Java 8 应用不能据此假定 Spring 模块仍能运行 |
| 集群准入与默认值 | 新服务器的 defaulting、validation、admission policy、Pod Security、field ownership 和 unknown-field pruning 会改变相同 Java 对象的结果。必须在与生产同 minor、同 admission/webhook 配置的测试集群执行 CRUD、watch、patch 和回滚 |

Kubernetes 官方提供了按 server release 整理的 deprecated API 迁移说明，例如 [1.22](https://kubernetes.io/docs/reference/using-api/deprecation-guide/#v1-22)、[1.25](https://kubernetes.io/docs/reference/using-api/deprecation-guide/#v1-25)、[1.26](https://kubernetes.io/docs/reference/using-api/deprecation-guide/#v1-26)、[1.29](https://kubernetes.io/docs/reference/using-api/deprecation-guide/#v1-29) 和 [1.32](https://kubernetes.io/docs/reference/using-api/deprecation-guide/#v1-32)。这些服务器侧变化是 generated Java class 消失或变化的根因，应与客户端 Javadocs 一起检查。

## 真实仓库与官方测试参考

测试从以下公开仓库的固定 commit 提取并约简：

- [Spring Cloud Kubernetes 305c1658](https://github.com/spring-cloud/spring-cloud-kubernetes/blob/305c16585471514b528de61b0e3f7dc202dc9ae5/spring-cloud-kubernetes-dependencies/pom.xml)：`11.0.2` 属性同时管理 core、extended 和 Spring integration，验证 dependencyManagement 与共享属性。
- [Apache ShenYu 2728a79c](https://github.com/apache/shenyu/blob/2728a79c8a283bd4076eed7ebb93f0e9e0442aa2/shenyu-admin/pom.xml)：`17.0.2` 属性支持直接依赖；其 [KubernetesScaler](https://github.com/apache/shenyu/blob/2728a79c8a283bd4076eed7ebb93f0e9e0442aa2/shenyu-admin/src/main/java/org/apache/shenyu/admin/scale/scaler/KubernetesScaler.java) 提供 legacy 多参数 generated API 的源码保留样例。
- [Apache Submarine 389c8fd9](https://github.com/apache/submarine/blob/389c8fd919411f1edb2aec05e6ed2bca83dcf15d/pom.xml)：`17.0.2` 在 dependencyManagement 中同时对齐 core 与 fluent module，验证 companion 坐标不被擅自重命名。
- [ButterCam/sisyphus f8d72b42](https://github.com/ButterCam/sisyphus/blob/f8d72b422f21ed718c778bc94dbe192e4892a405/gradle/libs.versions.toml)：`16.0.3` Gradle version catalog，验证不能可靠推断 `version.ref` 时安全 no-op。

实现与测试风格参考 OpenRewrite 官方 [UpgradeDependencyVersionTest 1.59.0](https://github.com/openrewrite/rewrite-java-dependencies/blob/v1.59.0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 和 [FindDependencyTest 1.59.0](https://github.com/openrewrite/rewrite-java-dependencies/blob/v1.59.0/src/test/java/org/openrewrite/java/dependencies/FindDependencyTest.java)。当前 27 个测试覆盖表格全部版本、精确旧版本门控、直接/属性/受管 Maven、Gradle 字符串/Map、Gradle 属性安全回退、目标/现代/后续/邻近版本 no-op、无版本与 version catalog 边界、相似/companion 坐标、scope/classifier/exclusion 保留、真实 generated API 源码保留，以及所有配方的 discovery/validation。

## 使用和验收

先执行 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-kubernetes-client-java-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo25_0_0_Legacy
```

应用 patch 后至少执行：

1. Java/Kotlin 全量编译、二进制链接、dependency convergence、重复类、SLF4J binding、JPMS/OSGi/shading/native-image 检查。
2. 对生产 Kubernetes minor 的真实测试集群运行 list/get/create/update/delete、pagination、selectors、watch/reconnect/410、informer/resync、exec/log/attach/port-forward。
3. 分别验证 kubeconfig、in-cluster、exec/OIDC、token rotation、证书、代理、TLS 与云厂商认证。
4. 对 JSON/YAML/model 做 round-trip golden test，对 JSON Patch/Merge Patch/SSA/status subresource 做并发冲突测试。
5. 验证旧集群、新集群、RBAC、admission webhook、Pod Security 和 API removal；同时升级 Helm/YAML/CRD。
6. 刷新 Gradle/Maven lockfile、依赖校验、SBOM 与镜像扫描结果，再做 canary 和回滚演练。

模块自身验证：

```bash
mvn -f rewrite-kubernetes-client-java-upgrade/pom.xml clean verify
```
