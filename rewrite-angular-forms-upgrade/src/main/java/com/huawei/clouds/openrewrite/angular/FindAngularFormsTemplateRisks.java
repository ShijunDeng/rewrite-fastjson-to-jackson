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

/** Adds exact snippet markers to Forms template constructs needing application decisions. */
public final class FindAngularFormsTemplateRisks extends Recipe {
    private static final Pattern TAG = Pattern.compile("<[^!/?][^>]*>", Pattern.DOTALL);
    private static final Pattern NGMODEL = Pattern.compile("\\[\\(ngModel\\)\\]|\\[ngModel\\]|\\bngModel\\b");
    private static final Pattern REACTIVE = Pattern.compile("\\[formControl\\]|\\bformControlName\\b|\\[formGroup\\]|\\bformGroupName\\b");
    private static final Pattern DISABLED = Pattern.compile("\\[disabled\\]|\\bdisabled(?:\\s|=|>)");
    private static final List<RiskPattern> SIMPLE = List.of(
            new RiskPattern(Pattern.compile("\\[ngModelOptions\\]|\\bngModelOptions\\b"),
                    "ngModelOptions standalone/updateOn behavior affects registration, validation, and event timing; verify parent form membership and submit/blur/change flows"),
            new RiskPattern(Pattern.compile("\\(ngModelChange\\)"),
                    "ngModelChange ordering relative to the bound value, validators, parent form, and native input/change events must be asserted"),
            new RiskPattern(Pattern.compile("\\(ngSubmit\\)|\\(reset\\)|#[-\\w]+\\s*=\\s*[\"']ngForm[\"']"),
                    "Form submit/reset lifecycle now has typed form events and source controls; verify preventDefault, validation, pending state, reset values, disabled fields, and event ordering"),
            new RiskPattern(Pattern.compile("\\bcompareWith\\b|\\bselectMultipleControlValueAccessor\\b|type\\s*=\\s*[\"']radio[\"']"),
                    "Selection accessor equality/identity, option replacement, multiple values, radio grouping, disabled state, and null values require tests")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Forms 20 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks mixed template/reactive directives, reactive disabled bindings, ngModel options/events, " +
               "submit/reset, and selection accessors at exact HTML snippets.";
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
                return mark(visited);
            }
        };
    }

    private static PlainText mark(PlainText text) {
        String source = text.getText();
        List<RiskMatch> matches = new ArrayList<>();
        Matcher tags = TAG.matcher(source);
        while (tags.find()) {
            String tag = tags.group();
            boolean reactive = REACTIVE.matcher(tag).find();
            if (reactive && NGMODEL.matcher(tag).find()) {
                matches.add(new RiskMatch(tags.start(), tags.end(),
                        "ngModel and reactive forms directives share this element; this deprecated mixed registration has ambiguous value/validation/event ownership and must be migrated to one forms model"));
            } else if (reactive && DISABLED.matcher(tag).find()) {
                matches.add(new RiskMatch(tags.start(), tags.end(),
                        "A disabled attribute/binding is mixed with a reactive directive; drive disabled state through the control and verify CVA calls, change detection, emitted events, and submitted/raw values"));
            }
        }
        for (RiskPattern risk : SIMPLE) {
            Matcher matcher = risk.pattern.matcher(source);
            while (matcher.find()) matches.add(new RiskMatch(matcher.start(), matcher.end(), risk.message));
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
