package com.huawei.clouds.openrewrite.pbr;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class PbrSupport {
    static final String TARGET = "7.0.3";
    static final Set<String> SOURCE_VERSIONS = Set.of("5.11.1", "5.5.1");
    static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "vendor", "target", "build", "dist", "out", "generated", "generated-sources",
            ".git", ".idea", ".vscode", ".cache", ".tox", ".nox", ".venv", "venv",
            ".mypy_cache", ".pytest_cache", ".ruff_cache", ".hypothesis", ".eggs", "eggs",
            "site-packages", "wheelhouse", "__pycache__", "node_modules");
    private static final Pattern REQUIREMENT_FILE = Pattern.compile(
            "(?i)(?:[a-z0-9_.-]+[-_.])?(?:requirements?|constraints?)(?:[-_.][a-z0-9_.-]+)?\\.(?:txt|in)");

    private PbrSupport() { }

    static boolean isProjectPath(Path path) {
        for (Path segment : path.normalize()) {
            String value = segment.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) || value.startsWith("generated") ||
                    value.startsWith("install")) return false;
        }
        return true;
    }

    static boolean isHistoricalPath(Path path) {
        String previous = "";
        for (Path segment : path.normalize()) {
            String current = segment.toString().toLowerCase(Locale.ROOT);
            if (("docs".equals(previous) || "doc".equals(previous)) &&
                    ("releases".equals(current) || "snapshots".equals(current))) return true;
            previous = current;
        }
        return false;
    }

    static boolean isLockFile(Path path) {
        String file = fileName(path);
        return file.equals("poetry.lock") || file.equals("pipfile.lock") || file.equals("uv.lock") ||
               file.endsWith(".lock");
    }

    static boolean isRequirementFile(Path path) {
        return REQUIREMENT_FILE.matcher(fileName(path)).matches();
    }

    static boolean isDependencyOwner(Path path) {
        if (!isProjectPath(path) || isHistoricalPath(path) || isLockFile(path)) return false;
        String file = fileName(path);
        return isRequirementFile(path) || file.equals("pyproject.toml") || file.equals("setup.py") ||
               file.equals("setup.cfg") || file.equals("tox.ini") || file.equals("pipfile") ||
               file.equals("environment.yml") || file.equals("environment.yaml") ||
               file.startsWith("conda") && (file.endsWith(".yml") || file.endsWith(".yaml")) ||
               file.equals("dockerfile") || file.startsWith("dockerfile.");
    }

    static boolean isOperationalFile(Path path) {
        if (!isProjectPath(path) || isHistoricalPath(path)) return false;
        String file = fileName(path);
        return file.equals("setup.py") || file.equals("setup.cfg") || file.equals("tox.ini") ||
               file.equals("makefile") || file.startsWith("makefile.") || file.equals("dockerfile") ||
               file.startsWith("dockerfile.") || file.endsWith(".sh") || file.endsWith(".bash") ||
               file.endsWith(".yaml") || file.endsWith(".yml");
    }

    static String fileName(Path path) {
        return path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    }
}
