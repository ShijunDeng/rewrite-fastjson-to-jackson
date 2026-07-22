package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

/** Marks behavior-sensitive MyBatis settings in .properties files. */
public final class FindMyBatisStarterPropertiesRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find MyBatis Starter 4 properties risks";
    }

    @Override
    public String getDescription() {
        return "Mark Batch executor mode, native-incompatible mapper injection, custom VFS, removed core settings, and " +
               "config-location/nested-configuration conflicts in properties files.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey();
                String value = e.getValue().getText().trim();
                if ("mybatis.executor-type".equals(key) && "BATCH".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "BATCH executor changes flush and transaction boundaries; verify generated keys, rollback, and partial failures");
                }
                if ("mybatis.inject-sql-session-on-mapper-scan".equals(key) && "false".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "false restores pre-2.2.2 mapper injection and is incompatible with the documented native/AOT path");
                }
                if ("mybatis.configuration.vfs-impl".equals(key)) {
                    return SearchResult.found(e,
                            "A custom VFS overrides the starter's SpringBootVFS selection; test executable/layered jars and non-ASCII paths");
                }
                if ("mybatis.configuration.multiple-result-sets-enabled".equals(key)) {
                    return SearchResult.found(e,
                            "MyBatis 3.5.19 no longer uses multipleResultSetsEnabled; remove it after confirming driver behavior");
                }
                if (hasConfigurationConflict(e, key)) {
                    return SearchResult.found(e,
                            "mybatis.config-location and mybatis.configuration.* cannot be used together; choose XML or bound CoreConfiguration");
                }
                return e;
            }

            private boolean hasConfigurationConflict(Properties.Entry entry, String key) {
                if (!("mybatis.config-location".equals(key) || key.startsWith("mybatis.configuration."))) {
                    return false;
                }
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                if (file == null) {
                    return false;
                }
                boolean hasLocation = file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast)
                        .anyMatch(candidate -> "mybatis.config-location".equals(candidate.getKey()));
                boolean hasNested = file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast)
                        .anyMatch(candidate -> candidate.getKey().startsWith("mybatis.configuration."));
                return hasLocation && hasNested;
            }
        };
    }
}
