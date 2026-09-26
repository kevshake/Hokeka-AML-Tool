package com.posgateway.aml.config.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiDecisionProperties.class)
public class AiDecisionConfig {
}
