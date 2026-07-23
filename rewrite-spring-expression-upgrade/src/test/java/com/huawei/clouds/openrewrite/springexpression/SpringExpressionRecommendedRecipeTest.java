package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringExpressionRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19";
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springexpression.UpgradeSpringExpressionTo6_2_19";
    private static final String CONFIGURE =
            "com.huawei.clouds.openrewrite.springexpression.ConfigureSpringExpression6Build";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springexpression.FindSpringExpression6_2Risks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATE));
    }

    @Test
    void runtimeRecipeTreeHasDeterministicOrderAndDirectOfficialBuildLeaves() {
        Recipe aggregate = environment().activateRecipes(MIGRATE);
        assertEquals(List.of(UPGRADE, CONFIGURE, RISKS),
                aggregate.getRecipeList().stream().map(Recipe::getName).toList());

        DeclarativeRecipe configure = assertInstanceOf(
                DeclarativeRecipe.class, aggregate.getRecipeList().get(1));
        assertEquals(List.of(FindSpringExpressionBuildOwner.class),
                configure.getPreconditions().stream().map(Object::getClass).toList());
        assertEquals(List.of(
                        "org.openrewrite.maven.UpdateMavenProjectPropertyJavaVersion",
                        "org.openrewrite.maven.AddProperty",
                        "org.openrewrite.gradle.UpdateJavaCompatibility",
                        "org.openrewrite.gradle.UpdateJavaCompatibility"),
                effectiveChildren(configure).stream().map(Recipe::getName).toList());

        Recipe risks = aggregate.getRecipeList().get(2);
        assertEquals(List.of(
                        FindSpringExpressionBuildRisks.class,
                        FindSpringExpressionSourceRisks.class,
                        FindSpringExpressionConfigurationRisks.class),
                risks.getRecipeList().stream().map(Object::getClass).toList());

        List<String> names = flatten(aggregate);
        assertFalse(names.contains(
                "com.huawei.clouds.openrewrite.springexpression.PreserveLegacyUnlimitedSpelOperations"));
        assertFalse(names.contains(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2"));
        assertFalse(names.contains(
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion"));
        assertTrue(aggregate.validate().isValid(), aggregate.validate().toString());
    }

    @Test
    void recommendedRecipeUpgradesThenRunsOfficialBuildConfigurationAndIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        pom("""
                        <properties><java.version>11</java.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("6.2.18"))),
                        pom("""
                        <properties><java.version>17</java.version>
                            <maven.compiler.parameters>true</maven.compiler.parameters>
                        </properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("6.2.19"))),
                        source -> source.afterRecipe(after -> assertMarkerCount(after, 0)))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"6.2.20", "6.3.0", "7.0.0"})
    void recommendedRecipeNeverDowngradesAndAddsExactConflictMarker(String version) {
        rewriteRun(
                spec -> spec.expectedCyclesThatMakeChanges(1),
                xml(
                        pom("<dependencies>" + dependency(version) + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            List<String> messages = markerDescriptions(after);
                            assertEquals(1, messages.size(), messages.toString());
                            assertTrue(messages.get(0).contains("目标版本冲突（禁止降级）"), messages.toString());
                        }))
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static List<String> flatten(Recipe root) {
        List<String> names = new ArrayList<>();
        for (Recipe child : root.getRecipeList()) {
            names.add(child.getName());
            names.addAll(flatten(child));
        }
        return names;
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(SpringExpressionRecommendedRecipeTest::unwrap)
                .filter(child -> !child.getClass().getName().endsWith("PreconditionBellwether"))
                .toList();
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }

    private static String pom(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependency(String version) {
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-expression</artifactId>" +
               "<version>" + version + "</version></dependency>";
    }

    private static void assertMarkerCount(SourceFile source, int expected) {
        assertEquals(expected, markerDescriptions(source).size());
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
}
