package com.huawei.clouds.openrewrite.nettycodechttp2;

import org.openrewrite.Cursor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NettyCodecHttp2Support {
    static final String GROUP = "io.netty";
    static final String ARTIFACT = "netty-codec-http2";
    static final String TARGET = "4.1.136.Final";
    static final Set<String> AUTO_SOURCES = Set.of(
            "4.1.100.Final", "4.1.101.Final", "4.1.108.Final", "4.1.109.Final", "4.1.112.Final",
            "4.1.118.Final", "4.1.124.Final", "4.1.125.Final", "4.1.126.Final", "4.1.128.Final",
            "4.1.129.Final", "4.1.130.Final", "4.1.132.Final");
    static final Set<String> TARGET_CONFLICTS = Set.of("4.2.10.Final", "4.2.12.Final");
    static final Set<String> WORKBOOK_SOURCES = Set.of(
            "4.1.100.Final", "4.1.101.Final", "4.1.108.Final", "4.1.109.Final", "4.1.112.Final",
            "4.1.118.Final", "4.1.124.Final", "4.1.125.Final", "4.1.126.Final", "4.1.128.Final",
            "4.1.129.Final", "4.1.130.Final", "4.1.132.Final", "4.2.10.Final", "4.2.12.Final");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp");
    private static final Pattern NUMERIC_VERSION_PREFIX =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[.-][A-Za-z0-9][A-Za-z0-9.-]*)?$");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".idea", ".m2",
            "node_modules", "vendor", ".cache", "coverage", "reports", "test-results", "tmp", "temp");

    private NettyCodecHttp2Support() {
    }

    static boolean generated(Path sourcePath) {
        Path parent = sourcePath.normalize().getParent();
        if (parent == null) return false;
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) || value.startsWith("generated") ||
                value.startsWith("install")) return true;
        }
        return false;
    }

    /**
     * A higher fixed Netty line is never a valid input to an upgrade-only recipe targeting 4.1.136.Final.
     * Comparing the numeric prefix also covers prerelease labels on a later minor/major line, such as
     * 5.0.0.Alpha1, without pretending that arbitrary Maven ranges or dynamic selectors are fixed versions.
     */
    static boolean targetConflict(String version) {
        if (version == null) return false;
        Matcher matcher = NUMERIC_VERSION_PREFIX.matcher(version);
        if (!matcher.matches()) return false;
        BigInteger major = new BigInteger(matcher.group(1));
        BigInteger minor = new BigInteger(matcher.group(2));
        BigInteger patch = new BigInteger(matcher.group(3));
        int majorOrder = major.compareTo(BigInteger.valueOf(4));
        int minorOrder = minor.compareTo(BigInteger.ONE);
        return majorOrder > 0 ||
               majorOrder == 0 && minorOrder > 0 ||
               majorOrder == 0 && minorOrder == 0 && patch.compareTo(BigInteger.valueOf(136)) > 0;
    }

    static boolean isCodecHttp2Dependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean standardJar(Xml.Tag tag) {
        return tag.getChild("classifier").isEmpty() && "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag d) || !"dependencies".equals(d.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (project(owner) || profile(owner)) return true;
        if (!(owner.getValue() instanceof Xml.Tag dm) || !"dependencyManagement".equals(dm.getName())) return false;
        return project(owner.getParentTreeCursor()) || profile(owner.getParentTreeCursor());
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag p) || !"properties".equals(p.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor owner = properties.getParentTreeCursor();
        return project(owner) || profile(owner);
    }

    private static boolean project(Cursor cursor) {
        return cursor.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName()) &&
               cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean profile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag p && "profiles".equals(p.getName()) &&
               project(profiles.getParentTreeCursor());
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) return false;
        boolean foundRoot = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!foundRoot) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    foundRoot = true;
                } else return false;
            }
        }
        return foundRoot;
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue).filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue).filter(String.class::isInstance)
                .map(String.class::cast).findFirst().orElse(null);
    }

    static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static J.Literal replaceLiteral(J.Literal literal, String value) {
        String old = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(value).withValueSource(source == null ? null : source.replace(old, value));
    }
}
