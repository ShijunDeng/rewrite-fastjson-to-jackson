# @angular/router 迁移到 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/router`。只处理表格中**明确可见**的源版本：

```text
10.0.14, 10.2.5, 11.2.14, 12.2.10, 12.2.13, 12.2.14,
12.2.16, 12.2.17, 13.1.3, 13.2.6
```

表格把最后一格折叠为 `13.2.6 ...（共28个版本）`；本模块只采用其中明确可见的 `13.2.6`，不会猜测省略的其他版本。每个可见版本接受 npm exact、单一 caret 或单一 tilde 声明（例如 `12.2.17`、`^12.2.17`、`~12.2.17`），并收敛为精确目标 `20.3.26`。复杂范围、协议、变量、tag、alias、中央版本 owner 和锁文件不会被自动改写。

## 配方

完整应用迁移推荐使用：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularRouterTo20_3_26
```

它组合严格依赖升级、Angular 官方可确定的 Router TypeScript/config migration，以及对需要导航业务语义的位置写入精确 `SearchResult`。仅需检查依赖声明时可使用：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularRouterTo20_3_26
```

Angular framework 包必须锁步到同一 patch。应从当前版本开始逐个大版本运行 `ng update @angular/core@<major> @angular/cli@<major>`，接受并验证每一阶段的官方 migrations，再用本模块收敛表格目标；不能把一次依赖字符串替换当作完整 Router 迁移。

## AUTO / MARK / NO-OP 与测试证明

`AUTO` 表示静态信息足以安全改写；`MARK` 表示精确标出需要业务决策的 AST/JSON/HTML 节点；`NO-OP` 表示有意保持原文以防误改。

