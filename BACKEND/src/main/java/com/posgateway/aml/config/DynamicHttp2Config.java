package com.posgateway.aml.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Dynamic HTTP/2 Configuration
 * Configures HTTP/2 support dynamically based on detection and health
 * monitoring
 */
@Configuration
public class DynamicHttp2Config implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${http2.auto.detect.enabled:true}")
    private boolean autoDetectEnabled;

    @Value("${http2.failover.enabled:true}")
    private boolean failoverEnabled;

    @Value("${server.http2.enabled:false}")
    private boolean http2Enabled;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // HTTP/2 will be dynamically enabled/disabled by Http2FailoverService
        // Initial configuration is set here, but runtime switching happens in the
        // service

        boolean shouldEnableHttp2 = false;

        if (autoDetectEnabled && failoverEnabled) {
            // Auto-detect will determine if HTTP/2 should be enabled
            // For now, use the configured value, but it will be overridden at runtime
            shouldEnableHttp2 = http2Enabled;
        } else if (http2Enabled) {
            // Manual HTTP/2 configuration
            shouldEnableHttp2 = true;
        }

        if (shouldEnableHttp2) {
            org.springframework.boot.web.server.Http2 http2 = new org.springframework.boot.web.server.Http2();
            http2.setEnabled(shouldEnableHttp2);
            factory.setHttp2(http2);
            factory.setProtocol("org.apache.coyote.http11.Http11NioProtocol");
        }

        // Note: HTTP/2 runtime switching requires server restart in Tomcat
        // For true dynamic switching, we'll use application-level protocol selection
    }
}
