package com.project.custom.payment.infrastructure.stripe;

import com.project.custom.payment.domain.ExpiryResult;
import com.project.custom.payment.domain.GatewayUnavailable;
import com.project.custom.payment.domain.PaymentGateway;
import com.project.custom.payment.domain.PaymentSession;
import com.project.custom.payment.domain.SessionRequest;
import com.project.custom.payment.domain.SessionResult;
import com.project.custom.shared.infrastructure.config.AppProperties;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Hosted Stripe Checkout (research R-11, R-13, contracts/stripe-webhook.md §1). Called outside database
 * transactions; every attempt of a call carries the same idempotency key.
 */
@Component
class StripePaymentGateway implements PaymentGateway {

    /** Stripe's minimum session lifetime. */
    static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);
    private static final String CURRENCY = "pln";
    private static final String SESSION_COMPLETE = "complete";
    private static final String SESSION_EXPIRED = "expired";

    private final StripeClient stripeClient;
    private final StripeRetryPolicy retryPolicy;
    private final String baseUrl;
    private final Clock clock;

    StripePaymentGateway(StripeClient stripeClient, Sleeper sleeper, AppProperties appProperties, Clock clock) {
        this.stripeClient = stripeClient;
        this.retryPolicy = new StripeRetryPolicy(sleeper);
        String base = appProperties.baseUrl().toString();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.clock = clock;
    }

    @Override
    public SessionResult createSession(SessionRequest request) {
        if (!request.linesTotal().equals(request.total())) {
            throw new IllegalArgumentException("Sum of the session lines differs from the order total (SC-004)");
        }
        SessionCreateParams params = sessionParams(request);
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("checkout-" + request.paymentId())
                .build();
        try {
            Session session = retryPolicy.execute(StripeOperation.CREATE_SESSION,
                    () -> stripeClient.v1().checkout().sessions().create(params, options));
            return new PaymentSession(session.getId(), session.getUrl());
        } catch (StripeUnavailableException unavailable) {
            return new GatewayUnavailable(unavailable.getMessage());
        } catch (StripeException rejected) {
            throw configurationError(StripeOperation.CREATE_SESSION, rejected);
        }
    }

    @Override
    public ExpiryResult expire(String providerSessionId) {
        try {
            retryPolicy.execute(StripeOperation.EXPIRE_SESSION,
                    () -> stripeClient.v1().checkout().sessions().expire(providerSessionId));
            return ExpiryResult.EXPIRED;
        } catch (StripeUnavailableException unavailable) {
            return ExpiryResult.UNAVAILABLE;
        } catch (InvalidRequestException notOpen) {
            return stateOfSessionThatIsNotOpen(providerSessionId, notOpen);
        } catch (StripeException rejected) {
            throw configurationError(StripeOperation.EXPIRE_SESSION, rejected);
        }
    }

    /** Stripe refuses to expire a session that is no longer open: it was either paid or has already expired. */
    private ExpiryResult stateOfSessionThatIsNotOpen(String providerSessionId, InvalidRequestException notOpen) {
        try {
            Session session = retryPolicy.execute(StripeOperation.EXPIRE_SESSION,
                    () -> stripeClient.v1().checkout().sessions().retrieve(providerSessionId));
            if (SESSION_COMPLETE.equals(session.getStatus())) {
                return ExpiryResult.ALREADY_PAID;
            }
            if (SESSION_EXPIRED.equals(session.getStatus())) {
                return ExpiryResult.EXPIRED;
            }
            throw configurationError(StripeOperation.EXPIRE_SESSION, notOpen);
        } catch (StripeUnavailableException unavailable) {
            return ExpiryResult.UNAVAILABLE;
        } catch (StripeException rejected) {
            throw configurationError(StripeOperation.EXPIRE_SESSION, rejected);
        }
    }

    private SessionCreateParams sessionParams(SessionRequest request) {
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setCustomerEmail(request.customerEmail())
                .setClientReferenceId(request.orderId().toString())
                .putMetadata("orderId", request.orderId().toString())
                .putMetadata("paymentId", request.paymentId().toString())
                .putMetadata("number", request.orderNumber())
                .setExpiresAt(clock.instant().plus(SESSION_LIFETIME).getEpochSecond())
                .setSuccessUrl(baseUrl + "/orders/" + request.orderNumber() + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/cart?payment=canceled");
        for (SessionRequest.Line line : request.lines()) {
            params.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) line.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(CURRENCY)
                            .setUnitAmount(line.unitPrice().minor())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(line.name())
                                    .build())
                            .build())
                    .build());
        }
        return params.build();
    }

    private static StripeConfigurationException configurationError(StripeOperation operation,
                                                                   StripeException exception) {
        StripeConfigurationException error = new StripeConfigurationException(operation, exception);
        log.error(error.getMessage());
        return error;
    }
}
