# AntV X6 3.1.7 migration recipes

This module upgrades the XLSX-selected `@antv/x6` releases to `3.1.7`. It separates a narrow dependency recipe from a recommended migration recipe so build automation can choose whether to apply source migrations and review markers.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `com.huawei.clouds.openrewrite.antvx6.UpgradeAntvX6To3_1_7` | Upgrade only direct `package.json` declarations from the XLSX whitelist. |
| `com.huawei.clouds.openrewrite.antvx6.MigrateAntvX6To3_1_7` | Run the strict upgrade, migrate documented named imports, and mark manifest, lockfile, configuration, source, runtime, animation, and framework decisions. |

The exact source whitelist is `1.30.0`, `1.31.0`, `1.34.14`, `1.34.5`, `2.0.2`, `2.11.1`, and `2.11.3`. Exact, caret, and tilde declarations keep their style: for example, `^2.11.3` becomes `^3.1.7`. Comparators, OR and hyphen ranges, prereleases, build metadata, wildcards, variables, npm aliases, workspaces, files, links, Git URLs, HTTP archives, unlisted versions, overrides, resolutions, scripts, and lockfiles are never version-rewritten by the strict recipe.

## Automatically migrated

The recommended recipe applies only transformations documented as direct equivalents by the X6 3.x upgrade guide:

- named imports from the eleven `@antv/x6-plugin-*` packages listed below move to `@antv/x6`;
- named imports from `@antv/x6-common` and `@antv/x6-geometry` move to `@antv/x6`;
- TypeScript `import type`, aliases, quote style, and the imported names are preserved.

The consolidated plugin packages are selection, transform, scroller, keyboard, history, clipboard, snapline, dnd, minimap, stencil, and export. Default, namespace, side-effect, CommonJS, and dynamic imports are deliberately not guessed.

## Incompatible changes surfaced for review

Each marker is attached to the smallest provable AST or lockfile element:

- X6 3 enables `Graph` panning by default. Constructors without an explicit `panning` option are marked for pointer, Selection, and Scroller regression tests.
- X6 3 removes the 2.x `transition` API in favor of `animate`. Calls are marked only on cells obtained from a tracked X6 graph.
- `@antv/x6-react-shape` replaces `Portal.getProvider()` with the named `getProvider()` export. Owned calls are marked for import and React lifecycle migration.
- React, Vue, and Angular shape packages must use compatible 3.x releases. Their direct declarations are marked rather than assigned an undocumented patch version.
- Consolidated packages should be removed after every static and dynamic consumer is migrated. Their declarations are marked; automatic removal could break consumers hidden behind dynamic resolution.
- `lib`, `es`, and `src` deep imports and aliases couple applications to package layout. They are marked in TypeScript/JavaScript, JSON configuration, and executable Vite/Webpack/Rollup/Jest configuration.
- `@antv/x6` 3.1.7 declares Node.js `>=20.0.0`. An incompatible or ambiguous `engines.node` constraint is marked for local, CI, image, and deployment alignment.
- npm, Yarn, and pnpm lock entries are marked for regeneration. The recipe never fabricates resolved URLs, integrity hashes, peer snapshots, or package-manager metadata.
- complex, prerelease, dynamic, protocol-based, and unlisted `@antv/x6` constraints are marked for an explicit version decision.

`node_modules`, `dist`, `build`, `out`, `generated`, `install`, `.next`, `.nuxt`, `.cache`, `.yarn`, `.mvn`, `.m2`, `coverage`, `target`, and `vendor` path components are excluded from both changes and markers.

## Specification-to-recipe-to-test map

| Evidence or migration rule | Implementation | Regression coverage |
| --- | --- | --- |
| XLSX whitelist and target `3.1.7` | `UpgradeSelectedAntvX6Dependency` | `AntvX6DependencyTest`: all seven exact/caret/tilde inputs, exact set equality, four dependency sections, 25 complex/dynamic/protocol NOOP cases, path exclusions, discovery, validation, and idempotency. |
| X6 3 plugin/common/geometry consolidation | `MigrateConsolidatedAntvX6Imports` | `AntvX6ImportMigrationTest`: all 13 packages, type-only and aliased imports, quote preservation, unsafe-form NOOP, path exclusions, and idempotency. |
| package constraints, consolidated/shape packages, Node 20, npm lockfiles, tsconfig aliases | `FindAntvX6JsonRisks` | `AntvX6JsonAndLockRiskTest`: precise positive and negative manifests, npm lock v1/v3, unchanged hashes, config aliases, and excluded paths. |
| Yarn and pnpm lockfiles | `FindAntvX6TextLockRisks` | `AntvX6JsonAndLockRiskTest`: exact entries, preserved integrity data, similar-package negatives, and generated-tree exclusions. |
| panning, transition, Provider, dynamic loads, unsafe imports, executable config | `FindAntvX6JavaScriptRisks` | `AntvX6SourceRiskTest`: ownership positives, same-name negatives, explicit panning, public CSS, internal paths, generated trees, and idempotency. |
| End-to-end real repository behavior | `MigrateAntvX6To3_1_7` | `AntvX6RecommendedRecipeTest`: pinned X6, XFlow, rxdrag, and OpenRewrite fixtures plus target NOOP, discovery, validation, and two-cycle idempotency. |

## Pinned primary sources and real fixtures

- [X6 3.x upgrade guide at the v3.1.7 commit](https://github.com/antvis/X6/blob/2c46438298e3aeb54549b8ddda25b934f9da7131/site/docs/tutorial/update.en.md) defines package consolidation, import changes, removed `transition`, default panning, shape-package alignment, and the React Provider change.
- [X6 3.1.7 package metadata](https://github.com/antvis/X6/blob/2c46438298e3aeb54549b8ddda25b934f9da7131/package.json) supplies the exact package version, published directories, and Node.js engine requirement.
- [AntV XFlow type imports](https://github.com/antvis/XFlow/blob/faa44bf048c92ac14108f10c0a9a3bffb5e4cd2f/packages/core/src/types/index.ts) exercise real Scroller and Selection type-only imports.
- [rxdrag graph initialization](https://github.com/codebdy/rxdrag/blob/6759ce350edb5a822c88f7c2c73275b6662f4206/packages/minions/editor/src/hooks/useCreateGraph.ts) exercises real Graph, Selection, MiniMap, and default-panning behavior.
- [OpenRewrite JavaScript import parser tests](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java) provide fixed AST formatting cases for named imports, aliases, and mixed spacing.

## Verification

From the repository root:

```shell
mvn -f rewrite-antv-x6-upgrade/pom.xml clean verify
```

Run the recommended recipe through the OpenRewrite Maven or Gradle plugin using its fully qualified name. Review every `SearchResult` marker, regenerate the lockfile with the repository's own package-manager version, and run compile, type-check, unit, integration, browser-interaction, visual, SSR/hydration, and production-bundle tests before accepting the upgrade.
