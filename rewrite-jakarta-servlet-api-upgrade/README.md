# Jakarta Servlet API upgrade to 6.1.0

本模块对应表格中的 `jakarta.servlet:jakarta.servlet-api`，处理 `4.0.3`、`4.0.4`、`5.0.0`、`6.0.0` 到 `6.1.0` 的升级。

需要特别注意：artifact 从 4.x 起已经叫 `jakarta.servlet-api`，但官方 Servlet 4.0.3 API 的 Java 包仍是 `javax.servlet.*`；Servlet 5.0 才把命名空间改为 `jakarta.servlet.*`。因此只改 Maven 版本会让 4.x 项目源码无法编译。

完整迁移配方：

```text
com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0
```

仅迁依赖的低风险配方：

```text
com.huawei.clouds.openrewrite.jakartaservlet.UpgradeJakartaServletApiTo6_1_0
```

## 自动处理范围

低风险配方只升级 Maven/Gradle 中精确坐标 `jakarta.servlet:jakarta.servlet-api`，支持 Maven 直接版本、版本属性、dependencyManagement，以及 Gradle 字符串、Map 和本地版本变量写法。scope、optional 等声明保持不变；不会修改 `javax.servlet:javax.servlet-api`、JSP API、Jetty API 等相似坐标，也不会把已使用 6.2 milestone 的工程降级。

完整配方在依赖升级之外执行以下高置信度源码修改：

- 递归迁移 `javax.servlet.*` 到 `jakarta.servlet.*`，覆盖核心、HTTP、annotation 与 descriptor 包；
- `isRequestedSessionIdFromUrl()` → `isRequestedSessionIdFromURL()`；
- `encodeUrl()` / `encodeRedirectUrl()` → `encodeURL()` / `encodeRedirectURL()`；
- `HttpSession.getValue/getValueNames/putValue/removeValue` → `getAttribute/getAttributeNames/setAttribute/removeAttribute`；
- 同时处理 Servlet 4.x 的 `javax` 类型和 Servlet 5.0 的 `jakarta` 类型，方法匹配依赖 OpenRewrite 类型信息，不按文本误改业务同名方法或字符串。

完整配方刻意不做有语义分支或无直接替代项的修改，例如 `setStatus(int,String)`、`ServletRequest.getRealPath()`、`ServletContext.log(Exception,String)`、`UnavailableException` 旧构造器、`SingleThreadModel` 和 `HttpSessionContext`。自定义 Request/Response/Session wrapper 中保留的兼容方法也是项目自身的公开 API，不能机械删除。

