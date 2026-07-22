# Feign Jackson 13.6 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `io.github.openfeign:feign-jackson` 的全部明确记录：工作表行 394、395、1505、1506（序号 393、394、1504、1505），把字面源版本 `10.4.0`、`11.1`、`12`、`12.4` 迁移到 `13.6`。`12` 是表格中的精确值；即使官方 tag 名为 `12.0`，配方也不会擅自把 `12.0` 扩入自动升级白名单。

推荐入口：

```text
com.huawei.clouds.openrewrite.feignjackson.MigrateFeignJacksonTo13_6
```

它依次执行严格依赖升级、类型归属可证明的 Feign 13 API 迁移，并在无法替业务判断的 codec、迭代器和依赖对齐节点添加带原因的 `SearchResult`。例如 `/*~~(Feign Jackson 12+ maps HTTP 404/204...)~~>*/new JacksonDecoder()` 表示该构造点已被发现但需要行为回归，不表示已经自动修复。

仅需版本选择时使用低层入口：

```text
com.huawei.clouds.openrewrite.feignjackson.UpgradeFeignJacksonTo13_6
```

低层入口只包含严格版本升级，不会隐式执行源码迁移或审计。

## 不兼容点、配方行为与验证

| 不兼容点或边界 | 配方行为 | 状态 | 测试依据 |
| --- | --- | --- | --- |
| 表格 4 个字面源版本 | 仅将 `10.4.0`、`11.1`、`12`、`12.4` 的 `feign-jackson` 改为 `13.6`，其他固定版本不猜测、不降级 | **AUTO** | 4 版本参数化 before→after + `Set` 等值锁定 + 7 个固定负例 |
| Maven direct / dependencyManagement / direct profile | 只处理 project 或其直接 profile 的标准 dependency；保留 scope、optional、exclusions 等相邻内容 | **AUTO** | direct、root/profile DM、元数据保留测试 |
| Maven 本地版本属性 | 仅当定义唯一、至少有一个目标引用、且全部引用都属于受支持 `feign-jackson` version 时更新；root 对 profile 可见，profile override 优先且不泄漏 | **AUTO / NOOP** | 独占属性、root/profile visibility、override、sibling、未使用/重复/跨用途/attribute 负例 |
| Gradle Groovy/Kotlin root dependencies | 仅顶层、无 select 的真实 `dependencies {}` 标准 configuration 直接坐标；支持字符串、Groovy named/map notation | **AUTO** | Groovy/Kotlin/map before→after，7 种嵌套 DSL NOOP |
| BOM/versionless、property/catalog、range/dynamic、variant、表格外版本 | 严格升级器保持不变；推荐入口在真实依赖值、模板、version 或 dependency 节点 MARK | **NOOP / MARK** | versionless、`${...}`、GString、`[10,13)`、`+`、classifier/type/ext、13.5 测试 |
| `Feign.Builder.decode404()` 在目标 API 中改名 | 当方法类型确认属于 `feign.BaseBuilder` / `feign.Feign.Builder` 时改为同语义的 `dismiss404()`，保持 Jackson encoder/decoder 链顺序 | **AUTO** | 单独/组合 chain、业务同名 NOOP、target NOOP、two-cycle、推荐入口测试 |
| 10.4 的 `JacksonIteratorDecoder` 迭代推进合同 | 在三个 `JacksonIteratorDecoder.create(...)` 重载的实际调用节点 MARK；业务需验证直接 `next()`、`hasNext()`、耗尽异常和提前关闭 | **MARK** | 3 重载精确 marker + 同名业务类 NOOP |
| 12.x 起 404/204 解码为空值 | 在实际 `new JacksonDecoder(...)` 节点 MARK；复核 POJO null、Optional、collection/map 默认空值与 `dismiss404` 组合 | **MARK** | 默认/custom mapper/modules 构造精确 marker |
| Jackson 2.9/2.10/2.15 跨到受管 2.18.3 | 默认 encoder/decoder 与 caller-owned ObjectMapper/Module 使用不同原因 MARK，要求验证 coercion、命名、日期、枚举、数字、records、转义、unknown properties 和 polymorphic typing 安全 | **MARK** | 4 类 constructor marker、普通 ObjectMapper NOOP |
| 自定义 `JacksonDecoder` / `JacksonEncoder` 子类 | 只在准确 extends type 节点 MARK；重新编译并验证异常合同、body 所有权、线程安全和 mapper 生命周期 | **MARK** | decoder/encoder extends 精确 marker |
| Feign family 版本不一致 | 直接声明的 `io.github.openfeign:feign-*` companion 若不是 `13.6`，在实际 version/coordinate 节点 MARK | **MARK** | Maven core/okhttp、Gradle Kotlin、aligned NOOP |
| 显式 Jackson 模块覆盖传递版本 | 对 core/datatype/module/dataformat 下的 `jackson-*` 非 `2.18.3` 声明 MARK，不自动替业务升级 Jackson BOM | **MARK** | Maven databind/jsr310、Gradle map/Kotlin、2.18.3 NOOP |
| Java 低于 8 | 只在 project/direct profile 的标准 compiler 属性值上 MARK，不扫描任意 XML 文本 | **MARK** | release 7 / profile java 1.7 marker，unrelated key 与 17 NOOP |
| `target/build/out/dist/generated*/install*/.gradle/.mvn/.m2/.idea/node_modules/vendor/reports` 等 | 只检查父目录组件；生成、安装和缓存产物不做 AUTO/MARK；叶文件 `install.java` / `install.gradle` 仍处理 | **NOOP** | 8 个父目录参数化测试、4 个源码目录测试、2 个叶文件对照 |

