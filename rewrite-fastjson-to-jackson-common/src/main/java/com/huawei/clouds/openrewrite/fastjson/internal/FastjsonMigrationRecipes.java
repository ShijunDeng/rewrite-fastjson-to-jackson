package com.huawei.clouds.openrewrite.fastjson.internal;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.AddDependency;

import java.util.List;

/** Builds a complete Fastjson-to-Jackson recipe for one Fastjson package family. */
public final class FastjsonMigrationRecipes {
    private FastjsonMigrationRecipes() {
    }

    public static List<Recipe> create(FastjsonMigrationConfiguration configuration, String jacksonVersion) {
        return List.of(
                new AddDependency(
                        "com.fasterxml.jackson.core",
                        "jackson-databind",
                        jacksonVersion,
                        null,
                        configuration.sourceTypePattern(),
                        null,
                        "com.fasterxml.jackson*",
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        true
                ),
                new ChangeType(
                        configuration.typeReferenceType(),
                        "com.fasterxml.jackson.core.type.TypeReference",
                        false
                ),
                new GenerateJacksonJsonHelper(configuration),
                new MigrateJsonFieldAnnotation(configuration),
                new MigrateFastjsonApi(configuration),
                new ChangeType(
                        configuration.jsonObjectType(),
                        "com.fasterxml.jackson.databind.node.ObjectNode",
                        false
                ),
                new ChangeType(
                        configuration.jsonArrayType(),
                        "com.fasterxml.jackson.databind.node.ArrayNode",
                        false
                ),
                new RemoveFastjsonDependency(configuration)
        );
    }
}
