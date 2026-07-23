package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;

import static com.huawei.clouds.openrewrite.mermaid.MermaidPlainTextSupport.risk;

/** Marks exact Mermaid markup, diagram, CDN, security, and CSS compatibility boundaries. */
public final class FindMermaid11MarkupRisks extends Recipe {
    private static final List<MermaidPlainTextSupport.Risk> RISKS = List.of(
            risk("<script[^>]+src\\s*=\\s*(['\"])[^'\"]*mermaid(?:@(?:9\\.1\\.[136]|9\\.4\\.3))?[^'\"]*\\.js[^>]*>",
                    "A classic or v9 Mermaid CDN asset cannot be version-swapped safely: Mermaid 10+ is ESM-only; use type=module/import and update SRI, CSP, cache, fallback, preload, and initialization ordering"),
            risk("(?:https?:)?//[^\"'\\s>]+/mermaid@(?:9\\.1\\.[136]|9\\.4\\.3)/[^\"'\\s>]+",
                    "This pinned Mermaid v9 CDN path needs an 11.15.0 ESM entry plus matching SRI/CSP/cache policy; verify core/full diagram loading and offline fallback"),
            risk("%%\\{\\s*(?:init|initialize)\\s*:",
                    "Mermaid directives are deprecated in favor of YAML frontmatter; migrate configuration only after reviewing site/frontmatter precedence, security policy, sanitization, theme, and shared Markdown portability"),
            risk("(?m)^\\s*click\\s+[A-Za-z0-9_.:-]+\\s+(?:call\\s+)?[A-Za-z_$]|(?m)^\\s*click\\s+[A-Za-z0-9_.:-]+\\s+(?:href\\s+)?['\"]?https?://",
                    "Mermaid click callbacks/links depend on securityLevel and DOM event binding; threat-model untrusted diagrams and verify sandbox/strict behavior, target/tooltip, CSP, accessibility, and bindFunctions"),
            risk("(?m)^\\s*namespace\\s+[A-Za-z0-9_.:-]+",
                    "Mermaid 11.15 changed nested class namespace semantics; choose class.hierarchicalNamespaces deliberately and compare qualified names, relations, labels, links, and snapshots"),
            risk("#(?:[A-Za-z0-9_-]+-)?(?:arrowhead|crosshead|circlehead)|\\[id[$^*|~]?=\\s*['\"][^'\"]*(?:arrowhead|crosshead|circlehead)",
                    "Mermaid 11 prefixes internal SVG IDs to avoid collisions; exact CSS/DOM selectors for markers must use a resilient suffix/structure and be verified across multiple diagrams"),
            risk("(?:securityLevel|htmlLabels|themeCSS|themeVariables|hierarchicalNamespaces|deterministicIDSeed)\\s*[:=]",
                    "This embedded Mermaid configuration controls security, sanitization, HTML labels, theme/CSS, namespaces, or SVG identifiers; review ownership and compare real visual/security snapshots on 11.15.0"),
            risk("(?:vendor|node_modules)/mermaid/(?:dist|src)/[^\"'\\s>)]+",
                    "This copied/install-layout Mermaid asset binds to generated v9 files; rebuild from the public 11.15.0 ESM entry and verify deployment, licenses, source maps, chunks, CSP, and cache busting"));

    @Override
    public String getDisplayName() {
        return "Find Mermaid 11 markup and diagram migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks classic/v9 CDN assets, deprecated directives, click security, class namespaces, generated SVG " +
               "ID selectors, embedded configuration, and copied distribution files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                return MermaidSupport.isMarkupOrDiagram(visited.getSourcePath())
                        ? MermaidPlainTextSupport.mark(visited, RISKS) : visited;
            }
        };
    }
}
