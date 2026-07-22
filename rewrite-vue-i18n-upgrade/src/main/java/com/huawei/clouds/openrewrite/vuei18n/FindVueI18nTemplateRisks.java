package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;
import java.util.regex.Pattern;

/** Marks Vue SFC/HTML/declaration boundaries not safely represented by the JavaScript AST. */
public final class FindVueI18nTemplateRisks extends Recipe {
    private static final List<VueI18nPlainTextSupport.Risk> RISKS = List.of(
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("(?is)<i18n\\b(?!-)(?=[^>]*\\b(?:path|places|tag)\\s*=)[^>]*>"),
                    "This v8 translation component could not be safely rewritten because place/places or incomplete structure remains; migrate to i18n-t, keypath, and named slots"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("(?i)\\bv-t(?:\\.preserve)?\\b(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?"),
                    "v-t is deprecated in v11 and .preserve was removed earlier; migrate to $t/t and verify SSR, fallback, scope, and editor key completion"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("\\$tc\\s*\\("),
                    "$tc was removed in v11; migrate to $t plural overloads and resolve locale, list/named values, default message, and plural count"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("(?s)\\$t\\s*\\([^,()]+,\\s*(['\"])[^'\"]+\\1"),
                    "A string second $t argument is ambiguous after the Legacy locale overload removal; use explicit { locale } or confirm an intentional default message"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("(?s)\\$t\\s*\\([^)]*\\)\\s*\\["),
                    "$t returns strings only; use $tm for an object/array message and $rt for the selected leaf"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("\\bimport\\s+\\w+\\s+from\\s+['\"]vue-i18n['\"]"),
                    "The v8 default class import was replaced by named createI18n/useI18n APIs coordinated with Vue 3 createApp"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("\\bVue\\s*[.]\\s*use\\s*\\(\\s*VueI18n\\b"),
                    "Vue.use(VueI18n) is a Vue 2 global install; use createApp and app.use(createI18n(...))"),
            new VueI18nPlainTextSupport.Risk(
                    Pattern.compile("\\bnew\\s+VueI18n\\s*\\("),
                    "new VueI18n was removed; migrate bootstrap and tests to createI18n and choose Legacy or Composition scope explicitly")
    );

    @Override
    public String getDisplayName() {
        return "Find Vue I18n 11 template and declaration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unsafe translation components, deprecated v-t, removed $tc, changed $t overload/return " +
               "shape, and Vue 2 initialization inside Vue/HTML/declaration plain-text sources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                return VueI18nPlainTextSupport.isTemplateOrDeclaration(visited)
                        ? VueI18nPlainTextSupport.mark(visited, RISKS) : visited;
            }
        };
    }
}
