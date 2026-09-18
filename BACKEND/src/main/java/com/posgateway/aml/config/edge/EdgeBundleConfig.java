package com.posgateway.aml.config.edge;

import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the (deliberately framework-free) edge crypto/bundle classes as control-plane beans.
 *
 * <p>{@link EdgeBundleService} and {@link HokekaSecureEnvelope} are plain classes so the same code
 * can be lifted into the edge host and into cross-language test vectors without dragging Spring
 * along. The control plane wires them here rather than annotating them.
 */
@Configuration(proxyBeanMethods = false)
public class EdgeBundleConfig {

    @Bean
    public HokekaSecureEnvelope hokekaSecureEnvelope() {
        return new HokekaSecureEnvelope();
    }

    @Bean
    public EdgeBundleService edgeBundleService(HokekaSecureEnvelope envelope) {
        return new EdgeBundleService(envelope);
    }
}
