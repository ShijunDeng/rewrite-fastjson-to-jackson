package com.huawei.clouds.openrewrite.pbr;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark pbr 7 dependency ownership, removed commands, legacy configuration, and internal APIs. */
public final class FindPbrMigrationRisks extends Recipe {
    private static final String OWNER =
            "This pbr dependency is dynamic, unpinned, indirect, or not the exact workbook target 7.0.3; update the real requirements/constraints/build owner deliberately and regenerate—not edit—lock files";
    private static final String CONFIG =
            "This pbr-specific setup.cfg option is removed or deprecated in pbr 7; migrate it to the documented setuptools setup.cfg/pyproject.toml owner and validate sdist, wheel, editable install, entry points and package data";
    private static final String COMMAND =
            "pbr 7 removed setup.py test/build_sphinx integration; invoke pytest/stestr or sphinx-build directly and preserve the owning command's arguments, environment, working directory and failure semantics";
    private static final String COMPILER =
            "pbr 7 removed [global] compilers support; move compiler selection to the actual setuptools/build environment and verify native extension build wheels on every target platform";
    private static final String INTERNAL =
            "This code imports a pbr implementation module removed or reorganized by 7.0.3; replace private API use with supported packaging metadata/build hooks or verify an explicit target API adapter";
    private static final String DISTUTILS =
            "pbr is a setuptools plugin, but this setup.py uses distutils; migrate the setup owner to setuptools before validating pbr 7 builds";

