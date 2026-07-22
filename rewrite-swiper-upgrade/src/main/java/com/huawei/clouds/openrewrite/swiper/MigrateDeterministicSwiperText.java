package com.huawei.clouds.openrewrite.swiper;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalize exact legacy CSS references and the v7 container class in parsed text assets. */
public final class MigrateDeterministicSwiperText extends Recipe {
    private static final Pattern QUOTED_SWIPER_CSS = Pattern.compile(
            "(['\"])(swiper/(?:dist/css/|css/)?swiper(?:-bundle)?(?:\\.min)?\\.css|" +
            "swiper/components/[a-z0-9-]+/[a-z0-9-]+(?:\\.min)?\\.css)\\1");
    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile(
            "(?<![:\\w-])((?:class|className)\\s*=\\s*(['\"])[^'\"]*?)(?<![A-Za-z0-9_-])swiper-container(?![A-Za-z0-9_-])([^'\"]*\\2)");
    private static final Pattern CSS_SELECTOR = Pattern.compile(
            "(?<![A-Za-z0-9_-])\\.swiper-container(?![A-Za-z0-9_-])");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Swiper 12 styles and markup";
    }

    @Override
    public String getDescription() {
        return "Normalize exact quoted legacy Swiper CSS entries and rename standalone swiper-container CSS/markup " +
               "classes while preserving comments, Web Component tags and prefixed/suffixed application classes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!SwiperSupport.isProjectPath(visited.getSourcePath())) return visited;
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                boolean style = path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".less");
                boolean markup = path.endsWith(".html") || path.endsWith(".htm") ||
                                 path.endsWith(".vue") || path.endsWith(".svelte");
                if (!style && !markup) return visited;
                String source = visited.getText();
                boolean[] visible = visiblePositions(source, markup, false);
                String migrated = replaceCssEntries(source, visible);
                if (markup) migrated = replaceVisible(migrated, CLASS_ATTRIBUTE, "$1swiper$3",
                        visiblePositions(migrated, true, false));
                if (style) migrated = replaceVisible(migrated, CSS_SELECTOR, ".swiper",
                        visiblePositions(migrated, false, true));
                return migrated.equals(source) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String replaceCssEntries(String source, boolean[] visible) {
        Matcher matcher = QUOTED_SWIPER_CSS.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            if (!visible[matcher.start()]) continue;
            String target = SwiperSupport.cssTarget(matcher.group(2));
            if (target != null) matcher.appendReplacement(result,
                    Matcher.quoteReplacement(matcher.group(1) + target + matcher.group(1)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceVisible(String source, Pattern pattern, String replacement, boolean[] visible) {
        Matcher matcher = pattern.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) if (visible[matcher.start()]) matcher.appendReplacement(result, replacement);
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean[] visiblePositions(String source, boolean markup, boolean hideStrings) {
        boolean[] visible = new boolean[source.length() + 1];
        boolean block = false;
        boolean line = false;
        char quote = 0;
        boolean escaped = false;
        String blockStart = markup ? "<!--" : "/*";
        String blockEnd = markup ? "-->" : "*/";
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (!block && !line && quote == 0 && source.startsWith(blockStart, i)) block = true;
            if (!markup && !block && !line && quote == 0 && source.startsWith("//", i) &&
                (i == 0 || source.charAt(i - 1) != ':')) {
                line = true;
            }
            if (hideStrings && !block && !line) {
                if (quote == 0 && (current == '\'' || current == '"')) {
                    quote = current;
                    escaped = false;
                } else if (quote != 0) {
                    if (escaped) escaped = false;
                    else if (current == '\\') escaped = true;
                    else if (current == quote) quote = 0;
                }
            }
            visible[i] = !block && !line && (!hideStrings || quote == 0);
            if (block && source.startsWith(blockEnd, i)) {
                for (int j = 0; j < blockEnd.length() && i + j < source.length(); j++) visible[i + j] = false;
                i += blockEnd.length() - 1;
                block = false;
            } else if (line && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) line = false;
        }
        visible[source.length()] = !block && !line && (!hideStrings || quote == 0);
        return visible;
    }
}
