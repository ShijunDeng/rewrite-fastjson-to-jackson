package com.huawei.clouds.openrewrite.jasypt;

import java.util.Set;

final class JasyptVersions {
    static final String TARGET = "4.0.3";
    static final Set<String> SOURCES = Set.of("2.1.1", "2.1.2", "3.0.3", "3.0.4", "3.0.5");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompile", "testRuntimeOnly", "annotationProcessor"
    );

    private JasyptVersions() {
    }

    static boolean isSource(String version) {
        return version != null && SOURCES.contains(version.trim());
    }
}
