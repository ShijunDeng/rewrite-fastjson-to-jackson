package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class FindGuavaBuildRisksTest implements RewriteTest {
    @Test
    void marksGradle6WrapperAndObsoleteGwtProperty() {
        rewriteRun(
                spec -> spec.recipe(UpgradeGuavaTest.environment().activateRecipes(
                                "com.huawei.clouds.openrewrite.guava.FindGuavaBuildMigrationRisks"))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9.4-bin.zip",
                        "~~(Guava 32+ publishes richer Gradle module metadata; upgrade this Gradle 6 wrapper and verify variant/capability resolution)~~>distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9.4-bin.zip",
                        source -> source.path("gradle/wrapper/gradle-wrapper.properties")
                ),
                text(
                        "guava.gwt.emergency_reenable_rpc=true",
                        "~~(Guava removed GWT-RPC support; this emergency re-enable property no longer restores serialization compatibility)~~>guava.gwt.emergency_reenable_rpc=true",
                        source -> source.path("src/main/resources/application.properties")
                ),
                text(
                        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14-bin.zip",
                        source -> source.path("modern/gradle/wrapper/gradle-wrapper.properties")
                ),
                text(
                        "guava.gwt.emergency_reenable_rpc=true",
                        source -> source.path("build/generated/application.properties")
                )
        );
    }
}
