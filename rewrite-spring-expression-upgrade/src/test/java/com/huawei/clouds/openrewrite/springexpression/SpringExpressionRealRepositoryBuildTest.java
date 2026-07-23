package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.Environment;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringExpressionRealRepositoryBuildTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springexpression.UpgradeSpringExpressionTo6_2_19";
    private static final String CONFIGURE =
            "com.huawei.clouds.openrewrite.springexpression.ConfigureSpringExpression6Build";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19";

    @Test
    void upgradesFixedIpedDeclarationAndPreservesItsExclusionAndNeighbors() {
        String before = fixture("iped-spring-expression-pom.xml.txt");
        String after = before.replace("<version>5.3.39</version>", "<version>6.2.19</version>");
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                pomXml(before, after, source -> source.path("iped-parsers-impl/pom.xml"))
        );
    }

    @Test
    void refusesFixedDubboSharedPropertyOwner() {
        String before = fixture("dubbo-shared-spring-owner-pom.xml.txt");
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                pomXml(before, source -> source.path("dubbo/pom.xml"))
        );
    }

    @Test
    void officialBuildLeavesAreGuardedFromFixedDubboSharedOwner() {
        String before = fixture("dubbo-shared-spring-owner-pom.xml.txt");
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(CONFIGURE)),
                pomXml(before, source -> source.path("dubbo/pom.xml"))
        );
    }

    @Test
    void fixedSonarJavaHigherVersionIsUnchangedAndMarkedAsNoDowngradeConflict() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RECOMMENDED))
                        .expectedCyclesThatMakeChanges(1),
                pomXml(
                        fixture("sonar-java-higher-spring-expression-pom.xml.txt"),
                        source -> source.path("sonar-java/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("<version>7.0.8</version>"), printed);
                                    assertFalse(printed.contains("<version>6.2.19</version>"), printed);
                                    List<String> markers = markerDescriptions(after);
                                    assertEquals(1, markers.size(), markers.toString());
                                    assertTrue(markers.get(0).contains(
                                            "目标版本冲突（禁止降级）"), markers.toString());
                                }))
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springexpression")
                .build();
    }

    private static List<String> markerDescriptions(SourceFile source) {
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

    private static String fixture(String name) {
        try (InputStream input = SpringExpressionRealRepositoryBuildTest.class.getResourceAsStream(
                "/fixtures/real/" + name)) {
            if (input == null) throw new IllegalArgumentException("Missing fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
