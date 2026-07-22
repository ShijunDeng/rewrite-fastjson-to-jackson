package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonKey;

import java.nio.file.Path;

/** Removes configuration made obsolete by Ivy-only compilation and the removal of ngcc. */
public final class MigrateDeterministicAngularCompilerJson extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular compiler JSON configuration";
    }

    @Override
    public String getDescription() {
        return "Removes enableIvy:true, removes an ngcc-only postinstall script, and corrects the unambiguous " +
               "angularCompilerOptions compliationMode typo.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String name = UpgradeSelectedAngularCompilerDependency.key(visited);
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if (file.startsWith("tsconfig") && file.endsWith(".json") &&
                    "angularCompilerOptions".equals(parentKey())) {
                    if ("enableIvy".equals(name) && visited.getValue() instanceof Json.Literal literal &&
                        Boolean.TRUE.equals(literal.getValue())) return null;
                    if ("compliationMode".equals(name) && !hasSibling("compilationMode")) {
                        return visited.withKey(renameKey(visited.getKey(), "compilationMode"));
                    }
                }
                if ("package.json".equals(file) && "scripts".equals(parentKey()) && "postinstall".equals(name) &&
                    visited.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String command &&
                    command.matches("^(?:npx\\s+)?ngcc(?:\\s+[A-Za-z0-9_./=,@+:-]+)*$")) return null;
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedAngularCompilerDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasSibling(String key) {
                Json.JsonObject object = getCursor().firstEnclosing(Json.JsonObject.class);
                return object != null && object.getMembers().stream().anyMatch(value ->
                        value instanceof Json.Member sibling &&
                        key.equals(UpgradeSelectedAngularCompilerDependency.key(sibling)));
            }
        };
    }

    private static JsonKey renameKey(JsonKey key, String replacement) {
        if (key instanceof Json.Identifier identifier) return identifier.withName(replacement);
        if (key instanceof Json.Literal literal) {
            String quote = literal.getSource().startsWith("'") ? "'" : "\"";
            return literal.withValue(replacement).withSource(quote + replacement + quote);
        }
        return key;
    }
}
