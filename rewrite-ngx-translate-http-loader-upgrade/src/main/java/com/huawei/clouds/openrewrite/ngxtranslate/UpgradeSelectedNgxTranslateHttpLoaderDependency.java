package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrades only scalar direct declarations selected by the migration spreadsheet. */
public final class UpgradeSelectedNgxTranslateHttpLoaderDependency extends Recipe {
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    static final Set<String> VERSIONS = Set.of("4.0.0", "6.0.0", "7.0.0", "8.0.0");

    @Override
    public String getDisplayName() {
        return "Upgrade selected @ngx-translate/http-loader declarations to 17.0.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only selected scalar declarations in direct package.json dependency sections without " +
               "changing ranges, protocols, aliases, lockfiles, nested metadata, or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"@ngx-translate/http-loader".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) || !isSelected(declaration)) {
                    return visited;
                }

                if (directDependencySection(getCursor()) == null) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"17.0.0\"").withValue("17.0.0"));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString()) &&
                       HttpLoaderSupport.isProjectPath(path);
            }
        };
    }

    private static boolean isSelected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) {
            candidate = candidate.substring(1);
        }
        return VERSIONS.contains(candidate) &&
               declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+");
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

    static String directDependencySection(Cursor memberCursor) {
        Cursor dependencyObject = memberCursor.getParentTreeCursor();
        Cursor sectionCursor = dependencyObject == null ? null : dependencyObject.getParentTreeCursor();
        if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member section) ||
            !SECTIONS.contains(key(section))) {
            return null;
        }
        Cursor rootObject = sectionCursor.getParentTreeCursor();
        Cursor documentCursor = rootObject == null ? null : rootObject.getParentTreeCursor();
        return rootObject != null && rootObject.getValue() instanceof Json.JsonObject root &&
               documentCursor != null && documentCursor.getValue() instanceof Json.Document document &&
               document.getValue() == root ? key(section) : null;
    }
}