其中 `getValueNames()` 的返回类型是 `String[]`，而 `getAttributeNames()` 返回 `Enumeration<String>`；配方会完成方法名迁移，但消费返回值的循环、变量和 method reference 必须人工改写。测试同时覆盖返回值被忽略时的安全变换和其他 session method reference，避免把返回类型差异隐藏起来。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| 4.x artifact 仍导出 `javax.servlet.*`，5.0 改为 `jakarta.servlet.*` | 全部源码、反射类名、SPI/service 文件、框架配置、JSP/taglib 与测试 mock 必须统一命名空间；不能在同一 Web 应用里混用两套 Servlet 类型 |
| Servlet 5.0 相对 4.0 的规范级主要变化就是命名空间 | 配方自动改有类型信息的 Java 引用；字符串、XML 和第三方二进制依赖仍需盘点，所有 filter/listener/initializer 库必须提供 Jakarta 版本 |
| Servlet 6.0 最低 Java 从 8 提升到 11 | 从 5.0 升级时至少需要 JDK 11；本模块最终目标 6.1 要求 JDK 17，因此编译、运行、CI、镜像与 Maven/Gradle toolchain 应直接统一到 17+ |
| Servlet 6.0 删除 5.0 及更早已废弃的 API | 删除项包括 `SingleThreadModel`、`HttpSessionContext`、`HttpUtils`，以及旧 URL/session/context/request/response 方法和构造器；配方只替换有一一对应关系的方法，其余必须人工决定语义 |
| `SingleThreadModel` 无替代 | Servlet 实例默认并发处理请求；消除可变实例字段，使用局部变量、线程安全组件或明确同步，做并发与压测验证 |
| `HttpSessionContext`、`getSessionContext()` 无替代 | 不应枚举或跨会话读取容器 session；改为应用自己的受控 session registry，并评估集群、失效、并发与隐私风险 |
| `setStatus(int,String)` 的 message 参数被删除 | 仅设状态用 `setStatus(int)`；确实要产生错误响应时评估 `sendError(int,String)`，两者提交响应和容器错误页行为不同，不能统一机械替换 |
| `ServletRequest.getRealPath()` 被删除 | 改用 `request.getServletContext().getRealPath()`，同时正确处理未展开 WAR 或容器不提供真实路径时的 `null`；优先按 classpath/stream 读取资源 |
| Servlet 6.0 Cookie 语义对齐 RFC 6265，并增加通用 attribute | 审核 name/value 校验、SameSite/Partitioned、自定义属性、domain/path/max-age；旧 RFC 2109 comment/version API 在 6.0 已标记待删除 |
| 6.0 URI 解码、规范化、RequestDispatcher wrapping 与字符编码行为被澄清 | 对编码斜线、反斜线、点段、重复斜线、matrix parameter、forward/include/error dispatch 和 wrapper 身份假设做安全回归测试 |
| 6.0 新增 request/connection ID 和 JPMS module | 若有自定义 wrapper，实现/委托新增默认方法并检查 `module-info.java`；不要用连接 ID 替代认证或业务 trace ID |
| Servlet 6.1 最低 Java 提升到 17 | Jakarta EE 11 / Servlet 6.1 容器也必须匹配；例如 Tomcat 11 要求 Java 17，不能只在应用 POM 中单独升级 API JAR |
| 6.1 删除所有 SecurityManager 相关要求 | 删除 policy、permission、`doPrivileged` 和容器 SecurityManager 启动参数；改用进程/容器隔离、最小权限账号和平台安全控制 |
| 6.1 参数解析错误可抛运行时异常 | `getParameter*()` 可能在触发解析时失败；统一异常映射并测试非法编码、超限参数、multipart 与 form body，不应把错误静默当成参数缺失 |
| 6.1 error dispatch 必须按 GET 执行，并新增原始 method/query 属性 | 依赖错误页收到原 HTTP method 的逻辑要改读新属性；验证认证、CSRF、审计、缓存、query string 和递归错误分派 |
| 6.1 HTTP/2 Server Push 被废弃且支持变为可选 | `newPushBuilder()` 可能返回 `null`；迁到 `103 Early Hints`、preload 或普通缓存策略，并保留无 push 回退路径 |
| 6.1 redirect、Charset、ByteBuffer 与状态码 API 扩展 | 新 API 不是强制迁移项；采用前需确认目标容器实现，测试 redirect body/status、非阻塞 IO 的 back-pressure 和 ByteBuffer position/limit |
| 6.1 header null/空值、TRACE、HTTPS 与路径 canonicalization 要求被澄清 | 自定义 container adapter、proxy、request wrapper 和安全过滤器需重新验证，不要依赖旧容器的偶然行为 |
| deployment descriptor schema 演进到 6.1 | 检查 `web.xml`、`web-fragment.xml`、JSP/taglib 描述符的 Jakarta namespace、版本和 ordering；本配方不猜测 XML 升级，因为 schema 版本与目标容器必须一起决定 |
| Servlet API 应由容器提供 | Maven 通常使用 `provided`，Gradle 通常使用 `compileOnly`；不要把 API JAR 打进 WAR。Spring Boot、Jetty、Tomcat、Undertow、GlassFish/Payara 等整套平台必须选择明确支持 Servlet 6.1 的版本 |

官方基线与变更记录：

