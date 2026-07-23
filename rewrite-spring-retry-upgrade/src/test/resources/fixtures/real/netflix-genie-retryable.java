import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

class ArchivedJobServiceImpl {
    @Retryable(
        maxAttemptsExpression = "#{${genie.jobs.archive.retry.get-metadata.num-retries:5}}",
        include = { JobDirectoryManifestNotFoundException.class },
        backoff = @Backoff(
            delayExpression = "#{${genie.jobs.archive.retry.get-metadata.initial-delay:1000}}",
            multiplierExpression = "#{${genie.jobs.archive.retry.get-metadata.multiplier:2.0}}"
        )
    )
    void getArchivedJobMetadata() {
    }

    static class JobDirectoryManifestNotFoundException extends RuntimeException {
    }
}
