# ngx-translate-module-loader 3.1.x → 5.1.0

本模块处理工作簿中 `@larscom/ngx-translate-module-loader` 的全部明确升级项。版本集合直接从 `开源软件升级.xlsx` 核对，不推断或扩大到其他版本：

| Excel 行 | 序号 | 源版本 | 目标版本 |
| ---: | ---: | ---: | ---: |
| 375 | 374 | `3.1.1` | `5.1.0` |
| 376 | 375 | `3.1.2` | `5.1.0` |

推荐业务迁移入口：

```text
com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.MigrateNgxTranslateModuleLoaderTo5_1_0
```

仅修改工作簿所选依赖、完全不执行 API 迁移或风险标记时，才使用低层入口：

```text
com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.UpgradeNgxTranslateModuleLoaderTo5_1_0
```

推荐配方按固定顺序执行：严格 dependency AUTO → 可证明的类型 import AUTO → manifest MARK → source MARK。

## Spec：不兼容点、执行能力和测试

| 不兼容点 | 配方行为 | 验收证据 |
| --- | --- | --- |
| 工作簿只选择 `3.1.1`、`3.1.2` | **AUTO**：四个 direct dependency section 中 exact、`^`、`~` 单版本改到 `5.1.0`，保留运算符 | 两个源值 × 四 section、所有 anchor、表格外版本/范围/协议/中央 owner 负例 |
| loader 5 删除自己声明的 `Translation` | **AUTO**：唯一、独占、所有权明确的 named import 改为 `@ngx-translate/core` 的 `TranslationObject`；保留 `import type`、引号和 alias | 根/官方旧 deep entry、alias、多个 type reference、冲突/遮蔽/混合 import 反例、两轮幂等 |
| loader 5 删除 `TranslationKey`，没有等价的 loader 导出 | **MARK**：精确标记 import specifier 和其本地引用，不猜业务 key model | alias/reference、同名无所有权负例、marker 幂等 |
| `Translation` 与仍保留的 loader API 混合在一个 import 中 | **MARK**：不拆分可能有注释、排序、type/runtime 语义的 declaration，标记待处理 symbol | `Translation + ModuleTranslateLoader` mixed fixture |
| target 新增 `provideModuleTranslateLoader`，官方示例使用 standalone providers | **MARK**：精确标记 `ModuleTranslateLoader` import 与 `new` 构造；不擅自选择 NgModule factory、standalone bootstrap、TestBed 或 SSR provider scope | 两个真实 Angular 仓的 factory/constructor 形态；import/new marker |
| peer 从 Angular common/core `>=6` 提高到 `>=16` | **MARK**：package.json 中可证明低于 16 或复杂不可证明的 Angular framework/CLI/compiler owner | core/common/CLI 等 direct owner、兼容值、复杂范围、无 loader 负例 |
| peer `@ngx-translate/core` 从 `>=10` 提高到 `>=16` | **MARK**：精确标记实际声明值 | 14.x、复杂范围、16.x 对照 |
| Angular 16 有 TypeScript/Node 工具链底线 | **MARK**：简单值低于 TypeScript 4.9.3、Node 16.14.0 时标记；复杂范围保守标记 | literal/caret/tilde 边界和 comparator 负载 |
| HTTP 返回处理从 typed JSON 变为 `responseType: 'text'` 后 `JSON.parse`/custom parser | **MARK**：constructor/import 的说明包含 parser、interceptor、error、charset、URL/cache/SSR 回归项 | loader construction marker 与真实 factory fixture |
| 新增 root/module `fileParser`，可改变扩展名与解码行为 | **MARK**：与 loader owner 一起要求业务选择，不自动推断 JSON5/XML/custom parser | 官方固定 source/README 差异 + marker 测试 |
| deep merge 从 Ramda `mergeDeepRight/reduce` 改为 core `mergeDeep` + array reduce | **MARK**：同一文件使用 loader 时精确标记 `ramda` module specifier；不改业务自己拥有的 Ramda | 有/无 loader 的 Ramda 对照 |
| loader 不再声明 `ramda` 运行依赖 | **MARK**：提示显式拥有 Ramda或迁到 core merge；不删除业务 direct dependency | source ownership tests |
| `lib/translation` 等 private/deep entry 不属于 target public surface | **AUTO/MARK**：唯一 `Translation` legacy entry 可确定迁移；其他 deep import、re-export 精确标记 | old published path、arbitrary deep/re-export/default/namespace/side-effect tests |
| override/resolution/pnpm override 是独立版本 owner | **MARK**：标记真实 selector value；不让 direct dependency AUTO 越权 | 三种中央 owner 的 exact value 与 marker 数量 |

