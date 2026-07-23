package com.huawei.clouds.openrewrite.springcloudcontext;

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
import static org.openrewrite.yaml.Assertions.yaml;
import static org.openrewrite.xml.Assertions.xml;

class SpringCloudContextRiskDetectionTest implements RewriteTest {
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS =
            new PrintOutputCapture.MarkerPrinter() {
                @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
                @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
                @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) { return ""; }
            };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion())
                .markerPrinter(SILENT_MARKERS)
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void marksJavaxNativeDeprecatedAndDirectImplementationImportsPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import javax.servlet.http.HttpServletRequest;
                        import org.springframework.nativex.hint.TypeHint;
                        import org.springframework.cloud.env.EnvironmentUtils;
                        import org.springframework.cloud.context.environment.WritableEnvironmentEndpoint;
                        import java.util.List;
                        class Legacy { List<String> ordinary; }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 4,
                        "Jakarta EE", "RuntimeHints", "deprecated for removal", "sanitization")))
        );
    }

    @Test
    void marksRefreshScopeAnnotationButNotAnUnrelatedAnnotation() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.springframework.cloud.context.config.annotation.RefreshScope;
                        @RefreshScope class Refreshable {}
                        @Deprecated class Ordinary {}
                        """, source -> source.afterRecipe(after -> assertMarks(after, 1, "lazy proxies")))
        );
    }

    @Test
    void marksCustomBootstrapAndContextRefresherExtensionPoints() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.springframework.cloud.context.refresh.ContextRefresher;
                        import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
                        class CustomRefresh extends ContextRefresher {}
                        class CustomLocator implements PropertySourceLocator {}
                        """, source -> source.afterRecipe(after -> assertMarks(after, 4,
                        "Direct refresh", "Custom ContextRefresher", "Legacy bootstrap", "two-phase")))
        );
    }

    @Test
    void marksMovedProtectedRefreshConstantAtItsUse() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextJavaRisks()).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.springframework.cloud.context.refresh.ContextRefresher;
                        class Custom { String name = ContextRefresher.REFRESH_ARGS_PROPERTY_SOURCE; }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2,
                        "Direct refresh", "moved out of ContextRefresher")))
        );
    }

    @Test
    void JavaMarkersAreIdempotentAndRespectGeneratedParentBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextJavaRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.validation.Valid; class A {}",
                        source -> source.path("generated-client/A.java").afterRecipe(after -> assertMarks(after, 0))),
                java("import javax.validation.Valid; class B {}",
                        source -> source.path("install.java").afterRecipe(after -> assertMarks(after, 1, "Jakarta EE")))
        );
    }

    @Test
    void marksMavenPlatformBaselineAndOwnershipRisks() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextBuildRisks()).expectedCyclesThatMakeChanges(1),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${spring-boot.version}</version></parent>
                          <groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                          <properties>
                            <java.version>11</java.version><spring-boot.version>3.4.9</spring-boot.version><spring-cloud.version>2024.0.2</spring-cloud.version>
                          </properties>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies>
                            <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId></dependency>
                            <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-rsa</artifactId><version>1.1.5</version></dependency>
                            <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                            <dependency><groupId>org.springframework.experimental</groupId><artifactId>spring-native</artifactId><version>0.12.1</version></dependency>
                            <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-bootstrap</artifactId><version>4.3.2</version></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml").afterRecipe(after -> assertMarks(after, 10,
                        "Java 17", "Boot 3.5", "Spring Cloud 2025.0.2", "versionless",
                        "spring-security-rsa", "Jakarta", "Spring Native", "legacy parent-context")))
        );
    }

    @Test
    void acceptsAlignedMavenOwnersIncludingProfileOverride() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextBuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${spring-boot.version}</version></parent>
                          <groupId>example</groupId><artifactId>aligned</artifactId><version>1</version>
                          <properties><java.version>17</java.version><spring-boot.version>3.5.13</spring-boot.version><spring-cloud.version>2025.0.2</spring-cloud.version><context.version>9.9.9</context.version></properties>
                          <dependencyManagement><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                          <profiles><profile><id>target</id><properties><context.version>4.3.2</context.version></properties><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${context.version}</version></dependency></dependencies></profile></profiles>
                        </project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    @Test
    void marksMavenVariantsAotAndExternallyOwnedVersion() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextBuildRisks()).expectedCyclesThatMakeChanges(1),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variants</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>${external.context}</version></dependency>
                          <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-context</artifactId><version>4.3.2</version><classifier>tests</classifier></dependency>
                        </dependencies><build><plugins>
                          <plugin><groupId>org.graalvm.buildtools</groupId><artifactId>native-maven-plugin</artifactId><version>0.10.6</version></plugin>
                        </plugins></build></project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 3,
                        "ambiguously owned", "variants", "AOT/native")))
        );
    }

    @Test
    void marksGradlePlatformsMapsVariantsAndLegacyDependenciesOnlyInRootDsl() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextBuildRisks()).expectedCyclesThatMakeChanges(1),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_11
                        dependencies {
                            implementation 'org.springframework.cloud:spring-cloud-context'
                            runtimeOnly 'org.springframework.cloud:spring-cloud-context:4.3.2:tests'
                            implementation 'org.springframework.cloud:spring-cloud-context:4.3.1'
                            implementation group: 'org.springframework.cloud', name: 'spring-cloud-context'
                            implementation([group: 'org.springframework.cloud', name: 'spring-cloud-context', version: '4.3.2', classifier: 'sources'])
                            implementation platform('org.springframework.cloud:spring-cloud-dependencies:2024.0.2')
                            implementation enforcedPlatform('org.springframework.boot:spring-boot-dependencies:3.4.9')
                            implementation 'org.springframework.security:spring-security-rsa:1.1.5'
                            implementation 'javax.servlet:javax.servlet-api:4.0.1'
                            implementation 'org.springframework.cloud:spring-cloud-starter-bootstrap:4.3.2'
                            implementation 'org.springframework.cloud:spring-cloud-context:4.3.2'
                        }
                        buildscript { dependencies { implementation 'javax.servlet:javax.servlet-api:4.0.1' } }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 11,
                        "Java 17", "versionless", "variants", "outside or ambiguously", "2025.0.2",
                        "Boot 3.5", "spring-security-rsa", "Javax/Spring Native", "legacy parent-context")))
        );
    }

    @Test
    void buildCompanionAuditRequiresContextConsumerInTheSameOwner() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextBuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sibling</artifactId><version>1</version>
                          <properties><java.version>11</java.version><spring-cloud.version>2024.0.2</spring-cloud.version></properties>
                          <dependencies>
                            <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-rsa</artifactId><version>1.1.5</version></dependency>
                            <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-bootstrap</artifactId><version>4.3.2</version></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.afterRecipe(after -> assertMarks(after, 0))),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_11
                        dependencies {
                            implementation platform('org.springframework.cloud:spring-cloud-dependencies:2024.0.2')
                            implementation 'javax.servlet:javax.servlet-api:4.0.1'
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    @Test
    void marksCatalogAndDynamicContextOwnersAtTheDependencyInvocation() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextBuildRisks()).expectedCyclesThatMakeChanges(1),
                buildGradle("""
                        def contextVersion = '3.1.7'
                        dependencies {
                            implementation libs.spring.cloud.context
                            runtimeOnly "org.springframework.cloud:spring-cloud-context:${contextVersion}"
                        }
                        """, source -> source.afterRecipe(after -> assertMarks(after, 2, "variable/catalog")))
        );
    }

    @Test
    void marksPropertiesBootstrapRefreshEndpointAndEncryptionEntriesOnly() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextPropertiesRisks()).expectedCyclesThatMakeChanges(1),
                properties("""
                        spring.cloud.bootstrap.enabled=true
                        spring.cloud.config.initialize-on-context-refresh=true
                        spring.cloud.refresh.enabled=true
                        spring.cloud.refresh.extra-refreshable=com.example.Mutable
                        spring.cloud.refresh.on-restart.enabled=false
                        management.endpoints.web.exposure.include=health,refresh,env
                        encrypt.key=secret
                        business.mode=ordinary
                        """, source -> source.path("application.properties")
                        .afterRecipe(after -> assertMarks(after, 7,
                                "architecture choice", "two-phase", "unsupported", "class names or bean names",
                                "checkpoint/restore", "authentication", "Bouncy Castle"))),
                properties("spring.application.name=config-client\n",
                        source -> source.path("bootstrap.properties")
                                .afterRecipe(after -> assertMarks(after, 1, "legacy parent-context"))),
                properties("spring.cloud.refresh.enabled=true\n",
                        source -> source.path("generated-resources/application.properties")
                                .afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    @Test
    void marksYamlConfigDataRefreshAndCipherScalarsAtExactEntries() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextYamlRisks()).expectedCyclesThatMakeChanges(1),
                yaml("""
                        spring:
                          config:
                            import: 'optional:configserver:http://localhost:8888'
                          cloud:
                            refresh:
                              never-refreshable: com.example.Fixed
                        management:
                          endpoint:
                            refresh:
                              enabled: true
                        password: '{cipher}AQB...'
                        ordinary: value
                        """, source -> source.path("application.yml")
                        .afterRecipe(after -> assertMarks(after, 4,
                                "Config Data", "include/exclude", "mutate live", "Encryption")))
        );
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringCloudContextPropertiesRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties("spring.cloud.refresh.enabled=false\n", source -> source.path("application.properties")
                        .afterRecipe(after -> assertMarks(after, 1, "AOT/native")))
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
