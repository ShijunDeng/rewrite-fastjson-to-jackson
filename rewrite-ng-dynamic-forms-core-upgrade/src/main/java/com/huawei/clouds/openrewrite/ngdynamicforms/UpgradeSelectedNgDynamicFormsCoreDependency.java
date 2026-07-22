package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Updates only npm single-version declarations explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedNgDynamicFormsCoreDependency extends Recipe {
    static final String PACKAGE = "@ng-dynamic-forms/core";
    static final String TARGET_VERSION = "18.0.0";
    static final Set<String> SOURCE_VERSIONS = Set.of("14.0.0", "15.0.0", "16.0.0", "17.0.0");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected @ng-dynamic-forms/core declarations to 18.0.0";
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
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
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
}
