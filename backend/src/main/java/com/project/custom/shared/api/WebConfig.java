package com.project.custom.shared.api;

import com.project.custom.shared.infrastructure.config.AppProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    static final int GUEST_ID_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Bean
    FilterRegistrationBean<GuestIdFilter> guestIdFilter(AppProperties appProperties) {
        FilterRegistrationBean<GuestIdFilter> registration = new FilterRegistrationBean<>(new GuestIdFilter(appProperties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(GUEST_ID_FILTER_ORDER);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GuestIdArgumentResolver());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
    }
}
