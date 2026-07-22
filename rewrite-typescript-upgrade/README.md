# TypeScript 升级到 6.0.3

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `typescript`。表格中可见、允许自动升级的来源版本只有：

```text
3.8.3, 3.9.5, 4.1.2, 4.2.3, 4.4.4,
4.5.5, 4.6.2, 4.6.4, 4.7.4
```

表格里的 `4.8.2 ...（共14个版本）` 是折叠展示，不包含另外 13 个可验证的版本号。本模块不会推断这些隐藏版本，也不会把任意 4.x/5.x 自动纳入升级。

推荐组合配方：

```text
com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptTo6_0_3
```

只修改依赖版本时使用：

```text
com.huawei.clouds.openrewrite.typescript.UpgradeTypeScriptDependencyTo6_0_3
```

## 处理契约

| 输入或不兼容点 | 配方行为 | 级别 | 测试证据 |
| --- | --- | --- | --- |
| 四个直接依赖区中的九个白名单版本，声明为 exact、`^exact`、`~exact` | 升级到 `6.0.3`，保留 exact/`^`/`~` 策略 | **AUTO** | `TypeScriptDependencyTest#upgradesEveryVisibleXlsxVersionAndPreservesOperator`，27 组参数；四依赖区与幂等测试 |
| `4.8.2 ...（共14个版本）`、未列版本、target/更高版本 | 低层配方不修改；推荐配方在非目标声明 value 上说明人工选版 | **NO-OP / MARK** | collapsed/hidden/target 参数化测试 |
| comparator、OR、hyphen、wildcard、prerelease、build metadata | 不猜 npm 约束；在原 value 标记 | **NO-OP / MARK** | complex/prerelease 参数化与 marker 测试 |
| workspace/catalog/npm alias、Git/HTTP/file/link、tag、变量 | 不改消费者字符串；标记真实 owner/发布策略 | **NO-OP / MARK** | protocol/dynamic 参数化测试 |
| `compilerOptions.lib` 同时含 `dom` 与 `dom.iterable`/`dom.asynciterable` | 删除已被 `dom` 包含的冗余 iterable lib | **AUTO** | `TypeScriptConfigAndLockTest#removesIterableLibrariesOnlyWhenDomAlreadyProvidesThem`；无 `dom`、nested、generated no-op |
| 非字符串名的旧 `module Foo {}` | 改成等价的 `namespace Foo {}`；`declare module "pkg"` 不动 | **AUTO** | `TypeScriptSourceTest#legacyInternalModules`、ambient no-op、真实 timers.ts 样例、幂等测试 |
| 静态 import assertion 的 `assert {}` | 改为 import attributes 的 `with {}` | **AUTO** | `TypeScriptSourceTest#replacesStaticImportAssertionWithImportAttributes` |
| `strict/module/target/noUncheckedSideEffectImports/libReplacement/types/rootDir` 新默认或推导变化 | 在 `compilerOptions` owner 标记缺失选择，要求显式决定 | **MARK** | changed-default owner 与 ts-node 真实 tsconfig 测试 |
| ES5、`downlevelIteration`、classic/node10 resolution、AMD/UMD/System、`baseUrl`、false interop/strict、`outFile` 等 | 在精确 option value 标记 | **MARK** | 16 组 config option marker 测试 |
| Node engine、CommonJS package mode、tsc 文件参数/旧 CLI option、compiler-integrated tools | 在精确 manifest value 标记 | **MARK** | 12 组 manifest marker 与 modern no-op 测试 |
| package-lock/npm-shrinkwrap、Yarn/pnpm lock 中的旧 TypeScript ownership | 不伪造 registry URL/integrity；标记后用仓库固定的包管理器重建 | **MARK** | JSON lock exact-node、Yarn/pnpm、target/generated no-op 测试 |
| `import ... from "typescript"`、`typescript/*` compiler 子路径、`require("typescript")`、no-default-lib、AMD directive | 在 import/call/compilation unit 标记 compiler API 与 directive 风险 | **MARK** | compiler import/require、triple-slash directive、相似字符串/注释 no-op 测试 |
| `node_modules/.pnpm/.yarn/.npm/.mvn/.m2/dist/build/generated/install/vendor` 等生成、缓存或安装树 | 所有配方跳过 | **NO-OP** | dependency/config/lock/source generated-path 测试 |

