package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact RxJS 7 source migration risks that require stream or application semantics. */
public final class FindRxjs7SourceRisks extends Recipe {
    private static final String TO_PROMISE =
            "toPromise is deprecated and may return undefined in RxJS 7; choose firstValueFrom or lastValueFrom and define empty-stream behavior";
    private static final String ADD_CHAIN =
            "Subscription.add returns void in RxJS 7; split this chain and retain the intended parent subscription";
    private static final String THROW_ERROR =
            "throwError should receive a lazy factory; preserve error identity, allocation timing, stack, and type deliberately";
    private static final String INTERNAL_IMPORT =
            "RxJS internal entry points are unsupported; choose the equivalent public rxjs or rxjs/operators export";
    private static final String OBSERVABLE_CREATE =
            "Observable.create was removed; use new Observable and verify teardown, sync-error, and subscription behavior";
    private static final String MULTICAST =
            "Legacy multicast/publish APIs changed or were deprecated; select connectable/share semantics and reset behavior";
    private static final String COMPLETION =
            "RxJS 7 changed notifier/finalization ordering; verify next/error/complete/unsubscribe timing with marble tests";
    private static final String UNDEFINED_INPUT =
            "RxJS 7 no longer accepts undefined as an ObservableInput here; return EMPTY or an explicit observable";
    private static final String INTERNAL_FIELD =
            "This RxJS private/internal field is not a supported extension point in RxJS 7; replace the dependency on internals";
    private static final String RACE_ZIP =
            "RxJS 7 changed race/zip edge behavior; verify empty sources, winner subscriptions, completion, and tuple expectations";
    private static final String SYNC_ERRORS =
            "Deprecated synchronous error handling is not a stable RxJS 7 contract; migrate consumers to normal async error ownership";

