# Angular Compiler 升级到 20.3.26

本模块处理 `开源软件升级.xlsx` 中的 `@angular/compiler`。表格当前可见源版本为：

```text
10.0.14, 10.2.5, 11.2.14, 12.2.10, 12.2.13, 12.2.14,
12.2.16, 12.2.17, 13.1.3, 13.2.6
```

`13.2.6` 单元格显示“共 28 个版本”，但其余折叠值不可见。本模块不猜测隐藏值；仅上述十个值及其单一 `^`/`~` 声明会自动升级。目标版本固定为 `20.3.26`。

## 配方

严格低层配方：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCompilerTo20_3_26
```

推荐迁移配方：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularCompilerTo20_3_26
```

推荐配方先执行严格依赖升级及可证明确定的 AUTO，再将需要模板、构建或业务判断的位置标为 `SearchResult`。marker 是必须处理的迁移待办。

## AUTO / MARK / NO-OP 矩阵

| 不兼容点 | 处理 | 行为及测试 |
| --- | --- | --- |
| XLSX 可见 exact/`^`/`~` 单版本 | AUTO | 仅修改 `package.json` 四个直接依赖区并写成精确 `20.3.26`；30 组参数化、workspace 路径、NiFi 风格固定样例和幂等测试 |
| 复杂范围、协议、变量、tag、未列版本、中央 owner、lockfile | NO-OP / MARK | 低层配方不改；推荐配方在具体 JSON 值标记；复杂/中央/非 package JSON 测试 |
| Ivy-only 后残留 `enableIvy:true` | AUTO | 仅删除 tsconfig 顶层 `angularCompilerOptions.enableIvy:true`；NG-ZORRO 固定样例 |
| `enableIvy:false` 请求 View Engine | MARK | 在具体 false 值标记；不能自动替换 View Engine-only 库 |
| Carbon 样例 `compliationMode` 明确拼写错误 | AUTO / MARK | 仅在 `angularCompilerOptions` 且不存在正确键时改名；双键冲突保留并精确标记；其他对象同名 NO-OP |
| ngcc/View Engine 被移除 | AUTO / MARK | 仅删除 ngcc-only `postinstall`；包含 `&&`/`||`/`;`/pipe 的复合脚本不改并在值上标记；angular-samples 固定样例 |
| Ivy 不再需要 `entryComponents` | AUTO | 仅删除从 `@angular/core` 导入的 `@NgModule` 元数据成员；支持 decorator alias；animator/nowzoo 固定样例和 foreign 同名 NO-OP |
| Angular 20 把 `in`/`void` 识别为模板运算符 | AUTO | HTML interpolation/binding 表达式开头的旧属性改为 `this.in`/`this.void`；运算符、长标识符、静态文本、HTML comment、script/style raw text、非 HTML 不改，含幂等测试 |
| `@angular/compiler` parser/AST/i18n API | MARK | 在具体函数调用、构造、类型声明或 namespace 成员使用处标记；ngx-translate-extract/codelyzer 固定样例 |
| 私有 `ɵ` compiler API | MARK | 在具体使用节点标记，无兼容契约；foreign package 同名 API 不标记 |
| runtime `Compiler`/`JitCompilerFactory`/resource compilation | MARK | 在构造或 `compile*` 调用节点标记 AOT/CSP/provider/resource 风险 |
| `platformBrowserDynamic` runtime JIT | MARK | 在调用节点标记，不粗标 import；迁移生产 AOT/bootstrap 并验证 SSR/CSP |
| dynamic `createComponent/createNgModule` | MARK | 在调用节点标记 environment injector、bindings/directives、lifecycle、lazy/AOT discoverability |
| Angular 19 standalone 默认值 | MARK | 在 `@NgModule.declarations` 精确标记；必须运行官方 `standalone:false` migration 并核对 scope |
| `@Component` inline/dynamic/external template | MARK | 在 `template`/`templateUrl` 元数据成员标记解析、资源、CSP、builder、i18n 与 source map 风险 |
| standalone `imports`、NgModule imports/providers/exports/schemas | MARK | 在具体静态元数据成员标记作用域、静态求值、linker、tree shaking 与 schema 风险 |
| host metadata/`HostBinding`/`HostListener` | MARK | 在属性或 annotation 节点标记严格 host type checking、继承、组合、事件与 `$event` |
| `jit:true` | MARK | 在元数据属性标记 AOT/CSP/resource loading 风险 |
| `fullTemplateTypeCheck` 与 strict template flags | MARK | `fullTemplateTypeCheck`、`strictTemplates/strictStandalone/strictInjectionParameters:false` 在顶层具体值标记；嵌套同名键不误报 |
| library `compilationMode` | MARK | 在具体值标记 published library `partial` 与 final application `full` 的选择、APF/linker/sideEffects |
| diagnostics、metadata/resource emit、whitespace | MARK | 在相应 compiler option 标记公共 API、i18n ID、hydration、source map 和 tree shaking 风险 |
| `@for/@if/@switch/@defer` | MARK | 在块头关键片段标记 track 唯一性、scope/narrowing、chunks/triggers、SSR/hydration |
| `*ngIf/*ngFor/ngSwitch` | MARK | 在具体 attribute 标记必须使用官方 parser-aware control-flow migration，不能文本替换 |
| `(foo?.bar).baz` | MARK | 在精确表达式标记 Angular 20 尊重括号后可能抛错的 JavaScript 语义；安全 optional chain 不标记 |
| i18n、`innerHTML`/SVG URL、iframe bindings | MARK | 在具体模板片段或完整 iframe tag 标记 catalog diff、sanitization、Trusted Types、CSP、SSR/hydration |
| `ngNonBindable`/`ng-template` scope | MARK | 标记 block syntax escaping、template refs/let 变量、复用、i18n 与 whitespace 风险 |
| compiler/core/compiler-cli/CLI/build、Node/TS/tslib | MARK | 在不兼容或未锁步声明值标记；无 compiler 的非 Angular package 不标记 |
| custom builder、`aot:false`、SSR/prerender | MARK | 在 workspace 具体节点标记 compiler/linker、resources、i18n、optimization、CSP 和 server/client parity |

