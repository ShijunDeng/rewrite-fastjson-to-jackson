# Vertical Blank SQL Formatter 15.6.5 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `com.github.vertical-blank:sql-formatter` 的全部出现：工作表行 414–417（序号 413–416），严格执行 `12.0.6`、`12.2.0`、`2.0.4`、`3.1.0` → `15.6.5`。不会扩充白名单、模糊匹配或给其他固定版本降级。

推荐入口：

```text
com.huawei.clouds.openrewrite.sqlformatter.MigrateSqlFormatterTo15_6_5
```

只需要工作簿字面版本替换时使用低层入口：

```text
com.huawei.clouds.openrewrite.sqlformatter.UpgradeSqlFormatterTo15_6_5
```

## 必须先处理的制品身份冲突

`com.github.vertical-blank:sql-formatter` 是 Java/Maven 制品，官方 Maven Central 只发布 `1.0`–`2.0.5`；其当前官方 README 也只声明 `2.0.5`。工作簿中的 `3.1.0`、`12.0.6`、`12.2.0` 和目标 `15.6.5` 是另一个项目 `sql-formatter-org/sql-formatter` 的 npm 版本，不是该 Maven 坐标的发布版本。Maven Central 对后三个源版本和目标版本的 POM 都返回 404。

因此公开 `Upgrade` 忠实实现清单要求，但生成的 `:15.6.5` **不能解析**。推荐 `Migrate` 会在精确版本/owner 节点留下 MARK，并在类型归属明确的 Java API 使用点留下迁移 MARK。它不会编造一个 Java 15.x API，也不会把 JVM 调用静默改成 Node/TypeScript 调用。合并前必须由清单负责人选择：

1. 若应用仍需 Java library，把清单目标纠正为实际发布版本（目前最高 `2.0.5`），然后重新建立版本白名单；
2. 若确实迁移到 npm `15.6.5`，将格式化能力移到 JS/Node 边界，删除 Maven 坐标，并用 golden corpus 验证输出；
3. 若由内部仓重新打包，明确新 group/artifact、来源、SBOM、许可证、维护与发布策略，不能冒用不存在的 Central 版本。

## 配方能力到规范的映射

| 不兼容/风险面 | 自动行为 | 级别 | 验证覆盖 |
|---|---|---:|---|
| 工作簿 4 个精确源版本 | Maven/Gradle owned literal 或安全局部 property 更新为 `15.6.5` | **AUTO** | 4 参数化 before→after、白名单等值锁定 |
| Maven target 不存在 | 解析为 `15.6.5` 时在 `<version>` 或真正 owner 节点 MARK，说明 Java/npm 仓库身份冲突 | **MARK** | literal、root property、profile override、推荐组合配方 |
| static/instance `format(...)` | 类型归属为 Vertical Blank 时 MARK；要求对格式化输出建立 golden corpus | **MARK** | static、formatter instance、StarRocks、SQLucky、DataCap fixtures |
| `SqlFormatter.of(...)` / `standard()` | MARK dialect/default 选择，不对枚举名做无依据映射 | **MARK** | MySQL、default、stored formatter/cache fixtures |
| `FormatConfig` builder | indent、column、uppercase、lines、params、parenthesis whitespace 调用精确 MARK | **MARK** | 7 个 builder 调用与 API boundary |
| `Formatter.extend(...)` | MARK 自定义 operator/dialect configuration；跨语言实现需设计 | **MARK** | type-attributed lambda extension |
| formatter 类型泄漏 | 变量、字段、返回值/参数含 library 类型时 MARK | **MARK** | public method 与 cached formatter |
| Maven root/profile/local DM/property | 仅处理当前 `pom.xml` 可证明 owner；profile local override 优先，root property 对直接 profile 可见 | **AUTO** | root/profile/DM/property before→after |
| 共享、重复或外部 property | 不修改；推荐入口在实际 dependency owner MARK | **NOOP/MARK** | plugin、other dependency、attribute、duplicate、unused |
| Gradle Groovy/Kotlin root DSL | 只处理 root `dependencies {}` 下已选 configuration 的 literal/string/map | **AUTO** | StarRocks Kotlin DSL、Groovy string/map |
| nested/select Gradle DSL | buildscript/subprojects/allprojects/project/constraints/custom/select 不碰 | **NOOP** | 7 个负例 |
| range/dynamic/catalog/BOM/缺 version | Upgrade 不猜；Migrate MARK owner | **NOOP/MARK** | range、`12.+`、`+`、latest、property、versionless |
| classifier/type/ext variant | 不升级并 MARK variant | **NOOP/MARK** | Maven classifier/type、Gradle classifier/@zip |
| 生成物/缓存 | 只根据父目录过滤；`target`、`generated*`、`install*`、缓存/报告/node 目录跳过 | **NOOP** | 16 个父目录；leaf `install.java/install.gradle` 正常处理 |

## Java 输出兼容性清单

若清单被纠正为跨生态迁移，必须把生产 SQL 样本固化为 before/after golden corpus，并逐 dialect 审核：

