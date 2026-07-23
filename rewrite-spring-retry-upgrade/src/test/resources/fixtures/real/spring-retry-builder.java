import org.springframework.retry.support.RetryTemplate;

class RetryTemplateBuilderTests {
    RetryTemplate timeoutPolicy() {
        return RetryTemplate.builder().withinMillis(10000).build();
    }
}
