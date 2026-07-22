package com.huawei.clouds.openrewrite.jsch;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.Set;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Marks exact security- and timeout-sensitive JSch/OpenSSH configuration entries. */
public final class FindJsch227ConfigurationRisks extends Recipe {
    private static final Set<String> ALGORITHM_KEYS = Set.of(
            "kex", "server_host_key", "PubkeyAcceptedAlgorithms", "PubkeyAcceptedKeyTypes", "cipher.s2c",
            "cipher.c2s", "mac.s2c", "mac.c2s", "compression.s2c", "compression.c2s", "CheckCiphers",
            "CheckKexes", "CheckSignatures", "FingerprintHash", "HostKeyAlgorithms");
    private static final Set<String> TIME_KEYS = Set.of(
            "ConnectTimeout", "ServerAliveInterval", "connect-timeout", "server-alive-interval");
    private static final Pattern LEGACY_ALGORITHM = Pattern.compile(
            "(?i)(^|[,\\s])(ssh-rsa|ssh-dss|diffie-hellman-group1-sha1|diffie-hellman-group14-sha1|" +
            "3des-cbc|blowfish-cbc|aes(?:128|192|256)-cbc|hmac-sha1(?:-96)?)(?=$|[,\\s])");
    private static final String ALGORITHM_MESSAGE =
            "Legacy/explicit SSH algorithm policy: JSch 2.27.7 has modern defaults and strict-KEX changes; prefer server upgrades and reviewed algorithms instead of blindly restoring SHA-1, DSA, weak KEX, CBC, or weak MACs";
    private static final String TRUST_MESSAGE =
            "StrictHostKeyChecking disables or weakens host authentication here; migrate with a managed known_hosts/HostKeyRepository and test host-key rotation rather than accepting all hosts";
    private static final String TIME_MESSAGE =
            "OpenSSH-style ConnectTimeout/ServerAliveInterval interpretation changed to seconds on this upgrade path; verify units, overflow, retry timing, keepalive behavior, and operational timeouts";

    @Override
    public String getDisplayName() {
        return "Find JSch 2.27.7 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact parsed properties, YAML, and non-POM XML entries that explicitly enable legacy SSH " +
               "algorithms, weaken host-key checking, or depend on changed OpenSSH timeout units.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || UpgradeSelectedJschDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                            Properties.Entry e = super.visitEntry(entry, executionContext);
                            String message = message(e.getKey(), e.getValue().getText().trim(),
                                    explicitOwnerKey(e.getKey()) || ownedPath(source.getSourcePath()));
                            return message == null ? e : mark(e, message);
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry,
                                                                     ExecutionContext executionContext) {
                            Yaml.Mapping.Entry e = super.visitMappingEntry(entry, executionContext);
                            String value = e.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue().trim() : "";
                            String message = message(e.getKey().getValue(), value,
                                    explicitOwnerKey(e.getKey().getValue()) || ownedPath(source.getSourcePath()) ||
                                    yamlOwner(getCursor()));
                            return message == null ? e : mark(e, message);
                        }
                    }.visitNonNull(yaml, ctx);
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            String message = t.getValue().map(value -> message(t.getName(), value.trim(),
                                    explicitOwnerKey(t.getName()) || ownedPath(source.getSourcePath()) ||
                                    xmlOwner(getCursor()))).orElse(null);
                            return message == null ? t : mark(t, message);
                        }
                    }.visitNonNull(xml, ctx);
                }
                return tree;
            }
        };
    }

    private static String message(String key, String value, boolean owned) {
        if (!owned) return null;
        if (matchesKey(key, "StrictHostKeyChecking") && Set.of("no", "ask", "accept-new")
                .contains(value.toLowerCase(Locale.ROOT))) return TRUST_MESSAGE;
        if (TIME_KEYS.stream().anyMatch(candidate -> matchesKey(key, candidate)) && value.matches("[0-9]+")) {
            return TIME_MESSAGE;
        }
        if (ALGORITHM_KEYS.stream().anyMatch(candidate -> matchesKey(key, candidate)) &&
            LEGACY_ALGORITHM.matcher(value).find()) return ALGORITHM_MESSAGE;
        return null;
    }

    private static boolean explicitOwnerKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return Pattern.compile("(?:^|[._-])(?:jsch|ssh|sftp)(?:[._-]|$)").matcher(normalized).find();
    }

    private static boolean ownedPath(Path path) {
        for (Path part : path.normalize()) {
            String[] tokens = part.toString().toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
            for (String token : tokens) if (Set.of("jsch", "ssh", "sftp").contains(token)) return true;
        }
        return false;
    }

    private static boolean yamlOwner(Cursor cursor) {
        return cursor.getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                .map(Yaml.Mapping.Entry.class::cast).map(entry -> entry.getKey().getValue())
                .anyMatch(FindJsch227ConfigurationRisks::ownerName);
    }

    private static boolean xmlOwner(Cursor cursor) {
        return cursor.getPathAsStream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .map(Xml.Tag::getName).anyMatch(FindJsch227ConfigurationRisks::ownerName);
    }

    private static boolean ownerName(String value) {
        return Set.of("jsch", "ssh", "sftp").contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesKey(String actual, String candidate) {
        return candidate.equals(actual) || actual.endsWith("." + candidate);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
