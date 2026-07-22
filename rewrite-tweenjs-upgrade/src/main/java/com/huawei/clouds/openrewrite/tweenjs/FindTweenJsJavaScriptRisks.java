package com.huawei.clouds.openrewrite.tweenjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark Tween.js calls whose correct migration depends on animation and clock semantics. */
public final class FindTweenJsJavaScriptRisks extends Recipe {
    private static final Set<String> CALLBACKS = Set.of(
            "onStart", "onEveryStart", "onUpdate", "onRepeat", "onComplete", "onStop"
    );
    private static final Set<String> REPEAT_FLOW = Set.of("repeat", "repeatDelay", "yoyo", "chain");
    private static final Set<String> CLOCK_CALLS = Set.of("start", "pause", "resume", "update");

    @Override
    public String getDisplayName() {
        return "Find Tween.js 23 JavaScript and TypeScript risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Tween.js imports, constructors and calls affected by package exports, dynamic targets, " +
               "started retargeting, groups, clocks, repeat/callback behavior, negative timing and frozen easing.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean tweenFile;
            private Set<String> tweenObjects = Set.of();
            private Set<String> tweenConstructors = Set.of();
            private Set<String> groupConstructors = Set.of();
            private Set<String> updateFunctions = Set.of();
            private Set<String> nowFunctions = Set.of();
            private Set<String> easingObjects = Set.of();
            private Set<String> tweenInstances = Set.of();
            private Set<String> groupInstances = Set.of();
            private Set<String> startedRetargets = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean previousFile = tweenFile;
                Set<String> previousObjects = tweenObjects;
                Set<String> previousTweenConstructors = tweenConstructors;
                Set<String> previousGroupConstructors = groupConstructors;
                Set<String> previousUpdates = updateFunctions;
                Set<String> previousNow = nowFunctions;
                Set<String> previousEasing = easingObjects;
                Set<String> previousTweens = tweenInstances;
                Set<String> previousGroups = groupInstances;
                Set<String> previousRetargets = startedRetargets;
                tweenFile = false;
                tweenObjects = new HashSet<>();
                tweenConstructors = new HashSet<>();
                groupConstructors = new HashSet<>();
                updateFunctions = new HashSet<>();
                nowFunctions = new HashSet<>();
                easingObjects = new HashSet<>();
                tweenInstances = new HashSet<>();
                groupInstances = new HashSet<>();
                startedRetargets = new HashSet<>();
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                tweenFile = previousFile;
                tweenObjects = previousObjects;
                tweenConstructors = previousTweenConstructors;
                groupConstructors = previousGroupConstructors;
                updateFunctions = previousUpdates;
                nowFunctions = previousNow;
                easingObjects = previousEasing;
                tweenInstances = previousTweens;
                groupInstances = previousGroups;
                startedRetargets = previousRetargets;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = TweenJsSupport.moduleName(visited);
                if (TweenJsSupport.isDistributionEntry(module)) {
                    return mark(visited, "Tween.js 23 package exports block physical dist entry points; this exact import is normalized to the public package root");
                }
                if (module.startsWith(TweenJsSupport.PACKAGE + "/") && !TweenJsSupport.PACKAGE.equals(module)) {
                    return mark(visited, "Tween.js 23 exports only the package root; replace this unpublished deep import with supported root exports");
                }
                if ("tween.js".equals(module) || "@types/tween.js".equals(module)) {
                    return mark(visited, "This is the legacy tween.js package/type stub, not @tweenjs/tween.js; migrate its API and ownership deliberately");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String constructor = compact(visited.getClazz());
                if (!ownedTweenConstructor(constructor)) return visited;
                List<Expression> arguments = visited.getArguments();
                if (arguments.size() > 1 && isFalse(arguments.get(1))) {
                    return mark(visited, "new Tween(object, false) is outside every group; prove that exactly one owner calls tween.update(time) with the same monotonic clock");
                }
                if (arguments.size() > 1 && !exportedMainGroup(arguments.get(1))) {
                    return mark(visited, "This tween uses an explicit group; verify one group update loop, teardown/remove ownership and no concurrent global or per-tween update");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!tweenFile) return visited;
                String method = visited.getSimpleName();
                String select = compact(visited.getSelect());
                boolean tween = ownedTweenSelect(select);
                boolean group = ownedGroupSelect(select);
                boolean object = tweenObjects.contains(select);
                boolean namedUpdate = visited.getSelect() == null && updateFunctions.contains(method);

                if ("to".equals(method) && tween && hasNegativeLiteral(visited.getArguments(), 1)) {
                    return mark(visited, "Tween.js 23.1.1 clamps negative duration to zero; validate the business duration and test zero/negative inputs instead of relying on old invalid results");
                }
                if ("to".equals(method) && tween) {
                    String instance = leadingOwnedName(select, tweenInstances);
                    if (instance != null && startedRetargets.contains(instance)) {
                        return mark(visited, "Tween.js 20+ throws when .to() retargets a started or paused tween; stop/recreate it or prove an intentional dynamic-target design");
                    }
                    return mark(visited, "Tween.js 20 changed .to(target) to snapshot targets by default; choose and test .dynamic(true) only when live target mutation is required");
                }
                if ("dynamic".equals(method) && tween && hasBoolean(visited.getArguments(), true)) {
                    return mark(visited, "dynamic(true) mutates interpolation-array targets while the tween runs; isolate shared arrays and test external writers/readers");
                }
                if ("delay".equals(method) && tween && hasNegativeLiteral(visited.getArguments(), 0)) {
                    return mark(visited, "Negative delay is not a supported Tween.js 23 offset; express the offset with one explicit monotonic timeline");
                }
                if ("duration".equals(method) && tween && hasNegativeLiteral(visited.getArguments(), 0)) {
                    return mark(visited, "Tween.js 23.1.1 clamps negative duration to zero; validate the business duration and test zero/negative inputs instead of relying on old invalid results");
                }
                if (REPEAT_FLOW.contains(method) && tween) {
                    return mark(visited, "Tween.js 23 advances across every skipped repeat after a large time jump; verify repeat/yoyo/chain state and callback order after browser sleep or sparse updates");
                }
                if (CALLBACKS.contains(method) && tween) {
                    return mark(visited, "Verify this callback's count and ordering for zero duration, repeat/yoyo/chain, stop and multi-period time jumps in Tween.js 23");
                }
                if ("getDuration".equals(method) && tween) return visited;
                if (CLOCK_CALLS.contains(method) && (tween || group || object || namedUpdate)) {
                    String clock = clockArgument(visited.getArguments());
                    if (clock != null) {
                        return mark(visited, "Tween start/pause/resume/update values must share one millisecond monotonic clock; this " + clock + " source needs explicit normalization and fake-clock tests");
                    }
                    if ("update".equals(method)) {
                        return mark(visited, "Tween.js 23 update handles large time jumps and group preservation differently; test RAF, background resume, repeat callbacks and single-owner advancement");
                    }
                    if (hasRealArguments(visited.getArguments())) {
                        return mark(visited, "Use the same millisecond monotonic time domain for Tween start, pause, resume and every update call");
                    }
                }
                if (Set.of("add", "remove", "removeAll").contains(method) && (group || object)) {
                    return mark(visited, "Group membership is mutable global/component state; verify add/remove/removeAll ownership, teardown and tweens added during update");
                }
                if (namedUpdate) {
                    String clock = clockArgument(visited.getArguments());
                    if (clock != null) {
                        return mark(visited, "Tween update values must share one millisecond monotonic clock; this " +
                                clock + " source needs explicit normalization and fake-clock tests");
                    }
                    return mark(visited, "Tween.js 23 update handles large time jumps and group preservation differently; test RAF, background resume, repeat callbacks and single-owner advancement");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!tweenFile || !updateFunctions.contains(
                        TweenJsSupport.expressionName(visited.getFunction()))) return visited;
                String clock = clockArgument(visited.getArguments());
                if (clock != null) {
                    return mark(visited, "Tween update values must share one millisecond monotonic clock; this " +
                            clock + " source needs explicit normalization and fake-clock tests");
                }
                return mark(visited, "Tween.js 23 update handles large time jumps and group preservation differently; test RAF, background resume, repeat callbacks and single-owner advancement");
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                return easingMutation(compact(visited.getVariable()))
                        ? mark(visited, "Tween.js freezes built-in Easing objects; keep custom easing in an application-owned function/object and pass it to .easing(fn)")
                        : visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (tweenFile && "_duration".equals(visited.getSimpleName()) &&
                    ownedTweenSelect(compact(visited.getTarget()))) {
                    return mark(visited, "_duration is private implementation state; Tween.js 23.1 provides getDuration() as the public read API");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value)) return visited;
                if (value.startsWith(TweenJsSupport.PACKAGE + "/dist/") &&
                    !TweenJsSupport.isDistributionEntry(value)) {
                    return mark(visited, "Tween.js 23 package exports do not expose this physical dist path; use the root import/require and public exports");
                }
                if (value.contains("node_modules/@tweenjs/tween.js/dist/") ||
                    value.contains("vendor/@tweenjs/tween.js/dist/")) {
                    return mark(visited, "Copied Tween.js physical assets bypass package exports; verify the build/CDN contract or consume the public root entry");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = TweenJsSupport.moduleName(visited);
                        if (!TweenJsSupport.PACKAGE.equals(module) &&
                            !module.startsWith(TweenJsSupport.PACKAGE + "/")) return visited;
                        tweenFile = true;
                        String defaultBinding = TweenJsSupport.defaultBinding(visited);
                        if (defaultBinding != null) tweenObjects.add(defaultBinding);
                        addBinding(visited, "Tween", tweenConstructors);
                        addBinding(visited, "Group", groupConstructors);
                        addBinding(visited, "update", updateFunctions);
                        addBinding(visited, "now", nowFunctions);
                        addBinding(visited, "Easing", easingObjects);
                        return visited;
                    }
                }.visit(cu, ctx);

