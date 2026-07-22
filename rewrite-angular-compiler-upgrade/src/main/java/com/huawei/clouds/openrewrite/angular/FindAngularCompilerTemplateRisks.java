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

/** Adds exact snippet markers to templates whose Angular 20 compiler behavior needs review. */
public final class FindAngularCompilerTemplateRisks extends Recipe {
    private static final Pattern TAG = Pattern.compile("<[^!/?][^>]*>", Pattern.DOTALL);
    private static final List<RiskPattern> RISKS = List.of(
            new RiskPattern(Pattern.compile("@for\\s*\\([^)]*\\)"),
                    "@for requires a stable unique track expression and correct empty/context variables; verify duplicate keys, reorder/insert/delete, signals, nested scopes, hydration, and animations"),
            new RiskPattern(Pattern.compile("@defer(?:\\s*\\([^)]*\\))?"),
                    "@defer trigger/prefetch/loading/error behavior affects chunks, dependencies, timers, interaction, SSR placeholders, hydration, and test stability"),
            new RiskPattern(Pattern.compile("@(if|switch)\\b"),
                    "Block control flow changes template scope and narrowing; verify aliases, nested templates, async values, else/case ordering, i18n, and DOM identity"),
            new RiskPattern(Pattern.compile("\\*ngIf\\b|\\*ngFor\\b|\\[ngSwitch\\]|\\*ngSwitchCase\\b|\\*ngSwitchDefault\\b"),
                    "Legacy structural control flow can be migrated only with Angular's official parser-aware migration; verify template references, microsyntax aliases, trackBy, scopes, whitespace, and DOM identity"),
            new RiskPattern(Pattern.compile("\\(\\s*[A-Za-z_$][\\w$]*(?:\\?\\.[^)]*)?\\)\\s*\\.[A-Za-z_$][\\w$]*"),
                    "Angular 20 always respects parentheses; a parenthesized optional-chain result may now throw on the following access, matching JavaScript semantics"),
            new RiskPattern(Pattern.compile("\\bi18n(?:-[\\w-]+)?\\b"),
                    "Compiler/i18n source spans, whitespace and message IDs changed; extract a controlled catalog diff and verify placeholders, ICU, custom IDs, translations, SSR, and hydration"),
            new RiskPattern(Pattern.compile("\\[innerHTML\\]|\\[outerHTML\\]|\\[attr\\.(?:href|xlink:href)\\]"),
                    "Compiler security schema/sanitization applies to this binding; verify trusted-value boundaries, SVG namespaces, dynamic tags, SSR parity, and CSP without bypassing sanitization"),
            new RiskPattern(Pattern.compile("\\bngNonBindable\\b|<ng-template\\b"),
                    "Literal Angular syntax/template reference scope can interact with block control flow and escaping; verify @/{/}, projected templates, let variables, reuse, i18n, and whitespace")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Compiler 20 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks block/legacy control flow, parenthesized optional chaining, i18n, security bindings, " +
               "non-bindable/template scopes, and iframe security-sensitive bindings at exact HTML snippets.";
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
        boolean[] visible = MigrateDeterministicAngularCompilerTemplate.visiblePositions(source);
        List<RiskMatch> matches = new ArrayList<>();
        Matcher tags = TAG.matcher(source);
        while (tags.find()) {
            String tag = tags.group();
            if (visible[tags.start()] && tag.matches("(?is)<iframe\\b.*(?:\\[src]|\\[srcdoc]|\\bsrcdoc\\s*=).*")) {
                matches.add(new RiskMatch(tags.start(), tags.end(),
                        "Security-sensitive iframe bindings are compiler-validated; preserve attribute ordering and verify resource URLs, sandbox/allow, Trusted Types, SSR, hydration, and CSP"));
            }
        }
        for (RiskPattern risk : RISKS) {
            Matcher matcher = risk.pattern.matcher(source);
            while (matcher.find()) {
                if (visible[matcher.start()]) {
                    matches.add(new RiskMatch(matcher.start(), matcher.end(), risk.message));
                }
            }
        }
        matches.sort(Comparator.comparingInt(RiskMatch::start).thenComparingInt(m -> -m.end));
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

    private static PlainText.Snippet snippet(String source) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source);
    }

    private record RiskPattern(Pattern pattern, String message) {}
    private record RiskMatch(int start, int end, String message) {}
}
