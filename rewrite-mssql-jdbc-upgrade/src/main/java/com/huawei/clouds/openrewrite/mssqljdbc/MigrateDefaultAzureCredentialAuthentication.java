package com.huawei.clouds.openrewrite.mssqljdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies Microsoft's exact 12.2 driver authentication value rename in typed setters and complete JDBC URLs. */
public final class MigrateDefaultAzureCredentialAuthentication extends Recipe {
    private static final String OLD_VALUE = "DefaultAzureCredential";
    private static final String NEW_VALUE = "ActiveDirectoryDefault";
    private static final Pattern URL_AUTH = Pattern.compile(
            "(?i)(authentication\\s*=\\s*)DefaultAzureCredential(?=\\s*;|$)");

    @Override
    public String getDisplayName() {
        return "Rename SQL Server DefaultAzureCredential authentication";
    }

    @Override
    public String getDescription() {
        return "Replace the SQL Server JDBC authentication value DefaultAzureCredential with its official " +
               "12.2+ name ActiveDirectoryDefault in complete JDBC URLs and typed SQLServerDataSource setters.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value)) {
                    return l;
                }
                if (value.regionMatches(true, 0, "jdbc:sqlserver:", 0, "jdbc:sqlserver:".length())) {
                    Matcher matcher = URL_AUTH.matcher(value);
                    if (matcher.find()) {
                        return replace(l, value, matcher.replaceAll("$1" + NEW_VALUE));
                    }
                }
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (OLD_VALUE.equalsIgnoreCase(value) && invocation != null && "setAuthentication".equals(invocation.getSimpleName()) &&
                    isMicrosoftSqlServerMethod(invocation.getMethodType())) {
                    return replace(l, value, NEW_VALUE);
                }
                return l;
            }
        };
    }

    private static boolean isMicrosoftSqlServerMethod(JavaType.Method method) {
        JavaType.FullyQualified owner = method == null ? null : TypeUtils.asFullyQualified(method.getDeclaringType());
        return owner != null && owner.getFullyQualifiedName().startsWith("com.microsoft.sqlserver.jdbc.");
    }

    private static J.Literal replace(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(
                source == null ? null : source.replace(oldValue, newValue));
    }
}
