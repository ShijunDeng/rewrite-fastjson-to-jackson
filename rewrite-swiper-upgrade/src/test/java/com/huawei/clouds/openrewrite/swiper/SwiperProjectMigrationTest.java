package com.huawei.clouds.openrewrite.swiper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class SwiperProjectMigrationTest implements RewriteTest {
    private static final String PROJECT = "com.huawei.clouds.openrewrite.swiper.AuditSwiper12Project";
    private static final String TEMPLATES = "com.huawei.clouds.openrewrite.swiper.AuditSwiper12TemplatesAndStyles";
    private static final String RECOMMENDED = "com.huawei.clouds.openrewrite.swiper.MigrateSwiperTo12_1_2";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolved")
    void marksEveryUnresolvedSwiperDeclaration(String declaration, String message) {
        assertPackageMarker("{\"dependencies\":{\"swiper\":\"" + declaration + "\"}}", message);
    }

    static Stream<Arguments> unresolved() {
        return Stream.of(
                Arguments.of("=3.4.2", "Complex Swiper range"),
                Arguments.of("v6.8.1", "Complex Swiper range"),
                Arguments.of("^v9.4.1", "Complex Swiper range"),
                Arguments.of(">=6.8.4 <10", "Complex Swiper range"),
                Arguments.of("3.4.2 || 9.4.1", "Complex Swiper range"),
                Arguments.of("6.8.1 - 9.4.1", "Complex Swiper range"),
                Arguments.of("9.x", "Complex Swiper range"),
                Arguments.of("8.4.7-beta.1", "Complex Swiper range"),
                Arguments.of("9.4.1+build.1", "Complex Swiper range"),
                Arguments.of("workspace:^", "Protocol, alias, tag or dynamic"),
                Arguments.of("workspace:9.4.1", "Protocol, alias, tag or dynamic"),
                Arguments.of("npm:@company/swiper@9.4.1", "Protocol, alias, tag or dynamic"),
                Arguments.of("file:../swiper", "Protocol, alias, tag or dynamic"),
                Arguments.of("link:../swiper", "Protocol, alias, tag or dynamic"),
                Arguments.of("github:nolimits4web/swiper", "Protocol, alias, tag or dynamic"),
                Arguments.of("https://example.test/swiper.tgz", "Protocol, alias, tag or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag or dynamic"),
                Arguments.of("next", "Protocol, alias, tag or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag or dynamic"),
                Arguments.of("${SWIPER_VERSION}", "Protocol, alias, tag or dynamic"),
                Arguments.of("{{swiperVersion}}", "Protocol, alias, tag or dynamic"),
                Arguments.of("3.4.3", "Unlisted or non-target"),
                Arguments.of("^5.4.5", "Unlisted or non-target"),
                Arguments.of("~9.4.2", "Unlisted or non-target"),
                Arguments.of("10.0.0", "Unlisted or non-target"),
                Arguments.of("^11.2.10", "Unlisted or non-target"),
                Arguments.of("12.1.1", "Unlisted or non-target"),
                Arguments.of("^12.1.2", "Unlisted or non-target"),
                Arguments.of("13.0.0", "Unlisted or non-target")
        );
    }

    @Test
    void marksNonStringDeclaration() {
        assertPackageMarker("{\"dependencies\":{\"swiper\":true}}", "Non-string Swiper declaration");
    }

    @ParameterizedTest(name = "marks old toolchain {0}@{1}")
    @MethodSource("legacyTools")
    void marksLegacyEsmBuildAndTestToolchains(String tool, String version) {
        assertPackageMarker("{\"dependencies\":{\"swiper\":\"12.1.2\"},\"devDependencies\":{\"" +
                tool + "\":\"" + version + "\"}}", "older toolchain");
    }

    static Stream<Arguments> legacyTools() {
        return Stream.of(
                Arguments.of("typescript", "4.6.4"), Arguments.of("webpack", "4.47.0"),
                Arguments.of("webpack-cli", "4.10.0"), Arguments.of("webpack-dev-server", "4.15.0"),
                Arguments.of("rollup", "2.79.1"), Arguments.of("vite", "3.2.10"),
                Arguments.of("parcel", "1.12.5"), Arguments.of("jest", "28.1.3"),
                Arguments.of("ts-jest", "28.0.8"), Arguments.of("babel-jest", "28.1.3")
        );
    }

    @ParameterizedTest(name = "leaves supported toolchain {0}@{1}")
    @MethodSource("supportedTools")
    void leavesModernToolchainScalarsUnmarked(String tool, String version) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"swiper\":\"12.1.2\"},\"devDependencies\":{\"" + tool +
                        "\":\"" + version + "\"}}", source -> source.path("package.json")));
    }

    static Stream<Arguments> supportedTools() {
        return Stream.of(
                Arguments.of("typescript", "4.7.4"), Arguments.of("typescript", "^5.5.4"),
                Arguments.of("webpack", "5.91.0"), Arguments.of("rollup", "3.29.5"),
                Arguments.of("vite", "4.5.5"), Arguments.of("vite", "^6.0.0"),
                Arguments.of("parcel", "2.12.0"), Arguments.of("jest", "29.7.0"),
                Arguments.of("ts-jest", "29.2.5"), Arguments.of("babel-jest", "29.7.0")
        );
    }

    @ParameterizedTest(name = "marks wrapper {0}")
    @MethodSource("wrappers")
    void marksThirdPartyFrameworkWrappers(String wrapper) {
        assertPackageMarker("{\"dependencies\":{\"swiper\":\"12.1.2\",\"" + wrapper + "\":\"1.0.0\"}}",
                "third-party Swiper wrapper");
    }

    static Stream<String> wrappers() {
        return Stream.of("angular2-useful-swiper", "ngx-swiper-wrapper", "vue-awesome-swiper",
                "react-id-swiper", "svelte-swiper", "swiper-solid");
    }

    @Test
    void marksLegacyTypes() {
        assertPackageMarker("{\"dependencies\":{\"swiper\":\"12.1.2\"},\"devDependencies\":{\"@types/swiper\":\"6.0.0\"}}",
                "publishes its own TypeScript declarations");
    }

    @Test
    void marksCommonJsPackageMode() {
        assertPackageMarker("{\"type\":\"commonjs\",\"dependencies\":{\"swiper\":\"12.1.2\"}}",
                "project is CommonJS");
    }

    @Test
    void marksInternetExplorerTarget() {
        assertPackageMarker("{\"browserslist\":[\"last 2 versions\",\"ie 11\"],\"dependencies\":{\"swiper\":\"12.1.2\"}}",
                "uses Pointer Events");
    }

    @ParameterizedTest(name = "marks manifest internal path {0}")
    @MethodSource("manifestPaths")
    void marksPhysicalAndInternalManifestConfiguration(String label, String member) {
        assertPackageMarker("{\"dependencies\":{\"swiper\":\"12.1.2\"}," + member + "}",
                "pins a Swiper physical/internal file");
    }

    static Stream<Arguments> manifestPaths() {
        return Stream.of(
                Arguments.of("copy", "\"scripts\":{\"copy\":\"cp node_modules/swiper/swiper-bundle.min.js public/\"}"),
                Arguments.of("browser", "\"browser\":{\"swiper\":\"swiper/dist/js/swiper.js\"}"),
                Arguments.of("jest", "\"jest\":{\"moduleNameMapper\":{\"swiper\":\"swiper/core/core\"}}"),
                Arguments.of("vendor", "\"unpkg\":\"vendor/swiper/swiper.min.js\""),
                Arguments.of("components", "\"exports\":{\"./lazy\":\"swiper/components/lazy/lazy.js\"}")
        );
    }

    @ParameterizedTest(name = "marks JSON config {0}")
    @MethodSource("jsonConfigs")
    void marksSwiperAwareJsonResolverAndTestConfiguration(String path, String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(source, input -> input.path(path).after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> jsonConfigs() {
        return Stream.of(
                Arguments.of("tsconfig.json", "{\"compilerOptions\":{\"moduleResolution\":\"node\",\"paths\":{\"swiper\":[\"node_modules/swiper/swiper.mjs\"]}}}", "pins Swiper package internals"),
                Arguments.of("jsconfig.app.json", "{\"compilerOptions\":{\"moduleResolution\":\"node\",\"paths\":{\"swiper\":[\"swiper/core/core\"]}}}", "legacy module resolution"),
                Arguments.of("jest.config.json", "{\"transformIgnorePatterns\":[\"node_modules/(?!swiper)/\"]}", "Jest transform exclusions"),
                Arguments.of("webpack.config.json", "{\"resolve\":{\"alias\":{\"swiper\":\"swiper/dist/js/swiper.js\"}}}", "pins Swiper package internals"),
                Arguments.of("vite.config.json", "{\"resolve\":{\"alias\":{\"swiper\":\"vendor/swiper/swiper.esm.js\"}}}", "pins Swiper package internals")
        );
    }

    @Test
    void leavesManifestWithoutDirectSwiperUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"type\":\"commonjs\",\"browserslist\":[\"ie 11\"],\"dependencies\":{\"vue-awesome-swiper\":\"4.1.1\"},\"devDependencies\":{\"webpack\":\"4.47.0\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void leavesGenericTsconfigWithoutSwiperReferenceUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"compilerOptions\":{\"moduleResolution\":\"node\",\"paths\":{\"@app/*\":[\"src/*\"]}}}",
                        source -> source.path("tsconfig.json")));
    }

    @ParameterizedTest(name = "marks template/style risk {0}")
    @MethodSource("templateStyleRisks")
    void marksExactTemplateAndVisualRisk(String label, String path, String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATES)),
                text(source, input -> input.path(path).after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> templateStyleRisks() {
        return Stream.of(
                Arguments.of("element", "src/app.html", "<swiper-container navigation=\"true\"></swiper-container>", "one-time registration"),
                Arguments.of("element-slide", "src/app.vue", "<swiper-slide><article>Card</article></swiper-slide>", "slide slots/content"),
                Arguments.of("container", "src/app.html", "<div class=\"hero swiper-container\"></div>", "renamed the ordinary container"),
                Arguments.of("lazy", "src/app.svelte", "<img class=\"swiper-lazy\" data-src=\"x.jpg\">", "removed the Lazy module/classes"),
                Arguments.of("cdn", "public/index.html", "<script src=\"https://cdn.example/swiper@9.4.1/dist/swiper-bundle.min.js\"></script>", "CDN URL pins"),
                Arguments.of("scss", "src/app.scss", "@use 'swiper/scss/navigation';", "removed published SCSS/Less"),
                Arguments.of("less", "src/app.less", "@import \"swiper/less/pagination\";", "removed published SCSS/Less"),
                Arguments.of("wrapper", "src/app.css", ".swiper-wrapper { transform: none; }", "overrides Swiper-owned DOM/classes"),
                Arguments.of("slide", "src/app.css", ".swiper-slide { width: 300px; }", "overrides Swiper-owned DOM/classes"),
                Arguments.of("pagination", "src/app.scss", ".swiper-pagination .swiper-pagination-bullet { color: red; }", "overrides Swiper-owned DOM/classes"),
                Arguments.of("nav-after", "src/app.css", ".swiper-button-next::after { content: '>'; }", "inline SVG"),
                Arguments.of("nav-var", "src/app.css", ".swiper { --swiper-navigation-size: 20px; }", "inline SVG"),
                Arguments.of("lazy-style", "src/app.css", ".swiper-lazy-preloader { opacity: .5; }", "removed module")
        );
    }

    @ParameterizedTest(name = "leaves template/style no-op {0}")
    @MethodSource("safeTemplateStyles")
    void leavesCommentsDocsAndApplicationClassesUntouched(String path, String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATES)),
                text(source, input -> input.path(path)));
    }

    static Stream<Arguments> safeTemplateStyles() {
        return Stream.of(
                Arguments.of("src/app.html", "<!-- <swiper-container><swiper-slide></swiper-slide></swiper-container> -->"),
                Arguments.of("src/app.scss", "/* .swiper-wrapper {} @use 'swiper/scss'; */"),
                Arguments.of("src/app.scss", "// .swiper-button-next::after {}\n.app {}"),
                Arguments.of("src/app.scss", ".note::after { content: '.swiper-wrapper'; }\n.app {}"),
                Arguments.of("README.md", "<swiper-container> .swiper-wrapper swiper/scss"),
                Arguments.of("snapshot.txt", "<div class=\"swiper-container\"></div>"),
                Arguments.of("src/app.html", "<div class=\"custom-swiper-container\"></div>"),
                Arguments.of("src/app.css", ".custom-swiper-wrapper { display: block; }"),
                Arguments.of("src/app.css", ".application-slide { width: 100%; }")
        );
    }

    @Test
    void leavesGeneratedTemplateAndManifestAuditUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.swiper.FindManualSwiper12MigrationRisks")),
                text("<swiper-container navigation=\"true\"></swiper-container>",
                        source -> source.path("dist/index.html")),
                json("{\"type\":\"commonjs\",\"dependencies\":{\"swiper\":\"9.4.1\"}}",
                        source -> source.path("build/package.json")));
    }

    @Test
    void recommendedRecipeCombinesAutoAndMarkerResults() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"swiper\":\"^8.4.7\"},\"devDependencies\":{\"vite\":\"3.2.10\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "\"swiper\":\"12.1.2\"");
                            assertContains(after.printAll(), "older toolchain");
                        })),
                javascript("import Swiper from 'swiper/swiper.esm.js';\nimport { Navigation } from 'swiper';\n" +
                                "const carousel = new Swiper('.swiper-container', { loop: true, loopedSlides: 4 });\n",
                        source -> source.path("src/app.js").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "from 'swiper'");
                            assertContains(after.printAll(), "from 'swiper/modules'");
                            assertContains(after.printAll(), "new Swiper('.swiper'");
                            assertContains(after.printAll(), "removed loopedSlides");
                        })),
                text("<swiper-container><swiper-slide>Card</swiper-slide></swiper-container>",
                        source -> source.path("src/app.html").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "one-time registration"))));
    }

    @Test
    void exposesAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{
                "com.huawei.clouds.openrewrite.swiper.UpgradeSwiperDependencyTo12_1_2",
                "com.huawei.clouds.openrewrite.swiper.MigrateDeterministicSwiperSourceTo12",
                "com.huawei.clouds.openrewrite.swiper.AuditSwiper12Source", PROJECT, TEMPLATES,
                "com.huawei.clouds.openrewrite.swiper.FindManualSwiper12MigrationRisks", RECOMMENDED
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertNotNull(recipe, name);
            assertTrue(recipe.getRecipeList().size() > 0, name);
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()), name);
        }
    }

    private void assertPackageMarker(String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(source, input -> input.path("package.json").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.swiper")
                .scanYamlResources().build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
