package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class VueI18nTemplateTest implements RewriteTest {
    @Test
    void migratesSafePairedAndSelfClosingTranslationComponents() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nTemplate()),
                text(
                        """
                        <i18n path="common.emqx" tag="p"><template #emqx><a>EMQX</a></template></i18n>
                        <i18n :path="messageKey" />
                        """,
                        """
                        <i18n-t keypath="common.emqx" tag="p"><template #emqx><a>EMQX</a></template></i18n-t>
                        <i18n-t :keypath="messageKey" />
                        """,
                        source -> source.path("src/EmptyPage.vue"))
        );
    }

    @Test
    void leavesPlaceSyntaxAndCustomLocaleBlocksForMarkerOrCompiler() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nTemplate()),
                text(
                        """
                        <i18n path="refund" :places="{ limit }"><a place="limit">{{ limit }}</a></i18n>
                        <i18n locale="en">{"hello":"Hello"}</i18n>
                        """,
                        source -> source.path("src/Legacy.vue"))
        );
    }

    @Test
    void templateMigrationSkipsCommentsAndIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nTemplate())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("<!-- <i18n path=\"old\" /> -->\n<i18n path=\"active\" />\n",
                        "<!-- <i18n path=\"old\" /> -->\n<i18n-t keypath=\"active\" />\n",
                        source -> source.path("src/Component.vue"))
        );
    }

    @Test
    void templateMigrationSkipsScriptAndStyleContents() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueI18nTemplate()),
                text(
                        "<script>const sample = '<i18n path=\"script.example\" />';</script>\n" +
                        "<style>/* <i18n path=\"style.example\" /> */</style>\n" +
                        "<template><i18n path=\"template.example\" /></template>\n",
                        "<script>const sample = '<i18n path=\"script.example\" />';</script>\n" +
                        "<style>/* <i18n path=\"style.example\" /> */</style>\n" +
                        "<template><i18n-t keypath=\"template.example\" /></template>\n",
                        source -> source.path("src/RawBlocks.vue"))
        );
    }

    @Test
    void marksUnsafeComponentVtTcAndChangedTranslationUse() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nTemplateRisks()),
                text(
                        """
                        <i18n path="refund" :places="{ limit }"><a place="limit">{{ limit }}</a></i18n>
                        <p v-t.preserve="'hello'">Hello</p>
                        <p>{{ $tc('items', count) }}</p>
                        <p>{{ $t('hello', 'ja') }}</p>
                        <p>{{ $t('errors')[code] }}</p>
                        """,
                        source -> source.path("src/Legacy.vue").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("place/places"));
                                    assertTrue(printed.contains("v-t is deprecated"));
                                    assertTrue(printed.contains("$tc was removed"));
                                    assertTrue(printed.contains("string second $t argument"));
                                    assertTrue(printed.contains("$t returns strings only"));
                                }))
        );
    }

    @Test
    void marksVue2BootstrapInsideSfcPlainText() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nTemplateRisks()),
                text("<script>import VueI18n from 'vue-i18n'; Vue.use(VueI18n); const i18n = new VueI18n({});</script>\n",
                        source -> source.path("src/Legacy.vue").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("default class import"));
                                    assertTrue(printed.contains("Vue.use(VueI18n)"));
                                    assertTrue(printed.contains("new VueI18n was removed"));
                                }))
        );
    }

    @Test
    void modernComponentsCustomBlockAndCommentsAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nTemplateRisks()),
                text(
                        """
                        <!-- <i18n path="old" /><p v-t="'old'">old</p> -->
                        <i18n-t keypath="hello"><template #name>World</template></i18n-t>
                        <i18n locale="en">{"hello":"Hello"}</i18n>
                        <p>{{ $t('hello') }}</p>
                        """,
                        source -> source.path("src/Modern.vue").afterRecipe(file ->
                                assertFalse(file.printAll().contains("~~>"))))
        );
    }

    @Test
    void templateMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nTemplateRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("<p v-t=\"'hello'\">Hello</p>\n",
                        source -> source.path("src/Legacy.vue").after(actual -> actual))
        );
    }
}