SearchResult 落在实际 JSON value、module specifier、import specifier、引用 identifier 或 `new` expression 上，不给整个文件打无法定位的笼统标记。

## 自动类型迁移的所有权条件

`MigrateNgxTranslateModuleLoaderTypeImports` 只在以下条件全部满足时执行：

1. import module 恰好为 package root 或旧发布的 `/lib/translation`；
2. declaration 是唯一 named import，且唯一 symbol 是 `Translation`；
3. 整个 compilation unit 中只有一个候选 declaration；
4. alias 形式只改 exported property，保留本地名称；
5. unaliased 形式不存在 `TranslationObject` 冲突，也不存在 class/interface/enum/type/value/function 的 `Translation` shadow；
6. 不存在同名变量、field access、method invocation 或 object property，避免把值空间业务代码当作 type binding；
7. 文件位于业务路径而非生成、安装、构建或包管理器缓存目录。

满足条件的例子：

```ts
// before
import type { Translation } from '@larscom/ngx-translate-module-loader'
const dictionary: Translation = {}

// after
import type { TranslationObject } from '@ngx-translate/core'
const dictionary: TranslationObject = {}
```

alias 不变：

```ts
// before
import type { Translation as AppTranslation } from '@larscom/ngx-translate-module-loader'

// after
import type { TranslationObject as AppTranslation } from '@ngx-translate/core'
```

混合 declaration、`TranslationKey`、target binding 冲突或本地 shadow 都保持原代码，由后续审计配方标记。

## 依赖所有权边界

低层升级只处理每个业务 `package.json` 根对象的：

- `dependencies`；
- `devDependencies`；
- `peerDependencies`；
- `optionalDependencies`。

只接受 `3.1.1` / `3.1.2` 及各自 `^`、`~` 单版本。以下内容一律不自动修改：

- `3.1.0`、`3.1.3`、4.x、5.x 或其他未列版本；
- comparator、OR、hyphen、wildcard、tag、prerelease、build metadata；
- workspace、catalog、npm alias、file/link/portal、Git 和 URL declaration；
- root `overrides`、`resolutions`、`pnpm.overrides`；
- lockfile、普通 JSON、嵌套 custom dependency object 和非字符串值。

推荐配方会标记未解决的 direct declaration 和中央 owner，要求与 lockfile、workspace consumer 一起更新。

## Angular、provider 与运行时迁移

3.1.1 发布清单的 peer 为 Angular common/core `>=6.0.0`、`@ngx-translate/core >=10.0.0`、RxJS `>=6.0.0`，并直接依赖 Ramda。5.1.0 目标源码的库清单要求 Angular common/core 和 translate core `>=16.0.0`，不再声明 Ramda。

官方目标示例从 `TranslateModule.forRoot({ loader: { useFactory, deps }})` 和手工 `new ModuleTranslateLoader(http, options)` 转向：

```ts
provideTranslateService({
  loader: provideModuleTranslateLoader(options)
})
```

同时还需要 `provideHttpClient()`。配方不自动把旧 factory 改成上述形式，因为仅凭一个源文件无法知道工程是否：

- 仍使用 NgModule bootstrap；
- 已采用 standalone `bootstrapApplication`；
- 在 lazy route、TestBed 或 SSR 中提供 loader；
- 依赖 factory 的额外 DI token、environment 配置或测试替身；
- 需要保留 `ModuleTranslateLoader` 的公开构造器（目标仍导出该 class）。

SearchResult 会落在真实 import/constructor 上，业务可以据项目架构选择 provider API，或保留与 target 兼容的显式 factory。

## HTTP、parser、merge 与错误行为

旧实现使用 `http.get<Translation>(path, { headers })`。目标实现改为：

