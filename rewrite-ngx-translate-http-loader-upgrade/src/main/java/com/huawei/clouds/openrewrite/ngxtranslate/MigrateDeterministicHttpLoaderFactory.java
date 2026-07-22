package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.JavaScriptParser;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migrates the common same-file Angular NgModule factory only when its constructor arguments and
 * provider wiring are completely deterministic. Cross-file factories and expression-valued paths
 * deliberately remain for the audit recipe.
 */
public final class MigrateDeterministicHttpLoaderFactory extends Recipe {
    private static final String LITERAL = "(?:'[^'\\r\\n]*'|\"[^\"\\r\\n]*\"|`[^`$\\r\\n]*`)";
    private static final Pattern FACTORY = Pattern.compile(
            "(?m)^[ \\t]*(?:export[ \\t]+)?function[ \\t]+(?<name>[A-Za-z_$][\\w$]*)[ \\t]*\\(" +
            "[ \\t]*(?<http>[A-Za-z_$][\\w$]*)[ \\t]*:[ \\t]*HttpClient[ \\t]*\\)[ \\t]*" +
            "(?::[ \\t]*[A-Za-z_$][\\w$<>., \\t]*)?[ \\t]*\\{[ \\t\\r\\n]*" +
            "return[ \\t]+new[ \\t]+TranslateHttpLoader[ \\t]*\\([ \\t]*\\k<http>" +
            "(?:[ \\t]*,[ \\t]*(?<prefix>" + LITERAL + ")" +
            "(?:[ \\t]*,[ \\t]*(?<suffix>" + LITERAL + "))?)?[ \\t]*\\)[ \\t]*;?[ \\t\\r\\n]*" +
            "\\}[ \\t]*(?:\\r?\\n(?:[ \\t]*\\r?\\n)?)?"
    );
    private static final Pattern MODULE_CALL = Pattern.compile(
            "\\bTranslateModule[ \\t\\r\\n]*\\.[ \\t\\r\\n]*(?:forRoot|forChild)[ \\t\\r\\n]*\\("
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic ngx-translate HTTP loader factories";
    }

    @Override
    public String getDescription() {
        return "Replace an exact same-file HttpClient factory and raw TranslateLoader provider with " +
               "provideTranslateHttpLoader while preserving literal prefix and suffix configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!HttpLoaderSupport.isProjectPath(cu.getSourcePath())) {
                    return cu;
                }
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                String migrated = migrate(visited.printAll());
                if (migrated.equals(visited.printAll())) return visited;

