// Adapted from dsyer/dist-tx at 88bc07b9c0f2a100d67fec2d283d883d908013fa.
import org.springframework.kafka.listener.DefaultAfterRollbackProcessor;
import org.springframework.kafka.transaction.ChainedKafkaTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.backoff.FixedBackOff;

class RealTransactionFixture {
    Object chained(KafkaTransactionManager<Object, Object> kafka) {
        return new ChainedKafkaTransactionManager<>(kafka);
    }

    Object rollbackProcessor() {
        DefaultAfterRollbackProcessor<Object, Object> processor =
                new DefaultAfterRollbackProcessor<>(new FixedBackOff(0L, 0L));
        processor.setCommitRecovered(false);
        return processor;
    }
}
