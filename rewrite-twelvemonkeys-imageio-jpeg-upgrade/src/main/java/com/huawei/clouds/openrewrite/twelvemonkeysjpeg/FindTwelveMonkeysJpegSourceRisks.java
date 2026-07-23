package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Marks exact ImageIO call sites whose behavior is affected by the JPEG provider upgrade. */
public final class FindTwelveMonkeysJpegSourceRisks extends ScanningRecipe<FindTwelveMonkeysJpegSourceRisks.Projects> {
    private static final MethodMatcher IMAGE_IO = new MethodMatcher("javax.imageio.ImageIO *(..)");
    private static final MethodMatcher IMAGE_READER = new MethodMatcher("javax.imageio.ImageReader *(..)", true);
    private static final MethodMatcher IMAGE_WRITER = new MethodMatcher("javax.imageio.ImageWriter *(..)", true);
    private static final MethodMatcher METADATA = new MethodMatcher("javax.imageio.metadata.IIOMetadata *(..)", true);
    private static final MethodMatcher REGISTRY = new MethodMatcher("javax.imageio.spi.ServiceRegistry *(..)", true);
    private static final Set<String> READ_METHODS = Set.of(
            "read", "readAll", "readAsRenderedImage", "readRaster", "setInput", "getImageTypes", "getRawImageType",
            "getImageMetadata", "getStreamMetadata", "getNumImages", "getWidth", "getHeight");
    private static final Set<String> WRITE_METHODS = Set.of(
            "write", "writeToSequence", "prepareWriteSequence", "setOutput", "getDefaultImageMetadata",
            "convertImageMetadata", "getDefaultStreamMetadata", "convertStreamMetadata");
    private static final Set<String> DISCOVERY_METHODS = Set.of(
            "scanForPlugins", "getImageReaders", "getImageReadersByFormatName", "getImageReadersByMIMEType",
            "getImageReadersBySuffix", "getImageWriters", "getImageWritersByFormatName", "getImageWritersByMIMEType",
            "getImageWritersBySuffix", "registerServiceProvider", "deregisterServiceProvider", "setOrdering", "unsetOrdering");
    private static final Set<String> METADATA_NODES = Set.of(
            "javax_imageio_jpeg_image_1.0", "app14Adobe", "unknown", "COM", "ICC_PROFILE");

    static final class Projects {
        private final Map<Path, Boolean> roots = new HashMap<>();

        void record(Path sourcePath, boolean ownsTarget) {
            Path parent = sourcePath.getParent();
            roots.merge(parent == null ? Path.of("") : parent, ownsTarget, Boolean::logicalOr);
        }

        boolean owns(Path sourcePath) {
            Path nearest = null;
            boolean owned = false;
            for (Map.Entry<Path, Boolean> entry : roots.entrySet()) {
                Path root = entry.getKey();
                if ((root.toString().isEmpty() || sourcePath.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                    owned = entry.getValue();
                }
            }
            return nearest != null && owned;
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }
    }

    @Override
    public String getDisplayName() {
        return "Find TwelveMonkeys JPEG 3.12 source compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Within the nearest project that directly owns imageio-jpeg, marks provider discovery, read/write, CMYK/ICC, JPEG metadata-tree, malformed-input, lifecycle, and direct SPI coupling decisions.";
    }

