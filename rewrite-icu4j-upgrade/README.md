# ICU4J 77.1 升级配方

本模块处理 `com.ibm.icu:icu4j` 从工作簿中明确出现的 `67.1`、`73.1`、`73.2` 升级到 `77.1`。严格升级配方不会把相邻版本、范围、动态版本、外部 BOM、version catalog、classifier/non-JAR 变体或生成目录擅自纳入范围；项目或 profile 自己拥有的标准 `dependencyManagement` 固定版本会安全升级。

## 配方

- `com.huawei.clouds.openrewrite.icu4j.UpgradeIcu4jTo77_1`：只升级 Maven 直接/本地 dependencyManagement 标准依赖、仅由该依赖拥有的本地 Maven 属性，以及 Gradle Groovy/Kotlin DSL 顶层 `dependencies {}` 中的直接字面量依赖。
- `com.huawei.clouds.openrewrite.icu4j.MigrateIcu4jTo77_1`：在严格升级之上执行确定性 Java 改写，并在精确的 Java/构建边界上插入 `SearchResult` 人工复核标记。

推荐先运行 `MigrateIcu4jTo77_1`，处理完全部标记后，再用目标 JDK 对完整工程进行 clean build、测试及业务语料回归。

## 已处理的不兼容修改点

| 修改点 | 本模块处理 | 迁移要求 |
| --- | --- | --- |
| Java 基线 | `MARK` Maven compiler `< 8` | ICU4J 从 72 起要求 Java 8；检查 CI、生产镜像、插件宿主、Android API 21+ desugaring 与测试启动器。77.1 POM 的 source/target 为 1.8。 |
| `IDNA.DEFAULT` 语义改变 | `AUTO` 将有类型归属的旧 `IDNA.DEFAULT` 改成字面量 `0`，并清理因此失效的 import | ICU 76 将 `DEFAULT` 从 `0` 改成 `NONTRANSITIONAL_TO_ASCII | NONTRANSITIONAL_TO_UNICODE`；自动改写保留旧程序数值语义。旧 IDNA2003 `convert*`/`compare*` API 仍会 `MARK`，需按 UTS #46、安全策略和真实域名语料迁移。 |
| `ListFormatter.Style` 删除 | `AUTO` 五个枚举常量映射到 `Type`/`Width` 并清理失效的 Style/static import | `STANDARD→AND/WIDE`、`OR→OR/WIDE`、`UNIT→UNITS/WIDE`、`UNIT_SHORT→UNITS/SHORT`、`UNIT_NARROW→UNITS/NARROW`；动态 `Style` 变量不猜测，保留并 `MARK`。 |
| `BasicTimeZone.getOffsetFromLocal(long,int,int,int[])` 删除 | `MARK` 精确调用 | 旧 `LOCAL_*` 位标志需要人工决定重叠时的 former/latter 与 standard/daylight 语义，再映射到 `LocalOption`。 |
| `NoUnit` 类型层级改变 | `MARK` 精确类型 | 77.1 中常量作为 `MeasureUnit` 暴露，复核赋值、重载、序列化、反射和二进制边界。 |
| draft API 删除 | `MARK` `PluralRanges`、`PluralSamples`、`FixedDecimalRange` 等精确类型 | draft API 不享受稳定性承诺；改用受支持 API，并重建 locale-specific golden data。 |
| `com.ibm.icu.impl.*` 内部 API | `MARK` 有类型归属的精确使用点 | 这是非支持实现 API；换成公开 API，检查反射、服务加载、shading 和插件 classloader。 |
| Unicode/CLDR 数据变化 | `MARK` 格式化、locale matching 边界 | 67.1 到 77.1 跨越 Unicode 16 与 CLDR 47；数字、货币、单位、复数、列表、日期、pattern、解析结果及 locale fallback 都可能变化。 |
| Collation 变化 | `MARK` Collator/RuleBasedCollator/StringSearch 调用 | 回归 root/DUCET、Han radical-stroke、瑞典语/芬兰语 tailoring、自定义 rule 与搜索结果。 |
| Segmentation 变化 | `MARK` BreakIterator 调用 | ICU 77 涉及 colon word-break tailoring、NBSP+combining+hyphen line break、Indic conjunct grapheme；回归 token/boundary index。 |
| 时区数据变化 | `MARK` TimeZone/BasicTimeZone/Calendar 调用 | 目标包含 tzdata 2025a，且中间版本改变部分 pre-1970 数据/别名；回归历史/未来 offset、transition、zone id、序列化。 |
| ICU4J companion 版本偏斜 | `MARK` `icu4j-charset`/`icu4j-localespi` 非 77.1 固定版本 | 三个 artifact 按同一版本族处理，检查 SPI discovery、charset、排除项与二进制链接。 |
| shaded/relocated ICU | `MARK` Maven relocation 的 `com.ibm.icu` pattern | 重新构建 shaded 包，检查 ICU data/resource/service 加载、重复类、反射、序列化和 classloader isolation。 |
| ICU4J 自身源码布局 | 文档约束 | ICU 74 将 ICU4J 源码从 Ant 风格重排为 Maven 默认布局；只影响 vendored/forked ICU 源码，普通 Maven/Gradle 消费者不自动移动目录。 |
| 二进制兼容 | 文档约束 | 67.1 classfile 为 Java 7、77.1 为 Java 8；所有直接/间接消费者和插件都应 clean rebuild，不能只替换运行时 JAR。 |

