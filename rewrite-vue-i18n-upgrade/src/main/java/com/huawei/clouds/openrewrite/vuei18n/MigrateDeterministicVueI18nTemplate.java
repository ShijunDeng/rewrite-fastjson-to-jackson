package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Migrates translation components only when legacy place syntax is provably absent. */
public final class MigrateDeterministicVueI18nTemplate extends Recipe {
    private static final Pattern PAIR = Pattern.compile("(?is)<i18n\\b([^>]*)>(.*?)</i18n\\s*>");
    private static final Pattern SELF_CLOSING = Pattern.compile("(?is)<i18n\\b([^>]*)/>");
    private static final Pattern PATH = Pattern.compile("(?i)(?<![\\w-])(:?)path(?=\\s*=)");
    private static final Pattern PLACE = Pattern.compile("(?i)(?<![\\w-]):?places?(?=\\s*=)");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Vue I18n translation components";
    }

    @Override
    public String getDescription() {
        return "Renames <i18n path> to <i18n-t keypath> only when the complete component has no removed " +
               "places prop or place attribute; custom <i18n> locale blocks remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (!path.endsWith(".vue") && !path.endsWith(".html")) {
                    return visited;
                }
                String migrated = replace(visited.getText(), PAIR, true);
                migrated = replace(migrated, SELF_CLOSING, false);
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String replace(String source, Pattern pattern, boolean paired) {
        boolean[] ignored = VueI18nPlainTextSupport.templateMigrationIgnoredPositions(source);
        Matcher matcher = pattern.matcher(source);
        StringBuffer migrated = new StringBuffer();
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String body = paired ? matcher.group(2) : "";
            boolean safe = !ignored[matcher.start()] && PATH.matcher(attributes).find() &&
                           !PLACE.matcher(attributes).find() && !PLACE.matcher(body).find();
            if (!safe) {
                matcher.appendReplacement(migrated, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String renamedAttributes = PATH.matcher(attributes).replaceAll("$1keypath");
            String replacement = paired
                    ? "<i18n-t" + renamedAttributes + ">" + body + "</i18n-t>"
                    : "<i18n-t" + renamedAttributes + "/>";
            matcher.appendReplacement(migrated, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(migrated);
        return migrated.toString();
    }
}
