package com.huawei.clouds.openrewrite.icu4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class Icu4jUpgradeTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.icu4j.UpgradeIcu4jTo77_1";
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.icu4j.MigrateIcu4jTo77_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void sourceWhitelistExactlyMatchesVisibleWorkbookCells() {
        assertEquals(Set.of("67.1", "73.1", "73.2"), UpgradeSelectedIcu4jDependency.SOURCE_VERSIONS);
        assertEquals("77.1", UpgradeSelectedIcu4jDependency.TARGET);
    }

    @ParameterizedTest(name = "Maven {0} -> 77.1")
    @ValueSource(strings = {"67.1", "73.1", "73.2"})
    void upgradesEveryWorkbookMavenVersion(String source) {
        rewriteRun(pomXml(pom(source), pom("77.1")));
    }

    @Test
    void upgradesRealReportPortalGradleDeclarationAtFixedCommit() {
        // Reduced from reportportal/agent-java-testNG at ae2b0beb07314f2dfb3473b28f481257d0bd175b:
        // https://github.com/reportportal/agent-java-testNG/blob/ae2b0beb07314f2dfb3473b28f481257d0bd175b/build.gradle#L41-L68
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api 'com.epam.reportportal:client-java:5.4.13'
                    testImplementation 'commons-io:commons-io:2.17.0'
                    testImplementation 'com.ibm.icu:icu4j:67.1'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api 'com.epam.reportportal:client-java:5.4.13'
                    testImplementation 'commons-io:commons-io:2.17.0'
                    testImplementation 'com.ibm.icu:icu4j:77.1'
                }
                """
        ));
    }

    @Test
    void upgradesRealIReaderKotlinDeclarationAtFixedCommit() {
        // Reduced from IReaderorg/IReader at 9e2b0d917532ceccd8c29c4fd3fde53ddda2cce5:
        // https://github.com/IReaderorg/IReader/blob/9e2b0d917532ceccd8c29c4fd3fde53ddda2cce5/android-compat/build.gradle.kts#L71-L90
        rewriteRun(buildGradleKts(
                """
                plugins { `java-library` }
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
                    implementation("com.ibm.icu:icu4j:73.1")
                }
                """,
                """
                plugins { `java-library` }
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
                    implementation("com.ibm.icu:icu4j:77.1")
                }
                """
        ));
    }

    @Test
    void upgradesRealWhatwgUrlKotlinDeclarationAtFixedCommit() {
        // Reduced from stephanebastian/whatwg-url at 91025bca89e71a85c98fc437110314f6b9b23337:
        // https://github.com/stephanebastian/whatwg-url/blob/91025bca89e71a85c98fc437110314f6b9b23337/build.gradle.kts#L15-L20
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                dependencies {
                    implementation ("com.ibm.icu:icu4j:73.2")
                    testImplementation("com.google.code.gson:gson:2.10.1")
                }
                """,
                """
                plugins { java }
                dependencies {
                    implementation ("com.ibm.icu:icu4j:77.1")
                    testImplementation("com.google.code.gson:gson:2.10.1")
                }
                """
        ));
    }

    @Test
    void upgradesExclusivelyOwnedMavenProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>icu</artifactId><version>1</version>
                  <properties><icu4j.version>73.2</icu4j.version></properties>
                  <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>${icu4j.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>icu</artifactId><version>1</version>
                  <properties><icu4j.version>77.1</icu4j.version></properties>
                  <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>${icu4j.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesSharedMavenPropertyForItsOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><platform.version>73.2</platform.version></properties>
                  <dependencies>
                    <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>${platform.version}</version></dependency>
                    <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j-charset</artifactId><version>${platform.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesLocallyOwnedDependencyManagementAndLeavesVersionlessUse() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>67.1</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>77.1</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesProfileDependencyAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>unicode</id><dependencies><dependency>
                    <groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>67.1</version>
                    <scope>test</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>unicode</id><dependencies><dependency>
                    <groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>77.1</version>
                    <scope>test</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @ParameterizedTest(name = "does not widen to {0}")
    @ValueSource(strings = {"66.1", "68.1", "69.1", "70.1", "71.1", "72.1", "74.1", "75.1", "76.1", "77.1"})
    void doesNotWidenMavenSourceVersionSet(String version) {
        rewriteRun(pomXml(pom(version)));
    }

    @Test
    void leavesVariantsOtherCoordinatesAndVersionlessDependencies() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>negative</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency>
                  <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>67.1</version><classifier>sources</classifier></dependency>
                  <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>73.1</version><type>test-jar</type></dependency>
                  <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j-charset</artifactId><version>73.2</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.15.2</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/pom.xml", "build/pom.xml", "out/pom.xml", "dist/pom.xml", "generated/pom.xml",
            "generated-client/pom.xml", "install/pom.xml", ".mvn/cache/pom.xml", ".m2/cache/pom.xml",
            ".yarn/cache/pom.xml", "node_modules/pom.xml", "vendor/pom.xml"})
    void skipsGeneratedMavenTrees(String path) {
        rewriteRun(pomXml(pom("67.1"), source -> source.path(path)));
    }

    @Test
    void upgradesGroovyStringAndBothMapNotations() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                dependencies {
                    implementation 'com.ibm.icu:icu4j:67.1'
                    testImplementation group: 'com.ibm.icu', name: 'icu4j', version: '73.1'
                    runtimeOnly([group: 'com.ibm.icu', name: 'icu4j', version: '73.2'])
                }
                """,
                """
                plugins { id 'java-library' }
                dependencies {
                    implementation 'com.ibm.icu:icu4j:77.1'
                    testImplementation group: 'com.ibm.icu', name: 'icu4j', version: '77.1'
                    runtimeOnly([group: 'com.ibm.icu', name: 'icu4j', version: '77.1'])
                }
                """
        ));
    }

    @Test
    void leavesGradleVariablesCatalogsPlatformsVariantsAndNonDependencyCalls() {
        rewriteRun(buildGradle(
                """
                def icuVersion = '67.1'
                dependencies {
                    implementation "com.ibm.icu:icu4j:${icuVersion}"
                    implementation libs.icu4j
                    implementation platform('com.ibm.icu:icu4j:73.1')
                    implementation 'com.ibm.icu:icu4j:73.2:sources'
                    implementation group: 'com.ibm.icu', name: 'icu4j', version: '67.1', classifier: 'sources'
                }
                subprojects { dependencies { implementation 'com.ibm.icu:icu4j:67.1' } }
                println 'com.ibm.icu:icu4j:67.1'
                """
        ));
    }

    @Test
    void strictRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("73.2"), pom("77.1")),
                buildGradleKts(
                        "dependencies { implementation(\"com.ibm.icu:icu4j:67.1\") }",
                        "dependencies { implementation(\"com.ibm.icu:icu4j:77.1\") }"
                )
        );
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Recipe upgrade = environment().activateRecipes(UPGRADE);
        Recipe migration = environment().activateRecipes(MIGRATE);
        assertFalse(upgrade.getRecipeList().isEmpty());
        assertFalse(migration.getRecipeList().isEmpty());
        assertTrue(upgrade.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertTrue(migration.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    @Test
    void preservesOldNumericMeaningOfQualifiedIdnaDefault() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        """
                        import com.ibm.icu.text.IDNA;
                        class Options { int flags() { return IDNA.DEFAULT | IDNA.USE_STD3_RULES; } }
                        """,
                        """
                        import com.ibm.icu.text.IDNA;
                        class Options { int flags() { return 0 | IDNA.USE_STD3_RULES; } }
                        """
                )
        );
    }

    @Test
    void preservesOldNumericMeaningOfStaticIdnaDefault() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        """
                        import static com.ibm.icu.text.IDNA.DEFAULT;
                        class Options { int flags() { return DEFAULT; } }
                        """,
                        """
                        class Options { int flags() { return 0; } }
                        """
                )
        );
    }

    @Test
    void removesObsoleteStaticListFormatterStyleImportAfterMigration() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        """
                        import static com.ibm.icu.text.ListFormatter.Style.STANDARD;
                        import com.ibm.icu.text.ListFormatter;
                        import com.ibm.icu.util.ULocale;
                        class Lists { ListFormatter format(ULocale locale) { return ListFormatter.getInstance(locale, STANDARD); } }
                        """,
                        """
                        import com.ibm.icu.text.ListFormatter;
                        import com.ibm.icu.util.ULocale;
                        class Lists { ListFormatter format(ULocale locale) { return ListFormatter.getInstance(locale, ListFormatter.Type.AND, ListFormatter.Width.WIDE); } }
                        """
                )
        );
    }

    @Test
    void preservesUnrelatedUnusedImportWhileCleaningIcuStaticImport() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        """
                        import java.util.Map;
                        import static com.ibm.icu.text.IDNA.DEFAULT;
                        class Options { int flags() { return DEFAULT; } }
                        """,
                        """
                        import java.util.Map;
                        class Options { int flags() { return 0; } }
                        """
                )
        );
    }

    @ParameterizedTest(name = "ListFormatter.Style.{0}")
    @ValueSource(strings = {"STANDARD", "OR", "UNIT", "UNIT_SHORT", "UNIT_NARROW"})
    void migratesEveryKnownListFormatterStyle(String style) {
        String mapped = switch (style) {
            case "STANDARD" -> "ListFormatter.Type.AND, ListFormatter.Width.WIDE";
            case "OR" -> "ListFormatter.Type.OR, ListFormatter.Width.WIDE";
            case "UNIT" -> "ListFormatter.Type.UNITS, ListFormatter.Width.WIDE";
            case "UNIT_SHORT" -> "ListFormatter.Type.UNITS, ListFormatter.Width.SHORT";
            default -> "ListFormatter.Type.UNITS, ListFormatter.Width.NARROW";
        };
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        """
                        import com.ibm.icu.text.ListFormatter;
                        import com.ibm.icu.util.ULocale;
                        class Lists { ListFormatter formatter(ULocale locale) { return ListFormatter.getInstance(locale, ListFormatter.Style.%s); } }
                        """.formatted(style),
                        """
                        import com.ibm.icu.text.ListFormatter;
                        import com.ibm.icu.util.ULocale;
                        class Lists { ListFormatter formatter(ULocale locale) { return ListFormatter.getInstance(locale, %s); } }
                        """.formatted(mapped)
                )
        );
    }

    @Test
    void leavesComputedListFormatterStyleForReviewAndLocalDefaultsAlone() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        """
                        import com.ibm.icu.text.ListFormatter;
                        import com.ibm.icu.util.ULocale;
                        class Local {
                            static final int DEFAULT = 9;
                            ListFormatter choose(ULocale locale, ListFormatter.Style style) {
                                int local = DEFAULT;
                                return ListFormatter.getInstance(locale, style);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void deterministicMigrationSkipsGeneratedJava() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicIcu4jJava()).parser(icuParser()),
                java(
                        "import com.ibm.icu.text.IDNA; class Generated { int flags() { return IDNA.DEFAULT; } }",
                        source -> source.path("target/generated-sources/Generated.java")
                )
        );
    }

    @Test
    void marksRemovedAndUnsupportedTypesPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jJavaMigrationRisks()).parser(icuParser()),
                java(
                        """
                        import com.ibm.icu.impl.ICUResourceBundle;
                        import com.ibm.icu.text.ListFormatter;
                        import com.ibm.icu.text.PluralRanges;
                        import com.ibm.icu.util.NoUnit;
                        class Legacy {
                            ICUResourceBundle internal;
                            ListFormatter.Style style;
                            PluralRanges ranges;
                            NoUnit noUnit;
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "unsupported ICU implementation API");
                            assertContains(printed, "ListFormatter.Style");
                            assertContains(printed, "PluralRanges was a draft API");
                            assertContains(printed, "NoUnit constants are exposed as MeasureUnit");
                        })
                )
        );
    }

    @Test
    void marksIdnaCollationSegmentationFormattingTimezoneAndLocaleBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jJavaMigrationRisks()).parser(icuParser()),
                java(
                        """
                        import com.ibm.icu.text.*;
                        import com.ibm.icu.util.*;
                        class Boundaries {
                            void run(String value, ULocale locale) throws Exception {
                                IDNA.convertIDNToASCII(value, IDNA.DEFAULT);
                                Collator.getInstance(locale).compare("a", "b");
                                BreakIterator.getWordInstance(locale);
                                NumberFormat.getInstance(locale).format(12);
                                TimeZone.getTimeZone("Europe/Brussels").getOffset(0L);
                                ULocale.addLikelySubtags(locale);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "legacy IDNA2003 API");
                            assertContains(printed, "collation data changed");
                            assertContains(printed, "changed word, line, and grapheme segmentation");
                            assertContains(printed, "Unicode/CLDR formatting data changed");
                            assertContains(printed, "timezone data and pre-1970 aliases changed");
                            assertContains(printed, "Locale fallback/matching data changed");
                        })
                )
        );
    }

    @Test
    void marksOnlyRemovedBasicTimeZoneIntegerFlagOverload() {
        JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().dependsOn(
                "package com.ibm.icu.util; public class BasicTimeZone { " +
                "public enum LocalOption { FORMER, LATTER } " +
                "public void getOffsetFromLocal(long t, int a, int b, int[] offsets) {} " +
                "public void getOffsetFromLocal(long t, LocalOption a, LocalOption b, int[] offsets) {} }");
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jJavaMigrationRisks()).parser(parser),
                java(
                        """
                        import com.ibm.icu.util.BasicTimeZone;
                        class Zones {
                            void inspect(BasicTimeZone zone, int[] offsets) {
                                zone.getOffsetFromLocal(0L, 1, 1, offsets);
                                zone.getOffsetFromLocal(0L, BasicTimeZone.LocalOption.FORMER, BasicTimeZone.LocalOption.LATTER, offsets);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertEquals(1, printed.split("LOCAL_\\* bit flags", -1).length - 1);
                        })
                )
        );
    }

    @Test
    void doesNotMarkUnrelatedLookalikeJavaApis() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jJavaMigrationRisks()),
                java(
                        """
                        class IDNA { static int convertIDNToASCII(String value, int flags) { return 0; } }
                        class NumberFormat { String format(int value) { return ""; } }
                        class Local { void run() { IDNA.convertIDNToASCII("x", 0); new NumberFormat().format(1); } }
                        """
                )
        );
    }

    @Test
    void marksMavenManagementExternalVariantsCompanionsAndOldJava() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jBuildMigrationRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risk</artifactId><version>1</version>
                          <properties><maven.compiler.release>7</maven.compiler.release></properties>
                          <dependencyManagement><dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>73.2</version></dependency></dependencies></dependencyManagement>
                          <dependencies>
                            <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>${icu.version}</version></dependency>
                            <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>67.1</version><classifier>sources</classifier></dependency>
                            <dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j-charset</artifactId><version>73.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "requires Java 8 or newer");
                            assertContains(printed, "versionless, property/catalog/BOM-managed");
                            assertContains(printed, "classified or non-JAR ICU4J artifact");
                            assertContains(printed, "companion is not aligned to 77.1");
                        })
                )
        );
    }

    @Test
    void recommendedRecipeDoesNotMarkLocallyTargetManagedVersionlessDependency() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)).parser(icuParser()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>73.2</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>77.1</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void profileManagementDoesNotLeakIntoRootDependencyScope() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jBuildMigrationRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scope</artifactId><version>1</version>
                          <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency></dependencies>
                          <profiles><profile><id>managed</id>
                            <dependencyManagement><dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>77.1</version></dependency></dependencies></dependencyManagement>
                            <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId></dependency></dependencies>
                          </profile></profiles>
                        </project>
                        """,
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertEquals(1, printed.split("versionless, property/catalog/BOM-managed", -1).length - 1);
                        })
                )
        );
    }

    @Test
    void marksGradleOutsideDynamicCompanionAndVariantVersions() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jBuildMigrationRisks()),
                buildGradle(
                        """
                        dependencies {
                            implementation 'com.ibm.icu:icu4j:76.1'
                            implementation 'com.ibm.icu:icu4j:77.+'
                            runtimeOnly 'com.ibm.icu:icu4j-localespi:73.2'
                            testImplementation 'com.ibm.icu:icu4j:73.1:sources'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "outside the workbook source selection");
                            assertContains(printed, "versionless, property/catalog/BOM-managed");
                            assertContains(printed, "companion is not aligned to 77.1");
                            assertContains(printed, "classified or non-JAR ICU4J artifact");
                        })
                )
        );
    }

    @Test
    void marksMavenShadeRelocation() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shade</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><configuration><relocations><relocation>
                            <pattern>com.ibm.icu</pattern><shadedPattern>example.internal.icu</shadedPattern>
                          </relocation></relocations></configuration>
                        </plugin></plugins></build></project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "ICU4J is shaded or relocated"))
                )
        );
    }

    @Test
    void ignoresCompilerAndShadeLookalikesOutsideOwnedBuildPlugins() {
        rewriteRun(
                spec -> spec.recipe(new FindIcu4jBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>lookalike</artifactId><version>1</version>
                          <dependencies><dependency><groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>77.1</version></dependency></dependencies>
                          <configuration><properties><maven.compiler.release>7</maven.compiler.release></properties><pattern>com.ibm.icu</pattern></configuration>
                          <reporting><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>7</release></configuration></plugin></plugins></reporting>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>maven-shade-plugin</artifactId><configuration><relocations><relocation><pattern>com.ibm.icu</pattern></relocation></relocations></configuration></plugin></plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void recommendedRecipeUpgradesAutoMigratesAndMarksInOneRun() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)).parser(icuParser()),
                pomXml(pom("67.1"), pom("77.1")),
                java(
                        """
                        import com.ibm.icu.text.IDNA;
                        import com.ibm.icu.text.NumberFormat;
                        import com.ibm.icu.util.ULocale;
                        class App {
                            int flags() { return IDNA.DEFAULT; }
                            String number() { return NumberFormat.getInstance(ULocale.ENGLISH).format(12); }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "return 0;");
                            assertContains(printed, "Unicode/CLDR formatting data changed");
                        })
                )
        );
    }

    @Test
    void recommendedRecipeIsStableAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)).parser(icuParser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("73.1"), pom("77.1")),
                java(
                        """
                        import com.ibm.icu.text.IDNA;
                        class App { int flags() { return IDNA.DEFAULT; } }
                        """,
                        """
                        class App { int flags() { return 0; } }
                        """
                )
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static JavaParser.Builder<?, ?> icuParser() {
        return JavaParser.fromJavaVersion().classpath("icu4j");
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>com.ibm.icu</groupId><artifactId>icu4j</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected output to contain: " + expected + "\nActual:\n" + actual);
    }
}
