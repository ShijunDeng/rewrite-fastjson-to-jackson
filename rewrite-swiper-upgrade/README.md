# Swiper upgrade to 12.1.2

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `swiper`，处理以下来源版本到 `12.1.2` 的升级：

```text
3.4.2
6.8.1  6.8.4
7.2.0
8.3.1  8.4.7
9.1.0  9.2.0  9.4.1
```

推荐使用的组合配方是：

```text
com.huawei.clouds.openrewrite.swiper.MigrateSwiperTo12_1_2
```

它依次升级受控的依赖声明、迁移可以确定等价的源码/样式入口，并把不能在缺少业务语义时安全改写的代码标成 OpenRewrite 搜索结果。模块还提供以下可单独运行的配方：

| 配方 | 用途 |
| --- | --- |
| `com.huawei.clouds.openrewrite.swiper.UpgradeSwiperDependencyTo12_1_2` | 只升级 `package.json` 中的受控 Swiper 版本 |
| `com.huawei.clouds.openrewrite.swiper.MigrateDeterministicSwiperSourceTo12` | 只迁移确定性的入口、模块导入、CSS 路径和容器类名 |
| `com.huawei.clouds.openrewrite.swiper.AuditSwiper12Source` | 审计 JS/TS import、API、参数与事件 |
| `com.huawei.clouds.openrewrite.swiper.AuditSwiper12Project` | 审计 manifest、TypeScript、bundler 与 Jest JSON 配置 |
| `com.huawei.clouds.openrewrite.swiper.AuditSwiper12TemplatesAndStyles` | 审计 Swiper Element、样式、Lazy 与 CDN 资产 |
| `com.huawei.clouds.openrewrite.swiper.FindManualSwiper12MigrationRisks` | 兼容旧调用方式的三个审计配方聚合入口 |

## Spec 与配方能力映射

| 不兼容点 | 配方行为 | 测试证据 |
| --- | --- | --- |
| 表格列出的 3/6/7/8/9 版本 | **AUTO**：四个直接依赖区中仅 exact、`^`、`~` 三种严格形式升级到 `12.1.2` | 版本×声明形式×依赖区参数化测试、6 个真实 package 样本和 protocol/range/lockfile no-op |
| 精确旧 JS core/bundle 与 CSS/模块样式入口 | **AUTO**：无 named binding 的 ESM import 与静态 `import()` 按全功能语义改为 `swiper`/`swiper/bundle`，样式改为公开 CSS export | JS/TS LST import/dynamic import 与注释感知 style visitor before→after |
| 根入口的 named-only 内置模块 import | **AUTO**：移动到 `swiper/modules`；default+named 混合 import 不猜拆分 | named、alias、多模块、混合 import MARK/no-op 测试 |
| Swiper 构造器字符串及独立 style/markup `.swiper-container` token | **AUTO**：改为 `.swiper`；动态 selector、Web Component 和前后缀 class 不动 | 绑定级构造器、CSS、HTML/Vue/Svelte 与边界测试 |
| `watchVisibleSlides`、wrapper、Lazy、loop、Grid/freeMode、`Swiper.use`、CJS、Element 事件 | **MARK**：在具体 LST/文本节点添加 SearchResult | 风险 marker、无 Swiper 绑定同名 API no-op 与推荐配方测试 |
| `= / v / ^v`、complex range、protocol、alias、catalog、未列版本 | **NO-OP + MARK**：依赖 AUTO 不猜；项目审计说明所有权或人工升级路径 | 严格白名单与 unresolved declaration 测试 |
| target/更高版本、override/resolution、lockfile、相似包、生成目录、注释/文档/样式字符串 | **NO-OP** | 防降级、防误改和文件/绑定/注释/字符串边界测试 |

## 自动处理范围

### package.json

依赖配方扫描根目录和任意子目录的 `package.json`，只修改以下四个直接依赖区中的精确 `swiper` 键：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

表格所列九个版本仅在 exact、caret、tilde 三种完整标量形式下被设置为精确版本 `12.1.2`，例如 `9.4.1`、`^9.4.1`、`~9.4.1`。`=9.4.1`、`v9.4.1`、`^v9.4.1` 也保持不变并由审计配方标记；配方不笼统匹配整个主版本，也不会把 `9.4.0`、`9.4.2`、`10.x` 或 `11.x` 纳入升级范围。

