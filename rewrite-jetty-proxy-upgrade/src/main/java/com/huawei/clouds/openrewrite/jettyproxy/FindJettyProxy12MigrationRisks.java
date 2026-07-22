package com.huawei.clouds.openrewrite.jettyproxy;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Type- and format-aware markers for Jetty 9.4 Proxy to Jetty 12.1 migration decisions. */
public final class FindJettyProxy12MigrationRisks extends Recipe {
    private static final Set<String> SERVLET_PROXY_TYPES = Set.of(
            "org.eclipse.jetty.proxy.AbstractProxyServlet", "org.eclipse.jetty.proxy.ProxyServlet",
            "org.eclipse.jetty.proxy.AsyncProxyServlet", "org.eclipse.jetty.proxy.AsyncMiddleManServlet",
            "org.eclipse.jetty.proxy.BalancerServlet", "org.eclipse.jetty.proxy.AfterContentTransformer"
    );
    private static final Set<String> OLD_CONTENT_TYPES = Set.of(
            "org.eclipse.jetty.client.api.ContentProvider",
            "org.eclipse.jetty.client.util.AbstractTypedContentProvider",
            "org.eclipse.jetty.client.util.ByteBufferContentProvider",
            "org.eclipse.jetty.client.util.BytesContentProvider",
            "org.eclipse.jetty.client.util.DeferredContentProvider",
            "org.eclipse.jetty.client.util.FormContentProvider",
            "org.eclipse.jetty.client.util.FutureResponseListener",
            "org.eclipse.jetty.client.util.InputStreamContentProvider",
            "org.eclipse.jetty.client.util.MultiPartContentProvider",
            "org.eclipse.jetty.client.util.OutputStreamContentProvider",
            "org.eclipse.jetty.client.util.PathContentProvider",
            "org.eclipse.jetty.client.util.StringContentProvider"
    );
    private static final Set<String> PROXY_INIT_PARAMETERS = Set.of(
            "proxyTo", "prefix", "maxThreads", "maxConnections", "idleTimeout", "timeout",
            "requestBufferSize", "responseBufferSize", "hostHeader"
    );
    private static final Set<String> PROXY_MODULES = Set.of(
            "proxy", "ee8-proxy", "ee9-proxy", "ee10-proxy", "ee11-proxy");
    private static final Pattern MODULE_ARGUMENT = Pattern.compile(
            "(?i)(?:^|\\s)--(?:add-)?module\\s*=\\s*([^\\s\"']+)");
    private static final String SERVLET_MESSAGE =
            "Jetty 12 moved Servlet proxy classes into separate EE8/EE9/EE10/EE11 artifacts and packages; select the application Servlet namespace before migrating this class and its javax/jakarta signatures";
    private static final String HANDLER_MESSAGE =
            "Jetty 12 redesigned Handler processing around boolean handle(Request, Response, Callback); port CONNECT/proxy subclasses asynchronously, complete Callback exactly once, and retest tunnel lifecycle and backpressure";
    private static final String CONTENT_MESSAGE =
            "Jetty 12 replaced ContentProvider helpers with Request.Content/RequestContent and demand-pull Content.Source APIs; select the matching content type and retest ownership, demand, release, abort and buffering behavior";
    private static final String LISTENER_MESSAGE =
            "Jetty 12 replaced onResponseContentDemanded with onResponseContentSource and a demand-pull model; port the listener and verify chunk demand, release, failure and completion ordering";
    private static final String HTTP_CLIENT_MESSAGE =
            "Jetty 12 HttpClient transport/TLS construction changed; configure ClientConnector/transport explicitly and verify executor, TLS, proxy authentication, redirects and lifecycle ownership";
    private static final String MODULE_MESSAGE =
            "Jetty 12 proxy.mod now selects the core ProxyHandler while Servlet proxy support is ee8/ee9/ee10/ee11-proxy; choose and rewrite module/XML/init parameters deliberately";

