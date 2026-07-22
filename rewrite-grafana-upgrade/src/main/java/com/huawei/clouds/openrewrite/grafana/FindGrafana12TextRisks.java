package com.huawei.clouds.openrewrite.grafana;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact INI, environment, Dockerfile, shell, and Terraform migration risks line by line. */
public final class FindGrafana12TextRisks extends Recipe {
    static final String API_KEY_MESSAGE =
            "Grafana API keys are migrated to service accounts; replace /api/auth/keys automation with the service-account API and plan token ownership, rotation, RBAC scope, and rollback";
    static final String DATABASE_MESSAGE =
            "Grafana database/encryption ownership detected; take a tested backup, preserve secret_key/KMS material, review migrations and HA rolling-upgrade compatibility, and verify rollback before 12.1.1";

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^\\s*(?:export\\s+)?([A-Za-z0-9_.-]+)\\s*=\\s*([^#;\\r\\n]*).*$");
    private static final Pattern DOCKER_ENV_ASSIGNMENT = Pattern.compile(
            "^(\\s*ENV\\s+)([A-Za-z0-9_]+)(\\s*=\\s*|\\s+)(\"[^\"]*\"|'[^']*'|[^\\s#]+)" +
            "([ \\t]*(?:#.*)?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCKER_IMAGE = Pattern.compile(
            "(?i)^\\s*FROM\\s+(?:--platform=\\S+\\s+)?(\\S+).*$");

    @Override
    public String getDisplayName() {
        return "Find Grafana 12 text configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact Grafana INI/environment/Dockerfile/API lines with messages for alerting, plugins, authentication, encryption, database, and image ownership.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof PlainText text) || !GrafanaMigrationSupport.isProjectPath(text.getSourcePath()) ||
                    !looksRelevant(text)) return tree;
                return GrafanaMigrationSupport.markLines(text, FindGrafana12TextRisks::message);
            }
        };
    }

    private static String message(String section, String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.isEmpty()) return null;
        Matcher image = DOCKER_IMAGE.matcher(trimmed);
        if (image.matches() && GrafanaMigrationSupport.isGrafanaImage(image.group(1)) &&
            !GrafanaMigrationSupport.isTargetImage(image.group(1))) return FindGrafana12YamlRisks.IMAGE_MESSAGE;
        if (trimmed.contains("/api/auth/keys")) return API_KEY_MESSAGE;
        if (trimmed.matches(".*\\bgrafana(?:-| )cli\\b.*\\bplugins\\s+install\\b.*")) {
            return FindGrafana12YamlRisks.PLUGIN_MESSAGE;
        }
        // PlainText snippets deliberately retain their line terminator so markers print on the
        // exact source line. Match the logical line; Java's dot does not consume that terminator.
        String logicalLine = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        if (logicalLine.endsWith("\r")) logicalLine = logicalLine.substring(0, logicalLine.length() - 1);
        String key;
        String value;
        Matcher dockerEnv = DOCKER_ENV_ASSIGNMENT.matcher(logicalLine);
        if (dockerEnv.matches()) {
            key = dockerEnv.group(2);
            value = unquote(dockerEnv.group(4).trim());
        } else if (trimmed.regionMatches(true, 0, "ENV ", 0, 4)) {
            if (containsDockerEnvOwner(logicalLine, "GF_INSTALL_PLUGINS") ||
                containsDockerEnvOwner(logicalLine, "GF_PLUGINS_INSTALL") ||
                containsDockerEnvOwner(logicalLine, "GF_INSTALL_PLUGINS_FORCE") ||
                containsDockerEnvOwner(logicalLine, "GF_PLUGINS_ALLOW_LOADING_UNSIGNED_PLUGINS")) {
                return FindGrafana12YamlRisks.PLUGIN_MESSAGE;
            }
            if (containsDockerEnvOwner(logicalLine, "GF_FEATURE_TOGGLES_ENABLE") &&
                GrafanaMigrationSupport.containsDeprecatedToggle(logicalLine)) {
                return FindGrafana12YamlRisks.TOGGLE_MESSAGE;
            }
            return null;
        } else {
            Matcher assignment = ASSIGNMENT.matcher(logicalLine);
            if (!assignment.matches()) return null;
            key = assignment.group(1);
            value = unquote(assignment.group(2).trim());
        }
        if ("GF_ALERTING_ENABLED".equals(key) && truthy(value) ||
            "GF_UNIFIED_ALERTING_ENABLED".equals(key) && falsey(value) ||
            "alerting".equalsIgnoreCase(section) && "enabled".equals(key) && truthy(value) ||
            "unified_alerting".equalsIgnoreCase(section) && "enabled".equals(key) && falsey(value)) {
            return FindGrafana12YamlRisks.ALERTING_MESSAGE;
        }
        if ("GF_PLUGINS_ANGULAR_SUPPORT_ENABLED".equals(key) && truthy(value) ||
            "plugins".equalsIgnoreCase(section) && "angular_support_enabled".equals(key) && truthy(value)) {
            return FindGrafana12YamlRisks.ANGULAR_MESSAGE;
        }
        if ("GF_INSTALL_PLUGINS_FORCE".equals(key) &&
            GrafanaMigrationSupport.pluginForceBlocksMigration(value)) return FindGrafana12YamlRisks.PLUGIN_MESSAGE;
        if (("GF_INSTALL_PLUGINS".equals(key) || "GF_PLUGINS_INSTALL".equals(key) ||
             "GF_PLUGINS_ALLOW_LOADING_UNSIGNED_PLUGINS".equals(key)) && !value.isEmpty() ||
            "plugins".equalsIgnoreCase(section) && ("install".equals(key) ||
                    "allow_loading_unsigned_plugins".equals(key)) && !value.isEmpty()) {
            return FindGrafana12YamlRisks.PLUGIN_MESSAGE;
        }
        if ("GF_USERS_EDITORS_CAN_ADMIN".equals(key) && truthy(value) ||
            "users".equalsIgnoreCase(section) && "editors_can_admin".equals(key) && truthy(value)) {
            return FindGrafana12YamlRisks.EDITOR_MESSAGE;
        }
        if ("GF_AUTH_OAUTH_ALLOW_INSECURE_EMAIL_LOOKUP".equals(key) && truthy(value) ||
            "auth".equalsIgnoreCase(section) && "oauth_allow_insecure_email_lookup".equals(key) && truthy(value)) {
            return FindGrafana12YamlRisks.OAUTH_MESSAGE;
        }
        if ("GF_AUTH_ANONYMOUS_ENABLED".equals(key) && truthy(value) ||
            "auth.anonymous".equalsIgnoreCase(section) && "enabled".equals(key) && truthy(value)) {
            return FindGrafana12YamlRisks.ANONYMOUS_MESSAGE;
        }
        if ("GF_FEATURE_TOGGLES_ENABLE".equals(key) && value.contains("disableEnvelopeEncryption") ||
            "feature_toggles".equalsIgnoreCase(section) && "enable".equals(key) &&
                    value.contains("disableEnvelopeEncryption")) {
            return FindGrafana12YamlRisks.ENCRYPTION_MESSAGE;
        }
        if (("GF_FEATURE_TOGGLES_ENABLE".equals(key) ||
             "feature_toggles".equalsIgnoreCase(section) && "enable".equals(key)) &&
            GrafanaMigrationSupport.containsDeprecatedToggle(value)) {
            return FindGrafana12YamlRisks.TOGGLE_MESSAGE;
        }
        if ("GF_SECURITY_SECRET_KEY".equals(key) || "GF_SECURITY_SECRET_KEY__FILE".equals(key) ||
            "security".equalsIgnoreCase(section) && "secret_key".equals(key)) {
            return DATABASE_MESSAGE;
        }
        if ("database".equalsIgnoreCase(section) && ("type".equals(key) || "url".equals(key) ||
            "host".equals(key) || "path".equals(key))) return DATABASE_MESSAGE;
        return null;
    }

    private static boolean containsDockerEnvOwner(String line, String key) {
        return Pattern.compile("(?:^|\\s)" + Pattern.quote(key) + "(?:\\s*=|\\s)")
                .matcher(line).find();
    }

    private static boolean looksRelevant(PlainText text) {
        String path = text.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        String content = text.printAll();
        return path.contains("grafana") || content.contains("grafana/grafana") || content.contains("GF_") ||
               content.contains("grafana cli") || content.contains("grafana-cli") ||
               content.contains("[unified_alerting]") || name.equals("grafana.ini") || name.equals("custom.ini");
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }

    private static boolean truthy(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static boolean falsey(String value) {
        return "false".equalsIgnoreCase(value) || "0".equals(value);
    }
}
