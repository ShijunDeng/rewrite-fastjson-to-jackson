package com.huawei.clouds.openrewrite.flyway;

import java.util.Set;

final class FlywayVersions {
    static final String TARGET = "11.14.1";
    static final Set<String> SOURCES = Set.of(
            "5.2.1", "7.1.1", "7.8.2", "7.11.1", "7.15.0",
            "8.5.13", "9.16.3", "9.19.4", "9.20.0"
    );
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );

    private FlywayVersions() {
    }

    static boolean isSource(String version) {
        return version != null && SOURCES.contains(version.trim());
    }
}
