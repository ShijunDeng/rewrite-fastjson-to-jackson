package com.huawei.clouds.openrewrite.postgresql;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks type-attributed pgjdbc usage whose runtime behavior needs an application decision. */
public final class FindPostgresql42SourceRisks extends Recipe {
    static final String URL =
            "PostgreSQL JDBC connection URL detected; verify sslmode/certificates, multi-host failover, timeouts, " +
            "targetServerType, autosave, prepared statements, binary transfer and server compatibility with 42.7.13";
    static final String PROPERTY =
            "PGProperty configuration detected; revalidate the exact property against 42.7.13, especially SSL/GSS, " +
            "timeouts, autosave, query mode, prepare threshold, binary transfer and batched-insert rewriting";
    static final String DATASOURCE =
            "PostgreSQL DataSource/XA configuration detected; verify property precedence, pooling ownership, XA " +
            "autocommit and recovery behavior, login timeout, SSL/GSS and multi-host failover with 42.7.13";
    static final String COPY =
            "PostgreSQL COPY API detected; retest text/binary formats, encoding, streaming close/cancel, transaction " +
            "boundaries, backpressure and partial-failure behavior with 42.7.13";
    static final String LOB =
            "PostgreSQL Large Object API detected; retest transaction requirements, mode, seek/truncate, stream " +
            "flush/reset, ownership and cleanup because 42.7.x contains LOB correctness fixes";
    static final String REPLICATION =
            "PostgreSQL replication/notification API detected; verify slot lifecycle, LSN acknowledgement, polling " +
            "timeouts, reconnect/failover, keepalive and resource closure with 42.7.13";
    static final String STATEMENT =
            "PostgreSQL statement tuning detected; verify prepareThreshold, adaptive fetch/fetch size, binary transfer, " +
            "batch rewrite, generated keys, cancellation and memory/latency behavior with 42.7.13";
    static final String UPDATABLE =
            "Updatable ResultSet requested; 42.7.x fixes search_path, java.time, bytea, character-stream and metadata " +
            "behavior, so retest positioned updates/inserts, schema resolution, nulls and generated values";
    static final String EXTENSION =
            "PostgreSQL-specific connection API detected; verify unwrap/cast assumptions, transaction state, cancel, " +
            "notifications, type mapping and extension lifecycle against 42.7.13";
    static final String INTERNAL =
            "org.postgresql internal implementation API has no compatibility guarantee; replace this import with a " +
            "published JDBC or pgjdbc extension API before upgrading to 42.7.13";

    private static final Set<String> DATA_SOURCE_TYPES = Set.of(
            "org.postgresql.ds.PGSimpleDataSource", "org.postgresql.ds.PGPoolingDataSource",
            "org.postgresql.xa.PGXADataSource", "org.postgresql.xa.PGXAConnection");
    private static final Set<String> COPY_TYPES = Set.of(
            "org.postgresql.copy.CopyManager", "org.postgresql.copy.CopyIn", "org.postgresql.copy.CopyOut",
            "org.postgresql.copy.CopyDual");
    private static final Set<String> LOB_TYPES = Set.of(
            "org.postgresql.largeobject.LargeObjectManager", "org.postgresql.largeobject.LargeObject");
    private static final Set<String> REPLICATION_PREFIXES = Set.of(
            "org.postgresql.replication.", "org.postgresql.PGNotification");
    private static final Set<String> STATEMENT_METHODS = Set.of(
            "setPrepareThreshold", "getPrepareThreshold", "setUseServerPrepare", "isUseServerPrepare",
            "setAdaptiveFetch", "getAdaptiveFetch", "setFetchSize", "addBatch", "executeBatch",
            "executeLargeBatch", "setFlushCacheOnDeallocate");
    private static final Set<String> CONNECTION_METHODS = Set.of(
            "getCopyAPI", "getLargeObjectAPI", "getReplicationAPI", "getNotifications", "getFastpathAPI",
            "getTypeInfo", "addDataType", "cancelQuery", "setPrepareThreshold", "getPrepareThreshold",
            "setAdaptiveFetch", "getAdaptiveFetch", "setAutosave", "getAutosave", "setPreferQueryMode",
            "getPreferQueryMode", "escapeIdentifier", "escapeLiteral");

