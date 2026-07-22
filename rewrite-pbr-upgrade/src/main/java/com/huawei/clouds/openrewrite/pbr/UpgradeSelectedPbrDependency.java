package com.huawei.clouds.openrewrite.pbr;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only exact, maintained pbr dependency owners listed in the workbook. */
public final class UpgradeSelectedPbrDependency extends Recipe {
    private static final String SOURCES = "(?:5\\.11\\.1|5\\.5\\.1)";
    private static final String END = "(?=\\s*(?:;|#|\\\\|--hash=|[\\\"'\\]\\)]|$))";
    private static final Pattern REQUIREMENT_PIN = Pattern.compile(
            "(?i)^(?!\\s*#)\\s*(?:-\\s*)?pbr\\s*={2,3}\\s*(?<version>" + SOURCES + ")" + END);
    private static final Pattern QUOTED_PIN = Pattern.compile(
            "(?i)[\\\"']pbr\\s*={2,3}\\s*(?<version>" + SOURCES + ")" + END);
    private static final Pattern POETRY_PIN = Pattern.compile(
            "(?i)^\\s*pbr\\s*=\\s*[\\\"'](?:==)?(?<version>" + SOURCES + ")(?=\\s*[\\\"'])");
    private static final Pattern ANY_PIN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_.-])pbr(?![A-Za-z0-9_.-])\\s*={2,3}\\s*(?<version>" + SOURCES + ")" + END);
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^]]+)]");
    private static final Pattern INI_KEY = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=(?!=)");
    private static final Pattern SETUP_DEPENDENCY_KEY = Pattern.compile(
            "(?i)\\b(?:setup_requires|install_requires|tests_require|requires)\\s*=");
    private static final Pattern DOCKER_PIP_INSTALL = Pattern.compile(
            "(?i)^run\\s+(?:(?:[^#]*?)(?:&&|;|\\|\\|)\\s*)?(?:python[0-9.]*\\s+-m\\s+pip|pip[0-9.]*)\\s+install\\b");

    @Override public String getDisplayName() { return "Upgrade workbook-selected pbr pins to 7.0.3"; }

    @Override
    public String getDescription() {
        return "Upgrade only exact pbr 5.11.1 and 5.5.1 pins in maintained Python dependency owner files, " +
               "without widening ranges or rewriting lock files, generated trees, or historical release snapshots.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!PbrSupport.isDependencyOwner(visited.getSourcePath())) return visited;
                String file = PbrSupport.fileName(visited.getSourcePath());
                String source = visited.getText();
                String updated;
                if (PbrSupport.isRequirementFile(visited.getSourcePath()) ||
                        file.equals("environment.yml") || file.equals("environment.yaml") ||
                        file.startsWith("conda") && (file.endsWith(".yml") || file.endsWith(".yaml"))) {
                    updated = mapLines(source, line -> replaceGroup(line, REQUIREMENT_PIN));
                } else if (file.equals("dockerfile") || file.startsWith("dockerfile.")) {
                    updated = upgradeDockerfile(source);
                } else if (file.equals("pipfile")) {
                    updated = upgradePipfile(source);
                } else if (file.equals("setup.cfg") || file.equals("tox.ini")) {
                    updated = upgradeIni(source, file);
                } else if (file.equals("pyproject.toml")) {
                    updated = upgradePyproject(source);
                } else if (file.equals("setup.py")) {
                    updated = upgradeSetupPy(source);
                } else {
                    updated = source;
                }
                return source.equals(updated) ? visited : visited.withText(updated);
            }
        };
    }

    private static String upgradeIni(String source, String file) {
        final String[] section = {""};
        final boolean[] dependencyValue = {false};
        return mapLines(source, line -> {
            String body = stripEnd(line).trim();
            Matcher header = SECTION.matcher(stripEnd(line));
            if (header.find()) {
                section[0] = header.group(1).trim().toLowerCase(Locale.ROOT);
                dependencyValue[0] = false;
                return line;
            }
            if (body.startsWith("#") || body.startsWith(";")) return line;
            Matcher key = INI_KEY.matcher(stripEnd(line));
            if (key.find()) {
                String name = key.group(1).toLowerCase(Locale.ROOT);
                dependencyValue[0] = dependencyKey(file, section[0], name);
                return dependencyValue[0] ? replaceGroup(line, ANY_PIN) : line;
            }
            return dependencyValue[0] && !body.isEmpty() ? replaceGroup(line, ANY_PIN) : line;
        });
    }

    private static boolean dependencyKey(String file, String section, String key) {
        if (file.equals("tox.ini")) {
            return section.startsWith("testenv") && key.equals("deps") ||
                   section.equals("tox") && key.equals("requires");
        }
        return section.equals("options") && Set.of("setup_requires", "install_requires", "tests_require")
                        .contains(key) ||
               section.equals("metadata") && Set.of("setup_requires_dist", "requires_dist").contains(key);
    }

    private static String upgradePipfile(String source) {
        final String[] section = {""};
        return mapLines(source, line -> {
            Matcher header = SECTION.matcher(stripEnd(line));
            if (header.find()) {
                section[0] = header.group(1).trim().toLowerCase(Locale.ROOT);
                return line;
            }
            return Set.of("packages", "dev-packages").contains(section[0]) &&
                   !line.stripLeading().startsWith("#") ? replaceGroup(line, POETRY_PIN) : line;
        });
    }

    private static String upgradePyproject(String source) {
        final String[] section = {""};
        final boolean[] dependencyArray = {false};
        return mapLines(source, line -> {
            String body = stripEnd(line);
            Matcher header = SECTION.matcher(body);
            if (header.find()) {
                section[0] = header.group(1).trim().toLowerCase(Locale.ROOT);
                dependencyArray[0] = false;
                return line;
            }
            if (body.stripLeading().startsWith("#")) return line;
            boolean poetry = section[0].equals("tool.poetry.dependencies") ||
                    section[0].equals("tool.poetry.dev-dependencies") ||
                    section[0].matches("tool[.]poetry[.]group[.][^.]+[.]dependencies");
            if (poetry) return replaceGroup(line, POETRY_PIN);

            boolean ownerKey = section[0].equals("build-system") && body.matches("\\s*requires\\s*=.*") ||
                    section[0].equals("project") && body.matches("\\s*dependencies\\s*=.*") ||
                    section[0].equals("project.optional-dependencies") && body.matches("\\s*[A-Za-z0-9_.-]+\\s*=.*");
            if (ownerKey) dependencyArray[0] = !body.substring(body.indexOf('=') + 1).contains("]");
            String result = ownerKey || dependencyArray[0] ? replaceGroup(line, QUOTED_PIN) : line;
            if (dependencyArray[0] && body.contains("]")) dependencyArray[0] = false;
            return result;
        });
    }

    private static String upgradeSetupPy(String source) {
        final boolean[] dependencyBlock = {false};
        final int[] depth = {0};
        return mapLines(source, line -> {
            String body = stripEnd(line);
            if (body.stripLeading().startsWith("#")) return line;
            Matcher key = SETUP_DEPENDENCY_KEY.matcher(body);
            boolean starts = key.find();
            if (starts) dependencyBlock[0] = true;
            String result = starts || dependencyBlock[0] ? replaceGroup(line, QUOTED_PIN) : line;
            if (starts || dependencyBlock[0]) {
                String tail = starts ? body.substring(key.end()).trim() : body.trim();
                if (starts) depth[0] = delimiters(tail);
                else depth[0] += delimiters(body);
                if (depth[0] <= 0 && !tail.isEmpty()) dependencyBlock[0] = false;
            }
            return result;
        });
    }

    private static int delimiters(String source) {
        int result = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '[' || source.charAt(i) == '(') result++;
            else if (source.charAt(i) == ']' || source.charAt(i) == ')') result--;
        }
        return result;
    }

    private static String upgradeDockerfile(String source) {
        final boolean[] pipInstall = {false};
        return mapLines(source, line -> {
            String body = stripEnd(line);
            String trimmed = body.stripLeading();
            if (trimmed.startsWith("#")) return line;
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("run ")) pipInstall[0] = DOCKER_PIP_INSTALL.matcher(lower).find();
            String result = pipInstall[0] ? replaceGroup(line, ANY_PIN) : line;
            if (!body.stripTrailing().endsWith("\\")) pipInstall[0] = false;
            return result;
        });
    }

    private static String replaceGroup(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder out = new StringBuilder(input);
        int searchFrom = 0;
        boolean changed = false;
        while (matcher.find(searchFrom)) {
            int start = matcher.start("version");
            int end = matcher.end("version");
            out.replace(start, end, PbrSupport.TARGET);
            searchFrom = start + PbrSupport.TARGET.length();
            matcher = pattern.matcher(out);
            changed = true;
        }
        return changed ? out.toString() : input;
    }

    private static String mapLines(String source, java.util.function.UnaryOperator<String> mapper) {
        StringBuilder out = new StringBuilder(source.length());
        int start = 0;
        while (start < source.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline < 0 ? source.length() : newline + 1;
            out.append(mapper.apply(source.substring(start, end)));
            start = end;
        }
        return out.toString();
    }

    private static String stripEnd(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) end--;
        return line.substring(0, end);
    }
}