推荐配方按 `package.json` 依赖升级、tsconfig 确定性清理、源码语法迁移、manifest/config/lock/source 风险审计的顺序组合。`TypeScriptRecommendedRecipeTest` 在同一运行中验证 package.json、package-lock.json、tsconfig.json 和 TypeScript source 的 AUTO + MARK 结果及两轮幂等性。

## 依赖声明范围

依赖升级只扫描文件名精确为 `package.json` 的 JSON LST，并且只接受以下四个根直属区：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

例如：

```diff
 {
   "devDependencies": {
-    "typescript": "^4.7.4"
+    "typescript": "^6.0.3"
   }
 }
```

低层配方不会写 `package-lock.json`，不会修改 `overrides`、`resolutions`、catalog 或嵌套 dependency-like object，也不会将 `>=4.7.4 <5`、`4.7.4-beta.1`、`workspace:^4.7.4` 等强行改成另一个 npm 语义。推荐配方会把这些未解决的所有权留成 `SearchResult`。

## TypeScript 6 不兼容修改点

### 默认值与工程边界

TypeScript 6 改变或收紧了多项默认行为：`strict`、`module`、`target`、`noUncheckedSideEffectImports`、`libReplacement`、默认 `types`，以及 config 相对的 `rootDir` 推导。缺失配置并不代表所有项目都应写同一个值：浏览器/Node/bundler 运行时、全局类型来源、side-effect import、声明输出和 monorepo 布局都属于业务选择，因此配方精确标记而不填充猜测值。

目标包声明 Node `>=14.17`。项目还需同步检查本地、CI、容器、IDE language service 和包管理器实际使用的 Node，而不仅是 package.json 的 engine 字符串。

### 已废弃或移除的 compiler options

升级跨度可能从 3.8 直接跨到 6.0，应逐项处理：

- ES5 target 与 `downlevelIteration` 的旧降级输出不再是可持续路径；决定现代 target，或把更老运行时输出交给受支持的外部 transpiler。
- `moduleResolution: classic/node/node10`、`module: amd/umd/system/systemjs` 需要按实际 Node、bundler、package exports 和运行时边界选择 `bundler`、`node16` 或 `nodenext` 等方案。
- `baseUrl` 不能机械删除；先重写每个 `paths` target 和 import owner，并确认测试、IDE、bundler、Node 的解析一致。
- `esModuleInterop: false`、`allowSyntheticDefaultImports: false`、`alwaysStrict: false` 的旧行为不能通过简单删配置等价保留，必须检查 imports、`this` 和 emit/runtime。
- `outFile` 被移除，拼接、chunk 和输出命名应迁到 bundler。
- `ignoreDeprecations: "6.x"` 只会隐藏迁移工作；解决标记后应移除，避免把问题推迟到 TypeScript 7。
- `types: ["*"]` 会重新引入广泛 ambient discovery；应列出 Node、测试 runner、浏览器插件等实际全局类型包。

### 源码、CLI 与 Compiler API

内部模块关键字 `module Foo` 自动改为 `namespace Foo`；字符串名的 ambient external module declaration 保持不变。旧 import assertions 自动改为 import attributes。`/// <reference no-default-lib="true" />` 不再受支持，AMD triple-slash directives 则与已废弃模块格式绑定，二者都只标记，因为替代方案取决于全局 lib 和 loader。

TypeScript 6 在存在 tsconfig 时执行 `tsc file.ts` 的配置选择发生变化，必须明确使用预期 project，或有意识地采用 `--ignoreConfig`。配方会标记带 TS/TSX 文件参数以及已知旧 option 的 tsc script，不修改 shell 命令。

