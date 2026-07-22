# Grafana 12.1.1 迁移规范

本模块处理 `开源软件升级.xlsx` 中 Grafana 的全部且仅有以下可见版本：

| XLSX 行 | 序号 | 表格源版本 | 目标版本 |
| --- | --- | --- | --- |
| 264 | 263 | `7.4.5` | `12.1.1` |
| 265 | 264 | `7.5.16` | `12.1.1` |
| 266 | 265 | `8.5.14` | `12.1.1` |
| 267 | 266 | `9.1.7` | `12.1.1` |

不会把相邻版本、任意 `7.x`/`8.x`/`9.x`、`latest`、变量或镜像 digest 推断为升级输入。推荐入口同时执行严格镜像升级、确定性配置迁移和兼容风险定位：

```text
com.huawei.clouds.openrewrite.grafana.MigrateGrafanaTo12_1_1
```

只修改表格版本镜像时使用低层入口：

```text
com.huawei.clouds.openrewrite.grafana.UpgradeGrafanaImageTo12_1_1
```

## 自动修改边界

`AUTO` 表示输入、目标和所有权均可证明的一对一修改；`MARK` 表示在准确 YAML/JSON/INI/Dockerfile 位置生成 `SearchResult`，保留业务选择；`NO-OP` 表示刻意不改。

| 场景 | 状态 | 配方行为 |
| --- | --- | --- |
| Compose/Kubernetes YAML 的 `image:` | AUTO | 只把四个表格 tag 的 `grafana/grafana`、`-oss`、`-enterprise` 官方镜像改为 `12.1.1`；同一 mapping 有重复 `image` 时不猜 owner |
| Helm `repository` + `tag` owner | AUTO | repository 必须是官方 Grafana 镜像，且同一 mapping 必须各有唯一的 repository/tag；重复 owner 保持不变并由 MARK 提示 |
| Dockerfile `FROM` | AUTO | 支持普通、多阶段、`--platform` 与 `AS`，只改精确表格 tag |
| Elasticsearch `access: direct` | AUTO | 仅 data source provisioning 作用域内、同一 mapping 有唯一 `type: elasticsearch` 和唯一 `access: direct` 时改为 `proxy` |
| `dashboardPreviews`、`envelopeEncryption` | AUTO | 从明确的 feature-toggle 列表删除已移除/被默认行为取代的 toggle；保留其他 toggle 和 `disableEnvelopeEncryption` |
| 简单插件 ID 列表 | AUTO | 仅官方 Docker 旧变量 `GF_INSTALL_PLUGINS`，且文件中有唯一旧 owner、没有 `GF_PLUGINS_PREINSTALL_SYNC`、没有生效的 `GF_INSTALL_PLUGINS_FORCE` 时改为同步 preinstall；支持 YAML map/sequence、`.env` 的 `export`/引号值，以及 Dockerfile `ENV KEY=value`/`ENV KEY value` |
| `GF_PLUGINS_INSTALL`、`[plugins] install`、FORCE、URL/版本/复杂格式 | NO-OP/MARK | 前两者不是已发布源版本中的正式 install owner；FORCE 会让目标代码跳过内建迁移；复杂语法涉及下载/签名策略，均保留并精确标记后人工转换 |
| 变量、variant、digest、未列/目标/未来镜像 | NO-OP/MARK | 不覆盖 registry、Helm、镜像签名和 digest owner；推荐配方在真实 owner 上标记 |
| 告警、Angular、身份、RBAC、UID、加密、数据库 | MARK | 只定位风险并给出阶段升级、权限、备份、回滚或引用联动要求 |
| 已兼容 12.1.1 的配置 | NO-OP | 不为缺失配置凭空选择值，不重写现代 dashboard/data source/plugin 声明 |