## 必须人工决定的语义

- 必须逐 major 运行官方 `ng update` migrations；不能用 Angular 20/TypeScript 5.8+ 直接替代旧 major 的 schematics 环境。
- 所有发布库应输出 Angular Package Format 的 partial compilation，由最终应用 linker；应用构建使用 full/AOT。
- 旧 View Engine-only 依赖不能靠删除 `enableIvy`/ngcc 修复，必须升级、替换或重新发布。
- compiler AST/parser API 虽有公开导出，也会随模板语法、source span、block、i18n 和 diagnostics 演进；自研工具必须固定并测试完整语料。
- standalone 默认变化需要知道每个 declaration 的 NgModule 归属，不能仅看到 `@Component` 就自动添加 `standalone:false`。
- 控制流迁移需要 Angular 模板 parser 处理 template refs、microsyntax、trackBy、aliases 和 whitespace；本配方不做正则替换。
- i18n extraction 必须做受控 catalog diff；不要直接覆盖已有译文或依赖自动变化的 message ID。
- runtime JIT/dynamic template 应迁移到 AOT-known 模板，且覆盖 production CSP、SSR/hydration 和 lazy provider scope。

## 固定官方依据

- Angular `20.3.26` release cut：[`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa)
- 目标 [`packages/compiler/package.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/compiler/package.json) 与固定 [`CHANGELOG.md`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/CHANGELOG.md)
- Angular 20 `in` operator：[`1b8e7ab9fe`](https://github.com/angular/angular/commit/1b8e7ab9fe46901979389b377be4232e11092260)；`void` operator：[`0361c2d81f`](https://github.com/angular/angular/commit/0361c2d81f5d2c56597002f465c00e9b1c4003e4)
- `enableIvy` 移除：[`16f96eeabf`](https://github.com/angular/angular/commit/16f96eeabf77964092b4b6a830e29f2761ffaeec)；ngcc 移除：[`48aa96ea13`](https://github.com/angular/angular/commit/48aa96ea13ebfadf2f6b13516c7702dae740a7be)
- standalone:false 官方迁移：[`6ea8e1e9aa`](https://github.com/angular/angular/commit/6ea8e1e9aae028572873cf97aa1949c8153f458f)
- Angular 20 固定 [control-flow migration](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/control-flow-migration) 及 [`migrations.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations.json)
- OpenRewrite JavaScript AST/测试参考：[`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite-javascript/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)

## 固定真实仓库样例

- Apache NiFi [`59cff970ca8b98ee51ae4418cf4de6830fa28c37`](https://github.com/apache/nifi/tree/59cff970ca8b98ee51ae4418cf4de6830fa28c37)：11.2.14 lockstep dependency fixture
- NG-ZORRO [`7071edd3f72d3384ec73a329fd0d9dce3af67fc5`](https://github.com/NG-ZORRO/ng-zorro-antd/tree/7071edd3f72d3384ec73a329fd0d9dce3af67fc5)：`enableIvy:true` AUTO fixture
- Carbon Components Angular [`ed90a4c8857e4c1cbc61d448c9c7f5c4a88a01bb`](https://github.com/carbon-design-system/carbon-components-angular/tree/ed90a4c8857e4c1cbc61d448c9c7f5c4a88a01bb)：`compliationMode` typo AUTO fixture
- angular-samples [`7852f85c2e9bb64fde4ca7c5ea9128814263bc91`](https://github.com/thelgevold/angular-samples/tree/7852f85c2e9bb64fde4ca7c5ea9128814263bc91)：ngcc-only postinstall AUTO fixture
- ngx-translate-extract [`82eb652e4bfec73f60f06cbc5ed4ddf8179f58f7`](https://github.com/biesbjerg/ngx-translate-extract/tree/82eb652e4bfec73f60f06cbc5ed4ddf8179f58f7)：`parseTemplate`/`TmplAstNode` marker fixture
- codelyzer [`8b7d153e737d0978a9aaa852beb32aad3345eb3b`](https://github.com/mgechev/codelyzer/tree/8b7d153e737d0978a9aaa852beb32aad3345eb3b)：namespace `Parser/Lexer/DomElementSchemaRegistry` marker fixture
- animator [`7ad91cb206b9d16f29907eeee9789048009b40d8`](https://github.com/veerajongit/animator/tree/7ad91cb206b9d16f29907eeee9789048009b40d8) 与 nowzoo-angular-bootstrap-lite [`50039ee563ad3e04a4fedebd4d940f5d5c2ca896`](https://github.com/nowzoo/nowzoo-angular-bootstrap-lite/tree/50039ee563ad3e04a4fedebd4d940f5d5c2ca896)：空/非空 `entryComponents` AUTO fixtures

测试使用提取后的最小 before/after/marker/no-op 片段，并保留固定仓库路径；覆盖幂等、alias、namespace、嵌套及同名误报边界。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-compiler-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularCompilerTo20_3_26
```

处理全部 marker 后，逐 major 执行官方 migrations，统一 Angular framework/compiler-cli/CLI/build 工具链，重建 lockfile，并运行 production AOT、strict template/host checks、library partial compilation/linker、i18n extraction diff、CSP、SSR/hydration 与端到端测试。

模块验证：

```bash
mvn -pl rewrite-angular-compiler-upgrade -am clean verify
```
