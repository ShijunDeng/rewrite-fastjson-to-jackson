# markdown-it 升级到 14.3.0

本模块处理 npm 包 `markdown-it`。它只采用 `开源软件升级.xlsx` 中明确出现的版本映射，不根据 semver 邻近版本或“跨大版本”备注扩大匹配范围。

| XLSX 行 | 序号 | 原始版本 | 目标版本 |
| --- | --- | --- | --- |
| 362 | 361 | `11.0.0` | `14.3.0` |
| 363 | 362 | `12.2.0` | `14.3.0` |
| 364 | 363 | `12.3.2` | `14.3.0` |
| 1422 | 1421 | `13.0.1` | `14.3.0` |
| 1423 | 1422 | `13.0.2` | `14.3.0` |
| 3151 | 3150 | `14.0.0` | `14.3.0` |
| 3152 | 3151 | `14.1.0` | `14.3.0` |

推荐入口：

```text
com.huawei.clouds.openrewrite.markdownit.MigrateMarkdownItTo14_3_0
```

只升级依赖声明的低层入口：

```text
com.huawei.clouds.openrewrite.markdownit.UpgradeMarkdownItTo14_3_0
```

## spec → recipe → test

| 规格 | 配方/实现 | 主要测试 |
| --- | --- | --- |
| 七个 XLSX 源版本精确升级 | `UpgradeSelectedMarkdownItDependency` | `upgradesEveryExactWorkbookSource`、caret/tilde 参数测试 |
| 只改直接 npm 声明 | `UpgradeSelectedMarkdownItDependency` | 四个 dependency section、workspace manifest；range/protocol/alias/catalog/override/lockfile NOOP |
| 公共入口 `markdown-it/index[.js]` 归一 | `MigrateDeterministicMarkdownItSource` | ESM import/re-export/dynamic import、direct CJS require、字符串/相似包反例 |
| 已存在的 deep ESM 文件改为 `.mjs` | `MigrateDeterministicMarkdownItSource` | `token`/`renderer`/`common/utils`、包括旧 `.mjs` 在内的 `rules_inline/text_collapse` 文件改名、未知文件和 deep CJS NOOP、Factly 固定 SHA fixture |
| owned `text_collapse` → `fragments_join` | `MigrateDeterministicMarkdownItSource` | default/named-default/CJS constructor、factory/new、before/after/at 与 bulk enable/disable；type-only、reassign、shadow、unowned/lookalike NOOP |
| manifest 兼容决策 | `FindMarkdownItManifestRisks` | unlisted/range、override/resolution、types/plugins 精确 MARK |
| deep module、parser/renderer/plugin 风险 | `FindMarkdownItSourceRisks` | ESM/CJS deep literal、ruler、renderer、delimiter jump、Discourse 固定 SHA fixture |
| 生成物隔离 | `MarkdownItSupport.isProjectPath` | 仅过滤父目录；`node_modules` 大小写、`generated*`/`install*` 父目录与 `src/install.js` 叶文件反例 |
| recipe discovery/validation 与幂等 | declarative `rewrite.yml` | strict/recommended validation，AUTO 与 manifest/source MARK 两轮测试 |

## AUTO 与 MARK 边界

| 不兼容点 | 行为 | 所有权与原则 |
| --- | --- | --- |
| 直接 `markdown-it` 依赖 | **AUTO** | 仅 `package.json` 根级四种 dependency section；仅精确、`^`、`~` 单版本，保留运算符意图 |
| `markdown-it/index[.js]` | **AUTO** | 静态 import/re-export、dynamic `import()` 或参数唯一的 direct `require` 改为公共根入口 `markdown-it` |
| static/dynamic ESM `markdown-it/lib/...` | **AUTO + MARK** | 只为目标 tarball 中已核对存在且被目标 `exports` 通配规则放行的文件补 `.mjs`；随后 MARK 内部 API 风险 |
| `text_collapse` rule reference | **AUTO** | 仅由 runtime default/named-default import 或 direct require 构造器创建、未 shadow/重赋值的实例，且调用链精确为 `instance.inline.ruler2.*`；支持精确 anchor 与 enable/disable 字符串列表 |
| unlisted/range/alias/catalog/override | **MARK** | 不猜测 package-manager 解析意图，要求对齐真实依赖图 |
| `@types/markdown-it`、`markdown-it-*` | **MARK** | 类型和插件必须验证 v14 ESM/内部 token/ruler 兼容性；`markdown-it-emoji` 还有官方注明的签名例外 |
| deep CommonJS `require` | **MARK** | v14 `lib` 是 ESM `.mjs`，不能把路径改名等同于可被 CJS 同步加载 |
| custom ruler、`delimiters[].jump` | **MARK** | rule 顺序、token/delimiter 状态与异常策略需要插件作者决定 |
| renderer overrides | **MARK** | image alt、hardbreak、CommonMark 与 Unicode 输出需用业务快照验证 |

