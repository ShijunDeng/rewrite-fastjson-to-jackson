# diagram-js-minimap upgrade to 5.2.0

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `diagram-js-minimap`，精确处理 `2.1.0` 到 `5.2.0` 的依赖声明升级。

配方名称：

```text
com.huawei.clouds.openrewrite.diagramjsminimap.UpgradeDiagramJsMinimapTo5_2_0
```

## 自动处理范围

配方只扫描根目录与工作区子目录的 `package.json`，并且只修改以下四个直接依赖区中的精确键 `diagram-js-minimap`：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

可安全识别的输入是以 `2.1.0` 为唯一版本的 registry 声明，例如 `2.1.0`、`^2.1.0`、`~2.1.0`、`=2.1.0` 和 `v2.1.0`。命中后统一写为精确版本 `5.2.0`。

为了避免把依赖约束含义扩大到未经验证的主版本，配方有意不修改：

- 除 `2.1.0` 外的任何版本，包括 `2.0.4`、`2.1.1`、3.x、4.x、目标版本和更高版本；
- `>=2.1.0`、`<3`、`2.x`、hyphen/OR range、prerelease、build metadata、`latest` 等含义更宽或不确定的声明；
- `workspace:`、npm alias、`file:`、`link:`、Git/GitHub 和 HTTP tarball 等非 registry 引用；
- `overrides`、`resolutions`、`pnpm.overrides`、`dependenciesMeta` 等传递依赖或包管理器专用配置；
- `package-lock.json`、`pnpm-lock.yaml`、`yarn.lock`、普通 JSON 和备份文件；
- JavaScript/TypeScript、CSS、HTML、打包器配置，以及相似包名。

工作区根清单中的 `workspaces` 配置保持原样；每个实际子包 `package.json` 会独立匹配。执行后必须使用工程原有 npm、pnpm 或 Yarn 重新解析依赖并生成 lockfile。

## 不兼容修改点

本次跨越三个主版本，不能把它当作仅改版本号的补丁升级。

| 变化 | 影响与迁移建议 |
| --- | --- |
| 2.1.0 引入 multi-plane diagram 支持并将开发基线推进到 `diagram-js@8.1.1` | 该版本曾破坏较老 `diagram-js@7` 的兼容性，官方随后在 2.1.1 修复。迁移前先记录现有 `bpmn-js` 实际解析出的 `diagram-js`，不要只看顶层声明 |
| 3.0.0 切换到 `diagram-js@9` | `diagram-js@9` 使用 ES2018 语法；老浏览器、老 WebView、未转译 `node_modules` 的 Babel/Webpack 配置可能无法解析。检查 browserslist、Webpack/Vite/Rollup 和测试运行器的转译边界 |
| 4.0.0 增加触摸支持并把 `hammerjs` 声明为 peer，4.0.1 又将其改为普通依赖 | 不要根据中间版本说明在目标工程永久补上 `hammerjs`；先检查项目是否因自己的代码仍直接使用它 |
| 5.0.0 跟随 `diagram-js@14` 删除 `hammerjs` 和失效的触摸支持 | 依赖 pinch/pan/touch 的平板、手机或触摸屏业务会发生真实功能回退。当前没有由配方自动提供的等价替代，应制定独立触摸交互方案并跑真机/E2E 测试 |
| 5.1.0 支持同页多个 diagram-js 实例，并为 SVG graphic ID 加前缀、复制元素时不再复制 ID | 依赖固定 SVG `id`、`url(#...)`、DOM 快照、截图基线或全局查询选择器的测试可能改变；多模型器页面要检查 ID 唯一性、销毁与重新挂载 |
| 5.2.0 声明支持 `diagram-js@15.1.0` | 应把 `bpmn-js`、直接声明的 `diagram-js` 及其它 diagram-js 插件作为一套兼容矩阵验证；不要让包管理器同时安装多个不兼容的 diagram-js 实例 |
| 目标包仍通过 `main`、`module`、`umd:main` 提供 `dist` 构建 | 继续使用公开根入口 `import minimapModule from "diagram-js-minimap"`。`diagram-js-minimap/lib/Minimap`、`dist/index.esm.js` 等深度导入依赖内部布局，可能绕开 Bundler 的格式选择，配方不会自动改写 |
| 样式不随 JavaScript 自动注入 | 必须确认 `diagram-js-minimap/assets/diagram-js-minimap.css` 被 HTML、CSS 或入口模块加载，并被发布/SSR/微前端构建实际打包；缺少该 asset 时控件可能存在但不可正确布局或操作 |

目标插件所处的 `diagram-js` 主版本跨度还包含以下上游不兼容点，业务往往通过 `bpmn-js` 间接遇到：

- `diagram-js@9` 改用 ES2018，旧 JS 引擎和转译链需要升级；
- `diagram-js@11` 重做 popup menu，改用 `.djs-parent` 作为 canvas 与 popup root 的共同选择器，并从 `KeyboardEvent#keyCode` 转向 `code`；自定义 CSS、popup provider 和键盘适配需迁移；
- `diagram-js@12` 重做 model/type definitions，元素必须通过 `model` 包暴露的 factory 创建，若干 `*Provider` TypeScript 类型成为真正的 interface；
- `diagram-js@14` 删除已损坏的 touch interaction 和 `hammerjs`，并调整类型；
- `diagram-js@15` 使 Canvas 可聚焦，键盘绑定改由 `keyboard.bind` 配置且依赖浏览器焦点，selection 不再默认提供视觉 outline；需要时启用 outline feature，并在弹窗/属性面板关闭后调用 `Canvas#restoreFocus` 恢复焦点。

这些变化也会影响围绕画布编写的业务插件，而不仅是 minimap 本身。尤其应检查：

