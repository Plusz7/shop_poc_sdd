package com.project.custom.payment.domain;

/**
 * The provider could not be reached after all attempts (connection error, timeout, {@code 5xx}, {@code 429}).
 *
 * @param reason technical description for logs; contains no secrets or customer data
 */
public record GatewayUnavailable(String reason) implements SessionResult {
}
