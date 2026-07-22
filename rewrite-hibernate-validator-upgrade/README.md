# Hibernate Validator 8.0.3.Final migration specification

本模块处理 `开源软件升级.xlsx` 中 `org.hibernate.validator:hibernate-validator` 的升级。表格给出的源版本是：

```text
6.0.23.Final
6.1.6.Final
6.1.7.Final
6.2.0.Final
6.2.1.Final
6.2.3.Final
6.2.4.Final
6.2.5.Final
```

目标版本固定为 `8.0.3.Final`。推荐入口为：

```text
com.huawei.clouds.openrewrite.hibernatevalidator.MigrateHibernateValidatorTo8_0_3
```

旧入口 `com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorTo8` 保留为兼容别名。只想执行严格依赖升级时，可使用 `com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorDependencyTo8_0_3`。

## 行为合同

`AUTO` 表示能够从语法和类型信息证明安全并自动修改；`MARK` 表示生成 `SearchResult`，必须由开发者判断；`NO-OP` 表示刻意不修改。

| 不兼容点/边界 | 状态 | 配方行为 | 主要测试 |
| --- | --- | --- | --- |
| 表格列出的 8 个 Hibernate Validator 版本 | AUTO | 单个确定性 Java 配方处理 Maven 项目/profile 的直接依赖和 `dependencyManagement`，以及 Gradle `dependencies {}` 内 Groovy/Kotlin DSL 的直接字符串；Groovy named/map notation 同样精确升级到 `8.0.3.Final` | `upgradesEverySpreadsheetMavenVersion`、`upgradesEverySpreadsheetManagedVersion`、`upgradesEverySpreadsheetGradleVersion`、`upgradesEverySpreadsheetKotlinGradleVersion`、`alignsGroovyNamedAndMapLiteralFamilyDeclarations` |
| `hibernate-validator-cdi` 与 annotation processor 版本对齐 | AUTO / NO-OP | 只有同一构建文件已精确选择白名单 core 版本时，才把实际存在且同样命中白名单的家族成员对齐；processor `<path>` 仅限 `maven-compiler-plugin/annotationProcessorPaths`。无 core、插件依赖、classifier/非 jar、任意 `<path>`、未列版本、目标版和新版不推断也不降级 | `alignsOnlyListedLiteralFamilyMembersWhenCoreIsSelected`、`alignsLiteralFamilyInKotlinGradle`、`companionWithoutSelectedCoreIsNoOp`、`doesNotDowngradeOrInferUnlistedCompanionVersions`、`preservesPluginDependenciesClassifiersNonJarArtifactsAndUnownedPaths` |
| 属性承载的版本 | AUTO / NO-OP | Maven root/profile 属性必须只定义一次，值精确命中白名单，并且全部 `${...}` 引用都位于 HV core/CDI/annotation processor 块中才自动修改；元数据/无关依赖共享、重复定义或父 POM 属性整文件 NO-OP | `upgradesAResolvedMavenVersionProperty`、`upgradesAnExclusiveProfilePropertyAndFamilyDeclarations`、`leavesAmbiguouslySharedVersionPropertyUntouched`、`leavesPropertyReferencedByProjectMetadataUntouched`、`leavesDuplicatePropertyDefinitionsUntouched` |
| 未列版本、目标版、新版、错误 groupId | NO-OP | 不做范围推断、不降级、不迁移 `org.hibernate:hibernate-validator` | `leavesUnlistedTargetAndNewerVersionsUntouched`、`leavesSameArtifactInLegacyGroupUntouched` |
| 外部 BOM 管理且无本地版本 | NO-OP | 不写入版本，也不覆盖平台决策 | `keepsExternalBomManagedVersionlessDependencyUntouched` |
| 动态/范围/生成输出中的构建声明 | NO-OP | Gradle 变量、字符串插值、版本范围、`dependencies {}` 外的同名方法，以及 `target`、`build`、`out`、`dist`、`generated`、`.gradle`、`.idea`、`node_modules` 下的副本保持不变 | `leavesDynamicGradleVersionExpressionUntouched`、`leavesGradleRangesAndKotlinInterpolationUntouched`、`doesNotTreatGroovyAndKotlinMethodsOutsideDependenciesAsDeclarations`、`ignoresGeneratedBuildDescriptors` |
| Bean Validation API 2.0 → Jakarta Validation 3.0 | AUTO | `javax.validation:validation-api` → `jakarta.validation:jakarta.validation-api:3.0.2`；属性承载的版本改为依赖内字面量，不改共享属性；Java 类型递归迁移到 `jakarta.validation` | `migratesValidationAndElDependenciesWithoutForcingExternalBomVersions`、`doesNotRepurposeAValidationApiVersionPropertySharedWithProjectMetadata`、`migratesValidationAndElJavaPackages` |
| EL API/实现进入 Jakarta EE 10 代际 | AUTO | `javax.el-api` → `jakarta.el-api:5.0.0`，GlassFish `javax.el`/`jakarta.el` 实现 → `org.glassfish.expressly:expressly:5.0.0`；支持 Maven 及直接字符串 Gradle 声明，Java 类型迁移到 `jakarta.el` | `migratesValidationAndElDependenciesWithoutForcingExternalBomVersions`、`migratesDirectGradleValidationAndExpresslyCoordinates`、`migratesDirectKotlinGradleElApiCoordinate`、`migratesValidationAndElJavaPackages` |
| Bean Validation `validation.xml` 2.0 | AUTO | 只修改 `validation-config` 根元素的 namespace、完整或相对 schemaLocation 和版本为 Jakarta 3.0 | `migratesSignalServerStyleValidationXmlWithRelativeSchemaLocation` |
| Constraint mapping XML 2.0 | AUTO | 只修改 `constraint-mappings` 根元素及标准 `javax.validation.*` constraint annotation；保留业务约束类 | `migratesConstraintMappingNamespaceSchemaVersionAndStandardAnnotation` |
| Validation service-loader 文件名 | AUTO | 精确重命名 `ValidationProvider`、`ConstraintValidator`、`ValueExtractor` 的 3 个标准描述符，内容保持不变 | `renamesValidationProviderServiceDescriptor`、`renamesConstraintValidatorServiceDescriptor`、`renamesValueExtractorServiceDescriptor` |
| `@SafeHtml` / `SafeHtmlDef` 已移除 | MARK | 标记类型使用；不猜测 sanitizer、白名单或输出编码策略 | `marksRemovedSafeHtmlInSuomenRiistakeskusStyleEntity`、`marksSafeHtmlFluentDefinitionAndScriptConstraints` |
| `@ScriptAssert` / `@ParameterScriptAssert` 依赖脚本引擎 | MARK | 标记约束使用，要求核对 JDK 11 运行时脚本引擎和安全边界 | `marksSafeHtmlFluentDefinitionAndScriptConstraints` |
| `GetterPropertySelectionStrategy#getGetterMethodNameCandidates` 返回值 `Set<String>` → `List<String>` | MARK | 类型感知搜索仅标记旧 `Set<String>` 实现，并给出顺序语义提示；目标 `List<String>` 不标记 | `marksChangedGetterStrategyContractFromWalmartConcord`、`leavesHibernateValidator8GetterStrategyContractUnmarked` |
| 6.2 起自定义 constraint violation 默认关闭 EL | MARK | 只在使用 `HibernateConstraintValidatorContext` 的 Java 文件中标记 `addExpressionVariable` 调用和 `${...}` 消息字面量 | `marksExpressionVariablesAndMessageElFromSanntranStyleValidator`、`customElAuditRequiresHibernateConstraintContextType` |
| `ValidationMessages*.properties` 中的 EL | MARK | 只标记消息 bundle 内 `${...}`，不扫描普通 `application.properties` | `marksOnlyValidationMessageBundlesContainingEl` |
| 已迁移 XML、稳定 HV 类型、相似服务名、注释和业务字符串 | NO-OP | 保持不变，防止误改文档文本、自定义 XML 和 `@URL` 等稳定约束 | `leavesJakartaValidationXmlAndOrdinaryXmlUntouched`、`apiAuditLeavesStableHibernateValidatorTypesUntouched`、`leavesSimilarServiceAndProviderImplementationTextUntouched`、`leavesOtherJavaxPackagesCommentsAndBusinessStringsUntouched` |
| JDK 11 最低运行要求 | MARK/MANUAL | README 明确阻断条件；配方不擅自修改 toolchain、基础镜像或生产 JVM | 迁移后检查 |
| Spring Boot 2、Java EE、旧 CDI/JPA/容器栈 | MANUAL | 不自动升级平台 BOM；必须与 Spring Boot 3/Jakarta EE 10 等平台迁移协同 | 迁移后检查 |

