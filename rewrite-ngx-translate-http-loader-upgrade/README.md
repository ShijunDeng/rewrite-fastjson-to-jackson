# @ngx-translate/http-loader 迁移到 17.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ngx-translate/http-loader`：只处理来源版本 `4.0.0`、`6.0.0`、`7.0.0`、`8.0.0`，目标固定为 `17.0.0`。实现使用 OpenRewrite JSON 与 JavaScript/TypeScript LST，自动修改严格可证明的依赖和同文件 factory/provider，其余兼容性问题留下精确 `SearchResult`。

推荐入口：

```text
com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17
```

## 配方

完整名称均以 `com.huawei.clouds.openrewrite.ngxtranslate.` 为前缀。

| 配方 | 用途 |
| --- | --- |
| `MigrateNgxTranslateHttpLoaderTo17` | 推荐入口：严格升级依赖、迁移确定性 factory，再运行源码和项目审计 |
| `UpgradeNgxTranslateHttpLoaderTo17` | 只升级表格选定的直接依赖声明 |
| `MigrateDeterministicHttpLoaderFactoriesTo17` | 把可证明等价的同文件 `HttpClient` factory/raw provider 改为 `provideTranslateHttpLoader` |
| `AuditNgxTranslateHttpLoader17Source` | 审计构造器、provider、custom loader、路径、缓存、interceptor 与 HTTP 注册 |
| `AuditNgxTranslateHttpLoader17Project` | 审计 core/Angular/TypeScript 配套版本及 JSON 配置 |
| `FindManualHttpLoader17MigrationRisks` | 向后兼容的源码审计入口 |
| `FindHttpLoader17CompanionDependencies` | 向后兼容的项目审计入口 |

## 严格依赖边界

依赖配方只修改文件名精确为 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。允许输入仅为四个来源版本的 exact、caret、tilde 形式：例如 `8.0.0`、`^8.0.0`、`~8.0.0` 均输出为精确 `17.0.0`。

以下内容有意保持不变：`=8.0.0`、`v8.0.0`、范围/通配符、预发布及构建版本、tag、变量、catalog、`workspace:`、npm alias、file/link/git/URL、未列版本、非标量、嵌套伪 `dependencies`、`overrides`、`resolutions`、锁文件、其他 JSON 和相似包名。`node_modules`、`dist`、`build`、`target`、`coverage`、`generated` 等安装/生成目录中的 manifest 与源码也不处理。core、Angular、TypeScript 由项目审计标记，不在本模块中跨软件擅自升级。

## AUTO、MARK 与 NO-OP

| 分类 | 行为 |
| --- | --- |
| **AUTO** | 依赖严格白名单；同一 TypeScript 文件内恰有一个旧 factory、一个 `TranslateModule.forRoot/forChild` raw loader provider、精确 named imports、factory 只有一次 `return new TranslateHttpLoader(http[, literalPrefix[, literalSuffix]])`、provider 精确为 `{ provide: TranslateLoader, useFactory, deps: [HttpClient] }` 时，转换成 `provideTranslateHttpLoader(...)` 并删除已证明无用的 factory/import。 |
| **MARK** | 剩余 `new TranslateHttpLoader`、raw loader provider、functional helper 的嵌套/顺序、custom `TranslateLoader`、动态 prefix/suffix、`useHttpBackend:true`、`enforceLoading:true`、HTTP 注册、私有 deep import、未决依赖、core/Angular/TypeScript 基线和 tsconfig 风险。 |
| **NO-OP** | 跨文件、多 factory/provider、动态路径、额外 factory 逻辑、alias/namespace 自动迁移、非标准 provider 顺序/deps、`TranslateModule` 参数中的嵌套伪 `loader`、普通同名对象、注释/字符串、生成目录、目标公开类型和不在白名单内的依赖。 |

自动转换的限制是刻意的。跨文件 factory 需要模块数据流；动态 URL 依赖部署路径；factory 中的额外语句可能有副作用；provider 顺序会影响最终 `TranslateLoader` token。因此这些场景不能仅凭文本相似度改写。

## 不兼容修改点

