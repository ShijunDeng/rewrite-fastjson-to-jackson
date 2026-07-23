// Adapted from kingkh1995/kk-ddd at 7d8e1b8e9355daf3a8259d02abb048428337f176.
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaOperations2;
import org.springframework.kafka.core.KafkaTemplate;

class RealFutureBridgeFixture {
    private final KafkaOperations2<String, Object> operations;

    RealFutureBridgeFixture(KafkaTemplate<String, Object> template) {
        this.operations = template.usingCompletableFuture();
    }

    KafkaOperations2<String, Object> operations() {
        return this.operations;
    }
}
