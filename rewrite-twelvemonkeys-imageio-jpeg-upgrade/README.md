# TwelveMonkeys ImageIO JPEG 3.9.3 → 3.12.0

本模块对应 `开源软件升级.xlsx` 中 Excel 第 4701 行（表内序号 4700）的唯一任务：坐标严格等于 `com.twelvemonkeys.imageio:imageio-jpeg`，源版本只有 `3.9.3`，目标版本为 `3.12.0`。

这不是只有版本声明的模块。`imageio-jpeg` 主要通过 `META-INF/services` 接入 JDK ImageIO，公开 Java API 的源码断裂很少，但 3.10–3.12 改变了错误输入拒绝、重复 APP 段颜色判定、CMYK 元数据写回和 OSGi service-loader 元数据。配方因此把可证明安全的 family convergence 做成 AUTO，把依赖真实图片、类加载器和部署容器的判断精确 MARK 到源码/构建节点。

## 公开配方

| 配方 | 定位 | 可执行行为 |
|---|---|---|
| `com.huawei.clouds.openrewrite.twelvemonkeysjpeg.UpgradeTwelveMonkeysImageIoJpegTo3_12_0` | 严格升级 | 只把标准 Maven/根 Gradle 直接依赖的精确 `3.9.3` 改为 `3.12.0` |
| `com.huawei.clouds.openrewrite.twelvemonkeysjpeg.MigrateTwelveMonkeysImageIoJpegTo3_12_0` | 推荐迁移 | 先复用公开 Upgrade，再对齐同 owner 的精确 family pin，最后执行源码和构建兼容性 MARK |

Maven AUTO 覆盖项目根、直属 profile 及各自 `dependencyManagement`，支持只被目标依赖独占的字面量属性；profile 覆盖不会污染根或兄弟 profile。Gradle AUTO 只处理根 `dependencies {}` 中的字面量字符串、Kotlin 字符串和 Groovy map，拒绝 buildscript/subprojects/project/constraints、catalog、变量、classifier/type 和动态版本。生成物/缓存只按父路径分量跳过，名为 `generated.gradle` 或 `install.gradle` 的真实叶文件仍可处理。

## AUTO：防止 TwelveMonkeys family 版本撕裂

推荐配方在同一个 Maven root/profile 或同一个根 Gradle owner 中看到 `imageio-jpeg 3.9.3/3.12.0` 时，会把以下显式固定为 `3.9.3` 的 companion 改到 `3.12.0`：

- `com.twelvemonkeys.imageio:imageio-core`
- `com.twelvemonkeys.imageio:imageio-metadata`
- `com.twelvemonkeys.common:common-lang`
- `com.twelvemonkeys.common:common-io`
- `com.twelvemonkeys.common:common-image`

如果一个 Maven 属性只被目标和上述 family 使用，配方会一次性把该属性改为 `3.12.0`。属性还被其他依赖/插件/属性引用、定义重复、版本不是精确 `3.9.3`、companion 位于兄弟 profile 或没有同 owner JPEG 依赖时保持不变。这个 AUTO 解决的是真实的运行时 linkage/SPI 组合问题，不会顺手升级工作簿未授权的其他 TwelveMonkeys 图片格式模块。

## 不兼容点与配方映射

