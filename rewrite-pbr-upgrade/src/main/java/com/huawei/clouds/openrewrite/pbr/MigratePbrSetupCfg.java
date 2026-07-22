package com.huawei.clouds.openrewrite.pbr;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Apply only one-to-one setup.cfg spellings documented by pbr 7. */
public final class MigratePbrSetupCfg extends Recipe {
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^]]+)]\\s*(?:#.*)?$");
    private static final Pattern KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+)(\\s*=)");
    private static final Map<String, String> UNDERSCORES = Map.ofEntries(
            Map.entry("author-email", "author_email"),
            Map.entry("home-page", "home_page"),
            Map.entry("description-file", "description_file"),
            Map.entry("description-content-type", "description_content_type"),
            Map.entry("python-requires", "python_requires"),
            Map.entry("requires-dist", "requires_dist"),
            Map.entry("package-data", "package_data"),
            Map.entry("data-files", "data_files"),
            Map.entry("extra-files", "extra_files"),
            Map.entry("setup-hooks", "setup_hooks"));
    private static final Map<String, String> METADATA_KEYS = Map.of(
            "home_page", "url", "summary", "description",
            "classifier", "classifiers", "platform", "platforms");

    @Override public String getDisplayName() { return "Migrate deterministic pbr setup.cfg aliases"; }

    @Override
    public String getDescription() {
        return "Normalize documented dash-separated pbr keys, rename conflict-free metadata aliases, and migrate " +
               "the legacy entry_points section to setuptools' options.entry_points section.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!PbrSupport.isProjectPath(visited.getSourcePath()) ||
                        !"setup.cfg".equals(PbrSupport.fileName(visited.getSourcePath()))) return visited;
                String source = visited.getText();
                Map<String, Set<String>> keys = keysBySection(source);
                Map<String, Map<String, Integer>> counts = keyCountsBySection(source);
                Map<String, Integer> sectionCounts = sectionCounts(source);
                boolean hasNewEntryPoints = keys.containsKey("options.entry_points");
                StringBuilder out = new StringBuilder(source.length());
                String section = "";
                for (String line : lines(source)) {
                    String body = stripEnd(line);
                    Matcher sectionMatcher = SECTION.matcher(body);
                    if (sectionMatcher.matches()) {
                        section = sectionMatcher.group(1).trim().toLowerCase(Locale.ROOT);
                        if (section.equals("entry_points") && !hasNewEntryPoints &&
                                sectionCounts.getOrDefault("entry_points", 0) == 1) {
                            int start = body.indexOf('[') + 1;
                            int end = body.indexOf(']', start);
                            line = line.substring(0, start) + "options.entry_points" + line.substring(end);
                            section = "options.entry_points";
                        }
                        out.append(line);
                        continue;
                    }
                    Matcher keyMatcher = KEY.matcher(body);
                    if (keyMatcher.find() && !body.stripLeading().startsWith("#") && !body.stripLeading().startsWith(";")) {
                        String oldKey = keyMatcher.group(2);
                        String lowerKey = oldKey.toLowerCase(Locale.ROOT);
                        boolean uniqueSource = counts.getOrDefault(section, Map.of())
                                .getOrDefault(lowerKey, 0) == 1;
                        String underscored = allowedUnderscore(section, lowerKey)
                                ? UNDERSCORES.get(lowerKey) : null;
                        String normalized = uniqueSource && underscored != null &&
                                !keys.getOrDefault(section, Set.of()).contains(underscored) ? underscored : oldKey;
                        if (section.equals("metadata") && uniqueSource) {
                            String target = METADATA_KEYS.get(normalized.toLowerCase(Locale.ROOT));
                            Set<String> metadataKeys = keys.getOrDefault("metadata", Set.of());
                            boolean sourceSpellingConflict = normalized.equalsIgnoreCase("home_page") &&
                                    metadataKeys.contains("home-page") && metadataKeys.contains("home_page");
                            if (target != null && !sourceSpellingConflict && !metadataKeys.contains(target)) {
                                normalized = target;
                            } else if (target != null && !normalized.equalsIgnoreCase(oldKey)) {
                                // Do not partially normalize an alias when its canonical target already owns the value.
                                normalized = oldKey;
                            }
                        }
                        if (!oldKey.equals(normalized)) {
                            line = line.substring(0, keyMatcher.start(2)) + normalized + line.substring(keyMatcher.end(2));
                        }
                    }
                    out.append(line);
                }
                String updated = out.toString();
                return source.equals(updated) ? visited : visited.withText(updated);
            }
        };
    }

    private static Map<String, Set<String>> keysBySection(String source) {
        Map<String, Set<String>> result = new HashMap<>();
        String section = "";
        for (String line : lines(source)) {
            String body = stripEnd(line);
            Matcher sectionMatcher = SECTION.matcher(body);
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1).trim().toLowerCase(Locale.ROOT);
                result.computeIfAbsent(section, ignored -> new HashSet<>());
                continue;
            }
            Matcher keyMatcher = KEY.matcher(body);
            if (keyMatcher.find() && !body.stripLeading().startsWith("#") && !body.stripLeading().startsWith(";")) {
                String key = keyMatcher.group(2).toLowerCase(Locale.ROOT);
                result.computeIfAbsent(section, ignored -> new HashSet<>()).add(key);
            }
        }
        return result;
    }

    private static Map<String, Map<String, Integer>> keyCountsBySection(String source) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        String section = "";
        for (String line : lines(source)) {
            String body = stripEnd(line);
            Matcher sectionMatcher = SECTION.matcher(body);
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1).trim().toLowerCase(Locale.ROOT);
                continue;
            }
            Matcher keyMatcher = KEY.matcher(body);
            if (keyMatcher.find() && !body.stripLeading().startsWith("#") &&
                    !body.stripLeading().startsWith(";")) {
                String key = keyMatcher.group(2).toLowerCase(Locale.ROOT);
                result.computeIfAbsent(section, ignored -> new HashMap<>())
                        .merge(key, 1, Integer::sum);
            }
        }
        return result;
    }

    private static Map<String, Integer> sectionCounts(String source) {
        Map<String, Integer> result = new HashMap<>();
        for (String line : lines(source)) {
            Matcher matcher = SECTION.matcher(stripEnd(line));
            if (matcher.matches()) {
                result.merge(matcher.group(1).trim().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        return result;
    }

    private static boolean allowedUnderscore(String section, String key) {
        if (section.equals("metadata")) {
            return Set.of("author-email", "home-page", "description-file", "description-content-type",
                    "python-requires", "requires-dist").contains(key);
        }
        if (section.equals("files")) {
            return Set.of("package-data", "data-files", "extra-files").contains(key);
        }
        return section.equals("global") && key.equals("setup-hooks");
    }

    private static java.util.List<String> lines(String source) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline < 0 ? source.length() : newline + 1;
            lines.add(source.substring(start, end));
            start = end;
        }
        return lines;
    }

    private static String stripEnd(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) end--;
        return line.substring(0, end);
    }
}
