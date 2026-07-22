package com.huawei.clouds.openrewrite.tweenjs;

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

/** Mark physical Tween.js asset references in templates and build/deploy configuration. */
public final class FindTweenJsTemplateConfigRisks extends Recipe {
    private static final List<Risk> RISKS = List.of(
            risk("(?:https?:)?//[^\"'\\s>]+/@tweenjs/tween\\.js(?:@[^/\"'\\s>]+)?/dist/[^\"'\\s>]+",
                    "This CDN URL pins a Tween.js physical asset; verify the exact 23.1.1 filename, global/ESM format, SRI, CSP and cache policy"),
            risk("(?:node_modules|vendor)/@tweenjs/tween\\.js/dist/[^\"'\\s>)]+",
                    "This copied Tween.js asset depends on install layout and a physical filename; prefer package-root resolution or verify the owned copy/deploy contract"),
            risk("@tweenjs/tween\\.js/dist/(?:tween|index)[-.A-Za-z0-9]+",
                    "Tween.js 23 package exports do not expose physical dist entries; root import/require is automatic only in parsed JavaScript and TypeScript")
    );

    @Override
    public String getDisplayName() {
        return "Find Tween.js 23 template and deployment configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact CDN, vendor, node_modules and package dist references in HTML, XML, YAML and " +
               "build/deployment text configuration while ignoring comment-only occurrences.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (!supported(path)) return visited;
                return mark(visited, path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".xml"));
            }
        };
    }

    private static boolean supported(String path) {
        return path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".xml") ||
               path.endsWith(".yaml") || path.endsWith(".yml") || path.endsWith(".sh") ||
               path.endsWith(".conf") || path.endsWith(".config") || path.endsWith(".properties");
    }

    private static PlainText mark(PlainText text, boolean markup) {
        String source = text.getText();
        boolean[] visible = visiblePositions(source, markup);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : RISKS) {
            Matcher matcher = risk.pattern.matcher(source);
            while (matcher.find()) if (visible[matcher.start()]) {
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

    private static boolean[] visiblePositions(String source, boolean markup) {
        boolean[] visible = new boolean[source.length() + 1];
        boolean blockComment = false;
        boolean lineComment = false;
        for (int i = 0; i < source.length(); i++) {
            if (!blockComment && !lineComment && markup && source.startsWith("<!--", i)) blockComment = true;
            if (!markup && !blockComment && !lineComment && source.charAt(i) == '#' &&
                linePrefixWhitespace(source, i)) lineComment = true;
            visible[i] = !blockComment && !lineComment;
            if (blockComment && source.startsWith("-->", i)) {
                for (int j = 0; j < 3 && i + j < source.length(); j++) visible[i + j] = false;
                i += 2;
                blockComment = false;
            } else if (lineComment && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) {
                lineComment = false;
            }
        }
        visible[source.length()] = !blockComment && !lineComment;
        return visible;
    }

    private static boolean linePrefixWhitespace(String source, int position) {
        for (int i = position - 1; i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r'; i--) {
            if (!Character.isWhitespace(source.charAt(i))) return false;
        }
        return true;
    }

    private static Risk risk(String pattern, String message) {
        return new Risk(Pattern.compile(pattern), message);
    }

    private record Risk(Pattern pattern, String message) { }
    private record Match(int start, int end, String message) { }
}
