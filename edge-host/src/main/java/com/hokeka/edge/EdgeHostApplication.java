package com.hokeka.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hokeka Edge Host — the on-prem Spring Boot service deployed to PSP sites. Serves the local
 * transaction API on virtual threads over TLS 1.3, evaluates via {@link EdgeEngine} (native Rust
 * core or Java fallback), pulls HSE-1 sealed rule bundles from the control plane and ships
 * aggregate-only metrics back. Raw transaction data never leaves the premises.
 */
@SpringBootApplication
public class EdgeHostApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdgeHostApplication.class, args);
    }
}
