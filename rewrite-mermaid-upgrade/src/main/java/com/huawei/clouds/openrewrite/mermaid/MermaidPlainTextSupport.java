package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MermaidPlainTextSupport {
    private MermaidPlainTextSupport() {
    }

    static PlainText mark(PlainText text, List<Risk> risks) {
        String source = text.getText();
        boolean[] comments = comments(source);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) if (!comments[matcher.start()]) {
                matches.add(new Match(matcher.start(), matcher.end(), risk.message()));
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> retained = new ArrayList<>();
        int end = -1;
        for (Match match : matches) if (match.start() >= end) {
            retained.add(match);
            end = match.end();
        }
        if (retained.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : retained) {
            if (cursor < match.start()) snippets.add(new PlainText.Snippet(
                    Tree.randomId(), Markers.EMPTY, source.substring(cursor, match.start())));
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start(), match.end())), match.message()));
            cursor = match.end();
        }
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(
                Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    static Risk risk(String expression, String message) {
        return new Risk(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), message);
    }

    private static boolean[] comments(String source) {
        boolean[] positions = new boolean[source.length() + 1];
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            if (!comment && source.startsWith("<!--", i)) comment = true;
            positions[i] = comment;
            if (comment && source.startsWith("-->", i)) {
                for (int j = 0; j < 3 && i + j < source.length(); j++) positions[i + j] = true;
                i += 2;
                comment = false;
            }
        }
        return positions;
    }

    record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }
}
