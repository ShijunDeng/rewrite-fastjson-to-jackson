# 高优先级 Java 迁移完成台账

> 状态：`COMPLETE`（25/25）
> 业务模块复审基线：`main@6423a9a`
> 记录日期：2026-07-23

本台账只记录用户指定的高优先级 Java 软件。每个模块均已实现可执行配方、兼容性
README、精确源版本/禁止降级边界、官方能力复用审计和模块测试，并已按模块提交到
GitHub。后续工作等待新的优先级或范围指示。

## 统一验收口径

- AUTO 只接受模块 README/Catalog 中的精确源版本白名单，并由升级前最近构建根门控。
- 目标版、表外版本、动态/外部 owner 和混合冲突保持不变；未来版本标记
  `目标版本冲突（禁止降级）`。
- 优先直接组合固定版本的 OpenRewrite 官方 recipe；官方没有满足精确边界的能力时，
  才使用最小自定义实现，并在模块 README 记录检索和排除证据。
- 25 个模块全部完成官方能力复审：24 个实际执行官方 recipe；`netty-codec-http`
  是唯一已证实没有安全 4.1 patch 官方 recipe 的模块，不属于漏复用。

## 模块明细

| # | 软件 / 目标版本 | 实现模块 | 官方能力复用结论 | 最终复审提交 |
| ---: | --- | --- | --- | --- |
| 1 | `tomcat-embed-core` → `10.1.57` | [`rewrite-tomcat-embed-core-upgrade`](../rewrite-tomcat-embed-core-upgrade) | 直接执行依赖、Jakarta/Servlet/EL、方法和参数迁移安全叶子 | `016ec34` |
| 2 | `log4j-core` → `2.25.5` | [`rewrite-log4j-core-upgrade`](../rewrite-log4j-core-upgrade) | 直接执行 3 个 Core `ChangeMethodName`；排除越界日志聚合 | `79ead1c` |
| 3 | `spring-webmvc` → `6.2.19` | [`rewrite-spring-webmvc-upgrade`](../rewrite-spring-webmvc-upgrade) | 直接执行 package、Servlet 6 及 Spring MVC 精确叶子 | `1db01a3` |
| 4 | `kafka-clients` → `4.1.2` | [`rewrite-kafka-clients-upgrade`](../rewrite-kafka-clients-upgrade) | 直接执行方法、类型和配置属性 Core recipe | `3445dc0` |
| 5 | `bcprov-jdk18on` → `1.84` | [`rewrite-bcprov-jdk18on-upgrade`](../rewrite-bcprov-jdk18on-upgrade) | 直接执行 package/type/method/constant recipe；排除方向不符聚合 | `afc0f86` |
| 6 | `netty-codec-http` → `4.1.136.Final` | [`rewrite-netty-codec-http-upgrade`](../rewrite-netty-codec-http-upgrade) | 已检索但无安全 4.1 patch 专用 recipe；排除 4.1→4.2 聚合，仅用官方类型归因/模板基础能力补窄 gap | `c907081` |
| 7 | `spring-webflux` → `6.2.19` | [`rewrite-spring-webflux-upgrade`](../rewrite-spring-webflux-upgrade) | 直接执行 Core 与 Spring WebFlux 精确叶子 | `2577a79` |
| 8 | `netty-codec-http2` → `4.1.136.Final` | [`rewrite-netty-codec-http2-upgrade`](../rewrite-netty-codec-http2-upgrade) | 安全守卫后调用 3 个 `AddLiteralMethodArgument` 和 1 个 `DeleteMethodArgument` | `b47e76d` |
| 9 | `bcpkix-jdk18on` → `1.81.1` | [`rewrite-bcpkix-jdk18on-upgrade`](../rewrite-bcpkix-jdk18on-upgrade) | 直接执行精确 `ChangeType`；排除宽 Bouncy Castle 聚合 | `22b042b` |
| 10 | `spring-web` → `6.2.19` | [`rewrite-spring-web-upgrade`](../rewrite-spring-web-upgrade) | 直接执行 14 个 Core/Spring 精确叶子 | `5c1505f` |
| 11 | `spring-boot-starter-actuator` → `3.5.15` | [`rewrite-spring-boot-starter-actuator-upgrade`](../rewrite-spring-boot-starter-actuator-upgrade) | 直接执行 Spring 属性、3.4 endpoint access、package/type recipe | `c8d023b` |
| 12 | `logback-core` → `1.5.34` | [`rewrite-logback-core-upgrade`](../rewrite-logback-core-upgrade) | 直接执行 type/method/XML recipe；无 1.2→1.5 专用聚合 | `8cf8423` |
| 13 | `tomcat-catalina` → `10.1.56` | [`rewrite-tomcat-catalina-upgrade`](../rewrite-tomcat-catalina-upgrade) | 直接执行 Servlet/EL 依赖、package、方法和参数叶子 | `e5046f0` |
| 14 | `netty-handler` → `4.1.136.Final` | [`rewrite-netty-handler-upgrade`](../rewrite-netty-handler-upgrade) | 直接执行 2 个 `AddLiteralMethodArgument`，本地仅补安全守卫 | `340000f` |
| 15 | `spring-kafka` → `3.3.15` | [`rewrite-spring-kafka-upgrade`](../rewrite-spring-kafka-upgrade) | 直接执行 Future、error handler、Header、测试工具和 Core 叶子 | `c7cbca6` |
| 16 | `spring-expression` → `6.2.19` | [`rewrite-spring-expression-upgrade`](../rewrite-spring-expression-upgrade) | 直接执行 Java 版本、Maven 参数和 Gradle compatibility recipe | `bf981eb` |
| 17 | `zookeeper` → `3.8.6` | [`rewrite-zookeeper-upgrade`](../rewrite-zookeeper-upgrade) | 直接执行 method/type/property recipe；YAML 精确旧值 gap 本地补齐 | `7889794` |
| 18 | `spring-security-web` → `6.5.11` | [`rewrite-spring-security-web-upgrade`](../rewrite-spring-security-web-upgrade) | 按源 release 门控官方 Java 17、Security 5.7–6.2、Servlet 和搜索叶子 | `90ce94a` |
| 19 | `elasticsearch` → `1.21.4` | [`rewrite-elasticsearch-upgrade`](../rewrite-elasticsearch-upgrade) | 直接执行 `ExplicitContainerImage`；AUTO 仅限 Testcontainers `1.17.6`，Server 7.x 只做身份冲突 MARK | `f991829` |
| 20 | `spring-boot` → `3.5.15` | [`rewrite-spring-boot-upgrade`](../rewrite-spring-boot-upgrade) | 按源 release band 执行 Boot 属性、Java、package/type/key 和 auto-configuration 叶子 | `9296618` |
| 21 | `log4j-1.2-api` → `2.25.5` | [`rewrite-log4j-1-2-api-upgrade`](../rewrite-log4j-1-2-api-upgrade) | 只有明确拥有 Log4j Core 时，opt-in 入口才执行官方源码叶子；默认入口不选择 backend | `678273e` |
| 22 | `spring-security-core` → `6.5.11` | [`rewrite-spring-security-core-upgrade`](../rewrite-spring-security-core-upgrade) | 直接执行密码编码器、Java/XML/Reactive 方法安全及 `queryableText` 搜索叶子 | `adcf9e1` |
| 23 | `junrar` → `7.5.10` | [`rewrite-junrar-upgrade`](../rewrite-junrar-upgrade) | 直接执行 8 个 `FindMethods` 叶子并复用 `LatestRelease` 禁止降级比较 | `e8e6d15` |
| 24 | `jetty-http` → `12.0.34` | [`rewrite-jetty-http-upgrade`](../rewrite-jetty-http-upgrade) | 直接执行 Java 17、2 个方法和 3 个类型迁移叶子 | `47019aa` |
| 25 | `spring-retry` → `2.0.13` | [`rewrite-spring-retry-upgrade`](../rewrite-spring-retry-upgrade) | 项目门控后直接执行官方依赖、Java 17、6 个注解和 1 个方法叶子；冲突注解安全保护 | `6423a9a` |

## 特殊边界

- `tomcat-embed-core` 的 Tomcat 11 声明绝不改到 10.1；它们属于禁止降级冲突。
- `tomcat-catalina:10.1.56` 已在模块中标记后续安全修复风险；配方仍严格遵守用户给定目标。
- `elasticsearch:1.21.4` 是 `org.testcontainers:elasticsearch`，不是
  `org.elasticsearch:elasticsearch` Server 的升级目标。
- GitHub Actions 为异步门禁；本地模块测试和 Catalog 校验完成后即提交，不以远端长流水线阻塞本地工作。
