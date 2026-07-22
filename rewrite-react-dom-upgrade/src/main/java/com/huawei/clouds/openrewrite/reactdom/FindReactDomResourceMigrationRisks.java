package com.huawei.clouds.openrewrite.reactdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Mark removed React DOM UMD artifacts and inline roots in HTML resources. */
public final class FindReactDomResourceMigrationRisks extends Recipe {
    private static final List<ReactDomSourceText.RiskPattern> RISKS = List.of(
            new ReactDomSourceText.RiskPattern(
                    Pattern.compile("https?://(?:unpkg[.]com|cdn[.]jsdelivr[.]net/npm)/react-dom@[^\"'<>\\s]+/(?:umd|dist)/[^\"'<>\\s]+"),
                    "React DOM 19 removed UMD builds; use an ESM CDN or bundler and align react/react-dom together"),
            new ReactDomSourceText.RiskPattern(
                    Pattern.compile("\\bReactDOM\\s*[.]\\s*(?:render|hydrate|unmountComponentAtNode)(?=\\s*\\()"),
                    "React DOM 19 removed this inline legacy root API; move bootstrap/teardown to a client module with explicit root ownership")
    );

    @Override
    public String getDisplayName() {
        return "Find React DOM 19 HTML resource migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed React DOM UMD assets and inline legacy root APIs in HTML resources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (!path.endsWith(".html") && !path.endsWith(".htm")) {
                    return visited;
                }
                return ReactDomSourceText.markAllMatches(visited, RISKS);
            }
        };
    }
}
