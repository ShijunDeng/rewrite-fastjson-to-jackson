package com.huawei.clouds.openrewrite.logbackcore;

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

/** Locate source boundaries proved by the fixed official 1.2.x to 1.5.34 API diff. */
public final class FindLogbackCore1534SourceRisks extends Recipe {
    static final String JORAN =
            "Joran was rewritten around a two-phase Model pipeline: GenericConfigurator, Interpreter, " +
            "InterpretationContext, implicit actions and several Action/RuleStore extension contracts were removed " +
            "or changed; redesign this extension against the 1.5.34 model APIs and test reconfiguration/idempotence";
    static final String DATABASE =
            "Database appenders and ch.qos.logback.core.db were removed from Logback; select the separately versioned " +
            "logback-db artifacts or another sink, migrate schema/credentials explicitly, and test failure/retry behavior";
    static final String ROLLING_TIME =
            "Rolling extension APIs moved from java.util.Date/time-zone state to Instant/ZoneId and length counters; " +
            "ArchiveRemover, RollingCalendar, DateTokenConverter and TimeBased policy subclasses require a semantic rewrite";
    static final String ROLLING_POLICY =
            "This custom rolling/file extension depends on collision, triggering or byte-count behavior changed by " +
            "Logback 1.5.x; validate rollover boundaries, restart indexes, prudent mode, compression and retention deletion";
    static final String WATCH_LIST =
            "ConfigurationWatchList.changeDetected() changed from boolean to the changed File, while URL watching and " +
            "topURL semantics were added; rewrite control flow and test file/HTTP scan, deletion and reappearance";
    static final String CONTEXT =
            "Context.getConfigurationLock now returns ReentrantLock and Context gained configuration-event and sequence " +
            "number contracts; custom Context implementations, casts and synchronization must be rebuilt and concurrency-tested";
    static final String STATUS =
            "This custom StatusListener/status servlet boundary crosses lifecycle and Jakarta servlet changes; verify " +
            "listener installation/removal, duplicate output, reset/reconfigure behavior and POST-only status clearing";
    static final String JAKARTA =
            "Logback 1.5 optional servlet and mail components use jakarta.* instead of javax.*; coordinate the container, " +
            "mail provider, JPMS modules and application namespace before changing imports";
    static final String RECEIVER =
            "Receiver/server-socket components were disabled and then removed by 1.5.27; replace the remote logging " +
            "transport and do not preserve Java-object-stream trust assumptions";
    static final String DESERIALIZATION =
            "HardenedObjectInputStream and object-writer contracts changed; 1.5.34 rejects Proxy classes to close " +
            "CVE-2026-10532—review every allow-list, serialized peer, replay/rollback path and untrusted input boundary";
    static final String JANINO =
            "JaninoEventEvaluatorBase was removed and conditional/evaluator processing was repeatedly hardened; " +
            "replace custom Java-expression evaluators with an approved condition/filter and threat-model configuration writes";
    static final String REMOVED_INTERNAL =
            "This Logback Core extension calls a removed or descriptor-changed internal API; there is no proven " +
            "one-to-one replacement, so redesign it against public 1.5.34 contracts and add binary/reflection tests";

    private static final Set<String> JORAN_TYPES = Set.of(
            "ch.qos.logback.core.joran.GenericConfigurator",
            "ch.qos.logback.core.joran.spi.Interpreter",
            "ch.qos.logback.core.joran.spi.InterpretationContext",
            "ch.qos.logback.core.joran.action.ImplicitAction",
            "ch.qos.logback.core.joran.action.NestedBasicPropertyIA",
            "ch.qos.logback.core.joran.action.NestedComplexPropertyIA",
            "ch.qos.logback.core.joran.action.IADataForComplexProperty",
            "ch.qos.logback.core.joran.conditional.ThenOrElseActionBase",
            "ch.qos.logback.core.joran.event.InPlayListener",
            "ch.qos.logback.core.sift.AbstractAppenderFactoryUsingJoran",
            "ch.qos.logback.core.sift.SiftingJoranConfiguratorBase");
    private static final Set<String> ROLLING_TYPES = Set.of(
            "ch.qos.logback.core.rolling.helper.ArchiveRemover",
            "ch.qos.logback.core.rolling.helper.RollingCalendar",
            "ch.qos.logback.core.rolling.helper.TimeBasedArchiveRemover",
            "ch.qos.logback.core.rolling.helper.SizeAndTimeBasedArchiveRemover",
            "ch.qos.logback.core.rolling.helper.DateTokenConverter",
            "ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicyBase",
            "ch.qos.logback.core.util.CachingDateFormatter");
    private static final Set<String> REMOVED_CORE_FIELDS = Set.of(
            "RFA_FILENAME_PATTERN_COLLISION_MAP", "FA_FILENAME_COLLISION_MAP",
            "RECONFIGURE_ON_CHANGE_TASK", "REFERENCE_BIPS");
    private static final Map<String, Set<String>> REMOVED_METHODS = Map.ofEntries(
            Map.entry("ch.qos.logback.core.ContextBase", Set.of("initCollisionMaps")),
            Map.entry("ch.qos.logback.core.FileAppender",
                    Set.of("addErrorForCollision", "checkForFileCollisionInPreviousFileAppenders")),
            Map.entry("ch.qos.logback.core.html.HTMLLayoutBase", Set.of("getDefaultConverterMap")),
            Map.entry("ch.qos.logback.core.rolling.helper.DateTokenConverter", Set.of("getTimeZone")),
            Map.entry("ch.qos.logback.core.util.CachingDateFormatter", Set.of("setTimeZone")),
            Map.entry("ch.qos.logback.core.util.ContextUtil",
                    Set.of("getLocalHostName", "safelyGetLocalHostName",
                           "getFilenameCollisionMap", "getFilenamePatternCollisionMap")),
            Map.entry("ch.qos.logback.core.util.Loader", Set.of("getClassLoaderAsPrivileged")),
            Map.entry("ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicyBase",
                    Set.of("computeNextCheck", "setDateInCurrentPeriod"))
    );

