package com.huawei.clouds.openrewrite.swiper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeSwiperTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.swiper.UpgradeSwiperDependencyTo12_1_2";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.swiper.MigrateDeterministicSwiperSourceTo12";
    private static final String RISK_RECIPE =
            "com.huawei.clouds.openrewrite.swiper.FindManualSwiper12MigrationRisks";
    private static final String COMPOSITE_RECIPE =
            "com.huawei.clouds.openrewrite.swiper.MigrateSwiperTo12_1_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @Test
    void upgradesAkveoNebularThree() {
        // Reduced from akveo/nebular at f761f852e6a46d163fe2c360a04df35c31b24246.
        // https://github.com/akveo/nebular/blob/f761f852e6a46d163fe2c360a04df35c31b24246/package.json
        rewriteRun(json(
                """
                {
                  "name": "smag",
                  "dependencies": {
                    "angular2-useful-swiper": "4.0.7",
                    "swiper": "^3.4.2"
                  },
                  "devDependencies": {"typescript": "2.3.4"}
                }
                """,
                """
                {
                  "name": "smag",
                  "dependencies": {
                    "angular2-useful-swiper": "4.0.7",
                    "swiper": "12.1.2"
                  },
                  "devDependencies": {"typescript": "2.3.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesJellyfinWebSixEightOne() {
        // Reduced from jellyfin/jellyfin-web at a99ac7791a7f735b3041883aa7f8948af4f5543f.
        // https://github.com/jellyfin/jellyfin-web/blob/a99ac7791a7f735b3041883aa7f8948af4f5543f/package.json
        rewriteRun(json(
                """
                {
                  "name": "jellyfin-web",
                  "devDependencies": {
                    "typescript": "^4.3.5",
                    "webpack": "^5.50.0"
                  },
                  "dependencies": {"swiper": "^6.8.1"}
                }
                """,
                """
                {
                  "name": "jellyfin-web",
                  "devDependencies": {
                    "typescript": "^4.3.5",
                    "webpack": "^5.50.0"
                  },
                  "dependencies": {"swiper": "12.1.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNumbersProtocolCaptureCamSixEightFour() {
        // Reduced from numbersprotocol/capture-cam at 9e33602a (the parent of its 7.4.1 bump).
        // https://github.com/numbersprotocol/capture-cam/blob/9e33602a/package.json
        rewriteRun(json(
                """
                {
                  "name": "capture-app",
                  "dependencies": {
                    "@ionic/angular": "^5.6.13",
                    "swiper": "^6.8.4"
                  },
                  "devDependencies": {"@angular/cli": "~12.2.0"}
                }
                """,
                """
                {
                  "name": "capture-app",
                  "dependencies": {
                    "@ionic/angular": "^5.6.13",
                    "swiper": "12.1.2"
                  },
                  "devDependencies": {"@angular/cli": "~12.2.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesTransmissionicEightFourSeven() {
        // Reduced from 6c65726f79/Transmissionic at 54ca0a7c264d4534a5ed6c37db12d56ecf522002.
        // https://github.com/6c65726f79/Transmissionic/blob/54ca0a7c264d4534a5ed6c37db12d56ecf522002/package.json
        rewriteRun(json(
                """
                {
                  "name": "transmissionic",
                  "dependencies": {
                    "@ionic/vue": "^6.6.2",
                    "swiper": "^8.4.7",
                    "vue": "^3.2.47"
                  }
                }
                """,
                """
                {
                  "name": "transmissionic",
                  "dependencies": {
                    "@ionic/vue": "^6.6.2",
                    "swiper": "12.1.2",
                    "vue": "^3.2.47"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesMapuppyTradeTrustEightFourSeven() {
        // Reduced from Mapuppy09/tradetrust-website at 143ed9b062be33cb0db58c45518aacfb2b568ddb.
        // https://github.com/Mapuppy09/tradetrust-website/blob/143ed9b062be33cb0db58c45518aacfb2b568ddb/package.json
        rewriteRun(json(
                """
                {
                  "name": "tradetrust-website",
                  "dependencies": {
                    "next": "13.0.6",
                    "react": "18.2.0",
                    "swiper": "8.4.7"
                  }
                }
                """,
                """
                {
                  "name": "tradetrust-website",
                  "dependencies": {
                    "next": "13.0.6",
                    "react": "18.2.0",
                    "swiper": "12.1.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesPermifyDocumentationNineOne() {
        // Reduced from Permify/permify at b17c461d.
        // https://github.com/Permify/permify/blob/b17c461d/docs/documentation/package.json
        rewriteRun(json(
                """
                {
                  "name": "documentation",
                  "dependencies": {
                    "docusaurus": "^2.3.1",
                    "swiper": "^9.1.0"
                  }
                }
                """,
                """
                {
                  "name": "documentation",
                  "dependencies": {
                    "docusaurus": "^2.3.1",
                    "swiper": "12.1.2"
                  }
                }
                """,
                spec -> spec.path("docs/documentation/package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {
            "3.4.2", "6.8.1", "6.8.4", "7.2.0", "8.3.1", "8.4.7", "9.1.0", "9.2.0", "9.4.1"
    })
    void upgradesEveryExactSpreadsheetVersion(String version) {
        rewriteRun(packageVersion("exact/" + version + "/package.json", "dependencies", version));
    }

    @ParameterizedTest(name = "upgrades selected declaration {0}")
    @ValueSource(strings = {
            "^3.4.2", "~3.4.2", "=3.4.2", "v3.4.2", "^v3.4.2",
            "^6.8.1", "~6.8.1", "=6.8.1", "v6.8.1", "^v6.8.1",
            "^6.8.4", "~6.8.4", "=6.8.4", "v6.8.4", "^v6.8.4",
            "^7.2.0", "~7.2.0", "=7.2.0", "v7.2.0", "^v7.2.0",
            "^8.3.1", "~8.3.1", "=8.3.1", "v8.3.1", "^v8.3.1",
            "^8.4.7", "~8.4.7", "=8.4.7", "v8.4.7", "^v8.4.7",
            "^9.1.0", "~9.1.0", "=9.1.0", "v9.1.0", "^v9.1.0",
            "^9.2.0", "~9.2.0", "=9.2.0", "v9.2.0", "^v9.2.0",
            "^9.4.1", "~9.4.1", "=9.4.1", "v9.4.1", "^v9.4.1"
    })
    void upgradesSupportedRegistrySemverForms(String declaration) {
        rewriteRun(packageVersion("semver/" + declaration.hashCode() + "/package.json", "dependencies", declaration));
    }

    @ParameterizedTest(name = "upgrades {0} from {1}")
    @MethodSource("selectedVersionAndSection")
    void upgradesEverySelectedVersionInEveryDirectSection(String section, String version) {
        rewriteRun(packageVersion(section + "/" + version + "/package.json", section, version));
    }

    @Test
    void upgradesAllFourDirectDependencySectionsTogether() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"swiper": "3.4.2"},
                  "devDependencies": {"swiper": "^6.8.4"},
                  "peerDependencies": {"swiper": "~8.4.7"},
                  "optionalDependencies": {"swiper": "=9.4.1"}
                }
                """,
                """
                {
                  "dependencies": {"swiper": "12.1.2"},
                  "devDependencies": {"swiper": "12.1.2"},
                  "peerDependencies": {"swiper": "12.1.2"},
                  "optionalDependencies": {"swiper": "12.1.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceManifestsWithoutChangingWorkspaceConfiguration() {
        rewriteRun(
                json(
                        """
                        {"name":"ui-workspace","private":true,"workspaces":["apps/*","packages/*"]}
                        """,
                        spec -> spec.path("package.json")
                ),
                packageVersion("apps/store/package.json", "dependencies", "^8.3.1"),
                packageVersion("packages/carousel/package.json", "peerDependencies", "~9.2.0")
        );
    }

    @ParameterizedTest(name = "leaves unlisted declaration {0} untouched")
    @ValueSource(strings = {
            "3.4.1", "3.4.3", "4.5.1", "5.4.5", "6.8.0", "6.8.2", "6.8.3", "6.8.5",
            "7.1.0", "7.2.1", "7.4.1", "8.3.0", "8.3.2", "8.4.6", "8.4.8", "9.0.0",
            "9.0.5", "9.1.1", "9.2.1", "9.3.2", "9.4.0", "9.4.2", "10.0.0", "11.2.10",
            "12.0.3", "12.1.0", "12.1.1", "12.1.2", "12.1.3", "12.2.0", "13.0.0", "14.0.6"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"swiper\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves ambiguous declaration {0} untouched")
    @ValueSource(strings = {
            "3.4.2-beta.1", "6.8.4+company.2", ">=6.8.4 <10", "7.x", "8.*", "^8.3.1 || ^9.4.1",
            "6.8.1 - 9.4.1", "latest", "next", "catalog:swiper", "${SWIPER_VERSION}", "{{swiperVersion}}"
    })
    void leavesPrereleaseCompoundBroadTagAndVariableDeclarationsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"swiper\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesProtocolsAliasesGitUrlsAndLocalReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"swiper": "workspace:^9.4.1"},
                  "devDependencies": {"swiper": "npm:@company/swiper@9.4.1"},
                  "peerDependencies": {"swiper": "git+ssh://git@github.com/nolimits4web/swiper.git#v9.4.1"},
                  "optionalDependencies": {"swiper": "file:../swiper-8.4.7"},
                  "resolutions": {"swiper": "https://registry.example/swiper-9.2.0.tgz"},
                  "pnpm": {"overrides": {"swiper": "link:../swiper"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsMetadataAndSimilarNamesUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"swiper": "9.4.1"},
                  "resolutions": {"swiper": "8.4.7"},
                  "pnpm": {"overrides": {"swiper": "7.2.0"}},
                  "peerDependenciesMeta": {"swiper": {"optional": true}},
                  "dependencies": {
                    "swiper-react": "9.4.1",
                    "vue-awesome-swiper": "8.4.7",
                    "react-id-swiper": "7.2.0",
                    "Swiper": "6.8.4",
                    "@types/swiper": "3.4.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNullObjectNumberBooleanAndArrayValuesUntouchedWithoutCrashing() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"swiper": null},
                  "devDependencies": {"swiper": {"version": "9.4.1"}},
                  "peerDependencies": {"swiper": 9},
                  "optionalDependencies": {"swiper": ["8.4.7"], "other": true}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesDependencySectionNullObjectsAndArraysUntouchedWithoutCrashing() {
        rewriteRun(
                json("{\"dependencies\":null}", spec -> spec.path("null/package.json")),
                json("{\"devDependencies\":[{\"swiper\":\"9.4.1\"}]}", spec -> spec.path("array/package.json")),
                json("{\"peerDependencies\":\"swiper@9.4.1\"}", spec -> spec.path("string/package.json")),
                json("{\"optionalDependencies\":true}", spec -> spec.path("boolean/package.json"))
        );
    }

    @Test
    void leavesPackageLockAndOrdinaryJsonUntouched() {
        rewriteRun(
                json(
                        """
                        {"packages":{"":{"dependencies":{"swiper":"9.4.1"}},"node_modules/swiper":{"version":"9.4.1"}}}
                        """,
                        spec -> spec.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"swiper\":\"9.4.1\"}}",
                        spec -> spec.path("fixtures/dependencies.json")
                ),
                json(
                        "{\"dependencies\":{\"swiper\":\"9.4.1\"}}",
                        spec -> spec.path("package.json.backup")
                )
        );
    }

    @Test
    void migratesGrevziSwiperSixReactImports() {
        // Reduced from grevzi/f697e307dd74cc383b0b9ebe3128224c at 7eb565742bf4fe1877e254c34d91598359a13ba2.
        // https://gist.github.com/grevzi/f697e307dd74cc383b0b9ebe3128224c/7eb565742bf4fe1877e254c34d91598359a13ba2
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import {Swiper, SwiperSlide} from "swiper/react";
                        import "swiper/swiper-bundle.min.css";
                        import SwiperCore, {
                          Scrollbar, EffectFade, Navigation, Pagination, Autoplay
                        } from 'swiper';
                        SwiperCore.use([Scrollbar, EffectFade, Navigation, Pagination, Autoplay]);
                        """,
                        """
                        import {Swiper, SwiperSlide} from "swiper/react";
                        import "swiper/css/bundle";
                        import SwiperCore from 'swiper';
                        import {
                          Scrollbar, EffectFade, Navigation, Pagination, Autoplay
                        } from 'swiper/modules';
                        SwiperCore.use([Scrollbar, EffectFade, Navigation, Pagination, Autoplay]);
                        """,
                        source -> source.path("src/Slider.jsx")
                )
        );
    }

    @Test
    void migratesCainmagiModuleImports() {
        // Reduced from cainmagi/581189cdf4673be2358eeef7f35feab5 at 5b8a38791fba05e5d672ef740507b3dc73a8fc20.
        // https://gist.github.com/cainmagi/581189cdf4673be2358eeef7f35feab5/5b8a38791fba05e5d672ef740507b3dc73a8fc20
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        "import SwiperCore, { Navigation, Pagination, A11y } from 'swiper';\n",
                        "import SwiperCore from 'swiper';\nimport { Navigation, Pagination, A11y } from 'swiper/modules';\n",
                        source -> source.path("src/SwiperCarousel.js")
                )
        );
    }

    @Test
    void migratesHelloKatonNamedModuleImports() {
        // Reduced from hellokaton/6576fc9844384b0da0a3311f9f7b65ce at 961d3b3195fb1ecb88860c587036878f016397a5.
        // https://gist.github.com/hellokaton/6576fc9844384b0da0a3311f9f7b65ce/961d3b3195fb1ecb88860c587036878f016397a5
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        "import { Autoplay, Pagination, Navigation } from \"swiper\";\n",
                        "import { Autoplay, Pagination, Navigation } from \"swiper/modules\";\n",
                        source -> source.path("src/App.tsx")
                )
        );
    }

    @Test
    void migratesChiilogModuleImports() {
        // Reduced from chiilog/a7d8b22ea128ae5ab6eff1f6cf045464 at 40d30712af6d96ad61ec1b811187d87df947c9cf.
        // https://gist.github.com/chiilog/a7d8b22ea128ae5ab6eff1f6cf045464/40d30712af6d96ad61ec1b811187d87df947c9cf
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        "import Swiper, { Navigation, Pagination, Autoplay } from 'swiper';\n",
                        "import Swiper from 'swiper';\nimport { Navigation, Pagination, Autoplay } from 'swiper/modules';\n",
                        source -> source.path("assets/swiper-control.js")
                )
        );
    }

    @Test
    void migratesWindiestAngularNewsMarkupAndSelector() {
        // Reduced from windiest/Angular-news at aec41cc0c2f4af2876507a22719b426e0935bdc9.
        // https://github.com/windiest/Angular-news/blob/aec41cc0c2f4af2876507a22719b426e0935bdc9/webroot/news/directive/swiper.html
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        <div class="news swiper-container featured">
                          <div class="swiper-wrapper"></div>
                        </div>
                        <style>.swiper-container { width: 100%; }</style>
                        """,
                        """
                        <div class="news swiper featured">
                          <div class="swiper-wrapper"></div>
                        </div>
                        <style>.swiper { width: 100%; }</style>
                        """,
                        source -> source.path("webroot/news/directive/swiper.html")
                )
        );
    }

    @Test
    void migratesNathobsonLegacySelectorWithoutGuessingLazyOrLoopOptions() {
        // Reduced from nathobson/5770850df9485542ee93a583ca23248e at ba7b9b65b55b57acaefa890625684d61ad3db8eb.
        // https://gist.github.com/nathobson/5770850df9485542ee93a583ca23248e/ba7b9b65b55b57acaefa890625684d61ad3db8eb
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        new Swiper('.buying-featured .swiper-container', {
                          loopedSlides: 2,
                          lazy: { loadPrevNext: true }
                        });
                        """,
                        """
                        new Swiper('.buying-featured .swiper', {
                          loopedSlides: 2,
                          lazy: { loadPrevNext: true }
                        });
                        """,
                        source -> source.path("assets/featured.js")
                )
        );
    }

    @ParameterizedTest(name = "migrates deterministic entry point {0}")
    @MethodSource("deterministicEntryPointCases")
    void migratesDeterministicEntryPoints(String path, String before, String after) {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(before, after, source -> source.path(path))
        );
    }

    @Test
    void migratesWatchVisibleSlidesOnlyAsAnOptionKey() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        const options = { watchVisibleSlides: true, watchSlidesProgress: false };
                        const watchVisibleSlides = options.watchVisibleSlides;
                        """,
                        """
                        const options = { watchSlidesProgress: true, watchSlidesProgress: false };
                        const watchVisibleSlides = options.watchVisibleSlides;
                        """,
                        source -> source.path("src/options.ts")
                )
        );
    }

    @Test
    void preservesPublishedTargetEntrypointsFrameworkWrappersTypesAndMixedImports() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import Swiper from 'swiper';
                        import SwiperBundle from 'swiper/bundle';
                        import { Swiper, SwiperSlide } from 'swiper/react';
                        import { Swiper as VueSwiper } from 'swiper/vue';
                        import { Navigation, Pagination } from 'swiper/modules';
                        import type { SwiperOptions } from 'swiper/types';
                        import { SwiperOptions, Navigation } from 'swiper';
                        import 'swiper/css';
                        import 'swiper/css/bundle';
                        """,
                        source -> source.path("src/already-modern.ts")
                )
        );
    }

    @Test
    void preservesSwiperElementTagModifierClassesAndSimilarNames() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        <swiper-container navigation="true">
                          <swiper-slide>One</swiper-slide>
                        </swiper-container>
                        <div class="swiper-container-horizontal custom-swiper-container"></div>
                        <style>
                        swiper-container { display: block; }
                        .swiper-container-horizontal { direction: ltr; }
                        .custom-swiper-container { contain: layout; }
                        </style>
                        """,
                        source -> source.path("src/element.html")
                )
        );
    }

    @Test
    void leavesUnsupportedExtensionsAndConfigurationFilesUntouched() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text("import Swiper, { Navigation } from 'swiper';\n", source -> source.path("README.md")),
                text("swiper/swiper-bundle.min.css\n", source -> source.path("config/swiper.txt")),
                json("{\"entry\":\"swiper/swiper-bundle.min.css\"}", source -> source.path("config.json"))
        );
    }

    @Test
    void findsHighRiskConstructsAsReviewMarkers() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RISK_RECIPE)),
                text(
                        "import { SwiperModule } from 'swiper/angular';\n",
                        "import { SwiperModule } from '~~>swiper/angular';\n",
                        source -> source.path("src/gallery.ts")
                )
        );
    }

    @Test
    void compositeUpgradesDependencyAndSafeSourceTogether() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPOSITE_RECIPE)),
                json(
                        "{\"dependencies\":{\"swiper\":\"9.4.1\"}}",
                        "{\"dependencies\":{\"swiper\":\"12.1.2\"}}",
                        source -> source.path("package.json")
                ),
                text(
                        "import { Navigation } from 'swiper';\nimport 'swiper/swiper-bundle.css';\n",
                        "import { Navigation } from 'swiper/modules';\nimport 'swiper/css/bundle';\n",
                        source -> source.path("src/gallery.ts")
                )
        );
    }

    @Test
    void discoversAllRecipesWithExpectedMetadataAndValidConfiguration() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe source = environment.activateRecipes(SOURCE_RECIPE);
        Recipe risks = environment.activateRecipes(RISK_RECIPE);
        Recipe composite = environment.activateRecipes(COMPOSITE_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> SOURCE_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> RISK_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> COMPOSITE_RECIPE.equals(recipe.getName())));
        assertEquals("Upgrade the Swiper dependency to 12.1.2", dependency.getDisplayName());
        assertEquals("Migrate deterministic Swiper source constructs to version 12", source.getDisplayName());
        assertEquals("Find Swiper 12 migration risks requiring manual review", risks.getDisplayName());
        assertEquals("Migrate Swiper applications to 12.1.2", composite.getDisplayName());
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(source.validate().isValid(), () -> source.validate().failures().toString());
        assertTrue(risks.validate().isValid(), () -> risks.validate().failures().toString());
        assertTrue(composite.validate().isValid(), () -> composite.validate().failures().toString());
    }

    private static Stream<Arguments> selectedVersionAndSection() {
        String[] sections = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"};
        String[] versions = {"3.4.2", "6.8.1", "6.8.4", "7.2.0", "8.3.1", "8.4.7", "9.1.0", "9.2.0", "9.4.1"};
        return Stream.of(sections).flatMap(section ->
                Stream.of(versions).map(version -> Arguments.of(section, version))
        );
    }

    private static Stream<Arguments> deterministicEntryPointCases() {
        return Stream.of(
                Arguments.of("src/v3.js", "import Swiper from 'swiper/dist/js/swiper.min.js';\n", "import Swiper from 'swiper';\n"),
                Arguments.of("src/v4.js", "import Swiper from \"swiper/dist/js/swiper.js\";\n", "import Swiper from \"swiper\";\n"),
                Arguments.of("src/v6.ts", "import Swiper from 'swiper/swiper.esm.js';\n", "import Swiper from 'swiper';\n"),
                Arguments.of("src/v7.ts", "import Swiper from 'swiper/swiper.min.js';\n", "import Swiper from 'swiper';\n"),
                Arguments.of("src/bundle.ts", "import Swiper from 'swiper/swiper-bundle.esm.min.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("src/v3.scss", "@import 'swiper/dist/css/swiper.min.css';\n", "@import 'swiper/css';\n"),
                Arguments.of("src/v5.scss", "@import \"swiper/css/swiper.css\";\n", "@import \"swiper/css\";\n"),
                Arguments.of("src/v6.ts", "import 'swiper/swiper.css';\n", "import 'swiper/css';\n"),
                Arguments.of("src/v6.ts", "import 'swiper/swiper-bundle.css';\n", "import 'swiper/css/bundle';\n"),
                Arguments.of("src/v6.ts", "import 'swiper/swiper-bundle.min.css';\n", "import 'swiper/css/bundle';\n"),
                Arguments.of("src/navigation.ts", "import 'swiper/components/navigation/navigation.min.css';\n", "import 'swiper/css/navigation';\n"),
                Arguments.of("src/pagination.ts", "import \"swiper/components/pagination/pagination.css\";\n", "import \"swiper/css/pagination\";\n"),
                Arguments.of("src/effect.ts", "import 'swiper/components/effect-fade/effect-fade.min.css';\n", "import 'swiper/css/effect-fade';\n"),
                Arguments.of("src/zoom.ts", "import 'swiper/components/zoom/zoom.css';\n", "import 'swiper/css/zoom';\n"),
                Arguments.of("styles/main.scss", "@use 'swiper/scss';\n", "@use 'swiper/css';\n"),
                Arguments.of("styles/main.scss", "@import 'swiper/scss/navigation';\n", "@import 'swiper/css/navigation';\n"),
                Arguments.of("styles/main.less", "@import 'swiper/less/bundle';\n", "@import 'swiper/css/bundle';\n"),
                Arguments.of("styles/main.less", "@import \"swiper/less/pagination\";\n", "@import \"swiper/css/pagination\";\n"),
                Arguments.of("src/gallery.ts", "const el = document.querySelector('.swiper-container');\n", "const el = document.querySelector('.swiper');\n"),
                Arguments.of("src/Gallery.tsx", "<div className=\"hero swiper-container\" />\n", "<div className=\"hero swiper\" />\n"),
                Arguments.of("src/Gallery.vue", "<div class='swiper-container gallery'></div>\n", "<div class='swiper gallery'></div>\n")
        );
    }

    private static SourceSpecs packageVersion(String path, String section, String version) {
        return json(
                "{\"" + section + "\":{\"swiper\":\"" + version + "\"}}",
                "{\"" + section + "\":{\"swiper\":\"12.1.2\"}}",
                spec -> spec.path(path)
        );
    }

    private static Recipe sourceRecipe() {
        return environment().activateRecipes(SOURCE_RECIPE);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.swiper")
                .scanYamlResources()
                .build();
    }
}
