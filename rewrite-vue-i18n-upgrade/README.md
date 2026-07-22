# Vue I18n 7/8 升级到 11.3.0

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `vue-i18n`，目标版本为 `11.3.0`。严格升级只接受表格中可见且能够还原为单个 semver 的源版本：

```text
7.3.2, 8.11.2, 8.20.0, 8.22.1, 8.22.4,
8.24.3, 8.24.4, 8.25.0, 8.26.7, 8.27.1
```

表格的 `8.27.1 ...（共17个版本）` 没有披露其余 16 个具体版本，因此不会被解释成“任意 8.x”，也不会借此升级 9/10/11.0–11.2。配方为：

- `com.huawei.clouds.openrewrite.vuei18n.UpgradeVueI18nTo11_3_0`：严格依赖升级；
- `com.huawei.clouds.openrewrite.vuei18n.MigrateVueI18nTo11_3_0`：推荐配方，追加确定性源码/模板迁移以及精确 manifest、JS/TS、SFC、JSON/YAML locale message 风险标记。

## 处理契约

| 输入或风险 | 严格配方 | 推荐配方 | 级别 |
| --- | --- | --- | --- |
| 任意 workspace `package.json` 顶层四个直接依赖区中的 `vue-i18n`，值为表格版本的 exact、`^exact`、`~exact` | 设置为精确 `11.3.0` | 同左 | **AUTO** |
| comparator、OR、hyphen、wildcard、多约束 | 不修改 | 在原 value 标记人工选择约束与 lockfile 重建 | **NO-OP / MARK** |
| workspace protocol、npm alias、Git/GitHub、file/link、URL、tag、变量、空白、`v`/`=`、prerelease/build | 不修改 | 在原 value 标记所有权或发布策略 | **NO-OP / MARK** |
| 未列出的 6/7/8/9/10/11 版本 | 不修改 | 非目标声明标记人工选版；目标 exact/caret/tilde 保持 | **NO-OP / MARK** |
| overrides/resolutions/catalog、lockfile、普通 JSON、相似包名 | 不修改 | 不修改 | **NO-OP** |
| `new VueI18n`/`createI18n` 的直接 options 中 `dateTimeFormats` | 不处理 | 无同名新键时改为 `datetimeFormats`；新旧键冲突时保留并标记 | **AUTO / MARK** |
| 已确认 owner 的 `tc/$tc(key, <numeric literal>)`，且恰好两个参数 | 不处理 | 改为 `t/$t`，参数保持 | **AUTO** |
| `<i18n path>` 完整 component，无 `places`/`place` | 不处理 | 改为 `<i18n-t keypath>`；自闭合和成对标签均支持 | **AUTO** |
| custom `<i18n>` locale block、带 `places`/`place` 或结构不完整的 component | 不处理 | 保留 custom block；对不安全 component 精确标记 | **NO-OP / MARK** |
| Vue 2/Vue 2 compiler、SSR、test、composition/build 包，bridge/legacy loader，Node <16 | 不处理 | 在精确 manifest value 标记 | **MARK** |
| default class import、`Vue.use`、`new VueI18n`、Legacy mode、移除 options/static API | 不处理 | 在精确 import/call/property 标记 | **MARK** |
| 剩余 `tc/$tc`、旧 `t/$t` locale overload、结构化返回读取、类数组 list interpolation | 不处理 | 在精确调用节点标记 | **MARK** |
| `v-t`/`.preserve`、SFC 内 Vue 2 bootstrap、剩余旧 translation component | 不处理 | 在精确 template/script 片段标记 | **MARK** |
| locale 路径 JSON/YAML 中 `%{...}`、`@:(...)`、未转义 email `@` | 不处理 | 在精确 message scalar 标记；其他路径不误报 | **MARK** |
| `@intlify/unplugin-vue-i18n`、`__INTLIFY_JIT_COMPILATION__` | 不处理 | 标记 JIT/CSP/runtime-only/build 回归边界 | **MARK** |

直接依赖区仅为根 object 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。配方不会改 lockfile，不会自动选择 Vue 3 bootstrap、Legacy/Composition scope、SSR instance ownership、locale/default-message overload 或 message escaping 业务语义。

## 不兼容修改点

