package com.huawei.clouds.openrewrite.shedlockspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;

/** Deterministic source migrations between the legacy ShedLock annotation contracts and 7.2.1. */
public final class MigrateShedLockSpring7Source extends Recipe {
    private static final String OLD_SCHEDULER_LOCK = "net.javacrumbs.shedlock.core.SchedulerLock";
    private static final String ENABLE_SCHEDULER_LOCK =
            "net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock";

    @Override
    public String getDisplayName() {
        return "Migrate deterministic ShedLock Spring 7 annotation constructs";
    }

    @Override
    public String getDescription() {
        return "Preserve the millisecond semantics of legacy SchedulerLock literals, rename its String duration " +
               "attributes, and move ShedLock 2.x InterceptMode configuration from mode to interceptMode.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                if (type == null) {
                    return a;
                }
                if (OLD_SCHEDULER_LOCK.equals(type.getFullyQualifiedName())) {
                    return migrateLegacySchedulerLock(a);
                }
                if (ENABLE_SCHEDULER_LOCK.equals(type.getFullyQualifiedName())) {
                    return migrateLegacyInterceptMode(a);
                }
                return a;
            }
        };
    }

    private static J.Annotation migrateLegacySchedulerLock(J.Annotation annotation) {
        List<Expression> arguments = annotation.getArguments();
        J.Assignment atMostNumeric = findAssignment(arguments, "lockAtMostFor");
        J.Assignment atLeastNumeric = findAssignment(arguments, "lockAtLeastFor");
        boolean unresolved = isNonLiteralNumber(atMostNumeric) || isNonLiteralNumber(atLeastNumeric);

        List<Expression> migrated = new ArrayList<>(arguments.size());
        for (Expression argument : arguments) {
            if (!(argument instanceof J.Assignment assignment)) {
                migrated.add(argument);
                continue;
            }
            String name = assignmentName(assignment);
            if ("lockAtMostFor".equals(name) || "lockAtLeastFor".equals(name)) {
                if (assignment.getAssignment() instanceof J.Literal literal && literal.getValue() instanceof Number number) {
                    if (number.longValue() >= 0) {
                        migrated.add(withStringMilliseconds(assignment, literal, number.longValue()));
                    }
                } else {
                    migrated.add(assignment);
                }
                continue;
            }
            if ("lockAtMostForString".equals(name)) {
                J.Assignment stringDuration = migrateStringDuration(assignment, atMostNumeric, "lockAtMostFor");
                if (stringDuration != null) {
                    migrated.add(stringDuration);
                }
                continue;
            }
            if ("lockAtLeastForString".equals(name)) {
                J.Assignment stringDuration = migrateStringDuration(assignment, atLeastNumeric, "lockAtLeastFor");
                if (stringDuration != null) {
                    migrated.add(stringDuration);
                }
                continue;
            }
            migrated.add(assignment);
        }
        J.Annotation result = annotation.withArguments(migrated);
        return unresolved ? SearchResult.found(result,
                "Legacy numeric lock duration is not a literal; convert its millisecond value to an explicit ShedLock 7 duration string") : result;
    }

    private static J.Annotation migrateLegacyInterceptMode(J.Annotation annotation) {
        List<Expression> migrated = annotation.getArguments().stream().map(argument -> {
            if (!(argument instanceof J.Assignment assignment) || !"mode".equals(assignmentName(assignment)) ||
                !isShedLockInterceptMode(assignment.getAssignment())) {
                return argument;
            }
            return renameAssignment(assignment, "interceptMode");
        }).toList();
        return annotation.withArguments(migrated);
    }

    private static J.Assignment findAssignment(List<Expression> arguments, String name) {
        return arguments.stream().filter(J.Assignment.class::isInstance).map(J.Assignment.class::cast)
                .filter(assignment -> name.equals(assignmentName(assignment))).findFirst().orElse(null);
    }

    private static String assignmentName(J.Assignment assignment) {
        return assignment.getVariable() instanceof J.Identifier identifier ? identifier.getSimpleName() : "";
    }

    private static boolean isNonLiteralNumber(J.Assignment assignment) {
        return assignment != null && !(assignment.getAssignment() instanceof J.Literal literal &&
                                       literal.getValue() instanceof Number);
    }

    private static boolean literalTakesPrecedence(J.Assignment assignment) {
        return assignment != null && assignment.getAssignment() instanceof J.Literal literal &&
               literal.getValue() instanceof Number number && number.longValue() >= 0;
    }

    private static J.Assignment migrateStringDuration(J.Assignment stringAssignment, J.Assignment numericAssignment,
                                                       String targetName) {
        if (literalTakesPrecedence(numericAssignment)) {
            return null;
        }
        if (isNonLiteralNumber(numericAssignment)) {
            return stringAssignment;
        }
        return renameAssignment(stringAssignment, targetName);
    }

    private static J.Assignment withStringMilliseconds(J.Assignment assignment, J.Literal literal, long value) {
        String milliseconds = Long.toString(value);
        J.Literal replacement = literal.withValue(milliseconds).withValueSource("\"" + milliseconds + "\"")
                .withType(JavaType.Primitive.String);
        return assignment.withAssignment(replacement);
    }

    private static J.Assignment renameAssignment(J.Assignment assignment, String newName) {
        if (assignment.getVariable() instanceof J.Identifier identifier) {
            return assignment.withVariable(identifier.withSimpleName(newName));
        }
        return assignment;
    }

    private static boolean isShedLockInterceptMode(Expression expression) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(expression.getType());
        if (type != null) {
            String fqn = type.getFullyQualifiedName();
            if (fqn.endsWith("EnableSchedulerLock$InterceptMode") ||
                fqn.endsWith("EnableSchedulerLock.InterceptMode")) {
                return true;
            }
        }
        String source = expression.printTrimmed();
        return source.equals("PROXY_METHOD") || source.equals("PROXY_SCHEDULER") ||
               source.endsWith(".PROXY_METHOD") || source.endsWith(".PROXY_SCHEDULER");
    }
}
