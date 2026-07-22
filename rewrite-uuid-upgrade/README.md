# uuid 升级到 13.0.2

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `uuid`，处理 `8.3.2`、`9.0.0`、`9.0.1`、`10.0.0`、`11.0.3` 和 `11.1.0`，目标版本为 `13.0.2`。

配方名称：

```text
com.huawei.clouds.openrewrite.uuid.UpgradeUuidTo13_0_2
```

## 自动处理范围

配方只修改名为 `package.json` 的文件，并只检查顶层 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies` 中的直接 `uuid` 声明。表格精确版本，以及以这些版本为基准的常见 caret、tilde、比较器、OR、hyphen、`v` 前缀、预发布和 build metadata 写法，会被设置为精确版本 `13.0.2`。

配方有意不修改：

- 表格未列出的旧版本、`11.1.1`/`12.x` 等中间补丁、目标版本和更高版本；
- `workspace:`、`link:`、`npm:` alias、Git/GitHub、`file:`、HTTP(S) tarball、tag、通配符和空声明；
- `overrides`、`resolutions`、`pnpm.overrides`、锁文件、普通 JSON 或间接依赖；
- `@types/uuid`、`uuid-apikey`、`uuid-validate`、`uuidv4` 等相似包；
- JavaScript/TypeScript import、CommonJS、Node/TypeScript/bundler/Jest 配置和 React Native polyfill。

这是刻意的安全边界。8/9/10/11 跨到 13 包含运行时、纯 ESM、conditional exports、TypeScript 类型和时间型 UUID 状态语义变化，仅替换依赖字符串不能可靠迁移业务代码。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v9 放弃 Node.js 10、IE 11 和 Safari 10，并删除 minified UMD build | 旧 Node、旧 WebView 和直接引用 `uuid.min.js` 的 `<script>`/CDN 页面会失败。使用现代 bundler 或标准 ESM，并在真实最低浏览器上验证 Web Crypto |
| v10 放弃 Node.js 12/14 | 目标 v13.0.2 的官方 CI 覆盖 Node 18、20、22、24。先把本地、CI、Docker 和生产运行时至少提升到 Node 18；仅修改 `engines.node` 并不能证明依赖链兼容 |
| v10 按 RFC 9562 增加 `MAX`、v6/v7 及转换 API | 原有 v1/v3/v4/v5 仍可用，但若采用时间有序 v6/v7，不要假设 UUID 字符串天然等于数据库排序、业务时间或全局单调序列；需回归索引、分片和碰撞处理 |
| v11 移植到 TypeScript 并收紧函数签名 | 旧 `any`、普通数组、Buffer/Uint8Array、namespace 和输出 buffer 调用可能出现编译错误。v11.0.3 进一步收紧 v* 签名，v11.1.0 才扩展到 Uint8Array subtype；目标类型由包自身提供 |
| v11 改变 v1/v6/v7 的 `options` 状态语义 | 不传 `options` 时使用内部状态改善唯一性；一旦传入 `options`，就不使用该内部状态并按传入值/默认值计算。固定时间、随机源、clock sequence 或批量生成测试必须验证序列与重复值 |
| v12 放弃 Node.js 16，并升级到 TypeScript 5.2 | 旧 TypeScript、Angular/ts-jest 声明解析可能失败。目标包使用 TypeScript 5.2.2 构建；升级编译器和 `moduleResolution`，不要依赖 `skipLibCheck` 掩盖真实调用签名问题 |
| v12 完全移除 CommonJS | `require('uuid')`、`module.exports` 消费、同步 CJS 测试 harness 和只输出 CJS 的库都可能报 `ERR_REQUIRE_ESM`。迁移为 `import { v4 as uuidv4 } from 'uuid'`，或在受控边界用动态 `import()`；同时调整 Jest、ts-jest、Babel、Webpack/Rollup/Vite 和发布产物 |
| 仅支持根 named exports | 目标 manifest 只导出 `.` 和 `./package.json`。不要使用 default import、`uuid/v4`、`uuid/dist/*` 或其他 deep import；使用 `import { v1, v4, v5, v7, parse, stringify, validate } from 'uuid'` |
| v13 把 browser export 设为 default condition | Node 条件解析到 `dist-node`，未声明 `node` condition 的 bundler/edge/SSR 工具可能改走 browser build。浏览器默认依赖 Web Crypto；分别回归 Node SSR、browser bundle、worker/edge 和 tree shaking，不要用 alias 强行绑定内部 `dist-*` 路径 |
| React Native/Expo 仍需要 `crypto.getRandomValues()` | 缺少 Web Crypto 时会出现 `getRandomValues() not supported`。官方建议先加载 `react-native-get-random-values`，而且必须早于任何直接或间接 uuid import；配方不会添加或重排 polyfill |
| 包内置 TypeScript declarations | 独立 `@types/uuid` 可能是冗余 stub 或与新签名冲突。先编译验证再删除；配方保留相邻依赖，避免误删被旧工具链显式引用的类型包 |
| v13.0.1 修复 CVE-2026-41907，13.0.2 为 provenance 重发 | v3/v5/v6 接收 output buffer 时，旧版本不会完整检查 buffer/offset 边界，可能静默部分写入。目标已含修复，应增加短 buffer、负/大 offset 和预分配 buffer 回归，并正确处理 `RangeError` |

官方依据：uuid [CHANGELOG](https://github.com/uuidjs/uuid/blob/v13.0.2/CHANGELOG.md)、[v13.0.2 README](https://github.com/uuidjs/uuid/blob/v13.0.2/README.md)、[v13.0.2 package manifest](https://github.com/uuidjs/uuid/blob/v13.0.2/package.json)、[v13 CI Node matrix](https://github.com/uuidjs/uuid/blob/v13.0.2/.github/workflows/ci.yml)、[v9.0.0 release](https://github.com/uuidjs/uuid/releases/tag/v9.0.0)、[v10.0.0 release](https://github.com/uuidjs/uuid/releases/tag/v10.0.0)、[v11.0.0 release](https://github.com/uuidjs/uuid/releases/tag/v11.0.0)、[v11.1.0 release](https://github.com/uuidjs/uuid/releases/tag/v11.1.0)、[v12.0.0 release](https://github.com/uuidjs/uuid/releases/tag/v12.0.0)、[v13.0.0 release](https://github.com/uuidjs/uuid/releases/tag/v13.0.0)、[v13.0.2 release](https://github.com/uuidjs/uuid/releases/tag/v13.0.2) 和 GitHub [CVE-2026-41907 / GHSA-w5hq-g745-h8pq](https://github.com/advisories/GHSA-w5hq-g745-h8pq)。

## 典型代码迁移

CommonJS 代码不能继续直接加载目标版本：

```diff
-const { v4: uuidv4 } = require('uuid');
+import { v4 as uuidv4 } from 'uuid';

 const id = uuidv4();
```

如果工程仍必须发布 CommonJS，需要在自己的构建过程中显式 bundle/转译并测试，而不是 deep import uuid 的内部构建文件。库项目还应同时验证 `exports`、`.d.ts`、ESM consumer 和 bundler consumer。

React Native/Expo 入口应保证 polyfill 顺序：

```js
import 'react-native-get-random-values';
import { v4 as uuidv4 } from 'uuid';
```

## 真实测试样本与 OpenRewrite 参考

测试从以下公开仓库的固定 commit 缩减而来：

- [sheinsight/shineout](https://github.com/sheinsight/shineout/blob/f75168569cbf87e269f0d37fee2e91b71e9b6ea1/package.json)：运行时 `uuid: 8.3.2`，同时存在 Webpack 4、TypeScript 4.5 和 `@types/uuid`；其 [`src/utils/uid.ts`](https://github.com/sheinsight/shineout/blob/f75168569cbf87e269f0d37fee2e91b71e9b6ea1/src/utils/uid.ts) 使用 `import { v4 as getUUid }` 并把参数类型透传给包装函数；
- [open-wa/wa-automate-nodejs](https://github.com/open-wa/wa-automate-nodejs/blob/043bb31e542944213375179532fbaef89a1e42af/package.json.v4-backup)：`uuid: ^9.0.0`、相同 `overrides` 和 Node `>=12.18.3` 共存，验证只改直接依赖、保留 override 和运行时基线；
- [gwenaelp/vue-diagrams](https://github.com/gwenaelp/vue-diagrams/blob/b417a289f08a0f0dccfa6156397d35142066334b/package.json)：Vite/TypeScript/Vue 库中的 `uuid: ^10.0.0`，但 manifest 仍声明 Node `>=8.9.0`；
- [liuzi6612/boomb](https://github.com/liuzi6612/boomb/blob/320c472f264d61fed254c2ce35b9fa6b2e4a12d9/package.json)：`type: module` 应用中的 `uuid: ^11.1.0` 与 `@types/uuid`；其 [`src/store/index.ts`](https://github.com/liuzi6612/boomb/blob/320c472f264d61fed254c2ce35b9fa6b2e4a12d9/src/store/index.ts) 已使用目标兼容的 named ESM import。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，覆盖 JSON 格式保持、JSONPath filter expression、多个 source path 和严格 no-op 边界。

当前测试覆盖四个真实仓库、表格全部六个版本、四个直接依赖区、caret/tilde/比较器/OR/hyphen/v-prefix/prerelease/build metadata、monorepo 子包及相邻类型/polyfill；同时验证目标/高版本防降级、未列版本、workspace/link/npm alias/Git/file/URL/tag/通配符、畸形版本、overrides/resolutions、lockfile、其他 JSON 和相似包名均不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-uuid-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.uuid.UpgradeUuidTo13_0_2
```

确认 patch 后，重建 lockfile，升级 Node/TypeScript，迁移 CommonJS 和测试工具链，并运行 TypeScript build、Node ESM、browser bundle、SSR/edge、React Native、v1/v6/v7 状态序列、v3/v5/v6 buffer bounds 以及 UUID 持久化/排序回归。

模块自身验证：

```bash
mvn -pl rewrite-uuid-upgrade -am clean verify
```
