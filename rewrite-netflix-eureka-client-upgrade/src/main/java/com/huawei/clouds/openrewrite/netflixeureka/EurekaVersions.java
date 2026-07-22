package com.huawei.clouds.openrewrite.netflixeureka;

import java.util.Set;

final class EurekaVersions {
    static final String SOURCE = "1.10.18";
    static final String TARGET = "2.0.4";
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompile", "testRuntimeOnly", "annotationProcessor"
    );

    private EurekaVersions() {
    }
}
