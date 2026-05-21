package com.posgateway.aml.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Distributed Tracing Configuration using OpenTelemetry.
 * Sends traces to Jaeger/OTLP collector.
 */
@Configuration
public class TracingConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(
                        BatchSpanProcessor.builder(
                                OtlpGrpcSpanExporter.builder()
                                        .setEndpoint("http://localhost:4317")
                                        .setTimeout(30, TimeUnit.SECONDS)
                                        .build()
                        ).build()
                )
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("aml-fraud-detector", "1.0.0");
    }
}