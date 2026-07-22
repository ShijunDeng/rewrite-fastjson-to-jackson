package com.huawei.clouds.openrewrite.prometheus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark Go, module, command, rules, storage, and image boundaries requiring an owner decision. */
public final class FindPrometheusTextMigrationRisks extends Recipe {
    private static final String GO_BASELINE =
            "Prometheus module v0.311.3 declares Go 1.25.0; upgrade the owning Go directive/toolchain, CI images, linters, generators, CGO and cross-build matrix before accepting this module graph";
    private static final String INTERNAL_API =
            "The Prometheus server repository does not guarantee stability for its Go packages; recompile this import against v0.311.3 and verify changed interfaces, labels/histograms, parser, storage, remote read/write, concurrency, ownership and serialization";
    private static final String MODULE_OWNER =
            "This Prometheus requirement is not the exact workbook target v0.311.3; update the real version owner deliberately instead of widening the automatic source whitelist";
    private static final String REPLACE_OWNER =
            "A Go replace/exclude directive changes the selected Prometheus source or version; migrate that owner explicitly and verify checksums, fork patches, module identity, vendoring and the final module graph";
    private static final String COMPANION =
            "An explicit Prometheus companion module can skew the server library graph; compare it with v0.311.3 go.mod and verify MVS selection for common, client_golang, client_model, procfs, alertmanager and remote protocol packages";
    private static final String LEGACY_RULES =
            "Prometheus 1.x rule syntax is unsupported; convert with the historical Prometheus 2.5 promtool update rules workflow, then validate the generated YAML and every recording/alert expression with the target promtool";
    private static final String PROMQL =
            "This PromQL expression crosses 1.x/2.x/3.x semantics; verify removed functions/modifiers, holt_winters replacement and feature gate, range left-boundary behavior, regex newline matching, UTF-8 names, and normalized le/quantile label values";
    private static final String FLAGS =
            "This removed or combined Prometheus flag has no safe local one-token rewrite; migrate storage/Alertmanager/feature ownership using the 2.0 and 3.0 guides and validate the complete final argv";
    private static final String STORAGE =
            "Prometheus TSDB formats and downgrade boundaries changed; snapshot/backup, retention, WAL/block permissions, capacity, rollback and staged upgrade via a compatible release must be validated before reusing this storage path";
    private static final String IMAGE =
            "This deployment still pins a Prometheus 1.x/2.x image independently of the Go module; align the binary/image owner, architecture, UID/GID, volume permissions, flags, config, promtool and rollback plan deliberately";

    private static final Set<String> COMPANIONS = Set.of(
            "github.com/prometheus/common", "github.com/prometheus/client_golang",
            "github.com/prometheus/client_model", "github.com/prometheus/procfs",
            "github.com/prometheus/alertmanager");

    @Override public String getDisplayName() { return "Find Prometheus v0.311.3 text and Go migration risks"; }

