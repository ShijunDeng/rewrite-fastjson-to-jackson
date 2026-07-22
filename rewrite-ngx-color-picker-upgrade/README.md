# ngx-color-picker 升级到 20.1.1

本模块对应 `开源软件升级.xlsx` 中 npm 包 `ngx-color-picker` 的两个明确映射：Excel 第 386、387 行（序号 385、386），分别把 `13.0.0`、`14.0.0` 升级到 `20.1.1`。配方不会推断或顺带处理表格中没有列出的版本。

推荐使用完整迁移配方：

```text
com.huawei.clouds.openrewrite.ngxcolorpicker.MigrateNgxColorPickerTo20_1_1
```

只修改依赖声明时使用：

```text
com.huawei.clouds.openrewrite.ngxcolorpicker.UpgradeNgxColorPickerTo20_1_1
```

## Spec、配方能力与测试映射

| 不兼容点 | 配方行为 | 测试证据 |
| --- | --- | --- |
| 表格仅列出 `13.0.0`、`14.0.0` | **AUTO**：四个根级直接依赖区中的精确、caret、tilde 声明写成 `20.1.1` | 2 版本 × 4 依赖区、4 个 anchored 值及真实 manifest 用例 |
| 19.x 删除 `ColorPickerModule` | **AUTO**：来源与全部用途可证明时，named import 改成 `ColorPickerDirective`，同步 Angular `imports`/`exports` | NgModule、standalone component、TestBed、alias、真实仓库、两轮幂等 |
| wrapper、spread、额外引用、双导入、type-only、re-export | **NOOP + MARK**：不猜测重写；在残留 import specifier 和引用标记 | 歧义矩阵、动态 wrapper、同名冲突、残留引用精确 marker |
| standalone directive/component/helper 被放在 `declarations` | **MARK**：标记实际数组元素，要求移到 `imports` | alias declaration 与外部同名负例 |
| `ngx-color-picker/**` 私有深度导入 | **MARK**：标记 module specifier | 根入口/noise 对照和 deep import marker |
| 20.1.1 peer 要求 Angular common/core/forms `>=19.0.0` | **MARK**：在实际 manifest 值标记低版本或无法证明的复杂约束 | Angular 包矩阵、workspace/catalog/range、兼容目标负例 |
| Angular 19+ 的 TypeScript、Node 运行线 | **MARK**：低版本和复杂约束均留人工清单，不替单包配方升级整套工具链 | 标量边界、复杂 range、兼容 manifest、engine marker |
| 目标 `SliderDirective` 删除 `slider` input | **MARK**：仅在已导入本包的 TypeScript inline template 上标记 `[slider]`/`bind-slider` | package-proven inline template 与同名业务组件负例 |
| central owner、协议、lockfile、生成/安装/缓存目录 | **NOOP；central owner MARK** | overrides/resolutions/pnpm、协议/range、lockfile、22 类目录负例 |

## 配方实际执行的修改

### 1. 严格升级直接依赖

`UpgradeSelectedNgxColorPickerDependency` 使用 JSON AST，只接受根对象下四个直接依赖区：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

接受值仅为：

```text
13.0.0  ^13.0.0  ~13.0.0
14.0.0  ^14.0.0  ~14.0.0
```

全部写成精确目标 `20.1.1`。它不会修改：

- `13.0.1`、`14.0.1`、15–20 的其它版本或目标版本；
- comparator、OR、hyphen、wildcard、prerelease、build metadata、tag、变量；
- `workspace:`、npm alias、`file:`、`link:`、`portal:`、`catalog:`、Git/GitHub/tarball URL；
- `overrides`、`resolutions`、`pnpm.overrides`、`dependenciesMeta` 或自定义嵌套对象；
- `package-lock.json`、pnpm/Yarn lockfile、普通 JSON、相似包名；
- `generated*`、`install*`、node_modules、包管理器缓存、构建/测试报告目录中的 manifest。

目录过滤只检查父目录组件，统一转小写后比较；源码叶文件 `install.ts`、`generated.ts` 不会因文件名被误排除。

### 2. `ColorPickerModule` 到 standalone directive

从 19.x 起，官方删除了 `ColorPickerModule` 和根入口导出。20.1.1 官方 README 的使用方式是直接导入 `ColorPickerDirective`：

