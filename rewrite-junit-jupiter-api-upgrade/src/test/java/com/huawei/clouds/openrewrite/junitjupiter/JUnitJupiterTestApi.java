package com.huawei.clouds.openrewrite.junitjupiter;

final class JUnitJupiterTestApi {
    private JUnitJupiterTestApi() { }

    static String[] sources() {
        return new String[] {
                "package org.junit.jupiter.api; public interface MethodOrderer { " +
                "class MethodName implements MethodOrderer {} @Deprecated class Alphanumeric extends MethodName {} }",
                "package org.junit.jupiter.api; import java.lang.annotation.*; " +
                "@Target(ElementType.TYPE) public @interface TestMethodOrder { Class<? extends MethodOrderer> value(); }",
                "package org.junit.jupiter.api; import java.lang.annotation.*; " +
                "@Target(ElementType.TYPE) public @interface Nested {}",
                "package org.junit.jupiter.api.extension; import java.util.function.Function; " +
                "public interface ExtensionContext { Store getStore(Namespace namespace); String getUniqueId(); " +
                "final class Namespace { public static final Namespace GLOBAL = new Namespace(); } " +
                "interface Store { " +
                "default <V> V getOrComputeIfAbsent(Class<V> type) { return null; } " +
                "default <K,V> Object getOrComputeIfAbsent(K key, Function<? super K,? extends V> creator) { return null; } " +
                "default <K,V> V getOrComputeIfAbsent(K key, Function<? super K,? extends V> creator, Class<V> type) { return null; } " +
                "default <V> V computeIfAbsent(Class<V> type) { return null; } " +
                "default <K,V> Object computeIfAbsent(K key, Function<? super K,? extends V> creator) { return null; } " +
                "default <K,V> V computeIfAbsent(K key, Function<? super K,? extends V> creator, Class<V> type) { return null; } } }",
                "package org.junit.jupiter.api.extension; public interface DynamicTestInvocationContext {}",
                "package org.junit.jupiter.api.extension; public interface InvocationInterceptor { " +
                "interface Invocation<T> { T proceed() throws Throwable; } " +
                "default void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {} " +
                "default void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext, " +
                "ExtensionContext context) throws Throwable {} }",
                "package org.junit.jupiter.api.condition; public enum JRE { JAVA_8, JAVA_9, JAVA_10, JAVA_11, " +
                "JAVA_12, JAVA_13, JAVA_14, JAVA_15, JAVA_16, JAVA_17, JAVA_18, JAVA_21, OTHER }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface EnabledOnJre { JRE[] value() default {}; int[] versions() default {}; }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface DisabledOnJre { JRE[] value() default {}; int[] versions() default {}; }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface EnabledForJreRange { " +
                "JRE min() default JRE.JAVA_8; JRE max() default JRE.OTHER; int minVersion() default -1; int maxVersion() default -1; }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface DisabledForJreRange { " +
                "JRE min() default JRE.JAVA_8; JRE max() default JRE.OTHER; int minVersion() default -1; int maxVersion() default -1; }",
                "package org.junit.jupiter.params.provider; import java.lang.annotation.*; " +
                "@Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE}) public @interface CsvSource { " +
                "String[] value() default {}; boolean useHeadersInDisplayName() default false; " +
                "boolean ignoreLeadingAndTrailingWhitespace() default true; String[] nullValues() default {}; }",
                "package org.junit.jupiter.params.provider; import java.lang.annotation.*; " +
                "@Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE}) public @interface CsvFileSource { " +
                "String[] resources() default {}; String lineSeparator() default \"\\n\"; " +
                "boolean useHeadersInDisplayName() default false; boolean ignoreLeadingAndTrailingWhitespace() default true; }"
        };
    }
}
