package com.huawei.clouds.openrewrite.jasypt;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class JasyptConfigurationMigrationTest implements RewriteTest {
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.jasypt.MigrateJasyptSpringBootStarterTo4_0_3";

    @Test
    void canonicalizesOnlyKnownLegacyPropertiesAndMovedStarterClasses() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJasyptProperties()).cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        """
                        jasypt.encryptor.keyObtentionIterations=1000
                        jasypt.encryptor.poolSize=2
                        jasypt.encryptor.providerName=SunJCE
                        jasypt.encryptor.providerClassName=example.Provider
                        jasypt.encryptor.saltGeneratorClassname=example.Salt
                        jasypt.encryptor.ivGeneratorClassname=example.Iv
                        jasypt.encryptor.stringOutputType=base64
                        jasypt.encryptor.proxyPropertySources=true
                        jasypt.encryptor.privateKeyString=${JASYPT_PRIVATE_KEY}
                        jasypt.encryptor.privateKeyLocation=file:/run/secrets/jasypt.pem
                        jasypt.encryptor.privateKeyFormat=PEM
                        jasypt.encryptor.poolSizeSuffix=unchanged
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.ulisesbocchio.jasyptspringboot.JasyptSpringBootAutoConfiguration,com.ulisesbocchio.jasyptspringboot.JasyptSpringCloudBootstrapConfiguration
                        """,
                        """
                        jasypt.encryptor.key-obtention-iterations=1000
                        jasypt.encryptor.pool-size=2
                        jasypt.encryptor.provider-name=SunJCE
                        jasypt.encryptor.provider-class-name=example.Provider
                        jasypt.encryptor.salt-generator-classname=example.Salt
                        jasypt.encryptor.iv-generator-classname=example.Iv
                        jasypt.encryptor.string-output-type=base64
                        jasypt.encryptor.proxy-property-sources=true
                        jasypt.encryptor.private-key-string=${JASYPT_PRIVATE_KEY}
                        jasypt.encryptor.private-key-location=file:/run/secrets/jasypt.pem
                        jasypt.encryptor.private-key-format=PEM
                        jasypt.encryptor.poolSizeSuffix=unchanged
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration,com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringCloudBootstrapConfiguration
                        """,
                        source -> source.path("src/main/resources/META-INF/spring.factories")
                )
        );
    }

    @Test
    void marksEmptyFallbackBeforeGenericSecretWarning() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptPropertiesRisks()),
                properties(
                        "jasypt.encryptor.password=${JASYPT_PASSWORD:}\n",
                        "~~(Encryption password has an empty placeholder fallback; fail deployment explicitly when the secret is absent)~~>jasypt.encryptor.password=${JASYPT_PASSWORD:}\n"
                )
        );
    }

    @Test
    void acceptsExactExternalSecretReferenceButRejectsPlaintextDefault() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptPropertiesRisks()),
                properties("jasypt.encryptor.password=${JASYPT_PASSWORD}\n"),
                properties(
                        "jasypt.encryptor.password=${JASYPT_PASSWORD:local-default}\n",
                        "~~(Key material appears in a tracked properties file; move it to a secret store or injected environment/system property and rotate the exposed value)~~>jasypt.encryptor.password=${JASYPT_PASSWORD:local-default}\n"
                )
        );
    }

    @Test
    void marksLegacyCipherTupleAndClasspathPrivateKey() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptPropertiesRisks()),
                properties(
                        """
                        jasypt.encryptor.algorithm=PBEWithMD5AndDES
                        jasypt.encryptor.iv-generator-classname=org.jasypt.iv.NoIvGenerator
                        jasypt.encryptor.private-key-location=classpath:keys/private.pem
                        """,
                        """
                        ~~(Legacy PBEWithMD5AndDES is needed only to read old ciphertext; pair it with NoIvGenerator, isolate compatibility, then re-encrypt with an approved algorithm)~~>jasypt.encryptor.algorithm=PBEWithMD5AndDES
                        ~~(NoIvGenerator preserves legacy ciphertext but must not silently remain for newly encrypted values)~~>jasypt.encryptor.iv-generator-classname=org.jasypt.iv.NoIvGenerator
                        ~~(Private/secret key is loaded from the application classpath and may be packaged in the artifact; use a protected external resource or key service)~~>jasypt.encryptor.private-key-location=classpath:keys/private.pem
                        """
                )
        );
    }

    @Test
    void marksYamlSecretFilterRefreshLazyAndCiphertext() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptYamlRisks()),
                yaml(
                        """
                        jasypt:
                          encryptor:
                            password: ${JASYPT_PASSWORD:}
                            property:
                              filter:
                                exclude-names: management.*
                            refreshed-event-classes: example.SecretRotated
                        spring:
                          main:
                            lazy-initialization: true
                        sample: ENC(ciphertext)
                        """,
                        """
                        jasypt:
                          encryptor:
                            ~~(Encryption password/key has an empty placeholder fallback; fail deployment explicitly when the secret is absent)~~>password: ${JASYPT_PASSWORD:}
                            property:
                              filter:
                                ~~(Filter/skip rules define the decryption boundary; verify negative cases and actuator exposure)~~>exclude-names: management.*
                            ~~(Custom refresh events clear decrypted-value caches; verify event ordering, concurrent reads, and key rotation)~~>refreshed-event-classes: example.SecretRotated
                        spring:
                          main:
                            ~~(Lazy initialization may defer missing-secret failures until first access; add eager startup probes)~~>lazy-initialization: true
                        sample: ~~(Verify this ciphertext with the active profile's algorithm/IV/salt/provider tuple; 2.x defaults differ from 4.0.3)~~>ENC(ciphertext)
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("empty placeholder fallback"));
                            assertTrue(printed.contains("Filter/skip rules define the decryption boundary"));
                            assertTrue(printed.contains("Custom refresh events clear decrypted-value caches"));
                            assertTrue(printed.contains("Lazy initialization may defer missing-secret failures"));
                            assertTrue(printed.contains("Verify this ciphertext with the active profile"));
                        })
                )
        );
    }

    @Test
    void marksScriptAndWorkflowCommandExposureButAllowsSecretReference() {
        rewriteRun(
                spec -> spec.recipe(new FindJasyptCommandRisks()),
                text(
                        "mvn jasypt:decrypt -Djasypt.encryptor.password=${JASYPT_PASSWORD}\n",
                        "~~(Password supplied on a command line may leak through process listings, shell history, CI logs, and diagnostics; use masked secret injection)~~>mvn jasypt:decrypt -Djasypt.encryptor.password=${JASYPT_PASSWORD}\n",
                        source -> source.path("tools/decrypt.sh")
                ),
                text(
                        "export JASYPT_ENCRYPTOR_PASSWORD=${JASYPT_PASSWORD}\njava -jar app.jar\n",
                        source -> source.path("run.sh")
                )
        );
    }

    @Test
    void recommendedRecipeIsDiscoverableValidAndIdempotent() {
        Environment environment = environment();
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATION.equals(recipe.getName())));
        assertTrue(environment.activateRecipes(MIGRATION).validate().isValid());
        rewriteRun(
                spec -> spec.recipe(environment.activateRecipes(MIGRATION))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        """
                        jasypt.encryptor.password=${JASYPT_PASSWORD}
                        jasypt.encryptor.poolSize=2
                        """,
                        """
                        jasypt.encryptor.password=${JASYPT_PASSWORD}
                        jasypt.encryptor.pool-size=2
                        """
                )
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jasypt")
                .scanYamlResources()
                .build();
    }
}
