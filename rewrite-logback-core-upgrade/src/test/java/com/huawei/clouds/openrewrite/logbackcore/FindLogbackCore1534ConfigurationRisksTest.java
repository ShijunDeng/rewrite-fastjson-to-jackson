package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class FindLogbackCore1534ConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindLogbackCore1534ConfigurationRisks());
    }

    static Stream<Arguments> xmlRisks() {
        return Stream.of(
                Arguments.of("<configuration><insertFromJNDI env-entry-name=\"ldap://legacy\" as=\"x\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.JNDI),
                Arguments.of("<configuration><newRule pattern=\"x\" actionClass=\"example.Action\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.JORAN),
                Arguments.of("<configuration><appender name=\"OUTER\"><appender name=\"INNER\"/></appender></configuration>",
                        FindLogbackCore1534ConfigurationRisks.JORAN),
                Arguments.of("<configuration><if condition='property(\"ENV\").equals(\"prod\")'><then/></if></configuration>",
                        FindLogbackCore1534ConfigurationRisks.CONDITIONAL),
                Arguments.of("<configuration scan=\"true\"><root/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.SCAN),
                Arguments.of("<configuration><include url=\"https://config.example/logback.xml\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.SCAN),
                Arguments.of("<configuration><variable name=\"dir\" value=\"logs\\\\tarchive\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.VARIABLE_ESCAPE),
                Arguments.of("<configuration><statusListener class=\"ch.qos.logback.core.status.OnConsoleStatusListener\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.STATUS),
                Arguments.of("<configuration><shutdownHook class=\"ch.qos.logback.core.hook.DefaultShutdownHook\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.LIFECYCLE),
                Arguments.of("<configuration><appender class=\"ch.qos.logback.classic.db.DBAppender\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.DATABASE),
                Arguments.of("<configuration><receiver class=\"ch.qos.logback.classic.net.SocketReceiver\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.SERIALIZATION),
                Arguments.of("<configuration><appender class=\"ch.qos.logback.classic.net.SMTPAppender\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.JAKARTA),
                Arguments.of("<configuration><prudent>true</prudent></configuration>",
                        FindLogbackCore1534ConfigurationRisks.PRUDENT),
                Arguments.of("<configuration><fileNamePattern>archive.%d{yyyy-MM}.xz</fileNamePattern></configuration>",
                        FindLogbackCore1534ConfigurationRisks.XZ),
                Arguments.of("<configuration><checkIncrement>100</checkIncrement></configuration>",
                        FindLogbackCore1534ConfigurationRisks.ROLLING),
                Arguments.of("<configuration><totalSizeCap>2GB</totalSizeCap></configuration>",
                        FindLogbackCore1534ConfigurationRisks.ROLLING),
                Arguments.of("<configuration><rollingPolicy class=\"ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy\"/></configuration>",
                        FindLogbackCore1534ConfigurationRisks.ROLLING),
                Arguments.of("<!DOCTYPE configuration SYSTEM \"https://example.invalid/logback.dtd\"><configuration/>",
                        FindLogbackCore1534ConfigurationRisks.XML_SECURITY)
        );
    }

    @ParameterizedTest(name = "XML risk {1}")
    @MethodSource("xmlRisks")
    void marksExactXmlBoundaries(String before, String message) {
        rewriteRun(xml(before, source -> source.path("src/main/resources/logback.xml")
                .after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    @Test
    void marksJakartaServletBoundaryInWebXml() {
        rewriteRun(xml("""
                <web-app>
                    <servlet>
                        <servlet-name>status</servlet-name>
                        <servlet-class>ch.qos.logback.classic.ViewStatusMessagesServlet</servlet-class>
                    </servlet>
                </web-app>
                """, source -> source.path("src/main/webapp/WEB-INF/web.xml")
                .after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(),
                        FindLogbackCore1534ConfigurationRisks.JAKARTA))));
    }

    @Test
    void marksPropertiesAndYamlIntegrationBoundaries() {
        rewriteRun(
                properties("logback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener\n",
                        source -> source.path("application.properties").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.STATUS))),
                properties("logback.configurationFile=classpath:logback.groovy\n",
                        source -> source.path("groovy.properties").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.GROOVY))),
                properties("logback.disableServletContainerInitializer=true\n",
                        source -> source.path("servlet.properties").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.LIFECYCLE))),
                properties("logging.logback.rollingpolicy.max-file-size=100MB\n",
                        source -> source.path("rolling.properties").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.ROLLING))),
                yaml("""
                        logging.logback.rollingpolicy.max-history: 30
                        """, source -> source.path("application.yml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.ROLLING))));
    }

    @Test
    void marksRemovedGroovyConfigurationTextTemplatesAndPackagingFiles() {
        rewriteRun(
                text("appender(\"STDOUT\", ConsoleAppender) {}\n",
                        source -> source.path("src/main/resources/logback.groovy").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.GROOVY))),
                text("<shutdownHook class=\"ch.qos.logback.core.hook.DelayingShutdownHook\"/>\n",
                        source -> source.path("deploy/logback.xml.j2").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.TEMPLATE))),
                text("Import-Package: ch.qos.logback.core.*\nMulti-Release: true\n",
                        source -> source.path("META-INF/MANIFEST.MF").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.PACKAGING))),
                text("Import-Package: ch.qos.logback.*\n",
                        source -> source.path("bnd.bnd").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.PACKAGING))),
                text("module app { requires ch.qos.logback.core; }\n",
                        source -> source.path("src/main/java/module-info.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.PACKAGING))));
    }

    @Test
    void ignoresSafeLookalikesAndUnownedXml() {
        rewriteRun(
                xml("<root><shutdownHook class=\"ch.qos.logback.core.hook.DelayingShutdownHook\"/></root>",
                        source -> source.path("application.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml("<configuration scan=\"false\"><include resource=\"defaults.xml\"/><prudent>false</prudent></configuration>",
                        source -> source.path("safe-logback.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                properties("logging.pattern.console=%msg%n\n",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                yaml("logging.level.root: INFO\n",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("ch.qos.logback.core.hook.DelayingShutdownHook\n",
                        source -> source.path("notes.txt")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void skipsGeneratedFilesAndMarkersAreIdempotent() {
        rewriteRun(xml("<configuration scan=\"true\"/>",
                source -> source.path("target/classes/logback.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml("<configuration><checkIncrement>100</checkIncrement></configuration>",
                        source -> source.path("logback.xml").after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(),
                                FindLogbackCore1534ConfigurationRisks.ROLLING, 1))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
