# PostgreSQL JDBC 迁移到 42.7.13

本模块处理 `org.postgresql:postgresql` 到 `42.7.13` 的迁移。它不是只修改版本号的 YAML：公开低层配方严格更新工作簿指定的版本；推荐配方显式复用低层配方，再执行 Java 类型归属和构建所有权 visitor，把 URL/SSL、DataSource/XA、COPY、LOB、复制、批处理、可更新结果集和非公开 API 风险标到准确 AST 节点。

## 工作簿范围

对 `开源软件升级.xlsx` 的全部 worksheet、共享字符串和内联字符串做全量扫描，得到以下可见值：

| 工作表行 | 序号 | 精确源版本 | 目标版本 | 说明 |
|---:|---:|---:|---:|---|
| 422 | 421 | `1.17.6` | `42.7.13` | 不是该 Maven 坐标的 pgjdbc 发布号；仍忠实执行清单映射 |
| 423 | 422 | `14.8` | `42.7.13` | 不是该 Maven 坐标的 pgjdbc 发布号；可能是其他生态版本，但不擅自改坐标 |
| 3295 | 3294 | `42.2.19` | `42.7.13` | pgjdbc 固定版本 |
| 3296 | 3295 | `42.2.5` | `42.7.13` | pgjdbc 固定版本 |
| 3297 | 3296 | `42.5.1` | `42.7.13` | pgjdbc 固定版本 |
| 3298 | 3297 | `42.5.4` | `42.7.13` | pgjdbc 固定版本 |
| 3299 | 3298 | `42.5.5` | `42.7.13` | pgjdbc 固定版本 |
| 3300 | 3299 | `42.5.6` | `42.7.13` | pgjdbc 固定版本 |
| 3301 | 3300 | `42.6.0` | `42.7.13` | pgjdbc 固定版本 |
| 3302 | 3301 | `42.6.1 ...（共18个版本）` | `42.7.13` | 只采纳可见前缀 `42.6.1`；其余 17 个值在文件中不可恢复，绝不猜测 |

严格白名单因此是 `1.17.6`、`14.8`、`42.2.19`、`42.2.5`、`42.5.1`、`42.5.4`、`42.5.5`、`42.5.6`、`42.6.0`、`42.6.1`。`42.2.18`、`42.5.7`、`42.6.2`、`42.7.12` 等其他固定版本不会被自动升级。

## 配方

| 配方 | 定位 | 实际行为 |
|---|---|---|
| `com.huawei.clouds.openrewrite.postgresql.UpgradePostgresqlTo42_7_13` | 公开低层 `Upgrade` | 只改普通 jar 的精确白名单 Maven/根 Gradle 声明，不改源码、不猜外部 owner |
| `com.huawei.clouds.openrewrite.postgresql.MigratePostgresqlTo42_7_13` | 推荐入口 | 第一项复用公开 `Upgrade`，然后运行 source/build risk recipes |
| `UpgradeSelectedPostgresqlDependency` | 确定性 AUTO | Maven root/profile/local DM/独占 property 和根 Gradle literal/map 版本升级 |
| `FindPostgresql42SourceRisks` | 精确 MARK | 类型归属明确的 pgjdbc/JDBC API 与内部 import |
| `FindPostgresql42BuildRisks` | 精确 MARK | unresolved/outside owner、variant 和同一 POM 内 Java 7 或更低基线 |

