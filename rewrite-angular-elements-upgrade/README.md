# @angular/elements 12–15 → 20.3.25

本模块对应 `开源软件升级.xlsx` 中的 `@angular/elements`。README 是不兼容点 spec；推荐配方则把可证明安全的依赖变更真正写入 `package.json`，并把无法仅凭语法决定的注册、输入、输出、生命周期、SSR、策略扩展和工具链工作精确标记到源码或 manifest 节点，而不是只提供阅读材料。

推荐入口：

```text
com.huawei.clouds.openrewrite.angularelements.MigrateAngularElementsTo20_3_25
```

只做工作簿严格版本升级：

```text
com.huawei.clouds.openrewrite.angularelements.UpgradeAngularElementsTo20_3_25
```

推荐入口在 YAML 中显式先复用公开 `Upgrade`，再执行 manifest 和 TypeScript/JavaScript 审计。公开 Elements API 在目标版本仍是 `createCustomElement(component, config)`，因此不存在可以普遍证明等价的源码重命名；本模块不会为了制造 AUTO 数量而伪造替换。其自动化价值由严格依赖改写和准确 `SearchResult` 共同提供，后者会直接出现在 OpenRewrite diff 中。

## 工作簿严格边界

完整扫描工作簿后只接受以下八条源版本：

| 工作表行 | 源版本 | 目标版本 |
| ---: | --- | --- |
| 424 | `12.2.13` | `20.3.25` |
| 425 | `12.2.16` | `20.3.25` |
| 426 | `13.1.3` | `20.3.25` |
| 427 | `13.3.12` | `20.3.25` |
| 428 | `14.2.12` | `20.3.25` |
| 429 | `15.1.5` | `20.3.25` |
| 430 | `15.2.1` | `20.3.25` |
| 431 | `15.2.9` | `20.3.25` |

每个版本仅接受 exact、单一 caret 或单一 tilde，例如 `15.2.9`、`^15.2.9`、`~15.2.9`，并保留 operator 写成 `20.3.25`、`^20.3.25`、`~20.3.25`。表格外固定值、复合范围、OR/hyphen、prerelease/build、tag、变量，以及 `workspace:`、npm alias、Git、file/link、URL 协议均不猜测。

