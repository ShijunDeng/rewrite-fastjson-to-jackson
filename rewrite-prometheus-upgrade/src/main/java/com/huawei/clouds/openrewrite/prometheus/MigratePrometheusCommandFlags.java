package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rename standalone flags in executable deployment text, not prose or arbitrary source strings. */
public final class MigratePrometheusCommandFlags extends Recipe {
    private static final Map<String, String> FLAGS = Map.of(
            "--enable-feature=agent", "--agent",
            "--enable-feature=remote-write-receiver", "--web.enable-remote-write-receiver",
            "--enable-feature=otlp-write-receiver", "--web.enable-otlp-receiver");

    @Override public String getDisplayName() { return "Migrate deterministic Prometheus command flags"; }

    @Override
    public String getDescription() {
        return "Rename standalone removed flags only in Dockerfiles, shell scripts, systemd units, and templates.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!PrometheusSupport.isProjectPath(visited.getSourcePath()) || !operational(visited)) return visited;
                String source = visited.getText();
                StringBuilder migrated = new StringBuilder(source.length());
                String file = PrometheusSupport.fileName(visited.getSourcePath(), "Dockerfile") ? "dockerfile" :
                        visited.getSourcePath().getFileName() == null ? "" :
                                visited.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                boolean prometheusDocker = Pattern.compile("(?im)^\\s*FROM(?:\\s+--platform=\\S+)?\\s+prom/prometheus:")
                        .matcher(source).find() || Pattern.compile("(?im)^\\s*ENTRYPOINT.*(?:^|[/\"'])prometheus(?:[\\s\"']|$)")
                        .matcher(source).find();
                boolean continuation = false;
                for (String withEnd : source.split("(?<=\\n)", -1)) {
                    String end = withEnd.endsWith("\r\n") ? "\r\n" : withEnd.endsWith("\n") ? "\n" : "";
                    String line = end.isEmpty() ? withEnd : withEnd.substring(0, withEnd.length() - end.length());
                    String trimmed = line.stripLeading();
                    boolean owned = continuation || ownsCommandLine(file, path, trimmed, prometheusDocker);
                    if (owned && !trimmed.startsWith("#")) line = migrateFlags(line);
                    continuation = owned && line.stripTrailing().endsWith("\\");
                    migrated.append(line).append(end);
                }
                String result = migrated.toString();
                return result.equals(source) ? visited : visited.withText(result);
            }
        };
    }

    private static String migrateFlags(String source) {
        String migrated = source;
                for (Map.Entry<String, String> flag : FLAGS.entrySet()) {
                    migrated = migrated.replaceAll("(?<![A-Za-z0-9_,=-])" + Pattern.quote(flag.getKey()) +
                            "(?![A-Za-z0-9_,=-])", Matcher.quoteReplacement(flag.getValue()));
                }
                migrated = migrated.replaceAll("(?<![A-Za-z0-9_.-])--storage\\.tsdb\\.retention=",
                        "--storage.tsdb.retention.time=");
                migrated = migrated.replaceAll("(?<![A-Za-z0-9_.-])-query\\.staleness-delta=",
                        "--query.lookback-delta=");
        return migrated;
    }

    private static boolean ownsCommandLine(String file, String path, String trimmed, boolean prometheusDocker) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (file.equals("dockerfile") || file.startsWith("dockerfile.")) {
            if (lower.startsWith("cmd ")) return prometheusDocker;
            if (lower.startsWith("entrypoint ")) return prometheusDocker || lower.contains("prometheus");
            return lower.startsWith("run ") && prometheusInvocation(lower.substring(4).stripLeading());
        }
        if (path.endsWith(".service")) {
            return lower.matches("exec(?:start|reload|stop)=.*prometheus(?:[\\s\"']|$).*");
        }
        if (path.endsWith(".sh")) {
            return prometheusInvocation(lower) || lower.matches("prometheus_[a-z0-9_]*=.*");
        }
        return prometheusInvocation(lower) || lower.matches("-\\s+--?(?:enable-feature|storage\\.tsdb|query\\.).*");
    }

    private static boolean prometheusInvocation(String line) {
        return Pattern.compile("^(?:(?:exec|command|nohup)\\s+)?(?:[^\\s\"']*/)?prometheus(?:[\\s\"']|$)")
                .matcher(line).find();
    }

    private static boolean operational(PlainText text) {
        String path = text.getSourcePath().toString().toLowerCase();
        String file = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase();
        return file.equals("dockerfile") || file.startsWith("dockerfile.") || path.endsWith(".sh") ||
               path.endsWith(".service") || path.endsWith(".tpl") || path.endsWith(".tmpl") ||
               path.endsWith(".yaml.gotmpl") || path.endsWith(".yml.gotmpl");
    }
}
