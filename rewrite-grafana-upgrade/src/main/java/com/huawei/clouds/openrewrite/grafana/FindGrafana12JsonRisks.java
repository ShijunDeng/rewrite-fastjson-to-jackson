package com.huawei.clouds.openrewrite.grafana;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Recipe;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;
import java.util.Set;

/** Marks dashboard and plugin JSON constructs affected by Grafana 9-12 changes. */
public final class FindGrafana12JsonRisks extends Recipe {
    static final String LEGACY_ALERT_MESSAGE =
            "Legacy dashboard alert detected; Grafana 11 removes legacy alerting, so migrate it in Grafana 10.4 and validate rule evaluation, labels, contact points, silences, and notification policies before 12.1.1";
    static final String ANGULAR_PANEL_MESSAGE =
            "This dashboard/plugin uses an Angular-era panel or Angular plugin metadata; Grafana 12 cannot load Angular plugins, so replace it with a supported React visualization and verify field/options migration";
    static final String CLOUDWATCH_ALIAS_MESSAGE =
            "Grafana 10 removed the CloudWatch Alias field; open and save the dashboard to migrate it to dynamic labels, then compare legend names and alert series matching";
    static final String LOKI_LABELS_MESSAGE =
            "Grafana 9 changed Loki logs to a single data frame with a labels field; replace/retest Labels to fields with Extract fields and verify table transforms and NaN behavior";

    private static final Set<String> ANGULAR_PANEL_TYPES = Set.of(
            "graph", "singlestat", "table-old", "grafana-piechart-panel", "grafana-worldmap-panel",
            "natel-plotly-panel", "briangann-datatable-panel");

    @Override
    public String getDisplayName() {
        return "Find Grafana 12 dashboard and plugin JSON risks";
    }

    @Override
    public String getDescription() {
        return "Marks precise legacy alert, Angular panel, data source UID, CloudWatch alias, and Loki transformation JSON members.";
    }

    @Override
    public JsonIsoVisitor<ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean grafana;
            private boolean dataSourceFile;
            private boolean pluginMetadata;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!GrafanaMigrationSupport.isProjectPath(document.getSourcePath())) return document;
                boolean oldGrafana = grafana;
                boolean oldDataSourceFile = dataSourceFile;
                boolean oldPluginMetadata = pluginMetadata;
                String printed = document.printAll();
                String path = document.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                String fileName = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                grafana = GrafanaMigrationSupport.looksLikeGrafanaJson(document.getSourcePath(), printed);
                dataSourceFile = GrafanaMigrationSupport.isGrafanaDataSourcePath(document.getSourcePath());
                pluginMetadata = "plugin.json".equals(fileName) || path.contains("/plugins/");
                Json.Document visited = super.visitDocument(document, ctx);
                grafana = oldGrafana;
                dataSourceFile = oldDataSourceFile;
                pluginMetadata = oldPluginMetadata;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!grafana) return visited;
                String key = GrafanaMigrationSupport.jsonKey(visited);
                String value = GrafanaMigrationSupport.jsonString(visited);
                boolean panelMember = GrafanaMigrationSupport.jsonPathContainsAny(getCursor(), "panels");
                if (panelMember && "alert".equals(key) && visited.getValue() instanceof Json.JsonObject) {
                    return SearchResult.found(visited, LEGACY_ALERT_MESSAGE);
                }
                if ((panelMember && "type".equals(key) && ANGULAR_PANEL_TYPES.contains(value)) ||
                    (pluginMetadata && "angular".equals(key) && visited.getValue() instanceof Json.Literal literal &&
                     Boolean.TRUE.equals(literal.getValue()))) {
                    return SearchResult.found(visited, ANGULAR_PANEL_MESSAGE);
                }
                if ("alias".equals(key) && GrafanaMigrationSupport.jsonPathContainsAny(getCursor(), "targets") &&
                    usesDatasource(getCursor(), "cloudwatch") && !value.isEmpty()) {
                    return SearchResult.found(visited, CLOUDWATCH_ALIAS_MESSAGE);
                }
                if ("id".equals(key) && "labelsToFields".equals(value) &&
                    GrafanaMigrationSupport.jsonPathContainsAny(getCursor(), "transformations") &&
                    usesDatasource(getCursor(), "loki")) {
                    return SearchResult.found(visited, LOKI_LABELS_MESSAGE);
                }
                if ("uid".equals(key) && !value.isEmpty() &&
                    (dataSourceFile || GrafanaMigrationSupport.jsonPathContainsAny(getCursor(), "datasource", "datasources")) &&
                    !GrafanaMigrationSupport.VALID_DATA_SOURCE_UID.matcher(value).matches()) {
                    return SearchResult.found(visited, FindGrafana12YamlRisks.UID_MESSAGE);
                }
                return visited;
            }
        };
    }

    private static boolean usesDatasource(Cursor cursor, String type) {
        return cursor.getPathAsStream().filter(Json.JsonObject.class::isInstance)
                .map(Json.JsonObject.class::cast).anyMatch(object -> object.getMembers().stream()
                        .filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                        .filter(member -> "datasource".equals(GrafanaMigrationSupport.jsonKey(member)))
                        .map(Json.Member::getValue).filter(Json.JsonObject.class::isInstance)
                        .map(Json.JsonObject.class::cast).flatMap(datasource -> datasource.getMembers().stream())
                        .filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                        .anyMatch(member -> "type".equals(GrafanaMigrationSupport.jsonKey(member)) &&
                                type.equalsIgnoreCase(GrafanaMigrationSupport.jsonString(member))));
    }
}
