package com.huawei.clouds.openrewrite.springdataelasticsearch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Marks exact Java AST nodes that cross removed client, query, mapping, repository, and reactive APIs. */
public final class FindSpringDataElasticsearchJavaRisks extends Recipe {
    private static final Map<String, String> EXACT_TYPE_MESSAGES = Map.ofEntries(
            Map.entry("org.elasticsearch.client.RestHighLevelClient",
                    "RHLC was removed from Spring Data Elasticsearch before 6.0; rebuild this exact client boundary with the Elasticsearch 9 Rest5Client, including TLS, authentication, timeouts, pooling, serialization, and shutdown"),
            Map.entry("org.elasticsearch.client.transport.TransportClient",
                    "TransportClient and the transport-based template were removed; replace this exact type with a supported HTTP client/ElasticsearchOperations boundary and revalidate cluster discovery and lifecycle"),
            Map.entry("org.springframework.data.elasticsearch.client.RestClients",
                    "RestClients/RHLC configuration is absent from 6.0.5; migrate this exact entry to client.elc.rest5_client and explicitly preserve headers, SSL, proxy, callbacks, timeouts, metrics, and close behavior"),
            Map.entry("org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration",
                    "The old RHLC-oriented AbstractElasticsearchConfiguration contract is not a drop-in 6.0.5 client configuration; rebuild the bean boundary for Elasticsearch 9 Rest5Client"),
            Map.entry("org.springframework.data.elasticsearch.core.ElasticsearchTemplate",
                    "The TransportClient-based ElasticsearchTemplate was removed; select ElasticsearchOperations with the supported 6.0.5 client and re-test refresh, mapping, conversion, callbacks, and exceptions"),
            Map.entry("org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate",
                    "This older implementation template crosses client-generation boundaries; inject the ElasticsearchOperations abstraction or the 6.0.5 implementation deliberately and avoid implementation-type coupling"),
            Map.entry("org.springframework.data.elasticsearch.core.ReactiveElasticsearchTemplate",
                    "Reactive template/client contracts changed across 4.4-6.0; verify nonblocking RestClient selection, refresh visibility, backpressure, cancellation, retries, and resource shutdown"),
            Map.entry("org.springframework.data.elasticsearch.annotations.DynamicMapping",
                    "@DynamicMapping was removed; move the exact value to @Document.dynamic/@Field.dynamic and verify generated mapping JSON against an Elasticsearch 9 test index"),
            Map.entry("org.springframework.data.elasticsearch.annotations.DynamicMappingValue",
                    "DynamicMappingValue belonged to removed @DynamicMapping; choose the corresponding target dynamic setting and validate strict/runtime behavior"),
            Map.entry("org.springframework.data.elasticsearch.core.ScriptType",
                    "ScriptType was removed in 6.0; migrate this exact use to ScriptData and verify inline/stored source, language, parameters, serialization, and compilation errors"),
            Map.entry("org.springframework.data.elasticsearch.core.query.ScriptType",
                    "ScriptType was removed in 6.0; migrate this exact use to ScriptData and verify inline/stored source, language, parameters, serialization, and compilation errors")
    );
    private static final Set<String> CACHE_ANNOTATIONS = Set.of(
            "org.springframework.cache.annotation.Cacheable", "org.springframework.cache.annotation.CachePut",
            "org.springframework.cache.annotation.CacheEvict"
    );

