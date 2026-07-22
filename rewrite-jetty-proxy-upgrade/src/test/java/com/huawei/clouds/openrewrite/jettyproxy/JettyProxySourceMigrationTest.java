package com.huawei.clouds.openrewrite.jettyproxy;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class JettyProxySourceMigrationTest implements RewriteTest {
    private static final String TYPES = "com.huawei.clouds.openrewrite.jettyproxy.MigrateDeterministicJetty12Types";
    private static final String RISKS = "com.huawei.clouds.openrewrite.jettyproxy.FindJettyProxy12SourceAndConfigRisks";
    private static final String RECOMMENDED = "com.huawei.clouds.openrewrite.jettyproxy.MigrateJettyProxyTo12_1_8";
    private static final String SERVLET =
            "Jetty 12 moved Servlet proxy classes into separate EE8/EE9/EE10/EE11 artifacts and packages; select the application Servlet namespace before migrating this class and its javax/jakarta signatures";
    private static final String HANDLER =
            "Jetty 12 redesigned Handler processing around boolean handle(Request, Response, Callback); port CONNECT/proxy subclasses asynchronously, complete Callback exactly once, and retest tunnel lifecycle and backpressure";
    private static final String CONTENT =
            "Jetty 12 replaced ContentProvider helpers with Request.Content/RequestContent and demand-pull Content.Source APIs; select the matching content type and retest ownership, demand, release, abort and buffering behavior";
    private static final String LISTENER =
            "Jetty 12 replaced onResponseContentDemanded with onResponseContentSource and a demand-pull model; port the listener and verify chunk demand, release, failure and completion ordering";
    private static final String HTTP_CLIENT =
            "Jetty 12 HttpClient transport/TLS construction changed; configure ClientConnector/transport explicitly and verify executor, TLS, proxy authentication, redirects and lifecycle ownership";
    private static final String MODULE =
            "Jetty 12 proxy.mod now selects the core ProxyHandler while Servlet proxy support is ee8/ee9/ee10/ee11-proxy; choose and rewrite module/XML/init parameters deliberately";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(TYPES)).parser(apiParser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesOfficialSameRoleTypeMoves() {
        rewriteRun(java(
                """
                package example;
                import org.eclipse.jetty.client.api.ContentResponse;
                import org.eclipse.jetty.client.api.Request;
                import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
                import org.eclipse.jetty.client.util.BasicAuthentication;
                import org.eclipse.jetty.proxy.ConnectHandler;
                class ProxyConfiguration {
                    ConnectHandler connect;
                    Request request;
                    ContentResponse response;
                    BasicAuthentication authentication;
                    HttpClientTransportOverHTTP transport;
                }
                """,
                """
                package example;
                import org.eclipse.jetty.client.BasicAuthentication;
                import org.eclipse.jetty.client.ContentResponse;
                import org.eclipse.jetty.client.Request;
                import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
                import org.eclipse.jetty.server.handler.ConnectHandler;

                class ProxyConfiguration {
                    ConnectHandler connect;
                    Request request;
                    ContentResponse response;
                    BasicAuthentication authentication;
                    HttpClientTransportOverHTTP transport;
                }
                """));
    }

    @Test
    void migratesStructuredConfigurationTypeNamesWithBoundaries() {
        rewriteRun(
                properties(
                        "handler=org.eclipse.jetty.proxy.ConnectHandler\nrequest=org.eclipse.jetty.client.api.Request\nlookalike=org.eclipse.jetty.client.api.RequestFactory\nprefixed=com.example.org.eclipse.jetty.proxy.ConnectHandler\n",
                        "handler=org.eclipse.jetty.server.handler.ConnectHandler\nrequest=org.eclipse.jetty.client.Request\nlookalike=org.eclipse.jetty.client.api.RequestFactory\nprefixed=com.example.org.eclipse.jetty.proxy.ConnectHandler\n"),
                yaml(
                        "handler: org.eclipse.jetty.proxy.ConnectHandler\nauth: org.eclipse.jetty.client.util.BasicAuthentication\n",
                        "handler: org.eclipse.jetty.server.handler.ConnectHandler\nauth: org.eclipse.jetty.client.BasicAuthentication\n"),
                xml(
                        "<Configure class=\"org.eclipse.jetty.proxy.ConnectHandler\"><Arg>org.eclipse.jetty.client.api.Response.Listener</Arg></Configure>",
                        "<Configure class=\"org.eclipse.jetty.server.handler.ConnectHandler\"><Arg>org.eclipse.jetty.client.Response.Listener</Arg></Configure>"));
    }

    @Test
    void preservesPomCoordinatesAndUnrelatedText() {
        rewriteRun(
                pomXml("""
                       <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>config</artifactId><version>1</version>
                         <properties><handler>org.eclipse.jetty.proxy.ConnectHandler</handler></properties>
                       </project>
                       """),
                properties("factory=org.eclipse.jetty.client.api.RequestFactory\nplain=ConnectHandler\n"));
    }

    @Test
    void skipsGeneratedAndInstalledSources() {
        rewriteRun(
                properties("handler=org.eclipse.jetty.proxy.ConnectHandler\n", source -> source.path("build/generated/jetty.properties")),
                java("import org.eclipse.jetty.proxy.ConnectHandler; class Generated { ConnectHandler value; }",
                        source -> source.path("target/generated-sources/Generated.java")));
    }

    @Test
    void deterministicTypeMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(recipe(TYPES)).parser(apiParser()).typeValidationOptions(TypeValidation.none())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import org.eclipse.jetty.proxy.ConnectHandler; class App { ConnectHandler value; }",
                        """
                        import org.eclipse.jetty.server.handler.ConnectHandler;

                        class App { ConnectHandler value; }
                        """));
    }

    @Test
    void marksOfficialJetty94ServletProxySubclassAndServletNamespace() {
        // Reduced from Jetty 9.4.45 ProxyServlet at fixed commit 4a0c91c0be53805e3fcffdcdcc9587d5301863db.
        // https://github.com/jetty/jetty.project/blob/4a0c91c0be53805e3fcffdcdcc9587d5301863db/jetty-proxy/src/main/java/org/eclipse/jetty/proxy/ProxyServlet.java
        rewriteRun(JettyProxySourceMigrationTest::riskSpec, java(
                """
                package example;
                import javax.servlet.http.HttpServletRequest;
                import org.eclipse.jetty.proxy.ProxyServlet;
                class CustomProxy extends ProxyServlet {
                    protected String rewriteTarget(HttpServletRequest request) { return "http://backend"; }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(SERVLET));
                    assertTrue(printed.contains("/*~~(" + SERVLET + ")~~>*/import javax.servlet.http.HttpServletRequest;"));
                    assertTrue(printed.contains("/*~~(" + SERVLET + ")~~>*/import org.eclipse.jetty.proxy.ProxyServlet;"));
                })));
    }

    @Test
    void marksConnectHandlerSubclassButNotPlainConstruction() {
        rewriteRun(JettyProxySourceMigrationTest::riskSpec,
                java(
                        """
                        import org.eclipse.jetty.proxy.ConnectHandler;
                        class CustomConnect extends ConnectHandler {}
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(HANDLER)))),
                java(
                        """
                        import org.eclipse.jetty.proxy.ConnectHandler;
                        class Configure { ConnectHandler create() { ConnectHandler h = new ConnectHandler(); h.setConnectTimeout(1000); return h; } }
                        """,
                        source -> source.path("Configure.java")));
    }

    @Test
    void marksOldContentProvidersAndDemandedListenerApi() {
        rewriteRun(JettyProxySourceMigrationTest::riskSpec, java(
                """
                import org.eclipse.jetty.client.api.Request;
                import org.eclipse.jetty.client.util.StringContentProvider;
                class ClientCode {
                    StringContentProvider body = new StringContentProvider("body");
                    void listen(Request request) { request.onResponseContentDemanded(); }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(CONTENT));
                    assertTrue(printed.contains(LISTENER));
                })));
    }

    @Test
    void marksLegacyHttpClientTlsConstructorOnly() {
        rewriteRun(JettyProxySourceMigrationTest::riskSpec, java(
                """
                import org.eclipse.jetty.client.HttpClient;
                class Clients {
                    HttpClient legacy(Object ssl) { return new HttpClient(ssl); }
                    HttpClient current() { return new HttpClient(); }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(HTTP_CLIENT));
                    assertFalse(printed.contains("*/new HttpClient()"));
                })));
    }

    @Test
    void marksExactProxyModulesPropertiesYamlAndServletXmlParameters() {
        rewriteRun(JettyProxySourceMigrationTest::riskSpec,
                properties(
                        "--module=proxy\njetty.proxy.proxyTo=http://backend\nnormal.proxy=value\n",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(MODULE));
                            assertFalse(printed.contains("*/normal.proxy"));
                        })),
                yaml(
                        "--add-module: proxy\njetty.proxy.prefix: /api\nnormal: proxy\n",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(MODULE)))),
                xml(
                        """
                        <web-app><servlet><servlet-name>proxy</servlet-name><servlet-class>org.eclipse.jetty.proxy.ProxyServlet$Transparent</servlet-class>
                          <init-param><param-name>proxyTo</param-name><param-value>http://backend</param-value></init-param>
                          <init-param><param-name>unrelated</param-name><param-value>safe</param-value></init-param>
                        </servlet></web-app>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(SERVLET));
                            assertTrue(printed.contains(MODULE));
                        })));
    }

    @Test
    void ignoresLookalikeTypesMethodsAndConfiguration() {
        rewriteRun(JettyProxySourceMigrationTest::riskSpec,
                java("""
                        class Request { void onResponseContentDemanded() {} }
                        class ProxyServletFactory {}
                        class Use { void call(Request request) { request.onResponseContentDemanded(); } }
                        """),
                properties("jetty.proxyish.proxyTo=http://backend\nmodule=proxy\n--module=proxying\n--add-module=application-proxy\nclass=com.example.org.eclipse.jetty.proxy.ProxyServlet\n"),
                yaml("--module: proxying\n--add-module: application-proxy\nnormal: proxy\n"),
                xml("<bean class=\"org.eclipse.jetty.proxy.ProxyServletFactory\"><property name=\"proxyTo\"/><value>com.example.org.eclipse.jetty.proxy.ProxyServlet</value></bean>"));
    }

    @Test
    void skipsRiskMarkersInGeneratedAndPomFiles() {
        rewriteRun(JettyProxySourceMigrationTest::riskSpec,
                properties("jetty.proxy.proxyTo=http://backend\n", source -> source.path("generated/jetty.properties")),
                pomXml("""
                       <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safe</artifactId><version>1</version>
                         <properties><proxy.class>org.eclipse.jetty.proxy.ProxyServlet</proxy.class></properties>
                       </project>
                       """),
                java("import org.eclipse.jetty.proxy.ProxyServlet; class Generated extends ProxyServlet {}",
                        source -> source.path("build/generated/Generated.java")));
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> {
                    riskSpec(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                java(
                        "import org.eclipse.jetty.client.util.StringContentProvider; class Body { StringContentProvider value; }",
                        "/*~~(%s)~~>*/import org.eclipse.jetty.client.util.StringContentProvider; class Body { StringContentProvider value; }"
                                .formatted(CONTENT)));
    }

    @Test
    void recommendedRecipeUpgradesDependencyMovesConnectHandlerAndMarksServletChoice() {
        rewriteRun(spec -> spec.recipe(recipe(RECOMMENDED)).parser(apiParser()).typeValidationOptions(TypeValidation.none()),
                pomXml(pom("9.4.45.v20220203"), pom("12.1.8")),
                java(
                        """
                        import org.eclipse.jetty.proxy.ConnectHandler;
                        import org.eclipse.jetty.proxy.ProxyServlet;
                        class Application extends ConnectHandler { ProxyServlet servlet; }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("org.eclipse.jetty.server.handler.ConnectHandler"));
                            assertTrue(printed.contains(SERVLET));
                            assertTrue(printed.contains(HANDLER));
                        })));
    }

    private static void riskSpec(RecipeSpec spec) {
        spec.recipe(recipe(RISKS)).parser(apiParser()).typeValidationOptions(TypeValidation.none());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static JavaParser.Builder<?, ?> apiParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package javax.servlet.http; public interface HttpServletRequest {}",
                "package org.eclipse.jetty.proxy; public class ProxyServlet { protected String rewriteTarget(javax.servlet.http.HttpServletRequest r) { return null; } public static class Transparent extends ProxyServlet {} }",
                "package org.eclipse.jetty.proxy; public class ConnectHandler { public void setConnectTimeout(long value) {} }",
                "package org.eclipse.jetty.server.handler; public class ConnectHandler { public void setConnectTimeout(long value) {} }",
                "package org.eclipse.jetty.client.api; public interface Request { Request onResponseContentDemanded(); }",
                "package org.eclipse.jetty.client; public interface Request { Request onResponseContentDemanded(); }",
                "package org.eclipse.jetty.client.api; public interface ContentResponse {}",
                "package org.eclipse.jetty.client; public interface ContentResponse {}",
                "package org.eclipse.jetty.client.api; public interface Response { interface Listener {} }",
                "package org.eclipse.jetty.client; public interface Response { interface Listener {} }",
                "package org.eclipse.jetty.client.util; public class BasicAuthentication {}",
                "package org.eclipse.jetty.client; public class BasicAuthentication {}",
                "package org.eclipse.jetty.client.http; public class HttpClientTransportOverHTTP {}",
                "package org.eclipse.jetty.client.transport; public class HttpClientTransportOverHTTP {}",
                "package org.eclipse.jetty.client.util; public class StringContentProvider { public StringContentProvider(String value) {} }",
                "package org.eclipse.jetty.client; public class HttpClient { public HttpClient() {} public HttpClient(Object value) {} }"
        );
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>proxy</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.eclipse.jetty</groupId><artifactId>jetty-proxy</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }
}
