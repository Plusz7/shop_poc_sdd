package com.project.custom.payment.application;

import com.project.custom.payment.ExpiryResultDto;
import com.project.custom.payment.PaymentFacade;
import com.project.custom.payment.PaymentFailedEvent;
import com.project.custom.payment.PaymentUnavailableException;
import com.project.custom.payment.StartPaymentDto;
import com.project.custom.payment.StartedPaymentDto;
import com.project.custom.payment.domain.ExpiryResult;
import com.project.custom.payment.domain.GatewayUnavailable;
import com.project.custom.payment.domain.Payment;
import com.project.custom.payment.domain.PaymentGateway;
import com.project.custom.payment.domain.PaymentId;
import com.project.custom.payment.domain.PaymentRepository;
import com.project.custom.payment.domain.PaymentSession;
import com.project.custom.payment.domain.SessionRequest;
import com.project.custom.payment.domain.SessionResult;
import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Starting and expiring payments (research R-11, R-13). The provider is always called between two short
 * transactions, never inside one (Principle V): the payment is stored first, so that a session created at
 * the provider can always be matched to it.
 */
@Service
class PaymentService implements PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    PaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                   ApplicationEventPublisher eventPublisher, TransactionTemplate transactionTemplate, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public StartedPaymentDto start(StartPaymentDto request) {
        Payment payment = Payment.create(PaymentId.random(), request.orderId(), request.guestId(),
                Money.pln(request.totalMinor()), clock.instant());
        transactionTemplate.executeWithoutResult(status -> paymentRepository.save(payment));

        SessionResult result;
        try {
            result = paymentGateway.createSession(sessionRequest(payment.id(), request));
        } catch (RuntimeException rejected) {
            storeFailure(payment.id());
            throw rejected;
        }
        return switch (result) {
            case PaymentSession session -> {
                transactionTemplate.executeWithoutResult(status -> {
                    Payment stored = load(payment.id());
                    stored.open(session.providerSessionId(), session.url());
                    paymentRepository.save(stored);
                });
                yield new StartedPaymentDto(session.url());
            }
            case GatewayUnavailable unavailable -> {
                storeFailure(payment.id());
                log.warn("Payment {} of order {} not started: {}", payment.id(), request.orderNumber(),
                        unavailable.reason());
                throw new PaymentUnavailableException(unavailable.reason());
            }
        };
    }

    @Override
    public ExpiryResultDto expireOpen(GuestId guestId) {
        for (Payment open : paymentRepository.findOpenByGuest(guestId)) {
            String sessionId = open.providerSessionId().orElseThrow();
            ExpiryResult result = paymentGateway.expire(sessionId);
            switch (result) {
                case EXPIRED -> transactionTemplate.executeWithoutResult(status -> {
                    Payment stored = load(open.id());
                    if (stored.expire()) {
                        paymentRepository.save(stored);
                        eventPublisher.publishEvent(
                                new PaymentFailedEvent(stored.orderId(), PaymentFailedEvent.Reason.EXPIRED));
                    }
                });
                case ALREADY_PAID -> {
                    log.info("Open payment {} has already been paid; a new checkout is refused", open.id());
                    return ExpiryResultDto.ALREADY_PAID;
                }
                case UNAVAILABLE -> {
                    return ExpiryResultDto.UNAVAILABLE;
                }
            }
        }
        return ExpiryResultDto.EXPIRED;
    }

    private void storeFailure(PaymentId paymentId) {
        transactionTemplate.executeWithoutResult(status -> {
            Payment stored = load(paymentId);
            if (stored.fail()) {
                paymentRepository.save(stored);
            }
        });
    }

    private Payment load(PaymentId paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " does not exist"));
    }

    private static SessionRequest sessionRequest(PaymentId paymentId, StartPaymentDto request) {
        return new SessionRequest(paymentId, request.orderId(), request.orderNumber(),
                request.lines().stream()
                        .map(line -> new SessionRequest.Line(line.name(), Money.pln(line.unitPriceMinor()),
                                line.quantity()))
                        .toList(),
                request.customerEmail(), Money.pln(request.totalMinor()));
    }
}
