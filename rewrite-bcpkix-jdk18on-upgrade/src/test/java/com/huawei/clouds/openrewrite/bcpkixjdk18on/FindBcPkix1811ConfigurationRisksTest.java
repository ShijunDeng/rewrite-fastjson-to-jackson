package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class FindBcPkix1811ConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBcPkix1811ConfigurationRisks());
    }

    @ParameterizedTest(name = "compatibility switch {0}")
    @ValueSource(strings = {"org.bouncycastle.asn1.allow_wrong_oid_enc", "org.bouncycastle.pemreader.lax",
            "org.bouncycastle.drbg.effective_256bits_entropy",
            "org.bouncycastle.x509.allow_absent_equiv_NULL",
            "org.bouncycastle.x509.allow_ca_without_crl_sign"})
    void marksCompatibilityAndSecuritySwitches(String key) {
        rewriteRun(markedProperties(key + "=true", FindBcPkix1811ConfigurationRisks.COMPATIBILITY_SWITCH));
    }

    @Test
    void marksJavaSecurityProviderOrder() {
        rewriteRun(markedProperties(
                "security.provider.3=org.bouncycastle.jce.provider.BouncyCastleProvider",
                FindBcPkix1811ConfigurationRisks.PROVIDER_ORDER));
    }

    @Test
    void marksYamlProviderAndExactCompatibilityKey() {
        rewriteRun(
                yaml("""
                        security:
                          provider.4: org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcPkix1811ConfigurationRisks.PROVIDER_ORDER))),
                yaml("""
                        crypto:
                          org.bouncycastle.pemreader.lax: true
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(),
                                        FindBcPkix1811ConfigurationRisks.COMPATIBILITY_SWITCH))));
    }

    @Test
    void marksXmlSystemPropertyConfiguration() {
        rewriteRun(xml("""
                <configuration>
                  <property name="org.bouncycastle.x509.allow_absent_equiv_NULL" value="true"/>
                </configuration>
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(),
                                FindBcPkix1811ConfigurationRisks.COMPATIBILITY_SWITCH))));
    }

    @Test
    void marksManifestAndBndPackagingMetadata() {
        rewriteRun(
                text("Import-Package: org.bouncycastle.*", source -> source.path("META-INF/MANIFEST.MF")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcPkix1811ConfigurationRisks.PACKAGING))),
                text("-includeresource: @bcpkix-jdk18on.jar!/META-INF/**", source -> source.path("bnd.bnd")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcPkix1811ConfigurationRisks.PACKAGING))));
    }

    @Test
    void arbitraryLookalikesAndPomTextAreNoop() {
        rewriteRun(
                properties("example.pemreader.lax=true", source -> source.afterRecipe(after ->
                        assertNoMarker(after.printAll()))),
                properties("documentation=org.bouncycastle.pemreader.lax", source ->
                        source.path("log4j2.component.properties").afterRecipe(after ->
                                assertNoMarker(after.printAll()))),
                yaml("provider: com.example.BouncyCastleProviderFactory", source -> source.afterRecipe(after ->
                        assertNoMarker(after.printAll()))),
                yaml("docs: org.bouncycastle.pemreader.lax", source -> source.path("application.yml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                yaml("docs:\n  provider.4: org.bouncycastle.jce.provider.BouncyCastleProvider", source ->
                        source.path("documentation.yml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml("<docs><code>org.bouncycastle.pemreader.lax</code></docs>", source ->
                        source.path("docs.xml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml("<project><name>org.bouncycastle.pemreader.lax</name></project>", source -> source.path("pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("org.bouncycastle.pemreader.lax=true", source -> source.path("README.md")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksExactPlainJavaSecurityEntryButIgnoresComments() {
        rewriteRun(text("security.provider.3=org.bouncycastle.jce.provider.BouncyCastleProvider", source ->
                source.path("java.security").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindBcPkix1811ConfigurationRisks.PROVIDER_ORDER))));
        rewriteRun(text("# org.bouncycastle.pemreader.lax=true", source -> source.path("java.security")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
        rewriteRun(text("// documentation mentions org.bouncycastle only", source -> source.path("sandbox.policy")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedConfigurationsAreSkippedAndMarkersAreIdempotent() {
        rewriteRun(properties("org.bouncycastle.pemreader.lax=true", source ->
                source.path("target/classes/application.properties").afterRecipe(after ->
                        assertNoMarker(after.printAll()))));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("org.bouncycastle.pemreader.lax=true", source -> source.after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(),
                                FindBcPkix1811ConfigurationRisks.COMPATIBILITY_SWITCH, 1))));
    }

    private static SourceSpecs markedProperties(String value, String message) {
        return properties(value, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message)));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("#~~(") || actual.contains("<!--~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
