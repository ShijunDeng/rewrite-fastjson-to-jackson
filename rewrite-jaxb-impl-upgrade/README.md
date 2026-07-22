# JAXB RI `jaxb-impl` 升级配方

本模块把工作簿中明确列出的 `com.sun.xml.bind:jaxb-impl` 版本升级到 `4.0.6`，并处理 JAXB 2.x/4.x 之间可确定的 Java、RI 扩展属性和绑定文件迁移。不能安全推断的代码生成、provider、运行时部署及行为变化只做 `SearchResult` 标记，不自动猜测修改。

## 工作簿范围

来源为仓库根目录的 `开源软件升级.xlsx`。行号按 Excel 可见行计数，序号为表内序号：

| Excel 行 | 表内序号 | 坐标 | 源版本 | 目标版本 |
|---:|---:|---|---|---|
| 382 | 381 | `com.sun.xml.bind:jaxb-impl` | `2.3.8` | `4.0.6` |
| 3190 | 3189 | `com.sun.xml.bind:jaxb-impl` | `4.0.2` | `4.0.6` |
| 3191 | 3190 | `com.sun.xml.bind:jaxb-impl` | `4.0.3` | `4.0.6` |

低层升级配方严格使用这个白名单，不会把 `2.3.7`、`4.0.1`、范围、动态版本或未来版本顺带升级。

## 配方

### 严格低层配方

`com.huawei.clouds.openrewrite.jaxbimpl.UpgradeJaxbImplTo4_0_6`

仅升级以下拥有者中的精确版本：

- Maven 项目、`dependencyManagement` 或 profile 的直接 `dependency`；保留 scope、optional、exclusions 等元数据。
- 只被目标依赖引用一次、定义唯一且未被其它 XML 文本/属性共享的本地 Maven property。
- Gradle Groovy/Kotlin `dependencies {}` 中标准 configuration 的完整字符串字面量；Groovy 还支持 map notation。

以下情况保持不变：无版本/BOM 或父 POM 管理、变量插值、version catalog、版本范围/动态版本、plugin 自身依赖、非标准 configuration、classifier/type/ext 变体、相似 group/artifact，以及生成目录中的副本。

### 推荐迁移配方

`com.huawei.clouds.openrewrite.jaxbimpl.MigrateJaxbImplTo4_0_6`

执行顺序固定为先 AUTO、后 MARK：

1. AUTO：执行严格依赖升级。
2. AUTO：把有类型归属的 `javax.xml.bind` 改为 `jakarta.xml.bind`；仍使用已删除 `Validator` 的整个编译单元暂不修改。
3. AUTO：把 `javax.activation` 改为 `jakarta.activation`。
4. AUTO：把 `com.sun.xml.bind.marshaller.NamespacePrefixMapper` 改为 `org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper`。
5. AUTO：只迁移六个精确 RI marshaller property key：`namespacePrefixMapper`、`indentString`、`characterEscapeHandler`、`xmlDeclaration`、`xmlHeaders`、`objectIdentitityCycleDetection`。
6. AUTO：在 `.xjb`、`.jxb`、`.xsd` 中迁移标准 binding namespace、schema URL 和 binding language `3.0`；明确保留 `http://java.sun.com/xml/ns/jaxb/xjc`。
7. MARK：标出 Java、构建和资源配置中需要人工决策的风险。

路径过滤只检查父目录组件（大小写不敏感）：常见构建/缓存目录，以及以 `generated`、`install` 开头的目录都会跳过。因此 `install.gradle`、`install.java`、`install.xjb` 等叶文件仍会处理，而 `install/build.gradle`、`generated-client/`、`.m2/`、`.pnpm/` 等副本不会被修改或标记。

## 需要处理的不兼容修改点

