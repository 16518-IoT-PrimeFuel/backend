package com.primefuel.fulltank.platform.tracking.infrastructure.metrics;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiRouteMetricsConfiguration implements WebMvcConfigurer {
    private final ApiRouteMetricsInterceptor interceptor;

    public ApiRouteMetricsConfiguration(ApiRouteMetricsInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**", "/api/v2/**");
    }
}
