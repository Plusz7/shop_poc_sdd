package com.project.custom.shared.infrastructure.metrics;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cardinality safeguard of the built-in HTTP metrics (research R-26): at most 50 {@code uri} values of
 * {@code http.server.requests}; further ones are dropped instead of creating new series.
 */
@Configuration(proxyBeanMethods = false)
class MetricsConfig {

    static final int MAX_HTTP_URIS = 50;

    @Bean
    MeterFilter httpUriCardinalityLimit() {
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri", MAX_HTTP_URIS, MeterFilter.deny());
    }
}