| 修改点 | 配方行为 | 迁移要求 |
|---|---|---|
| Java 基线 | MARK | JAXB RI 4.0.6 的源码/构建基线是 Java 11；同步 compiler/toolchain、CI、镜像和生产 JVM。 |
| Javax → Jakarta | AUTO/受限 | JAXB 与 Activation 类型迁至 `jakarta.*`；不要在同一运行时混用旧 Javax API。 |
| 已删除 Validator API | MARK | `Validator`、`JAXBContext#createValidator`、`Marshaller/Unmarshaller#setValidating/isValidating` 需改用 `javax.xml.validation.Schema` 和 `setSchema`。 |
| 更严格的词法转换 | MARK | 对日期、QName、数字、布尔、hex、Base64 的合法值和非法值补回归测试；不要依赖 2.x 宽松解析。 |
| RI 内部类 | AUTO/MARK | 仅稳定的 `NamespacePrefixMapper` 自动迁移；其它 `com.sun.xml.bind.*`/`com.sun.xml.internal.bind.*` 内部 API 人工移植。 |
| RI property key | AUTO/MARK | 六个有明确新名称的 key 自动改为 `org.glassfish.jaxb.*`，未知 RI key 保留并标记。 |
| Provider 发现 | MARK | 旧 `jaxb.properties`、`META-INF/services/javax.xml.bind.*`、旧 ContextFactory 字符串已不兼容；改用 `jakarta.xml.bind.JAXBContextFactory`、ServiceLoader 或 JPMS `provides`。 |
| Binding/XJC | AUTO/MARK | 标准 binding 文件迁到 Jakarta namespace/version 3.0；对 Maven/Gradle XJC 插件、episode、扩展插件先升级后从干净目录重新生成并 diff。 |
| 伴随依赖对齐 | MARK | 目标 BOM 为 `jaxb-core/xjc/jxc/bom 4.0.6`、XML Binding API `4.0.4`、Activation API `2.1.4`、Angus Activation `2.0.3`。变量/BOM 所有者由人工统一修改。 |
| bundle 与 runtime 重复 | MARK | `jaxb-impl` 是打包的 RI implementation；同时引入 `org.glassfish.jaxb:jaxb-runtime` 可能造成类/provider 重复，应选定一种部署形态。 |
| JPMS/OSGi/shading/native-image | MARK | 删除 JDK 旧模块名，更新 module service、Import-Package、shading service 合并和反射/native-image 元数据，并验证类加载器隔离。 |
| 并发 | MARK | `JAXBContext` 可共享，`Marshaller`/`Unmarshaller` 不可跨线程共享；每次创建或使用边界清晰的池/ThreadLocal。 |
| 序列化与安全 | 人工验证 | 检查 JAXB 绑定类的 Java 序列化/缓存/消息兼容性，并重新验证外部实体、DTD、SchemaFactory 与输入上限等安全策略。 |

配方不会自动把 `jaxb-impl` 改成 `jaxb-runtime`：工作簿目标仍是原坐标，而且两者的发布形态和类加载影响不同，不能仅凭依赖名替用户做部署决策。

## 官方固定版本依据

所有源码依据均固定到 tag 解引用后的 commit，避免文档随主分支漂移：

