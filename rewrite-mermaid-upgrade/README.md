# Mermaid 9 → 11.15.0 OpenRewrite 配方

本模块不是“只改版本号”的占位模块。它把工作表中的 Mermaid 迁移范围固化为严格白名单，自动处理可以证明等价的 API 变更，并把无法脱离业务上下文安全决定的 ESM、异步调用、DOM、安全、渲染、主题、配置、bundler 和 SSR 问题标在准确语法节点或文本片段上。

## 工作表范围（spec）

`开源软件升级.xlsx` 唯一工作表中，坐标严格等于 `mermaid` 的全部四行如下：

| 工作表行 | 源版本 | 目标版本 | 自动接受的声明 |
|---:|---:|---:|---|
| 418 | 9.1.1 | 11.15.0 | `9.1.1`、`^9.1.1`、`~9.1.1` |
| 419 | 9.1.3 | 11.15.0 | `9.1.3`、`^9.1.3`、`~9.1.3` |
| 420 | 9.1.6 | 11.15.0 | `9.1.6`、`^9.1.6`、`~9.1.6` |
| 421 | 9.4.3 | 11.15.0 | `9.4.3`、`^9.4.3`、`~9.4.3` |

`@bytemd/plugin-mermaid` 是工作表中的另一个 npm 坐标，不属于本模块；本模块不会因名称包含 `mermaid` 而误改它。目标版本和源版本依据固定的 Mermaid 官方提交核对：

