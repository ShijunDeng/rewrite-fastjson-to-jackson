package com.huawei.clouds.openrewrite.jaxen;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Finds source decisions for the Jaxen 1.2/2.0 compatibility boundary. */
public final class FindJaxenJavaMigrationRisks extends Recipe {
    private static final Set<String> REMOVED_TYPES = Set.of(
            "org.jaxen.util.LinkedIterator", "org.jaxen.util.StackedIterator",
            "org.jaxen.pattern.AnyChildNodeTest", "org.jaxen.pattern.NoNodeTest");
    private static final Set<String> HIDDEN_TYPES = Set.of(
            "org.jaxen.expr.DefaultExpr", "org.jaxen.expr.DefaultXPathExpr",
            "org.jaxen.expr.DefaultAbsoluteLocationPath", "org.jaxen.expr.DefaultAllNodeStep",
            "org.jaxen.expr.DefaultCommentNodeStep", "org.jaxen.expr.DefaultFilterExpr",
            "org.jaxen.expr.DefaultFunctionCallExpr", "org.jaxen.expr.DefaultNameStep",
            "org.jaxen.expr.DefaultProcessingInstructionNodeStep", "org.jaxen.expr.DefaultRelativeLocationPath",
            "org.jaxen.expr.DefaultStep", "org.jaxen.expr.DefaultTextNodeStep",
            "org.jaxen.expr.DefaultUnionExpr");
    private static final Set<String> ENGINES = Set.of(
            "org.jaxen.dom.DOMXPath", "org.jaxen.dom4j.Dom4jXPath", "org.jaxen.jdom.JDOMXPath",
            "org.jaxen.xom.XOMXPath", "org.jaxen.javabean.JavaBeanXPath");
    private static final Set<String> EXTERNAL_MODEL_ENGINES = Set.of(
            "org.jaxen.dom4j.Dom4jXPath", "org.jaxen.jdom.JDOMXPath", "org.jaxen.xom.XOMXPath");
    private static final Set<String> CONTEXT_INTERFACES = Set.of(
            "org.jaxen.NamespaceContext", "org.jaxen.FunctionContext", "org.jaxen.VariableContext");
    private static final Set<String> EXCEPTIONS = Set.of(
            "org.jaxen.JaxenException", "org.jaxen.FunctionCallException", "org.jaxen.XPathSyntaxException",
            "org.jaxen.saxpath.SAXPathException", "org.jaxen.saxpath.XPathSyntaxException");
    private static final String REMOVED =
            "This deprecated Jaxen implementation type was removed or made package-private in 2.0; replace it with a public XPath/Navigator API or an application-owned implementation";
    private static final String ENGINE =
            "Jaxen 2.0.1 changes XPath 1.0 edge behavior for number parsing/negative zero, chained predicates, comment predicates, namespaces and DOM fragment/entity traversal; run result, ordering and invalid-input regressions for this engine";
    private static final String OPTIONAL_MODEL =
            "JDOM, dom4j and XOM model libraries became optional in Jaxen 2; declare the chosen model explicitly and verify navigator/library version and classloader compatibility";
    private static final String CONTEXT =
            "Custom Jaxen namespace/function/variable context detected; verify null/unresolved handling, QName namespace rules, function arity/types, variable coercion, thread safety and the implicitly bound xml prefix";
    private static final String DOCUMENT =
            "Custom Navigator or document() loading detected; verify URI resolution, base URI, parser/document factory, encoding, XXE/DTD/entity policy, caching, classloader and FunctionCallException causes";
    private static final String EXCEPTION =
            "Jaxen exception handling detected; 2.0 uses standard exception chaining and removed getNestedException, so verify catch order, cause inspection, messages and serialization/logging contracts";
    private static final String SERIALIZATION =
            "A Jaxen XPath/context object is serialized; core serialVersionUIDs are retained but hidden classes, custom contexts and object-model graphs can still break stored/cache/message compatibility";