推荐入口组合了上述严格依赖升级、安全的 Java/build/XML/service 文件迁移以及全部风险搜索。OpenRewrite 搜索结果不是注释补丁；运行 `dryRun`/数据表后应逐项清零或记录接受理由。

## 版本与兼容性依据

- Hibernate Validator 7 完成 `javax.*` → `jakarta.*` 切换；本表源版本仍是 6.x，因此推荐配方一次完成该跨越。若输入依赖本身是未列出的 7.x，严格依赖配方会保持 NO-OP，但 Jakarta 源码/XML 迁移和 8.0 Getter SPI 风险搜索仍可独立使用。
- Hibernate Validator 6.2 移除了 `@SafeHtml`，并改变了自定义 violation 的 EL 默认行为。
- Hibernate Validator 8 面向 Jakarta EE 10、Jakarta Validation 3.0，目标构建最低 JDK 为 11；8.0 的 Getter SPI 候选集合改为有顺序的 `List<String>`。
- Java SE 应用若需要 EL，需要兼容 Jakarta EL 5 的实现；目标 Hibernate Validator 构建使用 Expressly 5.0.0。容器环境应先确认平台是否已提供实现，避免重复 provider。

固定官方证据：

- [Hibernate Validator 8.0.3.Final README/JDK 要求（固定提交 `40292e25`）](https://github.com/hibernate/hibernate-validator/blob/40292e2563a7cf2b3d927a694c5adc29cc0e3907/README.md)
- [Hibernate Validator 官方迁移指南](https://hibernate.org/validator/documentation/migration-guide/)
- [7.0.5 GetterPropertySelectionStrategy（Set，固定提交 `2afe6ad9`）](https://github.com/hibernate/hibernate-validator/blob/2afe6ad97e2907cf2821365336370b0791110315/engine/src/main/java/org/hibernate/validator/spi/properties/GetterPropertySelectionStrategy.java)
- [8.0.3 GetterPropertySelectionStrategy（List，固定提交 `40292e25`）](https://github.com/hibernate/hibernate-validator/blob/40292e2563a7cf2b3d927a694c5adc29cc0e3907/engine/src/main/java/org/hibernate/validator/spi/properties/GetterPropertySelectionStrategy.java)
- [8.0.3 target dependency definitions（固定提交 `40292e25`）](https://github.com/hibernate/hibernate-validator/blob/40292e2563a7cf2b3d927a694c5adc29cc0e3907/pom.xml)
- [Jakarta Bean Validation 3.0 specification](https://jakarta.ee/specifications/bean-validation/3.0/)

## 真实仓库回归语料

测试只保留触发行为所需的最小片段，出处固定到 commit，不依赖移动的默认分支。

| 仓库与固定提交 | 提取场景 | 验证结果 |
| --- | --- | --- |
| [dremio/dremio-oss@799ccbda](https://github.com/dremio/dremio-oss/blob/799ccbda47e6f2e1bfacf1ccbded174e00d4150a/pom.xml) | 共享属性管理 core、CDI 与 annotation processor | 家族版本一致升级 |
| [signalapp/Signal-Server@088037b4](https://github.com/signalapp/Signal-Server/blob/088037b4121621896931c0ee5beedb72fb74e5b9/service/src/main/resources/META-INF/validation.xml) | 使用相对 XSD 的 Validation 2.0 `validation.xml` | namespace/schema/version 精确迁移 |
| [suomenriistakeskus/oma-riista-web@f3550bce](https://github.com/suomenriistakeskus/oma-riista-web/blob/f3550bce98706dfe636232fb2765a44fa33f78ca/src/main/java/fi/riista/feature/news/News.java) | 实体字段使用已移除 `@SafeHtml` | 精确标记注解，不虚构替代实现 |
| [walmartlabs/concord@7fa56d81](https://github.com/walmartlabs/concord/blob/7fa56d815d28082cf5950f0e9688c385e8309757/server/impl/src/main/java/com/walmartlabs/concord/server/boot/validation/DefaultGetterPropertySelectionStrategy.java) | 自定义 Getter SPI 返回 `Set<String>` | 仅标记旧合同，提示顺序语义 |
| [sanntran/spring-boot-starter@9d45eb8e](https://github.com/sanntran/spring-boot-starter/blob/9d45eb8e6e371bb209260ce0366db1c3390b0b9e/spring-boot-rest-api/src/main/java/net/ionoff/service/validation/validator/AbstractValidator.java) | `HibernateConstraintValidatorContext#addExpressionVariable` | 标记 EL 依赖调用与 `${...}` 消息 |

## 使用

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-hibernate-validator-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.hibernatevalidator.MigrateHibernateValidatorTo8_0_3
```

应用补丁前先审查 `SearchResult`，然后执行：

```bash
rg 'javax\.(validation|el)|validation-api|xmlns\.jcp\.org/xml/ns/validation' src pom.xml build.gradle build.gradle.kts
rg 'SafeHtml|ScriptAssert|GetterPropertySelectionStrategy|addExpressionVariable|\$\{' src
```

迁移后至少验证：

1. 编译、测试与生产 JVM 都是 JDK 11+，容器/基础镜像一致。
2. Spring、CDI、JPA、Servlet、应用服务器和测试框架全部处于 Jakarta 代际，不混用 `javax.validation` 与 `jakarta.validation`。
3. 自定义 constraint、validator、value extractor、message interpolator、parameter name provider、traversable resolver 和 provider 能被加载。
4. `META-INF/validation.xml`、constraint mappings 和 `META-INF/services` 在打包产物中路径正确。
5. 自定义 violation 的 EL feature level 明确且不执行不可信输入；消息 bundle 做注入回归。
6. `@Email`、容器元素、方法参数/返回值、日期/数值、国际化消息及业务自定义约束做行为回归。

## 模块验证

本模块包含 84 个 JUnit 执行项（含参数化的全部表格版本）以及 OpenRewrite 多 cycle 幂等检查：

```bash
mvn -pl rewrite-hibernate-validator-upgrade -am clean verify
```
