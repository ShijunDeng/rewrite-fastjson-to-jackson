package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces the removed NgModule API only when all references have a deterministic standalone equivalent. */
public final class MigrateColorPickerModuleToStandaloneDirective extends Recipe {
    private static final String MODULE = "ColorPickerModule";
    private static final String DIRECTIVE = "ColorPickerDirective";

    @Override
    public String getDisplayName() {
        return "Migrate ColorPickerModule to ColorPickerDirective";
    }

    @Override
    public String getDescription() {
        return "Replaces one import-resolved ngx-color-picker module named import and its direct Angular " +
               "imports/exports references when the file has no ambiguous uses or directive collision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Migration migration;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxColorPickerSupport.isProjectSource(cu.getSourcePath())) return cu;
                Migration previous = migration;
                migration = findMigration(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                migration = previous;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (migration == null || !migration.importId.equals(visited.getId())) return visited;
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                List<JS.ImportSpecifier> elements = ListUtils.map(named.getElements(), element -> {
                    if (!MODULE.equals(NgxColorPickerSupport.importedName(element))) return element;
                    Expression specifier = element.getSpecifier();
                    if (specifier instanceof J.Identifier identifier) {
                        return element.withSpecifier(identifier.withSimpleName(DIRECTIVE));
                    }
                    if (specifier instanceof JS.Alias alias) {
                        return element.withSpecifier(alias.withPropertyName(
                                alias.getPropertyName().withSimpleName(DIRECTIVE)));
                    }
                    return element;
                });
                return visited.withImportClause(clause.withNamedBindings(named.withElements(elements)));
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (migration == null || !migration.renameLocal ||
                    !(visited.getInitializer() instanceof J.NewArray array) ||
                    !isAngularScopeProperty(visited, getCursor())) return visited;
                List<Expression> initializer = array.getInitializer();
                if (initializer == null) return visited;
                return visited.withInitializer(array.withInitializer(ListUtils.map(initializer, expression ->
                        expression instanceof J.Identifier identifier &&
                        migration.localName.equals(identifier.getSimpleName())
                                ? identifier.withSimpleName(DIRECTIVE) : expression)));
            }

            private Migration findMigration(JS.CompilationUnit cu, ExecutionContext ctx) {
                List<ImportCandidate> modules = new ArrayList<>();
                boolean[] hasDirective = {false};
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (!UpgradeSelectedNgxColorPickerDependency.PACKAGE.equals(
                                NgxColorPickerSupport.moduleName(visited))) return visited;
                        JS.ImportClause clause = visited.getImportClause();
                        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named) ||
                            visited.printTrimmed(getCursor()).startsWith("import type ")) return visited;
                        for (JS.ImportSpecifier element : named.getElements()) {
                            String imported = NgxColorPickerSupport.importedName(element);
                            if (DIRECTIVE.equals(imported)) hasDirective[0] = true;
                            if (MODULE.equals(imported)) modules.add(new ImportCandidate(
                                    visited.getId(), NgxColorPickerSupport.localName(element)));
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                if (hasDirective[0] || modules.size() != 1 || modules.get(0).localName.isEmpty() ||
                    DIRECTIVE.equals(modules.get(0).localName)) return null;
                ImportCandidate candidate = modules.get(0);
                int metadataReferences = countDirectMetadataReferences(cu, candidate.localName, ctx);
                if (metadataReferences == 0) return null;
                String source = cu.printAll();
                if (wordOccurrences(source, DIRECTIVE) != 0) return null;
                if (wordOccurrences(source, candidate.localName) != 1 + metadataReferences) return null;
                if (!MODULE.equals(candidate.localName) && wordOccurrences(source, MODULE) != 1) return null;
                return new Migration(candidate.importId, candidate.localName, MODULE.equals(candidate.localName));
            }
        };
    }

    private static int countDirectMetadataReferences(JS.CompilationUnit cu, String localName, ExecutionContext ctx) {
        int[] references = {0};
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext scanCtx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, scanCtx);
                if (isAngularScopeProperty(visited, getCursor()) && visited.getInitializer() instanceof J.NewArray array &&
                    array.getInitializer() != null) {
                    references[0] += (int) array.getInitializer().stream().filter(expression ->
                            expression instanceof J.Identifier identifier &&
                            localName.equals(identifier.getSimpleName())).count();
                }
                return visited;
            }
        }.visit(cu, ctx);
        return references[0];
    }

    private static boolean isAngularScopeProperty(JS.PropertyAssignment property, Cursor cursor) {
        String name = NgxColorPickerSupport.propertyName(property);
        if (!"imports".equals(name) && !"exports".equals(name)) return false;
        Cursor ancestor = cursor.getParent();
        while (ancestor != null && !(ancestor.getValue() instanceof J.NewClass)) {
            if (ancestor.getValue() instanceof JS.PropertyAssignment || ancestor.getValue() instanceof J.Annotation ||
                ancestor.getValue() instanceof J.MethodInvocation ||
                ancestor.getValue() instanceof JS.CompilationUnit) return false;
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) return false;
        ancestor = ancestor.getParent();
        while (ancestor != null &&
               (ancestor.getValue() instanceof JRightPadded<?> || ancestor.getValue() instanceof JContainer<?>)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) return false;
        Object owner = ancestor.getValue();
        if (owner instanceof J.Annotation annotation) {
            return "NgModule".equals(annotation.getSimpleName()) || "Component".equals(annotation.getSimpleName());
        }
        return owner instanceof J.MethodInvocation invocation &&
               "configureTestingModule".equals(invocation.getSimpleName()) &&
               "TestBed".equals(NgxColorPickerSupport.expressionName(invocation.getSelect()));
    }

    private static int wordOccurrences(String source, String word) {
        Matcher matcher = Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])").matcher(source);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private record ImportCandidate(UUID importId, String localName) {}
    private record Migration(UUID importId, String localName, boolean renameLocal) {}
}
