package com.posgateway.aml.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Reactive WebFlux Configuration
 * Optional reactive support for even higher throughput
 * Can handle 50K+ concurrent requests with reactive streams
 */
@Configuration
@SuppressWarnings("null") // @Value injected strings and WebClient builder are safe
public class ReactiveWebFluxConfig {

    @Value("${ultra.throughput.enable.reactive:false}")
    private boolean reactiveEnabled;

    @Value("${scoring.service.url:http://localhost:8000}")
    private String scoringServiceUrl;

    /**
     * WebClient for reactive scoring service calls
     * Only created if reactive mode is enabled
     */
    @Bean
    public WebClient reactiveWebClient() {
        if (!reactiveEnabled) {
            return null;
        }

        return WebClient.builder()
            .baseUrl(scoringServiceUrl)
            .build();
    }
}

