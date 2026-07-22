package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Marks exact attributed Java nodes whose Hibernate 7 replacement depends on application semantics. */
public final class FindHibernate7JavaMigrationRisks extends Recipe {
    private static final Map<String, String> TYPE_MESSAGES = Map.ofEntries(
            Map.entry("org.hibernate.Criteria", "Legacy Hibernate Criteria was removed; rewrite this exact boundary with Jakarta Criteria, HQL, or a typed repository and verify joins, fetches, projections, ordering, pagination, and generated SQL"),
            Map.entry("org.hibernate.criterion.DetachedCriteria", "DetachedCriteria was removed; choose Jakarta Criteria or an explicit query abstraction and test detached composition, aliases, subqueries, pagination, and result typing"),
            Map.entry("org.hibernate.criterion.Restrictions", "Legacy Restrictions was removed; rebuild each predicate with CriteriaBuilder while preserving null, collection, case, association, and SQL semantics"),
            Map.entry("org.hibernate.criterion.Projections", "Legacy Projections was removed; rebuild the select/grouping with Jakarta Criteria or HQL and validate scalar/tuple types, aliases, aggregates, and nullability"),
            Map.entry("org.hibernate.criterion.Order", "Legacy Criteria Order was removed; rebuild ordering explicitly and verify null ordering, collation, case, pagination stability, and database portability"),
            Map.entry("org.hibernate.transform.ResultTransformer", "ResultTransformer contracts changed; choose TupleTransformer, ResultListTransformer, constructor projection, or result-set mapping and verify aliases, duplicates, list post-processing, and result types"),
            Map.entry("org.hibernate.transform.AliasToBeanResultTransformer", "AliasToBeanResultTransformer crosses the removed transformer API; replace it deliberately and verify alias/property matching, constructors, nulls, duplicate rows, and module reflection access"),
            Map.entry("org.hibernate.transform.AliasToEntityMapResultTransformer", "AliasToEntityMapResultTransformer crosses the removed transformer API; choose an explicit tuple/map projection and verify aliases, duplicate keys, scalar types, and ordering"),
            Map.entry("org.hibernate.EmptyInterceptor", "EmptyInterceptor was removed; implement the target Interceptor contract directly and update identifier/signature types, lifecycle registration, dirty checking, serialization, and thread-safety assumptions"),
            Map.entry("org.hibernate.usertype.UserType", "UserType is a redesigned Hibernate 6/7 SPI; recompile this implementation against 7.2 and verify Java/JDBC types, mutability, equality, caching, null handling, SQL types, and database round-trips"),
            Map.entry("org.hibernate.usertype.CompositeUserType", "CompositeUserType changed substantially; reimplement the target embeddable contract and verify property ordering, instantiation, equality, mutability, caching, null handling, and dirty checking"),
            Map.entry("org.hibernate.type.BasicType", "BasicType participates in the redesigned type system; rebuild registration from JavaType/JdbcType converters and verify DDL, binding, extraction, caching, equality, and schema validation"),
            Map.entry("org.hibernate.integrator.spi.Integrator", "Integrator is an internal extension boundary across major versions; recompile all callbacks and registration against 7.2 and test bootstrap, metadata, service lifecycle, shutdown, and framework integration"),
            Map.entry("org.hibernate.boot.spi.MetadataContributor", "MetadataContributor is an SPI boundary; recompile against the target signature and verify bootstrap ordering, XML/Jandex inputs, native-image reachability, and produced mappings"),
            Map.entry("org.hibernate.boot.spi.AdditionalJaxbMappingProducer", "AdditionalJaxbMappingProducer is an evolving bootstrap SPI; migrate to the target contract and validate XML binding, source origin, ordering, class loading, and native-image behavior")
    );
    private static final Set<String> VERSIONED_DIALECTS = Set.of(
            "org.hibernate.dialect.MySQL5Dialect", "org.hibernate.dialect.MySQL57Dialect",
            "org.hibernate.dialect.MySQL8Dialect", "org.hibernate.dialect.PostgreSQL9Dialect",
            "org.hibernate.dialect.PostgreSQL10Dialect", "org.hibernate.dialect.Oracle12cDialect",
            "org.hibernate.dialect.SQLServer2012Dialect", "org.hibernate.dialect.MariaDB101Dialect",
            "org.hibernate.dialect.MariaDB102Dialect", "org.hibernate.dialect.MariaDB103Dialect",
            "org.hibernate.dialect.H2LegacyDialect"
    );
    private static final Set<String> REMOVED_ANNOTATIONS = Set.of(
            "org.hibernate.annotations.Proxy", "org.hibernate.annotations.Loader",
            "org.hibernate.annotations.Persister", "org.hibernate.annotations.SelectBeforeUpdate"
    );

