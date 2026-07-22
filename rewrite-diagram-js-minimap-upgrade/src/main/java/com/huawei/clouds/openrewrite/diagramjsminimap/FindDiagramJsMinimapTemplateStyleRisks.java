package com.huawei.clouds.openrewrite.diagramjsminimap;

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

/** Mark minimap stylesheet delivery and internal DOM/SVG selector coupling. */
public final class FindDiagramJsMinimapTemplateStyleRisks extends Recipe {
    private static final List<Risk> HTML = List.of(
            risk("<link\\b[^>]*diagram-js-minimap/assets/diagram-js-minimap\\.css[^>]*>",
                    "Verify the public minimap stylesheet link is copied, cache-busted, ordered and available under the deployed base/public path"),
            risk("(?:vendor|node_modules)/diagram-js-minimap/[^\"'\\s>]+",
                    "Direct vendor/node_modules minimap URLs depend on install layout; verify bundler copy rules, CSP, SSR and micro-frontend public paths"),
            risk("(?:id|class)\\s*=\\s*(['\"])[^'\"]*djs-minimap[^'\"]*\\1",
                    "Application markup uses minimap-internal classes/IDs; v5.1 multi-instance SVG ID prefixing and internal DOM ownership require regression tests")
    );
    private static final List<Risk> STYLES = List.of(
            risk("@(use|forward|import)\\s+(['\"])~?diagram-js-minimap/assets/diagram-js-minimap\\.css\\2",
                    "The public minimap CSS asset must survive production tree-shaking, load order, CSS isolation and SSR; legacy Sass tilde normalizes automatically"),
            risk("\\.djs-minimap(?:\\b|(?=[.:#\\s>+~\\[]))",
                    "Custom .djs-minimap overrides depend on internal DOM structure; verify open/closed layout, z-index, dimensions and multiple instances"),
            risk("\\.djs-minimap[^,{]*(?:\\.map|\\.toggle|\\.viewport|\\.viewport-dom|\\.overlay|\\.cursor-(?:move|crosshair))",
                    "Custom minimap child selector couples to internal map/toggle/viewport DOM; run pointer, wheel, resize and visibility regression tests"),
            risk("#djs-minimap-[-_A-Za-z0-9]+|url\\(\\s*#djs-minimap-",
                    "v5.1 prefixes SVG graphic IDs per minimap instance and strips copied IDs; fixed ID and url(#...) selectors must be redesigned"),
            risk("(?:vendor|node_modules)/diagram-js-minimap/[^)'\"\\s]+",
                    "Direct vendor/node_modules asset URLs depend on install layout and public path; prefer the documented public package CSS import")
    );

    @Override
    public String getDisplayName() {
        return "Find diagram-js-minimap 5 template and style risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact CSS asset delivery, copied vendor URLs, internal minimap DOM overrides and fixed SVG ID " +
               "assumptions while ignoring comments and unrelated files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (path.endsWith(".html") || path.endsWith(".htm")) return mark(visited, HTML, true);
                if (path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".sass") ||
                    path.endsWith(".less")) return mark(visited, STYLES, false);
                return visited;
            }
        };
    }

    private static PlainText mark(PlainText text, List<Risk> risks, boolean html) {
        String source = text.getText();
        boolean[] visible = visiblePositions(source, html);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
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
            if (cursor < match.start) snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(cursor, match.start)));
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static boolean[] visiblePositions(String source, boolean html) {
        boolean[] visible = new boolean[source.length() + 1];
        boolean block = false;
        boolean line = false;
        String start = html ? "<!--" : "/*";
        String end = html ? "-->" : "*/";
        for (int i = 0; i < source.length(); i++) {
            if (!block && !line && source.startsWith(start, i)) block = true;
            if (!html && !block && !line && source.startsWith("//", i) && linePrefixWhitespace(source, i)) line = true;
            visible[i] = !block && !line;
            if (block && source.startsWith(end, i)) {
                for (int j = 0; j < end.length() && i + j < source.length(); j++) visible[i + j] = false;
                i += end.length() - 1;
                block = false;
            } else if (line && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) line = false;
        }
        visible[source.length()] = !block && !line;
        return visible;
    }

    private static boolean linePrefixWhitespace(String source, int position) {
        for (int i = position - 1; i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r'; i--) {
            if (!Character.isWhitespace(source.charAt(i))) return false;
        }
        return true;
    }

    private static Risk risk(String pattern, String message) { return new Risk(Pattern.compile(pattern), message); }
    private record Risk(Pattern pattern, String message) { }
    private record Match(int start, int end, String message) { }
}