    @Override
    public String getDisplayName() {
        return "Find RxJS 7 source compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact, import-owned RxJS constructs whose correct migration depends on promise, subscription, " +
               "multicast, completion, error, or private-extension semantics.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!RxjsSourceText.isSupported(visited) || !visited.getSnippets().isEmpty()) {
                    return visited;
                }
                String source = visited.getText();
                if (!RxjsSourceText.hasRxjsReference(source)) {
                    return visited;
                }
                return mark(visited, findRisks(source));
            }
        };
    }

    private static List<RiskMatch> findRisks(String source) {
        List<RiskMatch> risks = new ArrayList<>();
        boolean[] code = RxjsSourceText.codePositions(source);

        collect(source, code, Pattern.compile("\\.\\s*toPromise\\s*\\(\\s*\\)"), TO_PROMISE, risks);
        collect(source, code, Pattern.compile(
                "(?m)^[ \\t]*(?:import|export)[^\\r\\n]*[\"']rxjs/internal/[^\"']+[\"'][^\\r\\n]*|" +
                "\\b(?:require|import)\\s*\\(\\s*[\"']rxjs/internal/[^\"']+[\"']\\s*\\)"
        ), INTERNAL_IMPORT, risks);
        collect(source, code, Pattern.compile(
                "\\bconfig\\s*\\.\\s*useDeprecatedSynchronousErrorHandling\\b|" +
                "\\buseDeprecatedSynchronousErrorHandling\\b\\s*:"
        ), SYNC_ERRORS, risks);

        Map<String, String> root = RxjsSourceText.namedImports(source, "rxjs");
        Map<String, String> operators = RxjsSourceText.namedImports(source, "rxjs/operators");

        if (root.containsKey("Subscription")) {
            collect(source, code, Pattern.compile("\\.\\s*add\\s*\\([^;\\r\\n]*?\\)\\s*\\.\\s*add\\s*\\("),
                    ADD_CHAIN, risks);
        }
        addImportedCallRisk(source, code, root, "throwError",
                "\\s*\\(\\s*(?!\\([^)]*\\)\\s*=>|[A-Za-z_$][A-Za-z0-9_$]*\\s*=>|function\\b)",
                THROW_ERROR, risks);
        addImportedCallRisk(source, code, root, "Observable", "\\s*\\.\\s*create\\s*\\(",
                OBSERVABLE_CREATE, risks);

        Set<String> multicast = Set.of(
                "multicast", "publish", "publishBehavior", "publishLast", "publishReplay", "refCount"
        );
        for (String imported : multicast) {
            addImportedCallRisk(source, code, root, operators, imported, "\\s*\\(", MULTICAST, risks);
        }
        for (String imported : Set.of("audit", "buffer", "debounce", "sample", "throttle", "finalize")) {
            addImportedCallRisk(source, code, root, operators, imported, "\\s*\\(", COMPLETION, risks);
        }
        for (String imported : Set.of("race", "zip")) {
            addImportedCallRisk(source, code, root, imported, "\\s*\\(", RACE_ZIP, risks);
        }
        addUndefinedInputRisk(source, code, root, "defer", risks);
        addUndefinedInputRisk(source, code, root, "iif", risks);

        if (!intersection(root.keySet(), Set.of(
                "Observable", "Subject", "BehaviorSubject", "ReplaySubject", "AsyncSubject",
                "Subscription", "Subscriber"
        )).isEmpty()) {
            collect(source, code, Pattern.compile(
                    "\\.\\s*(?:_subscribe|_isScalar|syncErrorThrowable|syncErrorThrown|syncErrorValue)\\b"
            ), INTERNAL_FIELD, risks);
        }
        return risks;
    }

    private static void addUndefinedInputRisk(String source, boolean[] code, Map<String, String> imports,
                                               String imported, List<RiskMatch> risks) {
        String local = imports.get(imported);
        if (local == null) return;
        String call = "(?<![\\w$.])" + Pattern.quote(local) +
                      "\\s*\\([^;\\r\\n]*(?:=>\\s*(?:undefined|void\\s+0)|[,:(]\\s*(?:undefined|void\\s+0))";
        collect(source, code, Pattern.compile(call), UNDEFINED_INPUT, risks);
    }

    private static void addImportedCallRisk(String source, boolean[] code, Map<String, String> imports,
                                             String imported, String suffix, String message,
                                             List<RiskMatch> risks) {
        String local = imports.get(imported);
        if (local != null) {
            collect(source, code, Pattern.compile("(?<![\\w$.])" + Pattern.quote(local) + suffix), message, risks);
        }
    }

    private static void addImportedCallRisk(String source, boolean[] code, Map<String, String> first,
                                             Map<String, String> second, String imported, String suffix,
                                             String message, List<RiskMatch> risks) {
        Set<String> locals = new LinkedHashSet<>();
        if (first.containsKey(imported)) locals.add(first.get(imported));
        if (second.containsKey(imported)) locals.add(second.get(imported));
        for (String local : locals) {
            collect(source, code, Pattern.compile("(?<![\\w$.])" + Pattern.quote(local) + suffix), message, risks);
        }
    }

    private static void collect(String source, boolean[] code, Pattern pattern, String message,
                                List<RiskMatch> risks) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (code[matcher.start()] && matcher.end() > matcher.start()) {
                risks.add(new RiskMatch(matcher.start(), matcher.end(), message));
            }
        }
    }

    private static Set<String> intersection(Set<String> first, Set<String> second) {
        Set<String> intersection = new LinkedHashSet<>(first);
        intersection.retainAll(second);
        return intersection;
    }

    private static PlainText mark(PlainText text, List<RiskMatch> found) {
        if (found.isEmpty()) return text;
        found.sort(Comparator.comparingInt(RiskMatch::start).thenComparingInt(match -> -match.end()));
        List<RiskMatch> selected = new ArrayList<>();
        int end = -1;
        for (RiskMatch risk : found) {
            if (risk.start >= end) {
                selected.add(risk);
                end = risk.end;
            }
        }

        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (RiskMatch risk : selected) {
            if (risk.start > cursor) {
                snippets.add(snippet(source.substring(cursor, risk.start)));
            }
            snippets.add(SearchResult.found(snippet(source.substring(risk.start, risk.end)), risk.message));
            cursor = risk.end;
        }
        if (cursor < source.length()) {
            snippets.add(snippet(source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private static PlainText.Snippet snippet(String source) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source);
    }

    private record RiskMatch(int start, int end, String message) {
    }
}
