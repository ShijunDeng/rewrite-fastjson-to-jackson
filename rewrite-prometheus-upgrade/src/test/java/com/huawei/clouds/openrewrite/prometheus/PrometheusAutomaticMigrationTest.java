package com.huawei.clouds.openrewrite.prometheus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class PrometheusAutomaticMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(
                new UpgradeSelectedPrometheusDependency(),
                new MigratePrometheusGoImports(),
                new MigratePrometheusConfiguration(),
                new MigratePrometheusCommandFlags()
        );
    }

    @ParameterizedTest(name = "relocates {0}")
    @CsvSource({
            "pkg/labels,model/labels",
            "pkg/textparse,model/textparse",
            "pkg/relabel,model/relabel",
            "pkg/timestamp,model/timestamp",
            "pkg/value,model/value"
    })
    void relocatesFiveDocumentedGoPackages(String oldPath, String newPath) {
        rewriteRun(go("package p\n\nimport \"github.com/prometheus/prometheus/" + oldPath + "\"\n",
                      "package p\n\nimport \"github.com/prometheus/prometheus/" + newPath + "\"\n"));
    }

    @Test
    void relocatesAliasedImportInBlock() {
        rewriteRun(go(
                "package p\n\nimport (\n\tpromlabels \"github.com/prometheus/prometheus/pkg/labels\" // old API\n\t_ \"github.com/prometheus/prometheus/pkg/textparse\"\n)\n",
                "package p\n\nimport (\n\tpromlabels \"github.com/prometheus/prometheus/model/labels\" // old API\n\t_ \"github.com/prometheus/prometheus/model/textparse\"\n)\n"));
    }

    @Test
    void relocatesDotAliasAndRawImportLiteral() {
        rewriteRun(go(
                "package p\nimport (\n\t. `github.com/prometheus/prometheus/pkg/labels`\n)\n",
                "package p\nimport (\n\t. `github.com/prometheus/prometheus/model/labels`\n)\n"));
    }

    @Test
    void closesImportBlockWithCommentWithoutRewritingLaterString() {
        rewriteRun(go(
                "package p\nimport (\n\t\"github.com/prometheus/prometheus/pkg/labels\"\n) // imports\nvar fixture = `\n\"github.com/prometheus/prometheus/pkg/relabel\"\n`\n",
                "package p\nimport (\n\t\"github.com/prometheus/prometheus/model/labels\"\n) // imports\nvar fixture = `\n\"github.com/prometheus/prometheus/pkg/relabel\"\n`\n"));
    }

    @Test
    void ignoresFakeImportsInsideGoRawStringsAndBlockComments() {
        rewriteRun(go("package p\nvar fixture = `\nimport (\n\"github.com/prometheus/prometheus/pkg/labels\"\n)\n`\n/*\nimport \"github.com/prometheus/prometheus/pkg/relabel\"\n*/\n"));
    }

    @Test
    void migratesPeriskopFixedCommitImportFixture() {
        rewriteRun(go(
                "package servicediscovery\n\nimport (\n\t\"github.com/prometheus/prometheus/discovery\"\n\t\"github.com/prometheus/prometheus/discovery/config\"\n\t\"github.com/prometheus/prometheus/discovery/targetgroup\"\n\tprometheus_labels \"github.com/prometheus/prometheus/pkg/labels\"\n\tprometheus_relabel \"github.com/prometheus/prometheus/pkg/relabel\"\n)\n",
                "package servicediscovery\n\nimport (\n\t\"github.com/prometheus/prometheus/discovery\"\n\t\"github.com/prometheus/prometheus/discovery/config\"\n\t\"github.com/prometheus/prometheus/discovery/targetgroup\"\n\tprometheus_labels \"github.com/prometheus/prometheus/model/labels\"\n\tprometheus_relabel \"github.com/prometheus/prometheus/model/relabel\"\n)\n"));
    }

    @Test
    void relocatesOneLineImportBlock() {
        rewriteRun(go("package p\nimport (\n\t\"github.com/prometheus/prometheus/pkg/relabel\"\n)\n",
                      "package p\nimport (\n\t\"github.com/prometheus/prometheus/model/relabel\"\n)\n"));
    }

    @Test
    void leavesCommentedImportAndOrdinaryStringUntouched() {
        rewriteRun(go("package p\n// import \"github.com/prometheus/prometheus/pkg/labels\"\nvar path = \"github.com/prometheus/prometheus/pkg/labels\"\n"));
    }

    @Test
    void leavesSimilarAndAlreadyMigratedImports() {
        rewriteRun(go("package p\nimport (\n\t\"github.com/prometheus/prometheus/pkg/labelsx\"\n\t\"github.com/prometheus/prometheus/model/labels\"\n)\n"));
    }

    @Test
    void skipsGeneratedAndVendorGo() {
        rewriteRun(
                text("package p\nimport \"github.com/prometheus/prometheus/pkg/labels\"\n", source -> source.path("vendor/acme/p.go")),
                text("package p\nimport \"github.com/prometheus/prometheus/pkg/labels\"\n", source -> source.path("build/generated/p.go"))
        );
    }

    @Test
    void renamesPrometheus3HistogramConfigKey() {
        rewriteRun(yaml("scrape_configs:\n  - job_name: api\n    scrape_classic_histograms: true\n",
                        "scrape_configs:\n  - job_name: api\n    always_scrape_classic_histograms: true\n"));
    }

    @Test
    void renamesPrometheus1StaticTargetKey() {
        rewriteRun(yaml("scrape_configs:\n  - job_name: api\n    target_groups:\n      - targets: ['api:8080']\n",
                        "scrape_configs:\n  - job_name: api\n    static_configs:\n      - targets: ['api:8080']\n"));
    }

    @Test
    void renamesOnlyGlobalLabels() {
        rewriteRun(yaml(
                "global:\n  labels:\n    region: eu\nscrape_configs:\n  - job_name: api\n    static_configs:\n      - targets: ['api:8080']\n        labels:\n          tier: backend\n",
                "global:\n  external_labels:\n    region: eu\nscrape_configs:\n  - job_name: api\n    static_configs:\n      - targets: ['api:8080']\n        labels:\n          tier: backend\n"));
    }

    @Test
    void leavesPrometheusLikeKeysOutsidePrometheusConfigStructure() {
        rewriteRun(yaml("service:\n  global:\n    labels:\n      team: api\n  scrape_classic_histograms: true\n  target_groups:\n    - name: app\n  scrape_configs:\n    - scrape_classic_histograms: true\n      target_groups:\n        - name: nested\n"));
    }

    @Test
    void leavesOldYamlKeyWhenReplacementSiblingExists() {
        rewriteRun(yaml("scrape_configs:\n  - job_name: api\n    scrape_classic_histograms: true\n    always_scrape_classic_histograms: false\n"));
    }

    @ParameterizedTest(name = "renames YAML argv {0}")
    @CsvSource({
            "--enable-feature=agent,--agent",
            "--enable-feature=remote-write-receiver,--web.enable-remote-write-receiver",
            "--enable-feature=otlp-write-receiver,--web.enable-otlp-receiver",
            "--storage.tsdb.retention=30d,--storage.tsdb.retention.time=30d",
            "-query.staleness-delta=5m,--query.lookback-delta=5m"
    })
    void renamesExactYamlCommandArguments(String before, String after) {
        rewriteRun(yaml(
                "containers:\n  - name: prometheus\n    image: prom/prometheus:v2.55.1\n    args:\n      - " + before + "\n",
                "containers:\n  - name: prometheus\n    image: prom/prometheus:v2.55.1\n    args:\n      - " + after + "\n"));
    }

    @ParameterizedTest(name = "renames operational text {0}")
    @CsvSource({
            "--enable-feature=agent,--agent",
            "--enable-feature=remote-write-receiver,--web.enable-remote-write-receiver",
            "--enable-feature=otlp-write-receiver,--web.enable-otlp-receiver",
            "--storage.tsdb.retention=30d,--storage.tsdb.retention.time=30d",
            "-query.staleness-delta=5m,--query.lookback-delta=5m"
    })
    void renamesExactOperationalTextFlags(String before, String after) {
        rewriteRun(text("#!/bin/sh\nexec prometheus " + before + "\n",
                        "#!/bin/sh\nexec prometheus " + after + "\n", source -> source.path("run-prometheus.sh")));
    }

    @Test
    void renamesDockerfileFlag() {
        rewriteRun(text("FROM prom/prometheus:v2.55.1\nCMD [\"--enable-feature=agent\"]\n",
                        source -> source.path("Dockerfile")
                                .after(actual -> actual.replace("--enable-feature=agent", "--agent"))));
    }

    @Test
    void leavesCombinedFeatureListForManualDecision() {
        rewriteRun(yaml("prometheusArgs:\n  - --enable-feature=agent,native-histograms\n"));
    }

    @Test
    void leavesGenericYamlArgsWithoutPrometheusOwner() {
        rewriteRun(yaml("containers:\n  - name: worker\n    image: acme/worker:1\n    args:\n      - --enable-feature=agent\n      - --storage.tsdb.retention=30d\n"));
    }

    @Test
    void installScriptLeafIsMaintainedButInstallDirectoriesAreSkipped() {
        rewriteRun(
                text("exec prometheus --enable-feature=agent\n",
                        "exec prometheus --agent\n", source -> source.path("scripts/install.sh")),
                text("exec prometheus --enable-feature=agent\n", source -> source.path("install-cache/run.sh"))
        );
    }

    @Test
    void leavesPrometheusFlagTextOutsideYamlArgumentOwners() {
        rewriteRun(yaml("metadata:\n  annotation: --enable-feature=agent\nconfig:\n  example: --storage.tsdb.retention=30d\n"));
    }

    @Test
    void leavesCommentsLabelsAndEchoProseInOperationalText() {
        rewriteRun(
                text("#!/bin/sh\n# prometheus --enable-feature=agent\necho 'prometheus --enable-feature=agent'\n",
                        source -> source.path("run.sh")),
                text("FROM alpine\n# CMD [\"--enable-feature=agent\"]\nLABEL example=\"--enable-feature=agent\"\nRUN echo 'prometheus --enable-feature=agent'\n",
                        source -> source.path("Dockerfile")),
                text("FROM alpine\nCMD [\"--enable-feature=agent\"]\n", source -> source.path("Dockerfile.worker")),
                text("[Unit]\nDescription=prometheus --enable-feature=agent\n",
                        source -> source.path("prometheus.service")));
    }

    @Test
    void renamesOwnedShellVariableAndSystemdCommand() {
        rewriteRun(
                text("PROMETHEUS_ARGS=\"--enable-feature=agent\"\n",
                        "PROMETHEUS_ARGS=\"--agent\"\n", source -> source.path("run.sh")),
                text("[Service]\nExecStart=/usr/bin/prometheus --enable-feature=agent\n",
                        "[Service]\nExecStart=/usr/bin/prometheus --agent\n", source -> source.path("prometheus.service")));
    }

    @Test
    void doesNotRenameFlagsInDocumentation() {
        rewriteRun(text("Use --enable-feature=agent while testing.\n", source -> source.path("README.md")));
    }

    @Test
    void automaticChangesAreIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                text("module example.com/app\nrequire github.com/prometheus/prometheus v0.54.1\n",
                     "module example.com/app\nrequire github.com/prometheus/prometheus v0.311.3\n", source -> source.path("go.mod")),
                go("package p\nimport \"github.com/prometheus/prometheus/pkg/labels\"\n",
                   "package p\nimport \"github.com/prometheus/prometheus/model/labels\"\n")
        );
    }

    private static org.openrewrite.test.SourceSpecs go(String before) {
        return text(before, source -> source.path("main.go"));
    }

    private static org.openrewrite.test.SourceSpecs go(String before, String after) {
        return text(before, after, source -> source.path("main.go"));
    }
}