## 确定性源码转换

```java
// before
Feign.builder()
    .decoder(new JacksonDecoder())
    .decode404()
    .target(Api.class, endpoint);

// after
Feign.builder()
    .decoder(new JacksonDecoder())
    .dismiss404()
    .target(Api.class, endpoint);
```

这是目标官方给出的等价命名迁移，且配方要求方法类型归属 Feign builder；下面的业务代码不会改变：

```java
class LocalBuilder {
    LocalBuilder decode404() { return this; }
}
```

## 必须人工回归的行为

- `JacksonDecoder` 在 Feign 12 起对 404/204 直接返回 `Util.emptyValueOf(type)`。分别验证 POJO、primitive、Optional、collection、map、void、空 body、有 body 的 404，以及是否启用 `dismiss404`。
- 10.4 的流式迭代器在 `hasNext()` 中推进并让 `next()` 返回当前项；11.x 修正为符合 `Iterator` 合同的缓存/推进逻辑，并在耗尽时抛 `NoSuchElementException`。验证 `next()` 不先调用 `hasNext()`、重复 `hasNext()`、部分消费后 close、解析错误和 response body 关闭。
- 非流式 decoder 从固定 UTF-8 改为使用 response `Content-Type` charset；流式 decoder 仍明确使用 UTF-8。回归 UTF-8、UTF-16、缺失/非法 charset、BOM 和服务端错误声明。
- Feign 13.6 管理 Jackson 2.18.3，而这些源线分别来自 Jackson 2.9.9.3/2.10.5.1/2.14 预发布线/2.15.2。重点验证 numeric/string/boolean coercion、unknown enum、case-insensitive、creator/record、Kotlin/JDK8/JSR310 module、naming、date/time zone、BigDecimal、null inclusion、escaping 和 polymorphic typing allow-list。
- `JacksonEncoder` 默认仍是 `NON_NULL + INDENT_OUTPUT`，但传入的自定义 mapper 完全由应用持有。确认 mapper 在注册 Feign codec 后不会被其他线程继续可变配置。
- 使用 `feign-bom`、Spring Cloud BOM、dependency locking、Gradle platform 或 version catalog 的项目，应在真正版本所有者处迁移，并用 dependency tree 验证没有旧 Feign core 或旧 Jackson module 赢得版本仲裁。

## 子配方

| 配方 | 作用 |
| --- | --- |
| `UpgradeSelectedFeignJacksonDependency` | 表格字面白名单、Maven scoped property、Gradle root DSL 的严格升级 |
| `MigrateFeignJackson13Apis` | 类型归属可证明的 `decode404()`→`dismiss404()` AUTO |
| `FindFeignJackson13SourceRisks` | codec 构造、ObjectMapper/module、iterator factory、自定义 subclass 的节点级 MARK |
| `FindFeignJackson13BuildRisks` | 版本所有权、variant、Feign/Jackson alignment、Java baseline 的节点级 MARK |
| `UpgradeFeignJacksonTo13_6` | 只执行严格依赖升级的公开入口 |
| `MigrateFeignJacksonTo13_6` | 推荐的 AUTO + MARK 组合入口 |

完整名称均以 `com.huawei.clouds.openrewrite.feignjackson.` 开头。

## 官方固定依据

