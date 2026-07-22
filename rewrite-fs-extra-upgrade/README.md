# fs-extra 11.3.4 OpenRewrite recipes

This module migrates only the `fs-extra` versions explicitly visible in `开源软件升级.xlsx` to `11.3.4`. It separates a deliberately narrow dependency edit from a recommended migration recipe that applies one documented ESM rewrite and places review markers exactly where runtime, module, option, lockfile, or filesystem semantics need human decisions.

## Spreadsheet contract

The worksheet contains six `fs-extra` rows: `8.1.0`, `9.1.0`, `10.0.0`, `10.0.1`, `10.1.0`, and `11.1.1`, all targeting `11.3.4`. The implementation stores this exact set and does not infer versions hidden by ellipses, neighboring releases, semver compatibility, or registry metadata.

`com.huawei.clouds.openrewrite.fsextra.UpgradeFsExtraTo11_3_4` changes only a direct `fs-extra` entry in `dependencies`, `devDependencies`, `peerDependencies`, or `optionalDependencies` of a project `package.json`:

| Input | Output |
| --- | --- |
| `"8.1.0"` | `"11.3.4"` |
| `"^10.1.0"` | `"^11.3.4"` |
| `"~11.1.1"` | `"~11.3.4"` |

All six source versions accept exact, caret, and tilde forms. Comparator sets, OR and hyphen ranges, wildcards, prereleases, build metadata, variables, `workspace:`, npm aliases, local paths, links, Git/HTTP declarations, unlisted versions, target declarations, overrides, resolutions, scripts, lockfiles, fixtures, and similarly named packages are unchanged by the strict recipe.

## Incompatibilities covered

The official changelog establishes these migration boundaries between the selected inputs and 11.3.4:

- v9 requires Node 10, changes recursive directory creation, properly preserves `atime` for timestamp-preserving copies, snapshots `outputJson` values when called, and rejects `null` JSON options.
- v10 requires Node 12, allows broken-symlink copies, validates existing `ensureLink*` and `ensureSymlink*` destinations by type, rejects unknown copied file types, removes undocumented `remove*` options, and changes several copy/filter and platform behaviors.
- v11 requires Node `14.14+`, exposes only `fs-extra` and `fs-extra/esm`, and therefore blocks every `fs-extra/lib/...` deep import. The `/esm` entry has named exports only for fs-extra-specific methods; native `fs` methods belong to `node:fs` or `node:fs/promises`.
- v11.1 preserves timestamps for cross-device moves; v11.2 copies directory contents concurrently; v11.3 uses `fs.opendir` and expands promise support. Copy filters with ordering or side effects, large trees, links, metadata, and error handling require regression tests even though their signatures did not change.
- v11.3.4 fixes relative-source `ensureSymlink*` behavior when the link already exists. Existing link and Windows permission cases still need explicit validation.

## Recommended migration behavior

`com.huawei.clouds.openrewrite.fsextra.MigrateFsExtraTo11_3_4` composes the strict upgrade with the following actions.

AUTO:

- A pure ESM named import from `fs-extra` is changed to `fs-extra/esm` only when every imported binding is one of the fs-extra-specific names exported by 11.3.4. Aliases, quote style, and spacing are preserved.
- Mixed native/fs-extra imports, default imports, namespace imports, CommonJS, side-effect imports, deep imports, unknown names, and similar packages remain unchanged.

MARK:

- A direct target dependency is marked when `engines.node` is absent or does not prove that all supported runtimes are at least Node 14.14. Exact/caret/tilde/`>=` stable floors and ORs made only from those simple ranges are evaluated; prerelease floors and compound comparator/hyphen expressions are deliberately marked as ambiguous instead of guessing. The engine declaration itself is marked when it admits or ambiguously describes older Node versions.
- Complex or unlisted constraints skipped by the strict recipe, `@types/fs-extra`, top-level package-manager overrides/resolutions (including nested npm override graphs), and JSON strings containing physical `fs-extra/lib` paths are marked at the owning value.
- npm, Yarn, and pnpm entries for the exact `fs-extra` package are marked for regeneration, including nested `node_modules/.../node_modules/fs-extra` package-lock keys. Versions, resolved URLs, integrity hashes, dependency snapshots, peer metadata, and package-manager layout are never fabricated. `@types/fs-extra`, scoped packages, and similarly named package lock keys are not mistaken for `fs-extra`.
- Deep imports/requires/build aliases are marked at the exact module literal. Native fs named imports and native method access through `fs-extra/esm` are directed to `node:fs` or `node:fs/promises` with an explicit callback/sync/promise choice.
- Owned `copy*`, link/symlink operations, `outputJson*`, `null` JSON options, and removed object options passed to `remove*` receive operation-specific markers. Local same-name functions and safe public CommonJS uses are not marked.

