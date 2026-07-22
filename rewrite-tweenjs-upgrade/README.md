# Tween.js 19/20 升级到 23.1.1

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `@tweenjs/tween.js`：源版本仅为 `19.0.0`、`20.0.3`，目标版本为 `23.1.1`。推荐执行：

```text
com.huawei.clouds.openrewrite.tweenjs.MigrateTweenJsTo23_1_1
```

推荐配方先完成可证明安全的修改，再把不能脱离业务语义判断的问题标成 OpenRewrite `SearchResult`。如只需要其中一部分，可分别执行：

| 配方 | 用途 |
| --- | --- |
| `UpgradeTweenJsTo23_1_1` | 严格升级表格指定的依赖声明 |
| `MigrateDeterministicTweenJsTo23` | 迁移确定性的 JS/TS 入口和 main-group 配置 |
| `AuditTweenJs23Source` | 标记 JS/TS 行为不兼容点 |
| `AuditTweenJs23Project` | 标记 manifest、TypeScript、Jest 和 bundler JSON 风险 |
| `AuditTweenJs23TemplatesAndConfig` | 标记 HTML/CDN、复制与部署配置风险 |

## AUTO / MARK / NO-OP

| 场景 | 处理 | 原因或结果 |
| --- | --- | --- |
| `package.json` 四个直接依赖区中的 `19.0.0`、`20.0.3` 及各自 `^`、`~` 形式 | **AUTO** | 精确改为 `23.1.1`；这是表格可证明的升级集合 |
| 精确的 `dist/tween.esm.js`、`dist/tween.cjs.js`、`dist/tween.cjs`、`dist/index.cjs(.js)` import/require/static dynamic import | **AUTO** | 统一为公开根入口 `@tweenjs/tween.js`，保留原单双引号（静态 template literal 规范化为普通字符串） |
| 已证明属于包默认/namespace 对象且绑定未被局部声明遮蔽的 `new TWEEN.Tween(state, TWEEN)` | **AUTO** | 第二参数是导出的默认 main group，与目标构造器默认值相同，删除后行为等价；同名参数/变量或仅注释中的 import 完全不改 |
| 其他 `@tweenjs/tween.js/*` 深导入、CDN、vendor/node_modules 物理路径 | **MARK** | v21 `exports` 只公开包根；CDN/global/复制策略还涉及格式、SRI、CSP 和部署路径 |
| `.to()`、started/paused 后重新 `.to()`、`.dynamic(true)` | **MARK** | v20 的 snapshot 默认值、运行中 retarget 抛错和 interpolation array 副作用需要业务选择 |
| global/group/tween `update()`、显式 `start/pause/resume` 时间、`Date.now()` | **MARK** | 必须统一毫秒单调时钟，并验证 RAF、fake clock、后台恢复和单一推进者 |
| `new Tween(x, false)`、自定义 `Group`、`add/remove/removeAll` | **MARK** | 生命周期、teardown、并发 global/group/tween 更新无法由语法决定 |
| repeat/repeatDelay/yoyo/chain 与所有生命周期 callback | **MARK** | v23 会在一次大跳时跨过多个 repeat，状态和 callback 次序可能变化 |
| 负 delay/duration、`_duration`、修改内置 `Easing` | **MARK** | 分别涉及未支持 offset、23.1.1 零时长完成、公开 `getDuration()` 和被冻结对象 |
| 未列版本、复合 range、比较器、OR、hyphen、`v`、预发布、build metadata | **NO-OP + MARK** | 自动升级不猜 npm 语义；项目审计会在直接声明上说明人工决策原因 |
| `workspace:`、`npm:` alias、catalog、变量、Git、file/link/portal、URL、tag | **NO-OP + MARK** | 版本所有权在 workspace/catalog/上游来源，需修改真正所有者 |
| target/更高版本、相似包、非直接依赖区 | **NO-OP** | 防止降级、误改 `tween.js`/`@types/tween.js` 或 overrides/resolutions |
| lockfile、普通 JSON、备份/近似文件名 | **NO-OP** | 配方不手写依赖解析结果；使用工程原 package manager 重建 lockfile |
| 无 Tween 绑定的同名 `.to()`/`update()`/`Group`，注释和文档 | **NO-OP** | 绑定或文件类型不满足证据边界，不产生行为标记 |

## 主要不兼容修改点

### v20：target snapshot 与运行中 retarget

`dynamic` 默认值由 `true` 改为 `false`。从 `19.0.0` 升级时，`.to(target)` 默认复制目标值；之后修改 `target` 不再移动终点。确实需要活动目标时才显式 `.dynamic(true)`，并注意该模式会修改 target 中的 interpolation array。

v20 起，tween 已 started 或 paused 后调用 `.to()` 会抛错。运行中改变目标应按业务语义选择 stop/recreate，或设计明确的 dynamic target；配方只在已证明属于 Tween.js 的调用节点做标记。

### v21/v22：package exports、ESM 与 CommonJS

v21 加入 package `exports`，目标 `23.1.1` 仅公开根入口。目标 manifest 声明：

```json
{
  "type": "module",
  "main": "dist/tween.cjs",
  "module": "dist/tween.esm.js",
  "types": "dist/tween.d.ts",
  "exports": {
    ".": {
      "import": "./dist/tween.esm.js",
      "require": "./dist/tween.cjs",
      "types": "./dist/tween.d.ts"
    }
  }
}
```

因此应用代码应使用：

```ts
import { Easing, Group, Tween, update } from '@tweenjs/tween.js';
import * as TWEEN from '@tweenjs/tween.js';
```

```js
const TWEEN = require('@tweenjs/tween.js');
```

