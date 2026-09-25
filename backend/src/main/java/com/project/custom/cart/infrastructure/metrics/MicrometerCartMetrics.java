package com.project.custom.cart.infrastructure.metrics;

import com.project.custom.cart.application.CartMetrics;
import com.project.custom.shared.infrastructure.metrics.AfterCommit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * {@link CartMetrics} on Micrometer; names from contracts/metrics.md §2. The counter is registered at startup,
 * so that {@code increase()} works from the first scrape.
 */
@Component
class MicrometerCartMetrics implements CartMetrics {

    static final String ADDITIONS = "shop.cart.additions";

    private final Counter additions;

    MicrometerCartMetrics(MeterRegistry registry) {
        this.additions = Counter.builder(ADDITIONS)
                .description("Lines added to carts")
                .register(registry);
    }

    @Override
    public void addedToCart() {
        AfterCommit.run(additions::increment);
    }
}
