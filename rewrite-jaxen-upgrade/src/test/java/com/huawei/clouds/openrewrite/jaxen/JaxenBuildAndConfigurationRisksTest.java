package com.huawei.clouds.openrewrite.jaxen;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class JaxenBuildAndConfigurationRisksTest implements RewriteTest {
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS = new PrintOutputCapture.MarkerPrinter() {
        @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
        @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
        @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.markerPrinter(SILENT_MARKERS);
    }

    @Test
    void marksMavenJava14PropertiesAndCompilerPluginConfiguration() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenBuildMigrationRisks()).expectedCyclesThatMakeChanges(1),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>old-jvm</artifactId><version>1</version>
                          <properties><maven.compiler.source>1.4</maven.compiler.source></properties>
                          <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><target>4</target></configuration></plugin></plugins></build>
                          <dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>2.0.1</version></dependency></dependencies>
                        </project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2, "Java 1.5")))
        );
    }

    @Test
    void acceptsJava5AndModernMavenCompilerLevels() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenBuildMigrationRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>modern</artifactId><version>1</version>
                          <properties><java.version>1.5</java.version><maven.compiler.release>17</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>2.0.1</version></dependency></dependencies>
                        </project>
                        """)
        );
    }

    @Test
    void distinguishesMavenOwnershipVersionAndVariantRisks() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenBuildMigrationRisks()).expectedCyclesThatMakeChanges(1),
                xml(pomDependency(""), source -> source.path("versionless/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "actual owner"))),
                pomXml(pomDependency("<version>[1.2,2.1)</version>"), source -> source.path("range/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "actual owner"))),
                pomXml(pomDependency("<version>1.1.6</version>"), source -> source.path("other/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "outside the workbook source set"))),
                pomXml(pomDependency("<version>2.0.1</version><classifier>tests</classifier>"),
                        source -> source.path("variant/pom.xml")
                                .afterRecipe(after -> assertMarks(after, 1, "artifact shape")))
        );
    }

    @Test
    void marksGradleJava14AndUnresolvedOrDynamicDependencies() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenBuildMigrationRisks()).expectedCyclesThatMakeChanges(1),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_1_4
                        dependencies {
                            implementation 'jaxen:jaxen:2.+'
                            runtimeOnly 'jaxen:jaxen:2.0.1@zip'
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 3,
                        "Java 1.5", "actual owner", "artifact shape"))),
                buildGradleKts("""
                        dependencies { implementation("jaxen:jaxen:${jaxenVersion}") }
                        """, source -> source.path("module/build.gradle.kts")
                        .afterRecipe(after -> assertMarks(after, 1, "actual owner")))
        );
    }

    @Test
    void rootGradleTargetPropertyIsResolvedButSubmoduleOwnershipIsNotAssumed() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenBuildMigrationRisks()).expectedCyclesThatMakeChanges(1),
                buildGradle("""
                        ext.jaxenVersion = '2.0.1'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """),
                buildGradle("""
                        ext.jaxenVersion = '2.0.1'
                        dependencies { implementation "jaxen:jaxen:$jaxenVersion" }
                        """, source -> source.path("module/build.gradle")
                        .afterRecipe(after -> assertMarks(after, 1, "actual owner")))
        );
    }

    @Test
    void marksExactStructuredConfigurationReferences() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenConfigurationRisks()).expectedCyclesThatMakeChanges(1),
                properties("xpath.engine=org.jaxen.dom4j.Dom4jXPath\n",
                        source -> source.afterRecipe(after -> assertMarks(after, 1, "model library explicit"))),
                yaml("reflection:\n  class: org.jaxen.util.StackedIterator\n",
                        source -> source.path("src/main/resources/xpath.yml")
                                .afterRecipe(after -> assertMarks(after, 1, "removed or hidden"))),
                xml("<factory type=\"org.jaxen.dom.DOMXPath\">org.jaxen.dom.DocumentNavigator</factory>",
                        source -> source.path("src/main/resources/xpath.xml")
                                .afterRecipe(after -> assertMarks(after, 2, "object-model engine", "document loading")))
        );
    }

    @Test
    void marksLegacyModuleNameAndPlainTextMetadata() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenConfigurationRisks()).expectedCyclesThatMakeChanges(1),
                text("module example { requires jaxen; }", source -> source.path("src/main/java/module-info.java")
                        .afterRecipe(after -> assertMarks(after, 1, "Automatic-Module-Name org.jaxen"))),
                text("{\"name\":\"org.jaxen.pattern.NoNodeTest\"}",
                        source -> source.path("src/main/resources/META-INF/native-image/reflect-config.json")
                                .afterRecipe(after -> assertMarks(after, 1, "removed or hidden"))),
                text("XPath-Engine: org.jaxen.xom.XOMXPath\n", source -> source.path("META-INF/MANIFEST.MF")
                        .afterRecipe(after -> assertMarks(after, 1, "model library explicit"))),
                text("factory=org.jaxen.saxpath.DocumentNavigator DocumentBuilderFactory\n",
                        source -> source.path("runtime.cfg")
                                .afterRecipe(after -> assertMarks(after, 1, "document loading")))
        );
    }

    @Test
    void ignoresSimilarBusinessTextModernModulePomAndUnsupportedTextFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenConfigurationRisks()),
                properties("xpath.engine=com.example.DOMXPath\nmessage=org.jaxen is enabled\n"),
                text("module example { requires org.jaxen; }", source -> source.path("module-info.java")),
                text("org.jaxen.dom.DOMXPath", source -> source.path("notes.txt")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>config</artifactId><version>1</version>
                          <properties><engine>org.jaxen.dom.DOMXPath</engine></properties>
                        </project>
                        """)
        );
    }

    @Test
    void parentGeneratedDirectoriesAreSkippedButLeafNamesRemainEligible() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenConfigurationRisks()).expectedCyclesThatMakeChanges(1),
                text("org.jaxen.dom.DOMXPath", source -> source.path("generated/reflect-config.json")
                        .afterRecipe(after -> assertMarks(after, 0))),
                text("org.jaxen.dom.DOMXPath", source -> source.path("install.json")
                        .afterRecipe(after -> assertMarks(after, 1, "object-model engine")))
        );
    }

    @Test
    void buildAndConfigurationMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxenBuildMigrationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pomDependency("<version>1.1.6</version>"),
                        source -> source.afterRecipe(after -> assertMarks(after, 1, "outside the workbook source set")))
        );
        rewriteRun(
                spec -> spec.recipe(new FindJaxenConfigurationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties("engine=org.jaxen.dom.DOMXPath\n",
                        source -> source.afterRecipe(after -> assertMarks(after, 1, "object-model engine")))
        );
    }

    private static String pomDependency(String detail) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId>" +
               "<version>1</version><dependencies><dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId>" +
               detail + "</dependency></dependencies></project>";
    }

    private static void assertMarks(SourceFile source, int expected, String... fragments) {
        List<String> descriptions = markDescriptions(source);
        assertEquals(expected, descriptions.size(), descriptions.toString());
        for (String fragment : fragments) {
            assertTrue(descriptions.stream().anyMatch(message -> message != null && message.contains(fragment)),
                    () -> "Missing '" + fragment + "' in " + descriptions);
        }
    }

    private static List<String> markDescriptions(SourceFile source) {
        List<String> descriptions = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                tree.getMarkers().findAll(SearchResult.class).stream()
                        .map(SearchResult::getDescription).forEach(descriptions::add);
                return tree;
            }
        }.visit(source, 0);
        return descriptions;
    }
}
