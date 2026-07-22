# angular-gridster2 升级到 20.2.4

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `angular-gridster2`，精确处理 `12.1.1`、`13.3.0`、`13.3.2`、`16.0.0` 到 `20.2.4` 的依赖、TypeScript 和模板迁移。

推荐使用的复合配方：

```text
com.huawei.clouds.openrewrite.angulargridster2.MigrateAngularGridster2To20_2_4
```

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.angulargridster2.UpgradeAngularGridster2To20_2_4` | 只升级表格指定的直接依赖声明 |
| `com.huawei.clouds.openrewrite.angulargridster2.MigrateStandaloneGridsterModuleToComponents` | 只转换严格匹配的 standalone component |
| `com.huawei.clouds.openrewrite.angulargridster2.MigrateDeterministicAngularGridster2SourceTo20` | 只执行可以证明边界的 TS/HTML 改写 |
| `com.huawei.clouds.openrewrite.angulargridster2.FindManualAngularGridster2To20MigrationRisks` | 只生成需要人工审查的 `SearchResult` |
| `com.huawei.clouds.openrewrite.angulargridster2.MigrateAngularGridster2To20_2_4` | 按“依赖→确定性源码→风险搜索”执行以上配方 |

## 自动处理范围

### 依赖声明

依赖配方只扫描根目录和 workspace 子目录的 `package.json`，并且只修改 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中名称精确为 `angular-gridster2` 的直接声明。

表格四个版本的精确值，以及明确锚定这些版本的 caret、tilde、equal、`v`/`^v` registry 声明，会统一写成精确版本 `20.2.4`。专用 OpenRewrite JSON LST visitor 同时检查文件名、直属依赖区、键、标量类型和完整版本；不会把数组中的同值字符串、lockfile 或嵌套 package-manager metadata 当成直接依赖。

配方有意保留：

- 未列版本、目标/更高版本、comparator、hyphen/OR range、prerelease、build metadata、tag 和变量；
- `workspace:`、catalog、npm alias、`file:`、`link:`、`portal:`、Git/GitHub、URL/tarball；
- `overrides`、`resolutions`、`pnpm.overrides`、peer metadata 和所有 lockfile；
- Angular、Angular Material/CDK、RxJS、Zone.js、TypeScript、CLI、builder、测试框架和第三方 dashboard 包；
- 相似包名以及普通 JSON、lockfile 和非字符串 dependency 值。

目标 `angular-gridster2@20.2.4` 的 peer dependency 是 `@angular/common`/`@angular/core ^20.0.0` 和 `rxjs ^7.0.0`。在 Angular 12/13/16 工程中单独安装升级结果必然形成不兼容 peer 组合；必须先规划 Angular 的逐主版本迁移，再统一生成 lockfile。

### TypeScript 与模板

确定性源码配方只处理以下可以限定输入结构的变化：

- 目标根入口仍公开的类型、component、module、push/swap service 的已知 `angular-gridster2/lib/...` 深导入改到 `angular-gridster2`；未知内部 service 深导入不猜测，留给风险配方；
- `this.options.api.optionsChanged()`、已有 api guard 和部分 optional-chain 写法统一为 strict-safe 的 `this.options.api?.optionsChanged?.()`；
- 只有当同一 `.ts` 文件显式含 `standalone: true`、且 import 恰好只导入 `GridsterModule` 时，才把它和 component `imports` 数组项改为 `GridsterComponent`、`GridsterItemComponent`；NgModule 和混合 named import 不改；
- 只有 `<gridster-item>` 上形如 `*ngFor="let item of dashboard"` 的简单 identifier/property-path iterable 才改为 `@for (item of dashboard; track item)`；带 pipe、`trackBy`、index/别名或复杂表达式的循环不改。

例如：

```diff
-import { GridsterConfig } from 'angular-gridster2/lib/gridsterConfig.interface';
+import { GridsterConfig } from 'angular-gridster2';

-this.options.api.optionsChanged();
+this.options.api?.optionsChanged?.();