- `additionalModules: [ minimapModule ]` 是否只注册一次，多个模型器是否各自拥有独立模块生命周期；
- `modeler.get("minimap").open()/close()/toggle()` 的调用时机，特别是 XML import、容器挂载和销毁前后；
- 自定义 `.djs-container`、popup、selection、outline、minimap DOM/SVG 选择器和 z-index；
- Shadow DOM、微前端 CSS 隔离、SSR、CSP、资源 public path 和 CSS tree-shaking；
- bpmn-js/diagram-js 插件是否深度导入内部模块，是否依赖旧 model 构造方式或 keyboard target；
- 触摸、滚轮、Ctrl/Cmd+滚轮、拖拽、缩放、viewport 同步、多 plane 和多个实例交互。

## 自动迁移与人工迁移边界

配方只对确定的 JSON 依赖声明负责，不会自动：

- 升级 `bpmn-js`、`diagram-js`、properties panel、token simulation、grid 或其它插件；
- 添加或删除 `hammerjs`，也不会恢复 v5 已删除的触摸支持；
- 插入 CSS import、复制 CSS asset 或修改 Webpack/Vite/Rollup/Angular 构建配置；
- 修改 import/require、深度导入、服务调用、DOM/CSS selector、keyboard/focus、model factory 或 TypeScript 类型；
- 猜测 peer range、workspace protocol 和 overrides 的发布策略；
- 修改任何 lockfile。

这类修改需要知道实际框架版本、浏览器目标、插件组合与交互需求，自动猜测的风险高于保留人工审核点。

## 官方依据

- [diagram-js-minimap CHANGELOG（目标 tag v5.2.0，commit 5ec9b954）](https://github.com/bpmn-io/diagram-js-minimap/blob/5ec9b954/CHANGELOG.md)：覆盖 v3、v4、v5、v5.1 和 v5.2 的兼容变化；
- [目标版本 package.json](https://github.com/bpmn-io/diagram-js-minimap/blob/5ec9b954/package.json)：确认目标版本的构建入口、发布文件及依赖；
- [官方 README](https://github.com/bpmn-io/diagram-js-minimap/blob/5ec9b954/README.md)：根入口注册方式与必需 CSS asset；
- [diagram-js v15.1.0 CHANGELOG](https://github.com/bpmn-io/diagram-js/blob/v15.1.0/CHANGELOG.md)：ES2018、popup/keyboard/CSS root、model/type factory、touch、focus 和 outline 等上游主版本变化；
- [2.1.0 与旧 diagram-js 的实际兼容问题](https://forum.bpmn.io/t/embedding-minimap-leads-to-an-error-this-canvas-findroot-is-not-a-function/7201)：`findRoot` 运行时错误及 2.1.1 修复背景。

## 真实仓库测试来源

测试固定到公开仓库的具体 commit，保留真实相邻依赖矩阵并验证精确升级或 no-op 边界：

- [bpmn-io/bpmn-js-examples @ 135f410e](https://github.com/bpmn-io/bpmn-js-examples/blob/135f410e645cb85bf689a5e0e7b6c515812c73c9/minimap/package.json)：官方 minimap 示例的 `bpmn-js@9` 集成形态，配合表格输入验证依赖升级且 JS/CSS 不被误改；
- [bpmn-io/bpmn-js-examples @ c7baad91](https://github.com/bpmn-io/bpmn-js-examples/blob/c7baad910b1185e8c6c58bb3676d7c9b0c36beac/minimap/package.json)：官方示例的目标版本与较新 bpmn-js 矩阵，验证 no-op；
- [codebdy/rxdrag @ 6759ce35](https://github.com/codebdy/rxdrag/blob/6759ce350edb5a822c88f7c2c73275b6662f4206/packages/bpmn-editor/package.json)：pnpm workspace 中 `bpmn-js@10` 与 minimap 3.x，验证子包路径与未列版本 no-op；
- [moon-studio/vite-vue-bpmn-process @ db85ffcc](https://github.com/moon-studio/vite-vue-bpmn-process/blob/db85ffccd714607ba966017a257d3699aec4d993/package.json)：Vue/Vite 应用的 `diagram-js@12.2.0`、`bpmn-js@13.2.0`、minimap 4.1.0 矩阵；
- [Link-Kou/React-bpmn @ 72bb0ed5](https://github.com/Link-Kou/React-bpmn/blob/72bb0ed51053bd81ecc9a4f6c2d62a8a83f3f558/package.json)：React 应用的 `bpmn-js@7.2.0`、`diagram-js@6.6.1`、minimap 2.0.3 遗留矩阵；
- [WPS/egon.io @ 6b5dd602](https://github.com/WPS/egon.io/blob/6b5dd602b26ba9bb05cef8ce09f1d97180ac4b32/package.json)：`diagram-js@15.4.0` 与 minimap 5.2.0 的真实目标侧矩阵。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。57 个测试调用覆盖四个依赖区、workspace 子清单、精确/caret/tilde/equal/v-prefix、真实版本矩阵、相邻依赖恰好也为 `2.1.0` 的隔离性、其他版本、宽范围、tag、prerelease、协议、alias、override、非字符串、lockfile、普通 JSON、相似包名和源码/CSS no-op。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-diagram-js-minimap-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.diagramjsminimap.UpgradeDiagramJsMinimapTo5_2_0
```

确认 patch 后，使用工程原有包管理器重建 lockfile。随后运行 lint、TypeScript、unit/E2E、Bundler production build 和支持浏览器矩阵，并重点回归 CSS asset、单/多实例、SVG ID、multi-plane、鼠标/滚轮/键盘/focus/outline 与触摸场景。

本模块自身验证：

```bash
mvn -f rewrite-diagram-js-minimap-upgrade/pom.xml clean verify
```