Both recipes exclude installed, generated, vendored, cached, coverage, and build output trees, including `node_modules`, `vendor`, `dist`, `build`, `out`, `generated`, `install`, `.next`, `.nuxt`, `.cache`, `.mvn`, `.m2`, `.yarn`, `coverage`, and `target`.

## Specification-to-test traceability

| Rule or evidence | Implementation | Regression coverage |
| --- | --- | --- |
| Six-value XLSX whitelist and target | `UpgradeSelectedFsExtraDependency` | `FsExtraDependencyTest`: all exact/caret/tilde values, set equality, four sections, complex/protocol negatives, exclusions, discovery, validation, and idempotency. |
| Documented v11 named ESM entry | `MigrateDeterministicFsExtraImports` | `FsExtraImportMigrationTest`: aliases, quotes, OpenRewrite spacing, mixed native imports, module forms, protected paths, and two-cycle idempotency. |
| Runtime, constraint, type, override, and npm lock decisions | `FindFsExtraManifestRisks` | `FsExtraManifestAndLockRiskTest`: compatible/incompatible Node matrices, skipped ranges, types, overrides, physical paths, npm v1/v3, preserved integrity, and negatives. |
| Yarn and pnpm exact package keys | `FindFsExtraTextLockRisks` | `FsExtraManifestAndLockRiskTest`: exact keys, slash keys, scoped/similar negatives, preserved hashes, and generated/install exclusions. |
| Export map and filesystem API behavior | `FindFsExtraJavaScriptRisks` | `FsExtraSourceRiskTest`: deep loading, native ESM APIs, named/default/namespace/CommonJS ownership, copy/link/remove/JSON cases, shadowing, exclusions, and marker idempotency. |
| End-to-end fixed repository behavior | `MigrateFsExtraTo11_3_4` | `FsExtraRecommendedRecipeTest`: fixed fs-extra v8/v11, PatternFly, lockfile, and OpenRewrite fixtures; modern NOOP, discovery, validation, and idempotency. |

## Pinned primary sources and real fixtures

- [fs-extra 11.3.4 changelog](https://github.com/jprichardson/node-fs-extra/blob/353a29b18c883fa0f3997fd8be90a89077633af4/CHANGELOG.md) defines every intervening breaking change and the target bug fixes.
- [fs-extra 11.3.4 package metadata](https://github.com/jprichardson/node-fs-extra/blob/353a29b18c883fa0f3997fd8be90a89077633af4/package.json) fixes the target Node floor and public exports map; [its ESM entry](https://github.com/jprichardson/node-fs-extra/blob/353a29b18c883fa0f3997fd8be90a89077633af4/lib/esm.mjs) fixes the named-export inventory and native-fs exclusion.
- [fs-extra 8.1.0 package metadata](https://github.com/jprichardson/node-fs-extra/blob/b7df7cce3f7ca5bc0ab85110aa997bd0ad33482f/package.json) supplies the real legacy Node floor and unrestricted `lib/index.js` baseline.
- [PatternFly React Topology's fixed build script](https://github.com/patternfly/react-topology/blob/e795e7b46a764993a01b98085e06e072d2c6d626/packages/module/scripts/writeClassMaps.js) supplies a real root `fs-extra` require beside `fs-extra/lib/mkdirs`.
- [OpenRewrite JavaScript import parser tests](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java) supply fixed aliases, quote, and spacing AST forms.

## Verification and use

From the repository root:

```shell
mvn -f rewrite-fs-extra-upgrade/pom.xml clean verify
```

Activate the recommended recipe by its fully qualified name with the OpenRewrite Maven or Gradle plugin. Review every `SearchResult`, select and install a Node runtime of at least 14.14, regenerate lockfiles with the repository's own package-manager version, and run type-checking, linting, unit/integration tests, copy/link tests on supported filesystems and operating systems, large-tree and filter-order tests, packaging/bundling, container builds, and deployment smoke tests before accepting the migration.
