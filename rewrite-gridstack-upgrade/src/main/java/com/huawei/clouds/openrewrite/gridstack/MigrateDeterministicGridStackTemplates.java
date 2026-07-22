package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

/** Migrates exact legacy GridStack attributes and removes target-absent extra CSS includes. */
public final class MigrateDeterministicGridStackTemplates extends Recipe {
    private static final Map<String, String> ATTRIBUTES = new LinkedHashMap<>();
    private static final Pattern EXTRA_IMPORT = Pattern.compile(
            "(?im)^[ \\t]*@import[ \\t]+(['\"])gridstack/dist/gridstack-extra(?:\\.min)?\\.css\\1[ \\t]*;?[ \\t]*(?:\\r?\\n)?");
    private static final Pattern EXTRA_LINK = Pattern.compile(
            "(?is)<link\\b[^>]*href=(['\"])[^'\"]*gridstack-extra(?:\\.min)?\\.css\\1[^>]*>\\s*");
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
            "(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)\\s*>");

    static {
        ATTRIBUTES.put("data-gs-auto-position", "gs-auto-position");
        ATTRIBUTES.put("data-gs-min-width", "gs-min-w");
        ATTRIBUTES.put("data-gs-max-width", "gs-max-w");
        ATTRIBUTES.put("data-gs-min-height", "gs-min-h");
        ATTRIBUTES.put("data-gs-max-height", "gs-max-h");
        ATTRIBUTES.put("data-gs-no-resize", "gs-no-resize");
        ATTRIBUTES.put("data-gs-no-move", "gs-no-move");
        ATTRIBUTES.put("data-gs-width", "gs-w");
        ATTRIBUTES.put("data-gs-height", "gs-h");
        ATTRIBUTES.put("data-gs-locked", "gs-locked");
        ATTRIBUTES.put("data-gs-id", "gs-id");
        ATTRIBUTES.put("data-gs-x", "gs-x");
        ATTRIBUTES.put("data-gs-y", "gs-y");
    }

    @Override
    public String getDisplayName() {
        return "Migrate deterministic GridStack templates and styles";
    }

    @Override
    public String getDescription() {
        return "Renames exact legacy data-gs attributes to target gs names and removes exact gridstack-extra.css " +
               "imports/link tags because v12 replaced custom column CSS with variables.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                boolean template = path.endsWith(".html") || path.endsWith(".vue");
                boolean style = path.endsWith(".css") || path.endsWith(".scss") ||
                                path.endsWith(".sass") || path.endsWith(".less");
                if (!template && !style) {
                    return visited;
                }
                String migrated = visited.getText();
                if (template) {
                    Pattern attributes = Pattern.compile("(?i)(?<=\\s)(" +
                            String.join("|", ATTRIBUTES.keySet().stream().map(Pattern::quote).toList()) +
                            ")(?=\\s|=|/?>)");
                    migrated = replaceVisible(migrated, attributes,
                            matcher -> ATTRIBUTES.get(matcher.group().toLowerCase(Locale.ROOT)), true, true);
                    migrated = replaceVisible(migrated, EXTRA_LINK, matcher -> "", true, true);
                }
                if (style) {
                    migrated = replaceVisible(migrated, EXTRA_IMPORT, matcher -> "", false, false);
                }
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String replaceVisible(String source, Pattern pattern, Function<Matcher, String> replacement,
                                         boolean html, boolean ignoreRawBlocks) {
        boolean[] ignored = GridStackPlainTextSupport.commentPositions(source, html);
        if (ignoreRawBlocks) {
            Matcher raw = SCRIPT_OR_STYLE.matcher(source);
            while (raw.find()) {
                Arrays.fill(ignored, raw.start(), raw.end(), true);
            }
        }
        Matcher matcher = pattern.matcher(source);
        StringBuffer migrated = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(migrated, Matcher.quoteReplacement(
                    ignored[matcher.start()] ? matcher.group() : replacement.apply(matcher)));
        }
        matcher.appendTail(migrated);
        return migrated.toString();
    }
}
