package com.huawei.clouds.openrewrite.jsch;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.openrewrite.java.Assertions.java;

class JschJavaRecipesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJsch227JavaRisks()).parser(jschParser());
    }

    @Test
    void canonicalizesOnlyAttributedJschConfigurationKeys() {
        rewriteRun(
                spec -> spec.recipe(new CanonicalizeJschConfigKeys()).parser(jschParser()),
                java(
                        """
                        import com.jcraft.jsch.JSch;
                        import com.jcraft.jsch.Session;
                        class SshAlgorithms {
                            void configure(Session session) {
                                JSch.setConfig("PubkeyAcceptedKeyTypes", "rsa-sha2-512");
                                session.setConfig("PubkeyAcceptedKeyTypes", "rsa-sha2-512");
                                String value = session.getConfig("PubkeyAcceptedKeyTypes");
                            }
                        }
                        """,
                        """
                        import com.jcraft.jsch.JSch;
                        import com.jcraft.jsch.Session;
                        class SshAlgorithms {
                            void configure(Session session) {
                                JSch.setConfig("PubkeyAcceptedAlgorithms", "rsa-sha2-512");
                                session.setConfig("PubkeyAcceptedAlgorithms", "rsa-sha2-512");
                                String value = session.getConfig("PubkeyAcceptedAlgorithms");
                            }
                        }
                        """),
                java("""
                        class OtherConfig {
                            void setConfig(String key, String value) {}
                            void configure() {
                                setConfig("PubkeyAcceptedKeyTypes", "not-jsch");
                                String text = "PubkeyAcceptedKeyTypes";
                            }
                        }
                        """));
    }

    @Test
    void canonicalizationIsIdempotentAndLeavesVariableKeys() {
        rewriteRun(
                spec -> spec.recipe(new CanonicalizeJschConfigKeys()).parser(jschParser())
                        .cycles(2).expectedCyclesThatMakeChanges(0),
                java("""
                        import com.jcraft.jsch.Session;
                        class Config {
                            void configure(Session session, String key) {
                                session.setConfig("PubkeyAcceptedAlgorithms", "rsa-sha2-512");
                                session.setConfig(key, "ssh-rsa");
                            }
                        }
                        """));
    }

    @Test
    void canonicalizerAndFinderSkipGeneratedParentsButProcessInstallJavaLeaf() {
        rewriteRun(
                spec -> spec.recipe(new CanonicalizeJschConfigKeys()).parser(jschParser()),
                java("""
                        import com.jcraft.jsch.Session;
                        class GeneratedConfig { void use(Session s) { s.setConfig("PubkeyAcceptedKeyTypes", "ssh-rsa"); } }
                        """, source -> source.path("GeneratedSources/GeneratedConfig.java")),
                java("""
                        import com.jcraft.jsch.Session;
                        class Install { void use(Session s) { s.setConfig("PubkeyAcceptedKeyTypes", "rsa-sha2-512"); } }
                        """, """
                        import com.jcraft.jsch.Session;
                        class Install { void use(Session s) { s.setConfig("PubkeyAcceptedAlgorithms", "rsa-sha2-512"); } }
                        """, source -> source.path("src/install.java")));

        rewriteRun(
                spec -> spec.recipe(new FindJsch227JavaRisks()).parser(jschParser()),
                java("""
                        import com.jcraft.jsch.Session;
                        class GeneratedRisk { void use(Session s) throws Exception { s.connect(); } }
                        """, source -> source.path("INSTALL-cache/GeneratedRisk.java").afterRecipe(after ->
                        assertFalse(after.printAll().contains("SSH connection boundary"), after.printAll()))),
                java("""
                        import com.jcraft.jsch.Session;
                        class InstallRisk { void use(Session s) throws Exception { s.connect(); } }
                        """, source -> source.path("src/install.java").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "SSH connection boundary"))));
    }

    @Test
    void marksRealZstackLegacyRsaConfiguration() {
        // Reduced from zstackio/zstack at 5a29293bd53c98a38488fb8b71d54531012d3dae:
        // https://github.com/zstackio/zstack/blob/5a29293bd53c98a38488fb8b71d54531012d3dae/utils/src/main/java/org/zstack/utils/ssh/Ssh.java
        rewriteRun(java(
                """
                import com.jcraft.jsch.Session;
                class Ssh {
                    void configure(Session session) {
                        session.setConfig("server_host_key", session.getConfig("server_host_key") + ",ssh-rsa,ssh-dss");
                        session.setConfig("PubkeyAcceptedKeyTypes", session.getConfig("PubkeyAcceptedKeyTypes") + ",ssh-rsa,ssh-dss");
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "changed secure algorithm defaults and ordering"))));
    }

    @Test
    void marksHostKeyTrustBoundariesAndRejectsSameNamedApis() {
        rewriteRun(
                java("""
                        import com.jcraft.jsch.JSch;
                        import com.jcraft.jsch.HostKeyRepository;
                        import com.jcraft.jsch.Session;
                        class Trust {
                            void configure(JSch jsch, Session session, HostKeyRepository repository) throws Exception {
                                jsch.setKnownHosts("/etc/ssh/ssh_known_hosts");
                                jsch.setHostKeyRepository(repository);
                                session.setConfig("StrictHostKeyChecking", "no");
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Host-key trust boundary");
                            assertContains(after.printAll(), "StrictHostKeyChecking");
                        })),
                java("""
                        class OtherSession {
                            void setConfig(String key, String value) {}
                            void connect() {}
                        }
                        class OtherUsage {
                            void use(OtherSession session) {
                                session.setConfig("StrictHostKeyChecking", "no");
                                session.connect();
                            }
                        }
                        """));
    }

    @Test
    void marksIdentityAndSessionOperationalBoundaries() {
        rewriteRun(java("""
                import com.jcraft.jsch.*;
                class Connector {
                    void connect(JSch jsch, Session session, IdentityRepository identities,
                                 Proxy proxy, SocketFactory sockets, ConfigRepository config) throws Exception {
                        jsch.addIdentity("id_ed25519", "secret");
                        jsch.setIdentityRepository(identities);
                        session.setProxy(proxy);
                        session.setSocketFactory(sockets);
                        session.setConfigRepository(config);
                        session.connect(10000);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "Identity/key loading boundary");
                    assertContains(after.printAll(), "SSH connection boundary");
                })));
    }

    @Test
    void marksTargetDeprecatedOverloadsButNotTypedReplacements() {
        rewriteRun(java("""
                import com.jcraft.jsch.*;
                import java.nio.charset.StandardCharsets;
                import java.util.Hashtable;
                class DeprecatedCalls {
                    void use(JSch jsch, ChannelSession channel, ChannelSftp sftp, Identity identity, KeyPair key)
                            throws Exception {
                        jsch.removeIdentity("legacy-name");
                        channel.setEnv(new Hashtable<byte[], byte[]>());
                        sftp.get("remote", ChannelSftp.RESUME);
                        sftp.get("remote", null, ChannelSftp.OVERWRITE);
                        sftp.setFilenameEncoding("UTF-8");
                        identity.decrypt();
                        key.setPassphrase("secret");

                        jsch.removeIdentity(identity);
                        channel.setEnv("LANG", "C");
                        sftp.get("remote", null, 0L);
                        sftp.setFilenameEncoding(StandardCharsets.UTF_8);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, "deprecated or unsupported in 2.27.7");
                    assertTrue(count(printed, "deprecated or unsupported in 2.27.7") == 7,
                            () -> "Expected seven exact deprecated calls:\n" + printed);
                })));
    }

    @Test
    void marksFingerprintFormattingBoundary() {
        // Usage shape reduced from mendhak/gpslogger at 32994bfc7c03d406aadbf038d0f4813849d0b250:
        // https://github.com/mendhak/gpslogger/blob/32994bfc7c03d406aadbf038d0f4813849d0b250/gpslogger/src/main/java/com/mendhak/gpslogger/senders/sftp/SFTPWorker.java
        rewriteRun(java("""
                import com.jcraft.jsch.JSch;
                import com.jcraft.jsch.Session;
                class Fingerprints {
                    String display(Session session, JSch jsch) { return session.getHostKey().getFingerPrint(jsch); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), "fingerprint formatting/default hash changed"))));
    }

    @Test
    void marksCustomExtensionImplementationsNotLibraryInterfaces() {
        rewriteRun(java("""
                import com.jcraft.jsch.*;
                class CompanyIdentity implements Identity {
                    public boolean decrypt() { return true; }
                }
                class CompanyProxy implements Proxy {}
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, "Custom JSch extension point");
                    assertTrue(count(printed, "Custom JSch extension point") == 2,
                            () -> "Expected two extension declarations:\n" + printed);
                })));
    }

    @Test
    void markerRecipeIsStableAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import com.jcraft.jsch.Session;
                        class Stable { void connect(Session session) throws Exception { session.connect(); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "SSH connection boundary"))));
    }

    private static JavaParser.Builder<?, ?> jschParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package com.jcraft.jsch;
                public class JSch {
                    public static void setConfig(String key, String value) {}
                    public static String getConfig(String key) { return null; }
                    public void setKnownHosts(String path) throws Exception {}
                    public void setHostKeyRepository(HostKeyRepository repository) {}
                    public void addIdentity(String path, String passphrase) throws Exception {}
                    public void setIdentityRepository(IdentityRepository repository) {}
                    public void removeIdentity(String name) throws Exception {}
                    public void removeIdentity(Identity identity) throws Exception {}
                }
                """,
                """
                package com.jcraft.jsch;
                public class Session {
                    public void setConfig(String key, String value) {}
                    public String getConfig(String key) { return null; }
                    public void setProxy(Proxy proxy) {}
                    public void setSocketFactory(SocketFactory factory) {}
                    public void setConfigRepository(ConfigRepository repository) {}
                    public void connect() throws Exception {}
                    public void connect(int timeout) throws Exception {}
                    public HostKey getHostKey() { return null; }
                }
                """,
                "package com.jcraft.jsch; public interface Identity { default boolean decrypt() { return true; } }",
                "package com.jcraft.jsch; public interface IdentityRepository {}",
                "package com.jcraft.jsch; public interface HostKeyRepository {}",
                "package com.jcraft.jsch; public interface ConfigRepository {}",
                "package com.jcraft.jsch; public interface Proxy {}",
                "package com.jcraft.jsch; public interface SocketFactory {}",
                "package com.jcraft.jsch; public interface Logger {}",
                """
                package com.jcraft.jsch;
                public class HostKey {
                    public String getFingerPrint(JSch jsch) { return null; }
                    public String getFingerprint(String hash) { return null; }
                }
                """,
                """
                package com.jcraft.jsch;
                import java.util.Hashtable;
                public class ChannelSession {
                    public void setEnv(Hashtable<byte[], byte[]> env) {}
                    public void setEnv(String name, String value) {}
                }
                """,
                "package com.jcraft.jsch; public interface SftpProgressMonitor {}",
                """
                package com.jcraft.jsch;
                import java.io.InputStream;
                import java.nio.charset.Charset;
                public class ChannelSftp {
                    public static final int RESUME = 1, OVERWRITE = 0;
                    public InputStream get(String path, int mode) { return null; }
                    public InputStream get(String path, SftpProgressMonitor monitor, int mode) { return null; }
                    public InputStream get(String path, SftpProgressMonitor monitor, long skip) { return null; }
                    public void setFilenameEncoding(String encoding) {}
                    public void setFilenameEncoding(Charset encoding) {}
                }
                """,
                """
                package com.jcraft.jsch;
                public class KeyPair {
                    public void setPassphrase(String passphrase) {}
                    public void setPassphrase(byte[] passphrase) {}
                    public void writePrivateKey(String path, byte[] passphrase) {}
                }
                """);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
