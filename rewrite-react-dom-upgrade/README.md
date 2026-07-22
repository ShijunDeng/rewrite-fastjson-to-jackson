# React DOM 升级到 19.0.0

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `react-dom`，精确处理 `16.6.1`、`16.14.0`、`17.0.2`、`18.2.0` 到 `19.0.0` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.reactdom.UpgradeReactDomTo19_0_0
```

## 自动处理范围

配方扫描根目录和 workspace/monorepo 子目录中的 `package.json`，只修改以下四个直接依赖区中名称精确为 `react-dom` 的声明：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

表格中的精确版本，以及明确锚定这些版本的 caret、tilde、equal、`v`/`^v` registry 声明，会统一为精确版本 `19.0.0`，例如 `^16.14.0`、`~17.0.2`、`v18.2.0`、`=16.6.1`。配方在目标叶节点上使用等值白名单，既不会笼统匹配全部 16/17/18，也不会对 JSON `null` 或对象执行正则。未列版本、comparator、hyphen/OR range、prerelease、build metadata 及 `17.x`、`>=16`、`*` 等宽泛范围保持不变。

配方有意不修改：

- `react`：目标 `react-dom@19.0.0` 的官方 peer 是 `react@^19.0.0`，但 React 自身升级涉及独立配方和业务判断，不能在本模块中暗改；
- `@types/react`、`@types/react-dom`、`react-test-renderer`、React Testing Library、Enzyme、框架 adapter 和其他 renderer；
- workspace 根配置；workspace 子包的真实 `package.json` 会分别处理，但 `workspace:`、`catalog:` 等协议引用保持不变；
- npm alias、`file:`、`link:`、Git/GitHub、HTTP tarball、tag 和 `${...}` 变量；
- `overrides`、`resolutions`、`pnpm.overrides`、`peerDependenciesMeta`；
- package-lock、pnpm-lock、Yarn lockfile、缓存元数据和普通 JSON；
- JavaScript/TypeScript/JSX/TSX 源码、SSR 入口、测试代码、HTML script tag、bundler 与 TypeScript 配置。

执行后必须将 `react` 与 `react-dom` 配成同一 19.0.x 版本线，并使用项目原包管理器重建 lockfile。对于发布库，配方把命中的 `peerDependencies.react-dom` 设置为精确目标版本以保证结果可重复；维护者应在兼容性验证后决定是否放宽为经过测试的 peer range。

## 不兼容修改点

### 16/17 升级到 18 的迁移基础

| 修改点 | 影响与人工迁移建议 |
| --- | --- |
| Client root API | `ReactDOM.render(element, container)` 改为从 `react-dom/client` 导入 `createRoot`，保存 root 后调用 `root.render(element)`；旧 API 在 React 18 只以 React 17 模式运行并告警，到 React 19 已移除。不要仅机械替换调用名，root 的创建、持有与重复 render 生命周期需要统一设计。 |
| Hydration | `ReactDOM.hydrate(element, container)` 改为 `hydrateRoot(container, element)`；参数顺序不同，而且 `hydrateRoot` 已包含首次 render，不应紧接着再调用 `root.render`。SSR 与 client 必须产生一致 HTML，`identifierPrefix` 在服务端/客户端以及多 root 场景必须一致。 |
| Unmount 与 render callback | `unmountComponentAtNode(container)` 改为创建该树的 `root.unmount()`；unmount 后同一 root 不能再次 render。旧 `render` 第三个 callback 没有一对一替代，需要按“DOM commit 后执行”的真实意图改为 ref callback、effect 或业务调度。 |
| Concurrent root 与自动 batching | 使用 `createRoot` 后，Promise、timer、原生事件等来源的更新也会自动 batch；依赖两次中间 render、同步读取 DOM 的代码和测试可能改变。只有确有同步 DOM 要求时才局部使用 `flushSync`。 |
| StrictMode | React 18 开发模式会模拟 unmount/remount 并重复 effect setup/cleanup，React 19 还会在初始 mount 双调用 ref callback。修复不幂等 effect、缺失 cleanup、重复订阅和不可重入第三方组件，不要把永久移除 StrictMode 当成迁移完成。 |
| Hydration 与 Suspense | React 18 把文本等 hydration mismatch 当成错误，并回退到最近 Suspense boundary/client render；React 19 提供更集中且带 diff 的诊断，也改善第三方 script/extension 共存。消除 `Date.now()`、`Math.random()`、locale、客户端条件分支和无快照外部数据造成的不一致。 |
| Server rendering | Node streaming 从已废弃的 `renderToNodeStream` 迁移到 `renderToPipeableStream`；Web Stream/edge 使用 `renderToReadableStream`。`renderToString`、`renderToStaticMarkup` 仍可用但对 Suspense 有限制，不能把 streaming 替换当成纯函数重命名。 |
| 测试环境 | 使用 concurrent root 的测试要由 testing library 正确包裹 `act`；自建环境需按官方说明设置 `globalThis.IS_REACT_ACT_ENVIRONMENT`。升级 React Testing Library/Jest/Vitest/jsdom 与 fake timer，并重审同步断言。 |
| 运行环境 | React 18 放弃 Internet Explorer，并依赖 Promise、Symbol、Object.assign、microtask 等现代能力。旧浏览器、WebView、嵌入式运行时要升级或维持旧 React。 |
| React 17 中间行为 | 从 16 跨越 17 时还会遇到事件委托由 document 移到 root、event pooling 移除、`onScroll` 不再模拟冒泡、effect cleanup 时机变化等；微前端、多 React root、原生 listener 与读取旧 SyntheticEvent 的代码应专项回归。 |

### 18 升级到 19 的移除与变化

| 修改点 | 影响与人工迁移建议 |
| --- | --- |
| `render` / `hydrate` 最终移除 | React 19 不再提供 `ReactDOM.render` 和 `ReactDOM.hydrate`。统一改为 `react-dom/client` 的 `createRoot`/`hydrateRoot`；官方 `react/19/replace-reactdom-render` codemod 可辅助，但仍需审查 root ownership、callback、hydrate 参数和错误处理。 |
| `unmountComponentAtNode` 移除 | 必须持有对应 root 并调用 `root.unmount()`；微前端、jQuery/Angular/Vue 宿主、modal/widget 和测试 teardown 尤其需要明确 root registry 与销毁顺序。 |
| `findDOMNode` 移除 | 改用显式 DOM ref/转发 ref；该 API 只取第一个 child、破坏封装且不适应 concurrent rendering。第三方动画、focus、popover、transition 库若仍调用它，也必须升级或替换。`createPortal` 本身仍是公开 API，不应误删。 |
| `react-dom/test-utils` | `act` 从 `react-dom/test-utils` 移到 `react`；其他低层 test-utils 在 React 19 调用时会报错，并计划移除 export。优先迁移到 React Testing Library 的用户行为测试；不要只改 import 后继续依赖 `Simulate`、renderIntoDocument 等内部细节。 |
| Render error reporting | render 中未被 Error Boundary 捕获的错误不再重新抛出，而是交给 `window.reportError`；已捕获错误报到 `console.error`。依赖 try/catch、全局 error handler 或重复日志的监控要调整，可在 `createRoot`/`hydrateRoot` 配置 `onUncaughtError`、`onCaughtError`、`onRecoverableError`；`onRecoverableError` 的 `errorInfo.digest` 已移除。 |
| Removed unstable APIs | `unstable_flushControlled`、`unstable_createEventHandle`、`unstable_renderSubtreeIntoContainer`、`unstable_runWithPriority` 等移除；访问 `SECRET_INTERNALS...` 的库也可能被阻断。升级相关框架/组件库，不要复制或映射 React 内部字段。 |
| StrictMode / Suspense | React 19 StrictMode 初始 mount 会双调用 ref callback；Suspense 会先提交最近 fallback，再预热 suspended siblings。依赖旧 render 次序、请求次数、动画或测试快照的代码要回归。 |
| DOM 与安全语义 | `src`/`href` 的 `javascript:` URL 现在报错，空 `src`/`href` 的处理和 custom element 属性/属性赋值语义也变化；表单 Action、document metadata、资源预加载 API 会改变框架集成边界。 |
| UMD 与 JSX transform | React 19 不再发布 UMD build，直接 `<script src=".../umd/react-dom...">` 的页面要切到模块化 CDN/构建产物；新 JSX transform 是必需项，应检查 Babel/SWC/TypeScript、老 webpack loader 和发布库编译结果。 |
| React 配套 | 固定目标 tag 的 `react-dom@19.0.0` 声明 `react: ^19.0.0` peer。React、React DOM、scheduler、renderer、框架、devtools 和测试库版本不一致会产生 invalid hook call、重复 React 副本或隐藏的 runtime 错误；必须按 workspace 检查依赖树。 |

### Server、stream 与 static API

- 普通 Node SSR 优先 `react-dom/server` 的 `renderToPipeableStream`；Web Stream/edge runtime 使用 `renderToReadableStream`，不要在 Node 中因为“也能运行”而忽略性能和 adapter 语义。
- `renderToString`/`renderToStaticMarkup` 是非 streaming legacy 入口，对 Suspense/等待数据能力有限。
- React 19 新增 `react-dom/static`：Web Stream 使用 `prerender`，Node 使用 `prerenderToNodeStream`，它们面向等待所有数据后生成静态 HTML/SSG；不是 streaming SSR 的等价替换。
- 目标包通过条件 exports 区分 `client`、多种 `server.*`、`static.*`、profiling 和 `react-server` 条件。Bundler、Node、Bun、Deno、worker/edge 选错 condition 会加载错误实现；禁止深度导入 `cjs/**` 或复制构建文件。
- CSP nonce、bootstrap scripts/modules、abort、backpressure、错误 shell、Suspense boundary、hydrateRoot 的 `identifierPrefix` 和 server/client release 一致性都需要集成测试。

### TypeScript 19 类型

- 同步升级 `@types/react@19` 和 `@types/react-dom@19`，并先清除旧的重复 types；React 18 types 已要求 props 显式声明 `children`。
- React 19 的 ref callback 可以返回 cleanup，因此 `ref={node => instance = node}` 这种隐式返回不再通过类型检查，改为带 `{}` 的无返回 callback；`useRef` 现在要求初始参数，refs 统一为可变 `RefObject`。
- 未指定 props 泛型的 `ReactElement["props"]` 从 `any` 变为 `unknown`；全局 `JSX` namespace 迁到与 JSX runtime 对应的 `React.JSX`/模块扩展；`useReducer` 泛型写法和大量已删除 API types 也会报错。
- 官方建议运行 `types-react-codemod preset-19`，但 codemod patch 必须通过类型、运行时和声明产物测试；本配置配方不会执行任何源码 codemod。

官方依据：

- React 官方 [React 18 Upgrade Guide](https://react.dev/blog/2022/03/08/react-18-upgrade-guide)：client root、hydrate、unmount、automatic batching、StrictMode、SSR streaming、测试环境和 TypeScript 18 变化；
- React 官方 [React 19 Upgrade Guide](https://react.dev/blog/2024/04/25/react-19-upgrade-guide)：移除的 React DOM/test APIs、error reporting、StrictMode、UMD/JSX transform 和 TypeScript 19 变化；
- React 官方 [React 19 stable release](https://react.dev/blog/2024/12/05/react-19)：hydration diagnostics、Suspense、custom elements 与新增 React DOM static API；
- React 官方 [Client APIs](https://react.dev/reference/react-dom/client)、[Server APIs](https://react.dev/reference/react-dom/server) 和 [Static prerender](https://react.dev/reference/react-dom/static/prerender)：各 runtime 的入口、root 生命周期与 SSR/SSG 边界；
- 固定目标 tag 的 [react-dom@19.0.0 package.json](https://github.com/facebook/react/blob/v19.0.0/packages/react-dom/package.json)：`react@^19.0.0` peer 与 client/server/static 条件 exports。

## 真实仓库测试来源

四组用例分别固定到一个公开仓库 commit，并覆盖表格中的四个源版本：

- [feedzai/brushable-histogram @ a46666e5](https://github.com/feedzai/brushable-histogram/blob/a46666e5de8cd24cfb490d14e26ea046b3945e60/package.json)：组件库在 `devDependencies` 固定 `react-dom: 16.6.1`，同时保留无法安全判定的 `peerDependencies: 16.x`；其 [Portal.js](https://github.com/feedzai/brushable-histogram/blob/a46666e5de8cd24cfb490d14e26ea046b3945e60/src/common/components/Portal.js) 使用仍受支持的 `createPortal`；
- [Faithlife/styled-ui @ 4b771c63](https://github.com/Faithlife/styled-ui/blob/4b771c63d1ed3381dfc40c29a87416402364d0bd/package.json)：开发依赖 `react-dom: ^16.14.0`，而宽泛 peer `^16.8.0` 保持 no-op；其 [catalog/index.js](https://github.com/Faithlife/styled-ui/blob/4b771c63d1ed3381dfc40c29a87416402364d0bd/catalog/index.js) 仍使用 React 19 已删除的 `ReactDOM.render`，测试明确保证配置配方不会假装自动迁移源码；
- [grafana/grafana v8.5.0 @ 6134e3cf](https://github.com/grafana/grafana/blob/6134e3cf35a99c7dd3041b7ececb47cb7619ba9a/package.json)：大型 monorepo 在根 manifest 固定 `react`/`react-dom: 17.0.2`，并使用独立 `@types/react-dom: 17.0.14`；测试只升级目标 renderer，暴露必须人工对齐 React/types 的边界；
- [vitejs/vite v4.5.0 @ 055d2b86](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/package.json)：React 模板声明 `react-dom: ^18.2.0`；其 [main.jsx](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/src/main.jsx) 已使用 `react-dom/client.createRoot` 和 StrictMode，升级依赖时保持源码不变。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。当前 36 个测试覆盖表格全部版本、安全 semver、四个直接依赖区、workspace 子 manifest、React/类型/生态包保留、真实 JS/JSX 源码，以及目标/高版本/未列版本/宽范围/协议/alias/Git/URL/tag/变量/override/lockfile/普通 JSON/相似包和已移除 client/test/server API 的严格 no-op。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-dom-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.reactdom.UpgradeReactDomTo19_0_0
```

确认 patch 后，用项目原包管理器重建 lockfile，然后：

1. 将 `react`、`react-dom`、`@types/react`、`@types/react-dom` 对齐到 19.0.x，并检查每个 workspace 的 peer tree 和重复 React；
2. 官方建议先临时升级到 18.3 发现 React 19 deprecation warning，再运行并审查 `react/19/migration-recipe` 与 `types-react-codemod preset-19`；
3. 静态搜索 `ReactDOM.render`、`hydrate`、`unmountComponentAtNode`、`findDOMNode`、`react-dom/test-utils`、`renderToNodeStream`、`unstable_`、UMD 路径和 React internals；
4. 运行 typecheck、lint、unit/component/E2E、SSR/SSG/stream、hydration、StrictMode、Suspense、Error Boundary、monitoring、micro-frontend root teardown、bundler/edge runtime 和浏览器矩阵测试。

本模块自身验证：

```bash
mvn -f rewrite-react-dom-upgrade/pom.xml clean verify
```
