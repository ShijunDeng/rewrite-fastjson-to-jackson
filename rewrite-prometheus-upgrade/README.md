# Prometheus Go 模块升级到 v0.311.3

本模块处理 Go module `github.com/prometheus/prometheus`。它不是 `client_golang`：这是 Prometheus server/TSDB/PromQL 源码仓作为 Go 依赖暴露的包，官方明确不承诺这些 server Go API 在小版本间稳定。

逐行读取 `开源软件升级.xlsx` 后，只有以下八个明确可见的源版本：

| XLSX 行 | 序号 | 原始版本 | 目标版本 |
| --- | --- | --- | --- |
| 326 | 325 | `v2.22.1+incompatible` | `v0.311.3` |
| 1331 | 1330 | `v1.8.3` | `v0.311.3` |
| 2952 | 2951 | `v0.44.0` | `v0.311.3` |
| 2953 | 2952 | `v0.46.0` | `v0.311.3` |
| 2954 | 2953 | `v0.48.1` | `v0.311.3` |
| 2955 | 2954 | `v0.49.1` | `v0.311.3` |
| 2956 | 2955 | `v0.50.1` | `v0.311.3` |
| 2957 | 2956 | `v0.54.1` | `v0.311.3` |

不会根据“跨大版本”、相邻版本、Go tag 或省略信息扩展白名单。特别地，Go proxy 对工作簿中的 `v1.8.3` 和 `v2.22.1+incompatible` 当前返回不存在；配方仍按工作簿精确匹配并迁移已有工程文本，但不会把它们宣称为可重新下载的上游 release。

推荐入口：

```text
com.huawei.clouds.openrewrite.prometheus.MigratePrometheusTo0_311_3
```

仅升级 `go.mod` 版本的低层入口：

```text
com.huawei.clouds.openrewrite.prometheus.UpgradePrometheusTo0_311_3
```

## spec → recipe → test 映射

| 规格 | 配方/实现 | 主要测试 |
| --- | --- | --- |
| 八个 XLSX 版本精确升级 | `UpgradeSelectedPrometheusDependency` | `PrometheusDependencyTest#upgradesEveryVisibleWorkbookSource`、严格 NOOP 参数集 |
| 只改 `go.mod require` | `UpgradeSelectedPrometheusDependency` | direct/block/indirect/CRLF/nested module；replace/exclude block、块尾注释、裸 module/comment/go.sum/vendor NOOP |
| 五个确定性 Go package relocation | `MigratePrometheusGoImports` | `relocatesFiveDocumentedGoPackages`、`migratesPeriskopFixedCommitImportFixture`、alias/dot alias/raw literal/block；注释、字符串和生成目录 NOOP |
| 1→2、2→3 确定性 YAML key | `MigratePrometheusConfiguration` | 根级 `global`、根级 `scrape_configs` before→after；嵌套同名键及新旧键冲突 NOOP |
| 已有一对一替代的独立 flag | `MigratePrometheusConfiguration`、`MigratePrometheusCommandFlags` | YAML argv owner、Prometheus shell/systemd/Docker/template command；prose/comment/echo/无所有权 CMD/combined-list NOOP |
| Go 1.25 与不稳定 server Go API | `FindPrometheusTextMigrationRisks` | 7 个 Go baseline、7 类 import、`marksCorootFixedCommitPromqlParserFixture`、require/replace/exclude/companion 精确 markers |
| 1.x rules/flags 与 TSDB | `FindPrometheusTextMigrationRisks` | legacy rules、PromQL、removed flag、storage、image markers |
| 3.x config/PromQL/protocol | `FindPrometheusYamlMigrationRisks` | scrape、remote_write、AM v1、regex、external labels、PromQL、image/argv markers |
| 生成物隔离 | 所有 visitor 的 `PrometheusSupport.isProjectPath` | 大小写变体、vendor/target/build/dist/generated/install 前缀正反例；`install.sh` 这类叶文件仍会处理 |
| recipe discovery/validation 与幂等 | declarative `rewrite.yml` | `discoversAndValidatesBothRecipes`、两轮 AUTO、Go MARK、YAML conflict MARK tests |

