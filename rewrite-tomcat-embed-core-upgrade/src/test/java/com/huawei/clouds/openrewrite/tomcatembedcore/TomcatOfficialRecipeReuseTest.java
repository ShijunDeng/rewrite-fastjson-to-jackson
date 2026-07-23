package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.DeleteMethodArgument;
import org.openrewrite.java.ReorderMethodArguments;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.search.DoesNotUseType;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomcatOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.tomcatembedcore.";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatembedcore",
                                  "org.openrewrite.java.migrate.jakarta")
            .build();

    @Test
    void apiDependencyCompositionUsesOfficialExactVersionRecipes() {
        DeclarativeRecipe recipe = recipe("MigrateTomcat9JakartaApiDependencies");
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        ChangeDependency.class,
                        UpgradeDependencyVersion.class,
                        ChangeDependency.class,
                        UpgradeDependencyVersion.class),
                composition.stream().map(Object::getClass).toList());
        assertEquals(List.of(IsTomcatNonGeneratedSource.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        List<ChangeDependency> changes = composition.stream()
                .filter(ChangeDependency.class::isInstance)
                .map(ChangeDependency.class::cast)
                .toList();
        assertEquals(Set.of(
                        "javax.servlet:javax.servlet-api->jakarta.servlet:jakarta.servlet-api:6.0.0",
                        "javax.el:javax.el-api->jakarta.el:jakarta.el-api:5.0.1"),
                changes.stream().map(change ->
                        change.getOldGroupId() + ":" + change.getOldArtifactId() + "->" +
                        change.getNewGroupId() + ":" + change.getNewArtifactId() + ":" +
                        change.getNewVersion()).collect(java.util.stream.Collectors.toSet()));

        List<UpgradeDependencyVersion> upgrades = composition.stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .toList();
        assertEquals(Set.of(
                        "jakarta.servlet:jakarta.servlet-api:6.0.0",
                        "jakarta.el:jakarta.el-api:5.0.1"),
                upgrades.stream().map(upgrade ->
                        upgrade.getGroupId() + ":" + upgrade.getArtifactId() + ":" +
                        upgrade.getNewVersion()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void namespaceCompositionUsesOfficialChangePackageAndRemovedTypeGuards() {
        DeclarativeRecipe recipe = recipe("MigrateTomcat9JakartaNamespaces");
        List<Recipe> composition = composition(recipe);

        assertEquals(Set.of(
                        "javax.servlet->jakarta.servlet:true",
                        "javax.el->jakarta.el:true"),
                composition.stream()
                        .map(ChangePackage.class::cast)
                        .map(change -> change.getOldPackageName() + "->" + change.getNewPackageName() +
                                       ":" + change.getRecursive())
                        .collect(java.util.stream.Collectors.toSet()));

        assertEquals(List.of(IsTomcatNonGeneratedSource.class,
                        DoesNotUseType.class, DoesNotUseType.class, DoesNotUseType.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
        assertEquals(Set.of(
                        "javax.servlet.SingleThreadModel",
                        "javax.servlet.http.HttpSessionContext",
                        "javax.servlet.http.HttpUtils"),
                recipe.getPreconditions().stream()
                        .filter(DoesNotUseType.class::isInstance)
                        .map(DoesNotUseType.class::cast)
                        .peek(guard -> assertEquals(Boolean.TRUE, guard.getIncludeImplicit()))
                        .map(DoesNotUseType::getFullyQualifiedTypeName)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void servletCompositionUsesSafeOfficialJakarta10Leaves() {
        DeclarativeRecipe recipe = recipe("MigrateTomcatEmbedCore101Java");
        List<Recipe> composition = composition(recipe);

        assertEquals(9, composition.stream().filter(ChangeMethodName.class::isInstance).count());
        assertEquals(4, composition.stream().filter(DeleteMethodArgument.class::isInstance).count());
        assertEquals(2, composition.stream().filter(ReorderMethodArguments.class::isInstance).count());
        assertEquals(List.of(
                        "org.openrewrite.java.migrate.jakarta.UpdateGetRealPath",
                        "org.openrewrite.java.migrate.jakarta.RemovedIsParmetersProvidedMethod",
                        "org.openrewrite.java.migrate.jakarta.ServletCookieBehaviorChangeRFC6265"),
                composition.stream().map(Recipe::getName)
                        .filter(name -> name.startsWith("org.openrewrite.java.migrate.jakarta."))
                        .toList());
        assertTrue(composition.stream().filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .noneMatch(change -> change.getMethodPattern().contains("getValueNames")));
        assertEquals(Set.of(
                        "getAttribute", "setAttribute", "removeAttribute",
                        "isRequestedSessionIdFromURL", "encodeURL", "encodeRedirectURL"),
                composition.stream().filter(ChangeMethodName.class::isInstance)
                        .map(ChangeMethodName.class::cast)
                        .map(ChangeMethodName::getNewMethodName)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(IsTomcatNonGeneratedSource.class, recipe.getPreconditions().get(0).getClass());
        assertEquals(Set.of(
                        "javax.servlet.SingleThreadModel",
                        "javax.servlet.http.HttpSessionContext",
                        "javax.servlet.http.HttpUtils",
                        "jakarta.servlet.SingleThreadModel",
                        "jakarta.servlet.http.HttpSessionContext",
                        "jakarta.servlet.http.HttpUtils"),
                recipe.getPreconditions().stream()
                        .filter(DoesNotUseType.class::isInstance)
                        .map(DoesNotUseType.class::cast)
                        .map(DoesNotUseType::getFullyQualifiedTypeName)
                        .collect(java.util.stream.Collectors.toSet()));

        Set<String> activated = flatten(recipe).map(Recipe::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(activated.contains("org.openrewrite.java.ChangeMethodName"));
        assertTrue(activated.contains("org.openrewrite.java.DeleteMethodArgument"));
        assertTrue(activated.contains("org.openrewrite.java.ReorderMethodArguments"));
        assertTrue(activated.contains("org.openrewrite.java.RemoveMethodInvocations"));
        assertTrue(activated.contains("org.openrewrite.java.migrate.jakarta.UpdateGetRealPath"));

        Set<String> broadAggregates = Set.of(
                "org.openrewrite.java.migrate.jakarta.RemovalsServletJakarta10",
                "org.openrewrite.java.migrate.jakarta.JakartaEE10",
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta",
                "org.openrewrite.java.migrate.jakarta.JavaxServletToJakartaServlet",
                "org.openrewrite.java.migrate.jakarta.JavaxElToJakartaEl",
                "org.openrewrite.java.migrate.jakarta.MigrationToJakarta10Apis");
        assertTrue(java.util.Collections.disjoint(activated, broadAggregates));
        assertFalse(flatten(recipe)
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .anyMatch(upgrade -> "org.apache.tomcat.embed".equals(upgrade.getGroupId()) &&
                                     "tomcat-embed-core".equals(upgrade.getArtifactId())));
    }

    private static DeclarativeRecipe recipe(String shortName) {
        return assertInstanceOf(DeclarativeRecipe.class,
                ENVIRONMENT.activateRecipes(PREFIX + shortName));
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(TomcatOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(TomcatOfficialRecipeReuseTest::flatten));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(TomcatOfficialRecipeReuseTest::isCompositionRecipe)
                .map(TomcatOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegate) {
            current = delegate.getDelegate();
        }
        return current;
    }
}
