package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;
import java.util.Locale;

import static com.huawei.clouds.openrewrite.gridstack.GridStackPlainTextSupport.risk;

/** Marks exact GridStack template, framework-rendering, nested-grid, and legacy CSS boundaries. */
public final class FindGridStackTemplateStyleRisks extends Recipe {
    private static final List<GridStackPlainTextSupport.Risk> TEMPLATE_RISKS = List.of(
            risk("\\bdata-gs-[a-z-]+", "Legacy GridStack data-gs attribute remains; map it to the exact gs-* target attribute and verify widget constraints and serialization"),
            risk("(?:\\[innerHTML\\]|\\bv-html\\b|\\bdangerouslySetInnerHTML\\b)", "GridStack 11 stopped injecting HTML content for XSS safety; use trusted framework rendering/renderCB and verify ownership, sanitization, updates, and teardown"),
            risk("<(?:gridstack|grid-stack|gridstack-item|grid-stack-item)\\b", "GridStack framework component/directive integration requires target wrapper API, mount/unmount, projection, nested grid, event, and SSR/hydration tests"),
            risk("\\b(?:sub-grid|subgrid|nested-grid)\\b", "Nested-grid template behavior requires parent/child drag, auto-column, save/load, v12.1 top-level event propagation, and teardown tests"),
            risk("\\bgrid-stack-(?:[2-9]|1[0-9]|[2-9][0-9]+)\\b", "Old generated column class is obsolete with GridStack 12 CSS variables; remove class assumptions and verify dynamic column/layout styling")
    );
    private static final List<GridStackPlainTextSupport.Risk> STYLE_RISKS = List.of(
            risk("gridstack-extra(?:\\.min)?\\.css", "gridstack-extra.css does not exist in v12; remove the include and verify columns through GridStack CSS variables"),
            risk("\\.grid-stack-(?:[2-9]|1[0-9]|[2-9][0-9]+)\\b", "Custom grid column classes are obsolete in v12; remove generated percentage rules and verify --gs-column-width/--gs-cell-height behavior"),
            risk("\\[gs-(?:x|w|y|h)(?:=|\\])", "Custom attribute-based GridStack geometry CSS can conflict with v12 variables and runtime positioning; verify specificity, RTL, animation, and dynamic columns"),
            risk("\\.grid-stack-item-content\\b", "Custom widget-content CSS depends on GridStack DOM/render ownership; verify v11 render callbacks, framework encapsulation, nested grids, lazy loading, and resize behavior")
    );

    @Override
    public String getDisplayName() {
        return "Find GridStack 12 template and style migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks concrete framework rendering, nested-grid, legacy attribute, custom column CSS, and widget " +
               "content styling boundaries while ignoring comments.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (path.endsWith(".html") || path.endsWith(".vue")) {
                    return GridStackPlainTextSupport.mark(visited, TEMPLATE_RISKS, true);
                }
                if (path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".sass") ||
                    path.endsWith(".less")) {
                    return GridStackPlainTextSupport.mark(visited, STYLE_RISKS, false);
                }
                return visited;
            }
        };
    }
}