直接导入/require `typescript` 通常意味着使用 compiler、transform、printer、language-service、host 或 plugin API。跨多个主版本无法只凭 import 自动修复；配方在 AST 节点标记，要求用 6.0.3 重新验证 factory、transform、printer、diagnostic、host 与 editor integration。

### Lockfile 与工具链

锁文件包含 package-manager 生成的版本、resolved URL、integrity、peer graph、workspace importer 等数据。配方只在旧 TypeScript ownership 上标记，不生成假的 URL/hash。处理 manifest、tool peer 和 catalog owner 后，必须使用仓库锁定的 npm/pnpm/Yarn 版本重新安装并审查 lock diff。

`ts-node`、`ts-jest`、`tsx`、`vue-tsc`、Typedoc、typescript-eslint、Angular compiler、fork-ts-checker 等会加载 compiler API 或限制 peer range；本模块标记而不擅自替换工具版本。应选择各工具明确支持 TypeScript 6 的版本并运行其 CLI、测试和 IDE 集成。

## 固定官方依据

目标 tag `v6.0.3` 固定到 TypeScript commit [`050880ce59e30b356b686bd3144efe24f875ebc8`](https://github.com/microsoft/TypeScript/commit/050880ce59e30b356b686bd3144efe24f875ebc8)：

- [TypeScript 6.0 release notes](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-6-0.html)：默认值、配置、CLI、module/target/resolution、lib、源码语法及移除项；
- [Announcing TypeScript 6.0](https://devblogs.microsoft.com/typescript/announcing-typescript-6-0/)：官方迁移背景与兼容性说明；
- [固定 6.0.3 package.json](https://github.com/microsoft/TypeScript/blob/050880ce59e30b356b686bd3144efe24f875ebc8/package.json)：目标版本和 Node `>=14.17`。

## 固定真实仓库样例与 OpenRewrite 参考

测试中的真实用例不是用动态 main 分支：

- [fersilva16/package.json gist @ `dc551719`](https://gist.github.com/fersilva16/0ab10d59310b539520d3603b39b4229a/dc5517197a9888ffc28f0494ce5ded26651bdc8b)：真实 `typescript: 4.5.5` 与 tsup scripts，验证依赖升级不碰其他键；
- [TypeStrong/ts-node v10.9.2 @ `057ac1be` package.json](https://github.com/TypeStrong/ts-node/blob/057ac1beb118f9c42d21e876a17320ad73ea6be2/package.json) 和 [tsconfig.json](https://github.com/TypeStrong/ts-node/blob/057ac1beb118f9c42d21e876a17320ad73ea6be2/tsconfig.json)：真实 `typescript: 4.7.4`、复杂 peer、compiler tools、Node/CommonJS resolution 配置；
- [halilkocaerkek/timers.ts gist @ `0269600b`](https://gist.github.com/halilkocaerkek/fe7e56ae0d2866f57c179333fe4453a5/0269600bd8e054fd969e8a5cf017e3ee014fdecb)：真实 `module Utilities` internal module 语法。

测试结构与 LST 写法参考固定 OpenRewrite 提交：

- [`rewrite@d4ac42eb`](https://github.com/openrewrite/rewrite/commit/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc) 的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)；
- [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [ImportTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[MethodInvocationTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java) 和 [JavaScriptParserTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/JavaScriptParserTest.java)。

当前测试套件执行 138 个测试 invocation，覆盖白名单版本 × npm 策略、四依赖区、真实仓库样例、before→after、精确 marker、复杂/动态/协议 no-op、target 防降级、锁文件、生成目录、组合配方、发现/校验和两轮幂等性。

## 使用与验证

先 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-typescript-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptTo6_0_3
```

审查 AUTO diff 并解决所有 `SearchResult` 后，用项目固定的 package manager 重建 lockfile。至少运行 clean install、TypeScript build/typecheck、lint、unit/integration/E2E、production bundling、SSR、declaration emit、editor/language-service plugin 和运行时 smoke test。

模块自身验证：

```bash
mvn -f rewrite-typescript-upgrade/pom.xml clean verify
```
