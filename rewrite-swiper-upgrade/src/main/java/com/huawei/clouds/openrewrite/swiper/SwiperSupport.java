package com.huawei.clouds.openrewrite.swiper;

import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SwiperSupport {
    static final String PACKAGE = "swiper";
    static final String TARGET = "12.1.2";
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", "coverage", "generated"
    );
    static final Set<String> MODULES = Set.of(
            "A11y", "Autoplay", "Controller", "EffectCards", "EffectCoverflow", "EffectCreative",
            "EffectCube", "EffectFade", "EffectFlip", "FreeMode", "Grid", "HashNavigation", "History",
            "Keyboard", "Manipulation", "Mousewheel", "Navigation", "Pagination", "Parallax", "Scrollbar",
            "Thumbs", "Virtual", "Zoom"
    );
    static final Map<String, String> JS_ENTRIES = Map.ofEntries(
            Map.entry("swiper/dist/js/swiper.js", "swiper/bundle"),
            Map.entry("swiper/dist/js/swiper.min.js", "swiper/bundle"),
            Map.entry("swiper/js/swiper.js", "swiper/bundle"),
            Map.entry("swiper/js/swiper.min.js", "swiper/bundle"),
            Map.entry("swiper/swiper-bundle.js", "swiper/bundle"),
            Map.entry("swiper/swiper-bundle.min.js", "swiper/bundle"),
            Map.entry("swiper/swiper-bundle.esm.js", "swiper/bundle"),
            Map.entry("swiper/swiper-bundle.esm.min.js", "swiper/bundle"),
            Map.entry("swiper/swiper.esm.js", "swiper"),
            Map.entry("swiper/swiper.esm.min.js", "swiper"),
            Map.entry("swiper/swiper.js", "swiper"),
            Map.entry("swiper/swiper.min.js", "swiper")
    );
    static final Map<String, String> CSS_ENTRIES = Map.ofEntries(
            Map.entry("swiper/dist/css/swiper.css", "swiper/css/bundle"),
            Map.entry("swiper/dist/css/swiper.min.css", "swiper/css/bundle"),
            Map.entry("swiper/css/swiper.css", "swiper/css"),
            Map.entry("swiper/css/swiper.min.css", "swiper/css"),
            Map.entry("swiper/swiper.css", "swiper/css"),
            Map.entry("swiper/swiper.min.css", "swiper/css"),
            Map.entry("swiper/swiper-bundle.css", "swiper/css/bundle"),
            Map.entry("swiper/swiper-bundle.min.css", "swiper/css/bundle")
    );

    private SwiperSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String defaultBinding(JS.Import declaration) {
        return declaration.getImportClause() != null && declaration.getImportClause().getName() != null
                ? declaration.getImportClause().getName().getSimpleName() : null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static boolean isSwiperReference(String module) {
        return PACKAGE.equals(module) || module.startsWith(PACKAGE + "/");
    }

    static String cssTarget(String module) {
        String direct = CSS_ENTRIES.get(module);
        if (direct != null) return direct;
        Matcher component = Pattern.compile("swiper/components/([a-z0-9-]+)/\\1(?:\\.min)?\\.css")
                .matcher(module);
        if (component.matches() && Set.of(
                "a11y", "autoplay", "controller", "effect-coverflow", "effect-cube", "effect-fade",
                "effect-flip", "free-mode", "hash-navigation", "history", "keyboard", "mousewheel",
                "navigation", "pagination", "parallax", "scrollbar", "thumbs", "virtual", "zoom"
        ).contains(component.group(1))) return "swiper/css/" + component.group(1);
        return null;
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }
}
