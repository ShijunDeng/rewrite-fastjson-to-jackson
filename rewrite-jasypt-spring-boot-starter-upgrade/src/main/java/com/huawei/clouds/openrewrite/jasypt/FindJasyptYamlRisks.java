package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

/** Marks security- and behavior-sensitive Jasypt settings in YAML. */
public final class FindJasyptYamlRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Jasypt YAML migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark tracked key material, algorithm/IV compatibility, custom interception, proxy, lazy-init, filter, and cache-refresh settings in YAML.";
    }

    @Override
    public YamlIsoVisitor<ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            private final List<JsonPathMatcher> secrets = matchers(
                    "$.jasypt.encryptor.password", "$.jasypt.encryptor.private-key-string",
                    "$.jasypt.encryptor.privateKeyString", "$.jasypt.encryptor.gcm-secret-key-string",
                    "$.jasypt.encryptor.gcm-secret-key-password", "$.jasypt.plugin.old.password");
            private final List<JsonPathMatcher> keyLocations = matchers(
                    "$.jasypt.encryptor.private-key-location", "$.jasypt.encryptor.privateKeyLocation",
                    "$.jasypt.encryptor.gcm-secret-key-location");
            private final JsonPathMatcher algorithm = new JsonPathMatcher("$.jasypt.encryptor.algorithm");
            private final List<JsonPathMatcher> iv = matchers(
                    "$.jasypt.encryptor.iv-generator-classname", "$.jasypt.encryptor.ivGeneratorClassname");
            private final List<JsonPathMatcher> custom = matchers(
                    "$.jasypt.encryptor.bean", "$.jasypt.encryptor.property.detector-bean",
                    "$.jasypt.encryptor.property.resolver-bean", "$.jasypt.encryptor.property.filter-bean");
            private final List<JsonPathMatcher> proxy = matchers(
                    "$.jasypt.encryptor.proxy-property-sources", "$.jasypt.encryptor.proxyPropertySources");
            private final List<JsonPathMatcher> compatibilityTuple = matchers(
                    "$.jasypt.encryptor.key-obtention-iterations", "$.jasypt.encryptor.keyObtentionIterations",
                    "$.jasypt.encryptor.salt-generator-classname", "$.jasypt.encryptor.saltGeneratorClassname",
                    "$.jasypt.encryptor.string-output-type", "$.jasypt.encryptor.stringOutputType",
                    "$.jasypt.encryptor.provider-name", "$.jasypt.encryptor.providerName");
            private final JsonPathMatcher refresh = new JsonPathMatcher("$.jasypt.encryptor.refreshed-event-classes");
            private final JsonPathMatcher lazy = new JsonPathMatcher("$.spring.main.lazy-initialization");
            private final List<JsonPathMatcher> filters = matchers(
                    "$.jasypt.encryptor.property.filter.include-sources",
                    "$.jasypt.encryptor.property.filter.exclude-sources",
                    "$.jasypt.encryptor.property.filter.include-names",
                    "$.jasypt.encryptor.property.filter.exclude-names",
                    "$.jasypt.encryptor.skip-property-sources");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                String value = e.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue().trim() : "";
                if (matches(secrets) && emptyFallback(value)) {
                    return SearchResult.found(e,
                            "Encryption password/key has an empty placeholder fallback; fail deployment explicitly when the secret is absent");
                }
                if (matches(secrets) && !externalReference(value)) {
                    return SearchResult.found(e,
                            "Key material appears in tracked YAML; move it to an injected secret source and rotate the exposed value");
                }
                if (matches(keyLocations) && value.startsWith("classpath:")) {
                    return SearchResult.found(e,
                            "Private/secret key may be packaged in the application artifact; use a protected external resource or key service");
                }
                if (algorithm.matches(getCursor())) {
                    return SearchResult.found(e, "Verify this algorithm with the exact IV, salt, iterations, provider, output encoding, and existing ciphertext");
                }
                if (matches(iv) && value.endsWith("NoIvGenerator")) {
                    return SearchResult.found(e,
                            "NoIvGenerator is a legacy-compatibility setting; isolate it and re-encrypt rather than using it for new values");
                }
                if (matches(custom)) {
                    return SearchResult.found(e,
                            "Custom encryptor/detector/resolver/filter changes the full pipeline; verify bean names and initialization order on Boot 3.5");
                }
                if (matches(proxy) && "true".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "CGLIB property-source proxying changes identity/getSource behavior; test every custom PropertySource");
                }
                if (matches(compatibilityTuple)) {
                    return SearchResult.found(e,
                            "This value is part of the ciphertext compatibility tuple; test it together with algorithm, IV, salt, provider, pool, and output encoding");
                }
                if (refresh.matches(getCursor())) {
                    return SearchResult.found(e,
                            "Custom refresh events clear decrypted-value caches; verify event ordering, concurrent reads, and key rotation");
                }
                if (lazy.matches(getCursor()) && "true".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "Lazy initialization may defer missing-secret failures until first access; add eager startup probes");
                }
                if (matches(filters)) {
                    return SearchResult.found(e,
                            "Filter/skip rules define the decryption boundary; verify negative cases and actuator exposure");
                }
                return e;
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Yaml.Scalar s = super.visitScalar(scalar, ctx);
                String value = s.getValue();
                if (value.contains("DEC(")) {
                    return SearchResult.found(s, "DEC(...) is plaintext Maven-plugin staging syntax and must not be committed or packaged");
                }
                if (value.contains("-Djasypt.encryptor.password=") ||
                    value.contains("--jasypt.encryptor.password=")) {
                    return SearchResult.found(s,
                            "Password supplied on a command line may leak through process listings, shell history, CI logs, and diagnostics; use masked secret injection");
                }
                if (value.matches("(?s).*JASYPT_ENCRYPTOR_PASSWORD\\s*=\\s*[^$\\s][^\\r\\n]*.*")) {
                    return SearchResult.found(s,
                            "Workflow assigns a concrete Jasypt password; replace it with a secret-store reference and rotate the value");
                }
                if (value.contains("jasypt:decrypt") || value.contains("jasypt:reencrypt")) {
                    return SearchResult.found(s,
                            "Decrypt/reencrypt handles plaintext and old/new keys; mask output and audit logs and temporary files");
                }
                if (value.contains("ENC(")) {
                    return SearchResult.found(s,
                            "Verify this ciphertext with the active profile's algorithm/IV/salt/provider tuple; 2.x defaults differ from 4.0.3");
                }
                return s;
            }

            private boolean matches(List<JsonPathMatcher> paths) {
                return paths.stream().anyMatch(path -> path.matches(getCursor()));
            }
        };
    }

    private static List<JsonPathMatcher> matchers(String... paths) {
        return java.util.Arrays.stream(paths).map(JsonPathMatcher::new).toList();
    }

    private static boolean externalReference(String value) {
        return value.matches("\\$\\{[A-Za-z0-9_.-]+}");
    }

    private static boolean emptyFallback(String value) {
        return value.matches("\\$\\{[^}:]+:\\s*}");
    }
}
