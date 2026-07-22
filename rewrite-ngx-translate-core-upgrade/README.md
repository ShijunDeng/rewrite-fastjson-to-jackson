# @ngx-translate/core 迁移到 17.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ngx-translate/core`：只处理来源版本 `11.0.1`、`13.0.0`、`14.0.0`、`15.0.0`，目标固定为 `17.0.0`。目标不是把版本字符串简单改掉，而是同时完成可以安全判定的 Angular/TypeScript 源码迁移，并把依赖上下文才能决定的风险精确标记出来。

推荐直接运行组合配方：

```text
com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17
```

## 配方

| 配方 | 行为 |
| --- | --- |
| `MigrateNgxTranslateCoreTo17` | 推荐入口：严格升级依赖、执行确定性源码迁移、标记人工复核点 |
| `UpgradeNgxTranslateCoreTo17` | 只升级符合表格来源版本边界的直接依赖声明 |
| `MigrateDeterministicNgxTranslateSourceTo17` | 迁移 fallback 术语、公开实现类、服务 getter、配置键和严格匹配的 standalone 组件 |
| `MigrateStandaloneTranslateModuleToPipeAndDirective` | 只处理 `standalone: true` 且仅导入 `TranslateModule` 的组件 |
| `FindManualNgxTranslate17MigrationRisks` | 只产生 `SearchResult`，不猜测 provider、loader、事件、并发或 SSR 的业务语义 |

完整名称均以 `com.huawei.clouds.openrewrite.ngxtranslate.` 为前缀。

## 严格依赖升级边界

`UpgradeNgxTranslateCoreTo17` 使用 JSON LST，不做全文件文本替换。它只修改任意层级 `package.json` 中以下四个直接区段的字符串值：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

允许的声明是四个来源版本的精确值，或安全前缀 `^`、`~`、`=`、`v`、`^v`，输出统一为精确的 `17.0.0`。

它明确保留：未列入表格的版本、复杂 range、预发布/构建元数据、`workspace:`、`npm:` alias、`file:`、`link:`、Git/URL、catalog、对象/数组/null，以及 `overrides`、`resolutions`、`pnpm.overrides`、锁文件和其他 JSON 文件。它也不会隐式升级 `@ngx-translate/http-loader`。

## 不兼容修改点与处理状态

