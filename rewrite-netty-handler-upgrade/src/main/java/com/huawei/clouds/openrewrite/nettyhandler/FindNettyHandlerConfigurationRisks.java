package com.huawei.clouds.openrewrite.nettyhandler;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Structured markers for Netty/JDK TLS system-property decisions. */
public final class FindNettyHandlerConfigurationRisks extends Recipe {
    static final String CONFIG =
            "This Netty/JDK TLS setting controls provider selection, native engine tasks, protocols, named groups, " +
            "session tickets/cache or OpenSSL buffers; verify the effective 4.1.136.Final value in every runtime image";

    @Override
    public String getDisplayName() {
        return "Find Netty handler TLS configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark structured Properties and YAML keys under io.netty.handler.ssl, jdk.tls and the JSSE session " +
               "cache setting so operational TLS choices are not hidden by a dependency-only upgrade.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    NettyHandlerSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                return riskyKey(visited.getKey()) ? mark(visited, CONFIG) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                if (!(visited.getValue() instanceof Yaml.Scalar)) return visited;
                return riskyKey(path()) ? mark(visited, CONFIG) : visited;
            }

            private String path() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        }.visitNonNull(source, ctx);
    }

    static boolean riskyKey(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
        return key.startsWith("io.netty.handler.ssl.") ||
               key.startsWith("jdk.tls.") ||
               "javax.net.ssl.sessioncachesize".equals(key);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree :
                SearchResult.found(tree, message);
    }
}
