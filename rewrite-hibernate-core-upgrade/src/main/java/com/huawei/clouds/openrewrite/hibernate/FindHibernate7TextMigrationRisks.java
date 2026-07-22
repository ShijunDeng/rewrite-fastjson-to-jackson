package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact Groovy, mapping, and configuration snippets with Hibernate 7 migration guidance. */
public final class FindHibernate7TextMigrationRisks extends Recipe {
    private record Risk(Pattern pattern, String message) {}
    private record Match(int start, int end, String message) {}

    private static final List<Risk> GROOVY_RISKS = List.of(
            risk("\\b(?:session|hibernateSession|currentSession\\s*\\(\\s*\\)|getCurrentSession\\s*\\(\\s*\\))\\s*\\.(?:save|update|saveOrUpdate|delete)\\s*\\(",
                    "Removed Session persistence operation; choose persist/merge/remove from entity state and verify cascades, identifiers, flush ordering, exceptions, and rollback"),
            risk("\\b(?:session|hibernateSession|currentSession\\s*\\(\\s*\\)|getCurrentSession\\s*\\(\\s*\\))\\s*\\.(?:createCriteria|createSQLQuery)\\s*\\(",
                    "Legacy Criteria/SQL query entry point was removed; rebuild with Jakarta Criteria/HQL/createNativeQuery and verify joins, projections, parameters, result mapping, and generated SQL"),
            risk("\\.(?:setResultTransformer|setResultSetMapping)\\s*\\(",
                    "Legacy result transformation changed; choose TupleTransformer, ResultListTransformer, constructor projection, or named mapping and verify aliases, duplicates, scalar types, and pagination"),
            risk("\\b(?:Criteria|DetachedCriteria|Restrictions|Projections|AliasToBeanResultTransformer|AliasToEntityMapResultTransformer|EmptyInterceptor|UserType|CompositeUserType)\\b",
                    "Legacy Hibernate API/SPI type detected; migrate this exact boundary against the 7.2 contract and verify query/type/interceptor semantics with integration tests"),
            risk("\\b(?:MySQL5Dialect|MySQL57Dialect|MySQL8Dialect|PostgreSQL9Dialect|PostgreSQL10Dialect|Oracle12cDialect|SQLServer2012Dialect|MariaDB10[123]Dialect|H2LegacyDialect)\\b",
                    "Version-specific/legacy dialect detected; confirm the real database version, select a supported generic/community dialect, and validate DDL, identity/sequence, pagination, locking, and types")
    );
    private static final List<Risk> HBM_RISKS = List.of(
            risk("<hibernate-mapping\\b", "hbm.xml is deprecated and several mapping features changed; migrate this mapping to annotations/orm.xml or validate every retained element against Hibernate 7.2"),
            risk("<(?:sql-query|return-join|loader)\\b", "This legacy hbm query/loader construct changed or was removed; replace it with an explicit supported query/result mapping and verify aliases, joins, callable/native SQL, and result types"),
            risk("<(?:generator|composite-id)\\b", "Legacy identifier mapping detected; verify Hibernate 7 generator naming, sequence/table defaults, composite-id instantiation/equality, unsaved values, and production schema compatibility")
    );
    private static final List<Risk> CONFIG_RISKS = List.of(
            risk("(?m)^[ \\t]*hibernate\\.dialect[ \\t]*[=:][^\\r\\n]*", "Explicit/versioned dialect is a database compatibility decision; align it with the actual supported database and validate schema generation, SQL, locking, pagination, temporal/JSON/array types, and community-dialect needs"),
            risk("(?m)^[ \\t]*hibernate\\.(?:allow_refresh_detached_entity|temp\\.use_jdbc_metadata_defaults|query\\.plan_cache_parameter_metadata_max_size|query\\.immutable_entity_update_query_handling_mode|session_factory_name|session_factory_name_is_jndi)[^\\r\\n]*", "Removed or behavior-changing Hibernate setting detected; remove or replace it only after verifying bootstrap, detached-entity, query-plan, JNDI, and operational behavior"),
            risk("(?m)^[ \\t]*(?:hibernate\\.(?:hbm2ddl|cache|enhancer|bytecode|id|jdbc\\.batch_size|timezone\\.default_storage)|jakarta\\.persistence\\.schema-generation)[^\\r\\n]*", "Schema/cache/enhancement/generator/batching/timezone behavior crosses Hibernate major versions; retain or change this exact setting only with schema diff and runtime regression evidence")
    );

    @Override
    public String getDisplayName() {
        return "Find Hibernate 7 Groovy, mapping, and configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact text snippets in Hibernate-aware Groovy, hbm.xml, properties, and YAML files with " +
               "category-specific remediation instead of anonymous broad search markers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedHibernateCoreDependency.generated(source.getSourcePath())) return tree;
                List<Risk> risks = risks(source.getSourcePath(), source.printAll());
                return risks.isEmpty() ? tree : mark(source, risks);
            }
        };
    }

    private static List<Risk> risks(Path path, String source) {
        String name = path.getFileName().toString();
        if (name.endsWith(".groovy") && (source.contains("org.hibernate") || source.contains("Session"))) {
            return GROOVY_RISKS;
        }
        if (name.endsWith(".hbm.xml")) return HBM_RISKS;
        if (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml")) {
            return source.contains("hibernate.") || source.contains("jakarta.persistence.schema-generation")
                    ? CONFIG_RISKS : List.of();
        }
        return List.of();
    }

    private static SourceFile mark(SourceFile source, List<Risk> risks) {
        PlainText text = PlainTextParser.convert(source);
        List<PlainText.Snippet> input = ListUtils.concat(snippet(text.getText()), text.getSnippets());
        List<PlainText.Snippet> output = new ArrayList<>();
        boolean changed = false;
        for (PlainText.Snippet part : input) {
            if (part.getMarkers().findFirst(SearchResult.class).isPresent()) {
                output.add(part);
                continue;
            }
            List<Match> matches = matches(part.getText(), risks);
            if (matches.isEmpty()) {
                output.add(part);
                continue;
            }
            changed = true;
            int offset = 0;
            for (Match match : matches) {
                if (match.start() > offset) output.add(snippet(part.getText().substring(offset, match.start())));
                output.add(SearchResult.found(snippet(part.getText().substring(match.start(), match.end())),
                        match.message()));
                offset = match.end();
            }
            if (offset < part.getText().length()) output.add(snippet(part.getText().substring(offset)));
        }
        return changed ? text.withText("").withSnippets(output) : source;
    }

    private static List<Match> matches(String text, List<Risk> risks) {
        List<Match> candidates = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern().matcher(text);
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    candidates.add(new Match(matcher.start(), matcher.end(), risk.message()));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Match::start).thenComparing(
                Comparator.comparingInt(Match::end).reversed()));
        List<Match> accepted = new ArrayList<>();
        int end = -1;
        for (Match candidate : candidates) {
            if (candidate.start() >= end) {
                accepted.add(candidate);
                end = candidate.end();
            }
        }
        return accepted;
    }

    private static Risk risk(String regex, String message) {
        return new Risk(Pattern.compile(regex), message);
    }

    private static PlainText.Snippet snippet(String value) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, value);
    }
}