| 兼容性边界 | 3.12.0 影响 | 配方处理与验证要求 |
|---|---|---|
| 非法 Huffman table class | 3.10 把合法 class 上限从错误的 `2` 收紧为 JPEG 规范的 `1` | 在拥有该依赖的最近工程中，对 `ImageIO`/`ImageReader` 读取入口 MARK；回归畸形 DHT、告警、`IIOException`、资源上限与 hostile corpus |
| 重复 APP0/APP14 | 3.10 起使用最后一个 JFIF/JFXX/Adobe APP 段做颜色判定，而 3.9.3 使用第一个 | read/metadata/image-type 调用 MARK；用重复/冲突 marker fixture 验证 RGB、CMYK、YCCK、ICC 选择和像素容差 |
| APP14 Adobe version | 3.12 读取完整 2-byte version，旧逻辑只看低字节 | 同一 read marker 覆盖；断言 version/flags/transform 与颜色转换，不把旧错误结果固化为 golden master |
| CMYK 写入元数据 | 3.11 起保留原 `COM`/`unknown` 节点，过滤旧 `ICC_PROFILE` chunks 后写入目标 profile，并生成 APP14 transform=0 | `ImageIO.write`/`ImageWriter`、`IIOMetadata` native tree、`IIOMetadataNode`、ICC/CMYK import 精确 MARK；比较 marker 顺序、comment/unknown、ICC identity、destination type 恢复和 round-trip 色差 |
| SPI 发现与顺序 | reader/writer 包装 JDK JPEG provider，并依赖 IIORegistry ordering；类加载器过早初始化或丢 service descriptor 会退回 JDK provider | discovery/registry 调用 MARK；运行时断言实际 provider class/order，覆盖线程 context class loader、容器重载和 `scanForPlugins()` 时机 |
| reader/writer 生命周期 | 包装 provider 转发 listener、input/output、abort/reset/dispose；池化或遗漏 dispose 会放大流/监听器泄露 | lifecycle 调用 MARK；断言 stream ownership、abort/reset/dispose exactly-once 和 pooled reuse |
| OSGi | 3.10 开始成为 OSGi bundle；3.10/3.11 补充 reader/writer `Provide-Capability`、service-loader `Require-Capability` 和 registrar 要求 | 同 scope 的 Felix/bnd 插件 MARK；验证 capability header、registrar、动态 bundle 安装/卸载和两类 SPI 均可见 |
| shade/shadow/nested JAR | service descriptor 或 `META-INF/versions` 被覆盖/排除时，单元测试 classpath 可通过但发布物失去 provider/模块信息 | Maven shade 缺 `ServicesResourceTransformer`、service/MR-JAR 排除、Gradle Shadow、Spring Boot nested JAR 精确 MARK；必须检查最终 JAR 内容并启动发布形态 |
| native image | ImageIO SPI、实现构造器、`java.desktop`、ICC 和测试图像需要 reachability/resource 配置 | native Maven/Gradle 边界 MARK；用真实 JPEG corpus 构建并执行 native binary |
| mixed family | 显式旧 core/metadata/common 可能在依赖仲裁后与 3.12 JPEG 混装 | 精确 `3.9.3` 且同 owner 时 AUTO；其他固定版本 MARK 或保持，要求 dependency convergence/`dependencyInsight` |

`SearchResult` 直接附着在具体调用、import、native metadata node、dependency、plugin 或资源过滤器上。普通使用 JDK ImageIO、但最近 `pom.xml`/`build.gradle` 不拥有 `imageio-jpeg` 的源码不会被标记；嵌套工程的最近构建文件也会阻断父工程所有权，避免多模块误报。

## 严格所有权边界

公开 Upgrade 不处理：

- `3.9`、`3.9.2`、`3.9.4`、3.10/3.11、已经是 `3.12.0` 或任何其他固定版本；
- Maven parent/BOM 所有权、共享/重复属性、plugin dependency、versionless、classifier/type 变体；
- Gradle catalog/platform/provider、变量/插值、动态/范围版本、classifier/extension；
- buildscript、subprojects、`project(':x')`、constraints、选中式 DSL；
- `target`、`build`、`generated*`、`install*`、cache/vendor 等父目录中的副本。

推荐配方仍会对真正的外部/不明确 owner、非工作簿固定版本、variant、mixed family 做 MARK，而不会扩大 AUTO 白名单。

## 固定证据

官方源和目标均固定到不可变提交：

