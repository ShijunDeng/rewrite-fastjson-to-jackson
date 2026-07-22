package com.huawei.clouds.openrewrite.tweenjs;

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

class TweenJsSourceMigrationTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.tweenjs.MigrateDeterministicTweenJsTo23";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.tweenjs.AuditTweenJs23Source";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "normalizes exact distribution import {0}")
    @MethodSource("distributionImports")
    void normalizesKnownPhysicalImportsToRoot(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/entry.js")));
    }

    static Stream<Arguments> distributionImports() {
        return Stream.of(
                Arguments.of("import TWEEN from '@tweenjs/tween.js/dist/tween.esm.js';\n",
                        "import TWEEN from '@tweenjs/tween.js';\n"),
                Arguments.of("import * as TWEEN from \"@tweenjs/tween.js/dist/tween.esm.js\";\n",
                        "import * as TWEEN from \"@tweenjs/tween.js\";\n"),
                Arguments.of("import { Tween } from '@tweenjs/tween.js/dist/tween.cjs.js';\n",
                        "import { Tween } from '@tweenjs/tween.js';\n"),
                Arguments.of("import { Group } from '@tweenjs/tween.js/dist/tween.cjs';\n",
                        "import { Group } from '@tweenjs/tween.js';\n"),
                Arguments.of("import TWEEN from '@tweenjs/tween.js/dist/index.cjs.js';\n",
                        "import TWEEN from '@tweenjs/tween.js';\n"),
                Arguments.of("import TWEEN from '@tweenjs/tween.js/dist/index.cjs';\n",
                        "import TWEEN from '@tweenjs/tween.js';\n")
        );
    }

    @ParameterizedTest(name = "normalizes runtime loader {0}")
    @MethodSource("runtimeLoaders")
    void normalizesExactRequireAndDynamicImport(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/loader.js")));
    }

    static Stream<Arguments> runtimeLoaders() {
        return Stream.of(
                Arguments.of("const TWEEN = require('@tweenjs/tween.js/dist/tween.cjs.js');\n",
                        "const TWEEN = require('@tweenjs/tween.js');\n"),
                Arguments.of("const TWEEN = require(\"@tweenjs/tween.js/dist/tween.cjs\");\n",
                        "const TWEEN = require(\"@tweenjs/tween.js\");\n"),
                Arguments.of("const TWEEN = require('@tweenjs/tween.js/dist/index.cjs.js');\n",
                        "const TWEEN = require('@tweenjs/tween.js');\n"),
                Arguments.of("const TWEEN = require(`@tweenjs/tween.js/dist/tween.cjs`);\n",
                        "const TWEEN = require('@tweenjs/tween.js');\n"),
                Arguments.of("const module = import('@tweenjs/tween.js/dist/tween.esm.js');\n",
                        "const module = import('@tweenjs/tween.js');\n")
        );
    }

    @ParameterizedTest(name = "removes proven redundant main group {0}")
    @MethodSource("redundantMainGroups")
    void removesOnlyProvenExportedMainGroupArgument(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                typescript(before, after, source -> source.path("src/group.ts")));
    }

    static Stream<Arguments> redundantMainGroups() {
        return Stream.of(
                Arguments.of("import * as TWEEN from '@tweenjs/tween.js';\nconst tween = new TWEEN.Tween(state, TWEEN);\n",
                        "import * as TWEEN from '@tweenjs/tween.js';\nconst tween = new TWEEN.Tween(state);\n"),
                Arguments.of("import TWEEN from '@tweenjs/tween.js';\nconst tween = new TWEEN.Tween(state, TWEEN);\n",
                        "import TWEEN from '@tweenjs/tween.js';\nconst tween = new TWEEN.Tween(state);\n"),
                Arguments.of("import TWEEN, { Tween as Animation } from '@tweenjs/tween.js';\nconst tween = new Animation(state, TWEEN);\n",
                        "import TWEEN, { Tween as Animation } from '@tweenjs/tween.js';\nconst tween = new Animation(state);\n")
        );
    }

    @ParameterizedTest(name = "leaves unsafe source change {0}")
    @ValueSource(strings = {
            "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(state, false);\n",
            "import { Tween, Group } from '@tweenjs/tween.js';\nconst group = new Group();\nconst t = new Tween(state, group);\n",
            "const TWEEN = localTween;\nconst t = new TWEEN.Tween(state, TWEEN);\n",
            "import TWEEN from '@company/tween.js';\nconst t = new TWEEN.Tween(state, TWEEN);\n",
            "import TWEEN from '@tweenjs/tween.js/dist/custom.js';\n",
            "import TWEEN from '@tweenjs/tween.js/lib/Tween';\n",
            "const TWEEN = require(name);\n",
            "const TWEEN = require('@tweenjs/tween.js/dist/tween.cjs', options);\n",
            "const TWEEN = require('@company/tween.js/dist/tween.cjs');\n",
            "const require = (name) => name;\nconst TWEEN = require('@tweenjs/tween.js/dist/tween.cjs');\n",
            "const root = '@tweenjs/tween.js/dist/tween.cjs';\n",
            "// import * as TWEEN from '@tweenjs/tween.js';\nconst TWEEN = localTween;\nconst t = new TWEEN.Tween(state, TWEEN);\n",
            "import * as TWEEN from '@tweenjs/tween.js';\nfunction build(TWEEN) { return new TWEEN.Tween(state, TWEEN); }\n",
            "import { Tween } from '@tweenjs/tween.js';\nfunction build(Tween, TWEEN) { return new Tween(state, TWEEN); }\n",
            "import TWEEN from '@tweenjs/tween.js';\nconst t = new TWEEN.Tween(state);\n"
    })
    void leavesUnprovenImportsLoadersAndGroupsUntouched(String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(source, input -> input.path("src/safe.js")));
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("import TWEEN from '@tweenjs/tween.js/dist/tween.esm.js';\nconst t = new TWEEN.Tween(x, TWEEN);\n",
                        "import TWEEN from '@tweenjs/tween.js';\nconst t = new TWEEN.Tween(x);\n",
                        source -> source.path("src/idempotent.js")));
    }

    @Test
    void marksRealGlobeStreamTweenFlow() {
        // hululuuuuu/GlobeStream3D@ba75e68c1575673cbbb1d2edc89ff7c28586d4db,
        // src/lib/utils/tween.ts, reduced without changing the Tween calls.
        assertTypeScriptMarkers(
                "import * as TWEEN from '@tweenjs/tween.js';\n" +
                "export function setTween(from: Record<any, any>, to: Record<any, any>, cb: Function) {\n" +
                "  const ro = new TWEEN.Tween(from);\n" +
                "  ro.to(to, 2000);\n" +
                "  ro.onUpdate((e) => cb(e));\n" +
                "  ro.start();\n" +
                "  ro.repeat(Infinity);\n" +
                "  ro.onComplete(() => cb(from));\n" +
                "}\n",
                "src/lib/utils/tween.ts", "snapshot targets by default", "callback's count and ordering",
                "advances across every skipped repeat");
    }

    @Test
    void marksRealGlobeStreamRafUpdateLoop() {
        // hululuuuuu/GlobeStream3D@ba75e68c1575673cbbb1d2edc89ff7c28586d4db,
        // src/lib/chartScene.ts uses this named export in its requestAnimationFrame loop.
        assertTypeScriptMarkers(
                "import { update as tweenUpdate } from '@tweenjs/tween.js';\n" +
                "export function animate() { tweenUpdate(); requestAnimationFrame(animate); }\n",
                "src/lib/chartScene.ts", "handles large time jumps");
    }

    @Test
    void marksRealThreeViewcubeDefaultApiFlow() {
        // mikemklee/three-viewcube@036db7b9e74c23fcb2dc6c7cb72d7220504ca558/index.ts,
        // reduced while retaining its default API object, chain and global update.
        assertTypeScriptMarkers(
                "import TWEEN from '@tweenjs/tween.js';\n" +
                "const positionTween = new TWEEN.Tween(camera.position)\n" +
                "  .to(finishPosition, 300)\n" +
                "  .easing(TWEEN.Easing.Cubic.InOut)\n" +
                "  .onUpdate(() => camera.lookAt(targetPosition));\n" +
                "positionTween.start();\nTWEEN.update();\n",
                "index.ts", "snapshot targets by default", "callback's count and ordering",
                "handles large time jumps");
    }

    @ParameterizedTest(name = "marks source risk {0}")
    @MethodSource("sourceRisks")
    void marksExactOwnedTweenRiskNodes(String label, String source, String message) {
        assertJavaScriptMarkers(source, "src/" + label + ".js", message);
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("deep-esm", "import TWEEN from '@tweenjs/tween.js/dist/tween.esm.js';\n", "exports block physical dist"),
                Arguments.of("deep-cjs", "import TWEEN from '@tweenjs/tween.js/dist/tween.cjs.js';\n", "exports block physical dist"),
                Arguments.of("private-lib", "import Tween from '@tweenjs/tween.js/lib/Tween';\n", "exports only the package root"),
                Arguments.of("private-source", "import Tween from '@tweenjs/tween.js/src/Tween';\n", "exports only the package root"),
                Arguments.of("legacy", "import TWEEN from 'tween.js';\n", "legacy tween.js package"),
                Arguments.of("legacy-types", "import type TWEEN from '@types/tween.js';\n", "legacy tween.js package/type stub"),
                Arguments.of("to-named", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.to(y, 10);\n", "snapshot targets by default"),
                Arguments.of("to-alias", "import { Tween as Animation } from '@tweenjs/tween.js';\nconst t = new Animation(x);\nt.to(y);\n", "snapshot targets by default"),
                Arguments.of("dynamic", "import * as TWEEN from '@tweenjs/tween.js';\nconst t = new TWEEN.Tween(x);\nt.dynamic(true);\n", "mutates interpolation-array targets"),
                Arguments.of("retarget", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.start();\nt.to(y, 10);\n", "throws when .to() retargets"),
                Arguments.of("paused-retarget", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.pause();\nt.to(y);\n", "throws when .to() retargets"),
                Arguments.of("negative-delay", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.delay(-5);\n", "Negative delay is not a supported"),
                Arguments.of("negative-to-duration", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.to(y, -5);\n", "clamps negative duration to zero"),
                Arguments.of("negative-duration", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.duration(-1);\n", "clamps negative duration to zero"),
                Arguments.of("repeat", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.repeat(3);\n", "advances across every skipped repeat"),
                Arguments.of("repeat-delay", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.repeatDelay(10);\n", "advances across every skipped repeat"),
                Arguments.of("yoyo", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.yoyo(true);\n", "advances across every skipped repeat"),
                Arguments.of("chain", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.chain(next);\n", "advances across every skipped repeat"),
                Arguments.of("on-start", callback("onStart"), "callback's count and ordering"),
                Arguments.of("on-every-start", callback("onEveryStart"), "callback's count and ordering"),
                Arguments.of("on-update", callback("onUpdate"), "callback's count and ordering"),
                Arguments.of("on-repeat", callback("onRepeat"), "callback's count and ordering"),
                Arguments.of("on-complete", callback("onComplete"), "callback's count and ordering"),
                Arguments.of("on-stop", callback("onStop"), "callback's count and ordering"),
                Arguments.of("global-update", "import * as TWEEN from '@tweenjs/tween.js';\nTWEEN.update();\n", "handles large time jumps"),
                Arguments.of("named-update", "import { update as tick } from '@tweenjs/tween.js';\ntick(100);\n", "handles large time jumps"),
                Arguments.of("group-update", "import { Group } from '@tweenjs/tween.js';\nconst g = new Group();\ng.update(100, true);\n", "handles large time jumps"),
                Arguments.of("tween-update", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x, false);\nt.update(100);\n", "outside every group"),
                Arguments.of("date-clock", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.start(Date.now());\n", "Date.now() epoch"),
                Arguments.of("performance-clock", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.pause(performance.now());\n", "performance.now() source"),
                Arguments.of("library-clock", "import { Tween, now } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.resume(now());\n", "Tween.js now() source"),
                Arguments.of("custom-clock", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.start(video.currentTime * 1000);\n", "same millisecond monotonic time domain"),
                Arguments.of("manual", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x, false);\n", "outside every group"),
                Arguments.of("custom-group", "import { Tween, Group } from '@tweenjs/tween.js';\nconst g = new Group();\nconst t = new Tween(x, g);\n", "explicit group"),
                Arguments.of("group-remove", "import { Group } from '@tweenjs/tween.js';\nconst g = new Group();\ng.removeAll();\n", "Group membership is mutable"),
                Arguments.of("main-remove", "import * as TWEEN from '@tweenjs/tween.js';\nTWEEN.removeAll();\n", "Group membership is mutable"),
                Arguments.of("easing-mutation", "import * as TWEEN from '@tweenjs/tween.js';\nTWEEN.Easing.Custom = fn;\n", "freezes built-in Easing objects"),
                Arguments.of("named-easing-mutation", "import { Easing } from '@tweenjs/tween.js';\nEasing.Quadratic.Custom = fn;\n", "freezes built-in Easing objects"),
                Arguments.of("private-duration", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nconsole.log(t._duration);\n", "getDuration() as the public read API"),
                Arguments.of("private-dist-literal", "import '@tweenjs/tween.js';\nconst entry = '@tweenjs/tween.js/dist/custom.js';\n", "do not expose this physical dist path"),
                Arguments.of("vendor-literal", "import '@tweenjs/tween.js';\ncopy('node_modules/@tweenjs/tween.js/dist/tween.cjs');\n", "Copied Tween.js physical assets")
        );
    }

    private static String callback(String name) {
        return "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt." + name + "(handler);\n";
    }

    @ParameterizedTest(name = "leaves unrelated behavior unmarked {0}")
    @MethodSource("safeSources")
    void leavesUnownedOrTargetSafeSourceNodesUnmarked(String label, String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript(source, input -> input.path("src/" + label + ".js")));
    }

    static Stream<Arguments> safeSources() {
        return Stream.of(
                Arguments.of("root-only", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\n"),
                Arguments.of("local-to", "const t = new LocalTween(x);\nt.to(y);\n"),
                Arguments.of("three-group", "import { Group } from 'three';\nconst g = new Group();\ng.removeAll();\n"),
                Arguments.of("other-update", "import { update } from './store';\nupdate();\n"),
                Arguments.of("dynamic-false", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.dynamic(false);\n"),
                Arguments.of("public-duration", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.getDuration();\n"),
                Arguments.of("positive-delay", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.delay(10);\n"),
                Arguments.of("default-start", "import { Tween } from '@tweenjs/tween.js';\nconst t = new Tween(x);\nt.start();\n"),
                Arguments.of("easing-read", "import * as TWEEN from '@tweenjs/tween.js';\nconst easing = TWEEN.Easing.Cubic.InOut;\n"),
                Arguments.of("similar-package", "import TWEEN from '@company/tween.js';\nconst t = new TWEEN.Tween(x);\nt.repeat(2);\n"),
                Arguments.of("comment", "// @tweenjs/tween.js/dist/tween.cjs\nconst value = 1;\n")
        );
    }

    private void assertJavaScriptMarkers(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript(before, source -> source.path(path).after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    for (String message : messages) assertTrue(printed.contains(message), printed);
                })));
    }

    private void assertTypeScriptMarkers(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(before, source -> source.path(path).after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    for (String message : messages) assertTrue(printed.contains(message), printed);
                })));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.tweenjs")
                .scanYamlResources().build();
    }
}
