package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;

final class SpringRetryTestSupport {
    private SpringRetryTestSupport() {
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.springretry",
                "org.openrewrite.java",
                "org.openrewrite.java.migrate").build();
    }

    static Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().classpath(
                "spring-retry", "spring-context", "spring-aop", "spring-beans",
                "spring-core", "spring-expression", "spring-jcl");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    static String pom(String version) {
        return project("<dependencies>" + dependency(version, "") + "</dependencies>");
    }

    static String dependency(String version, String extra) {
        return "<dependency><groupId>org.springframework.retry</groupId><artifactId>spring-retry</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + extra + "</dependency>";
    }

    static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
