# 固定提交真实用例

这些测试夹具是从真实仓库的固定提交中裁剪出的最小可解析片段。Java 裁剪仅移除了与
OpenRewrite 断言无关的框架类型、日志和业务代码；POM 裁剪仅移除了不可离线解析的 parent
和无关依赖。关键 SpEL 调用形态、依赖 owner、版本、scope、exclusion 和相邻坐标保持不变。
测试不会在构建期间访问网络。

| 夹具 | 固定来源 | 覆盖边界 |
|---|---|---|
| `spring-cloud-gateway-discovery.java.txt` | [`spring-cloud-gateway@f92a674e` `DiscoveryClientRouteDefinitionLocator`](https://github.com/spring-cloud/spring-cloud-gateway/blob/f92a674effe0fa67750e258849604a4d766943dd/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/discovery/DiscoveryClientRouteDefinitionLocator.java) | 动态路由表达式、只读 `SimpleEvaluationContext`、目标类型转换 |
| `java-sec-code-spel.java.txt` | [`java-sec-code@4711f4e1` `SpEL.java`](https://github.com/JoyChou93/java-sec-code/blob/4711f4e186258c6e0dd5c3863e7c9592e7e9026a/src/main/java/org/joychou/controller/SpEL.java) | 不可信输入、`StandardEvaluationContext` 与受限上下文对照 |
| `datagear-map-accessor.java.txt` | [`datagear@6398c73e` `MapAccessor.java`](https://github.com/datageartech/datagear/blob/6398c73ecc9cc3844b54dc85b59d5565eaf66100/datagear-util/src/main/java/org/datagear/util/spel/MapAccessor.java) | 6.2 target-specific `PropertyAccessor` 优先级 |
| `thymeleaf-spel-compiler.java.txt` | [`thymeleaf@7448e91e` `SPELVariableExpressionEvaluator.java`](https://github.com/thymeleaf/thymeleaf/blob/7448e91e73ac44666a7f8f74a5051a308b77122b/lib/thymeleaf-spring6/src/main/java/org/thymeleaf/spring6/expression/SPELVariableExpressionEvaluator.java) | `MIXED` 编译模式和旧构造器默认操作上限 |
| `iped-spring-expression-pom.xml.txt` | [`IPED@bdcf6f79` `iped-parsers-impl/pom.xml`](https://github.com/sepinf-inc/IPED/blob/bdcf6f792fe4dd592cc26bd7af88e6e9e2163ccc/iped-parsers/iped-parsers-impl/pom.xml) | 正例：精确字面量 `5.3.39` 升级，保留 exclusion 和相邻依赖 |
| `dubbo-shared-spring-owner-pom.xml.txt` | [`Apache Dubbo@eb1d8aba` `pom.xml`](https://github.com/apache/dubbo/blob/eb1d8abaebdc2ce1e15d6236cf9f9179d34e9082/pom.xml) | 负例：`spring-6.version` 跨多个 Spring 坐标共享，不接管 owner，也不改 Java 8 构建 |
| `sonar-java-higher-spring-expression-pom.xml.txt` | [`SonarJava@dc4ea3e9` `pom.xml`](https://github.com/SonarSource/sonar-java/blob/dc4ea3e9135cc9ef0a004c30c6d07e4233c5859f/pom.xml) | 负例：真实 `7.0.8` 高版本保持原样并精确标记禁止降级 |

许可证仍归各上游项目所有；这里只保留用于回归测试的短小事实性调用或构建片段。