v22 修正了 CommonJS 物理文件命名；应用不应再拼接 `tween.cjs.js`/`index.cjs.js`。目标包自带类型，历史 `@types/tween.js` 会被标记，但配方不直接删除它，避免误删项目自己的 declaration augmentation。

### v23/23.1.1：时间跳跃与负 duration

v23 修复浏览器休眠或低频更新时跨过多个 repeat duration 的推进逻辑。一次大跨度 `update(time)` 可能扣减多个 repeat，并改变 repeat/yoyo/chain 的状态与 callback 次序。至少测试连续 16ms、一次跨多个周期、后台标签恢复和可控 fake clock。

不传时间时 Tween.js 使用 `performance.now()`。一旦显式传时间，`start(time)`、`pause(time)`、`resume(time)`、global/group/tween `update(time)` 必须采用同一毫秒单调时钟；不要混用 `Date.now()` epoch、RAF 毫秒和视频秒。

23.1.1 将负 duration 按零处理并立即完成，避免旧版异常中间值，但负值通常仍是上游业务错误。`.delay(-n)` 也不是受支持的时间偏移方式。配方标记负字面量，最终校验应在业务边界完成。

### Group、callback 与公开 API

`new Tween(object, false)` 不加入任何 group，必须由调用方单独 `tween.update(time)`。自定义 `Group` 应只有一个更新循环，并在组件销毁时由同一 owner 执行 remove/removeAll；不要让 global、group 和单 tween 在同一帧重复推进。

目标版提供 `getDuration()`，不应读取 `_duration`。内置 `Easing` 自 v19 已被冻结，自定义 easing 应保存在应用自己的函数/对象中后传给 `.easing(fn)`。`onStart`、`onEveryStart`、`onUpdate`、`onRepeat`、`onComplete`、`onStop` 都会被精确标记，以提醒覆盖 zero duration、stop、repeat/yoyo/chain 和大时间跳测试。

## 固定上游依据

所有关键依据都固定到不可变 commit，而不是浮动 `main`：

- Tween.js `v19.0.0`：[`351810c1b1a4e2701a274229ae5ea9694a34c696`](https://github.com/tweenjs/tween.js/tree/351810c1b1a4e2701a274229ae5ea9694a34c696)；
- Tween.js `v20.0.3`：[`6abed8318a3fe602b616bb440f6037b061615e6a`](https://github.com/tweenjs/tween.js/tree/6abed8318a3fe602b616bb440f6037b061615e6a)；
- Tween.js `v23.1.1` 目标源码、manifest、类型和测试：[`451041100e54c8cd2472872e307c1895f7ede7db`](https://github.com/tweenjs/tween.js/tree/451041100e54c8cd2472872e307c1895f7ede7db)；
- OpenRewrite `8.87.5` 测试/visitor 参考：[`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)。

版本行为同时对照 Tween.js 官方 [v20](https://github.com/tweenjs/tween.js/releases/tag/v20.0.0)、[v21](https://github.com/tweenjs/tween.js/releases/tag/v21.0.0)、[v22](https://github.com/tweenjs/tween.js/releases/tag/v22.0.0)、[v23](https://github.com/tweenjs/tween.js/releases/tag/v23.0.0) 与 [v23.1.1](https://github.com/tweenjs/tween.js/releases/tag/v23.1.1) release notes。

## 固定真实仓库用例

测试不是只使用合成代码，而是从多个公开工程的不可变提交缩减 before→after/marker/no-op 场景：

- [awslabs/iot-app-kit@f38251529912f65e4994b6a19fd035a29dd9d8c4](https://github.com/awslabs/iot-app-kit/tree/f38251529912f65e4994b6a19fd035a29dd9d8c4)：ESM monorepo 子模块 `scene-composer` 的 `^20.0.3`；
- [hululuuuuu/GlobeStream3D@ba75e68c1575673cbbb1d2edc89ff7c28586d4db](https://github.com/hululuuuuu/GlobeStream3D/tree/ba75e68c1575673cbbb1d2edc89ff7c28586d4db)：`^20.0.3`、namespace `TWEEN.Tween`、repeat/callback 以及 RAF 中的 aliased named `update`；
- [mikemklee/three-viewcube@036db7b9e74c23fcb2dc6c7cb72d7220504ca558](https://github.com/mikemklee/three-viewcube/tree/036db7b9e74c23fcb2dc6c7cb72d7220504ca558)：peer `^19.0.0`、default `TWEEN`、链式 `.to().easing().onUpdate()` 和 global `update()`；
- [UBA-GCOEN/StichHub@1eb512f98f1f76cab581ceda39b9f89fbfb4547b](https://github.com/UBA-GCOEN/StichHub/tree/1eb512f98f1f76cab581ceda39b9f89fbfb4547b)：React/Vite/Three.js 工程中的 `^19.0.0`。

测试风格参考固定 OpenRewrite commit 中的 RewriteTest、JSON visitor 和 JavaScript visitor 用例：验证格式保持、每个 public recipe 可发现/可校验、两周期幂等、marker 内容、复杂 npm spec/protocol、workspace 子包、lockfile/备份/相似包 no-op 和无 Tween 绑定的同名 API no-op。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-tweenjs-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.tweenjs.MigrateTweenJsTo23_1_1
```

审查自动 patch 和 `/*~~(...)~~>*/` 标记后，用项目原 package manager 重建 lockfile，并运行 typecheck、unit/E2E、生产 bundle、SSR/CommonJS（如有）、真实 RAF、fake clock、后台恢复、repeat/yoyo/chain 和 teardown 测试。

模块验证：

```bash
mvn -f rewrite-tweenjs-upgrade/pom.xml clean verify
```
