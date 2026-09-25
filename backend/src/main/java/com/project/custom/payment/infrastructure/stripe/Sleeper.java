package com.project.custom.payment.infrastructure.stripe;

import java.time.Duration;

/**
 * Waits between Stripe call attempts; replaced in tests so that retries do not really wait.
 */
@FunctionalInterface
interface Sleeper {

    Sleeper THREAD = duration -> Thread.sleep(duration);

    void sleep(Duration duration) throws InterruptedException;
}
