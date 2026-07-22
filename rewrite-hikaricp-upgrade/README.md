# HikariCP 迁移到 6.3.3

本模块对应 `开源软件升级.xlsx` 中的 `com.zaxxer:HikariCP`，精确处理 `3.3.0`、`3.4.5`、`4.0.3` 到 `6.3.3` 的升级。推荐配方不仅改版本，还迁移可以确定保持语义的 Credentials 源码和 Spring Hikari 配置，并为必须结合 JDBC driver、容器和业务策略判断的问题添加 `SearchResult`。

推荐的完整迁移配方：

```text
com.huawei.clouds.openrewrite.hikaricp.MigrateHikariCPTo6_3_3
```

仅依赖版本配方：

```text
com.huawei.clouds.openrewrite.hikaricp.UpgradeHikariCPTo6_3_3
```

## 自动迁移行为

完整配方执行以下确定性变换：

1. 单一确定性依赖 recipe 只把显式 `com.zaxxer:HikariCP` 主 jar 的 `3.3.0`、`3.4.5`、`4.0.3` 改为 `6.3.3`。支持 Maven 项目/profile 的直接依赖、根/profile 自有属性、`dependencyManagement`，以及 Gradle `dependencies {}` 中 Groovy 的直接字符串/Map notation 和 Kotlin DSL 字面量；无需依赖可能缺失的 GradleProject semantic marker。插件内部依赖、classifier 和非 jar 构件保持不变。
2. 把同一稳定 receiver 上相邻的 `setUsername(username)`、`setPassword(password)` 合并为 `setCredentials(Credentials.of(username, password))`，使新连接原子地读取凭据。只有 username 在前、password 紧邻在后、没有中间注释、receiver 是隐式 this/标识符/稳定字段链时才改写，不会重排有副作用表达式。
3. 如果业务类继承 `HikariDataSource`、覆盖 `getUsername()` 或 `getPassword()`，但没有覆盖 `getCredentials()`，则添加：

```java
@Override
public Credentials getCredentials() {
    return Credentials.of(getUsername(), getPassword());
}
```

这使 HikariCP 6 的 `PoolBase` 继续观察动态密码、IAM token 或代理 DataSource 的旧 getter 行为，不依赖临时系统属性。
4. 在已经存在 `spring.datasource.hikari.*` 配置、但未声明 keepalive 的 `application.properties`/结构化 Spring YAML 中添加 `keepalive-time=0`，显式保留 HikariCP 3.x/4.x “默认禁用”的行为。已经配置 keepalive 的工程保持不变。
5. 标记自定义 `SQLExceptionOverride`、缺少新方法的直接 `HikariConfigMXBean` 实现、匿名 MXBean、临时 legacy credential 系统属性，以及实际声明 HikariCP 的 Maven 模块中 Java 8/9/10 baseline。每个 marker 均附带具体风险原因；Java baseline marker 只落在 `pom.xml` 的精确属性值上，不会污染同一次运行中的无关模块。

版本升级采用来源白名单。`3.4.4`、`4.0.2`、`5.1.0` 等表格未列版本保持不变，`6.3.3` 和更高版本不会降级。Maven 属性只有在定义唯一且全部引用都属于 HikariCP 依赖时才修改；被项目元数据、其他组件共享或在多个 profile 重复定义时 no-op。Gradle/Kotlin 变量、插值、Map 变量、范围、动态版本和 version catalog 即使值看起来相同也不会仅凭无关字面量猜测归属。

`target`、`build`、`out`、`dist`、`generated`、`.gradle`、`.idea`、`node_modules` 等生成目录中的构建描述符、Java 源码和 baseline 元数据不会处理。Maven BOM 或 Gradle platform 管理的无版本依赖也不会被强行添加版本。

配方不会修改 Spring Boot/BOM/platform 托管的无版本依赖，也不会自动选择 JDBC driver、池大小、超时、异常驱逐、metrics backend、SLF4J provider、TCP keepalive 或生产 Java 镜像。

## 不兼容点、配方行为与验证

