package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;
import java.util.Set;

/** Locate compile and behavior boundaries established by the official 1.74 to 1.81.1 source and release history. */
public final class FindBcPkix1811SourceRisks extends Recipe {
    static final String DELTA_DRAFT =
            "The delta-certificate request/descriptor draft changed: the request value type was renamed, " +
            "DeltaCertificateTool flags and its three-argument factory were removed, and the replacement factory " +
            "has different descriptor semantics; verify the selected draft and generated extension bytes";
    static final String PKMAC =
            "setPublicKeyMac now accepts PKMACBuilder directly and PKMACValueGenerator is no longer public; only an " +
            "inline one-use wrapper is auto-unwrapped—refactor shared/subclassed wrappers and verify CRMF MAC vectors";
    static final String BINARY_RETURN =
            "CertificateRepMessage.toASN1Structure has a covariant return descriptor in 1.81.1; source recompilation " +
            "is normally safe, but precompiled consumers, reflection, method handles, and ABI checks must be rebuilt";
    static final String CMS_DIGESTS =
            "CMSSignedData.replaceSigners now preserves original digest AlgorithmIdentifiers rather than re-encoding " +
            "or dropping NULL parameters; compare exact SignedData bytes and verifier interoperability";
    static final String CMS_AUTH_ENVELOPED =
            "CMS AuthEnvelopedData/KEM/recipient processing changed across 1.74-1.81.1, including version calculation, " +
            "OtherKeyAttribute optionality, KEM ciphertext sizing, direct S/MIME support, and ChaCha20-Poly1305; run decrypt/MAC vectors";
    static final String SIGNER_COPY =
            "The SignerInfoGenerator copy constructor now retains the associated certificate holder; verify certificate " +
            "embedding, signer identifiers, signed attributes, chain selection, and exact CMS output";
    static final String COMPOSITE =
            "Composite signer/verifier encodings and draft behavior changed; 1.81.1 additionally requires a generic " +
            "composite verifier to validate at least one component signature—test zero/missing/partial/all signature cases";
    static final String PKCS =
            "PKCS#12/PBMAC1 behavior changed, including PRF preservation and Oracle trusted-certificate attribute " +
            "handling; validate MACs, aliases, attributes, passwords, iterations, exact bytes, and rollback readers";
    static final String PEM =
            "PEM parsing became RFC 7468-aligned and stricter about header placement while whitespace handling changed; " +
            "test malformed, leading-whitespace, encrypted-key, multi-object, strict, and org.bouncycastle.pemreader.lax paths";
    static final String OCSP =
            "OCSP parsing/caching changed for malformed AlgorithmIdentifiers and weak-reference cache entries; exercise " +
            "good/revoked/unknown, malformed, repeated, concurrent, responder-chain, nonce, and offline-cache cases";
    static final String TSP =
            "TSP/ERS behavior changed across this range, including SHA-3 support and evidence-record hash/repetition fixes; " +
            "verify RFC 3161 request/token validation, policy, nonce, certificate binding, digest OIDs, and stored evidence";
    static final String PKIX =
            "PKIX certificate/CRL path behavior changed, including FTP CRL handling and LDAP-store refactoring; verify " +
            "trust anchors, name constraints, policies, delta/indirect CRLs, AIA/CDP fetches, LDAP filters, caching, and offline failure";
    static final String ASN1 =
            "ASN.1/OID/X.500 validation tightened across 1.74-1.81.1 (zero-length/oversized OIDs, empty extensions, " +
            "unescaped '=' RDNs, and malformed tags); test reject/accept decisions plus BER/DL/DER canonical bytes";
    static final String PROVIDER =
            "This PKIX/operator call depends on provider algorithms and Bouncy Castle family compatibility; pin the " +
            "provider, test unavailable/duplicate providers, and compare signature/key encodings and cross-version verification";
    static final String SERIALIZATION =
            "Java serialization of Bouncy Castle PKIX/CMS/provider objects is not a stable cross-version contract; " +
            "replace it with versioned DER/PEM/PKCS/CMS encodings or prove migration and rollback explicitly";
    static final String ENCODING =
            "This PKIX/CMS/PKCS object crosses a persisted or signed encoding boundary; compare exact bytes and ensure " +
            "both upgraded and rollback readers accept the intended standard/draft representation";
    static final String LDAP =
            "The Bouncy Castle LDAP certificate-store implementation was refactored in the 1.81.1 backport; verify URL, " +
            "DN/filter escaping, attribute mappings, referrals, timeouts, empty results, injection resistance, and caching";

