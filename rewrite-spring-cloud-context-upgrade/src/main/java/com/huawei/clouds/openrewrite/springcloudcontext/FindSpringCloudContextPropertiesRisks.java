package com.huawei.clouds.openrewrite.springcloudcontext;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Locale;

/** Mark exact Spring Cloud Context decisions in properties and spring.factories files. */
public final class FindSpringCloudContextPropertiesRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Spring Cloud Context 4.3 properties risks";
    }

    @Override
    public String getDescription() {
        return "Marks bootstrap, Config Data, refresh, CRaC, endpoint, encryption, AOT, and native decisions on exact properties entries.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                return SpringCloudContextSupport.generated(file.getSourcePath()) ? file : super.visitFile(file, ctx);
            }

            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry visited = super.visitEntry(entry, ctx);
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                String path = file == null ? "" : file.getSourcePath().toString().toLowerCase(Locale.ROOT);
                String message = SpringCloudContextConfigRisks.risk(
                        visited.getKey(), visited.getValue().getText(), path.contains("bootstrap"));
                return message == null ? visited : SearchResult.found(visited, message);
            }
        };
    }
}
