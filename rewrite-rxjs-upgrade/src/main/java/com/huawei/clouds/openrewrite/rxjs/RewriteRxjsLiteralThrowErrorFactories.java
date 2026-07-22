package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Pattern;

/** Wraps only literal RxJS throwError values in lazy factories. */
public final class RewriteRxjsLiteralThrowErrorFactories extends Recipe {
    private static final Pattern NEW_ERROR = Pattern.compile(
            "(?<![\\w$.])throwError\\(\\s*new\\s+Error\\(\\s*(?<quote>[\"'])(?<message>[^\"'\\\\\\r\\n]*)\\k<quote>\\s*\\)\\s*\\)"
    );
    private static final Pattern STRING = Pattern.compile(
            "(?<![\\w$.])throwError\\(\\s*(?<quote>[\"'])(?<message>[^\"'\\\\\\r\\n]*)\\k<quote>\\s*\\)"
    );

    @Override
    public String getDisplayName() {
        return "Rewrite literal RxJS throwError values";
    }

    @Override
    public String getDescription() {
        return "Wrap direct string literals and new Error string literals passed to an unaliased RxJS " +
               "throwError import in lazy factories without changing comments or strings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String source = visited.getText();
                if (!RxjsSourceText.isSupported(visited) ||
                    !RxjsSourceText.hasUnaliasedNamedImport(source, "rxjs", "throwError")) {
                    return visited;
                }
                String migrated = RxjsSourceText.replaceCodeMatches(source, NEW_ERROR, match ->
                        "throwError(() => new Error(" + match.group("quote") + match.group("message") +
                        match.group("quote") + "))"
                );
                migrated = RxjsSourceText.replaceCodeMatches(migrated, STRING, match ->
                        "throwError(() => " + match.group("quote") + match.group("message") + match.group("quote") + ")"
                );
                return visited.withText(migrated);
            }
        };
    }
}
