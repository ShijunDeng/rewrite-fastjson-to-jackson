package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/**
 * Upgrades only selected scalar direct dependency declarations. The explicit parent checks avoid
 * matching lockfile entries, package-manager metadata, and strings nested in array-shaped values.
 */
public final class UpgradeSelectedNgxTranslateCoreDependency extends Recipe {
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    static final Set<String> VERSIONS = Set.of("11.0.1", "13.0.0", "14.0.0", "15.0.0");

    @Override
    public String getDisplayName() {
        return "Upgrade selected scalar @ngx-translate/core dependency declarations";
    }

    @Override
    public String getDescription() {
        return "Update selected direct @ngx-translate/core strings to 17.0.0 without changing arrays, objects, protocols, aliases, or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member m = super.visitMember(member, ctx);
                if (!isPackageJson() || !"@ngx-translate/core".equals(key(m)) ||
                        !(m.getValue() instanceof Json.Literal)) {
                    return m;
                }

                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member) ||
                        !SECTIONS.contains(key((Json.Member) sectionCursor.getValue()))) {
                    return m;
                }
                Cursor rootObjectCursor = sectionCursor.getParentTreeCursor();
                Cursor documentCursor = rootObjectCursor == null ? null : rootObjectCursor.getParentTreeCursor();
                if (rootObjectCursor == null || !(rootObjectCursor.getValue() instanceof Json.JsonObject) ||
                    documentCursor == null || !(documentCursor.getValue() instanceof Json.Document)) {
                    return m;
                }

                Json.Literal value = (Json.Literal) m.getValue();
                if (!(value.getValue() instanceof String) || !isSelected((String) value.getValue())) {
                    return m;
                }
                return m.withValue(value.withSource("\"17.0.0\"").withValue("17.0.0"));
            }

            private boolean isPackageJson() {
                Path sourcePath = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return NgxTranslateSupport.isProjectPath(sourcePath) && sourcePath.getFileName() != null &&
                       "package.json".equals(sourcePath.getFileName().toString());
            }
        };
    }

    private static boolean isSelected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) {
            candidate = candidate.substring(1);
        }
        return VERSIONS.contains(candidate) && declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+");
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal) {
            Object value = ((Json.Literal) member.getKey()).getValue();
            return value == null ? "" : value.toString();
        }
        if (member.getKey() instanceof Json.Identifier) {
            return ((Json.Identifier) member.getKey()).getName();
        }
        return "";
    }
}
