package com.huawei.clouds.openrewrite.springcloudcontext;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;

/** Migrate the Boot 2 document-profile selector removed by Boot 3. */
public final class MigrateLegacyProfileActivation extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate legacy Spring profile document activation";
    }

    @Override
    public String getDescription() {
        return "Changes only the exact spring.profiles document selector to spring.config.activate.on-profile in " +
               "application and bootstrap YAML/properties files; active/include profile controls remain untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringCloudContextSupport.generated(source.getSourcePath()) ||
                    !springConfig(source)) return tree;
                if (tree instanceof Yaml.Documents yaml) {
                    return new org.openrewrite.yaml.ChangePropertyKey(
                            "spring.profiles", "spring.config.activate.on-profile", false, null, null)
                            .getVisitor().visitNonNull(yaml, ctx);
                }
                if (tree instanceof Properties.File properties) {
                    return new org.openrewrite.properties.ChangePropertyKey(
                            "spring.profiles", "spring.config.activate.on-profile", false, false)
                            .getVisitor().visitNonNull(properties, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean springConfig(SourceFile source) {
        if (source.getSourcePath().getFileName() == null) return false;
        String file = source.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return (file.startsWith("application") || file.startsWith("bootstrap")) &&
               (file.endsWith(".yml") || file.endsWith(".yaml") || file.endsWith(".properties"));
    }
}
