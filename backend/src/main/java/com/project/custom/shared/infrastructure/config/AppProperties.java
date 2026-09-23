package com.project.custom.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Application settings: the public frontend address (Stripe return URLs) and the guest cookie.
 */
@Validated
@ConfigurationProperties("shop.app")
public record AppProperties(@NotNull URI baseUrl, @Valid @DefaultValue Cookie cookie) {

    public record Cookie(@DefaultValue("true") boolean secure, @NotNull @DefaultValue("30d") Duration maxAge) {
    }
}
