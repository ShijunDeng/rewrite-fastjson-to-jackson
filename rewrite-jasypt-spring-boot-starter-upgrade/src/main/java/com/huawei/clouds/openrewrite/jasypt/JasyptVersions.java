package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;

import java.nio.file.Path;
import java.util.Set;

final class JasyptVersions {
    static final String TARGET = "4.0.3";
    static final Set<String> SOURCES = Set.of("2.1.1", "2.1.2", "3.0.3", "3.0.4", "3.0.5");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "generated-sources", "generated-test-sources",
            "install", ".gradle", ".mvn", ".idea", "node_modules"
    );

    private JasyptVersions() {
    }

    static boolean isSource(String version) {
        return version != null && SOURCES.contains(version.trim());
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path) {
            if (GENERATED_DIRECTORIES.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) {
            return false;
        }
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof J.MethodInvocation owner) {
                return "dependencies".equals(owner.getSimpleName());
            }
        }
        return false;
    }
}
