package com.huawei.clouds.openrewrite.swiper;

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

/** Mark Swiper template/style constructs that need visual, framework or asset review. */
public final class FindSwiperTemplateStyleRisks extends Recipe {
    private static final List<Risk> MARKUP = List.of(
            risk("<swiper-container\\b[^>]*>", "Swiper Element needs one-time registration plus explicit property, event-prefix/detail, slot, SSR/hydration and initialization tests"),
            risk("<swiper-slide\\b[^>]*>", "Swiper Element slide slots/content need framework binding, SSR/hydration and accessibility regression tests"),
            risk("(?<![:\\w-])(?:class|className)\\s*=\\s*(['\"])[^'\"]*(?<![A-Za-z0-9_-])swiper-container(?![A-Za-z0-9_-])[^'\"]*\\1",
                    "Swiper 7 renamed the ordinary container class to swiper; deterministic markup migration applies only to standalone class tokens"),
            risk("(?:class|className)\\s*=\\s*(['\"])[^'\"]*swiper-lazy(?:-preloader)?[^'\"]*\\1",
                    "Swiper 9 removed the Lazy module/classes; use native loading=lazy and an application-owned loading state"),
            risk("(?:https?:)?//[^\"'\\s>]+/swiper(?:@[^/\"'\\s>]+)?/(?:swiper|bundle|dist)/[^\"'\\s>]+",
                    "This CDN URL pins Swiper package internals; select and verify the target ESM/bundle/CSS artifact, SRI, CSP and cache policy")
    );
    private static final List<Risk> STYLES = List.of(
            risk("@(use|forward|import)\\s+(['\"])swiper/(?:scss|less)(?:/[^'\"]+)?\\2",
                    "Swiper 12 removed published SCSS/Less; consume a public CSS export and keep variables/mixins in application-owned styles"),
            codeRisk("(?<![A-Za-z0-9_-])\\.swiper-container(?![A-Za-z0-9_-])",
                    "Swiper 7 renamed the container selector to .swiper; deterministic style migration changes only this standalone token"),
            codeRisk("\\.swiper-(?:wrapper|slide|button-next|button-prev|pagination|scrollbar|notification)(?:\\b|(?=[.:#\\s>+~\\[]))",
                    "This style overrides Swiper-owned DOM/classes; verify v12 generated markup, disabled/lock state, dimensions, RTL and visual snapshots"),
            codeRisk("\\.swiper-(?:button-next|button-prev)[^,{]*(?:::before|::after)|--swiper-navigation-(?:size|color)",
                    "Swiper 12 navigation defaults use inline SVG; verify pseudo-element/font overrides, CSS variables, icon size, CSP and snapshots"),
            codeRisk("\\.swiper-lazy(?:-preloader)?(?:\\b|(?=[.:#\\s>+~\\[]))",
                    "Legacy Lazy styles target a removed module; redesign native image loading and loading/error visuals")
    );

    @Override
    public String getDisplayName() {
        return "Find Swiper 12 template and style migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Swiper Element markup, removed preprocessor/lazy assets, legacy container classes, CDN internals " +
               "and custom styles coupled to generated DOM/navigation while ignoring comments and unrelated files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!SwiperSupport.isProjectPath(visited.getSourcePath())) return visited;
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".vue") ||
                    path.endsWith(".svelte")) return mark(visited, MARKUP, true);
                if (path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".sass") ||
                    path.endsWith(".less")) return mark(visited, STYLES, false);
                return visited;
            }
        };
    }

    private static PlainText mark(PlainText text, List<Risk> risks, boolean markup) {
        String source = text.getText();
        boolean[] visible = visiblePositions(source, markup, false);
        boolean[] code = markup ? visible : visiblePositions(source, false, true);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern.matcher(source);
            boolean[] positions = risk.codeOnly ? code : visible;
            while (matcher.find()) if (positions[matcher.start()]) {
                matches.add(new Match(matcher.start(), matcher.end(), risk.message));
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> filtered = new ArrayList<>();
        int lastEnd = -1;
        for (Match match : matches) if (match.start >= lastEnd) {
            filtered.add(match);
            lastEnd = match.end;
        }
        if (filtered.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : filtered) {
            if (cursor < match.start) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, match.start)));
            }
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private static boolean[] visiblePositions(String source, boolean markup, boolean hideStrings) {
        boolean[] visible = new boolean[source.length() + 1];
        boolean block = false;
        boolean line = false;
        char quote = 0;
        boolean escaped = false;
        String start = markup ? "<!--" : "/*";
        String end = markup ? "-->" : "*/";
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (!block && !line && quote == 0 && source.startsWith(start, i)) block = true;
            if (!markup && !block && !line && quote == 0 && source.startsWith("//", i) &&
                (i == 0 || source.charAt(i - 1) != ':')) line = true;
            if (hideStrings && !block && !line) {
                if (quote == 0 && (current == '\'' || current == '"')) {
                    quote = current;
                    escaped = false;
                } else if (quote != 0) {
                    if (escaped) escaped = false;
                    else if (current == '\\') escaped = true;
                    else if (current == quote) quote = 0;
                }
            }
            visible[i] = !block && !line && (!hideStrings || quote == 0);
            if (block && source.startsWith(end, i)) {
                for (int j = 0; j < end.length() && i + j < source.length(); j++) visible[i + j] = false;
                i += end.length() - 1;
                block = false;
            } else if (line && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) line = false;
        }
        visible[source.length()] = !block && !line && (!hideStrings || quote == 0);
        return visible;
    }

    private static Risk risk(String pattern, String message) { return new Risk(Pattern.compile(pattern), message); }
    private static Risk codeRisk(String pattern, String message) { return new Risk(Pattern.compile(pattern), message, true); }
    private record Risk(Pattern pattern, String message, boolean codeOnly) {
        private Risk(Pattern pattern, String message) { this(pattern, message, false); }
    }
    private record Match(int start, int end, String message) { }
}
