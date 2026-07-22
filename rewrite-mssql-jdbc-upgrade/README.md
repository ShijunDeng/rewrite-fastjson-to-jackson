# Microsoft SQL Server JDBC 13.2.1 迁移配方

将表格中列出的 `com.microsoft.sqlserver:mssql-jdbc` 版本统一升级到
`13.2.1.jre11`。配方只修改 Maven/Gradle 中已有的显式版本，不覆盖由 Spring
Boot、BOM 或父 POM 管理的无版本依赖，也不会自动改写连接串、认证信息或业务
SQL。

## 配方

```text
com.huawei.clouds.openrewrite.mssqljdbc.UpgradeMssqlJdbcTo13_2_1Jre11
```

覆盖的表格源版本：

- `7.2.2.jre8`
- `9.4.1.jre11`
- `10.2.1.jre8`
- `10.2.3.jre8`
- `10.2.3.jre17`
- `11.2.2.jre11`

目标制品后缀是 `jre11`，因此运行环境至少需要 Java 11。使用 `jre8` 源制品的
应用必须先完成 JDK 升级；配方不会修改 Maven Compiler、Toolchains、容器基础镜像
或 CI 的 Java 版本。

## 使用

将本模块放入 OpenRewrite recipe classpath 后激活：

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.mssqljdbc.UpgradeMssqlJdbcTo13_2_1Jre11
```

## 自动修改范围

- Maven `dependencies` 和 `dependencyManagement` 中已有的精确坐标。
- Maven 属性引用的版本，同时保留 scope、optional、classifier 和 exclusions。
- Gradle Groovy 字符串与 map 依赖写法，同时保留原 configuration。
- 只执行升级，不把 `13.2.1.jre11` 或更高版本降级。
- 不触碰 `mssql-jdbc-auth`、相似 groupId/artifactId 或无版本的平台托管依赖。

## 不兼容修改与人工检查

### Java 基线与制品变体

目标 `13.2.1.jre11` 需要 Java 11+。Microsoft 的 13.2 系列支持现代 JDK，但
`jre8`、`jre11` 等制品不是可以随意互换的标签。请同步检查运行 JDK、构建
Toolchain、应用服务器、容器镜像，以及与应用一起部署的 JDBC Driver 文件，避免
classpath 上同时保留旧驱动。

### TLS 与证书校验

从 10.2 起，`encrypt` 默认值变为 `true`。旧环境若依赖未加密连接、自签名证书、
短主机名或不完整证书链，升级后可能在首次连接时失败。逐项验证：

- `encrypt`、`trustServerCertificate`、`trustStore`、`trustStorePassword`；
- `hostNameInCertificate` 与证书 SAN/CN 是否匹配；
- SQL Server 的 TLS 版本、加密套件以及企业/FIPS 策略。

不要把 `trustServerCertificate=true` 当作长期修复；应优先部署可信证书与正确的
信任链。连接串包含安全策略和秘密，配方不会自动改写。

### TDS 8.0 与连接超时

11.2 为 TDS 8.0 引入 `encrypt=strict`，并增加 `serverCertificate` 配置；同一版本
还把默认 `loginTimeout` 从 15 秒改为 30 秒。依赖旧超时的故障转移、连接池健康
检查或启动探针需要重新测量。使用 `strict` 时应按 Microsoft 文档核对服务器与
证书要求。

### Microsoft Entra ID、托管身份与可选依赖

认证实现跨版本调整较多：旧的 AAD secret principal 属性已经删除或弃用，12.2
起 Managed Identity 转向 Azure Identity，MSAL 及 Azure/Key Vault/Bouncy Castle
依赖按认证方式和 Always Encrypted 场景变为可选组合。请为实际启用的认证模式
显式准备兼容依赖并做登录测试，包括：

- password、service principal、certificate、managed identity；
- access-token callback 与 token cache；
- Kerberos/NTLM 和随平台部署的 `mssql-jdbc_auth` 原生库。

配方不会升级原生认证 DLL，也不会猜测租户、客户端 ID、证书或密钥配置。

### SQL `vector` 类型

13.2 的重要行为变化是原生支持 SQL Server `vector`。旧驱动将该列作为 JSON
字符串暴露，13.2 默认返回原生 vector 表示。依赖字符串反序列化、结果集类型或
ORM 自定义映射的代码需要回归；需要短期保留旧行为时，可评估
`vectorTypeSupport=off`，再制定显式迁移方案。

### 存储过程、批量复制与数据类型

12.x 多个补丁对 CallableStatement 的直接存储过程调用做过调整和回退；13.2
还包含 generated keys、触发器和 metadata 修复。至少覆盖以下集成测试：

- 有/无返回值、输出参数、命名参数、临时表和多结果集的存储过程；
- 带触发器表的 `getGeneratedKeys()`；
- `SQLServerBulkCopy` 的 timestamp、datetime、money/decimal、null 和大对象；
- `DatabaseMetaData.getIndexInfo()`、`getFunctions()`、`getProcedures()`；
- prepared-statement cache、连接池、session recovery、XA 与故障切换。

### Always Encrypted 与 enclave

使用 Always Encrypted、secure enclave 或 Key Vault provider 的项目，应联合升级并
验证密钥存储 provider、attestation、驱动可选依赖、证书权限和 FIPS/TLS 设置。
这类配置与安全边界相关，不能仅凭依赖版本安全地自动转换。

### 新的会话属性

13.2 增加 `quotedIdentifier` 与 `concatNullYieldsNull` 等会话属性。若应用、初始化
SQL、ORM 或数据库触发器依赖特定 session 设置，应在所有连接池入口检查实际值，
避免连接复用导致行为差异。

## 测试

```bash
mvn -f rewrite-mssql-jdbc-upgrade/pom.xml clean verify
```

共 18 个测试，采用 OpenRewrite `RewriteTest` 的 before/after 与 no-op 风格，覆盖
表格全部源版本、Maven/Gradle、多种元数据保留、平台托管依赖、幂等/不降级和
recipe discovery。真实工程样本固定到不可变 commit，避免测试依据随默认分支漂移：

- [Apache Flink JDBC Connector `140f179d`](https://github.com/apache/flink-connector-jdbc/blob/140f179d019aba6a3f52e17d180c8d329ccdb8b6/flink-connector-jdbc-sqlserver/pom.xml)
- [USACE data-query `e106e507`](https://github.com/USACE/data-query/blob/e106e50751fd8f7e4c6b524468d3015058d5e678/pom.xml)
- [AbsaOSS inception `e752c4b0`](https://github.com/AbsaOSS/inception/blob/e752c4b0f1d9843b749a9389b58a0deb0f558f22/src/pom.xml)

## 官方迁移依据

- [Microsoft JDBC Driver release notes](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver)
- [Microsoft JDBC Driver system requirements](https://learn.microsoft.com/en-us/sql/connect/jdbc/system-requirements-for-the-jdbc-driver)
- [Using encryption with the JDBC driver](https://learn.microsoft.com/en-us/sql/connect/jdbc/using-ssl-encryption)

版本配方只能完成可确定的依赖声明修改。上线前必须用目标 SQL Server 版本、真实
认证方式和生产等价 TLS 配置执行集成与故障切换测试。
