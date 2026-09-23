package com.project.custom.payment.infrastructure.stripe;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Reports an invalid Stripe configuration with the property name and the cause only. The standard
 * {@code BindFailureAnalyzer} would print {@code Value: "sk_live_…"} to the log (research R-20).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class StripePropertiesFailureAnalyzer extends AbstractFailureAnalyzer<BindException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, BindException cause) {
        if (cause.getTarget() == null
                || !StripeProperties.class.equals(cause.getTarget().getType().resolve())) {
            return null;
        }
        String reason = rootCauseMessage(cause);
        return new FailureAnalysis(
                "Invalid Stripe configuration under 'shop.stripe': " + reason,
                "Set STRIPE_SECRET_KEY to a Stripe test key (sk_test_…) and STRIPE_WEBHOOK_SECRET to a webhook "
                        + "signing secret (whsec_…), for example in .env (see .env.example).",
                null);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
