package com.project.custom.payment.domain;

/**
 * Result of creating a provider session.
 */
public sealed interface SessionResult permits PaymentSession, GatewayUnavailable {
}
