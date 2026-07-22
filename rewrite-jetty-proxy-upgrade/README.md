# Jetty Proxy 12.1.8 迁移模块

本模块为 `org.eclipse.jetty:jetty-proxy` 提供从 Jetty 9.4 到 12.1.8 的保守迁移。Jetty 12 将同一坐标重新定位为 Servlet 无关的 Core `ProxyHandler`，旧 `ProxyServlet` 则拆分到 EE8、EE9、EE10、EE11 四套 artifact。推荐配方不会替业务选择 Servlet 命名空间，只自动修改能够从 Jetty 固定版本源码和迁移文档证明的一对一类型移动，其余内容使用精确 `SearchResult` 标记。

## 配方

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.jettyproxy.MigrateJettyProxyTo12_1_8` | 推荐入口：严格升级依赖、迁移确定性类型移动，并运行 build/source/config 风险扫描 |
| `com.huawei.clouds.openrewrite.jettyproxy.UpgradeJettyProxyTo12_1_8` | 仅升级 Maven/Gradle 依赖，不修改源码和配置 |
| `com.huawei.clouds.openrewrite.jettyproxy.MigrateDeterministicJetty12Types` | 仅迁移官方明确、目标仍保持同名同职责的包移动 |
| `com.huawei.clouds.openrewrite.jettyproxy.FindJettyProxy12BuildMigrationRisks` | 标记 Java 17、外部版本所有者、Jetty 版本混用和 Servlet/Core 环境选择 |
| `com.huawei.clouds.openrewrite.jettyproxy.FindJettyProxy12SourceAndConfigRisks` | 标记 Servlet proxy、Handler、client content/listener、HttpClient 和 proxy module 风险 |

运行推荐配方：

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jettyproxy.MigrateJettyProxyTo12_1_8
```

## 表格规格和严格升级边界

目标版本固定为 `12.1.8`。自动升级白名单严格等于表格单元格中明确可见的两个源版本：

- `9.4.39.v20210325` → `12.1.8`
- `9.4.45.v20220203` → `12.1.8`

不会推断版本区间，也不会展开诸如“共 N 个版本”的折叠文本。

Maven 自动升级仅作用于项目或一级 profile 的 `dependencies` / `dependencyManagement`：

- 直接字面量必须等于上述两个版本之一；
- 根 `<properties>` 属性必须只声明一次、没有 profile 遮蔽，并且所有 XML 文本和属性引用都专属于目标依赖；
- 无版本、BOM/父 POM 管理、未解析属性、范围、动态版本、classifier 和自定义 type 均不自动修改；显式标准 `type=jar` 可安全升级；
- 插件依赖、插件配置中的相似 XML、嵌套伪项目均保持原样。

Gradle 自动升级仅作用于项目一级 `dependencies {}` 中的已知配置：

- 支持 Groovy 固定三段字符串、两种 Groovy map 写法和 Kotlin 固定三段字符串；
- 插值、version catalog、四段坐标、classifier/ext/type/variant map 不修改；
- `buildscript`、`publishing`、`subprojects` 和任何外层自定义嵌套 DSL 不修改。

`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.idea`、`.mvn`、`.m2`、`.yarn`、`node_modules`、`vendor` 下的文件不会被修改或标记。

## 确定性自动迁移

以下类型在 Jetty 12 保持同名和同一公开职责，只移动了包，因此 Java 和 properties/YAML/非 POM XML 中的完整类名可以安全迁移：

- `org.eclipse.jetty.proxy.ConnectHandler` → `org.eclipse.jetty.server.handler.ConnectHandler`；
- `org.eclipse.jetty.client.api.Authentication`、`AuthenticationStore`、`Connection`、`ContentResponse`、`Destination`、`Request`、`Response`、`Result` → `org.eclipse.jetty.client` 同名类型；
- `org.eclipse.jetty.client.util.AbstractAuthentication`、`BasicAuthentication`、`BufferingResponseListener`、`DigestAuthentication`、`InputStreamResponseListener`、`SPNEGOAuthentication` → `org.eclipse.jetty.client` 同名类型；
- `org.eclipse.jetty.client.http.HttpClientTransportOverHTTP`、`HttpClientConnectionFactory` → `org.eclipse.jetty.client.transport` 同名类型。

文本替换有完整类型边界，`RequestFactory` 等相似类名不会被误改。POM 属性值不属于运行时类配置，因此不会被类型配方修改。

