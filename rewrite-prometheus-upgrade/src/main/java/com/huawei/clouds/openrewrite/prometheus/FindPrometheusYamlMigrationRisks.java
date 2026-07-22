package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Mark exact Prometheus 2/3 configuration, PromQL, image, and argv changes in parsed YAML. */
public final class FindPrometheusYamlMigrationRisks extends Recipe {
    private static final Pattern INTEGER_BUCKET = Pattern.compile(".*\\b(?:le|quantile)\\s*=~?\\s*\\\"[0-9]+\\\".*");
    private static final Pattern RANGE = Pattern.compile(".*\\[[0-9]+[smhdwy](?::[^]]*)?].*");
    private static final Pattern OLD_IMAGE = Pattern.compile(".*prom/prometheus:(?:v?(?:1|2)\\.[0-9][A-Za-z0-9_.-]*|latest).*");
    private static final Set<String> REMOVED_FEATURES = Set.of(
            "promql-at-modifier", "promql-negative-offset", "new-service-discovery-manager",
            "expand-external-labels", "no-default-scrape-port", "auto-gomemlimit", "auto-gomaxprocs");
    private static final Set<String> DEDICATED_FEATURES = Set.of(
            "agent", "remote-write-receiver", "otlp-write-receiver");
    private static final Set<String> ARGUMENT_KEYS = Set.of(
            "args", "command", "extraArgs", "additionalArgs", "prometheusArgs", "prometheus_args");

    @Override public String getDisplayName() { return "Find Prometheus 3.11 YAML migration risks"; }

    @Override
    public String getDescription() {
        return "Mark scrape protocol/UTF-8 behavior, remote-write HTTP/2, Alertmanager v1, regex/PromQL semantics, " +
               "removed flags, and independently pinned server images in parsed YAML.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ctx);
                if (!projectYaml()) return visited;
                String key = MigratePrometheusConfiguration.key(visited);
                String value = scalar(visited);
                List<String> ancestors = ancestorKeysForEntry();
                if ("scrape_configs".equals(key) && ancestors.isEmpty()) return markKey(visited,
                        "Prometheus 3 requires valid scrape Content-Type (or an explicit fallback_scrape_protocol), no longer adds default ports, enables UTF-8 names, and normalizes le/quantile labels; test every job, exporter, relabel rule, alert and dashboard");
                if ("remote_write".equals(key) && ancestors.isEmpty()) return markKey(visited,
                        "Prometheus 3 changes remote-write HTTP/2 default to false and later releases evolve protocol/queue behavior; verify explicit enable_http2, receiver compatibility, WAL catch-up, retries, backpressure, ordering, metadata and rollback");
                if ("api_version".equals(key) && "v1".equals(value) &&
                        ancestors.equals(List.of("alertmanagers", "alerting"))) return markValue(visited,
                        "Prometheus 3 rejects Alertmanager API v1; upgrade Alertmanager to a supported release, select v2, and verify discovery, auth/TLS, relabeling, HA deduplication and notification delivery");
                if ("regex".equals(key) && relabelOwner(ancestors) && hasRegexWildcardDot(value)) return markValue(visited,
                        "Prometheus 3 regex dot matches newline; verify this relabel/matcher expression and replace semantic dots with an explicit class such as [^\\n] only when that preserves intent");
                if ("external_labels".equals(key) && ancestors.equals(List.of("global")) &&
                        visited.printTrimmed(getCursor()).matches("(?s).*\\$(?:\\{|[A-Za-z_]).*")) {
                    return mark(visited, "Prometheus 3 expands environment variables in external labels by default; verify undefined variables, $$ escaping, secret exposure, replica labels and rendered configuration");
                }
                if (Set.of("expr", "query").contains(key) && ancestors.equals(List.of("rules", "groups"))) {
                    String message = promqlMessage(value);
                    if (message != null) return markValue(visited, message);
                }
                String conflictTarget = conflictTarget(key, ancestors);
                if (conflictTarget != null && siblingKeyExists(conflictTarget)) return markKey(visited,
                        "Both deprecated Prometheus key " + key + " and replacement " + conflictTarget +
                        " exist in the same mapping; merge their values deliberately before removing the old key");
                return visited;
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Yaml.Scalar visited = super.visitScalar(scalar, ctx);
                if (!projectYaml()) return visited;
                String value = visited.getValue();
                if (imageValue() && OLD_IMAGE.matcher(value).matches()) return mark(visited,
                        "This YAML pins a Prometheus 1.x/2.x image independently of the Go module; align the binary/image, architecture, UID/GID, volume permissions, flags, config, promtool and rollback plan");
                if (commandArgument() && value.startsWith("--enable-feature=") &&
                        featureRisk(value.substring("--enable-feature=".length()))) {
                    return mark(visited, "This removed or combined Prometheus feature argument needs a complete argv decision; split dedicated flags, remove default-on features, and validate the rendered command");
                }
                if (commandArgument() && (value.startsWith("--alertmanager.timeout") ||
                    value.startsWith("--storage.tsdb.allow-overlapping-blocks") || value.startsWith("-storage.local.") ||
                    value.startsWith("-storage.remote.") || value.startsWith("-alertmanager.url") ||
                    value.startsWith("-log.format"))) {
                    return mark(visited, "This Prometheus 1.x/2.x flag was removed; migrate its storage or Alertmanager ownership using the official guides and validate the complete target argv");
                }
                return visited;
            }

