# Jaxen 2.0.1 升级配方

本模块处理 `jaxen:jaxen` 的两条工作簿升级路径：

| 工作簿行 | 序号 | 源版本 | 目标版本 |
|---:|---:|---:|---:|
| 1477 | 1476 | 1.2.0 | 2.0.1 |
| 3243 | 3242 | 2.0.0 | 2.0.1 |

模块坐标为 `com.huawei.clouds.openrewrite:rewrite-jaxen-upgrade`，Java 包和配方命名空间均为 `com.huawei.clouds.openrewrite.jaxen`。

## 配方

### `com.huawei.clouds.openrewrite.jaxen.UpgradeJaxenTo2_0_1`

只执行可证明归属且无歧义的依赖版本替换：

- Maven：支持项目、profile 及各自 `dependencyManagement` 中的标准无 classifier JAR 依赖；直接版本仅把 `1.2.0`、`2.0.0` 改为 `2.0.1`。
- Maven 属性：根属性可以服务项目和 profile；profile 属性只在本 profile 内生效。属性必须只有一个定义、只归属 Jaxen 版本，重复定义、跨 profile、复用为其他文本或依赖时不自动修改。
- Gradle Groovy/Kotlin：支持标准依赖配置中的直接字符串；Groovy 还支持 map notation。根 `build.gradle`/`build.gradle.kts` 的唯一专用版本属性可自动更新。
- 不自动修改版本目录、BOM/平台管理、动态版本、版本范围、无版本依赖、非根 Gradle 属性、classified/non-JAR 变体或工作簿以外的固定版本。
- `target`、`build`、`generated`、`install` 等父目录下的产物被跳过；名为 `target.gradle`、`install.gradle` 的普通叶文件仍可处理。

### `com.huawei.clouds.openrewrite.jaxen.MigrateJaxenTo2_0_1`

推荐配方严格按“确定性 AUTO 在前、精确风险 MARK 在后”执行：

1. 执行上述依赖升级。
2. 按 Jaxen 1.2 自身弃用说明执行两个类型归属明确的一对一 Java 替换：
   - `org.jaxen.XPath`/`BaseXPath.valueOf(Object)` → `stringValueOf(Object)`；
   - `org.jaxen.FunctionCallException.getNestedException()` → 标准 `getCause()`。
3. 标记不能安全猜测业务意图的 Java、构建和配置风险。

同名业务方法不会被改写；Java AUTO 依赖 OpenRewrite 类型归属，而不是文本替换。

## 不兼容修改与人工检查点

| 类别 | 本模块的处理 | 需要确认的内容 |
|---|---|---|
| Java 基线 | MARK | Jaxen 2 的公开基线为 Java 1.5；检查编译、测试、运行时和工具链。2.0.1 中央仓制品由 JDK 8 构建，但这不等同于要求应用使用 Java 8。 |
| 已删除/隐藏实现类 | MARK | `AnyChildNodeTest`、`NoNodeTest`、`LinkedIterator`、`StackedIterator` 已删除；多种 `org.jaxen.expr.Default*` 实现被隐藏。应迁移到公开 `XPath`/`Navigator` API 或应用自有实现。 |
| XPath 引擎/模型 | MARK | `DOMXPath`、`Dom4jXPath`、`JDOMXPath`、`XOMXPath`、`JavaBeanXPath` 的结果、顺序和非法输入回归。JDOM、dom4j、XOM 在 2.x 为可选依赖，必须由应用显式决定模型库版本。 |
| 2.0.1 行为修复 | MARK | 数字字符串解析和负零、连续谓词、comment 谓词文本、namespace（含预绑定 `xml` 前缀）、DOM `DocumentFragment`/`EntityReference` 遍历。 |
| 上下文扩展点 | MARK | 自定义 `NamespaceContext`、`FunctionContext`、`VariableContext` 的 QName、未解析值、函数元数/类型、强制转换、空值和线程安全。 |
| document() / Navigator | MARK | URI 与 base URI、parser/document factory、编码、缓存、classloader、XXE/DTD/entity 策略和异常 cause。 |
| 异常 | AUTO + MARK | 唯一确定的 `getNestedException()` 替换自动完成；catch 顺序、消息、cause、日志和序列化契约需人工验证。 |
| 序列化 | MARK | Jaxen 核心类型的 UID 未观察到变化，但隐藏类、自定义 context 和对象模型图仍可能使持久化缓存/消息不兼容。 |
| JPMS/OSGi/反射/native-image | MARK | 2.0.1 的自动模块名是 `org.jaxen`；检查旧 `requires jaxen`、bundle 导出/导入、反射类名和 native-image metadata。 |
| 构建归属 | MARK | 动态/范围/无版本、属性/BOM/catalog 管理、工作簿以外版本和变体只标记，不扩大 AUTO 范围。 |

