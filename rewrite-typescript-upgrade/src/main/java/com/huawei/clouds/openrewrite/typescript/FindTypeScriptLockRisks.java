package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.Markers;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Marks stale TypeScript lock ownership without fabricating resolved URLs or integrity hashes. */
public final class FindTypeScriptLockRisks extends Recipe {
    private static final String MESSAGE =
            "Lockfile still owns a non-target TypeScript resolution; regenerate it with the repository's pinned package manager and review resolved URL, integrity, peers, and workspace importers";
    private static final Pattern OLD_LOCK_VERSION = Pattern.compile(
            "(?s)(?<![a-z0-9_@-])typescript(?:@|:)\\s*(?:[^\\n]{0,80}|\\n.{0,160}?)(?:[~^]?(?:3|4|5)\\.)");

    @Override
    public String getDisplayName() { return "Find stale TypeScript lockfile entries"; }

    @Override
    public String getDescription() {
        return "Mark non-target TypeScript ownership in package-lock/npm-shrinkwrap, pnpm-lock, and Yarn lockfiles without inventing registry metadata.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !TypeScriptSupport.isProjectPath(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                if (tree instanceof Json.Document document &&
                    ("package-lock.json".equals(file) || "npm-shrinkwrap.json".equals(file))) {
                    return markJson(document, ctx);
                }
                if (tree instanceof PlainText text &&
                    ("yarn.lock".equals(file) || "pnpm-lock.yaml".equals(file) || "pnpm-lock.yml".equals(file)) &&
                    ownsNonTargetTypeScript(text.getText())) {
                    return markOwnedTokens(text);
                }
                return tree;
            }
        };
    }

    private static Json.Document markJson(Json.Document document, ExecutionContext ctx) {
        return (Json.Document) new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext p) {
                Json.Member visited = super.visitMember(member, p);
                String key = TypeScriptSupport.key(visited);
                String value = TypeScriptSupport.literalString(visited);
                if (TypeScriptSupport.PACKAGE.equals(key) && value != null && !TypeScriptSupport.isTarget(value) &&
                    lockOwnershipPath()) return TypeScriptSupport.markValue(visited, MESSAGE);
                if ("version".equals(key) && value != null && !TypeScriptSupport.TARGET.equals(value) &&
                    typeScriptEntryOwner()) return TypeScriptSupport.markValue(visited, MESSAGE);
                return visited;
            }

            private boolean lockOwnershipPath() {
                return getCursor().getPathAsStream().filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                        .map(TypeScriptSupport::key).anyMatch(name ->
                                TypeScriptSupport.DEPENDENCY_SECTIONS.contains(name) || "packages".equals(name));
            }

            private boolean typeScriptEntryOwner() {
                return getCursor().getPathAsStream().filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                        .map(TypeScriptSupport::key).anyMatch(name ->
                                TypeScriptSupport.PACKAGE.equals(name) || "node_modules/typescript".equals(name));
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean ownsNonTargetTypeScript(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean owner = false;
        for (int start = lower.indexOf(TypeScriptSupport.PACKAGE); start >= 0;
             start = lower.indexOf(TypeScriptSupport.PACKAGE, start + TypeScriptSupport.PACKAGE.length())) {
            if (isOwnerAt(lower, start)) {
                owner = true;
                break;
            }
        }
        if (!owner) return false;
        return OLD_LOCK_VERSION.matcher(lower).find() || !lower.contains(TypeScriptSupport.TARGET);
    }

    private static PlainText markOwnedTokens(PlainText text) {
        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        int scan = 0;
        int match;
        while ((match = source.toLowerCase(Locale.ROOT).indexOf(TypeScriptSupport.PACKAGE, scan)) >= 0) {
            scan = match + TypeScriptSupport.PACKAGE.length();
            if (!isOwnerAt(source.toLowerCase(Locale.ROOT), match)) continue;
            if (match > cursor) snippets.add(new PlainText.Snippet(
                    Tree.randomId(), Markers.EMPTY, source.substring(cursor, match)));
            snippets.add(TypeScriptSupport.mark(new PlainText.Snippet(
                    Tree.randomId(), Markers.EMPTY, source.substring(match, scan)), MESSAGE));
            cursor = scan;
        }
        if (snippets.isEmpty()) return text;
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(
                Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static boolean isOwnerAt(String source, int start) {
        if (start > 0) {
            char before = source.charAt(start - 1);
            if (Character.isLetterOrDigit(before) || before == '_' || before == '-' || before == '@') return false;
            if (before == '/') {
                int scopeEnd = start - 1;
                int scopeStart = scopeEnd - 1;
                while (scopeStart >= 0 && (Character.isLetterOrDigit(source.charAt(scopeStart)) ||
                        source.charAt(scopeStart) == '_' || source.charAt(scopeStart) == '-' ||
                        source.charAt(scopeStart) == '.')) scopeStart--;
                if (scopeStart >= 0 && source.charAt(scopeStart) == '@') return false;
            }
        }
        int end = start + TypeScriptSupport.PACKAGE.length();
        if (end >= source.length()) return false;
        char after = source.charAt(end);
        if (after == '@' || after == ':') return true;
        return start >= "node_modules/".length() &&
               source.regionMatches(start - "node_modules/".length(),
                       "node_modules/", 0, "node_modules/".length()) &&
               (after == '"' || after == '\'' || after == ':' || Character.isWhitespace(after));
    }
}
