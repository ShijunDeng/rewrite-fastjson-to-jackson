package com.huawei.clouds.openrewrite.reactdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Pattern;

/** Apply only narrow one-to-one React DOM root, act import and ref callback migrations. */
public final class MigrateDeterministicReactDomSource extends Recipe {
    private static final String IDENTIFIER = "[A-Za-z_$][\\w$]*";
    private static final Pattern REACT_DOM_IMPORT = Pattern.compile(
            "(?m)^(?<indent>[ \\t]*)import\\s+(?:(?<default>" + IDENTIFIER + ")|\\*\\s+as\\s+(?<namespace>" +
            IDENTIFIER + "))\\s+from\\s*(?<quote>[\"'])react-dom\\k<quote>\\s*;?[ \\t]*$");
    private static final Pattern NAMED_ROOT_IMPORT = Pattern.compile(
            "(?m)^(?<indent>[ \\t]*)import\\s*\\{\\s*(?<api>render|hydrate)\\s*}\\s*from\\s*" +
            "(?<quote>[\"'])react-dom\\k<quote>\\s*;?[ \\t]*$");
    private static final Pattern CLIENT_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+.+?\\s+from\\s*([\"'])react-dom/client\\1");
    private static final Pattern ACT_IMPORT = Pattern.compile(
            "(?m)^(?<indent>[ \\t]*)import\\s*\\{\\s*act\\s*}\\s*from\\s*(?<quote>[\"'])" +
            "react-dom/test-utils\\k<quote>\\s*(?<semi>;?)[ \\t]*$");
    private static final Pattern REACT_ACT_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s*\\{[^}]*\\bact\\b[^}]*}\\s*from\\s*([\"'])react\\1");
    private static final Pattern ACT_DECLARATION = Pattern.compile(
            "\\b(?:const|let|var|function|class)\\s+act\\b");
    private static final Pattern IMPLICIT_REF_ASSIGNMENT = Pattern.compile(
            "\\bref\\s*=\\s*\\{\\s*(?<parameter>" + IDENTIFIER + ")\\s*=>\\s*\\(\\s*" +
            "(?<target>" + IDENTIFIER + "(?:\\s*[.]\\s*" + IDENTIFIER + ")*)\\s*=\\s*" +
            "\\k<parameter>\\s*\\)\\s*}");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic React DOM 19 source constructs";
    }

    @Override
    public String getDescription() {
        return "Migrate a single unambiguous default, namespace, or sole named render/hydrate root, the exact " +
               "act test-utils import, and assignment ref callbacks while excluding comments and literals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!ReactDomSourceText.isSupported(visited)) {
                    return visited;
                }
                String source = migrateLegacyRoot(visited.getText());
                source = migrateSoleNamedRoot(source);
                source = migrateActImport(source);
                source = ReactDomSourceText.replaceCodeMatches(source, IMPLICIT_REF_ASSIGNMENT, match ->
                        "ref={" + match.group("parameter") + " => { " +
                        match.group("target").replaceAll("\\s+", "") + " = " + match.group("parameter") + "; }}");
                return visited.withText(source);
            }
        };
    }

    private static String migrateLegacyRoot(String source) {
        String defaultAlias = ReactDomSourceText.firstCodeGroup(source, REACT_DOM_IMPORT, "default");
        String namespaceAlias = ReactDomSourceText.firstCodeGroup(source, REACT_DOM_IMPORT, "namespace");
        String alias = defaultAlias == null ? namespaceAlias : defaultAlias;
        if (alias == null || ReactDomSourceText.hasCodeMatch(source, CLIENT_IMPORT)) {
            return source;
        }
        Pattern allLegacyCalls = Pattern.compile("\\b" + Pattern.quote(alias) +
                "\\s*[.]\\s*(?:render|hydrate)\\s*\\(");
        if (ReactDomSourceText.countCodeMatches(source, allLegacyCalls) != 1) {
            return source;
        }
        String container = simpleContainer();
        Pattern render = rootCall(Pattern.quote(alias) + "\\s*[.]\\s*render", container);
        Pattern hydrate = rootCall(Pattern.quote(alias) + "\\s*[.]\\s*hydrate", container);
        if (ReactDomSourceText.countCodeMatches(source, render) == 1 && canCreateRoot(source)) {
            String withImport = addClientImport(source, "createRoot");
            String newline = newline(source);
            return ReactDomSourceText.replaceCodeMatches(withImport, render, match ->
                    match.group("indent") + "const root = createRoot(" + match.group("container") + ");" + newline +
                    match.group("indent") + "root.render(" + match.group("element") + ");");
        }
        if (ReactDomSourceText.countCodeMatches(source, hydrate) == 1 &&
            !ReactDomSourceText.hasCodeMatch(source, Pattern.compile("\\bhydrateRoot\\b"))) {
            String withImport = addClientImport(source, "hydrateRoot");
            return ReactDomSourceText.replaceCodeMatches(withImport, hydrate, match ->
                    match.group("indent") + "hydrateRoot(" + match.group("container") + ", " +
                    match.group("element") + ");");
        }
        return source;
    }

    private static String migrateSoleNamedRoot(String source) {
        if (ReactDomSourceText.hasCodeMatch(source, CLIENT_IMPORT)) {
            return source;
        }
        String api = ReactDomSourceText.firstCodeGroup(source, NAMED_ROOT_IMPORT, "api");
        if (api == null || ReactDomSourceText.countCodeMatches(source,
                Pattern.compile("\\b" + api + "\\s*\\(")) != 1) {
            return source;
        }
        Pattern call = rootCall(api, simpleContainer());
        if (ReactDomSourceText.countCodeMatches(source, call) != 1) {
            return source;
        }
        String clientApi = "render".equals(api) ? "createRoot" : "hydrateRoot";
        if (("render".equals(api) && !canCreateRoot(source)) ||
            ("hydrate".equals(api) && ReactDomSourceText.hasCodeMatch(source, Pattern.compile("\\bhydrateRoot\\b")))) {
            return source;
        }
        String migrated = ReactDomSourceText.replaceCodeMatches(source, NAMED_ROOT_IMPORT, match ->
                match.group("indent") + "import { " + clientApi + " } from " + match.group("quote") +
                "react-dom/client" + match.group("quote") + ";");
        String lineBreak = newline(source);
        return ReactDomSourceText.replaceCodeMatches(migrated, call, match -> "render".equals(api) ?
                match.group("indent") + "const root = createRoot(" + match.group("container") + ");" + lineBreak +
                match.group("indent") + "root.render(" + match.group("element") + ");" :
                match.group("indent") + "hydrateRoot(" + match.group("container") + ", " +
                match.group("element") + ");");
    }

    private static Pattern rootCall(String receiver, String container) {
        return Pattern.compile("(?m)^(?<indent>[ \\t]*)" + receiver +
                "\\s*\\(\\s*(?<element><[^;\\r\\n]+>)\\s*,\\s*(?<container>" + container +
                ")\\s*\\)\\s*;?[ \\t]*$");
    }

    private static String simpleContainer() {
        return "(?:document\\.getElementById\\(\\s*[\"'][^\"'\\r\\n]+[\"']\\s*\\)|" +
               "document\\.querySelector\\(\\s*[\"'][^\"'\\r\\n]+[\"']\\s*\\)|" + IDENTIFIER + ")";
    }

    private static boolean canCreateRoot(String source) {
        return !ReactDomSourceText.hasCodeMatch(source, Pattern.compile("\\b(?:createRoot|root)\\b"));
    }

    private static String addClientImport(String source, String api) {
        return ReactDomSourceText.replaceCodeMatches(source, REACT_DOM_IMPORT, match ->
                match.group("indent") + "import { " + api + " } from " + match.group("quote") +
                "react-dom/client" + match.group("quote") + ";" + newline(source) + match.group());
    }

    private static String migrateActImport(String source) {
        if (ReactDomSourceText.hasCodeMatch(source, REACT_ACT_IMPORT) ||
            ReactDomSourceText.hasCodeMatch(source, ACT_DECLARATION)) {
            return source;
        }
        return ReactDomSourceText.replaceCodeMatches(source, ACT_IMPORT, match ->
                match.group("indent") + "import { act } from " + match.group("quote") + "react" +
                match.group("quote") + match.group("semi"));
    }

    private static String newline(String source) {
        return source.contains("\r\n") ? "\r\n" : "\n";
    }
}
