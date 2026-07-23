// Adapted from eugenp/tutorials at 5e4114a9482d68b6766ca738c087f0f9a87a7bd2.
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.mapping.Jackson2JavaTypeMapper;
import org.springframework.kafka.support.serializer.JsonDeserializer;

class RealJsonFixture {
    JsonDeserializer<Object> deserializer() {
        return new JsonDeserializer<>(Object.class);
    }

    DefaultJackson2JavaTypeMapper typeMapper() {
        DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper();
        mapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
        mapper.addTrustedPackages("com.baeldung.spring.kafka");
        return mapper;
    }
}