配方不会自动选择或添加 dom4j/JDOM/XOM，不会重写 XPath 表达式，也不会为已删除实现类臆造替代类。这些选择依赖应用的数据模型、XPath 结果契约和部署环境。

## 规格、实现与测试对应

| 规格 | 实现 | 主要测试 |
|---|---|---|
| 两条 XLSX 版本路径、直接版本和安全属性归属 | `UpgradeSelectedJaxenDependency` | 两个源版本、项目/profile/DM、跨 profile/重复/共享属性正反例 |
| Maven/Gradle 严格范围 | `JaxenSupport`、`UpgradeSelectedJaxenDependency` | 字符串/map/root property、Groovy/Kotlin、变体/动态/目录/叶文件/版本目录反例 |
| 官方一对一 Java 替换 | `MigrateJaxenDeterministicJava` | `XPath.valueOf`、`FunctionCallException.getNestedException`、同名业务方法、类型归属和幂等 |
| XPath/model/context/document/exception/serialization | `FindJaxenJavaMigrationRisks` | 五类 engine、匿名/命名 context、document、异常、反射、序列化及负例 |
| Java 基线和未解析构建归属 | `FindJaxenBuildMigrationRisks` | Maven/Gradle Java 1.4 与 1.5、范围/动态/无版本/变体、根与子模块属性 |
| JPMS、OSGi、native-image 和资源配置 | `FindJaxenConfigurationRisks` | properties/YAML/XML/manifest/JSON/cfg、POM 和普通文本负例、路径过滤 |
| AUTO-before-MARK 和重复执行稳定 | `rewrite.yml` 推荐配方 | 配方发现/校验/顺序、综合 Maven/Gradle/Java 场景、两轮幂等 |

当前测试套件共 44 个测试，所有 MARK 测试都直接检查 `SearchResult` 描述；测试输出隐藏 marker 文本，避免把打印格式误当作语义断言。

## 可复现依据

### 官方 Jaxen 固定提交

