package com.huawei.clouds.openrewrite.hikaricp;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/** Migrates credential patterns whose HikariCP 6 equivalent is deterministic. */
public final class MigrateHikariCredentials extends Recipe {
    private static final String HIKARI_DATA_SOURCE = "com.zaxxer.hikari.HikariDataSource";
    private static final String CREDENTIALS = "com.zaxxer.hikari.util.Credentials";
    private static final MethodMatcher SET_USERNAME =
            new MethodMatcher("com.zaxxer.hikari.HikariConfig setUsername(java.lang.String)", true);
    private static final MethodMatcher SET_PASSWORD =
            new MethodMatcher("com.zaxxer.hikari.HikariConfig setPassword(java.lang.String)", true);

    @Override
    public String getDisplayName() {
        return "Migrate HikariCP 6 credentials";
    }

    @Override
    public String getDescription() {
        return "Combine adjacent username/password updates into the atomic Credentials API and add a " +
               "getCredentials() compatibility override to HikariDataSource subclasses whose dynamic " +
               "getUsername() or getPassword() override would otherwise be bypassed by HikariCP 6.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate selectedCredentials = JavaTemplate.builder(
                    "#{any()}.setCredentials(Credentials.of(#{any()}, #{any()}))")
                    .imports(CREDENTIALS)
                    .javaParser(targetHikariParser())
                    .build();
            private final JavaTemplate implicitCredentials = JavaTemplate.builder(
                    "setCredentials(Credentials.of(#{any()}, #{any()}))")
                    .imports(CREDENTIALS)
                    .javaParser(targetHikariParser())
                    .build();
            private final JavaTemplate dataSourceCompatibilityOverride = JavaTemplate.builder(
                    """
                    @Override
                    public Credentials getCredentials() {
                        return Credentials.of(getUsername(), getPassword());
                    }
                    """)
                    .imports(CREDENTIALS)
                    .contextSensitive()
                    .javaParser(targetHikariParser())
                    .build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (!UpgradeSelectedHikariCPDependency.isProjectPath(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                List<Statement> statements = b.getStatements();
                if (statements.size() < 2) {
                    return b;
                }

                List<Statement> migrated = new ArrayList<>(statements.size());
                boolean changed = false;
                for (int i = 0; i < statements.size(); i++) {
                    Statement current = statements.get(i);
                    if (i + 1 < statements.size() &&
                        current instanceof J.MethodInvocation username &&
                        statements.get(i + 1) instanceof J.MethodInvocation password &&
                        SET_USERNAME.matches(username) && SET_PASSWORD.matches(password) &&
                        sameSelect(username.getSelect(), password.getSelect()) &&
                        stableSelect(username.getSelect()) &&
                        password.getPrefix().getComments().isEmpty() &&
                        username.getArguments().size() == 1 && password.getArguments().size() == 1) {
                        maybeAddImport(CREDENTIALS);
                        Cursor invocationCursor = new Cursor(getCursor(), username);
                        J.MethodInvocation replacement;
                        if (username.getSelect() == null) {
                            replacement = implicitCredentials.apply(invocationCursor,
                                    username.getCoordinates().replace(),
                                    username.getArguments().get(0), password.getArguments().get(0));
                        } else {
                            replacement = selectedCredentials.apply(invocationCursor,
                                    username.getCoordinates().replace(), username.getSelect(),
                                    username.getArguments().get(0), password.getArguments().get(0));
                        }
                        migrated.add(replacement.withPrefix(username.getPrefix()));
                        i++;
                        changed = true;
                    } else {
                        migrated.add(current);
                    }
                }
                return changed ? b.withStatements(migrated) : b;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (!TypeUtils.isAssignableTo(HIKARI_DATA_SOURCE, c.getType()) ||
                    declaresNoArgMethod(c, "getCredentials") ||
                    !(declaresNoArgMethod(c, "getUsername") || declaresNoArgMethod(c, "getPassword"))) {
                    return c;
                }

                maybeAddImport(CREDENTIALS);
                J.Block body = dataSourceCompatibilityOverride.apply(
                        new Cursor(getCursor(), c.getBody()), c.getBody().getCoordinates().lastStatement());
                return c.withBody(body);
            }
        };
    }

    private static boolean sameSelect(Expression left, Expression right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.printTrimmed().equals(right.printTrimmed());
    }

    private static boolean stableSelect(Expression select) {
        if (select == null || select instanceof J.Identifier) {
            return true;
        }
        if (select instanceof J.FieldAccess fieldAccess) {
            return stableSelect(fieldAccess.getTarget());
        }
        return false;
    }

    private static boolean declaresNoArgMethod(J.ClassDeclaration classDecl, String name) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .anyMatch(method -> name.equals(method.getSimpleName()) &&
                                    (method.getParameters().isEmpty() ||
                                     method.getParameters().size() == 1 &&
                                     method.getParameters().get(0) instanceof J.Empty));
    }

    private static JavaParser.Builder<?, ?> targetHikariParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package com.zaxxer.hikari.util;

                public final class Credentials {
                    public static Credentials of(String username, String password) {
                        return new Credentials();
                    }

                    public String getUsername() {
                        return null;
                    }

                    public String getPassword() {
                        return null;
                    }
                }
                """,
                """
                package com.zaxxer.hikari;

                import com.zaxxer.hikari.util.Credentials;

                public class HikariConfig {
                    public void setCredentials(Credentials credentials) {
                    }

                    public String getUsername() {
                        return null;
                    }

                    public String getPassword() {
                        return null;
                    }
                }
                """,
                """
                package com.zaxxer.hikari;

                public class HikariDataSource extends HikariConfig {
                }
                """
        );
    }
}
