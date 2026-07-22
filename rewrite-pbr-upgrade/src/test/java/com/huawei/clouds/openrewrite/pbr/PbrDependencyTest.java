package com.huawei.clouds.openrewrite.pbr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.test.SourceSpecs.text;

class PbrDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.pbr.UpgradePbrTo7_0_3";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.pbr.MigratePbrTo7_0_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades workbook source {0}")
    @ValueSource(strings = {"5.11.1", "5.5.1"})
    void upgradesEveryVisibleWorkbookSource(String version) {
        rewriteRun(requirements("pbr==" + version + "\n", "pbr==7.0.3\n"));
    }

    @Test
    void whitelistIsExactlyTheTwoVisibleWorkbookCells() {
        assertEquals(Set.of("5.11.1", "5.5.1"), PbrSupport.SOURCE_VERSIONS);
    }

    @ParameterizedTest(name = "active requirements owner {0}")
    @ValueSource(strings = {
            "requirements.txt", "requirements-dev.txt", "requirements_test.in",
            "constraints.txt", "constraints-py311.txt", "docs/requirements.txt",
            "tools/constraints-ci.in"
    })
    void upgradesActiveRequirementsAndConstraintOwners(String path) {
        rewriteRun(text("pbr==5.11.1\n", "pbr==7.0.3\n", source -> source.path(path)));
    }

    @Test
    void preservesEnvironmentMarkerCommentAndHash() {
        rewriteRun(requirements(
                "pbr == 5.5.1 ; python_version >= '3.9'  # build metadata\n" +
                "pbr===5.11.1 --hash=sha256:abcdef\n",
                "pbr == 7.0.3 ; python_version >= '3.9'  # build metadata\n" +
                "pbr===7.0.3 --hash=sha256:abcdef\n"));
    }

    @Test
    void preservesCrLfAndContinuation() {
        rewriteRun(requirements("pbr==5.11.1 \\\r\n", "pbr==7.0.3 \\\r\n"));
    }

    @Test
    void upgradesSetupPyQuotedBuildRequirement() {
        rewriteRun(text(
                "from setuptools import setup\nsetup(setup_requires=['pbr==5.5.1'], pbr=True)\n",
                "from setuptools import setup\nsetup(setup_requires=['pbr==7.0.3'], pbr=True)\n",
                source -> source.path("setup.py")));
    }

    @Test
    void upgradesMultilineSetupPyDependencyOwner() {
        rewriteRun(text(
                "from setuptools import setup\nsetup(\n    setup_requires=[\n        'pbr==5.11.1',\n    ],\n    description='pbr==5.11.1 is documentation',\n)\n",
                "from setuptools import setup\nsetup(\n    setup_requires=[\n        'pbr==7.0.3',\n    ],\n    description='pbr==5.11.1 is documentation',\n)\n",
                source -> source.path("setup.py")));
    }

    @Test
    void upgradesPyprojectBuildSystemAndProjectDependencyArrays() {
        rewriteRun(text(
                "[build-system]\nrequires = [\"setuptools\", \"pbr==5.11.1\"]\nbuild-backend = \"pbr.build\"\n\n[project]\ndependencies = ['pbr===5.5.1']\n",
                "[build-system]\nrequires = [\"setuptools\", \"pbr==7.0.3\"]\nbuild-backend = \"pbr.build\"\n\n[project]\ndependencies = ['pbr===7.0.3']\n",
                source -> source.path("pyproject.toml")));
    }

    @Test
    void upgradesPoetryExactVersionOwner() {
        rewriteRun(text(
                "[tool.poetry.dependencies]\npython = \"^3.11\"\npbr = \"5.11.1\"\n",
                "[tool.poetry.dependencies]\npython = \"^3.11\"\npbr = \"7.0.3\"\n",
                source -> source.path("pyproject.toml")));
    }

    @Test
    void upgradesMultilinePep621OptionalDependencyArray() {
        rewriteRun(text(
                "[project.optional-dependencies]\nbuild = [\n  \"setuptools\",\n  \"pbr==5.5.1\",\n]\n",
                "[project.optional-dependencies]\nbuild = [\n  \"setuptools\",\n  \"pbr==7.0.3\",\n]\n",
                source -> source.path("pyproject.toml")));
    }

    @Test
    void upgradesPipfileExactPin() {
        rewriteRun(text("[dev-packages]\npbr = \"==5.5.1\"\n",
                "[dev-packages]\npbr = \"==7.0.3\"\n", source -> source.path("Pipfile")));
    }

    @Test
    void upgradesSetupCfgAndToxDependencyLists() {
        rewriteRun(
                text("[options]\nsetup_requires =\n    pbr==5.11.1\n", "[options]\nsetup_requires =\n    pbr==7.0.3\n",
                        source -> source.path("setup.cfg")),
                text("[testenv]\ndeps =\n    pbr==5.5.1\n", "[testenv]\ndeps =\n    pbr==7.0.3\n",
                        source -> source.path("tox.ini")));
    }

    @Test
    void upgradesDeprecatedMetadataDependencyOwnerWithoutMovingIt() {
        rewriteRun(text("[metadata]\nsetup_requires_dist = pbr==5.11.1\n",
                "[metadata]\nsetup_requires_dist = pbr==7.0.3\n", source -> source.path("setup.cfg")));
    }

    @Test
    void upgradesCondaEnvironmentOwner() {
        rewriteRun(text("dependencies:\n  - python=3.11\n  - pip:\n    - pbr==5.5.1\n",
                "dependencies:\n  - python=3.11\n  - pip:\n    - pbr==7.0.3\n", source -> source.path("environment.yml")));
    }

    @Test
    void upgradesMultilineDockerPipOwner() {
        rewriteRun(text("FROM python:3.11\n" +
                        "RUN pip install --no-cache-dir \\\n" +
                        "    setuptools \\\n" +
                        "    pbr==5.5.1 \\\n" +
                        "    wheel\n",
                "FROM python:3.11\n" +
                        "RUN pip install --no-cache-dir \\\n" +
                        "    setuptools \\\n" +
                        "    pbr==7.0.3 \\\n" +
                        "    wheel\n", source -> source.path("Dockerfile")));
    }

    @Test
    void upgradesChainedPythonModulePipCommand() {
        rewriteRun(text("FROM python:3.11\nRUN apt-get update && python3 -m pip install pbr==5.11.1\n",
                "FROM python:3.11\nRUN apt-get update && python3 -m pip install pbr==7.0.3\n",
                source -> source.path("Dockerfile")));
    }

    @Test
    void leavesDocumentationStringsInOwnerFilesUntouched() {
        rewriteRun(
                text("print('pbr==5.11.1 is the old example')\n", source -> source.path("setup.py")),
                text("[project]\ndescription = \"pbr==5.11.1 is the old example\"\n\n[tool.acme]\npbr = \"5.5.1\"\n",
                        source -> source.path("pyproject.toml")),
                text("[scripts]\npbr = \"pbr==5.11.1\"\n", source -> source.path("Pipfile")),
                text("[options.entry_points]\nconsole_scripts = pbr==5.5.1\n", source -> source.path("setup.cfg")),
                text("FROM python:3.11\nRUN echo 'pip install pbr==5.11.1'\n", source -> source.path("Dockerfile")));
    }

    @ParameterizedTest(name = "leaves unlisted version {0}")
    @ValueSource(strings = {"5.5.0", "5.6.0", "5.11.0", "5.11.2", "6.0.0", "6.1.1", "7.0.0", "7.0.2", "7.0.3", "8.0.0", "latest", "main"})
    void leavesUnlistedVersions(String version) {
        rewriteRun(requirements("pbr==" + version + "\n"));
    }

    @ParameterizedTest(name = "does not widen range {0}")
    @ValueSource(strings = {"pbr>=5.11.1", "pbr~=5.5.1", "pbr>5.5.1", "pbr<=5.11.1", "pbr!=5.5.1", "pbr==5.11.1,<6", "pbr", "pbr @ git+https://example/pbr.git"})
    void leavesDynamicAndCompoundOwners(String declaration) {
        rewriteRun(requirements(declaration + "\n"));
    }

    @Test
    void leavesCommentAndSimilarNames() {
        rewriteRun(requirements("# pbr==5.11.1\npbr-tools==5.11.1\nmy-pbr==5.5.1\n"));
    }

    @ParameterizedTest(name = "generated/lock owner {0}")
    @ValueSource(strings = {"poetry.lock", "Pipfile.lock", "uv.lock", "requirements.lock",
            "vendor/requirements.txt", ".tox/requirements.txt", ".venv/requirements.txt",
            "build/requirements.txt", "dist/requirements.txt", "generated/requirements.txt",
            "generated-client/requirements.txt", "installed-sdk/requirements.txt", ".pytest_cache/requirements.txt"})
    void skipsGeneratedAndLockFiles(String path) {
        rewriteRun(text("pbr==5.11.1\n", source -> source.path(path)));
    }

    @Test
    void skipsHistoricalReleaseSnapshotAndDocumentationText() {
        rewriteRun(
                text("pbr==5.5.1 \\\n", source -> source.path("docs/releases/v2_4_0-requirements.txt")),
                text("Install pbr==5.11.1 for the old release.\n", source -> source.path("README.md")));
    }

    @Test
    void upgradesSecuredropFixedCommitContinuationFixture() {
        rewriteRun(text("bandit==1.7.5 \\\n" +
                        "pbr==5.11.1 \\\n" +
                        "    --hash=sha256:abcdef\n",
                "bandit==1.7.5 \\\n" +
                        "pbr==7.0.3 \\\n" +
                        "    --hash=sha256:abcdef\n", source -> source.path("dev-requirements.txt")));
    }

    @Test
    void upgradesLokoleFixedCommitCommentFixture() {
        rewriteRun(text("pbr==5.11.1  # Explicit dependency for bandit\n",
                "pbr==7.0.3  # Explicit dependency for bandit\n", source -> source.path("requirements-dev.txt")));
    }

    @Test
    void upgradesHaloscopeFixedCommitFixture() {
        rewriteRun(text("numpy==1.19.5\npbr==5.5.1\nscipy==1.5.4\n",
                "numpy==1.19.5\npbr==7.0.3\nscipy==1.5.4\n", source -> source.path("requirements.txt")));
    }

    @Test
    void upgradesBackendAiFixedCommitDockerFixture() {
        rewriteRun(text("RUN pip install \\\n" +
                        "        pbr==5.5.1 \\\n" +
                        "        six==1.15.0\n",
                "RUN pip install \\\n" +
                        "        pbr==7.0.3 \\\n" +
                        "        six==1.15.0\n", source -> source.path("python-ff/Dockerfile.21.03-py38-cuda11.1")));
    }

    @Test
    void lowLevelRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                requirements("pbr==5.11.1\n", "pbr==7.0.3\n"));
    }

    private static org.openrewrite.test.SourceSpecs requirements(String before) {
        return text(before, source -> source.path("requirements.txt"));
    }

    private static org.openrewrite.test.SourceSpecs requirements(String before, String after) {
        return text(before, after, source -> source.path("requirements.txt"));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.pbr").build();
    }
}
