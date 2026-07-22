package com.huawei.clouds.openrewrite.fastjson2;

import com.huawei.clouds.openrewrite.fastjson.internal.FastjsonMigrationConfiguration;
import com.huawei.clouds.openrewrite.fastjson.internal.FastjsonMigrationRecipes;
import org.openrewrite.Recipe;

import java.util.List;

/** Migrates the commonly used Fastjson2 APIs to Jackson 2.x. */
public final class MigrateFastjson2ToJackson extends Recipe {
    static final String JACKSON_VERSION = "2.22.x";

    @Override
    public String getDisplayName() {
        return "Migrate Fastjson2 to Jackson";
    }

    @Override
    public String getDescription() {
        return "Migrate common Fastjson2 serialization, deserialization, container, and annotation APIs " +
               "to Jackson, add jackson-databind, and remove Fastjson2 when no unsupported uses remain.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return FastjsonMigrationRecipes.create(FastjsonMigrationConfiguration.fastjson2(), JACKSON_VERSION);
    }
}
