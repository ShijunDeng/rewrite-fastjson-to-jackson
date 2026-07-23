package com.huawei.clouds.openrewrite.twelvemonkeysjpeg;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class TwelveMonkeysJpegSourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTwelveMonkeysJpegSourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("imageio-jpeg"));
    }

    @Test
    void marksRealKomgaStyleReadWriteAndProviderDiscovery() {
        // gotson/komga@6e8caba7fc1e74030b7665b59ed9cc555e3337e4,
        // BookAnalyzer.kt/ImageConverter.kt: ImageIO.read + ImageIO.write around JPEG content.
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.12.0"), source -> source.path("app/pom.xml")),
                java("""
                        import javax.imageio.ImageIO;
                        import java.awt.image.BufferedImage;
                        import java.io.*;
                        class ImageConverter {
                            byte[] sanitize(byte[] content) throws IOException {
                                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                                ByteArrayOutputStream output = new ByteArrayOutputStream();
                                ImageIO.write(image, "JPEG", output);
                                return output.toByteArray();
                            }
                            void refresh() { ImageIO.scanForPlugins(); }
                        }
                        """, source -> source.path("app/src/main/java/ImageConverter.java").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("JPEG read behavior changed"));
                    assertTrue(printed.contains("JPEG write behavior changed"));
                    assertTrue(printed.contains("ImageIO provider discovery/order"));
                })));
    }

    @Test
    void marksReaderMetadataLifecycleAndMalformedInputBoundaries() {
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.9.3"), source -> source.path("service/pom.xml")),
                java("""
                        import javax.imageio.ImageReader;
                        import javax.imageio.metadata.IIOMetadata;
                        class ReaderPipeline {
                            Object read(ImageReader reader, IIOMetadata metadata) throws Exception {
                                reader.setInput(null);
                                reader.getImageTypes(0);
                                reader.getImageMetadata(0);
                                Object image = reader.read(0);
                                metadata.getAsTree("javax_imageio_jpeg_image_1.0");
                                reader.reset();
                                reader.dispose();
                                return image;
                            }
                        }
                        """, source -> source.path("service/src/ReaderPipeline.java").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("duplicate APP0/APP14"));
                    assertTrue(printed.contains("metadata tree contract"));
                    assertTrue(printed.contains("Reader/writer lifecycle"));
                })));
    }

    @Test
    void marksCmykIccAndNativeMetadataConstruction() {
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.12.0"), source -> source.path("color/pom.xml")),
                java("""
                        import java.awt.color.ICC_ColorSpace;
                        import java.awt.color.ICC_Profile;
                        import javax.imageio.metadata.IIOMetadataNode;
                        class CmykMetadata {
                            IIOMetadataNode adobe(ICC_Profile profile) {
                                ICC_ColorSpace space = new ICC_ColorSpace(profile);
                                return new IIOMetadataNode("app14Adobe");
                            }
                        }
                        """, source -> source.path("color/src/CmykMetadata.java").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("ICC/CMYK path"));
                    assertTrue(printed.contains("JPEG native metadata tree"));
                })));
    }

    @Test
    void marksDirectTwelveMonkeysSpiCoupling() {
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.12.0"), source -> source.path("spi/pom.xml")),
                java("""
                        import com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi;
                        class DirectProvider {
                            Object provider() { return new JPEGImageReaderSpi(); }
                        }
                        """, source -> source.path("spi/src/DirectProvider.java").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("Direct TwelveMonkeys JPEG implementation/SPI coupling"));
                    assertTrue(printed.contains("Direct provider construction"));
                })));
    }

    @Test
    void genericImageIoCallsWithoutNearestDependencyOwnerRemainUntouched() {
        rewriteRun(java("""
                import javax.imageio.ImageIO;
                class PlainJdkImageIo {
                    Object read(java.io.File file) throws Exception { return ImageIO.read(file); }
                }
                """));
    }

    @Test
    void ownerDoesNotLeakIntoSiblingModules() {
        String ownedCode = """
                import javax.imageio.ImageIO;
                class OwnedImages { Object read(java.io.File f) throws Exception { return ImageIO.read(f); } }
                """;
        String siblingCode = """
                import javax.imageio.ImageIO;
                class SiblingImages { Object read(java.io.File f) throws Exception { return ImageIO.read(f); } }
                """;
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.12.0"), source -> source.path("with-jpeg/pom.xml")),
                java(ownedCode, source -> source.path("with-jpeg/src/OwnedImages.java").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains("JPEG read behavior changed")))),
                xml(UpgradeTwelveMonkeysJpegDependencyTest.project(""), source -> source.path("jdk-only/pom.xml")),
                java(siblingCode, source -> source.path("jdk-only/src/SiblingImages.java").afterRecipe(cu ->
                        assertFalse(cu.printAll().contains("JPEG read behavior changed")))));
    }

    @Test
    void nearestNestedBuildOwnerOverridesAnOwningParent() {
        String code = """
                import javax.imageio.ImageIO;
                class NestedImages { Object read(java.io.File f) throws Exception { return ImageIO.read(f); } }
                """;
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.12.0"), source -> source.path("pom.xml")),
                xml(UpgradeTwelveMonkeysJpegDependencyTest.project(""), source -> source.path("nested/pom.xml")),
                java(code, source -> source.path("nested/src/NestedImages.java").afterRecipe(cu ->
                        assertFalse(cu.printAll().contains("JPEG read behavior changed")))));
    }

    @Test
    void generatedSourcesAreSkippedButUnlistedOwnedVersionStillGetsReviewMarkers() {
        String generatedCode = """
                import javax.imageio.ImageIO;
                class GeneratedImages { Object read(java.io.File f) throws Exception { return ImageIO.read(f); } }
                """;
        String sourceCode = """
                import javax.imageio.ImageIO;
                class SourceImages { Object read(java.io.File f) throws Exception { return ImageIO.read(f); } }
                """;
        rewriteRun(
                xml(UpgradeTwelveMonkeysJpegDependencyTest.pom("3.10.1"), source -> source.path("app/pom.xml")),
                java(generatedCode, source -> source.path("app/generated/GeneratedImages.java")),
                java(sourceCode, source -> source.path("app/src/SourceImages.java").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains("JPEG read behavior changed")))));
    }
}
