# React DOM 升级到 19.0.0

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `react-dom`，严格处理以下可见版本：

| 源版本 | 目标版本 |
| --- | --- |
| `16.6.1` | `19.0.0` |
| `16.14.0` | `19.0.0` |
| `17.0.2` | `19.0.0` |
| `18.2.0` | `19.0.0` |

推荐运行完整迁移配方：

```text
com.huawei.clouds.openrewrite.reactdom.MigrateReactDomTo19_0_0
```

| 配方 | 用途 |
| --- | --- |
| `com.huawei.clouds.openrewrite.reactdom.UpgradeReactDomTo19_0_0` | 只升级表格选中的安全依赖声明 |
| `com.huawei.clouds.openrewrite.reactdom.MigrateDeterministicReactDomSourceTo19` | 只执行确定性源码改写 |
| `com.huawei.clouds.openrewrite.reactdom.AuditReactDom19SourceCompatibility` | 标记 client、hydration、server、test、DOM 行为风险 |
| `com.huawei.clouds.openrewrite.reactdom.AuditReactDom19ProjectCompatibility` | 标记复杂声明、companion、types、framework、tooling、JSX config 风险 |
| `com.huawei.clouds.openrewrite.reactdom.AuditReactDom19ResourceCompatibility` | 标记 HTML UMD 和 inline legacy root |

## 依赖升级边界

升级器只处理文件名为 `package.json` 的文件，只访问 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 的直接成员。包名必须精确为 `react-dom`，值必须是表格版本的 exact、单一 caret 或单一 tilde，例如 `17.0.2`、`^17.0.2`、`~17.0.2`。输出固定为 `19.0.0`。

复杂 range、协议、alias、tag、变量和非字符串值不会被自动修改；完整配方会在其 manifest 原位置增加 `SearchResult`，防止“安全 no-op”变成不可见的遗漏。表格未列出的普通三段 semver 也保持不改，但推荐配方会将其标记出来，避免把表格边界扩成泛化升级策略或静默漏项。

## 确定性 AUTO

OpenRewrite 当前未随本模块提供 JavaScript/TypeScript AST，因此源码自动迁移使用 comment/string/template/regex-safe 的保守词法边界。只有同时满足结构约束的官方一对一变化才会执行：

| 不兼容点 | 处理 | 安全条件与结果 | 测试 |
| --- | --- | --- | --- |
| default/namespace `ReactDOM.render(element, container)` | AUTO | 文件仅有一个 legacy root call；standalone 两参数；简单 identifier/getElementById/querySelector container；无 client import/root 名冲突；添加 `createRoot`，保留旧 import，保存 root 再 render | 官方 codemod fixture、真实应用、alias、callback/multi-root/conflict no-op、CRLF、幂等 |
| default/namespace `ReactDOM.hydrate(element, container)` | AUTO | 同一单 root 条件；添加 `hydrateRoot`，交换参数，首次 hydration 不额外 render | namespace hydrate 用例 |
| sole named `{ render }` / `{ hydrate }` | AUTO | import 只有该 binding，调用唯一且满足相同 standalone 条件；将 import 改到 `react-dom/client` | named render/hydrate 用例 |
| `import { act } from 'react-dom/test-utils'` | AUTO | import 只包含 `act`，文件没有 React act import 或同名声明 | import 与 conflict/no-op 用例 |
| ref callback 隐式返回简单赋值 | AUTO | `ref={node => (ref.current = node)}` 改成显式 block body，赋值语义不变 | TSX before/after、幂等 |

多行 JSX、render callback、返回值、多个 root、复杂/dynamic container、mixed/aliased binding 或名称冲突均不猜测，交给 MARK。

## 不兼容点：MARK / NO-OP

React 与 React DOM 必须保持同一兼容 release line。完整配方不会擅自升级另一个表格软件，而会把不匹配的 `react` 声明标出。

