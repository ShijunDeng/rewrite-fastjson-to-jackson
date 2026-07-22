package com.huawei.clouds.openrewrite.pbr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class PbrAutomaticMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new UpgradeSelectedPbrDependency(), new MigratePbrSetupCfg());
    }

    @ParameterizedTest(name = "metadata {0} -> {1}")
    @CsvSource({
            "home_page,url",
            "summary,description",
            "classifier,classifiers",
            "platform,platforms"
    })
    void migratesConflictFreeMetadataAliases(String beforeKey, String afterKey) {
        rewriteRun(setupCfg("[metadata]\n" + beforeKey + " = value\n",
                "[metadata]\n" + afterKey + " = value\n"));
    }

    @ParameterizedTest(name = "normalizes {0}")
    @CsvSource({
            "author-email,author_email",
            "description-file,description_file",
            "description-content-type,description_content_type",
            "python-requires,python_requires",
            "requires-dist,requires_dist"
    })
    void normalizesMetadataDashSpellings(String beforeKey, String afterKey) {
        rewriteRun(setupCfg("[metadata]\n" + beforeKey + " = value\n",
                "[metadata]\n" + afterKey + " = value\n"));
    }

    @ParameterizedTest(name = "normalizes files key {0}")
    @CsvSource({"package-data,package_data", "data-files,data_files", "extra-files,extra_files"})
    void normalizesFilesDashSpellings(String beforeKey, String afterKey) {
        rewriteRun(setupCfg("[files]\n" + beforeKey + " = value\n",
                "[files]\n" + afterKey + " = value\n"));
    }

    @Test
    void normalizesGlobalSetupHooks() {
        rewriteRun(setupCfg("[global]\nsetup-hooks = acme.hooks.setup\n",
                "[global]\nsetup_hooks = acme.hooks.setup\n"));
    }

    @Test
    void migratesLegacyEntryPointsSection() {
        rewriteRun(setupCfg("[entry_points]\nconsole_scripts =\n    app = app:main\n",
                "[options.entry_points]\nconsole_scripts =\n    app = app:main\n"));
    }

    @Test
    void mapsDashedHomePageDirectlyToUrl() {
        rewriteRun(setupCfg("[metadata]\nhome-page = https://example.test\n",
                "[metadata]\nurl = https://example.test\n"));
    }

    @Test
    void preservesCrLfAndSpacing() {
        rewriteRun(setupCfg("[metadata]\r\n\tsummary\t= A project\r\n",
                "[metadata]\r\n\tdescription\t= A project\r\n"));
    }

    @Test
    void migratesOfficialPbrFixedCommitSetupCfgShape() {
        rewriteRun(setupCfg(
                "[metadata]\nname = pbr_testpackage\nauthor-email = openstack@example.test\nhome-page = https://example.test\nsummary = Test package\ndescription-file = README.txt\nclassifier =\n    Programming Language :: Python\n\n[files]\npackages = pbr_testpackage\npackage-data = testpackage = package_data/*.txt\n\n[entry_points]\nconsole_scripts =\n    demo = demo:main\n",
                "[metadata]\nname = pbr_testpackage\nauthor_email = openstack@example.test\nurl = https://example.test\ndescription = Test package\ndescription_file = README.txt\nclassifiers =\n    Programming Language :: Python\n\n[files]\npackages = pbr_testpackage\npackage_data = testpackage = package_data/*.txt\n\n[options.entry_points]\nconsole_scripts =\n    demo = demo:main\n"));
    }

    @Test
    void leavesMetadataAliasWhenTargetAlreadyExists() {
        rewriteRun(setupCfg("[metadata]\nsummary = Old summary\ndescription = Canonical description\n"));
    }

    @Test
    void leavesEntryPointsWhenBothSectionsExist() {
        rewriteRun(setupCfg("[entry_points]\nconsole_scripts = old = old:main\n\n[options.entry_points]\nconsole_scripts = new = new:main\n"));
    }

    @Test
    void leavesDashedKeyWhenUnderscoreKeyAlreadyExists() {
        rewriteRun(setupCfg("[metadata]\nauthor-email = old@example.test\nauthor_email = canonical@example.test\n"));
    }

    @Test
    void leavesHomePageSpellingsWhenBothExist() {
        rewriteRun(setupCfg("[metadata]\nhome-page = https://old.example.test\nhome_page = https://canonical.example.test\n"));
    }

    @Test
    void leavesDashedHomePageWhenCanonicalUrlAlreadyOwnsMetadata() {
        rewriteRun(setupCfg("[metadata]\nhome-page = https://old.example.test\nurl = https://canonical.example.test\n"));
    }

    @Test
    void leavesSameNamedDashKeysInUnrelatedSections() {
        rewriteRun(setupCfg("[flake8]\nauthor-email = untouched\npackage-data = untouched\nsetup-hooks = untouched\n"));
    }

    @Test
    void leavesDuplicateSourceKeysAndSectionsForReview() {
        rewriteRun(setupCfg("[metadata]\nsummary = first\nsummary = second\n\n[entry_points]\nconsole_scripts = a = a:main\n\n[entry_points]\nconsole_scripts = b = b:main\n"));
    }

    @Test
    void leavesCommentedKeysAndOtherSections() {
        rewriteRun(setupCfg("[metadata]\n# summary = docs only\n\n[other]\nsummary = untouched\n"));
    }

    @Test
    void leavesSameTextOutsideSetupCfg() {
        rewriteRun(text("[metadata]\nsummary = docs only\n", source -> source.path("README.md")));
    }

    @Test
    void automaticMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                text("[metadata]\nsummary = Test\n\n[entry_points]\nconsole_scripts = app = app:main\n",
                        "[metadata]\ndescription = Test\n\n[options.entry_points]\nconsole_scripts = app = app:main\n",
                        source -> source.path("setup.cfg")),
                text("pbr==5.5.1\n", "pbr==7.0.3\n", source -> source.path("requirements.txt")));
    }

    private static org.openrewrite.test.SourceSpecs setupCfg(String before) {
        return text(before, source -> source.path("setup.cfg"));
    }

    private static org.openrewrite.test.SourceSpecs setupCfg(String before, String after) {
        return text(before, after, source -> source.path("setup.cfg"));
    }
}