运行推荐配方：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-postgresql-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.postgresql.MigratePostgresqlTo42_7_13
```

## 不兼容点：spec → recipe → test

| 不兼容点/风险 | 配方行为 | 精确节点 | 必须验证 |
|---|---|---|---|
| 工作簿白名单版本 | **AUTO** | Maven `<version>`/安全 local property；根 Gradle coordinate/map `version` | 实际解析为 `42.7.13`，lock/SBOM 同步 |
| 版本由 parent/BOM/catalog/platform/变量管理或缺失 | **MARK** | 实际 dependency owner/Gradle 参数 | 修改真正 owner，禁止局部假升级 |
| 范围、dynamic、非白名单固定版 | **NOOP/MARK** | 精确版本节点 | 单独评估路径，不扩大白名单 |
| classifier/type/ext | **NOOP/MARK** | variant dependency | 证明目标 variant 存在 |
| Java 7 或更低 | **MARK** | 当前含 pgjdbc consumer 的 POM 中 Java property | 42.7.13 最低 Java 8；TLS/GSS/服务加载回归 |
| `jdbc:postgresql:` URL | **MARK** | 精确 `DriverManager.getConnection`/driver 调用 | `sslmode`、证书、多 host、timeout、server type、autosave、query mode |
| `PGProperty` | **MARK** | 精确 `set/get` 调用 | SSL/GSS、timeout、prepared statement、binary、batch rewrite、autosave |
| `PGSimpleDataSource`/pool/XA | **MARK** | 构造与 setter 调用 | property 优先级、pool owner、XA autocommit/recovery、failover |
| COPY | **MARK** | `getCopyAPI` 与 `org.postgresql.copy.*` 调用 | text/binary、encoding、stream close/cancel、transaction、partial failure |
| Large Object | **MARK** | `getLargeObjectAPI`/LOB API 调用 | transaction、mode、seek/truncate、flush/reset、资源清理 |
| replication/notification | **MARK** | replication API 和 notification 调用 | slot、LSN ack、polling、keepalive、reconnect/failover |
| statement tuning/batch | **MARK** | `PGStatement` prepare/adaptive-fetch 调用 | prepare threshold、fetch、batch rewrite、generated key、取消、内存 |
| updatable ResultSet | **MARK** | 带 `CONCUR_UPDATABLE` 的 create/prepare 调用 | search_path、java.time、bytea、character stream、metadata、generated value |
| `PGConnection` extension | **MARK** | cancel/type/escape/preference 等精确调用 | unwrap/cast、transaction state、线程与生命周期 |
| `org.postgresql.core.*`、`jdbc.*`、`util.internal.*` | **MARK** | 精确 import | 换成 JDBC 或公开 pgjdbc extension API |

`MARK` 是可执行 OpenRewrite `SearchResult`，会直接出现在迁移 diff 中，并非只存在于本文档。

## 为什么没有伪造源码 AUTO

从工作簿选择的 42.2/42.5/42.6 线到 42.7.13，公开 JDBC 和 pgjdbc extension API 没有一组可依据语法唯一判断、且保持业务语义的统一重命名。自动把 `ssl=true` 改成某个 `sslmode`、替换 XA/pooling、修改 prepare threshold、batch rewrite、autosave 或 ResultSet concurrency 都可能改变安全、事务、一致性或性能。因此源码能力通过类型归属明确的 marker recipe 落到实际调用节点；确定性版本/owner-safe 更新仍由 AUTO 完成。

## 42.7 行为与回归重点

- 42.7.0 把连接会话 `DateStyle` 固定为 `ISO, MDY`；非默认服务端 DateStyle 和文本日期假设必须用真实查询回归。
- 42.7.4 及以后不再保证 PostgreSQL 9.1 之前服务端兼容；老数据库必须单独做握手、认证、metadata 和读写测试。
- 42.7.13 修复了可更新 ResultSet 的 `search_path`、`bytea toString`、`java.time`、character stream 与 metadata 行为。
- 42.7.13 包含 XA autocommit、native `CALL` comment、BigDecimal scale、协议 batching/sync、parameterless batch rewrite 修复。
- LOB flush/reset、SSL key autodetect/FIPS、GSS timeout、autosave、输入拒绝与 classloader/unload 生命周期均有目标线修复；命中对应 marker 的项目必须覆盖这些路径。
- 多 host/读写分离项目要覆盖 `targetServerType`、load balance、host recheck、failover、DNS、连接和 socket timeout。
- 性能基线要覆盖 server prepared statements、binary transfer、adaptive fetch、batch rewrite、large result、cancel 和 retry；不能只验证能连通。

## 构建所有权边界

### Maven

- 只处理文件名为 `pom.xml` 的 project 根 `dependencies`、`dependencyManagement` 和直接 profile 对应节点；plugin dependency 与形似 Maven 的其他 XML 不处理。
- root property 对 profile 可见；profile override 优先且不泄漏到 root/兄弟 profile。
- property 必须在 owner scope 唯一定义、值属于白名单、全部引用只服务普通 pgjdbc jar，才会 AUTO。
- 本地 DM 中白名单会升级；其 versionless consumer 保持 versionless。外部 BOM/parent 和无法证明的 owner 不猜。

### Gradle

- 只处理 `build.gradle`/`build.gradle.kts` 的根 `dependencies {}` 且 configuration 调用没有 select。
- 支持固定字符串坐标和 Groovy map；GString/Kotlin template/catalog/versionless 只 MARK。
- `buildscript`、`subprojects`、`allprojects`、`project(...)`、`constraints`、custom DSL、selected invocation、platform/BOM 不自动修改。

生成/安装/缓存过滤只看父目录组件，大小写不敏感；`generated*`、`install*`、`target`、`build`、`out`、`.gradle`、`.m2`、`node_modules`、报告和常见前端缓存均跳过。叶文件 `install.gradle`、`install.java` 仍属于项目源。

## 官方固定证据

- pgjdbc `42.2.5`：[`a1a5ae4f`](https://github.com/pgjdbc/pgjdbc/tree/a1a5ae4f2283d4557f36756d1a0228310a3acccb)
- pgjdbc `42.2.19`：[`207ce36e`](https://github.com/pgjdbc/pgjdbc/tree/207ce36ec2c5cce30c454afa3e51f39bbe5bd26b)
- pgjdbc `42.5.1`：[`9008dc9a`](https://github.com/pgjdbc/pgjdbc/tree/9008dc9aade6dbfe4efafcd6872ebc55f4699cf5)
- pgjdbc `42.5.4`：[`051ae1b7`](https://github.com/pgjdbc/pgjdbc/tree/051ae1b75a08a695ce1f96bcea7ce7ca49f798c9)
- pgjdbc `42.5.5`：[`475e3e2a`](https://github.com/pgjdbc/pgjdbc/tree/475e3e2af3033c666fc1c0015159b35455118ae5)
- pgjdbc `42.5.6`：[`b9953dc4`](https://github.com/pgjdbc/pgjdbc/tree/b9953dc45e1607ec1db292f59768f857b75386ef)
- pgjdbc `42.6.0`：[`d6a0cc2b`](https://github.com/pgjdbc/pgjdbc/tree/d6a0cc2babc5e5a4cef0a8eafd1b36198d8e3873)
- pgjdbc `42.6.1`：[`d368b1cc`](https://github.com/pgjdbc/pgjdbc/tree/d368b1cc5fbfc9750151f87d78e12db97e13af0e)
- pgjdbc `42.7.0` DateStyle 基线：[`1566eed0`](https://github.com/pgjdbc/pgjdbc/tree/1566eed0caeb26108f9df1d28255538767b7676f)
- pgjdbc `42.7.13`：[`3297557c`](https://github.com/pgjdbc/pgjdbc/tree/3297557c6a8059d0d6e3522c79f0bd9a6f82ee07)；[固定版本发布说明](https://jdbc.postgresql.org/changelogs/2026-07-06-42.7.13-release/)
- [官方下载/Java 与服务端支持矩阵](https://jdbc.postgresql.org/download/)
- OpenRewrite 测试参考：[`UpgradeDependencyVersionTest@decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)

## 真实公开仓夹具

- `42.2.19` Kotlin Gradle：[`k0kubun/gitstar-ranking@550a399d`](https://github.com/k0kubun/gitstar-ranking/blob/550a399d574f2171731b4056295d881c5edd5ba5/worker/build.gradle.kts)
- `42.5.6` Groovy Gradle：[`donalmun/GraduationBE@bfe66671`](https://github.com/donalmun/GraduationBE/blob/bfe66671501210425114142fa8b0827b505aefed/build.gradle)
- `42.6.1` Groovy Gradle：[`NASA-AMMOS/plandev@5c38ed67`](https://github.com/NASA-AMMOS/plandev/blob/5c38ed678074616d935d727f05f293f3cd5719e0/merlin-worker/build.gradle)

测试还覆盖 before→after、全部 10 个可见白名单、Maven root/profile/property/DM、Groovy/Kotlin root DSL、range/dynamic/catalog/variant NOOP+MARK、类型同名反例、精确 source marker、生成目录边界、公开 YAML 配方顺序和双周期幂等。当前独立 `clean verify` 执行 **87 项测试**。

```bash
mvn -f rewrite-postgresql-upgrade/pom.xml clean verify
```
