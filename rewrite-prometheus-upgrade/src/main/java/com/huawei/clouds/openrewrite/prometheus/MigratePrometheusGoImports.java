package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Move five package paths relocated without changing their Go package names. */
public final class MigratePrometheusGoImports extends Recipe {
    static final Map<String, String> RELOCATIONS = Map.of(
            PrometheusSupport.MODULE + "/pkg/labels", PrometheusSupport.MODULE + "/model/labels",
            PrometheusSupport.MODULE + "/pkg/textparse", PrometheusSupport.MODULE + "/model/textparse",
            PrometheusSupport.MODULE + "/pkg/relabel", PrometheusSupport.MODULE + "/model/relabel",
            PrometheusSupport.MODULE + "/pkg/timestamp", PrometheusSupport.MODULE + "/model/timestamp",
            PrometheusSupport.MODULE + "/pkg/value", PrometheusSupport.MODULE + "/model/value");
    private static final Pattern DIRECT_IMPORT = Pattern.compile(
            "^(?<prefix>[\\t ]*import[\\t ]+(?:(?:[A-Za-z_][A-Za-z0-9_]*|\\.)[\\t ]+)?)" +
            "(?<quote>[\"`])(?<path>[^\"`]+)\\k<quote>(?<suffix>[\\t ]*(?://.*)?)$");
    private static final Pattern BLOCK_IMPORT = Pattern.compile(
            "^(?<prefix>[\\t ]*(?:(?:[A-Za-z_][A-Za-z0-9_]*|\\.)[\\t ]+)?)" +
            "(?<quote>[\"`])(?<path>[^\"`]+)\\k<quote>(?<suffix>[\\t ]*(?://.*)?)$");
    private static final Pattern IMPORT_BLOCK_START = Pattern.compile("import\\s*\\(\\s*(?://.*)?");
    private static final Pattern IMPORT_BLOCK_END = Pattern.compile("\\)\\s*(?://.*)?");

    @Override public String getDisplayName() { return "Migrate deterministic Prometheus Go package relocations"; }

    @Override
    public String getDescription() {
        return "Move exact Go imports from pkg to model for labels, textparse, relabel, timestamp, and value, " +
               "only inside syntactically recognizable import declarations.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString();
                if (!PrometheusSupport.isProjectPath(visited.getSourcePath()) || !path.endsWith(".go")) return visited;
                String[] lines = visited.getText().split("(?<=\\n)", -1);
                boolean importBlock = false;
                boolean changed = false;
                PrometheusSupport.GoLexicalState lexical = new PrometheusSupport.GoLexicalState();
                StringBuilder output = new StringBuilder(visited.getText().length());
                for (String lineWithEnd : lines) {
                    String end = lineWithEnd.endsWith("\r\n") ? "\r\n" : lineWithEnd.endsWith("\n") ? "\n" : "";
                    String line = end.isEmpty() ? lineWithEnd : lineWithEnd.substring(0, lineWithEnd.length() - end.length());
                    String trimmed = line.trim();
                    boolean ignored = lexical.insideMultilineLiteralOrComment();
                    if (ignored) {
                        lexical.scan(line);
                        output.append(lineWithEnd);
                        continue;
                    }
                    if (!importBlock && IMPORT_BLOCK_START.matcher(trimmed).matches()) {
                        importBlock = true;
                        lexical.scan(line);
                        output.append(lineWithEnd);
                        continue;
                    }
                    if (importBlock && IMPORT_BLOCK_END.matcher(trimmed).matches()) {
                        importBlock = false;
                        lexical.scan(line);
                        output.append(lineWithEnd);
                        continue;
                    }
                    Matcher matcher = (importBlock ? BLOCK_IMPORT : DIRECT_IMPORT).matcher(line);
                    if (matcher.matches()) {
                        String replacement = RELOCATIONS.get(matcher.group("path"));
                        if (replacement != null) {
                            line = matcher.group("prefix") + matcher.group("quote") + replacement +
                                   matcher.group("quote") + matcher.group("suffix");
                            changed = true;
                        }
                    }
                    lexical.scan(line);
                    output.append(line).append(end);
                }
                return changed ? visited.withText(output.toString()) : visited;
            }
        };
    }
}