            private boolean projectYaml() {
                Yaml.Documents documents = getCursor().firstEnclosing(Yaml.Documents.class);
                return documents != null && PrometheusSupport.isProjectPath(documents.getSourcePath());
            }

            private List<String> ancestorKeysForEntry() {
                List<String> keys = new ArrayList<>();
                boolean current = true;
                for (Cursor cursor = getCursor(); cursor != null; cursor = cursor.getParent()) {
                    if (cursor.getValue() instanceof Yaml.Mapping.Entry enclosing) {
                        if (current) current = false;
                        else keys.add(MigratePrometheusConfiguration.key(enclosing));
                    }
                }
                return keys;
            }

            private boolean siblingKeyExists(String target) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent == null || !(parent.getValue() instanceof Yaml.Mapping mapping)) return false;
                return mapping.getEntries().stream().anyMatch(entry ->
                        target.equals(MigratePrometheusConfiguration.key(entry)));
            }

            private boolean commandArgument() {
                return PrometheusSupport.isPrometheusYamlArgument(getCursor(), ARGUMENT_KEYS);
            }

            private boolean imageValue() {
                return hasAncestorEntry(Set.of("image"));
            }

            private boolean hasAncestorEntry(Set<String> keys) {
                for (Cursor cursor = getCursor(); cursor != null; cursor = cursor.getParent()) {
                    if (cursor.getValue() instanceof Yaml.Mapping.Entry enclosing &&
                            keys.contains(MigratePrometheusConfiguration.key(enclosing))) return true;
                }
                return false;
            }
        };
    }

    private static String scalar(Yaml.Mapping.Entry entry) {
        return entry.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
    }

    private static boolean featureRisk(String features) {
        for (String feature : features.split(",")) {
            if (REMOVED_FEATURES.contains(feature) || DEDICATED_FEATURES.contains(feature)) return true;
        }
        return false;
    }

    private static boolean relabelOwner(List<String> ancestors) {
        return ancestors.equals(List.of("relabel_configs", "scrape_configs")) ||
               ancestors.equals(List.of("metric_relabel_configs", "scrape_configs")) ||
               ancestors.equals(List.of("alert_relabel_configs"));
    }

    private static boolean hasRegexWildcardDot(String regex) {
        boolean characterClass = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            int slashes = 0;
            for (int j = i - 1; j >= 0 && regex.charAt(j) == '\\'; j--) slashes++;
            boolean escaped = slashes % 2 == 1;
            if (c == '[' && !escaped) characterClass = true;
            else if (c == ']' && !escaped) characterClass = false;
            else if (c == '.' && !escaped && !characterClass) return true;
        }
        return false;
    }

    private static String conflictTarget(String key, List<String> ancestors) {
        if ("labels".equals(key) && ancestors.equals(List.of("global"))) return "external_labels";
        if (ancestors.equals(List.of("scrape_configs"))) {
            if ("target_groups".equals(key)) return "static_configs";
            if ("scrape_classic_histograms".equals(key)) return "always_scrape_classic_histograms";
        }
        return null;
    }

    private static String promqlMessage(String value) {
        if (value.matches("(?s).*\\b(?:holt_winters|count_scalar|drop_common_labels)\\s*\\(.*") ||
            value.matches("(?s).*\\bkeep_common\\b.*")) {
            return "This PromQL uses a removed/renamed function or modifier; redesign it deliberately and, for double_exponential_smoothing, configure the experimental function feature gate";
        }
        if (INTEGER_BUCKET.matcher(value).matches()) {
            return "Prometheus 3 normalizes classic histogram le and summary quantile values; update integer label matchers and verify queries spanning pre/post-upgrade samples, recording rules, alerts and dashboards";
        }
        if (RANGE.matcher(value).matches()) {
            return "Prometheus 3 range/lookback selectors are left-open; verify aligned subqueries, rate/increase windows, expected sample counts, no-data behavior, recording rules and tests";
        }
        return null;
    }

    private static Yaml.Mapping.Entry markValue(Yaml.Mapping.Entry entry, String message) {
        if (!(entry.getValue() instanceof Yaml.Scalar scalar)) return mark(entry, message);
        Yaml.Scalar marked = mark(scalar, message);
        return marked == scalar ? entry : entry.withValue(marked);
    }

    private static Yaml.Mapping.Entry markKey(Yaml.Mapping.Entry entry, String message) {
        if (!(entry.getKey() instanceof Yaml.Scalar scalar)) return mark(entry, message);
        Yaml.Scalar marked = mark(scalar, message);
        return marked == scalar ? entry : entry.withKey(marked);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
