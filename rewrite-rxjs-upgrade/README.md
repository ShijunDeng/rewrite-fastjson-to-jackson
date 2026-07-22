# RxJS → 7.8.2 自动迁移

本模块对应 `开源软件升级.xlsx` 中的 `rxjs`。表格列出的源版本为：

`6.5.5`、`6.6.7`、`7.3.0`、`7.4.0`、`7.5.5`、`7.5.6`、`7.5.7`、`7.6.0`、`7.8.0`、`7.8.1`。

推荐配方会依次升级依赖、执行有确定答案的源码改写，并对需要流语义或工程决策的位置添加带原因的 `SearchResult`：

```text
com.huawei.clouds.openrewrite.rxjs.MigrateRxjsTo7_8_2
```

只升级依赖可使用：

```text
com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsDependencyTo7_8_2
```

只做诊断、不修改业务源码可使用：

```text
com.huawei.clouds.openrewrite.rxjs.AuditRxjs7Compatibility
```

## 处理的不兼容修改点

| 不兼容点 | 行为 | 边界 |
| --- | --- | --- |
| `package.json` 的 RxJS 版本 | **AUTO**：四个直接依赖区中，表格 10 个版本的 exact、`^`、`~` 声明精确改为 `7.8.2` | 范围、协议、alias、未列版本、override、锁文件与生成目录不改写；由审计配方标记需决策的声明 |
| `rxjs/internal/Observable` 等内部类型入口 | **AUTO**：仅含已知公共符号的具名 import/export 改到 `rxjs` | 默认导入、opaque `require()`/动态 `import()`、混入内部符号或未知模块保持不变并由审计定位 |
| `rxjs/internal/observable/of` 等创建函数入口 | **AUTO**：已知创建函数改到 `rxjs` 公共入口 | 只接受精确白名单模块和匹配的导出符号 |
| `rxjs/internal/operators` 及已知单操作符入口 | **AUTO**：改到 `rxjs/operators` | 未知操作符或模糊文本不改写 |
| Ajax `AjaxRequest` 改名为 `AjaxConfig` | **AUTO**：仅处理直接、未取别名且未遮蔽的 `rxjs/ajax` 绑定和代码引用 | 同时存在新类型、局部同名符号、属性访问、注释、字符串和模板文本均不猜测 |
| `throwError` 建议使用惰性错误工厂 | **AUTO**：字符串与 `new Error(字符串)` 包为 `() => ...`；**MARK**：变量等复杂参数 | 已是箭头/函数工厂、属性调用、别名或有遮蔽的绑定不误改；复杂生命周期留给业务决定 |
| `Observable.toPromise()` 在 RxJS 7 中废弃且返回类型包含 `undefined` | **MARK**：精确定位调用 | 必须按首值/末值和空流行为选择 `firstValueFrom` 或 `lastValueFrom`；RxJS 8 已移除该方法 |
| `Subscription.add()` 不再返回 `Subscription` | **MARK**：导入 `Subscription` 时定位 `.add(...).add(...)` | 需要业务决定父订阅持有和清理方式 |
| `Observable.create` 被移除 | **MARK**：仅对实际导入的 `Observable` 绑定定位 | 改为 `new Observable` 时必须验证 teardown、同步错误和订阅行为 |
| `defer`/`iif` 不再接受 `undefined` ObservableInput | **MARK**：仅对实际导入绑定和明确 `undefined`/`void 0` 定位 | 由业务选择 `EMPTY`、`of(...)` 或其他显式 Observable |
| `multicast`/`publish*`/`refCount` 旧式多播 | **MARK**：只定位实际导入函数的调用 | 需要选择 `connectable`/`share` 及 reset、重订阅、缓存语义 |
| notifier/finalize 顺序变化 | **MARK**：定位实际导入的 `audit`、`buffer`、`debounce`、`sample`、`throttle`、`finalize` | 使用 marble test 验证 next/error/complete/unsubscribe 时序 |
| `race`/`zip` 边界行为变化 | **MARK**：定位实际导入函数的调用 | 验证空源、赢家订阅、完成和 tuple 预期 |
| 私有字段及同步错误兼容开关 | **MARK**：定位 `_subscribe`、`_isScalar`、`syncError*` 和废弃同步错误配置 | 必须移除对 RxJS 内部实现的依赖 |
| `rxjs-compat` 与 TypeScript 3 | **MARK**：在含 RxJS 的 `package.json` 中给出具体原因 | 移除兼容包并统一编译器、编辑器、测试和 CI；现代 TypeScript 不产生无意义标记 |

源码处理只针对 `.js/.jsx/.ts/.tsx/.mjs/.cjs`，并跳过 `node_modules`、`bower_components`、`dist`、`build`、`coverage`、`.next`、`.nuxt` 和 `generated`。文本级改写使用代码位置扫描排除注释、字符串与模板文本，并对局部遮蔽采取保守 NOOP。

## 不自动处理

- `package-lock.json`、`npm-shrinkwrap.json`、`yarn.lock`、`pnpm-lock.yaml`：运行配方后用工程原包管理器重新生成。
- `toPromise()` 的首值/末值选择和空流行为。
- 多播 reset、缓存、完成、错误、取消订阅及 scheduler 的产品语义。
- 复杂 `throwError` 参数、默认/namespace import、未知内部入口和中央版本所有权。
- `rxjs-compat` 的直接删除；必须先确认所有补丁导入和旧 API 都已消除。

这些场景保持 NOOP 或生成带原因的 MARK，避免得到“可以编译但运行语义错误”的补丁。

## 真实仓库用例与参考

测试中的样例固定到公开仓库 commit，避免上游主分支变化影响可追溯性：

- [beeman/angular-elements-chat-widget：RxJS 6.5.5 package.json](https://github.com/beeman/angular-elements-chat-widget/blob/b4759c2662874a7e8c84bfbcd84cc7b4209569a0/package.json)
- [bithost-gmbh/ngx-mat-select-search：RxJS 6.6.7 package.json](https://github.com/bithost-gmbh/ngx-mat-select-search/blob/0892f54ff6c865cbe3cc9fcd709eeb6a23f4f607/package.json)
- [UnchartedBull/OctoDash：`rxjs/internal/Observable` 导入](https://github.com/UnchartedBull/OctoDash/blob/2096721d7d08a7af88c4bd6aa389348b5b4ba002/src/model/files.model.ts)
- [ng-packagr：真实 `toPromise()` 调用](https://github.com/ng-packagr/ng-packagr/blob/0143b11efbbbebb997f8425ede6211dfa99381f2/src/lib/packagr.ts#L91-L95)
- [RxJS 7.8.2 官方 6→7 change summary](https://github.com/ReactiveX/rxjs/blob/7.8.2/docs_app/content/6-to-7-change-summary.md)
- [RxJS 7.8.2 官方 changelog](https://github.com/ReactiveX/rxjs/blob/7.8.2/CHANGELOG.md)
- [OpenRewrite 固定 commit 的 `RewriteTest` 测试基建](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)

当前测试共执行 **167** 次：其中依赖矩阵为 10 个源版本 × 4 个直接依赖区 × 3 种声明形式，共 120 次；其余覆盖真实仓库、自动迁移、带原因审计、误报反例、生成目录和两轮幂等。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-rxjs-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.rxjs.MigrateRxjsTo7_8_2
```

确认 patch 后将 `dryRun` 改为 `run`，重新生成锁文件，再运行 TypeScript 编译、单元测试、marble test 和端到端测试。

```bash
mvn -pl rewrite-rxjs-upgrade -am clean verify
```
