package com.huawei.clouds.openrewrite.jultoslf4j;

/** Recommended family-aware dependency migration, gated by a spreadsheet-selected JUL bridge. */
public final class MigrateSelectedJulToSlf4jFamilyDependencies extends AbstractSelectedSlf4jDependencyRecipe {
    public MigrateSelectedJulToSlf4jFamilyDependencies() {
        super(Mode.FAMILY_FROM_SOURCE);
    }

    @Override
    public String getDisplayName() {
        return "Migrate spreadsheet-selected JUL-to-SLF4J dependency families to 2.0.17";
    }

    @Override
    public String getDescription() {
        return "When a build explicitly contains a spreadsheet-selected JUL bridge, upgrade it and safely owned or literal older first-party SLF4J companion declarations without overriding platforms or newer versions.";
    }
}
