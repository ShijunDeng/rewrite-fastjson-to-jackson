// Adapted from eugenp/tutorials at 5e4114a9482d68b6766ca738c087f0f9a87a7bd2.
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;

class RealRetryTopicFixture {
    @RetryableTopic(attempts = "1", dltStrategy = DltStrategy.NO_DLT)
    @KafkaListener(topics = "payments-no-dlt", groupId = "payments")
    void handlePayment(String payment) {
    }

    @DltHandler
    void handleDltPayment(String payment) {
    }
}