                Path path = visited.getSourcePath();
                Parser.Input input = Parser.Input.fromString(path, migrated, visited.getCharset());
                JS.CompilationUnit parsed = (JS.CompilationUnit) JavaScriptParser.builder().build()
                        .parseInputs(List.of(input), Path.of("."), ctx)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Unable to parse migrated " + path));
                return parsed.withId(visited.getId())
                        .withSourcePath(path)
                        .withFileAttributes(visited.getFileAttributes())
                        .withCharsetBomMarked(visited.isCharsetBomMarked())
                        .withMarkers(visited.getMarkers());
            }
        };
    }

    private static String migrate(String source) {
        boolean[] code = codePositions(source);
        List<Match> factories = codeMatches(source, FACTORY, code);
        if (factories.size() != 1 ||
            !hasExactNamedImport(source, HttpLoaderSupport.HTTP_LOADER, "TranslateHttpLoader", code) ||
            !hasExactNamedImport(source, HttpLoaderSupport.CORE, "TranslateLoader", code) ||
            !hasExactNamedImport(source, HttpLoaderSupport.CORE, "TranslateModule", code) ||
            !hasExactNamedImport(source, HttpLoaderSupport.ANGULAR_HTTP, "HttpClient", code)) {
            return source;
        }

        Match factory = factories.get(0);
        String name = factory.group("name");
        Pattern providerPattern = Pattern.compile(
                "\\bloader[ \\t\\r\\n]*:[ \\t\\r\\n]*\\{[ \\t\\r\\n]*" +
                "provide[ \\t\\r\\n]*:[ \\t\\r\\n]*TranslateLoader[ \\t\\r\\n]*,[ \\t\\r\\n]*" +
                "useFactory[ \\t\\r\\n]*:[ \\t\\r\\n]*" + Pattern.quote(name) +
                "[ \\t\\r\\n]*,[ \\t\\r\\n]*deps[ \\t\\r\\n]*:[ \\t\\r\\n]*" +
                "\\[[ \\t\\r\\n]*HttpClient[ \\t\\r\\n]*][ \\t\\r\\n]*,?[ \\t\\r\\n]*}"
        );
        List<Match> providers = codeMatches(source, providerPattern, code);
        if (providers.size() != 1 || !insideTranslateModuleCall(source, providers.get(0), code)) {
            return source;
        }

        String prefix = factory.group("prefix");
        String suffix = factory.group("suffix");
        String options = prefix == null ? "" : suffix == null ?
                "({ prefix: " + prefix + " })" :
                "({ prefix: " + prefix + ", suffix: " + suffix + " })";
        String provider = "loader: provideTranslateHttpLoader" + (options.isEmpty() ? "()" : options);

        List<Replacement> replacements = List.of(
                new Replacement(providers.get(0).start, providers.get(0).end, provider),
                new Replacement(factory.start, factory.end, "")
        );
        String migrated = apply(source, replacements);
        migrated = replaceNamedImport(migrated, "@ngx-translate/http-loader",
                "TranslateHttpLoader", "provideTranslateHttpLoader");
        migrated = removeUnusedNamedImport(migrated, "@ngx-translate/core", "TranslateLoader");
        migrated = removeUnusedNamedImport(migrated, "@angular/common/http", "HttpClient");
        return migrated;
    }

    private static boolean hasExactNamedImport(String source, String module, String wanted, boolean[] code) {
        Pattern importPattern = Pattern.compile(
                "(?m)^[ \\t]*import[ \\t]*\\{(?<body>[^}]+)}[ \\t]*from[ \\t]*(?<quote>['\"])" +
                Pattern.quote(module) + "\\k<quote>[ \\t]*;?[ \\t]*(?:\\r?\\n)?"
        );
        for (Match match : codeMatches(source, importPattern, code)) {
            for (String specifier : match.group("body").split(",")) {
                if (wanted.equals(specifier.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean insideTranslateModuleCall(String source, Match provider, boolean[] code) {
        for (Match call : codeMatches(source, MODULE_CALL, code)) {
            int open = source.indexOf('(', call.start);
            if (open < 0 || open >= provider.start) continue;
            int parentheses = 0;
            int braces = 0;
            int brackets = 0;
            for (int index = open; index <= provider.start; index++) {
                if (!code[index]) continue;
                if (index == provider.start) {
                    return parentheses == 1 && braces == 1 && brackets == 0;
                }
                char token = source.charAt(index);
                if (token == '(') parentheses++;
                else if (token == ')' && --parentheses == 0) break;
                else if (token == '{') braces++;
                else if (token == '}') braces--;
                else if (token == '[') brackets++;
                else if (token == ']') brackets--;
            }
        }
        return false;
    }

    private static String removeUnusedNamedImport(String source, String module, String identifier) {
        Pattern importPattern = namedImport(module);
        Matcher matcher = importPattern.matcher(source);
        while (matcher.find()) {
            List<String> names = specifiers(matcher.group("body"));
            if (!names.contains(identifier)) {
                continue;
            }
            String withoutImport = source.substring(0, matcher.start()) + source.substring(matcher.end());
            if (containsIdentifier(withoutImport, identifier)) {
                return source;
            }
            names.remove(identifier);
            String replacement = names.isEmpty() ? "" : renderImport(matcher, names, module);
            return source.substring(0, matcher.start()) + replacement + source.substring(matcher.end());
        }
        return source;
    }

    private static String replaceNamedImport(String source, String module, String oldName,
                                             String newName) {
        Pattern pattern = namedImport(module);
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            List<String> names = specifiers(matcher.group("body"));
            int index = names.indexOf(oldName);
            if (index < 0) {
                continue;
            }
            if (names.contains(newName)) {
                names.remove(index);
            } else {
                names.set(index, newName);
            }
            String replacement = renderImport(matcher, names, module);
            return source.substring(0, matcher.start()) + replacement + source.substring(matcher.end());
        }
        return source;
    }

    private static Pattern namedImport(String module) {
        return Pattern.compile(
                "(?m)^(?<indent>[ \\t]*)import[ \\t]*\\{(?<body>[^}]+)}[ \\t]*from[ \\t]*" +
                "(?<quote>['\"])" + Pattern.quote(module) + "\\k<quote>[ \\t]*;?[ \\t]*(?<newline>\\r?\\n)?"
        );
    }

    private static List<String> specifiers(String body) {
        List<String> names = new ArrayList<>();
        for (String specifier : body.split(",")) {
            String name = specifier.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private static String renderImport(Matcher matcher, List<String> names, String module) {
        String newline = matcher.group("newline") == null ? "" : matcher.group("newline");
        return matcher.group("indent") + "import { " + String.join(", ", names) + " } from " +
               matcher.group("quote") + module + matcher.group("quote") + ";" + newline;
    }

    private static boolean containsIdentifier(String source, String identifier) {
        return Pattern.compile("(?<![\\w$])" + Pattern.quote(identifier) + "(?![\\w$])")
                .matcher(source).find();
    }

    private static List<Match> codeMatches(String source, Pattern pattern, boolean[] code) {
        List<Match> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            int codeIndex = matcher.start();
            while (codeIndex < matcher.end() && Character.isWhitespace(source.charAt(codeIndex))) {
                codeIndex++;
            }
            if (codeIndex < code.length && code[codeIndex]) {
                matches.add(new Match(matcher));
            }
        }
        return matches;
    }

    private static String apply(String source, List<Replacement> replacements) {
        List<Replacement> descending = new ArrayList<>(replacements);
        descending.sort((left, right) -> Integer.compare(right.start, left.start));
        String result = source;
        for (Replacement replacement : descending) {
            result = result.substring(0, replacement.start) + replacement.value + result.substring(replacement.end);
        }
        return result;
    }

    private static boolean[] codePositions(String source) {
        boolean[] code = new boolean[source.length() + 1];
        State state = State.CODE;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            code[index] = state == State.CODE;
            if (state == State.LINE_COMMENT) {
                if (current == '\n' || current == '\r') state = State.CODE;
            } else if (state == State.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    code[index + 1] = false;
                    index++;
                    state = State.CODE;
                }
            } else if (state != State.CODE) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == state.terminator) state = State.CODE;
            } else if (current == '/' && next == '/') {
                code[index + 1] = false;
                index++;
                state = State.LINE_COMMENT;
            } else if (current == '/' && next == '*') {
                code[index + 1] = false;
                index++;
                state = State.BLOCK_COMMENT;
            } else if (current == '\'') state = State.SINGLE_QUOTE;
            else if (current == '"') state = State.DOUBLE_QUOTE;
            else if (current == '`') state = State.TEMPLATE;
        }
        code[source.length()] = state == State.CODE;
        return code;
    }

    private static final class Match {
        private final int start;
        private final int end;
        private final Map<String, String> groups = new HashMap<>();

        private Match(Matcher matcher) {
            this.start = matcher.start();
            this.end = matcher.end();
            for (String name : List.of("name", "http", "prefix", "suffix", "body")) {
                try {
                    groups.put(name, matcher.group(name));
                } catch (IllegalArgumentException ignored) {
                    // The patterns intentionally expose different named groups.
                }
            }
        }

        private String group(String name) {
            return groups.get(name);
        }
    }

    private record Replacement(int start, int end, String value) {
    }

    private enum State {
        CODE('\0'), SINGLE_QUOTE('\''), DOUBLE_QUOTE('"'), TEMPLATE('`'), LINE_COMMENT('\0'), BLOCK_COMMENT('\0');

        private final char terminator;

        State(char terminator) {
            this.terminator = terminator;
        }
    }
}