1. `http.get(path, { responseType: 'text', headers })`；
2. 默认 `JSON.parse(text)`；
3. root/module `fileParser` 可以替换 extension 与 `parseFn`；
4. `translateMap` 在 parse 后运行；
5. merge 从 Ramda `reduce(mergeDeepRight, ...)` 变为 translate core `mergeDeep`；
6. fetch/parse 错误继续进入 loader catchError 并返回空对象。

业务验收必须覆盖：

- HttpInterceptor 对 text response 与 JSON response 的不同分支；
- BOM、charset、空文件、非 JSON、损坏 JSON、404/500、网络超时；
- root parser 与 module parser 的覆盖顺序、扩展名、query version 和 CDN cache key；
- `translateMap` 接收 parse 后对象而不是原始文本；
- namespace 大小写、disabled namespace、custom namespace 和重复 key；
- object/array/null/prototype-sensitive value 在两个 deep merge 实现中的差异；
- SSR/TransferState、预渲染、并发 locale 切换和错误日志；
- 自定义 `translateMerger` 的输入顺序、mutation 与返回类型。

配方只标记真实 loader owner，不伪造能够替代运行时回归测试的文本改写。

## 路径保护

路径过滤只检查父目录 component，并统一转小写。以下父目录及常见缓存/输出被跳过：

`node_modules`、`.pnpm`、`.yarn`、`.npm`、`.gradle`、`.mvn`、`.m2`、`.angular`、`.next`、`.nuxt`、`.output`、`target`、`build`、`dist`、`out`、`coverage`、`report(s)`、`test-results`、`storybook-static`、`vendor`、`tmp`、`temp`，以及任意以 `generated` 或 `install` 开头的父目录。

叶文件名不参与排除，因此 `src/install.ts`、`src/generated.ts` 和普通 workspace 子包仍然处理。测试同时覆盖大小写父目录。

## 官方不可变证据

