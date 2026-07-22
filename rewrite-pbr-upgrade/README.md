# pbr 升级到 7.0.3

本模块处理 Python 构建插件 `pbr`，不是名称相似的业务包。逐行读取 `开源软件升级.xlsx` 后，只有以下两个明确源版本：

| XLSX 行 | 序号 | 原始版本 | 目标版本 |
| --- | --- | --- | --- |
| 331 | 330 | `5.11.1` | `7.0.3` |
| 332 | 331 | `5.5.1` | `7.0.3` |

配方不会把白名单扩展到其他 5.x、6.x、动态范围或 Git 引用。

推荐入口：

```text
com.huawei.clouds.openrewrite.pbr.MigratePbrTo7_0_3
```

仅迁移精确版本的低层入口：

```text
com.huawei.clouds.openrewrite.pbr.UpgradePbrTo7_0_3
```

## spec → recipe → test

| 规格 | 配方/实现 | 主要测试 |
| --- | --- | --- |
| 两个 XLSX 版本精确升级 | `UpgradeSelectedPbrDependency` | `upgradesEveryVisibleWorkbookSource`、精确 whitelist、12 个未列版本 NOOP |
| Python 依赖所有权 | `UpgradeSelectedPbrDependency` | requirements/constraints、setup.py/setup.cfg/tox、pyproject/Poetry/Pipfile、Conda、Docker before→after |
| 动态版本与生成物边界 | `UpgradeSelectedPbrDependency`、`FindPbrMigrationRisks` | 8 类 range/VCS/bare MARK；lock/vendor/.tox/build/dist/history NOOP |
| setup.cfg 横线键规范化 | `MigratePbrSetupCfg` | 9 个官方键、CRLF、注释、冲突 NOOP、固定上游 fixture |
| setuptools 原生配置别名 | `MigratePbrSetupCfg` | metadata 四组 alias、`[entry_points]`、目标键/双 section 冲突 |
| 删除的命令与 compiler | `FindPbrMigrationRisks` | `test`/`build_sphinx` 多 owner MARK、`[global] compilers` MARK |
| 删除或重组的内部模块 | `FindPbrMigrationRisks` | 7 类 Python import MARK、稳定 `pbr.version` NOOP |
| 旧配置所有权 | `FindPbrMigrationRisks` | `[files]`/`[backwards_compat]`、8 个 metadata 字段、test alias MARK |
| recipe 质量 | declarative `rewrite.yml` | discovery/validation、AUTO 与 MARK 两轮幂等、147 个测试 |

## AUTO 与 MARK

| 不兼容点/边界 | 行为 | 原则 |
| --- | --- | --- |
| 精确 `pbr==5.11.1`、`pbr==5.5.1` | **AUTO** | 只改版本 token，保留空白、CRLF、marker、hash、注释和续行 |
| `author-email` 等已知横线键 | **AUTO** | 只在其官方 section 内归一化；上游因 setuptools 弃用横线 spelling 而改为下划线，有目标键、重复源键或重复 section 冲突则不改 |
| `home_page`、`summary`、`classifier`、`platform` | **AUTO** | 只在 `[metadata]` 同 section、目标键不存在时改到官方原生别名 |
| `[entry_points]` | **AUTO** | 仅当 `[options.entry_points]` 不存在时一对一改 section 名 |
| range、VCS、bare pin、未列版本 | **MARK** | 不猜测 constraints、镜像、CI 或上游依赖的真正版本 owner |
| lock、vendor、虚拟环境、构建产物 | **NOOP** | 由对应包管理器重建，禁止直接修改生成结果 |
| `[files]`、`[backwards_compat]` 与结构性字段迁移 | **MARK** | 跨 section 搬移涉及 package discovery、data files、namespace、wheel 内容，不能机械决定 |
| `description_file` 等 deprecated metadata | **MARK** | 需要在 `long_description` 或 `[project] readme` 之间按工程选择 |
| `setup.py test` / `build_sphinx` | **MARK** | pytest/stestr/sphinx-build 的参数、环境和退出语义不能由通用配方猜测 |
| `[global] compilers` | **MARK** | compiler/toolchain/platform wheel owner 必须人工确认 |
| `pbr.core`、`pbr.util` 等内部 import | **MARK** | 目标树已删除或重组这些实现模块，不假设私有函数仍有等价 API |
| distutils `setup` | **MARK** | pbr 是 setuptools 插件，需先迁移实际 build owner |