    @Override
    public String getDisplayName() {
        return "Find Spring Data Elasticsearch 6 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed clients/types/methods, Elasticsearch 7 direct APIs, old mapping/date constructs, " +
               "repository query null semantics, and cache/reactive boundaries at exact attributed Java nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedSpringDataElasticsearchDependency.generated(source.getSourcePath()) ||
                    !(tree instanceof J.CompilationUnit compilationUnit)) return tree;
                return javaVisitor().visitNonNull(compilationUnit, ctx);
            }
        };
    }

    private static JavaIsoVisitor<ExecutionContext> javaVisitor() {
        return new JavaIsoVisitor<>() {
            private boolean usesSpringDataElasticsearch;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                boolean old = usesSpringDataElasticsearch;
                usesSpringDataElasticsearch = cu.getImports().stream().anyMatch(anImport ->
                        anImport.getQualid().printTrimmed().startsWith("org.springframework.data.elasticsearch."));
                J.CompilationUnit visited = super.visitCompilationUnit(cu, ctx);
                usesSpringDataElasticsearch = old;
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                TypeTree type = visited.getTypeExpression();
                String message = type == null ? null : typeMessage(typeName(type.getType()));
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withTypeExpression(marked);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                TypeTree type = visited.getReturnTypeExpression();
                String message = type == null ? null : typeMessage(typeName(type.getType()));
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withReturnTypeExpression(marked);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                TypeTree type = visited.getClazz();
                String message = type == null ? null : typeMessage(typeName(type.getType()));
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withClazz(marked);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                TypeTree base = visited.getExtends();
                String message = base == null ? null : typeMessage(typeName(base.getType()));
                if (message == null) return visited;
                TypeTree marked = mark(base, message);
                return marked == base ? visited : visited.withExtends(marked);
            }

            @Override
            public J.ParameterizedType visitParameterizedType(J.ParameterizedType parameterized,
                                                              ExecutionContext ctx) {
                J.ParameterizedType visited = super.visitParameterizedType(parameterized, ctx);
                if (visited.getTypeParameters() == null) return visited;
                return visited.withTypeParameters(org.openrewrite.internal.ListUtils.map(
                        visited.getTypeParameters(), parameter -> {
                            String message = typeMessage(typeName(parameter.getType()));
                            return message == null ? parameter : mark(parameter, message);
                        }));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = visited.getSimpleName();
                String message = methodMessage(owner, name, method);
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                JavaType.Variable field = visited.getName().getFieldType();
                String owner = field == null || field.getOwner() == null ? null : typeName(field.getOwner());
                if ("org.springframework.data.elasticsearch.annotations.DateFormat".equals(owner) &&
                    ("none".equals(visited.getSimpleName()) || "custom".equals(visited.getSimpleName()))) {
                    J.Identifier marked = mark(visited.getName(),
                            "DateFormat." + visited.getSimpleName() + " was removed; specify a supported format/pattern and test legacy index values, timezone, epoch/string coercion, converters, and mapping creation");
                    return marked == visited.getName() ? visited : visited.withName(marked);
                }
                String message = typeMessage(owner);
                if (message != null) {
                    J.Identifier marked = mark(visited.getName(), message);
                    return marked == visited.getName() ? visited : visited.withName(marked);
                }
                return visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String fqn = typeName(visited.getType());
                if ("org.springframework.data.elasticsearch.annotations.DynamicMapping".equals(fqn)) {
                    return mark(visited, EXACT_TYPE_MESSAGES.get(fqn));
                }
                if ("org.springframework.data.elasticsearch.annotations.Query".equals(fqn)) {
                    return mark(visited,
                            "Repository @Query no longer renders a null parameter as the string null; test every nullable argument, JSON escaping, conversion failures, pagination/sort, and returned SearchHits against Elasticsearch 9");
                }
                if (usesSpringDataElasticsearch && CACHE_ANNOTATIONS.contains(fqn)) {
                    return mark(visited,
                            "Spring cache semantics cross Spring 5 to 7 and may wrap reactive publishers rather than their values; verify keys, nulls, exceptions, invalidation, stampedes, serialization, subscription timing, and cache backend compatibility");
                }
                return visited;
            }
        };
    }

    private static String typeMessage(String fqn) {
        if (fqn == null) return null;
        String exact = EXACT_TYPE_MESSAGES.get(fqn);
        if (exact != null) return exact;
        if (fqn.startsWith("org.elasticsearch.action.")) {
            return "Direct Elasticsearch 7 request/response type detected; rewrite this exact boundary with co.elastic.clients Elasticsearch 9 API and verify response records, errors, timeouts, serialization, and compatibility headers";
        }
        if (fqn.startsWith("org.elasticsearch.index.query.")) {
            return "Elasticsearch 7 QueryBuilders cannot populate the 6.0 NativeQuery DSL; rebuild this exact clause as co.elastic.clients.elasticsearch._types.query_dsl.Query and re-test bool, null, scoring, analyzers, pagination, and JSON";
        }
        if (fqn.startsWith("org.elasticsearch.search.")) {
            return "Direct Elasticsearch 7 search response type detected; migrate this exact result boundary to the Elasticsearch 9 client/Spring SearchHits model and verify totals, score, aggregations, suggest, sort, PIT, and nullability";
        }
        if (fqn.startsWith("org.springframework.data.elasticsearch.client.erhlc.")) {
            return "The erhlc package was removed; migrate this exact RHLC adapter to Rest5Client and preserve application-specific transport/security/serialization behavior";
        }
        if (fqn.startsWith("org.springframework.data.elasticsearch.client.reactive.")) {
            return "This legacy reactive client package does not map mechanically to 6.0; choose the supported reactive boundary and verify nonblocking I/O, backpressure, cancellation, refresh, retries, and teardown";
        }
        return null;
    }

    private static String methodMessage(String owner, String name, JavaType.Method method) {
        if ("org.springframework.data.elasticsearch.core.ElasticsearchOperations".equals(owner) &&
            "stringIdRepresentation".equals(name)) {
            return "stringIdRepresentation was replaced by convertId; verify custom converters, null IDs, numeric/UUID formatting, repository keys, routing, and existing document IDs";
        }
        if ("org.springframework.data.elasticsearch.core.query.IndexQuery".equals(owner) &&
            ("getParentId".equals(name) || "setParentId".equals(name))) {
            return "IndexQuery.parentId was removed; remodel this exact parent/child use with join-field/routing semantics and validate mapping, bulk indexing, updates, deletes, and queries";
        }
        if ("org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations".equals(owner) &&
            "execute".equals(name)) {
            return "The public reactive execute callback was removed; express this operation through supported reactive APIs without wrapping blocking I/O and verify backpressure, cancellation, retries, and client lifetime";
        }
        if (("org.springframework.data.elasticsearch.core.DocumentOperations".equals(owner) ||
             "org.springframework.data.elasticsearch.core.ReactiveDocumentOperations".equals(owner)) &&
            "delete".equals(name) && removedDeleteSignature(method)) {
            return "This delete(Query,...) overload was removed; select the target delete-by-query/client API explicitly and verify index coordinates, routing, refresh, failures, counts, and reactive completion";
        }
        if (("org.springframework.data.elasticsearch.core.SearchOperations".equals(owner) ||
             "org.springframework.data.elasticsearch.core.ReactiveSearchOperations".equals(owner)) &&
            "suggest".equals(name) && !method.getParameterTypes().isEmpty() &&
            startsWith(typeName(method.getParameterTypes().get(0)), "org.elasticsearch.search.suggest.")) {
            return "The old SuggestBuilder operation was removed with Elasticsearch 7 types; rebuild suggestions using the Elasticsearch 9/Spring Data 6 query model and verify payload parsing and result conversion";
        }
        if ("org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder".equals(owner) &&
            "withKnnQuery".equals(name)) {
            return "withKnnQuery was replaced by withKnnSearches; distinguish top-level kNN search from a kNN query clause and test filters, candidates, score, pagination, and server capabilities";
        }
        if ("org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchIndicesClient".equals(owner) &&
            "unfreeze".equals(name)) {
            return "The reactive unfreeze API was removed; remove the frozen-index workflow or replace it with a supported lifecycle operation and validate ILM/data tiers and permissions";
        }
        if ("org.elasticsearch.client.RestHighLevelClient".equals(owner) ||
            "org.springframework.data.elasticsearch.client.RestClients".equals(owner) ||
            owner.startsWith("org.elasticsearch.index.query.")) return typeMessage(owner);
        return null;
    }

    private static boolean removedDeleteSignature(JavaType.Method method) {
        if (method.getParameterTypes().size() < 2 || method.getParameterTypes().size() > 3) return false;
        if (!"org.springframework.data.elasticsearch.core.query.Query".equals(
                typeName(method.getParameterTypes().get(0))) ||
            !"java.lang.Class".equals(typeName(method.getParameterTypes().get(1)))) return false;
        return method.getParameterTypes().size() == 2 ||
               "org.springframework.data.elasticsearch.core.mapping.IndexCoordinates".equals(
                       typeName(method.getParameterTypes().get(2)));
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(type);
        return fqn == null ? null : fqn.getFullyQualifiedName();
    }

    private static <T extends org.openrewrite.Tree> T mark(T tree, String message) {
        boolean present = tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()));
        return present ? tree : SearchResult.found(tree, message);
    }
}
