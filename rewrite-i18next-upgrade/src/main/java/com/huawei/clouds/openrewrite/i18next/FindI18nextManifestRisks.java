package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks unresolved package, runtime, TypeScript, and ecosystem compatibility decisions. */
public final class FindI18nextManifestRisks extends Recipe {
    private static final Set<String> COMPANIONS = Set.of(
            "react-i18next", "next-i18next", "i18next-browser-languagedetector",
            "i18next-http-backend", "i18next-fs-backend", "i18next-localstorage-backend",
            "i18next-chained-backend", "i18next-multiload-backend-adapter");

    @Override
    public String getDisplayName() {
        return "Find i18next 25 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped i18next declarations, TypeScript versions below 5, Node engines below 14, and " +
               "independently versioned framework, backend, and detector packages.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean i18nextManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!I18nextManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = i18nextManifest;
                i18nextManifest = containsI18next(document);
                Json.Document visited = super.visitDocument(document, ctx);
                i18nextManifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!i18nextManifest) {
                    return visited;
                }
                String section = I18nextManifestSupport.directSection(getCursor());
                String key = I18nextManifestSupport.key(visited);
                String value = I18nextManifestSupport.stringValue(visited);
                if (!section.isEmpty()) {
                    if (I18nextManifestSupport.PACKAGE.equals(key) &&
                        !I18nextManifestSupport.targetDeclaration(value)) {
                        return markValue(visited, "Strict migration skipped this complex range, protocol, decorated, dynamic, or unlisted i18next declaration; select a tested 25.x constraint and regenerate the lockfile");
                    }
                    if ("typescript".equals(key) && !atLeastMajor(value, 5)) {
                        return markValue(visited, "i18next 23+ types require TypeScript 5 with strict or strictNullChecks; align compiler, editor, build, test, and declaration tooling");
                    }
                    if (COMPANIONS.contains(key)) {
                        return markValue(visited, key + " has an independent compatibility line; verify its i18next peer range, framework lifecycle, SSR/Suspense, backend/detector callbacks, cache, and types");
                    }
                }
                if ("node".equals(key) && directEnginesMember() && !supportsNode14(value)) {
                    return markValue(visited, "i18next 24+ dropped Node versions below 14; align local, CI, SSR/CLI, container, edge, and deployment runtimes before rebuilding resources");
                }
                return visited;
            }

            private boolean directEnginesMember() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor engines = object == null ? null : object.getParent();
                org.openrewrite.Cursor root = engines == null ? null : engines.getParent();
                org.openrewrite.Cursor document = root == null ? null : root.getParent();
                return engines != null && engines.getValue() instanceof Json.Member member &&
                       "engines".equals(I18nextManifestSupport.key(member)) &&
                       document != null && document.getValue() instanceof Json.Document;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsI18next(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !I18nextManifestSupport.SECTIONS.contains(I18nextManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member &&
                    I18nextManifestSupport.PACKAGE.equals(I18nextManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean supportsNode14(String declaration) {
        return everyAlternativeAtLeast(declaration, 14);
    }

    private static boolean atLeastMajor(String declaration, int minimum) {
        return everyAlternativeAtLeast(declaration, minimum);
    }

    private static boolean everyAlternativeAtLeast(String declaration, int minimum) {
        if (declaration == null) {
            return false;
        }
        String[] alternatives = declaration.trim().split("\\s*\\|\\|\\s*", -1);
        if (alternatives.length == 0) {
            return false;
        }
        java.util.regex.Pattern lowerBound = java.util.regex.Pattern.compile(
                "^(?:\\^|~|>=|=)?(\\d+)(?:(?:\\.(?:\\d+|x|\\*)){0,2})$");
        java.util.regex.Pattern upperOrAdditionalBound = java.util.regex.Pattern.compile(
                "^(?:<|<=|>|>=)\\d+(?:(?:\\.\\d+){0,2})$");
        for (String alternative : alternatives) {
            String normalized = alternative.trim().replaceAll("([<>]=?|=)\\s+", "$1");
            String[] tokens = normalized.split("\\s+");
            java.util.regex.Matcher lower = lowerBound.matcher(tokens[0]);
            if (!lower.matches() || Integer.parseInt(lower.group(1)) < minimum) {
                return false;
            }
            for (int i = 1; i < tokens.length; i++) {
                if (!upperOrAdditionalBound.matcher(tokens[i]).matches()) {
                    return false;
                }
            }
        }
        return true;
    }
}
