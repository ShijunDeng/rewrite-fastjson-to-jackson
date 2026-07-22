package com.huawei.clouds.openrewrite.jaxbimpl;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs only behavior-preserving source changes and excludes generated/installed parent directories. */
public final class MigrateJaxbImplDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic JAXB implementation Java namespaces and RI extensions";
    }

    @Override
    public String getDescription() {
        return "Move type-safe JAXB/Activation Java packages, NamespacePrefixMapper, and exact stable RI property " +
               "keys to their JAXB 4 names while preserving compilation units that still use removed Validator.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new SafeXmlBindPackageMigration()),
                projectSourcesOnly(new ChangePackage("javax.activation", "jakarta.activation", true)),
                projectSourcesOnly(new ChangeType(
                        "com.sun.xml.bind.marshaller.NamespacePrefixMapper",
                        "org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper", true)),
                projectSourcesOnly(new MigrateRiPropertyKeys())
        );
    }

    private static Recipe projectSourcesOnly(Recipe delegate) {
        return new Recipe() {
            @Override
            public String getDisplayName() {
                return delegate.getDisplayName();
            }

            @Override
            public String getDescription() {
                return delegate.getDescription();
            }

            @Override
            public TreeVisitor<?, ExecutionContext> getVisitor() {
                return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree visit(Tree tree, ExecutionContext ctx) {
                        return tree instanceof SourceFile source && !JaxbImplSupport.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }

    private static final class SafeXmlBindPackageMigration extends Recipe {
        @Override
        public String getDisplayName() {
            return "Migrate JAXB Javax types except removed Validator usages";
        }

        @Override
        public String getDescription() {
            return "Change javax.xml.bind to jakarta.xml.bind only when the compilation unit does not still use " +
                   "the removed javax.xml.bind.Validator API.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return Preconditions.check(Preconditions.not(new UsesRemovedValidationApi()),
                    new ChangePackage("javax.xml.bind", "jakarta.xml.bind", true).getVisitor());
        }
    }

    private static final class UsesRemovedValidationApi extends JavaIsoVisitor<ExecutionContext> {
        private static final Set<String> REMOVED_METHODS = Set.of(
                "createValidator", "setValidating", "isValidating");

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier i = super.visitIdentifier(identifier, ctx);
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(i.getType());
            return type != null && "javax.xml.bind.Validator".equals(type.getFullyQualifiedName())
                    ? JaxbImplSupport.mark(i, "uses removed JAXB Validator API") : i;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            JavaType.Method type = m.getMethodType();
            JavaType.FullyQualified owner = type == null ? null : TypeUtils.asFullyQualified(type.getDeclaringType());
            return type != null && owner != null && REMOVED_METHODS.contains(type.getName()) &&
                   owner.getFullyQualifiedName().startsWith("javax.xml.bind.")
                    ? JaxbImplSupport.mark(m, "uses removed JAXB Validator API") : m;
        }
    }

    private static final class MigrateRiPropertyKeys extends Recipe {
        private static final Map<String, String> KEYS = Map.of(
                "com.sun.xml.bind.namespacePrefixMapper", "org.glassfish.jaxb.namespacePrefixMapper",
                "com.sun.xml.bind.indentString", "org.glassfish.jaxb.indentString",
                "com.sun.xml.bind.characterEscapeHandler", "org.glassfish.jaxb.characterEscapeHandler",
                "com.sun.xml.bind.xmlDeclaration", "org.glassfish.jaxb.xmlDeclaration",
                "com.sun.xml.bind.xmlHeaders", "org.glassfish.jaxb.xmlHeaders",
                "com.sun.xml.bind.objectIdentitityCycleDetection", "org.glassfish.jaxb.objectIdentitityCycleDetection");

        @Override
        public String getDisplayName() {
            return "Migrate stable JAXB RI marshaller property keys";
        }

        @Override
        public String getDescription() {
            return "Rename only exact RI property keys with a documented JAXB 4 equivalent.";
        }

        @Override
        public JavaIsoVisitor<ExecutionContext> getVisitor() {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                    J.Literal l = super.visitLiteral(literal, ctx);
                    if (!(l.getValue() instanceof String value) || !KEYS.containsKey(value)) return l;
                    return JaxbImplSupport.replaceLiteral(l, KEYS.get(value));
                }
            };
        }
    }
}