    private static final String OLD_DELTA =
            "org.bouncycastle.pkcs.DeltaCertificateRequestAttribute";
    private static final String NEW_DELTA =
            "org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue";
    private static final String PKMAC_GENERATOR =
            "org.bouncycastle.cert.crmf.PKMACValueGenerator";
    private static final Set<String> DELTA_FIELDS =
            Set.of("signature", "issuer", "validity", "subject", "extensions");
    private static final Set<String> AUTH_ENVELOPED_OWNERS = Set.of(
            "org.bouncycastle.cms.CMSAuthEnvelopedData",
            "org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator",
            "org.bouncycastle.cms.CMSAuthEnvelopedDataStreamGenerator",
            "org.bouncycastle.cms.KEMRecipientInformation",
            "org.bouncycastle.cms.jcajce.JceCMSKEMKeyWrapper",
            "org.bouncycastle.cms.jcajce.JceKEMRecipient",
            "org.bouncycastle.cms.jcajce.JceKEMRecipientId",
            "org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient");
    private static final Set<String> PKCS_OWNERS = Set.of(
            "org.bouncycastle.pkcs.PKCS12PfxPdu",
            "org.bouncycastle.pkcs.PKCS12PfxPduBuilder",
            "org.bouncycastle.pkcs.jcajce.JcePKCS12MacCalculatorBuilder",
            "org.bouncycastle.pkcs.jcajce.JcePKCS12MacCalculatorBuilderProvider",
            "org.bouncycastle.pkcs.jcajce.JcePBMac1CalculatorBuilder");
    private static final Set<String> OCSP_OWNERS = Set.of(
            "org.bouncycastle.cert.ocsp.OCSPReqBuilder",
            "org.bouncycastle.cert.ocsp.OCSPResp",
            "org.bouncycastle.cert.ocsp.BasicOCSPResp",
            "org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder",
            "org.bouncycastle.cert.ocsp.CertificateID");
    private static final Set<String> TSP_OWNERS = Set.of(
            "org.bouncycastle.tsp.TimeStampRequestGenerator",
            "org.bouncycastle.tsp.TimeStampResponse",
            "org.bouncycastle.tsp.TimeStampToken",
            "org.bouncycastle.tsp.TimeStampTokenGenerator",
            "org.bouncycastle.tsp.ers.ERSEvidenceRecord",
            "org.bouncycastle.tsp.ers.ERSEvidenceRecordGenerator");
    private static final Set<String> PKIX_OWNERS = Set.of(
            "org.bouncycastle.cert.path.CertPath",
            "org.bouncycastle.cert.path.CertPathValidation",
            "org.bouncycastle.pkix.jcajce.PKIXCertPathReviewer",
            "org.bouncycastle.cert.X509v2CRLBuilder",
            "org.bouncycastle.cert.jcajce.JcaX509CRLConverter",
            "org.bouncycastle.cert.jcajce.JcaX509CertificateConverter");
    private static final Set<String> ENCODING_METHODS =
            Set.of("getEncoded", "toASN1Structure", "toCMSSignedData");
    private static final Set<String> JCA_FACTORIES = Set.of(
            "java.security.Signature", "java.security.cert.CertPathValidator",
            "java.security.cert.CertificateFactory", "java.security.cert.CertStore");

