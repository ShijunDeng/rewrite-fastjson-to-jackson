package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GridStackPlainTextSupport {
    private GridStackPlainTextSupport() {
    }

    static PlainText mark(PlainText text, List<Risk> risks, boolean html) {
        String source = text.getText();
        boolean[] comments = commentPositions(source, html);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) {
                if (!comments[matcher.start()]) {
                    matches.add(new Match(matcher.start(), matcher.end(), risk.message()));
                }
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> retained = new ArrayList<>();
        int end = -1;
        for (Match match : matches) {
            if (match.start() >= end) {
                retained.add(match);
                end = match.end();
            }
        }
        if (retained.isEmpty()) {
            return text;
        }
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : retained) {
            if (cursor < match.start()) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, match.start())));
            }
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start(), match.end())), match.message()));
            cursor = match.end();
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    static boolean[] commentPositions(String source, boolean html) {
        boolean[] comment = new boolean[source.length() + 1];
        State state = State.CODE;
        char quote = '\0';
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            comment[i] = state == State.HTML || state == State.BLOCK || state == State.LINE;
            if (state == State.STRING) {
                if (current == '\\') {
                    i++;
                } else if (current == quote) {
                    state = State.CODE;
                }
            } else if (state == State.HTML && source.startsWith("-->", i)) {
                for (int j = 0; j < 3 && i + j < source.length(); j++) comment[i + j] = true;
                i += 2;
                state = State.CODE;
            } else if (state == State.BLOCK && current == '*' && next == '/') {
                comment[i + 1] = true;
                i++;
                state = State.CODE;
            } else if (state == State.LINE && (current == '\n' || current == '\r')) {
                state = State.CODE;
            } else if (state == State.CODE && html && source.startsWith("<!--", i)) {
                for (int j = 0; j < 4 && i + j < source.length(); j++) comment[i + j] = true;
                i += 3;
                state = State.HTML;
            } else if (state == State.CODE && current == '/' && next == '*') {
                comment[i] = comment[i + 1] = true;
                i++;
                state = State.BLOCK;
            } else if (state == State.CODE && !html && current == '/' && next == '/') {
                comment[i] = comment[i + 1] = true;
                i++;
                state = State.LINE;
            } else if (state == State.CODE && !html &&
                       (current == '\'' || current == '"' || current == '`')) {
                quote = current;
                state = State.STRING;
            }
        }
        comment[source.length()] = state == State.HTML || state == State.BLOCK || state == State.LINE;
        return comment;
    }

    static Risk risk(String pattern, String message) {
        return new Risk(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), message);
    }

    record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }

    private enum State {
        CODE, STRING, HTML, BLOCK, LINE
    }
}
