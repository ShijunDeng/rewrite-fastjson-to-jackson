# GridStack 升级到 12.3.3

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `gridstack`，只处理表格可见的 `4.2.6`、`5.1.1`、`6.0.0`、`6.0.3`、`7.1.1` 到 `12.3.3` 的迁移。

推荐配方：

```text
com.huawei.clouds.openrewrite.gridstack.MigrateGridStackTo12_3_3
```

只修改依赖时使用严格子配方：

```text
com.huawei.clouds.openrewrite.gridstack.UpgradeGridStackTo12_3_3
```

## AUTO / MARK / NO-OP 矩阵

| 分类 | 自动行为 | 精确边界 |
| --- | --- | --- |
| AUTO：依赖 | 四个直接依赖区中的 `gridstack` 升级为精确 `12.3.3` | 只接受五个 XLSX 源版本的 exact、单 caret、单 tilde，例如 `6.0.3`、`^6.0.3`、`~6.0.3` |
| AUTO：import | `gridstack/dist/gridstack(.js)` 改为 public root `gridstack`；删除 v6 后不需要的 h5/jQuery D&D side-effect import；删除 v12 不存在的 `gridstack-extra.css` import | 只处理固定模块字面量；基础 `gridstack(.min).css` 保留 |
| AUTO：API/config | 已证明为 GridStack 实例的 `onParentResize()` 改为 `onResize()`；已证明属于 `GridStackOptions`/`init`/`addWidget` 且无冲突的 `subGrid` 改为 `subGridOpts`；无重复键时删除 `disableOneColumnMode:true` | 支持未被局部绑定/类型遮蔽的 named import 别名、局部变量、显式 `GridStack` 类型和 `this.grid`；spread、重复/目标键冲突、工厂返回值和无法证明所有权的同名对象不改并由推荐配方 MARK |
| AUTO：template/style | 旧 `data-gs-width/height/min/max/...` 精确改为 `gs-w/h/min-w/...`；删除精确的 `gridstack-extra.css` `@import`/`<link>` | 只处理 HTML/Vue template 与 CSS/Sass/Less 可见代码，忽略注释及 Vue `<script>/<style>` 原始块 |
| MARK：manifest/framework | 在未被严格配方选择的声明、React/Vue/Angular/Next/Nuxt 和 third-party wrapper 版本值上放置 `SearchResult` | 不猜 framework/wrapper 版本；要求验证 peer、render callback、mount/unmount、SSR/hydration |
| MARK：JS/TS | 在初始化、deep/internal import、engine/DD 类型、add/remove/update、event、save/load、drag-in、nested grid、global callback、destroy、`gridstackNode`、`innerHTML`、browser global 等具体 AST 节点放置 `SearchResult` | 只标记导入 GridStack 且所有权可证明的调用/选项；普通项目中的同名 `save/on/destroy` 不标记 |
| MARK：template/style | 在 framework rendering、`v-html`/`innerHTML`、nested grid、旧 column class、geometry selector、widget content override 上放置 `SearchResult` | 标记具体 token，忽略注释；普通 `.grid-stack`/`.grid-stack-item` 与基础 CSS import 不标记 |
| NO-OP | 保持输入 | `=版本`、比较器/OR/hyphen/wildcard、prerelease/build、协议/alias/Git/URL、未列/目标/未来版本、override/resolution、lockfile、非 `package.json`、相似包名及无所有权的同名 API |

严格依赖配方只检查 `package.json` 根对象下的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。workspace 子包的真实 manifest 会各自处理，但 `workspace:` 不会展开。审查 patch 后应使用工程原包管理器重建 lockfile。

推荐配方先执行所有可证明等价的 AUTO，再为剩余决策放置 MARK。标记是迁移待办，不代表对应行为已经兼容。

## v4 → v12 不兼容修改点

