package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Moves a deliberately bounded set of RxJS internal entry points to public exports. */
public final class MigrateRxjsDeepImports extends Recipe {
    private static final Set<String> ROOT_TYPES = Set.of(
            "Observable", "Subject", "BehaviorSubject", "ReplaySubject", "AsyncSubject",
            "Subscription", "Subscriber", "Notification"
    );
    private static final Set<String> CREATION_FUNCTIONS = Set.of(
            "of", "from", "fromEvent", "fromEventPattern", "defer", "iif", "throwError",
            "EMPTY", "NEVER", "combineLatest", "concat", "forkJoin", "merge", "race", "zip",
            "timer", "interval", "range", "using", "generate", "pairs", "onErrorResumeNext",
            "bindCallback", "bindNodeCallback"
    );
    private static final Set<String> OPERATORS = Set.of(
            "audit", "auditTime", "buffer", "bufferCount", "bufferTime", "bufferToggle", "bufferWhen",
            "catchError", "combineAll", "combineLatest", "concat", "concatAll", "concatMap",
            "concatMapTo", "count", "debounce", "debounceTime", "defaultIfEmpty", "delay", "delayWhen",
            "dematerialize", "distinct", "distinctUntilChanged", "distinctUntilKeyChanged", "elementAt",
            "endWith", "every", "exhaust", "exhaustMap", "expand", "filter", "finalize", "find",
            "findIndex", "first", "groupBy", "ignoreElements", "isEmpty", "last", "map", "mapTo",
            "materialize", "max", "merge", "mergeAll", "mergeMap", "mergeMapTo", "mergeScan", "min",
            "multicast", "observeOn", "onErrorResumeNext", "pairwise", "partition", "pluck", "publish",
            "publishBehavior", "publishLast", "publishReplay", "race", "reduce", "refCount", "repeat",
            "repeatWhen", "retry", "retryWhen", "sample", "sampleTime", "scan", "sequenceEqual", "share",
            "shareReplay", "single", "skip", "skipLast", "skipUntil", "skipWhile", "startWith",
            "subscribeOn", "switchAll", "switchMap", "switchMapTo", "take", "takeLast", "takeUntil",
            "takeWhile", "tap", "throttle", "throttleTime", "throwIfEmpty", "timeInterval", "timeout",
            "timeoutWith", "timestamp", "toArray", "window", "windowCount", "windowTime", "windowToggle",
            "windowWhen", "withLatestFrom", "zip", "zipAll"
    );

    private static final Map<String, ModuleTarget> MODULES = modules();

    @Override
    public String getDisplayName() {
        return "Migrate known RxJS deep imports";
    }

    @Override
    public String getDescription() {
        return "Moves exact named imports and exports containing only known RxJS internal types, creation " +
               "functions, or operators to supported public entry points.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!RxjsSourceText.isSupported(visited)) {
                    return visited;
                }
                String migrated = visited.getText();
                for (Map.Entry<String, ModuleTarget> entry : MODULES.entrySet()) {
                    migrated = migrateNamedImportOrExport(migrated, entry.getKey(), entry.getValue());
                }
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String migrateNamedImportOrExport(String source, String internalModule, ModuleTarget target) {
        Pattern statement = Pattern.compile(
                "(?m)^(?<prefix>[ \\t]*(?:import|export)(?:[ \\t]+type)?[ \\t]*\\{(?<names>[^}\\r\\n]+)}" +
                "[ \\t]*from[ \\t]*)(?<quote>[\"'])" + Pattern.quote(internalModule) +
                "\\k<quote>(?<suffix>[ \\t]*;?[ \\t]*(?://[^\\r\\n]*)?)$"
        );
        return RxjsSourceText.replaceCodeMatches(source, statement, match -> {
            if (!containsOnlyPublicNames(match.group("names"), target.publicSymbols)) {
                return match.group();
            }
            return match.group("prefix") + match.group("quote") + target.publicModule +
                   match.group("quote") + match.group("suffix");
        });
    }

    private static boolean containsOnlyPublicNames(String names, Set<String> publicSymbols) {
        boolean found = false;
        for (String candidate : names.split(",")) {
            Matcher binding = Pattern.compile("(?:type\\s+)?(?<name>[A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+as\\s+.+)?")
                    .matcher(candidate.trim());
            if (!binding.matches() || !publicSymbols.contains(binding.group("name"))) {
                return false;
            }
            found = true;
        }
        return found;
    }

    private static Map<String, ModuleTarget> modules() {
        Map<String, ModuleTarget> modules = new LinkedHashMap<>();
        for (String symbol : ROOT_TYPES) {
            modules.put("rxjs/internal/" + symbol, new ModuleTarget("rxjs", Set.of(symbol)));
        }
        for (String symbol : CREATION_FUNCTIONS) {
            modules.put("rxjs/internal/observable/" + symbol, new ModuleTarget("rxjs", Set.of(symbol)));
        }
        modules.put("rxjs/internal/operators", new ModuleTarget("rxjs/operators", OPERATORS));
        for (String symbol : OPERATORS) {
            modules.put("rxjs/internal/operators/" + symbol, new ModuleTarget("rxjs/operators", Set.of(symbol)));
        }
        return Map.copyOf(modules);
    }

    private record ModuleTarget(String publicModule, Set<String> publicSymbols) {
    }
}
