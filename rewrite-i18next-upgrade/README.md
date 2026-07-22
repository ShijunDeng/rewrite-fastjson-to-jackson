# i18next 升级到 25.10.10

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `i18next`，只处理表格可见的 `21.10.0`、`21.6.14`、`22.4.10`、`22.4.9`、`22.5.1` 到 `25.10.10` 的迁移。

推荐配方：

```text
com.huawei.clouds.openrewrite.i18next.MigrateI18nextTo25_10_10
```

若只允许修改依赖声明，使用严格子配方：

```text
com.huawei.clouds.openrewrite.i18next.UpgradeI18nextTo25_10_10
```

## 处理矩阵

| 分类 | 配方行为 | 边界 |
| --- | --- | --- |
| AUTO：依赖 | 将四个直接依赖区中 `i18next` 的表格源版本升级为精确 `25.10.10` | 只接受 `21.10.0`、`^21.10.0`、`~21.10.0` 这类精确/单 caret/单 tilde 形态，并覆盖表格中的全部五个版本 |
| AUTO：初始化 | 将可证明属于 i18next 且无目标键冲突的 `initImmediate` 改为 `initAsync` | 只处理直接传给未被局部绑定遮蔽的 i18next `.init({...})` 的对象，或显式标注为从 i18next 导入且未被本地类型遮蔽的 `InitOptions`/别名对象；已有 `initAsync` 时保留并 MARK |
| AUTO：英文复数 | 将英文 locale JSON 中无冲突的字符串 `key`/`key_plural` 对改为 `key_one`/`key_other` | 支持嵌套与反向排列；非英文、numeric suffix、缺半边、非字符串、已有 `_one`/`_other` 冲突均不自动改 |
| MARK：manifest/config | 在精确 AST 节点放置 `SearchResult` | 标记未被严格依赖配方选择的 i18next 声明、TypeScript <5、Node <14、独立版本的 bridge/backend/detector，以及 tsconfig 中显式 `strict:false`/`strictNullChecks:false` |
| MARK：源码 | 在 import、调用、选项或字符串 key 上放置 `SearchResult` | 标记移除类型、深度 import、`setDebug`、`isWhitelisted`、已识别实例的 `changeLanguage`/`exists`、旧 JSON 选项、return 行为、selector opt-in 和遗留 `initImmediate` 类型 key |
| MARK：locale | 在具体 JSON/YAML key 上放置 `SearchResult` | 标记自动处理后仍存在的 `_plural` 与 numeric plural suffix，要求按该语言的 CLDR cardinal/ordinal/context 规则审查 |
| NO-OP | 不猜测、不扩大范围 | 复杂 semver、协议/alias/Git/URL、prerelease/build、未列版本、override/resolution、lockfile、相似包名、非 locale JSON、现代 v4 资源与无 i18next 所有权的同名 API 保持不变 |

严格依赖配方只检查 `package.json` 根对象下的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。workspace 子包的真实 manifest 会各自处理，但 `workspace:` 不会展开；lockfile 需在审查结果后由工程原包管理器重建。

推荐配方先执行所有确定性 AUTO，再在剩余边界放置 MARK。标记是待办信息，不代表对应代码已完成迁移。

## 不兼容修改点

| 版本跨度 | 本模块处理的风险 |
| --- | --- |
| 21 → 22 | v22 重写 TypeScript 类型；JavaScript 22.0 与 21.10 行为接近，但 TS wrapper、module augmentation、namespace/key 推断和 mock 必须重新 typecheck。 |
| 22 → 23 | 需要 TypeScript 5，并开启 `strict` 或至少 `strictNullChecks`；`StringMap`、`KeysWithSeparator`、`TFuncKey`、`DefaultTFuncReturn*`、`Normalize*` 等旧公开类型被移除、内化或替代。 |
| 22 → 23 | `returnNull` 默认改为 `false`；全局 `returnObjects:true` 要与 `CustomTypeOptions` 对齐；ordinal key 引入 `_ordinal`；内部 logger `setDebug` 被移除。 |
| 23 → 24 | Node <14、TypeScript <5 和缺失 `Intl` 的环境不再支持；旧 JSON/API compatibility 被移除，`jsonFormat` 删除，`compatibilityJSON` 只保留 `v4`。 |
| 23 → 24 | `initImmediate` 改名为 `initAsync`。本模块只在所有权确定时自动改名；通过 spread、工厂、外部配置加载等无法静态证明的形态保留并标记。 |
| 24 → 25 | `changeLanguage` 的并发完成顺序、best-match 与同 script fallback 改变；需覆盖快速切换、SSR hydration、缓存、路由、事件和 backend 请求。 |
| 25.4 | selector API 是 opt-in；启用 `enableSelector` 前需使用官方 codemod/plugin，并回归动态 key、namespace、`keyPrefix`、plural/context 和测试 mock。 |
| 25.6 | `returnObjects:false` 时，`exists()` 对对象 key 返回 `false`；依赖对象/叶子判断的导航和 fallback 逻辑需回归。 |
| ESM/CJS | 25.10.10 的 root conditional exports 对 import/require 分别选择 `index.d.mts`/`index.d.ts`；禁止依赖 `dist/**` 或 `src/**` 实现路径，并分别验证 NodeNext/Bundler、Jest/Vitest、SSR 和 bundler。 |
| 生态包 | `react-i18next`、`next-i18next`、browser detector、HTTP/FS/local-storage/chained backend 等有独立兼容线；本模块仅标记，绝不猜测它们的目标版本。 |

