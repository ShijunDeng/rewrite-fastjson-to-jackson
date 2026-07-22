# @ng-dynamic-forms/core 升级到 18.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ng-dynamic-forms/core`。它严格升级表格可见版本，自动清理一个可证明等价的遗留模块包装，并把 Angular 16、Untyped Forms、standalone renderer、Kendo 移除、模型/服务和模板作用域风险标记在具体 JSON value、import、call、constructor、class、metadata property 或 HTML opening tag 上。

## 表格边界

| 可见源版本 | 目标版本 | 自动接受的声明 |
| --- | --- | --- |
| `14.0.0` | `18.0.0` | `14.0.0`、`^14.0.0`、`~14.0.0` |
| `15.0.0` | `18.0.0` | `15.0.0`、`^15.0.0`、`~15.0.0` |
| `16.0.0` | `18.0.0` | `16.0.0`、`^16.0.0`、`~16.0.0` |
| `17.0.0` | `18.0.0` | `17.0.0`、`^17.0.0`、`~17.0.0` |

补丁版本、wildcard、comparator/hyphen/OR range、`v`/`=` 前缀、prerelease、build metadata、tag、变量和 protocol 引用不在表格授权边界内，因此不自动改写。

## 配方

只升级依赖：

```text
com.huawei.clouds.openrewrite.ngdynamicforms.UpgradeNgDynamicFormsCoreTo18_0_0
```

推荐的完整迁移清单：

```text
com.huawei.clouds.openrewrite.ngdynamicforms.MigrateNgDynamicFormsCoreTo18_0_0
```

推荐配方依次执行严格依赖升级、确定性源码清理、package 风险扫描、TypeScript/JavaScript 风险扫描和 HTML 模板风险扫描。

## AUTO / MARK / NO-OP 矩阵

| 输入 | 行为 | 说明 |
| --- | --- | --- |
| 四个直接依赖区中的表格白名单 exact/`^`/`~` | **AUTO**：改为精确 `18.0.0` | 4 个可见基准、12 种声明均有参数化测试 |
| 从 `@ng-dynamic-forms/core` 导入的无参数 `DynamicFormsCoreModule.forRoot()` | **AUTO**：改为 `DynamicFormsCoreModule` | 官方早已在 tree-shakeable root provider 改造后删除该 wrapper；import alias、NgModule 和 `importProvidersFrom` 均覆盖 |
| 带参数/非标准 `DynamicFormsCoreModule.forRoot(...)` | **NO-OP + MARK** | 参数可能代表自定义 provider 约定，不能丢弃 |
| Angular、RxJS、core-js、TypeScript、Node、UI renderer 与中央版本所有者 | **MARK**：标记具体 JSON value | 目标 core 的 peer 是 Angular 16、RxJS `^7.5.7`、core-js `^3.31.0`；Angular 16.1 compiler 约束 TypeScript `>=4.9.3 <5.2` |
| `DynamicForms*UIModule` import | **NO-OP + MARK**：标记具体 import specifier | v18 renderer 改为 standalone components；必须按实际模板选择 form/container/control imports，不能把所有 module 机械替换成一个 component |
| Kendo package/import/tag | **NO-OP + MARK**：标记 package value、module path 或 HTML tag | v18 明确停止 Kendo renderer，没有一一对应替代 |
| core deep import | **NO-OP + MARK**：标记 module literal | 迁移到 public root entry 前先确认目标仍公开该 symbol |
| `DynamicFormService` 的 create/mutate/find/fromJSON/detectChanges call | **NO-OP + MARK**：标记具体 call | UntypedFormGroup/Array、严格类型、序列化、OnPush、validators/relations 行为需业务回归 |
| `new Dynamic*Model(...)` 与自定义 core base-class extension | **NO-OP + MARK**：标记具体 constructor/class | 配置 shape、renderer `additional`、抽象成员、queries、DI 和事件不能机械迁移 |
| standalone core directive 仍放在 `declarations` | **NO-OP + MARK**：标记 metadata property | 需移动到对应 NgModule/TestBed 的 `imports`，但必须保留同数组其他 declarations 并处理重复 import |
| `dynamic-*-form/control`、`[dynamicList]`、`ng-template[modelId/modelType]` | **NO-OP + MARK**：标记精确 opening tag | selector 本身没有确定性改名；迁移发生在 standalone scope、renderer peers、projection 和测试配置中 |
| 未列出的 patch/range/protocol、central owner、nested metadata、lockfile/shrinkwrap、非 package JSON | **NO-OP**（推荐配方对可识别风险 MARK） | 不猜测授权范围，不覆盖集中版本策略，不修改生成文件 |
| 本地或其他包的同名 module/service/model/base class；被局部参数/类型遮蔽的导入名；普通 HTML/TS 字符串、HTML 注释及 script/style 原始块 | **NO-OP** | import-aware、作用域和文件/可见区域反例防止误报/误改 |

模板没有安全的字符串级 AUTO：v18 保留 selector，但删除 renderer NgModule 并把组件变成 standalone。正确修改依赖 TypeScript scope 和项目实际使用的 selector；配方因此保留模板，只在精确 tag 上生成迁移清单。

