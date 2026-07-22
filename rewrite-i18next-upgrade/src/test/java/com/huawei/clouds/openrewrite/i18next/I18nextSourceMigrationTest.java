package com.huawei.clouds.openrewrite.i18next;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class I18nextSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void renamesDirectDefaultImportInitOption() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                javascript(
                        "import i18next from 'i18next';\ni18next.init({ initImmediate: false, fallbackLng: 'en' });\n",
                        "import i18next from 'i18next';\ni18next.init({ initAsync: false, fallbackLng: 'en' });\n",
                        source -> source.path("src/i18n.js"))
        );
    }

    @Test
    void renamesQuotedOptionOnUseChain() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                typescript(
                        "import core from \"i18next\";\ncore.use(Backend).use(detector).init({ \"initImmediate\": true });\n",
                        "import core from \"i18next\";\ncore.use(Backend).use(detector).init({ \"initAsync\": true });\n",
                        source -> source.path("src/bootstrap.ts"))
        );
    }

    @Test
    void renamesTypedAliasedInitOptionsFromModuleFederationFixture() {
        // module-federation/module-federation-examples @ 9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                typescript(
                        """
                        import i18next, { InitOptions as I18nInitOptions } from 'i18next';
                        const initOptions: I18nInitOptions = {
                          ...options,
                          initImmediate: false,
                        };
                        const newInstance = i18next.createInstance().use(Backend).use(detector);
                        newInstance.init(initOptions);
                        """,
                        """
                        import i18next, { InitOptions as I18nInitOptions } from 'i18next';
                        const initOptions: I18nInitOptions = {
                          ...options,
                          initAsync: false,
                        };
                        const newInstance = i18next.createInstance().use(Backend).use(detector);
                        newInstance.init(initOptions);
                        """,
                        source -> source.path("i18next-shared-lib/src/i18nService.ts"))
        );
    }

    @Test
    void renamesDirectOptionOnTrackedCreatedInstance() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                typescript(
                        "import i18next from 'i18next';\nconst local = i18next.createInstance().use(plugin);\nlocal.init({ initImmediate: false });\n",
                        "import i18next from 'i18next';\nconst local = i18next.createInstance().use(plugin);\nlocal.init({ initAsync: false });\n",
                        source -> source.path("src/local.ts"))
        );
    }

    @Test
    void leavesDetachedUntypedAndUnrelatedOptions() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                typescript(
                        """
                        import i18next from 'i18next';
                        const detached = { initImmediate: false };
                        unrelated.init({ initImmediate: false });
                        i18next.init(detached);
                        """,
                        source -> source.path("src/conservative.ts"))
        );
    }

    @Test
    void leavesConflictingAndShadowedOwnedNamesForManualReview() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                typescript(
                        "import i18next from 'i18next';\ni18next.init({ initImmediate: false, initAsync: true });\n",
                        source -> source.path("src/conflict.ts")),
                typescript(
                        "import i18next from 'i18next';\nfunction configure(i18next: any) { i18next.init({ initImmediate: false }); }\n",
                        source -> source.path("src/shadowed.ts"))
        );
    }

    @Test
    void leavesSamePropertyWithoutI18nextImport() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource()),
                typescript("scheduler.init({ initImmediate: false });\n",
                        source -> source.path("src/scheduler.ts"))
        );
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicI18nextSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import i18next from 'i18next';\ni18next.init({ initImmediate: false });\n",
                        "import i18next from 'i18next';\ni18next.init({ initAsync: false });\n",
                        source -> source.path("src/idempotent.ts"))
        );
    }
}
