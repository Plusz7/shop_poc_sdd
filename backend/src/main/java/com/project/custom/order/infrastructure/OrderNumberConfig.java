package com.project.custom.order.infrastructure;

import com.project.custom.order.domain.OrderNumberGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration(proxyBeanMethods = false)
class OrderNumberConfig {

    @Bean
    OrderNumberGenerator orderNumberGenerator() {
        return new OrderNumberGenerator(new SecureRandom());
    }
}