    @Override
    public String getDisplayName() {
        return "Find Bouncy Castle PKIX 1.81.1 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed delta/CRMF APIs and CMS, PKCS, PEM, OCSP, TSP, PKIX/LDAP, ASN.1, operator/provider, " +
               "encoding, and serialization boundaries that require application evidence.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return UpgradeSelectedBcPkixDependency.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String message = typeMessage(visited.getTypeName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = visited.getFieldType();
                if (field != null && "org.bouncycastle.cert.DeltaCertificateTool".equals(fqn(field.getOwner())) &&
                    DELTA_FIELDS.contains(field.getName())) return mark(visited, DELTA_DRAFT);
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = fqn(visited.getType());
                String message = typeMessage(type);
                if (message != null) return mark(visited, message);
                if ("org.bouncycastle.cms.SignerInfoGenerator".equals(type) &&
                    !visited.getArguments().isEmpty() &&
                    TypeUtils.isOfClassType(visited.getArguments().get(0).getType(), type)) {
                    return mark(visited, SIGNER_COPY);
                }
                if (AUTH_ENVELOPED_OWNERS.contains(type)) return mark(visited, CMS_AUTH_ENVELOPED);
                if (PKCS_OWNERS.contains(type)) return mark(visited, PKCS);
                if (OCSP_OWNERS.contains(type)) return mark(visited, OCSP);
                if (TSP_OWNERS.contains(type)) return mark(visited, TSP);
                if (PKIX_OWNERS.contains(type)) return mark(visited, PKIX);
                if ("org.bouncycastle.openssl.PEMParser".equals(type)) return mark(visited, PEM);
                if (type.contains("Composite") && type.startsWith("org.bouncycastle.")) return mark(visited, COMPOSITE);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                String owner = method == null ? "" : fqn(method.getDeclaringType());
                String name = visited.getSimpleName();

                if ("org.bouncycastle.cert.DeltaCertificateTool".equals(owner) &&
                    "makeDeltaCertificateExtension".equals(name) && visited.getArguments().size() == 3 ||
                    "org.bouncycastle.pkcs.DeltaCertAttributeUtils".equals(owner) &&
                    "makeDeltaCertificateExtension".equals(name)) return mark(visited, DELTA_DRAFT);
                if ("org.bouncycastle.cert.crmf.ProofOfPossessionSigningKeyBuilder".equals(owner) &&
                    "setPublicKeyMac".equals(name) && method != null && !method.getParameterTypes().isEmpty() &&
                    TypeUtils.isOfClassType(method.getParameterTypes().get(0), PKMAC_GENERATOR)) {
                    return mark(visited, PKMAC);
                }
                if ("org.bouncycastle.cert.crmf.CertificateRepMessage".equals(owner) &&
                    "toASN1Structure".equals(name)) return mark(visited, BINARY_RETURN);
                if ("org.bouncycastle.cms.CMSSignedData".equals(owner) &&
                    "replaceSigners".equals(name)) return mark(visited, CMS_DIGESTS);
                if (AUTH_ENVELOPED_OWNERS.contains(owner)) return mark(visited, CMS_AUTH_ENVELOPED);
                if (PKCS_OWNERS.contains(owner)) return mark(visited, PKCS);
                if (OCSP_OWNERS.contains(owner)) return mark(visited, OCSP);
                if (TSP_OWNERS.contains(owner)) return mark(visited, TSP);
                if (PKIX_OWNERS.contains(owner)) return mark(visited, PKIX);
                if ("org.bouncycastle.openssl.PEMParser".equals(owner) && "readObject".equals(name)) {
                    return mark(visited, PEM);
                }
                if (isCompositeOperator(owner, name, visited)) return mark(visited, COMPOSITE);
                if (isProviderOperator(owner, name)) return mark(visited, PROVIDER);
                if (isLdapFactory(owner, name, visited)) return mark(visited, LDAP);
                if ("org.bouncycastle.asn1.ASN1Primitive".equals(owner) && "fromByteArray".equals(name) ||
                    owner.startsWith("org.bouncycastle.asn1.") && ENCODING_METHODS.contains(name)) {
                    return mark(visited, ASN1);
                }
                if (owner.startsWith("org.bouncycastle.") && ENCODING_METHODS.contains(name)) {
                    return mark(visited, ENCODING);
                }
                if ("java.io.ObjectOutputStream".equals(owner) && "writeObject".equals(name) &&
                    !visited.getArguments().isEmpty() && isBouncyCastle(visited.getArguments().get(0).getType())) {
                    return mark(visited, SERIALIZATION);
                }
                if ("getInstance".equals(name) && JCA_FACTORIES.contains(owner) &&
                    (hasBcProvider(visited) || firstString(visited).map(FindBcPkix1811SourceRisks::pkixAlgorithm).orElse(false))) {
                    return mark(visited, PROVIDER);
                }
                return visited;
            }
        };
    }

    private static String typeMessage(String type) {
        if (OLD_DELTA.equals(type) || type.startsWith(OLD_DELTA + "$") ||
            NEW_DELTA.equals(type) || type.startsWith(NEW_DELTA + "$")) return DELTA_DRAFT;
        if (PKMAC_GENERATOR.equals(type) || type.startsWith(PKMAC_GENERATOR + "$")) return PKMAC;
        if (type.startsWith("org.bouncycastle.jce.X509LDAP") ||
            type.startsWith("org.bouncycastle.x509.util.LDAP")) return LDAP;
        return null;
    }

    private static boolean isCompositeOperator(String owner, String name, J.MethodInvocation invocation) {
        if (!Set.of("org.bouncycastle.operator.jcajce.JcaContentSignerBuilder",
                    "org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder").contains(owner) ||
            !"build".equals(name)) return false;
        return invocation.getArguments().stream().anyMatch(argument ->
                fqn(argument.getType()).contains("Composite") ||
                argument instanceof J.Literal literal && literal.getValue() instanceof String value &&
                value.toUpperCase(Locale.ROOT).contains("COMPOSITE"));
    }

    private static boolean isProviderOperator(String owner, String name) {
        return owner.startsWith("org.bouncycastle.operator.") &&
               Set.of("build", "setProvider", "get", "verify").contains(name);
    }

    private static boolean isLdapFactory(String owner, String name, J.MethodInvocation invocation) {
        return "java.security.cert.CertStore".equals(owner) && "getInstance".equals(name) &&
               firstString(invocation).map("LDAP"::equalsIgnoreCase).orElse(false);
    }

    private static boolean hasBcProvider(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().skip(1).anyMatch(argument ->
                argument instanceof J.Literal literal && "BC".equals(literal.getValue()) ||
                "org.bouncycastle.jce.provider.BouncyCastleProvider".equals(fqn(argument.getType())));
    }

    private static java.util.Optional<String> firstString(J.MethodInvocation invocation) {
        if (invocation.getArguments().isEmpty() ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return java.util.Optional.empty();
        return java.util.Optional.of(value);
    }

    private static boolean pkixAlgorithm(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("PKIX") || normalized.contains("X.509") ||
               normalized.contains("CMS") || normalized.contains("LDAP");
    }

    private static boolean isBouncyCastle(JavaType type) {
        return fqn(type).startsWith("org.bouncycastle.");
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
