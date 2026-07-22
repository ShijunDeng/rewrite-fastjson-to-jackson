# React 升级到 19.2.7

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `react`，精确处理 `16.6.1`、`16.14.0`、`17.0.2`、`18.2.0`、`19.0.0` 到 `19.2.7` 的依赖声明升级。

配方名称：

```text
com.huawei.clouds.openrewrite.react.UpgradeReactTo19_2_7
```

## 重要的版本联动约束

React 与 renderer（尤其 `react-dom`、`react-native`、测试 renderer 以及 Server Components 实现）必须按其支持矩阵成套升级。表格当前给出的目标是 `react@19.2.7`，但 `react-dom` 的目标是 `19.0.0`；这不是可直接上线的最终组合。执行本配方后，必须由依赖治理负责人决定把 renderer 统一到与 React 完全匹配的版本，或调整两者目标后再生成 lockfile。否则可能出现 incompatible renderer、invalid hook call、hydration、内部协议或 Server Components 运行时错误。

本模块有意只处理 `react`，不会隐藏这个冲突，也不会擅自覆盖表格中另一个软件的目标版本。

## 自动处理范围

配方扫描根目录及 workspace/monorepo 子目录的 `package.json`，只修改 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies` 中名称精确为 `react` 的直接声明。

表格五个版本的精确值，以及明确锚定这些版本的 caret、tilde、equal、`v`/`^v` registry 声明会统一写成精确 `19.2.7`。配方在目标叶节点使用等值白名单，不会对 JSON `null` 或对象做正则。没有列入表格的旧版本、目标/更新版本，以及 comparator、hyphen/OR range、prerelease、build metadata 和宽泛范围保持不变。

配方不会修改：

- `react-dom`、`react-native`、`react-test-renderer`、`@types/react`、`@types/react-dom`、React Router、框架或构建插件；
- `workspace:`、npm alias、`file:`、`link:`、Git/GitHub、URL/tarball、tag、catalog 或变量引用；
- `overrides`、`resolutions`、`pnpm.overrides`、peer metadata 和 lockfile；
- JavaScript、TypeScript、JSX/TSX、HTML script tag、测试、SSR/RSC 入口和构建配置；
- 相似包名、普通 JSON 或非字符串依赖值。

对于发布库，命中的 `peerDependencies.react` 也会写成精确目标，以保证机器迁移结果可复现；库维护者应在跨版本兼容测试后再决定是否发布经过证明的 peer range。

## 不兼容修改点

建议先迁移到 `18.3.1` 并消除警告，再进入 19.x；官方明确把 18.3 作为 React 19 的兼容诊断桥梁。16/17 项目还必须先完成 React 18 root、Strict Mode、自动批处理和 SSR 迁移。

| 变化 | 影响与迁移建议 |
| --- | --- |
| React 18 concurrent root | `ReactDOM.render`/`hydrate` 应先迁移为 `createRoot`/`hydrateRoot`；React 19 已删除旧入口。render callback 没有一对一替代，应按 DOM ref、effect 或调度目的重构。该工作属于 renderer/源码迁移，不由本配方猜测 |
| React 18 自动批处理 | `createRoot` 下 Promise、timeout 和原生事件的更新也会批处理。依赖中间 render、同步读取 DOM 或测试逐次断言的代码可能变化；仅在确有同步 DOM 要求时使用 `flushSync` |
| React 18 Strict Mode | 开发环境会模拟 effect 的卸载/重挂载；React 19 还会双调用 ref callback。effect、订阅、定时器、WebSocket、SDK 初始化和 ref cleanup 必须可重复执行并成对释放 |
| React 18 SSR/Suspense | Node streaming 推荐 `renderToPipeableStream`，edge 使用 `renderToReadableStream`；旧 `renderToNodeStream` 已弃用。回归 shell/error/status/header、abort、hydration 与 Suspense fallback |
| 新 JSX transform 成为 React 19 前提 | 升级 Babel、SWC、TypeScript、framework/bundler 插件并确认使用 `react-jsx`/`jsx-runtime`。仍依赖旧 UMD/global React 或自定义 JSX transform 的工程不能只改依赖 |
| React 19 删除 legacy API | string refs、module-pattern factories、`React.createFactory`、旧 legacy context、函数组件 `propTypes`/`defaultProps` 等已删除或不再生效；改为 ref callback、普通函数/JSX、新 Context、TypeScript/runtime schema 和默认参数 |
| React DOM 19 删除旧方法 | `render`、`hydrate`、`unmountComponentAtNode`、`findDOMNode` 被移除；分别迁移到 root API、`root.unmount()` 和 DOM ref。`react-dom/test-utils` 除迁移后的 `act` 外不再可用，`act` 从 `react` 导入 |
| ref 语义变化 | `ref` 可作为普通 prop，访问 `element.ref` 被弃用；ref callback 可以返回 cleanup，因此 TypeScript 不再接受会隐式返回赋值结果的 callback。改成 block body 并明确清理资源 |
| error 传播变化 | render 中未捕获错误不再按旧方式重新抛出；使用 root 的 `onUncaughtError`、`onCaughtError` 和 `onRecoverableError` 做监控。检查 Error Boundary、window error、日志去重和测试断言 |
| Suspense 行为变化 | React 19 会先提交最近 fallback，再预热 suspended siblings；19.2 会批量处理 SSR Suspense boundaries。加载顺序、请求预热、fallback 闪烁、stream chunk 和性能基线可能改变 |
| Strict Mode 与 memo | React 19 的开发期双 render 会复用第一次的 `useMemo`/`useCallback` 结果，但 ref callback 仍会额外执行。不要依赖 memo factory、render 或 ref 中的副作用 |
| UMD 构建被移除 | React 19 不再发布 UMD。HTML 直接引用 `unpkg/.../umd/react.*`、RequireJS 或全局 `React` 的应用要改为 ESM CDN 或正式 bundler，并重新设计 CSP/SRI/离线发布 |
| 测试 renderer 变化 | `react-test-renderer` 已弃用且切到 concurrent rendering，shallow re-export 被移除。优先迁移到 Testing Library；同步 snapshot、`act`、fake timers 和实现细节断言需重写 |
| TypeScript 19 类型变化 | 同步升级 `@types/react`/`@types/react-dom`。`useRef` 需要参数、refs 统一可变、`ReactElement` 默认 props 变为 `unknown`、全局 `JSX` 改为 scoped namespace、`useReducer` 泛型变化，并删除 `VFC`、`ReactText` 等旧类型。可先运行官方 `types-react-codemod` |
| key 与 element introspection | 包含 `key` 的 props spread、直接读取 element props/ref 及依赖 React element 内部结构的库需要审查。不要依赖 `SECRET_INTERNALS`；React 19 已更名内部入口并明确阻止此类耦合 |
| 19.2 的新 API 与 lint | `Activity`、`useEffectEvent`、`cacheSignal` 不是依赖升级后必须自动采用的 API。`useEffectEvent` 需要新版 `eslint-plugin-react-hooks` 理解其依赖语义；官方 v6 flat config 与 legacy preset 也需单独迁移 |
| `useId` 生成格式变化 | 默认前缀从 19.0 的 `:r:`、19.1 的特殊字符变为 19.2 的 `_r_`。不要把生成 ID 固化进 CSS selector、快照、持久化数据或跨请求协议；SSR/client 必须使用一致树和 identifierPrefix |
| 19.2 SSR/API 变化 | Node 新增 Web Streams 与 resume/prerender API，但官方仍建议 Node 优先用性能更好的 Node Streams。压缩、背压、缓存、partial pre-rendering 与恢复协议需要框架级验证 |
| Server Components 安全与协议 | React 19 的 RSC/Server Actions 依赖框架和 `react-server-dom-*` 的内部协议，不能混用补丁版本。目标 19.2.7 修复 19.2.6 引入的 Server Action `FormData` 丢项回归；仍需按框架安全公告同步升级所有 RSC 包，而不是只升级 `react` |
| Create React App 进入维护模式 | 官方已建议新应用使用 framework 或 Vite/Parcel/RSBuild 等构建工具。老 CRA 应先确认其 React 19 支持、Jest/jsdom、Babel 与 dev overlay，再决定原地升级还是独立迁移构建链 |

官方依据：

- [React 18 upgrade guide](https://react.dev/blog/2022/03/08/react-18-upgrade-guide)：root、SSR、自动批处理、Strict Mode 与 TypeScript；
- [React 19 upgrade guide](https://react.dev/blog/2024/04/25/react-19-upgrade-guide)：18.3 过渡、删除 API、新 JSX transform、ref、错误处理与类型迁移；
- [React 19.2 release](https://react.dev/blog/2025/10/01/react-19-2)：Activity、Effect Event、SSR、hooks lint 与 `useId` 变化；
- [React v19.2.7 release](https://github.com/react/react/releases/tag/v19.2.7) 与 [固定目标 changelog](https://github.com/react/react/blob/v19.2.7/CHANGELOG.md)：目标补丁和 Server Actions 回归修复；
- [React versions](https://react.dev/versions) 与 [React Server Components 安全公告](https://github.com/facebook/react/security/advisories/GHSA-fv66-9v8q-g76r)：官方版本线与 RSC 必须联动升级的背景。

## 真实仓库测试来源

测试从以下公开仓库固定 commit 缩减，保留其真实相邻依赖和工程形态：

- [Hitachi semantic-segmentation-editor @ b159ccf4](https://github.com/Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor/blob/b159ccf46001420b6018e6d0faca3e64b0955cf9/package.json)：Meteor、Material UI 4、React/ReactDOM `^16.14.0` 与 Router 5；
- [github-profile-readme-maker @ 089aa348](https://github.com/VishwaGauravIn/github-profile-readme-maker/blob/089aa348c0deedef1f1363d00b8c7ca0e74774d0/package.json)：Next 12、MobX 与精确 React/ReactDOM `17.0.2`；
- [vitejs/vite @ 055d2b86](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/package.json)：官方 Vite 4 React 模板的 `^18.2.0`、旧 types 和 plugin 矩阵，并保留其 [createRoot 入口](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/src/main.jsx)；
- [infinitered/ignite @ e829d2f9](https://github.com/infinitered/ignite/blob/e829d2f922c5568a59a77bfb6232aeb500be3f13/package.json)：React Native 工具链中作为 dev dependency 的精确 `19.0.0`、React 19 types 与 pnpm 10。

52 个测试调用的结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，覆盖表格全部版本、常见安全 semver、四个直接依赖区、workspace、真实源码 no-op，以及目标/更新/未列版本、compound range、协议、alias、tag、override、lockfile、相似包、普通 JSON 与非字符串声明的严格 no-op。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.react.UpgradeReactTo19_2_7
```

审核依赖 diff 后，不要立即安装并提交 lockfile。先统一 React 与 renderer/framework/types 的目标版本，再使用工程原包管理器重新解析。至少运行 typecheck、lint、unit/component/E2E、production build、SSR/streaming/hydration、Strict Mode、Error Boundary/监控、浏览器矩阵和 bundle 分析；RSC/Server Actions 工程还必须覆盖认证边界、action payload、FormData、缓存/恢复以及框架官方安全测试。

本模块自身验证：

```bash
mvn -f rewrite-react-upgrade/pom.xml clean verify
```
