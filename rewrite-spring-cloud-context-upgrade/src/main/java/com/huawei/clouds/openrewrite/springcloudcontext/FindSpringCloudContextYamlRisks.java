package com.huawei.clouds.openrewrite.springcloudcontext;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Mark exact Spring Cloud Context decisions in YAML. */
public final class FindSpringCloudContextYamlRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Spring Cloud Context 4.3 YAML risks";
    }

    @Override
    public String getDescription() {
        return "Marks bootstrap, Config Data, refresh, CRaC, endpoint, encryption, AOT, and native decisions on exact scalar YAML entries.";
    }

    @Override
    public YamlIsoVisitor<ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                return SpringCloudContextSupport.generated(documents.getSourcePath())
                        ? documents : super.visitDocuments(documents, ctx);
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ctx);
                if (!(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                Yaml.Documents documents = getCursor().firstEnclosing(Yaml.Documents.class);
                String path = documents == null ? "" : documents.getSourcePath().toString().toLowerCase(Locale.ROOT);
                String message = SpringCloudContextConfigRisks.risk(
                        propertyPath(), scalar.getValue(), path.contains("bootstrap"));
                return message == null ? visited : SearchResult.found(visited, message);
            }

            private String propertyPath() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        };
    }
}