以下内容有意保持不变：

- 已是目标版本、更高版本和表格未列版本；
- prerelease、build metadata、区间、通配符、OR 范围、tag 和变量占位符；
- `workspace:`、`npm:` alias、Git/HTTP URL、tarball、`file:`、`link:` 和 catalog 引用；
- `overrides`、`resolutions`、`pnpm.overrides`、`peerDependenciesMeta`；
- `package-lock.json`、普通 JSON、数组/对象/null 等非字符串值；
- `swiper-react`、`vue-awesome-swiper`、`react-id-swiper`、`Swiper`、`@types/swiper` 等相似名称。

这里使用一个很小的 OpenRewrite JSON LST visitor 执行叶节点更新，再由 YAML 声明式配方组合。原因是本项目采用的 OpenRewrite 版本中，JSONPath 叶值匹配还会命中数组元素；专用 visitor 同时校验文件名、直属依赖区、键、标量类型和完整版本，避免把 lockfile、数组或嵌套 override 误改。

### 源码、样式和模板

源码配方只在 JavaScript、TypeScript、Vue、Svelte、HTML、CSS、SCSS 和 Less 的受控扩展名中执行边界明确的迁移：

- 无 named binding 的 `swiper/dist/js/swiper*.js`、`swiper/js/swiper*.js` ESM import 改为 `swiper/bundle`，保留旧全功能构建语义；明确的旧 core ESM 入口改为 `swiper`；
- `swiper/swiper-bundle*.js` 改为 `swiper/bundle`；
- 旧 core/bundle CSS 入口改为 `swiper/css` 或 `swiper/css/bundle`；
- `swiper/components/<module>/<module>.css` 改为 `swiper/css/<module>`，但不猜测已经移除的 Lazy 模块；
- 从根 `swiper` 导入且只包含已知内置模块的 named import 迁到 `swiper/modules`；混合 default+named import 标记后人工拆分；
- 静态 `import('旧公开文件')` 会改为目标 export；CommonJS `require(...)` 因 Swiper 7+ 为纯 ESM 而保持不变并精确标记，不能只改字符串路径伪装成已完成迁移；
- 已证明为 Swiper 构造器第一参数的字符串，以及独立 CSS/markup token `.swiper-container`、`class="swiper-container"` 改为 `.swiper`/`swiper`；
- `swiper/scss`、`swiper/less`、`watchVisibleSlides` 等缺少等价上下文的用法只做精确标记，不自动猜改。

例如：

```diff
-import { Navigation, Pagination } from 'swiper';
-import 'swiper/swiper-bundle.min.css';
+import { Navigation, Pagination } from 'swiper/modules';
+import 'swiper/css/bundle';

-new Swiper('.hero .swiper-container', { loop: true });
+new Swiper('.hero .swiper', { loop: true }); // loop 会留下人工复核标记
```

配方不会把 `<swiper-container>` Web Component 标签误改，也不会改 `.swiper-container-horizontal`、`.custom-swiper-container` 等带前后缀的类名。混合导入、未知模块、配置 JSON、文档和文本快照也保持不变。

JavaScript/TypeScript 自动迁移和风险检测使用 OpenRewrite JavaScript LST visitor，先收集 Swiper import binding 和局部声明，再修改精确 ESM import/dynamic import/未遮蔽构造器节点；普通同名 `Swiper`、非构造器 options 和未证明的 Element 变量不会命中。CSS、HTML、Vue、Svelte 的纯文本资产使用受控扩展名、精确 token、静态 class 属性与注释/字符串屏蔽 visitor；动态 class binding 保持不变。务必审查 dry-run diff，并逐个处理 `SearchResult`。

## 版本跨度内的不兼容修改点

本次跨度可能从 Swiper 3 直接跨到 12。下表按主版本列出必须合并考虑的升级点；“人工处理”表示仅凭局部文本无法证明等价，本模块最多给出搜索标记。

