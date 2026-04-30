package com.posgateway.aml.service;

import com.posgateway.aml.entity.ModelConfig;
import com.posgateway.aml.repository.ModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration Service
 * Manages configurable thresholds and parameters from database
 * Uses caching for performance - all configs cached in memory
 */
@Service
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    private final ModelConfigRepository configRepository;

    @Autowired
    public ConfigService(ModelConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Get configuration value by key
     * 
     * @param configKey Configuration key
     * @param defaultValue Default value if not found
     * @return Configuration value
     */
    @Cacheable(value = "modelConfig", key = "#configKey")
    public String getConfig(String configKey, String defaultValue) {
        Optional<ModelConfig> config = configRepository.findByConfigKey(configKey);
        if (config.isPresent()) {
            logger.debug("Retrieved config {} = {}", configKey, config.get().getValue());
            return config.get().getValue();
        }
        logger.debug("Config {} not found, using default: {}", configKey, defaultValue);
        return defaultValue;
    }

    /**
     * Get configuration value as Double
     */
    public Double getConfigAsDouble(String configKey, Double defaultValue) {
        String value = getConfig(configKey, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid double value for config {}: {}, using default {}", 
                configKey, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get configuration value as Integer
     */
    public Integer getConfigAsInteger(String configKey, Integer defaultValue) {
        String value = getConfig(configKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for config {}: {}, using default {}", 
                configKey, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get configuration value as Boolean
     */
    public Boolean getConfigAsBoolean(String configKey, Boolean defaultValue) {
        String value = getConfig(configKey, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Update configuration value
     * 
     * @param configKey Configuration key
     * @param value New value
     * @param updatedBy User who updated
     * @param description Optional description
     */
    @Transactional
    @CacheEvict(value = "modelConfig", key = "#configKey")
    public void updateConfig(String configKey, String value, String updatedBy, String description) {
        Optional<ModelConfig> existing = configRepository.findByConfigKey(configKey);
        
        ModelConfig config;
        if (existing.isPresent()) {
            config = existing.get();
        } else {
            config = new ModelConfig();
            config.setConfigKey(configKey);
        }
        
        config.setValue(value);
        config.setUpdatedBy(updatedBy);
        if (description != null) {
            config.setDescription(description);
        }
        
        configRepository.save(config);
        logger.info("Updated config {} = {} by {}", configKey, value, updatedBy);
    }

    /**
     * Get all configurations as map
     */
    public Map<String, String> getAllConfigs() {
        Map<String, String> configs = new HashMap<>();
        configRepository.findAll().forEach(config -> 
            configs.put(config.getConfigKey(), config.getValue()));
        return configs;
    }

    /**
     * Get fraud threshold for blocking
     */
    public Double getFraudBlockThreshold() {
        return getConfigAsDouble("fraud.threshold.block", 0.95);
    }

    /**
     * Get fraud threshold for holding
     */
    public Double getFraudHoldThreshold() {
        return getConfigAsDouble("fraud.threshold.hold", 0.7);
    }

    /**
     * Get AML high value threshold
     */
    public Long getAmlHighValueThreshold() {
        return getConfigAsInteger("aml.high_value_amount_cents", 1000000).longValue();
    }

    /**
     * Check if blacklist rule is enabled
     */
    public Boolean isBlacklistEnabled() {
        return getConfigAsBoolean("fraud.rule.blacklist.enabled", true);
    }
}