    private static final Pattern VERSION_SPEC = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_.-])pbr(?![A-Za-z0-9_.-])\\s*(?:\\[[^]]+])?\\s*(?<operator>===?|~=|>=|<=|!=|>|<|@|\\^|~)\\s*(?<version>[^\\s,;#\\\"'\\]})]+)");
    private static final Pattern BARE_REQUIREMENT = Pattern.compile(
            "(?im)^(?!\\s*[#;])\\s*(?:-\\s*)?pbr\\s*(?=(?:[;#\\\\]|$))");
    private static final Pattern BARE_QUOTED = Pattern.compile("(?i)[\\\"']pbr[\\\"']");
    private static final Pattern POETRY_OWNER = Pattern.compile(
            "(?im)^(?!\\s*[#;])\\s*pbr\\s*=\\s*[\\\"'](?<version>[^\\\"']+)[\\\"']");
    private static final Pattern REMOVED_COMMAND = Pattern.compile(
            "(?i)(?:python(?:[0-9.]*)?(?:\\s+-m)?\\s+)?setup\\.py\\s+(?:test|build_sphinx)\\b");
    private static final Pattern INTERNAL_IMPORT = Pattern.compile(
            "(?m)^(?!\\s*#)\\s*(?:from\\s+|import\\s+)(?:pbr\\s+import\\s+(?:core|util)|pbr\\.(?:core|util|builddoc|testr_command|hooks\\.commands))\\b[^\\r\\n]*");
    private static final Pattern DISTUTILS_SETUP = Pattern.compile(
            "(?m)^(?!\\s*#)\\s*(?:from\\s+distutils\\.core\\s+import\\s+setup|import\\s+distutils\\.core)\\b[^\\r\\n]*");
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^]]+)]");
    private static final Pattern INI_KEY = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=(?!=)");
    private static final Pattern SETUP_DEPENDENCY_KEY = Pattern.compile(
            "(?i)\\b(?:setup_requires|install_requires|tests_require|requires)\\s*=");
    private static final Pattern DOCKER_PIP_INSTALL = Pattern.compile(
            "(?i)^run\\s+(?:(?:[^#]*?)(?:&&|;|\\|\\|)\\s*)?(?:python[0-9.]*\\s+-m\\s+pip|pip[0-9.]*)\\s+install\\b");

    @Override public String getDisplayName() { return "Find pbr 7.0.3 migration risks"; }

    @Override
    public String getDescription() {
        return "Mark non-exact dependency owners, removed pbr commands and compiler support, deprecated setup.cfg " +
               "ownership, private Python imports, and distutils setup boundaries while ignoring generated content.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!PbrSupport.isProjectPath(visited.getSourcePath()) || PbrSupport.isHistoricalPath(visited.getSourcePath())) return visited;
                String file = PbrSupport.fileName(visited.getSourcePath());
                List<Match> matches = new ArrayList<>();
                if (PbrSupport.isDependencyOwner(visited.getSourcePath())) inspectOwners(visited.getText(), file, matches);
                if (file.equals("setup.cfg")) inspectSetupCfg(visited.getText(), matches);
                if (file.endsWith(".py")) {
                    addAll(visited.getText(), INTERNAL_IMPORT, INTERNAL, matches);
                    if (file.equals("setup.py")) addAll(visited.getText(), DISTUTILS_SETUP, DISTUTILS, matches);
                }
                if (PbrSupport.isOperationalFile(visited.getSourcePath())) {
                    addCommands(visited.getText(), matches);
                }
                return markMatches(visited, matches);
            }
        };
    }

    private static void inspectOwners(String source, String file, List<Match> matches) {
        if (file.equals("dockerfile") || file.startsWith("dockerfile.")) {
            inspectDockerOwners(source, matches);
        } else if (file.equals("pipfile")) {
            inspectPipfileOwners(source, matches);
        } else if (file.equals("pyproject.toml")) {
            inspectPyprojectOwners(source, matches);
        } else if (file.equals("setup.cfg") || file.equals("tox.ini")) {
            inspectIniOwners(source, file, matches);
        } else if (file.equals("setup.py")) {
            inspectSetupPyOwners(source, matches);
        } else {
            int offset = 0;
            for (String line : lines(source)) {
                inspectDependencyLine(line, offset, false, matches);
                offset += line.length();
            }
        }
    }

    private static void inspectDependencyLine(String source, int offset, boolean quotedBare,
                                              List<Match> matches) {
        if (source.stripLeading().startsWith("#") || source.stripLeading().startsWith(";")) return;
        Matcher versions = VERSION_SPEC.matcher(source);
        while (versions.find()) {
            String operator = versions.group("operator");
            String version = versions.group("version");
            if (!(operator.equals("==") || operator.equals("===")) || !PbrSupport.TARGET.equals(version)) {
                matches.add(new Match(offset + versions.start(), offset + versions.end(), OWNER));
            }
        }
        Matcher bare = BARE_REQUIREMENT.matcher(source);
        while (bare.find()) matches.add(new Match(offset + bare.start(), offset + bare.end(), OWNER));
        if (quotedBare) {
            Matcher quoted = BARE_QUOTED.matcher(source);
            while (quoted.find()) matches.add(new Match(offset + quoted.start(), offset + quoted.end(), OWNER));
        }
        Matcher poetry = POETRY_OWNER.matcher(source);
        while (poetry.find()) {
            String value = poetry.group("version");
            if (!(value.equals(PbrSupport.TARGET) || value.equals("==" + PbrSupport.TARGET) ||
                    value.equals("===" + PbrSupport.TARGET))) {
                matches.add(new Match(offset + poetry.start(), offset + poetry.end(), OWNER));
            }
        }
    }

    private static void inspectDockerOwners(String source, List<Match> matches) {
        boolean pipInstall = false;
        int offset = 0;
        for (String line : lines(source)) {
            String body = stripEnd(line);
            String trimmed = body.stripLeading();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("run ")) {
                pipInstall = DOCKER_PIP_INSTALL.matcher(trimmed).find();
            }
            if (pipInstall) inspectDependencyLine(line, offset, true, matches);
            if (!body.stripTrailing().endsWith("\\")) pipInstall = false;
            offset += line.length();
        }
    }

    private static void inspectPipfileOwners(String source, List<Match> matches) {
        String section = "";
        int offset = 0;
        for (String line : lines(source)) {
            Matcher header = SECTION.matcher(stripEnd(line));
            if (header.find()) section = header.group(1).trim().toLowerCase(Locale.ROOT);
            else if (Set.of("packages", "dev-packages").contains(section)) {
                inspectDependencyLine(line, offset, true, matches);
            }
            offset += line.length();
        }
    }

    private static void inspectPyprojectOwners(String source, List<Match> matches) {
        String section = "";
        boolean dependencyArray = false;
        int offset = 0;
        for (String line : lines(source)) {
            String body = stripEnd(line);
            Matcher header = SECTION.matcher(body);
            if (header.find()) {
                section = header.group(1).trim().toLowerCase(Locale.ROOT);
                dependencyArray = false;
            } else {
                boolean poetry = section.equals("tool.poetry.dependencies") ||
                        section.equals("tool.poetry.dev-dependencies") ||
                        section.matches("tool[.]poetry[.]group[.][^.]+[.]dependencies");
                boolean ownerKey = section.equals("build-system") && body.matches("\\s*requires\\s*=.*") ||
                        section.equals("project") && body.matches("\\s*dependencies\\s*=.*") ||
                        section.equals("project.optional-dependencies") && body.matches("\\s*[A-Za-z0-9_.-]+\\s*=.*");
                if (ownerKey) dependencyArray = !body.substring(body.indexOf('=') + 1).contains("]");
                if (poetry || ownerKey || dependencyArray) inspectDependencyLine(line, offset, true, matches);
                if (dependencyArray && body.contains("]")) dependencyArray = false;
            }
            offset += line.length();
        }
    }

    private static void inspectIniOwners(String source, String file, List<Match> matches) {
        String section = "";
        boolean dependencyValue = false;
        int offset = 0;
        for (String line : lines(source)) {
            String body = stripEnd(line);
            Matcher header = SECTION.matcher(body);
            if (header.find()) {
                section = header.group(1).trim().toLowerCase(Locale.ROOT);
                dependencyValue = false;
            } else {
                Matcher key = INI_KEY.matcher(body);
                if (key.find()) {
                    dependencyValue = dependencyKey(file, section,
                            key.group(1).toLowerCase(Locale.ROOT));
                }
                if (dependencyValue) inspectDependencyLine(line, offset, true, matches);
            }
            offset += line.length();
        }
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

    private static void inspectSetupPyOwners(String source, List<Match> matches) {
        boolean dependencyBlock = false;
        int depth = 0;
        int offset = 0;
        for (String line : lines(source)) {
            String body = stripEnd(line);
            if (!body.stripLeading().startsWith("#")) {
                Matcher key = SETUP_DEPENDENCY_KEY.matcher(body);
                boolean starts = key.find();
                if (starts) dependencyBlock = true;
                if (starts || dependencyBlock) inspectDependencyLine(line, offset, true, matches);
                if (starts || dependencyBlock) {
                    String tail = starts ? body.substring(key.end()).trim() : body.trim();
                    if (starts) depth = delimiters(tail);
                    else depth += delimiters(body);
                    if (depth <= 0 && !tail.isEmpty()) dependencyBlock = false;
                }
            }
            offset += line.length();
        }
    }

    private static int delimiters(String source) {
        int result = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '[' || source.charAt(i) == '(') result++;
            else if (source.charAt(i) == ']' || source.charAt(i) == ')') result--;
        }
        return result;
    }

    private static void addCommands(String source, List<Match> matches) {
        Matcher matcher = REMOVED_COMMAND.matcher(source);
        while (matcher.find()) {
            int lineStart = source.lastIndexOf('\n', matcher.start()) + 1;
            int comment = source.indexOf('#', lineStart);
            if (comment < 0 || comment > matcher.start()) {
                matches.add(new Match(matcher.start(), matcher.end(), COMMAND));
            }
        }
    }

    private static void inspectSetupCfg(String source, List<Match> matches) {
        String section = "";
        int offset = 0;
        for (String line : lines(source)) {
            String body = stripEnd(line);
            Matcher header = Pattern.compile("^\\s*\\[([^]]+)]").matcher(body);
            if (header.find()) section = header.group(1).trim().toLowerCase(Locale.ROOT);
            Matcher key = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+)(\\s*=)").matcher(body);
            if (key.find() && !body.stripLeading().startsWith("#") && !body.stripLeading().startsWith(";")) {
                String name = key.group(2).toLowerCase(Locale.ROOT).replace('-', '_');
                boolean dashedAlias = key.group(2).contains("-") && (
                        section.equals("metadata") && Set.of("author_email", "home_page", "description_file",
                                "description_content_type", "python_requires", "requires_dist").contains(name) ||
                        section.equals("files") && Set.of("package_data", "data_files", "extra_files").contains(name) ||
                        section.equals("global") && name.equals("setup_hooks"));
                boolean globalCompiler = section.equals("global") && name.equals("compilers");
                boolean legacySection = section.equals("files") || section.equals("backwards_compat") ||
                        section.equals("entry_points") || section.equals("build_sphinx");
                boolean legacyMetadata = section.equals("metadata") && (name.equals("home_page") ||
                        name.equals("summary") || name.equals("classifier") || name.equals("platform") ||
                        name.equals("requires_dist") || name.equals("setup_requires_dist") ||
                        name.equals("python_requires") || name.equals("requires_python") ||
                        name.equals("provides_dist") || name.equals("provides_extra") ||
                        name.equals("obsoletes_dist") || name.equals("description_file"));
                boolean legacyTest = name.equals("tests_require") || section.equals("aliases") && name.equals("test");
                if (globalCompiler) matches.add(new Match(offset + key.start(2), offset + key.end(2), COMPILER));
                else if (dashedAlias || legacySection || legacyMetadata || legacyTest) {
                    matches.add(new Match(offset + key.start(2), offset + key.end(2), CONFIG));
                }
            } else if (header.find(0) && (section.equals("files") || section.equals("backwards_compat") ||
                    section.equals("entry_points") || section.equals("build_sphinx"))) {
                matches.add(new Match(offset + header.start(), offset + header.end(), CONFIG));
            }
            offset += line.length();
        }
    }

    private static void addAll(String source, Pattern pattern, String message, List<Match> matches) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) matches.add(new Match(matcher.start(), matcher.end(), message));
    }

    private static PlainText markMatches(PlainText text, List<Match> matches) {
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> selected = new ArrayList<>();
        int end = -1;
        for (Match match : matches) if (match.start >= end) { selected.add(match); end = match.end; }
        if (selected.isEmpty()) return text;
        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : selected) {
            if (cursor < match.start) snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(cursor, match.start)));
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static List<String> lines(String source) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline < 0 ? source.length() : newline + 1;
            lines.add(source.substring(start, end));
            start = end;
        }
        return lines;
    }

    private static String stripEnd(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) end--;
        return line.substring(0, end);
    }

    private record Match(int start, int end, String message) { }
}
