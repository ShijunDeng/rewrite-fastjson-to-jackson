# Angular Forms 升级到 20.3.26

本模块处理 `开源软件升级.xlsx` 中的 `@angular/forms`。表格当前可见源版本为：

```text
10.0.14, 10.2.5, 11.2.14, 12.2.10, 12.2.13, 12.2.14,
12.2.16, 12.2.17, 13.1.3, 13.2.6
```

表格中 `13.2.6` 单元格显示“共 28 个版本”，但其余折叠值不可见。本模块不会猜测这 27 个值；只有上面十个值及其单一 `^`/`~` 声明会自动升级。目标版本固定为 `20.3.26`。

## 配方

严格低层配方：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularFormsTo20_3_26
```

推荐迁移配方：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularFormsTo20_3_26
```

推荐配方先执行低层依赖升级和可证明等价的 AUTO，再把需要业务判断的代码、模板及配置标为 `SearchResult`。marker 是待办，不是迁移成功证明。

## 处理矩阵

| 不兼容点 | 处理 | 行为及验证 |
| --- | --- | --- |
| 表格可见的 exact/`^`/`~` 单版本 | AUTO | 仅改 `package.json` 四个直接依赖区，写成精确 `20.3.26`；30 组参数化测试及 NiFi 固定样例 |
| 复杂范围、workspace/npm 协议、变量、tag、未列版本、中央 catalog/override、lockfile | NO-OP / MARK | 低层配方不改；推荐配方在具体 JSON 值上标记；复杂范围/中央 owner/无关 JSON 测试 |
| Angular 14 typed forms 官方兼容迁移 | AUTO / MARK | 无显式泛型的 aliased `FormControl/FormGroup/FormArray/FormBuilder` import 改成对应 `Untyped*`；已有显式泛型不改；其他未类型化构造在构造节点标记 |
| `initialValueIsDefault` 废弃 | AUTO / MARK | 只在 Angular `FormControl` 构造选项内、且没有 `nonNullable` 冲突时改名；双键冲突保留并精确标记；ngx-formly 固定样例、引号属性和同名误报测试 |
| `FormControl<T>` null/reset/default 语义 | MARK | 在未类型化构造以及 `reset/setValue/patchValue` 调用处标记；需要决定泛型、optional control、`nonNullable` 与默认值 |
| `FormGroup.value` 排除 disabled 子控件；`getRawValue()` 包含它们 | MARK | 在精确 `.value`/`.getRawValue()` 节点标记；BigQuery Geo Viz 固定样例覆盖 valueChanges/raw DTO |
| options 与第三个 async validator 参数组合 | MARK | 在 `new FormControl(...)` 节点标记被丢弃/废弃的第三参数风险；必须合并到 options 并测试 pending/cancel/error |
| async validator 类型与取消时序 | MARK | 在 `validate` 实现、async validator 变更调用和相关事件节点标记；同名非 Angular 类不标记 |
| Angular 15 CVA `setDisabledState` 默认始终调用 | AUTO / MARK | `callSetDisabledState: 'always'` 是默认正确语义，安全删除冗余属性；legacy 值在配置属性及 module 调用上标记 |
| 自定义 `ControlValueAccessor` | MARK | 只在真正 `implements ControlValueAccessor` 类的 `writeValue/registerOnChange/registerOnTouched/setDisabledState` 方法标记；非实现类同名方法无误报 |
| disable/enable/mark/update 状态与事件时序 | MARK | 在已归因 form-control receiver 的调用处标记 `emitEvent/onlySelf`、父子聚合、CVA 与 change detection 风险 |
| `valueChanges/statusChanges/events` | MARK | 在具体字段访问处标记父子时序、async validation、reset 与 enable/disable 的事件顺序 |
| `ngModel` 与 reactive directive 同元素混用 | MARK | 对完整开始标签标记；位于不同元素时不误报。Angular Components 固定样例覆盖边界 |
| reactive directive 与 `[disabled]` 同元素 | MARK | 对完整标签标记，要求由 control 驱动 disabled 并验证 CVA、event、raw/submitted value |
| `ngModelOptions`、`ngModelChange`、submit/reset/ngForm | MARK | 在具体模板属性/引用上标记 registration、updateOn 和事件时序风险 |
| radio/select/`compareWith` | MARK | 在具体模板片段标记 identity、动态 option、多选、radio group、disabled/null 与 hydration 风险 |
| email validator 的字符串 `false` coercion | AUTO | 静态 `email="false"` 和绑定字符串 false 改为布尔 `[email]="false"`；动态/true/非 HTML 不改，含幂等测试 |
| standalone forms 作用域 | MARK | 在 forms 源文件的 `standalone: true` 属性标记显式 import/provider 与 strict template 检查 |
| Angular peer、Node、TypeScript、RxJS | MARK | `core/common/platform-browser/compiler-cli` 未锁步、Node/TS/RxJS 不兼容时在具体声明标记 |
| `strictTemplates/strictInputTypes/strictNullInputTypes=false` | MARK | 在 tsconfig 的具体 false 值标记可能被隐藏的 typed forms/template 问题 |
| custom builder、SSR/prerender | MARK | 在 workspace builder/target 节点标记 template compilation、初始值、disabled、validation 与 hydration 风险 |