SearchResult 代表人工决策点。推荐配方不会将 MARK 当成自动修复，也不会修改 lockfile、插件版本、构建 target 或 Markdown 内容。

## 严格 manifest 所有权

以下声明会升级，并保留 `^`/`~`：

```json
{
  "dependencies": { "markdown-it": "11.0.0" },
  "devDependencies": { "markdown-it": "^12.3.2" },
  "peerDependencies": { "markdown-it": "~13.0.2" },
  "optionalDependencies": { "markdown-it": "14.1.0" }
}
```

结果分别为 `14.3.0`、`^14.3.0`、`~14.3.0`、`14.3.0`。以下内容不自动升级：

- `>=12 <15`、`11 || 13`、hyphen、wildcard 等复合范围；
- `workspace:`、`npm:` alias、git/GitHub、URL、`file:`、`link:`、tag；
- 根级 `overrides`/`resolutions`、根级 `pnpm.overrides`、catalog 和任意嵌套同名键；package-manager override selector 只 MARK、不 AUTO；
- `package-lock.json`、`npm-shrinkwrap.json`、yarn/pnpm lockfile；
- `node_modules`、vendor/build/dist/out/coverage、generated 前缀、框架缓存与 IDE 目录。

运行后必须用项目自己的 package manager 重建锁文件，并审查实际图：

```bash
npm install
npm ls markdown-it
# 或项目锁定的 pnpm/yarn 命令
```

## 11/12 → 13 的不兼容点