| 不兼容点 | v17 影响 | 本模块行为 | 状态与测试 |
| --- | --- | --- | --- |
| Angular 构建基线提升 | v14 改为 Angular 13/Ivy-only；v15 改为 Angular 16；目标 v17 manifest 声明 `@angular/common`、`@angular/core >=16` | 不猜测 Angular/TypeScript/RxJS 版本，只升级表格指定 core 声明 | **边界保留**；真实依赖快照和 companion-package 不变测试 |
| default → fallback 术语 | v17 推荐 `fallbackLang`、`setFallbackLang()`、`getFallbackLang()`、`onFallbackLangChange`、`FallbackLangChangeEvent`；旧别名在 v17 仍为 deprecated | 服务方法/事件只对 `translate`、`translateService` 及其 `this.` 形式确定性改名；`defaultLanguage` 只在 `TranslateModule.forRoot/forChild` 或 `provideTranslateService` 的配置对象内改为 `fallbackLang` | **自动**；Genshin-Calc、TuniSalesGateway before→after 和无关 receiver/对象负例 |
| 服务属性 getter | `defaultLang`、`currentLang`、`langs` 已 deprecated，公开 getter 更稳定 | 只改 receiver 为 `translate`、`translateService`、`this.translate`、`this.translateService` 的精确属性读取 | **自动且保守**；目标 API 幂等、`config.currentLang` 等负例 |
| 默认实现类重命名 | 目标公开 API 使用 `DefaultMissingTranslationHandler`、`TranslateNoOpCompiler`、`TranslateNoOpLoader` | 对精确类型标识符改名 | **自动**；import 与使用点测试 |
| standalone pipe/directive | v16 起 `TranslatePipe`、`TranslateDirective` 可 standalone 使用 | 仅当同文件 `standalone: true`、import 恰为 `{ TranslateModule }` 时，改为两个 standalone 声明并替换 `imports` 数组项 | **自动且保守**；eSyncMate before→after，NgModule/混合 import 负例 |
| provider 系统重构 | v17 推荐 `provideTranslateService()` 及嵌套的 loader/compiler/parser/missing-handler helpers；`TranslateModule.forRoot/forChild` 在 v17 仍受支持 | 标记 module 配置与 raw provider object，不自动选择 root/child、extend/isolate 或 provider 生命周期 | **人工复核**；Genshin-Calc、TuniSalesGateway marker 测试 |
| HTTP loader 构造方式 | v17 `TranslateHttpLoader` 通过 injection 构造，prefix/suffix 等由 `provideTranslateHttpLoader({...})` 配置 | 标记 `new TranslateHttpLoader(...)`，保留路径、后缀、backend 和 interceptor 语义供人工迁移 | **人工复核**；eSyncMate 真实构造调用 marker 测试 |
| 事件存储改为 Observable | v17 事件为只读 Observable，不能继续对 `onLangChange` 等调用 `.emit()` | 标记四类翻译事件的直接 `.emit()` | **人工复核**；emit marker 与合法 subscribe 保留边界 |
| 自定义插件类型收紧 | v16 起参数/返回值由宽泛 `any` 收紧，自定义 loader/compiler/parser/handler 可能编译失败 | 标记 `implements`/`extends` 公开插件类型 | **人工复核**；自定义 loader marker 测试 |
| `getTranslation()` deprecated | v16 起不应依赖底层加载调用 | 标记直接调用，由业务选择 `get`、`instant`、`stream` 或 loader API | **人工复核**；调用 marker 测试 |
| `use()` 并发语义 | v16 修正为最后一次请求的语言生效，快速切换/路由初始化可能改变观察结果 | 标记明确的 `translate.use()` / `translateService.use()` 调用 | **人工复核**；并发调用 marker 测试 |
| SSR/browser 全局 | v17 的注入与初始化方式要求重新核对服务端执行边界 | ngx-translate 文件中出现 `window`、`document`、`navigator`、`isPlatformBrowser`、`afterNextRender` 时标记 | **人工复核**；browser global marker 测试 |
| 私有深层 import | `@ngx-translate/core/*` 不属于稳定的顶层公开入口 | 标记 ESM/CJS 深层 import | **人工复核**；固定深层 import marker 测试 |
| 剩余 legacy 配置/API | `useDefaultLang`、未能安全定位的 `defaultLanguage`，以及非标准 receiver 的 legacy 方法、事件或属性，需要上下文判断 | 自动阶段不猜测；风险阶段标记 | **人工复核**；保留与 marker 双边界测试 |

`~~>` 是 OpenRewrite 在 dry-run 结果中插入的 `SearchResult` 标记。例如：

```ts
const loader = ~~>new TranslateHttpLoader(http, 'assets/i18n/', '.json');
~~>TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: loaderFactory } });
```

## 自动迁移示例

```ts
// before
import { DefaultLangChangeEvent, TranslateModule } from '@ngx-translate/core';
translateService.setDefaultLang('en');
const current = translateService.currentLang;
TranslateModule.forRoot({ defaultLanguage: 'en' });

// after
import { FallbackLangChangeEvent, TranslateModule } from '@ngx-translate/core';
translateService.setFallbackLang('en');
const current = translateService.getCurrentLang();
TranslateModule.forRoot({ fallbackLang: 'en' });
```

standalone 迁移仅在可确定时执行：

```ts
// before
import { TranslateModule } from '@ngx-translate/core';
@Component({ standalone: true, imports: [CommonModule, TranslateModule] })

// after
import { TranslatePipe, TranslateDirective } from '@ngx-translate/core';
@Component({ standalone: true, imports: [CommonModule, TranslatePipe, TranslateDirective] })
```

## 官方固定依据

为避免上游默认分支继续变化，源码依据固定在 tag 对应 commit：