```typescript
// before
import { ColorPickerModule } from 'ngx-color-picker';

@NgModule({
  imports: [ColorPickerModule],
  exports: [ColorPickerModule]
})

// after
import { ColorPickerDirective } from 'ngx-color-picker';

@NgModule({
  imports: [ColorPickerDirective],
  exports: [ColorPickerDirective]
})
```

同样处理 standalone `@Component({ imports: [...] })` 和 `TestBed.configureTestingModule({ imports: [...] })`。named import alias 会保留：

```typescript
import { ColorPickerModule as PickerImports } from 'ngx-color-picker';
// becomes
import { ColorPickerDirective as PickerImports } from 'ngx-color-picker';
```

自动迁移采用保守证明边界：

1. 文件中恰好有一个来自精确根入口 `ngx-color-picker` 的 runtime named `ColorPickerModule` import；
2. 不存在 `ColorPickerDirective` import 或同名局部符号；
3. 本地 module 符号的所有出现都能由 import 和 Angular 直接 `imports`/`exports` 数组元素解释；
4. 属性必须直接属于 `@NgModule`、`@Component` 或 `TestBed.configureTestingModule` 的对象；
5. 至少存在一个确定的 Angular scope 引用。

因此动态数组、spread、nested wrapper、普通对象的 `imports` 字段、namespace/type-only/re-export、额外业务引用和同时导入新旧 API 不会被文本替换误伤。推荐配方在这些残留节点留下 `SearchResult` marker。

目标 module 原先只导出 `ColorPickerDirective`；服务 `ColorPickerService` 在旧 module 中没有 module-scoped provider。因此对满足证明条件的消费方，用 standalone directive 替换 module 可以保持其模板可见能力。若应用主动使用 `ColorPickerComponent`、`SliderDirective` 或 `TextDirective`，必须分别把实际需要的 standalone 声明加入作用域，配方不会根据间接文本猜测。

### 3. 可执行兼容性审计

推荐配方在升级和确定性源码迁移后执行两个搜索配方：

- manifest 审计定位 Angular 低版本/复杂所有权、TypeScript/Node 基线、未选择的本包约束和 central owner；
- TypeScript 审计定位残留 module、私有深度导入、standalone 声明误放 `declarations`、以及 package-proven inline template 中已删除的 `slider` input binding。

marker 位于实际 JSON value、import specifier、identifier、module specifier、数组元素或 template literal，不会只在整个文件或上层对象上给出模糊提示。

## 主要不兼容修改点

### Angular 19+ 是安装硬边界

`ngx-color-picker@20.1.1` 的已发布 manifest 声明：

```json
{
  "peerDependencies": {
    "@angular/common": ">=19.0.0",
    "@angular/core": ">=19.0.0",
    "@angular/forms": ">=19.0.0"
  }
}
```

13.0.0、14.0.0 的发布物只要求 Angular `>=9.0.0`，所以原工程可能仍处于 Angular 13–18。不能只提升 color picker 并依赖 `--legacy-peer-deps` 掩盖冲突；必须把 framework、CLI、compiler-cli、builder、Material/CDK、TypeScript、Node、Zone.js、SSR 和测试工具作为兼容集合迁移。

目标 peer 没有上界，且官方 20.1.1 仓库用 Angular 20 构建。配方因此接受 manifest 中能证明为 19 或更高的简单标量，但不会自动选择 Angular 19 还是 20；复杂 range、workspace/catalog 所有权均 MARK，留给 Angular 专项迁移或人工决定。

### standalone 默认值与 scope

官方 standalone 提交删除了 library module，并让 component/directives 使用 Angular 19 的 standalone 默认。需要检查：

- 传统 NgModule：standalone directive 应放 `imports`，可以从 `exports` 转出，不能放 `declarations`；
- standalone component：在组件自己的 `imports` 中加入 `ColorPickerDirective`；
- TestBed、Storybook、lazy route 和 library wrapper：每个独立编译作用域都要直接可见；
- 自定义 `SharedModule` 的公共导出：替换后重新编译所有消费方，不以根应用编译通过代替 lazy boundary 验证。

### `SliderDirective.slider` input 被删除

13/14 的公开 `SliderDirective` 同时用 `[slider]` 作为 selector 和一个未参与实现的 `@Input() slider`。目标仍保留 selector，但删除了该 input；库自己的模板从 `[slider]` 改为 bare `slider`。外部若直接消费这个 helper：

