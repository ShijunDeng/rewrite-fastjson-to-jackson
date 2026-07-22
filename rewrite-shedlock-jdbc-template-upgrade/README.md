# ShedLock JdbcTemplate Provider 7.2.1 迁移配方

将 `net.javacrumbs.shedlock:shedlock-provider-jdbc-template` 的显式 Maven/Gradle
版本升级到表格目标 `7.2.1`。

```text
com.huawei.clouds.openrewrite.shedlockjdbc.UpgradeShedLockJdbcTemplateTo7_2_1
```

表格源版本为 `2.2.0`、`4.29.0`、`4.33.0`、`4.44.0`。底层
`UpgradeDependencyVersion` 采用语义版本升级规则，因此也会把其他低于目标的显式
版本升级到 7.2.1，但不会降级目标或更高版本。

## 自动处理范围

- Maven `dependencies`、`dependencyManagement`、版本属性及 Gradle Groovy 声明；
- 只定位完整的 provider groupId/artifactId，保留 scope、configuration、optional、
  classifier 和 exclusions；
- 共享的 `shedlock.version` 属性会整体更新，因而使用同一属性的 `shedlock-spring`
  也会同步变化；
- 不修改 ShedLock BOM、core、spring、plain JDBC/其他 provider、Java 配置、SQL schema、
  数据库行和应用配置。

## 不兼容修改与人工检查

### Java、Spring 与 Jakarta 基线

ShedLock 7.x 最低要求 Java 17。7.2.1 构建时使用 Spring 7.0.1，官方兼容矩阵还测试
Spring 6.2；实际应用通常需要 Spring Boot 3.4/3.5 或 4.x 的一致依赖管理。2.x/4.x
应用常见的 Java 8/11、Spring 5、Boot 2 组合不能只升级 provider。同步检查：

- compiler/toolchain、容器和 CI 的 Java 17；
- Spring Framework/JDBC/Tx、Boot、数据库 driver 和 Jakarta namespace；
- `shedlock-core`、`shedlock-spring`、provider 与 BOM 必须使用同一 ShedLock 版本。

本模块不自动升级这些邻接组件；共享版本属性被修改时要特别审查生成的整体 diff。

### SQL provider 7.0 统一与 API 移动

7.0 统一了 SQL 类 provider 的 db time、表/列名等能力；`DatabaseProduct` 移到
`net.javacrumbs.shedlock.provider.sql.DatabaseProduct`。直接 import 旧枚举或调用
显式数据库产品 builder 的源码需要迁移。JdbcTemplate provider 的
`withTimeZone(...)` 已标记待移除，目标版本应评估改为 `forceUtcTimeZone()`。

这些调用与具体数据库/时区约定有关，本配置配方不会机械替换。

### 数据库时间与时钟

官方强烈建议 builder 使用 `usingDbTime()`。它使用数据库 UTC 时间，并针对受支持
数据库采用专用 SQL 来减少并发 INSERT 冲突。未启用时使用应用节点时钟，多节点
时钟漂移可能让锁过早释放或延迟取得。迁移时必须在目标数据库验证生成 SQL；支持
范围包括 PostgreSQL、MySQL/MariaDB、SQL Server、Oracle、DB2、HSQL 和 H2，方言
行为不能在不同数据库间推断。

### 异常与竞争行为

7.x 对意外 provider 错误统一抛出 `LockException`，SQL INSERT 失败的判断比旧版本
更严格；MySQL/MariaDB 的 db-time `INSERT IGNORE` 是特例。检查所有包裹 provider
的 retry、catch、监控与告警，避免把数据库不可用、权限错误或 schema 错误误判为
“锁已被其他节点持有”。用真实隔离级别模拟并发启动、主从切换、死锁和短暂断连。

### 事务边界与连接池

JdbcTemplate provider 默认以独立事务执行锁操作。核对自定义
`PlatformTransactionManager`、`withIsolationLevel`、只读路由、连接池超时和
外层业务事务；锁获得后业务事务回滚并不等于锁记录自动回滚。确认连接池耗尽、
数据库 failover 和 transaction synchronization 时的行为。

### 表结构、精度与大小写

`name` 必须是主键，官方 schema 需要 `lock_until`、`locked_at`、`locked_by`。
检查 timestamp 精度/时区、列长度、保留字、schema 前缀以及
`withDbUpperCase(true)`/自定义 `ColumnNames`。ShedLock 会缓存已见过的 lock row，
运行中手工删除行后不会自动重建，除非重启；运维清锁应优先更新时间而不是删行。

### 锁语义与配套 Spring 模块

从 4.0 起 Spring 默认拦截模式是 `PROXY_METHOD`；从 6.0 起允许多个 LockProvider，
但必须通过 `@LockProviderToUse` 消除歧义。它们属于 `shedlock-spring`，本 provider
模块不会改写注解或 AOP 配置。还应验证 `lockAtMostFor`/`lockAtLeastFor`、长任务
续锁、节点崩溃、同名任务和手工触发。ShedLock 是“跳过重复执行”的分布式锁，
不是等待型队列或完整分布式调度器。

## 真实代码样本与依据

测试使用固定 commit，保留真实依赖声明形态：

- [shamilvasanov/Cards `8bff0c8b`](https://github.com/shamilvasanov/Cards/blob/8bff0c8b21d9a6bca2c03514fef0cb68b5547bb0/build.gradle)：Gradle 直接声明 2.2.0；
- [rieckpil/blog-tutorials `cc20cab5`](https://github.com/rieckpil/blog-tutorials/blob/cc20cab53eeb73c404b9bcc4a22b169571f4b403/spring-boot-shedlock/pom.xml)：共享属性声明 4.29.0；
- [alibaba/SREWorks `5eb36fa9`](https://github.com/alibaba/SREWorks/blob/5eb36fa9170fb737a06d9e690bc6df90a9924067/paas/appmanager/pom.xml)：dependencyManagement 中共享属性 4.33.0；
- [konturio/insights-api `c8252503`](https://github.com/konturio/insights-api/blob/c8252503d0adb699ffc300bc149fde51dffb5757/pom.xml)：共享属性 4.44.0，且旧镜像仍是 Java 16，说明必须人工升级 runtime。

官方依据固定在目标 tag：

- [7.2.1 README、JdbcTemplate 配置与兼容矩阵](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/README.md)
- [7.2.1 release notes](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/RELEASES.md)
- [目标 JdbcTemplateLockProvider 源码](https://github.com/lukas-krecan/ShedLock/blob/shedlock-parent-7.2.1/providers/jdbc/shedlock-provider-jdbc-template/src/main/java/net/javacrumbs/shedlock/provider/jdbctemplate/JdbcTemplateLockProvider.java)

## 测试与使用

```bash
mvn -f rewrite-shedlock-jdbc-template-upgrade/pom.xml clean verify
```

共 25 个测试，采用 OpenRewrite `RewriteTest` before/after 和 no-op 风格，覆盖表格全部源版本、
4 个固定提交真实样本、Maven/Gradle、属性与 dependencyManagement、元数据保留、
相邻模块隔离、幂等和不降级。应用 recipe 后应使用 Java 17 与生产等价数据库做
并发、异常、failover 和时钟测试，再部署到多节点环境。
