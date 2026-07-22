package com.huawei.clouds.openrewrite.tweenjs;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrade only Tween.js releases explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedTweenJsDependency extends Recipe {
    static final Set<String> SOURCES = Set.of("19.0.0", "20.0.3");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected @tweenjs/tween.js declarations to 23.1.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact, caret, or tilde scalar declarations for spreadsheet-visible releases without " +
               "changing compound ranges, protocols, variables, metadata, lockfiles or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !TweenJsSupport.PACKAGE.equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration) || !selected(declaration) ||
                    !isDirectDependency()) return visited;
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withSource(quote + TweenJsSupport.TARGET + quote)
                        .withValue(TweenJsSupport.TARGET));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private boolean isDirectDependency() {
                Cursor object = getCursor().getParent();
                Cursor section = object == null ? null : object.getParent();
                Cursor root = section == null ? null : section.getParent();
                Cursor document = root == null ? null : root.getParent();
                return section != null && section.getValue() instanceof Json.Member parent &&
                       SECTIONS.contains(key(parent)) && document != null &&
                       document.getValue() instanceof Json.Document;
            }
        };
    }

    static boolean selected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) && declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+");
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }
}
