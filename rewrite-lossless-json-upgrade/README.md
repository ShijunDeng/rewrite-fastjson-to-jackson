# lossless-json upgrade to 4.0.1

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `lossless-json`，精确处理 `2.0.8`、`2.0.11` 到 `4.0.1` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.losslessjson.UpgradeLosslessJsonTo4_0_1
```

## 自动处理范围

配方只扫描根目录及 workspace 子目录中的 `package.json`，修改 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 四个直接依赖区中精确的 `lossless-json` 键。只有以表格所列版本为锚点的常见 npm semver 声明会被设为精确版本 `4.0.1`，包括 `^2.0.8`、`~2.0.11`、`>= 2.0.8 < 3`、`2.0.8 || ^2.0.11`、hyphen range、prerelease/build metadata 与 `v` 前缀。

配方不会笼统升级所有 2.x/3.x，也不会修改：

- 已是目标版本、目标 range 或 4.0.2+ 的声明，避免降级；
- 表格未列出的 `2.0.7`、`2.0.9`、`2.0.10`、3.x 以及 `2.x`、`>=2.0.0`、`*`、tag；
- `workspace:`、npm alias、Git/GitHub、HTTP tarball、`file:` 等非 registry 引用；
- `overrides`、`resolutions`、`pnpm.overrides`，它们通常用于传递依赖约束；
- `package-lock.json`、pnpm/Yarn lockfile、普通 JSON、`@types/lossless-json` 和相似包名；
- JavaScript/TypeScript/Vue 源码、导入、类型注解、Bundler、Jest/Vitest 或 SSR 配置。

运行后必须使用工程原有 npm、pnpm 或 Yarn 重建锁文件。发布库的 `peerDependencies` 表达兼容范围；在验证 4.x 后，可由维护者将配方产生的精确版本调整为合适范围。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v3 停止官方支持 Node.js 16 | 先把 CI、容器、serverless runtime、Electron、构建镜像和本地开发环境迁到受支持 Node；目标 manifest 没有 `engines` 字段，不能把“安装未报错”当作 Node 16 仍受支持 |
| v2.0.8 的根包声明 `type: module` 且 `main` 指向 ESM，v2.0.10 修正 `main` 为 CommonJS；4.0.1 使用 `exports.import`/`exports.require`、`module`/`main` 双入口 | ESM 使用 `import { parse, stringify } from "lossless-json"`，CommonJS 从公开根入口 `require("lossless-json")`；不要直读 `lib/esm`、`lib/umd`、`dist`，并回归 Jest/Vitest、Webpack/Vite、ts-node、SSR 与 Electron 对 conditional exports 的解析 |
| v3 把 `JavaScriptValue`、`JavaScriptObject`、`JavaScriptArray`、`JavaScriptPrimitive` 的实际定义替换为 `unknown` 并保留 deprecated alias | 删除对这些递归联合类型的假设，使用 `unknown` 后做 type guard/schema validation，或为业务结构定义明确接口；真实 PowerSync 样例直接导入 `JavaScriptValue`，升级后应清理 deprecated 使用 |
| v4 把 `JSONValue`、`JSONObject`、`JSONArray`、`JSONPrimitive` 的使用迁向 `unknown`/`Record`/数组并标为 deprecated | 自定义 replacer、wrapper 与再导出的公共类型可能出现 TypeScript 错误；不要靠类型断言掩盖输入边界，应收窄 `unknown` 并升级公开 API 类型 |
| `parse` 的返回类型在 4.x 是 `unknown` | `const value = parse(text)` 后不能直接访问属性；通过 Zod/JSON Schema/自定义 guard 验证，或在可信边界显式转换。React/Vue 响应转换器、Kafka message editor 和 RPC 数据尤其需要检查 |
| `Reviver` 的 value/return 与 `NumberParser` 的返回类型使用 `unknown` | 旧版回调按 `JSONValue`/`JavaScriptValue` 编写时要调整签名；reviver 按 JSON.parse 的叶到根顺序执行，返回 `undefined` 会删除属性，不能误把数字解析器当 reviver 参数 |
| `parse(text, reviver, parseNumber)` 的第三参数控制所有数字表示 | 保留参数占位：不需要 reviver 但需要 number parser 时使用 `parse(text, undefined, parser)` 或 4.0.1 允许的 `null`；逐项验证整数、小数、指数、负零、溢出/下溢，而不是统一 `parseFloat` 后声称“无损” |
| `parseNumberAndBigInt` 把整数转为 `bigint`，默认 parser 产生 `LosslessNumber` | 两者不是相同数据模型；BigInt 不可被原生 `JSON.stringify` 序列化，`LosslessNumber.valueOf()` 也可能返回 number、bigint 或抛错。算术、比较、数据库驱动、React state、structured clone 与 API schema 都要回归 |
| `stringify` 支持 BigInt、LosslessNumber 和自定义 `numberStringifiers` | 其输出可包含未加引号的大整数，接收端若使用普通 JSON parser 仍会丢精度；自定义 stringifier 必须返回合法 JSON number，否则 4.x 抛错。不要把 replacer 返回的数字字符串与 number stringifier 混淆 |
| 4.0.1 明确允许 `null` reviver/replacer | 目标补丁修复 TypeScript API 的 `null` 接受能力；调用 `parse(text, null, parser)`/`stringify(value, null, space)` 前仍应在所用 TS config 下编译验证，旧的本地声明或 `@types/lossless-json` 可能覆盖内置类型 |
| 包从 v2 起遇到重复 key 会检查并在值不相等时抛 SyntaxError | 表格起点已经具有该行为，但迁移回归不能按原生 `JSON.parse` 的“后值覆盖前值”设计；API、日志或区块链输入若可能重复 key，要在边界明确拒绝策略。当前实现对相同值重复 key 不抛错，不能把它当完整规范验证器 |
| `LosslessNumber` 保留原数字文本，安全转换有明确失败路径 | 大整数、小数尾数和极端指数的 `.valueOf()` 结果不同；优先用 `.toString()`、`isSafeNumber`、`getUnsafeNumberReason`、`toSafeNumberOrThrow` 建立业务策略，不要隐式用一元加号、模板算术或宽松相等 |
| Date 支持通过 `reviveDate` 提供且默认关闭 | 开启后 ISO 字符串会变为 Date，可能改变 DTO、缓存 key、序列化和时区语义；自定义 reviver/replacer 的组合顺序、无效日期与 UTC/本地时区必须测试 |
| 4.x 内置 TypeScript 声明，不需要 DefinitelyTyped 包 | `@types/lossless-json` 可能陈旧并与内置声明冲突；本配置配方为控制范围不会自动删除它，升级后人工移除并执行 strict typecheck，尤其检查 `types`、`moduleResolution` 与 package exports |
| 包有 `sideEffects: false` 且鼓励模块化 named imports | tree-shaking 可能移除未使用入口；namespace import 通常仍可工作，但 bundle 快照、CommonJS interop、mock 写法和动态 require 必须在生产构建中验证 |

官方依据：

- [官方 v4.0.1 changelog](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/CHANGELOG.md)：v3 的 Node 16 与 `JavaScript*` 类型变化、v4 的 `JSON*` 类型变化、4.0.1 的 null reviver/replacer 修复；
- [官方 v4.0.1 package manifest](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/package.json)：目标 ESM/CJS/TypeScript conditional exports 与 `sideEffects`；
- [官方 v4.0.1 README/API](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/README.md)：parse/stringify、reviver/replacer、number parser、LosslessNumber、BigInt、Date 与安全数字策略；
- [官方 v4.0.1 parse 实现](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/src/parse.ts)：重复 key、null reviver 和第三参数 number parser 的准确行为。

本配方只自动修改能安全定位的直接依赖声明。模块系统、Node runtime、TypeScript 类型与数字语义必须结合业务源码迁移。

## 真实仓库测试来源

测试固定到公开仓库的具体 commit，并同时保留依赖上下文与需要人工审查的真实源码形态：

- [provectus/kafka-ui @ 83b5a60c package.json](https://github.com/provectus/kafka-ui/blob/83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7/kafka-ui-react-app/package.json)：React 前端使用 `lossless-json: ^2.0.8` 和旧 `@types`/Node 16 类型；[EditorViewer.tsx](https://github.com/provectus/kafka-ui/blob/83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7/kafka-ui-react-app/src/components/common/EditorViewer/EditorViewer.tsx) 使用 named `parse`/`stringify`；
- [powersync-ja/powersync-service @ 1c44c31b package.json](https://github.com/powersync-ja/powersync-service/blob/1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6/packages/jsonbig/package.json)：pnpm monorepo 子包使用 `^2.0.8`；[json.ts](https://github.com/powersync-ja/powersync-service/blob/1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6/packages/jsonbig/src/json.ts) 同时使用 namespace import、`JavaScriptValue`/`NumberParser`/`Reviver` 类型与 BigInt parser；
- [serenita-org/ethstaker.tax @ 1baf7fbf package.json](https://github.com/serenita-org/ethstaker.tax/blob/1baf7fbfe024576770fe56b13fe25b37b68e08b5/src/frontend_vue/package.json)：Vue 工程使用 `^2.0.11`；[TheMainView.vue](https://github.com/serenita-org/ethstaker.tax/blob/1baf7fbfe024576770fe56b13fe25b37b68e08b5/src/frontend_vue/src/views/TheMainView.vue) 通过 `parse`/`isInteger` 处理可能超过 JavaScript 安全范围的 wei；
- [color-typea/lido-trustless-tvl-oracle-solution @ 89bdedcb package.json](https://github.com/color-typea/lido-trustless-tvl-oracle-solution/blob/89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2/package.json)：Hardhat 工程使用 `^2.0.11`；[部署源码](https://github.com/color-typea/lido-trustless-tvl-oracle-solution/blob/89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2/deploy/00-gates.ts) 使用 ESM namespace import；
- [kafbat/kafka-ui @ bc2d7cad package.json](https://github.com/kafbat/kafka-ui/blob/bc2d7cad4678d5ebb6fd06af49068e34c9cc8b59/frontend/package.json)：现代 Node 类型与 pnpm 前端中精确固定 `2.0.11`，用于验证精确版本和相邻依赖保持。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。共 50 个测试，覆盖两项表格版本、四依赖区、十种 semver、多个 workspace manifest、五个真实工程、真实源码 no-op，以及目标/高版本/未列版本/协议/override/lockfile/普通 JSON/非字符串值/相似包名等边界。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-lossless-json-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.losslessjson.UpgradeLosslessJsonTo4_0_1
```

确认 patch 后，移除不再需要的 `@types/lossless-json`，用原包管理器重建 lockfile。运行 Node 支持矩阵、strict TypeScript、unit/integration/E2E、Jest/Vitest、CJS/ESM、Bundler、SSR/Electron 和 bundle-size 测试；另外用安全整数、大整数、BigInt、小数、指数、负零、重复 key、null reviver/replacer、Date、自定义 replacer/number parser/number stringifier 建立序列化兼容测试。

本模块自身验证：

```bash
mvn -f rewrite-lossless-json-upgrade/pom.xml clean verify
```