- `9.1.1`: [`5a9ec308`](https://github.com/mermaid-js/mermaid/tree/5a9ec30803398464688f7e460fc66a008814d7a2)
- `9.1.3`: [`1509ee68`](https://github.com/mermaid-js/mermaid/tree/1509ee68bede6c6dc12bcc196d54b89f3b97ac4a)
- `9.1.6`: [`a89b6fd0`](https://github.com/mermaid-js/mermaid/tree/a89b6fd0549a91fb09e72c4e40decf5a0b866828)（官方 tag 的 `package.json` 仍写 9.1.5，因此以 tag/commit 身份为准并保留该异常说明）
- `9.4.3`: [`1a7b8d38`](https://github.com/mermaid-js/mermaid/tree/1a7b8d38970f367e273b61599e6c4f75a8635158)
- `11.15.0`: [`41646dfd`](https://github.com/mermaid-js/mermaid/tree/41646dfd43ac83f001b03c70605feb036afae46d)

## 配方

公开严格升级配方：

```text
com.huawei.clouds.openrewrite.mermaid.UpgradeMermaidTo11_15_0
```

推荐迁移配方：

```text
com.huawei.clouds.openrewrite.mermaid.MigrateMermaidTo11_15_0
```

推荐配方通过 YAML 直接复用公开升级配方，而不是复制一份依赖升级逻辑。执行顺序是：严格依赖升级 → 确定性 API 迁移 → manifest 风险 → JavaScript/TypeScript 风险 → markup/diagram/style 风险。

### 严格依赖升级

只访问项目源目录内名为 `package.json` 的文件，只处理根对象下四个直接区段：`dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。`^`/`~` 运算符会保留，例如 `^9.1.3 → ^11.15.0`。

下列内容不会自动改：复杂范围、预发布/构建元数据、动态 tag、`workspace:`/`npm:`/`git:`/`file:`/URL 协议、catalog/变量、`overrides`、`resolutions`、嵌套伪依赖对象、非字符串值、lockfile、普通 JSON、相似包名以及 central owner。被跳过的直接 Mermaid 声明会由推荐配方精确标记。

以下父目录（大小写不敏感）完全排除：`node_modules`、`vendor`、`dist`、`build`、`out`、`target`、`.next`、`.nuxt`、`.svelte-kit`、`.angular`、`.cache`、`.yarn`、`.pnpm`、`.npm`、`coverage`、`reports`、`test-results`、`storybook-static`，以及以 `generated` 或 `install` 开头的父目录。规则只判断父目录，不会因为普通叶文件名中出现 `install` 就扩大删除/修改范围。

### 确定性 AUTO

Mermaid v9 同时提供同步名和 `*Async` 名，v10 删除旧异步别名并令公开 API 本身异步。只有参数签名可证明等价且接收者来自 `import mermaid from 'mermaid'` 时才自动改：

```ts
await mermaid.parseAsync(text);          // → await mermaid.parse(text)
await mermaid.renderAsync(id, text);     // → await mermaid.render(id, text)
await mermaid.mermaidAPI.parseAsync(text);       // → ...parse(text)
await mermaid.mermaidAPI.renderAsync(id, text);  // → ...render(id, text)
```

带 error callback 的 `parseAsync`、带 callback/container 的 `renderAsync`、deep import、namespace interop、`require`、动态接收者和无 Mermaid 所有权证据的同名 API 都不猜测。自动改名后仍会标记 Promise 返回值和已废弃 `mermaidAPI`，提示调用方完成真正的业务迁移。

## 不兼容点：spec → recipe → test

官方依据固定在 11.15.0 commit 的 [`CHANGELOG`](https://github.com/mermaid-js/mermaid/blob/41646dfd43ac83f001b03c70605feb036afae46d/packages/mermaid/CHANGELOG.md)、[`mermaid.ts`](https://github.com/mermaid-js/mermaid/blob/41646dfd43ac83f001b03c70605feb036afae46d/packages/mermaid/src/mermaid.ts)、[`mermaidAPI.ts`](https://github.com/mermaid-js/mermaid/blob/41646dfd43ac83f001b03c70605feb036afae46d/packages/mermaid/src/mermaidAPI.ts)、[configuration](https://github.com/mermaid-js/mermaid/blob/41646dfd43ac83f001b03c70605feb036afae46d/packages/mermaid/src/docs/config/configuration.md)、[usage](https://github.com/mermaid-js/mermaid/blob/41646dfd43ac83f001b03c70605feb036afae46d/packages/mermaid/src/docs/config/usage.md) 和 [security](https://github.com/mermaid-js/mermaid/blob/41646dfd43ac83f001b03c70605feb036afae46d/packages/mermaid/src/docs/community/security.md)。

| 不兼容边界 | 官方事实/风险 | 本模块行为 | 主要测试 |
|---|---|---|---|
| ESM-only | Mermaid 10 起移除 CommonJS build | `require`、deep import、`type: commonjs`、动态 import 精确 MARK；不擅自改 module mode | `MermaidJavaScriptRiskTest`、`MermaidManifestRiskTest` |
| `render` | 变为 Promise，返回 `{svg, bindFunctions}`，callback 被移除，第三参改为 container | 两参数 `renderAsync` AUTO；所有公开 render 调用 MARK callback/DOM/错误/快照工作 | `MermaidSourceMigrationTest`、`MermaidJavaScriptRiskTest` |
| `parse` | 变为 Promise，第二参改为 `ParseOptions`，不再是 ParseError callback | 单参数 `parseAsync` AUTO；parse 调用 MARK await/catch 和错误语义 | 同上 |
| `init*` | `init` deprecated；`initThrowsErrors*` 不再是 v11 公共契约 | 精确 MARK，要求拆成 `initialize` + `run` 并选择 `suppressErrors` | `MermaidJavaScriptRiskTest` |
| 初始化/运行 | `initialize` 是 site-wide 配置；`run` 异步并标记 DOM 已处理 | MARK 单次初始化、DOM 生命周期、HMR、SSR、hydration、callback 与错误策略 | `MermaidJavaScriptRiskTest` |
| `mermaidAPI` | v11 类型中标为 deprecated/internal | 精确 MARK，避免把内部配置/渲染契约当稳定 API | 推荐集成测试 |
| sanitization/security | `strict`/`loose`/`antiscript`/`sandbox` 影响 DOMPurify、HTML、链接、click 和 iframe | `securityLevel`、`htmlLabels`、click callback/link、全局错误钩子 MARK | JavaScript/markup 风险测试 |
| 指令和配置优先级 | init directive deprecated，配置来自 default/site/frontmatter/directive | `%%{init...}%%` MARK；不自动搬运可能改变安全/共享文档语义的配置 | `MermaidMarkupRiskTest` |
| flowchart 曲线 | 11.13 默认 `basis → rounded`；显式 `basis` 可恢复 | owned `curve` 配置 MARK，要求真实 SVG/视觉快照 | `MermaidJavaScriptRiskTest` |
| HTML labels | `flowchart.htmlLabels` deprecated，root `htmlLabels` 及 escaping/sandbox 行为变化 | owned 配置 MARK，不在未知合并/优先级下移动属性 | JavaScript/markup 风险测试 |
| class namespace | 11.15 默认启用分层 namespace；可用 `class.hierarchicalNamespaces=false` 恢复旧行为 | 配置和 diagram `namespace` 精确 MARK | `MermaidMarkupRiskTest` |
| SVG 内部 ID | 11.15 为避免多图冲突给内部 ID 加 diagram 前缀 | exact arrowhead CSS/DOM selector MARK，提示改为韧性 selector 并多图测试 | `MermaidMarkupRiskTest` |
| theme/renderers | theme variables/CSS、font、尺寸、错误渲染、ID seed、renderer 和图表布局跨大版本变化 | owned config 精确 MARK，要求 SVG/视觉/可访问性快照 | `MermaidJavaScriptRiskTest` |
| CDN/copied assets | classic v9 script 不能安全地只替换版本；full/core、SRI、CSP、缓存均需选择 | v9/classic CDN、vendor/install layout 精确 MARK，不编辑生成物 | markup 风险和推荐集成测试 |

MARK 是 OpenRewrite `SearchResult`，在 diff 中显示为 `/*~~(message)~~>*/` 或 `~~(message)~~>`；它是可定位、可审查的迁移输出，不是仅写在 README 里的提示。

## 真实仓库与固定用例

测试夹具从下列不可变 revision 精简而来，保留了与配方判断相关的原始字段/调用；没有依赖会变化的默认分支：

- DevUI Vue [`b9c4bfd4`](https://github.com/DevCloudFE/vue-devui/blob/b9c4bfd4cb5543e26d14821a67ab4b14a9d52a39/packages/devui-vue/package.json)：`"mermaid": "9.1.1"`。
- Apache DevLake website [`70e46f5d`](https://github.com/apache/incubator-devlake-website/blob/70e46f5d050e521b45b8b30a5324977c821b5bc1/package.json)：`"mermaid": "^9.1.3"`。
- Cherry Markdown [`c2b9e9ea`](https://github.com/Tencent/cherry-markdown/blob/c2b9e9ea874c319015fae1829ec3896de772464e/packages/cherry-markdown/package.json)：`"mermaid": "9.4.3"`。
- Mermaid v9.4.3 官方实现 [`mermaid.ts`](https://github.com/mermaid-js/mermaid/blob/1a7b8d38970f367e273b61599e6c4f75a8635158/packages/mermaid/src/mermaid.ts) 与 [`mermaidAPI.ts`](https://github.com/mermaid-js/mermaid/blob/1a7b8d38970f367e273b61599e6c4f75a8635158/packages/mermaid/src/mermaidAPI.ts)：`parseAsync`/`renderAsync`、callbacks、`init*` 的真实旧契约。

测试结构参考固定版本的 OpenRewrite [`rewrite-test`](https://github.com/openrewrite/rewrite/tree/0a0b6d2c42d710995d74846aa7c461de2c44f521/rewrite-test) 和 [`rewrite-javascript`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 用例风格：公开 YAML 配方发现、before→after、no-op、marker 内容、真实路径和多 cycle 幂等性均由 RecipeTest 验证。

## 测试覆盖与运行

当前测试包含全部 12 个白名单 exact/caret/tilde 组合、四个直接依赖区段、复杂 semver、协议/alias、central owner、lockfile/普通 JSON、相似包、生成/安装/缓存路径、AUTO before→after/no-op、API/config/markup marker、真实仓库夹具、公开配方发现和幂等性。独立验证：

```bash
mvn -f rewrite-mermaid-upgrade/pom.xml clean verify
```

本模块不会改 lockfile；配方完成且所有 MARK 都被人工处置后，应使用项目原有 package manager 重新解析依赖，并运行 Node/browser/SSR 测试、真实 Mermaid corpus、SVG snapshot、视觉回归、安全用例（含恶意 label/link/click）、多图 ID/CSS、可访问性和 CSP/SRI 部署检查。
