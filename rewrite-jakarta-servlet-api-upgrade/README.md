# Jakarta Servlet API 6.1 migration

本模块迁移 `jakarta.servlet:jakarta.servlet-api`。推荐配方同时执行严格依赖升级、规范明确的一对一 Java/XML 修改，以及构建、容器、deployment descriptor、removed API 和运行时语义扫描：

```text
com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0
```

只修改依赖时使用：

```text
com.huawei.clouds.openrewrite.jakartaservlet.UpgradeJakartaServletApiTo6_1_0
```

## 精确版本边界

目标版本为 `6.1.0`。`开源软件升级.xlsx` 对该 artifact 实际给出且仅给出四个源版本：

```text
4.0.3, 4.0.4, 5.0.0, 6.0.0
```

低层配方是 strict dependency-only：不会用 `4.x`/`latest.release` 等范围猜版本，不会修改 Java/XML/config，也不会覆盖 BOM/versionless 声明。`javax.servlet:javax.servlet-api` 是不同坐标，同样不会被低层配方越权替换。

## 不兼容处理规范

`AUTO` 表示规范或旧 API 明确给出等价替代；`MARK` 表示在准确 AST/配置位置生成 `SearchResult` 并保留业务选择；`NO-OP` 表示刻意不改。

| 迁移点 | 状态 | 处理 | 主要测试 |
| --- | --- | --- | --- |
| Maven direct、dependencyManagement、profile 中四个精确版本 | AUTO | 仅将目标 artifact 改为 `6.1.0` | 四版本参数化、Jenkins、HAPI |
| 仅供目标 artifact 使用的本地 property | AUTO | property 改为 `6.1.0` | exclusive property |
| property 同时用于其他依赖、Bnd/OSGi metadata token 或有多个定义 | AUTO | 只把目标 dependency 隔离为 `6.1.0` 字面量，property/metadata 不动 | shared/duplicate property |
| Maven BOM/versionless、外部 property | NO-OP/MARK | strict 配方不覆盖；推荐配方提示升级 owning platform/BOM | managed fixture |
| Maven range、动态/未列/目标/更高版本 | NO-OP/MARK | 不猜、不降级；推荐配方只标记未列或无法解析版本 | strict no-op、build marker |
| Gradle Groovy/Kotlin 直接字符串与 Groovy map 字面量 | AUTO | 仅 dependency DSL 中的精确坐标；classifier/extension 保留 | jsonrpc4j、map、Kotlin |
| Gradle interpolation、变量、version catalog、动态版本、说明字符串 | NO-OP | 不修改版本所有者或普通字符串 | variable/catalog fixture |
| Servlet 4 `javax.servlet.*` Java 类型 | AUTO | 按规范递归迁移到 `jakarta.servlet.*`；若同一 compilation unit 使用已删除类型则整文件停止 namespace 修改，只保留 marker | import、annotation、descriptor、FQN、removed-type guard |
| Servlet 4/5 的 `isRequestedSessionIdFromUrl`、`encodeUrl`、`encodeRedirectUrl` | AUTO | 改为规范明确替代的 `*URL` 方法；调用、method reference 和 override 均处理 | YaCy、wrapper override |
| `HttpSession.getValue/putValue/removeValue` | AUTO | 改为 `getAttribute/setAttribute/removeAttribute` | Yona、调用与 method reference |
| `HttpSession.getValueNames()` | MARK | 不自动改名：旧返回 `String[]`，`getAttributeNames()` 返回 `Enumeration<String>` | call、method reference、recommended no-rename |
| 非 POM XML 值中的精确 `javax.servlet.` 包前缀 | AUTO | 改为 `jakarta.servlet.`；schema/version 不连带猜测 | XML attribute/text、POM no-op |
| `web.xml`/`web-fragment.xml` 旧 namespace/schema/version | MARK | 要求在目标容器上选择 6.1 schema；不单独改版本以免隐藏已删除 element/类 | descriptor schema |
| `metadata-complete=true` | MARK | 核对 annotation 与 initializer discovery | descriptor marker |
| error-page 与 `dispatcher=ERROR` | MARK | 6.1 error dispatch 以 GET 执行；核对原 method/query、认证、CSRF、缓存 | XML/Java error fixtures |
| `META-INF/services/javax.servlet.ServletContainerInitializer` | MARK | 要求重命名 service contract，并先检查目标路径冲突和 provider 顺序 | service path marker |
| 自定义 `ServletContainerInitializer`、request/response/session 实现或 wrapper | MARK | 重编译完整契约并验证 HandlesTypes、扫描、delegation、async 与新增 defaults | initializer、YaCy、Gitblit |
| Java < 17 | MARK | 标记 Maven property、Gradle compatibility/toolchain | Maven/Groovy/Kotlin |
| API JAR 被打包进 WAR/JAR | MARK | Maven 建议 `provided`，Gradle 建议 `compileOnly`；平台特例人工确认 | scope/configuration |
| Spring Boot、Tomcat、Jetty、Undertow | MARK | API JAR 不能升级实现；Tomcat < 11 明确标记，其他平台要求核对 Servlet 6.1 支持 | Maven/Gradle container |
| `javax.servlet` artifact 与 Jakarta Pages/WebSocket/EL/JSTL | MARK | 统一 Jakarta EE 11 web ecosystem，避免二进制类型边界混用 | ecosystem dependencies |
| `SingleThreadModel`、`HttpSessionContext`、`HttpUtils` | MARK | 6.0 已删除且无类型级替代；分别重构并发、session registry、URL/form parsing | removed imports/types |
| `getSessionContext`、ServletContext servlet enumeration、`UnavailableException` 旧 constructor/getServlet | MARK | 无一对一替代，不生成臆测代码 | removed call fixtures |
| `ServletRequest.getRealPath`、`ServletContext.log(Exception,String)`、`setStatus(int,String)` | MARK | 替代路径/日志/响应提交语义有分支，必须人工选择 | removed call fixtures |
| `getParameter*` | MARK | 6.1 参数解析错误可抛 `IllegalStateException`；统一异常映射和 limits 测试 | parameter marker |
| error handler 中 `getMethod/getQueryString` | MARK | 改读 `RequestDispatcher.ERROR_METHOD/ERROR_QUERY_STRING` 前先确认业务意图 | error-dispatch marker |
| URI/path、`getRealPath`、resource lookup | MARK | 验证 canonicalization、encoded separator、dot segment、proxy 与 exploded WAR | URI marker |
| async/non-blocking API | MARK | 验证 dispatch serialization、`isReady`、back-pressure、close/complete 顺序 | async marker |
| Cookie comment/version、PushBuilder/newPushBuilder | MARK | RFC 2109 API 待删；server push 已 deprecated 且实现可不支持 | cookie/push marker |
| SecurityManager/policy/startup flags | MARK | 6.1 已删除相关规范要求；改用进程、容器和平台隔离 | Java/config marker |
| Java 字符串、OSGi/config 中的 `javax.servlet` | MARK | 无法静态证明是反射、scanner、序列化还是普通文本，不自动替换 | literal/properties/MANIFEST |
| `getAttribute/setAttribute`、单参数 `setStatus`、`log(String,Throwable)` | NO-OP | 目标 API 仍稳定使用 | stable API fixture |

