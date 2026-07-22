package com.huawei.clouds.openrewrite.prometheus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class PrometheusRiskAndValidationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(PrometheusDependencyTest.environment().activateRecipes(PrometheusDependencyTest.MIGRATE));
    }

    @ParameterizedTest(name = "marks old Go baseline {0}")
    @ValueSource(strings = {"1.12", "1.16", "1.18", "1.20", "1.22.1", "1.23", "1.24.6"})
    void marksGoToolchainBelowTargetBaseline(String version) {
        rewriteRun(goMod("module example.com/app\n\ngo " + version + "\n\nrequire github.com/prometheus/prometheus v0.311.3\n",
                source -> contains(source, "declares Go 1.25.0")));
    }

    @Test
    void targetGoBaselineAndTargetModuleAreClean() {
        rewriteRun(goMod("module example.com/app\n\ngo 1.25.0\n\nrequire github.com/prometheus/prometheus v0.311.3\n"));
    }

    @Test
    void marksUnlistedRequirementOwner() {
        rewriteRun(goMod("module example.com/app\n\ngo 1.25\n\nrequire github.com/prometheus/prometheus v0.55.0\n",
                source -> contains(source, "not the exact workbook target")));
    }

    @Test
    void marksReplaceForkOwner() {
        rewriteRun(goMod("module example.com/app\n\ngo 1.25\n\nrequire github.com/prometheus/prometheus v0.311.3\nreplace github.com/prometheus/prometheus => github.com/acme/prometheus v0.311.3-patched\n",
                source -> contains(source, "replace/exclude directive")));
    }

    @Test
    void marksExcludeOwner() {
        rewriteRun(goMod("module example.com/app\n\ngo 1.25\nexclude github.com/prometheus/prometheus v0.300.0\n",
                source -> contains(source, "replace/exclude directive")));
    }

    @Test
    void marksReplaceBlockAsReplaceOwnerNotRequirementOwner() {
        rewriteRun(goMod("module example.com/app\ngo 1.25\nreplace (\n\tgithub.com/prometheus/prometheus v0.44.0 => github.com/acme/prometheus v0.44.0\n) // replacements\n",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("replace/exclude directive"), printed);
                    assertFalse(printed.contains("not the exact workbook target"), printed);
                })));
    }

    @Test
    void marksMissingGoDirectiveAtModuleOwner() {
        rewriteRun(goMod("module example.com/app\nrequire github.com/prometheus/prometheus v0.311.3\n",
                source -> contains(source, "declares Go 1.25.0")));
    }

    @Test
    void marksOldGoDirectiveWithTrailingComment() {
        rewriteRun(goMod("module example.com/app\ngo 1.24.6 // toolchain floor\nrequire github.com/prometheus/prometheus v0.311.3\n",
                source -> contains(source, "declares Go 1.25.0")));
    }

    @ParameterizedTest(name = "marks companion {0}")
    @ValueSource(strings = {"github.com/prometheus/common", "github.com/prometheus/client_golang", "github.com/prometheus/client_model", "github.com/prometheus/procfs", "github.com/prometheus/alertmanager"})
    void marksExplicitPrometheusCompanion(String module) {
        rewriteRun(goMod("module example.com/app\n\ngo 1.25\nrequire (\n\tgithub.com/prometheus/prometheus v0.311.3\n\t" + module + " v0.1.0\n)\n",
                source -> contains(source, "companion module can skew")));
    }

    @ParameterizedTest(name = "marks unstable Go package {0}")
    @ValueSource(strings = {"promql/parser", "storage/remote", "tsdb", "rules", "model/labels", "model/textparse", "discovery/kubernetes"})
    void marksPrometheusServerGoPackageImports(String packagePath) {
        rewriteRun(go("package app\nimport \"github.com/prometheus/prometheus/" + packagePath + "\"\n",
                source -> contains(source, "does not guarantee stability")));
    }

    @Test
    void marksCorootFixedCommitPromqlParserFixture() {
        rewriteRun(go("package config\nimport \"github.com/prometheus/prometheus/promql/parser\"\n",
                source -> contains(source, "does not guarantee stability")));
    }

    @Test
    void marksRawDotImportButNotStringAfterCommentedBlockClose() {
        rewriteRun(go("package app\nimport (\n\t. `github.com/prometheus/prometheus/promql/parser`\n) // imports\nvar fixture = `\n\"github.com/prometheus/prometheus/tsdb\"\n`\n",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(1, occurrences(after.printAll(), "does not guarantee stability")))));
    }

    @Test
    void ignoresFakeGoImportsInsideRawStringsAndBlockComments() {
        rewriteRun(go("package app\nvar fixture = `\nimport (\n\"github.com/prometheus/prometheus/tsdb\"\n)\n`\n/* import \"github.com/prometheus/prometheus/promql/parser\" */\n"));
    }

    @Test
    void unrelatedGoImportIsClean() {
        rewriteRun(go("package app\nimport \"github.com/prometheus/client_golang/prometheus\"\n"));
    }

    @ParameterizedTest(name = "marks legacy rule token {0}")
    @ValueSource(strings = {"ALERT ApiDown\n  IF up == 0\n  FOR 5m\n", "job:http_requests:rate5m = rate(http_requests_total[5m])\n"})
    void marksPrometheus1RuleSyntax(String rules) {
        rewriteRun(text(rules, source -> source.path("alerts.rules").after(actual -> actual)
                .afterRecipe(after -> assertTrue(after.printAll().contains("Prometheus 1.x rule syntax")))));
    }

    @ParameterizedTest(name = "marks changed PromQL {0}")
    @ValueSource(strings = {"holt_winters(temp[1h], 0.5, 0.5)", "count_scalar(up)", "drop_common_labels(up)", "sum(up) keep_common"})
    void marksRemovedOrRenamedPromql(String query) {
        rewriteRun(text(query + "\n", source -> source.path("query.promql").after(actual -> actual)
                .afterRecipe(after -> assertTrue(after.printAll().contains("crosses 1.x/2.x/3.x semantics")))));
    }

    @Test
    void marksPromqlRangeBoundary() {
        rewriteRun(text("rate(requests_total[5m])\n", source -> source.path("query.promql").after(actual -> actual)
                .afterRecipe(after -> assertTrue(after.printAll().contains("range left-boundary")))));
    }

    @Test
    void marksIntegerHistogramLabelMatcher() {
        rewriteRun(text("sum(rate(latency_bucket{le=\"1\"}[5m]))\n", source -> source.path("query.promql").after(actual -> actual)
                .afterRecipe(after -> assertTrue(after.printAll().contains("normalized le/quantile")))));
    }

    @ParameterizedTest(name = "marks removed operational flag {0}")
    @ValueSource(strings = {"-storage.local.path=/data", "-storage.remote.influxdb-url=http://db", "-alertmanager.url=http://am:9093", "--alertmanager.timeout=10s", "--storage.tsdb.allow-overlapping-blocks"})
    void marksRemovedOperationalFlags(String flag) {
        rewriteRun(shell("exec prometheus " + flag + "\n", source -> contains(source, "removed or combined Prometheus flag")));
    }

    @Test
    void marksCombinedFeatureFlags() {
        rewriteRun(shell("exec prometheus --enable-feature=agent,native-histograms\n",
                source -> contains(source, "removed or combined Prometheus flag")));
    }

    @Test
    void marksRemovedDefaultFeatureFlag() {
        rewriteRun(shell("exec prometheus --enable-feature=promql-at-modifier\n",
                source -> contains(source, "removed or combined Prometheus flag")));
    }

    @Test
    void marksTsdbPathForRollbackPlanning() {
        rewriteRun(shell("exec prometheus --storage.tsdb.path=/prometheus\n",
                source -> contains(source, "TSDB formats and downgrade boundaries")));
    }

    @Test
    void marksOldPrometheusImageInDockerfile() {
        rewriteRun(text("FROM prom/prometheus:v2.55.1\n", source -> source.path("Dockerfile")
                .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("pins a Prometheus 1.x/2.x image")))));
    }

    @Test
    void targetImageIsNotMarked() {
        rewriteRun(text("FROM prom/prometheus:v3.11.3\n", source -> source.path("Dockerfile")));
    }

    @Test
    void ignoresOperationalCommentsLabelsDescriptionsAndEchoProse() {
        rewriteRun(
                text("#!/bin/sh\n# prometheus -storage.local.path=/data\necho 'prometheus --storage.tsdb.path=/data'\n",
                        source -> source.path("run.sh")),
                text("FROM alpine\n# FROM prom/prometheus:v2.55.1\nLABEL old=\"prom/prometheus:v2.55.1\"\nRUN echo 'prometheus -storage.local.path=/data'\n",
                        source -> source.path("Dockerfile")),
                text("FROM alpine\nCMD [\"-storage.local.path=/data\"]\n", source -> source.path("Dockerfile.worker")),
                text("[Unit]\nDescription=prometheus -storage.local.path=/data\n",
                        source -> source.path("prometheus.service")));
    }

    @Test
    void marksScrapeProtocolAndUtf8Boundary() {
        rewriteRun(yaml("global:\n  scrape_interval: 15s\nscrape_configs:\n  - job_name: api\n    static_configs:\n      - targets: ['api:8080']\n",
                source -> contains(source, "requires valid scrape Content-Type")));
    }

    @Test
    void marksRemoteWriteDefaultAndQueueBoundary() {
        rewriteRun(yaml("remote_write:\n  - url: https://remote.example/write\n",
                source -> contains(source, "remote-write HTTP/2 default")));
    }

    @Test
    void marksAlertmanagerV1Api() {
        rewriteRun(yaml("alerting:\n  alertmanagers:\n    - api_version: v1\n      static_configs:\n        - targets: ['am:9093']\n",
                source -> contains(source, "rejects Alertmanager API v1")));
    }

    @Test
    void alertmanagerV2ApiIsNotSpecificallyMarked() {
        rewriteRun(yaml("alerting:\n  alertmanagers:\n    - api_version: v2\n      static_configs:\n        - targets: ['am:9093']\n"));
    }

    @Test
    void marksRegexDotNewlineSemantics() {
        rewriteRun(yaml("scrape_configs:\n  - job_name: api\n    relabel_configs:\n      - source_labels: [path]\n        regex: foo.*bar\n        action: keep\n",
                source -> contains(source, "regex dot matches newline")));
    }

    @Test
    void escapedLiteralDotIsNotSpecificallyMarked() {
        rewriteRun(yaml("relabel_configs:\n  - regex: 'foo\\.bar'\n"));
    }

    @Test
    void regexCharacterClassDotIsNotMarkedButEvenEscapedDotIs() {
        rewriteRun(
                yaml("scrape_configs:\n  - job_name: safe\n    relabel_configs:\n      - regex: 'foo[.]bar'\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertFalse(after.printAll().contains("regex dot matches newline"), after.printAll()))),
                yaml("scrape_configs:\n  - job_name: risky\n    relabel_configs:\n      - regex: 'foo\\\\.*bar'\n",
                        source -> contains(source, "regex dot matches newline")));
    }

    @Test
    void marksExternalLabelEnvironmentExpansion() {
        rewriteRun(yaml("global:\n  external_labels:\n    cluster: ${CLUSTER}\n",
                source -> contains(source, "expands environment variables in external labels")));
    }

    @ParameterizedTest(name = "marks YAML PromQL semantics {0}")
    @ValueSource(strings = {"holt_winters(temp[1h], 0.5, 0.5)", "rate(requests_total[5m])", "latency_bucket{le=\"1\"}"})
    void marksYamlPromqlSemantics(String expression) {
        rewriteRun(yaml("groups:\n  - name: test\n    rules:\n      - record: test\n        expr: '" + expression + "'\n",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("Prometheus 3") || printed.contains("PromQL uses"), printed);
                })));
    }

    @Test
    void marksRemovedFeatureInYamlArg() {
        rewriteRun(yaml("prometheusArgs:\n  - --enable-feature=promql-negative-offset\n",
                source -> contains(source, "removed or combined Prometheus feature")));
    }

    @Test
    void marksCombinedYamlFeatureArg() {
        rewriteRun(yaml("prometheusArgs:\n  - --enable-feature=agent,native-histograms\n",
                source -> contains(source, "removed or combined Prometheus feature")));
    }

    @Test
    void combinedUnaffectedFeatureArgumentsAreClean() {
        rewriteRun(yaml("prometheusArgs:\n  - --enable-feature=native-histograms,exemplar-storage\n"));
    }

    @Test
    void genericYamlArgsWithoutPrometheusOwnerAreClean() {
        rewriteRun(yaml("containers:\n  - name: worker\n    image: acme/worker:1\n    args:\n      - --enable-feature=promql-negative-offset\n      - -storage.local.path=/data\n"));
    }

    @Test
    void unrelatedYamlLookalikesAreNotMarked() {
        rewriteRun(yaml(
                "service:\n  scrape_configs:\n    - scrape_classic_histograms: true\n      relabel_configs:\n        - regex: foo.*bar\nmetadata:\n  api_version: v1\n  regex: foo.*bar\n  query: rate(requests_total[5m])\n  note: prom/prometheus:v2.55.1\n  flag: --enable-feature=promql-negative-offset\n"));
    }

    @Test
    void marksOldAndReplacementYamlKeyConflict() {
        rewriteRun(yaml("scrape_configs:\n  - job_name: api\n    target_groups:\n      - targets: ['api:8080']\n    static_configs:\n      - targets: ['api:8081']\n",
                source -> contains(source, "exist in the same mapping")));
    }

    @Test
    void marksOldImageInKubernetesYaml() {
        rewriteRun(yaml("apiVersion: v1\nkind: Pod\nspec:\n  containers:\n    - name: prometheus\n      image: prom/prometheus:v2.55.1\n",
                source -> contains(source, "pins a Prometheus 1.x/2.x image")));
    }

    @Test
    void skipsGeneratedAndVendoredRisks() {
        rewriteRun(
                text("module x\ngo 1.20\nrequire github.com/prometheus/prometheus v0.55.0\n", source -> source.path("vendor/x/go.mod")),
                text("package p\nimport \"github.com/prometheus/prometheus/tsdb\"\n", source -> source.path("build/generated/p.go")),
                yaml("scrape_configs:\n  - job_name: x\n", source -> source.path("target/prometheus.yml"))
        );
    }

    @Test
    void skipsCaseVariantAndGeneratedPrefixDirectories() {
        rewriteRun(
                text("module x\ngo 1.20\nrequire github.com/prometheus/prometheus v0.55.0\n",
                        source -> source.path("Vendor/x/go.mod")),
                yaml("scrape_configs:\n  - job_name: x\n", source -> source.path("generated-test-resources/prometheus.yml"))
        );
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                goMod("module x\ngo 1.20\nrequire github.com/prometheus/prometheus v0.55.0\n",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertEquals(1, occurrences(after.printAll(), "declares Go 1.25.0"));
                            assertEquals(1, occurrences(after.printAll(), "not the exact workbook target"));
                        }))
        );
    }

    @Test
    void yamlConflictMarkerIsIdempotentAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                yaml("global:\n  labels:\n    cluster: legacy\n  external_labels:\n    cluster: current\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        "Both deprecated Prometheus key labels and replacement external_labels"))))
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = PrometheusDependencyTest.environment();
        Recipe upgrade = environment.activateRecipes(PrometheusDependencyTest.UPGRADE);
        Recipe migrate = environment.activateRecipes(PrometheusDependencyTest.MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> PrometheusDependencyTest.UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> PrometheusDependencyTest.MIGRATE.equals(recipe.getName())));
        assertEquals(PrometheusDependencyTest.UPGRADE, upgrade.getName());
        assertEquals(PrometheusDependencyTest.MIGRATE, migrate.getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs goMod(String source) {
        return text(source, spec -> spec.path("go.mod"));
    }

    private static org.openrewrite.test.SourceSpecs goMod(String source, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.text.PlainText>> consumer) {
        return text(source, spec -> { spec.path("go.mod"); consumer.accept(spec); });
    }

    private static org.openrewrite.test.SourceSpecs go(String source) {
        return text(source, spec -> spec.path("main.go"));
    }

    private static org.openrewrite.test.SourceSpecs go(String source, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.text.PlainText>> consumer) {
        return text(source, spec -> { spec.path("main.go"); consumer.accept(spec); });
    }

    private static org.openrewrite.test.SourceSpecs shell(String source, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.text.PlainText>> consumer) {
        return text(source, spec -> { spec.path("run-prometheus.sh"); consumer.accept(spec); });
    }

    private static void contains(org.openrewrite.test.SourceSpec<?> source, String token) {
        source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(token), after::printAll));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int i = 0; (i = source.indexOf(token, i)) >= 0; i += token.length()) count++;
        return count;
    }
}