目标 tag [`13.6@abd43f76`](https://github.com/OpenFeign/feign/tree/abd43f761071653587ec10e98c03e749879485cc) 与目标制品 [Maven Central `feign-jackson:13.6`](https://repo1.maven.org/maven2/io/github/openfeign/feign-jackson/13.6/) 固定了本模块的目标：

- [13.6 `JacksonDecoder`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/jackson/src/main/java/feign/jackson/JacksonDecoder.java)、[`JacksonEncoder`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/jackson/src/main/java/feign/jackson/JacksonEncoder.java) 和 [`JacksonIteratorDecoder`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/jackson/src/main/java/feign/jackson/JacksonIteratorDecoder.java) 是目标公开 API 与默认行为依据；
- [13.6 parent POM](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/pom.xml) 固定 Jackson `2.18.3` 与 Java 8 主源码基线；
- iterator 合同修复固定到官方提交 [`ad8c9190`](https://github.com/OpenFeign/feign/commit/ad8c9190ae1a56baf0a7c991813f424db849fb21)；
- 404/204 empty-value 规则固定到 [`04a85e69`](https://github.com/OpenFeign/feign/commit/04a85e6961123d4fdb2ab9e4eabbfe09b4eea0a6)；
- response charset 修复固定到 [`b79d6fcf`](https://github.com/OpenFeign/feign/commit/b79d6fcf83bc56b6ab0459c0f63688a2b6197b28)；
- `decode404` 的目标替代名固定到 [`dacb0869`](https://github.com/OpenFeign/feign/commit/dacb086923dac14331f014fb25728661b2901f75)。

源版本 tag 固定提交：[`10.4.0@44d76840`](https://github.com/OpenFeign/feign/tree/44d76840b80417068a7b97b16a7b8a9a3d082fd3)、[`11.1@f6f5ff81`](https://github.com/OpenFeign/feign/tree/f6f5ff814c9bcc3918abb6e39764ad2c96536faa)、[`12.0@8c22fccd`](https://github.com/OpenFeign/feign/tree/8c22fccd8cdcbc875f4eede019f9d76332527d99)、[`12.4@602f588c`](https://github.com/OpenFeign/feign/tree/602f588ca538e0f7cc1b06840e5be6bb06f619d2)。`12.0` 只用于理解表格字面 `12` 对应的大版本源码，不扩大升级白名单。

## 真实公开仓固定夹具

| 仓库固定提交 | 实际场景 | 模块验证 |
| --- | --- | --- |
| [CNR/epas `7bac6d72`](https://github.com/consiglionazionaledellericerche/epas/blob/7bac6d72ae3af2a3b0dadc848a83e2af58d630ee/conf/dependencies.yml) | `feign-core/jackson/gson/okhttp/slf4j/micrometer` 全家族 `12.4`，同时显式固定 Jackson 2.12.3 | selected dependency AUTO；Feign family 与 Jackson mediation MARK |
| [Apache James `75a3c1e7`](https://github.com/apache/james-project/blob/75a3c1e7a4ae0656ffc8558c5853aba00a4f9009/server/protocols/webadmin/webadmin-http-client/src/main/java/org/apache/james/webadmin/httpclient/WebAdminHTTPClientFactory.java) | builder 分别注册默认 `JacksonDecoder` / `JacksonEncoder` | 保持链顺序；两个准确 constructor MARK |
| [DependencyTrack `018760a1`](https://github.com/DependencyTrack/dependency-track/blob/018760a18e623da18b0dbc36ec07dd84732448f9/e2e/src/main/java/org/dependencytrack/e2e/api/CompositeDecoder.java) | `JacksonDecoder` 被组合进自定义 decoder | 默认 decoder 行为边界 MARK |
| [OpenFeign benchmark `071cfe53`](https://github.com/OpenFeign/feign/blob/071cfe53ace0c38d7b1f19cd790eac7c9e8e11bf/benchmark/src/main/java/feign/benchmark/DecoderIteratorsBenchmark.java) | `JacksonIteratorDecoder.create()` 流式用法 | factory 调用节点 MARK 与 iterator 回归清单 |

测试结构参考 OpenRewrite 官方固定提交 [`rewrite-java-dependencies@decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，采用 before→after、NOOP、实际原因 marker、真实仓固定提交夹具和 two-cycle idempotency。模块当前共 80 个 JUnit invocation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-feign-jackson-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.feignjackson.MigrateFeignJacksonTo13_6
```

审查所有 patch 与 `~~>`，刷新 dependency locks，再运行 compile、unit/integration、404/204、empty body、charset、JSON golden-file、iterator exhaustion/close、ObjectMapper module、polymorphic typing/security、Feign interceptors/decoder、dependency convergence、JPMS/shading/native-image 测试。

模块自身验证（无需修改根聚合 POM）：

```bash
mvn -f rewrite-feign-jackson-upgrade/pom.xml clean verify
```