安全边界：只要一个 compilation unit 使用 `SingleThreadModel`、`HttpSessionContext` 或 `HttpUtils`，完整配方就不会在该文件执行 `javax.servlet` → `jakarta.servlet`，避免制造不存在的 Jakarta 类型；风险配方仍会在 removed import/type 上给出 marker。先重构删除项，再重复执行推荐配方即可迁移该文件剩余的 Servlet 类型。

## 固定官方依据

`6.1.0-RELEASE` 是 annotated tag。本模块固定使用 peeled commit [`fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082`](https://github.com/jakartaee/servlet/tree/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082)，避免默认分支变化：

- 固定 [Servlet 6.1 specification source](https://github.com/jakartaee/servlet/blob/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082/spec/src/main/asciidoc/servlet-spec-body.adoc) 规定 Java 17、4→5 只有 namespace 变化，并在 change log 列出 5→6 的删除项及 6→6.1 的参数异常、SecurityManager、error dispatch、Push 等变化。
- 固定 [`ServletRequest`](https://github.com/jakartaee/servlet/blob/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082/api/src/main/java/jakarta/servlet/ServletRequest.java) 明确 `getParameter*` 的异常边界；固定 [`RequestDispatcher`](https://github.com/jakartaee/servlet/blob/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082/api/src/main/java/jakarta/servlet/RequestDispatcher.java) 定义原 method/query 属性。
- 固定 [`HttpServletRequest.newPushBuilder`](https://github.com/jakartaee/servlet/blob/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082/api/src/main/java/jakarta/servlet/http/HttpServletRequest.java) 说明可返回 `null` 且已 deprecated。
- 固定 [`web-app_6_1.xsd`](https://github.com/jakartaee/servlet/blob/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082/api/src/main/resources/jakarta/servlet/resources/web-app_6_1.xsd) 与 [`module-info.java`](https://github.com/jakartaee/servlet/blob/fe6e12a5d54d8ccf5084e034be3e5ecc5a27f082/api/src/main/java/module-info.java) 给出 descriptor 和 JPMS 目标。
- 旧 Servlet 5 API 固定到 [`e07dd4b795b42478a5b7363b6801a5fe3318d3e1`](https://github.com/jakartaee/servlet/tree/e07dd4b795b42478a5b7363b6801a5fe3318d3e1)：[`HttpSession`](https://github.com/jakartaee/servlet/blob/e07dd4b795b42478a5b7363b6801a5fe3318d3e1/api/src/main/java/jakarta/servlet/http/HttpSession.java) 证明 aliases 与 `getValueNames` 返回类型不同，[`HttpServletResponse`](https://github.com/jakartaee/servlet/blob/e07dd4b795b42478a5b7363b6801a5fe3318d3e1/api/src/main/java/jakarta/servlet/http/HttpServletResponse.java) 说明 URL aliases 与两参数 status 的替代分支。

[Jakarta Servlet 6.1 release page](https://jakarta.ee/specifications/servlet/6.1/) 给出 Maven 坐标和兼容实现记录。配方不会据此假定任意应用服务器小版本已兼容；实际部署版本仍需集成测试。

OpenRewrite 测试方式固定参考：

- [`ChangeMethodNameTest` at `1b1804a`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java)
- [`ChangePackageTest` at `1b1804a`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java)
- [`UpgradeDependencyVersionTest` at `decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)

## 固定真实仓用例

测试只保留迁移相关的最小形态，每个来源固定到不可漂移的 commit：

| 固定仓库 | 真实形态 | 期望 |
| --- | --- | --- |
| [jenkins-infra/crawler `efb1b391`](https://github.com/jenkins-infra/crawler/blob/efb1b391762056a1a558ab2d340d840ed2aad527/pom.xml) | Maven direct `4.0.4` | AUTO → `6.1.0` |
| [hapifhir/hapi-hl7v2 `de150365`](https://github.com/hapifhir/hapi-hl7v2/blob/de1503651040e592d529d43980c06b19b89e2c27/pom.xml) | dependencyManagement `6.0.0` | AUTO → `6.1.0` |
| [briandilley/jsonrpc4j `59ff0c95`](https://github.com/briandilley/jsonrpc4j/blob/59ff0c955087a3fe1abfbf870ae27d60dbf6c9e2/build.gradle) | Gradle `5.0.0` 与 Javax 4.0.1 并存 | AUTO target literal；MARK ecosystem |
| [YaCy `1f181065`](https://github.com/yacy/yacy_search_server/blob/1f181065cebaabff33f961ecdf81fb2a57748053/source/net/yacy/cora/protocol/RequestHeader.java) | custom request、alias override、removed `getRealPath` | AUTO alias/namespace；MARK wrapper/removed API |
| [Yona `60a5ac40`](https://github.com/yona-projects/yona/blob/60a5ac40689fc36ee5b55eddedd345fc34878190/app/utils/PlayServletSession.java) | session aliases、`getValueNames`/`getSessionContext` | AUTO safe aliases；MARK changed/no-replacement APIs |
| [Gitblit `ee443d9d`](https://github.com/pdinc-oss/gitblit/blob/ee443d9da3939395243e2f81436ad4059c7b72bb/src/com/gitblit/ServletRequestWrapper.java) | direct custom request implementation | MARK full contract boundary |

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jakarta-servlet-api-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0
```

处理全部 `~~>` 标记并确认 patch 后再执行 `run`。至少执行 Java 17 编译、目标 Servlet 6.1 容器启动、filter/listener/initializer discovery、descriptor validation、session、multipart/parameter limits、async/non-blocking IO、error dispatch、URI/path traversal、Cookie、认证/CSRF、proxy/header 与 WAR packaging 测试。

模块自身验证：

```bash
mvn -f rewrite-jakarta-servlet-api-upgrade/pom.xml clean verify
```

当前 54 个测试覆盖四个精确版本、Maven/Gradle/Kotlin、shared property/metadata token、BOM/dynamic/catalog no-op、确定性 Java/XML 修改、removed-type namespace guard、build/Java/resource marker、6 个固定真实仓、推荐组合单 cycle、配方 validation 和重复 cycle 幂等检查。
