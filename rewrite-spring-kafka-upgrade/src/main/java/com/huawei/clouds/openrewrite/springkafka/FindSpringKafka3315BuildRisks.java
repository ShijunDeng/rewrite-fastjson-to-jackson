package com.huawei.clouds.openrewrite.springkafka;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locate ownership, no-downgrade, baseline, BOM and companion alignment risks. */
public final class FindSpringKafka3315BuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern JAVA_NUMBER = Pattern.compile("^(?:1\\.)?(\\d+)(?:\\..*)?$");
    static final String OWNER =
            "Spring Kafka 版本由缺失版本、属性共享/遮蔽、父 POM、BOM/platform、version catalog、动态表达式或外部插件控制；" +
            "请修改真实 owner，并确认最终解析为 3.3.15";
    static final String OUTSIDE =
            "该固定 Spring Kafka 版本不在自动白名单 2.8.11、2.9.5 或目标 3.3.15 中；请显式选择支持路径，配方不会扩大范围";
    static final String VARIANT =
            "该 Spring Kafka 声明包含 classifier、非 JAR type 或 Gradle variant；其制品形状不在确定性升级范围内";
    static final String JAVA_BASELINE =
            "Spring Kafka 3.3.15 使用 Java 17 toolchain；请把编译、测试、运行时、容器镜像与 CI 的 Java 基线统一到 17 或更高";
    static final String SPRING_BASELINE =
            "Spring Kafka 3.3.15 发布 POM 使用 Spring Framework 6.2.18；请统一 Spring/Boot BOM，避免 5.x/6.x 混装与二进制不兼容";
    static final String KAFKA_ALIGNMENT =
            "Spring Kafka 3.3.15 发布 POM使用 kafka-clients 3.8.1；请对齐 clients/streams/test/server 家族并验证 broker 协议兼容";
    static final String RETRY_ALIGNMENT =
            "Spring Kafka 3.3.15 发布 POM使用 Spring Retry 2.0.12；请对齐重试 API、BackOff 与 RetryTemplate 行为";
    static final String OBSERVATION_ALIGNMENT =
            "Spring Kafka 3.3.15 发布 POM使用 Micrometer Observation 1.14.14；请统一 observation/core/tracing BOM 并验证标签与追踪";
    static final String JACKSON_ALIGNMENT =
            "Spring Kafka 3.3.15 可选 JSON 集成按 Jackson 2.18.6 构建；请统一 Jackson BOM，避免 serializer/type mapper 链路混装";
    static final String TEST_ALIGNMENT =
            "spring-kafka-test、embedded broker 或 Kafka test/server 依赖必须与 Spring Kafka 3.3.15/Kafka 3.8.1 对齐；" +
            "请验证 KRaft/ZooKeeper、全局 broker、端口与测试生命周期";

    @Override
    public String getDisplayName() {
        return "Find Spring Kafka 3.3.15 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark target conflicts without changing the version text, plus unresolved owners, variants, Java 17, " +
               "Spring 6.2, Kafka 3.8.1, Retry, Micrometer, Jackson and test-family alignment decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringKafkaUpgradeSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Set<String> primaryScopes = primaryScopes(source, ctx);
        if (primaryScopes.isEmpty()) return source;
        MavenProperties properties = mavenProperties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), primaryScopes)) return visited;
                if (SpringKafkaUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    javaProperty(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(FindSpringKafka3315BuildRisks::belowJava17)
                            .isPresent()) {
                    return mark(visited, JAVA_BASELINE);
                }
                if (compilerLevel(getCursor(), visited) &&
                    visited.getValue().map(String::trim).filter(FindSpringKafka3315BuildRisks::belowJava17)
                            .isPresent()) {
                    return mark(visited, JAVA_BASELINE);
                }
                if (!SpringKafkaUpgradeSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String version = resolve(raw, getCursor(), properties);

                if (SpringKafkaUpgradeSupport.GROUP.equals(group) &&
                    SpringKafkaUpgradeSupport.ARTIFACT.equals(artifact)) {
                    if (!SpringKafkaUpgradeSupport.standardJar(visited)) return mark(visited, VARIANT);
                    String message = primaryMessage(version);
                    return message == null ? visited : markVersion(visited, message);
                }
                String message = companionMessage(group, artifact, version);
                return message == null ? visited : markVersion(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx) && !managedSpringKafka(source.printAll())) return source;
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String group = SpringKafkaUpgradeSupport.mapValue(visited, "group");
                String artifact = SpringKafkaUpgradeSupport.mapValue(visited, "name");
                String version = SpringKafkaUpgradeSupport.mapValue(visited, "version");
                boolean variant = SpringKafkaUpgradeSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = SpringKafkaUpgradeSupport.mapValue(map, "group");
                    artifact = SpringKafkaUpgradeSupport.mapValue(map, "name");
                    version = SpringKafkaUpgradeSupport.mapValue(map, "version");
                    variant = SpringKafkaUpgradeSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                return invocationMentionsDynamicPrimary(visited) ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        if (belowJava17Build(source.printAll())) result = mark(result, JAVA_BASELINE);
        if (managedSpringKafka(source.printAll())) result = mark(result, OWNER);
        return result;
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx) && !managedSpringKafka(source.printAll())) return source;
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return direct && invocationMentionsDynamicPrimary(visited) ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        if (belowJava17Build(source.printAll())) result = mark(result, JAVA_BASELINE);
        if (managedSpringKafka(source.printAll())) result = mark(result, OWNER);
        return result;
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (SpringKafkaUpgradeSupport.GROUP.equals(group) &&
            SpringKafkaUpgradeSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            return primaryMessage(version);
        }
        return companionMessage(group, artifact, version);
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String plain = coordinate;
        int at = plain.indexOf('@');
        if (at >= 0) plain = plain.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) return null;
        String version = parts.length == 3 ? parts[2] : null;
        if (SpringKafkaUpgradeSupport.GROUP.equals(parts[0]) &&
            SpringKafkaUpgradeSupport.ARTIFACT.equals(parts[1])) {
            if (parts.length != 3 || at >= 0) return at >= 0 ? VARIANT : OWNER;
            return primaryMessage(version);
        }
        return parts.length == 3 ? companionMessage(parts[0], parts[1], version) : null;
    }

    private static String primaryMessage(String version) {
        if (version == null || version.isBlank()) return OWNER;
        if (SpringKafkaUpgradeSupport.TARGET.equals(version) ||
            SpringKafkaUpgradeSupport.SOURCE_VERSIONS.contains(version)) return null;
        if (SpringKafkaUpgradeSupport.targetConflict(version)) return SpringKafkaUpgradeSupport.TARGET_CONFLICT;
        return SpringKafkaUpgradeSupport.fixedVersion(version) ? OUTSIDE : OWNER;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if (group == null || artifact == null) return null;
        if ("org.springframework.kafka".equals(group) && "spring-kafka-test".equals(artifact) &&
            !SpringKafkaUpgradeSupport.TARGET.equals(version)) return TEST_ALIGNMENT;
        if ("org.springframework.kafka".equals(group) && "spring-kafka-bom".equals(artifact)) return OWNER;
        if ("org.apache.kafka".equals(group) && kafkaFamily(artifact) && !"3.8.1".equals(version)) {
            return artifact.contains("test") || artifact.startsWith("kafka-server") ? TEST_ALIGNMENT : KAFKA_ALIGNMENT;
        }
        if ("org.springframework".equals(group) && artifact.startsWith("spring-") &&
            !"6.2.18".equals(version)) return SPRING_BASELINE;
        if ("org.springframework.boot".equals(group) && artifact.startsWith("spring-boot")) {
            return SPRING_BASELINE;
        }
        if ("org.springframework.retry".equals(group) && "spring-retry".equals(artifact) &&
            !"2.0.12".equals(version)) return RETRY_ALIGNMENT;
        if ("io.micrometer".equals(group) && artifact.startsWith("micrometer-")) {
            String expected = artifact.startsWith("micrometer-tracing") ? "1.4.13" : "1.14.14";
            if (!expected.equals(version)) return OBSERVATION_ALIGNMENT;
        }
        if (group.startsWith("com.fasterxml.jackson") && artifact.startsWith("jackson-") &&
            !"2.18.6".equals(version)) return JACKSON_ALIGNMENT;
        return null;
    }

    private static boolean kafkaFamily(String artifact) {
        return "kafka-clients".equals(artifact) || "kafka-streams".equals(artifact) ||
               "kafka-streams-test-utils".equals(artifact) || artifact.startsWith("kafka-server") ||
               artifact.startsWith("kafka-metadata") || artifact.matches("kafka_\\d+\\.\\d+");
    }

    private static boolean javaProperty(String name) {
        return Set.of("maven.compiler.release", "maven.compiler.source", "maven.compiler.target",
                      "java.version", "jdk.version").contains(name);
    }

    private static boolean compilerLevel(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("release", "source", "target").contains(tag.getName())) return false;
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag owner &&
                "plugin".equals(owner.getName()) &&
                "maven-compiler-plugin".equals(owner.getChildValue("artifactId").orElse(""))) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean belowJava17(String value) {
        Matcher matcher = JAVA_NUMBER.matcher(value);
        return matcher.matches() && new BigInteger(matcher.group(1)).compareTo(BigInteger.valueOf(17)) < 0;
    }

    private static boolean belowJava17Build(String text) {
        return text.matches("(?s).*sourceCompatibility\\s*=\\s*['\"](?:1\\.)?(?:8|9|10|11|12|13|14|15|16)['\"].*") ||
               text.matches("(?s).*targetCompatibility\\s*=\\s*['\"](?:1\\.)?(?:8|9|10|11|12|13|14|15|16)['\"].*") ||
               text.matches("(?s).*JavaVersion\\.VERSION_(?:1_8|9|10|11|12|13|14|15|16).*") ||
               text.matches("(?s).*JavaLanguageVersion\\.of\\((?:8|9|10|11|12|13|14|15|16)\\).*");
    }

    private static boolean managedSpringKafka(String text) {
        return text.contains("libs.spring.kafka") ||
               text.matches("(?s).*platform\\s*\\(\\s*['\"]org\\.springframework\\.kafka:spring-kafka-bom.*");
    }

    private static Set<String> primaryScopes(Xml.Document source, ExecutionContext ctx) {
        Set<String> scopes = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringKafkaUpgradeSupport.isSpringKafkaDependency(getCursor(), visited)) {
                    scopes.add(scope(getCursor()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return Set.copyOf(scopes);
    }

    private static boolean visible(Cursor cursor, Set<String> primaryScopes) {
        String owner = scope(cursor);
        return primaryScopes.contains("ROOT") || primaryScopes.contains(owner) ||
               "ROOT".equals(owner) && !primaryScopes.isEmpty();
    }

    private static MavenProperties mavenProperties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringKafkaUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new MavenProperties(counts, values, profileNames);
    }

    private static String resolve(String raw, Cursor cursor, MavenProperties properties) {
        if (SpringKafkaUpgradeSupport.fixedVersion(raw)) return raw;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return null;
        String currentScope = scope(cursor);
        PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(currentScope) && properties.counts().containsKey(local) ? local : root;
        if (properties.counts().getOrDefault(owner, 0) != 1 ||
            "ROOT".equals(owner.scope()) && properties.profileNames().contains(owner.name())) return null;
        return properties.values().get(owner);
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private static boolean hasPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasPrimary(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringKafkaUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (SpringKafkaUpgradeSupport.GROUP.equals(SpringKafkaUpgradeSupport.mapValue(method, "group")) &&
            SpringKafkaUpgradeSupport.ARTIFACT.equals(SpringKafkaUpgradeSupport.mapValue(method, "name"))) {
            return true;
        }
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                SpringKafkaUpgradeSupport.GROUP.equals(SpringKafkaUpgradeSupport.mapValue(map, "group")) &&
                SpringKafkaUpgradeSupport.ARTIFACT.equals(SpringKafkaUpgradeSupport.mapValue(map, "name"))) {
                return true;
            }
            if (dynamicPrimary(argument)) return true;
        }
        return false;
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(SpringKafkaUpgradeSupport.GROUP + ":" + SpringKafkaUpgradeSupport.ARTIFACT) ||
                coordinate.startsWith(SpringKafkaUpgradeSupport.GROUP + ":" +
                                      SpringKafkaUpgradeSupport.ARTIFACT + ":"));
    }

    private static boolean invocationMentionsDynamicPrimary(J.MethodInvocation method) {
        return method.getArguments().stream().anyMatch(FindSpringKafka3315BuildRisks::dynamicPrimary);
    }

    private static boolean dynamicPrimary(J expression) {
        java.util.List<J> parts;
        if (expression instanceof G.GString string) parts = string.getStrings();
        else if (expression instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.stripLeading().startsWith(
                        SpringKafkaUpgradeSupport.GROUP + ":" + SpringKafkaUpgradeSupport.ARTIFACT + ":"));
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = mark(version, message);
            return marked == version ? dependency : dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> mark(dependency, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record MavenProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values,
                                   Set<String> profileNames) {
    }
}
