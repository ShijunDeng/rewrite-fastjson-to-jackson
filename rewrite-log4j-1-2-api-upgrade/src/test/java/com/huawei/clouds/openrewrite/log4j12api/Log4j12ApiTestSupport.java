package com.huawei.clouds.openrewrite.log4j12api;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;

final class Log4j12ApiTestSupport {
    static final String PREFIX = "com.huawei.clouds.openrewrite.log4j12api.";
    static final String UPGRADE = PREFIX + "UpgradeLog4j12ApiTo2_25_5";
    static final String SAFE_SOURCE = PREFIX + "MigrateSafeLog4j12SetLevel";
    static final String RECOMMENDED = PREFIX + "MigrateLog4j12ApiTo2_25_5";
    static final String WITH_OWNED_CORE =
            PREFIX + "MigrateLog4j12ApiTo2_25_5WithOwnedCore";

    private Log4j12ApiTestSupport() {
    }

    static Recipe activate(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().classpath("log4j-1.2-api", "log4j-api", "log4j-core");
    }
}
