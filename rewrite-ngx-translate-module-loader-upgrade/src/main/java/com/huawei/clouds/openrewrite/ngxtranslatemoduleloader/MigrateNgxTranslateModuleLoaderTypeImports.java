package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Moves the removed Translation type to its exact ngx-translate/core replacement when binding ownership is provable. */
public final class MigrateNgxTranslateModuleLoaderTypeImports extends Recipe {
    private static final String OLD_TYPE = "Translation";
    private static final String NEW_TYPE = "TranslationObject";
    private static final String CORE = "@ngx-translate/core";
    private static final Pattern SHADOW_DECLARATION = Pattern.compile(
            "(?m)\\b(?:class|interface|enum|type|const|let|var|function)\\s+Translation\\b");

    @Override
    public String getDisplayName() {
        return "Migrate ngx-translate-module-loader Translation imports";
    }

    @Override
    public String getDescription() {
        return "Replace a uniquely owned Translation import with @ngx-translate/core TranslationObject, preserving " +
               "aliases and renaming unaliased type references only when no collision or shadow declaration exists.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Migration migration;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxTranslateModuleLoaderSupport.isProjectPath(cu.getSourcePath())) return cu;
                Migration previous = migration;
                migration = findMigration(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                migration = previous;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (migration == null || !migration.importId().equals(visited.getId()) ||
                    !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                List<JS.ImportSpecifier> elements = named.getElements().stream().map(element -> {
                    if (!(element.getSpecifier() instanceof JS.Alias alias) ||
                        !OLD_TYPE.equals(alias.getPropertyName().getSimpleName())) return element;
                    return element.withSpecifier(alias.withPropertyName(
                            alias.getPropertyName().withSimpleName(NEW_TYPE)));
                }).toList();
                return visited.withImportClause(clause.withNamedBindings(named.withElements(elements)))
                        .withModuleSpecifier(NgxTranslateModuleLoaderSupport.replaceString(literal, CORE));
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                return migration != null && migration.renameLocal() &&
                       OLD_TYPE.equals(visited.getSimpleName())
                        ? visited.withSimpleName(NEW_TYPE) : visited;
            }
        };
    }

    private static Migration findMigration(JS.CompilationUnit cu, ExecutionContext ctx) {
        List<Candidate> candidates = new ArrayList<>();
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                String module = NgxTranslateModuleLoaderSupport.moduleName(visited);
                if (!NgxTranslateModuleLoaderSupport.PACKAGE.equals(module) &&
                    !(NgxTranslateModuleLoaderSupport.PACKAGE + "/lib/translation").equals(module)) return visited;
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named) ||
                    named.getElements().size() != 1) return visited;
                JS.ImportSpecifier element = named.getElements().get(0);
                if (OLD_TYPE.equals(NgxTranslateModuleLoaderSupport.importedName(element))) {
                    candidates.add(new Candidate(visited.getId(),
                            NgxTranslateModuleLoaderSupport.localName(element),
                            element.getSpecifier() instanceof J.Identifier));
                }
                return visited;
            }
        }.visit(cu, ctx);
        if (candidates.size() != 1 || candidates.get(0).localName().isEmpty()) return null;
        Candidate candidate = candidates.get(0);
        if (!candidate.unaliased()) return new Migration(candidate.importId(), false);
        String source = cu.printAll();
        if (source.matches("(?s).*\\b" + NEW_TYPE + "\\b.*") || SHADOW_DECLARATION.matcher(source).find()) {
            return null;
        }
        boolean[] ambiguous = {false};
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                if (OLD_TYPE.equals(visited.getSimpleName())) ambiguous[0] = true;
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext scanCtx) {
                J.FieldAccess visited = super.visitFieldAccess(field, scanCtx);
                if (OLD_TYPE.equals(visited.getSimpleName())) ambiguous[0] = true;
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext scanCtx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, scanCtx);
                if (OLD_TYPE.equals(visited.getSimpleName())) ambiguous[0] = true;
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext scanCtx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, scanCtx);
                Expression name = visited.getName();
                if (name instanceof J.Identifier identifier && OLD_TYPE.equals(identifier.getSimpleName())) {
                    ambiguous[0] = true;
                }
                return visited;
            }
        }.visit(cu, ctx);
        return ambiguous[0] ? null : new Migration(candidate.importId(), true);
    }

    private record Candidate(UUID importId, String localName, boolean unaliased) {
    }

    private record Migration(UUID importId, boolean renameLocal) {
    }
}