扫描排除 `target`、`build`、`out`、`dist`、`generated*`、`install*`、`vendor`、`.gradle`、`.mvn`、`.m2`、`.idea`、`.git`、`node_modules`、`bower_components`、`.pnpm`、`.yarn`、`.npm`、`.angular`、`.nx`、`.next`、`.cache`、`coverage` 等构建、安装、包管理器或生成目录。所有 AUTO 与 MARK 都经过双 cycle 测试；再次执行不会继续产生变化。

## 不兼容修改点

### 7.x/8.x → 9.x

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| legacy/unified alerting | MARK | Grafana 9 会迁移旧告警，但 Grafana 11 已删除迁移能力；若仍使用 legacy alert，必须先在 `10.4.x` 完成 rules、notification channels、contact points、silences 与 policy 验证 |
| Elasticsearch `<7.10` | MARK | Grafana 9 不再支持；升级 cluster/data-source plugin，并回归 index pattern、time field、认证、查询和 alerting |
| Elasticsearch browser/direct access | AUTO | 官方要求 server access；只在明确 Elasticsearch provisioning mapping 中改 `direct` 为 `proxy` |
| envelope encryption | AUTO/MARK | 删除旧 `envelopeEncryption` toggle；标记 `disableEnvelopeEncryption`、`secret_key` 与数据库 owner，确保 HA 副本使用同一密钥/KMS 并演练 pre-v9 rollback 边界 |
| Loki frame/transform | MARK | 标记 `labelsToFields`，检查 single-frame labels、Extract fields、table transform 与 NaN 表现 |

### 9.x → 10.x

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| `dashboardPreviews` | AUTO | 功能已删除，安全地从 feature-toggle 列表移除 |
| API keys → service accounts | MARK | 数据 token 会迁移，但 `/api/auth/keys` 管理自动化、所有权、rotation、RBAC scope 与 rollback 流程必须改为 service-account API |
| OAuth/email identity | MARK | 标记 `oauth_allow_insecure_email_lookup`，审计 provider 间重复邮箱、大小写归一化和账号接管风险 |
| CloudWatch Alias | MARK | 标记 dashboard target 的 `alias`，在 Grafana 中保存迁成 dynamic labels 后比对 legend 与 alert series matching |
| plugin frontend/API | MARK | 检查 React 18、Grafana plugin API、签名、私有插件和被弃用 Angular 依赖；不会自动改业务 plugin 代码 |

### 10.x → 11.x

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| legacy alerting 删除 | MARK | 11.x 无法启动旧 alerting，也不能执行迁移；`10.4.x` 是最后迁移跳板，禁止从仍有 legacy alert 的 7/8/9 直接跳到 12 |
| AngularJS 默认关闭 | MARK | 标记 `angular_support_enabled`、已知 Angular panel type 和 plugin metadata；使用官方检测工具清点后替换为 React 插件 |
| folder/subfolder policy | MARK | folder 名含 `/` 时，`grafana_folder` notification-policy matcher 语义受 subfolder 影响；逐条验证 receiver |
| anonymous Enterprise users | MARK | 标记匿名访问，确认 edition、license、org/role 和暴露面；需要公开分享时评估 public dashboards |

### 11.x → 12.1.1

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| AngularJS 完全删除 | MARK | `angular_support_enabled=true` 不再是回退手段；必须在升级前替换 dashboard、panel、data source 和 app plugin |
| `editors_can_admin` 删除 | MARK | Editor 隐式 team-management grant 消失；用明确 RBAC/team permission 重建最小权限 |
| data source UID 严格校验 | MARK | UID 必须匹配 `[A-Za-z0-9_-]{1,40}`；创建新 UID 时同步 dashboard、alert、API 与 Terraform 引用，不能只改 provisioning 字符串 |
| plugin preinstall | AUTO/MARK | 无 FORCE 的唯一 `GF_INSTALL_PLUGINS` 简单 catalog ID 可迁成 `GF_PLUGINS_PREINSTALL_SYNC`；非官方 install aliases、URL/version/custom plugin 必须核对 12.1.1 兼容性、签名和新 `plugin_id@version@url` 语法 |
| database schema 与回滚 | MARK | 先做可恢复备份，保留 `secret_key`/KMS；验证 migration、HA rolling upgrade、插件数据与 downgrade 边界 |
| 镜像供应链 | MARK | 变量/digest/私有镜像不自动重写；由 registry policy owner 构建/扫描/签名并重新生成 digest |

