package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only exact require directives in maintained go.mod files. */
public final class UpgradeSelectedPrometheusDependency extends Recipe {
    private static final Pattern DIRECT_REQUIRE = Pattern.compile(
            "^(?<prefix>[\\t ]*require[\\t ]+)" +
            Pattern.quote(PrometheusSupport.MODULE) +
            "(?<space>[\\t ]+)(?<version>[^\\s/]+)(?<suffix>[\\t ]*(?://[^\\r\\n]*)?)$");
    private static final Pattern BLOCK_REQUIRE = Pattern.compile(
            "^(?<prefix>[\\t ]*)" + Pattern.quote(PrometheusSupport.MODULE) +
            "(?<space>[\\t ]+)(?<version>[^\\s/]+)(?<suffix>[\\t ]*(?://[^\\r\\n]*)?)$");
    private static final Pattern BLOCK_START = Pattern.compile("^(require|replace|exclude)\\s*\\(\\s*(?://.*)?$");
    private static final Pattern BLOCK_END = Pattern.compile("^\\)\\s*(?://.*)?$");
    private static final Pattern NEVER = Pattern.compile("a^");

    @Override public String getDisplayName() { return "Upgrade selected Prometheus Go module requirements"; }

    @Override
    public String getDescription() {
        return "Upgrade only exact workbook versions of github.com/prometheus/prometheus in direct or block " +
               "go.mod require directives, preserving whitespace and indirect comments.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!PrometheusSupport.isProjectPath(visited.getSourcePath()) ||
                    !PrometheusSupport.fileName(visited.getSourcePath(), "go.mod")) return visited;
                String source = visited.getText();
                StringBuilder migrated = new StringBuilder(source.length());
                String block = "";
                boolean changed = false;
                for (String withEnd : source.split("(?<=\\n)", -1)) {
                    String end = withEnd.endsWith("\r\n") ? "\r\n" : withEnd.endsWith("\n") ? "\n" : "";
                    String line = end.isEmpty() ? withEnd : withEnd.substring(0, withEnd.length() - end.length());
                    String trimmed = line.trim();
                    Matcher start = BLOCK_START.matcher(trimmed);
                    if (block.isEmpty() && start.matches()) {
                        block = start.group(1);
                    } else if (!block.isEmpty() && BLOCK_END.matcher(trimmed).matches()) {
                        block = "";
                    } else {
                        Matcher requirement = ("require".equals(block) ? BLOCK_REQUIRE :
                                block.isEmpty() ? DIRECT_REQUIRE : NEVER).matcher(line);
                        if (requirement.matches() && PrometheusSupport.SOURCE_VERSIONS.contains(requirement.group("version"))) {
                            line = requirement.group("prefix") + PrometheusSupport.MODULE + requirement.group("space") +
                                   PrometheusSupport.TARGET + requirement.group("suffix");
                            changed = true;
                        }
                    }
                    migrated.append(line).append(end);
                }
                if (!changed) return visited;
                return visited.withText(migrated.toString());
            }
        };
    }
}