| 不兼容点 | 处理 | 迁移要求 | 测试覆盖 |
| --- | --- | --- | --- |
| complex/protocol/dynamic `react-dom` 声明 | MARK | 明确 workspace/catalog/alias/tag/变量 owner 或 range 支持矩阵后再修改 | 13 种声明、四依赖区、non-string |
| 表格未列普通 semver、目标/更高版本 | MARK / NO-OP | 未列版本不越过 XLSX 边界并明确 MARK；精确目标 `19.0.0` 及其单一 caret/tilde 声明保持 NO-OP | old/newer/target 用例 |
| `react` 不在 19.0.0 line | MARK | 与 React DOM 对齐并检查重复 React、invalid hook 与 renderer protocol | companion 用例 |
| `@types/react` / `@types/react-dom` | MARK | 同步 v19 types，再处理 ref、JSX、ReactElement、client/server/static entry types | 两个 types package；aligned v19 no-op |
| `react-test-renderer`、Testing Library、Enzyme | MARK | renderer 已弃用；升级 Testing Library；Enzyme 无官方 React 18/19 adapter | 三个 package marker、test-utils source |
| Next、Gatsby、React Router DOM | MARK | 选择明确支持 React 19 的版本，复测 RSC、SSR、hydration、navigation 与 conditions | framework/router 参数用例 |
| Vite plugin、react-scripts、scheduler | MARK | 验证 JSX/bundler/Jest；CRA 维护状态；避免独立 pin 内部 scheduler | tooling 参数用例 |
| classic JSX transform | MARK | `jsx: react/preserve`、Babel `runtime: classic` 由 framework/compiler owner 决定如何切 automatic | classic/preserve/modern no-op |
| 剩余 `render` / `hydrate` | MARK | callback、return value、复杂 root 要按真实 ownership/commit 语义迁移 | Faithlife 多行真实代码、callback、named import |
| `unmountComponentAtNode` | MARK | 持有创建该 tree 的 root，并在宿主 teardown 调 `root.unmount()`；unmount 后不可再次 render | Testing Library 真实代码与 call-site |
| `findDOMNode` | MARK | 使用 owned/forwarded DOM ref，升级 transition/focus/popover 等第三方集成 | alias/direct call |
| `react-dom/test-utils` | MARK | `act` 从 `react` 导入；Simulate/renderIntoDocument 等迁到用户行为测试；dynamic fallback 需一起改 | static/dynamic import |
| removed unstable API / secret internals | MARK | 选择公开 root/event/scheduling/framework API，不能映射内部字段 | unstable family、internals |
| `renderToNodeStream` | MARK | 迁到 `renderToPipeableStream` 是 stream 架构迁移，需处理 shell、abort、error、backpressure | 真实 styled-components stream |
| `renderToString` / `renderToStaticMarkup` | MARK | 确认非 streaming 与 Suspense/data 限制仍符合业务 | 两类 legacy SSR |
| pipeable/readable stream | MARK | 核对 Node/Web runtime、status/header、abort、bootstrap、nonce、backpressure、hydration | streaming family |
| `react-dom/static` prerender | MARK | static API 等待数据，不是 streaming SSR 等价替换；验证 SSG/cache/abort | prerender family |
| runtime-specific / deep imports | MARK | 核对 browser/node/edge/bun conditions；删除 `cjs/**`、`src/**` 深度导入 | path-aware import 用例 |
| `hydrateRoot` 与 `identifierPrefix` | MARK | 消除 server/client mismatch，多 root 前缀必须一致且唯一，复查 recovery | hydration/options 用例 |
| root error reporting | MARK | React 19 改变 uncaught/caught/recoverable 报告；复查 Error Boundary、reportError/console、监控去重和 callbacks | createRoot、三个 callback、digest |
| automatic batching / `flushSync` | MARK | 复查 Promise/timer/native event render 次序，只在同步 DOM read 必需时强制 flush | batching control |
| custom act environment | MARK | 验证 runner/library 是否仍需要 `IS_REACT_ACT_ENVIRONMENT` 以及 async scheduling | setup marker |
| StrictMode / Suspense | MARK | effect/ref 必须可重放；回归 hydration fallback/retry/reveal/effect ordering | JSX call-site |
| ref callback cleanup typing | MARK | 非简单赋值 callback 需要人工明确返回/cleanup 语义 | exact ref marker |
| `javascript:` / empty `src`/`href` | MARK | sanitize 或显式省略；React DOM 19 的安全/空属性行为改变 | JSX attribute marker |
| React 17 event changes | MARK | SyntheticEvent pooling 已删除，scroll 不再模拟 bubbling；验证旧 `persist()` 与 nested scroll | event/onScroll markers |
| `createPortal`、modern `root.unmount()`、现代 Vite root | NO-OP | 这些 API 本身仍受支持，不误标或误改 | Feedzai、Vite 与 portal/root no-op |
| UMD/global ReactDOM | MARK | React DOM 19 不发布 UMD；迁到 ESM CDN 或 bundler，联动 CSP/SRI/offline 发布 | unpkg、jsDelivr、inline root、ESM no-op |

标记是 dry-run/code review 的决策清单，不表示风险已经修复。

## 明确 NO-OP

