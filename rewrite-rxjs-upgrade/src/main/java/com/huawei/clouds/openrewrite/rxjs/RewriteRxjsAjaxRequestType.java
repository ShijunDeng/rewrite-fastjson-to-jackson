package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

/** Renames the imported RxJS Ajax request type without touching comments or string literals. */
public final class RewriteRxjsAjaxRequestType extends Recipe {
    @Override
    public String getDisplayName() {
        return "Rewrite the RxJS AjaxRequest type";
    }

    @Override
    public String getDescription() {
        return "Rename an unaliased AjaxRequest import from rxjs/ajax and its code identifiers to AjaxConfig, " +
               "while preserving comments and quoted or template-string content.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String source = visited.getText();
                if (!RxjsSourceText.isSupported(visited) ||
                    !RxjsSourceText.hasUnaliasedNamedImport(source, "rxjs/ajax", "AjaxRequest") ||
                    RxjsSourceText.hasUnaliasedNamedImport(source, "rxjs/ajax", "AjaxConfig") ||
                    RxjsSourceText.hasAnyImportBinding(source, "AjaxConfig") ||
                    RxjsSourceText.hasPotentialShadowingBinding(source, "AjaxRequest") ||
                    RxjsSourceText.hasPotentialShadowingBinding(source, "AjaxConfig")) {
                    return visited;
                }
                return visited.withText(RxjsSourceText.replaceIdentifierInCode(
                        source, "AjaxRequest", "AjaxConfig"
                ));
            }
        };
    }
}
