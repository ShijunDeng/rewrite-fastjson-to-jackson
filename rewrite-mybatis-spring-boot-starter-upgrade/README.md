# MyBatis Spring Boot Starter 4.0.0 迁移规格

本模块对应 `开源软件升级.xlsx` 中的 `org.mybatis.spring.boot:mybatis-spring-boot-starter`。推荐入口不仅修改版本，还迁移由目标源码能够证明等价的 Java、XML、YAML 和 properties 用法，并对必须由应用负责人决定的行为差异添加带原因的 `SearchResult`。

## 输入、输出与公开配方

| 项目 | 规格 |
| --- | --- |
| groupId / artifactId | `org.mybatis.spring.boot:mybatis-spring-boot-starter` |
| 表格中可见的精确源版本 | `1.1.1`、`1.3.2`、`2.0.0`、`2.1.2`、`2.1.3`、`2.1.4`、`2.2.0`、`2.2.2`、`2.3.0`、`2.3.1` |
| 目标版本 | `4.0.0`，不会追随更新的 `4.1.x` |
| 推荐迁移 | `com.huawei.clouds.openrewrite.mybatisspringboot.MigrateMyBatisSpringBootStarterTo4_0_0` |
| 仅严格升级依赖 | `com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4_0_0` |
| 兼容别名 | `com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4` |

工作簿单元格在前十个版本后以“共 12 个版本”折叠尾项，未提供另外两个精确值。本模块不根据公开版本历史猜测隐藏值；严格配方目前只接受上表十个可审计的精确值，待获得完整清单后再补齐。它支持 Maven 直接依赖、本地 `dependencyManagement` 和 Gradle 显式版本；本地 Maven 属性仅在全部引用都属于 Starter family 时升级，被项目元数据或其他组件共享时整文件 no-op。外部 BOM 管理的无版本依赖不被强行覆盖，未列出的 `2.3.2`、`3.0.4`、目标版本和更新版本均不降级。

当同一构建文件显式声明以下配套模块时，严格配方将它们一并对齐到 `4.0.0`，避免 Starter 与自动配置测试组件混用不同代际：

- `mybatis-spring-boot-autoconfigure`
- `mybatis-spring-boot-test-autoconfigure`
- `mybatis-spring-boot-starter-test`

## 目标平台基线

官方 4.0.0 要求 Java 17+、Spring Boot 4.0+ 和 MyBatis-Spring 4.0；其构建锁定 MyBatis 3.5.19。配方不会擅自升级 Spring Boot 父 POM/BOM、JDK、数据库驱动或整个 Jakarta 平台。Maven 中低于 Java 17 的明确编译属性、低于 Boot 4 的 parent/imported BOM 会被精准标记，必须先运行组织认可的平台迁移配方。

| Starter 代际 | 官方平台含义 | 本模块策略 |
| --- | --- | --- |
| 1.x | Spring Boot 1/早期 2 生态 | 跨越 Boot 2、3、4；仅迁移本模块能证明安全的用法，其余标记或留给平台配方 |
| 2.3.x | MyBatis-Spring 2.1、Boot 2.7、Java 8+ | 标记 Java/Boot/Jakarta、自动配置、Batch、测试及 AOT 风险 |
| 3.0.x | MyBatis-Spring 3、Boot 3.2–3.5、Java 17+ | 继续处理 Boot 4 包移动、测试基础设施和 MyBatis-Spring 4 / Batch 6 变化 |
| 4.0.0 | MyBatis-Spring 4、Boot 4.0+、Java 17+ | 本模块的固定目标，不自动升级到当前更新版本 |