SearchResult 是明确的待决策点，不代表自动修复。

## 精确 Python 依赖所有权

低层配方只修改维护源码中的直接、精确 pin：

```text
pbr==5.11.1
pbr===5.5.1 ; python_version >= "3.9"
```

支持的 owner 包括：

- `requirements*.txt/.in`、`constraints*.txt/.in`，也支持 `dev-requirements.txt` 等前缀；
- `setup.py` 的精确 quoted requirement；
- `setup.cfg` / `tox.ini` 的依赖字段和列表；
- `pyproject.toml` build/project 数组及 Poetry exact key、`Pipfile` exact key；
- `environment.yml` / `conda*.yml` 的 pip requirement；
- Dockerfile 中明确的 `RUN ... pip install` 命令和续行。

每类 owner 都按自己的 section/key/command 语法收窄：例如只读取 PEP 517/621 dependency 数组、Poetry/Pipfile dependency section、INI dependency key、setup.py dependency keyword，以及真正执行的 Docker `pip install`。说明文字、任意 TOML table、entry point 值和仅 `echo` 出来的命令不是 AUTO 所有权。

以下不会自动修改：

- `pbr>=5.11.1`、`~=5.5.1`、compound range、bare `pbr`、URL/VCS、变量或其他版本；
- `poetry.lock`、`Pipfile.lock`、`uv.lock` 和其他 lock；
- `.tox`、`.nox`、`.venv`、`venv`、`site-packages`、vendor、build、dist、generated 等；
- `docs/releases` / `docs/snapshots` 历史 fixture、README 和普通字符串。

升级后由真正 owner 重新生成并核验：

```bash
python -m pip install --upgrade build pip setuptools wheel
python -m build
python -m pip install --force-reinstall dist/*.whl
python -m pip check
```

Poetry/Pipenv/uv/Conda 工程应使用自己的 lock 命令，不要手改 lock。

## 官方不兼容点

