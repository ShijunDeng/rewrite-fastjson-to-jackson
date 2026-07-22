# Hibernate Core upgrade to 7.2.12.Final

本模块对应表格中的旧/新两组坐标 `org.hibernate:hibernate-core` 与 `org.hibernate.orm:hibernate-core`，合并处理 `5.4.15.Final`、`5.4.24.Final`、`5.4.25.Final`、`5.4.28.Final`、`5.5.6`、`5.6.5.Final`、`5.6.7.Final`、`5.6.14.Final`、`5.6.15.Final` 以及 `5.6.9.Final …（共 13 个版本）`，目标为 `org.hibernate.orm:hibernate-core:7.2.12.Final`。

主配方：

```text
com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12
```

仅迁依赖的窄配方：

```text
com.huawei.clouds.openrewrite.hibernate.UpgradeHibernateCoreDependencyTo7_2_12
```

## 自动处理范围

窄配方处理 Maven 的旧 groupId、Maven/Gradle 直接依赖、Maven 版本属性和 dependencyManagement，并升级已使用 `org.hibernate.orm` 的旧版本；它不会把 7.2.12.Final 之后的版本降级。Gradle 旧 groupId 与插值版本需要由 Gradle Tooling 模型解析；没有语义模型时配方会安全地保持不变。

主配方在此基础上迁移 `javax.persistence-api` 到 Jakarta Persistence 3.2、Java 源码的 `javax.persistence` 包、常见版本化方言、`JavaTypeDescriptor`/`SqlTypeDescriptor`、Hibernate 7 删除的 Session `save/delete/get/load` 方法，以及 `CascadeType.DELETE`。

这些是高置信度机械修改，不代表覆盖全部 Hibernate 迁移。SQL/HQL、映射、DDL、缓存、SPI、自定义类型和数据库行为仍需人工处理。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Maven groupId 从 `org.hibernate` 迁到 `org.hibernate.orm` | core 及 Envers、spatial、jcache、processor 等同版本模块应统一新坐标；检查 BOM、plugin、annotationProcessor 与排除规则 |
| Hibernate 7 要求 Java 17、Jakarta Persistence 3.2 | 编译/运行/CI/toolchain 统一 JDK 17+；全工程及依赖完成 `javax.persistence`→`jakarta.persistence`，不能混用 JPA 2.x |
| Hibernate 6 的 JDBC 结果读取改为按位置 | 自定义 `UserType`、`BasicType`、`JdbcType`、native query scalar/result mapping 与存储过程映射需要重新验证 |
| 类型系统在 6.0 重构 | `JavaTypeDescriptor`→`JavaType`，SQL descriptor 演进为 `JdbcType`；自定义 basic/composite/collection type 和方言扩展需按新 SPI 重写 |
| `BIGINT`/`count()`、UUID、Duration、Instant 等默认映射变化 | 比较 schema、JDBC 类型与 Java 返回类型；修复强制 cast、native DTO 投影和历史列兼容性 |
| 标识符/序列命名和 implicit generator 默认变化 | 不允许 Hibernate 自动 DDL 直接作用生产库；生成 5.x/7.x schema diff，显式固定 sequence/table generator 名称和 allocationSize |
| legacy Hibernate Criteria API 被移除 | 迁到 Jakarta Criteria 或 HQL；验证 join、fetch、projection、subquery、pagination 和动态 predicate 语义 |
| HQL/SQM 语义更严格 | path 类型比较、association 与 literal 比较、ordinal parameter、implicit select、null comparison 会暴露旧查询；启动期编译全部 named query |
| Session `save/delete/get/load` 在 7.0 删除 | 配方迁到 `persist/remove/find/getReference`；返回值、transient/detached 状态、级联和异常语义不能按名称替换后直接假定等价 |
| 方言支持重新分层 | 版本化方言改为通用方言；社区/旧数据库方言移到 `hibernate-community-dialects`，确认最低数据库版本和 metadata 探测 |
| hbm.xml 多项旧能力被删除或废弃 | 优先迁到 annotations/orm.xml；若保留 hbm.xml，逐个验证多 column、return-join、callable/native query 与新版 XSD |
| association laziness、fetch circularity 与 batch fetching 行为变化 | 用 SQL 计数和序列化场景验证 N+1、LazyInitializationException、EntityGraph、join fetch、锁与 batch/subselect fetching |
| 6.2 的 optional one-to-one 会生成 UNIQUE constraint | 对现有重复数据和 schema migration 先做审计；UUID、enum、JSON、timezone/offset DDL 类型也可能变化 |
| 6.5/6.6 的 auto-flush、merge 与 query result type 校验变化 | 特别测试已删除行 merge、versioned entity、bulk DML、只读事务、native result class 和事务边界 |
| 7.0 的 persist/flush、refresh/lock detached entity 行为更严格 | 修复依赖旧隐式 reattach 的代码；明确使用 merge、managed entity 和事务内操作 |
| bytecode enhancement 与 proxy/lazy 策略演进 | 统一 Maven/Gradle enhancement plugin 版本，做 dirty tracking、lazy basic、association management 与 final class 测试 |
| 二级/查询缓存结构和 key 可能变化 | 升级缓存 provider，清空不兼容缓存，验证 query cache layout、集群滚动升级和序列化兼容 |
| 7.2 child Session flush/close 传播发生变化 | parent flush/close 会影响共享事务上下文的 child session；检查 listener、JFR 指标、StatelessSession 与多租户封装 |
| 7.2 testing 工具转向 JUnit 6 | 使用 `hibernate-testing`、`@Jpa` 或 bytecode-enhanced test engine 的项目需对齐精确测试依赖并显式开启 engine |
| companion integrations 必须匹配 Hibernate 7.2 | Spring Boot/Spring ORM、Quarkus、Envers、Search、Validator、Hypersistence Utils、Liquibase Hibernate 和 JDBC driver 均需单独核对支持矩阵 |

