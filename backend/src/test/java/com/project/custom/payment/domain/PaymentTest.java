package com.project.custom.payment.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant CREATED = Instant.parse("2026-09-25T10:00:00Z");
    private static final Instant CONFIRMED = Instant.parse("2026-09-25T10:03:00Z");
    private static final Instant LATER = Instant.parse("2026-09-25T10:40:00Z");

    private final Payment payment = Payment.create(PaymentId.random(), UUID.randomUUID(), GuestId.random(),
            Money.pln(30_800), CREATED);

    @Test
    void newPaymentIsCreatedWithoutSession() {
        assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.providerSessionId()).isEmpty();
        assertThat(payment.paymentUrl()).isEmpty();
        assertThat(payment.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void createdPaymentOpensWithSession() {
        payment.open("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1");

        assertThat(payment.status()).isEqualTo(PaymentStatus.OPEN);
        assertThat(payment.providerSessionId()).contains("cs_test_1");
        assertThat(payment.paymentUrl()).contains("https://checkout.stripe.com/c/pay/cs_test_1");
    }

    @Test
    void createdPaymentFailsWhenSessionCannotBeCreated() {
        assertThat(payment.fail()).isTrue();

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void openPaymentIsConfirmed() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");

        assertThat(payment.confirm("pi_1", CONFIRMED)).isTrue();

        assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.providerPaymentId()).contains("pi_1");
        assertThat(payment.confirmedAt()).contains(CONFIRMED);
    }

    @Test
    void openPaymentExpires() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");

        assertThat(payment.expire()).isTrue();

        assertThat(payment.status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void openPaymentFails() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");

        assertThat(payment.fail()).isTrue();

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void repeatedConfirmationIsIdempotent() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");
        payment.confirm("pi_1", CONFIRMED);

        assertThat(payment.confirm("pi_2", LATER)).isFalse();

        assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.providerPaymentId()).contains("pi_1");
        assertThat(payment.confirmedAt()).contains(CONFIRMED);
    }

    @Test
    void confirmedIsFinal() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");
        payment.confirm("pi_1", CONFIRMED);

        assertThat(payment.expire()).isFalse();
        assertThat(payment.fail()).isFalse();

        assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void lateConfirmationOfExpiredPaymentIsRecorded() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");
        payment.expire();

        assertThat(payment.confirm("pi_1", LATER)).isTrue();

        assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void repeatedExpiryOrFailureChangesNothing() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");
        payment.expire();

        assertThat(payment.expire()).isFalse();
        assertThat(payment.fail()).isFalse();
        assertThat(payment.status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void onlyCreatedPaymentCanOpen() {
        payment.open("cs_test_1", "https://checkout.stripe.com/x");

        assertThatThrownBy(() -> payment.open("cs_test_2", "https://checkout.stripe.com/y"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paymentWithoutSessionCannotBeConfirmedOrExpired() {
        assertThatThrownBy(() -> payment.confirm("pi_1", CONFIRMED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(payment::expire).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void openingRequiresSessionIdAndUrl() {
        assertThatThrownBy(() -> payment.open(" ", "https://checkout.stripe.com/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.open("cs_test_1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