- 缩进字符、最大列宽、空行、CRLF/LF、statement delimiter 与多语句间距；
- keyword uppercase 对大小写敏感 identifier 的影响；
- 单/双/backtick/bracket quoting、escaped quote、Unicode 与 vendor-specific operator；
- line/block/hint comments 的位置，placeholder list/map 顺序与 `?`、`:name`、`@name` 行为；
- nested parentheses、CTE、window、JSON、array、MERGE、DDL/DML、stored procedure/自定义 delimiter；
- `SqlFormatter.of(Dialect)`、string dialect、`standard()` fallback 和未知 dialect 的失败策略；
- formatter 是否在日志/诊断/UI/持久化/hash/snapshot 路径使用；输出改变是否成为协议或审计差异。

Java `2.0.4` → `2.0.5` 的官方 diff只增加 opt-in `skipWhitespaceNearBlockParentheses`，默认值为 `false`；这并不能证明 npm 15.6.5 与 Java API 或输出兼容。

## 配方组成

完整名称均以 `com.huawei.clouds.openrewrite.sqlformatter.` 开头：

- `UpgradeSelectedSqlFormatterDependency`：工作簿白名单与 owner-safe Maven/Gradle AUTO；
- `FindSqlFormatter15SourceRisks`：type-attributed format/dialect/config/extension/API boundary MARK；
- `FindSqlFormatter15BuildRisks`：unpublished target、owner、dynamic/range、outside、variant MARK；
- YAML `UpgradeSqlFormatterTo15_6_5`：只含严格升级器；
- YAML `MigrateSqlFormatterTo15_6_5`：第一项显式复用公开 Upgrade，再运行 source/build risk recipes。

## 固定证据

### Java/Maven 官方仓

- [`vertical-blank/sql-formatter@2.0.4`，commit `1f533051`](https://github.com/vertical-blank/sql-formatter/tree/1f533051ea5e7075e6f4b0759c3c147b077ce77a)：工作簿中唯一实际存在的 Maven 源版本；
- [`2.0.5`，commit `937171e5`](https://github.com/vertical-blank/sql-formatter/tree/937171e5fb061617062fb7489694c1fdc4c96044)：Java 仓最后一个 tag/发布；
- [Maven Central metadata](https://repo1.maven.org/maven2/com/github/vertical-blank/sql-formatter/maven-metadata.xml) 与 [2.0.5 POM](https://repo1.maven.org/maven2/com/github/vertical-blank/sql-formatter/2.0.5/sql-formatter-2.0.5.pom)：固定实际 Maven 版本线；
- [2.0.4→2.0.5 diff](https://github.com/vertical-blank/sql-formatter/compare/2.0.4...2.0.5)：固定 Java API/默认行为依据。

### npm 同名项目（用于证明身份冲突，不作为 Java API）

- [`v3.1.0@64f18a8a`](https://github.com/sql-formatter-org/sql-formatter/tree/64f18a8a1032b0f7696099b01f2d0c9d91b79b06)、[`v12.0.6@1be6cb07`](https://github.com/sql-formatter-org/sql-formatter/tree/1be6cb07fddc24d3a54eb6624db9861e2dd604bb)、[`v12.2.0@2628fe62`](https://github.com/sql-formatter-org/sql-formatter/tree/2628fe6245f60922d1b9d5a18ba2317bf857effa)；
- [`v15.6.5@efee49f9`](https://github.com/sql-formatter-org/sql-formatter/tree/efee49f91f76e5d2ea356e628e959ac8cf0a7ddd) 与 [npm package page](https://www.npmjs.com/package/sql-formatter/v/15.6.5)：固定真正的 15.6.5 身份。

### 真实公开 Java 用例

| 固定仓库/提交 | 提取用例 | 本模块验证 |
|---|---|---|
| [StarRocks `aab21898`](https://github.com/StarRocks/starrocks/blob/aab21898c1cb0991e261dfb0bbf43f78969c0633/fe/fe-core/src/main/java/com/starrocks/http/action/QueryProfileAction.java) | cached `SqlFormatter.Formatter`、MySQL/Standard dialect；同 commit 同时有 Maven 与 Kotlin Gradle `2.0.4` 声明 | build before→after、stored type/dialect/output MARK |
| [automvc/bee-ext `6bfc6f2f`](https://github.com/automvc/bee-ext/blob/6bfc6f2f654adcb9b27b9b1d82bd3029f8a40825/src/main/java/org/teasoft/beex/spi/BeeSqlFormatter.java) | dialect-based formatter cache 与 `standard()` fallback | exact dialect/output/type MARK |
| [tenie/SQLucky `799e5c09`](https://github.com/tenie/SQLucky/blob/799e5c09b8f2b4842b3f5498ccce54f0079a3d60/sdk/src/main/java/net/tenie/Sqlucky/sdk/sql/SqlUtils.java) | UI text static `SqlFormatter.format` | static output MARK |
| [DataCap `e0d081d0`](https://github.com/devlive-community/datacap/blob/e0d081d01bb0297732ab2e8a4a5f192c061c6fb4/core/datacap-service/src/main/java/io/edurt/datacap/service/service/impl/FormatServiceImpl.java) | `of(Dialect.MySql).format` service boundary | dialect + output MARK |

测试结构参考 OpenRewrite [`UpgradeDependencyVersionTest@decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 的 before→after/no-op 风格，并额外锁定 local owner、路径、marker 与双周期幂等性。

## 运行

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-sql-formatter-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.sqlformatter.MigrateSqlFormatterTo15_6_5
```

推荐先在分支运行并提交 Rewrite data table、diff、依赖解析失败日志与 formatter golden corpus。当前目标未发布是预期门禁失败，不应通过增加任意第三方仓库或关闭 dependency verification 来掩盖。