| 3.x/4.x 到 6.3.3 的变化 | 配方行为 | 业务验证 |
| --- | --- | --- |
| 目标制品由 Java 11 编译，3.x/4.0.3 可运行于 Java 8 | Maven `java.version`、`maven.compiler.release/source/target` 为 8/9/10 时加搜索标记，不擅自升级整个工程工具链 | 同步 Maven/Gradle daemon、CI、容器基础镜像和生产 JVM；Java 8 应停留在 4.0.3 |
| 6.0 使用原子的 `Credentials`，建连读取 `config.getCredentials()`；旧 `HikariDataSource#getUsername/getPassword` 子类覆盖会被绕过 | 自动补充 `getCredentials()` 兼容 override；检测 legacy 系统属性 | 对 IAM/短期 token、secret refresh 做并发轮换、过期 token、数据库重启和池补连接测试 |
| 分开设置 username/password 可能让并发建连观察到混合凭据 | 相邻且稳定的 setter 对合并为 `setCredentials(Credentials.of(...))` | 非相邻、反序、有分支或有副作用的写入保持不变，应在业务锁/刷新事务中人工原子化 |
| `HikariConfigMXBean` 新增抽象 `setCredentials(Credentials)` | 直接/匿名自定义实现添加搜索标记，不自动猜测其状态模型 | 实现真正的原子更新；确认运行期修改只影响新建连接，且 DataSource 与 Driver/JDBC URL 模式差异符合预期 |
| 6.2.1 把 `keepaliveTime` 默认值从 0 改为 120000ms；非零值至少 30 秒且小于 `maxLifetime` | 对明确的 Spring Hikari 配置写入 0 以保持旧默认；显式策略不覆盖 | 按 DB/LB/firewall idle timeout 决定是否删除 0 并启用；测量 `Connection.isValid()`/test query 成本。Hikari keepalive 不能替代 OS/driver TCP keepalive |
| 6.2.0 对 `SQLTimeoutException` 默认不再驱逐连接，并新增 `MUST_EVICT` | 所有自定义 `SQLExceptionOverride` 实现加带原因的标记 | 根据具体 driver 的 timeout 后连接状态决定 `MUST_EVICT`、`DO_NOT_EVICT` 或默认策略；覆盖 SQLState、errorCode、chained exception，防止坏连接复用或重连风暴 |
| 6.1.0 把 `maxLifetime` 随机负向衰减从 2.5% 提高到 25%，6.3.0 把 keepalive variance 从 10% 提高到 20% | 不改时长配置 | 告警与容量模型不能假设固定退休时点；做滚动连接退休、连接上限和故障注入测试 |
| 5.0 重写连接状态省略/恢复路径；6.0 调整重复 close、savepoint rollback、被驱逐连接关闭、network timeout shutdown、JDBC begin/end request | 无通用安全替换 | 回归数据库重启、网络分区、池降到 0 后恢复、重复 close、savepoint、unwrap、request boundaries 与优雅关闭 |
| 6.3.0 支持 duration 文本和数组属性，6.3.1 修复 key/value 强制字符串化 | 旧毫秒数字仍有效，配方不做无意义格式转换 | Spring/MicroProfile/自定义 binder 可能有不同转换；在真实配置入口验证 `ms/s/m/h/d` 和 driver 数组属性 |
| 4.0 引入 `keepaliveTime` 和 JPMS；6.3.2 恢复 6.3.1 丢失的 module-info，目标版模块名为 `com.zaxxer.hikari` | 不猜测模块声明 | 模块化应用验证 `requires com.zaxxer.hikari`、`java.sql`、`org.slf4j` 及 optional metrics module 可读性；同时测试 classpath/module-path |
| 目标 POM 使用 `slf4j-api:2.0.17`；应用可能仍由框架/BOM管理日志栈 | 不修改 SLF4J provider | 最终只能有一个兼容 provider；检查依赖收敛、容器 classloader、OSGi import 和启动告警 |
| Metrics/health 依赖仍为 optional；6.1 支持 Dropwizard Metrics 5，6.3.3 修复 metric registry 反射签名 | 不添加或升级监控库 | 实际调用 `setMetricRegistry()`，验证 acquisition/usage/creation/timeout/active/idle/pending；测试多池、同名 pool、关闭/重建和热部署的注册注销 |
| 配置启动后仍 sealed；JMX suspend 期间 `getConnection()` 不按 `connectionTimeout` 超时 | 不移动 setter、不开启 suspension | 运行期只通过支持的 MXBean 更新；运维暂停必须设置外部 deadline，防止请求永久挂起 |
| `minimumIdle` 默认等于 `maximumPoolSize`，只有前者更小时 `idleTimeout` 才缩池 | 不猜测容量 | 用数据库连接上限、请求并发和 acquisition latency 定池大小，不按 CPU 线程数盲目放大 |

## SearchResult 的处理

`dryRun` 中的 `/*~~(...)~~>*/` 或 XML `<!--~~>-->` 表示风险已被检测、但没有足够上下文安全决策：

- `SQLExceptionOverride`：明确 SQL timeout 是否必须驱逐，必要时返回 `MUST_EVICT`；
- `HikariConfigMXBean`：新增 `setCredentials(Credentials)` 并保证真正原子；
- `com.zaxxer.hikari.legacy.supportUserPassDataSourceOverride`：升级 DataSource 子类后删除临时兼容开关；
- Java 8/9/10 Maven 属性：确认构建和运行环境均为 Java 11+，不能只把 source/target 数字改大。

标记处理完仍需运行依赖树和真实启动测试。搜索配方无法发现 Docker/Kubernetes 镜像、远端 CI 模板、平台 BOM 或启动脚本中的所有 Java 版本权威。

## 真实 before/after 与 marker 测试

依赖声明样本固定到以下公开提交：

