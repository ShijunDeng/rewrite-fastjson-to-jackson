package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Canonicalizes deterministic Jasypt configuration and starter auto-configuration names. */
public final class MigrateJasyptProperties extends Recipe {
    private static final Map<String, String> KEYS = new LinkedHashMap<>();
    private static final Map<String, String> CLASS_NAMES = Map.of(
            "com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration",
            "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration",
            "com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration",
            "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringCloudBootstrapConfiguration"
    );

    static {
        KEYS.put("jasypt.encryptor.keyObtentionIterations", "jasypt.encryptor.key-obtention-iterations");
        KEYS.put("jasypt.encryptor.poolSize", "jasypt.encryptor.pool-size");
        KEYS.put("jasypt.encryptor.providerName", "jasypt.encryptor.provider-name");
        KEYS.put("jasypt.encryptor.providerClassName", "jasypt.encryptor.provider-class-name");
        KEYS.put("jasypt.encryptor.saltGeneratorClassname", "jasypt.encryptor.salt-generator-classname");
        KEYS.put("jasypt.encryptor.ivGeneratorClassname", "jasypt.encryptor.iv-generator-classname");
        KEYS.put("jasypt.encryptor.stringOutputType", "jasypt.encryptor.string-output-type");
        KEYS.put("jasypt.encryptor.proxyPropertySources", "jasypt.encryptor.proxy-property-sources");
        KEYS.put("jasypt.encryptor.privateKeyString", "jasypt.encryptor.private-key-string");
        KEYS.put("jasypt.encryptor.privateKeyLocation", "jasypt.encryptor.private-key-location");
        KEYS.put("jasypt.encryptor.privateKeyFormat", "jasypt.encryptor.private-key-format");
    }

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Jasypt properties";
    }

    @Override
    public String getDescription() {
        return "Canonicalize legacy camel-case Jasypt keys and update the two starter auto-configuration class names moved to the jasyptspringbootstarter package.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = KEYS.get(e.getKey());
                if (key != null) {
                    e = e.withKey(key);
                }
                String value = e.getValue().getText();
                String migrated = value;
                for (Map.Entry<String, String> className : CLASS_NAMES.entrySet()) {
                    migrated = migrated.replace(className.getKey(), className.getValue());
                }
                return migrated.equals(value) ? e : e.withValue(e.getValue().withText(migrated));
            }
        };
    }
}
