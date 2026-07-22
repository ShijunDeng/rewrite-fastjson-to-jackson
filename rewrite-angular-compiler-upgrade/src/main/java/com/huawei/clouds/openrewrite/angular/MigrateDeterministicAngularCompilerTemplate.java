package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies Angular 20's documented migration for component properties named in or void. */
public final class MigrateDeterministicAngularCompilerTemplate extends Recipe {
    private static final Pattern INTERPOLATION = Pattern.compile("(\\{\\{\\s*)(in|void)(?=\\b)");
    private static final Pattern BINDING = Pattern.compile(
            "((?:\\[\\([^)]*\\)\\]|\\[[^]]+]|\\([^)]*\\)|\\*[-\\w]+)\\s*=\\s*([\"'])\\s*)(in|void)(?=\\b)");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular compiler templates";
    }

    @Override
    public String getDescription() {
        return "Qualifies leading component properties named in or void with this. in Angular interpolation and binding expressions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html")) return visited;
                String migrated = qualify(BINDING, qualify(INTERPOLATION, visited.getText()));
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String qualify(Pattern pattern, String source) {
        boolean[] visible = visiblePositions(source);
        Matcher matcher = pattern.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            if (!visible[matcher.start()]) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String replacement = matcher.group(1) + "this." + matcher.group(matcher.groupCount());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static boolean[] visiblePositions(String source) {
        boolean[] visible = new boolean[source.length() + 1];
        java.util.Arrays.fill(visible, true);
        Matcher ignored = Pattern.compile("(?is)<!--.*?-->|<script\\b[^>]*>.*?</script\\s*>|<style\\b[^>]*>.*?</style\\s*>")
                .matcher(source);
        while (ignored.find()) {
            for (int i = ignored.start(); i < ignored.end(); i++) visible[i] = false;
        }
        return visible;
    }
}
