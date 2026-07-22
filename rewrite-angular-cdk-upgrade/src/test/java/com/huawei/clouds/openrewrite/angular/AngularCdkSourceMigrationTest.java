package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class AngularCdkSourceMigrationTest implements RewriteTest {
    private static final String SOURCE = "com.huawei.clouds.openrewrite.angular.MigrateDeterministicAngularCdkTo20";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.angular.AuditAngularCdk20Source";

    @AfterAll
    static void stopRpc() { JavaScriptRewriteRpc.shutdownCurrent(); }

    @ParameterizedTest(name = "renames aliased {1} import")
    @MethodSource("renamedImports")
    void renamesLegacyImportPropertyWhilePreservingLocalAlias(String module, String oldName, String replacement) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("import { " + oldName + " as Owned } from '" + module + "';\nconst value: Owned | null = null;\n",
                        "import { " + replacement + " as Owned } from '" + module + "';\nconst value: Owned | null = null;\n",
                        source -> source.path("src/alias.ts")));
    }

    static Stream<Arguments> renamedImports() {
        return Stream.of(
                Arguments.of("@angular/cdk/portal", "DomPortalHost", "DomPortalOutlet"),
                Arguments.of("@angular/cdk/portal", "PortalHost", "PortalOutlet"),
                Arguments.of("@angular/cdk/portal", "BasePortalHost", "BasePortalOutlet"),
                Arguments.of("@angular/cdk/overlay", "ConnectedPositionStrategy", "FlexibleConnectedPositionStrategy"),
                Arguments.of("@angular/cdk/clipboard", "CKD_COPY_TO_CLIPBOARD_CONFIG", "CDK_COPY_TO_CLIPBOARD_CONFIG")
        );
    }

    @Test
    void leavesUnaliasedAndUnrelatedSameNamedImportsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("import { DomPortalHost } from '@angular/cdk/portal';\n", source -> source.path("src/unaliased.ts")),
                typescript("import { DomPortalHost as Host } from '@company/portal';\n", source -> source.path("src/unrelated.ts")),
                typescript("import * as portal from '@angular/cdk/portal';\n", source -> source.path("src/namespace.ts")));
    }

    @Test
    void renamesConnectedToOnTypedOverlayPositionBuilder() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { OverlayPositionBuilder } from '@angular/cdk/overlay';\n" +
                        "function place(builder: OverlayPositionBuilder, origin: unknown) { return builder.connectedTo(origin); }\n",
                        "import { OverlayPositionBuilder } from '@angular/cdk/overlay';\n" +
                        "function place(builder: OverlayPositionBuilder, origin: unknown) { return builder.flexibleConnectedTo(origin); }\n",
                        source -> source.path("src/overlay.ts")));
    }

    @Test
    void renamesConnectedToForAliasedInjectedPositionBuilder() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { OverlayPositionBuilder as Builder } from '@angular/cdk/overlay';\n" +
                        "const positions = inject(Builder);\npositions.connectedTo(origin);\n",
                        "import { OverlayPositionBuilder as Builder } from '@angular/cdk/overlay';\n" +
                        "const positions = inject(Builder);\npositions.flexibleConnectedTo(origin);\n",
                        source -> source.path("src/inject.ts")));
    }

    @Test
    void leavesSameNamedLocalConnectedToUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("const builder = localBuilder();\nbuilder.connectedTo(origin);\n",
                        source -> source.path("src/local.ts")));
    }

    @Test
    void removesOnlyDirectTypedDialogConfigResolver() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { DialogConfig } from '@angular/cdk/dialog';\n" +
                        "const config: DialogConfig = { componentFactoryResolver: resolver, width: '20rem' };\n",
                        "import { DialogConfig } from '@angular/cdk/dialog';\n" +
                        "const config: DialogConfig = { width: '20rem' };\n",
                        source -> source.path("src/dialog.ts")));
    }

    @Test
    void leavesUntypedNestedAndSameNamedDialogPropertiesUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("const config = { componentFactoryResolver: resolver };\n",
                        source -> source.path("src/local-dialog.ts")),
                typescript("import { DialogConfig } from '@company/dialog';\nconst config: DialogConfig = { componentFactoryResolver: resolver };\n",
                        source -> source.path("src/company-dialog.ts")));
    }

    @Test
    void marksRealSupernovaOldPortalConstructors() {
        // superannotateai/supernova, fixed commit 95876804c037b73306583dedf330c70f6bf199a7.
        assertMarkers(
                "import { ComponentPortal, DomPortalOutlet } from '@angular/cdk/portal';\n" +
                "const outlet = new DomPortalOutlet(host, resolver, appRef, injector, document);\n" +
                "const portal = new ComponentPortal(MessageComponent);\n",
                "projects/supernova/src/lib/components/message/message.ts",
                "Portal constructors changed in CDK 20");
    }

    @Test
    void marksRealNgDocModernOverlayBoundaryWithoutRewritingIt() {
        // ng-doc/ng-doc, fixed commit b595004d8925b5c93ae56f82a6439cd10e5de0cb.
        assertMarkers(
                "import { FlexibleConnectedPositionStrategy, Overlay } from '@angular/cdk/overlay';\n" +
                "const strategy: FlexibleConnectedPositionStrategy = overlay.position().flexibleConnectedTo(origin);\n",
                "libs/ui-kit/services/overlay/overlay.service.ts",
                "CDK major upgrades can change DOM");
    }

    @Test
    void marksRealNgZorroTemplatePortalBoundary() {
        // NG-ZORRO/ng-zorro-antd, fixed commit 7071edd3f72d3384ec73a329fd0d9dce3af67fc5.
        assertMarkers(
                "import { Overlay, OverlayRef } from '@angular/cdk/overlay';\n" +
                "import { TemplatePortal } from '@angular/cdk/portal';\n",
                "components/dropdown/context-menu.service.ts",
                "CDK major upgrades can change DOM");
    }

    @Test
    void ignoresRealWeaveFrameworkUnrelatedConnectedPositionStrategy() {
        // weave-framework/weave, fixed commit c00c01578efda9cc08d49b258b055fe339844898.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("export class ConnectedPositionStrategy { connectedTo(origin: unknown) { return origin; } }\n",
                        source -> source.path("packages/ui/src/cdk/positioning.ts")));
    }

    @ParameterizedTest(name = "marks removed API {1}")
    @MethodSource("removedApis")
    void marksRemovedAndChangedImportedApis(String module, String api, String message) {
        assertMarkers("import { " + api + " } from '" + module + "';\n", "src/risk.ts", message);
    }

    static Stream<Arguments> removedApis() {
        return Stream.of(
                Arguments.of("@angular/cdk/portal", "DomPortalHost", "Portal host classes"),
                Arguments.of("@angular/cdk/portal", "PortalHost", "Portal host classes"),
                Arguments.of("@angular/cdk/portal", "BasePortalHost", "Portal host classes"),
                Arguments.of("@angular/cdk/portal", "PortalInjector", "Injector.create"),
                Arguments.of("@angular/cdk/table", "StickyStyler", "sticky internals/mixins"),
                Arguments.of("@angular/cdk/table", "CDK_TABLE_TEMPLATE", "sticky internals/mixins"),
                Arguments.of("@angular/cdk/dialog", "DIALOG_SCROLL_STRATEGY_PROVIDER", "provider/factory exports were removed"),
                Arguments.of("@angular/cdk/drag-drop", "DragDropRegistry", "no longer generic"),
                Arguments.of("@angular/cdk/dialog", "DialogConfig", "componentFactoryResolver was removed"),
                Arguments.of("@angular/cdk/portal", "ComponentPortal", "constructors changed")
        );
    }

    @Test
    void marksRemovedProtractorAndPrivateEntryPoints() {
        assertMarkers("import { ProtractorHarnessEnvironment } from '@angular/cdk/testing/protractor';\n",
                "e2e/app.e2e-spec.ts", "removed in v14");
        assertMarkers("import { privateApi } from '@angular/cdk/overlay/private';\n",
                "src/private.ts", "Private or internal CDK entry point");
    }

    @Test
    void marksSelectionModelBooleanContractOnOwnedTypedVariable() {
        assertMarkers(
                "import { SelectionModel } from '@angular/cdk/collections';\n" +
                "function update(selection: SelectionModel<string>) { selection.select('a'); selection.clear(); }\n",
                "src/selection.ts", "now returns whether the selection changed");
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { PortalHost as Host } from '@angular/cdk/portal';\n",
                        "import { PortalOutlet as Host } from '@angular/cdk/portal';\n",
                        source -> source.path("src/idempotent.ts")));
    }

    private void assertMarkers(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(before, source -> source.path(path).after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    for (String message : messages) assertTrue(printed.contains(message), printed);
                })));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
    }
}
