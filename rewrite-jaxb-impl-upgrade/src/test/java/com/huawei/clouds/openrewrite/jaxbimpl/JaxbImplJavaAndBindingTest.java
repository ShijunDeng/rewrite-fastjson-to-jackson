package com.huawei.clouds.openrewrite.jaxbimpl;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JaxbImplJavaAndBindingTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(
                        "jaxb-api", "javax.activation-api", "jakarta.xml.bind-api",
                        "jakarta.activation-api", "jaxb-impl"))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesStandardJaxbAndActivationTypes() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java(
                        """
                        import javax.activation.DataHandler;
                        import javax.xml.bind.JAXBContext;
                        import javax.xml.bind.annotation.XmlRootElement;
                        @XmlRootElement class Message { JAXBContext context; DataHandler attachment; }
                        """,
                        """
                        import jakarta.activation.DataHandler;
                        import jakarta.xml.bind.JAXBContext;
                        import jakarta.xml.bind.annotation.XmlRootElement;
                        @XmlRootElement class Message { JAXBContext context; DataHandler attachment; }
                        """
                )
        );
    }

    @Test
    void migratesGoogleHealthcareMapperAndPropertyFixture() {
        // GoogleCloudPlatform/healthcare-data-harmonization@
        // a69ff9619ae665ce475f6206ebc1fb459f69fbc2, reduced from XmlToJsonCDARev2.java.
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java("""
                        package com.sun.xml.bind.marshaller;
                        public abstract class NamespacePrefixMapper {}
                        """, source -> source.path("build/stubs/com/sun/xml/bind/marshaller/NamespacePrefixMapper.java")),
                java(
                        """
                        import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
                        import javax.xml.bind.Marshaller;
                        import javax.xml.bind.PropertyException;
                        class CdaWriter {
                            void configure(Marshaller marshaller, NamespacePrefixMapper mapper) throws PropertyException {
                                marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mapper);
                            }
                        }
                        """,
                        """
                        import jakarta.xml.bind.Marshaller;
                        import jakarta.xml.bind.PropertyException;
                        import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;

                        class CdaWriter {
                            void configure(Marshaller marshaller, NamespacePrefixMapper mapper) throws PropertyException {
                                marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", mapper);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesOnlySixExactRiPropertyKeys() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java(
                        """
                        class Properties {
                            String a = "com.sun.xml.bind.indentString";
                            String b = "com.sun.xml.bind.characterEscapeHandler";
                            String c = "com.sun.xml.bind.xmlDeclaration";
                            String d = "com.sun.xml.bind.xmlHeaders";
                            String e = "com.sun.xml.bind.objectIdentitityCycleDetection";
                            String similar = "com.sun.xml.bind.indentString.extra";
                        }
                        """,
                        """
                        class Properties {
                            String a = "org.glassfish.jaxb.indentString";
                            String b = "org.glassfish.jaxb.characterEscapeHandler";
                            String c = "org.glassfish.jaxb.xmlDeclaration";
                            String d = "org.glassfish.jaxb.xmlHeaders";
                            String e = "org.glassfish.jaxb.objectIdentitityCycleDetection";
                            String similar = "com.sun.xml.bind.indentString.extra";
                        }
                        """
                )
        );
    }

    @Test
    void preservesWholeCompilationUnitThatUsesRemovedValidator() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java("""
                        import javax.xml.bind.JAXBContext;
                        import javax.xml.bind.Validator;
                        class OldValidation { Validator create(JAXBContext c) throws Exception { return c.createValidator(); } }
                        """)
        );
    }

    @Test
    void preservesJaxbPackageWhenRemovedValidationMethodIsUsedWithoutValidatorDeclaration() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java("""
                        import javax.xml.bind.JAXBContext;
                        class OldValidation {
                            void configure(JAXBContext context) throws Exception {
                                context.createValidator().setEventHandler(null);
                            }
                        }
                        """)
        );
    }

    @Test
    void parentDirectoryFilterSkipsGeneratedButKeepsLeafNames() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java("class Generated { String p = \"com.sun.xml.bind.xmlHeaders\"; }",
                        source -> source.path("generated/src/Generated.java")),
                java("class Installed { String p = \"com.sun.xml.bind.xmlHeaders\"; }",
                        "class Installed { String p = \"org.glassfish.jaxb.xmlHeaders\"; }",
                        source -> source.path("install.java")),
                java("class TargetLeaf { String p = \"com.sun.xml.bind.xmlDeclaration\"; }",
                        "class TargetLeaf { String p = \"org.glassfish.jaxb.xmlDeclaration\"; }",
                        source -> source.path("target.java"))
        );
    }

    @Test
    void deterministicJavaMigrationSkipsGeneratedPrefixesAndCaches() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava()),
                java("class GeneratedClient { String p = \"com.sun.xml.bind.xmlHeaders\"; }",
                        source -> source.path("generated-client/GeneratedClient.java")),
                java("class InstalledCopy { String p = \"com.sun.xml.bind.xmlHeaders\"; }",
                        source -> source.path("INSTALLER/InstalledCopy.java")),
                java("class CachedCopy { String p = \"com.sun.xml.bind.xmlHeaders\"; }",
                        source -> source.path(".m2/cache/CachedCopy.java")),
                java("class Leaf { String p = \"com.sun.xml.bind.xmlHeaders\"; }",
                        "class Leaf { String p = \"org.glassfish.jaxb.xmlHeaders\"; }",
                        source -> source.path("generated-client.java"))
        );
    }

    @Test
    void deterministicJavaMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbImplDeterministicJava())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.activation.DataHandler; class A { DataHandler h; }",
                        "import jakarta.activation.DataHandler; class A { DataHandler h; }")
        );
    }

    @Test
    void migratesRealPahoBindingFile() {
        // eclipse-paho/paho.mqtt-spy@737699afbabaf01520302080a6f8b910f121ab2,
        // reduced from spy-common/src/main/resources/spy-bindings.xjb.
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles()),
                xml(
                        """
                        <jxb:bindings xmlns:jxb="http://java.sun.com/xml/ns/jaxb" version="2.0" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                          <jxb:globalBindings><jxb:serializable uid="1"/></jxb:globalBindings>
                          <jxb:bindings namespace="http://baczkowicz.pl/spy/common" schemaLocation="spy-common.xsd">
                            <jxb:schemaBindings><jxb:package name="pl.baczkowicz.spy.common.generated"/></jxb:schemaBindings>
                          </jxb:bindings>
                        </jxb:bindings>
                        """,
                        """
                        <jxb:bindings xmlns:jxb="https://jakarta.ee/xml/ns/jaxb" version="3.0" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                          <jxb:globalBindings><jxb:serializable uid="1"/></jxb:globalBindings>
                          <jxb:bindings namespace="http://baczkowicz.pl/spy/common" schemaLocation="spy-common.xsd">
                            <jxb:schemaBindings><jxb:package name="pl.baczkowicz.spy.common.generated"/></jxb:schemaBindings>
                          </jxb:bindings>
                        </jxb:bindings>
                        """,
                        source -> source.path("spy-bindings.xjb")
                )
        );
    }

    @Test
    void migratesSchemaLocationNamespacePairAndPreservesXjcNamespace() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles()),
                xml(
                        """
                        <jaxb:bindings xmlns:jaxb="http://java.sun.com/xml/ns/jaxb"
                                       xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc"
                                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                       xsi:schemaLocation="http://java.sun.com/xml/ns/jaxb http://java.sun.com/xml/ns/jaxb/bindingschema_2_0.xsd"
                                       jaxb:version="2.1" jaxb:extensionBindingPrefixes="xjc"/>
                        """,
                        """
                        <jaxb:bindings xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb"
                                       xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc"
                                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                       xsi:schemaLocation="https://jakarta.ee/xml/ns/jaxb https://jakarta.ee/xml/ns/jaxb/bindingschema_3_0.xsd"
                                       jaxb:version="3.0" jaxb:extensionBindingPrefixes="xjc"/>
                        """,
                        source -> source.path("schema-pair.xjb")
                )
        );
    }

    @Test
    void migratesInlineXsdBindingVersion() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles()),
                xml(
                        """
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:jaxb="http://java.sun.com/xml/ns/jaxb" jaxb:version="2.0">
                          <xs:annotation><xs:appinfo><jaxb:globalBindings/></xs:appinfo></xs:annotation>
                        </xs:schema>
                        """,
                        """
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb" jaxb:version="3.0">
                          <xs:annotation><xs:appinfo><jaxb:globalBindings/></xs:appinfo></xs:annotation>
                        </xs:schema>
                        """,
                        source -> source.path("src/main/xsd/order.xsd")
                )
        );
    }

    @Test
    void leavesXjcOnlyOrdinaryXmlAndNonBindingExtensionUntouched() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles()),
                xml("<bindings xmlns:xjc=\"http://java.sun.com/xml/ns/jaxb/xjc\"><xjc:simple/></bindings>",
                        source -> source.path("xjc-only.xjb")),
                xml("<root xmlns=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.1\"/>",
                        source -> source.path("ordinary.xml")),
                xml("<root xmlns=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.1\"/>",
                        source -> source.path("service.wsdl"))
        );
    }

    @Test
    void bindingPathFilterChecksParentsNotLeaf() {
        String before = "<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.0\"/>";
        String after = "<jaxb:bindings xmlns:jaxb=\"https://jakarta.ee/xml/ns/jaxb\" version=\"3.0\"/>";
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles()),
                xml(before, after, source -> source.path("install.xjb")),
                xml(before, after, source -> source.path("generated.xjb")),
                xml(before, source -> source.path("install/binding.xjb")),
                xml(before, source -> source.path("target/model.xsd"))
        );
    }

    @Test
    void bindingMigrationSkipsGeneratedPrefixesAndCaches() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles()),
                xml("<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.0\"/>",
                        source -> source.path("generated-model/bindings.xjb")),
                xml("<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.0\"/>",
                        source -> source.path(".cache/bindings.xjb")),
                xml("<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" version=\"2.0\"/>",
                        "<jaxb:bindings xmlns:jaxb=\"https://jakarta.ee/xml/ns/jaxb\" version=\"3.0\"/>",
                        source -> source.path("install.xjb"))
        );
    }

    @Test
    void bindingMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJaxbBindingFiles())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml("<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" version=\"1.0\"/>",
                        "<jaxb:bindings xmlns:jaxb=\"https://jakarta.ee/xml/ns/jaxb\" version=\"3.0\"/>",
                        source -> source.path("bindings.jxb"))
        );
    }
}
