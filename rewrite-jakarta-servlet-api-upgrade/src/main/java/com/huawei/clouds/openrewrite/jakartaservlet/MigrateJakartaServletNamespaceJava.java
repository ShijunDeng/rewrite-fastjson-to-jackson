package com.huawei.clouds.openrewrite.jakartaservlet;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Migrate the Servlet namespace while refusing to manufacture names for types removed in Servlet 6.0. */
public final class MigrateJakartaServletNamespaceJava extends Recipe {
    private static final Set<String> REMOVED_TYPES = Set.of(
            "javax.servlet.SingleThreadModel",
            "javax.servlet.http.HttpSessionContext",
            "javax.servlet.http.HttpUtils");

    @Override
    public String getDisplayName() {
        return "Safely migrate the Java Servlet namespace to Jakarta";
    }

    @Override
    public String getDescription() {
        return "Migrate javax.servlet Java types to jakarta.servlet unless the compilation unit uses a type removed " +
               "in Servlet 6.0; such units remain unchanged for the risk recipe to mark without inventing types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit compilationUnit) || containsRemovedType(compilationUnit)) {
                    return tree;
                }
                return new ChangePackage("javax.servlet", "jakarta.servlet", true)
                        .getVisitor().visitNonNull(compilationUnit, ctx);
            }
        };
    }

    private static boolean containsRemovedType(J.CompilationUnit compilationUnit) {
        if (compilationUnit.getImports().stream().map(anImport -> anImport.getQualid().printTrimmed())
                .anyMatch(MigrateJakartaServletNamespaceJava::isRemovedTypeOrMember)) {
            return true;
        }
        for (JavaType type : compilationUnit.getTypesInUse().getTypesInUse()) {
            JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
            if (fullyQualified != null && REMOVED_TYPES.contains(fullyQualified.getFullyQualifiedName())) {
                return true;
            }
        }
        String source = compilationUnit.printAll();
        return REMOVED_TYPES.stream().anyMatch(source::contains);
    }

    private static boolean isRemovedTypeOrMember(String imported) {
        return REMOVED_TYPES.stream().anyMatch(type -> imported.equals(type) || imported.startsWith(type + "."));
    }
}
