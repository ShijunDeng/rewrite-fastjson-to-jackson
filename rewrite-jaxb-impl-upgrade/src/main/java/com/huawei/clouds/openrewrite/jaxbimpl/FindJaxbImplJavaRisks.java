package com.huawei.clouds.openrewrite.jaxbimpl;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Marks type-attributed JAXB 4 source decisions without inventing semantic replacements. */
public final class FindJaxbImplJavaRisks extends Recipe {
    private static final Set<String> REMOVED_METHODS = Set.of("createValidator", "setValidating", "isValidating");
    private static final Set<String> PROVIDER_STRINGS = Set.of(
            "javax.xml.bind.context.factory", "javax.xml.bind.JAXBContext",
            "com.sun.xml.bind.v2.ContextFactory", "com.sun.xml.internal.bind.v2.ContextFactory");
    private static final Set<String> AUTO_PROPERTIES = Set.of(
            "com.sun.xml.bind.namespacePrefixMapper", "com.sun.xml.bind.indentString",
            "com.sun.xml.bind.characterEscapeHandler", "com.sun.xml.bind.xmlDeclaration",
            "com.sun.xml.bind.xmlHeaders", "com.sun.xml.bind.objectIdentitityCycleDetection");
    private static final String VALIDATOR =
            "JAXB Validator was removed; create javax.xml.validation.Schema with SchemaFactory and attach it to Marshaller/Unmarshaller instead";
    private static final String INTERNAL =
            "JAXB RI internal API has no compatibility guarantee; replace it with a standard Jakarta XML Binding API/SPI or port it against 4.0.6 deliberately";
    private static final String LEXICAL =
            "JAXB 4 lexical conversion is stricter; add positive and invalid-value tests for dates, QName, numbers, booleans, hex and Base64 instead of relying on 2.x tolerance";
    private static final String THREADING =
            "Marshaller and Unmarshaller are not thread-safe; create per operation or use a correctly bounded pool/ThreadLocal while sharing only JAXBContext";
    private static final String PROVIDER =
            "Old JAXB provider lookup/reflection name is incompatible with JAXB 4 ServiceLoader/JAXBContextFactory discovery";
    private static final String FACTORY =
            "Custom JAXBContextFactory/provider: recompile against 4.0.6 and verify ServiceLoader/module registration, supported properties, classloader isolation, OSGi and native-image metadata";

    @Override
    public String getDisplayName() {
        return "Find JAXB implementation 4.0.6 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed validation APIs, RI internals, provider strings/factories, strict lexical parsing, " +
               "shared marshallers/unmarshallers, and obsolete JPMS declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (JaxbImplSupport.generated(compilationUnit.getSourcePath())) return compilationUnit;
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                String source = cu.printAll();
                return source.contains("requires java.xml.bind") || source.contains("requires java.activation")
                        ? JaxbImplSupport.mark(cu,
                        "JDK JAXB/Activation modules were removed; use jakarta.xml.bind/jakarta.activation and verify module-path provider discovery")
                        : cu;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDeclaration, ctx);
                JavaType.FullyQualified type = c.getType();
                if (type != null && (assignable("javax.xml.bind.JAXBContextFactory", type) ||
                                     assignable("jakarta.xml.bind.JAXBContextFactory", type)) &&
                    !Set.of("javax.xml.bind.JAXBContextFactory", "jakarta.xml.bind.JAXBContextFactory")
                            .contains(type.getFullyQualifiedName())) return JaxbImplSupport.mark(c, FACTORY);
                return c;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String name = i.getQualid().printTrimmed(getCursor());
                if (name.equals("javax.xml.bind.Validator") || name.equals("jakarta.xml.bind.Validator")) {
                    return JaxbImplSupport.mark(i, VALIDATOR);
                }
                if ((name.startsWith("com.sun.xml.bind.") || name.startsWith("com.sun.xml.internal.bind.")) &&
                    !name.equals("com.sun.xml.bind.marshaller.NamespacePrefixMapper")) {
                    return JaxbImplSupport.mark(i, INTERNAL);
                }
                return i;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null) return m;
                String owner = owner(type);
                if (REMOVED_METHODS.contains(type.getName()) &&
                    (owner.startsWith("javax.xml.bind.") || owner.startsWith("jakarta.xml.bind."))) {
                    return JaxbImplSupport.mark(m, VALIDATOR);
                }
                if (type.getName().startsWith("parse") &&
                    Set.of("javax.xml.bind.DatatypeConverter", "jakarta.xml.bind.DatatypeConverter").contains(owner)) {
                    return JaxbImplSupport.mark(m, LEXICAL);
                }
                return m;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                     ExecutionContext ctx) {
                J.VariableDeclarations variables = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(variables.getType());
                boolean shared = variables.hasModifier(J.Modifier.Type.Static) ||
                                 variables.hasModifier(J.Modifier.Type.Volatile);
                return shared && type != null && Set.of(
                        "javax.xml.bind.Marshaller", "javax.xml.bind.Unmarshaller",
                        "jakarta.xml.bind.Marshaller", "jakarta.xml.bind.Unmarshaller")
                        .contains(type.getFullyQualifiedName()) ? JaxbImplSupport.mark(variables, THREADING) : variables;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value)) return l;
                if (PROVIDER_STRINGS.contains(value)) return JaxbImplSupport.mark(l, PROVIDER);
                if ((value.startsWith("com.sun.xml.bind.") || value.startsWith("com.sun.xml.internal.bind.")) &&
                    !AUTO_PROPERTIES.contains(value)) return JaxbImplSupport.mark(l, INTERNAL);
                if (value.startsWith("javax.xml.bind.") || value.startsWith("javax.activation.")) {
                    return JaxbImplSupport.mark(l, PROVIDER);
                }
                return l;
            }
        };
    }

    private static boolean assignable(String target, JavaType type) {
        try {
            return TypeUtils.isAssignableTo(target, type);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String owner(JavaType.Method type) {
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(type.getDeclaringType());
        return owner == null ? "" : owner.getFullyQualifiedName();
    }
}
