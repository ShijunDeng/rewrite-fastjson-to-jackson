package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalize the exact public minimap stylesheet URL in Sass module/import statements. */
public final class MigrateDeterministicDiagramJsMinimapStyles extends Recipe {
    private static final Pattern LEGACY_TILDE = Pattern.compile(
            "(?m)(@(use|forward|import)\\s+(['\"]))~(?=diagram-js-minimap/assets/diagram-js-minimap\\.css\\3)");

    @Override
    public String getDisplayName() {
        return "Normalize deterministic diagram-js-minimap Sass asset URLs";
    }

    @Override
    public String getDescription() {
        return "Remove webpack's obsolete tilde from exact quoted Sass @use, @forward and @import references to " +
               "the target package's public diagram-js-minimap.css asset.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (!path.endsWith(".scss") && !path.endsWith(".sass")) return visited;
                String migrated = migrateVisibleStatements(visited.getText());
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String migrateVisibleStatements(String source) {
        boolean[] visible = visiblePositions(source);
        Matcher matcher = LEGACY_TILDE.matcher(source);
        StringBuffer migrated = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(migrated, Matcher.quoteReplacement(
                    visible[matcher.start()] ? matcher.group(1) : matcher.group()));
        }
        matcher.appendTail(migrated);
        return migrated.toString();
    }

    private static boolean[] visiblePositions(String source) {
        boolean[] visible = new boolean[source.length() + 1];
        State state = State.CODE;
        char quote = '\0';
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            visible[index] = state == State.CODE;
            if (state == State.STRING) {
                if (current == '\\') index++;
                else if (current == quote) state = State.CODE;
            } else if (state == State.BLOCK && current == '*' && next == '/') {
                index++;
                state = State.CODE;
            } else if (state == State.LINE && (current == '\n' || current == '\r')) {
                state = State.CODE;
            } else if (state == State.CODE && current == '/' && next == '*') {
                visible[index] = false;
                index++;
                state = State.BLOCK;
            } else if (state == State.CODE && current == '/' && next == '/') {
                visible[index] = false;
                index++;
                state = State.LINE;
            } else if (state == State.CODE && (current == '\'' || current == '"')) {
                quote = current;
                state = State.STRING;
            }
        }
        visible[source.length()] = state == State.CODE;
        return visible;
    }

    private enum State {
        CODE, STRING, BLOCK, LINE
    }
}