| 发布版本 | larscom/ngx-translate-module-loader 固定 commit |
| --- | --- |
| 3.1.1 | [`d0f87709d6ec1db5dc045718f863b0775fa32957`](https://github.com/larscom/ngx-translate-module-loader/tree/d0f87709d6ec1db5dc045718f863b0775fa32957) |
| 3.1.2 | [`96924b0b7b889b28ba67a15ee33a516828715bbf`](https://github.com/larscom/ngx-translate-module-loader/tree/96924b0b7b889b28ba67a15ee33a516828715bbf) |
| 4.0.0 | [`e1a02a15b8d058cdc35bd7212a7f38a46cfede6a`](https://github.com/larscom/ngx-translate-module-loader/tree/e1a02a15b8d058cdc35bd7212a7f38a46cfede6a) |
| 5.0.0 | [`c8e4981bca871a89254d6d27f7cef25591aa023d`](https://github.com/larscom/ngx-translate-module-loader/tree/c8e4981bca871a89254d6d27f7cef25591aa023d) |
| 5.1.0 | [`1d8ec9c393a52a193ce1977696d5d1ebd301f8f2`](https://github.com/larscom/ngx-translate-module-loader/tree/1d8ec9c393a52a193ce1977696d5d1ebd301f8f2) |

固定源码证据：

- [3.1.1 public API](https://github.com/larscom/ngx-translate-module-loader/blob/d0f87709d6ec1db5dc045718f863b0775fa32957/projects/ngx-translate-module-loader/src/public-api.ts) 导出 `Translation` 与 `TranslationKey`；
- [3.1.1 translation types](https://github.com/larscom/ngx-translate-module-loader/blob/d0f87709d6ec1db5dc045718f863b0775fa32957/projects/ngx-translate-module-loader/src/lib/translation.ts) 是两个被删除的声明；
- [5.1.0 public API](https://github.com/larscom/ngx-translate-module-loader/blob/1d8ec9c393a52a193ce1977696d5d1ebd301f8f2/projects/ngx-translate-module-loader/src/public-api.ts) 不再导出二者并新增 provider；
- [5.1.0 provider](https://github.com/larscom/ngx-translate-module-loader/blob/1d8ec9c393a52a193ce1977696d5d1ebd301f8f2/projects/ngx-translate-module-loader/src/lib/module-translate-provider.ts) 定义 token、loader class 与 DI deps；
- [5.1.0 loader](https://github.com/larscom/ngx-translate-module-loader/blob/1d8ec9c393a52a193ce1977696d5d1ebd301f8f2/projects/ngx-translate-module-loader/src/lib/module-translate-loader.ts) 是 text fetch、file parser、core merge 和错误行为的最终依据；
- [5.1.0 README](https://github.com/larscom/ngx-translate-module-loader/blob/1d8ec9c393a52a193ce1977696d5d1ebd301f8f2/README.md) 固定 provider 与 parser 的官方用法。

## 真实公开仓固定用例

测试不只使用人工字符串，还保留真实公开工程在固定 commit 的最小形态：

- [KonsumGandalf/rsdp package.json @ `cd46193`](https://github.com/KonsumGandalf/rsdp/blob/cd46193bab67a9790ac8837869f872ae2e918c69/package.json) 使用 `^3.1.1`；其 [translation.module.ts](https://github.com/KonsumGandalf/rsdp/blob/cd46193bab67a9790ac8837869f872ae2e918c69/apps/frontend/src/app/translation/translation.module.ts) 使用 `IModuleTranslationOptions`、`ModuleTranslateLoader` 和显式 constructor；
- [khalifa005/khalifa-angular-template @ `6ad63fa`](https://github.com/khalifa005/khalifa-angular-template/blob/6ad63fa55fd6d50ae5cef5b8f1d63bcbd9af3be9/package.json) 使用 `^3.1.1`，用于验证真实 manifest anchor；
- [thomas-chu-30/angular-translate package.json @ `68f5bab`](https://github.com/thomas-chu-30/angular-translate/blob/68f5bab9009cbc5a2d346ca5c7ee2e95e3672a9f/package.json) 使用 `^3.1.2`；其 [app.module.ts](https://github.com/thomas-chu-30/angular-translate/blob/68f5bab9009cbc5a2d346ca5c7ee2e95e3672a9f/src/app/app.module.ts) 保留 factory/constructor 形态。

fixture 固定到 commit，避免默认分支后续变化让测试依据漂移。

## OpenRewrite 固定测试参考

测试结构、before/after、NOOP、marker、recipe discovery 和 cycle 断言参考：

- OpenRewrite `8.87.5` tag commit [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，尤其 [JSON ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)；
- rewrite-javascript 固定 commit [`9e3b820e6a44808b095bb7e3aab670fd67de99a5`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5)，尤其 [ImportTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[JavaScriptParserTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/JavaScriptParserTest.java) 和 [RecipeTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/recipe/RecipeTest.java)。

## spec → recipe → test 映射

| Spec | AUTO / MARK | 测试覆盖 |
| --- | --- | --- |
| 工作簿版本白名单 | dependency AUTO | 两版本、四 section、anchor、表格外/复杂/协议声明、workspace、lockfile、中央/嵌套 owner、22 个路径排除、幂等 |
| `Translation` → `TranslationObject` | type import AUTO | type/runtime、root/deep、alias、引用、collision/shadow/value/property、mixed/multiple candidate、leaf path、真实 shape、幂等 |
| `TranslationKey` 删除 | source MARK | specifier、alias reference、unowned same name、marker 幂等 |
| provider/constructor 决策 | source MARK | import + exact `new`、真实 factory、低层 recipe source NOOP |
| HTTP/parser/merge/Ramda | source MARK + README runtime spec | loader owner、Ramda same-file ownership、有/无 package 对照 |
| Angular/core/TS/Node floor | manifest MARK | 多个 owner、simple/complex/compatible、无 package、workspace/path边界 |
| central owner | manifest MARK | npm override、Yarn resolution、pnpm override 的实际 value |
| public recipe composition | AUTO before MARK | dependency + peer markers、type AUTO 无残留 marker、loader remaining marker、recipe validation |

独立验证：

```bash
mvn -f rewrite-ngx-translate-module-loader-upgrade/pom.xml clean verify
```

当前共 **100 个测试执行用例**：71 个严格 dependency/recipe 入口用例、14 个确定性类型迁移用例、15 个 manifest/source/组合与精确标记用例。

业务工程建议先 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-module-loader-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.MigrateNgxTranslateModuleLoaderTo5_1_0
```

逐项审查 patch 与 SearchResult，完成 Angular/translate core 对齐、provider scope、HTTP/parser/merge、SSR 和真实 locale 文件回归后再执行 `run`。