| 不兼容点 | v17 行为 | 本模块处理 |
| --- | --- | --- |
| 构造器改为 Angular 注入 | `TranslateHttpLoader` 构造器为零参数，内部通过 `inject()` 获得 config 和 `HttpClient/HttpBackend` | 严格 factory/provider 形状做 `AUTO`；其余 direct `new` 做 `MARK` |
| functional provider | 路径和行为由 `provideTranslateHttpLoader(config)` 提供 | 自动保留静态 prefix/suffix；所有 helper 位置标记 provider 嵌套和覆盖顺序 |
| provider 覆盖顺序 | 后注册的 `TranslateLoader` provider 可能覆盖先注册配置 | raw/provider helper 做 `MARK`，要求确认 root/child scope 与顺序 |
| interceptor 绕过 | `useHttpBackend:true` 使用 `HttpBackend`，绕开 `HttpClient` interceptor | 精确属性做 `MARK`，复核认证、租户头、重试、追踪和错误处理 |
| 强制加载 | `enforceLoading:true` 为请求追加时间戳 | 精确属性做 `MARK`，复核 CDN/浏览器缓存、离线和流量 |
| 动态 URL | prefix/suffix 可决定部署 base、tenant、cache key 与 SSR URL | 非静态配置做 `MARK`；静态字符串不误报动态路径 |
| custom loader 类型 | `getTranslation` 返回 `Observable<TranslationObject>` | `implements/extends TranslateLoader` 和 `getTranslation` 做 `MARK`，要求 strict typecheck |
| HTTP 注册 | NgModule/standalone 可能分别使用 `HttpClientModule`/`provideHttpClient` | 只在使用 HTTP loader 的文件中标记注册点，复核重复注册、features 与 SSR transfer cache |
| Angular baseline | v17 manifest 声明 `@angular/common`、`@angular/core >=16` | 旧版或无法解析的相关依赖做 `MARK` |
| core 配套 | 应与 core 17 的 provider/API 迁移共同验证 | 非 `17.0.0` exact/caret/tilde 的 core 声明做 `MARK` |
| TypeScript/解析配置 | Angular 16+ 要求匹配的 TypeScript 与现代解析方式 | 旧 TypeScript、`strict:false`、classic resolution、内部 paths 做 `MARK` |
| 私有入口 | `@ngx-translate/http-loader/*` 不是稳定公开 API | deep import 做 `MARK` |

## 确定性转换示例

```ts
// before
import { HttpClient } from '@angular/common/http';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

TranslateModule.forRoot({
  loader: {
    provide: TranslateLoader,
    useFactory: HttpLoaderFactory,
    deps: [HttpClient]
  }
});

// after
import { TranslateModule } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

TranslateModule.forRoot({
  loader: provideTranslateHttpLoader({
    prefix: './assets/i18n/',
    suffix: '.json'
  })
});
```

推荐组合配方随后会在 helper 调用上留下 provider-order 标记；这不是转换失败，而是提醒确认整个应用的 provider 组合。

## 官方固定依据

目标实现固定到 ngx-translate/core 的 HTTP loader v17 peeled commit [`e7b83e9`](https://github.com/ngx-translate/core/tree/e7b83e9a495b127ed63378fdd77b458d3caffcc0)：

- [`http-loader.ts`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/http-loader/src/lib/http-loader.ts)：零参数构造、DI config、默认 prefix/suffix、`provideTranslateHttpLoader`、`useHttpBackend` 与 `enforceLoading`；
- [`http-loader/package.json`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/http-loader/package.json)：固定 `17.0.0` 及 Angular `>=16` peer baseline；
- [`translate.providers.ts`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/ngx-translate/src/lib/translate.providers.ts)：loader provider 顺序及 `provideTranslateService` 组合；
- [`translate.module.ts`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/ngx-translate/src/lib/translate.module.ts)：`forRoot/forChild` 仍接受 provider。

本模块按表格要求固定到 v17，不动态追踪上游最新版本。

## 真实公开仓库用例

测试从固定提交提取最小可复现片段；这些仓库是输入快照，不表示其已迁移到 v17。

| 仓库固定提交 | 输入文件 | 已验证效果 |
| --- | --- | --- |
| [ShahidBaig/eSyncMate_V2 `8478a962`](https://github.com/ShahidBaig/eSyncMate_V2/tree/8478a96267fb692985c70e32f5dde0544209d6a5) | [`UI/package.json`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/package.json)、[`app.module.ts`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/app.module.ts) | `^8.0.0 → 17.0.0`；双 literal factory/provider 自动迁移；core 和 provider 顺序得到标记 |
| [Kurarion/Genshin-Calc `c7dd4d85`](https://github.com/Kurarion/Genshin-Calc/tree/c7dd4d850db8523e33302e98d71d9e180605bd4e) | [`package.json`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/package.json)、[`app.module.ts`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/app.module.ts) | `^7.0.0 → 17.0.0`；template literal prefix 自动迁移；旧 Angular/core/TypeScript 得到标记 |
| [FeDi20-03/TuniSalesGateway `40e99bed`](https://github.com/FeDi20-03/TuniSalesGateway/tree/40e99bedf123169767fbcf7200f4e2e0a94eb402) | [`package.json`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/package.json)、[`translation.config.ts`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/src/main/webapp/app/config/translation.config.ts) | `7.0.0 → 17.0.0`；动态 suffix/跨文件边界保持原样并精确标记 |

测试结构参考 OpenRewrite 固定提交 `b3008cc4` 的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)，覆盖 before/after、no-change、参数化边界、`SearchResult`、真实仓库、recipe discovery/validation 和幂等性。当前模块共有 **237 个 JUnit invocation**。

## 使用与验证

先运行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-http-loader-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17
```

检查自动 patch 与全部 `~~>` 标记后再执行 `run`。随后使用工程原包管理器重新生成锁文件，并执行 Angular build、strict TypeScript、SSR/hydration、404/离线、interceptor、缓存、fallback language、快速切换语言和部署子路径测试。

模块验证：

```bash
mvn -f rewrite-ngx-translate-http-loader-upgrade/pom.xml clean verify
```
