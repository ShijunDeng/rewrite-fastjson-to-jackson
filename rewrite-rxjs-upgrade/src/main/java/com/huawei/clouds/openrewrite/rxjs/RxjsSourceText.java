package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.text.PlainText;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RxjsSourceText {
    private static final Pattern SOURCE_EXTENSION = Pattern.compile(".*\\.(?:[cm]?[jt]sx?)$");

    private RxjsSourceText() {
    }

    static boolean isSupported(PlainText text) {
        return SOURCE_EXTENSION.matcher(text.getSourcePath().toString().toLowerCase(Locale.ROOT)).matches() &&
               !isGenerated(text.getSourcePath());
    }

    static boolean isGenerated(Path path) {
        String normalized = "/" + path.toString().replace('\\', '/').toLowerCase(Locale.ROOT) + "/";
        return normalized.contains("/node_modules/") || normalized.contains("/bower_components/") ||
               normalized.contains("/dist/") || normalized.contains("/build/") ||
               normalized.contains("/coverage/") || normalized.contains("/.next/") ||
               normalized.contains("/.nuxt/") || normalized.contains("/generated/");
    }

    static boolean hasUnaliasedNamedImport(String source, String module, String symbol) {
        return symbol.equals(namedImports(source, module).get(symbol));
    }

    /** Returns imported-name to local-name mappings for named ES imports from one exact module. */
    static Map<String, String> namedImports(String source, String module) {
        Pattern imports = Pattern.compile(
                "\\bimport\\s*\\{(?<names>[^}]*)}\\s*from\\s*(?<quote>[\"'])" +
                Pattern.quote(module) + "\\k<quote>"
        );
        boolean[] code = codePositions(source);
        Map<String, String> names = new LinkedHashMap<>();
        Matcher matcher = imports.matcher(source);
        while (matcher.find()) {
            if (!code[matcher.start()]) {
                continue;
            }
            for (String imported : matcher.group("names").split(",")) {
                String candidate = imported.trim().replaceFirst("^type\\s+", "");
                Matcher binding = Pattern.compile(
                        "(?<imported>[A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+as\\s+(?<local>[A-Za-z_$][A-Za-z0-9_$]*))?"
                ).matcher(candidate);
                if (binding.matches()) {
                    names.put(binding.group("imported"),
                            binding.group("local") == null ? binding.group("imported") : binding.group("local"));
                }
            }
        }
        return names;
    }

    static boolean hasRxjsReference(String source) {
        Pattern reference = Pattern.compile(
                "(?:\\bfrom\\s*[\"']rxjs(?:/[^\"']*)?[\"']|" +
                "\\bimport\\s*[\"']rxjs(?:/[^\"']*)?[\"']|" +
                "\\brequire\\s*\\(\\s*[\"']rxjs(?:/[^\"']*)?[\"']\\s*\\)|" +
                "\\bimport\\s*\\(\\s*[\"']rxjs(?:/[^\"']*)?[\"']\\s*\\))"
        );
        boolean[] code = codePositions(source);
        Matcher matcher = reference.matcher(source);
        while (matcher.find()) {
            if (code[matcher.start()]) {
                return true;
            }
        }
        return false;
    }

    static boolean hasLocalDeclaration(String source, String identifier) {
        return hasCodeMatch(source, Pattern.compile(
                "\\b(?:const|let|var|function|class|interface|type|enum|namespace)\\s+" +
                Pattern.quote(identifier) + "\\b"));
    }

    static boolean hasPotentialShadowingBinding(String source, String identifier) {
        String name = Pattern.quote(identifier);
        return hasLocalDeclaration(source, identifier) ||
               hasCodeMatch(source, Pattern.compile(
                       "\\b(?:const|let|var)\\s*\\{[^}\\r\\n]*\\b" + name + "\\b[^}\\r\\n]*}")) ||
               hasCodeMatch(source, Pattern.compile(
                       "(?:\\((?:[^,)]*,\\s*)*(?:\\.\\.\\.\\s*)?" + name +
                       "\\s*(?:[?:=,)]|$)[^)]*\\)|\\b" + name +
                       "\\b)\\s*(?::[^=\\r\\n]+)?=>")) ||
               hasCodeMatch(source, Pattern.compile(
                       "\\b(?:function(?:\\s+[A-Za-z_$][A-Za-z0-9_$]*)?|catch)\\s*" +
                       "\\((?:[^,)]*,\\s*)*(?:\\.\\.\\.\\s*)?" + name +
                       "\\s*(?:[?:=,)]|$)[^)]*\\)"));
    }

    static boolean hasAnyImportBinding(String source, String identifier) {
        String name = Pattern.quote(identifier);
        Pattern importWithBinding = Pattern.compile(
                "\\bimport\\s+(?:type\\s+)?(?:" + name + "\\s*(?:,|from\\b)|" +
                "\\*\\s+as\\s+" + name + "\\b|\\{(?<names>[^}]*)})"
        );
        boolean[] code = codePositions(source);
        Matcher matcher = importWithBinding.matcher(source);
        while (matcher.find()) {
            if (!code[matcher.start()]) continue;
            if (matcher.group("names") == null) return true;
            for (String candidate : matcher.group("names").split(",")) {
                Matcher named = Pattern.compile(
                        "(?:type\\s+)?[A-Za-z_$][A-Za-z0-9_$]*(?:\\s+as\\s+(?<local>[A-Za-z_$][A-Za-z0-9_$]*))?"
                ).matcher(candidate.trim());
                if (named.matches()) {
                    String local = named.group("local") == null
                            ? candidate.trim().replaceFirst("^type\\s+", "") : named.group("local");
                    if (identifier.equals(local)) return true;
                }
            }
        }
        return false;
    }

    static boolean hasCodeMatch(String source, Pattern pattern) {
        boolean[] code = codePositions(source);
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (code[matcher.start()]) {
                return true;
            }
        }
        return false;
    }

    static String replaceIdentifierInCode(String source, String before, String after) {
        boolean[] code = codePositions(source);
        StringBuilder result = new StringBuilder(source.length());
        int last = 0;
        for (int index = 0; index <= source.length() - before.length(); index++) {
            if (!code[index] || !source.startsWith(before, index) ||
                index > 0 && isIdentifierPart(source.charAt(index - 1)) ||
                index + before.length() < source.length() &&
                isIdentifierPart(source.charAt(index + before.length())) || isPropertyAccess(source, index)) {
                continue;
            }
            result.append(source, last, index).append(after);
            last = index + before.length();
            index = last - 1;
        }
        return last == 0 ? source : result.append(source, last, source.length()).toString();
    }

    static String replaceCodeMatches(String source, Pattern pattern, Function<Matcher, String> replacement) {
        boolean[] code = codePositions(source);
        Matcher matcher = pattern.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        int last = 0;
        boolean changed = false;
        while (matcher.find()) {
            if (!code[matcher.start()]) {
                continue;
            }
            result.append(source, last, matcher.start()).append(replacement.apply(matcher));
            last = matcher.end();
            changed = true;
        }
        return changed ? result.append(source, last, source.length()).toString() : source;
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    private static boolean isPropertyAccess(String source, int index) {
        for (int previous = index - 1; previous >= 0; previous--) {
            if (!Character.isWhitespace(source.charAt(previous))) {
                return source.charAt(previous) == '.';
            }
        }
        return false;
    }

    /** Marks JavaScript/TypeScript code while excluding comments and quoted/template-string content. */
    static boolean[] codePositions(String source) {
        boolean[] code = new boolean[source.length() + 1];
        State state = State.CODE;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            code[index] = state == State.CODE;

            if (state == State.LINE_COMMENT) {
                if (current == '\n' || current == '\r') {
                    state = State.CODE;
                }
                continue;
            }
            if (state == State.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    code[index + 1] = false;
                    index++;
                    state = State.CODE;
                }
                continue;
            }
            if (state != State.CODE) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (state.matches(current)) {
                    state = State.CODE;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                code[index + 1] = false;
                index++;
                state = State.LINE_COMMENT;
            } else if (current == '/' && next == '*') {
                code[index + 1] = false;
                index++;
                state = State.BLOCK_COMMENT;
            } else if (current == '\'') {
                state = State.SINGLE_QUOTE;
            } else if (current == '"') {
                state = State.DOUBLE_QUOTE;
            } else if (current == '`') {
                state = State.TEMPLATE;
            }
        }
        code[source.length()] = state == State.CODE;
        return code;
    }

    private enum State {
        CODE('\0'), SINGLE_QUOTE('\''), DOUBLE_QUOTE('"'), TEMPLATE('`'), LINE_COMMENT('\0'), BLOCK_COMMENT('\0');

        private final char terminator;

        State(char terminator) {
            this.terminator = terminator;
        }

        boolean matches(char character) {
            return character == terminator;
        }
    }
}