    @Override
    public Projects getInitialValue(ExecutionContext ctx) {
        return new Projects();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Projects projects) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || TwelveMonkeysJpegSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) projects.record(source.getSourcePath(), ownsMaven(document, ctx));
                else if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) projects.record(source.getSourcePath(), ownsGroovy(groovy, ctx));
                else if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) projects.record(source.getSourcePath(), ownsKotlin(kotlin, ctx));
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Projects projects) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (TwelveMonkeysJpegSupport.generated(cu.getSourcePath()) || !projects.owns(cu.getSourcePath())) return cu;
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
                J.Import visited = super.visitImport(import_, ctx);
                String name = visited.getQualid().printTrimmed(getCursor());
                if (name.startsWith("com.twelvemonkeys.imageio.plugins.jpeg.")) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "Direct TwelveMonkeys JPEG implementation/SPI coupling: verify provider registration order, delegate availability, module exports, and dispose/reset lifecycle on 3.12.0");
                }
                if (name.equals("java.awt.color.ICC_ColorSpace") || name.equals("java.awt.color.ICC_Profile")) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "ICC/CMYK path: 3.11+ preserves existing COM/unknown metadata while replacing embedded ICC chunks; verify profile identity, inversion, APP14 transform, and round-trip color tolerances");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isAssignableTo("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi", visited.getType()) ||
                    TypeUtils.isAssignableTo("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageWriterSpi", visited.getType()) ||
                    TypeUtils.isAssignableTo("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReader", visited.getType()) ||
                    TypeUtils.isAssignableTo("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageWriter", visited.getType())) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "Direct provider construction bypasses normal IIORegistry selection; verify the JDK delegate is registered, listener forwarding works, and reader/writer disposal is exactly once");
                }
                if (TypeUtils.isAssignableTo("javax.imageio.metadata.IIOMetadataNode", visited.getType()) &&
                    visited.getArguments().stream().anyMatch(FindTwelveMonkeysJpegSourceRisks::metadataLiteral)) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "JPEG native metadata tree: 3.11+ CMYK writes retain COM/unknown nodes, replace ICC_PROFILE chunks, and force APP14 transform=0; assert the complete native tree before and after write");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                String name = visited.getSimpleName();
                if ((IMAGE_IO.matches(visited) || REGISTRY.matches(visited)) && DISCOVERY_METHODS.contains(name)) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "ImageIO provider discovery/order: keep META-INF/services and MR-JAR resources through packaging, scan the intended class loader, and assert TwelveMonkeys precedes the JDK JPEG provider");
                }
                if ((IMAGE_IO.matches(visited) || IMAGE_READER.matches(visited)) && READ_METHODS.contains(name)) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "JPEG read behavior changed for duplicate APP0/APP14 segments and malformed Huffman tables; test last-marker color detection, CMYK/YCCK/ICC conversion, warnings, null/exception contracts, and hostile inputs");
                }
                if ((IMAGE_IO.matches(visited) || IMAGE_WRITER.matches(visited)) && WRITE_METHODS.contains(name)) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "JPEG write behavior changed for CMYK metadata preservation; compare APP14/ICC/COM/unknown segments, destination type restoration, quality/subsampling, and pixel color tolerance after round trip");
                }
                if (METADATA.matches(visited) && Set.of("getAsTree", "mergeTree", "setFromTree", "reset").contains(name)) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "JPEG metadata tree contract participates in the 3.12 CMYK writer path; verify native format names, node order, cloned/reparented nodes, ICC replacement, comments, and invalid-tree failures");
                }
                if ((IMAGE_READER.matches(visited) || IMAGE_WRITER.matches(visited)) && Set.of("dispose", "reset", "abort").contains(name)) {
                    return TwelveMonkeysJpegSupport.mark(visited,
                            "Reader/writer lifecycle: verify delegate listener cleanup, stream ownership, abort/reset/dispose ordering, and pooled instance reuse after provider upgrade");
                }
                return visited;
            }
        };
    }

    private static boolean metadataLiteral(org.openrewrite.java.tree.Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value && METADATA_NODES.contains(value);
    }

    private static boolean ownsMaven(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TwelveMonkeysJpegSupport.isJpegDependency(getCursor(), visited) && TwelveMonkeysJpegSupport.standardJar(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static boolean ownsGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (targetGradleDependency(method, getCursor())) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean ownsKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (targetGradleDependency(method, getCursor())) found[0] = true;
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean targetGradleDependency(J.MethodInvocation method, org.openrewrite.Cursor cursor) {
        if (!TwelveMonkeysJpegSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return false;
        if (TwelveMonkeysJpegSupport.GROUP.equals(TwelveMonkeysJpegSupport.mapValue(method, "group")) &&
            TwelveMonkeysJpegSupport.ARTIFACT.equals(TwelveMonkeysJpegSupport.mapValue(method, "name")) &&
            !TwelveMonkeysJpegSupport.hasVariant(method)) return true;
        if (method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof String coordinate) {
            String prefix = TwelveMonkeysJpegSupport.GROUP + ":" + TwelveMonkeysJpegSupport.ARTIFACT + ":";
            return coordinate.startsWith(prefix) && !coordinate.substring(prefix.length()).contains(":") && !coordinate.contains("@");
        }
        return false;
    }
}
