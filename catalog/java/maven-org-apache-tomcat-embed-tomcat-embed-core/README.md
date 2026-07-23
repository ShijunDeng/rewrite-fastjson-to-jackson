# org.apache.tomcat.embed:tomcat-embed-core / tomcat-embed-core 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于
> [`rewrite-tomcat-embed-core-upgrade`](../../../rewrite-tomcat-embed-core-upgrade)，
> 推荐入口是
> `com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57`。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-apache-tomcat-embed-tomcat-embed-core` |
| Maven artifactId | `migration-spec-java-maven-org-apache-tomcat-embed-tomcat-embed-core` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.apache.tomcat.embed:tomcat-embed-core`<br>`tomcat-embed-core` |
| Catalog canonical identity | `org.apache.tomcat.embed:tomcat-embed-core`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `10.1.57` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `11`；不同版本行不累加 |
| 分桶 | `B1_Patch直升`, `B2_Minor单包` |
| 难度 | `低` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-tomcat-embed-core-upgrade` |

## Excel 事实快照

本节逐字记录表格，不把自动分桶、难度或备注提升为官方兼容性结论。厂商后缀、
截断显示、无法解析值和疑似跨发布线目标均原样保留。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 3281 | 3280 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.0.27` | `10.1.57` | 11 | B2_Minor单包 | 低 | upgrade-candidate/mark | 同大版本内minor升级，通常向后兼容 |
| 3282 | 3281 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.15` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3283 | 3282 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.16` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3284 | 3283 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.20` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3285 | 3284 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.25` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3286 | 3285 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.28` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3287 | 3286 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.35` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3288 | 3287 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.36` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3289 | 3288 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.40` | `10.1.57` | 11 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3290 | 3289 | `org.apache.tomcat.embed:tomcat-embed-core` | java | `10.1.41 ... (共41个版本)` | `10.1.57` | 11 | B1_Patch直升 | 低 | unknown/mark | 仅patch变更，无breaking change |
| 4801 | 4800 | `tomcat-embed-core` | java | `10.0.27` | `10.1.57` | 0 | B2_Minor单包 | 低 | upgrade-candidate/mark | 同大版本内minor升级，通常向后兼容 |
| 4802 | 4801 | `tomcat-embed-core` | java | `10.1.15` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4803 | 4802 | `tomcat-embed-core` | java | `10.1.16` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4804 | 4803 | `tomcat-embed-core` | java | `10.1.20` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4805 | 4804 | `tomcat-embed-core` | java | `10.1.25` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4806 | 4805 | `tomcat-embed-core` | java | `10.1.28` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4807 | 4806 | `tomcat-embed-core` | java | `10.1.35` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4808 | 4807 | `tomcat-embed-core` | java | `10.1.36` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4809 | 4808 | `tomcat-embed-core` | java | `10.1.40` | `10.1.57` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |

## 升级方向与禁止降级

- 用户最新最高优先级清单展开出 38 个精确 AUTO 来源：18 个 Tomcat 10.1
  版本和 20 个 Tomcat 9.0 版本；完整列表见下节及实现模块。
- `10.1.57` 是目标版本，完整 NOOP。
- `11.0.18`、`11.0.21` 高于目标发布线，保持原文并精确标记
  `目标版本冲突（禁止降级）`。
- 最新清单取代旧合并口径；旧值 `10.0.27` 不再是批准来源，完整 NOOP。
- Excel 的 `10.1.41 ... (共41个版本)` 是不可执行的聚合事实；只有用户清单明确
  展开的原子版本可以 AUTO。
- 任何高于目标的版本、更新发布线或无法可靠比较的厂商版本必须保持字节级不变，并在
  真实依赖 owner 上标记 `目标版本冲突（禁止降级）`；本项目不存在回退路径。
- 表外低版本、动态版本、范围、变量、BOM/platform、parent、catalog、workspace、
  constraints 和锁文件不能被猜测式改写；应定位并迁移真正的版本 owner。
