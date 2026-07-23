package com.huawei.clouds.openrewrite.nettyhandler;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateRuleBasedIpFilterDefaultTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.nettyhandler.MigrateRuleBasedIpFilterDefault";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe())
                .parser(JavaParser.fromJavaVersion().classpath("netty-handler", "netty-buffer"));
    }

    @Test
    void runtimeTreeUsesOfficialConstructorRecipeAndFormattingOnlyAdapter() {
        Recipe recipe = recipe();
        List<Recipe> tree = flatten(recipe);
        var official = tree.stream()
                .filter(org.openrewrite.java.AddLiteralMethodArgument.class::isInstance)
                .map(org.openrewrite.java.AddLiteralMethodArgument.class::cast)
                .findFirst().orElseThrow();
        assertEquals("io.netty.handler.ipfilter.RuleBasedIpFilter " +
                     "<constructor>(io.netty.handler.ipfilter.IpFilterRule[])",
                official.getMethodPattern());
        assertEquals(0, official.getArgumentIndex());
        assertEquals(true, official.getLiteral());
        assertEquals("boolean", official.getPrimitiveType());
        assertEquals(1, tree.stream()
                .filter(org.openrewrite.java.AddLiteralMethodArgument.class::isInstance).count());
        assertTrue(tree.stream()
                .anyMatch(NormalizeRuleBasedIpFilterLiteralSpacing.class::isInstance));
        assertFalse(tree.stream()
                .map(Recipe::getName)
                .anyMatch("com.huawei.clouds.openrewrite.nettyhandler.MigrateDeprecatedNettyHandlerApis"::equals));
    }

    @Test
    void makesDefaultAcceptPolicyExplicitForArray() {
        rewriteRun(java(
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create(IpFilterRule[] rules) {
                        return new RuleBasedIpFilter(rules);
                    }
                }
                """,
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create(IpFilterRule[] rules) {
                        return new RuleBasedIpFilter(true, rules);
                    }
                }
                """));
    }

    @Test
    void makesDefaultAcceptPolicyExplicitForExpandedVarargs() {
        rewriteRun(java(
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create(IpFilterRule first, IpFilterRule second) {
                        return new RuleBasedIpFilter(first, second);
                    }
                }
                """,
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create(IpFilterRule first, IpFilterRule second) {
                        return new RuleBasedIpFilter(true, first, second);
                    }
                }
                """));
    }

    @Test
    void makesZeroRuleVarargsDefaultExplicit() {
        rewriteRun(java(
                """
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create() {
                        return new RuleBasedIpFilter();
                    }
                }
                """,
                """
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create() {
                        return new RuleBasedIpFilter(true);
                    }
                }
                """));
    }

    @Test
    void preservesRuleEvaluationAndOrder() {
        rewriteRun(java(
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    IpFilterRule rule(int id) { return null; }
                    RuleBasedIpFilter create() {
                        return new RuleBasedIpFilter(rule(1), rule(2), rule(3));
                    }
                }
                """,
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    IpFilterRule rule(int id) { return null; }
                    RuleBasedIpFilter create() {
                        return new RuleBasedIpFilter(true, rule(1), rule(2), rule(3));
                    }
                }
                """));
    }

    @Test
    void preservesMultilineCommentPrefix() {
        rewriteRun(java(
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create(IpFilterRule first, IpFilterRule second) {
                        return new RuleBasedIpFilter(
                                // first rule stays attached to the rule
                                first,
                                second);
                    }
                }
                """,
                """
                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;

                class FilterFactory {
                    RuleBasedIpFilter create(IpFilterRule first, IpFilterRule second) {
                        return new RuleBasedIpFilter(true,
                                // first rule stays attached to the rule
                                first,
                                second);
                    }
                }
                """));
    }

    @Test
    void explicitPolicyAndBusinessLookalikeAreNoop() {
        rewriteRun(
                java("""
                        import io.netty.handler.ipfilter.IpFilterRule;
                        import io.netty.handler.ipfilter.RuleBasedIpFilter;

                        class Explicit {
                            RuleBasedIpFilter create(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(false, rules);
                            }
                            RuleBasedIpFilter accept(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(true, rules);
                            }
                        }
                        """),
                java("""
                        package com.acme;
                        class RuleBasedIpFilter {
                            RuleBasedIpFilter(Object... rules) {}
                        }
                        class Business {
                            Object create(Object a) { return new RuleBasedIpFilter(a); }
                        }
                        """));
    }

    @Test
    void migratesApacheFlumeFixedCommitFixture() {
        // Reduced from Apache Flume RPC @ 579b77c28000e19c3ee10aca1677202dfa951f72 (Apache-2.0).
        rewriteRun(java(
                """
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                package org.apache.flume.rpc.avro.source;

                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;
                import java.util.List;

                class AvroSource {
                    RuleBasedIpFilter create(List<IpFilterRule> rules) {
                        RuleBasedIpFilter filter =
                                new RuleBasedIpFilter(rules.toArray(new IpFilterRule[0]));
                        return filter;
                    }
                }
                """,
                """
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                package org.apache.flume.rpc.avro.source;

                import io.netty.handler.ipfilter.IpFilterRule;
                import io.netty.handler.ipfilter.RuleBasedIpFilter;
                import java.util.List;

                class AvroSource {
                    RuleBasedIpFilter create(List<IpFilterRule> rules) {
                        RuleBasedIpFilter filter =
                                new RuleBasedIpFilter(true, rules.toArray(new IpFilterRule[0]));
                        return filter;
                    }
                }
                """));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import io.netty.handler.ipfilter.*;
                        class C {
                            RuleBasedIpFilter filter(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(rules);
                            }
                        }
                        """,
                        """
                        import io.netty.handler.ipfilter.*;
                        class C {
                            RuleBasedIpFilter filter(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(true, rules);
                            }
                        }
                        """,
                        source -> source.afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "new RuleBasedIpFilter(true")))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.nettyhandler", "org.openrewrite.java").build()
                .activateRecipes(RECIPE);
    }

    private static List<Recipe> flatten(Recipe recipe) {
        List<Recipe> recipes = new ArrayList<>();
        Recipe unwrapped = recipe;
        while (unwrapped instanceof Recipe.DelegatingRecipe delegating) {
            unwrapped = delegating.getDelegate();
        }
        recipes.add(unwrapped);
        for (Recipe child : unwrapped.getRecipeList()) recipes.addAll(flatten(child));
        return recipes;
    }
}