- `v1.2.0`：轻量 tag 指向 [`b9fb932ef64f0fe7b05f7ac33b4c5e8de6597b21`](https://github.com/jaxen-xpath/jaxen/tree/b9fb932ef64f0fe7b05f7ac33b4c5e8de6597b21)。1.2 的 [`XPath.java`](https://github.com/jaxen-xpath/jaxen/blob/b9fb932ef64f0fe7b05f7ac33b4c5e8de6597b21/src/java/main/org/jaxen/XPath.java) 明确要求用 `stringValueOf` 替代 `valueOf`；[`FunctionCallException.java`](https://github.com/jaxen-xpath/jaxen/blob/b9fb932ef64f0fe7b05f7ac33b4c5e8de6597b21/src/java/main/org/jaxen/FunctionCallException.java) 明确要求用 `getCause`。
- `v2.0.0`：轻量 tag 指向 [`39e4f2853710da1453980a72acc24b77fe7bf5d9`](https://github.com/jaxen-xpath/jaxen/tree/39e4f2853710da1453980a72acc24b77fe7bf5d9)；[发布说明](https://github.com/jaxen-xpath/jaxen/blob/39e4f2853710da1453980a72acc24b77fe7bf5d9/src/site/xdoc/releases.xml) 记录 Java 1.5+ 与对象模型依赖可选化。
- `v2.0.1`：annotated tag object `d74960c5…` peel 后指向 [`21b6f5f4a85c61964e79eabadbf32451fa2a14ec`](https://github.com/jaxen-xpath/jaxen/tree/21b6f5f4a85c61964e79eabadbf32451fa2a14ec)。目标 [`pom.xml`](https://github.com/jaxen-xpath/jaxen/blob/21b6f5f4a85c61964e79eabadbf32451fa2a14ec/pom.xml) 和 [`API_COMPATIBILITY.md`](https://github.com/jaxen-xpath/jaxen/blob/21b6f5f4a85c61964e79eabadbf32451fa2a14ec/API_COMPATIBILITY.md) 固定 Java 1.5 API 检查；[`core/pom.xml`](https://github.com/jaxen-xpath/jaxen/blob/21b6f5f4a85c61964e79eabadbf32451fa2a14ec/core/pom.xml) 定义 `Automatic-Module-Name: org.jaxen`。

2.0.1 的风险提示还对应以下官方修复提交：[`a99ad317`](https://github.com/jaxen-xpath/jaxen/commit/a99ad317)（负零）、[`e65b0335`](https://github.com/jaxen-xpath/jaxen/commit/e65b0335)（数字解析）、[`bf71dedb`](https://github.com/jaxen-xpath/jaxen/commit/bf71dedb)（谓词）、[`f0130508`](https://github.com/jaxen-xpath/jaxen/commit/f0130508)（comment）、[`f311e1db`](https://github.com/jaxen-xpath/jaxen/commit/f311e1db)（DOM fragment/entity）和 [`e4007dcf`](https://github.com/jaxen-xpath/jaxen/commit/e4007dcf)（`xml` namespace）。

### Maven Central 制品

| 版本 | JAR | SHA-256 |
|---:|---|---|
| 1.2.0 | [jaxen-1.2.0.jar](https://repo1.maven.org/maven2/jaxen/jaxen/1.2.0/jaxen-1.2.0.jar) | `70feef9dd75ad064def05a3ce8975aeba515ee7d1be146d12199c8828a64174c` |
| 2.0.0 | [jaxen-2.0.0.jar](https://repo1.maven.org/maven2/jaxen/jaxen/2.0.0/jaxen-2.0.0.jar) | `9499e487a66268f47b8307d130cd1e13a58392105e98a51f6a525db79c615cc5` |
| 2.0.1 | [jaxen-2.0.1.jar](https://repo1.maven.org/maven2/jaxen/jaxen/2.0.1/jaxen-2.0.1.jar) | `71d5c125bd1686de2f9ad8c95efbfb93896d39862cba2e69362d45112bc85d56` |

### 真实公开仓库固定用例

- Spring Framework [`224522244f7698673584910ee805415673227a7e`](https://github.com/spring-projects/spring-framework/blob/224522244f7698673584910ee805415673227a7e/framework-platform/framework-platform.gradle#L83)：Gradle `api("jaxen:jaxen:1.2.0")`。
- Spring Web Services [`2692136fca6d3f89aea8478ae5dd106bde5e785e`](https://github.com/spring-projects/spring-ws/blob/2692136fca6d3f89aea8478ae5dd106bde5e785e/spring-ws-platform/build.gradle#L50)：Gradle `2.0.0`。
- And Bible [`0b03d63d07d5f1626f2cd7fdae05e026e0eecda8`](https://github.com/AndBible/and-bible/blob/0b03d63d07d5f1626f2cd7fdae05e026e0eecda8/app/build.gradle.kts#L477)：Kotlin DSL `2.0.0`。
- Apache FreeMarker [`5e60f377fa5cd7cfc8c61575907374b1487654c7`](https://github.com/apache/freemarker/blob/5e60f377fa5cd7cfc8c61575907374b1487654c7/freemarker-core/src/main/java/freemarker/ext/dom/NodeModel.java#L666)：反射加载 `org.jaxen.dom.DOMXPath`，用于精确反射风险用例。
- OpenRewrite [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)：测试结构参考其 Java `ChangePackage`、Gradle `ChangeDependency` 和文本查找/替换用例，增加了正例、近似负例、路径叶文件和多轮幂等断言。

## 运行

```bash
mvn -f rewrite-jaxen-upgrade/pom.xml clean verify
```
