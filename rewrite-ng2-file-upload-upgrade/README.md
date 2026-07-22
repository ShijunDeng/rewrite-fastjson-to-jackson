# ng2-file-upload 2.0.0-3 / 3.0.0 / 4.0.0 → 10.0.0

本模块实现工作簿中的三个精确升级项：

| 工作簿行 | 序号 | 源版本 | 目标版本 |
| --- | ---: | ---: | ---: |
| 383 | 382 | 2.0.0-3 | 10.0.0 |
| 384 | 383 | 3.0.0 | 10.0.0 |
| 385 | 384 | 4.0.0 | 10.0.0 |

推荐配方坚持 **有所有权约束的 AUTO → 精确 MARK**。它不把 Angular 主版本升级、认证/CORS、上传协议、过滤策略或自定义 uploader 的行为选择伪装成安全的文本替换。

## 固定官方依据

### ng2-file-upload 版本

- `v2.0.0-3` annotated tag object 为 `a0daa76d2c181c39702f037486306527b8771ba2`，peeled commit 为 [`19ec08cc025f8f7a0857586adb1444b1f343de4a`](https://github.com/valor-software/ng2-file-upload/tree/19ec08cc025f8f7a0857586adb1444b1f343de4a)。
- `v3.0.0` annotated tag object 为 `3a9bd3eee549cd49db7289db30e2bfdd62540529`，peeled commit 为 [`21e04deafe36f96e617014056561c246bcc51759`](https://github.com/valor-software/ng2-file-upload/tree/21e04deafe36f96e617014056561c246bcc51759)。
- 官方 `v4.0.0` lightweight tag 指向 `6dbb644803c0b0f25f74d67b577aa67128fc4b85`，但该树内库清单仍写 `3.0.0`；npm 发布的 `4.0.0` 明确记录 `gitHead` 为 [`860e8aa0be5bf476ac42372a90d2c940891b39d5`](https://github.com/valor-software/ng2-file-upload/tree/860e8aa0be5bf476ac42372a90d2c940891b39d5)，本模块以发布物 `gitHead` 为 4.0.0 源码依据，同时保留 tag 异常的审计记录。
- `v10.0.0` lightweight tag 和目标提交均为 [`4fa805d9e1e5ff5bcd642c4c6312424e27185fb4`](https://github.com/valor-software/ng2-file-upload/tree/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4)。
- [固定目标 CHANGELOG](https://github.com/valor-software/ng2-file-upload/blob/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4/CHANGELOG.md) 记录 5–10 分别对 Angular 16–21 的支持。
- [npm 10.0.0 发布清单](https://unpkg.com/ng2-file-upload@10.0.0/package.json) 是 export map、FESM2022 入口、types 和 peer 约束的最终依据。

### Angular 21 兼容矩阵

- Angular `21.0.0` 官方 tag 固定为 [`63a95c7b9e6cc2dc5296331507ec028ca92ee2c4`](https://github.com/angular/angular/tree/63a95c7b9e6cc2dc5296331507ec028ca92ee2c4)。
- 发布的 `@angular/core@21.0.0` 要求 Node `^20.19.0 || ^22.12.0 || >=24.0.0`，peer RxJS `^6.5.3 || ^7.4.0`、zone.js `~0.15.0`。
- 发布的 `@angular/compiler-cli@21.0.0` 要求 TypeScript `>=5.9 <6.0`。
- `ng2-file-upload@10.0.0` peer `@angular/core` / `@angular/common` `^21.x.x`，依赖 `tslib ^2.3.0`。

## 不兼容点与处理

### 1. package.json 所有权

`UpgradeSelectedNg2FileUploadDependency` 仅修改根级 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中的：

- 精确 `2.0.0-3`、`3.0.0`、`4.0.0`；
- 对应的 `^` 或 `~` 单版本声明。

它保留运算符并升级到 `10.0.0`。复杂范围、workspace/catalog、npm alias、Git/file/URL、动态版本、未列版本、override/resolution 和 lockfile 不自动修改。

`FindNg2FileUploadManifestRisks` 随后标记：

- 未到目标的直接 owner 及 override/resolution 独立 owner；
- 未对齐 Angular 21 的 core/common/CLI/compiler/forms/router/platform/CDK/build owner；
- 不满足 Angular 21 Node 基线的 `engines.node`；
- 不满足 RxJS `^6.5.3 || ^7.4.0`、TypeScript `>=5.9 <6` 或 tslib `^2.3.0` 的简单声明；
- 多余的 `@types/ng2-file-upload`。

复杂 companion 范围保守标记，不猜测实际 lockfile 解析结果。

### 2. 目标 export map 与 ESM

10.0.0 发布包只导出 `.` 和 `./package.json`，根运行时入口是 `fesm2022/ng2-file-upload.mjs`，没有 CJS `require` 条件。旧工程可访问的 `file-upload/*.class|directive|module` 物理路径因此不能继续作为公共入口。

`NormalizeNg2FileUploadPublicImports` 只对静态 named import 做 AUTO，并且要求：

- 路径恰好是官方旧物理文件之一；
- 每个 named symbol 同时由该文件和 10.0.0 根 `index.ts` 导出；
- 没有 default、namespace、side-effect、unknown symbol 或 re-export 语义。

符合时只把 module specifier 改为 `ng2-file-upload`，保留 alias、`import type` 和引号。CommonJS、动态 import、任意 private path、default/namespace/re-export 均不自动处理并由 MARK 报告。

### 3. Angular standalone / NgModule 与选择器

目标源码中的 [`FileSelectDirective`](https://github.com/valor-software/ng2-file-upload/blob/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4/libs/ng2-file-upload/file-upload/file-select.directive.ts) 和 [`FileDropDirective`](https://github.com/valor-software/ng2-file-upload/blob/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4/libs/ng2-file-upload/file-upload/file-drop.directive.ts) 都明确 `standalone: false`；选择器仍是 `[ng2FileSelect]` / `[ng2FileDrop]`。目标继续通过 [`FileUploadModule`](https://github.com/valor-software/ng2-file-upload/blob/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4/libs/ng2-file-upload/file-upload/file-upload.module.ts) 声明和导出它们。

因此配方不会把 directive 自动变成 standalone import。TypeScript MARK 指向直接 runtime directive import；HTML MARK 精确落在 selector、`[uploader]`、`(onFileSelected)`、`(fileOver)`、`(onFileDrop)` 片段，要求业务确认：

- NgModule、standalone Component 或 TestBed 的 `imports` 中有 `FileUploadModule`；
- lazy scope、模板类型检查、初始化/销毁和 change detection 正确；
- file select/reset、多选、拖放、重复文件和失败事件符合产品行为。

### 4. FileUploader API、扩展点与行为

公开的 `FileUploader`、`FileItem`、`FileLikeObject`、options、queue 和 callbacks 基本保持原名，不能据此假设跨 Angular 11/14/15→21 无行为风险。`FindNg2FileUploadTypeScriptRisks` 用 import alias、局部声明计数、实例类型/构造器和子类所有权标记：

- `new FileUploader(...)`；
- endpoint/auth/header/CORS 选项；
- multipart/raw body、field alias/order、同步/异步 format function；
- MIME/extension/size/count/custom filters；
- upload/cancel/add/remove/clear/setOptions/ready queue 调用；
- success/error/cancel/progress/build-form/filter-failure callbacks；
- `withCredentials` 跨域凭据赋值；
- 自定义 uploader 对 `_file`、XHR helpers 和 `_on*` protected/internal 实现的依赖。

官方源码差异还包含两个必须回归的点：

- 10.0.0 将 protected `_transformResponse(response, headers)` 改为 `_transformResponse(response)`；两参数 override 会被精确标记。
- 过滤失败索引逻辑从 truthy 判断改为显式数字/非负判断；自定义 filters 与 `onWhenAddingFileFailed` 需要覆盖第一个过滤器、后续过滤器、空列表及拒绝回调。

### 5. 上传安全不是客户端 AUTO

`allowedMimeType`、扩展名、`maxFileSize`、queue limit 都只是客户端体验层；服务端仍必须验证实际内容、类型、大小、数量、文件名/path、解压炸弹和访问权限。`authToken`、headers、cookies、`withCredentials`、URL 与 CORS origin 必须一起审查，避免把令牌或 cookie 发送到不可信地址。配方只标记这些精确 owner，不改安全策略。

## 配方入口与顺序

推荐入口：

```yaml
recipeList:
  - com.huawei.clouds.openrewrite.ng2fileupload.MigrateNg2FileUploadTo10_0_0
```

只升级严格依赖声明的低层入口：

```text
com.huawei.clouds.openrewrite.ng2fileupload.UpgradeNg2FileUploadTo10_0_0
```

固定顺序为：依赖 AUTO → public import AUTO → manifest MARK → TypeScript MARK → template MARK。

## 路径规则

路径检查仅查看父目录 component，并先转小写。跳过 node/package-manager caches、构建/覆盖率/vendor/cache 目录，以及名称以 `generated` 或 `install` 开头的父目录；普通 workspace 子包和文件名为 `install.ts`、`install.js`、`install.html` 的叶文件仍处理。

## 真实公开仓固定用例

- [HelloImKevo/UdemyDatingApp](https://github.com/HelloImKevo/UdemyDatingApp)：仓库文档固定使用 `ng2-file-upload@2.0.0-3`，用于 exact manifest 用例。
- [aquality-automation/aquality-tracking-ui uploader @ `90f313a6fe7b954e9b80b10302edf8bba2167154`](https://github.com/aquality-automation/aquality-tracking-ui/blob/90f313a6fe7b954e9b80b10302edf8bba2167154/src/app/elements/uploader/uploader.element.ts)：auth token、MIME/size filters、credentials 和多个 callbacks；[固定模板](https://github.com/aquality-automation/aquality-tracking-ui/blob/90f313a6fe7b954e9b80b10302edf8bba2167154/src/app/elements/uploader/uploader.element.html) 同时覆盖 select/drop。
- [ghillert/botanic-ng component @ `e824ceaa59a08d938bffb6b4f74ca12576d6c18a`](https://github.com/ghillert/botanic-ng/blob/e824ceaa59a08d938bffb6b4f74ca12576d6c18a/ui/src/app/plant/plant-details.component.ts)：`FileUploader`、`FileItem`、`ParsedResponseHeaders` 和 complete callback；[固定模板](https://github.com/ghillert/botanic-ng/blob/e824ceaa59a08d938bffb6b4f74ca12576d6c18a/ui/src/app/plant/plant-details.component.html) 覆盖 drop/select/events。
- [blueriq/blueriq-material custom uploader @ `4626d39a1d014abf3d96b0338b8299e21e340f69`](https://github.com/blueriq/blueriq-material/blob/4626d39a1d014abf3d96b0338b8299e21e340f69/src/app/modules/file/file-upload/custom-file-uploader.ts)：真实子类、XHR/protected API 和多文件语义；[固定现代模板](https://github.com/blueriq/blueriq-material/blob/4626d39a1d014abf3d96b0338b8299e21e340f69/src/app/modules/file/file-upload/file-upload.component.html) 覆盖 Angular control flow 与 select binding。
- [官方 10.0.0 demo TypeScript](https://github.com/valor-software/ng2-file-upload/blob/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4/apps/demo/src/app/components/file-upload/simple-demo.ts) 和 [模板](https://github.com/valor-software/ng2-file-upload/blob/4fa805d9e1e5ff5bcd642c4c6312424e27185fb4/apps/demo/src/app/components/file-upload/simple-demo.html) 用于核对目标 API/selector 仍然成立。

## OpenRewrite 固定测试参考

测试结构和断言模式同时对照：

- OpenRewrite `8.87.5` tag commit [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，尤其 [JSON ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)。
- 固定的 rewrite-javascript commit [`9e3b820e6a44808b095bb7e3aab670fd67de99a5`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5)，尤其 [ImportTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[JavaScriptParserTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/JavaScriptParserTest.java) 和 [RecipeTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/recipe/RecipeTest.java)。

## spec → recipe → test 映射

| 不兼容规范 | AUTO / MARK | 测试覆盖 |
| --- | --- | --- |
| 工作簿 exact / `^` / `~` | dependency AUTO | 三个源版本、四 section、复杂范围/协议/未列版本反例、workspace/生成目录、幂等 |
| 10.0.0 只开放 root ESM | import AUTO + source MARK | 每个旧物理文件和根导出、alias/type/quote、多 symbol、default/namespace/re-export/require/dynamic 反例、AUTO-before-MARK |
| Angular 21 peer 工具链 | manifest MARK | 12 个 Angular owner、Node 正反边界、RxJS、TS、tslib、override/types、幂等 |
| directives `standalone:false` | TS/template MARK | 两个 runtime directive import、select/drop 各种属性语法、NgModule/standalone 提示、类似属性/注释反例 |
| FileUploader options/queue/callbacks | source MARK | 每个安全/body/filter option、10 个 queue/transport API、12 个 callbacks、credentials、shadow/no-owner 反例 |
| `_transformResponse` 与 internals | source MARK | 两参数 override、protected calls/fields、真实 custom subclass |
| 真实业务行为 | MARK fixtures | Aquality、Botanic、Blueriq 真实固定提交的 TS/HTML 用例 |

独立验证：

```bash
mvn -f rewrite-ng2-file-upload-upgrade/pom.xml clean verify
```

当前共 248 个测试：67 个严格依赖/声明式入口测试、18 个 import AUTO 测试、72 个 manifest MARK 测试、67 个 TypeScript MARK 测试和 24 个模板片段 MARK 测试。
