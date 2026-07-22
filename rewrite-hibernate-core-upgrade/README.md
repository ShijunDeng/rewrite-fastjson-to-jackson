# Hibernate Core 迁移到 7.2.12.Final

本模块把表格中可确认的 Hibernate Core 旧版本迁到 `org.hibernate.orm:hibernate-core:7.2.12.Final`，并处理 Hibernate 5→6→7 跨代迁移中可机械证明安全的 Jakarta、源码和配置变化。不能从语法判断实体状态、数据库版本、查询语义或 SPI 实现意图的代码不会被猜测改写，而会由 `SearchResult` 精确标记。

推荐配方：

```text
com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12
```

## 表格版本边界

工作簿分别为 `org.hibernate:hibernate-core` 和 `org.hibernate.orm:hibernate-core` 列出了相同的可见源值：

```text
5.4.15.Final
5.4.24.Final
5.4.25.Final
5.4.28.Final
5.5.6
5.6.5.Final
5.6.7.Final
5.6.9.Final
5.6.14.Final
5.6.15.Final
```

其中最后一个工作簿单元格实际显示为 `5.6.9.Final ...（共13个版本）`，没有提供省略的其余 12 个字面版本。因此配方只把可证明的 `5.6.9.Final` 纳入集合，不推断隐藏版本，也不使用 `5.x`、`6.x` 或版本范围扩大选择面。表中 `org.hibernate.orm` 的 5.x 坐标在 Maven Central 并不存在，但配方仍按工作簿原文处理这类构建文件文本。

窄依赖配方：

```text
com.huawei.clouds.openrewrite.hibernate.UpgradeHibernateCoreDependencyTo7_2_12
```

它的边界如下：

| 声明 | 结果 |
| --- | --- |
| Maven 直接依赖或 `dependencyManagement`，版本是上述字面值 | groupId 统一为 `org.hibernate.orm`，版本改为 `7.2.12.Final` |
| Maven `${property}`，属性值是上述版本，且该属性的所有引用都只服务于匹配的 `hibernate-core` 声明 | 同时安全更新坐标和属性值 |
| Gradle Groovy/Kotlin 标准依赖 configuration 中的完整字符串字面量 | 更新旧/新 groupId 坐标和版本 |
| BOM 管理且无 `<version>`、共享属性、未解析属性、版本范围、Gradle 插值/目录别名 | 保持原样，不注入 override |
| 未列出的 5.x/6.x、目标版、7.2.12 之后版本、相似 artifact | 保持原样，不扩大升级、不降级 |

## 配方组成与自动修改

| 配方 | 自动处理 |
| --- | --- |
| `UpgradeHibernateCoreDependencyTo7_2_12` | 上述严格依赖选择与旧 groupId 迁移 |
| `MigrateJakartaPersistenceConfigurationTo3_2` | `javax.persistence.*` 设置名；`persistence.xml`/`orm.xml` 的 Jakarta namespace、3.2 XSD 和 version；`PersistenceProvider` service-loader 文件名 |
| `MigrateDeterministicHibernateSourceTo7` | `javax.persistence`→`jakarta.persistence`；Java 字符串中的同名前缀；两个官方明确的 descriptor contract 重命名；两参数 `Session.load`→同语义 `getReference`；`CascadeType.DELETE`→`REMOVE` |
| `FindManualHibernate7JavaMigrationRisks` | 标记 removed Session 操作、legacy Criteria、query transformer、查询、旧 annotation、方言、自定义类型和 API/SPI 扩展 |
| `FindManualHibernate7MappingAndConfigurationRisks` | 标记 hbm.xml、版本化方言、移除设置、DDL/缓存/增强/生成器/native mapping |
| `FindManualHibernate7BuildBaselineRisks` | 标记 Java <17、Envers/Spatial/JCache/processor/testing/community dialect、Spring/Quarkus/Hypersistence 等需对齐项 |

推荐配方还把显式 `javax.persistence:javax.persistence-api` 改为 `jakarta.persistence:jakarta.persistence-api:3.2.0`。这一步只处理显式依赖；使用平台/BOM 的工程应在 dry-run 后由平台统一管理 Jakarta 和 Hibernate companion 版本。

自动改写的依据来自固定官方源码：

