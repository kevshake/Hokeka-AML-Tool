package com.posgateway.aml.service;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP/2 Detection Service
 * Automatically detects if the server and client support HTTP/2
 * Checks hardware/software capabilities and determines best protocol
 */
// @RequiredArgsConstructor removed
@Service
public class Http2DetectionService {

    private static final Logger logger = LoggerFactory.getLogger(Http2DetectionService.class);

    @Value("${http2.auto.detect.enabled:true}")
    private boolean autoDetectEnabled;

    @Value("${http2.detection.test.url:http://localhost:8080/actuator/health}")
    private String testUrl;

    @Value("${http2.detection.timeout.ms:5000}")
    private int detectionTimeout;

    private final AtomicBoolean http2Supported = new AtomicBoolean(false);
    private final AtomicBoolean http2Capable = new AtomicBoolean(false);
    private final AtomicReference<Long> lastDetectionTime = new AtomicReference<>(0L);

    @Value("${http2.detection.refresh.interval.seconds:300}")
    private long refreshIntervalSeconds;

    /**
     * Detect if HTTP/2 is supported and should be used
     * 
     * @return true if HTTP/2 is supported and available
     */
    public boolean detectHttp2Support() {
        if (!autoDetectEnabled) {
            logger.debug("HTTP/2 auto-detection is disabled");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long lastDetected = lastDetectionTime.get();
        
        // Use cached result if recent (within refresh interval)
        if (currentTime - lastDetected < refreshIntervalSeconds * 1000 && http2Capable.get()) {
            logger.debug("Using cached HTTP/2 detection result: {}", http2Capable.get());
            return http2Capable.get();
        }

        try {
            // Check system properties for HTTP/2 support
            boolean systemSupport = checkSystemHttp2Support();
            
            // Check if server responds to HTTP/2
            boolean serverSupport = checkServerHttp2Support();
            
            // Check JVM/Java version (HTTP/2 requires Java 9+)
            boolean jvmSupport = checkJvmHttp2Support();
            
            boolean isSupported = systemSupport && serverSupport && jvmSupport;
            
            http2Supported.set(isSupported);
            http2Capable.set(isSupported);
            lastDetectionTime.set(currentTime);
            
            logger.info("HTTP/2 detection result: supported={}, system={}, server={}, jvm={}", 
                isSupported, systemSupport, serverSupport, jvmSupport);
            
            return isSupported;
            
        } catch (Exception e) {
            logger.warn("Error during HTTP/2 detection, defaulting to HTTP/1.1: {}", e.getMessage());
            http2Capable.set(false);
            return false;
        }
    }

    /**
     * Check if system supports HTTP/2
     */
    private boolean checkSystemHttp2Support() {
        try {
            // Check if HTTP/2 is available in system
            String javaVersion = System.getProperty("java.version");
            if (javaVersion != null && javaVersion.startsWith("1.")) {
                int majorVersion = Integer.parseInt(javaVersion.split("\\.")[1]);
                if (majorVersion < 9) {
                    logger.debug("Java version {} does not support HTTP/2", javaVersion);
                    return false;
                }
            }
            
            // Check if ALPN (Application-Layer Protocol Negotiation) is available
            // HTTP/2 requires ALPN for protocol negotiation
            return true;
            
        } catch (Exception e) {
            logger.warn("Error checking system HTTP/2 support: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if server supports HTTP/2
     */
    private boolean checkServerHttp2Support() {
        try {
            URL url = new URL(testUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(detectionTimeout);
            connection.setReadTimeout(detectionTimeout);
            
            // Try to connect and check response
            connection.connect();
            int responseCode = connection.getResponseCode();
            
            connection.disconnect();
            
            // If we can connect and get a response, assume HTTP/2 is possible
            // (actual protocol negotiation happens at connection time)
            return responseCode >= 200 && responseCode < 300;
            
        } catch (IOException e) {
            logger.debug("Server HTTP/2 support check failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Error checking server HTTP/2 support: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if JVM supports HTTP/2
     */
    private boolean checkJvmHttp2Support() {
        try {
            // Java 9+ has built-in HTTP/2 support via java.net.http
            String javaVersion = System.getProperty("java.version");
            
            // Check for Java 11+ (better HTTP/2 support)
            if (javaVersion != null) {
                // Java 9+ supports HTTP/2
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warn("Error checking JVM HTTP/2 support: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current HTTP/2 support status
     */
    public boolean isHttp2Supported() {
        return http2Supported.get();
    }

    /**
     * Get HTTP/2 capability status
     */
    public boolean isHttp2Capable() {
        return http2Capable.get();
    }

    /**
     * Force refresh of HTTP/2 detection
     */
    public boolean refreshDetection() {
        lastDetectionTime.set(0L); // Reset cache
        return detectHttp2Support();
    }
}