## 配方组成与测试映射

| 实现 | 作用 | 主要覆盖 |
| --- | --- | --- |
| `UpgradeSelectedGrafanaImage` | 严格镜像升级 | 四个表格版本、三类 YAML owner、官方 edition/registry、Dockerfile、多阶段、未列/dynamic/digest/generated NO-OP |
| `MigrateGrafana12DeterministicConfiguration` | 一对一配置迁移 | Grafana provisioning Elasticsearch proxy、官方 Docker plugin env、`export`/引号、FORCE/目标冲突、feature toggle、复杂值 NO-OP、幂等 |
| `FindGrafana12YamlRisks` | 部署/provisioning 风险 | image、alerting、Angular、plugin、identity、anonymous、UID、Elasticsearch、encryption、folder |
| `FindGrafana12JsonRisks` | dashboard/plugin JSON 风险 | legacy alert、Angular panel/metadata、CloudWatch alias、Loki transform、UID |
| `FindGrafana12TextRisks` | INI/env/Dockerfile/API 风险 | 精确行 marker、数据库/密钥、API key、CLI plugin、动态 image，注释 NO-OP |
| 推荐 declarative recipe | 组合 AUTO 与 MARK | 真实仓库 fixtures、官方 provisioning shape、recipe discovery/validation、no-op 与双 cycle 幂等 |

当前共 `156` 个测试，覆盖 before→after、精确 marker 节点、重复/冲突/FORCE owner、YAML/JSON 泛型同名字段反例、`.env*` profile/`export`/引号、Dockerfile 两种 `ENV` 语法与多 owner 反例、Spring provisioning/dashboard/plugin 识别、带参数 CLI/API 所有权、生成目录与 `install.*` 叶文件边界、真实仓库固定提交形态和最终推荐入口。测试断言 marker 的业务信息与挂载位置，而不是把搜索标记误当成配置修改。

## 固定上游依据

