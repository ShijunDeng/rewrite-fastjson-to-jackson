# @ngx-translate/http-loader 17.0.0 迁移

本模块对应 `开源软件升级.xlsx` 中 `@ngx-translate/http-loader` 的 `4.0.0`、`6.0.0`、`7.0.0`、`8.0.0` → `17.0.0`。README 是迁移规范；实际修改和检测均由下列 OpenRewrite 配方执行。

推荐入口：

```text
com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17
```

它依次运行严格依赖升级、确定性 TypeScript 迁移和两组兼容性检测。`~~>` 是 OpenRewrite 的 `SearchResult` 标记，表示“已发现、需复核”，不表示该问题已自动修复。

## 配方能力

| 不兼容点 | 配方行为 | 自动化状态 | 测试依据 |
| --- | --- | --- | --- |
| 表格所列旧版本升级到 17.0.0 | 只修改任意层级 `package.json` 的四个直接依赖区；只接受精确值及 `^`、`~`、`=`、`v`、`^v` 前缀 | **自动迁移** | 4 个版本 × 4 个依赖区 × 3 种常用前缀，另有 6 个显式前缀用例 |
| 范围、协议、alias、lockfile、override、非标量和未列版本不能被误改 | 保留 `>=4 <9`、`workspace:`、`npm:`、git/file/URL、数组/对象、lockfile 和新版本 | **安全门禁** | 28 组负例 |
| v17 `TranslateHttpLoader` 构造器不再接收 `HttpClient/prefix/suffix` | 当 factory 与 `TranslateModule.forRoot/forChild` 的 raw provider 位于同一文件、各唯一、参数为零到两个静态字符串时，改成 `provideTranslateHttpLoader(...)` | **自动迁移（严格匹配）** | eSyncMate 与 Genshin-Calc 固定提交 before→after；默认、prefix-only、prefix+suffix 三种形态 |
| 旧 factory 删除后遗留导入 | 将 HTTP loader 命名导入替换为 `provideTranslateHttpLoader`；仅在文件中确已无使用时移除 `HttpClient`、`TranslateLoader`，其余 specifier 保留 | **自动迁移** | 混合导入、仍被自定义 loader 使用、幂等用例 |
| factory 在另一文件，或 prefix/suffix 是环境表达式、模板插值 | 不猜测跨文件数据流和部署路径；标记剩余 `new TranslateHttpLoader(...)` 与 raw `{provide,useFactory,deps}` | **检测** | TuniSalesGateway 两文件真实用例；动态环境值负例 |
| 自定义 `TranslateLoader.getTranslation()` 的 v17 `TranslationObject` 返回契约 | 标记 `implements TranslateLoader` 与 `getTranslation(...)`，供 strict TypeScript 编译后修正类型和错误分支 | **检测** | 自定义 loader marker 用例 |
| `useHttpBackend: true` 会绕过 interceptor | 标记显式启用点，要求确认认证、租户头、重试、监控与错误处理是否应被绕过 | **检测** | backend marker 用例 |
| `enforceLoading: true` 每次增加时间戳、绕过浏览器/CDN 缓存 | 标记显式启用点，要求评估流量与缓存策略 | **检测** | cache marker 用例 |
| prefix 依赖环境、浏览器对象、base URL 或部署子路径 | 标记 `provideTranslateHttpLoader({...})` 中的环境/浏览器路径表达式 | **检测** | 动态 prefix marker 与静态 literal 负例 |
| v17 通过 Angular DI 获取 HTTP 服务 | 标记 `HttpClientModule`/`provideHttpClient()` 注册点，供确认 NgModule 或 standalone 根环境确实提供 HTTP | **检测** | 两种 HTTP 注册方式 marker 用例 |
| loader 与 core/Angular 的配套版本 | 标记四个依赖区中的 `@ngx-translate/core`、`@angular/core`、`@angular/common`；loader 17 的 Angular peer baseline 为 `>=16` | **检测** | companion JSON marker 用例 |

确定性转换示例：

```ts
// before
export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}
TranslateModule.forRoot({
  loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory, deps: [HttpClient] }
});

// after
TranslateModule.forRoot({
  loader: provideTranslateHttpLoader({ prefix: './assets/i18n/', suffix: '.json' })
});
```

该转换保留现有 NgModule 架构，不擅自改造成 standalone bootstrap。跨文件 factory、动态 prefix、多个候选 factory 或非标准 provider 顺序会保留原代码并由检测配方标记。

## 子配方

| 配方 | 作用 |
| --- | --- |
| `UpgradeNgxTranslateHttpLoaderTo17` | 严格升级表格所列依赖声明 |
| `MigrateDeterministicHttpLoaderFactoriesTo17` | 转换可证明等价的同文件 factory/provider，并清理确定无用的导入 |
| `FindManualHttpLoader17MigrationRisks` | 检测未转换构造/provider、自定义契约、HTTP、路径、缓存和 interceptor 风险 |
| `FindHttpLoader17CompanionDependencies` | 检测 core 与 Angular 配套依赖 |
| `MigrateNgxTranslateHttpLoaderTo17` | 推荐组合入口 |

## 依据与真实样本

目标 API 固定在 ngx-translate/core 的 v17.0.0 提交 [`e7b83e9`](https://github.com/ngx-translate/core/tree/e7b83e9a495b127ed63378fdd77b458d3caffcc0)：

- [`http-loader.ts`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/http-loader/src/lib/http-loader.ts) 证明无参 DI 构造、默认 prefix/suffix、`provideTranslateHttpLoader`、`useHttpBackend` 和 `enforceLoading` 行为；
- [`http-loader/package.json`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/http-loader/package.json) 固定 17.0.0 及 Angular `>=16` peer baseline；
- [`translate.module.ts`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/ngx-translate/src/lib/translate.module.ts) 与 [`translate.providers.ts`](https://github.com/ngx-translate/core/blob/e7b83e9a495b127ed63378fdd77b458d3caffcc0/projects/ngx-translate/src/lib/translate.providers.ts) 证明 NgModule 配置仍接受 provider。

测试从以下公共仓库的固定提交抽取并缩减，保留真实 import、factory、路径和 provider 结构：

- [ShahidBaig/eSyncMate_V2 `8478a962`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/app.module.ts)：双参数 factory，实际执行 before→after；
- [Kurarion/Genshin-Calc `c7dd4d85`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/app.module.ts)：template literal prefix-only factory，实际执行 before→after；
- [FeDi20-03/TuniSalesGateway `40e99bed`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/src/main/webapp/app/config/translation.config.ts)：动态 suffix 和跨文件 provider，验证不会猜测并产生 marker。

测试风格遵循 OpenRewrite 官方固定输入/输出和 `SearchResult` 断言方式，不把 no-op 当作真实迁移测试。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-http-loader-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17
```

确认 patch 和 `~~>` 点位后再运行 `run`。随后用项目原包管理器重新生成 lockfile，并执行 Angular build、strict TypeScript、SSR、404/离线、interceptor、fallback language、快速切换语言与部署子路径测试。

模块自身验证：

```bash
mvn -pl rewrite-ngx-translate-http-loader-upgrade -am clean verify
```