`AUTO` 仅用于等价关系确定、且 OpenRewrite 类型归属完整的代码。无法证明等价的 Unicode/locale/timezone 行为一律只标记，不做基于文本的猜测。

Maven 属性会统计所有文本与 attribute 引用，重复定义、root/profile 遮蔽或被 companion/其他依赖共享时保持不变并标记真实所有者；目标版本的本地 dependencyManagement 会抑制对应 versionless consumer 的假阳性，且 profile 管理不会泄漏到 root 作用域。Gradle 只接受没有 `subprojects`、`buildscript` 或自定义外层 DSL 的项目级 `dependencies {}`。`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、`.m2`、`.yarn`、`.idea`、`node_modules`、`vendor` 均排除。

## 版本与官方依据

- ICU 77.1 固定提交：[`457157a92aa053e632cc7fcfd0e12f8a943b2d11`](https://github.com/unicode-org/icu/tree/457157a92aa053e632cc7fcfd0e12f8a943b2d11)，[ICU 77 发布说明](https://unicode-org.github.io/icu/download/77.html)，[目标 ICU4J POM](https://github.com/unicode-org/icu/blob/457157a92aa053e632cc7fcfd0e12f8a943b2d11/icu4j/main/icu4j/pom.xml)。
- 源版本固定提交：[`67.1`](https://github.com/unicode-org/icu/tree/125e29d54990e74845e1546851b5afa3efab06ce)、[`73.1`](https://github.com/unicode-org/icu/tree/5861e1fd52f1d7673eee38bc3c965aa18b336062)、[`73.2`](https://github.com/unicode-org/icu/tree/680f521746a3bd6a86f25f25ee50a62d88b489cf)。
- 中间迁移依据：[ICU 68](https://icu.unicode.org/download/68)、[ICU 69](https://icu.unicode.org/download/69)、[ICU 70](https://icu.unicode.org/download/70)、[ICU 73](https://icu.unicode.org/download/73)、[ICU 74](https://icu.unicode.org/download/74)、[ICU 76](https://unicode-org.github.io/icu/download/76.html)。
- API 稳定性边界：[ICU design / API stability](https://unicode-org.github.io/icu/userguide/icu/design)、[ICU4J FAQ](https://unicode-org.github.io/icu/userguide/icu4j/faq.html)。
- `IDNA.DEFAULT` 与 API 差异直接比较：[67.1 IDNA.java](https://github.com/unicode-org/icu/blob/125e29d54990e74845e1546851b5afa3efab06ce/icu4j/main/classes/core/src/com/ibm/icu/text/IDNA.java)、[77.1 IDNA.java](https://github.com/unicode-org/icu/blob/457157a92aa053e632cc7fcfd0e12f8a943b2d11/icu4j/main/core/src/main/java/com/ibm/icu/text/IDNA.java)、[67.1 ListFormatter.java](https://github.com/unicode-org/icu/blob/125e29d54990e74845e1546851b5afa3efab06ce/icu4j/main/classes/core/src/com/ibm/icu/text/ListFormatter.java)、[77.1 ListFormatter.java](https://github.com/unicode-org/icu/blob/457157a92aa053e632cc7fcfd0e12f8a943b2d11/icu4j/main/core/src/main/java/com/ibm/icu/text/ListFormatter.java)。

## 真实仓库与测试依据

构建测试从真实仓库固定提交缩减而来，测试注释保留了源文件和行号：

- ReportPortal TestNG agent：[`ae2b0beb07314f2dfb3473b28f481257d0bd175b`](https://github.com/reportportal/agent-java-testNG/blob/ae2b0beb07314f2dfb3473b28f481257d0bd175b/build.gradle#L41-L68)，覆盖 Groovy DSL `67.1`。
- IReader：[`9e2b0d917532ceccd8c29c4fd3fde53ddda2cce5`](https://github.com/IReaderorg/IReader/blob/9e2b0d917532ceccd8c29c4fd3fde53ddda2cce5/android-compat/build.gradle.kts#L71-L90)，覆盖 Kotlin DSL `73.1`。
- whatwg-url：[`91025bca89e71a85c98fc437110314f6b9b23337`](https://github.com/stephanebastian/whatwg-url/blob/91025bca89e71a85c98fc437110314f6b9b23337/build.gradle.kts#L15-L20)，覆盖 Kotlin DSL `73.2`。
- OpenRewrite 测试写法固定参考 [`openrewrite/rewrite@d4ac42e`](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-java-test/src/test/java/org/openrewrite/java/JavaTemplateTest.java)，包括 `JavaTemplate` argument replacement、before/after、类型校验和 cycle/idempotency 模式。

当前 61 个测试覆盖 Maven、Gradle Groovy、Gradle Kotlin、全部可见源版本、真实仓库夹具、属性所有权、profiles、metadata、dependencyManagement 与 versionless scope、variants、dynamic/external ownership、generated paths、recipe discovery/validation、全部五个 `ListFormatter.Style` 映射、仅限 ICU 的失效 import 清理与无关 import 保留、qualified/static-import `IDNA.DEFAULT`、旧/新 `BasicTimeZone` 重载精确正反例、真实 compiler/shade 插件所有权、构建标记及双周期幂等。

## 本地验证

```bash
mvn -f rewrite-icu4j-upgrade/pom.xml clean verify
```