官方固定版本 [CHANGELOG](https://github.com/markdown-it/markdown-it/blob/ff0ee084fc6b0d10fac049fa562bc2925b5cc723/CHANGELOG.md) 记录了这些边界：

- 12.0 为 `highlight(code, lang, attrs)` 增加第三个参数，并按新 GFM 规则重写 table；已有两参数函数仍可调用，但使用 fence attrs 或比较 HTML 时要补用例；
- 12.3 移除了 `StateInline.delimiters[].jump`，依赖内部 delimiter 状态的插件不能机械替换；
- 13.0 新增 `text_special` token 和 core `text_join`，并把 inline `text_collapse` 改名为 `fragments_join`；
- typographer 不再把 `(p)` 变成 `§`，被转义的 smartquote/replacement/plain-text link 行为改变；
- 13.0.2 开始在第三方 block/inline rule 不推进 `line`/`pos` 时直接抛错，而不是继续到潜在死循环。

配方只对可证明属于 markdown-it 实例的 `text_collapse` rule anchor 做一对一 AUTO。自定义 rule、token 合并、delimiter 算法和输出快照全部 MARK。

## 13 → 14.3 的模块与输出边界

目标提交 [`ff0ee084fc6b0d10fac049fa562bc2925b5cc723`](https://github.com/markdown-it/markdown-it/tree/ff0ee084fc6b0d10fac049fa562bc2925b5cc723) 的 [`package.json`](https://github.com/markdown-it/markdown-it/blob/ff0ee084fc6b0d10fac049fa562bc2925b5cc723/package.json) 明确给出：

- ESM 根入口 `index.mjs`；
- 根入口仍有 CJS fallback `dist/index.cjs.js`，所以 `require('markdown-it')` 本身不是错误；
- `exports` 同时声明根入口条件映射与 `./*` import/require 通配映射；deep 路径不是被 export map 全面封禁，而是内部 API 与真实文件名风险；
- `lib/*` 源文件全面改为 `.mjs`，deep CommonJS consumer 必须迁到 ESM 或公共 API；
- v14 不再把 `dist/` 提交到 Git 仓，而是在发布 npm 包时构建。目标 npm tarball 仍包含 `dist/index.cjs.js`、`dist/markdown-it.js` 和 minified bundle，不能把“仓库删除 dist”误判为“发布包没有 dist”；
- v14 放弃 ancient browser 支持，并更新 `entities`、`linkify-it`、`mdurl`、`uc.micro`、punycode 等依赖；构建 target、legacy browser 与 polyfill 要按业务环境验证；
- image alt 内的 HTML token/hardbreak 渲染改变，CommonMark 0.31.2、Unicode delimiter、entity、HTML comment 与 hard-line-break 行为又在 14.1–14.3 继续修正；必须对不可信输入、病理输入和 HTML 输出做快照/安全测试。

推荐验证：

```bash
npm test
npm run build
# 项目自己的 lint/typecheck/e2e
```

至少覆盖 `html/linkify/typographer/breaks/xhtmlOut/quotes/highlight` 组合、所有 `.use()` 插件、custom ruler/renderer、tables、image alt、CJK/astral Unicode、escaped links/replacements、超长 references/quotes 和恶意链接输入。

## 真实仓固定 SHA 夹具

manifest 测试从公开仓的真实声明形态缩减而来：

- [igembitsgoa/igem-wiki-starter `29cbb8ee2170ad7aca7fa001eaf2947d1542a89d`](https://github.com/igembitsgoa/igem-wiki-starter/blob/29cbb8ee2170ad7aca7fa001eaf2947d1542a89d/src/package.json.jinja)：`^11.0.0`；
- [rubickCenter/rubick `d2f3f347af9a1104fd92d19c78b849c710dc275f`](https://github.com/rubickCenter/rubick/blob/d2f3f347af9a1104fd92d19c78b849c710dc275f/feature/package.json)：`^12.2.0`；
- [animate-css/animate.css `3f8ab233dbbd9d2fe577528d2296382954be3d1a`](https://github.com/animate-css/animate.css/blob/3f8ab233dbbd9d2fe577528d2296382954be3d1a/package.json)：`^12.3.2`；
- [edemaine/coauthor `7323c84d65812c696472316d4dda29cb6a146bf1`](https://github.com/edemaine/coauthor/blob/7323c84d65812c696472316d4dda29cb6a146bf1/package.json)：精确 `13.0.1`；
- [easysoft/zui `b46fbf22e1343c89fc3c1bf7473142842479c67d`](https://github.com/easysoft/zui/blob/b46fbf22e1343c89fc3c1bf7473142842479c67d/package.json)：`^13.0.2`；
- [observablehq/framework `85e843e6acfa3dbe93129c125156352c9c50b697`](https://github.com/observablehq/framework/blob/85e843e6acfa3dbe93129c125156352c9c50b697/package.json)：`^14.0.0`；
- [coroot/coroot `0aaf083f9e9a60cb07274a4c4c5a008e31c0c495`](https://github.com/coroot/coroot/blob/0aaf083f9e9a60cb07274a4c4c5a008e31c0c495/front/package.json)：`^14.1.0`。

源码夹具：

- [factly/scooter `e9c8a10d3076a521da8b7e8f9d007f7cc9ae5919`](https://github.com/factly/scooter/blob/e9c8a10d3076a521da8b7e8f9d007f7cc9ae5919/libs/scooter-table/src/lib/tableRules.js)：真实 root + `markdown-it/lib/token` static import，验证只补目标存在的 `.mjs` 并 MARK internal API；
- [educative/discourse `ea5ff29aaea7f04067bb1fa904e7317688335cb2`](https://github.com/educative/discourse/blob/ea5ff29aaea7f04067bb1fa904e7317688335cb2/app/assets/javascripts/pretty-text/engines/discourse-markdown/bbcode-inline.js.es6)：真实 `md.inline.ruler2.before("text_collapse", ...)`，因构造器所有权不在文件内而不 AUTO，只在精确 literal MARK；
- 测试结构参考 OpenRewrite 官方 [`UpgradeDependencyVersionTest` 固定提交 `decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，同时增加 npm ownership、JavaScript AST、marker 与两轮幂等反例。

官方 tag 对应提交也固定记录如下：`11.0.0`=`1093e68e51c9b3104289c3cfbaffbb1fa4039d59`、`12.2.0`=`6e2de08a0b03d3d0dcc524b89710ce05f83a0283`、`12.3.2`=`d72c68b520cedacae7878caa92bf7fe32e3e0e6f`、`13.0.1`=`e843acc9edad115cbf8cf85e676443f01658be08`、`13.0.2`=`e476f78bc3ea3576beb61bdc94322d0a6b2d85cc`、`14.0.0`=`4949a10120d101bdca48bf1b13f9e790d6f2049e`、`14.1.0`=`0fe7ccb4b7f30236fb05f623be6924961d296d3d`、`14.3.0`=`ff0ee084fc6b0d10fac049fa562bc2925b5cc723`。

## 使用与模块验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-markdown-it-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.markdownit.MigrateMarkdownItTo14_3_0
```

模块独立验证：

```bash
mvn -f rewrite-markdown-it-upgrade/pom.xml clean verify
```

当前共 115 个测试：67 个版本/manifest 所有权测试、20 个确定性源码 AUTO 测试、28 个 MARK/组合配方/幂等验证测试。
