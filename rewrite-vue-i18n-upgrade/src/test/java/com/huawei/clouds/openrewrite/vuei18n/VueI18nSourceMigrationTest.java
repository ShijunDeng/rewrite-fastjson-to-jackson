package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class VueI18nSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesOwnedLegacyDateTimeFormatsInNewAndCreateOptions() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nSource()),
                javascript(
                        "import VueI18n from 'vue-i18n';\nconst i18n = new VueI18n({ locale: 'en', dateTimeFormats: formats });\n",
                        "import VueI18n from 'vue-i18n';\nconst i18n = new VueI18n({ locale: 'en', datetimeFormats: formats });\n",
                        source -> source.path("src/legacy-i18n.js")),
                typescript(
                        "import { createI18n } from 'vue-i18n';\nexport const i18n = createI18n({ legacy: false, 'dateTimeFormats': formats });\n",
                        "import { createI18n } from 'vue-i18n';\nexport const i18n = createI18n({ legacy: false, 'datetimeFormats': formats });\n",
                        source -> source.path("src/i18n.ts"))
        );
    }

    @Test
    void migratesOnlyUnambiguousNumericPluralCalls() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nSource()),
                typescript(
                        """
                        import { createI18n } from 'vue-i18n';
                        const i18n = createI18n({ legacy: true });
                        const one = i18n.global.tc('banana', 1);
                        class View { label() { return this.$tc('items', 2); } }
                        """,
                        """
                        import { createI18n } from 'vue-i18n';
                        const i18n = createI18n({ legacy: true });
                        const one = i18n.global.t('banana', 1);
                        class View { label() { return this.$t('items', 2); } }
                        """,
                        source -> source.path("src/plural.ts"))
        );
    }

    @Test
    void leavesAmbiguousPluralOverloadsAndUnrelatedPropertiesUntouched() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nSource()),
                typescript(
                        """
                        import { createI18n } from 'vue-i18n';
                        const i18n = createI18n({ legacy: true, messages: { en: { dateTimeFormats: 'literal key' } } });
                        i18n.global.tc('banana');
                        i18n.global.tc('banana', locale);
                        i18n.global.tc('banana', 2, 'ja');
                        const calendar = { dateTimeFormats: formats };
                        """,
                        source -> source.path("src/boundary.ts"))
        );
    }

    @Test
    void leavesConflictingLegacyAndCurrentDateTimeFormatKeysUntouched() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nSource()),
                typescript(
                        "import { createI18n } from 'vue-i18n';\n" +
                        "const i18n = createI18n({ dateTimeFormats: legacy, datetimeFormats: current });\n",
                        source -> source.path("src/conflicting-formats.ts"))
        );
    }

    @Test
    void doesNotTouchSimilarUnownedTcCalls() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nSource()),
                typescript(
                        "import { createI18n } from 'vue-i18n';\nconst metrics = { tc(key: string, count: number) {} }; metrics.tc('jobs', 2);\n",
                        source -> source.path("src/metrics.ts"))
        );
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { createI18n } from 'vue-i18n';\nconst i18n = createI18n({ legacy: false, dateTimeFormats: formats });\n",
                        "import { createI18n } from 'vue-i18n';\nconst i18n = createI18n({ legacy: false, datetimeFormats: formats });\n",
                        source -> source.path("src/i18n.ts"))
        );
    }
}
