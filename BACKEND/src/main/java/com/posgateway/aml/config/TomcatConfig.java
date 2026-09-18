package com.posgateway.aml.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat Configuration
 * Optimizes Tomcat for 30,000+ concurrent requests
 */
@Configuration
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${server.tomcat.threads.min-spare:200}")
    private int minSpareThreads;

    @Value("${server.tomcat.threads.max:1000}")
    private int maxThreads;

    @Value("${server.tomcat.max-connections:10000}")
    private int maxConnections;

    @Value("${server.tomcat.accept-count:5000}")
    private int acceptCount;

    @Value("${server.tomcat.connection-timeout:20000}")
    private int connectionTimeout;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            
            // Thread pool settings
            protocol.setMinSpareThreads(minSpareThreads);
            protocol.setMaxThreads(maxThreads);
            
            // Connection settings
            protocol.setMaxConnections(maxConnections);
            protocol.setAcceptCount(acceptCount);
            protocol.setConnectionTimeout(connectionTimeout);
            
            // Performance optimizations
            protocol.setCompression("on");
            protocol.setCompressionMinSize(1024);
            protocol.setCompressibleMimeType("application/json,application/xml,text/html,text/xml,text/plain");
            
            // Keep-alive settings
            protocol.setKeepAliveTimeout(60000);
            protocol.setMaxKeepAliveRequests(1000);
            
            // Enable TCP no delay for low latency
            connector.setProperty("tcpNoDelay", "true");
            
            // Enable APR for better performance (if available)
            // protocol.setUseSendfile(true);
        });
    }
}

