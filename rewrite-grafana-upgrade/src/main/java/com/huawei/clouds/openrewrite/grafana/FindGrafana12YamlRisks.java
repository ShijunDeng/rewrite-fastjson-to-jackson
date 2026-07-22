package com.huawei.clouds.openrewrite.grafana;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks deployment, provisioning, authentication, alerting, plugin, and data source risks in YAML. */
public final class FindGrafana12YamlRisks extends Recipe {
    static final String IMAGE_MESSAGE =
            "Grafana image ownership is dynamic, digest-pinned, unlisted, or not at XLSX target 12.1.1; update the owning Helm value/registry policy and regenerate the digest deliberately";
    static final String ALERTING_MESSAGE =
            "Grafana 11+ removed legacy alerting and cannot perform that migration; migrate rules and notification channels in Grafana 10.4 before deploying 12.1.1, back up the database, and enable Grafana Alerting";
    static final String ANGULAR_MESSAGE =
            "Grafana 12 removes AngularJS plugin support completely; inventory dashboards and replace every Angular panel/data source/app with a compatible React plugin before upgrade";
    static final String PLUGIN_MESSAGE =
            "This plugin setting cannot be migrated mechanically; verify Grafana 12.1.1 compatibility/signatures and convert complex URL/version syntax to plugins.preinstall[_sync] deliberately";
    static final String TOGGLE_MESSAGE =
            "This feature-toggle owner still contains removed dashboardPreviews or envelopeEncryption but cannot be rewritten safely; resolve interpolation/comments at the owner and retain only valid Grafana 12 toggles";
    static final String EDITOR_MESSAGE =
            "Grafana 12 removes editors_can_admin; Editor team-management privileges change, so replace this implicit grant with explicit RBAC/team permissions";
    static final String OAUTH_MESSAGE =
            "OAuth email lookup and identity matching changed in Grafana 10; audit cross-provider/case-insensitive identity conflicts and use oauth_allow_insecure_email_lookup only after a security review";
    static final String ANONYMOUS_MESSAGE =
            "Anonymous access changes security exposure and is billed as active users in Grafana Enterprise 11+; confirm edition, license, role, org, and prefer public dashboards where appropriate";
    static final String UID_MESSAGE =
            "Grafana 12 rejects invalid data source UIDs by default; create a 1-40 character [A-Za-z0-9_-] UID and update all dashboard, alert, API, and Terraform references together";
    static final String ELASTICSEARCH_MESSAGE =
            "Grafana 9 removed support for Elasticsearch versions before 7.10; upgrade the cluster/data-source plugin and retest index patterns, time fields, auth, alerting, and query semantics";
    static final String ENCRYPTION_MESSAGE =
            "Envelope encryption is default since Grafana 9; keep secret_key/KMS providers identical across replicas, plan the pre-v9 rollback boundary, and test secret migrations before a rolling upgrade";
    static final String FOLDER_MESSAGE =
            "Grafana 11 subfolders change alert notification-policy matching for folder names containing '/'; escape/rewrite grafana_folder matchers and validate the receiver before upgrade";

    private static final Set<String> PLUGIN_ENV = Set.of(
            "GF_INSTALL_PLUGINS", "GF_PLUGINS_INSTALL",
            "GF_PLUGINS_ALLOW_LOADING_UNSIGNED_PLUGINS");
    private static final Pattern VERSION = Pattern.compile("(\\d+)[.](\\d+).*?");

    @Override
    public String getDisplayName() {
        return "Find Grafana 12 YAML migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks precise Grafana YAML deployment and provisioning values that require operator, security, plugin, alerting, or data-source decisions.";
    }