| 版本跨度 | 不兼容变化与处理建议 |
| --- | --- |
| 3.x → 6.x | npm 文件布局和导入入口发生变化，core 与 bundle 分离，旧 `dist/js`、`dist/css` 路径需要迁移；Swiper 6 提供官方 TypeScript 声明和 React 组件。跨过 4/5/6 的项目还要逐项核对参数、回调和模块注册方式。Swiper 6 的事件 listener 会把 Swiper 实例作为第一个参数，依赖旧参数位置的回调必须人工修正并回归。 |
| 6.x → 7.x | Swiper 7 是纯 ESM、发布代码目标 ES2015，并通过 package `exports` 限制可导入路径；CommonJS `require("swiper")`、深层文件路径和旧 bundler/Jest/SSR 配置可能失效。容器类由 `.swiper-container` 改为 `.swiper`。模块推荐经构造参数 `modules` 使用，而不是全局 `Swiper.use`。FreeMode、Grid、Manipulation 等成为独立模块，旧 `slidesPerColumn*` 和多个 free-mode 平铺参数需要迁成嵌套配置。`watchVisibleSlides` 合入 `watchSlidesProgress`；`touchEventsTarget`、`watchOverflow`、ResizeObserver 等默认行为也改变。 |
| 7.x → 8.x | 官方没有单独的 v8 major migration guide；该代包含 framework、类型、运行时和浏览器修复，例如 Angular 13 strict、React 18 及 Safari 相关变化。不要据此假定无兼容风险：必须在实际使用的 React/Vue/Angular adapter、SSR/hydration、类型检查、touch/pointer、loop 和 breakpoint 上回归。 |
| 8.x → 9.x | 触摸处理改用 Pointer Events；需要兼容老浏览器或模拟 Touch Events 的测试环境必须调整。Autoplay 和 loop 被重写，loop 模式对 slide 数量、`slidesPerView`、`slidesPerGroup` 和 grid 有约束。Lazy 模块及相关参数/类移除，应改用原生 `loading="lazy"` 和 preload spinner。Dom7 从 Swiper API 移除。Angular、Svelte、Solid 组件包装器从主包移除，官方建议改用 Swiper Element；React/Vue 入口仍保留。 |
| 9.x → 10.x | 包结构扁平化，JS 发布文件使用 `.mjs`，模块从 `swiper/modules` 导入；不要继续依赖未导出的内部目录。Swiper Element 的内部 DOM/样式组织有变化，依赖 shadow DOM 内部结构、注入样式顺序或旧 bundle 文件名的代码需要调整。 |
| 10.x → 11.x | `loopedSlides` 被移除，loop 下动态 slide、breakpoint 和不足 slide 的场景需要重新设计。Swiper Element 事件具有默认前缀，原来直接监听未加前缀事件名的代码需要按官方 Element 规则调整。Swiper 11 还改变了容器 overflow 行为，依赖溢出内容可见的布局应增加自己的容器/样式并做视觉回归。 |
| 11.x → 12.x | Swiper 12 移除发布包中的 Less/SCSS，必须使用 CSS exports 或在应用中维护自己的预处理源；Navigation 默认图标改用内联 SVG，覆盖旧伪元素/font 的主题样式、CSP、尺寸和视觉快照需要验证。 |

### 模块、构建与运行环境

目标 `12.1.2` manifest 声明 `"type": "module"`，core 与 bundle 的 JS exports 分别是 `swiper`、`swiper/bundle`，功能模块是 `swiper/modules`。CSS 只能使用 manifest 明确公开的 `swiper/css`、`swiper/css/bundle` 与 `swiper/css/<module>` 路径。不要从 `node_modules/swiper` 的内部文件布局导入；package exports 会让未公开的深路径在 Node、webpack、Vite、Rollup、Jest 或 SSR 中失败。

建议重点检查：

- 项目、测试 runner、SSR 和构建器能否消费纯 ESM 与 `.mjs`；
- 浏览器目标是否支持 Swiper 7+ 使用的现代语法和 Pointer Events，必要时由应用负责转译/polyfill；
- tree shaking 场景是否从 `swiper/modules` 导入并把模块传入实例/framework API；
- 使用完整功能时是否应选择 `swiper/bundle` 与 `swiper/css/bundle`；
- TypeScript 是否仍从公开入口 `swiper/types` 或对应 framework export 取类型，是否存在旧 `@types/swiper` 冲突；
- SSR/hydration 中访问 `window`/DOM 的时机、动态 import、测试环境 PointerEvent 支持和客户端初始化顺序。

