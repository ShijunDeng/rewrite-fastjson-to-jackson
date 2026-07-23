# Spring Web MVC 6.2.19 迁移模块

本模块把 `org.springframework:spring-webmvc` 的 5.2/5.3 大跨度升级实现为可执行、可审计的 OpenRewrite 配方。README 是不兼容点规格；AUTO 配方只执行可证明等价的修改，无法证明业务语义的变化由 MARK 配方在原位置留下 `SearchResult`，而不是仅提醒读者。

## 配方入口

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19` | 推荐入口：严格依赖升级、确定性 Java 迁移、构建/源码/配置风险标记 |
| `com.huawei.clouds.openrewrite.springwebmvc.UpgradeSpringWebMvcTo6_2_19` | 只升级构建声明 |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java` | 组合精确的官方 Spring/Java 配方，迁移 Jakarta Servlet、MVC configurer、异常 status API；仅为官方 interceptor 配方的契约缺口保留本地实现 |
| `com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6BuildMigrationRisks` | 只扫描依赖所有权、基线与关联制品风险 |
| `com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6SourceAndConfigurationRisks` | 只扫描 Java、properties、YAML、XML 风险 |

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19
```

## 工作簿范围

规范坐标出现在 Excel 行 1297–1306（序号 1296–1305），目标固定为 `6.2.19`。可从单元格精确恢复的源版本为：

`5.2.5.RELEASE`、`5.2.9.RELEASE`、`5.3.21`、`5.3.23`、`5.3.26`、`5.3.27`、`5.3.30`、`5.3.31`、`5.3.32`、`5.3.33`。

最后一个单元格实际只保存 `5.3.33 ...（共24个版本）`，其余 14 个值并不存在于 xlsx，配方不会猜测。Excel 行 2193–2201 还有没有 groupId 的 `spring-webmvc` 别名记录，不能扩展规范 Maven 坐标，也不重复计入白名单。

## 自动修改（AUTO）

### 严格依赖升级

- Maven 只处理当前项目或一级 profile 的 `dependencies` / `dependencyManagement` 中标准 JAR；保留 scope、optional、exclusions 和相邻节点。
- 支持直接版本以及唯一声明、全部引用都专属于目标依赖的根/profile 属性。重复定义、XML attribute 引用、共享属性和任意 profile 遮蔽都会停止自动修改。
- Gradle 只处理根 `dependencies {}` 的已知 configuration，支持 Groovy 字符串、两种 map 和 Kotlin 字符串字面量。
- classifier、自定义 type/ext/variant、范围、动态/变量版本、version catalog、platform/BOM 管理、无版本依赖、buildscript、嵌套 project/custom DSL 均不自动覆盖。
- `target`、`build`、`generated*`、`install*`、`.gradle`、`.m2`、`node_modules`、`vendor` 等生成/缓存路径完全跳过。

### 可证明等价的源码迁移

- 复用 OpenRewrite 核心 `ChangePackage`，对具有类型归属的 `javax.servlet` 及其子包执行 `jakarta.servlet` 一对一命名空间迁移；不靠文本替换，不修改注释、字符串或 Maven 坐标。
- 复用 `rewrite-spring` 的 `MigrateWebMvcConfigurerAdapter`：直接子类改为实现 `WebMvcConfigurer`，删除 adapter 的空 `super` 调用，并迁移匿名类和返回类型。
- 直接继承 `HandlerInterceptorAdapter` 且没有调用任何 `super` 行为时，改为实现 `AsyncHandlerInterceptor`；它继承 `HandlerInterceptor`，因此同时保留旧 adapter 的同步与异步类型契约，原有其他 interface 也保留。
- 复用 `MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode`，把真实 override 签名中的 `HttpStatus` 参数改为 `HttpStatusCode`，同时更新方法和变量类型归属。
- 复用 `MigrateResponseStatusException`：`getRawStatusCode()` 改为 `getStatusCode().value()`，`getStatus()` 改为 `getStatusCode()`，需要时同步把接收变量从 `HttpStatus` 改为 `HttpStatusCode`。
- `HandlerInterceptorAdapter` 的匿名类、返回/字段类型、间接继承，以及包含 `super.*` 的类保持原样并由 MARK 提示。`WebMvcConfigurerAdapter` 的匿名类、返回类型和已知空 `super` 调用由官方配方安全迁移。

## 自动标记（MARK）

### 构建与运行基线

- Spring Framework 6.2 的 Java 17 基线：标记 Maven compiler 属性/插件和 Gradle compatibility/toolchain 的明确低版本。
- 无版本、BOM/parent/platform/catalog/变量控制的 `spring-webmvc`：标记真实所有者，绝不注入局部 `6.2.19`。
- 低于目标且不在白名单的固定版本标记 OUTSIDE；高于 `6.2.19` 的固定版本原样保留并精确标记 `目标版本冲突（禁止降级）`；classifier/type/ext/四段坐标标记 VARIANT，不冒充标准运行时 JAR。
- 只有同一 Maven root/profile 或同一根 Gradle build 中存在标准 `spring-webmvc` 时，才标记其他 `org.springframework:spring-*`/`spring-framework-bom` 的混合版本；variant 不会触发关联扫描，profile 不会泄漏到兄弟 profile，根 Gradle 项目不会拥有子项目依赖。
- 标记不属于 Spring Framework 6.2 管理线的 Boot parent/BOM（含 Boot 2、Boot 3.0–3.3、Boot 4 或外部 owner）、javax Servlet/Validation/Annotation、Tiles、Commons FileUpload、`webjars-locator-core` 以及 javax 时代 Servlet 容器；这些需要整列对齐，而不是孤立修改 Web MVC。

### Spring Framework 6.0 边界

- Java 17、Jakarta EE 9+ 和 Jakarta Servlet；`javax.servlet` classpath 与 Tomcat 9 等运行时不兼容。
- `PathPatternParser` 成为默认路径引擎，optional trailing slash 默认由 `true` 改为 `false`；`AntPathMatcher`、`UrlPathHelper`、suffix/path-extension、matrix variable、自定义 servlet path 与安全规则需要成组验证。
- 只在类型上声明 `@RequestMapping`、却没有 `@Controller`/`@RestController` 的类不再自动识别为 controller。
- `WebMvcConfigurerAdapter`、`HandlerInterceptorAdapter`、`GzipResourceResolver`、`AppCacheManifestTransformer` 和完整 Tiles 3 集成已删除；`CommonsMultipartResolver` 及 Commons FileUpload 旧集成也已从 Spring Web 删除。
- Jakarta Servlet namespace 自动迁移只解决源码类型；Servlet API 坐标、容器、JSP/JSTL、Validation、Security 与 Boot 必须根据构建 marker 协同升级。

### Spring Framework 6.1 边界

- MVC 内建 controller method validation。`@Validated` controller、参数约束、`@Valid`、`BindingResult`、validation groups 与 `HandlerMethodValidationException` 的异常契约需重新设计/测试。
- `@RequestParam`、`@RequestHeader`、`@CookieValue` 等存在 `defaultValue` 时，非空但无文本输入也会采用默认值。
- `RouterFunctionMapping` 默认顺序从 3 改为 -1，会先于 `RequestMappingHandlerMapping`；重叠的 functional/annotation route 可能改变命中目标。
- 未匹配 handler 默认抛出 `NoHandlerFoundException`，静态资源抛出 `NoResourceFoundException` 并默认处理为 404；自定义 resolver、`@ExceptionHandler`、`ResponseEntityExceptionHandler` 与 ProblemDetail 的 status/header/body 必须复核。
- CORS preflight 在 interceptor chain 开始前执行；鉴权、审计、异步和 completion interceptor 行为可能变化。
- `ResponseBodyEmitter` 对非 `IOException` 的错误完成语义改变；SSE 的 timeout、断连、keep-alive 和重复 listener 需要回归。
- 参数名不再从 local-variable table 推断。controller/exception handler/constructor binding 使用名称时，Java 必须启用 `-parameters`，Kotlin/Groovy 也要保留对应元数据。
- `ResponseEntityExceptionHandler` override 中的 `HttpStatus`→`HttpStatusCode` 签名由官方配方自动迁移；异常映射、status/header/body 和自定义继承行为仍由 MARK 提醒复核。

### Spring Framework 6.2 边界

- 字符串形式静态资源 location 缺少尾部 `/` 时现在自动补齐；路径拼接、目录包含关系、自定义 `Resource`、cache/encoded resolver 必须验证。
- `WebJarsResourceResolver` 的 `webjars-locator-core` 支持已弃用，推荐 `webjars-locator-lite` 与 `LiteWebJarsResourceResolver`；配方只标记，不替用户选择依赖。
- 新 `UrlHandlerFilter` 可 redirect/rewrite trailing slash。SEO、HTTP method/status、代理和 Spring Security matcher 决定了选择，不能机械插入。
- RFC 9457 `ProblemDetail` 可增加 `ErrorResponse.Interceptor`；既有全局异常处理器需验证 content negotiation、国际化、敏感字段与客户端兼容性。
- Spring Web MVC 可选 Jackson 基线与自动模块行为改变；目标工程要统一 Jackson 版本并回归 request/response JSON，不由单制品配方强行升级。

## 保持不动（NO-OP）

- 工作簿未明示的版本、目标/未来版本、范围、snapshot/dynamic、外部 BOM/parent/platform/catalog 所有权；
- `spring-webmvc-extra`、其他 group 下同 artifactId、任意普通 XML 中伪造的 dependency；
- plugin dependency/configuration、嵌套 `<project>`、Gradle buildscript/custom/nested project；
- `HandlerInterceptorAdapter` 匿名类、间接子类或调用 `super` 的子类；
- 同名但类型归属不是 Spring MVC 的方法、annotation 和类；
- 普通文本、注释、字符串、POM 中的 Jakarta 类名，以及生成/缓存路径。

这些 no-op 是配方的安全边界。特别是仅把 `spring-webmvc` 局部覆盖为 6.2.19、同时保留 Spring 5/Boot 2/Tomcat 9，通常会形成不可运行的混合 classpath。

## 官方能力复用审计

本模块按“官方精确组件优先、本地代码只补缺口”实现。审计固定在以下实际构建输入：

| 上游 | 固定证据 | 采用结论 |
| --- | --- | --- |
| OpenRewrite core `8.87.5` | [`rewrite@b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，`rewrite-java` SHA-256 `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | 直接复用官方 [`ChangePackage`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangePackage.java)；本地只增加生成/缓存路径 precondition。 |
| `rewrite-spring` `6.35.0` | [`d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)，SHA-256 `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` | 直接复用 `MigrateWebMvcConfigurerAdapter`、`MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode`、`MigrateResponseStatusException` 及其两个嵌套 accessor 配方；删除原本重复的本地 Web MVC adapter/composite visitor。 |
| `rewrite-java-dependencies` `1.59.0` | [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/tree/decb8dbb2b5b726f8815efc51c85c34a60268bb0)，SHA-256 `b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550` | 已审计但不允许其通用 version selector 控制主坐标；它无法表达 Excel 十值白名单、严格 owner、生成目录和禁止降级规则。 |

