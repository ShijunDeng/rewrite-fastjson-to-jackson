package com.huawei.clouds.openrewrite.springcloudcontext;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateLegacyProfileActivationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateLegacyProfileActivation());
    }

    @Test
    void migratesExactYamlDocumentSelectorInApplicationAndBootstrapFiles() {
        rewriteRun(
                yaml("spring.profiles: dev\n", "spring.config.activate.on-profile: dev\n",
                        source -> source.path("src/main/resources/application.yml")),
                yaml("spring.profiles: prod\n", "spring.config.activate.on-profile: prod\n",
                        source -> source.path("config/bootstrap-extra.yaml"))
        );
    }

    @Test
    void migratesPropertiesAndPreservesActiveAndIncludeControls() {
        rewriteRun(properties(
                """
                spring.profiles=cloud
                spring.profiles.active=dev
                spring.profiles.include=local
                """,
                """
                spring.config.activate.on-profile=cloud
                spring.profiles.active=dev
                spring.profiles.include=local
                """,
                source -> source.path("application.properties")));
    }

    @Test
    void migratesNestedYamlWithoutTouchingSiblingProfileKeys() {
        rewriteRun(yaml(
                """
                spring:
                  profiles: prod
                  application:
                    name: orders
                """,
                """
                spring:
                  config.activate.on-profile: prod
                  application:
                    name: orders
                """,
                source -> source.path("application-prod.yml")));
    }

    @Test
    void leavesNonSpringConfigurationGeneratedParentsAndNearMatchesUntouched() {
        rewriteRun(
                yaml("spring.profiles: dev\n", source -> source.path("deployment.yml")),
                properties("spring.profiles=dev\n", source -> source.path("generated-config/application.properties")),
                properties("spring.profiles.active=dev\nspring.profile=one\n",
                        source -> source.path("application.properties")),
                yaml("spring.profiles: dev\n", "spring.config.activate.on-profile: dev\n",
                        source -> source.path("application-install.yml"))
        );
    }

    @Test
    void migrationIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                yaml("spring.profiles: qa\n", "spring.config.activate.on-profile: qa\n",
                        source -> source.path("bootstrap.yml"))
        );
    }
}
