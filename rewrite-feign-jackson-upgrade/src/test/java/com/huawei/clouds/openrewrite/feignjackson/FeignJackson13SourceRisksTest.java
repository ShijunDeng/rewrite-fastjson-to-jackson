package com.huawei.clouds.openrewrite.feignjackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FeignJackson13SourceRisksTest implements RewriteTest {
    private static final String DECODER_DEFAULT =
            "Feign Jackson 12+ maps HTTP 404/204 to Util.emptyValueOf(type), while 10/11 did not; also revalidate empty bodies, response charset, unknown properties, Optional/collection defaults, and Jackson 2.18 coercion";
    private static final String DECODER_CUSTOM =
            "This JacksonDecoder receives caller-owned modules/ObjectMapper; verify its Jackson 2.18 module versions, polymorphic typing security, coercion, naming, date/time, unknown-property, charset, and 404/204 behavior";
    private static final String ENCODER_DEFAULT =
            "JacksonEncoder's default mapper still omits nulls and enables indentation, but the managed Jackson line moves to 2.18.3; snapshot JSON property names, inclusion, dates, enums, numbers, records, and escaping";
    private static final String ENCODER_CUSTOM =
            "This JacksonEncoder receives caller-owned modules/ObjectMapper; verify Jackson 2.18 module alignment, inclusion, naming, date/time, enum/number formats, polymorphic typing, escaping, and content type";
    private static final String ITERATOR =
            "JacksonIteratorDecoder changed Iterator.next()/hasNext behavior after 10.4 and maps 404/204 to empty values in 12+; verify direct next(), exhaustion, NoSuchElementException, early close, parse failure, and charset";
    private static final String SUBCLASS =
            "Custom Feign Jackson codec subclass detected; recompile against Feign 13.6/Jackson 2.18.3 and verify decode/encode exception contracts, empty responses, body ownership, mapper configuration, and thread safety";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeignJackson13SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("feign-core", "feign-jackson", "jackson-databind",
                        "jackson-core", "jackson-annotations"));
    }

    @Test
    void marksDefaultDecoderAtConstructor() {
        rewriteRun(java(
                """
                import feign.jackson.JacksonDecoder;
                class Codec { Object decoder() { return new JacksonDecoder(); } }
                """,
                """
                import feign.jackson.JacksonDecoder;
                class Codec { Object decoder() { return /*~~(%s)~~>*/new JacksonDecoder(); } }
                """.formatted(DECODER_DEFAULT)));
    }

    @Test
    void marksObjectMapperDecoderAtConstructor() {
        // Custom mapper form reduced from DependencyTrack/dependency-track at
        // 018760a18e623da18b0dbc36ec07dd84732448f9.
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import feign.jackson.JacksonDecoder;
                class Codec { Object decoder(ObjectMapper mapper) { return new JacksonDecoder(mapper); } }
                """,
                """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import feign.jackson.JacksonDecoder;
                class Codec { Object decoder(ObjectMapper mapper) { return /*~~(%s)~~>*/new JacksonDecoder(mapper); } }
                """.formatted(DECODER_CUSTOM)));
    }

    @Test
    void marksModuleIterableDecoderAtConstructor() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.Module;
                import feign.jackson.JacksonDecoder;
                class Codec { Object decoder(Iterable<Module> modules) { return new JacksonDecoder(modules); } }
                """,
                """
                import com.fasterxml.jackson.databind.Module;
                import feign.jackson.JacksonDecoder;
                class Codec { Object decoder(Iterable<Module> modules) { return /*~~(%s)~~>*/new JacksonDecoder(modules); } }
                """.formatted(DECODER_CUSTOM)));
    }

    @Test
    void marksDefaultAndCustomEncoderDifferently() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import feign.jackson.JacksonEncoder;
                class Codec {
                    Object defaultEncoder() { return new JacksonEncoder(); }
                    Object customEncoder(ObjectMapper mapper) { return new JacksonEncoder(mapper); }
                }
                """,
                """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import feign.jackson.JacksonEncoder;
                class Codec {
                    Object defaultEncoder() { return /*~~(%s)~~>*/new JacksonEncoder(); }
                    Object customEncoder(ObjectMapper mapper) { return /*~~(%s)~~>*/new JacksonEncoder(mapper); }
                }
                """.formatted(ENCODER_DEFAULT, ENCODER_CUSTOM)));
    }

    @Test
    void marksEveryIteratorFactoryOverloadAtCall() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.Module;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import feign.jackson.JacksonIteratorDecoder;
                class Streaming {
                    Object a() { return JacksonIteratorDecoder.create(); }
                    Object b(Iterable<Module> modules) { return JacksonIteratorDecoder.create(modules); }
                    Object c(ObjectMapper mapper) { return JacksonIteratorDecoder.create(mapper); }
                }
                """,
                """
                import com.fasterxml.jackson.databind.Module;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import feign.jackson.JacksonIteratorDecoder;
                class Streaming {
                    Object a() { return /*~~(%1$s)~~>*/JacksonIteratorDecoder.create(); }
                    Object b(Iterable<Module> modules) { return /*~~(%1$s)~~>*/JacksonIteratorDecoder.create(modules); }
                    Object c(ObjectMapper mapper) { return /*~~(%1$s)~~>*/JacksonIteratorDecoder.create(mapper); }
                }
                """.formatted(ITERATOR)));
    }

    @Test
    void marksCodecSubclassAtExactExtendsType() {
        rewriteRun(java(
                """
                import feign.jackson.JacksonDecoder;
                import feign.jackson.JacksonEncoder;
                class DecoderExtension extends JacksonDecoder { }
                class EncoderExtension extends JacksonEncoder { }
                """,
                """
                import feign.jackson.JacksonDecoder;
                import feign.jackson.JacksonEncoder;
                class DecoderExtension extends /*~~(%1$s)~~>*/JacksonDecoder { }
                class EncoderExtension extends /*~~(%1$s)~~>*/JacksonEncoder { }
                """.formatted(SUBCLASS)));
    }

    @Test
    void sameNamedApplicationCodeIsNoop() {
        rewriteRun(java("""
                class JacksonDecoder { }
                class JacksonEncoder { }
                class JacksonIteratorDecoder { static Object create() { return null; } }
                class Use { Object x() { new JacksonDecoder(); new JacksonEncoder(); return JacksonIteratorDecoder.create(); } }
                """));
    }

    @Test
    void ordinaryObjectMapperUseWithoutFeignCodecIsNoop() {
        rewriteRun(java("""
                import com.fasterxml.jackson.databind.ObjectMapper;
                class Json { ObjectMapper mapper() { return new ObjectMapper(); } }
                """));
    }

    @Test
    void generatedInstallationAndCachesAreNoop() {
        rewriteRun(
                java("import feign.jackson.JacksonDecoder; class GeneratedCodec { Object x() { return new JacksonDecoder(); } }", source -> source.path("generated-test/GeneratedCodec.java")),
                java("import feign.jackson.JacksonDecoder; class InstalledCodec { Object x() { return new JacksonDecoder(); } }", source -> source.path("installations/lib/InstalledCodec.java")),
                java("import feign.jackson.JacksonDecoder; class GradleCodec { Object x() { return new JacksonDecoder(); } }", source -> source.path(".gradle/cache/GradleCodec.java")),
                java("import feign.jackson.JacksonDecoder; class TargetCodec { Object x() { return new JacksonDecoder(); } }", source -> source.path("target/generated-sources/TargetCodec.java")));
    }

    @Test
    void leafInstallJavaIsStillMarked() {
        rewriteRun(java(
                "import feign.jackson.JacksonDecoder; class install { Object x() { return new JacksonDecoder(); } }",
                "import feign.jackson.JacksonDecoder; class install { Object x() { return /*~~(%s)~~>*/new JacksonDecoder(); } }".formatted(DECODER_DEFAULT),
                source -> source.path("install.java")));
    }
}