## 不兼容修改点

| 变化 | 迁移建议 |
| --- | --- |
| v15 迁移到 Angular 13 | 源为 v14 时按 Angular 官方流程先升级 framework、CLI、compiler-cli 和相关 UI；每个 major 分开构建/发布验证 |
| v16 迁移到 Angular 15 Untyped Forms | core service 返回/操作 `UntypedFormGroup`、`UntypedFormArray` 和 `UntypedFormControl`；检查显式 `FormGroup<T>`、casts、disabled/raw values、validators、arrays 和 strict template |
| v17 Material 15 MDC | 使用 `ui-material` 时完成 Material MDC migration；回归 theme Sass、DOM/CSS selectors、density、typography、form-field、checkbox/radio/slide-toggle、harness 和视觉快照 |
| v18 迁移到 Angular 16 | core 18 的 Angular peer 是 `^16.0.0`，并非 Angular 18；Angular 17/18 工程同样会产生 peer mismatch，不能按包版本数字猜框架版本 |
| Angular 16 工具链 | 固定 Angular 16.1.3 源码显示 Node `^16.14.0 || >=18.10.0`、compiler-cli TypeScript `>=4.9.3 <5.2`；统一本地、CI、IDE、test、SSR 和 image runtime |
| core runtime peers | 目标 manifest 要求 Angular common/core/forms `^16.0.0`、RxJS `^7.5.7`、core-js `^3.31.0`；验证 relation streams、async validators、teardown、polyfills 和浏览器矩阵 |
| standalone renderer | v18 UI source只导出 standalone form/container/control components，不再提供 `DynamicForms*UIModule`；按模板实际 selector 导入，覆盖 standalone component、NgModule、lazy route、TestBed、Storybook 和 SSR scope |
| standalone core directives | `DynamicListDirective` 与 `DynamicTemplateDirective` 为 standalone，`DynamicFormsCoreModule` 仍可导入/导出它们；选择 direct import 或 core module，不能放在 declarations |
| Kendo renderer 移除 | `@ng-dynamic-forms/ui-kendo` 不存在于 v18；选择支持的 renderer、自维护 fork 或基于 core 的 custom controls，并做功能、样式、a11y 和许可核查 |
| renderer peers 不统一 | basic/bootstrap/foundation 使用 ngx-mask 16；ng-bootstrap/ngx-bootstrap 使用 ngx-mask 13；Material 16、Ionic 7、PrimeNG 16。必须读取实际 renderer manifest，不能统一升级一个版本 |
| 模型与 strict mode | 检查 model config 的 value/disabled、validators/asyncValidators、relations、group/groupFactory、mask/maskConfig、`additional` renderer config、日期/文件/options 和泛型假设 |
| JSON revival | `DynamicFormService.fromJSON()` 会构造运行期 model/date；对外部 JSON 做 schema 校验，验证 custom decorators、validators、relations、mask 与 round-trip，不把配置当可信代码 |
| OnPush model update | value/disabled setter 与 label/layout/options 等 metadata 修改可见性不同；需要时调用 `DynamicFormService.detectChanges()`，并覆盖 lazy/destroyed component 和 async validation |
| 自定义 renderer/base classes | 自定义 `DynamicFormComponent`/container subclass 需要核对 v18 abstract members、UntypedFormGroup inputs、ContentChildren/ViewChildren、events、standalone imports、OnPush 和 provider scope |
| provider scope | matcher/validator tokens、`DYNAMIC_MATCHER_PROVIDERS` 和 custom control mapping 在 standalone/lazy/dialog/TestBed/SSR injector 中可能出现不可见或多实例，需逐 scope 测试 |

v18 renderer peer 摘要：

| renderer | 固定 v18 manifest 的主要 peer |
| --- | --- |
| `ui-basic` | core 18、ngx-mask 16 |
| `ui-bootstrap` | core 18、Bootstrap 3、ngx-bootstrap 6、ngx-mask 16 |
| `ui-foundation` | core 18、Foundation Sites 6、ngx-mask 16 |
| `ui-ionic` | core 18、Ionic Angular 7 |
| `ui-material` | core 18、Angular Material 16 |
| `ui-ng-bootstrap` | core 18、ng-bootstrap 11、Bootstrap 4、ngx-mask 13 |
| `ui-ngx-bootstrap` | core 18、ngx-bootstrap 8、Bootstrap 4、ngx-mask 13 |
| `ui-primeng` | core 18、PrimeNG 16 |

## 固定证据