-<gridster-item [item]="item" *ngFor="let item of dashboard">
-  <app-widget [widget]="item"></app-widget>
-</gridster-item>
+@for (item of dashboard; track item) {
+  <gridster-item [item]="item">
+    <app-widget [widget]="item"></app-widget>
+  </gridster-item>
+}
```

`@for ... track item` 保留旧 `NgFor` 默认的对象 identity 跟踪语义；它不是针对具有自定义 `trackBy` 的机械替代。源码配方基于严格限定的文本模式，因为 OpenRewrite 当前没有供本模块使用的稳定 TypeScript/Angular template 语义 AST，必须审查 dry-run diff。

## 不兼容修改点

angular-gridster2 的主版本基本跟随 Angular 主版本。本次跨度的主要风险不是某一个 Gridster 方法改名，而是 Angular 运行时、编译器、包格式和应用架构一起跨越 12→20。

| 变化 | 影响与迁移建议 |
| --- | --- |
| Angular peer 基线 | 源版本分别面向 Angular 12、13、16，目标只接受 Angular 20。使用 `ng update` 按 12→13→…→20 逐段执行并在每一段编译/测试；不要直接覆盖所有 `@angular/*` 字符串，因为 CLI migration 会改代码、配置和 builder |
| RxJS 基线 | 12/13 项目可能仍在 RxJS 6，目标要求 RxJS 7。检查已移除/弃用操作符、`toPromise`、scheduler、subscription teardown、测试 marble 和 peer duplication；不要让 workspace 同时加载多个 RxJS 实例 |
| TypeScript、Node 与 CLI | Angular 20 对 Node/TypeScript 有严格支持窗口。按 Angular 官方版本兼容表统一本地、CI、容器、IDE language service、test runner 和 build image；只提升库而保留旧 CLI/compiler 会在安装或 partial compilation 阶段失败 |
| Ivy 与发布格式 | Angular 13 起不再支持旧 View Engine 库；后续 Angular Package Format 只保留现代 ESM/FESM。自有组件库必须用匹配 compiler 重新构建，删除 ngcc workaround、旧 UMD/bundles 深路径和对 `__ivy_ngcc__` 缓存的依赖 |
| 目标包入口 | 20.2.4 发布 `fesm2022/angular-gridster2.mjs`、根 `exports` 与类型文件，并声明 `sideEffects: false`。只从 `angular-gridster2` 公共入口导入；`dist/**`、`bundles/**`、内部 service/interface 文件等深导入可能无法由 exports 解析 |
| Standalone 组件 | 目标公开的 `GridsterComponent`/`GridsterItemComponent` 可直接用于 standalone component 的 `imports`；目标也仍公开 `GridsterModule`。不要使用较新主版本文档中的 `Gridster`/`GridsterItem` 别名推断 20.2.4 API，也不要在同一 component 重复通过 module 和 standalone components 注册 |
| 模板控制流 | 旧 `*ngFor` 模板可先保持，Angular 20 项目可再迁移到 `@for`。迁移时必须提供稳定 track 表达式，避免 dashboard 重排时销毁/重建 GridsterItem，造成拖拽状态、事件和内嵌组件丢失 |
| responsive breakpoint | 目标默认按 Gridster 元素宽度决定 mobile breakpoint，新增 `useBodyForBreakpoint` 可恢复按 body 宽度判断。嵌套容器、侧栏、微前端和 ResizeObserver 场景可能在不同宽度切换 mobile，需要视觉/E2E 回归 |
| 配置与布局能力扩展 | 目标配置增加 `CompactGrid`、`itemAspectRatio`、`addEmptyRowsCount`、`enableBoundaryControl` 等能力。升级不会自动开启；自有配置 mapper 若使用白名单、schema 或 `satisfies GridsterConfig`，需决定是否暴露并验证默认值 |
| item resize handles | 目标支持 item 级 `resizableHandles` 与 grid 级 handles 合并。自有 DTO 序列化、服务端 dashboard schema、clone/equality 和迁移脚本应允许新字段，同时保持旧数据可读 |
| 私有 API 变化 | 内部 `calculateLayoutDebounce()` 已演进为 RxJS `calculateLayout$`，component/service 的内部实现和类型持续变化。业务不得调用私有 gridster component、renderer、drag/resize service；使用 `options.api.optionsChanged()` 等公开入口 |
| options 更新 | 修改嵌套 options 后仍要显式调用 `options.api?.optionsChanged?.()` 或替换配置引用。Signals/OnPush/zoneless 迁移不会自动让第三方 mutable options 具备响应性，应为布局刷新写明确测试 |
| event 与 callback 生命周期 | `itemChange`、`itemResize`、init/remove、drag/resize start/stop 等可能在布局重算、mobile 切换或 Angular change detection 下改变次数和时机。callback 不应假定只调用一次，也不要在回调里同步触发无限 options/layout 循环 |
| scrolling 行为 | 新实现对新 item 的 `scrollIntoView` 使用平滑、nearest/end 选项。设置 `scrollToNewItems` 的应用要回归嵌套滚动容器、reduced-motion、focus、虚拟滚动和 E2E timing |
| drag、touch 与 iframe | drag handle、ignore content、empty-cell drop、touch/mouse/pointer 与 iframe 仍需真机验证。模板内部交互控件应阻止不需要的拖拽起始事件，同时保持键盘和可访问性；不要仅用桌面鼠标测试 |
| CSS 和容器尺寸 | Gridster 会占满父容器，不会按内容自动决定外层高度。Angular Material layout、flex/grid、Shadow DOM、micro-frontend style isolation 和 SSR hydration 后都要保证父级有确定尺寸，并回归 margin、transform、RTL 和 mobile stack |
| SSR/hydration | drag/resize 和元素测量依赖浏览器 DOM。SSR 工程应延迟布局交互到 browser，避免服务端读取 window/document，并验证 hydration 后首次测量、route reuse、destroy/recreate 和 event cleanup |
| Angular Material/CDK | 表格中的真实应用经常同时使用 Material/CDK。它们也必须升级到 Angular 20 支持线；overlay、drag-drop、MDC 样式和 Gridster pointer/z-index 可能相互影响，但本配方不会猜测联动版本 |

## 自动迁移与人工迁移边界

风险配方先要求文件中存在 `angular-gridster2` 或 `<gridster>`，再按类别标记剩余深导入、复杂循环、布局/compact/push/swap、drag/resize/empty-cell、responsive breakpoint、浏览器全局和私有 API。它不会自动更改 GridsterConfig 数值、dashboard DTO、callback、CSS、SSR guard 或持久化布局。

常见标记包括：

- 布局：`gridType`、`compactType`、`setGridSize`、push/swap 与 `get*PossiblePosition`；
- 交互：`draggable`、`resizable`、drag handle、ignore content、empty-cell drop 和模板 pointer/touch handler；
- responsive/SSR：`mobileBreakpoint`、fixed mobile 尺寸、`ResizeObserver`、`window`、`document`、platform/hydration guard；
- 私有 API：`calculateLayoutDebounce`、`calculateLayout$`、`$options`、内部 renderer/尺寸字段、剩余深导入；
- standalone/control flow：没有满足严格自动条件的 `GridsterModule` 和带 pipe/trackBy/局部变量的复杂 `*ngFor`。

`~~>` 表示“需要查看”，不表示该代码一定错误。配方不会自动运行 Angular CLI migrations；Angular 12→20 的 compiler、builder、SSR 和 control-flow migrations 仍必须逐主版本执行。

建议顺序：先在当前版本记录 dashboard save/load、布局截图和交互 E2E；逐主版本执行 Angular update；对齐 RxJS/Material/CDK/TypeScript/Node；升级 angular-gridster2；最后审查 standalone、responsive、私有 API 和新配置字段。

## spec → 行为 → 测试矩阵

| 迁移 spec | 配方行为 | 测试证据 |
| --- | --- | --- |
| 只升级表格四个版本 | 四个 direct dependency 区中的 24 种白名单 registry 字符串写为 `20.2.4` | 四个真实 manifest、四区组合、workspace、全部 exact/prefix 参数化测试 |
| 不误改 package metadata | 协议、alias、range、override、lockfile、普通 JSON、null/object/number/boolean/array 保持 | protocol/range/no-op 测试及 array value/array-shaped section 回归 |
| 消除目标 exports 拒绝的已知深导入 | 仅把目标 public API 仍导出的十类内部文件归一到根入口 | 十个参数化 before→after，未知/非源码扩展名保持 |
| 兼容 strict optional API | `this.options.api.optionsChanged()` 和真实 guard 统一为双 optional chain | HyperIoT 与 SoSTrades 固定 commit 的 TS before→after |
| 明确 standalone 转换 | 仅 exact `GridsterModule` import + 显式 standalone 文件转换两个 component imports | standalone before→after；NgModule、混合 import no-op |
| 保持简单 NgFor identity 行为 | 简单 Gridster item loop 改为 `@for (...; track item)` | HyperIoT 固定 HTML before→after |
| 不猜测复杂模板 | pipe、trackBy、局部变量/复杂表达式不改并标记 | fischertechnik async pipe 固定 HTML marker；complex no-op |
| 不猜测布局与交互语义 | 布局、drag/resize/empty-cell 配置只生成 marker | HyperIoT compact/responsive 与 SoSTrades drag 固定源码 marker |
| 不猜测 SSR/hydration | browser global、ResizeObserver 与 SSR guard 只生成 marker | fischertechnik `window.ResizeObserver` 固定源码 marker |
| 一次运行完成受控迁移 | composite 按依赖、source、risk 顺序组合 | 同一测试验证 JSON 更新、optionsChanged 改写和 breakpoint marker |

## 官方依据

- [angular-gridster2 v20.2.4 README](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/README.md)：目标公共导入、模板、options API、父容器尺寸和交互说明；
- [目标 package manifest](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/package.json)：Angular 20/RxJS 7 peer 与发布元数据；
- [v12.1.1→v20.2.4 官方源码比较](https://github.com/tiberiuzuld/angular-gridster2/compare/v12.1.1...v20.2.4)：standalone、配置、resize handles、responsive、scroll 和内部 API 演进；
- [目标 GridsterConfig](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/src/lib/gridsterConfig.interface.ts) 与 [GridsterItem types](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/src/lib/gridsterItem.interface.ts)：公开配置和 item contract；
- [目标默认配置](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/src/lib/gridsterConfig.constant.ts) 与 [responsive 实现](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/src/lib/gridster.component.ts)：element/body breakpoint、布局刷新和内部 RxJS 实现；
- Angular 官方 [Update Guide](https://angular.dev/update-guide) 与 [version compatibility](https://angular.dev/reference/versions)：逐主版本迁移以及 Node/TypeScript/RxJS 支持窗口。

## 真实仓库测试来源

测试固定到四个公开仓库 commit，覆盖表格每个源版本并保留真实 Angular/RxJS 组合和源码形态：

- [HyperIoT-UI @ da8e6ebd](https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/package.json)：Angular 12.2、`~12.1.1`，以及真实 [widgets TS](https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts) 和 [template](https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.html)；
- [ERNI starterkit @ 72188b3c](https://github.com/ERNI-Academy/starterkit-angular-and-dotnet-api/blob/72188b3cb657b4a7d318f4e29ae751995d5efe5b/src/App.UI/package.json)：Angular 13.2、RxJS 6 与 `^13.3.0`；
- [fischertechnik Agile Production @ 24568073](https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/package.json)：Angular 13.4、RxJS 7、`^13.3.2` 和真实 [factory layout](https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts)；
- [SoSTrades WebGUI @ fe148be3](https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/package.json)：Angular 16.2、Material、RxJS 7、`^16.0.0` 与真实 [dashboard component](https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/src/app/modules/dashboard/dashboard.component.ts)。

95 个测试调用参考 OpenRewrite 固定提交 `b3008cc4` 的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)、[JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 和 [FindAndReplaceTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java)，覆盖四个真实 manifest、三套真实 source/template、表格全部版本、四个依赖区、20 种安全声明、workspace、严格 no-op、十类 deep import、standalone、simple/complex control flow、五类 marker、配方发现与 composite 行为。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-gridster2-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angulargridster2.MigrateAngularGridster2To20_2_4
```

确认 patch 后先完成 Angular 20 迁移和 peer 对齐，再重建 lockfile。运行 typecheck、lint、unit、dashboard save/load round-trip、drag/resize/touch、responsive/mobile、RTL、SSR/hydration、视觉快照、production bundle 和浏览器 E2E；特别覆盖父容器 resize、route remount、optionsChanged、nested scrolling、interactive widget 与销毁清理。

本模块自身验证：

```bash
mvn -f rewrite-angular-gridster2-upgrade/pom.xml clean verify
```