    @Override
    public String getDisplayName() {
        return "Find Jetty Proxy 12.1 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Servlet proxy types, redesigned CONNECT handlers, client content/listener APIs, HttpClient construction, " +
               "and exact proxy module or structured configuration that requires an explicit Jetty 12 design choice.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !UpgradeSelectedJettyProxyDependency
                        .isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) return markJava(java, ctx);
                if (tree instanceof Properties.File properties) return markProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return markYaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && source.getSourcePath().getFileName() != null &&
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString())) return markXml(xml, ctx);
                return tree;
            }
        };
    }

    private static J.CompilationUnit markJava(J.CompilationUnit source, ExecutionContext ctx) {
        boolean servletProxyFile = source.getImports().stream()
                .map(anImport -> anImport.getQualid().printTrimmed())
                .anyMatch(FindJettyProxy12MigrationRisks::isServletProxyType);
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext p) {
                J.Import visited = super.visitImport(anImport, p);
                String name = visited.getQualid().printTrimmed(getCursor());
                if (isServletProxyType(name)) return mark(visited, SERVLET_MESSAGE);
                if (isOldContentType(name)) return mark(visited, CONTENT_MESSAGE);
                if (name.startsWith("org.eclipse.jetty.proxy.ProxyConnection")) return mark(visited, HANDLER_MESSAGE);
                if (servletProxyFile && (name.startsWith("javax.servlet.") || name.startsWith("jakarta.servlet."))) {
                    return mark(visited, SERVLET_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext p) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, p);
                if (isAssignableToAny(SERVLET_PROXY_TYPES, visited.getType())) return mark(visited, SERVLET_MESSAGE);
                if (TypeUtils.isAssignableTo("org.eclipse.jetty.server.handler.ConnectHandler", visited.getType()) ||
                    TypeUtils.isAssignableTo("org.eclipse.jetty.proxy.ConnectHandler", visited.getType())) {
                    return mark(visited, HANDLER_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                String owner = owner(visited.getMethodType());
                if (("org.eclipse.jetty.client.Request".equals(owner) || "org.eclipse.jetty.client.api.Request".equals(owner)) &&
                    "onResponseContentDemanded".equals(visited.getSimpleName())) return mark(visited, LISTENER_MESSAGE);
                if (isServletProxyType(owner)) return mark(visited, SERVLET_MESSAGE);
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext p) {
                J.NewClass visited = super.visitNewClass(newClass, p);
                String type = fqn(visited.getType());
                if (isServletProxyType(type)) return mark(visited, SERVLET_MESSAGE);
                if (isOldContentType(type)) return mark(visited, CONTENT_MESSAGE);
                if ("org.eclipse.jetty.client.HttpClient".equals(type) && visited.getArguments().stream()
                        .anyMatch(argument -> !(argument instanceof J.Empty))) {
                    return mark(visited, HTTP_CLIENT_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                J.Literal visited = super.visitLiteral(literal, p);
                if (!(visited.getValue() instanceof String value)) return visited;
                String risk = riskForText(value);
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static Properties.File markProperties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry visited = super.visitEntry(entry, p);
                String key = visited.getKey().toLowerCase(Locale.ROOT);
                String value = visited.getValue().getText();
                if (key.startsWith("jetty.proxy.") ||
                    (Set.of("--module", "--add-module").contains(key) && containsProxyModuleList(value))) {
                    return mark(visited, MODULE_MESSAGE);
                }
                String risk = riskForText(value);
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents markYaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                String key = visited.getKey().getValue();
                String value = visited.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
                if (key.toLowerCase(Locale.ROOT).startsWith("jetty.proxy.") ||
                    (Set.of("--module", "--add-module").contains(key) && containsProxyModuleList(value))) {
                    return mark(visited, MODULE_MESSAGE);
                }
                String risk = riskForText(value);
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document markXml(Xml.Document source, ExecutionContext ctx) {
        boolean servletProxyConfiguration = SERVLET_PROXY_TYPES.stream().anyMatch(type -> source.printAll().contains(type));
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (servletProxyConfiguration && "init-param".equals(visited.getName()) &&
                    visited.getChildValue("param-name").filter(PROXY_INIT_PARAMETERS::contains).isPresent()) {
                    return mark(visited, MODULE_MESSAGE);
                }
                return visited;
            }

            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                Xml.CharData visited = super.visitCharData(charData, p);
                String risk = riskForText(visited.getText());
                return risk == null ? visited : mark(visited, risk);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                Xml.Attribute visited = super.visitAttribute(attribute, p);
                String risk = riskForText(visited.getValueAsString());
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static String riskForText(String value) {
        for (String type : SERVLET_PROXY_TYPES) if (containsType(value, type)) return SERVLET_MESSAGE;
        for (String type : OLD_CONTENT_TYPES) if (containsType(value, type)) return CONTENT_MESSAGE;
        if (containsType(value, "org.eclipse.jetty.proxy.ProxyConnection")) return HANDLER_MESSAGE;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (containsProxyModuleArgument(value) || lower.startsWith("jetty.proxy.")) return MODULE_MESSAGE;
        return null;
    }

    private static boolean containsType(String value, String type) {
        for (int index = value.indexOf(type); index >= 0; index = value.indexOf(type, index + 1)) {
            int end = index + type.length();
            boolean startBoundary = index == 0 ||
                    (!Character.isJavaIdentifierPart(value.charAt(index - 1)) &&
                     value.charAt(index - 1) != '.' && value.charAt(index - 1) != '$');
            boolean endBoundary = end == value.length() || !Character.isJavaIdentifierPart(value.charAt(end)) ||
                    value.charAt(end) == '$';
            if (startBoundary && endBoundary) return true;
        }
        return false;
    }

    private static boolean containsProxyModuleArgument(String value) {
        Matcher matcher = MODULE_ARGUMENT.matcher(value);
        while (matcher.find()) if (containsProxyModuleList(matcher.group(1))) return true;
        return false;
    }

    private static boolean containsProxyModuleList(String value) {
        for (String module : value.split(",")) {
            if (PROXY_MODULES.contains(module.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean isServletProxyType(String type) {
        return SERVLET_PROXY_TYPES.stream().anyMatch(candidate -> type.equals(candidate) ||
                type.startsWith(candidate + ".") || type.startsWith(candidate + "$"));
    }

    private static boolean isOldContentType(String type) {
        return OLD_CONTENT_TYPES.stream().anyMatch(candidate -> type.equals(candidate) ||
                type.startsWith(candidate + ".") || type.startsWith(candidate + "$"));
    }

    private static boolean isAssignableToAny(Set<String> targets, JavaType type) {
        return targets.stream().anyMatch(target -> TypeUtils.isAssignableTo(target, type));
    }

    private static String owner(JavaType.Method method) {
        return method == null ? "" : fqn(method.getDeclaringType());
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
