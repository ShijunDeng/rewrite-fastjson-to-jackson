package com.huawei.clouds.openrewrite.grafana;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strictly upgrades XLSX-selected Grafana image tags in deployment-owned locations. */
public final class UpgradeSelectedGrafanaImage extends Recipe {
    private static final Pattern DOCKER_FROM = Pattern.compile(
            "(?m)^([ \\t]*FROM[ \\t]+(?:--platform=\\S+[ \\t]+)?)(\\S+)(?:([ \\t]+AS[ \\t]+\\S+[ \\t]*)|([ \\t]*))$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getDisplayName() {
        return "Upgrade selected Grafana images to 12.1.1";
    }

    @Override
    public String getDescription() {
        return "Upgrades only exact XLSX-listed Grafana Docker image tags in YAML image owners, Helm repository/tag mappings, and Dockerfile FROM instructions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !GrafanaMigrationSupport.isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof Yaml.Documents yaml) return upgradeYaml(yaml, ctx);
                if (tree instanceof PlainText text && isDockerfile(text)) return upgradeDockerfile(text);
                return tree;
            }
        };
    }

    private static Yaml.Documents upgradeYaml(Yaml.Documents documents, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext p) {
                Yaml.Mapping visited = super.visitMapping(mapping, p);
                if (GrafanaMigrationSupport.yamlKeyCount(visited, "repository") != 1 ||
                    GrafanaMigrationSupport.yamlKeyCount(visited, "tag") != 1) return visited;
                String repository = GrafanaMigrationSupport.yamlValue(visited, "repository");
                if (!repository.matches("(?:(?:docker|registry-1)[.]io/)?grafana/grafana(?:-enterprise|-oss)?")) {
                    return visited;
                }
                for (int i = 0; i < visited.getEntries().size(); i++) {
                    Yaml.Mapping.Entry entry = visited.getEntries().get(i);
                    if (!"tag".equals(GrafanaMigrationSupport.yamlKey(entry)) ||
                        !(entry.getValue() instanceof Yaml.Scalar scalar) ||
                        !GrafanaMigrationSupport.SOURCE_VERSIONS.contains(scalar.getValue().trim())) continue;
                    var entries = new java.util.ArrayList<>(visited.getEntries());
                    entries.set(i, entry.withValue(scalar.withValue(GrafanaMigrationSupport.TARGET_VERSION)));
                    return visited.withEntries(entries);
                }
                return visited;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                if (!"image".equals(GrafanaMigrationSupport.yamlKey(visited)) ||
                    !(visited.getValue() instanceof Yaml.Scalar scalar) ||
                    !GrafanaMigrationSupport.uniqueYamlKey(getCursor(), "image")) return visited;
                String upgraded = GrafanaMigrationSupport.upgradeImage(scalar.getValue());
                return upgraded.equals(scalar.getValue()) ? visited : visited.withValue(scalar.withValue(upgraded));
            }
        }.visitNonNull(documents, ctx);
    }

    private static PlainText upgradeDockerfile(PlainText text) {
        Matcher matcher = DOCKER_FROM.matcher(text.getText());
        StringBuffer result = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String upgraded = GrafanaMigrationSupport.upgradeImage(matcher.group(2));
            if (!upgraded.equals(matcher.group(2))) changed = true;
            String suffix = matcher.group(3) == null ? matcher.group(4) : matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + upgraded + suffix));
        }
        matcher.appendTail(result);
        return changed ? text.withText(result.toString()) : text;
    }

    private static boolean isDockerfile(PlainText text) {
        String name = text.getSourcePath().getFileName() == null ? "" :
                text.getSourcePath().getFileName().toString().toLowerCase();
        return "dockerfile".equals(name) || name.startsWith("dockerfile.") || name.endsWith(".dockerfile");
    }
}
