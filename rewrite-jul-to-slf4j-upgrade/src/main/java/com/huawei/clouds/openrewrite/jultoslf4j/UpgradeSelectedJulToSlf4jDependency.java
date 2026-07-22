package com.huawei.clouds.openrewrite.jultoslf4j;

/** Strict dependency-only migration for spreadsheet-selected JUL-to-SLF4J versions. */
public final class UpgradeSelectedJulToSlf4jDependency extends AbstractSelectedSlf4jDependencyRecipe {
    public UpgradeSelectedJulToSlf4jDependency() {
        super(Mode.CORE_FROM_SOURCE);
    }

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected JUL-to-SLF4J declarations to 2.0.17";
    }

    @Override
    public String getDescription() {
        return "Upgrade only org.slf4j:jul-to-slf4j declarations whose literal or core-owned Maven-property version exactly matches 1.7.30, 1.7.32, or 1.7.36.";
    }
}
