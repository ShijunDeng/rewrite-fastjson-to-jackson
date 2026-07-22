# diagram-js-minimap 迁移到 5.2.0

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `diagram-js-minimap`。表格中唯一可见源版本为 `2.1.0`，目标为 `5.2.0`；依赖自动升级严格限定为 `2.1.0`、`^2.1.0`、`~2.1.0`，不会推断或扩大版本范围。

推荐配方：

```text
com.huawei.clouds.openrewrite.diagramjsminimap.MigrateDiagramJsMinimapTo5_2_0
```

它组合严格依赖升级、可证明的源码/样式/配置自动迁移，以及其余不兼容点的精确 `SearchResult` 标记。子配方包括：

- `UpgradeDiagramJsMinimapTo5_2_0`
- `MigrateDeterministicDiagramJsMinimapTo5`
- `AuditDiagramJsMinimap5Source`
- `AuditDiagramJsMinimap5Project`
- `AuditDiagramJsMinimap5TemplatesAndStyles`

## AUTO / MARK / NO-OP

| 类别 | 处理内容 | 测试边界 |
| --- | --- | --- |
| AUTO | 四个直接依赖区中 `2.1.0` 的 exact/`^`/`~` 声明改为精确 `5.2.0` | 根/子 workspace、四依赖区、幂等 |
| AUTO | 将精确 `diagram-js-minimap/dist/index.esm.js` 或 `dist/index.js` import 归一到官方公开根入口 | 单/双引号、JS/TS；UMD、`lib`、第三方同名包不改 |
| AUTO | 在直接 `additionalModules` 数组中，只对已由 minimap 默认 import 证明的重复 binding 去重 | alias、混合自定义模块、单次注册及同名本地变量反例 |
| AUTO | Sass 可执行代码的 `@use`/`@forward`/`@import` 中，精确公共 CSS asset 去除旧 webpack `~` | 单/双引号；注释、变量、URL、自定义 CSS、相似包不改 |
| MARK | 根/深度/UMD/asset import，minimap 注册与 service 生命周期，HammerJS/touch、`keyCode`、DOM/SVG query、webpack vendor copy | 官方旧/新 example 固定提交与参数化 API 用例 |
| MARK | 未解决依赖、diagram-js/bpmn-js 矩阵、HammerJS、旧 bundler、IE/ES2018、`sideEffects:false`、styles/assets/Jest config | 精确 JSON member marker 与支持版本反例 |
| MARK | HTML/CSS asset delivery、`.djs-minimap` 内部 DOM override、固定 `djs-minimap-*` SVG ID、`url(#...)` | HTML/CSS/SCSS/Sass/Less 及注释反例 |
| NO-OP | `=2.1.0`、`v2.1.0`、未列版本、复合范围、tag、prerelease、protocol/alias/变量、override、lockfile、普通 JSON、相似包 | 广泛 npm spec 与真实版本矩阵 |

## 不兼容修改点

| 跨版本变化 | 处理与验证要求 |
| --- | --- |
| 2.1.0 将开发基线推进到 `diagram-js@8.1.1` 并增加 multi-plane | 对旧 diagram-js 7 曾出现 `findRoot` 兼容问题；必须以最终解析树而非只看顶层版本判断 |
| 3.0.0 切换 `diagram-js@9` | 上游使用 ES2018；审计旧 webpack/Rollup/Vite、Jest transform、IE browserslist 和 `node_modules` 转译边界 |
| 4.0.0 增加 touch 并将 HammerJS 设为 peer，4.0.1 改为普通依赖 | 不根据中间版本机械添加/删除 HammerJS；标记应用直接依赖和 gesture 代码 |
| 5.0.0 跟随 `diagram-js@14` 删除 HammerJS 和失效 touch support | 触摸、pinch、pan 没有机械等价迁移；必须设计应用方案并做真机/E2E |
| 5.1.0 支持同页多个 diagram-js 实例，并前缀 SVG graphic ID、复制时移除 ID | 标记固定 ID、`url(#...)`、DOM selector、快照和多实例注册；验证销毁/重建及 ID 唯一性 |
| 5.2.0 支持 `diagram-js@15.1.0` | 标记旧 direct diagram-js 和所有 bpmn-js 声明，要求验证最终解析的单一 diagram-js 与插件矩阵 |
| 上游 diagram-js 11–15 的 popup、model/type、keyboard/focus 变化 | 标记旧 `keyCode` 和内部 DOM；验证 `.djs-parent`、model factory、keyboard binding、Canvas focus/restoreFocus、selection outline |
| 目标 npm 包仅发布 `dist` 与 `assets` | `lib/Minimap` 不在发布清单，标记为必须迁移；可证明的 dist index import 自动改用公开根入口 |
| CSS 不随 JavaScript 自动注入 | 必须加载 `diagram-js-minimap/assets/diagram-js-minimap.css`；标记 tree-shaking、copy/public path、SSR、CSP、微前端隔离和 load order |
| module/service 注册与生命周期 | 已证明的重复 `additionalModules` 自动去重；其余注册、`modeler.get('minimap')`、open/close/toggle 均标记检查 import、隐藏容器和 destroy 时机 |

