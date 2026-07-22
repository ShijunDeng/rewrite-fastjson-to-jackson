package com.huawei.clouds.openrewrite.jakartaservlet;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class JakartaServletResourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJakartaServlet61ResourceRisks());
    }

    @Test
    void marksLegacySchemaErrorPageAndErrorFilterDispatch() {
        rewriteRun(xml(
                """
                <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
                         version="4.0">
                  <error-page><error-code>500</error-code><location>/error</location></error-page>
                  <filter-mapping><filter-name>audit</filter-name><url-pattern>/*</url-pattern><dispatcher>ERROR</dispatcher></filter-mapping>
                </web-app>
                """,
                """
                <!--~~(Select the Servlet 6.1 Jakarta descriptor schema with the target container; do not change only version/schemaLocation while removed elements or old ecosystem classes remain)~~>--><web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
                         version="4.0">
                  <!--~~(Servlet 6.1 error dispatches execute as GET; read original method/query from RequestDispatcher.ERROR_METHOD and ERROR_QUERY_STRING and retest auth, CSRF and caching)~~>--><error-page><error-code>500</error-code><location>/error</location></error-page>
                  <filter-mapping><filter-name>audit</filter-name><url-pattern>/*</url-pattern><!--~~(ERROR filter dispatch now observes an HTTP GET in Servlet 6.1; audit method-sensitive filter behavior)~~>--><dispatcher>ERROR</dispatcher></filter-mapping>
                </web-app>
                """,
                source -> source.path("src/main/webapp/WEB-INF/web.xml")));
    }

    @Test
    void marksMetadataCompleteOnCurrentSchema() {
        rewriteRun(xml(
                """
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
                         version="6.1" metadata-complete="true"/>
                """,
                """
                <!--~~(metadata-complete disables annotation discovery; verify @WebServlet/@WebFilter/@WebListener and initializer registration after the Jakarta migration)~~>--><web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
                         version="6.1" metadata-complete="true"/>
                """,
                source -> source.path("src/main/webapp/WEB-INF/web.xml")));
    }

    @Test
    void marksLegacyServletContainerInitializerServicePath() {
        rewriteRun(text(
                "com.example.LegacyInitializer\n",
                "~~(Rename this service file to META-INF/services/jakarta.servlet.ServletContainerInitializer, preserve provider ordering and verify duplicate target resources before merging)~~>com.example.LegacyInitializer\n",
                source -> source.path("src/main/resources/META-INF/services/javax.servlet.ServletContainerInitializer")));
    }

    @Test
    void marksStringConfigurationAndOsgiMetadata() {
        rewriteRun(
                text(
                        "scanner.contract=javax.servlet.ServletContainerInitializer\n",
                        "~~(String/configuration metadata still names javax.servlet; identify its reflection, OSGi, scanner or framework owner before changing it)~~>scanner.contract=javax.servlet.ServletContainerInitializer\n",
                        source -> source.path("src/main/resources/application.properties")),
                text(
                        "Import-Package: javax.servlet;version=\"[4,5)\"\n",
                        "~~(String/configuration metadata still names javax.servlet; identify its reflection, OSGi, scanner or framework owner before changing it)~~>Import-Package: javax.servlet;version=\"[4,5)\"\n",
                        source -> source.path("src/main/resources/META-INF/MANIFEST.MF")));
    }

    @Test
    void marksSecurityManagerStartupConfiguration() {
        rewriteRun(text(
                "JAVA_OPTS=-Djava.security.manager -Djava.security.policy=/opt/app.policy\n",
                "~~(Servlet 6.1 removed SecurityManager requirements; replace policy/startup flags with process, container and platform isolation controls)~~>JAVA_OPTS=-Djava.security.manager -Djava.security.policy=/opt/app.policy\n",
                source -> source.path("conf/jvm.options")));
    }

    @Test
    void preservesCurrentDescriptorWithoutBehaviorSensitiveElements() {
        rewriteRun(xml(
                """
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
                         version="6.1">
                  <servlet><servlet-name>status</servlet-name><servlet-class>com.example.StatusServlet</servlet-class></servlet>
                </web-app>
                """,
                source -> source.path("src/main/webapp/WEB-INF/web.xml")));
    }
}
