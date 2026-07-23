package com.huawei.clouds.openrewrite.nettyhandler;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

class MigrateDeprecatedNettyHandlerApisTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeprecatedNettyHandlerApis())
                .parser(JavaParser.fromJavaVersion().classpath("netty-handler", "netty-buffer"));
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
    void explicitPolicyAndBusinessLookalikeAreNoop() {
        rewriteRun(
                java("""
                        import io.netty.handler.ipfilter.IpFilterRule;
                        import io.netty.handler.ipfilter.RuleBasedIpFilter;

                        class Explicit {
                            RuleBasedIpFilter create(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(false, rules);
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
}