    @Override
    public YamlIsoVisitor<ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            private boolean grafana;
            private boolean dataSourceFile;

            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                if (!GrafanaMigrationSupport.isProjectPath(documents.getSourcePath())) return documents;
                boolean old = grafana;
                boolean oldDataSourceFile = dataSourceFile;
                String content = documents.printAll();
                grafana = GrafanaMigrationSupport.looksLikeGrafanaYaml(documents.getSourcePath(), content);
                dataSourceFile = GrafanaMigrationSupport.isGrafanaDataSourcePath(documents.getSourcePath());
                Yaml.Documents visited = super.visitDocuments(documents, ctx);
                grafana = old;
                dataSourceFile = oldDataSourceFile;
                return visited;
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping result = super.visitMapping(mapping, ctx);
                if (!grafana) return result;
                boolean officialRepository = result.getEntries().stream()
                        .filter(entry -> "repository".equals(GrafanaMigrationSupport.yamlKey(entry)))
                        .map(GrafanaMigrationSupport::yamlScalar)
                        .anyMatch(value -> value.matches(
                                "(?:(?:docker|registry-1)[.]io/)?grafana/grafana(?:-enterprise|-oss)?"));
                long tagOwners = GrafanaMigrationSupport.yamlKeyCount(result, "tag");
                if (officialRepository) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        Yaml.Mapping.Entry entry = result.getEntries().get(i);
                        if (!"tag".equals(GrafanaMigrationSupport.yamlKey(entry)) ||
                            tagOwners == 1 && GrafanaMigrationSupport.TARGET_VERSION.equals(
                                    GrafanaMigrationSupport.yamlScalar(entry)) ||
                            entry.getMarkers().findFirst(SearchResult.class).isPresent()) continue;
                        var entries = new java.util.ArrayList<>(result.getEntries());
                        entries.set(i, SearchResult.found(entry, IMAGE_MESSAGE));
                        result = result.withEntries(entries);
                    }
                }
                boolean dataSourceOwner = dataSourceFile ||
                                          GrafanaMigrationSupport.yamlPathContainsAny(getCursor(), "datasources", "datasource");
                String type = GrafanaMigrationSupport.yamlValue(result, "type");
                String uid = GrafanaMigrationSupport.yamlValue(result, "uid");
                if (dataSourceOwner && !type.isEmpty() && !uid.isEmpty() &&
                    !GrafanaMigrationSupport.VALID_DATA_SOURCE_UID.matcher(uid).matches()) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        Yaml.Mapping.Entry entry = result.getEntries().get(i);
                        if (!"uid".equals(GrafanaMigrationSupport.yamlKey(entry)) ||
                            entry.getMarkers().findFirst(SearchResult.class).isPresent()) continue;
                        var entries = new java.util.ArrayList<>(result.getEntries());
                        entries.set(i, SearchResult.found(entry, UID_MESSAGE));
                        result = result.withEntries(entries);
                        break;
                    }
                }
                if (dataSourceOwner && "elasticsearch".equalsIgnoreCase(type)) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        Yaml.Mapping.Entry jsonData = result.getEntries().get(i);
                        if (!"jsonData".equals(GrafanaMigrationSupport.yamlKey(jsonData)) ||
                            !(jsonData.getValue() instanceof Yaml.Mapping jsonDataMapping)) continue;
                        for (int j = 0; j < jsonDataMapping.getEntries().size(); j++) {
                            Yaml.Mapping.Entry database = jsonDataMapping.getEntries().get(j);
                            if (!"database".equals(GrafanaMigrationSupport.yamlKey(database)) ||
                                !elasticsearchBefore710(GrafanaMigrationSupport.yamlScalar(database)) ||
                                database.getMarkers().findFirst(SearchResult.class).isPresent()) continue;
                            var jsonDataEntries = new java.util.ArrayList<>(jsonDataMapping.getEntries());
                            jsonDataEntries.set(j, SearchResult.found(database, ELASTICSEARCH_MESSAGE));
                            var entries = new java.util.ArrayList<>(result.getEntries());
                            entries.set(i, jsonData.withValue(jsonDataMapping.withEntries(jsonDataEntries)));
                            result = result.withEntries(entries);
                            break;
                        }
                    }
                }
                return result;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ctx);
                if (!grafana || !(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                String key = GrafanaMigrationSupport.yamlKey(visited);
                String value = scalar.getValue().trim();
                String message = risk(key, value);
                if (message == null && "image".equals(key) && GrafanaMigrationSupport.isGrafanaImage(value) &&
                    !GrafanaMigrationSupport.isTargetImage(value)) message = IMAGE_MESSAGE;
                if (message == null && "enable".equals(key) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "feature_toggles") &&
                    value.contains("disableEnvelopeEncryption")) message = ENCRYPTION_MESSAGE;
                if (message == null && "enable".equals(key) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "feature_toggles") &&
                    GrafanaMigrationSupport.containsDeprecatedToggle(value)) message = TOGGLE_MESSAGE;
                if (message == null && "angular_support_enabled".equals(key) && truthy(value) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "plugins")) message = ANGULAR_MESSAGE;
                if (message == null && "install".equals(key) && !value.isEmpty() &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "plugins")) message = PLUGIN_MESSAGE;
                if (message == null && "editors_can_admin".equals(key) && truthy(value) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "users")) message = EDITOR_MESSAGE;
                if (message == null && "oauth_allow_insecure_email_lookup".equals(key) && truthy(value) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "auth")) message = OAUTH_MESSAGE;
                if (message == null && "enabled".equals(key) && truthy(value) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "auth.anonymous")) message = ANONYMOUS_MESSAGE;
                if (message == null && "secret_key".equals(key) &&
                    GrafanaMigrationSupport.yamlPathContains(getCursor(), "security")) message = ENCRYPTION_MESSAGE;
                if (message == null && "grafana_folder".equals(key) && value.contains("/")) {
                    message = FOLDER_MESSAGE;
                }
                return message == null ? visited : SearchResult.found(visited, message);
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Yaml.Scalar visited = super.visitScalar(scalar, ctx);
                if (!grafana || !GrafanaMigrationSupport.yamlPathContains(getCursor(), "environment")) return visited;
                String value = visited.getValue();
                int equals = value.indexOf('=');
                if (equals < 1) return visited;
                String message = risk(value.substring(0, equals).trim(), value.substring(equals + 1).trim());
                return message == null ? visited : SearchResult.found(visited, message);
            }
        };
    }

    private static String risk(String key, String value) {
        if (("GF_ALERTING_ENABLED".equals(key) && truthy(value)) ||
            ("GF_UNIFIED_ALERTING_ENABLED".equals(key) && falsey(value))) return ALERTING_MESSAGE;
        if ("GF_PLUGINS_ANGULAR_SUPPORT_ENABLED".equals(key) && truthy(value)) return ANGULAR_MESSAGE;
        if ("GF_INSTALL_PLUGINS_FORCE".equals(key) &&
            GrafanaMigrationSupport.pluginForceBlocksMigration(value)) return PLUGIN_MESSAGE;
        if (PLUGIN_ENV.contains(key) && !value.isEmpty()) return PLUGIN_MESSAGE;
        if ("GF_USERS_EDITORS_CAN_ADMIN".equals(key) && truthy(value)) return EDITOR_MESSAGE;
        if ("GF_AUTH_OAUTH_ALLOW_INSECURE_EMAIL_LOOKUP".equals(key) && truthy(value)) return OAUTH_MESSAGE;
        if ("GF_AUTH_ANONYMOUS_ENABLED".equals(key) && truthy(value)) return ANONYMOUS_MESSAGE;
        if (("GF_SECURITY_SECRET_KEY".equals(key) || "GF_SECURITY_SECRET_KEY__FILE".equals(key) ||
             "GF_FEATURE_TOGGLES_ENABLE".equals(key) && value.contains("disableEnvelopeEncryption"))) {
            return ENCRYPTION_MESSAGE;
        }
        if ("GF_FEATURE_TOGGLES_ENABLE".equals(key) &&
            GrafanaMigrationSupport.containsDeprecatedToggle(value)) return TOGGLE_MESSAGE;
        return null;
    }

    private static boolean truthy(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static boolean falsey(String value) {
        return "false".equalsIgnoreCase(value) || "0".equals(value);
    }

    private static boolean elasticsearchBefore710(String value) {
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.matches()) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major < 7 || major == 7 && minor < 10;
    }
}
