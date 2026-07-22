package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrades only spreadsheet-listed scalar RxJS declarations in direct dependency sections. */
public final class UpgradeSelectedRxjsDependency extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> VERSIONS = Set.of("6.5.5", "6.6.7");

    @Override
    public String getDisplayName() {
        return "Upgrade selected RxJS dependency declarations";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct scalar RxJS declarations for the spreadsheet-listed 6.5.5 and 6.6.7 " +
               "versions, including exact, caret, and tilde forms, to 7.8.2.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"rxjs".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) || !isSelected(declaration)) {
                    return visited;
                }

                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member section) ||
                    !SECTIONS.contains(key(section))) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"7.8.2\"").withValue("7.8.2"));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }
        };
    }

    private static boolean isSelected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~") ?
                declaration.substring(1) : declaration;
        return VERSIONS.contains(candidate) && declaration.matches("[~^]?" +
                java.util.regex.Pattern.quote(candidate));
    }

    private static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        if (member.getKey() instanceof Json.Identifier identifier) {
            return identifier.getName();
        }
        return "";
    }
}