| 版本跨度 | 处理与验证重点 |
| --- | --- |
| v4 | collision 与 drag-in/out heuristic 重写；`GridStackEngine` 的 collision/move 方法改为 option object，旧 `locked/move/resize/minWidth/...` wrapper 删除。访问 engine/private 字段只 MARK。 |
| v5 / v5.1 | nested grid 支持父子间拖入拖出与 `column:'auto'`；`registerEngine()` 可注册公开 engine；`minWidth` 曾改为 `oneColumnSize`。nested/engine 均需行为测试。 |
| v6 | D&D 改写为原生 MouseEvent/TouchEvent，删除 HTML5 standalone 与 jQuery UI 构建选择；旧 h5/jq side-effect import 可安全删除，但 handle、Safari、移动端、nested drag、trash/accept 仅 MARK。 |
| v7 | 增加动态 sub-grid；需验证创建/移除、空 child、parent-child 拖动、序列化与事件。 |
| v8 | JS/TS 目标提升到 ES2020；`subGrid → subGridOpts` 可自动改名；`addRemoveCB` 移至全局，`dragInOptions` 删除，旧 `minWidth` 删除，CSS class/attribute 输出改变。全局 callback 与旧配置仅 MARK。 |
| v9 | `onParentResize() → onResize()` 是确定性改名；`sizeToContent` 会影响 widget 高度、resize 与 lazy rendering，仍需 UI 回归。 |
| v10 | `disableOneColumnMode`、`oneColumnSize`、`oneColumnModeDomSort` 删除，responsive 改为显式 `columnOpts.breakpoints`，且默认不再自动进入一列。只有 `disableOneColumnMode:true` 可等价删除；其他值需按业务 breakpoint/layout 设计。 |
| v11 | 为避免 XSS，不再把 widget `content` 写入 `innerHTML`；`addWidget()` 只支持 `GridStackWidget` object；增加 `GridStack.renderCB`，重写 side-panel `setupDragIn()` 与 lazy loading。需决定安全渲染、component ownership、callback 生命周期。 |
| v12 | column/cell height 改用 CSS variables，`gridstack-extra.css` 与自定义 column class 不再需要；v12.1 删除 ES5/IE，nested grid 事件传播到顶层；v12.2 增加 `updateCB`；v12.3 增加/修正 `save(column)` 与自定义数组数据保存。 |

### 必须人工确认的行为

- `GridStack.init/initAll/addGrid`：只能在浏览器 mount 后访问 DOM；验证 SSR import、hydration、route remount、`destroy()`、existing DOM 与多实例。
- `addWidget/update/removeWidget/removeAll`：验证 target signature、DOM 删除、`renderCB/addRemoveCB/updateCB` 顺序、framework component dispose、event 与 nested grid。
- `on/off`：验证 `added/removed/change` 批次、drag/resize element payload、`dropped` previous/new node、v12.1 nested event 冒泡及重复 workaround。
- `save/load`：区分可序列化 `GridStackWidget`、运行时 `GridStackNode` 和 DOM `gridstackNode`；统一 string `id`，验证 custom data、content、subGridOpts、column layout 与后端 DTO round-trip。
- D&D：验证 mouse/touch/Safari、handle、helper clone、accept/removable/trash、跨 grid/nested grid、side panel 与清理。
- CSS：保留目标基础 `gridstack.min.css`；删除 extra/custom column 规则后检查 `--gs-column-width`、`--gs-cell-height`、RTL、animation、content overflow 与生产 asset。

## 固定官方依据

