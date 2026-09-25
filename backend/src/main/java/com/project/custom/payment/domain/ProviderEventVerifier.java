package com.project.custom.payment.domain;

/**
 * Port verifying that a webhook call really comes from the payment provider (Principle V, FR-019).
 */
public interface ProviderEventVerifier {

    /**
     * @param payload         the raw request body, exactly as received
     * @param signatureHeader the provider signature header; may be {@code null} when missing
     * @throws InvalidEventSignature when the signature is missing, invalid or too old
     */
    ProviderConfirmation verify(String payload, String signatureHeader);
}
