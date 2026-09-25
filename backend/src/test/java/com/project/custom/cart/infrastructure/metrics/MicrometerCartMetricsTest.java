package com.project.custom.cart.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerCartMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerCartMetrics metrics = new MicrometerCartMetrics(registry);

    @Test
    void counterIsRegisteredWithZeroAtStartup() {
        assertThat(registry.get("shop.cart.additions").counter().count()).isZero();
    }

    @Test
    void additionIncrementsTheCounter() {
        metrics.addedToCart();
        metrics.addedToCart();

        assertThat(registry.get("shop.cart.additions").counter().count()).isEqualTo(2);
    }
}