英文 v3 复数转换是刻意收窄的特例：英文 cardinal 的无冲突 singular/plural 字符串对可确定映射为 `one`/`other`。其他语言的 plural category、ordinal、context、自定义 separator 和 key collision 需要按 locale 决策，因此仅 MARK。

## 固定官方依据

- 目标 tag `v25.10.10` 解引用到 [`e0fa8382de3b64100a594a2c27124ea9fa48814b`](https://github.com/i18next/i18next/tree/e0fa8382de3b64100a594a2c27124ea9fa48814b)；固定 [`package.json`](https://github.com/i18next/i18next/blob/e0fa8382de3b64100a594a2c27124ea9fa48814b/package.json) 用于核对版本、CJS/ESM 入口、conditional exports 与 TypeScript peer 范围。
- 表格源 tag 固定为 [`v21.10.0`](https://github.com/i18next/i18next/tree/6bc410b08cdcc33007ecd231787c679d38ee4933)、[`v21.6.14`](https://github.com/i18next/i18next/tree/9ca694db52fe5b6f10da5debf3e106e6f615b907)、[`v22.4.10`](https://github.com/i18next/i18next/tree/d787dd22d1ad775aea68ae53bfa04784285cb0eb)、[`v22.4.9`](https://github.com/i18next/i18next/tree/a334583a7752bf83a62da10fae035af53641784b)、[`v22.5.1`](https://github.com/i18next/i18next/tree/51ef11d0703b193b71bcb6263dc585765221fec6)。
- 文档固定到 `i18next-gitbook@34151b62445e0a81649c7e1e40fc57636f7ffd87` 的 [`migration-guide.md`](https://github.com/i18next/i18next-gitbook/blob/34151b62445e0a81649c7e1e40fc57636f7ffd87/misc/migration-guide.md)、[`json-format.md`](https://github.com/i18next/i18next-gitbook/blob/34151b62445e0a81649c7e1e40fc57636f7ffd87/misc/json-format.md) 与 [`typescript.md`](https://github.com/i18next/i18next-gitbook/blob/34151b62445e0a81649c7e1e40fc57636f7ffd87/overview/typescript.md)。
- 英文 plural 用例固定取自官方 [`i18next-v4-format-converter@f18d4f5`](https://github.com/i18next/i18next-v4-format-converter/tree/f18d4f5424994c5f34ab535f7ba82ca617e02616)，用于验证 `myKey`/`myKey_plural` 到 `_one`/`_other` 的正向、反向和嵌套形态。

## 固定真实仓库用例

- [`module-federation/module-federation-examples@9c4e554a`](https://github.com/module-federation/module-federation-examples/blob/9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5/i18next-nextjs-react/i18next-shared-lib/package.json)：同时覆盖 dev/peer 声明、`InitOptions` 别名、`createInstance().use(...)` 链和 [`initImmediate`](https://github.com/module-federation/module-federation-examples/blob/9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5/i18next-nextjs-react/i18next-shared-lib/src/i18nService.ts)。
- [`binwiederhier/ntfy@7680cb49`](https://github.com/binwiederhier/ntfy/blob/7680cb490687e5e80b9d3ce501bb538db8ee1776/web/package.json)：覆盖嵌套 web manifest、`^21.6.14`、React bridge、backend、detector，以及其 [`i18n.js`](https://github.com/binwiederhier/ntfy/blob/7680cb490687e5e80b9d3ce501bb538db8ee1776/web/src/app/i18n.js) 和现代英文 locale no-op。
- [`openmrs/openmrs-esm-fast-data-entry-app@e7b81a0f`](https://github.com/openmrs/openmrs-esm-fast-data-entry-app/blob/e7b81a0fb60ccb028eb7dd74a5af30e79e75f593/package.json)：覆盖 `^21.10.0` 与 `react-i18next:11.x` peer，且 [`CancelModal.tsx`](https://github.com/openmrs/openmrs-esm-fast-data-entry-app/blob/e7b81a0fb60ccb028eb7dd74a5af30e79e75f593/src/CancelModal.tsx) 不因仅导入 bridge 而误报 core API。
- [`LaravelRUS/SleepingOwlAdmin@2ef22d8e`](https://github.com/LaravelRUS/SleepingOwlAdmin/blob/2ef22d8e656aa159a9e21f77ef603dbd259e431a/package.json)：覆盖 Vue 2/Laravel Mix 工程及其普通 [`init`/`t` helper](https://github.com/LaravelRUS/SleepingOwlAdmin/blob/2ef22d8e656aa159a9e21f77ef603dbd259e431a/resources/assets/js_owl/libs/i18next.js) no-op。

测试结构固定参考 OpenRewrite [`rewrite@1b1804a5`](https://github.com/openrewrite/rewrite/commit/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 [`ChangeValueTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) / [`JsonPathMatcherTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [`ImportTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[`ObjectLiteralTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ObjectLiteralTest.java) 和 [`MethodInvocationTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)。测试包括 before/after、marker、no-op、格式保持、真实 fixture、两周期幂等、recipe discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-i18next-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.i18next.MigrateI18nextTo25_10_10
```

审查 AUTO patch 和全部 `SearchResult` 后，重建 lockfile，并执行 TypeScript typecheck、lint、unit/component/E2E。重点覆盖每个 locale 的 plural/ordinal/context、`Intl`、缺失 key、return 值、并发语言切换、SSR/edge、backend/detector、CJS/ESM 与翻译快照。

本模块验证：

```bash
mvn -f rewrite-i18next-upgrade/pom.xml clean verify
```
