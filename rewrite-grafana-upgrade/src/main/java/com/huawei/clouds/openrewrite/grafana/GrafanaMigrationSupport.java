package com.huawei.clouds.openrewrite.grafana;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GrafanaMigrationSupport {
    static final Set<String> SOURCE_VERSIONS = Set.of("7.4.5", "7.5.16", "8.5.14", "9.1.7");
    static final String TARGET_VERSION = "12.1.1";
    static final Set<String> DEPRECATED_TOGGLES = Set.of("dashboardPreviews", "envelopeEncryption");
    static final Pattern SIMPLE_PLUGIN_LIST = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]*(?:,[A-Za-z0-9][A-Za-z0-9._-]*)*");
    static final Pattern VALID_DATA_SOURCE_UID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern DEPRECATED_TOGGLE_TOKEN = Pattern.compile(
            "(?:^|[,\\s'\"])(?:dashboardPreviews|envelopeEncryption)(?=$|[,\\s'\"])");
    private static final Pattern IMAGE = Pattern.compile(
            "^((?:(?:docker|registry-1)[.]io/)?grafana/grafana(?:-enterprise|-oss)?):([^@\\s]+)$");
    private static final Set<String> EXCLUDED = Set.of(
            "target", "build", "out", "dist", "generated", "install", "vendor", ".gradle", ".mvn",
            ".m2", ".idea", ".git", "node_modules", "bower_components", ".pnpm", ".yarn", ".npm",
            ".angular", ".nx", ".next", ".cache", "coverage");

    private GrafanaMigrationSupport() {
    }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        for (int i = 0; i < Math.max(0, normalized.getNameCount() - 1); i++) {
            Path part = normalized.getName(i);
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(value) || value.startsWith("generated") || value.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean isGrafanaDataSourcePath(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return normalized.contains("provisioning/datasources/") ||
               normalized.contains("grafana") && fileName.contains("datasource");
    }

    static boolean looksLikeGrafanaYaml(Path path, String content) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("grafana") || isGrafanaDataSourcePath(path) ||
               content.contains("grafana/grafana") || content.contains("GF_") ||
               Pattern.compile("(?m)^\\s*apiVersion:\\s*['\"]?1['\"]?\\s*$").matcher(content).find() &&
               Pattern.compile("(?m)^\\s*datasources:\\s*$").matcher(content).find();
    }

    static boolean looksLikeGrafanaJson(Path path, String content) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return normalized.contains("grafana") || isGrafanaDataSourcePath(path) ||
               (content.contains("\"schemaVersion\"") && content.contains("\"panels\"")) ||
               ("plugin.json".equals(fileName) && content.contains("\"type\"") &&
                (content.contains("\"angular\"") || content.contains("\"info\"") ||
                 content.contains("\"includes\"")));
    }

    static String upgradeImage(String value) {
        Matcher matcher = IMAGE.matcher(value.trim());
        if (!matcher.matches() || !SOURCE_VERSIONS.contains(matcher.group(2))) return value;
        return matcher.group(1) + ":" + TARGET_VERSION;
    }

    static boolean isGrafanaImage(String value) {
        return IMAGE.matcher(value.trim()).matches() || value.trim().matches(
                "(?:(?:docker|registry-1)[.]io/)?grafana/grafana(?:-enterprise|-oss)?(?::|@).*" );
    }

    static boolean isTargetImage(String value) {
        Matcher matcher = IMAGE.matcher(value.trim());
        return matcher.matches() && TARGET_VERSION.equals(matcher.group(2));
    }

    static String removeDeprecatedToggles(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return value;
        String quote = "";
        String toggles = trimmed;
        if (trimmed.length() >= 2 && (trimmed.startsWith("\"") && trimmed.endsWith("\"") ||
                                    trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            quote = trimmed.substring(0, 1);
            toggles = trimmed.substring(1, trimmed.length() - 1);
        }
        if (toggles.contains("${") || toggles.contains("$(") || toggles.contains("#") || toggles.contains(";")) {
            return value;
        }
        String[] tokens = toggles.trim().split("[,\\s]+");
        List<String> kept = new ArrayList<>();
        boolean removed = false;
        for (String token : tokens) {
            if (!token.isEmpty() && !token.matches("[A-Za-z0-9_-]+")) return value;
            if (DEPRECATED_TOGGLES.contains(token)) removed = true;
            else if (!token.isEmpty()) kept.add(token);
        }
        if (!removed) return value;
        String prefix = value.substring(0, value.indexOf(trimmed));
        String suffix = value.substring(value.indexOf(trimmed) + trimmed.length());
        return prefix + quote + String.join(",", kept) + quote + suffix;
    }

    static boolean containsDeprecatedToggle(String value) {
        return DEPRECATED_TOGGLE_TOKEN.matcher(value).find();
    }

    static boolean simplePluginList(String value) {
        String candidate = value.trim();
        if (candidate.length() >= 2 &&
            (candidate.startsWith("\"") && candidate.endsWith("\"") ||
             candidate.startsWith("'") && candidate.endsWith("'"))) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }
        return SIMPLE_PLUGIN_LIST.matcher(candidate).matches();
    }

    static boolean pluginForceBlocksMigration(String value) {
        String candidate = value.trim();
        if (candidate.length() >= 2 &&
            (candidate.startsWith("\"") && candidate.endsWith("\"") ||
             candidate.startsWith("'") && candidate.endsWith("'"))) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }
        return !candidate.isEmpty() && !"false".equalsIgnoreCase(candidate) && !"0".equals(candidate);
    }

    static String yamlKey(Yaml.Mapping.Entry entry) {
        return entry.getKey().getValue();
    }

    static String yamlScalar(Yaml.Mapping.Entry entry) {
        return entry.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue().trim() : "";
    }

    static String yamlValue(Yaml.Mapping mapping, String key) {
        return mapping.getEntries().stream().filter(entry -> key.equals(yamlKey(entry)))
                .map(GrafanaMigrationSupport::yamlScalar).filter(value -> !value.isEmpty()).findFirst().orElse("");
    }

    static long yamlKeyCount(Yaml.Mapping mapping, String key) {
        return mapping.getEntries().stream().filter(entry -> key.equals(yamlKey(entry))).count();
    }

    static boolean uniqueYamlKey(Cursor cursor, String key) {
        return cursor.getPathAsStream().filter(Yaml.Mapping.class::isInstance)
                .map(Yaml.Mapping.class::cast).findFirst()
                .map(mapping -> yamlKeyCount(mapping, key) == 1).orElse(false);
    }

    static boolean yamlPathContainsAny(Cursor cursor, String... keys) {
        Set<String> expected = Set.of(keys);
        return cursor.getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                .map(Yaml.Mapping.Entry.class::cast).anyMatch(entry -> expected.contains(yamlKey(entry)));
    }

    static boolean jsonPathContainsAny(Cursor cursor, String... keys) {
        Set<String> expected = Set.of(keys);
        return cursor.getPathAsStream().filter(Json.Member.class::isInstance)
                .map(Json.Member.class::cast).anyMatch(member -> expected.contains(jsonKey(member)));
    }

    static boolean yamlPathContains(Cursor cursor, String key) {
        return cursor.getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                .map(Yaml.Mapping.Entry.class::cast).anyMatch(entry -> key.equals(yamlKey(entry)));
    }

    static String jsonKey(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }

    static String jsonString(Json.Member member) {
        if (member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value) return value;
        return "";
    }

    /** Convert matching plain-text lines to markable snippets without changing their printed content. */
    static PlainText markLines(PlainText text, BiFunction<String, String, String> markerForSectionAndLine) {
        List<PlainText.Snippet> input;
        if (text.getSnippets().isEmpty()) {
            input = new ArrayList<>();
            Matcher matcher = Pattern.compile(".*(?:\\r?\\n|$)").matcher(text.getText());
            while (matcher.find() && !matcher.group().isEmpty()) {
                input.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, matcher.group()));
            }
        } else {
            input = text.getSnippets();
        }
        String section = "";
        boolean marked = false;
        List<PlainText.Snippet> output = new ArrayList<>(input.size());
        for (PlainText.Snippet snippet : input) {
            String line = snippet.getText();
            String trimmed = line.trim();
            if (trimmed.matches("\\[[^]]+]")) section = trimmed.substring(1, trimmed.length() - 1).trim();
            String message = markerForSectionAndLine.apply(section, line);
            if (message != null && snippet.getMarkers().findFirst(SearchResult.class).isEmpty()) {
                output.add(SearchResult.found(snippet, message));
                marked = true;
            } else {
                output.add(snippet);
            }
        }
        if (!marked) return text;
        return text.withText("").withSnippets(output);
    }
}