必须顺序阅读官方 [6.0 migration guide](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)、各 6.x 指南、[7.0 migration guide](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)、[7.1 guide](https://github.com/hibernate/hibernate-orm/blob/7.1/migration-guide.adoc) 与目标 [7.2 guide](https://github.com/hibernate/hibernate-orm/blob/7.2.12/migration-guide.adoc)。

## 测试样本来源

- Hibernate 官方 [ORM 5](https://github.com/hibernate/hibernate-test-case-templates/blob/dd6d9afc51df72b389f3113fd04379996cc4af43/orm/hibernate-orm-5/pom.xml) 与 [ORM 7](https://github.com/hibernate/hibernate-test-case-templates/blob/dd6d9afc51df72b389f3113fd04379996cc4af43/orm/hibernate-orm-7/pom.xml) test-case templates
- [Quarkus application BOM](https://github.com/quarkusio/quarkus/blob/04011e13d6b1c34f5d9cddfb355dcc4c918a1d11/bom/application/pom.xml) 的 managed property 与 exclusions 结构
- [OpenBoxes](https://github.com/openboxes/openboxes/blob/5632415fba713129d81835c5b1498506091a1915/build.gradle) 的 Gradle 版本变量和旧 groupId
- [TEAMMATES](https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/build.gradle) 的新 groupId Gradle 声明
- OpenRewrite 官方 [ChangeDependencyTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/ChangeDependencyTest.java)、[UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 以及 [Hibernate 7 test patterns](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/test/java/org/openrewrite/hibernate/MigrateToHibernate70Test.java)

测试覆盖两组 Maven 坐标、新坐标 Gradle 升级、Maven 属性、dependencyManagement、无语义模型时的 Gradle 旧坐标/插值安全回退、目标/后续版本 no-op、相似 artifact 防误伤、JPA 包和依赖迁移、Session 方法、方言、类型 descriptor 与 cascade 常量。

## 许可证说明

OpenRewrite 官方 `rewrite-hibernate` 从当前版本起使用 Moderne Source Available License。为了保持本仓库配方的 Apache 2.0 依赖边界，本模块未直接引入或复制该 recipe pack；上面的官方测试仅作为公开行为参考。若组织接受其许可证，可以在本配方 dry-run 之外单独评估官方 pack 的更广覆盖范围。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-hibernate-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12
```

确认 patch 后重建锁文件，并执行编译、全部 named/native queries、schema diff、数据库集成、事务/锁/缓存、lazy fetching、bytecode enhancement、Spring/Quarkus 集成和生产数据回放测试。

本模块自身验证：

```bash
mvn -pl rewrite-hibernate-core-upgrade -am clean verify
```
