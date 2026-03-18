package com.posgateway.aml.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration for Production Environment
 * 
 * Configures Cross-Origin Resource Sharing for hokeka.com domains.
 * This configuration is active for 'production' profile only.
 * 
 * Security considerations:
 * - Only allows specific trusted domains (hokeka.com and subdomains)
 * - Credentials are allowed for authenticated requests
 * - Proper headers are exposed for API consumers
 */
@Configuration
@Profile("production")
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:https://hokeka.com,https://www.hokeka.com,https://api.hokeka.com}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:Authorization,Content-Type,X-Requested-With,Accept,Origin,X-CSRF-Token,X-Api-Key}")
    private String allowedHeaders;

    @Value("${cors.exposed-headers:X-Total-Count,X-Page-Count,X-Current-Page}")
    private String exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        List<String> methods = Arrays.asList(allowedMethods.split(","));
        List<String> headers = Arrays.asList(allowedHeaders.split(","));
        List<String> exposed = Arrays.asList(exposedHeaders.split(","));

        registry.addMapping("/api/v1/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods(methods.toArray(new String[0]))
                .allowedHeaders(headers.toArray(new String[0]))
                .exposedHeaders(exposed.toArray(new String[0]))
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);

        // Separate mapping for auth endpoints
        registry.addMapping("/api/v1/auth/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(headers.toArray(new String[0]))
                .exposedHeaders(exposed.toArray(new String[0]))
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);

        // Public endpoints with more permissive CORS
        registry.addMapping("/api/v1/merchants/onboard")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders(headers.toArray(new String[0]))
                .allowCredentials(false)
                .maxAge(maxAge);
    }

    /**
     * CORS Configuration Source for Spring Security integration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        
        List<String> methods = Arrays.asList(allowedMethods.split(","));
        configuration.setAllowedMethods(methods);
        
        List<String> headers = Arrays.asList(allowedHeaders.split(","));
        configuration.setAllowedHeaders(headers);
        
        List<String> exposed = Arrays.asList(exposedHeaders.split(","));
        configuration.setExposedHeaders(exposed);
        
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        source.registerCorsConfiguration("/actuator/**", configuration);

        return source;
    }
}
