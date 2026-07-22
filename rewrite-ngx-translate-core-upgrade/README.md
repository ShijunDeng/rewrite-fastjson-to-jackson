# @ngx-translate/core 迁移到 17.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ngx-translate/core`。它仅把表格列出的 `11.0.1`、`13.0.0`、`14.0.0`、`15.0.0` 迁移到固定目标 `17.0.0`，并使用 JavaScript/TypeScript、JSON 的 OpenRewrite LST 完成可证明安全的源码改写和精确风险标记。

推荐入口：

```text
com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17
```

## 配方

完整名称均以 `com.huawei.clouds.openrewrite.ngxtranslate.` 为前缀。

| 配方 | 用途 |
| --- | --- |
| `MigrateNgxTranslateCoreTo17` | 推荐入口：严格升级依赖、执行确定性源码改写，再运行源码和项目审计 |
| `UpgradeNgxTranslateCoreTo17` | 只升级表格选定的直接依赖声明 |
| `MigrateDeterministicNgxTranslateSourceTo17` | 只改写由 import 和 `TranslateService` 注入关系证明的公开符号、方法及事件 |
| `AuditNgxTranslate17Source` | 标记 TypeScript/JavaScript 中的 provider、loader、事件、插件、SSR 等风险 |
| `AuditNgxTranslate17Project` | 标记 manifest、Angular/TypeScript 基线及 tsconfig 风险 |
| `FindManualNgxTranslate17MigrationRisks` | 为兼容已有调用保留的两类审计聚合入口 |

## 严格依赖边界

`UpgradeNgxTranslateCoreTo17` 只修改文件名精确为 `package.json` 的下列直接区段：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

唯一允许的输入是四个来源版本的精确、caret 或 tilde 形式。例如 `15.0.0`、`^15.0.0`、`~15.0.0` 均输出为 `17.0.0`。

以下内容有意不自动修改：`=15.0.0`、`v15.0.0`、范围/通配符、预发布及构建版本、tag、变量、catalog、`workspace:`、npm alias、file/link/git/URL、未列版本、非标量值、`overrides`、`resolutions`、锁文件和相似包名。`@ngx-translate/http-loader` 也不会被隐式升级，而是由审计配方标记兼容性边界。

## AUTO、MARK 与 NO-OP

| 分类 | 处理内容 |
| --- | --- |
| **AUTO** | `DefaultLangChangeEvent → FallbackLangChangeEvent`、`FakeMissingTranslationHandler → DefaultMissingTranslationHandler`、`TranslateFakeCompiler → TranslateNoOpCompiler`、`TranslateFakeLoader → TranslateNoOpLoader`；在类型化构造注入或 `inject(TranslateService)` 证明的 receiver 上，将 `setDefaultLang()`、`getDefaultLang()`、`onDefaultLangChange` 改为 fallback API。支持 import alias。 |
| **MARK** | `TranslateModule.forRoot/forChild`、standalone 组件中的 `TranslateModule`、`provideTranslateService`、raw provider 对象、`new TranslateHttpLoader`、`defaultLanguage/useDefaultLang`、deprecated service 属性、`getTranslation()`、`use()`、事件 `.emit()`、自定义插件、深层 import、浏览器全局、Angular/TypeScript 基线、HTTP loader、tsconfig strict/module resolution。 |
| **NO-OP** | 无 `@ngx-translate/core` 类型证据的同名方法/属性、普通配置对象、事件订阅、目标 v17 API、standalone import 的具体能力选择、复杂或非白名单依赖声明、锁文件。 |

自动迁移不会把 `defaultLang/currentLang/langs` 属性擅自改成 getter，也不会把 `defaultLanguage` 擅自改成 `fallbackLang`，因为初始化时序、null 语义、provider 所有权和 root/child scope 需要业务上下文。它也不会把 standalone 组件的 `TranslateModule` 自动展开成 pipe/directive；模板到底使用哪种能力必须由项目确认。

## 不兼容修改点

| 不兼容点 | v17 影响 | 本模块处理 |
| --- | --- | --- |
| Angular 构建基线 | 目标 manifest 声明 `@angular/common`、`@angular/core >=16` | 对旧版或无法解析的 Angular 依赖做 `MARK`，不跨软件擅自升级 |
| default → fallback 术语 | v17 使用 fallback 命名，旧名 deprecated | import 和绑定证明的 service 方法/事件做 `AUTO`；配置键和属性做 `MARK` |
| 默认实现类改名 | handler/compiler/loader 的公开默认实现采用新名称 | 精确的 root package named import 及其引用做 `AUTO` |
| standalone pipe/directive | pipe/directive 可独立导入 | standalone 中使用 `TranslateModule` 做 `MARK`，由模板能力决定替换项 |
| provider 系统 | functional provider 的顺序、嵌套 helper、root/child、isolate/extend 会影响行为 | module/provider 调用及 raw provider 键做 `MARK` |
| HTTP loader | v17 采用注入和 `provideTranslateHttpLoader` 配置 | constructor 与 manifest companion dependency 做 `MARK` |
| 服务属性 deprecated | `defaultLang/currentLang/langs` 应转向 getter | 只在 proven `TranslateService` receiver 上做 `MARK` |
| 事件只读 | 翻译事件是 readonly Observable，应用不应 `.emit()` | 四种事件的 proven receiver 写入做 `MARK`；合法订阅保持不变 |
| 自定义插件类型收紧 | loader/compiler/parser/handler 类型契约更严格 | `implements/extends` 做 `MARK`，要求 strict typecheck |
| `getTranslation()` deprecated | 不应依赖底层直接加载 | proven receiver 调用做 `MARK` |
| `use()` 并发语义 | 快速调用时最后一次请求生效 | proven receiver 调用做 `MARK`，要求测试初始化和连续切换 |
| SSR/hydration | 浏览器 API 和初始化时点可能在服务端执行 | ngx-translate 源文件内的相关全局/钩子做 `MARK` |
| 私有深层 import | `@ngx-translate/core/*` 不是稳定公开入口 | 做 `MARK`，改用 root public export |
| TypeScript/解析配置 | Angular 16+ 需要匹配的 TypeScript 和现代解析方式 | 旧 TypeScript、`strict:false`、classic resolution、内部 paths 做 `MARK` |

