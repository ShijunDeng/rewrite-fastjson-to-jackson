# React Router DOM 4.3.1 升级到 6.30.4

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `react-router-dom`，将直接声明的 `4.3.1`（包括 `^4.3.1`、`~4.3.1` 和以 `4.3.1` 为边界的常见 range）升级到 `6.30.4`。

配方名称：

```text
com.huawei.clouds.openrewrite.reactrouterdom.UpgradeReactRouterDomTo6_30_4
```

## 自动处理范围

配方仅修改根目录或 workspace 子目录 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies`，把可确定来自 `4.3.1` 的 registry semver 声明设置为精确版本 `6.30.4`。

它不会修改其他 4.x/5.x、目标或更高版本，不会覆盖 `workspace:`、npm alias、Git、file/link、URL/tarball、tag、变量等非 registry 声明；也不会修改 `overrides`、`resolutions`、lockfile、shrinkwrap 或其他 JSON。配方有意保留 `react-router`、`history`、`connected-react-router`、`react-router-config` 和旧 `@types`，因为是否删除或升级这些包依赖实际源码和运行架构，自动联动可能破坏应用。

本模块只安全地改变依赖字符串，不改写 JavaScript/TypeScript/JSX/TSX。4.3.1→6.30.4 跨越路由匹配、渲染、导航和 SSR/data router 架构，简单的符号替换不能证明行为等价；应把生成的依赖 patch 当成迁移清单入口，而不是可直接上线的完整源码迁移。

## 不兼容修改点

| 变化 | 影响与迁移建议 |
| --- | --- |
| React Router v6 大量使用 Hooks | React 与 ReactDOM 必须至少为 `16.8`。官方建议先独立升级 React 并发布验证，再迁移 Router；class component 需要包装函数组件或重构后才能使用 Router hooks |
| 大型 v4/v5 应用不宜一次切换 | 官方建议先到 v5.1，消除旧 render API、floating Route 和 `withRouter`，或使用 `react-router-dom-v5-compat` 让 v5/v6 路由逐段共存；完成后删除 compat 包 |
| `<Switch>` 改为 `<Routes>` | `<Routes>` 按最佳匹配选择而不是按声明顺序 first-match；因此 404、静态/动态路径优先级可能改变。其直接子节点必须是 `<Route>` 或 fragment，自定义 `PrivateRoute` 要改成 route element/wrapper |
| `<Route exact>` 被移除 | v6 默认进行完整 segment 匹配；需要在另一个组件中继续匹配 descendant routes 的父 route 使用尾部 `/*`。不要机械删除 `exact` 而不验证 index 与嵌套路由 |
| `component`/`render`/旧 `children` rendering API 改为 `element` | `component={Page}`→`element={<Page />}`，`render={props => ...}`→普通 element，并在组件内用 hooks 取 params/location；自有 props 直接传入元素。v6 的 `children` 用于嵌套 route definitions，不再等价于旧 render prop |
| `<Redirect>` 改为 `<Navigate>` 或服务端 redirect | `<Navigate to="..." replace />` 用于 render-time 客户端导航；`<Routes>` 内 redirect 通常写成 `<Route path="old" element={<Navigate ... />} />`。首屏重定向优先在服务端完成，data router 的 loader/action 使用 `redirect()` |
| `useHistory`/`history` 改为 `useNavigate` | `history.push(to)`→`navigate(to)`，`replace`→`navigate(to,{replace:true})`，`go(n)`→`navigate(n)`；不能继续依赖外部 `history` object 的 listen/block/length 细节。命令式导航还需回归 state、relative、replace 和 transition 行为 |
| `withRouter` 被移除 | 函数组件改用 `useLocation`、`useNavigate`、`useParams`、`useMatch`；class/第三方组件需要自建受控 HOC 把 hook 结果作为 props 注入。盲目删除 HOC 会丢失更新触发和 route props |
| `useRouteMatch` 改为 `useMatch` | `useMatch` 必须给出 pattern；旧 `match.path`/`match.url` 拼接通常改为相对 path/link，参数读取用 `useParams`，URL resolution 可用 `useResolvedPath` |
| route/link 默认采用相对层级语义 | 子 route path 与 `<Link to>` 会相对父 route；嵌套 UI 应由父 route 的 `<Outlet>` 渲染。检查旧代码中 `match.path`/`match.url` 字符串拼接、开头 `/`、`..`、index route、pathless layout 和 splat，否则可能得到不同 URL/层级 |
| route path 语法与 `path-to-regexp` 不同 | v6 不接受 regexp path、自定义 regexp group 或 path 数组，应把这些变体拆成多条 route；6.30.4 支持 `?` optional segment，但其排名/嵌套语义仍需回归。旧 strict/sensitive 配置要按 v6 route API 重新表达 |
| `<NavLink>` API 变化 | `exact`→`end`；`activeClassName`/`activeStyle` 被删除，改用 `className`/`style` callback 的 `isActive`/`isPending`。回归尾斜杠、根路径、大小写和 pending 状态 |
| `<Prompt>` 被移除 | v6.30.4 可用 `useBlocker` 实现应用内导航确认，浏览器 unload 另配 `useBeforeUnload`；`unstable_usePrompt` 依赖 `window.confirm` 且多次前进/后退时跨浏览器行为不可靠。必须明确 blocker 的 proceed/reset 状态机 |
| 导航与 location state 约定变化 | v6 `<Link state>`/`navigate(...,{state})` 取代把 state 放进 `to` object 的部分旧写法；`<Navigate>` 默认 push 而旧 `<Redirect>` 常见语义不同，需明确 `replace`。验证 search/hash、编码、basename 和 back/forward |
| `react-router-config`/静态 route config 迁移 | 使用 `useRoutes`、`createRoutesFromElements` 或 data router route objects；`renderRoutes`、`matchRoutes` 的参数/返回假设不可直接沿用。route object 的 `Component` 与 JSX `<Route component>` 不是同一 API |
| `connected-react-router` 与自建 history 集成 | v6 不鼓励应用直接维护外部 history；应评估移除 `connected-react-router`、Redux router reducer/middleware 和直接 `history.push`，改用 Router context/hooks。若暂时保留，先验证其明确支持的 Router major，不能只提升 DOM 包 |
| v6.4+ 引入 data routers | `createBrowserRouter`/`RouterProvider` 才提供 loader、action、fetcher、errorElement、defer 等数据 API；不能假定把现有 `<BrowserRouter>` 换名就获得这些能力。Router 应在 React tree 外创建，嵌套路由使用 `Outlet`，并设计 revalidation/error boundary |
| SSR 入口和流程变化 | declarative SSR 使用 `react-router-dom/server` 的 `StaticRouter`；data router SSR 使用 `createStaticHandler`、`createStaticRouter`、`StaticRouterProvider`，同时传递 hydration data。重定向/状态码/headers 必须在发 HTML 前处理，客户端 route tree 与 hydration data 必须一致 |
| lazy/Suspense 与 data-router lazy 含义不同 | `React.lazy` 仍负责 component bundle；route `lazy` 返回 route module 字段并在匹配后执行。重新检查 chunk error、loader 并发、fallback/hydration fallback 和错误边界，不要混用两类 contract |
| v7 future flags 会产生告警 | 6.28+ 会提示尚未选择的 v7 行为。逐项验证并显式启用/禁用 `v7_relativeSplatPath`、`v7_startTransition` 等适用 flags，尤其回归 splat 下相对链接与 fetcher persistence，为后续 v7 降低风险 |
| 6.30.3/6.30.4 包含 redirect/path 修复 | 6.30.3 校验 redirect location 以修复 open-redirect XSS；6.30.4 把 decodePath 移出匹配热路径并规范化 redirect 的双斜杠。若应用曾依赖外部协议、协议相对或 `//` redirect，必须做安全与行为回归 |
| TypeScript 类型随包发布 | v6 包含自身 `.d.ts`，旧 `@types/react-router-dom` 代表 v4/v5 API，通常应在源码迁移完成后删除；升级 TS 后检查 JSX element、route object、loader/action data 和 module resolution 错误，不要用 `skipLibCheck` 掩盖双版本类型 |
| Node、bundler 与公开入口 | 6.30.4 manifest 要求 Node `>=14`，声明 `main`、`module`、types，并提供 `server.js`/`server.mjs`。升级构建/SSR/Jest 工具后验证 CJS/ESM resolution、tree shaking 和 browser/server 分包；只用 `react-router-dom` 与 `react-router-dom/server` 等公开入口，不依赖 `dist` 深路径 |

这些变化必须以固定目标标签下的官方 [v5→v6 upgrade guide](https://github.com/remix-run/react-router/blob/react-router%406.30.4/docs/upgrading/v5.md)、[6.30.4 changelog](https://github.com/remix-run/react-router/blob/react-router%406.30.4/CHANGELOG.md#v6304)、[6.30.4 package manifest](https://github.com/remix-run/react-router/blob/react-router%406.30.4/packages/react-router-dom/package.json)、[SSR guide](https://github.com/remix-run/react-router/blob/react-router%406.30.4/docs/guides/ssr.md) 与 [useBlocker API](https://github.com/remix-run/react-router/blob/react-router%406.30.4/docs/hooks/use-blocker.md) 为准。

## 真实代码样本

- [s-yadav/class-to-function-with-react-hooks@f8853e0c](https://github.com/s-yadav/class-to-function-with-react-hooks/blob/f8853e0cf38a919c7688da73576f1c995ebf7f25/package.json) 使用精确 `4.3.1`、React 16.8 和 CRA 2；其 [routes.js](https://github.com/s-yadav/class-to-function-with-react-hooks/blob/f8853e0cf38a919c7688da73576f1c995ebf7f25/src/routes.js) 使用 `{path, component}` route config，迁移时要转换为 element/route objects。
- [ohansemmanuel/nav-state-react-router@e38f9f03](https://github.com/ohansemmanuel/nav-state-react-router/blob/e38f9f039d1119c3a2f8a82ddecc16c23883bac0/package.json) 使用 `^4.3.1`、React 16.4、Redux 和 CRA 1；其 [App.js](https://github.com/ohansemmanuel/nav-state-react-router/blob/e38f9f039d1119c3a2f8a82ddecc16c23883bac0/src/containers/App.js) 含典型 `<Switch>`、`exact`、`component` API，并且必须先把 React 升到 16.8+。
- [supasate/connected-react-router@d822fb9a basic example](https://github.com/supasate/connected-react-router/blob/d822fb9afd12e9d32e8353a04c1c6b4b5ba95f72/examples/basic/package.json) 同时声明 `connected-react-router`、外部 `history`、`react-router` 和 `react-router-dom ^4.3.1`；其 [routes](https://github.com/supasate/connected-react-router/blob/d822fb9afd12e9d32e8353a04c1c6b4b5ba95f72/examples/basic/src/routes/index.js) 使用 `Switch/component`，而 [App](https://github.com/supasate/connected-react-router/blob/d822fb9afd12e9d32e8353a04c1c6b4b5ba95f72/examples/basic/src/App.js) 将外部 history 传给 `ConnectedRouter`，证明只改依赖无法安全完成源码迁移。
- JSONPath 与断言结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。

25 个测试覆盖 3 个固定提交真实样本、精确/caret/tilde/range/`v` 前缀/prerelease/build metadata、四个依赖区、根包与多级 workspace，以及目标/新版本、相邻 4.x、v5、协议/alias/tag/变量、lockfile/shrinkwrap、其他 JSON、相似包名、override/resolution、嵌套配置和非字符串声明的严格 no-op。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-router-dom-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.reactrouterdom.UpgradeReactRouterDomTo6_30_4
```

大型项目优先按官方路径分阶段：React 升至 16.8+；Router 到 v5.1 并消除旧 API；必要时引入 v5 compat；最后切到 v6.30.4 并删除 compat/旧类型/不再使用的 history 集成。确认 dependency patch 后重建 lockfile，运行 TypeScript、lint、unit/component/E2E、production build 与 SSR/hydration 测试，并覆盖深链接、404、redirect、relative/splat、basename、导航阻断、浏览器前进后退、data loaders/actions/errors 和安全 redirect。

本模块自身验证：

```bash
mvn -f rewrite-react-router-dom-upgrade/pom.xml clean verify
```
