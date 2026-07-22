package com.huawei.clouds.openrewrite.icu4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Mark attributed ICU4J boundaries affected by removed APIs or data-driven behavior changes. */
public final class FindIcu4jJavaMigrationRisks extends Recipe {
    private static final Map<String, String> REMOVED_TYPES = Map.ofEntries(
            Map.entry("com.ibm.icu.text.ListFormatter$Style",
                    "ListFormatter.Style and its two-argument getInstance overload are removed in ICU4J 77; use the deterministic migration for known constants or choose Type and Width explicitly"),
            Map.entry("com.ibm.icu.text.PluralRanges",
                    "PluralRanges was a draft API and is absent in ICU4J 77; redesign this boundary with supported plural-range formatting and regression-test locale-specific output"),
            Map.entry("com.ibm.icu.text.PluralSamples",
                    "PluralSamples is absent in ICU4J 77; replace this draft API with supported PluralRules sampling APIs and verify sample semantics"),
            Map.entry("com.ibm.icu.text.PluralRules$FixedDecimalRange",
                    "PluralRules.FixedDecimalRange is absent in ICU4J 77; migrate to supported decimal-quantity APIs only after reviewing endpoint and exponent semantics"),
            Map.entry("com.ibm.icu.util.NoUnit",
                    "NoUnit constants are exposed as MeasureUnit in ICU4J 77 and NoUnit is no longer a MeasureUnit subtype; review assignments, overload selection, serialization, and reflection")
    );
    private static final Set<String> FORMATTING_TYPES = Set.of(
            "com.ibm.icu.text.NumberFormat", "com.ibm.icu.text.DecimalFormat",
            "com.ibm.icu.number.NumberFormatter", "com.ibm.icu.text.MeasureFormat",
            "com.ibm.icu.text.PluralRules", "com.ibm.icu.text.RuleBasedNumberFormat",
            "com.ibm.icu.text.ListFormatter", "com.ibm.icu.text.DateFormat",
            "com.ibm.icu.text.SimpleDateFormat", "com.ibm.icu.text.DateTimePatternGenerator"
    );
    private static final Set<String> COLLATION_TYPES = Set.of(
            "com.ibm.icu.text.Collator", "com.ibm.icu.text.RuleBasedCollator", "com.ibm.icu.text.StringSearch"
    );
    private static final Set<String> SEGMENTATION_TYPES = Set.of(
            "com.ibm.icu.text.BreakIterator", "com.ibm.icu.text.RuleBasedBreakIterator"
    );
    private static final Set<String> TIMEZONE_TYPES = Set.of(
            "com.ibm.icu.util.TimeZone", "com.ibm.icu.util.BasicTimeZone", "com.ibm.icu.util.Calendar",
            "com.ibm.icu.util.GregorianCalendar"
    );

    @Override
    public String getDisplayName() {
        return "Find ICU4J 77 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark only type-attributed ICU4J APIs whose compatibility depends on removed draft/internal APIs or " +
               "Unicode 16, CLDR 47, tzdata, collation, segmentation, locale matching, IDNA, and formatting data.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedIcu4jDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) return visited;
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null || !visited.getSimpleName().equals(type.getClassName())) return visited;
                String fqn = type.getFullyQualifiedName();
                String message = REMOVED_TYPES.get(fqn);
                if (message == null && fqn.startsWith("com.ibm.icu.impl.")) {
                    message = "com.ibm.icu.impl is unsupported ICU implementation API and multiple members were removed by 77.1; replace this exact boundary with public com.ibm.icu APIs and test binary/classloader behavior";
                }
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String message = methodMessage(method);
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static String methodMessage(JavaType.Method method) {
        String owner = method.getDeclaringType().getFullyQualifiedName();
        String name = method.getName();
        if ("com.ibm.icu.impl".equals(owner) || owner.startsWith("com.ibm.icu.impl.")) {
            return "com.ibm.icu.impl is unsupported ICU implementation API and multiple members were removed by 77.1; replace this exact boundary with public com.ibm.icu APIs and test binary/classloader behavior";
        }
        if ("com.ibm.icu.util.BasicTimeZone".equals(owner) && "getOffsetFromLocal".equals(name) &&
            isRemovedOffsetSignature(method)) {
            return "BasicTimeZone.getOffsetFromLocal(long,int,int,int[]) was removed; map the former LOCAL_* bit flags to LocalOption only after choosing former/latter and standard/daylight overlap semantics";
        }
        if ("com.ibm.icu.text.IDNA".equals(owner) &&
            (name.startsWith("convert") || name.startsWith("compare"))) {
            return "This legacy IDNA2003 API is deprecated; ICU 76 also changed IDNA.DEFAULT from 0 to non-transitional UTS #46 options, so preserve intended flags and add Unicode/security conformance cases before migrating";
        }
        if ("com.ibm.icu.util.LocaleMatcher".equals(owner) || "com.ibm.icu.util.ULocale".equals(owner)) {
            return "Locale fallback/matching data changed across ICU 69-77 (including Norwegian parenting, en-CA/en-PH matching, uk-to-ru fallback, and CLDR 47); regression-test every supported locale and fallback contract";
        }
        if (COLLATION_TYPES.contains(owner)) {
            return "ICU collation data changed through CLDR 47, including root/DUCET, Han ordering, and Swedish/Finnish behavior; regenerate golden sort/search results for every locale and tailored rule set";
        }
        if (SEGMENTATION_TYPES.contains(owner)) {
            return "ICU 77 changed word, line, and grapheme segmentation, including colon tailoring, NBSP/combining/hyphen handling, and Indic conjuncts; regression-test boundary indexes and tokenization";
        }
        if (FORMATTING_TYPES.contains(owner)) {
            return "Unicode/CLDR formatting data changed through ICU 77.1; regression-test exact numbers, plurals, lists, dates, patterns, currencies, units, parsing, and locale fallback rather than assuming output stability";
        }
        if (TIMEZONE_TYPES.contains(owner)) {
            return "ICU timezone data and pre-1970 aliases changed through tzdata 2025a; regression-test historical/future offsets, transitions, parsing, serialization, and configured zone identifiers";
        }
        return null;
    }

    private static boolean isRemovedOffsetSignature(JavaType.Method method) {
        var parameters = method.getParameterTypes();
        return parameters.size() == 4 && parameters.get(0) == JavaType.Primitive.Long &&
               parameters.get(1) == JavaType.Primitive.Int && parameters.get(2) == JavaType.Primitive.Int &&
               parameters.get(3) instanceof JavaType.Array array && array.getElemType() == JavaType.Primitive.Int;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
