package com.huawei.clouds.openrewrite.ngxinfinitescroll;

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

/** Adds exact snippet markers to ngx-infinite-scroll template attributes requiring behavior review. */
public final class FindNgxInfiniteScrollTemplateRisks extends Recipe {
    private static final Pattern TAG = Pattern.compile("<[^!/?][^>]*>", Pattern.DOTALL);
    private static final List<RiskAttribute> ATTRIBUTES = List.of(
            attribute("infiniteScrollContainer|fromRoot|scrollWindow",
                    "This attribute controls window/element/query-root selection; verify CSS height/overflow, selector uniqueness, overlays/Shadow DOM, SSR/hydration timing, route reuse, and container replacement"),
            attribute("infiniteScrollDistance|infiniteScrollUpDistance|infiniteScrollThrottle|infiniteScrollDisabled|immediateCheck|horizontal|alwaysCallback",
                    "This input changes thresholds, throttling, direction, setup, or disabled callback behavior; verify runtime changes, leading/trailing event counts, OnPush/zoneless rendering, pagination idempotency, and teardown"),
            attribute("scrolled|scrolledUp",
                    "This output drives pagination across the directive's Zone/throttle boundary; verify IInfiniteScrollEvent typing, request cancellation/deduplication, terminal pages, errors, content growth, hydration, and destroy/recreate cycles"),
            attribute("infiniteScroll|infinite-scroll|data-infinite-scroll",
                    "This exact element uses the standalone InfiniteScrollDirective; verify direct/NgModule/TestBed/lazy scope plus window or element scrolling, CSS overflow/height, SSR hydration, Zone behavior, and teardown")
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-infinite-scroll 17 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks each directive, input, and output attribute at its exact HTML snippet for standalone scope, " +
               "scroll-container, threshold, throttle, pagination, SSR, Zone, and lifecycle review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html") ||
                    !NgxInfiniteScrollSourceSupport.isProjectSource(visited.getSourcePath())) return visited;
                return mark(visited);
            }
        };
    }

    private static PlainText mark(PlainText text) {
        String source = text.getText();
        List<RiskMatch> matches = new ArrayList<>();
        Matcher tags = TAG.matcher(source);
        while (tags.find()) {
            if (insideHtmlComment(source, tags.start())) continue;
            String tag = tags.group();
            for (RiskAttribute risk : ATTRIBUTES) {
                Matcher attribute = risk.pattern.matcher(tag);
                while (attribute.find()) {
                    matches.add(new RiskMatch(tags.start() + attribute.start(), tags.start() + attribute.end(), risk.message));
                }
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
        if (selected.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (RiskMatch match : selected) {
            if (match.start > cursor) snippets.add(snippet(source.substring(cursor, match.start)));
            snippets.add(SearchResult.found(snippet(source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) snippets.add(snippet(source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static boolean insideHtmlComment(String source, int offset) {
        int open = source.lastIndexOf("<!--", offset);
        int close = source.lastIndexOf("-->", offset);
        return open > close;
    }

    private static RiskAttribute attribute(String names, String message) {
        String syntax = "(?:\\[\\(" + names + "\\)\\]|\\[" + names + "\\]|\\(" + names + "\\)|" +
                        "bind-(?:" + names + ")|on-(?:" + names + ")|(?:" + names + "))";
        Pattern pattern = Pattern.compile("(?<![\\w:-])" + syntax + "(?![\\w:-])" +
                                          "(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?");
        return new RiskAttribute(pattern, message);
    }

    private static PlainText.Snippet snippet(String source) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source);
    }

    private record RiskAttribute(Pattern pattern, String message) {}
    private record RiskMatch(int start, int end, String message) {}
}
