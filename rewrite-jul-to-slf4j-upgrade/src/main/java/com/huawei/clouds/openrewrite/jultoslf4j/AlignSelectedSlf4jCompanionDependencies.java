package com.huawei.clouds.openrewrite.jultoslf4j;

/** Conservative implementation behind the backward-compatible companion alignment recipe. */
public final class AlignSelectedSlf4jCompanionDependencies extends AbstractSelectedSlf4jDependencyRecipe {
    public AlignSelectedSlf4jCompanionDependencies() {
        super(Mode.COMPANIONS_FOR_TARGET);
    }

    @Override
    public String getDisplayName() {
        return "Align selected SLF4J companions with JUL-to-SLF4J 2.0.17";
    }

    @Override
    public String getDescription() {
        return "When a build explicitly contains JUL-to-SLF4J 2.0.17, align only safely owned or literal companion versions from the audited 1.7 sources or older 2.0 patches; never downgrade newer versions.";
    }
}
