package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Set;

/** Marks security- and behavior-sensitive Jasypt settings in properties files. */
public final class FindJasyptPropertiesRisks extends Recipe {
    private static final Set<String> SECRET_KEYS = Set.of(
            "jasypt.encryptor.password", "jasypt.encryptor.private-key-string",
            "jasypt.encryptor.privateKeyString", "jasypt.encryptor.gcm-secret-key-string",
            "jasypt.encryptor.gcm-secret-key-password", "jasypt.plugin.old.password"
    );
    private static final Set<String> CUSTOM_COMPONENT_KEYS = Set.of(
            "jasypt.encryptor.bean", "jasypt.encryptor.property.detector-bean",
            "jasypt.encryptor.property.resolver-bean", "jasypt.encryptor.property.filter-bean"
    );
    private static final Set<String> COMPATIBILITY_TUPLE_KEYS = Set.of(
            "jasypt.encryptor.key-obtention-iterations", "jasypt.encryptor.salt-generator-classname",
            "jasypt.encryptor.string-output-type", "jasypt.encryptor.provider-name",
            "jasypt.encryptor.provider-class-name", "jasypt.encryptor.gcm-secret-key-salt",
            "jasypt.encryptor.gcm-secret-key-algorithm"
    );

    @Override
    public String getDisplayName() {
        return "Find Jasypt properties migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark plaintext key material, default-algorithm ambiguity, weak compatibility crypto, custom property interception, proxying, lazy startup, and cache-refresh behavior.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                return JasyptVersions.isProjectPath(file.getSourcePath()) ? super.visitFile(file, ctx) : file;
            }

            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey();
                String value = e.getValue().getText().trim();
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                boolean hasAlgorithm = hasKey(file, "jasypt.encryptor.algorithm");
                boolean hasIv = hasKey(file, "jasypt.encryptor.iv-generator-classname") ||
                                hasKey(file, "jasypt.encryptor.ivGeneratorClassname");

                if (SECRET_KEYS.contains(key) && emptyFallback(value)) {
                    return SearchResult.found(e,
                            "Encryption password has an empty placeholder fallback; fail deployment explicitly when the secret is absent");
                }
                if (SECRET_KEYS.contains(key) && !externalReference(value)) {
                    return SearchResult.found(e,
                            "Key material appears in a tracked properties file; move it to a secret store or injected environment/system property and rotate the exposed value");
                }
                if ((key.endsWith("private-key-location") || key.endsWith("privateKeyLocation") ||
                     key.endsWith("gcm-secret-key-location")) && value.startsWith("classpath:")) {
                    return SearchResult.found(e,
                            "Private/secret key is loaded from the application classpath and may be packaged in the artifact; use a protected external resource or key service");
                }
                if ("jasypt.encryptor.algorithm".equals(key)) {
                    if ("PBEWithMD5AndDES".equalsIgnoreCase(value)) {
                        return SearchResult.found(e,
                                "Legacy PBEWithMD5AndDES is needed only to read old ciphertext; pair it with NoIvGenerator, isolate compatibility, then re-encrypt with an approved algorithm");
                    }
                    if (value.toUpperCase(java.util.Locale.ROOT).contains("AES") && !hasIv) {
                        return SearchResult.found(e,
                                "AES algorithm is explicit but IV generator is not colocated; verify the generating profile used RandomIvGenerator and the same provider/settings");
                    }
                }
                if (("jasypt.encryptor.iv-generator-classname".equals(key) ||
                     "jasypt.encryptor.ivGeneratorClassname".equals(key)) && value.endsWith("NoIvGenerator")) {
                    return SearchResult.found(e,
                            "NoIvGenerator preserves legacy ciphertext but must not silently remain for newly encrypted values");
                }
                if (CUSTOM_COMPONENT_KEYS.contains(key)) {
                    return SearchResult.found(e,
                            "Custom encryptor/detector/resolver/filter bean changes the decryption pipeline; verify bean name, initialization order, and all target interfaces on Boot 3.5");
                }
                if (("jasypt.encryptor.proxy-property-sources".equals(key) ||
                     "jasypt.encryptor.proxyPropertySources".equals(key)) && "true".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "CGLIB property-source proxying preserves concrete types but changes identity/getSource behavior; test every custom PropertySource on Boot 3.5");
                }
                if (key.startsWith("jasypt.encryptor.property.filter.") ||
                    "jasypt.encryptor.skip-property-sources".equals(key)) {
                    return SearchResult.found(e,
                            "Property filter/skip rules change which names and sources are decrypted; test negative cases and ensure secrets are neither skipped nor exposed by actuator endpoints");
                }
                if ("jasypt.encryptor.refreshed-event-classes".equals(key)) {
                    return SearchResult.found(e,
                            "Custom refresh events clear the decrypted-value cache; verify event availability, ordering, concurrent reads, and secret rotation");
                }
                if ("spring.main.lazy-initialization".equals(key) && "true".equalsIgnoreCase(value)) {
                    return SearchResult.found(e,
                            "Global lazy initialization can defer missing-password and custom-encryptor failures until first access; add eager startup probes");
                }
                if (value.contains("DEC(")) {
                    return SearchResult.found(e,
                            "DEC(...) is Maven-plugin plaintext staging syntax and must not be committed or packaged");
                }
                if (value.contains("ENC(") && !hasAlgorithm) {
                    return SearchResult.found(e,
                            "Ciphertext has no algorithm declaration in this file; verify the runtime profile and preserve 2.x PBEWithMD5AndDES/NoIvGenerator only long enough to re-encrypt");
                }
                if (COMPATIBILITY_TUPLE_KEYS.contains(key)) {
                    return SearchResult.found(e,
                            "This value is part of the ciphertext compatibility tuple; verify algorithm, IV, salt, iterations, provider, pool, and output encoding together");
                }
                return e;
            }
        };
    }

    private static boolean hasKey(Properties.File file, String key) {
        return file != null && file.getContent().stream().filter(Properties.Entry.class::isInstance)
                .map(Properties.Entry.class::cast).anyMatch(entry -> key.equals(entry.getKey()));
    }

    private static boolean externalReference(String value) {
        return value.matches("\\$\\{[A-Za-z0-9_.-]+}");
    }

    private static boolean emptyFallback(String value) {
        return value.matches("\\$\\{[^}:]+:\\s*}");
    }
}