    @Override
    public String getDisplayName() {
        return "Find PostgreSQL JDBC 42.7 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact type-attributed pgjdbc URL, property, DataSource/XA, COPY, LOB, replication, statement, " +
               "updatable ResultSet, extension, and internal API nodes requiring behavioral regression decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return UpgradeSelectedPostgresqlDependency.excluded(cu.getSourcePath())
                        ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String name = visited.getQualid().printTrimmed(getCursor());
                return name.startsWith("org.postgresql.core.") || name.startsWith("org.postgresql.jdbc.") ||
                       name.startsWith("org.postgresql.util.internal.")
                        ? mark(visited, INTERNAL) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = typeName(visited.getType());
                if (DATA_SOURCE_TYPES.contains(type)) return mark(visited, DATASOURCE);
                if (COPY_TYPES.contains(type)) return mark(visited, COPY);
                if (LOB_TYPES.contains(type)) return mark(visited, LOB);
                if (replication(type)) return mark(visited, REPLICATION);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                String owner = method == null ? null : typeName(method.getDeclaringType());
                String name = visited.getSimpleName();

                if ("java.sql.DriverManager".equals(owner) && "getConnection".equals(name) &&
                    visited.getArguments().stream().anyMatch(FindPostgresql42SourceRisks::postgresUrl)) {
                    return mark(visited, URL);
                }
                if ("org.postgresql.Driver".equals(owner) && Set.of("connect", "acceptsURL").contains(name)) {
                    return mark(visited, URL);
                }
                if ("org.postgresql.PGProperty".equals(owner)) return mark(visited, PROPERTY);
                if (DATA_SOURCE_TYPES.contains(owner) || owner != null && owner.startsWith("org.postgresql.ds.")) {
                    return mark(visited, DATASOURCE);
                }
                if (COPY_TYPES.contains(owner)) return mark(visited, COPY);
                if (LOB_TYPES.contains(owner)) return mark(visited, LOB);
                if (replication(owner)) return mark(visited, REPLICATION);
                if (("org.postgresql.PGStatement".equals(owner) || "org.postgresql.jdbc.PgStatement".equals(owner)) &&
                    STATEMENT_METHODS.contains(name)) return mark(visited, STATEMENT);
                if ("org.postgresql.PGConnection".equals(owner) && "getCopyAPI".equals(name)) {
                    return mark(visited, COPY);
                }
                if ("org.postgresql.PGConnection".equals(owner) && "getLargeObjectAPI".equals(name)) {
                    return mark(visited, LOB);
                }
                if ("org.postgresql.PGConnection".equals(owner) &&
                    Set.of("getReplicationAPI", "getNotifications").contains(name)) {
                    return mark(visited, REPLICATION);
                }
                if ("org.postgresql.PGConnection".equals(owner) && CONNECTION_METHODS.contains(name)) {
                    return mark(visited, EXTENSION);
                }
                if (updatableRequest(visited, owner)) return mark(visited, UPDATABLE);
                return visited;
            }
        };
    }

    private static boolean postgresUrl(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value &&
               value.startsWith("jdbc:postgresql:");
    }

    private static boolean updatableRequest(J.MethodInvocation invocation, String owner) {
        if (!("java.sql.Connection".equals(owner) || "org.postgresql.PGConnection".equals(owner)) ||
            !Set.of("createStatement", "prepareStatement", "prepareCall").contains(invocation.getSimpleName())) {
            return false;
        }
        return invocation.getArguments().stream().anyMatch(argument ->
                argument.printTrimmed().endsWith("CONCUR_UPDATABLE"));
    }

    private static boolean replication(String type) {
        if (type == null) return false;
        return REPLICATION_PREFIXES.stream().anyMatch(type::startsWith);
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? null : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