- 来源标签：[v11.0.1 / `920b95df`](https://github.com/ngx-translate/core/tree/920b95df45e98a097ef11824ba741bb0a9025b92)、[v13.0.0 / `efcb4f43`](https://github.com/ngx-translate/core/tree/efcb4f43a645d9ac630aae8e50b60cc883e675fd)、[v14.0.0 / `3a4c7ee9`](https://github.com/ngx-translate/core/tree/3a4c7ee9e56a86f72a42dd3590122b0db7667779)、[v15.0.0 / `9c3244d3`](https://github.com/ngx-translate/core/tree/9c3244d3e36d4419306877944ccd294de84c61f0)
- 目标标签：[v17.0.0 / `4500e0b8`](https://github.com/ngx-translate/core/tree/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53)
- 目标 peer dependencies：[v17 package.json](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/package.json)
- fallback aliases、getter、事件和 `use()`：[v17 TranslateService](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/src/lib/translate.service.ts)
- provider 结构与 deprecated 配置：[v17 translate.providers.ts](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/src/lib/translate.providers.ts)
- `TranslateModule.forRoot/forChild` 仍存在：[v17 translate.module.ts](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/src/lib/translate.module.ts)
- HTTP loader injection/configuration：[v17 http-loader.ts](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/http-loader/src/lib/http-loader.ts)
- 版本说明：[v14 release（Angular 13、Ivy-only）](https://github.com/ngx-translate/core/releases/tag/v14.0.0)、[v15 release（Angular 16）](https://github.com/ngx-translate/core/releases/tag/v15.0.0)、[v16 release（standalone、类型、并发、deprecation）](https://github.com/ngx-translate/core/releases/tag/v16.0.0)、[v17 FAQ](https://ngx-translate.org/v17/resources/faq/)

说明：当前上游可能已有更新主版本；本模块按表格要求有意固定到 v17，不把“最新版本”作为动态目标。目标 manifest 没有声明 RxJS peer dependency，因此本模块也不会凭空改写 RxJS。

## 真实公开仓库用例

测试从以下公开仓库的固定提交提取并缩减，commit 链接用于确保样例可复现；这些仓库只是输入代码快照，不表示它们已经迁移到 v17：

| 仓库固定提交 | 实际输入 | 覆盖效果 |
| --- | --- | --- |
| [ShahidBaig/eSyncMate_V2 `8478a962`](https://github.com/ShahidBaig/eSyncMate_V2/tree/8478a96267fb692985c70e32f5dde0544209d6a5) | [`UI/package.json`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/package.json)、[standalone component](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/alert-configuration/alert-configuration.component.ts)、[`app.module.ts`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/app.module.ts) | `^15.0.0 → 17.0.0`；TranslateModule → pipe/directive；HTTP loader 与 forRoot marker |
| [Kurarion/Genshin-Calc `c7dd4d85`](https://github.com/Kurarion/Genshin-Calc/tree/c7dd4d850db8523e33302e98d71d9e180605bd4e) | [`package.json`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/package.json)、[`app.module.ts`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/app.module.ts)、[`language.service.ts`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/shared/service/language.service.ts) | `^14.0.0 → 17.0.0`；`defaultLanguage → fallbackLang`；getter 迁移；raw loader marker |
| [FeDi20-03/TuniSalesGateway `40e99bed`](https://github.com/FeDi20-03/TuniSalesGateway/tree/40e99bedf123169767fbcf7200f4e2e0a94eb402) | [`package.json`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/package.json)、[`translation.module.ts`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/src/main/webapp/app/shared/language/translation.module.ts) | `14.0.0 → 17.0.0`；setter 迁移；missing handler provider marker |

测试风格遵循 OpenRewrite 固定提交 `b3008cc4` 的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)：使用 before/after、no-change、recipe discovery/validation 和 `SearchResult` marker 断言。当前共运行 74 个 JUnit invocation，包含参数化来源版本、声明格式、负边界、真实源码迁移、风险标记、幂等性和组合配方。

## 使用与验证

先 dry-run 推荐组合配方：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17
```

确认自动 patch 和全部 `~~>` 标记后，将 `dryRun` 改为 `run`。然后使用工程原有包管理器重新生成锁文件，并执行 Angular build、TypeScript strict 编译、单元测试、SSR 测试和“连续快速切换语言”的端到端测试。若同时升级 HTTP loader，应显式选择与 core 17 匹配的版本，并单独审核其变更。

本模块验证：

```bash
mvn -f rewrite-ngx-translate-core-upgrade/pom.xml clean verify
```
