package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.yaml.Assertions.yaml;

class VueI18nLocaleMessageRiskTest implements RewriteTest {
    @Test
    void marksRemovedModuloLinkedGroupingAndRawEmailInLocaleJson() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJsonMessageRisks()),
                json(
                        """
                        {
                          "welcome": "Hello %{name}",
                          "linked": "Reason @:(message.reason)",
                          "support": "Email support@example.com",
                          "safe": "Email support{'@'}example.com"
                        }
                        """,
                        source -> source.path("src/locales/en.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("removed legacy %{...}"));
                                    assertTrue(printed.contains("removed @:(...)"));
                                    assertTrue(printed.contains("treats @ as message syntax"));
                                    assertFalse(printed.contains("~~(Vue I18n 9 treats @ as message syntax)~~>\"Email support{'@'}"));
                                })
                )
        );
    }

    @Test
    void marksSameConcreteSyntaxInLocaleYaml() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nYamlMessageRisks()),
                yaml(
                        """
                        welcome: 'Hello %{name}'
                        linked: 'Reason @:(message.reason)'
                        support: 'Email support@example.com'
                        """,
                        source -> source.path("src/i18n/en.yml").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("removed legacy %{...}"));
                                    assertTrue(printed.contains("removed @:(...)"));
                                    assertTrue(printed.contains("treats @ as message syntax"));
                                })
                )
        );
    }

    @Test
    void ordinaryInterpolationPluralAndEscapedAtAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJsonMessageRisks()),
                json("{\"hello\":\"Hello {name}\",\"apples\":\"no apples | one apple | {count} apples\",\"email\":\"a{'@'}b.example\"}",
                        source -> source.path("locales/en.json"))
        );
    }

    @Test
    void similarJsonAndYamlOutsideLocalePathsAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJsonMessageRisks()),
                json("{\"template\":\"Hello %{name}\",\"email\":\"support@example.com\"}",
                        source -> source.path("fixtures/data.json"))
        );
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nYamlMessageRisks()),
                yaml("template: 'Hello %{name}'\nemail: support@example.com\n",
                        source -> source.path("config/application.yml"))
        );
    }

    @Test
    void resourceMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJsonMessageRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"email\":\"support@example.com\"}",
                        source -> source.path("src/lang/en.json").after(actual -> actual))
        );
    }
}
