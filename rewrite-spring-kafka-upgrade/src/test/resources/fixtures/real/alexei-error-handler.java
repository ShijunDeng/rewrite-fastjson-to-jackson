// Adapted from AlexeiZenin/sb-gp-testing at 5b1edf2dbaa45ce16058d11d04e10c5c284019ad.
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

class RealErrorHandlerFixture {
    SeekToCurrentErrorHandler seekToCurrentErrorHandler(long interval, long attempts) {
        return new SeekToCurrentErrorHandler(new FixedBackOff(interval, attempts));
    }
}
