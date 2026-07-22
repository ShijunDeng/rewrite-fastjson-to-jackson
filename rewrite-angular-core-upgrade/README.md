# @angular/core upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/core`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 28 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCoreTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/core` 设置为 `20.3.26`。目标 core 要求 `@angular/compiler:20.3.26`、`zone.js ~0.15.0`（可选）以及 RxJS `^6.5.3` 或 `^7.4.0`。

所有 Angular framework 包必须锁定相同 patch。必须从当前版本逐个大版本运行 `ng update @angular/core@<major> @angular/cli@<major>`，让官方 schematics 修改源码和 workspace；本配方只负责最终版本声明核对。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Node/TypeScript 基线跨多代提升 | Angular 20 使用 Node `^20.19.0`、`^22.12.0` 或 `>=24`，compiler 工具链使用 TypeScript `>=5.8 <6.0` |
| View Engine、ngcc 与旧 metadata 被移除 | 仅保留 Ivy/partial compilation；升级或替换 View Engine-only 库，删除 ngcc postinstall |
| `entryComponents` 与 `ANALYZE_FOR_ENTRY_COMPONENTS` 失去作用/移除 | 删除旧声明，动态组件使用 `ViewContainerRef.createComponent` 或公开 `createComponent` API |
| standalone 成为默认和推荐模型 | v19+ 新声明默认 standalone；旧 NgModule declarations 需由 migrations 明确标记，避免组件同时被错误声明/导入 |
| bootstrap/provider 架构转向 `bootstrapApplication`/`ApplicationConfig` | 用环境 provider 和 `importProvidersFrom` 过渡，检查 service scope 与 multi provider 是否重复 |
| `InjectFlags` 在 v20 移除 | `inject`、`Injector.get`、`EnvironmentInjector.get`、TestBed 调用改用 options 对象或公开替代 API |
| `TestBed.get` 在 v20 移除 | 改用类型安全的 `TestBed.inject`；字符串 token 迁移到 `InjectionToken` |
| `inject()` 仅能在 injection context 使用 | field initializer、factory、constructor 或 `runInInjectionContext` 内调用；不要在普通回调/异步延迟中直接调用 |
| signals、computed、effect 引入且 effect 调度语义演进 | 不要依赖同步 effect；处理 cleanup、写入限制、untracked 读取和组件销毁，使用稳定的测试 tick API |
| `afterRender` 重命名为 `afterEveryRender` | 更新调用与测试；DOM 逻辑还可根据需求使用 `afterNextRender`，SSR 中避免假定执行 |
| zoneless change detection 在 v20.2 稳定 | 只有依赖库和应用都不依靠 Zone 副作用时才启用；显式通过 signals、markForCheck 或输入/事件触发更新 |
| `zone.js` 对 core 成为可选 peer | 这不表示现有应用可直接删除；先审计 fakeAsync、第三方库、全局异步 patch 与错误处理 |
| v20 默认不生成 `ng-reflect-*` | 删除测试/业务选择器依赖；开发调试使用 Angular DevTools 或稳定 DOM 属性 |
| `ApplicationRef.tick()` 与未处理事件错误更直接向调用方抛出 | 修复根因或在明确边界捕获；测试不要只断言 ErrorHandler 日志 |
| TestBed effect/错误 API 改变 | `flushEffects()` 改用 `TestBed.tick()`；故意测试应用错误时显式捕获或谨慎配置 rethrow 行为 |
| `PendingTasks`、SSR 稳定性与 hydration 生命周期变化 | 自定义异步工作要正确登记和结束；处理 rejection，防止 SSR 永不稳定或过早序列化 |
| 装饰器、继承和 DI 元数据更严格 | 开启 strict 模板/注入检查；复查抽象 directive、构造器继承、泛型 `ModuleWithProviders<T>` 和私有 Angular API |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide)、[版本兼容矩阵](https://angular.dev/reference/versions)、[Dependency injection guide](https://angular.dev/guide/di) 和 [20.0.0 release](https://github.com/angular/angular/releases/tag/20.0.0) 为准。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularCoreTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 production/AOT build、strict template/type check、单元/E2E、SSR、hydration 与 zoned/zoneless 目标测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-core-upgrade -am clean verify
```