- [Hibernate 6.0 migration guide：Jakarta 包/设置/XSD 与两个 descriptor contract 重命名](https://github.com/hibernate/hibernate-orm/blob/2560cc6638fa91e4dc5f57eeaa2e8877c3a5c637/migration-guide.adoc)
- [Hibernate 7.0 migration guide：`load` 与 `getReference` 同语义、Session/Cascade 删除项及行为变化](https://github.com/hibernate/hibernate-orm/blob/9e2de133bd210c35cbc581e327921f60bfba4409/migration-guide.adoc)
- [目标 7.2.12 的 `Session` API，固定提交 `ecd25d85`](https://github.com/hibernate/hibernate-orm/blob/ecd25d85a13df1921ec8da1a6318eab3e882054c/hibernate-core/src/main/java/org/hibernate/Session.java)
- [目标 7.2.12 migration guide](https://github.com/hibernate/hibernate-orm/blob/ecd25d85a13df1921ec8da1a6318eab3e882054c/migration-guide.adoc)

## 不自动猜测的不兼容点

| 跨代变化 | 为什么只标记，以及需要怎样处理 |
| --- | --- |
| Java/Jakarta 基线 | Hibernate 7 运行时基线是 Java 17，Jakarta Persistence 3.2；统一 compiler/toolchain、CI、容器和框架，不允许 JPA 2.x/3.2 混跑 |
| `Session.save/update/saveOrUpdate/delete` 删除 | `save` 有返回值，`update`/`saveOrUpdate` 要按 transient/detached 状态选 `persist` 或 `merge`，7.0 对 detached 和 cascade 更严格；逐调用点决定并做事务测试 |
| `Session.get` | 7.0 只把它 deprecated，目标 `Session` 仍保留；配方不会错误地把所有 `get` 改成 `find` |
| 三参数/对象形式 `load` | lock option 与“填充已有对象”形式没有简单等价签名；人工迁到 `getReference`/`find`/显式锁 |
| legacy Hibernate Criteria | 6.0 已移除；迁到 Jakarta Criteria、HQL 或 Hibernate Criteria 扩展，逐项验证 join/fetch/projection/subquery/pagination |
| HQL/SQM 和 native query | path 类型比较、association/literal、参数、implicit select、result class 和 transformer 行为变化；启动期编译 named query，集成测试 native result mapping |
| `ResultTransformer`/`setResultTransformer` | 按用途迁到 `TupleTransformer`、`ResultListTransformer`、DTO constructor 或 result-set mapping，不能只改方法名 |
| `JavaType`/`JdbcType`、`UserType`、`CompositeUserType`、`BasicType` | 名称改写只是第一步，6.0 重构了 basic type 组成，7.0 又改变 UserType/SPI 签名；重新实现并做数据库 round-trip |
| `EmptyInterceptor`、`Interceptor`、`Integrator`、metadata contributor | 7.0 移除旧类/重载并把 identifier 从 `Serializable` 演进为 `Object`；改为实现新接口和目标签名 |
| 版本化/旧数据库方言 | 6.2 引入最低数据库版本并把部分 legacy dialect 移到 `hibernate-community-dialects`；先确认真实数据库版本，不能机械地统一成通用 dialect |
| hbm.xml | 6.0 已 deprecated，多个 column、`return-join`、callable query 等行为变化；优先迁 annotation/orm.xml，保留时逐 XSD/feature 验证 |
| DDL、序列和类型映射 | BIGINT/count、UUID、Duration/Instant、array/JSON/timezone、optional one-to-one UNIQUE 等默认变化；生产库只应用审阅过的 schema diff |
| persist/flush/merge/detached 行为 | 7.0 对 detached association、refresh/lock 和 cascade 更符合 Jakarta 规范；覆盖 EntityExistsException、乐观锁、bulk DML 和回滚场景 |
| lazy/fetch/batch/enhancement | association laziness、fetch circularity、batch/subselect 和 enhancement plugin 行为跨版本变化；用 SQL 计数与 LazyInitializationException 场景验证 |
| 二级/查询缓存 | cache layout、key、provider 兼容性变化；对齐 provider，必要时清缓存，验证集群滚动升级 |
| 7.2 child Session | parent flush/close 会传播到共享事务上下文 child session，listener/JFR flush 事件也变化；检查封装与监控断言 |
| 7.2 offline bootstrap | `hibernate.boot.allow_jdbc_metadata_access=false` 时还要显式给 database product/version；否则 dialect capability 可能按最低版本推断 |
| testing | 7.2 `hibernate-testing` 使用 JUnit 6，`@Jpa` 多个 compliance 属性删除，bytecode enhanced engine 默认关闭且要求精确 JUnit 对齐 |
| companions | Envers、Spatial、JCache、Search、Validator、processor、Spring ORM/Boot、Quarkus、Hypersistence Utils、Liquibase Hibernate 必须分别核对支持矩阵 |

数据库最低版本的官方依据见固定的 [6.2 migration guide](https://github.com/hibernate/hibernate-orm/blob/4a987660fe4e05906841ea6cf1d1e11c1d557808/migration-guide.adoc)。目标版本对离线 metadata、child session、testing 和 SPI 的要求见上面的 `ecd25d85` 目标指南。

## 真实仓库测试样本

测试不是只用合成 happy path；以下 fixture 都保留了固定 commit 链接，并验证实际 before→after 或 `SearchResult`：

- Hibernate 官方 [ORM 5 test-case template `dd6d9afc`](https://github.com/hibernate/hibernate-test-case-templates/blob/dd6d9afc51df72b389f3113fd04379996cc4af43/orm/hibernate-orm-5/pom.xml)：隔离 Maven 属性和旧坐标会一起升级。
- [Quarkus application BOM `04011e13`](https://github.com/quarkusio/quarkus/blob/04011e13d6b1c34f5d9cddfb355dcc4c918a1d11/bom/application/pom.xml)：真实 `6.6.0.Final` 不在工作簿可见集合，严格 no-op。
- [OpenBoxes `5632415f` build.gradle](https://github.com/openboxes/openboxes/blob/5632415fba713129d81835c5b1498506091a1915/build.gradle)：Gradle 插值保持不变；[HibernateSessionService](https://github.com/openboxes/openboxes/blob/5632415fba713129d81835c5b1498506091a1915/grails-app/services/org/pih/warehouse/data/HibernateSessionService.groovy) 的 native query/transformer 被精确标记。
- [TEAMMATES `e8270607`](https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/build.gradle)：新 groupId 的真实 `6.4.4.Final` 未被表格选择，保持不变。
- [Dropwizard AbstractDAO `ee35db5b`](https://github.com/dropwizard/dropwizard/blob/ee35db5b4aeb07acf59f7aea53616222444d8372/dropwizard-hibernate/src/main/java/io/dropwizard/hibernate/AbstractDAO.java)：`saveOrUpdate` 只标记，不做不安全替换。
- [OneDev HibernateInterceptor `1d3f0ae6`](https://github.com/theonedev/onedev/blob/1d3f0ae62b73233970390fd4af5f836cdc4cdc82/server-core/src/main/java/io/onedev/server/persistence/HibernateInterceptor.java)：`EmptyInterceptor` 和旧 interceptor SPI 被标记。

测试结构参考 OpenRewrite 官方固定提交中的 [ChangeDependencyTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/ChangeDependencyTest.java)、[UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 与 [rewrite-hibernate Hibernate 7 tests](https://github.com/openrewrite/rewrite-hibernate/tree/48c9947a1790dbdcedeecb4d907d76bed837be95/src/test/java/org/openrewrite/hibernate)。当前模块有 18 个测试方法，覆盖两组坐标的 10 个可见版本、Maven 属性隔离、dependencyManagement、BOM/range/unresolved/shared-property 安全、Groovy/Kotlin literal、插值/catalog no-op、目标/后续版本、幂等、Java/XML/properties/YAML/service 路径、三个以上真实仓库 marker、build baseline 和全部配方发现/校验。

## 使用与验收

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-hibernate-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12
```

先审查 diff 和所有 `SearchResult`，再依次执行：JDK 17 编译、全部 named/HQL/native query、schema diff、真实数据库 round-trip、事务/锁/merge/cascade、lazy/fetch SQL 计数、缓存、enhancement、Spring/Quarkus 集成与生产数据回放。若项目依赖 Hibernate Platform/BOM，应让平台统一 core 与 companion，而不是给无版本依赖注入局部 override。

模块自身验证：

```bash
mvn -pl rewrite-hibernate-core-upgrade -am clean verify
```

## 许可证说明

OpenRewrite 官方 `rewrite-hibernate` 当前采用 Moderne Source Available License。本模块没有直接依赖或复制该 recipe pack；仅引用公开测试结构和上游行为作为验证依据，以维持本仓库现有依赖边界。组织若接受该许可证，可在本配方 dry-run 之外另行评估官方 pack。
