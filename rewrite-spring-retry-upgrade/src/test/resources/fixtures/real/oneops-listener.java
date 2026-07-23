import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;

class CMSClient {
    static class DefaultListenerSupport extends RetryListenerSupport {
        @Override
        public <T, E extends Throwable> void onError(
                RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            context.getRetryCount();
            super.onError(context, callback, throwable);
        }

        @Override
        public <T, E extends Throwable> void close(
                RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        }
    }
}
