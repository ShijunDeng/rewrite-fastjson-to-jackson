package com.huawei.clouds.openrewrite.angulargridster2;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Updates only selected scalar direct dependency declarations. A dedicated JSON visitor avoids
 * treating values nested in arrays or package-manager metadata as direct dependency declarations.
 */
public final class UpgradeSelectedAngularGridster2Dependency extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> VERSIONS = Set.of("12.1.1", "13.3.0", "13.3.2", "16.0.0");

    @Override
    public String getDisplayName() {
        return "Upgrade selected scalar angular-gridster2 dependency declarations";
    }

    @Override
    public String getDescription() {
        return "Update selected direct angular-gridster2 strings to 20.2.4 without matching arrays, objects, protocols, aliases, or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member m = super.visitMember(member, ctx);
                if (!isPackageJson() || !"angular-gridster2".equals(key(m)) ||
                        !(m.getValue() instanceof Json.Literal)) {
                    return m;
                }

                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member) ||
                        !SECTIONS.contains(key((Json.Member) sectionCursor.getValue()))) {
                    return m;
                }

                Json.Literal value = (Json.Literal) m.getValue();
                if (!(value.getValue() instanceof String) || !isSelected((String) value.getValue())) {
                    return m;
                }
                return m.withValue(value.withSource("\"20.2.4\"").withValue("20.2.4"));
            }

            private boolean isPackageJson() {
                Path sourcePath = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return sourcePath.getFileName() != null && "package.json".equals(sourcePath.getFileName().toString());
            }
        };
    }

    private static boolean isSelected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~") || candidate.startsWith("=")) {
            candidate = candidate.substring(1);
        }
        if (candidate.startsWith("v")) {
            candidate = candidate.substring(1);
        }
        return VERSIONS.contains(candidate) &&
                declaration.matches("(?:[~^=]?|\\^?v)" + Pattern.quote(candidate));
    }

    private static String key(Json.Member member) {
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