以下官方能力经过检查但明确不进入 runtime tree：

| 排除项 | 原因 |
| --- | --- |
| `UpgradeSpringFramework_5_3/6_0/6_1/6_2` | 全框架 aggregate 会升级全部 Spring、Spring Data/Kafka/Integration、插件和 HTTP client，并使用 `6.2.x` selector；不符合单坐标、精确 `6.2.19` 和十值白名单合同。 |
| `JakartaEE10` | 会迁移整个 Jakarta EE 平台及第三方集成；本模块只执行有类型归属的 Servlet package 迁移，其余依赖由 MARK 协调。 |
| 官方 `MigrateHandlerInterceptor` | 固定实现用单元素 `implements HandlerInterceptor` 覆盖原 interfaces，并把 adapter 原有 `AsyncHandlerInterceptor` 契约收窄；可能丢失 `Serializable` 等接口及异步回调。模块保留一个仅处理无 `super` 直接子类、且保留全部 interfaces/async 契约的本地 gap recipe。 |
| trailing-slash Boot 配方 | 添加双路由或全局恢复旧匹配策略是业务、安全和 SEO 决策，不是语义等价迁移；继续 MARK。 |
| `MigrateMethodArgumentNotValidExceptionErrorMethod` | 目标是 Spring 6.1 中出现、6.2 才弃用的 helper；Excel 输入全为 5.2/5.3，不能从这些源版本产生该调用。 |
| Spring Web client、WebFlux、HTTP Components 与 Spring `Assert` 配方 | 不属于本模块的 MVC server 确定性边界，启用会扩大到用户未授权的软件范围。 |