```html
<!-- legacy binding: target may report unknown input -->
<div [slider]="mode" [rgX]="1"></div>

<!-- target selector form -->
<div slider [rgX]="1"></div>
```

外部 HTML 文件本身不能证明 `slider` 属于本包，因此配方不会全仓替换同名属性。只有 TypeScript inline template 且同文件已使用 `ngx-color-picker` 时才 MARK；普通业务 slider 保持无标记。

### 颜色输入和浏览器行为需要回归

20.x 的维护提交删除了 hex 输入框把 3 位颜色主动展开为 6 位的步骤，并清理 IE10 检测、document/window 访问和依赖注入实现。公开 directive 的主要 inputs/outputs 仍在，但跨七个 major 应回归：

- `#abc`、`#aabbcc`、8 位 hex、alpha `always/forced/disabled` 的输入、展示与输出格式；
- `cpFallbackColor`、非法值、空值和双向 `[(colorPicker)]` 的恢复行为；
- popup/inline、auto position、arrow offset、页面滚动和窗口 resize；
- `cpUseRootViewContainer`、overlay、Shadow DOM、SSR/hydration 与组件销毁；
- OK/Cancel、click-outside、preset add/remove、EyeDropper、touch/mouse drag；
- OnPush/zoneless、异步 EventEmitter、表单更新和重复 change detection。

这些行为依赖应用状态、DOM/CSS 和选择的 Angular major，没有跨项目通用的安全源码替换，因此 README 给出验证 spec，配方只在存在确定静态证据的节点进行 AUTO/MARK。

## 自动与人工迁移边界

本模块会自动升级白名单依赖并迁移可证明的 Angular scope，不会自动：

- 选择或执行 Angular 13/14/15/16/17/18 到 19/20 的 framework migration；
- 修改 lockfile、执行包管理器安装或绕过 peer dependency；
- 猜测 spread/wrapper/module factory 的运行时结构；
- 把私有 deep import 映射到某个公共符号；
- 全仓替换同名 `slider`、颜色字符串、CSS 或 DOM 逻辑；
- 证明 SSR、hydration、overlay、touch、颜色格式和业务表单行为。

## 固定官方证据