## 固定依据与真实用例

- diagram-js-minimap `v5.2.0` 固定提交 [`5ec9b954be718d8377bb45c2379f0c981428d034`](https://github.com/bpmn-io/diagram-js-minimap/tree/5ec9b954be718d8377bb45c2379f0c981428d034)：CHANGELOG、package 发布清单、README、CSS 和实现。
- 源版本 `v2.1.0` 固定提交 [`6e19232b18f2d176a832ce0e9c8f9e738af4eed2`](https://github.com/bpmn-io/diagram-js-minimap/tree/6e19232b18f2d176a832ce0e9c8f9e738af4eed2)：旧依赖与发布形态对照。
- diagram-js `v15.1.0` 固定提交 [`c5d87651f362e46104b947811dc88977b4ca0f5e`](https://github.com/bpmn-io/diagram-js/tree/c5d87651f362e46104b947811dc88977b4ca0f5e)：目标上游行为基线。
- OpenRewrite `8.87.5` 固定提交 [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)：JavaScript/JSON/Text `RewriteTest`、幂等和 SearchResult 测试模式。
- [`bpmn-io/bpmn-js-examples@135f410e`](https://github.com/bpmn-io/bpmn-js-examples/tree/135f410e645cb85bf689a5e0e7b6c515812c73c9/minimap)：旧 webpack vendor-copy、HTML CSS link、module 注册与 service 调用 marker；真实 `^1.2.1` 依赖为严格 no-op。
- [`bpmn-io/bpmn-js-examples@c7baad91`](https://github.com/bpmn-io/bpmn-js-examples/tree/c7baad910b1185e8c6c58bb3676d7c9b0c36beac/minimap)：目标 `^5.2.0`、JS CSS import 与现代 bpmn-js marker/no-op。
- [`codebdy/rxdrag@6759ce35`](https://github.com/codebdy/rxdrag/blob/6759ce350edb5a822c88f7c2c73275b6662f4206/packages/bpmn-editor/package.json)：workspace、bpmn-js 10、minimap 3 no-op。
- [`moon-studio/vite-vue-bpmn-process@db85ffcc`](https://github.com/moon-studio/vite-vue-bpmn-process/blob/db85ffccd714607ba966017a257d3699aec4d993/package.json)：diagram-js 12、bpmn-js 13、minimap 4 no-op/audit matrix。
- [`Link-Kou/React-bpmn@72bb0ed5`](https://github.com/Link-Kou/React-bpmn/blob/72bb0ed51053bd81ecc9a4f6c2d62a8a83f3f558/package.json)：diagram-js 6、bpmn-js 7、minimap 2.0.3 legacy no-op。
- [`WPS/egon.io@6b5dd602`](https://github.com/WPS/egon.io/blob/6b5dd602b26ba9bb05cef8ce09f1d97180ac4b32/package.json)：diagram-js 15.4 + minimap 5.2 target-side no-op。

当前测试矩阵共 153 个测试调用：58 个严格依赖、27 个源码/配置、68 个工程/样式/推荐配方测试。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-diagram-js-minimap-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.diagramjsminimap.MigrateDiagramJsMinimapTo5_2_0
```

模块独立验证：

```bash
mvn -f rewrite-diagram-js-minimap-upgrade/pom.xml clean verify
```

应用 patch 后使用原包管理器重建 lockfile，并运行 production bundle、TypeScript、unit/E2E、浏览器与真机测试；重点覆盖 CSS asset、single/multi instance、SVG ID、multi-plane、鼠标/滚轮/键盘/focus/outline、触摸、SSR 与销毁重建。
