# RxJS 6.x → 7.8.2 自动迁移

本模块对应 `开源软件升级.xlsx` 中的 `rxjs`，处理源版本 `6.5.5` 和 `6.6.7`。推荐配方不是单纯修改版本号，而是同时执行依赖升级、确定性源码改写和语义风险定位：

```text
com.huawei.clouds.openrewrite.rxjs.MigrateRxjsTo7_8_2
```

只需要修改依赖时，可单独使用底层配方：

```text
com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsDependencyTo7_8_2
```

## 规格与配方行为

| RxJS 7 不兼容点 | 配方行为 | 状态 | 主要测试 |
| --- | --- | --- | --- |
| `package.json` 中的 RxJS 6.5.5/6.6.7 | 精确升级四个直接依赖区中的 exact、caret、tilde 声明；不碰范围、协议、alias、override 和锁文件 | 自动修复 | 24 个参数化组合、两份真实仓库 package、边界反例 |
| `rxjs/internal/Observable` 等已知类型深层导入 | 改为 `rxjs` 公共入口；`rxjs/internal/operators` 改为 `rxjs/operators` | 自动修复 | OctoDash 固定 commit 及组合导入 before/after |
| `rxjs/internal/observable/of` 等已知创建函数深层导入 | 改为 `rxjs` 公共入口 | 自动修复 | 创建函数与操作符组合 before/after |
| Ajax 类型 `AjaxRequest` 改名为 `AjaxConfig` | 仅在文件直接从 `rxjs/ajax` 导入该类型且未同时导入新类型时，修改导入和本文件引用 | 自动修复 | Ajax 类型 before/after、无 RxJS 导入反例 |
| `throwError(error)` 使用惰性错误工厂 | 字符串字面量和 `new Error(字符串字面量)` 自动包成 `() => ...`；变量或函数调用只标记，不猜测生命周期语义 | 自动修复 + 自动检测 | 三种参数并列 before/after、lookalike 反例 |
| `Observable.toPromise()` 废弃，需选 `firstValueFrom` 或 `lastValueFrom` | 在实际导入 RxJS 的源码中精确标记调用位置，交由业务选择空流和首/末值语义 | 自动检测 | ng-packagr 固定 commit、无 RxJS 导入反例 |
| `Subscription.add()` 不再返回 `Subscription` | 标记链式 `.add(...).add(...)`，避免配方擅自决定订阅持有方式 | 自动检测 | 链式调用标记测试 |
| `defer`/`iif` 分支返回 `undefined`，旧 multicast/publish、`Observable.create`、内部字段 | 在导入 RxJS 的源码中标记候选位置 | 自动检测 | 审计配方标记测试 |
| notifier/finalize 完成顺序及 `race`、`zip`、`ReplaySubject` 行为变化 | 标记 completion-sensitive 操作符；运行时行为必须由项目回归测试确认 | 检测 + 人工验证 | 审计配方标记测试 |
| `rxjs-compat`、TypeScript 版本与 RxJS 7 类型声明兼容性 | 在 `package.json` 标记依赖键，由项目决定删除兼容包及升级 TypeScript | 自动检测 | JSON SearchResult 测试 |

“自动检测”会生成 OpenRewrite `SearchResult` 标记，不等同于已完成迁移。它把无法在缺少类型信息、流完成行为和产品语义时安全决定的工作定位到具体源码位置。

## 不自动处理的内容

- `package-lock.json`、`npm-shrinkwrap.json`、`yarn.lock`、`pnpm-lock.yaml`：运行配方后必须使用项目原有包管理器重新生成。
- `toPromise()` 的首值/末值选择，以及空流是否应抛出异常。
- multicast、notifier、`finalize`、同步完成/报错和缓存重订阅的运行时语义。
- 复杂 `throwError` 参数、命名冲突、namespace/default import 和未列入白名单的内部入口。

这些边界保留为标记或不修改，是为了避免产生能编译但行为错误的补丁。

## 真实仓库与参考依据

测试样例固定到公开仓库 commit，避免上游分支变化破坏可追溯性：

- [beeman/angular-elements-chat-widget：RxJS 6.5.5 package.json](https://github.com/beeman/angular-elements-chat-widget/blob/b4759c2662874a7e8c84bfbcd84cc7b4209569a0/package.json)
- [bithost-gmbh/ngx-mat-select-search：RxJS 6.6.7 package.json](https://github.com/bithost-gmbh/ngx-mat-select-search/blob/0892f54ff6c865cbe3cc9fcd709eeb6a23f4f607/package.json)
- [UnchartedBull/OctoDash：`rxjs/internal/Observable` 导入](https://github.com/UnchartedBull/OctoDash/blob/2096721d7d08a7af88c4bd6aa389348b5b4ba002/src/model/files.model.ts)
- [ng-packagr：真实 `toPromise()` 调用](https://github.com/ng-packagr/ng-packagr/blob/0143b11efbbbebb997f8425ede6211dfa99381f2/src/lib/packagr.ts#L91-L95)
- [RxJS 7.8.2 官方 6→7 change summary](https://github.com/ReactiveX/rxjs/blob/7.8.2/docs_app/content/6-to-7-change-summary.md)
- [OpenRewrite 固定 commit 的测试基建参考](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)

## 使用

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-rxjs-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.rxjs.MigrateRxjsTo7_8_2
```

确认 patch 后将 `dryRun` 改为 `run`，重新生成锁文件，再运行 TypeScript 编译、单元测试和端到端测试。

## 模块验证

```bash
mvn -pl rewrite-rxjs-upgrade -am clean verify
```