目标 npm 包是不可变的 [`@angular/elements@20.3.25`](https://registry.npmjs.org/@angular/elements/20.3.25)，其中 `gitHead` 固定到 Angular release cut [`91858c91`](https://github.com/angular/angular/commit/91858c91c75d7ecf36d65daff85bab77d1d587ad)，Node engine 为 `^20.19.0 || ^22.12.0 || >=24.0.0`，peer `@angular/core` 为精确 `20.3.25`，peer RxJS 为 `^6.5.3 || ^7.4.0`。

## AUTO / MARK / NO-OP

| 类型 | 可执行行为 |
| --- | --- |
| **AUTO** | 仅在项目 `package.json` 根对象四个直接依赖区，把八个工作簿源版本升级到 `20.3.25` 并保留 `^`/`~` |
| **MARK** | 标记复杂/外部版本 owner、Angular package-group 锁步、Node/TypeScript/CLI/builder、RxJS/zone/zoneless、legacy polyfill/ngcc，以及 owned Elements 源码中的创建、registry、input/output、lifecycle、SSR、typing/schema、strategy/deep import/Shadow DOM 节点 |
| **NO-OP** | lockfile、central-only manifest、普通 JSON、相似包、非字符串、表外版本、协议/范围、没有 `@angular/elements` 所有权证据的同名函数/DOM、普通 div，以及生成、安装、vendor、dist/build/cache 目录 |

路径隔离只检查父目录组件并统一小写。`generated*`、`install*` 以及 `node_modules`、`vendor`、`dist`、`build`、`out`、`target`、框架/包管理器缓存、coverage/report/test output 均跳过；真正的 manifest 叶文件仍必须精确名为 `package.json`。

严格配方只处理根级 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。`overrides`、`resolutions`、pnpm policy、catalog/catalogs 和嵌套伪依赖对象不会被 AUTO；当同一 manifest 确实直接使用 Elements 时，推荐配方会在这些 owner 的准确值上 MARK。仅 central override 而没有直接 Elements 依赖的兄弟 manifest 不触发整份工具链审计。

## 不兼容点：spec → recipe → test

事实依据固定到 target release 的 [`packages/elements`](https://github.com/angular/angular/tree/91858c91c75d7ecf36d65daff85bab77d1d587ad/packages/elements)、[官方 Elements guide](https://angular.dev/guide/elements)、[`createCustomElement` API](https://angular.dev/api/elements/createCustomElement) 和 target package metadata。

| 边界 | 目标语义与风险 | 配方行为 | 覆盖测试 |
| --- | --- | --- | --- |
| Angular package group | Elements peer core 必须精确 `20.3.25`；跨 12→20 不能跳过各 major 官方 migrations | 非对齐 core/common/compiler/platform/router 等直接值 MARK | `AngularElementsManifestRiskTest` |
| Node/toolchain | target package Node engine 为 `^20.19.0 || ^22.12.0 || >=24.0.0`；Angular 20 的 TS/CLI/builder/ESM 需联合验证 | engine、TypeScript、CLI/compiler-cli/build builder 精确 MARK | 同上 |
| RxJS/zone | peer RxJS 仍允许 6/7；Elements change detection 可运行 zone 或 zoneless，行为不能由版本字面量决定 | 出现的 RxJS、zone.js 值 MARK | 同上 |
| Custom Elements polyfill | Angular 20 支持浏览器原生 Custom Elements；旧 polyfill 可能重复 patch registry | 旧 Web Components/polyfill 声明 MARK，不自动删除 | 同上 |
| ngcc/View Engine | Angular 20 已无 ngcc/View Engine 路径 | 含 `ngcc` 的 scripts value MARK | 同上 |
| `createCustomElement` | config injector 是默认实例 injector；standalone/NgModule provider、zone、error 和 teardown 归属必须复核 | 只对 package-root named/alias/namespace/require 所有权明确的调用 MARK | `AngularElementsSourceRiskTest`、recommended fixtures |
| tag/registry | tag 必须含连字符；不得复用 Angular component selector；重复 bundle/HMR/microfrontend define 会抛错；SSR 没有浏览器 global | `customElements`/`window.customElements`/`globalThis.customElements` 的 define 精确 MARK | 同上 |
| lookup/upgrade/lifecycle | 元素插入时 bootstrap、移除时 destroy；disconnect/reconnect、移动节点、预注册节点与 realm registry 需真实 DOM 测试 | `get`、`whenDefined`、`upgrade` MARK | 同上 |
| input/attribute | input 名映射成 dash-separated lowercase attribute；attribute 是 string，property input 可为对象 | 已识别 custom tag 的 create/query 与其 attribute 调用 MARK | 同上 |
| output/event | output 成为 `CustomEvent`；payload 位于 `event.detail`，bubbles/composed 不应凭想象 | 已识别 custom element 的 event listener MARK | 同上 |
| typing | `NgElement & WithProperties<T>`、`HTMLElementTagNameMap` 决定 DOM property/event 类型 | root typing import MARK | 同上 |
| Angular template schema | 消费 Web Component 常需 `CUSTOM_ELEMENTS_SCHEMA`，但它也会弱化未知元素/属性诊断 | core schema import MARK | 同上 |
| custom strategy | `NgElementStrategy`/factory 是高级扩展点，涉及 connect/disconnect、inputs、events 与 injector 生命周期 | strategy import、调用和 lifecycle callback MARK | 同上、官方 strategy fixture |
| deep import | target exports 仅 `.` 与 `./package.json`；物理 `src/*`/distribution path 不是公共 API | import/require 准确 MARK | 同上 |
| Shadow DOM | slot/style/focus/composed event/SSR 行为需要浏览器验证 | 已识别 custom element 的 Shadow DOM 调用 MARK | 同上 |

例如 dry-run 会把任务直接贴到业务节点：

```ts
const ctor = /*~~(Angular createCustomElement boundary detected; ...)~~>*/createCustomElement(Widget, { injector });
/*~~(Custom-element registry boundary detected; ...)~~>*/customElements.define('my-widget', ctor);
```

marker 不会因为第二个 cycle 重复叠加。普通模块中同名 `createCustomElement`、没有 Elements import 的 registry 代码、以及 Elements 文件里的普通 `div.setAttribute()` 都有明确 NO-OP 测试。

## 必须执行的业务验证

依赖升级跨越八个 Angular major。先按 major 顺序运行官方 [`ng update`](https://angular.dev/cli/update) migrations，再收敛所有 Angular framework package 到 `20.3.25`；本配方不会伪装成 Angular CLI schematics 的替代品。

对每个实际 custom element 至少验证：

1. 同一 bundle、HMR、多个微前端和多 Angular runtime 下只注册一次，tag 含连字符且不等于 component selector。
2. 在 SSR/prerender/test runner 中不提前访问 `window`、`document`、`customElements`；浏览器 hydration/首次插入时机一致。
3. 所有 input alias、dash-case attribute、boolean/number/object coercion、required input 和 property-before-connect 顺序。
4. 所有 output alias、大小写、`CustomEvent.detail`、bubbles/composed 预期、React/Vue/plain DOM adapter 与 listener teardown。
5. insert/remove/reinsert、跨容器移动、预注册节点 `upgrade()`、异步 `whenDefined()`、异常与 injector/application destroy。
6. zone.js 与 `provideZonelessChangeDetection` 的真实 change detection；RxJS subscription 和 strategy event stream 不泄漏。
7. Shadow DOM slots/style/custom properties/focus/event composed path，以及 CSP、Trusted Types、attribute/HTML sanitization。
8. 目标 Node/TypeScript/CLI/builder/package manager 下 clean build，重建 lockfile，并在所有受支持浏览器和外部 host framework 做 E2E。

## 固定官方与真实仓库用例

源 tag 均固定到不可漂移 commit：

| 版本 | Angular commit |
| --- | --- |
| `12.2.13` | [`8a99ef18`](https://github.com/angular/angular/tree/8a99ef18b4ae58736f0b7457e88908d3adcad613) |
| `12.2.16` | [`b2a081a1`](https://github.com/angular/angular/tree/b2a081a19d6d006023b0f79076e9e499441a7810) |
| `13.1.3` | [`7cb3b78a`](https://github.com/angular/angular/tree/7cb3b78acc001bb261bf236de18c72d665194199) |
| `13.3.12` | [`ec58266e`](https://github.com/angular/angular/tree/ec58266eabf93d23e58b58615cd8d1cf5e449f99) |
| `14.2.12` | [`61e69c87`](https://github.com/angular/angular/tree/61e69c87e6b577f1c5a71fb5c39c3bd6125c4b91) |
| `15.1.5` | [`d69ef721`](https://github.com/angular/angular/tree/d69ef721d43ec62ea901cfb90d9c4e5252066f72) |
| `15.2.1` | [`17499715`](https://github.com/angular/angular/tree/174997150f814f2a9addd41e64b78b2f27e7b26a) |
| `15.2.9` | [`2d68de5b`](https://github.com/angular/angular/tree/2d68de5bb118ff27f47aa2abedea112b2f0a3495) |
| `20.3.25` | [`91858c91`](https://github.com/angular/angular/tree/91858c91c75d7ecf36d65daff85bab77d1d587ad) |

recommended tests 使用下列固定公开源码的缩减片段：

- Angular 官方 target [`create-custom-element_spec.ts`](https://github.com/angular/angular/blob/91858c91c75d7ecf36d65daff85bab77d1d587ad/packages/elements/test/create-custom-element_spec.ts)：strategy factory、registry define/upgrade、connect 生命周期。
- `mal90/angular-elements-sample` [`087cad05` 的 `app.module.ts`](https://github.com/mal90/angular-elements-sample/blob/087cad052d1d939d4ad09330f1bcf5b6495dc5c8/src/app/app.module.ts)：旧 NgModule/Injector、create、define 组合。
- `giacomo/elements-template` [`cf21f05f` 的 `src/main.ts`](https://github.com/giacomo/elements-template/blob/cf21f05f20ffa4ba65edd120e7dbda300432189d/src/main.ts)：standalone `createApplication`、zoneless provider、duplicate guard 和 registry。

测试结构参考固定 OpenRewrite [`rewrite-test@0a0b6d2c`](https://github.com/openrewrite/rewrite/tree/0a0b6d2c42d710995d74846aa7c461de2c44f521/rewrite-test) 与 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5)，覆盖公开 YAML recipe discovery、严格 before→after、复杂值/路径 NO-OP、marker 内容、真实路径、两周期幂等和固定公开仓库 fixture。

## 验证

```bash
mvn -f rewrite-angular-elements-upgrade/pom.xml clean verify
```

运行 recommended recipe 后先审查全部 `~~(...)~~>`，再执行上述逐 major migration、clean install/build、lockfile 重建、浏览器/SSR/host-framework E2E；不要把“代码可编译”当成 custom element 输入输出和生命周期兼容的证明。
