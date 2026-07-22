# Guava upgrade to 33.5.0-jre

本模块对应 `开源软件升级.xlsx` 中的 `com.google.guava:guava`，合并处理以下源版本：

```text
21、29.0-jre、30.1-jre、30.1.1-jre、31.1-jre、
32.0.0-jre、32.0.1-jre、32.1.0-jre、32.1.1-android、32.1.1-jre
```

目标版本为 `33.5.0-jre`。配方名称：

```text
com.huawei.clouds.openrewrite.guava.UpgradeGuavaTo33_5_0Jre
```

## 自动处理范围

配置型配方使用 OpenRewrite 的 `UpgradeDependencyVersion`，升级 Maven 和 Gradle 中的：

```text
com.google.guava:guava:33.5.0-jre
```

同时处理直接声明和 Maven `dependencyManagement`。如果版本来自企业 BOM 或父 POM，配方会为 Guava 添加/更新显式覆盖版本；升级后应检查企业依赖治理规则。

## 不兼容修改点

| 版本跨度内的变化 | 影响与处理建议 |
| --- | --- |
| Java 7 运行时支持已移除 | 目标版本至少使用 Java 8；编译、测试和生产运行时必须统一 |
| 24.0 移除 `Predicates.assignableFrom`、`BinaryTreeTraverser`、`Futures.dereference`、`MoreExecutors.sequentialExecutor` 等废弃 API | 使用对应的 Java 类型判断、`Traverser`、`Futures.transformAsync` 或 JDK executor 能力替代 |
| 25.0 移除 `Files.fileTreeTraverser()` 和 `MoreFiles.directoryTreeTraverser()` | 改用 `MoreFiles.fileTraverser()` 或 JDK `Files.walk` |
| 26.0 移除 `CharMatcher` 的废弃静态字段以及隐式使用 `directExecutor()` 的 `Futures` 重载 | 静态字段改为同名方法；显式传入 executor，避免回调线程语义不清晰 |
| 26.0 调整 `HostAndPort.equals/hashCode` | 不再区分主机是否带方括号；检查以其作为 Map/Set key 的逻辑 |
| 28.0 移除 `CheckedFuture` 及相关工具 | 改用 `ListenableFuture`/`FluentFuture`，在业务边界显式转换异常 |
| 30.0 移除单参数 `ServiceManager.addListener` | 使用 `addListener(listener, executor)`；需要原行为时显式传 `MoreExecutors.directExecutor()` |
| `Files.createTempDir()` 被废弃且安全实现、权限和异常行为发生变化 | 优先改用 `java.nio.file.Files.createTempDirectory`，验证 Windows、容器用户和共享目录权限 |
| 31.0 的 nullness 标注和泛型签名更严格 | NullAway、Error Prone、Kotlin 调用方可能出现新的源码错误；修正空值与显式泛型，而不是关闭检查 |
| `Invokable` 不再继承 `AccessibleObject`/`GenericDeclaration` | 反射封装代码改用其公开方法或直接使用 JDK reflection API |
| `murmur3_32` 因实现缺陷被废弃 | 如需跨语言一致哈希，迁移到 `murmur3_32_fixed` 并评估已有持久化哈希值 |
| GWT-RPC 对 Guava 类型的支持已彻底移除；Guava GWT 需要更新的 GWT，并移除部分 enum 反射 API | DTO 不要直接暴露 Guava 集合；GWT 项目单独执行客户端编译和序列化回归 |
| 输入为 `32.1.1-android` 时目标切换为 `-jre` flavor | Android 项目不要直接接受该切换；应确认表格目标是否正确，或改用对应 `33.5.0-android`；33.5 Android flavor 的 `minSdkVersion` 为 23 |
| 新版引入/更新 `failureaccess`、JSpecify 等依赖元数据 | 检查 shading、JPMS、OSGi、依赖锁定和许可证清单，不要排除 `failureaccess` 运行时依赖 |

Guava 大部分普通公开 API 保持较强二进制兼容性，但跨 21→33 的已废弃 API 移除、静态分析变化和 GWT/Android flavor 差异仍需要源码编译与业务测试确认。

参考官方发布说明：[24.0](https://github.com/google/guava/releases/tag/v24.0)、[26.0](https://github.com/google/guava/releases/tag/v26.0)、[28.0](https://github.com/google/guava/releases/tag/v28.0)、[30.0](https://github.com/google/guava/releases/tag/v30.0)、[31.0](https://github.com/google/guava/releases/tag/v31.0)、[32.0.0](https://github.com/google/guava/releases/tag/v32.0.0)、[33.0.0](https://github.com/google/guava/releases/tag/v33.0.0)、[33.5.0](https://github.com/google/guava/releases/tag/v33.5.0)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-guava-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.guava.UpgradeGuavaTo33_5_0Jre
```

确认 patch 后将 `dryRun` 改为 `run`，并执行完整编译、单元测试、静态检查以及依赖树审计。

本模块自身验证：

```bash
mvn -pl rewrite-guava-upgrade -am clean verify
```