目标 tag `7.0.3` 解引用到固定提交 [`f4bc8350f2cbddd9d631e65327965579779ec454`](https://github.com/openstack/pbr/tree/f4bc8350f2cbddd9d631e65327965579779ec454)。两个源 tag 分别解引用到 [`5.11.1` 的 `98c84b5f87b89573ea6930bc783f07f2dd5ae8fd`](https://github.com/openstack/pbr/tree/98c84b5f87b89573ea6930bc783f07f2dd5ae8fd) 和 [`5.5.1` 的 `5150198c294802f82ddc7ed5765546653bc19adf`](https://github.com/openstack/pbr/tree/5150198c294802f82ddc7ed5765546653bc19adf)。

目标固定提交的官方 release notes 明确说明：

- [`setup.py build_sphinx` 集成已删除](https://github.com/openstack/pbr/blob/f4bc8350f2cbddd9d631e65327965579779ec454/releasenotes/notes/build_sphinx_removal-de990a5c14a9e64d.yaml)；
- [`setup.py test` 集成已删除](https://github.com/openstack/pbr/blob/f4bc8350f2cbddd9d631e65327965579779ec454/releasenotes/notes/test-command-removal-153fc9ecdd6834ef.yaml)；
- [`[global] compilers` 已删除](https://github.com/openstack/pbr/blob/f4bc8350f2cbddd9d631e65327965579779ec454/releasenotes/notes/global-compilers-removal-62b131e40de087ef.yaml)；
- [大量 pbr 专用 setup.cfg 字段已弃用](https://github.com/openstack/pbr/blob/f4bc8350f2cbddd9d631e65327965579779ec454/releasenotes/notes/setuptools-alignment-b5b1309f47e9cf98.yaml)，应迁到 setuptools 的 `[options]`、`[options.entry_points]` 或 `pyproject.toml` owner。

官方 [`using.rst`](https://github.com/openstack/pbr/blob/f4bc8350f2cbddd9d631e65327965579779ec454/doc/source/user/using.rst) 仍要求 `setuptools.setup(pbr=True)`；PEP 517/660 可使用 `build-backend = "pbr.build"`，但这不授权配方凭空选择 build frontend、setuptools 下限或删除已有 setup.py。目标的 [`requirements.txt`](https://github.com/openstack/pbr/blob/f4bc8350f2cbddd9d631e65327965579779ec454/requirements.txt) 已显式依赖 setuptools，尤其针对 Python 3.12 不再默认携带 setuptools 的环境。

目标发布元数据仍声明 `Requires-Python >=2.6`，因此本模块不会伪造新的 Python baseline MARK；真实兼容性必须由目标项目的 Python/setuptools/Sphinx/test/build matrix 验证。

从两个源提交到目标提交的官方树还删除了 `pbr/core.py`、`pbr/util.py`、`pbr/builddoc.py`、`pbr/testr_command.py`、`pbr/hooks/commands.py`。推荐配方只 MARK 真实 import，不把私有 API 猜成 `pbr.setupcfg`。横线配置键的 AUTO 依据官方固定提交 [`ee04b62de28061018496d6324639446afe2059c7`](https://github.com/openstack/pbr/commit/ee04b62de28061018496d6324639446afe2059c7)。

## 真实固定提交 fixture

测试不只使用合成片段，还保留公开仓库的真实所有权形态：

- [freedomofpress/securedrop.org `b718809dec323499cf883f3571cfc4e4fcb8941a`](https://github.com/freedomofpress/securedrop.org/blob/b718809dec323499cf883f3571cfc4e4fcb8941a/dev-requirements.txt)：`pbr==5.11.1` 的续行/hash 形态；
- [ascoderu/lokole `b453459596e87f5441463b88781e2791e29b20df`](https://github.com/ascoderu/lokole/blob/b453459596e87f5441463b88781e2791e29b20df/requirements-dev.txt)：带业务说明注释的精确 pin；
- [deeplearning-wisc/haloscope `283733a498c3b411277ca4b3a16ebf93b27416bf`](https://github.com/deeplearning-wisc/haloscope/blob/283733a498c3b411277ca4b3a16ebf93b27416bf/requirements.txt)：`5.5.1` 普通 requirements owner；
- [lablup/backend.ai-kernels `f8de34bac4cee01a9f9052b47a98a15c6892ad79`](https://github.com/lablup/backend.ai-kernels/blob/f8de34bac4cee01a9f9052b47a98a15c6892ad79/python-ff/Dockerfile.21.03-py38-cuda11.1)：Docker pip install 续行 owner；
- [tern-tools/tern `717ea47be7310d055b86fb1b80d39fb472c0ddbf`](https://github.com/tern-tools/tern/blob/717ea47be7310d055b86fb1b80d39fb472c0ddbf/docs/releases/v2_4_0-requirements.txt)：历史 release snapshot NOOP 边界；
- pbr 自身 `5.11.1` 测试包的 setup.cfg 形态用于配置 before→after fixture；
- 测试结构和 PlainText/SearchResult 断言参考 OpenRewrite 官方 [`FindAndReplaceTest` 固定提交 `433cf7d8e445cddb9aa1caf1956938f2563f0976`](https://github.com/openrewrite/rewrite/blob/433cf7d8e445cddb9aa1caf1956938f2563f0976/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-pbr-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.pbr.MigratePbrTo7_0_3
```

审查所有 patch/SearchResult 后，至少运行：

```bash
python -m build
python -m pip install --force-reinstall dist/*.whl
python -m pip check
pytest
sphinx-build -W -b html docs docs/_build/html
```

模块独立验证：

```bash
mvn -f rewrite-pbr-upgrade/pom.xml clean verify
```
