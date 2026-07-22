package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.text.PlainText;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RxjsSourceText {
    private static final Pattern SOURCE_EXTENSION = Pattern.compile(".*\\.(?:[cm]?[jt]sx?)$");

    private RxjsSourceText() {
    }

    static boolean isSupported(PlainText text) {
        return SOURCE_EXTENSION.matcher(text.getSourcePath().toString().toLowerCase(Locale.ROOT)).matches();
    }

    static boolean hasUnaliasedNamedImport(String source, String module, String symbol) {
        Pattern imports = Pattern.compile(
                "\\bimport\\s*\\{(?<names>[^}]*)}\\s*from\\s*(?<quote>[\"'])" +
                Pattern.quote(module) + "\\k<quote>"
        );
        boolean[] code = codePositions(source);
        Matcher matcher = imports.matcher(source);
        while (matcher.find()) {
            if (!code[matcher.start()]) {
                continue;
            }
            for (String imported : matcher.group("names").split(",")) {
                if (imported.trim().matches("(?:type\\s+)?" + Pattern.quote(symbol))) {
                    return true;
                }
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
                isIdentifierPart(source.charAt(index + before.length()))) {
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

    /** Marks JavaScript/TypeScript code while excluding comments and quoted/template-string content. */
    private static boolean[] codePositions(String source) {
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