固定官方实现与测试证据包括 [`MigrateWebMvcConfigurerAdapter`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateWebMvcConfigurerAdapter.java)、[`MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode.java)、[`MigrateResponseStatusException`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-framework-60.yml)、[`MigrateWebMvcConfigurerAdapterTest`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/framework/MigrateWebMvcConfigurerAdapterTest.java) 和 [`MigrateResponseStatusExceptionTest`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/framework/MigrateResponseStatusExceptionTest.java)。本模块的 runtime recipe-tree 测试会在宽泛 aggregate、不安全 Handler recipe 或通用 Spring 依赖升级节点意外进入组合时立即失败。

## 真实公共仓库用例

测试从以下固定 commit 缩减，保留决定配方行为的结构：

- [`zhyocean/MyBlog@9410e07` 的 `WebMvcConfig`](https://github.com/zhyocean/MyBlog/blob/9410e07dbb254d1678e2216e0857b14b1c82ca25/src/main/java/com/zhy/config/WebMvcConfig.java)：无 `super` 行为的 `WebMvcConfigurerAdapter`、静态资源 location，验证安全 AUTO；
- [`talkincode/ToughProxy@c40aaac` 的 `SessionInterceptor`](https://github.com/talkincode/ToughProxy/blob/c40aaaceba3fc0c3e81855cdba52af2a9ae3eb7a/src/main/java/org/toughproxy/config/SessionInterceptor.java)：包含 `super.postHandle/afterCompletion`，验证 adapter 保守 no-op 与 Servlet namespace AUTO 可以并存；
- [`xenv/S-mall-ssm@3d9e77f` 的 `AuthInterceptor`](https://github.com/xenv/S-mall-ssm/blob/3d9e77f7d80289a30f67aaba1ae73e375d33ef71/src/main/java/tmall/interceptor/AuthInterceptor.java)：鉴权 interceptor 的 session/redirect 逻辑，作为 interceptor 顺序和 Jakarta 迁移用例来源；
- [`zyf265600/Programmer@edbf61d` 保存的 5.3.23 发布 POM](https://github.com/zyf265600/Programmer/blob/edbf61d3f40e21925794a03bd0c31cc37e6b626e/Maven/Resource/mvn_repo/org/springframework/spring-webmvc/5.3.23/spring-webmvc-5.3.23.pom)：真实 Spring family 对齐关系，用于 `spring-aop/beans/context/core/expression/web` companion marker 规格。

## 固定上游依据

- Spring Framework 6.2.19 tag/commit：[`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522)；目标 [`spring-webmvc.gradle`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-webmvc/spring-webmvc.gradle) 与 [`PathMatchConfigurer`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-webmvc/src/main/java/org/springframework/web/servlet/config/annotation/PathMatchConfigurer.java)。
- 对照 Spring 5.3.39 [`f1b128b8`](https://github.com/spring-projects/spring-framework/tree/f1b128b88d734670b4e1842e9ecf41f5252c778d) 与 5.2.9.RELEASE [`69921b49`](https://github.com/spring-projects/spring-framework/tree/69921b49a5836e412ffcd1ea2c7e20d41f0c0fd6)。
- 固定 wiki 修订：6.0 release notes [`2db215b1`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes/2db215b1f920c4c1245d0af3bac131311201ece7)、6.1 [`723e8e77`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes/723e8e77fbd0ca2cbb3cd90083ba144f89f7425d)、6.2 [`0a2f0f58`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes/0a2f0f586889261c625eae34194978b700f6e46c)、6.x upgrade guide [`d2c44a64`](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x/d2c44a64e398286bc553e977ff093ce54d6171c1)。
- Spring Boot 3.4 固定 wiki 修订 [`6abfcf76`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes/6abfcf76885589ba0dd0ebf5561838ba636e0206)：Boot 3.4 升级到 Spring Framework 6.2，作为 parent/BOM 管理线 MARK 的依据。
- OpenRewrite 官方测试/实现风格固定参考：[`rewrite-spring@d28afcb`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee) 的 `MigrateWebMvcConfigurerAdapterTest`、`MigrateHandlerInterceptorTest`、`MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCodeTest` 与 `MigrateResponseStatusExceptionTest`。

## 测试与验证

```bash
mvn -f rewrite-spring-webmvc-upgrade/pom.xml clean verify
```

当前 77 个 JUnit 执行用例覆盖 10 个精确源版本、目标 `6.2.19`、高版本不降级及精确冲突标记、Maven root/profile/dependencyManagement、属性独占/共享/重复/attribute 引用/profile 遮蔽、Gradle Groovy/Kotlin string/map、变量/catalog/platform/BOM/variant、owner/scope 隔离、Java 17/Boot/Jakarta/容器/family MARK、官方 runtime recipe-tree、固定 Spring 5.3 类型信息上的 Web MVC adapter/异常 before-after、保留 async/interfaces 的 interceptor gap、generated-path 边界、路由/validation/exception/interceptor/resource/streaming MARK、properties/YAML/XML namespace、真实仓库缩减 fixture、lookalike/no-op、幂等与 aggregate parity。自动测试后仍需在目标 Servlet 容器中对路由、安全、validation、异常、multipart、静态资源、SSE、JSON 和灰度回滚做集成验证。
