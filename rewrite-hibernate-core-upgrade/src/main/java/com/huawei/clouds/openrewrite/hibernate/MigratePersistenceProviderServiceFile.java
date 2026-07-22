package com.huawei.clouds.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.nio.file.Path;

/** Renames only the standard persistence-provider service descriptor outside generated trees. */
public final class MigratePersistenceProviderServiceFile extends Recipe {
    private static final String OLD_PATH = "META-INF/services/javax.persistence.spi.PersistenceProvider";
    private static final String NEW_NAME = "jakarta.persistence.spi.PersistenceProvider";

    @Override
    public String getDisplayName() {
        return "Migrate the PersistenceProvider service descriptor path";
    }

    @Override
    public String getDescription() {
        return "Rename the standard Javax Persistence provider service descriptor to its Jakarta Persistence name " +
               "while leaving generated resource trees untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedHibernateCoreDependency.generated(source.getSourcePath())) return tree;
                Path path = source.getSourcePath();
                String normalized = path.normalize().toString().replace('\\', '/');
                boolean serviceDescriptor = OLD_PATH.equals(normalized) || normalized.endsWith("/" + OLD_PATH);
                return serviceDescriptor ? source.withSourcePath(path.resolveSibling(NEW_NAME)) : tree;
            }
        };
    }
}
