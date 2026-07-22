package com.huawei.clouds.openrewrite.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;

/** Validates explicit SLF4J provider service descriptors without inventing a provider class. */
public final class FindSlf4jServiceProviderResourceRisks extends Recipe {
    private static final String PROVIDER_SERVICE =
            "META-INF/services/org.slf4j.spi.SLF4JServiceProvider";
    private static final String LEGACY_SERVICE =
            "META-INF/services/org.slf4j.spi.LoggerFactoryBinder";

    @Override
    public String getDisplayName() {
        return "Find SLF4J provider ServiceLoader resource risks";
    }

    @Override
    public String getDescription() {
        return "Mark legacy or malformed SLF4J provider service descriptors; a valid descriptor with exactly one " +
               "provider class is preserved.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText t = (PlainText) super.visitText(text, ctx);
                String path = t.getSourcePath().toString().replace('\\', '/');
                if (path.endsWith(LEGACY_SERVICE)) {
                    return SearchResult.found(t,
                            "SLF4J 2 discovers SLF4JServiceProvider, not LoggerFactoryBinder; migrate the implementation and service descriptor together");
                }
                if (!path.endsWith(PROVIDER_SERVICE)) {
                    return t;
                }
                List<String> providers = t.getText().lines()
                        .map(line -> line.replaceFirst("#.*$", "").trim())
                        .filter(line -> !line.isEmpty())
                        .toList();
                if (providers.size() != 1 || !isClassName(providers.get(0))) {
                    return SearchResult.found(t,
                            "SLF4J provider service descriptor must name exactly one valid provider class; verify shaded output and provider selection");
                }
                return t;
            }
        };
    }

    private static boolean isClassName(String value) {
        return value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    }
}
