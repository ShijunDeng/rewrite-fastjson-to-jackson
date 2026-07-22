package com.huawei.clouds.openrewrite.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class Slf4jServiceProviderResourceRisksTest implements RewriteTest {
    private static final String PROVIDER_PATH =
            "src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSlf4jServiceProviderResourceRisks());
    }

    @Test
    void preservesOfficialSingleProviderDescriptor() {
        // Exact descriptor shape from SLF4J 2.0.17 commit c233ea19:
        // https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-simple/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider
        rewriteRun(text(
                "org.slf4j.simple.SimpleServiceProvider\n",
                spec -> spec.path(PROVIDER_PATH)
        ));
    }

    @Test
    void marksMultipleProvidersInOneDescriptor() {
        rewriteRun(text(
                """
                com.example.FirstProvider
                com.example.SecondProvider
                """,
                """
                ~~(SLF4J provider service descriptor must name exactly one valid provider class; verify shaded output and provider selection)~~>com.example.FirstProvider
                com.example.SecondProvider
                """,
                spec -> spec.path(PROVIDER_PATH)
        ));
    }

    @Test
    void marksMalformedProviderDescriptor() {
        rewriteRun(text(
                "not a class name\n",
                "~~(SLF4J provider service descriptor must name exactly one valid provider class; verify shaded output and provider selection)~~>not a class name\n",
                spec -> spec.path(PROVIDER_PATH)
        ));
    }

    @Test
    void marksLegacyBinderServiceDescriptor() {
        rewriteRun(text(
                "com.example.LegacyBinder\n",
                "~~(SLF4J 2 discovers SLF4JServiceProvider, not LoggerFactoryBinder; migrate the implementation and service descriptor together)~~>com.example.LegacyBinder\n",
                spec -> spec.path("src/main/resources/META-INF/services/org.slf4j.spi.LoggerFactoryBinder")
        ));
    }

    @Test
    void leavesUnrelatedServiceDescriptorUntouched() {
        rewriteRun(text(
                "com.example.Driver\n",
                spec -> spec.path("src/main/resources/META-INF/services/java.sql.Driver")
        ));
    }
}
