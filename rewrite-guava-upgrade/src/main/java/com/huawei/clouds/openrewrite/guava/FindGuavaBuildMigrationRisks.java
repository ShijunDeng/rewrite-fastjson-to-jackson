package com.huawei.clouds.openrewrite.guava;

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

/** Precisely marks Gradle metadata and removed GWT-RPC configuration risks. */
public final class FindGuavaBuildMigrationRisks extends Recipe {
    private static final Pattern GRADLE_6 = Pattern.compile("(?m)^distributionUrl=.*gradle-6(?:\\.|-).*$");
    private static final Pattern GWT_RPC = Pattern.compile("guava\\.gwt\\.emergency_reenable_rpc");
    private static final String GRADLE_MESSAGE =
            "Guava 32+ publishes richer Gradle module metadata; upgrade this Gradle 6 wrapper and verify variant/capability resolution";
    private static final String GWT_MESSAGE =
            "Guava removed GWT-RPC support; this emergency re-enable property no longer restores serialization compatibility";

    @Override
    public String getDisplayName() {
        return "Find Guava build and GWT migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Gradle 6 wrapper lines and obsolete GWT-RPC emergency property keys with actionable review reasons.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!UpgradeSelectedGuavaDependency.isProjectPath(visited.getSourcePath())) {
                    return visited;
                }
                String path = visited.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                List<Match> matches = new ArrayList<>();
                if (path.endsWith("gradle/wrapper/gradle-wrapper.properties")) {
                    FindGuavaBuildMigrationRisks.collect(visited.getText(), GRADLE_6, GRADLE_MESSAGE, matches);
                }
                if (path.endsWith(".properties") || path.endsWith(".gwt.xml")) {
                    FindGuavaBuildMigrationRisks.collect(visited.getText(), GWT_RPC, GWT_MESSAGE, matches);
                }
                return mark(visited, matches);
            }
        };
    }

    private static void collect(String source, Pattern pattern, String message, List<Match> matches) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            matches.add(new Match(matcher.start(), matcher.end(), message));
        }
    }

    private static PlainText mark(PlainText text, List<Match> matches) {
        if (matches.isEmpty()) {
            return text;
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(Match::end));
        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : matches) {
            if (match.start < cursor) {
                continue;
            }
            if (cursor < match.start) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor, match.start)));
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

    private record Match(int start, int end, String message) {
    }
}