固定依据为 MyBatis Starter 4.0.0 tag 对应的直接提交 [`756259338c8753228641de3a789fafb4a8b82e8d`](https://github.com/mybatis/spring-boot-starter/tree/756259338c8753228641de3a789fafb4a8b82e8d)；MyBatis-Spring 4.0.0 对应提交 [`6faf7f0b97de5b9ab549a470bd308edda140f03e`](https://github.com/mybatis/spring/tree/6faf7f0b97de5b9ab549a470bd308edda140f03e)。

## 不兼容点、处理状态与验证映射

| 不兼容点 / 目标行为 | 状态 | 配方行为 | 覆盖测试 |
| --- | --- | --- | --- |
| Java 17+、Spring Boot 4.0+ 基线 | 标记 | Maven Java 版本、Boot parent/BOM 低于目标时标记；不擅自改平台 | `marksMavenJavaAndSpringBootPlatformBlockersPrecisely`，含 Boot 4 / Java 17 负例 |
| Boot 3/4 的 Jakarta 迁移 | 标记 | 标记 `javax.persistence`、`validation`、`servlet`、非 XA `transaction` 类型；保留 Java SE 的 `javax.sql` 与 `javax.transaction.xa` | `marksJakartaEeRemnantsButNotJavaSeXa` |
| Boot 4 JDBC/Flyway/Liquibase 自动配置包移动 | 自动 | 对目标源码中实际发生的类移动执行类型安全 `ChangeType` | `movesBoot4JdbcAutoConfigurationType` |
| 自动配置只接受单一候选 `DataSource` | 标记 | 同一配置类存在多个 `@Bean DataSource` 时标记，要求显式绑定 mapper/session/transaction manager | `marksMultiDataSourceManualFactoryAndChangedPropertiesAccess` |
| 手工 `SqlSessionFactoryBean` 绕过 Starter 默认值 | 标记 | 标记构造点并提示核对 `SpringBootVFS`、mapper、插件、type handler 和数据源 | 同上 |
| `@MapperScan.value` 与 `basePackages` 成为 `@AliasFor` | 标记 | 同时提供两者时标记，不猜测应保留哪一个 | `marksMapperScanAliasAndSessionBoundaryConflicts` |
| mapper scan 同时指定 factory/template | 标记 | Java `@MapperScan` 和 XML `mybatis:scan` 均标记冲突 session 边界 | `marksMapperScanAliasAndSessionBoundaryConflicts`、`marksOnlyAmbiguousXmlMapperScan` |
| `MybatisProperties.configuration` 在 Starter 3 改为 `CoreConfiguration` | 标记 | 标记 `getConfiguration` / `setConfiguration` 调用，建议把运行期修改迁入 `ConfigurationCustomizer` | `marksMultiDataSourceManualFactoryAndChangedPropertiesAccess` |
| 废弃 `mybatis.configuration.default-scripting-language` | 自动 | properties/YAML 改为 `mybatis.default-scripting-language-driver` | 固定仓用例 `migratesStudentSysDefaultScriptingLanguageFromFixedCommit`、YAML 嵌套用例 |
| Velocity `userdirective` 废弃 | 自动 | 改为官方 metadata 指定的 `velocity-settings.runtime.custom_directives` | `migratesNestedYamlAndVelocityReplacement` |
| `config-location` 不能与 `configuration.*` 并用 | 标记 | properties 仅在两类键共同出现时双向标记；YAML 的 XML 模式入口标记 | `marksPropertiesXmlAndCoreConfigurationConflict`、`marksYamlAotAndConfigurationRisks` |
| `executor-type=BATCH` 改变 flush/事务边界 | 标记 | 仅对值为 `BATCH` 的 properties/YAML 标记，`SIMPLE` 不误报 | `marksPropertiesBehaviorRisksAndLeavesSafeExecutorUnmarked`、YAML 用例 |
| 自定义 VFS 覆盖 `SpringBootVFS` | 标记 | 精确标记 `mybatis.configuration.vfs-impl`，提示验证 fat/layered jar 与路径 | properties/YAML 风险用例 |
| `multiple-result-sets-enabled` 在 MyBatis 3.5.19 不再使用 | 标记 | 精确标记后由应用确认驱动行为再删除 | properties 风险用例 |
| 旧 `mybatis-spring-1.2.xsd` 文件名不再作为目标 schema | 自动 | XML schema location 归一化到稳定的 `mybatis-spring.xsd` | 固定仓用例 `normalizesEasyHousingMyBatisSpringSchemaFromFixedCommit` |
| MyBatis-Spring 4 使用 Spring Batch 6 infrastructure 包 | 自动 + 标记 | 自动迁移 `item`、`poller`、`repeat`、`support` 四组包；`@EnableBatchProcessing` 标记事务库/重启元数据审查 | `movesSpringBatchSixInfrastructureTypesAndMarksBatchConfiguration` |
| Native/AOT mapper 注入 | 标记 | 精确标记 `mybatis.inject-sql-session-on-mapper-scan=false`，该值恢复旧行为且不符合官方 native 路径 | properties/YAML AOT 用例 |
| Boot 4 移除 `@MockBean` | 自动 + 标记 | 无参数 `@MockBean` 自动迁为 `@MockitoBean`；带属性形式因契约不同保留并标记 | 固定仓用例 `migratesSpringbootMybatisMockBeansFromFixedCommitAndMarksJUnit4`、属性负例 |
| JUnit 4 runner 不属于 Boot 4 默认测试栈 | 标记 | 精确标记 `@RunWith`，不在未见 rules/lifecycle 时猜测迁法 | 固定仓用例同上 |
| Boot 4 `TestRestTemplate` 包与显式测试自动配置 | 自动 | 移到 `resttestclient` 包，并给使用它的 `@SpringBootTest` 添加 `@AutoConfigureTestRestTemplate` | `movesTestRestTemplateAndAddsBoot4AutoConfiguration` |
| 依赖版本必须严格命中工作簿 | 自动 / no-op | 十个可见精确版本逐项 precondition；两个被折叠版本不猜测；本地 managed、Gradle 和 family-owned 属性升级；共享属性、外部 BOM、未列出/目标/更新版本保持；配套模块和两周期幂等均验证 | `UpgradeMyBatisSpringBootStarterTest` 全部 10 个测试方法 |

## 真实公开仓回归样本

测试中的 before 片段保持真实仓结构，固定到不可漂移的 commit；after 或 marker 由本模块断言：

| 仓库与固定提交 | 原始文件 | 本模块断言 |
| --- | --- | --- |
| [`junzhu0510/student-sys@204b02c`](https://github.com/junzhu0510/student-sys/blob/204b02c7ce9c6d517d5b4aacf3cca006e76a5787/src/main/resources/application.properties) | `application.properties` 的旧 scripting language 键 | properties before → after |
| [`632team/EasyHousing@5362a94`](https://github.com/632team/EasyHousing/blob/5362a94acc5d792ece4e4b3afdb827e415b98cc9/config/bean.xml) | `mybatis-spring-1.2.xsd` | XML before → stable schema after |
| [`hiwattc/springboot-mybatis@7035eb2`](https://github.com/hiwattc/springboot-mybatis/blob/7035eb257fbfce7f063a5041d8ee14c2a4a98c1c/src/test/java/com/staroot/mybatis/controller/ApiUserControllerWebMvcTest.java) | mapper 的无参数 `@MockBean` 与 JUnit 4 runner | `@MockitoBean` after + `@RunWith` marker |
| [MyBatis 官方 Starter 4.0.0 Web sample](https://github.com/mybatis/spring-boot-starter/blob/756259338c8753228641de3a789fafb4a8b82e8d/mybatis-spring-boot-samples/mybatis-spring-boot-sample-web/src/test/java/sample/mybatis/web/SampleMybatisApplicationTest.java) | Boot 4 TestRestTemplate 方式 | 包移动和显式 auto-config after |

测试写法还对齐 OpenRewrite 自身固定版本用例：[`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)、[`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java) 和 [`ChangePropertyKeyTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-properties/src/test/java/org/openrewrite/properties/ChangePropertyKeyTest.java)。

## 使用与验收

先 dry-run 推荐配方：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-mybatis-spring-boot-starter-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.mybatisspringboot.MigrateMyBatisSpringBootStarterTo4_0_0
```

合并前必须逐个处理 `SearchResult`，然后用 Java 17+ 和 Boot 4 执行编译、Spring context、真实数据库事务/回滚、Batch 重启、mapper XML、fat jar/AOT/native 与测试切片回归。模块验收命令：

```bash
mvn -f rewrite-mybatis-spring-boot-starter-upgrade/pom.xml clean verify
```
