package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

class MigrateBcPkix1811JavaTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateDeterministicBcPkix1_81_1Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe())
                .parser(JavaParser.fromJavaVersion().classpath(
                        "bcpkix-jdk18on", "bcutil-jdk18on", "bcprov-jdk18on"));
    }

    @Test
    void directlyComposesOfficialChangeTypeBeforeTheCustomPkmacGapRecipe() {
        assertEquals(List.of(
                        "org.openrewrite.java.ChangeType",
                        "com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateBcPkix1811Java"),
                recipe().getRecipeList().stream().map(Recipe::getName)
                        .filter(name -> !name.contains("PreconditionBellwether"))
                        .toList());
    }

    @Test
    void renamesDeltaCertificateRequestAttributeAndPreservesUseSites() {
        rewriteRun(java(
                """
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;

                class DeltaRequest {
                    DeltaCertificateRequestAttribute parse(Attribute attribute) {
                        DeltaCertificateRequestAttribute value =
                                new DeltaCertificateRequestAttribute(attribute);
                        value.getSubject();
                        value.getSubjectPKInfo();
                        value.getExtensions();
                        value.getSignatureAlgorithm();
                        return value;
                    }
                }
                """,
                """
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue;

                class DeltaRequest {
                    DeltaCertificateRequestAttributeValue parse(Attribute attribute) {
                        DeltaCertificateRequestAttributeValue value =
                                new DeltaCertificateRequestAttributeValue(attribute);
                        value.getSubject();
                        value.getSubjectPKInfo();
                        value.getExtensions();
                        value.getSignatureAlgorithm();
                        return value;
                    }
                }
                """));
    }

    @Test
    void renamesFullyQualifiedDeltaCertificateType() {
        rewriteRun(java(
                """
                class DeltaRequest {
                    org.bouncycastle.pkcs.DeltaCertificateRequestAttribute parse(
                            org.bouncycastle.asn1.pkcs.Attribute attribute) {
                        return new org.bouncycastle.pkcs.DeltaCertificateRequestAttribute(attribute);
                    }
                }
                """,
                """
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue;

                class DeltaRequest {
                    DeltaCertificateRequestAttributeValue parse(
                            org.bouncycastle.asn1.pkcs.Attribute attribute) {
                        return new DeltaCertificateRequestAttributeValue(attribute);
                    }
                }
                """));
    }

    @Test
    void migratesTheRealBcJava174DeltaCertTestShape() {
        // bcgit/bc-java r1rv74 (434cab9b), pkix/src/test/java/.../DeltaCertTest.java#L721-L733
        rewriteRun(java(
                """
                import org.bouncycastle.asn1.ASN1ObjectIdentifier;
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;
                import org.bouncycastle.pkcs.PKCS10CertificationRequest;

                class DeltaCertTest {
                    Object parse(PKCS10CertificationRequest pkcs10CertReq) {
                        Attribute[] attributes = pkcs10CertReq.getAttributes(
                                new ASN1ObjectIdentifier("2.16.840.1.114027.80.6.2"));
                        DeltaCertificateRequestAttribute deltaReq =
                                new DeltaCertificateRequestAttribute(attributes[0]);
                        return deltaReq.getSubjectPKInfo();
                    }
                }
                """,
                """
                import org.bouncycastle.asn1.ASN1ObjectIdentifier;
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue;
                import org.bouncycastle.pkcs.PKCS10CertificationRequest;

                class DeltaCertTest {
                    Object parse(PKCS10CertificationRequest pkcs10CertReq) {
                        Attribute[] attributes = pkcs10CertReq.getAttributes(
                                new ASN1ObjectIdentifier("2.16.840.1.114027.80.6.2"));
                        DeltaCertificateRequestAttributeValue deltaReq =
                                new DeltaCertificateRequestAttributeValue(attributes[0]);
                        return deltaReq.getSubjectPKInfo();
                    }
                }
                """));
    }

    @Test
    void unwrapsOnlyInlinePkmacGeneratorWithAttributedTypes() {
        rewriteRun(java(
                """
                package org.bouncycastle.cert.crmf;

                import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

                class CrmfRequest {
                    ProofOfPossessionSigningKeyBuilder configure(
                            SubjectPublicKeyInfo key, PKMACBuilder mac, char[] password) throws CRMFException {
                        return new ProofOfPossessionSigningKeyBuilder(key)
                                .setPublicKeyMac(new PKMACValueGenerator(mac), password);
                    }
                }
                """,
                """
                package org.bouncycastle.cert.crmf;

                import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

                class CrmfRequest {
                    ProofOfPossessionSigningKeyBuilder configure(
                            SubjectPublicKeyInfo key, PKMACBuilder mac, char[] password) throws CRMFException {
                        return new ProofOfPossessionSigningKeyBuilder(key)
                                .setPublicKeyMac(mac, password);
                    }
                }
                """));
    }

    @Test
    void preservesTheRealBcJava174SharedPkmacGeneratorShapeForReview() {
        // bcgit/bc-java r1rv74 (434cab9b), CertificateRequestMessageBuilder.java#L285-L299
        rewriteRun(java("""
                package org.bouncycastle.cert.crmf;

                class CertificateRequestMessageBuilder {
                    void addProof(ProofOfPossessionSigningKeyBuilder builder,
                                  PKMACBuilder pkmacBuilder,
                                  char[] password) throws CRMFException {
                        PKMACValueGenerator pkmacGenerator = new PKMACValueGenerator(pkmacBuilder);
                        builder.setPublicKeyMac(pkmacGenerator, password);
                    }
                }
                """));
    }

    @Test
    void leavesSharedPkmacGeneratorForReview() {
        rewriteRun(java("""
                package org.bouncycastle.cert.crmf;

                import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

                class CrmfRequest {
                    void configure(SubjectPublicKeyInfo key, PKMACBuilder mac, char[] password)
                            throws CRMFException {
                        PKMACValueGenerator generator = new PKMACValueGenerator(mac);
                        new ProofOfPossessionSigningKeyBuilder(key)
                                .setPublicKeyMac(generator, password);
                    }
                }
                """));
    }

    @Test
    void refusesUnattributedAndThirdPartyLookalikes() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion())
                        .typeValidationOptions(TypeValidation.none()),
                java("""
                        class DeltaCertificateRequestAttribute {
                            DeltaCertificateRequestAttribute(Object ignored) { }
                        }
                        class Lookalike {
                            Object parse(Object value) {
                                return new DeltaCertificateRequestAttribute(value);
                            }
                        }
                        """),
                java("""
                        package org.bouncycastle.pkcs.application;
                        class DeltaCertificateRequestAttribute { }
                        """));
    }

    @Test
    void skipsGeneratedSourcesAndIsIdempotent() {
        rewriteRun(java(
                """
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;
                class Generated {
                    Object parse(Attribute value) {
                        return new DeltaCertificateRequestAttribute(value);
                    }
                }
                """, source -> source.path("target/generated/Generated.java")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;
                class DeltaRequest {
                    Object parse(Attribute value) {
                        return new DeltaCertificateRequestAttribute(value);
                    }
                }
                """,
                """
                import org.bouncycastle.asn1.pkcs.Attribute;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue;

                class DeltaRequest {
                    Object parse(Attribute value) {
                        return new DeltaCertificateRequestAttributeValue(value);
                    }
                }
                """));
    }

    private static Recipe recipe() {
        return Environment.builder()
                .scanRuntimeClasspath(
                        "com.huawei.clouds.openrewrite.bcpkixjdk18on",
                        "org.openrewrite.java")
                .build()
                .activateRecipes(RECIPE);
    }
}
