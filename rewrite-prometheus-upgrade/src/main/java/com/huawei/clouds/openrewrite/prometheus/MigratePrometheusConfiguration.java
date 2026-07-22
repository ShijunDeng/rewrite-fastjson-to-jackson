package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Apply configuration renames explicitly documented as replacements by Prometheus. */
public final class MigratePrometheusConfiguration extends Recipe {
    private static final Map<String, String> EXACT_FLAGS = Map.of(
            "--enable-feature=agent", "--agent",
            "--enable-feature=remote-write-receiver", "--web.enable-remote-write-receiver",
            "--enable-feature=otlp-write-receiver", "--web.enable-otlp-receiver");

    @Override public String getDisplayName() { return "Migrate deterministic Prometheus YAML configuration"; }

    @Override
    public String getDescription() {
        return "Rename documented equivalent Prometheus configuration keys and standalone command arguments " +
               "without inventing scrape, storage, Alertmanager, or PromQL decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ctx);
                if (!projectYaml()) return visited;
                String key = key(visited);
                String replacement = null;
                List<String> ancestors = ancestorKeys();
                if ("scrape_classic_histograms".equals(key) && ancestors.equals(List.of("scrape_configs"))) {
                    replacement = "always_scrape_classic_histograms";
                } else if ("target_groups".equals(key) && ancestors.equals(List.of("scrape_configs"))) {
                    replacement = "static_configs";
                } else if ("labels".equals(key) && ancestors.equals(List.of("global"))) {
                    replacement = "external_labels";
                }
                if (replacement != null && !siblingKeyExists(replacement) &&
                        visited.getKey() instanceof Yaml.Scalar scalar) {
                    return visited.withKey(scalar.withValue(replacement));
                }
                return visited;
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Yaml.Scalar visited = super.visitScalar(scalar, ctx);
                if (!projectYaml() || !commandArgument()) return visited;
                String replacement = EXACT_FLAGS.get(visited.getValue());
                if (replacement != null) return visited.withValue(replacement);
                if (visited.getValue().startsWith("--storage.tsdb.retention=")) {
                    return visited.withValue(visited.getValue().replaceFirst(
                            "^--storage\\.tsdb\\.retention=", "--storage.tsdb.retention.time="));
                }
                if (visited.getValue().startsWith("-query.staleness-delta=")) {
                    return visited.withValue(visited.getValue().replaceFirst(
                            "^-query\\.staleness-delta=", "--query.lookback-delta="));
                }
                return visited;
            }

            private boolean projectYaml() {
                Yaml.Documents documents = getCursor().firstEnclosing(Yaml.Documents.class);
                return documents != null && PrometheusSupport.isProjectPath(documents.getSourcePath());
            }

            private List<String> ancestorKeys() {
                List<String> keys = new ArrayList<>();
                boolean current = true;
                for (Cursor cursor = getCursor(); cursor != null; cursor = cursor.getParent()) {
                    if (cursor.getValue() instanceof Yaml.Mapping.Entry enclosing) {
                        if (current) current = false;
                        else keys.add(key(enclosing));
                    }
                }
                return keys;
            }

            private boolean siblingKeyExists(String target) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent == null || !(parent.getValue() instanceof Yaml.Mapping mapping)) return false;
                return mapping.getEntries().stream().anyMatch(entry -> target.equals(key(entry)));
            }

            private boolean commandArgument() {
                Set<String> owners = Set.of("args", "command", "extraArgs", "additionalArgs",
                        "prometheusArgs", "prometheus_args");
                return PrometheusSupport.isPrometheusYamlArgument(getCursor(), owners);
            }
        };
    }

    static String key(Yaml.Mapping.Entry entry) {
        return entry.getKey() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
    }
}