- [Jakarta Servlet 4.0](https://jakarta.ee/specifications/servlet/4.0/) 及其 [`javax.servlet.http` API](https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/http/package-summary)
- [Jakarta Servlet 5.0](https://jakarta.ee/specifications/servlet/5.0/) 和 [5.0 deprecated API 清单](https://jakarta.ee/specifications/servlet/5.0/apidocs/deprecated-list.html)
- [Jakarta Servlet 6.0 change log](https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#change-log)
- [Jakarta Servlet 6.1 change log](https://jakarta.ee/specifications/servlet/6.1/jakarta-servlet-spec-6.1.html#change-log) 与 [6.1 deprecated API 清单](https://jakarta.ee/specifications/servlet/6.1/apidocs/deprecated-list)
- [Tomcat 11 migration guide](https://tomcat.apache.org/migration-11.0.html)：Servlet 6.1 与 Java 17 的一个官方兼容实现示例

## 测试样本来源

- [jenkins-infra/crawler](https://github.com/jenkins-infra/crawler/blob/efb1b391762056a1a558ab2d340d840ed2aad527/pom.xml) 的 Maven `4.0.4` 直接依赖；
- [HAPI HL7v2](https://github.com/hapifhir/hapi-hl7v2/blob/de1503651040e592d529d43980c06b19b89e2c27/pom.xml) 的 dependencyManagement `6.0.0`；
- [jsonrpc4j](https://github.com/briandilley/jsonrpc4j/blob/59ff0c955087a3fe1abfbf870ae27d60dbf6c9e2/build.gradle) 同时声明 Javax Servlet 4.0.1 与 Jakarta Servlet 5.0.0 的 Gradle 场景，并明确记录 6.x 的 Java 11 基线；
- [YaCy RequestHeader](https://github.com/yacy/yacy_search_server/blob/1f181065cebaabff33f961ecdf81fb2a57748053/source/net/yacy/cora/protocol/RequestHeader.java)、[Yona PlayServletSession](https://github.com/yona-projects/yona/blob/60a5ac40689fc36ee5b55eddedd345fc34878190/app/utils/PlayServletSession.java) 与 [Gitblit ServletRequestWrapper](https://github.com/pdinc-oss/gitblit/blob/ee443d9da3939395243e2f81436ad4059c7b72bb/src/com/gitblit/ServletRequestWrapper.java) 的废弃方法/wrapper 形态；
- OpenRewrite Apache 2 核心的 [ChangeMethodName](https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java)、[ChangePackage](https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/test/java/org/openrewrite/java/ChangePackageTest.java) 与 [UpgradeDependencyVersion](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 测试模式。

测试覆盖 Maven 4.0.3/4.0.4/5.0.0/6.0.0、属性、dependencyManagement、scope/optional 保留、Gradle 字符串/Map/变量、目标/后续版本 no-op、相似坐标防误伤、依赖窄配方不改源码、Servlet 4 命名空间与全限定类型、Servlet 5 删除方法、方法引用、标准 wrapper 与业务同名方法/字符串防误伤，共 18 个场景。

OpenRewrite 官方 `rewrite-migrate-java` 的当前 Jakarta Servlet 测试使用 Moderne Source Available License。本模块只把公开测试作为行为研究资料，没有复制代码或引入该 recipe pack；实际实现仅组合 Apache 2.0 的 OpenRewrite 核心与 `rewrite-java-dependencies` 配方。

## 使用与验证

先使用完整配方生成 dry-run patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jakarta-servlet-api-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0
```

确认 patch 后，至少执行 JDK 17 编译、目标 Servlet 6.1 容器启动、filter/listener/initializer 扫描、session、async/non-blocking IO、multipart、dispatch/error page、URL 编码、Cookie、认证授权、代理转发和恶意 URI 回归测试。对自定义 wrapper 与容器扩展执行一次 `jdeps`/编译级 API 清查。

本模块自身验证：

```bash
mvn -f rewrite-jakarta-servlet-api-upgrade/pom.xml clean verify
```
