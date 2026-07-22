package com.huawei.clouds.openrewrite.grafana;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies only documented one-to-one Grafana configuration migrations. */
public final class MigrateGrafana12DeterministicConfiguration extends Recipe {
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^(\\s*(?:export\\s+)?)([A-Za-z0-9_.-]+)(\\s*=\\s*)(.*?)(\\r?\\n)?$");
    private static final Pattern DOCKER_ENV_ASSIGNMENT = Pattern.compile(
            "^(\\s*ENV\\s+)([A-Za-z0-9_]+)(\\s*=\\s*|\\s+)(\"[^\"]*\"|'[^']*'|[^\\s#]+)" +
            "([ \\t]*(?:#.*)?)(\\r?\\n)?$", Pattern.CASE_INSENSITIVE);

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Grafana 12 configuration";
    }

    @Override
    public String getDescription() {
        return "Migrates removed feature toggles, simple plugin preinstall settings, and Elasticsearch browser access where the target behavior is unambiguous.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !GrafanaMigrationSupport.isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof Yaml.Documents yaml && looksLikeGrafana(source, yaml.printAll())) {
                    return migrateYaml(yaml, ctx);
                }
                if (tree instanceof PlainText text && isGrafanaConfig(text)) return migrateText(text);
                return tree;
            }
        };
    }

    private static Yaml.Documents migrateYaml(Yaml.Documents documents, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            private boolean pluginAutoSafe;
            private boolean toggleAutoSafe;
            private boolean dataSourceFile;

            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents docs, ExecutionContext p) {
                YamlOwnership ownership = yamlOwnership(docs, p);
                boolean oldPlugin = pluginAutoSafe;
                boolean oldToggle = toggleAutoSafe;
                boolean oldDataSourceFile = dataSourceFile;
                pluginAutoSafe = ownership.pluginAutoSafe();
                toggleAutoSafe = ownership.toggleAutoSafe();
                dataSourceFile = GrafanaMigrationSupport.isGrafanaDataSourcePath(docs.getSourcePath());
                Yaml.Documents visited = super.visitDocuments(docs, p);
                pluginAutoSafe = oldPlugin;
                toggleAutoSafe = oldToggle;
                dataSourceFile = oldDataSourceFile;
                return visited;
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext p) {
                Yaml.Mapping visited = super.visitMapping(mapping, p);
                if ((dataSourceFile || GrafanaMigrationSupport.yamlPathContainsAny(getCursor(), "datasources", "datasource")) &&
                    GrafanaMigrationSupport.yamlKeyCount(visited, "type") == 1 &&
                    GrafanaMigrationSupport.yamlKeyCount(visited, "access") == 1 &&
                    "elasticsearch".equalsIgnoreCase(GrafanaMigrationSupport.yamlValue(visited, "type")) &&
                    "direct".equalsIgnoreCase(GrafanaMigrationSupport.yamlValue(visited, "access"))) {
                    return visited.withEntries(visited.getEntries().stream().map(entry -> {
                        if (!"access".equals(GrafanaMigrationSupport.yamlKey(entry)) ||
                            !(entry.getValue() instanceof Yaml.Scalar scalar)) return entry;
                        return entry.withValue(scalar.withValue("proxy"));
                    }).toList());
                }
                return visited;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                if (!(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                String key = GrafanaMigrationSupport.yamlKey(visited);
                String value = scalar.getValue();
                if (pluginAutoSafe && pluginEnvironmentKey(key) && GrafanaMigrationSupport.simplePluginList(value) &&
                    visited.getKey() instanceof Yaml.Scalar keyScalar) {
                    return visited.withKey(keyScalar.withValue("GF_PLUGINS_PREINSTALL_SYNC"));
                }
                if (toggleAutoSafe && GrafanaMigrationSupport.uniqueYamlKey(getCursor(), key) &&
                    ("GF_FEATURE_TOGGLES_ENABLE".equals(key) ||
                    "enable".equals(key) && GrafanaMigrationSupport.yamlPathContains(getCursor(), "feature_toggles"))) {
                    String migrated = GrafanaMigrationSupport.removeDeprecatedToggles(value);
                    if (!migrated.equals(value)) return visited.withValue(scalar.withValue(migrated));
                }
                return visited;
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext p) {
                Yaml.Scalar visited = super.visitScalar(scalar, p);
                if (!GrafanaMigrationSupport.yamlPathContains(getCursor(), "environment")) return visited;
                String value = visited.getValue();
                int equals = value.indexOf('=');
                if (equals < 1) return visited;
                String key = value.substring(0, equals).trim();
                String assigned = value.substring(equals + 1).trim();
                if (pluginAutoSafe && pluginEnvironmentKey(key) && GrafanaMigrationSupport.simplePluginList(assigned)) {
                    return visited.withValue("GF_PLUGINS_PREINSTALL_SYNC=" + assigned);
                }
                if (toggleAutoSafe && "GF_FEATURE_TOGGLES_ENABLE".equals(key)) {
                    String migrated = GrafanaMigrationSupport.removeDeprecatedToggles(assigned);
                    if (!migrated.equals(assigned)) return visited.withValue(key + "=" + migrated);
                }
                return visited;
            }
        }.visitNonNull(documents, ctx);
    }

    private static PlainText migrateText(PlainText text) {
        boolean dockerfile = isDockerfile(text);
        TextOwnership ownership = textOwnership(text.getText(), dockerfile);
        String section = "";
        boolean changed = false;
        List<String> output = new ArrayList<>();
        Matcher lines = Pattern.compile(".*(?:\\r?\\n|$)").matcher(text.getText());
        while (lines.find() && !lines.group().isEmpty()) {
            String line = lines.group();
            String trimmed = line.trim();
            if (trimmed.matches("\\[[^]]+]")) {
                section = trimmed.substring(1, trimmed.length() - 1).trim().toLowerCase(Locale.ROOT);
                output.add(line);
                continue;
            }
            if (dockerfile) {
                Matcher dockerEnv = DOCKER_ENV_ASSIGNMENT.matcher(line);
                if (dockerEnv.matches()) {
                    String key = dockerEnv.group(2);
                    String value = dockerEnv.group(4);
                    String newKey = key;
                    String newValue = value;
                    if (ownership.pluginAutoSafe() && pluginEnvironmentKey(key) &&
                        GrafanaMigrationSupport.simplePluginList(value)) {
                        newKey = "GF_PLUGINS_PREINSTALL_SYNC";
                    }
                    if (ownership.toggleAutoSafe() && "GF_FEATURE_TOGGLES_ENABLE".equals(key)) {
                        newValue = GrafanaMigrationSupport.removeDeprecatedToggles(value);
                    }
                    String migrated = dockerEnv.group(1) + newKey + dockerEnv.group(3) + newValue +
                                      dockerEnv.group(5) + (dockerEnv.group(6) == null ? "" : dockerEnv.group(6));
                    changed |= !migrated.equals(line);
                    output.add(migrated);
                    continue;
                }
            }
            Matcher assignment = ASSIGNMENT.matcher(line);
            if (!assignment.matches() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                output.add(line);
                continue;
            }
            String key = assignment.group(2);
            String value = assignment.group(4);
            String newKey = key;
            String newValue = value;
            if (ownership.pluginAutoSafe() && pluginEnvironmentKey(key) &&
                GrafanaMigrationSupport.simplePluginList(value)) {
                newKey = "GF_PLUGINS_PREINSTALL_SYNC";
            }
            if (ownership.toggleAutoSafe() && ("GF_FEATURE_TOGGLES_ENABLE".equals(key) ||
                "feature_toggles".equals(section) && "enable".equals(key))) {
                newValue = GrafanaMigrationSupport.removeDeprecatedToggles(value);
            }
            String migrated = assignment.group(1) + newKey + assignment.group(3) + newValue +
                              (assignment.group(5) == null ? "" : assignment.group(5));
            changed |= !migrated.equals(line);
            output.add(migrated);
        }
        return changed ? text.withText(String.join("", output)) : text;
    }

    private static boolean pluginEnvironmentKey(String key) {
        return "GF_INSTALL_PLUGINS".equals(key);
    }

    private static YamlOwnership yamlOwnership(Yaml.Documents documents, ExecutionContext ctx) {
        int[] owners = new int[4]; // legacy plugin, target plugin, feature-toggle owner, force owner
        new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                String key = GrafanaMigrationSupport.yamlKey(visited);
                if (pluginEnvironmentKey(key)) owners[0]++;
                if ("GF_PLUGINS_PREINSTALL_SYNC".equals(key) || "preinstall_sync".equals(key) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "plugins")) owners[1]++;
                if ("GF_FEATURE_TOGGLES_ENABLE".equals(key) || "enable".equals(key) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "feature_toggles")) owners[2]++;
                if ("GF_INSTALL_PLUGINS_FORCE".equals(key) &&
                    GrafanaMigrationSupport.pluginForceBlocksMigration(GrafanaMigrationSupport.yamlScalar(visited))) {
                    owners[3]++;
                }
                return visited;
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext p) {
                Yaml.Scalar visited = super.visitScalar(scalar, p);
                if (!GrafanaMigrationSupport.yamlPathContains(getCursor(), "environment")) return visited;
                String value = visited.getValue();
                int equals = value.indexOf('=');
                if (equals < 1) return visited;
                String key = value.substring(0, equals).trim();
                if (pluginEnvironmentKey(key)) owners[0]++;
                if ("GF_PLUGINS_PREINSTALL_SYNC".equals(key)) owners[1]++;
                if ("GF_FEATURE_TOGGLES_ENABLE".equals(key)) owners[2]++;
                if ("GF_INSTALL_PLUGINS_FORCE".equals(key) &&
                    GrafanaMigrationSupport.pluginForceBlocksMigration(value.substring(equals + 1))) owners[3]++;
                return visited;
            }
        }.visit(documents, ctx);
        return new YamlOwnership(owners[0] == 1 && owners[1] == 0 && owners[3] == 0, owners[2] == 1);
    }

    private static TextOwnership textOwnership(String text, boolean dockerfile) {
        int pluginLegacy = 0;
        int pluginTarget = 0;
        int pluginForce = 0;
        int toggleOwners = 0;
        String section = "";
        Matcher lines = Pattern.compile(".*(?:\\r?\\n|$)").matcher(text);
        while (lines.find() && !lines.group().isEmpty()) {
            String line = lines.group();
            String trimmed = line.trim();
            if (trimmed.matches("\\[[^]]+]")) {
                section = trimmed.substring(1, trimmed.length() - 1).trim().toLowerCase(Locale.ROOT);
                continue;
            }
            if (dockerfile) {
                Matcher dockerEnv = DOCKER_ENV_ASSIGNMENT.matcher(line);
                if (dockerEnv.matches()) {
                    String key = dockerEnv.group(2);
                    String value = dockerEnv.group(4);
                    if (pluginEnvironmentKey(key)) pluginLegacy++;
                    if ("GF_PLUGINS_PREINSTALL_SYNC".equals(key)) pluginTarget++;
                    if ("GF_FEATURE_TOGGLES_ENABLE".equals(key)) toggleOwners++;
                    if ("GF_INSTALL_PLUGINS_FORCE".equals(key) &&
                        GrafanaMigrationSupport.pluginForceBlocksMigration(value)) pluginForce++;
                    continue;
                }
                String upper = trimmed.toUpperCase(Locale.ROOT);
                if (upper.startsWith("ENV ")) {
                    if (containsDockerEnvOwner(upper, "GF_INSTALL_PLUGINS")) pluginLegacy++;
                    if (containsDockerEnvOwner(upper, "GF_PLUGINS_PREINSTALL_SYNC")) pluginTarget++;
                    if (containsDockerEnvOwner(upper, "GF_FEATURE_TOGGLES_ENABLE")) toggleOwners++;
                    if (containsDockerEnvOwner(upper, "GF_INSTALL_PLUGINS_FORCE")) pluginForce++;
                    continue;
                }
            }
            Matcher assignment = ASSIGNMENT.matcher(line);
            if (!assignment.matches() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
            String key = assignment.group(2);
            if (pluginEnvironmentKey(key)) pluginLegacy++;
            if ("GF_PLUGINS_PREINSTALL_SYNC".equals(key) ||
                "plugins".equals(section) && "preinstall_sync".equals(key)) pluginTarget++;
            if ("GF_FEATURE_TOGGLES_ENABLE".equals(key) ||
                "feature_toggles".equals(section) && "enable".equals(key)) toggleOwners++;
            if ("GF_INSTALL_PLUGINS_FORCE".equals(key) &&
                GrafanaMigrationSupport.pluginForceBlocksMigration(assignment.group(4))) pluginForce++;
        }
        return new TextOwnership(pluginLegacy == 1 && pluginTarget == 0 && pluginForce == 0, toggleOwners == 1);
    }

    private static boolean containsDockerEnvOwner(String line, String key) {
        return Pattern.compile("(?:^|\\s)" + Pattern.quote(key) + "(?:\\s*=|\\s)")
                .matcher(line).find();
    }

    private record YamlOwnership(boolean pluginAutoSafe, boolean toggleAutoSafe) {
    }

    private record TextOwnership(boolean pluginAutoSafe, boolean toggleAutoSafe) {
    }

    private static boolean looksLikeGrafana(SourceFile source, String content) {
        return GrafanaMigrationSupport.looksLikeGrafanaYaml(source.getSourcePath(), content);
    }

    private static boolean isGrafanaConfig(PlainText text) {
        String path = text.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return (path.contains("grafana") || name.equals("custom.ini") || name.equals("grafana.ini") ||
                text.getText().contains("GF_")) &&
               (isDockerfile(text) || name.endsWith(".ini") || name.endsWith(".env") || name.equals("custom.ini") ||
                name.equals(".env") || name.startsWith(".env.") || name.equals("grafana.ini"));
    }

    private static boolean isDockerfile(PlainText text) {
        String name = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return "dockerfile".equals(name) || name.startsWith("dockerfile.") || name.endsWith(".dockerfile");
    }
}
