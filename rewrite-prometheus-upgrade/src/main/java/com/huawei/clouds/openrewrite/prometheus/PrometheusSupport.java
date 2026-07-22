package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.Cursor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class PrometheusSupport {
    static final String MODULE = "github.com/prometheus/prometheus";
    static final String TARGET = "v0.311.3";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "v2.22.1+incompatible", "v1.8.3", "v0.44.0", "v0.46.0",
            "v0.48.1", "v0.49.1", "v0.50.1", "v0.54.1");
    static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "vendor", "target", "build", "dist", "out", "generated", "generated-sources",
            ".git", ".idea", ".vscode", ".cache", ".gopath", ".terraform", ".gradle",
            "coverage", "tmp", "temp", "node_modules", ".pnpm", ".yarn", ".npm",
            "bower_components");
    private static final Set<String> DEDICATED_PROMETHEUS_ARGUMENT_KEYS = Set.of(
            "prometheusargs", "prometheus_args");
    private static final Pattern PROMETHEUS_IMAGE = Pattern.compile(
            "(?i)(?:^|/)(?:prom/prometheus|prometheus/prometheus)(?::|@|$)");
    private static final Pattern PROMETHEUS_NAME = Pattern.compile("(?i)(?:^|[-_.])prometheus(?:$|[-_.])");
    private static final Pattern PROMETHEUS_EXECUTABLE = Pattern.compile(
            "(?i)^(?:(?:exec|command|nohup)\\s+)?(?:[^\\s\"']*/)?prometheus(?:[\\s\"']|$).*");

    private PrometheusSupport() { }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        for (int i = 0; i < Math.max(0, normalized.getNameCount() - 1); i++) {
            Path segment = normalized.getName(i);
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(name) || name.startsWith("generated-") ||
                    name.startsWith("generated_") || name.startsWith("install")) return false;
        }
        return true;
    }

    /**
     * Require an actual Prometheus owner before treating a generic YAML {@code args}/{@code command}
     * scalar as a Prometheus argv token. Dedicated prometheusArgs keys are already unambiguous.
     */
    static boolean isPrometheusYamlArgument(Cursor cursor, Set<String> argumentKeys) {
        Cursor argumentOwner = null;
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Yaml.Mapping.Entry entry) {
                String key = yamlKey(entry).toLowerCase(Locale.ROOT);
                if (argumentKeys.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(key::equals)) {
                    if (DEDICATED_PROMETHEUS_ARGUMENT_KEYS.contains(key)) return true;
                    argumentOwner = current;
                    break;
                }
            }
        }
        if (argumentOwner == null) return false;
        for (Cursor current = argumentOwner.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof Yaml.Mapping.Entry entry) {
                String key = yamlKey(entry).toLowerCase(Locale.ROOT);
                if (key.equals("prometheus") || key.startsWith("prometheus-") ||
                        key.startsWith("prometheus_") || key.startsWith("prometheusspec")) return true;
            } else if (current.getValue() instanceof Yaml.Mapping mapping && mappingOwnsPrometheus(mapping)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mappingOwnsPrometheus(Yaml.Mapping mapping) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            String key = yamlKey(entry).toLowerCase(Locale.ROOT);
            if ("image".equals(key) && anyScalarMatches(entry.getValue(), PROMETHEUS_IMAGE)) return true;
            if ("name".equals(key) && anyScalarMatches(entry.getValue(), PROMETHEUS_NAME)) return true;
            if (("command".equals(key) || "entrypoint".equals(key)) &&
                    anyScalarMatches(entry.getValue(), PROMETHEUS_EXECUTABLE)) return true;
        }
        return false;
    }

    private static boolean anyScalarMatches(Yaml.Block block, Pattern pattern) {
        if (block instanceof Yaml.Scalar scalar) return pattern.matcher(scalar.getValue()).find();
        if (block instanceof Yaml.Sequence sequence) {
            return sequence.getEntries().stream().anyMatch(entry -> anyScalarMatches(entry.getBlock(), pattern));
        }
        return false;
    }

    private static String yamlKey(Yaml.Mapping.Entry entry) {
        return entry.getKey() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
    }

    static boolean fileName(Path path, String expected) {
        return path.getFileName() != null && expected.equals(path.getFileName().toString());
    }

    /** Minimal Go lexical state used to keep import detection out of multiline raw strings and block comments. */
    static final class GoLexicalState {
        private boolean rawString;
        private boolean blockComment;

        boolean insideMultilineLiteralOrComment() {
            return rawString || blockComment;
        }

        void scan(String line) {
            boolean quoted = false;
            boolean rune = false;
            boolean escaped = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
                if (rawString) {
                    if (c == '`') rawString = false;
                    continue;
                }
                if (blockComment) {
                    if (c == '*' && next == '/') { blockComment = false; i++; }
                    continue;
                }
                if (quoted || rune) {
                    if (escaped) { escaped = false; continue; }
                    if (c == '\\') { escaped = true; continue; }
                    if (quoted && c == '"') quoted = false;
                    else if (rune && c == '\'') rune = false;
                    continue;
                }
                if (c == '/' && next == '/') break;
                if (c == '/' && next == '*') { blockComment = true; i++; }
                else if (c == '`') rawString = true;
                else if (c == '"') quoted = true;
                else if (c == '\'') rune = true;
            }
        }
    }
}