## 不自动选择 Servlet 环境

Jetty 9 的 `org.eclipse.jetty:jetty-proxy` 同时包含 `ProxyServlet` 和 `ConnectHandler`。Jetty 12.1.8 的同坐标只提供 Core `ProxyHandler`，Servlet 实现需要根据应用环境选择：

| 环境 | Jetty 12 artifact | ProxyServlet 包 |
| --- | --- | --- |
| Java EE 8 / `javax.servlet` | `org.eclipse.jetty.ee8:jetty-ee8-proxy` | `org.eclipse.jetty.ee8.proxy` |
| Jakarta EE 9 | `org.eclipse.jetty.ee9:jetty-ee9-proxy` | `org.eclipse.jetty.ee9.proxy` |
| Jakarta EE 10 | `org.eclipse.jetty.ee10:jetty-ee10-proxy` | `org.eclipse.jetty.ee10.proxy` |
| Jakarta EE 11 | `org.eclipse.jetty.ee11:jetty-ee11-proxy` | `org.eclipse.jetty.ee11.proxy` |
| Servlet 无关 Core Handler | `org.eclipse.jetty:jetty-proxy` | `org.eclipse.jetty.proxy.ProxyHandler` |

选择会影响 Servlet API、部署模块、包名、请求/响应签名和运行容器，不能仅凭旧依赖确定，因此本模块只添加明确标记。

## 标记的不兼容修改

| 不兼容点 | 模块处理 |
| --- | --- |
| Jetty 12.1 要求 Java 17+ | 仅当构建真正拥有标准 target 依赖时，标记 Maven compiler 属性/配置及 Gradle compatibility/toolchain 的低版本 |
| 版本由 BOM、父 POM、platform、catalog、动态版本控制 | 标记目标依赖，要求迁移真实版本所有者；不注入局部版本 |
| classifier、自定义 type/ext/variant、四段坐标 | 保持依赖不变并精确标记该声明，要求选择标准 runtime artifact 或明确的 Jetty 12 变体 |
| `jetty-server`、`jetty-client`、HTTP2/HTTP3 等仍在 9.x 或其他版本 | 标记显式混用项，要求统一 12.1.8，并处理 12.x artifact 重命名 |
| 旧 Servlet API、`jetty-servlet`/`webapp` 或 EE proxy artifact | 标记精确坐标，要求显式选择 Core 或 EE8/9/10/11 |
| `ProxyServlet`、`AsyncProxyServlet`、`AsyncMiddleManServlet`、`BalancerServlet` 等 | 标记精确 import、继承、构造、调用和结构化配置；不猜测 EE 包 |
| `ConnectHandler` 子类 | 包名可自动迁移，但子类必须改为 Jetty 12 的异步 `boolean handle(Request, Response, Callback)`，并保证 Callback 只完成一次 |
| `ProxyConnection` 和旧 Handler 内部扩展 | 标记为 Handler/tunnel 生命周期重构，要求验证背压、超时和连接关闭 |
| `ContentProvider` 与 `*ContentProvider` helper | 标记为 `Request.Content` / `*RequestContent` 迁移，要求验证 demand、buffer 释放、abort 和所有权 |
| `onResponseContentDemanded` | 标记为 `onResponseContentSource` demand-pull 模型迁移，要求验证 chunk 请求和完成顺序 |
| 带 TLS/transport 参数的旧 `HttpClient` 构造 | 标记为 `ClientConnector` / transport 显式配置；无参构造不标记 |
| `proxy.mod`、`--module=proxy`、`jetty.proxy.*` 和旧 Servlet XML init-param | 标记 Core 与 EE proxy module 语义变化；相似 key/value 不标记 |

所有 marker 均有幂等保护。类型风险依赖类型归属；构建风险依赖精确 group/artifact 和标准依赖归属；配置风险依赖完整类名、完整 key 或确切 module 语法，不使用模糊 artifactId/方法名搜索。

## 规格到测试的映射

