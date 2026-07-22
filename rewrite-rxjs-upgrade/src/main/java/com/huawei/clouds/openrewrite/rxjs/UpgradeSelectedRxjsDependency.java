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
    static final String TARGET_VERSION = "7.8.2";
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "6.5.5", "6.6.7", "7.3.0", "7.4.0", "7.5.5",
            "7.5.6", "7.5.7", "7.6.0", "7.8.0", "7.8.1"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected RxJS dependency declarations";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct scalar RxJS declarations for the ten spreadsheet-listed source " +
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

                if (directDependencySection(getCursor()) == null) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"" + TARGET_VERSION + "\"").withValue(TARGET_VERSION));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString()) &&
                       !RxjsSourceText.isGenerated(path);
            }
        };
    }

    static boolean isSelected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~") ?
                declaration.substring(1) : declaration;
        return SOURCE_VERSIONS.contains(candidate) && declaration.matches("[~^]?" +
                java.util.regex.Pattern.quote(candidate));
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        if (member.getKey() instanceof Json.Identifier identifier) {
            return identifier.getName();
        }
        return "";
    }

    /** Returns the top-level direct dependency section owning the current member, or {@code null}. */
    static String directDependencySection(Cursor memberCursor) {
        Cursor dependencyObject = memberCursor.getParentTreeCursor();
        Cursor sectionCursor = dependencyObject == null ? null : dependencyObject.getParentTreeCursor();
        if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member section) ||
            !SECTIONS.contains(key(section)) || !isTopLevelMember(sectionCursor)) {
            return null;
        }
        return key(section);
    }

    static boolean isTopLevelMember(Cursor memberCursor) {
        Cursor rootObject = memberCursor.getParentTreeCursor();
        Cursor documentCursor = rootObject == null ? null : rootObject.getParentTreeCursor();
        return rootObject != null && rootObject.getValue() instanceof Json.JsonObject root &&
               documentCursor != null && documentCursor.getValue() instanceof Json.Document document &&
               document.getValue() == root;
    }
}
