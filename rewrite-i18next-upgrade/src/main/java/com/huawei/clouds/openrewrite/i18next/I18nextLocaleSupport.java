package com.huawei.clouds.openrewrite.i18next;

import java.nio.file.Path;
import java.util.Locale;

final class I18nextLocaleSupport {
    private I18nextLocaleSupport() {
    }

    static boolean isLocaleResource(Path sourcePath) {
        String path = normalized(sourcePath);
        return path.contains("/locale/") || path.contains("/locales/") || path.contains("/i18n/") ||
               path.contains("/translations/") || path.contains("/langs/") || path.startsWith("locale/") ||
               path.startsWith("locales/") || path.startsWith("i18n/") || path.startsWith("translations/") ||
               path.startsWith("langs/");
    }

    static boolean isEnglishResource(Path sourcePath) {
        if (!isLocaleResource(sourcePath)) {
            return false;
        }
        String path = normalized(sourcePath);
        String[] segments = path.split("/");
        for (String segment : segments) {
            String name = segment.replaceFirst("\\.(?:json|json5|ya?ml)$", "");
            if (name.matches("en(?:[-_][a-z]{2,4})?")) {
                return true;
            }
        }
        return false;
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