- [13.0.0 源标签 @ 45d685f7](https://github.com/zefoy/ngx-color-picker/tree/45d685f731de7b993bfa456b26f8483e9cbc8e3c)：旧根 API 包含 `ColorPickerModule`；
- [14.0.0 源标签 @ 557140f1](https://github.com/zefoy/ngx-color-picker/tree/557140f10b24023da505f3dec74621c3ac51b250)：表格第二个源版本；
- [20.1.1 目标标签 @ aa11d798](https://github.com/zefoy/ngx-color-picker/tree/aa11d79859e545f8943c00030cef1df4b63d2043)：目标源码、README 和 package manifest；
- [standalone 删除提交 @ b7c2b82a](https://github.com/zefoy/ngx-color-picker/commit/b7c2b82afe08d5883eb8a773eddb0c3f596be1df)：删除 `ColorPickerModule`，更新 app scope；
- [20.1.1 peer 下限提交 @ 566dc8c2](https://github.com/zefoy/ngx-color-picker/commit/566dc8c2efc26c3bbbc2d4585e2d689c31202e25)：common/core/forms 更新为 `>=19.0.0`；
- [3 位 hex 处理提交 @ 39762d9c](https://github.com/zefoy/ngx-color-picker/commit/39762d9c10e3a2e91884585ff8fa51cc22e0cecd)：移除输入框主动扩展步骤；
- [deprecated/DOM 清理提交 @ 83f9922b](https://github.com/zefoy/ngx-color-picker/commit/83f9922b496bf8e5947df1c1fa31dbad96ac1733)：helper input、DOCUMENT/window 等实现调整；
- [Angular 官方版本兼容表](https://angular.dev/reference/versions)：Angular 19/20 对 Node、TypeScript 和 RxJS 的精确范围；
- npm 13.0.0 tarball：`https://registry.npmjs.org/ngx-color-picker/-/ngx-color-picker-13.0.0.tgz`，integrity `sha512-3mgMbs21KeqnmmY5p1cn71ckTH3q7gBt6Qn0fMfeF/Ql7ddTZsW4Z7Z8ga6LymMP/ugooGuLOFX+V6yx0dDxAw==`；
- npm 14.0.0 tarball：`https://registry.npmjs.org/ngx-color-picker/-/ngx-color-picker-14.0.0.tgz`，integrity `sha512-w28zx2DyVpIJeNsTB3T2LUI4Ed/Ujf5Uhxuh0dllputfpxXwZG9ocSJM/0L67+fxA3UnfvvXVZNUX1Ny5nZIIw==`；
- npm 20.1.1 tarball：`https://registry.npmjs.org/ngx-color-picker/-/ngx-color-picker-20.1.1.tgz`，integrity `sha512-wFk1L9F3JBd1Th+auYexN4uyynaT2dhxVRn0Dd55HrhfVcxfylExITDJ+Bhrcl0W+QHtX6gVbfZuX2hBXeVMFA==`。

## 真实公开仓库测试来源

测试保留公开仓库的实际依赖版本和 Angular scope 形态，并固定到不可变 commit：

- [tensorflow/tensorboard @ 1b86d2d5 package.json](https://github.com/tensorflow/tensorboard/blob/1b86d2d579ad6b0c2554cc0f775d50f567e78ffb/package.json) 使用精确 `13.0.0`；[runs_table_module.ts](https://github.com/tensorflow/tensorboard/blob/1b86d2d579ad6b0c2554cc0f775d50f567e78ffb/tensorboard/webapp/runs/views/runs_table/runs_table_module.ts) 在大型 NgModule imports 中使用 `ColorPickerModule`；
- [frangoteam/FUXA @ 2ec5d15d client/package.json](https://github.com/frangoteam/FUXA/blob/2ec5d15dce43d6ca6d1642d754432e1f0d0c46b9/client/package.json) 使用 `^13.0.0`；[app.module.ts](https://github.com/frangoteam/FUXA/blob/2ec5d15dce43d6ca6d1642d754432e1f0d0c46b9/client/src/app/app.module.ts) 覆盖大型根 module；
- [toniskobic/dragon-drop @ 27448e5f package.json](https://github.com/toniskobic/dragon-drop/blob/27448e5f53806b079a3f9435e91bac6213299e2e/package.json) 使用 `^14.0.0`；[theme-colors-picker.component.ts](https://github.com/toniskobic/dragon-drop/blob/27448e5f53806b079a3f9435e91bac6213299e2e/src/app/components/theme-colors-picker/theme-colors-picker.component.ts) 覆盖 standalone component 中的旧 module；
- [dres-dev/DRES @ bd600c8d frontend/package.json](https://github.com/dres-dev/DRES/blob/bd600c8d3e586e92356e9362d68634eb365e8a85/frontend/package.json) 使用 `^14.0.0`；[competition-builder.module.ts](https://github.com/dres-dev/DRES/blob/bd600c8d3e586e92356e9362d68634eb365e8a85/frontend/src/app/competition/competition-builder/competition-builder.module.ts) 覆盖传统 feature NgModule。

测试风格参考 OpenRewrite 8.87.5 固定提交中的 [ChangeValueTest @ b3008cc4](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [FindTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindTest.java)，TypeScript import/alias/lossless 用例参考 [rewrite-javascript ImportTest @ 9e3b820e](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)。本模块当前有 111 个测试：82 个依赖矩阵、路径/协议负例与依赖幂等场景，11 个 AST 自动迁移/真实仓库/幂等场景，18 个 marker、公开配方边界与组合配方场景。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-color-picker-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxcolorpicker.MigrateNgxColorPickerTo20_1_1
```

审查 patch 和所有 `SearchResult` 后，用原包管理器重建 lockfile。至少执行：

1. 不带 `--legacy-peer-deps` 的 clean install；
2. Angular production/AOT/strict build、lint 和 unit tests；
3. NgModule、standalone、TestBed、Storybook、lazy route 编译；
4. popup/inline、auto position、root container、overlay、resize、scroll；
5. hex/rgba/hsla/cmyk、alpha、invalid/fallback、preset 和表单双向绑定；
6. mouse/touch、OK/Cancel、click-outside、EyeDropper；
7. SSR render、hydration、client navigation 和销毁重建。

模块自身验证：

```bash
mvn -f rewrite-ngx-color-picker-upgrade/pom.xml clean verify
```
