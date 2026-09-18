package com.posgateway.aml.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Configures OTLP distributed tracing when an exporter is explicitly enabled. */
@Configuration
public class TracingConfig {

    @Bean(destroyMethod = "close")
    public SdkTracerProvider sdkTracerProvider(
            @Value("${otel.tracing.enabled:false}") boolean enabled,
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String endpoint,
            @Value("${otel.service.name:hokeka-aml-backend}") String serviceName) {
        Resource resource = Resource.getDefault().merge(Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));
        SdkTracerProviderBuilder provider = SdkTracerProvider.builder().setResource(resource);
        if (enabled) {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .setTimeout(Duration.ofSeconds(10))
                    .build();
            provider.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
        }
        return provider.build();
    }

    @Bean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }
}
