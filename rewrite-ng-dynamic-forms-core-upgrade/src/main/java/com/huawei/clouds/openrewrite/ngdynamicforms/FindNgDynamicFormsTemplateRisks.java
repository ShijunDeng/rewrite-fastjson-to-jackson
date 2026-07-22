package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact dynamic-form template tags whose standalone renderer scope needs migration. */
public final class FindNgDynamicFormsTemplateRisks extends Recipe {
    private static final Pattern TAG = Pattern.compile("<[^!/?][^>]*>", Pattern.DOTALL);
    private static final Pattern COMMENT_OR_RAW_BLOCK = Pattern.compile(
            "(?is)<!--.*?-->|<(?:script|style)\\b[^>]*>.*?</(?:script|style)\\s*>");

    @Override
    public String getDisplayName() {
        return "Find NG Dynamic Forms 18 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks dynamic renderer tags, removed Kendo selectors, dynamicList, and modelId/modelType template " +
               "directives at exact HTML opening tags for standalone-scope review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html")) return visited;
                return mark(visited);
            }
        };
    }

    private static PlainText mark(PlainText text) {
        String source = text.getText();
        boolean[] ignored = new boolean[source.length() + 1];
        Matcher ignoredMatcher = COMMENT_OR_RAW_BLOCK.matcher(source);
        while (ignoredMatcher.find()) Arrays.fill(ignored, ignoredMatcher.start(), ignoredMatcher.end(), true);
        Matcher matcher = TAG.matcher(source);
        List<RiskMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            if (ignored[matcher.start()]) continue;
            String tag = matcher.group();
            String lower = tag.toLowerCase(Locale.ROOT);
            String message = null;
            if (lower.matches("(?s)<dynamic-kendo-[^>]*>")) {
                message = "The v18 Kendo renderer no longer exists; replace this exact control/form selector with a supported renderer or owned custom component and verify behavior/accessibility";
            } else if (lower.matches("(?s)<dynamic-(?:basic|bootstrap|foundation|ionic|material|ng-bootstrap|ngx-bootstrap|primeng)-[^>]*>")) {
                message = "NG Dynamic Forms v18 removed renderer UIModules; import the standalone component matching this selector and verify group/model/layout, projected templates, events, styles, and lazy/TestBed scope";
            } else if (lower.matches("(?s)<[^>]*(?:\\[dynamiclist]|\\bdynamiclist(?:\\s|=|>))[^>]*>")) {
                message = "DynamicListDirective is standalone in v18; ensure it is imported directly or through DynamicFormsCoreModule in every component, NgModule, TestBed, lazy, and SSR scope";
            } else if (lower.matches("(?s)<ng-template\\b[^>]*\\b(?:modelid|modeltype)(?:\\s|=|>)[^>]*>")) {
                message = "DynamicTemplateDirective is standalone in v18; verify direct/core-module scope, modelId/modelType selection, align/as/index, projection, arrays, and duplicate templates";
            }
            if (message != null) matches.add(new RiskMatch(matcher.start(), matcher.end(), message));
        }
        if (matches.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (RiskMatch match : matches) {
            if (match.start > cursor) snippets.add(snippet(source.substring(cursor, match.start)));
            snippets.add(SearchResult.found(snippet(source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) snippets.add(snippet(source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static PlainText.Snippet snippet(String source) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source);
    }

    private record RiskMatch(int start, int end, String message) {}
}
