package com.posgateway.aml.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers {@link StreamingResponseBodyMessageConverter} for every profile (the CORS
 * {@link WebMvcConfig} is production-only) so wildcard {@code ResponseEntity<?>} CSV streams render.
 */
@Configuration
public class StreamingResponseConfig implements WebMvcConfigurer {

    /** Prepends the converter so it is consulted before the JSON/String converters. */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new StreamingResponseBodyMessageConverter());
    }
}