## 自动修改与人工边界

| 不兼容点 | 行为 | 处理原则 |
| --- | --- | --- |
| 八个精确 `require` 版本 | **AUTO** | 只改到 `v0.311.3`，保留空白和 `// indirect` |
| `pkg/{labels,textparse,relabel,timestamp,value}` | **AUTO** | 只在 Go import 声明中改到对应 `model/*`；支持 alias/dot alias 和双引号/raw literal，跳过注释及多行字符串 |
| `global.labels` | **AUTO** | 仅根级 `global` 的直接子键改为 `external_labels`；新键已存在时不自动合并 |
| `target_groups` | **AUTO** | 仅根级 `scrape_configs` item 的直接子键改为 `static_configs`；新键已存在时不改 |
| `scrape_classic_histograms` | **AUTO** | 同上，仅精确 owner 下改为 `always_scrape_classic_histograms` |
| 独立 `agent`/remote receiver/OTLP feature flag | **AUTO** | 只处理可证明由 Prometheus 持有的 argv；逗号组合列表不拆分 |
| `storage.tsdb.retention`、`query.staleness-delta` | **AUTO** | 只改有官方一对一替代的参数名 |
| Go `<1.25` | **MARK** | 目标 module 的实际 `go.mod` 是 `go 1.25.0` |
| 其他 `prometheus/prometheus/*` Go imports | **MARK** | 官方不保证 server Go API；必须针对目标 clean compile/test |
| replace/exclude 与 companion 显式版本 | **MARK** | 检查 fork、MVS、checksum、vendor 和完整 graph |
| 1.x rules、storage/remote flags | **MARK** | rule YAML、TSDB 和远端适配器需要迁移方案 |
| Alertmanager API v1 | **MARK** | Prometheus 3 拒绝 v1，需同步升级 AM 并改 v2 |
| scrape Content-Type、默认端口、UTF-8、bucket label | **MARK** | exporter、query、alert、dashboard 和 relabel 联动验证 |
| remote_write | **MARK** | HTTP/2 默认、WAL catch-up、retry/backpressure/protocol |
| regex `.`、range/lookback、旧 PromQL 函数 | **MARK** | 语义改变，无跨业务通用的安全替换 |
| TSDB path 与旧 server image | **MARK** | staged upgrade、备份、权限、回滚和 binary/config 对齐 |

SearchResult 是精确的待决策点。推荐配方不会把 MARK 当成自动修复，也不会为缺失配置凭空选择值。

## 严格 `go.mod` 所有权

低层配方只读取维护源码路径下名为 `go.mod` 的文件，并用 directive 状态机区分 `require`、`replace`、`exclude` block；支持：

```go
require github.com/prometheus/prometheus v0.44.0

require (
    github.com/prometheus/prometheus v0.54.1 // indirect
)
```

以下内容完全不自动修改：

- `replace`、`exclude`、fork、local path；
- `go.sum`、普通文档或字符串；
- 未列入 XLSX 的版本、branch/tag/pseudo-version；
- `vendor`、`target`、`build`、`dist`、`out`、`generated*`/`install*` 目录及常见缓存/IDE/包管理器目录（目录名大小写不敏感）；过滤只检查目录组件，不会因为叶文件名是 `install.sh` 而跳过。

推荐配方会 MARK target module 的 replace/exclude，以及显式 `prometheus/common`、`client_golang`、`client_model`、`procfs`、`alertmanager`。执行后检查真正的 MVS 结果：

```bash
go mod tidy
go mod graph | grep 'github.com/prometheus'
go list -m -json all
go list -deps ./...
go test ./...
```

