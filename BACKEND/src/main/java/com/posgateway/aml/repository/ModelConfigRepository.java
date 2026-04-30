package com.posgateway.aml.repository;

import com.posgateway.aml.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Model Configuration
 * Stores configurable thresholds and parameters
 */
@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    /**
     * Find configuration by key
     * 
     * @param configKey The configuration key
     * @return Optional ModelConfig
     */
    Optional<ModelConfig> findByConfigKey(String configKey);

    /**
     * Get configuration value by key with default
     * 
     * @param configKey The configuration key
     * @param defaultValue Default value if not found
     * @return Configuration value
     */
    @Query("SELECT COALESCE(m.value, :defaultValue) FROM ModelConfig m WHERE m.configKey = :configKey")
    String getValueOrDefault(String configKey, String defaultValue);
}

