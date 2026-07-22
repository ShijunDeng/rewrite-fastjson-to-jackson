# JetBrains Annotations 升级到 26.0.2-1

本模块对应表格中的 `org.jetbrains:annotations`，覆盖 `23.0.0`、`23.1.0`、`24.0.0`、`24.0.1` 到 `26.0.2-1` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.jetbrainsannotations.UpgradeJetBrainsAnnotationsTo26_0_2_1
```

目标版本的 `-1` 是 JetBrains 发布版本的一部分，不是本项目添加的 classifier。配方内部使用 `26.0.2.x` 选择器和固定的 `-1` 元数据模式；Maven Central 当前唯一匹配项就是 `26.0.2-1`。这样可以避免把纯数字后缀误解释为 Node SemVer 的区间，同时不会选择普通的 `26.0.2` 或将来的其他版本。

## 自动处理边界

- 升级 Maven 直接依赖、`dependencyManagement`、活动 profile 和版本属性中的显式 `org.jetbrains:annotations` 版本。
- 升级带 Gradle Tooling API 语义模型的 Groovy DSL 字符串、map notation 和版本变量；保留 `compileOnly`、`testCompileOnly`、`api`、`implementation` 等 configuration。
- 保留 Maven 的 `provided`/`test` scope、`optional`、classifier 和 exclusions；不会把仅编译期注解变为运行时依赖。
- 不给 BOM/平台管理的无版本依赖强行添加版本，`overrideManagedVersion` 为 `false`。应在平台定义处升级，避免同一工程出现两个版本来源。
- 不将 `26.0.2-1`、普通 `26.0.2` 或语义上更高的版本降级。
- 不修改 `annotations-java5`、`annotations-*` Kotlin/Native 平台模块、旧 `com.intellij:annotations`、AndroidX/Checker Framework/SpotBugs 等相似注解坐标。
- 不自动增加、删除或重写 Java/Kotlin 注解。特别是不会擅自应用 `@NotNullByDefault`，因为它会递归改变字段、参数、返回值、泛型参数上界和数组元素的空值契约。
- 没有 `GradleProject` 语义模型时，Kotlin DSL 和需要查询 Maven metadata 的版本选择会安全保持不变。实际运行应使用 OpenRewrite Gradle 插件、Maven 插件解析 Gradle 工程，或其他能提供 Tooling API 模型的执行方式；纯文本扫描不足以可靠处理变量、平台和仓库。

## 主要不兼容修改点与工具链影响

| 版本变化 | 影响与迁移建议 |
| --- | --- |
| 23.0.0 | 新增 `@BlockingExecutor`、`@NonBlockingExecutor`。这些契约可被 IDE/静态分析用于检查阻塞调用，升级依赖本身不会自动标注线程池或调度器 |
| 23.1.0 | 新增 `@ApiStatus.Obsolete`。工具可能开始对带此注解的 API 给出更明确的过时提示；不要把它与 Java `@Deprecated` 或 `@ScheduledForRemoval` 混为一谈 |
| 24.0.0 | 新增实验性的 `@CheckReturnValue`。它可标在方法、构造器、类型和 package 上，静态分析可能开始报告忽略返回值的问题 |
| 24.0.1 | 为 `Language`、`MagicConstant`、`Pattern`、`PrintFormat`、`RegExp`、`CheckReturnValue` 等补充 `@Documented`。这会改变生成 Javadoc 中的可见元数据，即使业务字节码调用不变 |
| 24.1.0 | `@CheckReturnValue` 去掉 `@ApiStatus.Experimental`，成为稳定契约。若质量门禁将新告警视为错误，需要先记录升级前后 IDE、Qodana、Error Prone、Sonar 或自研检查器结果 |
| 25.0.0 | 发布结构改为 Kotlin Multiplatform，并停止更新 Java 5 artifact。`org.jetbrains:annotations` 仍向 Maven/JVM 使用者提供普通 JAR，但 Gradle module metadata 还公开 Apple、Linux、Windows、JS/Wasm 等平台 variant |
| 26.0.0 | 新增实验性的 `@NotNullByDefault`。它作用于类或 package，并递归影响泛型、数组和隐式 `Object` 上界；覆盖父类方法时还需保持空值协变/逆变契约。本配方不会自动添加它 |
| 26.0.1 | 修复 25.0.0 后的 sources JAR 构建。若源码下载、Javadoc 或 source attachment 流水线曾针对旧布局做 workaround，应清理并重新验证 |
| 26.0.2 | 修复 Apple artifacts 缺失的 klib。Kotlin Multiplatform 项目应清理 Gradle 缓存，并分别解析真实目标而不是只验证 JVM |
| 26.0.2-1 | JetBrains 在移除旧 Sonatype 发布方式、验证新 Maven Central 发布任务时把版本改为 `26.0.2-1`；相对 `26.0.2` 的注解源码只有 `@ApiStatus.OverrideOnly` 文档澄清，未引入新的 Java API 删除 |

上述版本事实来自 JetBrains 官方 [CHANGELOG](https://github.com/JetBrains/java-annotations/blob/26.0.2-1/CHANGELOG.md)、[目标 tag](https://github.com/JetBrains/java-annotations/tree/26.0.2-1)、[`26.0.2...26.0.2-1` 比较](https://github.com/JetBrains/java-annotations/compare/26.0.2...26.0.2-1) 和 [发布版本提交](https://github.com/JetBrains/java-annotations/commit/21116ccb03cfc35415188fc522b67ba67c2a98d8)。官方贡献规范要求注解演进保持向后兼容；即便没有二进制 API 删除，静态分析结果、生成文档和依赖解析仍可能变化。

## Java、JPMS 与 Multi-Release JAR

- 官方 README 明确 `annotations` 要求 JDK 8+。目标 JAR 的普通类是 Java 8 字节码（major version 52）；仍在 JDK 5、6、7 上构建的工程不能使用本目标。旧 `annotations-java5` 的最后版本是 `24.1.0`，且本配方不会把它改成不存在的 26.x。
- 目标是 Multi-Release JAR，manifest 含 `Multi-Release: true`，Java 9 目录下提供正式模块 `org.jetbrains.annotations`。模块导出 `org.jetbrains.annotations` 和 `org.intellij.lang.annotations`，并有 `requires static java.desktop`。
- 在 JPMS 工程中继续使用 `requires static org.jetbrains.annotations;`，以符合仅编译期依赖的常见用法。升级后执行 `jdeps`、模块路径编译和 `jlink`；若同时存在旧 `com.intellij:annotations`、IDE SDK 内嵌注解或重新打包 JAR，检查 split package 和 duplicate class。
- 不要因为 JAR 包含模块描述符就把 Maven `provided` 或 Gradle `compileOnly` 改为 runtime。大多数 JetBrains 契约由编译器、IDE或静态分析读取；真正需要运行时反射的自定义流程，应按所使用注解的 `Retention` 单独验证。

目标结构可由 JetBrains 官方 [README](https://github.com/JetBrains/java-annotations/blob/26.0.2-1/README.md)、[build.gradle.kts](https://github.com/JetBrains/java-annotations/blob/26.0.2-1/build.gradle.kts) 和 [Maven Central artifact](https://repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2-1/) 核对。

## Kotlin、Nullability 与静态分析检查

- 依赖升级不会把 Java 的 `@NotNull`/`@Nullable` 转为 Kotlin 类型，也不会处理 JSpecify、JSR-305、Checker Framework 或 AndroidX annotation。一个工程混用多套 nullability 模型时，应明确编译器和检查器的优先级。
- Kotlin 编译器、IDE、K2 前端和第三方分析器对外部 Java 注解的增强规则并不完全等价。对平台类型、override、泛型 variance、数组元素、SAM、反射和 Java/Kotlin 互调建立编译样本。
- `@NotNullByDefault` 目前仍是实验 API。若计划采用，应由单独变更在 package/class 边界渐进引入，并使用 `@Nullable` 或 `@UnknownNullability` 显式恢复例外；不要与依赖升级放在同一机械提交中。
- `@CheckReturnValue` 稳定化后，构造器、fluent API、不可变集合和纯函数更可能出现新告警。先分类真正遗漏结果、允许忽略结果和误报，再调整质量门禁或局部抑制。
- 对 `@ApiStatus.Internal`、`@OverrideOnly`、`@NonExtendable`、`@Obsolete` 和 `@ScheduledForRemoval` 的使用做 API guardian/IDE 检查；升级不会替业务团队决定哪些警告应阻断发布。

## Kotlin Multiplatform 发布边界

25.0.0 起，同一个 `org.jetbrains:annotations` 发布通过 Gradle module metadata 暴露多平台 variant，并在 Maven Central 发布 `annotations-iosArm64`、`annotations-linuxX64`、`annotations-wasmJs` 等平台模块。JVM Maven POM 仍解析普通 `annotations-26.0.2-1.jar`。

本配方只修改声明的根坐标版本，不会把平台模块互相改名，也不会重写 `commonMain`、`jvmMain` 或 native source set。KMP 项目还需验证：

- Kotlin Gradle Plugin 与 Gradle 版本能否读取目标 `.module` 元数据；
- 所有实际 Apple/Native/JS/Wasm target 的 klib 都可从企业代理或离线仓库取得；
- dependency locking、verification metadata、version catalog 和 repository content filter 已同步；
- JVM consumer 没有因 variant attributes 错配而误选 common metadata artifact。

## 真实项目样本与测试覆盖

测试从以下公开仓库固定 commit 的构建文件和源码缩减而来，保留了真实 configuration 与注解使用方式：

- [Twitter4J/Twitter4J：Gradle `implementation` 23.0.0 与 `@NotNull`/`@Nullable`](https://github.com/Twitter4J/Twitter4J/blob/87ccc41fb14434e328946fa4422460990be7a2d4/twitter4j-core/build.gradle)
- [Rosewood-Development/RoseStacker：Gradle `compileOnly` 23.1.0](https://github.com/Rosewood-Development/RoseStacker/blob/e0b7f772f6cab7a7cf64370d33b3e8dd17d89685/Plugin/build.gradle)
- [Vineflower/vineflower：Gradle `implementation` 24.0.0 与 nullable SPI](https://github.com/Vineflower/vineflower/blob/b8273988af850e8cfb234ca08d129058502b032f/build.gradle)
- [KunMinX/Jetpack-MVVM-Best-Practice：Gradle `api` 24.0.1 与 AndroidX/JetBrains 混合 nullability](https://github.com/KunMinX/Jetpack-MVVM-Best-Practice/blob/543eb8659089d74ccad403763cb16596febc89b7/architecture/build.gradle)
- [JetBrains/intellij-community：Kotlin DSL 24.0.0](https://github.com/JetBrains/intellij-community/blob/365b46565dbd5db1912919eac0485827deed753b/updater/build.gradle.kts)

用例组织参考 OpenRewrite 官方固定提交的 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，Gradle 变更用例也采用官方 `withToolingApi()` 方式生成真实 `GradleProject` 标记。

当前 24 个测试执行覆盖：表格全部四个起始版本、旧版兜底、Maven 直接/属性/dependencyManagement/活动 profile/重复声明、Gradle 字符串/map/变量与 `compileOnly`/`testCompileOnly`/`api`/`implementation`、scope 与附加节点保留、无版本依赖、目标和更高版本防降级、相似坐标防误伤、Java 注解源码 no-op、Kotlin DSL 无语义模型安全回退，以及 recipe discovery/validation。

## 使用与验证

先验证本模块，再在目标工程 dry-run：

```bash
mvn -f rewrite-jetbrains-annotations-upgrade/pom.xml clean verify

mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jetbrains-annotations-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jetbrainsannotations.UpgradeJetBrainsAnnotationsTo26_0_2_1
```

审核依赖 diff 后，至少运行：JDK 8 基线编译、当前生产 JDK 编译、JPMS/module-path 编译、Java/Kotlin 混编、所有 annotation processor、IDE/Qodana/Error Prone/Sonar 检查、Javadoc、KMP 全 target 解析、dependency lock/verification，以及公开 API 的二进制兼容检查。把新增告警作为独立审核结果处理，不要用全局 suppression 掩盖真实空值或 API 使用问题。