## 必须人工决定的业务语义

- `FormControl<string | null>`、`nonNullable` 和 reset default 必须与产品的“清空/恢复默认”语义一致。
- DTO 若包含权限禁用字段，应明确使用 `getRawValue()`；不要机械地用它替换所有 `.value`。
- `setValue` 要求完整结构，`patchValue` 允许部分更新；动态键应使用 `FormRecord` 或准确模型，而不是 `any`。
- async validator 必须覆盖快速输入取消、迟到响应、空/错误流、disabled、parent pending 和销毁。
- CVA 必须让 DOM、ARIA、交互和内部状态在初始 enabled、disabled、再次 enabled、write/reset/destroy 时一致。
- template-driven/reactive 双源状态应拆成一种模型；不能通过删除警告或 marker 解决事件所有权。
- submit/reset、value/status/events 的相对顺序不得依赖未声明的同步时序。
- signal forms 是单独的新模型，不在本次兼容升级中自动引入。

## 固定依据

所有依据固定到 commit，避免 README 随默认分支漂移：

- Angular `20.3.26` release cut：[`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa)
- 目标 [`packages/forms/package.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/forms/package.json) 与 [`CHANGELOG.md`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/CHANGELOG.md)
- Angular typed-forms 官方迁移：[`d336ba96d922363235688f54d8af108ef7ab01f0`](https://github.com/angular/angular/tree/d336ba96d922363235688f54d8af108ef7ab01f0)，含 [migration source](https://github.com/angular/angular/blob/d336ba96d922363235688f54d8af108ef7ab01f0/packages/core/schematics/migrations/typed-forms/util.ts) 与 [official tests](https://github.com/angular/angular/blob/d336ba96d922363235688f54d8af108ef7ab01f0/packages/core/schematics/test/typed_forms_spec.ts)
- CVA disabled-state 行为提交：[`96b7fe93af361a1cf2ea5477970f64ba6f3d8cd5`](https://github.com/angular/angular/commit/96b7fe93af361a1cf2ea5477970f64ba6f3d8cd5)
- OpenRewrite JavaScript 测试/AST 参考：[`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite-javascript/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)

## 固定真实仓库样例

- Apache NiFi [`59cff970ca8b98ee51ae4418cf4de6830fa28c37`](https://github.com/apache/nifi/tree/59cff970ca8b98ee51ae4418cf4de6830fa28c37)：`@angular/forms` 11.2.14 dependency fixture
- ngx-formly [`ff356fcf8bebcd880c0426a06a0813433ac9563a`](https://github.com/ngx-formly/ngx-formly/tree/ff356fcf8bebcd880c0426a06a0813433ac9563a)：`initialValueIsDefault` AUTO fixture
- Mockoon [`4ef62c89faa8d972a39abac1503050ca03cf8950`](https://github.com/mockoon/mockoon/tree/4ef62c89faa8d972a39abac1503050ca03cf8950)：legacy `callSetDisabledState` marker fixture
- BigQuery Geo Viz [`3c9001bca04c9b3b687ef4315449d1b5c5bf005a`](https://github.com/GoogleCloudPlatform/bigquery-geo-viz/tree/3c9001bca04c9b3b687ef4315449d1b5c5bf005a)：`valueChanges/getRawValue` fixture
- ngx-bootstrap [`404c3b21ac2b8d1b240fad2cc533291fc6bb6456`](https://github.com/valor-software/ngx-bootstrap/tree/404c3b21ac2b8d1b240fad2cc533291fc6bb6456)：CVA fixture
- Angular Components [`452d3cce3e2c651945e0f85c545a2e13b90b8add`](https://github.com/angular/components/tree/452d3cce3e2c651945e0f85c545a2e13b90b8add)：相邻但不在同元素的 ngModel/reactive template no-op fixture

测试使用提取后的最小 before/after/marker/no-op 片段，保留固定仓库路径，并覆盖幂等与同名误报边界。

## 使用

先运行推荐配方的 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-forms-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularFormsTo20_3_26
```

逐项处理 marker，按 major 顺序运行官方 `ng update` migrations，统一全部 Angular framework packages 到 `20.3.26`，重建 lockfile，然后执行 strict template/type check、单元测试、CVA/键盘/可访问性、async validator、submit/reset、SSR/hydration 与端到端测试。

模块验证：

```bash
mvn -pl rewrite-angular-forms-upgrade -am clean verify
```