| RI tag | 解引用 commit | 用途 |
|---|---|---|
| `2.3.8-RI` | [`1b86833e72d5f71eacfc473ea9fd95cdd762e90d`](https://github.com/eclipse-ee4j/jaxb-ri/commit/1b86833e72d5f71eacfc473ea9fd95cdd762e90d) | 工作簿旧线基线 |
| `4.0.2-RI` | [`d104f19389281e8308b1f94a93010ed40d7de573`](https://github.com/eclipse-ee4j/jaxb-ri/commit/d104f19389281e8308b1f94a93010ed40d7de573) | 工作簿源版本 |
| `4.0.3-RI` | [`ff66b109a0730e1a60182739edfe9dcf9a15d388`](https://github.com/eclipse-ee4j/jaxb-ri/commit/ff66b109a0730e1a60182739edfe9dcf9a15d388) | 工作簿源版本 |
| `4.0.6-RI` | [`0dcfdf5aed904623fb652dcf2d5ee06267e9a756`](https://github.com/eclipse-ee4j/jaxb-ri/commit/0dcfdf5aed904623fb652dcf2d5ee06267e9a756) | 目标实现、Java 11 和 BOM/依赖基线 |

目标 `jaxb-impl` 依赖与 manifest 可从固定提交的 [`bundles/runtime/pom.xml`](https://github.com/eclipse-ee4j/jaxb-ri/blob/0dcfdf5aed904623fb652dcf2d5ee06267e9a756/jaxb-ri/bundles/runtime/pom.xml) 核对；版本对齐见 [`boms/bom/pom.xml`](https://github.com/eclipse-ee4j/jaxb-ri/blob/0dcfdf5aed904623fb652dcf2d5ee06267e9a756/jaxb-ri/boms/bom/pom.xml)。规范依据为 [Jakarta XML Binding 4.0](https://jakarta.ee/specifications/xml-binding/4.0/)，JDK 内置 JAXB/Activation 模块移除背景见 [JEP 320](https://openjdk.org/jeps/320)。

## 真实仓库测试样本

测试不是只用人造最小片段；下列样本固定到不可变 commit 并缩减为单一行为：

- `2.3.8` Gradle：[`dbwlgns777/HMIToMESBridgeServer@ac52e.../build.gradle:32`](https://github.com/dbwlgns777/HMIToMESBridgeServer/blob/ac52e08f316060cd919aaacaf232570b9b009413/build.gradle#L32)。
- `4.0.2` Kotlin DSL：[`PBX-Manager/pbx-manager@1dbb.../build.gradle.kts:37`](https://github.com/PBX-Manager/pbx-manager/blob/1dbbfe4a9db76100a59ac7c7726099105582ebc0/build.gradle.kts#L37)。
- `4.0.3` Gradle：[`pegasystems/pega-logviewer@bb76.../build.gradle:41`](https://github.com/pegasystems/pega-logviewer/blob/bb7692ae9d06a910ceff09061580e457a56a076a/build.gradle#L41)。
- RI mapper/property：[`GoogleCloudPlatform/healthcare-data-harmonization@a69f.../XmlToJsonCDARev2.java`](https://github.com/GoogleCloudPlatform/healthcare-data-harmonization/blob/a69ff9619ae665ce475f6206ebc1fb459f69fbc2/wstl1/tools/XmlToJson/src/main/java/com/google/cloud/healthcare/etl/xmltojson/XmlToJsonCDARev2.java#L22)。
- XJB：[`eclipse-paho/paho.mqtt-spy@7376.../spy-bindings.xjb`](https://github.com/eclipse-paho/paho.mqtt-spy/blob/737699afbabaf01520302080a6f8b910f121ab2f/spy-common/src/main/resources/spy-bindings.xjb)。

测试写法参考 OpenRewrite 固定提交中的 [`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java)、[`ChangeDependencyTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-gradle/src/test/java/org/openrewrite/gradle/ChangeDependencyTest.java) 和 [`FindAndReplaceTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java)。当前 50 个测试覆盖严格白名单、根/profile property 解析与覆盖规则、错误坐标、动态/范围/变体、Gradle 根 DSL 所有权、生成目录和缓存、叶文件、类型归属、已删除验证 API 保护、精确字符串、AUTO→MARK 顺序及两周期幂等。

## 运行

```bash
mvn -f rewrite-jaxb-impl-upgrade/pom.xml test
```

在已加载本模块 recipe jar 的工程中：

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jaxbimpl.MigrateJaxbImplTo4_0_6
```

建议先在独立分支运行，检查所有 `~~(...)~~>` 标记，再执行 clean code generation、编译、单元/集成测试以及实际 marshalling/unmarshalling 样本对比。
