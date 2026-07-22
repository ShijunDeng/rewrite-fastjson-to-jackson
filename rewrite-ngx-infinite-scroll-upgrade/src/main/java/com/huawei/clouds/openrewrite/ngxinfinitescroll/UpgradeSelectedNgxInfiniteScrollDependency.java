package com.huawei.clouds.openrewrite.ngxinfinitescroll;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Updates only npm declarations explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedNgxInfiniteScrollDependency extends Recipe {
    static final String PACKAGE = "ngx-infinite-scroll";
    static final String TARGET_VERSION = "17.0.1";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "9.1.0", "10.0.1", "13.0.1", "13.1.0", "14.0.1", "16.0.0"
    );
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", "coverage"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected ngx-infinite-scroll declarations to 17.0.1";
    }

    @Override
    public String getDescription() {
        return "Updates exact, caret, or tilde direct declarations explicitly visible in the spreadsheet without " +
               "changing patches, ranges, protocols, central owners, metadata, or lockfiles.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !PACKAGE.equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration) || !selected(declaration) ||
                    !isDirectDependency()) return visited;
                return visited.withValue(literal.withValue(TARGET_VERSION).withSource('"' + TARGET_VERSION + '"'));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return isEditablePackageJson(path);
            }

            private boolean isDirectDependency() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor section = object == null ? null : object.getParentTreeCursor();
                Cursor rootObject = section == null ? null : section.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return section != null && section.getValue() instanceof Json.Member &&
                       SECTIONS.contains(key((Json.Member) section.getValue())) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    static boolean selected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) candidate = candidate.substring(1);
        return SOURCE_VERSIONS.contains(candidate) && declaration.matches("[~^]?" + Pattern.quote(candidate));
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }

    static boolean isEditablePackageJson(Path path) {
        if (path.getFileName() == null || !"package.json".equals(path.getFileName().toString())) return false;
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }
}