                String source = cu.printAll();
                Matcher namespace = Pattern.compile("import\\s*\\*\\s*as\\s*([$A-Za-z_][$\\w]*)\\s*from\\s*(['\"])" +
                        Pattern.quote(TweenJsSupport.PACKAGE) + "(?:/[^'\"]+)?\\2").matcher(source);
                while (namespace.find()) tweenObjects.add(namespace.group(1));
                Matcher required = Pattern.compile("(?:const|let|var)\\s+([$A-Za-z_][$\\w]*)\\s*=\\s*require\\(\\s*(['\"])" +
                        Pattern.quote(TweenJsSupport.PACKAGE) + "(?:/[^'\"]+)?\\2\\s*\\)").matcher(source);
                while (required.find()) {
                    tweenFile = true;
                    tweenObjects.add(required.group(1));
                }
                collectConstructedVariables(source, tweenConstructors, "Tween", tweenInstances);
                collectConstructedVariables(source, groupConstructors, "Group", groupInstances);
                for (String objectName : tweenObjects) {
                    collectConstructedVariables(source, Set.of(objectName + ".Tween"), "", tweenInstances);
                    collectConstructedVariables(source, Set.of(objectName + ".Group"), "", groupInstances);
                }
                for (String instance : tweenInstances) {
                    int started = Pattern.compile("\\b" + Pattern.quote(instance) + "\\s*\\.\\s*(?:start|pause)\\s*\\(")
                            .matcher(source).results().mapToInt(match -> match.start()).min().orElse(-1);
                    int retarget = Pattern.compile("\\b" + Pattern.quote(instance) + "\\s*\\.\\s*to\\s*\\(")
                            .matcher(source).results().mapToInt(match -> match.start()).filter(i -> i > started).min().orElse(-1);
                    if (started >= 0 && retarget > started) startedRetargets.add(instance);
                }
            }

            private void addBinding(JS.Import declaration, String imported, Set<String> bindings) {
                String binding = TweenJsSupport.importedBinding(declaration, imported);
                if (binding != null) bindings.add(binding);
            }

            private void collectConstructedVariables(String source, Set<String> constructors, String suffix,
                                                     Set<String> output) {
                for (String constructor : constructors) {
                    String qualified = suffix.isEmpty() ? constructor : constructor;
                    Matcher matcher = Pattern.compile("(?:const|let|var)\\s+([$A-Za-z_][$\\w]*)\\s*=\\s*new\\s+" +
                            Pattern.quote(qualified) + "\\s*\\(").matcher(source);
                    while (matcher.find()) output.add(matcher.group(1));
                }
            }

            private boolean ownedTweenConstructor(String constructor) {
                if (tweenConstructors.contains(constructor)) return true;
                for (String object : tweenObjects) if ((object + ".Tween").equals(constructor)) return true;
                return false;
            }

            private boolean ownedTweenSelect(String select) {
                if (select.isEmpty()) return false;
                if (leadingOwnedName(select, tweenInstances) != null) return true;
                for (String constructor : tweenConstructors) if (select.contains("new" + constructor + "(")) return true;
                for (String object : tweenObjects) if (select.contains("new" + object + ".Tween(")) return true;
                return false;
            }

            private boolean ownedGroupSelect(String select) {
                if (select.isEmpty()) return false;
                if (leadingOwnedName(select, groupInstances) != null) return true;
                for (String object : tweenObjects) if (object.equals(select)) return true;
                return false;
            }

            private boolean exportedMainGroup(Expression expression) {
                return expression instanceof J.Identifier identifier && tweenObjects.contains(identifier.getSimpleName());
            }

            private boolean easingMutation(String variable) {
                if (!tweenFile) return false;
                for (String easing : easingObjects) if (variable.startsWith(easing + ".")) return true;
                for (String object : tweenObjects) if (variable.startsWith(object + ".Easing.")) return true;
                return false;
            }

            private String clockArgument(List<Expression> arguments) {
                if (!hasRealArguments(arguments)) return null;
                String argument = compact(arguments.get(0));
                if (argument.startsWith("Date.now(")) return "Date.now() epoch";
                if (argument.startsWith("performance.now(")) return "performance.now()";
                for (String now : nowFunctions) if (argument.startsWith(now + "(")) return "Tween.js now()";
                return null;
            }
        };
    }

    private static boolean isFalse(Expression expression) {
        return expression instanceof J.Literal literal && Boolean.FALSE.equals(literal.getValue());
    }

    private static boolean hasBoolean(List<Expression> arguments, boolean wanted) {
        return !arguments.isEmpty() && arguments.get(0) instanceof J.Literal literal &&
               Boolean.valueOf(wanted).equals(literal.getValue());
    }

    private static boolean hasRealArguments(List<Expression> arguments) {
        return arguments.stream().anyMatch(argument -> !(argument instanceof J.Empty));
    }

    private static boolean hasNegativeLiteral(List<Expression> arguments, int position) {
        if (arguments.size() <= position) return false;
        String rendered = compact(arguments.get(position));
        return rendered.matches("-\\d+(?:\\.\\d+)?");
    }

    private static String leadingOwnedName(String select, Set<String> names) {
        for (String name : names) if (select.equals(name) || select.startsWith(name + ".")) return name;
        return null;
    }

    private static String compact(Object tree) {
        return tree == null ? "" : tree.toString().replaceAll("\\s+", "");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
