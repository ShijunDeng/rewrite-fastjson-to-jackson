# Flyway Core 升级到 11.14.1

本模块对应表格中的 `org.flywaydb:flyway-core`，覆盖 `5.2.1`、`7.1.1`、`7.11.1`、`7.15.0`、`7.8.2`、`8.5.13`、`9.16.3`、`9.19.4`、`9.20.0` 到 `11.14.1` 的升级。

默认使用仅迁依赖的窄配方：

```text
com.huawei.clouds.openrewrite.flyway.UpgradeFlywayCoreDependencyTo11_14_1
```

如项目直接声明了 Flyway Maven/Gradle 插件，可单独启用：

```text
com.huawei.clouds.openrewrite.flyway.UpgradeFlywayBuildPluginsTo11_14_1
```

组合配方会升级上述显式版本，并将官方在 10.0 删除的 properties/conf 键 `flyway.check.reportFilename` 改为 `flyway.reportFilename`：

```text
com.huawei.clouds.openrewrite.flyway.MigrateFlywayTo11_14_1
```

## 自动处理边界

- 升级 Maven 直接依赖、dependencyManagement、版本属性，以及有 Gradle 语义模型的直接依赖到 `11.14.1`。
- 不覆盖 Spring Boot/BOM 管理的无版本依赖；应先选择与 Spring Boot 版本匹配的 Flyway，再决定是否显式覆盖 `flyway.version`。
- 不把 `11.14.1` 或更高版本降级。
- 插件配方只升级显式版本的开源坐标 `org.flywaydb:flyway-maven-plugin` 与插件 ID `org.flywaydb.flyway`；不会添加缺失版本，也不会改变 Redgate edition 坐标或许可层级。
- Kotlin Gradle DSL 没有 `GradleProject` 语义模型时安全保持不变；实际执行建议通过 Gradle Tooling API 提供模型。
- 不根据 JDBC driver、URL 文本或 Spring 配置猜测并自动添加数据库模块。一个工程可能连接多个数据库，URL 也可能来自运行时 secret；错误推断会造成构建成功但运行期加载错误。
- 不自动修改 `cleanOnValidationError`、`cleanDisabled`、`baselineOnMigrate`、`repair`、schema history 或已执行 SQL。它们涉及数据与审计状态，必须人工决策。

## 10+ 数据库模块拆分

Flyway 10 将多种数据库支持从 `flyway-core` 拆出；只升级 core 对这些数据库不够，运行时常见错误是 `No Flyway database plugin found to handle jdbc:...`。以下名称来自官方 `flyway-11.14.1` tag 的实际模块和数据库文档，而非名称猜测：

| 数据库 | 11.14.1 开源模块 | 处理建议 |
| --- | --- | --- |
| PostgreSQL/CockroachDB | `org.flywaydb:flyway-database-postgresql:11.14.1` | API/Spring 项目加入运行 classpath；Gradle Flyway task 还要核对 build classpath |
| MySQL/MariaDB | `org.flywaydb:flyway-mysql:11.14.1` | 仍需对应 JDBC driver；MariaDB driver 3.x 与 MySQL URL 要核对 `permitMysqlScheme` |
| SQL Server | `org.flywaydb:flyway-sqlserver:11.14.1` | 同时核对 Microsoft JDBC driver、加密与 Entra/Kerberos 配置 |
| Oracle | `org.flywaydb:flyway-database-oracle:11.14.1` | 同时核对 `ojdbc11`、Wallet、SQL*Plus 与 edition 功能 |
| DB2、Derby、HSQLDB、Informix | `flyway-database-db2`、`flyway-database-derby`、`flyway-database-hsqldb`、`flyway-database-informix` | 每项都以对应官方 database support 页的 driver/版本为准 |
| Redshift、SAP HANA、Snowflake、Sybase ASE | `flyway-database-redshift`、`flyway-database-saphana`、`flyway-database-snowflake`、`flyway-database-sybasease` | 云 driver、认证、parser 与支持层级需分别验证 |
| Firebird、BigQuery、Spanner、SingleStore | `flyway-firebird`、`flyway-gcp-bigquery`、`flyway-gcp-spanner`、`flyway-singlestore` | 部分还需要额外 SDK/JDBC driver |
| H2、SQLite | 仍在 `flyway-core` | 不要虚构数据库模块；JDBC driver 仍由应用提供 |

