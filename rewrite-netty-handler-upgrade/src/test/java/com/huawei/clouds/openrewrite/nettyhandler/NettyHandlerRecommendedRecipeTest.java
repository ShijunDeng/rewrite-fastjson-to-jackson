package com.huawei.clouds.openrewrite.nettyhandler;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class NettyHandlerRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.nettyhandler.MigrateNettyHandlerTo4_1_136";

    @Test
    void descriptorComposesOfficialAndCustomCapabilitiesInAuditedOrder() {
        Recipe recipe = recipe();
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.nettyhandler.UpgradeSelectedNettyHandlerDependency",
                "com.huawei.clouds.openrewrite.nettyhandler.FindNettyHandler41136BuildRisks",
                "com.huawei.clouds.openrewrite.nettyhandler.MigrateRuleBasedIpFilterDefault",
                "com.huawei.clouds.openrewrite.nettyhandler.MigrateSslHandlerIsEncrypted",
                "com.huawei.clouds.openrewrite.nettyhandler.FindNettyHandler41136SourceRisks",
                "com.huawei.clouds.openrewrite.nettyhandler.FindNettyHandlerConfigurationRisks"), names);
        for (int index : List.of(2, 3)) {
            assertTrue(flatten(recipe.getRecipeList().get(index)).stream()
                    .anyMatch("org.openrewrite.java.AddLiteralMethodArgument"::equals));
        }
        assertTrue(flatten(recipe.getRecipeList().get(2)).contains(
                "com.huawei.clouds.openrewrite.nettyhandler.NormalizeRuleBasedIpFilterLiteralSpacing"));
    }

    @Test
    void twoOfficialLiteralRecipesWorkTogether() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(targetParser()),
                java("""
                        import io.netty.buffer.ByteBuf;
                        import io.netty.handler.ipfilter.IpFilterRule;
                        import io.netty.handler.ipfilter.RuleBasedIpFilter;
                        import io.netty.handler.ssl.SslHandler;

                        class ProtocolDetection {
                            boolean encrypted(ByteBuf in) {
                                return SslHandler.isEncrypted(in);
                            }
                            RuleBasedIpFilter filter(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(rules);
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("SslHandler.isEncrypted(in, false)"), printed);
                    assertTrue(printed.contains("new RuleBasedIpFilter(true, rules)"), printed);
                    assertTrue(printed.contains(FindNettyHandler41136SourceRisks.TLS_DETECTION), printed);
                    assertTrue(printed.contains(FindNettyHandler41136SourceRisks.IP_FILTER), printed);
                })));
    }

    @Test
    void fixedRewriteNettyRuntimeTreeIsAuditedButBroadUpgradeIsNotComposed() {
        Recipe official = environment().activateRecipes("org.openrewrite.netty.UpgradeNetty_4_1_to_4_2");
        List<String> officialChildren = flatten(official);
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion"), officialChildren.toString());
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.dependencies.ChangeDependency"), officialChildren.toString());
        assertTrue(officialChildren.contains("org.openrewrite.java.ChangeType"), officialChildren.toString());
        assertTrue(officialChildren.contains("org.openrewrite.java.ChangePackage"), officialChildren.toString());
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.netty.EventLoopGroupToMultiThreadIoEventLoopGroupRecipes"),
                officialChildren.toString());

        List<String> selectedTree = flatten(recipe());
        assertFalse(selectedTree.contains("org.openrewrite.netty.UpgradeNetty_4_1_to_4_2"),
                selectedTree.toString());
        assertFalse(selectedTree.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"),
                selectedTree.toString());
        assertFalse(selectedTree.contains("org.openrewrite.java.dependencies.ChangeDependency"),
                selectedTree.toString());
        assertFalse(selectedTree.contains(
                "org.openrewrite.java.netty.EventLoopGroupToMultiThreadIoEventLoopGroupRecipes"),
                selectedTree.toString());
        assertEquals(2, selectedTree.stream()
                .filter("org.openrewrite.java.AddLiteralMethodArgument"::equals).count(),
                selectedTree.toString());
    }

    @Test
    void dependencyUpgradeRunsBeforeBuildAudit() {
        rewriteRun(spec -> spec.recipe(recipe()),
                xml(UpgradeNettyHandlerDependencyTest.pom("4.1.100.Final"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("4.1.136.Final"), printed);
                            assertFalse(printed.contains(FindNettyHandler41136BuildRisks.OUTSIDE), printed);
                            assertFalse(printed.contains(FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX),
                                    printed);
                        })));
    }

    @Test
    void higherBranchRemainsUnchangedAndGetsExactNoDowngradeMarker() {
        rewriteRun(spec -> spec.recipe(recipe()),
                xml(UpgradeNettyHandlerDependencyTest.pom("4.2.10.Final"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("4.2.10.Final"), printed);
                            assertTrue(printed.contains(FindNettyHandler41136BuildRisks.NO_DOWNGRADE_PREFIX),
                                    printed);
                            assertFalse(printed.contains(">4.1.136.Final<"), printed);
                        })));
    }

    @Test
    void recommendedRecipeAlsoAuditsStructuredTlsConfiguration() {
        rewriteRun(spec -> spec.recipe(recipe()),
                properties("io.netty.handler.ssl.openssl.useTasks=true", source ->
                        source.path("application.properties").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindNettyHandlerConfigurationRisks.CONFIG), after.printAll()))));
    }

    @Test
    void officialAndCustomAutosIgnoreGeneratedSources() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(targetParser()),
                java("""
                        import io.netty.buffer.ByteBuf;
                        import io.netty.handler.ipfilter.IpFilterRule;
                        import io.netty.handler.ipfilter.RuleBasedIpFilter;
                        import io.netty.handler.ssl.SslHandler;
                        class GeneratedProtocolDetection {
                            boolean encrypted(ByteBuf in) {
                                return SslHandler.isEncrypted(in);
                            }
                            RuleBasedIpFilter filter(IpFilterRule[] rules) {
                                return new RuleBasedIpFilter(rules);
                            }
                        }
                        """, source -> source.path(
                        "target/generated-sources/netty/GeneratedProtocolDetection.java")));
    }

    @Test
    void completeCompositionIsIdempotent() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(targetParser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeNettyHandlerDependencyTest.pom("4.1.133.Final"),
                        UpgradeNettyHandlerDependencyTest.pom("4.1.136.Final"),
                        source -> source.path("pom.xml")),
                java("""
                        import io.netty.buffer.ByteBuf;
                        import io.netty.handler.ssl.SslHandler;
                        class C { boolean encrypted(ByteBuf in) { return SslHandler.isEncrypted(in); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, occurrences(printed, "SslHandler.isEncrypted(in, false)"));
                    assertEquals(1, occurrences(printed,
                            FindNettyHandler41136SourceRisks.TLS_DETECTION));
                })));
    }

    @Test
    void realDubboFixtureGetsOfficialAutoAndHandlerMarks() {
        // Fixed source: apache/dubbo @ eb1d8abaebdc2ce1e15d6236cf9f9179d34e9082, Apache-2.0.
        rewriteRun(spec -> spec.recipe(recipe()).parser(targetParser()),
                java("""
                        /*
                         * Licensed to the Apache Software Foundation (ASF) under one
                         * or more contributor license agreements.
                         */
                        package org.apache.dubbo.remoting.transport.netty4.ssl;
                        import io.netty.buffer.ByteBuf;
                        import io.netty.channel.ChannelHandlerContext;
                        import io.netty.handler.ssl.*;
                        class SslServerTlsHandler {
                            void decode(ChannelHandlerContext ctx, ByteBuf buf, SslContext sslContext) {
                                if (SslHandler.isEncrypted(buf)) {
                                    ctx.pipeline().addFirst("ssl", sslContext.newHandler(ctx.alloc()));
                                }
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("SslHandler.isEncrypted(buf, false)"), printed);
                    assertTrue(printed.contains(FindNettyHandler41136SourceRisks.TLS_DETECTION), printed);
                    assertTrue(printed.contains(FindNettyHandler41136SourceRisks.TLS_CONTEXT), printed);
                    assertTrue(printed.contains(FindNettyHandler41136SourceRisks.PIPELINE), printed);
                })));
    }

    private static Recipe recipe() {
        return environment().activateRecipes(RECIPE);
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.nettyhandler",
                "org.openrewrite.netty",
                "org.openrewrite.java.netty",
                "org.openrewrite.java.dependencies",
                "org.openrewrite.java").build();
    }

    private static List<String> flatten(Recipe recipe) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        names.add(recipe.getName());
        for (Recipe child : recipe.getRecipeList()) names.addAll(flatten(child));
        return names;
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().classpath(
                "netty-handler", "netty-buffer", "netty-common", "netty-transport", "netty-codec");
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
