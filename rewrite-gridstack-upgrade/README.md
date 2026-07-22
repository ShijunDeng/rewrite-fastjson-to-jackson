# GridStack upgrade to 12.3.3

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `gridstack`，精确处理 `4.2.6`、`5.1.1`、`6.0.0`、`6.0.3`、`7.1.1` 到 `12.3.3` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.gridstack.UpgradeGridStackTo12_3_3
```

## 自动处理范围

配方只扫描根目录和 workspace 子目录中的 `package.json`，只修改以下四个直接依赖区中精确的 `gridstack` 键：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

每个表格版本仅接受精确值以及明确锚定该版本的 caret、tilde、equal 形式，例如 `6.0.3`、`^6.0.3`、`~6.0.3`、`=6.0.3`；命中后统一写为精确版本 `12.3.3`。配方在目标叶节点上做等值白名单判断，不会因为同一依赖区的另一个包恰好使用 `6.0.3` 而误改 GridStack。

配方有意不修改：

- 未列版本、目标版本、目标 range 或更高版本；
- comparator、OR、hyphen、`6.x`、prerelease、build metadata、tag、环境变量和模板变量；
- `workspace:`、npm alias、`file:`、`link:`、`portal:`、`catalog:`、Git/GitHub 和 URL/tarball；
- `overrides`、`resolutions`、`pnpm.overrides`、`dependenciesMeta`；
- npm、pnpm、Yarn lockfile，普通 JSON、备份文件和相似包名；
- JavaScript/TypeScript/Vue/Angular 源码、HTML、CSS、测试、快照和 Bundler 配置。

执行后必须用项目原有包管理器重新解析依赖和重建 lockfile。发布库还应人工决定 `peerDependencies` 的兼容范围，不能把自动写入的精确版本直接视作新的兼容承诺。

## v5 到 v12 的不兼容修改点

本次升级跨越 GridStack 多个主版本，应按官方迁移链逐段处理，而不是只验证页面能渲染。

| 版本 | 主要变化和迁移要求 |
| --- | --- |
| v5 / v5.1 | 强化嵌套 grid，支持父子 grid 间拖入拖出和 nested `column: "auto"`；5.1 增加 `GridStack.registerEngine()`，`minWidth` 改名为 `oneColumnSize`。自定义 layout engine 应继承公开 `GridStackEngine` 并注册，避免直接补丁私有 engine 字段 |
| v6 | 拖拽实现从 HTML5 `draggable=true` 改为原生 MouseEvent 和 TouchEvent，删除 jQuery UI 实现及 D&D plugin 选择；不再需要 `dist/h5/gridstack-dd-native` 或 `dist/jq/gridstack-dd-jqueryui`。移动端、Safari、混合触摸/鼠标设备和自定义 drag handle 必须重测 |
| v7 | 增加拖到另一个 widget 上动态创建 sub-grid、暂停碰撞和 sub-grid 间拖动；后续 7.x 增加官方 Angular component wrapper、framework `addRemoveCB` 和 nested load 修复。动态 nesting 可能改变 drop/布局结果 |
| v8 | 发布的 TypeScript/JS 目标改为 ES2020；`subGrid` 改为 `subGridOpts`，`GridStackOptions.addRemoveCB` 移到全局 `GridStack.addRemoveCB`，删除 `dragInOptions` 和旧 `minWidth`；`GridStackWidget.id` 收窄为 string；生成的部分 DOM 属性和 CSS selector 被删除或改名 |
| v9 | 增加并完善 `sizeToContent`、`resizecontent`；早期 `fitToContent` 名称改为 `sizeToContent`，`onParentResize()` 改为 `onResize()`。自动内容高度、嵌套 grid 和 resize 回调可能改变布局与事件时序 |
| v10 | 响应式布局改为 `columnOpts` 和 breakpoint/自动列数；删除 `disableOneColumnMode`、`oneColumnSize`、`oneColumnModeDomSort`，且不再默认切换单列。旧配置应显式改为如 `columnOpts: { breakpoints: [{ w: 768, c: 1 }] }`；10.3 的 `load()` 创建顺序也发生修复 |
| v11 | 为避免 XSS，不再由库直接把 widget `content` 写入 `innerHTML`；`addWidget()` 只支持 `GridStackWidget` 对象形式，引入 `GridStack.renderCB`，重写 side-panel 拖入流程并增加 lazy loading；11.4 又把 `Utils.createWidgetDivs()` 移到 `GridStack.createWidgetDivs()` |
| v12 | 列和 cell height 改用 CSS variables，删除 `gridstack-extra.min.css` 和自定义列 CSS 的需要；12.1 删除 ES5/IE 支持，并把 nested grid 事件传播到顶层 grid；12.2 增加 `updateCB`；12.3 增加 `save(columnCount)` 相关能力并修正数组自定义数据保存 |

### API、事件与序列化

- `addWidget()` 在 v11 只接受 `GridStackWidget`；旧字符串 HTML 或 `addWidget(el, x, y, w, h)` 调用必须改造。需要 DOM 时可使用 `GridStack.createWidgetDivs()`、安全填充内容后 `makeWidget(el)`，或者配置 `GridStack.renderCB`；任何 HTML 都必须先做可信模板渲染或消毒。
- `save()`、`load()` 和 framework callback 在各版本持续扩展。回归自定义字段、`save(false)`、nested children、最高列数布局、加载顺序、删除/更新组件生命周期以及 12.3 的保存列数。不要假定序列化输出、数组清理或 widget 顺序与旧版完全一致。
- 分别监听 `added`、`removed`、`change`、`dropped`、`dragstart/dragstop`、`resizestart/resizestop`、`resizecontent`；不要依赖一次操作必然产生旧版相同数量或顺序的事件。v12.1 后 nested 事件会传播到主 grid，旧的手工转发可能导致重复处理。
- `gridstackNode`、`grid.engine` 和 `DDGridStack` 虽可被部分版本源码访问，但包含内部状态和私有方法。真实 DevUI 用例直接访问 D&D/engine 私有结构，升级时需要逐项对照目标声明和运行行为，不能由版本配方猜测改名。
- 自定义 engine 使用 `GridStack.registerEngine(CustomEngine)` 和公开 `GridStackEngine`/`GridStackMoveOpts`；避免覆盖 `_leave`、`_updateContainerHeight`、`_gridstackNodeOrig` 等私有字段。

### 类型与 `GridStackWidget`

- v8 后包输出 ES2020 并继续提供 TypeScript 声明；旧 TypeScript、Jest、Angular builder 或未转译依赖的 Webpack 配置可能无法解析目标代码。
- `GridStackWidget.id` 从 number|string 收窄为 string。依赖数字 ID 的保存、查找、Vue key、React key 和后端 DTO 需要统一转换并验证 round-trip。
- 区分可序列化的 `GridStackWidget`、运行时 `GridStackNode` 和带 `gridstackNode` 的 DOM 元素；不要把 engine/node 私有字段直接持久化。
- `subGridOpts`、`columnOpts`、`sizeToContent`、lazy load、render/update/save callback 的类型都需要重新编译。`as any` 只能临时隔离，不能代替行为回归。

### CSS、浏览器与模块加载

- 继续显式导入 `gridstack/dist/gridstack.min.css`，或者在 HTML 中部署对应 CSS。GridStack 的 JavaScript 不会替应用自动注入完整基础样式。
- v8 删除/调整多项 `.grid-stack`/`.gs-*` 规则和生成属性，v12 又改用 CSS variables。检查自定义 `gs-*` selector、主题覆盖、resize handle、item content、动画、RTL、Shadow DOM、CSP nonce 和视觉快照。
- `gridstack-extra.min.css` 在 v12 不再存在；旧自定义列数生成 CSS 应删除，并用 GridStack API/CSS variables 验证动态列数。不能把缺失文件通过 alias 指到旧副本继续混用。
- 目标 `12.3.3` manifest 只有 `main: ./dist/gridstack.js` 和 `types`，没有声明 `module`、`exports`、`type`、`engines` 或 `sideEffects`。这不代表任意旧 Node/浏览器都受支持：v8 的 ES2020 和 v12 的 CSS variables/IE 删除才是实际基线。也不要依赖缺少 `sideEffects` 元数据来推断 CSS 是否会被 tree-shaking 保留，生产构建必须检查最终 asset。
- 官方推荐根入口 `import { GridStack } from "gridstack"`，浏览器整包可使用 `dist/gridstack-all.js`。`dist/h5/**`、`dist/jq/**`、`dist/dd-element`、`dist/src/**` 等深度路径在主版本间变化且可能绕开打包器预期的格式选择，应尽量迁移到公开入口。

### jQuery、拖拽和触摸

- GridStack 的 jQuery API 在 v1 已移除，v3 曾保留 HTML5 与 jQuery UI 构建选择，v6 最终删除 jQuery UI D&D。应用可继续因其它组件使用 jQuery，但不要把它误认为 GridStack 12 的运行依赖。
- 删除 `gridstack-dd-jqueryui`、`gridstack-dd-native`、`jquery-ui-touch-punch` 和旧 alias 前，先确认没有其它业务组件共用它们。
- 在手机、平板、触控笔、触摸屏笔记本、Safari 和鼠标+触摸混合设备上测试拖入、拖出、resize、scroll、取消、editable input/textarea、trash/removable 和 nested grid。`alwaysShowResizeHandle: "mobile"` 的默认行为与旧布尔值也应纳入测试。

### Angular、React、Vue 与其它 wrapper

- GridStack 7.x 后发行包包含官方 Angular wrapper，目标包中存在 `gridstack/dist/angular`；应按目标 Angular 兼容范围和官方组件 API 迁移，不要继续绑定老 third-party directive 的私有 DOM。
- React/Vue 集成通常由应用负责渲染 widget 内容，再调用 `makeWidget`、`load` 或 framework callback；v11 的 `renderCB`/`addRemoveCB` 生命周期和 side-panel 改动必须与组件 mount/unmount 对齐。
- `react-gridstack`、`gridstack-angular`、Aurelia/Ember 等第三方 wrapper 不会由本配方同步升级。先查看其 peer dependency，再决定升级 wrapper、改用官方示例或直接集成 GridStack。
- SSR 环境应延迟访问 `window`、`document` 和 `GridStack.init()`，只在浏览器挂载后初始化；同时验证 hydration、路由重挂载和 `destroy()` 清理。

## 自动迁移与人工迁移边界

本配方只改确定的直接依赖声明，不会自动：

- 删除 jQuery/jQuery UI/H5 D&D adapter 或添加移动端替代；
- 改写 import、`addWidget/save/load`、事件签名、responsive options、engine 或 nested JSON；
- 修改 `GridStackWidget`/`GridStackNode`/DTO 类型或序列化数据；
- 添加 CSS、删除 extra CSS、修改 selector、CSS variables、Bundler、SSR 或 CSP 配置；
- 升级 Angular/React/Vue、第三方 wrapper、TypeScript、Webpack/Vite/Jest 和浏览器目标；
- 生成 lockfile 或判断 peer range。

这些修改依赖业务的 widget 内容、持久化协议、框架生命周期和交互设备，必须保留给人工审查。

## 官方依据

- [GridStack v12.3.3 README / 完整迁移链](https://github.com/gridstack/gridstack.js/blob/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb/README.md)：v5 至 v12 的逐版迁移说明、jQuery 历史、engine 和 framework 指南；
- [GridStack v12.3.3 changelog](https://github.com/gridstack/gridstack.js/blob/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb/doc/CHANGES.md)：目标版本和各主版本的 API、类型、CSS、D&D、nested/save/load 修复；
- [GridStack v12.3.3 package manifest](https://github.com/gridstack/gridstack.js/blob/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb/package.json)：目标版本入口、类型、依赖与发布文件；
- [GridStack API 文档](https://gridstackjs.com/doc/html/classes/GridStack.html)：目标系列 `addWidget`、`save/load`、事件、responsive 和 sub-grid API。

## 真实仓库测试来源

测试固定到公开仓库的具体 commit，并保留相邻依赖与真实源码形态：

- [pgregory/wetracker @ e461f104](https://github.com/pgregory/wetracker/blob/e461f104a01895bd9380213f14aea77d104b3f08/package.json)：`gridstack: ^4.2.6`、jQuery/jQuery UI 与 Webpack 5；其 [app.js](https://github.com/pgregory/wetracker/blob/e461f104a01895bd9380213f14aea77d104b3f08/src/app.js) 仍导入 `dist/h5/gridstack-dd-native`；
- [Hexa-ai/hexa-data @ 784f572b](https://github.com/Hexa-ai/hexa-data/blob/784f572b5775b4a3789bb86ca12eaafb374740cc/ui/package.json)：Vue 3/Vite/TypeScript 与 `gridstack: ^5.1.1`；其 [DashboardGrid.vue](https://github.com/Hexa-ai/hexa-data/blob/784f572b5775b4a3789bb86ca12eaafb374740cc/ui/src/components/DashboardGrid.vue) 使用 deep import、`GridStackWidget`、`save()` 和 `disableOneColumnMode`；
- [DevCloudFE/ng-devui @ ef76c44e](https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/package.json)：Angular 19 与 `gridstack: ^6.0.0`；其 [grid-stack.service.ts](https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/devui/dashboard/grid-stack.service.ts) 访问 DD/engine/内部 drag-drop 行为；
- [Taylor-CCB-Group/MDV @ bdec64eb](https://github.com/Taylor-CCB-Group/MDV/blob/bdec64ebaa69cb04f4277e7cc7919b7406a5bac7/package.json)：ESM 应用的 `gridstack: ^7.1.1`；其 [GridstackManager.ts](https://github.com/Taylor-CCB-Group/MDV/blob/bdec64ebaa69cb04f4277e7cc7919b7406a5bac7/src/charts/GridstackManager.ts) 使用 CSS、事件、`gridstackNode`、`oneColumnSize` 和 `destroy(false)`；
- [mxtommy/Kip @ 8b75977a](https://github.com/mxtommy/Kip/blob/8b75977accb67929aa6d2ba32056c2d807be9820/package.json)：Angular/TypeScript 工程的目标版本 `gridstack: ^12.3.3`，验证不重复修改。

82 个测试调用参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。覆盖表格全部版本、四个依赖区、四类安全 semver、workspace 子清单、真实源码 no-op、目标叶节点与相邻版本隔离，以及未列/目标/更高版本、宽范围、tag、变量、prerelease、protocol、alias、override、非字符串、lockfile、普通 JSON、备份和相似包名。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-gridstack-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.gridstack.UpgradeGridStackTo12_3_3
```

确认 patch 后，重建 lockfile，并依次运行 TypeScript/lint、unit/E2E、production bundle、SSR、视觉快照和支持浏览器测试。重点验证 add/save/load round-trip、responsive breakpoint、nested/sub-grid、side-panel、事件次数、CSS variables、touch/mouse、editable control 与 framework mount/unmount。

本模块自身验证：

```bash
mvn -f rewrite-gridstack-upgrade/pom.xml clean verify
```
