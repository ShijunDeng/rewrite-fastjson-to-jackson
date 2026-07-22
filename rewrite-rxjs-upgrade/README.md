# RxJS 6.x to 7.8.2 upgrade recipe

本模块对应 `开源软件升级.xlsx` 中的 `rxjs`，合并处理源版本 `6.5.5` 与 `6.6.7`，目标版本为 `7.8.2`。

配方名称：

```text
com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsTo7_8_2
```

## 自动处理范围

配方仅修改 `package.json`，将下列直接依赖区中的 `rxjs` 版本统一设置为 `7.8.2`：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

`package-lock.json`、`npm-shrinkwrap.json`、`yarn.lock` 和 `pnpm-lock.yaml` 包含解析地址、完整性摘要及依赖图，不能只替换版本字符串。运行配方后必须使用项目原有包管理器重新生成锁文件，例如：

```bash
npm install
# 或 yarn install / pnpm install
```

## RxJS 7 不兼容修改点

以下内容需要结合业务代码和测试人工确认；配置型配方不会在缺少类型和运行时上下文时盲目修改 TypeScript：

| 不兼容点 | 迁移建议 |
| --- | --- |
| `Observable.toPromise()` 返回类型变为 `Promise<T \| undefined>`，并已废弃 | 根据语义改用 `firstValueFrom()` 或 `lastValueFrom()`，明确空流行为 |
| `Subscription.add()` 不再返回 `Subscription` | 拆开链式 `add()`，保留待移除的函数或订阅实例 |
| `defer` 工厂不能返回 `void`/`undefined` | 显式返回 `EMPTY`、`of()`、Promise 或其他 `ObservableInput` |
| `iif` 分支不能为 `undefined` | 使用 `EMPTY` 表示不发出元素的分支 |
| `throwError(error)` 推荐并逐步要求工厂形式 | 改为 `throwError(() => error)`；若错误本身是函数，需要再包一层工厂 |
| `multicast`、`publish*` 的旧式连接写法被替代 | 无 selector 时改用 `connectable`；有 selector 时评估 `connect`/`share` |
| `Subscription`、`Subscriber`、`Observable` 的多个内部字段和方法不再公开 | 删除对 `_subscribe`、`_isScalar`、`syncError*` 等内部实现的依赖 |
| 多个创建函数和操作符的显式泛型签名改变 | 优先让 TypeScript 推断 `combineLatest`、`forkJoin`、`of`、`zip` 等类型 |
| `finalize` 的执行顺序改变 | 检查依赖多个 `finalize` 回调先后顺序的资源释放逻辑 |
| `audit`、`buffer`、`debounce`、`sample`、`throttle` 等 notifier 完成语义改变 | 为 notifier 补充明确的 next 通知，并增加完成边界测试 |
| `race`、`zip`、`ReplaySubject` 等修复了旧行为 | 对同步完成/报错、无限 iterable、重订阅缓存场景增加回归测试 |
| Ajax 配置类型从 `AjaxRequest` 转向 `AjaxConfig`，并移除 IE10 及以下支持 | 更新显式类型；确认浏览器支持基线 |

完整清单以 RxJS 官方的 [6.x → 7.x change summary](https://github.com/ReactiveX/rxjs/blob/7.8.2/docs_app/content/6-to-7-change-summary.md) 为准。

## 使用方式

安装本仓库配方后，在待升级项目执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-rxjs-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsTo7_8_2
```

确认 patch 后将 `dryRun` 改为 `run`，随后重新生成锁文件，并执行 TypeScript 编译、单元测试和端到端测试。

## 模块验证

```bash
mvn -pl rewrite-rxjs-upgrade -am clean verify
```
