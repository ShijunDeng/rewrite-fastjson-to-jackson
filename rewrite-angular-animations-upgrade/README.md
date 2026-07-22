# @angular/animations upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/animations`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 28 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularAnimationsTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/animations` 设置为 `20.3.26`。目标包要求 `@angular/core:20.3.26`。Angular 20.2 已废弃整个 legacy animations 包，版本升级是满足表格目标的过渡动作，新开发应使用 CSS 与 `animate.enter`/`animate.leave`。

实际升级必须逐大版本运行 Angular CLI migrations，统一所有 framework 包后重建锁文件。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 20.2 废弃 `@angular/animations` | 制定逐组件迁离计划；不要继续扩展 trigger/state/transition DSL |
| `BrowserAnimationsModule`/`NoopAnimationsModule` 及对应 provider 进入 legacy 路径 | 新动画使用 CSS；测试按需关闭动画或等待实际完成，不要依赖全局模块隐式时序 |
| `@Component({animations: [...]})` 在 v20.2 废弃 | 将进入/离开场景迁移到模板 `animate.enter`/`animate.leave` 和 CSS class/keyframes |
| `AnimationBuilder`、`AnimationFactory` 等程序式 API | 使用 Web Animations API、CSS 或维护中的动画库；明确处理 cancel、finished promise 和元素销毁 |
| route animation 依赖 outlet DOM 与状态表达式 | 改造前覆盖前进/后退、lazy route、SSR hydration 与 reduced-motion，避免导航完成时序变化 |
| query/stagger/group/sequence 等复杂 DSL 没有机械一一映射 | 分解成 CSS transitions/keyframes 或显式 timeline；通过视觉回归确认延迟、并发和最终样式 |
| disabled/reduced-motion 行为 | 尊重 `prefers-reduced-motion`；不要仅依赖 `@.disabled`，确保可访问模式不会等待隐藏动画 |
| 动画回调事件与 DOM 移除时机变化 | 检查 `start`/`done`、enter/leave 回调、焦点管理和 overlay 销毁，避免重复提交或悬挂节点 |
| Angular 20 自动变更检测会更一致地 flush animations | 旧测试可能在 DOM 尚未/已经删除的不同阶段断言；使用稳定的 async/harness 等待方式 |
| SSR 不执行真实浏览器动画 | server 输出必须是可用最终/初始状态，client hydration 后再增强，防止闪烁和 hydration mismatch |
| framework 包与运行工具链锁步 | core/animations 使用 20.3.26；Node 使用 `^20.19.0`、`^22.12.0` 或 `>=24`，TypeScript 按 Angular 矩阵升级 |
| 三方组件库可能仍依赖 legacy animations | 核对 peer dependencies；应用移除本包前先升级或替换这些组件库 |

完整迁移步骤以 Angular 官方 [Animations guide](https://angular.dev/guide/animations)、[Update Guide](https://angular.dev/update-guide)、[版本兼容矩阵](https://angular.dev/reference/versions) 与 [20.2.0 release](https://github.com/angular/angular/releases/tag/20.2.0) 为准。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-animations-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularAnimationsTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 production build、单元/E2E、视觉回归、reduced-motion、SSR 与 hydration 测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-animations-upgrade -am clean verify
```