    @Override
    public String getDisplayName() {
        return "Find Logback Core 1.5.34 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed Joran/database/receiver APIs and rolling, serialization, status/listener, Jakarta, " +
               "Context and configuration-watch contracts that require application decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return UpgradeSelectedLogbackCoreDependency.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String message = typeMessage(visited.getTypeName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType type = visited.getType();
                if (assignable("ch.qos.logback.core.joran.action.Action", type) ||
                    assignable("ch.qos.logback.core.joran.spi.RuleStore", type) ||
                    assignable("ch.qos.logback.core.joran.GenericConfigurator", type)) {
                    return mark(visited, JORAN);
                }
                if (assignable("ch.qos.logback.core.Context", type)) return mark(visited, CONTEXT);
                if (assignable("ch.qos.logback.core.rolling.helper.ArchiveRemover", type) ||
                    assignable("ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicy", type)) {
                    return mark(visited, ROLLING_TIME);
                }
                if (assignable("ch.qos.logback.core.status.StatusListener", type) ||
                    assignable("ch.qos.logback.core.status.ViewStatusMessagesServletBase", type)) {
                    return mark(visited, STATUS);
                }
                if (assignable("ch.qos.logback.core.net.HardenedObjectInputStream", type)) {
                    return mark(visited, DESERIALIZATION);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = fqn(visited.getType());
                String message = typeMessage(type);
                if (message != null) return mark(visited, message);
                if ("ch.qos.logback.core.joran.spi.ConfigurationWatchList".equals(type)) {
                    return mark(visited, WATCH_LIST);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String name = visited.getSimpleName();
                if ("ch.qos.logback.core.joran.spi.ConfigurationWatchList".equals(owner) &&
                    "changeDetected".equals(name)) return mark(visited, WATCH_LIST);
                if (owner.startsWith("ch.qos.logback.core.joran.") ||
                    owner.startsWith("ch.qos.logback.core.sift.")) {
                    if (JORAN_TYPES.contains(owner) || Set.of(
                            "begin", "body", "end", "finish", "addRule", "matchActions",
                            "getInterpretationContext", "buildInterpreter").contains(name)) {
                        return mark(visited, JORAN);
                    }
                }
                if (ROLLING_TYPES.contains(owner)) return mark(visited, ROLLING_TIME);
                if ("ch.qos.logback.core.rolling.RollingFileAppender".equals(owner) &&
                    Set.of("start", "rollover").contains(name)) return mark(visited, ROLLING_POLICY);
                if ("ch.qos.logback.core.net.HardenedObjectInputStream".equals(owner) ||
                    "ch.qos.logback.core.net.ObjectWriter".equals(owner) ||
                    "ch.qos.logback.core.net.ObjectWriterFactory".equals(owner)) {
                    return mark(visited, DESERIALIZATION);
                }
                Set<String> removed = REMOVED_METHODS.get(owner);
                if (removed != null && removed.contains(name)) return mark(visited, REMOVED_INTERNAL);
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = visited.getFieldType();
                if (field != null && "ch.qos.logback.core.CoreConstants".equals(fqn(field.getOwner())) &&
                    REMOVED_CORE_FIELDS.contains(field.getName())) return mark(visited, REMOVED_INTERNAL);
                return visited;
            }
        };
    }

    private static String typeMessage(String type) {
        if (type == null) return null;
        if (type.startsWith("ch.qos.logback.core.db.") ||
            type.startsWith("ch.qos.logback.classic.db.")) return DATABASE;
        if (JORAN_TYPES.contains(type) || type.startsWith("ch.qos.logback.core.joran.event.stax.")) return JORAN;
        if (ROLLING_TYPES.contains(type)) return ROLLING_TIME;
        if ("ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP".equals(type)) return ROLLING_POLICY;
        if (type.startsWith("ch.qos.logback.core.net.server.") ||
            type.contains("SocketReceiver") || type.endsWith("ReceiverBase")) return RECEIVER;
        if ("ch.qos.logback.core.net.HardenedObjectInputStream".equals(type) ||
            "ch.qos.logback.core.net.ObjectWriter".equals(type) ||
            "ch.qos.logback.core.net.ObjectWriterFactory".equals(type) ||
            "ch.qos.logback.core.spi.PreSerializationTransformer".equals(type)) return DESERIALIZATION;
        if ("ch.qos.logback.core.boolex.JaninoEventEvaluatorBase".equals(type) ||
            type.endsWith(".boolex.JaninoEventEvaluator")) return JANINO;
        if ("ch.qos.logback.core.status.ViewStatusMessagesServletBase".equals(type) ||
            type.endsWith(".ViewStatusMessagesServlet")) return STATUS;
        if ("ch.qos.logback.core.net.SMTPAppenderBase".equals(type) ||
            "ch.qos.logback.core.net.LoginAuthenticator".equals(type)) return JAKARTA;
        return null;
    }

    private static boolean assignable(String target, JavaType type) {
        return type != null && TypeUtils.isAssignableTo(target, type);
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