    @Override
    public String getDescription() {
        return "Mark exact go.mod owners, unstable Prometheus Go imports, legacy rules/flags, TSDB paths, and " +
               "independently pinned server images while ignoring generated/vendor content.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!PrometheusSupport.isProjectPath(visited.getSourcePath())) return visited;
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (PrometheusSupport.fileName(visited.getSourcePath(), "go.mod")) return inspectGoMod(visited);
                if (path.endsWith(".go")) return inspectGoImports(visited);
                if (path.endsWith(".rules") || path.endsWith(".rules.txt")) return inspectRules(visited, true);
                if (path.endsWith(".promql") || path.endsWith(".prom")) return inspectRules(visited, false);
                if (operational(path, visited)) return inspectOperational(visited, path);
                return visited;
            }
        };
    }

    private static PlainText inspectGoMod(PlainText text) {
        String source = text.getText();
        List<Match> matches = new ArrayList<>();
        Pattern directRequirement = Pattern.compile("^[\\t ]*require[\\t ]+" +
                Pattern.quote(PrometheusSupport.MODULE) + "[\\t ]+(?<version>[^\\s/]+)");
        Pattern blockRequirement = Pattern.compile("^[\\t ]*" + Pattern.quote(PrometheusSupport.MODULE) +
                "[\\t ]+(?<version>[^\\s/]+)");
        Pattern blockStart = Pattern.compile("^(require|replace|exclude)\\s*\\(\\s*(?://.*)?$");
        Pattern blockEnd = Pattern.compile("^\\)\\s*(?://.*)?$");
        Pattern goDirective = Pattern.compile("^[\\t ]*go[\\t ]+(?<version>1\\.(?:[0-9]|1[0-9]|2[0-4])(?:\\.[0-9]+)?)(?:[\\t ]*//.*)?$");
        Pattern anyGoDirective = Pattern.compile("^[\\t ]*go[\\t ]+[^\\s/]+(?:[\\t ]*//.*)?$");
        Pattern moduleDeclaration = Pattern.compile("^[\\t ]*module[\\t ]+(?<path>[^\\s/]+(?:/[^\\s/]+)*)");
        String block = "";
        boolean hasGoDirective = false;
        boolean hasPrometheusRequirement = false;
        int moduleStart = -1;
        int moduleEnd = -1;
        int offset = 0;
        for (String withEnd : source.split("(?<=\\n)", -1)) {
            String line = withEnd.endsWith("\r\n") ? withEnd.substring(0, withEnd.length() - 2) :
                    withEnd.endsWith("\n") ? withEnd.substring(0, withEnd.length() - 1) : withEnd;
            String trimmed = line.trim();
            Matcher module = moduleDeclaration.matcher(line);
            if (module.find()) {
                moduleStart = offset + module.start("path");
                moduleEnd = offset + module.end("path");
            }
            Matcher anyGo = anyGoDirective.matcher(line);
            if (anyGo.matches()) hasGoDirective = true;
            Matcher oldGo = goDirective.matcher(line);
            if (oldGo.matches()) matches.add(new Match(offset + oldGo.start("version"),
                    offset + oldGo.end("version"), GO_BASELINE));

            Matcher start = blockStart.matcher(trimmed);
            if (block.isEmpty() && start.matches()) {
                block = start.group(1);
            } else if (!block.isEmpty() && blockEnd.matcher(trimmed).matches()) {
                block = "";
            } else if ("replace".equals(block) || "exclude".equals(block)) {
                addReplaceIfPrometheus(line, offset, matches);
            } else {
                Matcher requirement = ("require".equals(block) ? blockRequirement :
                        block.isEmpty() ? directRequirement : Pattern.compile("a^")).matcher(line);
                if (requirement.find()) {
                    hasPrometheusRequirement = true;
                    if (!PrometheusSupport.TARGET.equals(requirement.group("version"))) {
                        matches.add(new Match(offset + requirement.start("version"),
                                offset + requirement.end("version"), MODULE_OWNER));
                    }
                }
                if ("require".equals(block) || block.isEmpty()) addCompanions(line, offset, block.isEmpty(), matches);
                if (block.isEmpty() && trimmed.matches("(?:replace|exclude)\\b.*")) {
                    addReplaceIfPrometheus(line, offset, matches);
                }
            }
            offset += withEnd.length();
        }
        if (hasPrometheusRequirement && !hasGoDirective && moduleStart >= 0) {
            matches.add(new Match(moduleStart, moduleEnd, GO_BASELINE));
        }
        return markMatches(text, matches);
    }

    private static void addReplaceIfPrometheus(String line, int offset, List<Match> matches) {
        int start = line.indexOf(PrometheusSupport.MODULE);
        if (start < 0) return;
        int end = start + PrometheusSupport.MODULE.length();
        boolean left = start == 0 || Character.isWhitespace(line.charAt(start - 1));
        boolean right = end == line.length() || Character.isWhitespace(line.charAt(end));
        if (left && right) matches.add(new Match(offset + start, offset + end, REPLACE_OWNER));
    }

    private static void addCompanions(String line, int offset, boolean direct, List<Match> matches) {
        for (String companion : COMPANIONS) {
            Pattern pattern = Pattern.compile("^[\\t ]*" + (direct ? "require[\\t ]+" : "") +
                    Pattern.quote(companion) + "[\\t ]+[^\\s/]+");
            Matcher candidate = pattern.matcher(line);
            if (candidate.find()) matches.add(new Match(offset + candidate.start(), offset + candidate.end(), COMPANION));
        }
    }

    private static PlainText inspectGoImports(PlainText text) {
        Pattern direct = Pattern.compile("^[\\t ]*import[\\t ]+(?:(?:[A-Za-z_][A-Za-z0-9_]*|\\.)[\\t ]+)?" +
                "(?<quote>[\"`])(?<path>" + Pattern.quote(PrometheusSupport.MODULE) +
                "/[^\"`]+)\\k<quote>[\\t ]*(?://.*)?$");
        Pattern block = Pattern.compile("^[\\t ]*(?:(?:[A-Za-z_][A-Za-z0-9_]*|\\.)[\\t ]+)?" +
                "(?<quote>[\"`])(?<path>" + Pattern.quote(PrometheusSupport.MODULE) +
                "/[^\"`]+)\\k<quote>[\\t ]*(?://.*)?$");
        List<Match> matches = new ArrayList<>();
        String source = text.getText();
        boolean importBlock = false;
        PrometheusSupport.GoLexicalState lexical = new PrometheusSupport.GoLexicalState();
        int offset = 0;
        for (String withEnd : source.split("(?<=\\n)", -1)) {
            String line = withEnd.endsWith("\r\n") ? withEnd.substring(0, withEnd.length() - 2) :
                    withEnd.endsWith("\n") ? withEnd.substring(0, withEnd.length() - 1) : withEnd;
            String trimmed = line.trim();
            boolean ignored = lexical.insideMultilineLiteralOrComment();
            if (ignored) {
                lexical.scan(line);
                offset += withEnd.length();
                continue;
            }
            if (!importBlock && trimmed.matches("import\\s*\\(\\s*(?://.*)?")) importBlock = true;
            else if (importBlock && trimmed.matches("\\)\\s*(?://.*)?")) importBlock = false;
            else {
                Matcher matcher = (importBlock ? block : direct).matcher(line);
                if (matcher.matches()) matches.add(new Match(offset + matcher.start("quote"),
                        offset + matcher.end("path") + 1, INTERNAL_API));
            }
            lexical.scan(line);
            offset += withEnd.length();
        }
        return markMatches(text, matches);
    }

    private static PlainText inspectRules(PlainText text, boolean legacyFile) {
        List<Risk> risks = new ArrayList<>();
        if (legacyFile) {
            risks.add(risk("(?m)^[\\t ]*(?:ALERT|IF|FOR|LABELS|ANNOTATIONS)[\\t ]+[^\\r\\n]+", LEGACY_RULES));
            risks.add(risk("(?m)^[\\t ]*[A-Za-z_:][A-Za-z0-9_:]*[\\t ]*=", LEGACY_RULES));
        }
        risks.add(risk("\\b(?:holt_winters|count_scalar|drop_common_labels)\\s*\\(|\\bkeep_common\\b|\\[[0-9]+[smhdwy](?::[^]]*)?]", PROMQL));
        risks.add(risk("\\b(?:le|quantile)\\s*=~?\\s*\"[0-9]+\"", PROMQL));
        return markPatterns(text, risks);
    }

    private static List<Risk> operationalRisks() {
        return List.of(
                risk("(?<!-)\\-(?:storage\\.local\\.[A-Za-z0-9_.-]+|storage\\.remote\\.[A-Za-z0-9_.-]+|alertmanager\\.url|log\\.format)(?:=[^\\s\\\\]+)?", FLAGS),
                risk("--(?:alertmanager\\.timeout|storage\\.tsdb\\.allow-overlapping-blocks)(?:=[^\\s\\\\]+)?", FLAGS),
                risk("--storage\\.tsdb\\.path(?:=|[\\t ]+)[^\\s\\\\]+", STORAGE));
    }

    private static PlainText inspectOperational(PlainText text, String path) {
        List<Match> matches = new ArrayList<>();
        String source = text.getText();
        String file = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        boolean prometheusDocker = Pattern.compile("(?im)^\\s*FROM(?:\\s+--platform=\\S+)?\\s+prom/prometheus:")
                .matcher(source).find() || Pattern.compile("(?im)^\\s*ENTRYPOINT.*(?:^|[/\"'])prometheus(?:[\\s\"']|$)")
                .matcher(source).find();
        boolean continuation = false;
        int offset = 0;
        Pattern feature = Pattern.compile("--enable-feature=(?<features>[^\\s\\\\\"']+)");
        Pattern image = Pattern.compile("prom/prometheus:(?:v?(?:1|2)\\.[0-9][A-Za-z0-9_.-]*|latest)");
        for (String withEnd : source.split("(?<=\\n)", -1)) {
            String line = withEnd.endsWith("\r\n") ? withEnd.substring(0, withEnd.length() - 2) :
                    withEnd.endsWith("\n") ? withEnd.substring(0, withEnd.length() - 1) : withEnd;
            String trimmed = line.stripLeading();
            boolean docker = file.equals("dockerfile") || file.startsWith("dockerfile.");
            boolean from = docker && trimmed.toLowerCase(Locale.ROOT).matches("from\\b.*");
            boolean owned = continuation || ownsCommandLine(file, path, trimmed, prometheusDocker);
            boolean imageOwner = from || owned || trimmed.toLowerCase(Locale.ROOT).matches("(?:docker|podman)\\s+run\\b.*");
            if (!trimmed.startsWith("#") && (owned || imageOwner)) {
                if (owned) {
                    for (Risk risk : operationalRisks()) {
                        Matcher matcher = risk.pattern.matcher(line);
                        while (matcher.find()) matches.add(new Match(offset + matcher.start(),
                                offset + matcher.end(), risk.message));
                    }
                    Matcher featureMatcher = feature.matcher(line);
                    while (featureMatcher.find()) if (featureRisk(featureMatcher.group("features"))) {
                        matches.add(new Match(offset + featureMatcher.start(), offset + featureMatcher.end(), FLAGS));
                    }
                }
                if (imageOwner) {
                    Matcher imageMatcher = image.matcher(line);
                    while (imageMatcher.find()) matches.add(new Match(offset + imageMatcher.start(),
                            offset + imageMatcher.end(), IMAGE));
                }
            }
            continuation = owned && line.stripTrailing().endsWith("\\");
            offset += withEnd.length();
        }
        return markMatches(text, matches);
    }

    private static boolean featureRisk(String features) {
        Set<String> risks = Set.of("agent", "remote-write-receiver", "otlp-write-receiver",
                "promql-at-modifier", "promql-negative-offset", "new-service-discovery-manager",
                "expand-external-labels", "no-default-scrape-port", "auto-gomemlimit", "auto-gomaxprocs");
        for (String feature : features.split(",")) if (risks.contains(feature)) return true;
        return false;
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

    private static boolean operational(String path, PlainText text) {
        String file = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return file.equals("dockerfile") || file.startsWith("dockerfile.") || path.endsWith(".sh") ||
               path.endsWith(".service") || path.endsWith(".tpl") || path.endsWith(".tmpl") ||
               path.endsWith(".yaml.gotmpl") || path.endsWith(".yml.gotmpl");
    }

    private static PlainText markPatterns(PlainText text, List<Risk> risks) {
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern.matcher(text.getText());
            while (matcher.find()) matches.add(new Match(matcher.start(), matcher.end(), risk.message));
        }
        return markMatches(text, matches);
    }

    private static PlainText markMatches(PlainText text, List<Match> matches) {
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> selected = new ArrayList<>();
        int end = -1;
        for (Match match : matches) if (match.start >= end) { selected.add(match); end = match.end; }
        if (selected.isEmpty()) return text;
        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : selected) {
            if (cursor < match.start) snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(cursor, match.start)));
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static Risk risk(String regex, String message) { return new Risk(Pattern.compile(regex), message); }
    private record Risk(Pattern pattern, String message) { }
    private record Match(int start, int end, String message) { }
}
