package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class BcPkixRecommendedRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                        "com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateBcPkixJdk18onTo1_81_1"))
                .parser(JavaParser.fromJavaVersion().classpath(
                        "bcpkix-jdk18on", "bcutil-jdk18on", "bcprov-jdk18on"));
    }

    @Test
    void upgradesDependencyRenamesDeltaTypeAndMarksPemBoundary() {
        rewriteRun(
                pomXml(pom("1.75"), pom("1.81.1")),
                java("""
                        import java.io.IOException;
                        import java.io.Reader;
                        import org.bouncycastle.asn1.pkcs.Attribute;
                        import org.bouncycastle.openssl.PEMParser;
                        import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;

                        class PkiInput {
                            Object delta(Attribute value) {
                                return new DeltaCertificateRequestAttribute(value);
                            }
                            Object pem(Reader reader) throws IOException {
                                return new PEMParser(reader).readObject();
                            }
                        }
                        """, source -> source.after(actual -> {
                            assertTrue(actual.contains("DeltaCertificateRequestAttributeValue"), actual);
                            return actual;
                        }).afterRecipe(after -> assertTrue(after.printAll().contains(
                                FindBcPkix1811SourceRisks.PEM), after.printAll()))));
    }

    @Test
    void autoMigrationAndManualDeltaDraftMarkerCoexist() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.asn1.x509.Extension;
                import org.bouncycastle.cert.DeltaCertificateTool;
                import org.bouncycastle.cert.X509CertificateHolder;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;
                class DeltaDraft {
                    DeltaCertificateRequestAttribute value(Attribute attribute) {
                        return new DeltaCertificateRequestAttribute(attribute);
                    }
                    Extension extension(X509CertificateHolder holder) throws IOException {
                        return DeltaCertificateTool.makeDeltaCertificateExtension(
                                true, DeltaCertificateTool.signature, holder);
                    }
                }
                """, source -> source.after(actual -> {
                    assertTrue(actual.contains("DeltaCertificateRequestAttributeValue"), actual);
                    return actual;
                }).afterRecipe(after -> assertTrue(after.printAll().contains(
                        FindBcPkix1811SourceRisks.DELTA_DRAFT), after.printAll()))));
    }

    @Test
    void aggregateNeverDowngradesHigherBcpkix() {
        rewriteRun(pomXml(pom("1.84"), source -> source.after(actual -> actual)
                .afterRecipe(after -> assertTrue(after.printAll().contains(
                        FindBcPkix1811BuildRisks.DOWNGRADE_FORBIDDEN), after.printAll()))));
    }

    @Test
    void aggregateIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.74"), pom("1.81.1")));
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version><dependencies><dependency><groupId>org.bouncycastle</groupId>" +
               "<artifactId>bcpkix-jdk18on</artifactId><version>" + version +
               "</version></dependency></dependencies></project>";
    }
}
