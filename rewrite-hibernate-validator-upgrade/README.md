# Hibernate Validator upgrade to 8.0.3.Final

本模块对应 `开源软件升级.xlsx` 中的 `org.hibernate.validator:hibernate-validator`，处理 6.0/6.1/6.2 以及表格列出的 7.0.x 版本，目标为 `8.0.3.Final`。

配方名称：

```text
com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorTo8
```

## 自动处理范围

声明式配方组合完成三项修改：

1. 将 Maven/Gradle 的 `org.hibernate.validator:hibernate-validator` 升级到 `8.0.3.Final`。
2. 将显式 `javax.validation:validation-api` 依赖改为 `jakarta.validation:jakarta.validation-api:3.0.2`。
3. 递归迁移 Java 源码中的 `javax.validation` 包、import、注解和限定类型到 `jakarta.validation`。

## 不兼容修改点

| 不兼容点 | 影响与迁移建议 |
| --- | --- |
| Hibernate Validator 7 起实现 Jakarta Bean Validation 3.0 | 所有 `javax.validation.*` 改为 `jakarta.validation.*`；框架、容器、测试工具和自定义扩展必须使用 Jakarta 版本，不能混用两套 API |
| 目标版本最低要求 JDK 11 | 编译、测试和生产运行时统一到 JDK 11+；检查基础镜像、Maven/Gradle toolchain 和字节码目标 |
| Bean Validation XML namespace/version 变化 | `validation.xml` 和 constraint mapping XML 迁移到 Jakarta 3.0 namespace/schema；配置型 Java 包迁移不会自动判断自定义 XML 的合并语义，必须单独校验 |
| `@SafeHtml` 在 7.0 中移除 | 使用专门的 HTML sanitizer；不要把输入校验等同于 XSS 清洗 |
| 自定义 constraint violation 默认禁用 Expression Language | 依赖 `${...}` 插值的自定义消息需显式选择安全的 EL feature level，并防止用户输入注入表达式 |
| Java SE 环境的 EL 实现需要显式提供 | 按运行环境添加兼容的 `org.glassfish.expressly:expressly`；Jakarta EE 容器通常由平台提供，避免重复实现冲突 |
| CDI、JPA、Spring 等生态必须同步使用 Jakarta 版本 | Spring Boot 2 / Java EE 旧栈不能只升级 Validator；应与 Spring Boot 3、Jakarta EE 10 等平台升级协同 |
| SPI 与自定义扩展包名变化 | 更新 `ConstraintValidator`、`ValueExtractor`、`MessageInterpolator`、`TraversableResolver`、`ParameterNameProvider`、CDI extension 及 service loader 配置 |
| `hibernate-validator-cdi`、annotation processor 等伴随 artifact 必须对齐 | 若项目显式使用这些模块，将它们同步到 `8.0.3.Final`，并检查 annotation processor path |
| 校验消息、内置约束和边界 bug 修复可能改变结果 | 对 `@Email`、日期/数值、容器元素、方法参数/返回值、国际化消息以及巴西文档号等约束执行回归测试 |
| 8.x 更新到 Jakarta EE 10 API（EL 5、CDI 4、Persistence 3.1 等） | 排除项、provided scope、应用服务器模块和 OSGi/JPMS 元数据需要重新审计 |

官方依据：[Hibernate Validator 8.0.3 README 与系统要求](https://github.com/hibernate/hibernate-validator/blob/8.0.3.Final/README.md)、[8.x/7.x changelog](https://github.com/hibernate/hibernate-validator/blob/8.0.3.Final/changelog.txt)、[Jakarta Bean Validation 3.0 specification](https://jakarta.ee/specifications/bean-validation/3.0/)。

## 迁移后检查

```bash
rg 'javax\.validation|validation-api|xmlns\.jcp\.org/xml/ns/validation' src pom.xml build.gradle*
```

同时检查：

- `META-INF/validation.xml`
- constraint mapping XML
- `META-INF/services` 中的 Validation SPI 实现
- Spring/Quarkus/Jakarta EE 平台 BOM 与应用服务器版本

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-hibernate-validator-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorTo8
```

本模块自身验证：

```bash
mvn -pl rewrite-hibernate-validator-upgrade -am clean verify
```
