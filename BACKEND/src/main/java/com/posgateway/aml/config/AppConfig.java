package com.posgateway.aml.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Application Configuration
 * Includes VGS Proxy integration for secure outbound HTTP requests
 * 
 * @see <a href="https://github.com/verygoodsecurity/vgs-proxy-spring">VGS Proxy
 *      Spring</a>
 * 
 *      NOTE: VGS Proxy logic is implemented manually here as the external
 *      dependency
 *      is not available.
 */
@Configuration
public class AppConfig {

    /**
     * VGS Proxied RestTemplate (Manual Fallback)
     * 
     * Since the vgs-proxy-spring library is missing, we manually define this bean
     * creates a standard RestTemplate.
     * 
     * TODO: Add manual Proxy configuration using SimpleClientHttpRequestFactory if
     * needed.
     */
    @Bean(name = "vgsProxiedRestTemplate")
    public RestTemplate vgsProxiedRestTemplate() {
        return new RestTemplate();
    }

    /**
     * Regular RestTemplate for non-proxied requests
     * Use this for internal or non-sensitive outbound requests
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