- 若同一模块列出多个坐标或别名，配方必须分别证明身份；在官方 relocation 证据固定前，
  不得因为 artifact 名相同而跨 group、生态或发行渠道改坐标。


## 用户任务边界补充

本节记录用户最新最高优先级边界；实现、测试与 `migration.yaml` 均以此为准。

| ID | 补充源版本 | 方向/动作 | 任务边界 |
| --- | --- | --- | --- |
| U-001 | `10.1.15`, `10.1.16`, `10.1.20`, `10.1.25`, `10.1.28`, `10.1.35`, `10.1.36`, `10.1.40`, `10.1.41`, `10.1.42`, `10.1.43`, `10.1.44`, `10.1.46`, `10.1.47`, `10.1.48`, `10.1.49`, `10.1.52`, `10.1.54`; `9.0.54`, `9.0.69`, `9.0.71`, `9.0.75`, `9.0.82`, `9.0.83`, `9.0.86`, `9.0.87`, `9.0.91`, `9.0.96`, `9.0.98`, `9.0.102`, `9.0.104`, `9.0.105`, `9.0.106`, `9.0.107`, `9.0.108`, `9.0.111`, `9.0.115`, `9.0.117` | upgrade/auto | 仅精确 owner 和升级前最近构建根唯一命中时执行。 |
| U-002 | `11.0.18`, `11.0.21` | conflict/mark | 10.1.57 低于 11.x；保持原样并标记禁止降级。 |
| U-003 | `10.0.27` | superseded/noop | 最新清单不含该旧值，不沿用早期候选边。 |


## 不兼容点规格

| ID | 不兼容点 | 适用来源 | 自动化处置 | 仍需业务验证 |
| --- | --- | --- | --- | --- |
| C-001 | Java 11、Servlet 6、EL 5 基线 | 38 个 AUTO 来源 | 构建风险精确 MARK；Tomcat 9 工程迁移 Servlet/EL 依赖与类型 | CI/容器/runtime JDK、框架与 JSP/WebSocket 兼容 |
| C-002 | `javax.servlet` / `javax.el` → `jakarta.*` | 20 个 Tomcat 9 来源 | 复用官方 `ChangeDependency`、`UpgradeDependencyVersion`、`ChangePackage`，且只在 Tomcat 9 build-root 门控内运行 | descriptor、反射字符串、service provider、第三方集成 |
| C-003 | Servlet 6 删除/重命名 API | Tomcat 9/10.1 | 复用官方安全叶子：方法改名、删参、参数重排、`UpdateGetRealPath` 等 | 删除类型、自定义 interface 实现与 overload 冲突 |
| C-004 | `HttpSession.getValueNames()` 返回类型不等价 | Tomcat 9/10.1 | 不启用官方聚合中的 name-only 叶子，保留原调用并精确 MARK | `String[]` → `Enumeration<String>` 的迭代、集合和公开返回类型 |
| C-005 | APR/JNI 移除与 Tomcat 内部 API | 全部 | 精确 MARK native、Catalina/Coyote/Tomcat internal 使用 | NIO/NIO2、OpenSSL、Valve/Realm/Connector/loader |
| C-006 | 参数、URI、HTTP method、ETag、DIGEST 默认与安全行为 | 跨相关 10.1 补丁 | 配置/源码风险 MARK，不恢复旧默认 | 1000 参数上限、NULL、大小写、缓存 identity、RFC 7616 qop |
| C-007 | `EncryptInterceptor` wire format 变化 | 跨 10.1.56 | cluster 配置精确 MARK | 全集群停机升级；禁止旧新节点滚动混跑 |
| C-008 | Tomcat Embed sibling artifact 对齐 | 全部 | 构建风险定位未对齐 family owner | BOM/catalog/parent 和最终 dependency convergence |
| C-009 | Servlet 4/5/6.1 descriptor 与资源字符串 | Tomcat 9/10.1/冲突工程 | 结构化配置与资源 MARK | schema、SCI、服务文件、manifest、部署器 |
| C-010 | Tomcat 11 与任何高于 10.1.57 的版本 | 冲突/未来版本 | 原样保留并精确 MARK `目标版本冲突（禁止降级）` | 为 Tomcat 11 选择不低于现用版本的新目标 |