官方 [PostgreSQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database)、[MySQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)、[SQL Server](https://documentation.red-gate.com/flyway/reference/database-driver-reference/sql-server-database) 和 [Oracle](https://documentation.red-gate.com/flyway/reference/database-driver-reference/oracle-database) 页面都明确说明 Java 用法中的独立模块。目标版本源码的完整模块目录见 [flyway-11.14.1 tag](https://github.com/flyway/flyway/tree/flyway-11.14.1/flyway-database)。

Spring Boot 项目还应遵循其 [Flyway 初始化说明](https://docs.spring.io/spring-boot/how-to/data-initialization.html)：让 Boot BOM 管理一组经过验证的版本，并为非内嵌数据库显式加入数据库模块。本模块因此不会给无版本的 Boot-managed `flyway-core` 强塞 `11.14.1`。

## 主要不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Java/构建工具基线提高 | `flyway-core:11.14.1` JAR manifest 的 `Build-Jdk-Spec` 为 17；构建、运行、容器和 CI 统一 JDK 17+。Flyway 10 起 Gradle 插件最低为 7.6；Maven/Gradle 插件也运行在 Java 17 上 |
| 7.x CLI/配置变化 | `-json` 改为 `-outputType=json`，`-logFile` 已删除并以 `-outputFile` 代替；无效 target、缺失 location 会更严格报错，placeholder 名变为大小写不敏感 |
| 7.x 移除旧 migration type | `SPRING_JDBC`/`UNDO_SPRING_JDBC` 改为 `JDBC`/`UNDO_JDBC`；自定义 resolver、历史表中旧类型和消费 JSON 输出的脚本都要验证 |
| 8.x 删除 7.x deprecated API | Android 支持删除；旧 JSON 字段/flag 删除；多个 `Configuration` boolean getter 改为 JavaBean 风格，extension API 由 `ApiExtension` 演进为 `ConfigurationExtension` |
| 9.x 安全默认值变化 | `cleanDisabled` 默认改为 `true`；不要为了让旧流水线通过而全局设成 `false`。旧 `ignore*Migration` 参数改为 `ignoreMigrationPatterns` |
| 9.x 执行与 SPI 变化 | Java/script migrations 不再在 dry run 中执行；`MigrationType`、resolver/context、baseline API 和 PostgreSQL locking 行为发生变化，自定义 resolver/plugin/callback 必须重新编译测试 |
| 10.x 数据库实现模块化 | PostgreSQL、DB2、Derby、HSQLDB、Informix、Redshift、SAP HANA、Snowflake、Sybase ASE 等从 core 拆出，后来还包括 MySQL/SQL Server/Oracle 等独立模块；API、Maven plugin 与 Gradle plugin 的 classpath 放置方式不同 |
| 10.0 配置/目录变化 | `flyway.check.reportFilename` 删除，改用 `flyway.reportFilename`；CLI/Docker 不再自带默认 `sql` 目录，必须显式检查 `locations`；`cherryPick` 和 license key 转为 extension 配置 |
| 10.x Java SPI 继续演进 | `ErrorCode` 从 enum 变成接口、基础 enum 为 `CoreErrorCode`；`FlywayMigrateException` 被拆到独立类；parser/createStatement、report/S3 helpers 等扩展点有移动 |
| 10.18/11.0 删除自动 clean | `cleanOnValidationError` 在 10.18 废弃、11.0 删除，配置存在会报错。正确做法是失败后人工检查并选择 migrate/repair/回滚，不是自动 clean |
| 10.20+ callback/check 变化 | `createSchema` callback 改为 `beforeCreateSchema`；旧 `check.url/password/username` 删除，应改为 environment 或标准连接参数 |
| 11.0 凭据输入变化 | CLI 不再交互式询问用户名/密码，改用环境变量或 secrets manager；不要把凭据写入仓库或命令历史 |
| Redgate artifact group 变化 | 商业版 11+ 不再发布 `org.flywaydb.enterprise`，改用 `com.redgate.flyway`；本配方只处理 OSS `org.flywaydb`，不会跨 edition 改坐标 |
| 11.x 插件/扩展变化 | `flyway.plugins.*` namespace 开始告警；`CommandExtension` 在 11.11 移除 telemetry 参数；`Location` 构造器在 11.13.2 废弃；11.14.0 将 `S3ClientFactory` 移至 `flyway-locations-s3` |
| 11.14.1 本身 | 该版本修复 JDBC URL 密码脱敏和根目录 JAR install-dir 崩溃，并更新 Snowflake driver；仍需逐数据库做 parser、driver 与迁移回归 |
| 版本/重复 migration 语义 | 9.x 以后 migration pattern 的版本按数字解释；repeatable、baseline、out-of-order、target 与 checksum 状态可能和旧流水线不同，必须在生产副本执行 `info`/`validate` |
| 日志/JSON 输出变化 | 多个大版本调整命令 JSON 字段、错误对象、输出文件和日志；所有解析 Flyway 输出的 CI 脚本都要使用录制样本回归 |

权威依据为 Redgate 官方 [5.x 至 11.14.1 release notes](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine)、[Flyway 9→10 指南](https://documentation.red-gate.com/fd/flyway-v10-upgrading-from-flyway-v9-224920031.html)、[Flyway 10→11 指南](https://documentation.red-gate.com/flyway/flyway-blog/flyway-v11-updating-from-v10)，以及目标 [Maven Central manifest/POM](https://central.sonatype.com/artifact/org.flywaydb/flyway-core/11.14.1)。

## Schema history、repair 与 clean 风险

- `flyway_schema_history` 是迁移审计记录，包含版本、状态和 checksum。升级前备份数据库与该表，记录旧版 `info -outputType=json` 和 `validate` 结果；升级后先对生产快照验证。
- 已在永久环境执行的 versioned migration 不应原地修改。官方建议新增 roll-forward migration；否则 checksum 不一致会破坏可重建性。
- `repair` 会删除失败记录、对齐 checksum/description/type，并把缺失 migration 标记为 deleted；它不会清理由失败 SQL 留下的用户对象，而且必须使用与 `migrate` 相同的 `locations`。必须经 DBA 审核并保存前后 diff。
- `clean` 会删除配置 schema 中的对象。9.x 起默认禁用是安全边界，生产环境不要为兼容旧脚本改成 `cleanDisabled=false`；测试库也要验证账号、schema 与 URL 后再启用。
- 不要在首次升级时同时执行依赖升级、`repair`、baseline、out-of-order 或 migration 文件重写。先 dry-run 构建变更，再独立演练数据库状态变化，方便定位 checksum/parser/driver 差异。

详见官方 [schema history](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table)、[versioned migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations)、[repair](https://documentation.red-gate.com/flyway/reference/commands/repair) 与 [cleanDisabled](https://documentation.red-gate.com/fd/flyway-clean-disabled-setting-277578981.html) 文档。

## Community、Teams 与 Enterprise

升级 OSS `org.flywaydb:flyway-core` 不会自动获得 Teams/Enterprise 功能。Undo、部分 secrets/auth、Oracle SQL*Plus、advanced check/compare、dry-run 等能力随版本和 edition 变化；升级前盘点实际命令、license 获取方式、离线 permit、私有 Redgate repository 与 EULA。不要把商业版依赖机械改为 OSS，也不要假设旧 license key 配置在 11.x 仍有效。

## 测试样本与覆盖

测试从真实项目结构缩减而来，并保留固定 commit 链接：

- [ninjaframework/ninja Maven dependencyManagement](https://github.com/ninjaframework/ninja/blob/f7b39ce103e547595585276857c3fc77d1e6f4f0/pom.xml#L713-L718)
- [Testcontainers Spring Boot quickstart Gradle managed dependency](https://github.com/testcontainers/testcontainers-java-spring-boot-quickstart/blob/c2fa0b2287a97026e153c9d9ee3aab5b7e96ba02/build.gradle)
- [Apache Gobblin Flyway fluent Java API](https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-metastore/src/main/java/org/apache/gobblin/metastore/DatabaseJobHistoryStore.java#L89-L96)
- [stitchfix/flotilla-os flyway.conf 安全配置](https://github.com/stitchfix/flotilla-os/blob/11568a7acfb10880744dccd48fba5e99db995b55/.migrations/dev.conf)
- Flyway 官方 [11.14.1 core source](https://github.com/flyway/flyway/tree/flyway-11.14.1/flyway-core) 与数据库模块

用例组织参考 OpenRewrite 官方 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)、[Maven UpgradePluginVersionTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-maven/src/test/java/org/openrewrite/maven/UpgradePluginVersionTest.java) 和 [Gradle UpgradePluginVersionTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-gradle/src/test/java/org/openrewrite/gradle/plugins/UpgradePluginVersionTest.java)。

当前测试覆盖表格全部 9 个起始版本、Maven 直接/属性/dependencyManagement、Groovy Gradle 字符串与 map notation、Kotlin DSL 无模型安全回退、Spring Boot/BOM 无版本 no-op、目标及更高版本防降级、数据库 companion 防误伤、Maven/Gradle plugin、配置键迁移、clean 安全配置保留、11.14.1 Java API 类型归因和 recipe discovery/validation。

## 使用与验证

先在本仓库安装模块，再在目标项目 dry-run：

```bash
mvn -f rewrite-flyway-core-upgrade/pom.xml clean verify

mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-flyway-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.flyway.UpgradeFlywayCoreDependencyTo11_14_1
```

审核 patch 并补齐明确的数据库模块后，在隔离的生产数据副本依次执行：JDK/构建验证、`info`、`validate`、空库全量 migrate、旧库增量 migrate、repeatable/out-of-order/baseline/callback/Java migration、并发锁、失败恢复、Spring Boot 启动和所有数据库方言集成测试。未经审核不要运行 `repair` 或 `clean`。