    @Override
    public String getDisplayName() {
        return "Find exact Hibernate 7 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks attributed removed Session/Criteria/transformer APIs, versioned dialects, annotations, custom " +
               "types, queries, and extension SPIs with decision-specific migration guidance.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedHibernateCoreDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                TypeTree type = visited.getTypeExpression();
                String message = type == null ? null : typeMessage(type.getType());
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withTypeExpression(marked);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                TypeTree type = visited.getReturnTypeExpression();
                String message = type == null ? null : typeMessage(type.getType());
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withReturnTypeExpression(marked);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                TypeTree base = visited.getExtends();
                if (base != null) {
                    String message = typeMessage(base.getType());
                    if (message != null) {
                        TypeTree marked = mark(base, message);
                        if (marked != base) visited = visited.withExtends(marked);
                    }
                }
                return visited.withImplements(org.openrewrite.internal.ListUtils.map(visited.getImplements(), type -> {
                    String message = typeMessage(type.getType());
                    return message == null ? type : mark(type, message);
                }));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                TypeTree type = visited.getClazz();
                String message = type == null ? null : typeMessage(type.getType());
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withClazz(marked);
            }

            @Override
            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J.TypeCast visited = super.visitTypeCast(typeCast, ctx);
                String message = typeMessage(visited.getClazz().getType());
                if (message == null) return visited;
                J.ControlParentheses<TypeTree> marked = mark(visited.getClazz(), message);
                return marked == visited.getClazz() ? visited : visited.withClazz(marked);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String fqn = typeName(visited.getType());
                if (fqn != null && REMOVED_ANNOTATIONS.contains(fqn)) {
                    return mark(visited, "This Hibernate annotation is removed or no longer a stable mapping contract in 7.2; redesign this exact mapping/lazy-loading/customization boundary and validate metadata plus generated SQL");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                JavaType.Variable field = visited.getName().getFieldType();
                String owner = field == null ? null : typeName(field.getOwner());
                if ("org.hibernate.annotations.CascadeType".equals(owner) &&
                    Set.of("SAVE_UPDATE", "REPLICATE", "LOCK").contains(visited.getSimpleName())) {
                    J.Identifier marked = mark(visited.getName(),
                            "This Hibernate-specific cascade value was removed; choose Jakarta persist/merge/remove/refresh/detach semantics explicitly and test transient, managed, detached, orphan, flush, and database cascade behavior");
                    return marked == visited.getName() ? visited : visited.withName(marked);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = visited.getSimpleName();
                String message = methodMessage(owner, name, method.getParameterTypes().size());
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static String methodMessage(String owner, String name, int parameterCount) {
        if (TypeUtils.isAssignableTo("org.hibernate.Session", JavaType.ShallowClass.build(owner))) {
            if (Set.of("save", "update", "saveOrUpdate", "delete").contains(name)) {
                return "Removed Session." + name + " operation detected; choose persist, merge, remove, or another target operation from the entity's transient/managed/detached state and verify cascades, identifiers, flush ordering, exceptions, and transaction rollback";
            }
            if ("load".equals(name) && parameterCount != 2) {
                return "This Session.load overload is not the deterministic two-argument form; choose getReference, find, explicit locking, or an entity-copy strategy and verify proxy initialization, EntityNotFoundException timing, lock modes, and transaction scope";
            }
            if ("createCriteria".equals(name)) {
                return "Session.createCriteria was removed; rebuild this exact query with Jakarta Criteria/HQL and test joins, fetches, projections, filters, pagination, locking, and generated SQL";
            }
            if ("createSQLQuery".equals(name)) {
                return "createSQLQuery was removed; migrate to createNativeQuery with an explicit result contract and verify scalar/entity mapping, aliases, parameters, transformers, and database portability";
            }
            if ("createQuery".equals(name) || "createNativeQuery".equals(name)) {
                return "Hibernate 6/7 query typing and SQM/native result semantics changed; compile this exact query at startup and verify path comparisons, parameters, result type, aliases, pagination, transformers, and generated SQL";
            }
        }
        if ((owner.startsWith("org.hibernate.query.") || "org.hibernate.Query".equals(owner) ||
             "org.hibernate.SQLQuery".equals(owner)) &&
            Set.of("setResultTransformer", "setResultSetMapping").contains(name)) {
            return "This legacy query result transformation boundary changed; choose TupleTransformer, ResultListTransformer, constructor projection, or named result-set mapping and verify aliases, duplicates, scalar types, pagination, and list post-processing";
        }
        return null;
    }

    private static String typeMessage(JavaType type) {
        String fqn = typeName(type);
        if (fqn == null) return null;
        String exact = TYPE_MESSAGES.get(fqn);
        if (exact != null) return exact;
        if (VERSIONED_DIALECTS.contains(fqn)) {
            return "Version-specific or legacy dialect detected; confirm the production database version, use the supported generic/community dialect deliberately, and validate DDL, sequences, identity, pagination, locking, temporal/JSON/array types, and schema migration";
        }
        return null;
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(type);
        return fqn == null ? null : fqn.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        boolean present = tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()));
        return present ? tree : SearchResult.found(tree, message);
    }
}