- 目标 tag [`v12.3.3`](https://github.com/gridstack/gridstack.js/tree/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb) 固定 commit `cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb`；其 [`README.md`](https://github.com/gridstack/gridstack.js/blob/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb/README.md) 提供 v4-v12 完整迁移链，[`CHANGES.md`](https://github.com/gridstack/gridstack.js/blob/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb/doc/CHANGES.md) 固定目标 patch 行为，[`package.json`](https://github.com/gridstack/gridstack.js/blob/cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb/package.json) 固定 `main/types/files`。
- XLSX 源 tag 固定为 [`v4.2.6@2527c612`](https://github.com/gridstack/gridstack.js/tree/2527c6125f45234685113f1d272ff2863934ddae)、[`v5.1.1@68be754c`](https://github.com/gridstack/gridstack.js/tree/68be754cc0afd87d3e14503e0a0e805cfe80b3c8)、[`v6.0.0@75227e46`](https://github.com/gridstack/gridstack.js/tree/75227e4600e638ea159335a2cf4f2e143f0127f1)、[`v6.0.3@db36da5e`](https://github.com/gridstack/gridstack.js/tree/db36da5e7cc4dd5b596f4447a0f48e9eb17ebe09)、[`v7.1.1@daddce8b`](https://github.com/gridstack/gridstack.js/tree/daddce8b7808ea294b015501d1702d4c8de417cd)。

## 固定真实仓库用例

- [`pgregory/wetracker@e461f104`](https://github.com/pgregory/wetracker/blob/e461f104a01895bd9380213f14aea77d104b3f08/package.json)：`gridstack:^4.2.6`、jQuery/jQuery UI 与 Webpack；其 [`src/app.js`](https://github.com/pgregory/wetracker/blob/e461f104a01895bd9380213f14aea77d104b3f08/src/app.js) 验证删除 h5 D&D import、保留基础 CSS 和无关 jQuery。
- [`Hexa-ai/hexa-data@784f572b`](https://github.com/Hexa-ai/hexa-data/blob/784f572b5775b4a3789bb86ca12eaafb374740cc/ui/package.json)：Vue/Vite/TypeScript 与 `gridstack:^5.1.1`；[`DashboardGrid.vue`](https://github.com/Hexa-ai/hexa-data/blob/784f572b5775b4a3789bb86ca12eaafb374740cc/ui/src/components/DashboardGrid.vue) 覆盖 deep import、旧 D&D、`disableOneColumnMode`、`GridStackWidget` 与 `save()`。
- [`DevCloudFE/ng-devui@ef76c44e`](https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/package.json)：Angular 19 与 `gridstack:^6.0.0`；[`grid-stack.service.ts`](https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/devui/dashboard/grid-stack.service.ts) 覆盖 `DDGridStack`、`DDElement`、`Utils` 与内部 D&D/engine contract 标记。
- [`Taylor-CCB-Group/MDV@bdec64eb`](https://github.com/Taylor-CCB-Group/MDV/blob/bdec64ebaa69cb04f4277e7cc7919b7406a5bac7/package.json)：React/ESM 与 `gridstack:^7.1.1`；[`GridstackManager.ts`](https://github.com/Taylor-CCB-Group/MDV/blob/bdec64ebaa69cb04f4277e7cc7919b7406a5bac7/src/charts/GridstackManager.ts) 覆盖 responsive 旧配置、event、runtime node 与 `destroy(false)`。
- [`mxtommy/Kip@8b75977a`](https://github.com/mxtommy/Kip/blob/8b75977accb67929aa6d2ba32056c2d807be9820/package.json)：已使用 `gridstack:^12.3.3`，用于目标声明 no-op 边界。

测试结构固定参考 OpenRewrite [`rewrite@1b1804a5`](https://github.com/openrewrite/rewrite/commit/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 [`ChangeValueTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) / [`JsonPathMatcherTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [`ImportTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[`ObjectLiteralTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ObjectLiteralTest.java) 与 [`MethodInvocationTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)。用例覆盖 before→after、精确 marker、no-op、格式、comments、真实 fixture、两周期幂等及 recipe discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-gridstack-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.gridstack.MigrateGridStackTo12_3_3
```

审查 AUTO 与所有 `SearchResult` 后重建 lockfile，并运行 typecheck、lint、unit/component/E2E 及真实鼠标/触摸浏览器测试。重点覆盖 responsive/nested layout、add/remove/update、serialization round-trip、event ordering、safe rendering、framework teardown、SSR/hydration 和最终 CSS asset。

模块自身验证：

```bash
mvn -f rewrite-gridstack-upgrade/pom.xml clean verify
```