不要手工复制目标上游的整份 `go.mod` 到业务工程；只让 Go MVS 选择业务实际导入需要的图。

## 目标基线

上游同时发布 `v0.311.3` Go module tag 和 `v3.11.3` server tag；两者都解引用到固定提交 [`eb173f5256d4022afba1e9bc3d19740a76859fae`](https://github.com/prometheus/prometheus/commit/eb173f5256d4022afba1e9bc3d19740a76859fae)。目标 [`go.mod`](https://github.com/prometheus/prometheus/blob/eb173f5256d4022afba1e9bc3d19740a76859fae/go.mod) 明确声明：

```go
module github.com/prometheus/prometheus
go 1.25.0
```

因此 Go toolchain、CI/build image、golangci-lint、代码生成、CGO 和目标架构都必须升级。Prometheus 仓库 [README](https://github.com/prometheus/prometheus/tree/eb173f5256d4022afba1e9bc3d19740a76859fae) 说明 server 仓的 Go APIs 允许在 major-zero module 版本间破坏；这也是为什么推荐配方会 MARK 所有真实 server package import。

`v3.11.3` 还包含 remote-read 解压上限、AzureAD secret 暴露和旧 UI XSS 的安全修复；见固定提交的 [CHANGELOG](https://github.com/prometheus/prometheus/blob/eb173f5256d4022afba1e9bc3d19740a76859fae/CHANGELOG.md)。升级不能只做到编译通过，还要确认目标 binary/image 与部署配置确实采用同一版本。

## 1.8 → 2.x 不兼容点

官方 [Prometheus 2.0 migration guide](https://prometheus.io/docs/prometheus/2.55/migration/) 说明：

- CLI 从单横线改为双横线；`-storage.local.*`、`-storage.remote.*`、`-alertmanager.url` 等被移除；
- `-query.staleness-delta` 改名为 `--query.lookback-delta`；
- Alertmanager 静态 URL flag 改为配置文件 service discovery；
- alert/recording rule 从旧文本语法改成 YAML；历史自动转换命令只存在于 Prometheus 2.5 的 `promtool update rules`；
- 2.0 TSDB 与 1.8 不兼容，历史数据需要保留 1.8 reader 并通过 remote read 访问；
- `count_scalar`、`drop_common_labels`、`keep_common` 等 PromQL 能力被移除。

配方只自动修改确定的一对一名字。旧 rules、storage、Alertmanager 和 query 语义全部 MARK。

## 2.x → 3.x 不兼容点

官方 [Prometheus 3 migration guide](https://prometheus.io/docs/prometheus/latest/migration/) 列出的关键风险已经映射到 MARK：

- scrape 缺失/非法 Content-Type 不再隐式回退；需要 exporter 修复或明确 `fallback_scrape_protocol`；
- `scrape_classic_histograms` 改名；
- regex 的 `.` 开始匹配换行；
- range/lookback selector 左边界从闭区间改为开区间；
- `holt_winters` 改为受 feature gate 控制的 `double_exponential_smoothing`；
- classic histogram 的 `le` 和 summary 的 `quantile` 值规范化，如 `1` 变成 `1.0`；
- UTF-8 metric/label names 默认启用；需要旧行为时显式选择 legacy validation；
- remote-write `enable_http2` 默认变成 `false`；
- Alertmanager v1 API 不再允许；
- removed/default-on feature flags、日志格式和默认端口行为改变。

TSDB 在 2.55 已为新 index format 改变；官方指出 Prometheus 3 的数据只能由 2.55+ 读取。建议先升级并稳定运行 2.55，完成 snapshot/backup 和 rollback drill，再进入 3.x。不要让旧 binary 直接打开新 TSDB。

部署验证至少包括：抓取成功/错误 Content-Type、reload、remote_write 断网和 WAL catch-up、AM HA 通知、rules expected samples、UTF-8/bucket dashboard、磁盘满、WAL replay、rolling restart、权限和 downgrade 演练。

## 真实固定提交夹具

测试采用公开仓库中真实 `go.mod` 声明形态，并缩减成最小 fixture：

- [byrnedo/prometheus-gsheet `221e56d2949c3b45902760d8f6c8aea1894b19ca`](https://github.com/byrnedo/prometheus-gsheet/blob/221e56d2949c3b45902760d8f6c8aea1894b19ca/go.mod)：`v0.44.0`；
- [googleforgames/open-match `d781be1a3ce1b6b7fce495345b23256089f55de9`](https://github.com/googleforgames/open-match/blob/d781be1a3ce1b6b7fce495345b23256089f55de9/go.mod)：`v0.46.0`；
- [lindb/lindb `612070e1dc6043d2ea47d5b3bc6dccd51eefaee4`](https://github.com/lindb/lindb/blob/612070e1dc6043d2ea47d5b3bc6dccd51eefaee4/go.mod)：`v0.48.1`；
- [uptrace/uptrace `f617f6c76a035a1b2a83cc19cb2bc2802a68f0a6`](https://github.com/uptrace/uptrace/blob/f617f6c76a035a1b2a83cc19cb2bc2802a68f0a6/go.mod)：`v0.49.1`；
- [pingcap/tidb `e35ad93664b36eeab96fb78f254dd611078c4867`](https://github.com/pingcap/tidb/blob/e35ad93664b36eeab96fb78f254dd611078c4867/go.mod)：`v0.50.1`；
- [juicedata/juicefs `b475bc08c360abaf174290a0e7c7489eddd03968`](https://github.com/juicedata/juicefs/blob/b475bc08c360abaf174290a0e7c7489eddd03968/go.mod)：`v0.54.1`；
- [periskop-dev/periskop `4b3986e03595e889755b5c7feeb247f9090ab6bc`](https://github.com/periskop-dev/periskop/blob/4b3986e03595e889755b5c7feeb247f9090ab6bc/servicediscovery/servicediscovery.go)：保留真实 alias/import block 形态，验证 `pkg/labels` 与 `pkg/relabel` 的确定性迁移；
- [coroot/coroot `0aaf083f9e9a60cb07274a4c4c5a008e31c0c495`](https://github.com/coroot/coroot/blob/0aaf083f9e9a60cb07274a4c4c5a008e31c0c495/config/utils.go)：真实 `promql/parser.ParseMetricSelector` 调用，验证不稳定 server API 的精确 MARK；
- 上游旧 package layout 以 [`v2.22.1` 固定提交 `00f16d1ac3a4c94561e5133b821d8e4d9ef78ec2`](https://github.com/prometheus/prometheus/tree/00f16d1ac3a4c94561e5133b821d8e4d9ef78ec2/pkg) 校验，新 `model/*` layout 以 [`v0.44.0` 固定提交 `1ac5131f698ebc60f13fe2727f89b115a41f6558`](https://github.com/prometheus/prometheus/tree/1ac5131f698ebc60f13fe2727f89b115a41f6558/model) 和目标提交校验；
- 测试的 before→after、NOOP、marker、幂等和 recipe validation 组织参考 OpenRewrite 官方 [`UpgradeDependencyVersionTest` 固定提交 `decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-prometheus-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.prometheus.MigratePrometheusTo0_311_3
```

OpenRewrite Maven plugin 只是配方载体；目标工程仍是 Go 工程。审查所有 patch/SearchResult 后，运行目标 toolchain：

```bash
go mod tidy
go test ./...
promtool check config prometheus.yml
promtool check rules path/to/*.rules.yml
```

本模块独立验证：

```bash
mvn -f rewrite-prometheus-upgrade/pom.xml clean verify
```

当前模块包含 155 个测试（45 个依赖所有权/版本测试、38 个确定性自动迁移测试、72 个风险标记与配方验证测试）。