- [`3.9.3@ada3a84`](https://github.com/haraldk/TwelveMonkeys/tree/ada3a84bec463ee64a2b1be6eac1fd57d87f7940) 与 [`3.12.0@1197004`](https://github.com/haraldk/TwelveMonkeys/tree/119700487b78717d45ab74bf5a61ba9824442697)；两版 [`imageio-jpeg/pom.xml`](https://github.com/haraldk/TwelveMonkeys/blob/119700487b78717d45ab74bf5a61ba9824442697/imageio/imageio-jpeg/pom.xml) 都声明稳定 JPMS 名 `com.twelvemonkeys.imageio.jpeg`，所以配方不会制造无依据的 module rename。
- Huffman class guard [`608b372`](https://github.com/haraldk/TwelveMonkeys/commit/608b37232df44d90f5bc9950add7b2f609ab457c)、last APP segment [`2c7c47b`](https://github.com/haraldk/TwelveMonkeys/commit/2c7c47b1589da51a0e9ded1528ae1cf5138e630b)、CMYK metadata preservation [`031937f`](https://github.com/haraldk/TwelveMonkeys/commit/031937fe99e11a730ce3ca910c6aa7f726727e1f) 与 full Adobe version [`b91d02a`](https://github.com/haraldk/TwelveMonkeys/commit/b91d02a5626452b70b5b102def4d8c25cd37e991)。
- OSGi bundle [`f2cc9fa`](https://github.com/haraldk/TwelveMonkeys/commit/f2cc9faaf8561c46dbe959bdebe0d6c4be1cd16a)、reader/writer capabilities [`e65f471`](https://github.com/haraldk/TwelveMonkeys/commit/e65f471a8f843f88218ea54d9a7cec6c32dcd62c)、registrar requirement [`f0a032a`](https://github.com/haraldk/TwelveMonkeys/commit/f0a032a7b97fa006169dc7ea1dd4a7cc510af687) 和 writer SPI export 修复 [`a9e4b2e`](https://github.com/haraldk/TwelveMonkeys/commit/a9e4b2e262a089f7bc75bd3ed3fd0847eea2d713)。

真实公共仓库 fixture 固定在：

- [`gotson/komga@6e8caba`](https://github.com/gotson/komga/blob/6e8caba7fc1e74030b7665b59ed9cc555e3337e4/komga/src/main/kotlin/org/gotson/komga/domain/service/BookAnalyzer.kt) 的 JPEG `ImageIO.read`→`ImageIO.write` 清洗路径，以及同提交 [`ImageConverter`](https://github.com/gotson/komga/blob/6e8caba7fc1e74030b7665b59ed9cc555e3337e4/komga/src/main/kotlin/org/gotson/komga/infrastructure/image/ImageConverter.kt) 的 read/write 路径；
- [`sksamuel/scrimage@a922d93`](https://github.com/sksamuel/scrimage/blob/a922d935b182b526803f9a8902cba5a0dbeeca06/scrimage-core/build.gradle.kts) 同时固定 `imageio-core` 与 `imageio-jpeg` 的 Kotlin Gradle 形态；
- [`ethereum-lists/chains@30f9d54`](https://github.com/ethereum-lists/chains/blob/30f9d5450c836e2d8e64f0e886c1489e52f54bcd/processor/build.gradle) 的 Groovy 直接依赖，以及 [`deepjavalibrary/djl@af1d0b8`](https://github.com/deepjavalibrary/djl/blob/af1d0b804c110fb533c4580d681c76e51954edfd/docs/dataset.md) 要求应用显式加入 JPEG provider 的真实部署边界。

测试结构固定参考 OpenRewrite [`rewrite@b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 的 `RewriteTest`、XML/Java visitor、扫描式 recipe 与 before/after/marker 断言，不依赖移动分支内容。

## 验证

```bash
mvn -f rewrite-twelvemonkeys-imageio-jpeg-upgrade/pom.xml clean verify
```

当前独立测试执行 **54 个 test invocation**，覆盖工作簿精确白名单、Maven root/profile/dependencyManagement/property、Gradle Groovy/Kotlin/map、嵌套 owner/variant/路径负例、五个 companion AUTO、共享 family property、兄弟 profile 隔离、真实仓库形态、读/写/metadata/SPI/lifecycle MARK、shade/OSGi/native/nested-JAR 门控、配方发现以及两周期幂等。

迁移后的业务验证不能只停在编译：应在最终发布物和实际 class loader 中断言 provider 顺序，并用 RGB、gray、CMYK、YCCK、带/不带 ICC、重复 APP0/APP14、COM/unknown metadata、progressive/subsampled 以及畸形/超大 JPEG corpus 做像素、元数据、异常、内存和吞吐回归。