- [AxRinfinity/OOP @ 8b83655](https://github.com/AxRinfinity/OOP/blob/8b83655242269105df65aef7f1d4de117f1672cf/pom.xml)：Maven 直接 `3.4.5`；
- [Evolveum/midPoint @ f6a8e48](https://github.com/Evolveum/midpoint/blob/f6a8e48436f71690b7cdf38786951b6a562ad744/pom.xml)：`dependencyManagement` 的 `4.0.3`；
- [TechEmpower/Gemini @ fdf8d8a](https://github.com/TechEmpower/gemini/blob/fdf8d8a24c6d94f8f303d61804cd36f444d96f87/pom.xml)：共享 Maven 属性 `3.4.5`；
- [joaolucasl/wallet @ d5f3c4f](https://github.com/joaolucasl/wallet/blob/d5f3c4f2897db79b5e6bb08c5640fe098bacbddb/build.gradle)：旧 Gradle `compile` 的 `3.4.5`；
- [AtlasOfLivingAustralia/volunteer-portal @ afcc2b3](https://github.com/AtlasOfLivingAustralia/volunteer-portal/blob/afcc2b3cfd001a4d4de4e043b15582196c897aae/build.gradle.backup.txt)：旧 Gradle `runtime` 的 `4.0.3`；
- [4o4E/EOrm @ 5755ae3](https://github.com/4o4E/EOrm/blob/5755ae3941d481f9e32239bb4a488af35b3a441e/eorm-core/build.gradle.kts)：Kotlin DSL 中 `HikariCP:4.0.3` 的直接字面量在不依赖 Gradle semantic model 时升级，并保持其余依赖结构。

源码兼容用例采用真实生产缺陷及修复：

- [hortonworks/cloudbreak 修复前 @ 9e75d59](https://github.com/hortonworks/cloudbreak/blob/9e75d595c46134661c6adf36afda57f73b806a00/service-common/src/main/java/com/sequenceiq/cloudbreak/database/RdsIamAuthBasedHikariDataSource.java) 的 IAM DataSource 只覆盖 `getPassword()`；真实 [修复提交 92b69e5](https://github.com/hortonworks/cloudbreak/commit/92b69e5979521b36f4ebfddcfd0b4e9d694dae4c) 在 Spring Boot 静默升级到 HikariCP 6.3.3 后增加 `getCredentials()`。测试验证配方生成同等兼容桥接；
- [alibaba/SREWorks @ 5eb36fa](https://github.com/alibaba/SREWorks/blob/5eb36fa9170fb737a06d9e690bc6df90a9924067/paas/appmanager/tesla-appmanager-spring/src/main/java/com/alibaba/tesla/appmanager/spring/config/DatasourceExceptionConfig.java) 的自定义 `SQLExceptionOverride` 返回 `CONTINUE_EVICT`；测试验证精确标记 6.2 SQL timeout 行为变化，而不擅自改成 `MUST_EVICT`。

测试结构参考 OpenRewrite 官方固定提交的 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。当前 67 个执行场景覆盖三种来源版本、Maven/Groovy/Kotlin 直接/managed/profile/安全属性/Map 声明、两周期幂等、真实 before/after、Credentials 原子合并、动态子类、带原因风险 marker、properties/YAML keepalive、Java baseline，以及目标/更新/未列版本、共享/重复属性、BOM/platform、插件依赖/classifier/非 jar、Gradle 非依赖块同名调用、变量/插值/范围/动态/catalog、生成目录、相似坐标和已迁移源码 no-op。

## 官方依据

- [HikariCP 6.3.3 固定源码](https://github.com/brettwooldridge/HikariCP/tree/ea81bfb5852216dbfcb1f219742f91b5abceb81b)；
- [6.3.3 CHANGES](https://github.com/brettwooldridge/HikariCP/blob/ea81bfb5852216dbfcb1f219742f91b5abceb81b/CHANGES)：Credentials、SQL timeout、keepalive/maxLifetime variance、连接状态、metrics 和 module-info 变化；
- [6.3.3 README](https://github.com/brettwooldridge/HikariCP/blob/ea81bfb5852216dbfcb1f219742f91b5abceb81b/README.md)：Java 11、配置约束、TCP keepalive、池大小与 suspension；
- [6.3.3 POM](https://github.com/brettwooldridge/HikariCP/blob/ea81bfb5852216dbfcb1f219742f91b5abceb81b/pom.xml)：Java 11、SLF4J 2.0.17、optional metrics 和 OSGi 元数据；
- [目标 `PoolBase#getCredentials()`](https://github.com/brettwooldridge/HikariCP/blob/ea81bfb5852216dbfcb1f219742f91b5abceb81b/src/main/java/com/zaxxer/hikari/pool/PoolBase.java)：证明建连默认读取原子 Credentials，legacy getter 开关只是兼容路径。

## 使用与验证

```bash
mvn -f rewrite-hikaricp-upgrade/pom.xml clean install

mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-hikaricp-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.hikaricp.MigrateHikariCPTo6_3_3
```

审核 patch 和所有 SearchResult 后，运行 Java 11+ 全量编译、依赖收敛、unit/integration、真实应用启动、密码轮换、数据库重启、网络半开、池耗尽/恢复、超时驱逐、连接退休、优雅关闭、JMX、metrics/health、多池隔离和生产等价压力测试。检查 Docker/Kubernetes/CI 的实际 JVM，并配置 driver/OS TCP keepalive。

模块自身验证：

```bash
mvn -f rewrite-hikaricp-upgrade/pom.xml clean verify
```
