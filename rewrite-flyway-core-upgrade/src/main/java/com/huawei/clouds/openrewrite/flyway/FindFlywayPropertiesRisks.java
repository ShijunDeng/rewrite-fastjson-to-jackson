package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Set;

/** Marks behavior-sensitive Flyway properties. */
public final class FindFlywayPropertiesRisks extends Recipe {
    private static final Set<String> LEGACY_IGNORE = Set.of(
            "flyway.ignoreMissingMigrations", "flyway.ignoreIgnoredMigrations",
            "flyway.ignorePendingMigrations", "flyway.ignoreFutureMigrations",
            "spring.flyway.ignore-missing-migrations", "spring.flyway.ignore-ignored-migrations",
            "spring.flyway.ignore-pending-migrations", "spring.flyway.ignore-future-migrations"
    );
    private static final Set<String> REMOVED_CHECK_CONNECTION = Set.of(
            "flyway.check.url", "flyway.check.user", "flyway.check.username", "flyway.check.password"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Flyway properties";
    }

    @Override
    public String getDescription() {
        return "Mark removed validation flags, deprecated automatic clean, destructive defaults, removed check credentials, and filesystem-only Java migration discovery.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey();
                String value = e.getValue().getText().trim();
                if (LEGACY_IGNORE.contains(key)) {
                    return SearchResult.found(e, "Removed ignore*Migrations booleans must be merged into ignoreMigrationPatterns while preserving the *:future default intentionally");
                }
                if (REMOVED_CHECK_CONNECTION.contains(key)) {
                    return SearchResult.found(e, "Removed check connection setting; choose a named environment or the intended standard connection without copying secrets into source");
                }
                if (("flyway.cleanOnValidationError".equals(key) || "spring.flyway.clean-on-validation-error".equals(key))) {
                    return SearchResult.found(e, "cleanOnValidationError is deprecated and can clean the wrong TOML environment; replace it with an explicitly approved validate-then-clean workflow");
                }
                if (("flyway.cleanDisabled".equals(key) || "spring.flyway.clean-disabled".equals(key)) && "false".equalsIgnoreCase(value)) {
                    return SearchResult.found(e, "clean is explicitly enabled; verify this cannot reach permanent or production schemas");
                }
                if (isTrue(key, value, "flyway.baselineOnMigrate", "spring.flyway.baseline-on-migrate")) {
                    return SearchResult.found(e, "baselineOnMigrate can accept a non-empty schema without migration history; verify the baseline version and target database");
                }
                if (isTrue(key, value, "flyway.outOfOrder", "spring.flyway.out-of-order")) {
                    return SearchResult.found(e, "outOfOrder changes migration ordering; compare info/validate results against a production snapshot");
                }
                if (("flyway.locations".equals(key) || "spring.flyway.locations".equals(key)) &&
                    value.contains("filesystem:") && !value.contains("classpath:")) {
                    return SearchResult.found(e, "filesystem locations discover SQL migrations only; add an explicit classpath location if Java migrations must still be discovered");
                }
                return e;
            }
        };
    }

    private static boolean isTrue(String key, String value, String flyway, String spring) {
        return (flyway.equals(key) || spring.equals(key)) && "true".equalsIgnoreCase(value);
    }
}
