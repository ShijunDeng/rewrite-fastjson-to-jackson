# vue-i18n upgrade to 11.3.0

本模块对应 `开源软件升级.xlsx` 中的 `vue-i18n`，合并处理明确列出的 `7.3.2`、`8.11.2`、`8.20.0`、`8.22.1`、`8.22.4`、`8.24.3`、`8.24.4`、`8.25.0`、`8.26.7` 以及 `8.27.1 …（共 17 个版本）`，目标版本为 `11.3.0`。

配方名称：

```text
com.huawei.clouds.openrewrite.vuei18n.UpgradeVueI18nTo11_3_0
```

## 自动处理范围

配方只修改 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies` 中名为 `vue-i18n` 的直接声明。它识别 7.x–10.x、11.0.x–11.2.x 和 `11.3.0` 预发布版本的精确版本、常见 `^`/`~`/比较器范围、`v` 前缀及 major wildcard，并设置为精确版本 `11.3.0`。

以下内容刻意不自动修改：

- `workspace:`、npm alias、Git/GitHub、HTTP tarball 和 `file:` 引用；
- `latest`、`next`、`*` 等不能确定升级方向的标签或无界范围；
- `package-lock.json` 等锁文件、其他 JSON 文件、低于表格处理范围的 6.x 及更早版本；
- 已是 `11.3.0` 的声明，以及 11.3.1、11.4.x、12.x 等更高版本，防止降级；
- Vue、Vue Router、Vuex/Pinia、Nuxt、`@intlify/*`、源码、SFC 模板及 locale message 文件。

依赖声明升级只是迁移入口。7.x/8.x 工程通常仍运行 Vue 2，而目标包要求 Vue `^3.0.0`；必须先完成 Vue 3、构建链和应用 API 迁移，再安装目标版本并重建锁文件。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v11.3.0 的 peer 依赖是 Vue `^3.0.0`，Node engine 是 `>=16` | Vue 2 + vue-i18n 7/8 不能只改一个版本号；先迁到 Vue 3，统一 `vue`、compiler、test-utils、router、store、SSR 和 Node 构建环境 |
| v8 的 `Vue.use(VueI18n)` / `new VueI18n(options)` 在 v9+ 改为 `createI18n(options)` / `app.use(i18n)` | 调整应用启动、插件注入、测试 mount 和 SSR 每请求实例；目标 v11 虽仍兼容 Legacy API，但该模式已弃用并计划在 v12 删除 |
| `dateTimeFormats` 改名为 `datetimeFormats` | 修改全局及组件局部配置、类型定义、fixture 和序列化配置；漏改会导致日期格式配置不生效 |
| `$t`/`t` 在 v9+ 只返回字符串 | 旧代码若读取对象或数组，改用 `$tm`/`tm` 获取消息结构，再用 `$rt`/`rt` 解析叶子消息 |
| v9 删除 `getChoiceIndex`、custom formatter、`preserveDirectiveContent` 等 v8 扩展点 | 自定义复数规则使用 `pluralRules`；formatter 逻辑迁到 message function/编译链；删除对应 option、mock 和类型扩展 |
| v9 消息编译器严格解析 `{`、`}`、`@`、`$`、`|` | 邮箱、管道符等字面字符使用 literal interpolation；回归所有 locale 文件的预编译和运行时编译错误 |
| v9 不再接受 list interpolation 的类数组对象，并移除 linked message 的括号分组旧语法 | `{ '0': value }` 改为 `[value]`；按官方语法更新 linked key/modifier，批量编译全部语言资源验证 |
| `<i18n>` 改为 `<i18n-t>`，`path` 改为 `keypath`，`places`/`place` 被 slots 取代 | 修改 SFC/JSX 模板和组件注册；无 wrapper 时省略 `tag`，具名插值改为具名 slot |
| v10 默认启用 JIT compilation | 重新构建生产包并复核 CSP、runtime-only/full build、动态加载消息和 `@intlify/unplugin-vue-i18n` 配置 |
| v10 统一 Legacy 与 Composition 的 `$t`/`t` overload | `$t(key, locale, ...)` 等把 locale 作为位置参数的调用要改为 options 形式；更新 TypeScript 类型断言和 wrapper API |
| v10 弃用 `tc`/`$tc`，v11 完全删除 | 统一改为 `t`/`$t` 的 plural 参数形式；搜索模板、Options API、Composition API、测试 spy 和类型扩展中的所有调用 |
| v10 删除 `%` linked syntax、`vue-i18n-bridge`、`allowComposition` 及多项 v8 compatibility | Vue 2 bridge 最后停留在 v9.13；迁往目标 v11 前必须去掉 bridge 和兼容开关，并改写旧 linked message |
| v11 弃用 Legacy API mode 与自定义指令 `v-t` | Legacy 迁到 `legacy: false` + `useI18n`；使用 `eslint-plugin-vue-i18n` 的 deprecated rules 将 `v-t` 改为 `$t`/`t`，避免 v12 再次阻塞 |
| Legacy 与 Composition scope/inheritance 不完全相同 | 检查 `useScope`、`globalInjection`、局部 `<i18n>` block、fallback locale、missing/warn handler 以及组件卸载后的资源生命周期 |
| SSR 与 lazy locale 加载需要每请求隔离 | 不要跨请求复用全局 composer；验证 hydration、route 切换竞态、异步 chunk 失败、fallback chain 与服务端/客户端 locale 一致性 |
| v11.3.0 增加 message escape sequence 支持并包含 message-path、modifier、`v-t`、日期/数字格式修复 | 不应依赖旧 bug；对包含转义、点号 key、missing modifier、locale `!`、`n`/`d` undefined 输入的消息增加回归测试 |

逐版本细节以 Vue I18n 官方 [v9 breaking changes](https://vue-i18n.intlify.dev/guide/migration/breaking)、[v10 breaking changes](https://vue-i18n.intlify.dev/guide/migration/breaking10)、[v11 breaking changes](https://vue-i18n.intlify.dev/guide/migration/breaking11)、[Vue 2 migration](https://vue-i18n.intlify.dev/guide/migration/vue2)、[Vue 3 migration](https://vue-i18n.intlify.dev/guide/migration/vue3) 和 [v11.3.0 release](https://github.com/intlify/vue-i18n/releases/tag/v11.3.0) 为准。

## 测试样本来源

- [Pixelfed](https://github.com/pixelfed/pixelfed/blob/c8bed78bee3d796c5efb57393dafafbba3706f38/package.json) 的 `vue-i18n ^8.27.1`、Vue 2、Vue Router 3、Vuex 3 真实组合
- [XBoot Front](https://github.com/Exrick/xboot-front/blob/fb933de4c5927792b71da31479f5f0693aeb71c6/package.json) 的 `vue-i18n ^8.24.4` 与相邻 Vue 2 技术栈
- [Vuestic Admin](https://github.com/epicmaxco/vuestic-admin/blob/9c5b44f3674d4c3e7ad01cc043d5331cee953c49/package.json) 的 `vue-i18n ^9.6.2` 与 Vue 3、Pinia、Vue Router 4 组合
- [Vue I18n v11.3.0 官方 manifest](https://github.com/intlify/vue-i18n/blob/v11.3.0/packages/vue-i18n/package.json) 的版本、Vue peer、Node engine、exports 和构建格式
- [OpenRewrite ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 的 JSONPath filter、格式保持和 no-op 测试方式

测试覆盖表格逐个明列版本、三个真实仓库、四个直接依赖区、比较器/caret/tilde/v-prefix/wildcard、monorepo 子包、11.3.0 预发布版本，以及目标版本、高版本、workspace/alias/Git/file/URL、tag、lockfile、其他 JSON 和相似包名不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-vue-i18n-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.vuei18n.UpgradeVueI18nTo11_3_0
```

确认 patch 后先完成 Vue 3 和 Vue I18n API/消息语法迁移，再重建 lockfile。至少运行 TypeScript typecheck、locale message compile、unit/E2E、SSR/hydration、lazy locale loading、fallback/missing handler、plural/date/number formatting 与各语言 UI 视觉回归。

本模块自身验证：

```bash
mvn -f rewrite-vue-i18n-upgrade/pom.xml clean verify
```
