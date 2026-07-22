package com.huawei.clouds.openrewrite.angulargridster2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeAngularGridster2Test implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.angulargridster2.UpgradeAngularGridster2To20_2_4";
    private static final String STANDALONE_RECIPE =
            "com.huawei.clouds.openrewrite.angulargridster2.MigrateStandaloneGridsterModuleToComponents";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.angulargridster2.MigrateDeterministicAngularGridster2SourceTo20";
    private static final String RISK_RECIPE =
            "com.huawei.clouds.openrewrite.angulargridster2.FindManualAngularGridster2To20MigrationRisks";
    private static final String COMPOSITE_RECIPE =
            "com.huawei.clouds.openrewrite.angulargridster2.MigrateAngularGridster2To20_2_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @Test
    void upgradesHyperIotAngularTwelveDashboardAndPreservesSource() {
        // Reduced from HyperIoT-Labs/HyperIoT-UI at da8e6ebd:
        // https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/package.json
        // https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "hyperiot",
                          "dependencies": {
                            "@angular/core": "12.2.16",
                            "angular-gridster2": "~12.1.1",
                            "rxjs": "7.5.5",
                            "zone.js": "0.11.8"
                          }
                        }
                        """,
                        """
                        {
                          "name": "hyperiot",
                          "dependencies": {
                            "@angular/core": "12.2.16",
                            "angular-gridster2": "20.2.4",
                            "rxjs": "7.5.5",
                            "zone.js": "0.11.8"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterItem } from 'angular-gridster2';
                        export class WidgetsLayoutComponent {
                          options: GridsterConfig = { draggable: { enabled: true } };
                          dashboard: Array<GridsterItem> = [];
                        }
                        """,
                        spec -> spec.path("projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts")
                )
        );
    }

    @Test
    void upgradesErniAngularThirteenStarter() {
        // Reduced from ERNI-Academy/starterkit-angular-and-dotnet-api at 72188b3c:
        // https://github.com/ERNI-Academy/starterkit-angular-and-dotnet-api/blob/72188b3cb657b4a7d318f4e29ae751995d5efe5b/src/App.UI/package.json
        rewriteRun(json(
                """
                {
                  "name": "appui",
                  "dependencies": {
                    "@angular/core": "~13.2.1",
                    "angular-gridster2": "^13.3.0",
                    "rxjs": "~6.6.6"
                  }
                }
                """,
                """
                {
                  "name": "appui",
                  "dependencies": {
                    "@angular/core": "~13.2.1",
                    "angular-gridster2": "20.2.4",
                    "rxjs": "~6.6.6"
                  }
                }
                """,
                spec -> spec.path("src/App.UI/package.json")
        ));
    }

    @Test
    void upgradesFischertechnikAngularThirteenWorkspaceAndPreservesModuleUse() {
        // Reduced from fischertechnik/Agile-Production-Simulation-24V-Dev at 24568073:
        // https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/package.json
        // https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "ff-frontend",
                          "dependencies": {
                            "@angular/core": "13.4.0",
                            "angular-gridster2": "^13.3.2",
                            "rxjs": "~7.5.0"
                          }
                        }
                        """,
                        """
                        {
                          "name": "ff-frontend",
                          "dependencies": {
                            "@angular/core": "13.4.0",
                            "angular-gridster2": "20.2.4",
                            "rxjs": "~7.5.0"
                          }
                        }
                        """,
                        spec -> spec.path("frontend/package.json")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterItem } from 'angular-gridster2';
                        export class FactoryLayoutComponent {
                          options: GridsterConfig;
                          dashboard: GridsterItem[];
                          changedOptions() { this.options.api?.optionsChanged?.(); }
                        }
                        """,
                        spec -> spec.path("frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts")
                )
        );
    }

    @Test
    void upgradesSostradesAngularSixteenDashboardAndPreservesCompanions() {
        // Reduced from os-climate/sostrades-webgui at fe148be3:
        // https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/package.json
        // https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/src/app/modules/dashboard/dashboard.component.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "sos-trades-gui",
                          "dependencies": {
                            "@angular/core": "16.2.12",
                            "@angular/material": "16.2.12",
                            "angular-gridster2": "^16.0.0",
                            "rxjs": "7.8.1"
                          }
                        }
                        """,
                        """
                        {
                          "name": "sos-trades-gui",
                          "dependencies": {
                            "@angular/core": "16.2.12",
                            "@angular/material": "16.2.12",
                            "angular-gridster2": "20.2.4",
                            "rxjs": "7.8.1"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterItem } from 'angular-gridster2';
                        export class DashboardComponent {
                          options: GridsterConfig = { mobileBreakpoint: 640 };
                          dashboard: GridsterItem[] = [];
                        }
                        """,
                        spec -> spec.path("src/app/modules/dashboard/dashboard.component.ts")
                )
        );
    }

    @ParameterizedTest(name = "upgrades spreadsheet angular-gridster2 version {0}")
    @ValueSource(strings = {"12.1.1", "13.3.0", "13.3.2", "16.0.0"})
    void upgradesEverySpreadsheetVersion(String version) {
        rewriteRun(packageVersion("package.json", version));
    }

    @ParameterizedTest(name = "upgrades safe selected declaration {0}")
    @ValueSource(strings = {
            "^12.1.1", "~12.1.1", "=12.1.1", "v12.1.1", "^v12.1.1",
            "^13.3.0", "~13.3.0", "=13.3.0", "v13.3.0", "^v13.3.0",
            "^13.3.2", "~13.3.2", "=13.3.2", "v13.3.2", "^v13.3.2",
            "^16.0.0", "~16.0.0", "=16.0.0", "v16.0.0", "^v16.0.0"
    })
    void upgradesSupportedRegistryDeclarations(String declaration) {
        rewriteRun(packageVersion("package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"angular-gridster2": "12.1.1"},
                  "devDependencies": {"angular-gridster2": "^13.3.0"},
                  "peerDependencies": {"angular-gridster2": "~13.3.2"},
                  "optionalDependencies": {"angular-gridster2": "=16.0.0"}
                }
                """,
                """
                {
                  "dependencies": {"angular-gridster2": "20.2.4"},
                  "devDependencies": {"angular-gridster2": "20.2.4"},
                  "peerDependencies": {"angular-gridster2": "20.2.4"},
                  "optionalDependencies": {"angular-gridster2": "20.2.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildrenWithoutChangingWorkspaceConfiguration() {
        rewriteRun(
                json(
                        "{\"private\":true,\"workspaces\":[\"apps/*\",\"packages/*\"]}",
                        spec -> spec.path("package.json")
                ),
                packageVersion("apps/dashboard/package.json", "^13.3.2"),
                packageVersion("packages/widgets/package.json", "16.0.0")
        );
    }

    @Test
    void preservesAngularRxjsMaterialAndDifferentGridPackages() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/common": "16.2.12",
                    "@angular/core": "16.2.12",
                    "@angular/material": "16.2.12",
                    "angular-gridster2": "16.0.0",
                    "gridstack": "7.1.1",
                    "ngx-gridster": "16.0.0",
                    "rxjs": "7.8.1",
                    "zone.js": "0.13.3"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/common": "16.2.12",
                    "@angular/core": "16.2.12",
                    "@angular/material": "16.2.12",
                    "angular-gridster2": "20.2.4",
                    "gridstack": "7.1.1",
                    "ngx-gridster": "16.0.0",
                    "rxjs": "7.8.1",
                    "zone.js": "0.13.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unlisted version {0} untouched")
    @ValueSource(strings = {
            "12.1.0", "12.1.2", "13.2.0", "13.3.1", "13.3.3", "14.1.0",
            "15.0.4", "16.0.1", "16.2.0", "17.0.0", "18.0.1", "19.0.0"
    })
    void leavesUnlistedVersionsUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves target or later declaration {0} untouched")
    @ValueSource(strings = {"20.2.4", "^20.2.4", "20.2.5", "21.0.0", "22.0.0-next.1"})
    void leavesTargetAndNewerVersionsUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "rejects ambiguous declaration {0}")
    @ValueSource(strings = {
            ">=12.1.1 <13", "13.x", "12.1.1 - 16.0.0", "13.3.0 || ^16.0.0",
            "13.3.2-rc.1", "16.0.0+company.2", "latest", "next", "*", "${GRIDSTER_VERSION}"
    })
    void leavesRangesPrereleasesTagsAndVariablesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "rejects non-registry reference {0}")
    @ValueSource(strings = {
            "workspace:^16.0.0", "catalog:angular-gridster2", "npm:@example/gridster@16.0.0",
            "file:../gridster", "link:../gridster", "portal:../gridster",
            "github:tiberiuzuld/angular-gridster2#v16.0.0",
            "git+ssh://git@github.com/tiberiuzuld/angular-gridster2.git#v16.0.0",
            "https://registry.example/angular-gridster2-16.0.0.tgz"
    })
    void leavesProtocolsAliasesAndExternalReferencesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsAndPeerMetadataUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"angular-gridster2": "12.1.1"},
                  "resolutions": {"angular-gridster2": "13.3.0"},
                  "pnpm": {"overrides": {"angular-gridster2": "13.3.2"}},
                  "peerDependenciesMeta": {"angular-gridster2": {"optional": true}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringDependencyValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"angular-gridster2": {"version": "16.0.0"}},
                  "devDependencies": {"angular-gridster2": 16},
                  "peerDependencies": {"angular-gridster2": true},
                  "optionalDependencies": {"angular-gridster2": null, "fixture": ["13.3.2"]}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesAngularGridster2ArrayValuesAndArrayShapedSectionsUntouched() {
        rewriteRun(
                json(
                        """
                        {"dependencies":{"angular-gridster2":["16.0.0"]}}
                        """,
                        spec -> spec.path("array-value/package.json")
                ),
                json(
                        """
                        {"devDependencies":[{"angular-gridster2":"13.3.2"}]}
                        """,
                        spec -> spec.path("array-section/package.json")
                )
        );
    }

    @Test
    void doesNotModifyLockfilesOtherJsonOrSimilarNames() {
        rewriteRun(
                json(
                        """
                        {"packages":{"":{"dependencies":{"angular-gridster2":"16.0.0"}},"node_modules/angular-gridster2":{"version":"16.0.0"}}}
                        """,
                        spec -> spec.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"angular-gridster2\":\"16.0.0\"}}",
                        spec -> spec.path("fixtures/dependencies.json")
                ),
                json(
                        """
                        {"dependencies":{"angular-gridster":"16.0.0","angular-gridster2-plugin":"16.0.0","Angular-Gridster2":"16.0.0"}}
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void leavesTemplatesStylesAndBuildConfigurationUntouched() {
        rewriteRun(
                text(
                        """
                        <gridster [options]="options">
                          <gridster-item *ngFor="let item of dashboard" [item]="item"></gridster-item>
                        </gridster>
                        """,
                        spec -> spec.path("src/app/dashboard.component.html")
                ),
                text(
                        """
                        gridster { display: flex; height: 100%; }
                        gridster-item { overflow: hidden; }
                        """,
                        spec -> spec.path("src/app/dashboard.component.scss")
                ),
                json(
                        "{\"projects\":{\"app\":{\"architect\":{\"build\":{\"options\":{\"styles\":[]}}}}}}",
                        spec -> spec.path("angular.json")
                )
        );
    }

    @Test
    void migratesHyperIotStrictOptionsChangedCall() {
        // Reduced from HyperIoT-Labs/HyperIoT-UI at da8e6ebd16aba40fc1996e6da706b2954c3b12f3:
        // https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        export class WidgetsDashboardLayoutComponent {
                          options: GridsterConfig;
                          refreshLayout() {
                            this.options.api.optionsChanged();
                          }
                        }
                        """,
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        export class WidgetsDashboardLayoutComponent {
                          options: GridsterConfig;
                          refreshLayout() {
                            this.options.api?.optionsChanged?.();
                          }
                        }
                        """,
                        source -> source.path("projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts")
                )
        );
    }

    @Test
    void migratesSostradesGuardedOptionsChangedCall() {
        // Reduced from os-climate/sostrades-webgui at fe148be3c158cd681fb7bce739575f9d24f2c2d2:
        // https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/src/app/modules/dashboard/dashboard.component.ts
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { GridsterConfig } from "angular-gridster2";
                        export class DashboardComponent {
                          options: GridsterConfig;
                          ngAfterViewInit() {
                            if (this.options.api) this.options.api.optionsChanged();
                          }
                        }
                        """,
                        """
                        import { GridsterConfig } from "angular-gridster2";
                        export class DashboardComponent {
                          options: GridsterConfig;
                          ngAfterViewInit() {
                            this.options.api?.optionsChanged?.();
                          }
                        }
                        """,
                        source -> source.path("src/app/modules/dashboard/dashboard.component.ts")
                )
        );
    }

    @Test
    void migratesHyperIotSimpleGridsterItemLoopToBuiltInControlFlow() {
        // Reduced from HyperIoT-Labs/HyperIoT-UI at da8e6ebd16aba40fc1996e6da706b2954c3b12f3:
        // https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.html
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        <gridster [options]="options">
                          <gridster-item [item]="item" *ngFor="let item of dashboard">
                            <hyperiot-dynamic-widget [widget]="item"></hyperiot-dynamic-widget>
                          </gridster-item>
                        </gridster>
                        """,
                        """
                        <gridster [options]="options">
                          @for (item of dashboard; track item) {
                            <gridster-item [item]="item">
                            <hyperiot-dynamic-widget [widget]="item"></hyperiot-dynamic-widget>
                            </gridster-item>
                          }
                        </gridster>
                        """,
                        source -> source.path("projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.html")
                )
        );
    }

    @Test
    void migratesExactStandaloneModuleUseToPublishedStandaloneComponents() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { Component } from '@angular/core';
                        import { GridsterModule } from 'angular-gridster2';

                        @Component({
                          standalone: true,
                          imports: [CommonModule, GridsterModule],
                          templateUrl: './dashboard.html'
                        })
                        export class DashboardComponent {}
                        """,
                        """
                        import { Component } from '@angular/core';
                        import { GridsterComponent, GridsterItemComponent } from 'angular-gridster2';

                        @Component({
                          standalone: true,
                          imports: [CommonModule, GridsterComponent, GridsterItemComponent],
                          templateUrl: './dashboard.html'
                        })
                        export class DashboardComponent {}
                        """,
                        source -> source.path("src/app/dashboard.component.ts")
                )
        );
    }

    @ParameterizedTest(name = "normalizes published symbol deep import {0}")
    @ValueSource(strings = {
            "gridster.component", "gridsterItem.component", "gridsterItem.interface",
            "gridster.interface", "gridsterConfig.interface", "gridsterConfig.constant",
            "gridster.module", "gridsterPush.service", "gridsterPushResize.service", "gridsterSwap.service"
    })
    void normalizesKnownPublicDeepImports(String internalFile) {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        "import { PublicSymbol } from 'angular-gridster2/lib/" + internalFile + "';\n",
                        "import { PublicSymbol } from 'angular-gridster2';\n",
                        source -> source.path("src/app/deep-import.ts")
                )
        );
    }

    @Test
    void preservesNgModuleApplicationsComplexStandaloneImportsAndComplexLoops() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { GridsterModule } from 'angular-gridster2';
                        @NgModule({ imports: [GridsterModule] })
                        export class DashboardModule {}
                        """,
                        source -> source.path("src/app/dashboard.module.ts")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterModule } from 'angular-gridster2';
                        @Component({ standalone: true, imports: [GridsterModule] })
                        export class DashboardComponent {}
                        """,
                        source -> source.path("src/app/complex-standalone.component.ts")
                ),
                text(
                        """
                        <gridster [options]="options">
                          <gridster-item [item]="item" *ngFor="let item of gridContents$ | async; trackBy: trackItem">
                          </gridster-item>
                        </gridster>
                        """,
                        source -> source.path("src/app/dashboard.component.html")
                ),
                text(
                        "other.options.api.optionsChanged();\n",
                        source -> source.path("src/app/unrelated.ts")
                ),
                text(
                        "this.options.api.optionsChanged();\n",
                        source -> source.path("src/app/unrelated-this.ts")
                )
        );
    }

    @Test
    void marksFischertechnikSsrBrowserGlobalRisk() {
        // Reduced from fischertechnik/Agile-Production-Simulation-24V-Dev at 24568073:
        // https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { GridsterComponent } from 'angular-gridster2';
                        if (window.ResizeObserver) {
                          this.resizeObserver = new ResizeObserver(() => {});
                        }
                        """,
                        """
                        import { GridsterComponent } from 'angular-gridster2';
                        if (~~>window.~~>ResizeObserver) {
                          this.resizeObserver = new ~~>ResizeObserver(() => {});
                        }
                        """,
                        source -> source.path("frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts")
                )
        );
    }

    @Test
    void marksHyperIotResponsiveAndLayoutRisks() {
        // Reduced from HyperIoT-Labs/HyperIoT-UI at da8e6ebd16aba40fc1996e6da706b2954c3b12f3.
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        const availableWidth = document.documentElement.clientWidth;
                        const options: GridsterConfig = { mobileBreakpoint: 0, compactType: 'compactUp' };
                        """,
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        const availableWidth = ~~>document.documentElement.clientWidth;
                        const options: GridsterConfig = { ~~>mobileBreakpoint: 0, ~~>compactType: 'compactUp' };
                        """,
                        source -> source.path("projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts")
                )
        );
    }

    @Test
    void marksSostradesDragConfigurationRisk() {
        // Reduced from os-climate/sostrades-webgui at fe148be3c158cd681fb7bce739575f9d24f2c2d2.
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        const options: GridsterConfig = {
                          draggable: { enabled: true, dragHandleClass: 'drag-handle' },
                          resizable: { enabled: true }
                        };
                        """,
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        const options: GridsterConfig = {
                          ~~>draggable: { enabled: true, ~~>dragHandleClass: 'drag-handle' },
                          ~~>resizable: { enabled: true }
                        };
                        """,
                        source -> source.path("src/app/modules/dashboard/dashboard.component.ts")
                )
        );
    }

    @Test
    void marksComplexGridsterLoopForManualControlFlowMigration() {
        // Reduced from fischertechnik/Agile-Production-Simulation-24V-Dev at 24568073.
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        <gridster [options]="options">
                          <gridster-item [item]="item" *ngFor="let item of gridContents$ | async">
                          </gridster-item>
                        </gridster>
                        """,
                        """
                        <gridster [options]="options">
                          <gridster-item [item]="item" ~~>*ngFor="let item of gridContents$ | async">
                          </gridster-item>
                        </gridster>
                        """,
                        source -> source.path("frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.html")
                )
        );
    }

    @Test
    void marksFischertechnikLayoutPositionApiForBehaviorReview() {
        // Reduced from fischertechnik/Agile-Production-Simulation-24V-Dev at 24568073:
        // https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { GridsterComponent } from 'angular-gridster2';
                        item.x = this.gridster.getFirstPossiblePosition(item).x;
                        """,
                        """
                        import { GridsterComponent } from 'angular-gridster2';
                        item.x = this.gridster.~~>getFirstPossiblePosition(item).x;
                        """,
                        source -> source.path("frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts")
                )
        );
    }

    @Test
    void marksUnknownDeepImportAndRemovedInternalLayoutFunction() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { GridsterDraggable } from 'angular-gridster2/lib/gridsterDraggable.service';
                        this.gridster.calculateLayoutDebounce();
                        """,
                        """
                        import { GridsterDraggable } ~~>from 'angular-gridster2/lib/gridsterDraggable.service';
                        this.gridster.~~>calculateLayoutDebounce();
                        """,
                        source -> source.path("src/app/internal-gridster.ts")
                )
        );
    }

    @Test
    void compositeUpgradesDependencyMigratesSafeSourceAndMarksRisks() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPOSITE_RECIPE)),
                json(
                        "{\"dependencies\":{\"angular-gridster2\":\"16.0.0\"}}",
                        "{\"dependencies\":{\"angular-gridster2\":\"20.2.4\"}}",
                        source -> source.path("package.json")
                ),
                text(
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        this.options.api.optionsChanged();
                        const options: GridsterConfig = { mobileBreakpoint: 640 };
                        """,
                        """
                        import { GridsterConfig } from 'angular-gridster2';
                        this.options.api?.optionsChanged?.();
                        const options: GridsterConfig = { ~~>mobileBreakpoint: 640 };
                        """,
                        source -> source.path("src/app/dashboard.component.ts")
                )
        );
    }

    @Test
    void discoversAndValidatesRecipes() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe standalone = environment.activateRecipes(STANDALONE_RECIPE);
        Recipe source = environment.activateRecipes(SOURCE_RECIPE);
        Recipe risks = environment.activateRecipes(RISK_RECIPE);
        Recipe composite = environment.activateRecipes(COMPOSITE_RECIPE);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> DEPENDENCY_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> SOURCE_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RISK_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> COMPOSITE_RECIPE.equals(candidate.getName())));
        assertEquals("Upgrade angular-gridster2 to 20.2.4", dependency.getDisplayName());
        assertEquals("Use angular-gridster2 standalone components in standalone Angular components", standalone.getDisplayName());
        assertEquals("Migrate deterministic angular-gridster2 source constructs to version 20", source.getDisplayName());
        assertEquals("Find angular-gridster2 20 migration risks requiring manual review", risks.getDisplayName());
        assertEquals("Migrate angular-gridster2 applications to 20.2.4", composite.getDisplayName());
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(standalone.validate().isValid(), () -> standalone.validate().failures().toString());
        assertTrue(source.validate().isValid(), () -> source.validate().failures().toString());
        assertTrue(risks.validate().isValid(), () -> risks.validate().failures().toString());
        assertTrue(composite.validate().isValid(), () -> composite.validate().failures().toString());
    }

    private static SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + version + "\"}}",
                "{\"dependencies\":{\"angular-gridster2\":\"20.2.4\"}}",
                spec -> spec.path(path)
        );
    }

    private static Recipe sourceRecipe() {
        return environment().activateRecipes(SOURCE_RECIPE);
    }

    private static Recipe riskRecipe() {
        return environment().activateRecipes(RISK_RECIPE);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angulargridster2")
                .scanYamlResources()
                .build();
    }
}