    @Override
    public String getDisplayName() {
        return "Find Jaxen 2.0.1 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed/hidden implementation APIs, XPath engines and behavior, custom contexts/navigators, " +
               "document loading, exception handling, optional models, reflection and serialization.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JaxenSupport.generated(source.getSourcePath())) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Import visitImport(J.Import anImport, ExecutionContext context) {
                        J.Import i = super.visitImport(anImport, context);
                        String name = i.getQualid().printTrimmed(getCursor());
                        return REMOVED_TYPES.contains(name) || HIDDEN_TYPES.contains(name)
                                ? JaxenSupport.mark(i, REMOVED) : i;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                                     ExecutionContext context) {
                        J.ClassDeclaration c = super.visitClassDeclaration(classDeclaration, context);
                        JavaType.FullyQualified type = c.getType();
                        if (type == null) return c;
                        if (CONTEXT_INTERFACES.stream().anyMatch(target -> assignable(target, type))) {
                            return JaxenSupport.mark(c, CONTEXT);
                        }
                        return assignable("org.jaxen.Navigator", type) ? JaxenSupport.mark(c, DOCUMENT) : c;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext context) {
                        J.NewClass n = super.visitNewClass(newClass, context);
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                        if (type == null) return n;
                        String fqn = type.getFullyQualifiedName();
                        if (ENGINES.contains(fqn)) {
                            return JaxenSupport.mark(n, EXTERNAL_MODEL_ENGINES.contains(fqn)
                                    ? ENGINE + "; " + OPTIONAL_MODEL : ENGINE);
                        }
                        if (n.getBody() != null && CONTEXT_INTERFACES.stream().anyMatch(target ->
                                target.equals(fqn) || assignable(target, type))) {
                            return JaxenSupport.mark(n, CONTEXT);
                        }
                        return n;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, context);
                        JavaType.Method type = m.getMethodType();
                        if (type == null) return m;
                        String owner = owner(type);
                        if (Set.of("setNamespaceContext", "setFunctionContext", "setVariableContext")
                                .contains(type.getName()) && owner.startsWith("org.jaxen.")) {
                            return JaxenSupport.mark(m, CONTEXT);
                        }
                        if ("getDocument".equals(type.getName()) && owner.startsWith("org.jaxen.")) {
                            return JaxenSupport.mark(m, DOCUMENT);
                        }
                        if ("writeObject".equals(type.getName()) &&
                            "java.io.ObjectOutputStream".equals(owner) && !m.getArguments().isEmpty() &&
                            jaxenSerializable(m.getArguments().get(0))) {
                            return JaxenSupport.mark(m, SERIALIZATION);
                        }
                        return m;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                             ExecutionContext context) {
                        J.VariableDeclarations variables = super.visitVariableDeclarations(multiVariable, context);
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(variables.getType());
                        return type != null && EXCEPTIONS.contains(type.getFullyQualifiedName())
                                ? JaxenSupport.mark(variables, EXCEPTION) : variables;
                    }

                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext context) {
                        J.Literal l = super.visitLiteral(literal, context);
                        if (!(l.getValue() instanceof String value)) return l;
                        if (REMOVED_TYPES.contains(value) || HIDDEN_TYPES.contains(value)) {
                            return JaxenSupport.mark(l, REMOVED);
                        }
                        return ENGINES.contains(value) ? JaxenSupport.mark(l, ENGINE) : l;
                    }
                }.visitNonNull(source, ctx);
            }
        };
    }

    private static boolean jaxenSerializable(Expression expression) {
        JavaType type = expression.getType();
        return TypeUtils.isAssignableTo("org.jaxen.XPath", type) ||
               TypeUtils.isAssignableTo("org.jaxen.Context", type) ||
               TypeUtils.isAssignableTo("org.jaxen.ContextSupport", type) ||
               TypeUtils.isAssignableTo("org.jaxen.NamespaceContext", type) ||
               TypeUtils.isAssignableTo("org.jaxen.FunctionContext", type) ||
               TypeUtils.isAssignableTo("org.jaxen.VariableContext", type);
    }

    private static boolean assignable(String target, JavaType type) {
        try {
            JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(type);
            return fqn != null && !target.equals(fqn.getFullyQualifiedName()) && TypeUtils.isAssignableTo(target, type);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String owner(JavaType.Method method) {
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(method.getDeclaringType());
        return owner == null ? "" : owner.getFullyQualifiedName();
    }
}
