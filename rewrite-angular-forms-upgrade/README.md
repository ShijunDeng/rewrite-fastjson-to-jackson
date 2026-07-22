# @angular/forms upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/forms`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 28 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularFormsTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/forms` 设置为 `20.3.26`。目标包要求 core、common、platform-browser 精确匹配 `20.3.26`，并支持 RxJS `^6.5.3` 或 `^7.4.0`。

必须逐大版本执行 Angular CLI migrations，尤其不能跳过 Angular 14 的 typed forms 迁移；本配方只负责最终版本声明。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 14 引入严格类型 Reactive Forms | `FormControl`/`FormGroup`/`FormArray` 增加泛型；先接受官方 Untyped 迁移，再逐表单恢复准确模型类型 |
| `FormControl<T>` 默认仍可能包含 `null` | 使用 `{nonNullable: true}` 或 `NonNullableFormBuilder` 表达重置语义；不要靠强制断言掩盖 null |
| `FormGroup.value` 会排除 disabled controls | 需要完整持久化值时使用 `getRawValue()`；检查 DTO 映射与权限禁用字段 |
| `setValue` 与 `patchValue` 的结构约束在 typed forms 中显式化 | 完整替换使用 setValue，部分更新使用 patchValue；修正可选 control 与动态 key 模型 |
| 自定义 validator/async validator 类型更严格 | 返回 `ValidationErrors | null`/Observable/Promise，避免抛异常或永不完成的流；处理并发请求取消 |
| `ControlValueAccessor.setDisabledState` 调用策略更一致 | 自定义控件必须真正实现禁用 DOM、交互、ARIA 和内部状态；不要仅靠输入属性 |
| `FormsModule.withConfig({callSetDisabledState})` 控制兼容行为 | 优先使用始终调用的正确语义，修复旧 CVA；不要长期选择 legacy `whenDisabledForLegacyCode` |
| 混用 `ngModel` 与 reactive form 指令一直处于废弃路径 | 拆分为 template-driven 或 reactive 单一模型，避免双源状态与事件顺序问题 |
| standalone 组件需显式导入 forms 指令 | 在组件 imports 中加入 `FormsModule`/`ReactiveFormsModule` 或所需 standalone directive，修复严格模板错误 |
| control 状态/值事件 API 扩展，emit 时序经修正 | 不要依赖父子 `valueChanges` 的脆弱同步顺序；对 debounce、联动计算和循环更新增加测试 |
| reset、markAll、statusChanges 行为修复 | 明确 `emitEvent`；测试 dirty/touched/pending/disabled 与父组聚合状态，而非只测试值 |
| 表单提交/重置事件类型增强 | 更新直接消费 DOM event 的代码；确保 `ngSubmit`、FormSubmitted/Reset 与原生 submit 不重复处理 |
| radio/select 多值 accessor 与 compareWith 边界 | `compareWith` 使用稳定身份函数；覆盖对象重建、动态 option、同名 radio 与 SSR hydration |
| signal forms 属于独立的新模型 | 不要在这次版本升级中未经评估重写生产表单；先完成兼容迁移，再单独试点 |
| 工具链与 Angular 包必须锁步 | Node 使用 `^20.19.0`、`^22.12.0` 或 `>=24`，TypeScript 按 v20 矩阵，所有 framework 包同 patch |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide)、[Forms guide](https://angular.dev/guide/forms)、[Typed Forms](https://angular.dev/guide/forms/typed-forms) 和 [版本兼容矩阵](https://angular.dev/reference/versions) 为准。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-forms-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularFormsTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 strict template/type check、表单单元测试、键盘/可访问性、异步校验、提交和端到端测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-forms-upgrade -am clean verify
```