### Framework 包装器与 Swiper Element

目标版本公开 `swiper/react`、`swiper/vue`、`swiper/element`、`swiper/element/bundle`，不再公开 `swiper/angular`、`swiper/svelte`、`swiper/solid`。旧 Angular/Svelte/Solid wrapper 不能安全地自动替换成 Web Components，因为 template binding、事件名、slot、生命周期和 SSR 策略不同；风险配方会标记这些 import，迁移时应按官方 [Swiper Element 文档](https://swiperjs.com/element) 重写并注册 custom elements。

不要把普通 CSS class `swiper-container` 的 v7 改名与 `<swiper-container>` 元素混为一谈：前者会自动迁移，后者是目标版本仍使用的 Web Component 标签。

### Loop、Lazy、Autoplay、导航和事件

以下项只会被搜索配方标记，不会被自动“猜改”：

- `Swiper.use(...)`：应确认实例/框架组件如何接收 `modules`，避免重复注册或破坏 tree shaking；
- `loopedSlides`：v11 已移除，替代配置取决于业务的动态 slides 和 loop 期望；
- `slidesPerColumn*`：需要迁到 Grid 的 `grid.rows`/`grid.fill`，同时验证 loop 和断点；
- 旧 free-mode 平铺参数：需要重组为 `freeMode: { ... }`，各字段并非都能机械一对一映射；
- `lazy`、`swiper-lazy`、`swiper/components/lazy/...`：需改为浏览器原生 lazy loading 并处理 loading 状态；
- Autoplay：v9 重写后要回归 pause/resume、interaction、visibility、transition、loop 边界和事件；
- Navigation/Pagination：检查生成 DOM、disabled/lock/hidden class、SVG 默认图标、主题覆盖和 a11y；
- v6 以前的事件回调：检查第一个参数是否已变为 Swiper 实例；
- Swiper Element 的 `addEventListener`：检查 v11 事件前缀及事件 detail 参数。

风险搜索不是错误诊断器，搜索结果表示“需要人工查看”，不是说该行一定有问题。组合配方执行后应处理全部 `~~>` 标记，或者单独运行风险配方生成审计清单。

## 官方依据

实现和兼容性清单以固定版本的上游材料为依据：

- [Swiper 12.1.2 发布 manifest](https://github.com/nolimits4web/swiper/blob/2fd88b718b6854e8d6be7f183e68b73b68dae816/src/copy/package.json)：ESM、公开 JS/CSS/framework/types exports；
- [Swiper 12.1.2 固定 commit 的 CHANGELOG](https://github.com/nolimits4web/swiper/blob/2fd88b718b6854e8d6be7f183e68b73b68dae816/CHANGELOG.md)：各主版本行为与发布变化；
- [Swiper 7 migration guide](https://v7.swiperjs.com/migration-guide)：ESM、package exports、类名、模块、参数和默认值；
- [Swiper 9 migration guide](https://swiperjs.com/migration-guide-v9)：Pointer Events、Autoplay、loop、Lazy、Dom7 和 framework wrappers；
- [Swiper 10 migration guide](https://swiperjs.com/migration-guide-v10)：扁平化 `.mjs` 包、模块 import 和 Swiper Element；
- [Swiper 11 migration guide](https://swiperjs.com/migration-guide-v11)：loop、Element 事件前缀和 overflow；
- [Swiper Element](https://swiperjs.com/element)、[Swiper API](https://swiperjs.com/swiper-api) 和 [Get Started](https://swiperjs.com/get-started)：目标版本的推荐入口、模块、样式与 API。

## 真实仓库测试样本

测试不是只验证人工构造的最小片段。`package.json` 用例从以下真实仓库的固定 commit 提取并缩减：

- [akveo/nebular @ f761f852](https://github.com/akveo/nebular/blob/f761f852e6a46d163fe2c360a04df35c31b24246/package.json)：`swiper: ^3.4.2`；
- [jellyfin/jellyfin-web @ a99ac779](https://github.com/jellyfin/jellyfin-web/blob/a99ac7791a7f735b3041883aa7f8948af4f5543f/package.json)：`swiper: ^6.8.1`；
- [numbersprotocol/capture-cam @ 9e33602a](https://github.com/numbersprotocol/capture-cam/blob/9e33602a/package.json)：`swiper: ^6.8.4`；
- [6c65726f79/Transmissionic @ 54ca0a7](https://github.com/6c65726f79/Transmissionic/blob/54ca0a7c264d4534a5ed6c37db12d56ecf522002/package.json)：`swiper: ^8.4.7`；
- [Mapuppy09/tradetrust-website @ 143ed9b](https://github.com/Mapuppy09/tradetrust-website/blob/143ed9b062be33cb0db58c45518aacfb2b568ddb/package.json)：`swiper: 8.4.7`；
- [Permify/permify @ b17c461](https://github.com/Permify/permify/blob/b17c461d/docs/documentation/package.json)：`swiper: ^9.1.0`。

源码 before/after 用例也固定到可复现 revision：

- [grevzi Swiper 6 React 示例](https://gist.github.com/grevzi/f697e307dd74cc383b0b9ebe3128224c/7eb565742bf4fe1877e254c34d91598359a13ba2)：bundle CSS、默认 import 和多行模块 import；
- [cainmagi Swiper 示例](https://gist.github.com/cainmagi/581189cdf4673be2358eeef7f35feab5/5b8a38791fba05e5d672ef740507b3dc73a8fc20)：默认 import 与多个模块；
- [hellokaton Swiper 示例](https://gist.github.com/hellokaton/6576fc9844384b0da0a3311f9f7b65ce/961d3b3195fb1ecb88860c587036878f016397a5)：根入口的 named module imports；
- [chiilog Swiper 示例](https://gist.github.com/chiilog/a7d8b22ea128ae5ab6eff1f6cf045464/40d30712af6d96ad61ec1b811187d87df947c9cf)：core 与模块的混合 import；
- [windiest/Angular-news @ aec41cc](https://github.com/windiest/Angular-news/blob/aec41cc0c2f4af2876507a22719b426e0935bdc9/webroot/news/directive/swiper.html)：旧容器 markup/class selector；
- [nathobson Swiper 示例](https://gist.github.com/nathobson/5770850df9485542ee93a583ca23248e/ba7b9b65b55b57acaefa890625684d61ad3db8eb)：迁移容器 selector，同时保留需人工处理的 lazy/loop 参数。

测试写法参考 OpenRewrite 官方固定提交 `b3008cc4a1f0c43f562da16e5933a2a56d9bc568` 的 JSON、JavaScript visitor、PlainText marker 与 RewriteTest 用例。当前测试套件执行 354 个测试 invocation，覆盖：

- 九个表格版本、exact/`^`/`~` 三类允许形式、四个直接依赖区和 workspace 子包；
- 真实仓库 package/source 形态；
- 绑定级旧 JS core/bundle、named modules、静态 dynamic import、CSS module 与 class 入口的 before/after；
- 目标/高版本、未列版本、协议/alias、复杂 range、override、lockfile、错误 JSON 类型、相似包名、现代入口、Web Component 标签和不支持扩展名的 no-op；
- 风险标记、组合配方、配方发现、元数据和配置验证。

## 使用与验证

先发布或安装本仓库的 `1.0.0-SNAPSHOT`，再在待迁移项目中运行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-swiper-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.swiper.MigrateSwiperTo12_1_2
```

确认 patch 后再执行 `rewrite:run`。随后用项目原有 npm、pnpm 或 Yarn 重新解析依赖并重建 lockfile；本配方刻意不直接编辑锁文件。

建议至少执行：依赖安装、lint、TypeScript build、unit/component/E2E、SSR/hydration、支持浏览器矩阵、touch/pointer、keyboard/a11y、loop、autoplay、lazy image、breakpoints、RTL、navigation/pagination 和视觉快照测试。跨多个主版本时，不应仅以“编译通过”作为迁移完成标准。

本模块自身验证：

```bash
mvn -pl rewrite-swiper-upgrade -am clean verify
```
