# @angular/cdk 迁移到 20.2.14

本模块对应 `开源软件升级.xlsx` 中的 `@angular/cdk`。目标版本为 `20.2.14`；严格升级的源版本仅限表格中可见的：`10.2.6`、`10.2.7`、`11.2.13`、`12.2.10`、`12.2.13`、`13.1.3`、`13.3.1`、`13.3.9`、`14.0.6`、`14.2.0`。表格折叠显示“共 17 个版本”的其余值不可见，因此本模块不会猜测或扩大范围。

推荐配方：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularCdkTo20_2_14
```

它组合了严格依赖升级、确定性自动迁移和待人工确认项的 `SearchResult` 标记。也可以单独运行：

- `UpgradeAngularCdkTo20_2_14`：只升级表格可见版本。
- `MigrateDeterministicAngularCdkTo20`：只执行确定性 TypeScript/Sass 修改。
- `AuditAngularCdk20Source`：审计 TypeScript API。
- `AuditAngularCdk20Project`：审计依赖、工具链与 JSON 配置。
- `AuditAngularCdk20TemplatesAndStyles`：审计 HTML 与样式行为边界。

## AUTO / MARK / NO-OP

| 类别 | 处理内容 | 代表性测试 |
| --- | --- | --- |
| AUTO | `package.json` 四个直接依赖区中，表格可见版本的 exact、`^`、`~` 声明改为 `20.2.14` | 全部 10 个版本及 20 个前缀形式；四依赖区；根与嵌套 workspace |
| AUTO | 仅当本地绑定由显式 alias 保持不变时，修改 `DomPortalHost`→`DomPortalOutlet`、`PortalHost`→`PortalOutlet`、`BasePortalHost`→`BasePortalOutlet`、`ConnectedPositionStrategy`→`FlexibleConnectedPositionStrategy`、`CKD_COPY_TO_CLIPBOARD_CONFIG`→`CDK_COPY_TO_CLIPBOARD_CONFIG` | 每个 rename 的 AST 测试；无 alias、namespace、同名第三方 import 均不修改 |
| AUTO | 对类型或 `inject` 已证明为 `OverlayPositionBuilder` 的变量，将 `connectedTo` 改为 `flexibleConnectedTo` | 类型注解、import alias、inject 与同名本地对象反例 |
| AUTO | 仅从直接、类型已证明的 `DialogConfig` 对象字面量删除 `componentFactoryResolver` | typed direct property；untyped 和第三方同名类型反例 |
| AUTO | 仅从 Sass 可执行代码中 `@use`/`@forward` 的精确 `~@angular/cdk...` URL 删除 `~` | 单/双引号、use/forward；注释、`@import`、URL、变量和 Material 反例 |
| MARK | removed/changed portal、dialog、overlay、drag-drop、table、SelectionModel、Protractor、private/deep TypeScript API | 固定 API 参数化用例、boolean 返回值、private/protractor 用例 |
| MARK | Material/core/common、CLI/build、TypeScript、Node、RxJS、builder、global styles、SSR、strictTemplates、paths 配置 | package/workspace/tsconfig 精确 JSON member 用例 |
| MARK | overlay、drag/drop、virtual scroll、portal、table、focus、scrollable 模板，以及 high-contrast、overlay specificity、drag 样式 | HTML/CSS/SCSS/Sass/Less 参数化用例和注释反例 |
| NO-OP | 未列出的版本、复合范围、协议/alias/tag/变量、lockfile、overrides、metadata、相似包、目标或新版本 | `10.1.3` 真实 TaskBoard 反例及广泛边界矩阵 |

## 不兼容修改点

| 变化 | 本模块的处理与迁移要求 |
| --- | --- |
| 必须逐主版本运行官方 migrations | 依赖修改不能替代 `ng update @angular/cdk`；在 v10→…→v20 每一步检查并提交 schematics 输出和锁文件 |
| Material 与 CDK 版本耦合 | 标记非 `20.2.14` 的 `@angular/material`；同步运行 Material migrations 和视觉回归 |
| Sass webpack `~` 和旧 `@import` 退出 | 确定性的 `@use`/`@forward` URL 自动去 `~`；旧 `@import` 和其他上下文标记人工迁移 |
| overlay 旧 API | alias 安全的 `ConnectedPositionStrategy` import 和已证明 builder 的 `connectedTo` 自动迁移；其余标记 |
| v14 删除 Protractor harness entry point | 标记 `@angular/cdk/testing/protractor` 和 Protractor builder；迁到受支持 runner 并适配 HarnessEnvironment |
| v19 high-contrast 与 overlay 样式变化 | 标记 `cdk.high-contrast` 和 `.cdk-overlay-*` override；验证 forced-colors、CSS load order、specificity、stacking |
| v19 virtual scroll 模板检查增强 | 标记 virtual-scroll 模板及 `strictTemplates: false`；修复 item/context/trackBy 类型并回归 range/cache |
| v20 SelectionModel 返回 boolean | 精确标记已绑定实例的 `clear`、`deselect`、`select`、`setSelection`、`toggle`；检查 caller、mock 和 override |
| v20 DragDropRegistry 变化 | 标记泛型使用及被移除的 `scroll`；改用 `scrolled` 并验证订阅、auto-scroll、pointer/keyboard 行为 |
| v20 portal 名称和构造签名变化 | 安全 alias import 自动迁移；`PortalInjector` 改用 `Injector.create`；ComponentPortal/DomPortalOutlet 构造和 disposal 标记人工处理 |
| v20 dialog 变化 | typed direct `DialogConfig.componentFactoryResolver` 自动删除；spread/builder 及 scroll strategy provider 标记处理 |
| v20 table sticky 内部 API 删除 | 标记 `CanStick`、`CDK_TABLE_TEMPLATE`、`StickyDirection`、`StickyStyler` 等；迁到公开 CdkTable/column API |
| DOM、focus、overlay、drag/drop、SSR 行为跨版本变化 | 用浏览器、a11y、RTL、nested scroll、SSR/hydration、zoneless、视觉测试验证；SearchResult 保留在具体节点 |
| 运行时与工具链基线上升 | 标记 Angular 20 以下 core/common/CLI/build、TypeScript 5.8 以下、非支持 Node 和 RxJS 范围 |

## 固定来源与真实用例

- Angular Components `20.2.14` 固定提交 [`8c64a8da0d81f42556f33bb9319664555cb0a799`](https://github.com/angular/components/tree/8c64a8da0d81f42556f33bb9319664555cb0a799)：CDK manifest、changelog、exports 与 migrations 元数据。
- OpenRewrite `8.87.5` 固定提交 [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)：RewriteTest、JSON 和 SearchResult 测试模式。
- [`sliit-foss/sliitfoss@3d06f8e`](https://github.com/sliit-foss/sliitfoss/blob/3d06f8e2b94a9fc6ef0d77b3676bf490b4e9cd6c/package.json)：exact `10.2.6`。
- [`TIBCOSoftware/TCSTK-Angular@da04a8a`](https://github.com/TIBCOSoftware/TCSTK-Angular/blob/da04a8ac3553d0cf29eaf2361b4cf59cf4fec1d0/package.json)：caret `12.2.13`。
- [`onosproject/aether-roc-gui@d344536`](https://github.com/onosproject/aether-roc-gui/blob/d344536843f81ff673e999f210cc7af0a2ca632a/package.json)：tilde `12.2.13`。
- [`DSpace/dspace-angular@8410074`](https://github.com/DSpace/dspace-angular/blob/8410074a9e74654a260d000a89b6f6fe1fd54167/package.json)：目标 CDK 20.2.14 no-op。
- [`kiswa/TaskBoard@857583e`](https://github.com/kiswa/TaskBoard/blob/857583e4bb508c7b449a8e45bb0747d22d88abdb/package.json)：未列出的 `^10.1.3` no-op，防止扩大表格范围。
- [`superannotateai/supernova@9587680`](https://github.com/superannotateai/supernova/blob/95876804c037b73306583dedf330c70f6bf199a7/projects/supernova/src/lib/components/message/message.ts)：旧 DomPortalOutlet/ComponentPortal 构造。
- [`ng-doc/ng-doc@b595004`](https://github.com/ng-doc/ng-doc/blob/b595004d8925b5c93ae56f82a6439cd10e5de0cb/libs/ui-kit/services/overlay/overlay.service.ts)：现代 overlay strategy。
- [`NG-ZORRO/ng-zorro-antd@7071edd`](https://github.com/NG-ZORRO/ng-zorro-antd/blob/7071edd3f72d3384ec73a329fd0d9dce3af67fc5/components/dropdown/context-menu.service.ts)：Overlay 与 TemplatePortal 组合。
- [`weave-framework/weave@c00c015`](https://github.com/weave-framework/weave/blob/c00c01578efda9cc08d49b258b055fe339844898/packages/ui/src/cdk/positioning.ts)：非 Angular 同名 strategy no-op，防止误改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-cdk-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularCdkTo20_2_14
```

模块独立验证：

```bash
mvn -f rewrite-angular-cdk-upgrade/pom.xml clean verify
```

检查 patch 后，逐主版本运行 `ng update @angular/cdk`；使用 Material 时同步升级。重建锁文件，并执行 production build、unit/E2E、test harness、a11y、overlay/dialog、drag/drop、virtual-scroll、table、SSR/hydration 与视觉回归。
