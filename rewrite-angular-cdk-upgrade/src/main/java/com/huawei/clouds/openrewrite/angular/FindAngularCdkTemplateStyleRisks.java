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

/** Mark CDK templates/styles whose observable behavior cannot be migrated mechanically. */
public final class FindAngularCdkTemplateStyleRisks extends Recipe {
    private static final List<Risk> TEMPLATE_RISKS = List.of(
            risk("(?<=\\s)cdkConnectedOverlay(?=\\s|=|>|\\])", "Connected overlay placement, push, viewport margins and scroll strategy can change; verify clipping, RTL and teardown"),
            risk("(?<=\\s)(?:cdkDrag|cdkDropList|cdkDragHandle|cdkDragPreview|cdkDragPlaceholder)(?=\\s|=|>|\\])", "Drag/drop pointer, keyboard, auto-scroll, projection and preview-container behavior requires interaction tests"),
            risk("(?:(?<=\\s)cdkVirtualFor(?=\\s|=|>|\\])|(?<=<)cdk-virtual-scroll-viewport(?=\\s|>))", "Virtual scroll has stricter template checking and range/cache behavior; verify item identity, dynamic sizes and accessibility"),
            risk("(?<=\\s)(?:cdkPortal|cdkPortalOutlet)(?=\\s|=|>|\\])", "Portal attachment/detachment and injector ownership changed; verify lifecycle, focus and disposal"),
            risk("(?:(?<=<)cdk-table(?=\\s|>)|(?<=\\s)(?:cdkRowDef|cdkHeaderRowDef|cdkColumnDef|sticky)(?=\\s|=|>|\\]))", "CDK table sticky/rendering internals changed; verify columns, sticky offsets, RTL and browser layout"),
            risk("(?<=\\s)(?:cdkTrapFocus|cdkFocusInitial|cdkMonitorElementFocus|cdkMonitorSubtreeFocus)(?=\\s|=|>|\\])", "Focus trapping/monitoring affects keyboard order, restoration and screen readers; run accessibility tests"),
            risk("(?<=\\s)cdkScrollable(?=\\s|=|>|\\])", "Scrollable registration drives overlay repositioning; verify nested containers, subscriptions and server rendering")
    );
    private static final List<Risk> STYLE_RISKS = List.of(
            risk("@import\\s+(['\"])~?@angular/cdk", "Legacy Sass @import CDK entry points require migration to supported @use APIs; include order and emitted CSS need review"),
            risk("~@angular/cdk", "Webpack tilde resolution is obsolete; @use/@forward URLs migrate automatically, but this remaining occurrence needs manual ownership review"),
            risk("\\bcdk\\.high-contrast\\b", "CDK high-contrast mixin now targets media queries with lower specificity; verify forced-colors output and overrides"),
            risk("\\.cdk-overlay-(?:container|pane|backdrop|connected-position-bounding-box)\\b", "Custom overlay internals depend on load order and specificity changed since CDK 19; verify stacking, fullscreen and encapsulation"),
            risk("\\.cdk-(?:drag|drop-list)[-_A-Za-z0-9]*", "Custom drag/drop CSS can affect transforms, previews, placeholders and animations; verify pointer and keyboard interactions")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular CDK 20 template and style risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact overlay, drag/drop, virtual-scroll, portal, table, focus, scrollable and Sass/style " +
               "compatibility boundaries while ignoring comments.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (path.endsWith(".html")) return mark(visited, TEMPLATE_RISKS, true);
                if (path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".sass") ||
                    path.endsWith(".less")) return mark(visited, STYLE_RISKS, false);
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
