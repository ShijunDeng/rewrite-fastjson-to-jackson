package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class NgxTranslateModuleLoaderTypeMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesExclusiveTypeOnlyTranslationImportAndReferences() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import type { Translation } from '@larscom/ngx-translate-module-loader';\nconst value: Translation = {};\n",
                "import type { TranslationObject } from '@ngx-translate/core';\nconst value: TranslationObject = {};\n",
                source -> source.path("src/types.ts")));
    }

    @Test
    void migratesRuntimeImportAndAllUnambiguousTypeReferences() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import { Translation } from \"@larscom/ngx-translate-module-loader\";\nexport function load(value: Translation): Promise<Translation> { return Promise.resolve(value); }\n",
                "import { TranslationObject } from \"@ngx-translate/core\";\nexport function load(value: TranslationObject): Promise<TranslationObject> { return Promise.resolve(value); }\n",
                source -> source.path("src/loader.ts")));
    }

    @Test
    void preservesLocalAliasWhileChangingOwnedExportAndModule() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import type { Translation as AppTranslation } from '@larscom/ngx-translate-module-loader';\nexport type State = AppTranslation;\n",
                "import type { TranslationObject as AppTranslation } from '@ngx-translate/core';\nexport type State = AppTranslation;\n",
                source -> source.path("src/state.ts")));
    }

    @Test
    void migratesTheExactPublishedLegacyDeepTypeEntry() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import { Translation } from '@larscom/ngx-translate-module-loader/lib/translation';\nconst dictionary: Translation = { home: 'Home' };\n",
                "import { TranslationObject } from '@ngx-translate/core';\nconst dictionary: TranslationObject = { home: 'Home' };\n",
                source -> source.path("src/dictionary.ts")));
    }

    @Test
    void modelsTheOfficialLegacyExportShape() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import type { Translation } from '@larscom/ngx-translate-module-loader';\ninterface ModuleTranslation { moduleName: string; translation: Translation; }\nconst byLocale: Record<string, Translation> = {};\n",
                "import type { TranslationObject } from '@ngx-translate/core';\ninterface ModuleTranslation { moduleName: string; translation: TranslationObject; }\nconst byLocale: Record<string, TranslationObject> = {};\n",
                source -> source.path("src/module-translation.ts")));
    }

    @Test
    void doesNotRewriteMixedImportBecauseTheWholeDeclarationIsNotProvablyMovable() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import { Translation, ModuleTranslateLoader } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\nnew ModuleTranslateLoader(http, options);\n",
                source -> source.path("src/mixed.ts")));
    }

    @Test
    void doesNotInventAReplacementForTranslationKey() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import type { TranslationKey } from '@larscom/ngx-translate-module-loader';\nlet key: TranslationKey;\n",
                source -> source.path("src/key.ts")));
    }

    @Test
    void protectsExistingTargetBindingAndShadowDeclarations() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nimport type { TranslationObject } from '@ngx-translate/core';\nlet value: Translation;\n",
                        source -> source.path("src/collision.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\ntype Translation = Record<string, string>;\nlet value: Translation;\n",
                        source -> source.path("src/shadow.ts")));
    }

    @Test
    void protectsSameNamedValuesMethodsAndProperties() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nconst Translation = factory();\nlet value: Translation;\n",
                        source -> source.path("src/value.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nservice.Translation();\nlet value: Translation;\n",
                        source -> source.path("src/method.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nconst config = { Translation: true };\nlet value: Translation;\n",
                        source -> source.path("src/property.ts")));
    }

    @Test
    void skipsDefaultNamespaceSideEffectReexportSimilarAndArbitraryDeepImports() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()),
                typescript("import Translation from '@larscom/ngx-translate-module-loader';", source -> source.path("src/default.ts")),
                typescript("import * as Translation from '@larscom/ngx-translate-module-loader';", source -> source.path("src/namespace.ts")),
                typescript("import '@larscom/ngx-translate-module-loader';", source -> source.path("src/effect.ts")),
                typescript("export { Translation } from '@larscom/ngx-translate-module-loader';", source -> source.path("src/export.ts")),
                typescript("import type { Translation } from '@company/ngx-translate-module-loader';", source -> source.path("src/similar.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader/private/types';", source -> source.path("src/private.ts")));
    }

    @Test
    void onlyMigratesAUniqueOwnedCandidate() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()), typescript(
                "import type { Translation } from '@larscom/ngx-translate-module-loader';\nimport type { Translation as DeepTranslation } from '@larscom/ngx-translate-module-loader/lib/translation';\nlet a: Translation; let b: DeepTranslation;\n",
                source -> source.path("src/duplicate.ts")));
    }

    @Test
    void excludesGeneratedAndInstallParentsButNotLeafFileNames() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\n",
                        source -> source.path("generated-client/types.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\n",
                        source -> source.path("Installer/cache/types.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\n",
                        "import type { TranslationObject } from '@ngx-translate/core';\nlet value: TranslationObject;\n",
                        source -> source.path("src/install.ts")),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\n",
                        "import type { TranslationObject } from '@ngx-translate/core';\nlet value: TranslationObject;\n",
                        source -> source.path("src/generated.ts")));
    }

    @Test
    void realRepositoryMixedImportsRemainForPreciseAudit() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports()),
                // KonsumGandalf/rsdp@cd46193bab67a9790ac8837869f872ae2e918c69
                typescript("import { IModuleTranslationOptions, ModuleTranslateLoader } from '@larscom/ngx-translate-module-loader';\nexport function loader(http: any, options: IModuleTranslationOptions) { return new ModuleTranslateLoader(http, options); }\n",
                        source -> source.path("fixtures/rsdp/translation.module.ts")),
                // thomas-chu-30/angular-translate@68f5bab9009cbc5a2d346ca5c7ee2e95e3672a9f
                typescript("import { ModuleTranslateLoader, IModuleTranslationOptions } from '@larscom/ngx-translate-module-loader';\nexport function HttpLoaderFactory(http: any, options: IModuleTranslationOptions) { return new ModuleTranslateLoader(http, options); }\n",
                        source -> source.path("fixtures/angular-translate/app.module.ts")));
    }

    @Test
    void automaticTypeMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateNgxTranslateModuleLoaderTypeImports())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\n",
                        "import type { TranslationObject } from '@ngx-translate/core';\nlet value: TranslationObject;\n",
                        source -> source.path("src/types.ts")));
    }
}
