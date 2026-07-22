package com.huawei.clouds.openrewrite.reactdom;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/** Mark unresolved react-dom declarations, companion packages, and JSX configuration. */
public final class FindReactDomProjectMigrationRisks extends Recipe {
    private static final Pattern SIMPLE_SCALAR = Pattern.compile("[~^]?\\d+\\.\\d+\\.\\d+");
    private static final Map<String, String> COMPANIONS = Map.ofEntries(
            Map.entry("react", "Align React exactly with the React DOM 19.0.x line; mismatched renderers can cause invalid hooks and internal protocol failures"),
            Map.entry("@types/react", "Align @types/react with React 19 and resolve ref, JSX, reducer and element-props type changes"),
            Map.entry("@types/react-dom", "Align @types/react-dom with React DOM 19 and resolve client/server/static entry-point types"),
            Map.entry("react-test-renderer", "react-test-renderer is deprecated and concurrent; align temporarily and migrate tests to a maintained renderer"),
            Map.entry("@testing-library/react", "Upgrade React Testing Library for React DOM 19 roots/act and re-run async and hydration tests"),
            Map.entry("enzyme", "Enzyme has no official React 18/19 adapter; replace it or explicitly own an unofficial adapter strategy"),
            Map.entry("next", "Use a Next.js release that declares React 19 support and verify RSC, SSR, hydration and bundler conditions"),
            Map.entry("gatsby", "Verify Gatsby React 19 support and test SSR, hydration, plugins and webpack aliases"),
            Map.entry("react-router-dom", "Verify this React Router DOM major supports React 19 roots, transitions, data routers and hydration"),
            Map.entry("@vitejs/plugin-react", "Verify the Vite React plugin and JSX transform support the selected React DOM 19 toolchain"),
            Map.entry("react-scripts", "Create React App is maintenance-only; verify its Babel, Jest, webpack and React 19 compatibility or migrate the build"),
            Map.entry("scheduler", "Do not pin React's internal scheduler independently unless the framework explicitly requires it; verify dependency deduplication")
    );

    @Override
    public String getDisplayName() {
        return "Find React DOM 19 project compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark complex or dynamic react-dom declarations left unchanged, incompatible companion packages, " +
               "and classic JSX configuration that require an explicit project decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String filename = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(filename) && isDirectDependency()) {
                    String packageName = UpgradeSelectedReactDomDependency.key(visited);
                    if ("react-dom".equals(packageName) && shouldMarkUnresolved(visited)) {
                        return SearchResult.found(visited, unresolvedMessage(visited));
                    }
                    String message = COMPANIONS.get(packageName);
                    if (message != null && !isAlignedCompanion(packageName, visited)) {
                        return SearchResult.found(visited, message);
                    }
                }
                if (("tsconfig.json".equals(filename) || "jsconfig.json".equals(filename)) &&
                    "jsx".equals(UpgradeSelectedReactDomDependency.key(visited)) &&
                    visited.getValue() instanceof Json.Literal value &&
                    ("react".equals(value.getValue()) || "preserve".equals(value.getValue()))) {
                    return SearchResult.found(visited,
                            "React 19 requires the modern JSX transform; select react-jsx/react-jsxdev unless the framework owns compilation");
                }
                if ((filename.startsWith(".babelrc") || "babel.config.json".equals(filename)) &&
                    "runtime".equals(UpgradeSelectedReactDomDependency.key(visited)) &&
                    visited.getValue() instanceof Json.Literal value && "classic".equals(value.getValue())) {
                    return SearchResult.found(visited,
                            "React 19 requires the automatic JSX transform; verify Babel/framework ownership before changing this runtime");
                }
                return visited;
            }

            private boolean isDirectDependency() {
                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                return sectionCursor != null && sectionCursor.getValue() instanceof Json.Member section &&
                       UpgradeSelectedReactDomDependency.SECTIONS.contains(UpgradeSelectedReactDomDependency.key(section));
            }
        };
    }

    private static boolean shouldMarkUnresolved(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal value) || !(value.getValue() instanceof String declaration)) {
            return true;
        }
        return !declaration.matches("[~^]?19\\.0\\.0");
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal value) || !(value.getValue() instanceof String declaration)) {
            return "Non-string react-dom declaration was not changed; resolve it to a package-manager scalar before applying the 19.0.0 target";
        }
        if (declaration.contains(":") || declaration.contains("${") || declaration.matches("(?:latest|next|canary|catalog.*)")) {
            return "Protocol, alias, tag or dynamic react-dom declaration was not changed; resolve its owner and compatible React DOM 19 target";
        }
        if (SIMPLE_SCALAR.matcher(declaration).matches()) {
            return "Unlisted react-dom scalar version was not changed; confirm it is in migration scope before selecting the 19.0.0 target";
        }
        return "Complex react-dom range was not changed; replace it only after proving the intended supported React/renderer matrix";
    }

    private static boolean isAlignedCompanion(String packageName, Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal value) || !(value.getValue() instanceof String declaration)) {
            return false;
        }
        if ("react".equals(packageName)) {
            return declaration.matches("[~^]?19\\.0\\.0");
        }
        if ("@types/react".equals(packageName) || "@types/react-dom".equals(packageName)) {
            return declaration.matches("[~^]?19\\..+");
        }
        return false;
    }
}
