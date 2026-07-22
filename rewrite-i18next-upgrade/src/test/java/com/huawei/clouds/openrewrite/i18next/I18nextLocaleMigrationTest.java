package com.huawei.clouds.openrewrite.i18next;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.yaml.Assertions.yaml;

class I18nextLocaleMigrationTest implements RewriteTest {
    @Test
    void convertsOfficialEnglishConverterPairsIncludingReverseOrderAndNesting() {
        // i18next/i18next-v4-format-converter @ f18d4f5424994c5f34ab535f7ba82ca617e02616
        rewriteRun(
                spec -> spec.recipe(new MigrateEnglishV3PluralJson()),
                json(
                        """
                        {
                          "myKey": "item",
                          "myKey_plural": "items",
                          "reverse_plural": "many",
                          "reverse": "one",
                          "nested": {"friend": "friend", "friend_plural": "friends"}
                        }
                        """,
                        """
                        {
                          "myKey_one": "item",
                          "myKey_other": "items",
                          "reverse_other": "many",
                          "reverse_one": "one",
                          "nested": {"friend_one": "friend", "friend_other": "friends"}
                        }
                        """,
                        source -> source.path("src/locales/en/translation.json"))
        );
    }

    @Test
    void preservesKeyQuoteAndFormatting() {
        rewriteRun(
                spec -> spec.recipe(new MigrateEnglishV3PluralJson()),
                json("{'car': 'car', 'car_plural': 'cars'}",
                        "{'car_one': 'car', 'car_other': 'cars'}",
                        source -> source.path("public/langs/en-US.json"))
        );
    }

    @Test
    void leavesIncompleteNumericNonStringAndConflictingEnglishKeys() {
        rewriteRun(
                spec -> spec.recipe(new MigrateEnglishV3PluralJson()),
                json(
                        """
                        {
                          "only": "one",
                          "missing_plural": "many",
                          "item": "one",
                          "item_plural": "many",
                          "item_one": "already",
                          "count": 1,
                          "count_plural": 2,
                          "dog_0": "dog",
                          "dog_1": "dogs"
                        }
                        """,
                        source -> source.path("locales/en.json"))
        );
    }

    @Test
    void leavesNonEnglishAndNonLocaleJson() {
        rewriteRun(
                spec -> spec.recipe(new MigrateEnglishV3PluralJson()),
                json("{\"car\":\"Auto\",\"car_plural\":\"Autos\"}",
                        source -> source.path("src/locales/de.json")),
                json("{\"car\":\"car\",\"car_plural\":\"cars\"}",
                        source -> source.path("fixtures/en.json"))
        );
    }

    @Test
    void marksRemainingJsonPluralSuffixesAtLocaleKeys() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJsonLocaleRisks()),
                json("{\"friend_plural\":\"friends\",\"item_0\":\"item\",\"safe_one\":\"one\",\"safe_other\":\"many\"}",
                        source -> source.path("src/locales/fr.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Legacy i18next plural suffix remains"));
                                    assertTrue(printed.contains("friend_plural"));
                                    assertTrue(printed.contains("item_0"));
                                    assertFalse(printed.contains("~~(Legacy i18next plural suffix remains") &&
                                                printed.indexOf("safe_one") < printed.indexOf("~~>"));
                                }))
        );
    }

    @Test
    void marksRemainingYamlPluralSuffixes() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextYamlLocaleRisks()),
                yaml("friend_plural: friends\nitem_1: items\nsafe_other: many\n",
                        source -> source.path("translations/pl.yml").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll()
                                        .contains("Legacy i18next plural suffix remains"))))
        );
    }

    @Test
    void localeMarkersIgnoreModernKeysAndNonLocaleFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJsonLocaleRisks()),
                json("{\"item_one\":\"one\",\"item_other\":\"many\"}",
                        source -> source.path("locales/en.json")),
                json("{\"item_plural\":\"many\"}",
                        source -> source.path("fixtures/data.json"))
        );
    }

    @Test
    void EnglishConversionIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateEnglishV3PluralJson())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"book\":\"book\",\"book_plural\":\"books\"}",
                        "{\"book_one\":\"book\",\"book_other\":\"books\"}",
                        source -> source.path("i18n/en.json"))
        );
    }
}