| 不兼容点或边界 | 状态 | 配方行为 | 对应测试 |
| --- | --- | --- | --- |
| XLSX 可见 10 个版本的 exact/`^`/`~` 单值 | AUTO | 只改 `package.json` 四个根级直接依赖区，并收敛为精确 `20.3.26` | `AngularRouterDependencyTest.upgradesEveryVisibleExactCaretAndTildeDeclaration`（30 组）、`supportsNpmYarnAndPnpmWorkspacePackageLocations` |
| 比较/并集/hyphen 等复杂范围，workspace/protocol/变量/tag/alias、未列出或较新版本 | MARK / NO-OP | 依赖 recipe 保持原值；推荐 recipe 在实际声明标记约束选择和锁文件重建 | `leavesComplexRangesUntouched`、`leavesProtocolsVariablesTagsUnlistedNewerAndTargetUntouched`、`marksComplexConstraintCentralOwnerAndLockstepToolchain` |
| catalog/overrides/resolutions/pnpm 与 nested metadata | MARK / NO-OP | 不误当直接依赖；中央 owner 精确标记原子升级要求 | `leavesCentralOwnersAndNestedMetadataUntouched`、`marksComplexConstraintCentralOwnerAndLockstepToolchain` |
| core/common/platform-browser 等 peers 与 framework 包必须同 patch | MARK | 标记未对齐的 sibling Angular 声明 | `apacheNifiPinnedFixtureMigratesOnlyRouter`、`marksComplexConstraintCentralOwnerAndLockstepToolchain` |
| Node `^20.19.0`/`^22.12.0`/`>=24`、TypeScript `>=5.8 <6`、RxJS 支持范围 | MARK | 只在包含 Router 的 package 标记真实声明，避免普通 Node 服务误报 | `marksComplexConstraintCentralOwnerAndLockstepToolchain`、`doesNotMarkSupportedSingleDeclarationsOrUnrelatedNodePackages` |
| `Router.getCurrentNavigation()` 废弃，改为 `currentNavigation` signal | AUTO / MARK | 仅对显式 `Router` 类型或 `inject(Router)` 变量按官方 migration 改名；随后标记 active-navigation 生命周期与 signal 读取决策；`any` 保持原文 | `followsOfficialAngularTypedConstructorCurrentNavigationMigration`、`migratesInjectRouterFromPinnedOktaStyleFixture`、`conservativelyLeavesAnyAndSameNamedNonAngularRouter`、`marksNavigationEventsAndCurrentNavigationLifecycle` |
| `relativeLinkResolution` 被移除 | AUTO / MARK | 仅从 attributed `RouterModule.forRoot` option object 删除；保留 forRoot marker，要求回归相对链接/空路径 | `removesLegacyRelativeLinkResolutionFromPinnedRealFixture`、`doesNotRemoveSamePropertyOutsideAttributedRouterForRoot`、`marksPinnedRelativeLinkResolutionAndRouterModuleProviderChoices` |
| `initialNavigation: 'enabled'` 被 `enabledBlocking` 取代 | AUTO / MARK | 精确替换已移除的字面量；Router option marker提示检查 SSR/客户端时序 | `migratesRemovedEnabledInitialNavigationValue`、`marksProvideRouterFeaturesAndTestProviderOrder` |
| `RouterLinkWithHref` 合并到 `RouterLink` | AUTO / MARK | 已有 alias 时只改 imported property 并保留本地 alias；mixed/unaliased import 精确标记，避免未归因地重命名本地符号 | `migratesAliasedRouterLinkWithHrefImportWithoutTouchingLocalUses`、`marksPinnedWebDbRouterLinkWithHrefFixture` |
| class/InjectionToken guards 与 resolvers、`CanLoad`/`CanMatch`、functional guards | MARK | 标记 API import 和 route property；要求确认 DI context、首个 emission、empty/error/cancel 和“不匹配后继续匹配” | `marksPinnedCanLoadGuardImportAndRouteProperty`、`marksDynamicLazyLoadingAndRouteProviders` |
| guard/resolver 返回 `UrlTree`/`RedirectCommand`，resolver 只取首值且空流取消导航 | MARK | 标记 guard/resolver、UrlTree/RedirectCommand import 和配置成员 | `marksUrlTreeRedirectCommandAndReuseStrategyImports`、`marksPinnedCanLoadGuardImportAndRouteProperty` |
| `redirectTo` 可为函数，绝对 redirect 可继续 redirect，replace/history 语义变化 | MARK | 在 `redirectTo` member 标记 loop、参数、outlet、history、SSR 与鉴权决策 | `marksRedirectAndPathMatchChoices` |
| 字符串式 `loadChildren` 被移除；动态 lazy loader 进入 route injection context | MARK | 分别标记旧字符串与函数属性；不猜 module/export 或 chunk 路径 | `marksPinnedLegacyStringLazyLoadingAtTheProperty`、`marksDynamicLazyLoadingAndRouteProviders` |
| `Route.pathMatch` 类型收紧，空路径/redirect/named outlet 规则跨版本修正 | MARK | 标记 `pathMatch` 和 redirect；要求保留 `Routes`/`Route` attribution | `marksRedirectAndPathMatchChoices` |
| route-level providers/lazy injectors 不再从 RouterOutlet component provider 继承 | MARK | 标记 route `providers` 成员 | `marksDynamicLazyLoadingAndRouteProviders` |
| RouteReuseStrategy、detached handle、title/url handling 与 Router writable properties | MARK | 标记 reuse import；在已归因 Router receiver 上标记移除/废弃 property | `marksUrlTreeRedirectCommandAndReuseStrategyImports`、`marksRemovedWritableRouterProperties` |
| `navigate`/`navigateByUrl`/`createUrlTree`、相对 URL、query/matrix/named outlet | MARK | 在已归因 Router call 标记历史、并发导航、取消、promise rejection 与完整 UrlTree 断言 | `marksNavigationAndUrlCreationCalls` |
| NavigationCancel/Error/Skipped、Scroll 与并发/redirect 生命周期 | MARK | 标记导航事件 import 和 current navigation 读取 | `marksNavigationEventsAndCurrentNavigationLifecycle` |
| `RouterModule.forRoot/forChild` 与 `provideRouter`/`with*` provider features | MARK | 在 call 精确标记 initial navigation、hash、scrolling、preloading、error handler、component input 与 provider order | `marksPinnedRelativeLinkResolutionAndRouterModuleProviderChoices`、`marksProvideRouterFeaturesAndTestProviderOrder` |
| `RouterTestingModule` 废弃，测试迁移为 `provideRouter`/`RouterTestingHarness` | MARK | 标记 testing import；test 文件中的 provider call使用专门的顺序/异步语义提示 | `marksPinnedNgRxRouterTestingModuleFixture`、`marksProvideRouterFeaturesAndTestProviderOrder` |
| SSR/prerender blocking initial navigation 与 client hydration/event replay | MARK | 标记 server provider call、hydration call及 workspace server/ssr/prerender target | `marksServerRoutingAndHydrationInteractions`、`marksWorkspaceBuilderDeploymentAndSsrTargets` |
| baseHref/deployUrl/custom builder 影响 browser history、lazy chunk、navigation fallback | MARK | 在 `angular.json`/`workspace.json` 的具体值上标记部署验证 | `marksWorkspaceBuilderDeploymentAndSsrTargets` |
| 模板 routerLink/RouterLinkActive/router-outlet/query/fragment | MARK | 在每个 HTML attribute/tag snippet 标记相对链接、active match、outlet activation/reuse、query/fragment 语义 | `AngularRouterTemplateRiskTest` 的 7 个测试，含固定 angular-gridster2 fixture |
| 非 Angular Router、`any` receiver、非 HTML、无 Router package 的 Node 服务 | NO-OP | import/type/path/package scope 防止同名 API 误改误报 | `conservativelyLeavesAnyAndSameNamedNonAngularRouter`、`ignoresSimilarTextOutsideHtmlFiles`、`doesNotMarkSupportedSingleDeclarationsOrUnrelatedNodePackages` |
| 多次执行 | NO-OP | dependency/source/config 第二轮无 diff，template markers 不重叠 | `directSectionsMoveTogetherAndRecipeIsIdempotent`、`deterministicRouterMigrationIsIdempotent`、`templateMarkersRemainNonOverlappingAcrossCycles` |

