package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Remove obsolete webpack tilde resolution only from Sass module-system CDK URLs. */
public final class MigrateDeterministicAngularCdkStyles extends Recipe {
    private static final Pattern SASS_MODULE_CDK = Pattern.compile(
            "(?m)(@(use|forward)\\s+(['\"]))~(?=@angular/cdk(?:/[^'\"]*)?\\3)");

    @Override
    public String getDisplayName() {
        return "Normalize deterministic Angular CDK Sass module URLs";
    }

    @Override
    public String getDescription() {
        return "Remove webpack's obsolete tilde prefix from exact @use and @forward @angular/cdk URLs while " +
               "leaving legacy @import and unrelated package paths for review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (!path.endsWith(".scss") && !path.endsWith(".sass")) return visited;
                String migrated = migrateVisibleDirectives(visited.getText());
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String migrateVisibleDirectives(String source) {
        boolean[] visible = visiblePositions(source);
        Matcher matcher = SASS_MODULE_CDK.matcher(source);
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
                if (current == '\\') {
                    index++;
                } else if (current == quote) {
                    state = State.CODE;
                }
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
