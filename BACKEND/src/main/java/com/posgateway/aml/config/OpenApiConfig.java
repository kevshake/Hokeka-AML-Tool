package com.posgateway.aml.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) Configuration
 * Provides API documentation at /swagger-ui.html and /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Value("${server.port:2637}")
    private int serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                .info(new Info()
                        .title("AML Fraud Detector API")
                        .version("1.0.0")
                        .description("""
                                Anti-Money Laundering and Fraud Detection System API
                                
                                This API provides comprehensive AML and fraud detection capabilities including:
                                - Transaction monitoring and screening
                                - Sanctions list screening
                                - Case management and investigation
                                - SAR (Suspicious Activity Report) generation
                                - Risk analytics and reporting
                                - Compliance calendar management
                                - Regulatory reporting (CTR, LCTR, IFTR)
                                
                                **Authentication:** Most endpoints require authentication via Bearer token.
                                Use the login endpoint to obtain a JWT token, then include it in the Authorization header.
                                """)
                        .contact(new Contact()
                                .name("AML Fraud Detector Support")
                                .email("support@posgateway.com")
                                .url("https://posgateway.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://posgateway.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort + contextPath)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.posgateway.com" + contextPath)
                                .description("Production Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token obtained from /api/v1/auth/login")));
    }
}