## 自动迁移示例

只有 receiver 绑定可证明时才自动修改：

```ts
// before
import {
  DefaultLangChangeEvent,
  TranslateService as I18n
} from '@ngx-translate/core';

class LanguageService {
  constructor(private translate: I18n) {}
  initialize(): DefaultLangChangeEvent {
    this.translate.setDefaultLang('en');
    return this.translate.onDefaultLangChange;
  }
}

// after
import {
  FallbackLangChangeEvent,
  TranslateService as I18n
} from '@ngx-translate/core';

class LanguageService {
  constructor(private translate: I18n) {}
  initialize(): FallbackLangChangeEvent {
    this.translate.setFallbackLang('en');
    return this.translate.onFallbackLangChange;
  }
}
```

需要业务判断的代码保持原样并带 `SearchResult`：

```ts
const current = this.translate./*~~(deprecated property...)~~>*/currentLang;
/*~~(provider scope/order...)~~>*/TranslateModule.forRoot({
  /*~~(legacy default-language config...)~~>*/defaultLanguage: 'en'
});
```

## 官方固定依据

为避免上游默认分支变化，研究和测试固定到 tag 对应 commit：

- 来源：[v11.0.1 / `920b95df`](https://github.com/ngx-translate/core/tree/920b95df45e98a097ef11824ba741bb0a9025b92)、[v13.0.0 / `efcb4f43`](https://github.com/ngx-translate/core/tree/efcb4f43a645d9ac630aae8e50b60cc883e675fd)、[v14.0.0 / `3a4c7ee9`](https://github.com/ngx-translate/core/tree/3a4c7ee9e56a86f72a42dd3590122b0db7667779)、[v15.0.0 / `9c3244d3`](https://github.com/ngx-translate/core/tree/9c3244d3e36d4419306877944ccd294de84c61f0)
- 目标：[v17.0.0 / `4500e0b8`](https://github.com/ngx-translate/core/tree/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53)
- [v17 peer dependencies](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/package.json)
- [v17 TranslateService API](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/src/lib/translate.service.ts)
- [v17 functional providers](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/src/lib/translate.providers.ts)
- [v17 TranslateModule](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/ngx-translate/src/lib/translate.module.ts)
- [v17 HTTP loader](https://github.com/ngx-translate/core/blob/4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53/projects/http-loader/src/lib/http-loader.ts)
- 版本说明：[v14](https://github.com/ngx-translate/core/releases/tag/v14.0.0)、[v15](https://github.com/ngx-translate/core/releases/tag/v15.0.0)、[v16](https://github.com/ngx-translate/core/releases/tag/v16.0.0)、[v17 FAQ](https://ngx-translate.org/v17/resources/faq/)

本模块有意固定到 v17，而不是动态追踪上游最新版本。

## 真实公开仓库用例

测试从固定提交提取最小可复现片段。这些仓库是输入快照，不表示其已迁移到 v17。

| 仓库固定提交 | 输入文件 | 已验证效果 |
| --- | --- | --- |
| [ShahidBaig/eSyncMate_V2 `8478a962`](https://github.com/ShahidBaig/eSyncMate_V2/tree/8478a96267fb692985c70e32f5dde0544209d6a5) | [`UI/package.json`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/package.json)、[standalone component](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/alert-configuration/alert-configuration.component.ts)、[`app.module.ts`](https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/app.module.ts) | `^15.0.0 → 17.0.0`；standalone、HTTP loader、module/provider 边界得到标记 |
| [Kurarion/Genshin-Calc `c7dd4d85`](https://github.com/Kurarion/Genshin-Calc/tree/c7dd4d850db8523e33302e98d71d9e180605bd4e) | [`package.json`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/package.json)、[`app.module.ts`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/app.module.ts)、[`language.service.ts`](https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/shared/service/language.service.ts) | `^14.0.0 → 17.0.0`；proven setter/getter 自动迁移；Angular、HTTP loader、legacy property 得到标记 |
| [FeDi20-03/TuniSalesGateway `40e99bed`](https://github.com/FeDi20-03/TuniSalesGateway/tree/40e99bedf123169767fbcf7200f4e2e0a94eb402) | [`package.json`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/package.json)、[`translation.module.ts`](https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/src/main/webapp/app/shared/language/translation.module.ts) | `14.0.0 → 17.0.0`；setter 自动迁移；child scope 和 missing-handler provider 得到标记 |

测试结构参考 OpenRewrite 固定提交 `b3008cc4` 的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)，覆盖 before/after、no-change、参数化边界、绑定冲突、对象 shorthand、生成目录、`SearchResult`、真实仓库、recipe discovery/validation 和幂等性。当前模块共有 **275 个 JUnit invocation**。

## 使用与验证

先运行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17
```

检查自动 patch 和全部 `~~>` 标记后再执行 `run`。随后用项目原包管理器重新生成锁文件，并执行 Angular build、严格 TypeScript 编译、单元测试、SSR/hydration 测试和快速连续切换语言的端到端测试。

本模块验证命令：

```bash
mvn -f rewrite-ngx-translate-core-upgrade/pom.xml clean verify
```
