package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds exact snippet markers to Angular Router template constructs needing navigation tests. */
public final class FindAngularRouterTemplateRisks extends Recipe {
    private static final List<RiskPattern> RISKS = List.of(
            new RiskPattern(Pattern.compile("\\[routerLink\\]|\\brouterLink\\b"),
                    "Router link resolution needs nested/empty paths, named outlets, matrix/query params, base href, history, and SSR/hydration tests"),
            new RiskPattern(Pattern.compile("\\brouterLinkActive\\b"),
                    "RouterLinkActive matching needs exact/subset options, redirects, query/fragment behavior, aria state, and nested link tests"),
            new RiskPattern(Pattern.compile("<router-outlet\\b"),
                    "RouterOutlet activation timing, route-level providers, reuse/detach, named outlets, transitions, and hydration require review"),
            new RiskPattern(Pattern.compile("\\[queryParams\\]|\\bqueryParamsHandling\\b|\\[fragment\\]"),
                    "Query/fragment merging and preservation changed across redirects and relative navigation; assert the complete UrlTree and browser URL")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Router 20 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks router links, active-link matching, outlets, and query/fragment handling at exact HTML snippets.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html")) {
                    return visited;
                }
                return markMatches(visited);
            }
        };
    }

    private static PlainText markMatches(PlainText text) {
        String source = text.getText();
        List<RiskMatch> matches = new ArrayList<>();
        for (RiskPattern risk : RISKS) {
            Matcher matcher = risk.pattern.matcher(source);
            while (matcher.find()) {
                matches.add(new RiskMatch(matcher.start(), matcher.end(), risk.message));
            }
        }
        matches.sort(Comparator.comparingInt(RiskMatch::start).thenComparingInt(match -> -match.end));
        List<RiskMatch> selected = new ArrayList<>();
        int end = -1;
        for (RiskMatch match : matches) {
            if (match.start >= end) {
                selected.add(match);
                end = match.end;
            }
        }
        if (selected.isEmpty()) {
            return text;
        }

        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (RiskMatch match : selected) {
            if (match.start > cursor) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, match.start)));
            }
            PlainText.Snippet risky = new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end));
            snippets.add(SearchResult.found(risky, match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private record RiskPattern(Pattern pattern, String message) {
    }

    private record RiskMatch(int start, int end, String message) {
    }
}
