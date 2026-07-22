package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Updates only the ngx-color-picker versions explicitly visible in the spreadsheet. */
public final class UpgradeSelectedNgxColorPickerDependency extends Recipe {
    static final String PACKAGE = "ngx-color-picker";
    static final String TARGET_VERSION = "20.1.1";
    static final Set<String> SOURCE_VERSIONS = Set.of("13.0.0", "14.0.0");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected ngx-color-picker declarations to 20.1.1";
    }

    @Override
    public String getDescription() {
        return "Updates only exact, caret, or tilde direct declarations for spreadsheet rows 386 and 387; " +
               "ranges, protocols, central owners, metadata, and lockfiles remain unchanged.";
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
        return path.getFileName() != null && "package.json".equals(path.getFileName().toString()) &&
               NgxColorPickerSupport.isProjectSource(path);
    }
}
