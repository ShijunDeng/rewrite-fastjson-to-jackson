package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks dependency and runtime decisions that cannot be safely selected automatically. */
public final class FindVueI18nManifestRisks extends Recipe {
    private static final Set<String> VUE2_PACKAGES = Set.of(
            "vue-template-compiler", "vue-server-renderer", "@vue/composition-api",
            "@vitejs/plugin-vue2", "@vue/test-utils");
    private static final Set<String> LEGACY_I18N_PACKAGES = Set.of(
            "vue-i18n-bridge", "@intlify/vue-i18n-bridge", "vue-i18n-composable",
            "@intlify/vue-i18n-loader", "vue-i18n-loader");

    @Override
    public String getDisplayName() {
        return "Find Vue I18n 11 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped Vue I18n declarations, Vue 2 toolchains, bridge/legacy integration packages, " +
               "incompatible Vue peers, and Node engines below the Vue I18n 11.3.0 baseline.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean i18nManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!VueI18nManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = i18nManifest;
                i18nManifest = containsI18n(document);
                Json.Document visited = super.visitDocument(document, ctx);
                i18nManifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!i18nManifest) {
                    return visited;
                }
                String section = VueI18nManifestSupport.directSection(getCursor());
                String key = VueI18nManifestSupport.key(visited);
                String value = VueI18nManifestSupport.stringValue(visited);
                if (!section.isEmpty()) {
                    if (VueI18nManifestSupport.PACKAGE.equals(key) &&
                        !VueI18nManifestSupport.targetDeclaration(value)) {
                        return markValue(visited, "Strict migration skipped this complex range, protocol, decorated, dynamic, or unlisted Vue I18n declaration; select a tested 11.x constraint and regenerate the lockfile");
                    }
                    if ("vue".equals(key) && !supportsVue3(value)) {
                        return markValue(visited, "Vue I18n 11.3.0 requires Vue ^3.0.0; complete Vue 2-to-3 runtime, compiler, tests, router/store, SSR, and UI-library migration first");
                    }
                    if (VUE2_PACKAGES.contains(key) && incompatibleVue2Tool(key, value)) {
                        return markValue(visited, key + " identifies a Vue 2-era compiler, SSR, test, composition, or build boundary; align it with Vue 3 before installing Vue I18n 11");
                    }
                    if (LEGACY_I18N_PACKAGES.contains(key)) {
                        return markValue(visited, key + " is a Vue 2/legacy Vue I18n bridge or loader boundary removed before v11; migrate its imports/build integration and then remove the owner");
                    }
                }
                if ("node".equals(key) && directEnginesMember() && !supportsNode16(value)) {
                    return markValue(visited, "Vue I18n 11.3.0 declares Node >=16; align local, CI, build, SSR, container, and deployment runtimes before rebuilding locale messages");
                }
                return visited;
            }

            private boolean directEnginesMember() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor engines = object == null ? null : object.getParent();
                org.openrewrite.Cursor root = engines == null ? null : engines.getParent();
                org.openrewrite.Cursor document = root == null ? null : root.getParent();
                return engines != null && engines.getValue() instanceof Json.Member member &&
                       "engines".equals(VueI18nManifestSupport.key(member)) &&
                       document != null && document.getValue() instanceof Json.Document;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsI18n(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !VueI18nManifestSupport.SECTIONS.contains(VueI18nManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member &&
                    VueI18nManifestSupport.PACKAGE.equals(VueI18nManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean supportsVue3(String declaration) {
        return atLeastMajor(declaration, 3);
    }

    private static boolean incompatibleVue2Tool(String key, String declaration) {
        if (!"@vue/test-utils".equals(key)) {
            return true;
        }
        return !atLeastMajor(declaration, 2);
    }

    private static boolean supportsNode16(String declaration) {
        if (declaration == null) {
            return false;
        }
        String compact = declaration.replace(" ", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(?:>=|\\^|~)?(\\d+)(?:\\.\\d+){0,2}$").matcher(compact);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) >= 16;
    }

    private static boolean atLeastMajor(String declaration, int minimum) {
        if (declaration == null) {
            return false;
        }
        String value = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d+)(?:\\.\\d+){0,2}$").matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) >= minimum;
    }
}
