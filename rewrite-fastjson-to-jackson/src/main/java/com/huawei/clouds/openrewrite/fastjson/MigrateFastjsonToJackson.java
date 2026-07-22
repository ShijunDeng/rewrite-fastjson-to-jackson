package com.huawei.clouds.openrewrite.fastjson;

import com.huawei.clouds.openrewrite.fastjson.internal.FastjsonMigrationConfiguration;
import com.huawei.clouds.openrewrite.fastjson.internal.FastjsonMigrationRecipes;
import org.openrewrite.Recipe;

import java.util.List;

/**
 * Migrates the commonly used Fastjson 1.x APIs to Jackson 2.x.
 */
public final class MigrateFastjsonToJackson extends Recipe {
    static final String JACKSON_VERSION = "2.22.x";

    @Override
    public String getDisplayName() {
        return "Migrate Fastjson 1.x to Jackson";
    }

    @Override
    public String getDescription() {
        return "Migrate common Fastjson 1.x serialization, deserialization, container, and annotation APIs " +
               "to Jackson, add jackson-databind, and remove Fastjson when no Fastjson types remain.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return FastjsonMigrationRecipes.create(FastjsonMigrationConfiguration.fastjson1(), JACKSON_VERSION);
    }
}
