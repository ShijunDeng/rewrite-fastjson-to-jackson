package com.huawei.clouds.openrewrite.jaxbimpl;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class JaxbImplRiskDetectionTest implements RewriteTest {
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS =
            new PrintOutputCapture.MarkerPrinter() {
                @Override
                public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> commentWrapper) {
                    return "";
                }

                @Override
                public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> commentWrapper) {
                    return "";
                }

                @Override
                public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> commentWrapper) {
                    return "";
                }
            };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(
                        "jaxb-api", "javax.activation-api", "jakarta.xml.bind-api",
                        "jakarta.activation-api", "jaxb-impl"))
                .markerPrinter(SILENT_MARKERS)
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void marksRemovedValidatorImportAndMethodOnly() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import javax.xml.bind.JAXBContext;
                        import javax.xml.bind.Validator;
                        class Validation { Validator old(JAXBContext context) throws Exception { return context.createValidator(); } }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2, "Validator was removed")))
        );
    }

    @Test
    void marksOnlyAttributedDatatypeConverterParsing() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import javax.xml.bind.DatatypeConverter;
                        class Parse {
                            int strict() { return DatatypeConverter.parseInt("01"); }
                            int ordinary() { return Helper.parseInt("01"); }
                            static class Helper { static int parseInt(String value) { return 1; } }
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1, "lexical conversion is stricter")))
        );
    }

    @Test
    void marksSharedMarshallerButNotContextOrInstanceField() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import jakarta.xml.bind.JAXBContext;
                        import jakarta.xml.bind.Marshaller;
                        import jakarta.xml.bind.Unmarshaller;
                        class Shared {
                            static Marshaller marshaller;
                            volatile Unmarshaller unmarshaller;
                            static JAXBContext context;
                            Marshaller local;
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2, "not thread-safe")))
        );
    }

    @Test
    void marksRiInternalsAndProviderStringsWithPreciseNegatives() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
                        import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
                        class RiUse {
                            JAXBContextImpl context; NamespacePrefixMapper mapper;
                            String provider = "com.sun.xml.bind.v2.ContextFactory";
                            String automatic = "com.sun.xml.bind.namespacePrefixMapper";
                            String business = "prefix com.sun.xml.bind.v2.ContextFactory";
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2,
                                "internal API", "provider lookup")))
        );
    }

    @Test
    void JavaRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplJavaRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.xml.bind.DatatypeConverter; class P { int p() { return DatatypeConverter.parseInt(\"1\"); } }",
                        source -> source.afterRecipe(after -> assertMarks(after, 1, "lexical conversion")))
        );
    }

    @Test
    void JavaRiskMarkersSkipGeneratedPrefixesAndCachesButKeepLeafNames() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("import javax.xml.bind.DatatypeConverter; class A { int p() { return DatatypeConverter.parseInt(\"1\"); } }",
                        source -> source.path("generated-api/A.java").afterRecipe(after -> assertMarks(after, 0))),
                java("import javax.xml.bind.DatatypeConverter; class B { int p() { return DatatypeConverter.parseInt(\"1\"); } }",
                        source -> source.path(".m2/cache/B.java").afterRecipe(after -> assertMarks(after, 0))),
                java("import javax.xml.bind.DatatypeConverter; class C { int p() { return DatatypeConverter.parseInt(\"1\"); } }",
                        source -> source.path("install.java").afterRecipe(after -> assertMarks(after, 1,
                                "lexical conversion")))
        );
    }

    @Test
    void marksMavenJavaPluginAndOutOfScopeVersion() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplBuildRisks()).expectedCyclesThatMakeChanges(1),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>xjc</artifactId><version>1</version>
                          <properties><maven.compiler.release>8</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>2.3.8</version></dependency></dependencies>
                          <build><plugins><plugin><groupId>org.codehaus.mojo</groupId><artifactId>jaxb2-maven-plugin</artifactId><version>2.5.0</version></plugin></plugins></build>
                        </project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 3,
                                "requires Java 11", "outside the workbook source set", "build plugin detected")))
        );
    }

    @Test
    void marksUnresolvedDynamicAndVariantOwners() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplBuildRisks()).expectedCyclesThatMakeChanges(1),
                xml("""
                        <project><dependencies>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId></dependency>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>${ri.version}</version></dependency>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>4.0.6</version><classifier>sources</classifier></dependency>
                        </dependencies></project>
                        """, source -> source.path("pom.xml").afterRecipe(after -> assertMarks(after, 3,
                                "actual owner", "classified or non-JAR")))
        );
    }

    @Test
    void marksLegacyMisalignedAndDuplicateRuntimeButLeavesAlignedSetClean() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplBuildRisks()).expectedCyclesThatMakeChanges(1),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed</artifactId><version>1</version><dependencies>
                          <dependency><groupId>javax.xml.bind</groupId><artifactId>jaxb-api</artifactId><version>2.3.1</version></dependency>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-core</artifactId><version>4.0.3</version></dependency>
                          <dependency><groupId>jakarta.activation</groupId><artifactId>jakarta.activation-api</artifactId><version>2.1.0</version></dependency>
                          <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>4.0.6</version></dependency>
                        </dependencies></project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 4,
                                "Legacy Javax", "not aligned", "additional jaxb-runtime"))),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>aligned</artifactId><version>1</version>
                          <properties><maven.compiler.release>11</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>4.0.6</version></dependency>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-core</artifactId><version>4.0.6</version></dependency>
                          <dependency><groupId>jakarta.xml.bind</groupId><artifactId>jakarta.xml.bind-api</artifactId><version>4.0.4</version></dependency>
                          <dependency><groupId>jakarta.activation</groupId><artifactId>jakarta.activation-api</artifactId><version>2.1.4</version></dependency>
                        </dependencies></project>
                        """, source -> source.path("aligned/pom.xml").afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    @Test
    void marksGradleJavaAndCoordinateRiskOnlyInsideDependencies() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplBuildRisks()).expectedCyclesThatMakeChanges(1),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_1_8
                        def note = 'com.sun.xml.bind:jaxb-impl:2.3.7'
                        dependencies {
                            implementation 'com.sun.xml.bind:jaxb-impl:2.3.7'
                            runtimeOnly 'com.sun.xml.bind:jaxb-impl:4.0.6'
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2,
                                "requires Java 11", "outside the workbook source set")))
        );
    }

    @Test
    void buildPathFilterChecksParentComponentsOnly() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplBuildRisks()).expectedCyclesThatMakeChanges(1),
                buildGradle("sourceCompatibility = '1.8'\n", source -> source.path("install/build.gradle")
                        .afterRecipe(after -> assertMarks(after, 0))),
                buildGradle("sourceCompatibility = '1.8'\n", source -> source.path("install.gradle")
                        .afterRecipe(after -> assertMarks(after, 1, "requires Java 11")))
        );
    }

    @Test
    void marksProviderConfigurationInParsedFormats() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplConfigurationRisks()).expectedCyclesThatMakeChanges(1),
                properties("factory=com.sun.xml.bind.v2.ContextFactory\nordinary=business\n",
                        source -> source.path("application.properties").afterRecipe(after -> assertMarks(after, 1, "legacy Javax"))),
                yaml("provider: javax.xml.bind.JAXBContext\nordinary: business\n",
                        source -> source.path("application.yml").afterRecipe(after -> assertMarks(after, 1, "legacy Javax"))),
                xml("<config provider=\"com.sun.xml.bind.v2.ContextFactory\"><ordinary>business</ordinary></config>",
                        source -> source.path("config.xml").afterRecipe(after -> assertMarks(after, 1, "legacy Javax")))
        );
    }

    @Test
    void marksRemovedAndMalformedServiceDiscoveryButAcceptsOneProvider() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplConfigurationRisks()).expectedCyclesThatMakeChanges(1),
                text("com.sun.xml.bind.v2.ContextFactory\n", source -> source
                        .path("META-INF/services/javax.xml.bind.JAXBContext")
                        .afterRecipe(after -> assertMarks(after, 1, "removed legacy"))),
                text("bad provider name\nother.Provider\n", source -> source
                        .path("META-INF/services/jakarta.xml.bind.JAXBContextFactory")
                        .afterRecipe(after -> assertMarks(after, 1, "removed legacy"))),
                text("org.example.Provider\n", source -> source
                        .path("valid/META-INF/services/jakarta.xml.bind.JAXBContextFactory")
                        .afterRecipe(after -> assertMarks(after, 0))),
                properties("javax.xml.bind.context.factory=com.sun.xml.bind.v2.ContextFactory\n", source -> source
                        .path("model/jaxb.properties").afterRecipe(after -> assertMarks(after, 1, "removed legacy")))
        );
    }

    @Test
    void marksManifestNativeImageAndBindingOutsideAutomaticFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplConfigurationRisks()).expectedCyclesThatMakeChanges(1),
                text("Import-Package: javax.xml.bind,com.sun.xml.bind.v2\n", source -> source
                        .path("META-INF/MANIFEST.MF").afterRecipe(after -> assertMarks(after, 1, "legacy Javax"))),
                text("[{\"name\":\"javax.xml.bind.JAXBContext\"}]\n", source -> source
                        .path("META-INF/native-image/app/reflect-config.json")
                        .afterRecipe(after -> assertMarks(after, 1, "legacy Javax"))),
                text("<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\"/>\n", source -> source
                        .path("service.wsdl").afterRecipe(after -> assertMarks(after, 1, "non-auto-migrated"))),
                xml("<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\"/>", source -> source
                        .path("bindings.xjb").afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    @Test
    void configurationPathFilterChecksParentsAndMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbImplConfigurationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("Import-Package: javax.xml.bind\n", source -> source.path("install/META-INF/MANIFEST.MF")
                        .afterRecipe(after -> assertMarks(after, 0))),
                text("Import-Package: javax.xml.bind\n", source -> source.path("install.MF")
                        .afterRecipe(after -> assertMarks(after, 0))),
                text("Import-Package: javax.xml.bind\n", source -> source.path("MANIFEST.MF")
                        .afterRecipe(after -> assertMarks(after, 1, "legacy Javax")))
        );
    }

    private static void assertMarks(SourceFile source, int expected, String... messageFragments) {
        List<String> descriptions = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                tree.getMarkers().findAll(SearchResult.class).stream()
                        .map(SearchResult::getDescription).forEach(descriptions::add);
                return tree;
            }
        }.visit(source, 0);
        assertEquals(expected, descriptions.size(), descriptions.toString());
        for (String fragment : messageFragments) {
            assertTrue(descriptions.stream().anyMatch(message -> message != null && message.contains(fragment)),
                    () -> "Missing marker fragment '" + fragment + "' in " + descriptions);
        }
    }
}
