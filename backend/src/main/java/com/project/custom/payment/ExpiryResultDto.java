package com.project.custom.payment;

public enum ExpiryResultDto {
    /** No session of the guest can be paid any more (including when none was open). */
    EXPIRED,
    /** A previous session of the guest has already been paid (R-13). */
    ALREADY_PAID,
    /** The provider could not be reached; open sessions may remain. */
    UNAVAILABLE
}
