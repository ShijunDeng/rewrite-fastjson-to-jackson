package com.huawei.clouds.openrewrite.pbr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class PbrRiskAndValidationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(PbrDependencyTest.environment().activateRecipes(PbrDependencyTest.MIGRATE));
    }

    @ParameterizedTest(name = "marks dynamic requirement {0}")
    @ValueSource(strings = {"pbr>=5.11.1", "pbr~=5.5.1", "pbr<8", "pbr!=7.0.1", "pbr==6.1.1", "pbr===7.0.2", "pbr @ git+https://example.test/pbr.git", "pbr"})
    void marksDynamicOrUnlistedRequirementOwner(String declaration) {
        rewriteRun(requirements(declaration + "\n", source -> contains(source, "not the exact workbook target 7.0.3")));
    }

    @Test
    void exactTargetRequirementIsClean() {
        rewriteRun(requirements("pbr==7.0.3\n"));
    }

    @Test
    void workbookSourceBecomesCleanExactTarget() {
        rewriteRun(requirements("pbr==5.11.1\n", "pbr==7.0.3\n"));
    }

    @Test
    void marksBarePyprojectBuildOwner() {
        rewriteRun(text("[build-system]\nrequires = [\"setuptools\", \"pbr\"]\nbuild-backend = \"pbr.build\"\n",
                source -> { source.path("pyproject.toml"); contains(source, "not the exact workbook target 7.0.3"); }));
    }

    @Test
    void marksPoetryCaretOwner() {
        rewriteRun(text("[tool.poetry.dependencies]\npbr = \"^5.11.1\"\n",
                source -> { source.path("pyproject.toml"); contains(source, "not the exact workbook target 7.0.3"); }));
    }

    @Test
    void exactPoetryTargetIsClean() {
        rewriteRun(text("[tool.poetry.dependencies]\npbr = \"7.0.3\"\n", source -> source.path("pyproject.toml")));
    }

    @Test
    void ignoresPbrTextOutsideDependencyOwners() {
        rewriteRun(
                requirements("# pbr>=5.11.1 is historical documentation\nmy-pbr>=5.11.1\npbr-tools==5.5.1\n"),
                text("[project]\ndescription = \"pbr>=5.11.1 is old\"\n\n[tool.acme]\npbr = \"5.11.1\"\n",
                        source -> source.path("pyproject.toml")),
                text("[options.entry_points]\nconsole_scripts = pbr>=5.11.1\n", source -> source.path("setup.cfg")),
                text("FROM python:3.11\nRUN echo 'pip install pbr>=5.11.1'\n", source -> source.path("Dockerfile")));
    }

    @Test
    void ignoresCommentedRemovedCommands() {
        rewriteRun(text("#!/bin/sh\n# python setup.py test\necho '# setup.py build_sphinx'\n",
                source -> source.path("run.sh")));
    }

    @Test
    void ignoresDashAliasesInUnrelatedSetupCfgSections() {
        rewriteRun(text("[flake8]\nauthor-email = docs@example.test\npackage-data = docs\nsetup-hooks = docs\n",
                source -> source.path("setup.cfg")));
    }

    @ParameterizedTest(name = "marks metadata option {0}")
    @ValueSource(strings = {"requires_dist", "setup_requires_dist", "python_requires", "requires_python", "provides_dist", "provides_extra", "obsoletes_dist", "description_file"})
    void marksDeprecatedMetadataOwners(String key) {
        rewriteRun(setupCfg("[metadata]\n" + key + " = value\n", source -> contains(source, "removed or deprecated in pbr 7")));
    }

    @ParameterizedTest(name = "marks legacy section {0}")
    @ValueSource(strings = {"files", "backwards_compat", "build_sphinx"})
    void marksLegacySetupCfgSections(String section) {
        rewriteRun(setupCfg("[" + section + "]\noption = value\n", source -> contains(source, "removed or deprecated in pbr 7")));
    }

    @Test
    void marksEntryPointsConflictLeftByAutoRecipe() {
        rewriteRun(setupCfg("[entry_points]\nconsole_scripts = old = old:main\n\n[options.entry_points]\nconsole_scripts = new = new:main\n",
                source -> contains(source, "removed or deprecated in pbr 7")));
    }

    @Test
    void migratedEntryPointsSectionIsClean() {
        rewriteRun(setupCfg("[entry_points]\nconsole_scripts = app = app:main\n",
                "[options.entry_points]\nconsole_scripts = app = app:main\n"));
    }

    @Test
    void marksMetadataAliasConflictLeftByAutoRecipe() {
        rewriteRun(setupCfg("[metadata]\nsummary = Legacy\ndescription = Canonical\n",
                source -> contains(source, "removed or deprecated in pbr 7")));
    }

    @Test
    void marksDashNormalizationConflictLeftByAutoRecipe() {
        rewriteRun(setupCfg("[metadata]\nauthor-email = old@example.test\nauthor_email = canonical@example.test\n",
                source -> contains(source, "removed or deprecated in pbr 7")));
    }

    @Test
    void marksGlobalCompilers() {
        rewriteRun(setupCfg("[global]\ncompilers = unix, mingw32\n", source -> contains(source, "removed [global] compilers support")));
    }

    @Test
    void marksTestsRequireAndTestAlias() {
        rewriteRun(
                setupCfg("[options]\ntests_require = pytest\n", source -> contains(source, "removed or deprecated in pbr 7")),
                text("[aliases]\ntest = pytest\n", source -> { source.path("tools/setup.cfg"); contains(source, "removed or deprecated in pbr 7"); }));
    }

    @ParameterizedTest(name = "marks removed command {0}")
    @ValueSource(strings = {"python setup.py test", "python3 setup.py test -q", "setup.py build_sphinx", "python setup.py build_sphinx -W"})
    void marksRemovedSetupCommands(String command) {
        rewriteRun(text(command + "\n", source -> { source.path("run.sh"); contains(source, "removed setup.py test/build_sphinx"); }));
    }

    @Test
    void marksRemovedCommandInToxAndWorkflowOwners() {
        rewriteRun(
                text("[testenv]\ncommands = python setup.py test\n", source -> { source.path("tox.ini"); contains(source, "removed setup.py test/build_sphinx"); }),
                text("steps:\n  - run: python setup.py build_sphinx\n", source -> { source.path(".github/workflows/docs.yml"); contains(source, "removed setup.py test/build_sphinx"); }));
    }

    @ParameterizedTest(name = "marks removed internal module {0}")
    @ValueSource(strings = {"pbr.core", "pbr.util", "pbr.builddoc", "pbr.testr_command", "pbr.hooks.commands"})
    void marksRemovedInternalModuleImports(String module) {
        rewriteRun(python("import " + module + "\n", source -> contains(source, "implementation module removed or reorganized")));
    }

    @Test
    void marksFromImportForms() {
        rewriteRun(
                python("from pbr.core import pbr\n", source -> contains(source, "implementation module removed or reorganized")),
                text("from pbr import util as pbr_util\n", source -> { source.path("tools/version.py"); contains(source, "implementation module removed or reorganized"); }));
    }

    @Test
    void stableVersionInfoImportIsNotMarked() {
        rewriteRun(python("from pbr.version import VersionInfo\n"));
    }

    @Test
    void marksDistutilsSetupOwner() {
        rewriteRun(text("from distutils.core import setup\nsetup(pbr=True)\n",
                source -> { source.path("setup.py"); contains(source, "setuptools plugin"); }));
    }

    @Test
    void setuptoolsSetupOwnerIsClean() {
        rewriteRun(text("from setuptools import setup\nsetup(setup_requires=['pbr==7.0.3'], pbr=True)\n",
                source -> source.path("setup.py")));
    }

    @ParameterizedTest(name = "skips generated risk {0}")
    @ValueSource(strings = {"vendor/setup.cfg", ".tox/setup.py", "build/tox.ini", "dist/tool.py", "generated/setup.cfg"})
    void skipsGeneratedRiskFiles(String path) {
        rewriteRun(text("[global]\ncompilers = unix\npython setup.py test\nimport pbr.core\n", source -> source.path(path)));
    }

    @Test
    void skipsHistoricalReleaseFixturesAndReadmeCommands() {
        rewriteRun(
                text("pbr>=5.5.1\n", source -> source.path("docs/releases/v2/requirements.txt")),
                text("The old command was python setup.py test.\n", source -> source.path("README.md")));
    }

    @Test
    void lockFilesRemainUnchangedAndUnmarked() {
        rewriteRun(
                text("pbr==5.11.1\n", source -> source.path("poetry.lock")),
                text("{\"default\": {\"pbr\": {\"version\": \"==5.5.1\"}}}\n", source -> source.path("Pipfile.lock")));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                requirements("pbr>=5.11.1\n", source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(1, occurrences(after.printAll(), "not the exact workbook target 7.0.3")))));
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = PbrDependencyTest.environment();
        Recipe upgrade = environment.activateRecipes(PbrDependencyTest.UPGRADE);
        Recipe migrate = environment.activateRecipes(PbrDependencyTest.MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> PbrDependencyTest.UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> PbrDependencyTest.MIGRATE.equals(recipe.getName())));
        assertEquals(PbrDependencyTest.UPGRADE, upgrade.getName());
        assertEquals(PbrDependencyTest.MIGRATE, migrate.getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs requirements(String source) {
        return text(source, spec -> spec.path("requirements.txt"));
    }

    private static org.openrewrite.test.SourceSpecs requirements(String source, String after) {
        return text(source, after, spec -> spec.path("requirements.txt"));
    }

    private static org.openrewrite.test.SourceSpecs requirements(String source, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.text.PlainText>> consumer) {
        return text(source, spec -> { spec.path("requirements.txt"); consumer.accept(spec); });
    }

    private static org.openrewrite.test.SourceSpecs setupCfg(String source, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.text.PlainText>> consumer) {
        return text(source, spec -> { spec.path("setup.cfg"); consumer.accept(spec); });
    }

    private static org.openrewrite.test.SourceSpecs setupCfg(String source, String after) {
        return text(source, after, spec -> spec.path("setup.cfg"));
    }

    private static org.openrewrite.test.SourceSpecs python(String source) {
        return text(source, spec -> spec.path("tool.py"));
    }

    private static org.openrewrite.test.SourceSpecs python(String source, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.text.PlainText>> consumer) {
        return text(source, spec -> { spec.path("tool.py"); consumer.accept(spec); });
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
