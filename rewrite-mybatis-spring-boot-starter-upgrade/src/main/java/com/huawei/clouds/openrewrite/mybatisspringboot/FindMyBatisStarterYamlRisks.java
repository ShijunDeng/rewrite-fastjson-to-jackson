package com.huawei.clouds.openrewrite.mybatisspringboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/** Marks behavior-sensitive MyBatis settings in YAML files. */
public final class FindMyBatisStarterYamlRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find MyBatis Starter 4 YAML risks";
    }

    @Override
    public String getDescription() {
        return "Mark Batch executor mode, native-incompatible mapper injection, custom VFS, removed core settings, " +
               "and XML configuration mode in YAML files.";
    }

    @Override
    public YamlIsoVisitor<ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher executorType = new JsonPathMatcher("$.mybatis.executor-type");
            private final JsonPathMatcher injectSession =
                    new JsonPathMatcher("$.mybatis.inject-sql-session-on-mapper-scan");
            private final JsonPathMatcher vfsImpl = new JsonPathMatcher("$.mybatis.configuration.vfs-impl");
            private final JsonPathMatcher multipleResults =
                    new JsonPathMatcher("$.mybatis.configuration.multiple-result-sets-enabled");
            private final JsonPathMatcher configLocation = new JsonPathMatcher("$.mybatis.config-location");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                String value = e.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue().trim() : "";
                if (executorType.matches(getCursor()) && "BATCH".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "BATCH executor changes flush and transaction boundaries; verify generated keys, rollback, and partial failures");
                }
                if (injectSession.matches(getCursor()) && "false".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "false restores pre-2.2.2 mapper injection and is incompatible with the documented native/AOT path");
                }
                if (vfsImpl.matches(getCursor())) {
                    return SearchResult.found(e,
                            "A custom VFS overrides the starter's SpringBootVFS selection; test executable/layered jars and non-ASCII paths");
                }
                if (multipleResults.matches(getCursor())) {
                    return SearchResult.found(e,
                            "MyBatis 3.5.19 no longer uses multipleResultSetsEnabled; remove it after confirming driver behavior");
                }
                if (configLocation.matches(getCursor())) {
                    return SearchResult.found(e,
                            "config-location selects XML configuration; do not combine it with mybatis.configuration.* bound CoreConfiguration settings");
                }
                return e;
            }
        };
    }
}
