package com.huawei.clouds.openrewrite.swiper;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/**
 * Updates only scalar direct dependency declarations. A dedicated visitor is used because
 * JsonPath leaf filters in the supported OpenRewrite version also match values nested in arrays.
 */
public final class UpgradeSelectedSwiperDependency extends Recipe {
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    static final Set<String> VERSIONS = Set.of(
            "3.4.2", "6.8.1", "6.8.4", "7.2.0", "8.3.1", "8.4.7", "9.1.0", "9.2.0", "9.4.1"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected scalar Swiper dependency declarations";
    }

    @Override
    public String getDescription() {
        return "Update selected direct Swiper dependency strings to 12.1.2 without matching arrays, objects, protocols, aliases, or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member m = super.visitMember(member, ctx);
                if (!isPackageJson() || !"swiper".equals(key(m)) || !(m.getValue() instanceof Json.Literal)) {
                    return m;
                }

                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                Cursor rootObjectCursor = sectionCursor == null ? null : sectionCursor.getParentTreeCursor();
                Cursor documentCursor = rootObjectCursor == null ? null : rootObjectCursor.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member) ||
                    !SECTIONS.contains(key((Json.Member) sectionCursor.getValue())) ||
                    documentCursor == null || !(documentCursor.getValue() instanceof Json.Document)) {
                    return m;
                }

                Json.Literal value = (Json.Literal) m.getValue();
                if (!(value.getValue() instanceof String) || !isSelected((String) value.getValue())) {
                    return m;
                }
                return m.withValue(value.withSource("\"12.1.2\"").withValue("12.1.2"));
            }

            private boolean isPackageJson() {
                Path sourcePath = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return SwiperSupport.isProjectPath(sourcePath) && sourcePath.getFileName() != null &&
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
