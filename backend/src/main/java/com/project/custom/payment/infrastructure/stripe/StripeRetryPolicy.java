package com.project.custom.payment.infrastructure.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * Retries of Stripe calls (research R-13, contracts/stripe-webhook.md §1): at most 2 retries after 0.5 s and
 * 1 s, only for connection errors, timeouts, {@code 5xx} and {@code 429}. The SDK itself never retries
 * ({@code maxNetworkRetries = 0}), so every attempt is visible here. The caller passes the same idempotency
 * key to every attempt. The constants are part of the integration contract, not configuration.
 */
class StripeRetryPolicy {

    /** Delays before the 1st and 2nd retry; their count is the maximum number of retries. */
    static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMillis(500), Duration.ofSeconds(1));

    private static final Logger log = LoggerFactory.getLogger(StripeRetryPolicy.class);

    private final Sleeper sleeper;

    StripeRetryPolicy(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * @throws StripeUnavailableException when all attempts failed with a retryable error
     * @throws StripeException            a non-retryable error of the first attempt that got it
     */
    <T> T execute(StripeOperation operation, StripeCall<T> call) throws StripeException {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.execute();
            } catch (StripeException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                CallOutcome outcome = outcomeOf(exception);
                if (attempt > RETRY_DELAYS.size()) {
                    log.warn("Stripe {} unavailable after {} attempts, last outcome {}", operation, attempt, outcome);
                    throw new StripeUnavailableException(operation, outcome);
                }
                log.warn("Stripe {} attempt {} failed with {} ({}), retrying", operation, attempt, outcome,
                        exception.getClass().getSimpleName());
                pause(RETRY_DELAYS.get(attempt - 1), operation);
            }
        }
    }

    static boolean isRetryable(StripeException exception) {
        if (exception instanceof ApiConnectionException || exception instanceof RateLimitException) {
            return true;
        }
        Integer status = exception.getStatusCode();
        return status != null && (status >= 500 || status == 429);
    }

    static CallOutcome outcomeOf(StripeException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return CallOutcome.TIMEOUT;
            }
        }
        return CallOutcome.ERROR;
    }

    private void pause(Duration delay, StripeOperation operation) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new StripeUnavailableException(operation, CallOutcome.ERROR);
        }
    }

    @FunctionalInterface
    interface StripeCall<T> {

        T execute() throws StripeException;
    }
}