| 跨越变化 | AUTO | MARK 后需要完成的决策 |
| --- | --- | --- |
| v7/v8 面向 Vue 2；11.3.0 peer 是 Vue `^3.0.0`，Node engine `>=16` | 无 | 先迁 Vue runtime/compiler/test-utils/router/store/SSR/UI 库和所有运行环境；Bridge 只能作为 v9 中转 |
| `Vue.use(VueI18n)`/`new VueI18n`→`createI18n`/`app.use(i18n)` | 仅迁 option 内确定性字段 | 标记 default import/install/construction；按 app/test/SSR request 边界重建实例和插件注入 |
| `dateTimeFormats`→`datetimeFormats` | context-proven direct option 且不存在新键时自动改名 | 新旧键同时存在时保留并标记；同名业务对象或嵌套 message key 不修改、不误报 |
| `$t`/`t` 自 v9 只返回 string | 无 | 对索引/field 读取标记；迁为 `$tm`/`tm` 后用 `$rt`/`rt` 解析叶子 |
| `getChoiceIndex`、custom formatter、`preserveDirectiveContent` 移除 | 无 | 标记 override/call/options；迁 `pluralizationRules`/`pluralRules`、message format 和 directive 行为 |
| list interpolation 不再接受类数组 object | 无 | 精确标记数字 key object；改为数组并验证 plural/options overload |
| message compiler 严格处理 `{`、`}`、`@`、`$`、`|`，linked bracket grouping 删除 | 无 | 精确标记 `%{`、`@:(...)`、email `@`；用 literal interpolation 并编译全部语言资源，其余特殊字符通过 compiler 回归发现 |
| `<i18n>`→`<i18n-t>`，`path`→`keypath`，`places`/`place`→slots | 无 place syntax 的完整 component 自动迁移 | 带 place syntax 保留并标记；custom `<i18n>` locale block 永不改名 |
| v10 默认启用 JIT | 无 | 标记 unplugin/旧 define flag；重建 production/SSR，验证 CSP、runtime-only/full build、动态 message 和 precompile |
| v10 删除 Legacy `$t(key, locale, ...)` overload | 无 | string 第二参数精确标记但不猜测它是 locale 还是 default message；迁到 `{ locale }` options |
| `tc/$tc` 在 v11 删除 | 仅 exact 两参数且第二参数为 numeric literal 时改名 | 其余调用标记；补齐 plural count，并区分 locale、list、named、default message |
| v10 移除 `%` syntax、bridge、`allowComposition` 和多项 v8 compatibility | 无 | 标记 bridge import/owner 和移除 option；先完成 Vue 3 与 Composition scope 迁移 |
| v11 弃用 Legacy API mode与 `v-t` | 无 | 标记未明确 `legacy:false` 的 `createI18n` 及 `v-t`；迁 `useI18n`、scope/globalInjection 与模板调用，为 v12 做准备 |
| Legacy/Composition scope 与 SSR/lazy loading 生命周期不同 | 无 | 回归 `useScope`、fallback/missing/warn、局部 block 卸载、每请求隔离、hydration、route chunk 竞态 |
| 11.3.0 message escaping/path/modifier/directive/date/number 修复 | 无 | 不依赖旧 bug；覆盖 escape、点号 key、missing modifier、locale `!`、`n`/`d` undefined 输入 |

## 测试矩阵

| 维度 | 覆盖 |
| --- | --- |
| XLSX | 10 个可见源版本 × exact/caret/tilde；四个直接依赖区；workspace manifests；target、防降级和 no-invention |
| npm spec | complex range、protocol、alias、Git、file/link、URL、tag、variable、decorated、prerelease/build 严格 no-op，推荐配方 marker |
| JSON scope | nested owners、catalog、lockfile、普通 JSON、相似包名；格式保持和幂等 |
| manifest | Vue2 runtime/compiler/SSR/test/composition、bridge/loaders、Node14 marker；Vue3/Node16/target no-op |
| deterministic JS | 两种 option owner、quoted property、numeric `tc/$tc`；nested/unowned/ambiguous overload/新旧键冲突 no-op；冲突 marker；幂等 |
| JS/TS marker | import/install/new/static、Legacy、options、tc、locale overload、structured result、array-like list、JIT/unplugin；现代 Composition no-op |
| template | paired/self-closing AUTO、place/custom block/comment/script/style no-op、v-t/tc/overload/result/bootstrap MARK；幂等 |
| locale JSON/YAML | modulo、linked grouping、raw email；normal named/plural/literal escape 和非-locale 路径 no-op；幂等 |
| real repositories | 4 个固定 commit 的 manifest 与真实 source/template 形态执行推荐配方，覆盖 upgrade、AUTO、MARK 和 strict no-invention |
| quality | strict/recommended discover + validate；manifest/source/template/resource two-cycle idempotency；模块 clean verify |

## 固定官方依据