| 规格 | 实现 | 测试证据 |
| --- | --- | --- |
| 两个可见源版本严格升级 | `UpgradeSelectedJettyProxyDependency` | Maven/Gradle 参数化 before→after（4 个执行用例） |
| Maven project/profile/dependencyManagement | 同上 | literal、profile、managed、plugin/config lookalike 和 metadata 保留用例 |
| 根属性独占、共享、属性引用、重复、遮蔽 | 同上 | exclusive、Apache Geaflow shared、attribute、duplicate、profile shadow 用例 |
| Gradle 一级 dependencies 和坐标形状 | 同上 | string/map/Kotlin、interpolation/catalog/four-part/variant、nested/buildscript 用例 |
| 一对一 Java 类型移动 | `MigrateJetty12DeterministicTypes` | 多类型 before→after、配置格式、边界 no-op、generated 防护和幂等用例 |
| Servlet/Core 选择与 Handler/API 风险 | `FindJettyProxy12MigrationRisks` | Jetty 固定源码衍生的 ProxyServlet/ConnectHandler、content/listener/HttpClient/module MARK 用例 |
| Java 17、外部所有者、Jetty 对齐、Servlet artifact | `FindJettyProxy12BuildRisks` | Maven/Groovy/Kotlin MARK、无 target/variant/nested/generated no-op 和幂等用例 |
| 推荐组合顺序 | YAML composite recipe | 同一测试验证依赖升级、ConnectHandler 移动和 Servlet/Handler 两类标记 |

当前共有 50 个 JUnit 执行用例。测试风格参考 OpenRewrite `v8.87.5` 固定提交中的 [Maven `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-maven/src/test/java/org/openrewrite/maven/UpgradeDependencyVersionTest.java) 与 [Gradle `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-gradle/src/test/java/org/openrewrite/gradle/UpgradeDependencyVersionTest.java)。

真实固定仓库用例包括：

- Jetty 9.4.45 固定提交的 [`ProxyServlet.java`](https://github.com/jetty/jetty.project/blob/4a0c91c0be53805e3fcffdcdcc9587d5301863db/jetty-proxy/src/main/java/org/eclipse/jetty/proxy/ProxyServlet.java) 和 [HTTP/2 client POM 中的 versionless `jetty-proxy`](https://github.com/jetty/jetty.project/blob/4a0c91c0be53805e3fcffdcdcc9587d5301863db/jetty-http2/http2-client/pom.xml)；
- Apache Geaflow 固定提交的 [共享 `jetty.version` 管理形态](https://github.com/apache/geaflow/blob/f8be72222ee1ddbbf66a87fc351cd727a4d13b03/geaflow/pom.xml)；
- Jetty 12.1.8 固定提交的 Core [`ProxyHandler`](https://github.com/jetty/jetty.project/blob/c9cdc9aaa434a3665b8a53b4d1cc3684992da649/jetty-core/jetty-proxy/src/main/java/org/eclipse/jetty/proxy/ProxyHandler.java)、移动后的 [`ConnectHandler`](https://github.com/jetty/jetty.project/blob/c9cdc9aaa434a3665b8a53b4d1cc3684992da649/jetty-core/jetty-server/src/main/java/org/eclipse/jetty/server/handler/ConnectHandler.java) 和 EE11 [`ProxyServlet`](https://github.com/jetty/jetty.project/blob/c9cdc9aaa434a3665b8a53b4d1cc3684992da649/jetty-ee11/jetty-ee11-proxy/src/main/java/org/eclipse/jetty/ee11/proxy/ProxyServlet.java)。

## 上游迁移依据

- [Jetty 版本、Java 和 Jakarta EE 兼容矩阵](https://jetty.org/docs/jetty/12/index.html)
- [Jetty 9.4 → 10 迁移指南](https://jetty.org/docs/jetty/12/programming-guide/migration/94-to-10.html)
- [Jetty 11 → 12 迁移指南：artifact、包名、Handler 和 HttpClient 变化](https://jetty.org/docs/jetty/12.1/programming-guide/migration/11-to-12.html)
- [Jetty 12.0 → 12.1 迁移指南](https://jetty.org/docs/jetty/12.1/programming-guide/migration/12.0-to-12.1.html)
- [Jetty 12.1 ProxyHandler / ConnectHandler 官方编程指南](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html)
- [Jetty 12.1.8 `jetty-proxy` POM（固定提交）](https://github.com/jetty/jetty.project/blob/c9cdc9aaa434a3665b8a53b4d1cc3684992da649/jetty-core/jetty-proxy/pom.xml)

## 验证

```bash
mvn -f rewrite-jetty-proxy-upgrade/pom.xml clean verify
```
