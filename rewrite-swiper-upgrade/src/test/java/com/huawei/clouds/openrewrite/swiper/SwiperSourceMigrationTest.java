package com.huawei.clouds.openrewrite.swiper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.test.SourceSpecs.text;

class SwiperSourceMigrationTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.swiper.MigrateDeterministicSwiperSourceTo12";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.swiper.AuditSwiper12Source";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "normalizes JavaScript entry {0}")
    @MethodSource("javascriptEntries")
    void normalizesExactLegacyCoreAndBundleImports(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/entry.js")));
    }

    static Stream<Arguments> javascriptEntries() {
        return Stream.of(
                Arguments.of("import Swiper from 'swiper/dist/js/swiper.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from \"swiper/dist/js/swiper.min.js\";\n", "import Swiper from \"swiper/bundle\";\n"),
                Arguments.of("import Swiper from 'swiper/js/swiper.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from 'swiper/js/swiper.min.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from 'swiper/swiper-bundle.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from 'swiper/swiper-bundle.min.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from 'swiper/swiper-bundle.esm.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from 'swiper/swiper-bundle.esm.min.js';\n", "import Swiper from 'swiper/bundle';\n"),
                Arguments.of("import Swiper from 'swiper/swiper.esm.js';\n", "import Swiper from 'swiper';\n"),
                Arguments.of("import Swiper from 'swiper/swiper.esm.min.js';\n", "import Swiper from 'swiper';\n"),
                Arguments.of("import Swiper from 'swiper/swiper.js';\n", "import Swiper from 'swiper';\n"),
                Arguments.of("import Swiper from 'swiper/swiper.min.js';\n", "import Swiper from 'swiper';\n")
        );
    }

    @ParameterizedTest(name = "normalizes CSS entry {0}")
    @MethodSource("cssImports")
    void normalizesExactPublicCssImports(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/style.js")));
    }

    static Stream<Arguments> cssImports() {
        return Stream.of(
                Arguments.of("import 'swiper/dist/css/swiper.css';\n", "import 'swiper/css/bundle';\n"),
                Arguments.of("import 'swiper/dist/css/swiper.min.css';\n", "import 'swiper/css/bundle';\n"),
                Arguments.of("import 'swiper/swiper.css';\n", "import 'swiper/css';\n"),
                Arguments.of("import 'swiper/swiper.min.css';\n", "import 'swiper/css';\n"),
                Arguments.of("import 'swiper/swiper-bundle.css';\n", "import 'swiper/css/bundle';\n"),
                Arguments.of("import 'swiper/swiper-bundle.min.css';\n", "import 'swiper/css/bundle';\n"),
                Arguments.of("import 'swiper/components/navigation/navigation.css';\n", "import 'swiper/css/navigation';\n"),
                Arguments.of("import 'swiper/components/pagination/pagination.min.css';\n", "import 'swiper/css/pagination';\n"),
                Arguments.of("import 'swiper/components/effect-fade/effect-fade.css';\n", "import 'swiper/css/effect-fade';\n")
        );
    }

    @ParameterizedTest(name = "moves named module imports {0}")
    @ValueSource(strings = {
            "Navigation", "Pagination", "Autoplay", "A11y", "EffectFade", "EffectCreative", "FreeMode",
            "Grid", "Keyboard", "Mousewheel", "Scrollbar", "Thumbs", "Virtual", "Zoom"
    })
    void movesNamedOnlyBuiltInModulesToModulesExport(String module) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript("import { " + module + " } from 'swiper';\n",
                        "import { " + module + " } from 'swiper/modules';\n",
                        source -> source.path("src/module.js")));
    }

    @Test
    void movesAliasedAndMultipleNamedOnlyModules() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                typescript("import { Navigation as Nav, Pagination, A11y } from \"swiper\";\n",
                        "import { Navigation as Nav, Pagination, A11y } from \"swiper/modules\";\n",
                        source -> source.path("src/modules.ts")));
    }

    @ParameterizedTest(name = "normalizes static dynamic loader {0}")
    @MethodSource("runtimeLoaders")
    void normalizesExactRequireAndStaticDynamicImports(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/loader.js")));
    }

    static Stream<Arguments> runtimeLoaders() {
        return Stream.of(
                Arguments.of("const module = import('swiper/swiper-bundle.esm.js');\n", "const module = import('swiper/bundle');\n")
        );
    }

    @ParameterizedTest(name = "renames proven constructor selector {0}")
    @MethodSource("constructorSelectors")
    void renamesLegacyContainerOnlyInOwnedSwiperConstructor(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/carousel.js")));
    }

    static Stream<Arguments> constructorSelectors() {
        return Stream.of(
                Arguments.of("import Swiper from 'swiper';\nnew Swiper('.swiper-container');\n",
                        "import Swiper from 'swiper';\nnew Swiper('.swiper');\n"),
                Arguments.of("import Carousel from 'swiper/bundle';\nnew Carousel('.hero > .swiper-container .slides', {});\n",
                        "import Carousel from 'swiper/bundle';\nnew Carousel('.hero > .swiper .slides', {});\n"),
                Arguments.of("import Swiper from 'swiper/swiper.esm.js';\nnew Swiper('#app .swiper-container');\n",
                        "import Swiper from 'swiper';\nnew Swiper('#app .swiper');\n")
        );
    }

    @ParameterizedTest(name = "normalizes style/markup text {0}")
    @MethodSource("textMigrations")
    void normalizesExactStylesAndStandaloneContainerClasses(String path, String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                text(before, after, source -> source.path(path)));
    }

    static Stream<Arguments> textMigrations() {
        return Stream.of(
                Arguments.of("src/app.css", "@import 'swiper/swiper.css';\n.swiper-container > .slide {}",
                        "@import 'swiper/css';\n.swiper > .slide {}"),
                Arguments.of("src/app.scss", "@import \"swiper/components/navigation/navigation.css\";\n.hero .swiper-container {}",
                        "@import \"swiper/css/navigation\";\n.hero .swiper {}"),
                Arguments.of("src/app.less", "@import 'swiper/swiper-bundle.min.css';\n.swiper-container:hover {}",
                        "@import 'swiper/css/bundle';\n.swiper:hover {}"),
                Arguments.of("src/index.html", "<div class=\"hero swiper-container featured\"></div>",
                        "<div class=\"hero swiper featured\"></div>"),
                Arguments.of("src/App.vue", "<div class='swiper-container'><slot /></div>",
                        "<div class='swiper'><slot /></div>"),
                Arguments.of("src/App.svelte", "<section class=\"shell swiper-container\"></section>",
                        "<section class=\"shell swiper\"></section>")
        );
    }

    @ParameterizedTest(name = "leaves unproven automatic case {0}")
    @MethodSource("automaticNoOps")
    void leavesUnprovenAndAlreadyModernSourceUntouched(String path, String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                path.endsWith(".js") || path.endsWith(".ts")
                        ? javascript(source, input -> input.path(path))
                        : text(source, input -> input.path(path)));
    }

    static Stream<Arguments> automaticNoOps() {
        return Stream.of(
                Arguments.of("src/mixed.js", "import Swiper, { Navigation } from 'swiper';\n"),
                Arguments.of("src/legacy-mixed.js", "import Swiper, { Navigation } from 'swiper/swiper.esm.js';\n"),
                Arguments.of("src/api.js", "import { SwiperOptions } from 'swiper';\n"),
                Arguments.of("src/require.js", "const Swiper = require('swiper/dist/js/swiper.js');\n"),
                Arguments.of("src/local.js", "const Swiper = LocalSwiper;\nnew Swiper('.swiper-container');\n"),
                Arguments.of("src/shadowed.js", "import Swiper from 'swiper';\nfunction build(Swiper) { return new Swiper('.swiper-container'); }\n"),
                Arguments.of("src/dynamic.js", "import Swiper from 'swiper';\nnew Swiper(selector);\n"),
                Arguments.of("src/prefix.js", "import Swiper from 'swiper';\nnew Swiper('.custom-swiper-container');\n"),
                Arguments.of("src/suffix.js", "import Swiper from 'swiper';\nnew Swiper('.swiper-container-horizontal');\n"),
                Arguments.of("src/modern.js", "import Swiper from 'swiper';\nimport { Navigation } from 'swiper/modules';\nnew Swiper('.swiper');\n"),
                Arguments.of("src/deep.js", "import Swiper from 'swiper/core/core';\n"),
                Arguments.of("src/style.js", "import 'swiper/scss/navigation';\n"),
                Arguments.of("src/style.js", "import 'swiper/components/lazy/lazy.css';\n"),
                Arguments.of("src/style.css", "/* .swiper-container {} @import 'swiper/swiper.css'; */\n.safe {}"),
                Arguments.of("src/style.scss", "// @import 'swiper/swiper.css';\n.safe {}"),
                Arguments.of("src/string.scss", ".note::after { content: '.swiper-container'; }\n.safe {}"),
                Arguments.of("src/inline-comment.scss", ".safe {} // .swiper-container {}\n"),
                Arguments.of("src/index.html", "<!-- <div class=\"swiper-container\"></div> -->"),
                Arguments.of("src/index.html", "<swiper-container><swiper-slide></swiper-slide></swiper-container>"),
                Arguments.of("src/index.html", "<div class=\"custom-swiper-container swiper-container-horizontal\"></div>"),
                Arguments.of("src/App.vue", "<div :class=\"{ 'swiper-container': enabled }\"></div>"),
                Arguments.of("src/index.html", "<div data-class=\"swiper-container\"></div>"),
                Arguments.of("README.md", "swiper/swiper.css .swiper-container"),
                Arguments.of("fixtures/snapshot.txt", "<div class=\"swiper-container\"></div>")
        );
    }

    @Test
    void leavesTypeOnlyModuleImportAndGeneratedAssetsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                typescript("import type { Navigation } from 'swiper';\n",
                        source -> source.path("src/types.ts")),
                javascript("import Swiper from 'swiper/swiper.esm.js';\nnew Swiper('.swiper-container');\n",
                        source -> source.path("dist/generated.js")),
                text("<div class=\"swiper-container\"></div>",
                        source -> source.path("build/assets/generated.html")));
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("import Swiper from 'swiper/swiper.esm.js';\nimport { Navigation } from 'swiper';\nnew Swiper('.swiper-container');\n",
                        "import Swiper from 'swiper';\nimport { Navigation } from 'swiper/modules';\nnew Swiper('.swiper');\n",
                        source -> source.path("src/idempotent.js")));
    }

    @Test
    void migratesFixedRealGrevziSwiperSixImports() {
        // gist grevzi/f697e307dd74cc383b0b9ebe3128224c@7eb565742bf4fe1877e254c34d91598359a13ba2
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript("import SwiperCore from 'swiper';\nimport { Navigation, Pagination, Autoplay } from 'swiper';\nimport 'swiper/swiper-bundle.min.css';\n",
                        "import SwiperCore from 'swiper';\nimport { Navigation, Pagination, Autoplay } from 'swiper/modules';\nimport 'swiper/css/bundle';\n",
                        source -> source.path("src/SwiperCarousel.js")));
    }

    @Test
    void migratesFixedRealHellokatonNamedModules() {
        // gist hellokaton/6576fc9844384b0da0a3311f9f7b65ce@961d3b3195fb1ecb88860c587036878f016397a5
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                typescript("import { Navigation, Pagination, Autoplay } from 'swiper';\n",
                        "import { Navigation, Pagination, Autoplay } from 'swiper/modules';\n",
                        source -> source.path("src/swiper.ts")));
    }

    @Test
    void migratesFixedRealAngularNewsContainerMarkup() {
        // windiest/Angular-news@aec41cc0c2f4af2876507a22719b426e0935bdc9
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                text("<div class=\"swiper-container\"><div class=\"swiper-wrapper\"></div></div>",
                        "<div class=\"swiper\"><div class=\"swiper-wrapper\"></div></div>",
                        source -> source.path("webroot/news/directive/swiper.html")));
    }

    @ParameterizedTest(name = "marks source risk {0}")
    @MethodSource("sourceRisks")
    void marksExactSwiperOwnedRiskNodes(String label, String source, String message) {
        assertJavaScriptMarker(source, "src/" + label + ".js", message);
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("mixed", "import Swiper, { Navigation } from 'swiper';\n", "split the default core import"),
                Arguments.of("angular", "import { SwiperModule } from 'swiper/angular';\n", "removed this framework wrapper"),
                Arguments.of("svelte", "import Swiper from 'swiper/svelte';\n", "removed this framework wrapper"),
                Arguments.of("solid", "import Swiper from 'swiper/solid';\n", "removed this framework wrapper"),
                Arguments.of("react", "import { Swiper } from 'swiper/react';\n", "target React/Vue wrapper API"),
                Arguments.of("vue", "import { Swiper } from 'swiper/vue';\n", "target React/Vue wrapper API"),
                Arguments.of("scss", "import 'swiper/scss/navigation';\n", "no longer publishes SCSS or Less"),
                Arguments.of("less", "import 'swiper/less/pagination';\n", "no longer publishes SCSS or Less"),
                Arguments.of("lazy-entry", "import 'swiper/components/lazy/lazy.css';\n", "removed the Lazy module"),
                Arguments.of("deep", "import x from 'swiper/core/core';\n", "do not expose this internal/deep entry"),
                Arguments.of("shared", "import x from 'swiper/shared/utils';\n", "do not expose this internal/deep entry"),
                Arguments.of("use", "import Swiper from 'swiper';\nSwiper.use([Navigation]);\n", "Global Swiper.use registration"),
                Arguments.of("require", "const Swiper = require('swiper');\n", "ESM-only"),
                Arguments.of("watch-visible", config("watchVisibleSlides: true"), "folded into watchSlidesProgress"),
                Arguments.of("looped", config("loopedSlides: 4"), "removed loopedSlides"),
                Arguments.of("columns", config("slidesPerColumn: 2"), "moved to Grid"),
                Arguments.of("column-fill", config("slidesPerColumnFill: 'row'"), "moved to Grid"),
                Arguments.of("free-min", config("freeModeMinimumVelocity: 0.1"), "flat freeMode option"),
                Arguments.of("free-momentum", config("freeModeMomentum: true"), "flat freeMode option"),
                Arguments.of("free-bounce", config("freeModeMomentumBounce: true"), "flat freeMode option"),
                Arguments.of("free-ratio", config("freeModeMomentumRatio: 1"), "flat freeMode option"),
                Arguments.of("free-velocity", config("freeModeMomentumVelocityRatio: 1"), "flat freeMode option"),
                Arguments.of("free-sticky", config("freeModeSticky: true"), "flat freeMode option"),
                Arguments.of("lazy", config("lazy: { loadPrevNext: true }"), "removed the Lazy module/options"),
                Arguments.of("loop", config("loop: true"), "changes loop behavior"),
                Arguments.of("autoplay", config("autoplay: { delay: 1000 }"), "changes autoplay behavior"),
                Arguments.of("grid", config("grid: { rows: 2 }"), "changes grid behavior"),
                Arguments.of("free-mode", config("freeMode: { enabled: true }"), "changes freeMode behavior"),
                Arguments.of("modules", config("modules: [Navigation]"), "changes modules behavior"),
                Arguments.of("breakpoints", config("breakpoints: { 640: { slidesPerView: 2 } }"), "changes breakpoints behavior"),
                Arguments.of("virtual", config("virtual: true"), "changes virtual behavior"),
                Arguments.of("observer", config("observer: true"), "changes observer behavior"),
                Arguments.of("element-event", "import 'swiper/element';\nconst el = document.querySelector('swiper-container');\nel.addEventListener('slidechange', handler);\n", "event prefix/detail contract"),
                Arguments.of("instance-event", "import Swiper from 'swiper';\nconst s = new Swiper('.swiper');\ns.on('progress', handler);\n", "prepended the Swiper instance"),
                Arguments.of("lazy-class", "import Swiper from 'swiper';\nconst name = 'swiper-lazy-preloader';\n", "removed Lazy module"),
                Arguments.of("selector", "import Swiper from 'swiper';\ndocument.querySelector('.swiper-container');\n", "renamed the ordinary container class"),
                Arguments.of("dynamic-deep", "import Swiper from 'swiper';\nconst path = 'swiper/modules/navigation/navigation';\n", "unpublished Swiper path")
        );
    }

    private static String config(String property) {
        return "import Swiper from 'swiper';\nnew Swiper('.swiper', { " + property + " });\n";
    }

    @ParameterizedTest(name = "leaves unrelated source unmarked {0}")
    @MethodSource("safeSources")
    void leavesUnownedSameNamedApisUnmarked(String label, String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript(source, input -> input.path("src/" + label + ".js")));
    }

    static Stream<Arguments> safeSources() {
        return Stream.of(
                Arguments.of("local-loop", "const options = { loop: true, lazy: true };\n"),
                Arguments.of("local-use", "LocalSwiper.use([Plugin]);\n"),
                Arguments.of("other-require", "const carousel = require('@company/swiper');\n"),
                Arguments.of("other-event", "button.addEventListener('slidechange', handler);\n"),
                Arguments.of("root", "import Swiper from 'swiper';\nnew Swiper('.swiper');\n"),
                Arguments.of("unrelated-options", "import Swiper from 'swiper';\nconst analytics = { loop: true, autoplay: true, modules: [] };\n"),
                Arguments.of("unrelated-element-event", "import 'swiper/element';\nbutton.addEventListener('slidechange', handler);\n"),
                Arguments.of("public-module", "import { Navigation } from 'swiper/modules';\n"),
                Arguments.of("public-css", "import 'swiper/css/navigation';\n"),
                Arguments.of("unrelated-string", "const name = 'custom-swiper-container';\n"),
                Arguments.of("comment", "// import x from 'swiper/core/core';\nconst value = 1;\n")
        );
    }

    private void assertJavaScriptMarker(String before, String path, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript(before, source -> source.path(path).after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.swiper")
                .scanYamlResources().build();
    }
}
