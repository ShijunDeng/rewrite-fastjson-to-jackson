package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes Angular email-validator false literals whose boolean coercion changed. */
public final class MigrateDeterministicAngularFormsTemplate extends Recipe {
    private static final Pattern TAG = Pattern.compile("<[^!/?][^>]*>", Pattern.DOTALL);
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular Forms templates";
    }

    @Override
    public String getDescription() {
        return "Rewrites static email=\"false\" to the equivalent boolean binding [email]=\"false\".";
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
                String migrated = migrateTags(visited.getText());
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String migrateTags(String source) {
        Matcher matcher = TAG.matcher(source);
        StringBuffer migrated = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group()
                    .replaceAll("(?<=\\s)email\\s*=\\s*\"false\"", "[email]=\"false\"")
                    .replaceAll("(?<=\\s)email\\s*=\\s*'false'", "[email]=\"false\"")
                    .replaceAll("(?<=\\s)\\[email]\\s*=\\s*\"'false'\"", "[email]=\"false\"")
                    .replaceAll("(?<=\\s)\\[email]\\s*=\\s*'\"false\"'", "[email]=\"false\"");
            matcher.appendReplacement(migrated, Matcher.quoteReplacement(tag));
        }
        matcher.appendTail(migrated);
        return migrated.toString();
    }
}
