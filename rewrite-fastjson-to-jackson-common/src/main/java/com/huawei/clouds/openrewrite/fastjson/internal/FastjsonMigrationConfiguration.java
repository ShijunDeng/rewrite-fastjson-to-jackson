package com.huawei.clouds.openrewrite.fastjson.internal;

import java.util.Set;

/**
 * Version-specific coordinates and type names consumed by the shared migration engine.
 */
public record FastjsonMigrationConfiguration(
        String sourceName,
        String sourcePackage,
        String dependencyGroupId,
        String dependencyArtifactId,
        String helperPackage
) {
    public static FastjsonMigrationConfiguration fastjson1() {
        return new FastjsonMigrationConfiguration(
                "Fastjson 1.x",
                "com.alibaba.fastjson",
                "com.alibaba",
                "fastjson",
                "com.huawei.clouds.openrewrite.fastjson"
        );
    }

    public static FastjsonMigrationConfiguration fastjson2() {
        return new FastjsonMigrationConfiguration(
                "Fastjson2",
                "com.alibaba.fastjson2",
                "com.alibaba.fastjson2",
                "fastjson2",
                "com.huawei.clouds.openrewrite.fastjson2"
        );
    }

    String jsonType() {
        return sourcePackage + ".JSON";
    }

    String jsonObjectType() {
        return sourcePackage + ".JSONObject";
    }

    String jsonArrayType() {
        return sourcePackage + ".JSONArray";
    }

    String typeReferenceType() {
        return sourcePackage + ".TypeReference";
    }

    String jsonFieldType() {
        return sourcePackage + ".annotation.JSONField";
    }

    String sourceTypePattern() {
        return sourcePackage + "..*";
    }

    Set<String> migratableTypes() {
        return Set.of(jsonType(), jsonObjectType(), jsonArrayType(), typeReferenceType(), jsonFieldType());
    }
}
