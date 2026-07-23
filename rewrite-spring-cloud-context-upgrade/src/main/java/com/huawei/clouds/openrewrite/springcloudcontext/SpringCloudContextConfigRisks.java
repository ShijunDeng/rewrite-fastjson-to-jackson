package com.huawei.clouds.openrewrite.springcloudcontext;

import java.util.Locale;

final class SpringCloudContextConfigRisks {
    private SpringCloudContextConfigRisks() {
    }

    static String risk(String rawKey, String rawValue, boolean bootstrapFile) {
        String key = rawKey.toLowerCase(Locale.ROOT).replace('_', '-');
        String value = rawValue == null ? "" : rawValue.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if ("org.springframework.cloud.bootstrap.bootstrapconfiguration".equals(key.toLowerCase(Locale.ROOT))) {
            return "Custom BootstrapConfiguration remains a spring.factories extension; verify isolation from component scanning, ordering, parent/child context visibility, Config Data choice, AOT hints, and native reachability";
        }
        if ("spring.cloud.bootstrap.enabled".equals(key)) {
            return "Legacy bootstrap is an explicit architecture choice in the target train; verify how it is enabled, property-source precedence, logging, parent/child contexts, and whether Config Data should replace it";
        }
        if ("spring.cloud.config.initialize-on-context-refresh".equals(key)) {
            return "PropertySourceLocator timing changed after the selected releases; retest the two-phase profile fetch, source precedence, startup network calls, and refresh ordering";
        }
        if ("spring.config.import".equals(key) && lower.contains("configserver:")) {
            return "Config Data import replaces many legacy-bootstrap use cases; verify optional/fail-fast behavior, retry, credentials, profile activation, precedence, and refresh fetch ordering";
        }
        if (bootstrapFile && (key.startsWith("spring.cloud.") || "spring.application.name".equals(key) ||
                              "spring.profiles.active".equals(key))) {
            return "This bootstrap file relies on the legacy parent-context path; verify explicit bootstrap enablement or migrate the owning feature to Config Data without changing precedence";
        }
        if ("spring.cloud.refresh.enabled".equals(key)) {
            return "Context refresh is unsupported for Spring AOT/native images and must be false there; on the JVM retest rebinding, removed values, lazy proxies, and refresh ordering";
        }
        if ("spring.cloud.refresh.extra-refreshable".equals(key) ||
            "spring.cloud.refresh.never-refreshable".equals(key)) {
            return "Refresh include/exclude entries can be class names or bean names in 4.3.2; verify every target, proxy boundary, immutable/record configuration, DataSource policy, and rebind result";
        }
        if ("spring.cloud.refresh.on-restart.enabled".equals(key)) {
            return "4.3.2 refreshes scope on JVM checkpoint/restore restart by default; verify CRaC lifecycle, secret/config freshness, duplicate side effects, and whether this should be disabled";
        }
        if (key.startsWith("spring.cloud.refresh.")) {
            return "Refresh configuration changes bean recreation and retained property-source behavior; verify proxy identity, lifecycle, source ordering, and configuration-property rebinding";
        }
        if (key.startsWith("management.endpoint.env.post") ||
            key.matches("management[.]endpoint[.](?:refresh|restart|pause|resume)[.].*") ||
            (key.contains("management.endpoints") && key.endsWith("exposure.include") &&
             (lower.contains("refresh") || lower.contains("env") || lower.contains("restart") ||
              lower.contains("pause") || lower.contains("resume")))) {
            return "Environment/refresh/restart management endpoints mutate live application state; verify explicit exposure, authentication/authorization, CSRF, audit, secret sanitization, restart coupling, and operational rollback";
        }
        if (key.startsWith("encrypt.") || key.startsWith("spring.cloud.decrypt-environment-post-processor.") ||
            value.contains("{cipher}")) {
            return "Encryption bootstrap internals and providers changed across this span; verify key format/provider, Bouncy Castle versus spring-security-rsa ownership, indexed property decryption, failure policy, AOT runtime hints, and secret redaction";
        }
        return null;
    }
}