目标 tag `v11.3.0` peeled commit 固定为 [`241f5890`](https://github.com/intlify/vue-i18n/commit/241f5890c5353abd6580b2b050643e749965b78c)：

- [v9 breaking changes](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/docs/guide/migration/breaking.md)；
- [v10 breaking changes](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/docs/guide/migration/breaking10.md)；
- [v11 breaking changes](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/docs/guide/migration/breaking11.md)；
- [Vue 2 bridge migration](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/docs/guide/migration/vue2.md)、[Vue 3/Composition migration](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/docs/guide/migration/vue3.md)；
- [11.3.0 package manifest](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/packages/vue-i18n/package.json) 与 [fixed changelog](https://github.com/intlify/vue-i18n/blob/241f5890c5353abd6580b2b050643e749965b78c/CHANGELOG.md)。

XLSX source tags 固定到 commits：`7.3.2` [`9c92f867`](https://github.com/kazupon/vue-i18n/commit/9c92f867594c2f777e37ff40fddb4b23a95ec6e2)、`8.11.2` [`1b31bba3`](https://github.com/kazupon/vue-i18n/commit/1b31bba3594b3528c32c1b350a0c3264ba721c0f)、`8.20.0` [`992c4022`](https://github.com/kazupon/vue-i18n/commit/992c4022b49900f45e702dfc8271f9b4df01e95e)、`8.22.1` [`0365b836`](https://github.com/kazupon/vue-i18n/commit/0365b836253283dd780115ad8cdc5607b0aeb6ba)、`8.22.4` [`92a54e71`](https://github.com/kazupon/vue-i18n/commit/92a54e7141823409f81b233e4a94200911794b04)、`8.24.3` [`5b0e290e`](https://github.com/kazupon/vue-i18n/commit/5b0e290e4930ddd4ec8f71ce5bef443016039550)、`8.24.4` [`13f3e618`](https://github.com/kazupon/vue-i18n/commit/13f3e618d5a7c3c32fe99f156d1ee2b4e199a5a4)、`8.25.0` [`8a93a9b8`](https://github.com/kazupon/vue-i18n/commit/8a93a9b8aac8c7680d7437db0236da899e35acb4)、`8.26.7` [`83bd8d41`](https://github.com/kazupon/vue-i18n/commit/83bd8d414a46aa850a86edfc746b4104086bb5ef)、`8.27.1` [`74ebab63`](https://github.com/kazupon/vue-i18n/commit/74ebab63c825e7cf4cabd47bb1133ea66f838d5f)。

## 固定真实仓用例与 OpenRewrite 参考

- [Pixelfed `c8bed78b` package](https://github.com/pixelfed/pixelfed/blob/c8bed78bee3d796c5efb57393dafafbba3706f38/package.json) 与 [`resources/assets/js/app.js`](https://github.com/pixelfed/pixelfed/blob/c8bed78bee3d796c5efb57393dafafbba3706f38/resources/assets/js/app.js)：`^8.27.1` AUTO，Vue2/default/new/install MARK；
- [XBoot Front `fb933de4` package](https://github.com/Exrick/xboot-front/blob/fb933de4c5927792b71da31479f5f0693aeb71c6/package.json) 与 [`src/locale/index.js`](https://github.com/Exrick/xboot-front/blob/fb933de4c5927792b71da31479f5f0693aeb71c6/src/locale/index.js)：`^8.24.4` AUTO，真实 Vue2 bootstrap MARK；
- [MQTTX `a8a9087f` package](https://github.com/emqx/MQTTX/blob/a8a9087fd6a9b434300bf4882c7978c9196ac674/package.json)、[`EmptyPage.vue`](https://github.com/emqx/MQTTX/blob/a8a9087fd6a9b434300bf4882c7978c9196ac674/src/components/EmptyPage.vue) 与 [`help/index.vue`](https://github.com/emqx/MQTTX/blob/a8a9087fd6a9b434300bf4882c7978c9196ac674/src/views/help/index.vue)：`^8.11.2` 与两组 translation component AUTO，Vue2 compiler/真实 `$tc` MARK；
- [Vuestic Admin `9c5b44f3` package](https://github.com/epicmaxco/vuestic-admin/blob/9c5b44f3674d4c3e7ad01cc043d5331cee953c49/package.json)、[`src/i18n/index.ts`](https://github.com/epicmaxco/vuestic-admin/blob/9c5b44f3674d4c3e7ad01cc043d5331cee953c49/src/i18n/index.ts) 与 [`vite.config.ts`](https://github.com/epicmaxco/vuestic-admin/blob/9c5b44f3674d4c3e7ad01cc043d5331cee953c49/vite.config.ts)：未列 `^9.6.2` 保持并标记，现代 `legacy:false` source no-op，unplugin 是 JIT build review 边界。

测试结构参考 OpenRewrite 固定提交 [`rewrite@1b1804a5`](https://github.com/openrewrite/rewrite/commit/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 [`ChangeValueTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) / [`JsonPathMatcherTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [`ImportTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[`ObjectLiteralTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ObjectLiteralTest.java) 和 [`MethodInvocationTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-vue-i18n-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.vuei18n.MigrateVueI18nTo11_3_0
```

检查全部 `SearchResult`，完成 Vue 3、Legacy/Composition、SSR、scope、message compiler、JIT/CSP 决策后重建 lockfile。运行 typecheck、locale compile、unit/E2E、production/SSR build、hydration、lazy loading、fallback/missing handler、plural/date/number formatting 和各语言 UI 回归。

模块验证：

```bash
mvn -f rewrite-vue-i18n-upgrade/pom.xml clean verify
```
