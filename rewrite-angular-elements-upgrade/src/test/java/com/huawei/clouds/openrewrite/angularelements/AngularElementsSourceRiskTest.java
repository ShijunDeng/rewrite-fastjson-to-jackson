package com.huawei.clouds.openrewrite.angularelements;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class AngularElementsSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "createCustomElement(Widget, { injector })",
            "makeElement(Widget, { injector: appRef.injector })",
            "elements.createCustomElement(Widget, { injector })"
    })
    void marksOwnedCreationAliases(String expression) {
        String imports = expression.startsWith("makeElement")
                ? "import { createCustomElement as makeElement } from '@angular/elements';"
                : expression.startsWith("elements")
                ? "import * as elements from '@angular/elements';"
                : "import { createCustomElement } from '@angular/elements';";
        assertMarked(imports + "\nconst Ctor = " + expression + ";", FindAngularElements20SourceRisks.CREATION);
    }

    @Test
    void recognizesCommonJsNamespaceCreation() {
        assertMarked("const elements = require('@angular/elements');\nconst Ctor = elements.createCustomElement(Widget, { injector });",
                FindAngularElements20SourceRisks.CREATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "customElements.define('my-widget', Ctor);",
            "window.customElements.define('my-widget', Ctor);",
            "globalThis.customElements.define('my-widget', Ctor);"
    })
    void marksRegistrationAndSsrBoundary(String statement) {
        assertMarked("import { createCustomElement } from '@angular/elements';\n" + statement,
                FindAngularElements20SourceRisks.REGISTRATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "customElements.get('my-widget');", "customElements.whenDefined('my-widget');",
            "customElements.upgrade(node);", "window.customElements.get('my-widget');"
    })
    void marksRegistryLifecycle(String statement) {
        assertMarked("import { createCustomElement } from '@angular/elements';\n" + statement,
                FindAngularElements20SourceRisks.LIFECYCLE);
    }

    @Test
    void marksKnownCustomElementAttributesEventsAndShadowDom() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20SourceRisks()),
                typescript(
                        """
                        import { createCustomElement } from '@angular/elements';
                        const widget = document.createElement('my-widget');
                        widget.setAttribute('user-name', name);
                        widget.addEventListener('valueChanged', event => consume(event.detail));
                        widget.attachShadow({ mode: 'open' });
                        """, source -> source.path("src/host.ts").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("dash-separated lowercase"));
                            assertTrue(printed.contains("event.detail"));
                            assertTrue(printed.contains("Shadow DOM boundary"));
                        })));
    }

    @Test
    void marksSchemaTypingStrategyAndDeepImports() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20SourceRisks()),
                typescript("import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';\nimport { createCustomElement } from '@angular/elements';",
                        source -> source.path("src/schema.ts").after(actual -> actual).afterRecipe(cu ->
                                assertTrue(cu.printAll().contains("weakens template diagnostics")))),
                typescript("import type { NgElement, WithProperties } from '@angular/elements';",
                        source -> source.path("src/types.ts").after(actual -> actual).afterRecipe(cu ->
                                assertTrue(cu.printAll().contains("HTMLElementTagNameMap")))),
                typescript("import { NgElementStrategy } from '@angular/elements';\ndeclare const strategy: NgElementStrategy;\nstrategy.connect(node);",
                        source -> source.path("src/strategy.ts").after(actual -> actual).afterRecipe(cu ->
                                assertTrue(cu.printAll().contains("Custom NgElement strategy")))),
                typescript("import { NgElement } from '@angular/elements/src/create-custom-element';",
                        source -> source.path("src/deep.ts").after(actual -> actual).afterRecipe(cu ->
                                assertTrue(cu.printAll().contains("deep import")))));
    }

    @Test
    void marksCustomStrategyLifecycleMethods() {
        assertMarked(
                """
                import { NgElementStrategy } from '@angular/elements';
                class StrategyHost {
                  connectedCallback() {}
                  disconnectedCallback() {}
                  attributeChangedCallback() {}
                }
                """, FindAngularElements20SourceRisks.STRATEGY);
    }

    @Test
    void ignoresSameNamesUnownedDomOrdinaryTagsAndExcludedTrees() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20SourceRisks()),
                typescript("import { createCustomElement } from 'other';\ncreateCustomElement(Widget, { injector });\ncustomElements.define('other-widget', Ctor);",
                        source -> source.path("src/other.ts")),
                typescript("import { createCustomElement } from '@angular/elements';\nconst div = document.createElement('div');\ndiv.setAttribute('title', 'x');\ndiv.addEventListener('click', handler);",
                        source -> source.path("src/ordinary.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~")))),
                typescript("import { createCustomElement } from '@angular/elements';\ncreateCustomElement(Widget, { injector });",
                        source -> source.path("generated-client/widget.ts")));
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20SourceRisks()).cycles(2),
                typescript("import { createCustomElement } from '@angular/elements';\nconst Ctor = createCustomElement(Widget, { injector });",
                        source -> source.path("src/idempotent.ts").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("createCustomElement boundary"));
                            assertFalse(printed.contains("~~(~~("));
                        })));
    }

    private void assertMarked(String sourceCode, String message) {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20SourceRisks()),
                typescript(sourceCode, source -> source.path("src/elements.ts").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains(message)))));
    }
}
