package com.posgateway.aml.config.onprem;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HokekaAuthProperties.class)
public class OnPremAuthConfig {
}