完整的每个 API、配置和运行时 marker 验收说明见
[`rewrite-tomcat-embed-core-upgrade/README.md`](../../../rewrite-tomcat-embed-core-upgrade/README.md)。

### `java` 生态最低核查项

- 确认规范 Maven 坐标、relocation 关系，以及 parent/BOM/property/platform 的真实版本 owner。
- 覆盖 Maven 与 Gradle；核查 JDK/字节码基线、包名和公开 API、反射、注解处理与 ServiceLoader。
- 核查 JPMS/OSGi、shade/native-image、序列化/缓存/数据库数据，以及配置文件和框架联动。

## 证据台账

| Claim ID | 已证明事项 | 状态 | 固定证据 |
| --- | --- | --- | --- |
| E-001 | 坐标、源版本和目标制品身份 | `VERIFIED` | Tomcat `5da21b1c`；目标 JAR `9e230f…`、POM `a649e2…` |
| E-002 | Namespace、Servlet 6/EL 5、配置和默认行为 | `VERIFIED` | Tomcat 10/10.1 指南、10.1.57 changelog、Servlet 6 规范 |
| E-003 | 真实用法、负例和行为边界 | `VERIFIED` | JFinal、Yona、Jahia 固定提交；437 项测试 |

官方能力审计固定实际构建使用的 OpenRewrite JAR manifest、SHA-256 与运行时 recipe tree。
推荐树复用 Core、`rewrite-java-dependencies` 和 `rewrite-migrate-java` 的 20 余个安全
叶子；本地代码仅负责 38 值白名单、最近构建根、Tomcat XML/资源和风险搜索。完整
`RemovalsServletJakarta10` 聚合因其 `getValueNames()` 只改方法名却改变返回类型而不被
直接激活；宽泛 `JakartaEE10` 也因会扩大到工作簿外 API 而排除。

## 后续 OpenRewrite 配方契约

### AUTO

- 只处理 38 个精确源版本、明确坐标和当前文件拥有的标准依赖声明；
- 推荐入口先冻结升级前最近构建根；Tomcat 9 namespace AUTO 另受源分支门控；
- 更高版本永不降级，表外版本、变体和外部 owner 永不猜测；
- 官方安全叶子优先；返回类型或业务语义不等价的官方叶子被拆除并改为 MARK；
- 保留 scope、classifier/type、optional、exclusions、workspace/profile 和相邻内容。

### MARK

- 在具体依赖、属性、BOM/platform、调用、类型、配置键或资源节点标记未决事项；
- marker 必须说明业务 owner 需要作出的决定、所需证据和验收方法；
- 不用文件级泛化告警代替精确定位，也不把 README 文字伪装成已执行迁移。

### MANUAL

- 运行时流量、安全策略、数据和 wire format、集群滚动策略、原生 ABI、性能容量、
  外部服务兼容性与回滚均由业务证据决定；
- 无法通过静态上下文证明安全的语义变换保持原样。

## 测试与真实用例验收

- `clean verify` 执行 437 项测试，0 failures/errors/skipped；
- 38 个 AUTO 版本逐一通过推荐入口；目标、高版、表外、无关和嵌套工程完整 NOOP；
- `11.0.18` / `11.0.21` 仅在 build owner 上出现精确禁止降级 marker；
- 覆盖 Maven property、Gradle Groovy/Kotlin/map、variant、动态 owner 和生成目录；
- 展开实际运行树验证官方叶子、参数、project gate，以及危险聚合确实被排除；
- 固定 JFinal、Yona、Jahia 与 Tomcat 自身真实仓库片段并验证两周期幂等。

## 当前阶段结论

规格、固定证据、真实仓库夹具和可执行配方均已完成。AUTO 严格限于 U-001；
MARK 和 MANUAL 项仍须在业务仓库完成协议、安全、集群、部署和回滚验收。
