package com.huawei.clouds.openrewrite.vuei18n;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

final class VueI18nLocaleMessageSupport {
    private static final Pattern EMAIL = Pattern.compile("(?i)(?<!\\{')\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern LINKED_GROUP = Pattern.compile("@:\\([^)]*\\)");

    private VueI18nLocaleMessageSupport() {
    }

    static boolean isLocaleResource(Path sourcePath) {
        String path = sourcePath.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.contains("/locale/") || path.contains("/locales/") || path.contains("/i18n/") ||
               path.contains("/lang/") || path.startsWith("locale/") || path.startsWith("locales/") ||
               path.startsWith("i18n/") || path.startsWith("lang/") ||
               path.endsWith("/messages.json") || path.endsWith("/messages.yaml") || path.endsWith("/messages.yml");
    }

    static String risk(String value) {
        if (value.contains("%{")) {
            return "Vue I18n 10 removed legacy %{...} modulo interpolation; replace it with documented list/named interpolation and compile every locale resource";
        }
        if (LINKED_GROUP.matcher(value).find()) {
            return "Vue I18n 9 removed @:(...) linked-message grouping; rewrite the linked key/modifier with current literal interpolation syntax and compile all locales";
        }
        if (!value.contains("{'@'}") && EMAIL.matcher(value).find()) {
            return "Vue I18n 9 treats @ as message syntax; escape the literal @ using literal interpolation and verify runtime or build-time message compilation";
        }
        return null;
    }
}
