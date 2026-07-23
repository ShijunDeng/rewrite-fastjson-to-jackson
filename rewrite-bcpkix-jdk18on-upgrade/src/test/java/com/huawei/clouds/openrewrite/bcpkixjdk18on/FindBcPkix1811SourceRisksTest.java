package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindBcPkix1811SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBcPkix1811SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath(
                        "bcpkix-jdk18on", "bcutil-jdk18on", "bcprov-jdk18on"));
    }

    @Test
    void marksRemovedDeltaCertificateDraftApis() {
        rewriteRun(markedJava("""
                import java.io.IOException;
                import org.bouncycastle.asn1.x509.Extension;
                import org.bouncycastle.cert.DeltaCertificateTool;
                import org.bouncycastle.cert.X509CertificateHolder;
                import org.bouncycastle.pkcs.DeltaCertificateRequestAttribute;

                class DeltaDraft {
                    DeltaCertificateRequestAttribute value;
                    Extension extension(X509CertificateHolder holder) throws IOException {
                        return DeltaCertificateTool.makeDeltaCertificateExtension(
                                true, DeltaCertificateTool.signature | DeltaCertificateTool.subject, holder);
                    }
                }
                """, FindBcPkix1811SourceRisks.DELTA_DRAFT));
    }

    @Test
    void marksSharedPkmacWrapperThatCannotBeAutoUnwrapped() {
        rewriteRun(markedJava("""
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
                """, FindBcPkix1811SourceRisks.PKMAC));
    }

    @Test
    void marksCovariantCertificateRepMessageAbiBoundary() {
        rewriteRun(markedJava("""
                import org.bouncycastle.asn1.ASN1Encodable;
                import org.bouncycastle.cert.crmf.CertificateRepMessage;
                class CmpClient {
                    ASN1Encodable encode(CertificateRepMessage message) {
                        return message.toASN1Structure();
                    }
                }
                """, FindBcPkix1811SourceRisks.BINARY_RETURN));
    }

    @Test
    void marksRealKeystoreExplorerCmsReplaceSignersShape() {
        // Extracted from active upstream:
        // https://github.com/kaikramer/keystore-explorer/blob/740ff3c04eb4916dac3309754cfc58809f3d539b/kse/src/main/java/org/kse/crypto/signing/CmsSigner.java#L107-L113
        rewriteRun(markedJava("""
                import org.bouncycastle.cms.CMSSignedData;
                import org.bouncycastle.cms.SignerInformationStore;
                class CmsTimestamp {
                    CMSSignedData addTimestamp(CMSSignedData signedData,
                                               SignerInformationStore signerInfos) {
                        return CMSSignedData.replaceSigners(signedData, signerInfos);
                    }
                }
                """, FindBcPkix1811SourceRisks.CMS_DIGESTS));
    }

    @Test
    void marksCmsAuthEnvelopedParsingBoundary() {
        rewriteRun(markedJava("""
                import org.bouncycastle.cms.CMSAuthEnvelopedData;
                import org.bouncycastle.cms.CMSException;
                class AuthEnveloped {
                    CMSAuthEnvelopedData parse(byte[] encoded) throws CMSException {
                        return new CMSAuthEnvelopedData(encoded);
                    }
                }
                """, FindBcPkix1811SourceRisks.CMS_AUTH_ENVELOPED));
    }

    @Test
    void marksSignerInfoGeneratorCopyConstructor() {
        rewriteRun(markedJava("""
                import org.bouncycastle.cms.CMSAttributeTableGenerator;
                import org.bouncycastle.cms.SignerInfoGenerator;
                class SignerCopy {
                    SignerInfoGenerator copy(SignerInfoGenerator source,
                                             CMSAttributeTableGenerator signed,
                                             CMSAttributeTableGenerator unsigned) {
                        return new SignerInfoGenerator(source, signed, unsigned);
                    }
                }
                """, FindBcPkix1811SourceRisks.SIGNER_COPY));
    }

    @Test
    void marksPkcs12MacValidationBoundary() {
        rewriteRun(markedJava("""
                import java.io.IOException;
                import org.bouncycastle.pkcs.PKCS12MacCalculatorBuilderProvider;
                import org.bouncycastle.pkcs.PKCS12PfxPdu;
                import org.bouncycastle.pkcs.PKCSException;
                class PfxReader {
                    boolean validate(byte[] bytes, PKCS12MacCalculatorBuilderProvider provider, char[] password)
                            throws IOException, PKCSException {
                        return new PKCS12PfxPdu(bytes).isMacValid(provider, password);
                    }
                }
                """, FindBcPkix1811SourceRisks.PKCS));
    }

    @Test
    void marksRealNettyPemParsingShape() {
        // Extracted from active upstream:
        // https://github.com/netty/netty/blob/e64a6b505d54cf1478b9c804f6508333626070a5/example/src/main/java/io/netty/example/ocsp/OcspServerExample.java#L178-L190
        rewriteRun(markedJava("""
                import java.io.IOException;
                import java.io.Reader;
                import org.bouncycastle.openssl.PEMParser;
                class PemCertificates {
                    Object parse(Reader reader) throws IOException {
                        PEMParser parser = new PEMParser(reader);
                        return parser.readObject();
                    }
                }
                """, FindBcPkix1811SourceRisks.PEM));
    }

    @Test
    void marksRealPdfboxOcspRequestAndVerificationShapes() {
        // Extracted from active upstream:
        // https://github.com/apache/pdfbox/blob/29282601d914ae1834918f388d69ec5f7483cc60/examples/src/main/java/org/apache/pdfbox/examples/signature/cert/OcspHelper.java#L401-L409
        // https://github.com/apache/pdfbox/blob/29282601d914ae1834918f388d69ec5f7483cc60/examples/src/main/java/org/apache/pdfbox/examples/signature/cert/OcspHelper.java#L599-L603
        rewriteRun(markedJava("""
                import org.bouncycastle.cert.ocsp.BasicOCSPResp;
                import org.bouncycastle.cert.ocsp.OCSPException;
                import org.bouncycastle.cert.ocsp.OCSPReq;
                import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
                import org.bouncycastle.operator.ContentVerifierProvider;
                class OcspClient {
                    OCSPReq request() throws OCSPException {
                        return new OCSPReqBuilder().build();
                    }
                    boolean verify(BasicOCSPResp response, ContentVerifierProvider verifier)
                            throws OCSPException {
                        return response.isSignatureValid(verifier);
                    }
                }
                """, FindBcPkix1811SourceRisks.OCSP));
    }

    @Test
    void marksRealPdfboxTimestampRequestShape() {
        // Extracted from active upstream:
        // https://github.com/apache/pdfbox/blob/29282601d914ae1834918f388d69ec5f7483cc60/examples/src/main/java/org/apache/pdfbox/examples/signature/TSAClient.java#L97-L105
        rewriteRun(markedJava("""
                import java.math.BigInteger;
                import org.bouncycastle.asn1.ASN1ObjectIdentifier;
                import org.bouncycastle.tsp.TimeStampRequest;
                import org.bouncycastle.tsp.TimeStampRequestGenerator;
                class TsaClient {
                    TimeStampRequest request(ASN1ObjectIdentifier oid, byte[] hash) {
                        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
                        generator.setCertReq(true);
                        return generator.generate(oid, hash, BigInteger.ONE);
                    }
                }
                """, FindBcPkix1811SourceRisks.TSP));
    }

    @Test
    void marksCertificateConversionAndOperatorProviderBoundaries() {
        rewriteRun(
                markedJava("""
                        import java.security.cert.CertificateException;
                        import java.security.cert.X509Certificate;
                        import org.bouncycastle.cert.X509CertificateHolder;
                        import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
                        class CertificateConversion {
                            X509Certificate convert(X509CertificateHolder holder) throws CertificateException {
                                return new JcaX509CertificateConverter().setProvider("BC")
                                        .getCertificate(holder);
                            }
                        }
                        """, FindBcPkix1811SourceRisks.PKIX),
                markedJava("""
                        import java.security.PrivateKey;
                        import org.bouncycastle.operator.ContentSigner;
                        import org.bouncycastle.operator.OperatorCreationException;
                        import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
                        class OperatorBoundary {
                            ContentSigner signer(PrivateKey key) throws OperatorCreationException {
                                return new JcaContentSignerBuilder("SHA256withRSA")
                                        .setProvider("BC").build(key);
                            }
                        }
                        """, FindBcPkix1811SourceRisks.PROVIDER));
    }

    @Test
    void marksCompositeVerifierSecurityBoundary() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion()
                        .classpath("bcpkix-jdk18on", "bcutil-jdk18on", "bcprov-jdk18on")
                        .dependsOn("""
                                package org.bouncycastle.jcajce;
                                public class CompositePublicKey implements java.security.PublicKey {
                                    public String getAlgorithm() { return "COMPOSITE"; }
                                    public String getFormat() { return "X.509"; }
                                    public byte[] getEncoded() { return new byte[0]; }
                                }
                                """)),
                markedJava("""
                        import org.bouncycastle.jcajce.CompositePublicKey;
                        import org.bouncycastle.operator.ContentVerifierProvider;
                        import org.bouncycastle.operator.OperatorCreationException;
                        import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
                        class CompositeVerification {
                            ContentVerifierProvider verifier(CompositePublicKey key)
                                    throws OperatorCreationException {
                                return new JcaContentVerifierProviderBuilder().build(key);
                            }
                        }
                        """, FindBcPkix1811SourceRisks.COMPOSITE));
    }

    @Test
    void marksAsn1EncodingAndJavaSerializationBoundaries() {
        rewriteRun(
                markedJava("""
                        import java.io.IOException;
                        import org.bouncycastle.asn1.ASN1Primitive;
                        class Asn1Boundary {
                            ASN1Primitive parse(byte[] bytes) throws IOException {
                                return ASN1Primitive.fromByteArray(bytes);
                            }
                        }
                        """, FindBcPkix1811SourceRisks.ASN1),
                markedJava("""
                        import java.io.IOException;
                        import java.io.ObjectOutputStream;
                        import org.bouncycastle.cert.X509CertificateHolder;
                        class SerializedCertificate {
                            void write(ObjectOutputStream out, X509CertificateHolder certificate)
                                    throws IOException {
                                out.writeObject(certificate);
                            }
                        }
                        """, FindBcPkix1811SourceRisks.SERIALIZATION));
    }

    @Test
    void marksLdapCertStoreButNotUnrelatedNetworkCode() {
        rewriteRun(
                markedJava("""
                        import java.security.InvalidAlgorithmParameterException;
                        import java.security.NoSuchAlgorithmException;
                        import java.security.cert.CertStore;
                        import java.security.cert.LDAPCertStoreParameters;
                        class LdapCertificates {
                            CertStore open() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
                                return CertStore.getInstance("LDAP",
                                        new LDAPCertStoreParameters("ldap.example", 389));
                            }
                        }
                        """, FindBcPkix1811SourceRisks.LDAP),
                java("""
                        class NetworkClient {
                            String open(String protocol) { return protocol; }
                        }
                        """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void lookalikesStringsAndGeneratedSourcesAreNoop() {
        rewriteRun(
                java("""
                        class CMSSignedData {
                            static void replaceSigners(Object data, Object signers) { }
                        }
                        class Lookalike {
                            String documentation = "org.bouncycastle.cms.CMSSignedData.replaceSigners";
                            void call() { CMSSignedData.replaceSigners(null, null); }
                        }
                        """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                java("""
                        import java.io.IOException;
                        import java.io.Reader;
                        import org.bouncycastle.openssl.PEMParser;
                        class Generated {
                            Object parse(Reader reader) throws IOException {
                                return new PEMParser(reader).readObject();
                            }
                        }
                        """, source -> source.path("target/generated/Generated.java")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import java.io.IOException;
                        import java.io.Reader;
                        import org.bouncycastle.openssl.PEMParser;
                        class PemReader {
                            Object parse(Reader reader) throws IOException {
                                return new PEMParser(reader).readObject();
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindBcPkix1811SourceRisks.PEM, 2))));
    }

    private static SourceSpecs markedJava(String source, String message) {
        return java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message)));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
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
