# @angular/compiler upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/compiler`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 28 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCompilerTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/compiler` 设置为 `20.3.26`。`@angular/core` 对 compiler 有精确 `20.3.26` peer 要求；`@angular/compiler-cli` 及全部 framework 包也必须同步，并逐大版本运行 Angular CLI migrations。

本配方不会改模板或 `angularCompilerOptions`，也不能替代 Angular compiler/linker 的官方迁移。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| View Engine 编译器、metadata 与 ngcc 被移除 | 应用使用 Ivy AOT，库使用 partial compilation；升级/替换仅含 View Engine 产物的依赖 |
| runtime JIT 与 `platform-browser-dynamic` 在 v20 废弃 | 消除动态字符串模板和 compiler 私有 API；生产构建必须通过 AOT/CSP 验证 |
| compiler/core/compiler-cli 必须完全锁步 | 三者统一 20.3.26，防止模板指令版本、linker 和 runtime instruction 不匹配 |
| TypeScript 支持窗口跨版本严格变化 | Angular 20 compiler-cli 使用 TypeScript `>=5.8 <6.0`；逐大版本升级，不能用最终 TS 运行旧 schematics |
| `fullTemplateTypeCheck` 演进为 `strictTemplates` 体系 | 开启严格检查并修复输入、输出、null、DOM event、泛型 directive 和 `$event` 类型，不要全局关闭 |
| 内建 `@if`/`@for`/`@switch`/`@defer` 改变模板语法 | 旧模板文本中的 `@`、`{`、`}` 按 migrations 转义；`@for` 使用稳定且唯一的 track 表达式 |
| v20 将 `in` 与 `void` 识别为模板运算符 | 原本名为 `in`/`void` 的组件属性需写成 `this.in`/`this.void` 或重命名 |
| 模板表达式支持面扩展且解析更严格 | 修复依赖旧解析歧义的 optional chaining、nullish coalescing、赋值和管道表达式，避免调用全局对象 |
| standalone 默认值与依赖分析变化 | 旧声明由 migrations 明确 `standalone: false`；standalone 组件显式列出模板实际使用的 imports |
| decorator 元数据必须可静态求值 | routes/providers/imports/host 元数据避免任意运行时代码；使用导出的常量或 provider factory |
| library deep import 与私有 `ɵ` API 不稳定 | 仅从公开 entry point 导入；修复依赖 compiler AST/内部 transform 的自研工具 |
| template whitespace、i18n message ID 和 source span 经多次修正 | 对 i18n 提取物执行受控 diff，重新合并翻译并验证空白敏感布局 |
| strict injection/template diagnostics 增加 | 将诊断作为源码缺陷处理；只有确认兼容意图后才用 `extendedDiagnostics` 调整单项级别 |
| host binding/event 的模板类型检查增强 | 修复 host expression 和事件名类型；覆盖 directive 继承、组合与自定义元素 schema |
| linker 与构建优化依赖标准包结构 | 不要手工处理 FESM/metadata；确保 Babel/esbuild 插件保留 Angular linker 所需顺序与 sideEffects 信息 |
| Node 与整个 Angular 工具链基线 | Node 使用 `^20.19.0`、`^22.12.0` 或 `>=24`，并统一 CLI/build-angular/devkit 兼容版本 |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide)、[Template expression syntax](https://angular.dev/guide/templates/expression-syntax)、[AOT compiler guide](https://angular.dev/tools/cli/aot-compiler) 和 [版本兼容矩阵](https://angular.dev/reference/versions) 为准；v20 breaking changes 见 [20.0.0 release](https://github.com/angular/angular/releases/tag/20.0.0)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-compiler-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularCompilerTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 production AOT build、strict template check、库 partial compilation/linker、i18n extraction、SSR 与 hydration 测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-compiler-upgrade -am clean verify
```