- 低层依赖升级器对表格未列版本、目标/高版本、prerelease、build metadata 均不改；推荐配方会对未列普通版本和其余未解析声明 MARK，精确目标声明不制造 marker；
- `v17.0.2`、`=18.2.0`、`^v16.14.0`、comparator、hyphen、OR、wildcard；低层升级器均不改，其中复杂形态只在推荐审计中 MARK；
- `workspace:`、npm alias、`file:`、`link:`、Git/GitHub、URL/tarball、tag、catalog、变量；
- `overrides`、`resolutions`、`pnpm.overrides`、peer metadata、lockfile；
- 相似包名、普通 JSON、非字符串值（升级器 no-op，推荐审计 marker）；
- 注释、字符串、template、regex、Markdown、其他对象的同名方法；
- 无法证明 root ownership、binding 或 runtime 的源码。

## 固定官方依据

- [React 18 upgrade guide](https://react.dev/blog/2022/03/08/react-18-upgrade-guide)：client root、hydrate、unmount、batching、StrictMode、SSR 与 testing；
- [React 19 upgrade guide](https://react.dev/blog/2024/04/25/react-19-upgrade-guide)：删除的 React DOM/test API、errors、UMD、JSX 与 types；
- [React 19 stable release](https://react.dev/blog/2024/12/05/react-19)：hydration diagnostics、Suspense、custom elements、static APIs；
- [React DOM `19.0.0` 固定提交 `7aa5dda` package.json](https://github.com/react/react/blob/7aa5dda3b3e4c2baa905a59b922ae7ec14734b24/packages/react-dom/package.json)：目标版本、React peer 与 client/server/static exports；
- [React 官方 render codemod 固定提交 `5207d59`](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/replace-reactdom-render.ts)，固定 [before](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.input.js) / [after](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.output.js)；
- [OpenRewrite `ChangeValueTest` 固定提交 `b3008cc`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [RewriteTest harness](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)。

## 真实仓库回归来源

所有链接固定 commit，并截取最小可验证上下文：

- [reactjs/react-codemod `5207d59` fixture](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.input.js)：官方 render before→after；
- [Design-Patterns-JavaScript `2c7ef90`](https://github.com/zoltantothcom/Design-Patterns-JavaScript/blob/2c7ef902dbefb8a7a2ecea407ac7e8e6682f5b0a/index.js)：真实单行 root 自动迁移；
- [Feedzai brushable-histogram `a46666e`](https://github.com/feedzai/brushable-histogram/blob/a46666e5de8cd24cfb490d14e26ea046b3945e60/package.json) 与 [Portal](https://github.com/feedzai/brushable-histogram/blob/a46666e5de8cd24cfb490d14e26ea046b3945e60/src/common/components/Portal.js)：16.6.1 dev dependency、broad peer 和 supported createPortal no-op；
- [Faithlife styled-ui `4b771c6` package](https://github.com/Faithlife/styled-ui/blob/4b771c63d1ed3381dfc40c29a87416402364d0bd/package.json) 与 [多行 render](https://github.com/Faithlife/styled-ui/blob/4b771c63d1ed3381dfc40c29a87416402364d0bd/catalog/index.js)：16.14 依赖与复杂 JSX marker；
- [Grafana `6134e3c`](https://github.com/grafana/grafana/blob/6134e3cf35a99c7dd3041b7ececb47cb7619ba9a/package.json)：React/DOM 17 和独立 DefinitelyTyped matrix；
- [Vite `055d2b8` React template package](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/package.json) 与 [modern client root](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/src/main.jsx)：18.2 dependency 与 source no-op；
- [React Testing Library `be9d81d`](https://github.com/testing-library/react-testing-library/blob/be9d81d91314c9f0bafaa363f70b409b4b31989c/src/pure.js)：legacy hydrate/render/unmount compatibility branch marker；
- [Universal React Apollo Registration `26f4864`](https://github.com/simpletut/Universal-React-Apollo-Registration/blob/26f48641468386b412058f1a190d338e55c69281/src/client.js)：真实多行 hydration marker；
- [styled-components streaming bug `b471ecd`](https://github.com/chin2km/styled-components-react-streaming-bug/blob/b471ecdc897320db22cdbf94240407cd9a446c59/src/ssr-render-methods/renderToNodeStream.js)：真实 `renderToNodeStream` pipeline marker。

当前 125 个测试调用：42 个依赖严格边界、46 个源码 AUTO/MARK/no-op/幂等/组合配方、37 个 manifest companion/config/resource 用例。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-dom-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.reactdom.MigrateReactDomTo19_0_0
```

审核所有 AUTO diff 与 MARK 后，对齐 React、React DOM、types、framework 和 test renderer，再使用项目原包管理器重建 lockfile。至少运行 typecheck、lint、unit/component/E2E、StrictMode、SSR/SSG/streaming、hydration、Error Boundary/monitoring、micro-frontend teardown、browser/edge runtime 与 bundle 检查。

模块验证：

```bash
mvn -pl rewrite-react-dom-upgrade clean verify
```
