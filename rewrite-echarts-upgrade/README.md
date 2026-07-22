# Apache ECharts 4.x/5.x to 6.1.0 upgrade recipe

本模块对应 `开源软件升级.xlsx` 中的 `echarts`，合并处理表中列出的 `4.8.0`、`4.9.0`、`5.0.2`、`5.2.1`、`5.2.2`、`5.3.0`、`5.3.1`、`5.3.3`、`5.4.0` 及 `5.4.1 …（共 12 个版本）`，目标版本为 `6.1.0`。

配方名称：

```text
com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0
```

## 自动处理范围

配方仅修改 `package.json`，将下列直接依赖区中的 `echarts` 版本统一设置为 `6.1.0`：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

配方不会修改名称相似但生命周期独立的 `ngx-echarts`、`echarts-for-react` 或 `vue-echarts`。这些适配层必须按各自模块的目标版本单独升级。

`package-lock.json`、`npm-shrinkwrap.json`、`yarn.lock` 和 `pnpm-lock.yaml` 包含解析地址、完整性摘要及依赖图，不能只替换版本字符串。运行配方后必须使用项目原有包管理器重新生成锁文件，例如：

```bash
npm install
# 或 yarn install / pnpm install
```

## ECharts 4 → 5 不兼容修改点

| 不兼容点 | 迁移建议 |
| --- | --- |
| 默认主题和调色板改变 | 对颜色敏感的图表显式设置主题或调色板；临时兼容可复用 v4 调色板 |
| npm 包不再内置地图 GeoJSON | 自行引入所需 GeoJSON，并通过 `echarts.registerMap()` 注册 |
| `echarts/lib/...` 内部源码路径及 TypeScript/ESM 结构改变 | 按需引入改用公开的 `echarts/core`、`echarts/charts`、`echarts/components` 和 `echarts/renderers` 入口，避免内部路径 |
| 停止支持 IE8/VML | 更新浏览器支持基线；旧版 IE 场景不能直接升级 |
| `visualMap` 与 `itemStyle` 的视觉优先级调整 | 检查同时使用两者的颜色、透明度和边框结果 |
| 富文本 `padding` 修复为与 CSS 一致 | 对依赖旧错误顺序的富文本标签重新调整 padding |
| simple 构建不再包含 ARIA | 需要无障碍能力时改用包含 `AriaComponent` 的模块化构建 |
| 图形元素数组式 `position`、`scale`、`origin` 已废弃 | 改用 `x`/`y`、`scaleX`/`scaleY`、`originX`/`originY` |
| 文本元素样式接口重构 | 使用 `textContent`、`textConfig` 和新的文本样式属性，清理旧版扁平文本属性 |
| `hoverAnimation`、`clipOverflow` 等旧配置废弃 | 分别迁移到 `emphasis.scale`、`clip`，检查所有废弃配置警告 |

## ECharts 5 → 6 不兼容修改点

| 不兼容点 | 迁移建议 |
| --- | --- |
| v6 默认主题、视觉样式和组件位置改变，图例默认移到底部 | 做视觉回归；需要保持 v5 外观时显式使用 `echarts/theme/v5.js` |
| Cartesian 坐标轴默认启用防溢出和轴名防重叠布局 | 检查像素级布局；必要时设置 `grid.outerBoundsMode: 'none'` 并关闭相应轴标签/轴名防重叠选项 |
| `geo`、`map`、`graph`、`tree` 的百分比 `center` 计算基准修正 | 重新校准中心位置；短期可在根 option 设置 `legacyViewCoordSysCenterBase: true` |
| rich label 样式现在继承普通 label 样式 | 检查字体与阴影继承；需要旧行为时设置 `richInheritPlainLabel: false` |
| `tooltip.valueFormatter` 第二个参数改为原始数据索引 | 若回调使用索引读取数据，改为基于 raw data index，并覆盖 dataZoom/filter 场景测试 |
| `axis.startValue` 不再兼作 `axis.min` | 需要固定最小值时同时显式设置 `axis.min` |
| bar、pictorialBar、candlestick、boxplot 默认不再越出 grid | 确认边界图形展示；需要旧行为时设置 `axis.containShape: false` |

完整差异以 Apache ECharts 官方的 [5.0.0](https://github.com/apache/echarts/releases/tag/5.0.0)、[6.0.0](https://github.com/apache/echarts/releases/tag/6.0.0) 和 [6.1.0](https://github.com/apache/echarts/releases/tag/6.1.0) 发布说明为准。

## 使用方式

安装本仓库配方后，在待升级项目执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-echarts-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0
```

确认 patch 后将 `dryRun` 改为 `run`，随后重新生成锁文件，并执行 TypeScript 编译、单元测试、视觉回归和端到端测试。

## 模块验证

```bash
mvn -pl rewrite-echarts-upgrade -am clean verify
```
