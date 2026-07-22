package com.huawei.clouds.openrewrite.swiper;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mark Swiper constructs whose correct migration depends on framework, layout or runtime behavior. */
public final class FindSwiperJavaScriptRisks extends Recipe {
    private static final Set<String> REMOVED_OPTIONS = Set.of(
            "loopedSlides", "slidesPerColumn", "slidesPerColumnFill", "freeModeMinimumVelocity",
            "freeModeMomentum", "freeModeMomentumBounce", "freeModeMomentumRatio",
            "freeModeMomentumVelocityRatio", "freeModeSticky", "lazy", "watchVisibleSlides"
    );
    private static final Set<String> BEHAVIOR_OPTIONS = Set.of(
            "loop", "autoplay", "freeMode", "grid", "modules", "touchEventsTarget", "watchOverflow",
            "resizeObserver", "observer", "observeParents", "cssMode", "virtual", "breakpoints"
    );
    private static final Set<String> ELEMENT_EVENTS = Set.of(
            "slidechange", "progress", "reachbeginning", "reachend", "transitionstart", "transitionend",
            "autoplaystart", "autoplaystop", "autoplaypause", "autoplayresume"
    );

    @Override
    public String getDisplayName() {
        return "Find Swiper 12 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact imports, module registration, removed options, loop/lazy/autoplay/grid behavior, " +
               "Element events, legacy CommonJS and container/lazy selectors in Swiper-owned source files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean swiperFile;
            private Set<String> constructors = Set.of();
            private Set<String> instances = Set.of();
            private Set<String> elements = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!SwiperSupport.isProjectPath(cu.getSourcePath())) return cu;
                boolean previousFile = swiperFile;
                Set<String> previousConstructors = constructors;
                Set<String> previousInstances = instances;
                Set<String> previousElements = elements;
                Map<String, Integer> previousDeclarations = declarationCounts;
                swiperFile = false;
                constructors = new HashSet<>();
                instances = new HashSet<>();
                elements = new HashSet<>();
                declarationCounts = new HashMap<>();
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                swiperFile = previousFile;
                constructors = previousConstructors;
                instances = previousInstances;
                elements = previousElements;
                declarationCounts = previousDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = SwiperSupport.moduleName(visited);
                if (!SwiperSupport.isSwiperReference(module)) return visited;
                if (mixedDefaultAndModules(visited)) {
                    return mark(visited, "Swiper 12 built-in modules come from swiper/modules; split the default core import from these named module imports");
                }
                if (SwiperSupport.JS_ENTRIES.containsKey(module)) {
                    if (hasNamedBindings(visited)) {
                        return mark(visited, "This legacy Swiper entry includes named exports that cannot be moved by a path-only rewrite; keep the default core/bundle import separate and move proven built-in modules to swiper/modules");
                    }
                    return mark(visited, "This exact legacy Swiper core/bundle entry is normalized automatically to a Swiper 12 public export");
                }
                if (SwiperSupport.cssTarget(module) != null) {
                    return mark(visited, "This exact legacy Swiper stylesheet entry is normalized automatically to a public Swiper 12 CSS export");
                }
                if (Set.of("swiper/angular", "swiper/svelte", "swiper/solid").contains(module)) {
                    return mark(visited, "Swiper 9 removed this framework wrapper; migrate templates, properties, slots, lifecycle, SSR and events to Swiper Element deliberately");
                }
                if (Set.of("swiper/react", "swiper/vue").contains(module)) {
                    return mark(visited, "Verify the target React/Vue wrapper API, module delivery, slots, refs, SSR/hydration and framework version/types");
                }
                if (module.startsWith("swiper/scss") || module.startsWith("swiper/less")) {
                    return mark(visited, "Swiper 12 no longer publishes SCSS or Less; choose a public CSS export and move preprocessor customization into application-owned styles");
                }
                if (module.startsWith("swiper/components/lazy") || module.contains("/lazy/")) {
                    return mark(visited, "Swiper 9 removed the Lazy module; use native loading=lazy and verify preload, spinner and virtual/loop behavior");
                }
                if (module.startsWith("swiper/") && !publicEntry(module)) {
                    return mark(visited, "Swiper 12 package exports do not expose this internal/deep entry; select an explicit public root, bundle, modules, css, element, framework or types export");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!isDirectSwiperOptionsProperty()) return visited;
                String name = propertyName(visited.getName());
                if (REMOVED_OPTIONS.contains(name)) {
                    return mark(visited, removedOptionMessage(name));
                }
                if (BEHAVIOR_OPTIONS.contains(name)) {
                    return mark(visited, "Swiper 12 changes " + name + " behavior across the selected major-version span; verify breakpoints, slide counts, visibility, lifecycle and interaction in this integration");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String select = compact(visited.getSelect());
                if ("use".equals(visited.getSimpleName()) && constructors.contains(select) &&
                    declarationCounts.getOrDefault(select, 0) == 0) {
                    return mark(visited, "Global Swiper.use registration conflicts with per-instance/framework modules and tree shaking; import from swiper/modules and pass modules at the owning boundary");
                }
                if (visited.getSelect() == null && "require".equals(visited.getSimpleName()) &&
                    firstLiteral(visited.getArguments()) != null &&
                    SwiperSupport.isSwiperReference(firstLiteral(visited.getArguments()))) {
                    return mark(visited, "Swiper 7+ is ESM-only; replace CommonJS require with an ESM import or an intentional async interop boundary supported by the runtime/bundler");
                }
                if (("on".equals(visited.getSimpleName()) && ownedInstance(select)) ||
                    "addEventListener".equals(visited.getSimpleName()) && elements.contains(select)) {
                    String event = firstLiteral(visited.getArguments());
                    if (event != null && ELEMENT_EVENTS.contains(event.toLowerCase())) {
                        return mark(visited, "Verify event naming and callback arguments: v6 prepended the Swiper instance and Swiper Element v11 uses an event prefix/detail contract");
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!swiperFile || !(visited.getValue() instanceof String value)) return visited;
                if (value.contains("swiper-lazy") || value.contains("swiper-lazy-preloader")) {
                    return mark(visited, "Legacy swiper-lazy classes belong to the removed Lazy module; migrate to native image lazy loading and an application-owned loading state");
                }
                if (value.matches(".*(?<![A-Za-z0-9_-])\\.swiper-container(?![A-Za-z0-9_-]).*")) {
                    return mark(visited, "Swiper 7 renamed the ordinary container class to .swiper; constructor literals normalize automatically, while other selector ownership needs review");
                }
                if (value.startsWith("swiper/") && !publicEntry(value) &&
                    !SwiperSupport.JS_ENTRIES.containsKey(value) && SwiperSupport.cssTarget(value) == null) {
                    return mark(visited, "This string names an unpublished Swiper path; verify dynamic import, worker, test mapper or copy configuration against package exports");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = SwiperSupport.moduleName(visited);
                        if (!SwiperSupport.isSwiperReference(module)) return visited;
                        swiperFile = true;
                        if ((SwiperSupport.PACKAGE.equals(module) || "swiper/bundle".equals(module) ||
                             SwiperSupport.JS_ENTRIES.containsKey(module)) &&
                            !visited.printTrimmed(getCursor()).startsWith("import type ")) {
                            String binding = SwiperSupport.defaultBinding(visited);
                            if (binding != null) constructors.add(binding);
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
                                                                    ExecutionContext scanCtx) {
                        J.MethodInvocation visited = super.visitMethodInvocation(invocation, scanCtx);
                        if (visited.getSelect() == null && "require".equals(visited.getSimpleName())) {
                            String module = firstLiteral(visited.getArguments());
                            if (module != null && SwiperSupport.isSwiperReference(module)) swiperFile = true;
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (declarationCounts.getOrDefault(visited.getSimpleName(), 0) != 1) return visited;
                        if (visited.getInitializer() instanceof J.NewClass construction &&
                            construction.getClazz() != null) {
                            String constructor = compact(construction.getClazz());
                            if (constructors.contains(constructor) &&
                                declarationCounts.getOrDefault(constructor, 0) == 0) {
                                instances.add(visited.getSimpleName());
                            }
                        }
                        if (visited.getInitializer() instanceof J.MethodInvocation call &&
                            "querySelector".equals(call.getSimpleName()) &&
                            "swiper-container".equals(firstLiteral(call.getArguments()))) {
                            elements.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean mixedDefaultAndModules(JS.Import declaration) {
                JS.ImportClause clause = declaration.getImportClause();
                if (clause == null || clause.getName() == null ||
                    !(clause.getNamedBindings() instanceof JS.NamedImports named)) return false;
                return named.getElements().stream().anyMatch(specifier ->
                        SwiperSupport.MODULES.contains(SwiperSupport.importedName(specifier)));
            }

            private boolean hasNamedBindings(JS.Import declaration) {
                return declaration.getImportClause() != null &&
                       declaration.getImportClause().getNamedBindings() instanceof JS.NamedImports named &&
                       !named.getElements().isEmpty();
            }

            private boolean isDirectSwiperOptionsProperty() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null && !(cursor.getValue() instanceof J.NewClass)) {
                    if (cursor.getValue() instanceof JS.PropertyAssignment ||
                        cursor.getValue() instanceof JS.CompilationUnit) return false;
                    cursor = cursor.getParent();
                }
                if (cursor == null || ((J.NewClass) cursor.getValue()).getClazz() != null) return false;
                J.NewClass options = (J.NewClass) cursor.getValue();
                cursor = cursor.getParent();
                while (cursor != null &&
                       (cursor.getValue() instanceof JRightPadded<?> || cursor.getValue() instanceof JContainer<?>)) {
                    cursor = cursor.getParent();
                }
                if (cursor == null || !(cursor.getValue() instanceof J.NewClass construction) ||
                    construction.getArguments().size() < 2 || construction.getArguments().get(1) != options ||
                    construction.getClazz() == null) return false;
                String constructor = compact(construction.getClazz());
                return constructors.contains(constructor) && declarationCounts.getOrDefault(constructor, 0) == 0;
            }

            private boolean ownedInstance(String select) {
                for (String instance : instances) if (select.equals(instance) || select.startsWith(instance + ".")) return true;
                return false;
            }
        };
    }

    private static boolean publicEntry(String module) {
        return module.equals("swiper/bundle") || module.equals("swiper/modules") || module.equals("swiper/types") ||
               module.equals("swiper/react") || module.equals("swiper/vue") || module.equals("swiper/element") ||
               module.equals("swiper/element/bundle") || module.equals("swiper/css") ||
               module.equals("swiper/css/bundle") || module.matches("swiper/css/[a-z0-9-]+");
    }

    private static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return expression.toString().replaceAll("[\"']", "").trim();
    }

    private static String removedOptionMessage(String option) {
        return switch (option) {
            case "watchVisibleSlides" -> "watchVisibleSlides was folded into watchSlidesProgress; rename only after confirming visible-slide and progress consumers";
            case "loopedSlides" -> "Swiper 11 removed loopedSlides; redesign loop slide duplication around actual slidesPerView/group/grid and dynamic slide behavior";
            case "slidesPerColumn", "slidesPerColumnFill" -> "Multi-row options moved to Grid; migrate to grid.rows/fill and test loop, breakpoints and incomplete rows";
            case "lazy" -> "Swiper 9 removed the Lazy module/options; use native loading=lazy and verify preload, spinner, loop and virtual slides";
            default -> "Legacy flat freeMode option moved into the freeMode object; map its semantics deliberately and test momentum, bounce and sticky behavior";
        };
    }

    private static String firstLiteral(List<Expression> arguments) {
        return !arguments.isEmpty() && arguments.get(0) instanceof J.Literal literal &&
               literal.getValue() instanceof String ? (String) literal.getValue() : null;
    }

    private static String compact(Object tree) {
        return tree == null ? "" : tree.toString().replaceAll("\\s+", "");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