目标版本 `v12.1.1` 固定到 peeled commit [`df5de8219b41d1e639e003bf5f3a85913761d167`](https://github.com/grafana/grafana/tree/df5de8219b41d1e639e003bf5f3a85913761d167)。四个源 release 也分别固定为：

- [`v7.4.5` / `8a2c78d3f82ab36c1c53e745a755f0e63f01f360`](https://github.com/grafana/grafana/tree/8a2c78d3f82ab36c1c53e745a755f0e63f01f360)；
- [`v7.5.16` / `c0e2ad126c0e83928f3a358e159f442f21cf8d08`](https://github.com/grafana/grafana/tree/c0e2ad126c0e83928f3a358e159f442f21cf8d08)；
- [`v8.5.14` / `5bc88988a5a25c23452249315e8789ef059a2a3d`](https://github.com/grafana/grafana/tree/5bc88988a5a25c23452249315e8789ef059a2a3d)；
- [`v9.1.7` / `0cbb79298dd9edd8f5eb067d663de3b73cff3242`](https://github.com/grafana/grafana/tree/0cbb79298dd9edd8f5eb067d663de3b73cff3242)。

不兼容点和 AUTO 边界逐项对照目标固定提交中的：

- [v9 upgrade guide](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/docs/sources/upgrade-guide/upgrade-v9.0/index.md)：Elasticsearch browser access、最低版本与 envelope encryption；
- [v10 breaking changes](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/docs/sources/breaking-changes/breaking-changes-v10-0.md)：Angular、legacy alerting、service accounts、dashboard previews 与 OAuth identity；
- [v11 breaking changes](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/docs/sources/breaking-changes/breaking-changes-v11-0.md)：Angular 默认关闭、legacy alerting 删除及 `10.4.x` 跳板；
- [v12 upgrade guide](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/docs/sources/upgrade-guide/upgrade-v12.0/index.md)：严格 data source UID；
- [target plugin configuration](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/docs/sources/setup-grafana/configure-grafana/_index.md)、[`defaults.ini`](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/conf/defaults.ini)、[Docker `run.sh`](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/packaging/docker/run.sh) 与 [`setting_plugins.go`](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/pkg/setting/setting_plugins.go)：`GF_INSTALL_PLUGINS` 的正式弃用/迁移、FORCE 分支、`preinstall`/`preinstall_sync` 和目标配置所有权；
- [v12.1 upgrade guide](https://github.com/grafana/grafana/blob/df5de8219b41d1e639e003bf5f3a85913761d167/docs/sources/upgrade-guide/upgrade-v12.1/index.md)：目标 minor release 的备份与通用升级边界。

## 真实固定提交夹具与 OpenRewrite 测试参考

测试从公开工程固定提交提取真实形态并保留足以验证 owner 的上下文：

- [`netsampler/goflow2@6dee964`](https://github.com/netsampler/goflow2/blob/6dee964c38ee5f6b04a38681d069427c28ee5cb3/compose/kcg/grafana/Dockerfile)：多阶段 Dockerfile 中的 `grafana/grafana:9.1.7`；
- 同一固定提交的 [`docker-compose.yml`](https://github.com/netsampler/goflow2/blob/6dee964c38ee5f6b04a38681d069427c28ee5cb3/compose/kcg/docker-compose.yml)：sequence environment 中的 `GF_INSTALL_PLUGINS`；
- [`OpenBMP/obmp-docker@3f38af5`](https://github.com/OpenBMP/obmp-docker/blob/3f38af5312ae4b99cc3b3dcc6f54f6909439579d/docker-compose.yml)：表格镜像与匿名访问同时存在，验证同一 cycle 的 AUTO + MARK；
- [`strimzi/strimzi-kafka-operator@205ce5a`](https://github.com/strimzi/strimzi-kafka-operator/blob/205ce5aa3143c5fd76cbd53da5aa966ef3d069d7/examples/metrics/grafana-install/grafana.yaml)：Kubernetes Deployment 中的 `grafana/grafana:7.4.5`；
- [`temporalio/samples-server@ca1106b`](https://github.com/temporalio/samples-server/blob/ca1106b647c34323876bd6f221f4310271096dd8/compose/deployment/grafana/Dockerfile)：Dockerfile 中的 `grafana/grafana:7.5.16`；
- [`sylvek/domotik@b4835a2`](https://github.com/sylvek/domotik/blob/b4835a2b3874005103f5eb727ba51d0bad35b84e/grafana/Dockerfile)：Dockerfile 中的 `grafana/grafana:8.5.14`。

测试组织固定参考 [`openrewrite/rewrite@d4ac42e`](https://github.com/openrewrite/rewrite/tree/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc) 的 [YAML ChangeValueTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-yaml/src/test/java/org/openrewrite/yaml/ChangeValueTest.java)、[YAML FindKeyTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-yaml/src/test/java/org/openrewrite/yaml/search/FindKeyTest.java)、[JSON ChangeValueTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [PlainText FindTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-core/src/test/java/org/openrewrite/text/FindTest.java)，并补充 declarative recipe discovery/validation 与双 cycle 幂等断言。

## 使用与验证

先 dry-run 并审查所有 patch/SearchResult：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-grafana-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.grafana.MigrateGrafanaTo12_1_1
```

建议按 `7/8/9 → 10.4.x → 12.1.1` 的实际风险制定 staged plan；尤其不要绕过 legacy-alert migration。至少验证：数据库恢复、secret/KMS、HA rolling restart、告警与通知、登录/OAuth、匿名权限、所有 dashboard/data source/plugin、Elasticsearch/Loki/CloudWatch 查询、镜像 digest/signature、API/service-account 自动化和 downgrade 演练。

本模块独立验证：

```bash
mvn -f rewrite-grafana-upgrade/pom.xml clean verify
```