官方 `v18.0.0` 固定到提交 [`da1742ce051af5cebdcf905b189ab6b55fe365d7`](https://github.com/udos86/ng-dynamic-forms/commit/da1742ce051af5cebdcf905b189ab6b55fe365d7)：

- [CHANGELOG](https://github.com/udos86/ng-dynamic-forms/blob/da1742ce051af5cebdcf905b189ab6b55fe365d7/CHANGELOG.md)
- [core package manifest](https://github.com/udos86/ng-dynamic-forms/blob/da1742ce051af5cebdcf905b189ab6b55fe365d7/projects/ng-dynamic-forms/core/package.json)
- [core module（无 `forRoot`，导入 standalone directives）](https://github.com/udos86/ng-dynamic-forms/blob/da1742ce051af5cebdcf905b189ab6b55fe365d7/projects/ng-dynamic-forms/core/src/lib/core.module.ts)
- [basic standalone form](https://github.com/udos86/ng-dynamic-forms/blob/da1742ce051af5cebdcf905b189ab6b55fe365d7/projects/ng-dynamic-forms/ui-basic/src/lib/dynamic-basic-form.component.ts)
- [material standalone form](https://github.com/udos86/ng-dynamic-forms/blob/da1742ce051af5cebdcf905b189ab6b55fe365d7/projects/ng-dynamic-forms/ui-material/src/lib/dynamic-material-form.component.ts)

目标 Angular 16.1.3 固定到 [`angular/angular@d9d70f0`](https://github.com/angular/angular/commit/d9d70f0a6fcded2a6b8a2fcad152a201152749df)，其中 [compiler-cli manifest](https://github.com/angular/angular/blob/d9d70f0a6fcded2a6b8a2fcad152a201152749df/packages/compiler-cli/package.json) 给出 TypeScript 和 Node 边界。

OpenRewrite 测试结构参考固定提交的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)、[JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 和 rewrite-javascript [ImportTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)，所有 AUTO 均包含 before/after、idempotence 与 import-aware no-op。

## 真实仓库用例

- [`dhrn/electron-mailer-poc@51602c24`](https://github.com/dhrn/electron-mailer-poc/tree/51602c24bc36c400fe8d5a28a02d7a06188aa11f) 使用 Angular 12、core/ui-material 14；[model/service source](https://github.com/dhrn/electron-mailer-poc/blob/51602c24bc36c400fe8d5a28a02d7a06188aa11f/apps/ng-mailer/src/app/mail-configuration/mail-configuration.component.ts)、[material template](https://github.com/dhrn/electron-mailer-poc/blob/51602c24bc36c400fe8d5a28a02d7a06188aa11f/apps/ng-mailer/src/app/mail-configuration/mail-configuration.component.html) 与 [UIModule](https://github.com/dhrn/electron-mailer-poc/blob/51602c24bc36c400fe8d5a28a02d7a06188aa11f/apps/ng-mailer/src/app/app.module.ts) 分别验证 model/service、template、standalone scope markers。
- [`Patrick5078/Angular-form-builder@1a5d5f64`](https://github.com/Patrick5078/Angular-form-builder/tree/1a5d5f64142c68bf869ccd75b312a24fbac7c181) 使用 Angular 13、core/ui-basic 15；[component](https://github.com/Patrick5078/Angular-form-builder/blob/1a5d5f64142c68bf869ccd75b312a24fbac7c181/src/app/components/view-dynamic-form/view-dynamic-form.component.ts)、[basic template](https://github.com/Patrick5078/Angular-form-builder/blob/1a5d5f64142c68bf869ccd75b312a24fbac7c181/src/app/components/view-dynamic-form/view-dynamic-form.component.html) 和 [AppModule](https://github.com/Patrick5078/Angular-form-builder/blob/1a5d5f64142c68bf869ccd75b312a24fbac7c181/src/app/app.module.ts) 覆盖 models、service、selector 和 removed UIModule。
- [`umd-lib/mdsoar-angular@0e309c2b`](https://github.com/umd-lib/mdsoar-angular/tree/0e309c2b2aeba34c6815b5e4df56fc26fe1bbc4b) 使用 Angular 17、core 16；其 [custom standalone renderer](https://github.com/umd-lib/mdsoar-angular/blob/0e309c2b2aeba34c6815b5e4df56fc26fe1bbc4b/src/app/shared/form/builder/ds-dynamic-form-ui/ds-dynamic-form.component.ts) 验证 base-class marker，而 [layout utility](https://github.com/umd-lib/mdsoar-angular/blob/0e309c2b2aeba34c6815b5e4df56fc26fe1bbc4b/src/app/shared/form/builder/parsers/parser.utils.ts) 作为 import-aware NO-OP；Angular 17/TS 5.4 package 则验证反向 peer mismatch。

当前测试套件包含 71 个执行用例：49 个严格依赖/真实 package/复杂 spec/NO-OP 用例、4 个确定性源码 AUTO/幂等/误报用例、14 个 package/source/template 精确 MARK 与真实仓库/NO-OP 用例、4 个 YAML 发现与组合用例。

## 使用与验证

推荐先 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ng-dynamic-forms-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngdynamicforms.MigrateNgDynamicFormsCoreTo18_0_0
```

审查 AUTO patch 和每个 `~~(...)~~>` 标记后，按 Angular 官方流程逐 major 迁移到 Angular 16，选择并升级 renderer，重建 lockfile。运行 AOT/production build、strict template/typecheck、unit/component/E2E、SSR、lazy route、TestBed/Storybook、model JSON round-trip、mask、async validators、relations、form arrays、Material MDC 视觉快照和可访问性测试。

模块验证：

```bash
mvn -f rewrite-ng-dynamic-forms-core-upgrade/pom.xml clean verify
```
