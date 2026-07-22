package com.huawei.clouds.openrewrite.netflixeureka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;
import java.util.regex.Pattern;

/** Marks removed Eureka implementation classes and credentials embedded in configuration resources. */
public final class FindNetflixEurekaConfigurationRisks extends Recipe {
    private static final Pattern USER_INFO = Pattern.compile("https?://[^\\s/:@]+:[^\\s@/]+@");

    @Override
    public String getDisplayName() {
        return "Find Netflix Eureka configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed Guice/Governator/Jersey 1 implementation class names and credentials embedded in Eureka service URLs across parsed or plain configuration resources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !configurationLike(source)) {
                    return tree;
                }
                String content = source.printAll();
                if (content.contains("com.netflix.discovery.guice.") ||
                    content.contains("com.netflix.discovery.InternalEurekaStatusModule")) {
                    return SearchResult.found(source,
                            "Eureka 2.0.4 removed built-in Guice/Governator bootstrap classes; replace this descriptor with explicit application, transport, and lifecycle wiring");
                }
                if (content.contains("com.netflix.discovery.EurekaIdentityHeaderFilter") ||
                    content.contains("com.netflix.discovery.shared.transport.jersey.Jersey1") ||
                    content.contains("com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient") ||
                    content.contains("com.sun.jersey.")) {
                    return SearchResult.found(source,
                            "Descriptor references the removed Jersey 1 transport; select Jersey 3 or another transport and port filters, TLS, proxy, authentication, and pooling deliberately");
                }
                if (USER_INFO.matcher(content).find() &&
                    (content.contains("eureka.serviceUrl") || content.contains("defaultZone") ||
                     content.contains("service-url"))) {
                    return SearchResult.found(source,
                            "Eureka service URL embeds credentials; move them to a protected authentication mechanism and rotate the exposed value before transport migration");
                }
                return tree;
            }
        };
    }

    private static boolean configurationLike(SourceFile source) {
        String path = source.getSourcePath().toString().toLowerCase(Locale.ROOT);
        return path.endsWith(".properties") || path.endsWith(".yml") || path.endsWith(".yaml") ||
               path.endsWith(".xml") || path.endsWith(".conf") || path.endsWith(".json") ||
               path.contains("/meta-inf/services/") || path.startsWith("meta-inf/services/");
    }
}