## 固定官方依据与真实仓用例

Angular 语义固定到 `v20.3.26` release cut [`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa)：

- [v14–v20 完整 CHANGELOG](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/CHANGELOG.md)；
- [官方 router-current-navigation migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/router-current-navigation/router_current_navigation_migration.ts)及其[测试](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/test/router_current_navigation_spec.ts)；
- [`@angular/router` package metadata](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/router/package.json)；
- CHANGELOG 中固定的官方 [`relativeLinkResolution` migration commit](https://github.com/angular/angular/commit/7bee28d037a8a21a7440293b3e8c118cc93ec8c1) 与 [`RouterLinkWithHref` migration commit](https://github.com/angular/angular/commit/16c8f55663c30270fcd647b1a8a20ddbc8923349)。

OpenRewrite TypeScript recipe/test写法固定参考 [`openrewrite/rewrite-javascript@b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite-javascript/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 的 [JavaScript/TypeScript tests](https://github.com/openrewrite/rewrite-javascript/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/src/test/java/org/openrewrite/javascript)。

公开仓 fixture 全部固定 commit：

| 固定仓库/文件 | 提取内容 | 用例 |
| --- | --- | --- |
| [apache/nifi@59cff970](https://github.com/apache/nifi/blob/59cff970ca8b98ee51ae4418cf4de6830fa28c37/nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json) | 精确 Router 11.2.14 与 sibling Angular packages | dependency before→after + siblings no-op |
| [okta/okta-angular@d437b3a4](https://github.com/okta/okta-angular/blob/d437b3a4cf13190f0adddd395edca9cb2d7a9d8b/lib/src/okta/okta.guard.ts) | `inject(Router)` + `getCurrentNavigation()` | currentNavigation before→after |
| [aitboudad/ngx-loading-bar@f788e933](https://github.com/aitboudad/ngx-loading-bar/blob/f788e93335a20bba29a366a5974bc8ccac19c465/packages/router/src/router.service.ts) | `router: any` current-navigation compatibility wrapper | conservative no-op + lifecycle marker |
| [ngrx/platform@7f22a8e8](https://github.com/ngrx/platform/blob/7f22a8e81747c1a6c236cea7adef9983686cd9d3/modules/router-store/spec/utils.ts) | RouterTestingModule、guards、lazy routes、redirect | testing/route markers |
| [WebDB-App/app@297d200d](https://github.com/WebDB-App/app/blob/297d200df0ccfdd47781fe7ea4ea16dad0955f7c/front/src/shared/shared.module.ts) | mixed RouterLink/RouterLinkWithHref import | precise marker |
| [mathisGarberg/angular-folder-structure@9d554b33](https://github.com/mathisGarberg/angular-folder-structure/blob/9d554b33144f2a90f56f80e90cd65c32be0424d5/src/app/app-routing.module.ts) | `relativeLinkResolution:'legacy'` 与 dynamic lazy routes | config AUTO + forRoot marker |
| [hantsy/spring-microservice-sample@f0051138](https://github.com/hantsy/spring-microservice-sample/blob/f005113835de3e46eccf51ddc97cabba51310315/ui/src/app/core/load-guard.ts) | class `CanLoad` guard | guard marker |
| [vladotesanovic/angular2-express-starter@81d08fb3](https://github.com/vladotesanovic/angular2-express-starter/blob/81d08fb3eea7b154302403a8c07a2e4b777d0bc4/src/app/app.router.ts) | 字符串 `loadChildren` | removed lazy syntax marker |
| [tiberiuzuld/angular-gridster2@ff33c128](https://github.com/tiberiuzuld/angular-gridster2/blob/ff33c1283c1718db6e5623dd143260a004053847/src/app/app.html) | routerLink、RouterLinkActive、router-outlet | HTML snippet markers |

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-router-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularRouterTo20_3_26
```

审查 patch 和所有 `SearchResult` 后再执行 `run`。用原包管理器重建锁文件，并逐阶段运行 production build、RouterTestingHarness、guard/resolver/redirect、lazy chunk、history/back-forward、scroll、SSR/prerender/hydration 与端到端测试。

模块验证：

```bash
mvn -pl rewrite-angular-router-upgrade -am clean verify
```
