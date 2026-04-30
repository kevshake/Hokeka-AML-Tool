package com.posgateway.aml.controller;

import com.posgateway.aml.dto.SystemSettingsRequest;
import com.posgateway.aml.dto.psp.PspThemeUpdateRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.ConfigService;
import com.posgateway.aml.service.psp.PspService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings Controller
 * Provides endpoints for system settings and PSP theme configuration
 */
@RestController
@RequestMapping("/settings")
@PreAuthorize("hasAnyRole('ADMIN')")
public class SettingsController {

    private final PspRepository pspRepository;
    private final PspService pspService;
    private final ConfigService configService;

    @Autowired
    public SettingsController(PspRepository pspRepository, PspService pspService, ConfigService configService) {
        this.pspRepository = pspRepository;
        this.pspService = pspService;
        this.configService = configService;
    }

    /**
     * Get user settings (legacy endpoint for backward compatibility)
     * GET /settings
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", "light");
        settings.put("notifications", true);
        settings.put("autoRefresh", true);
        settings.put("refreshInterval", 30); // seconds
        settings.put("timezone", "UTC");
        settings.put("dateFormat", "YYYY-MM-DD");
        settings.put("itemsPerPage", 50);
        return ResponseEntity.ok(settings);
    }

    /**
     * Update system settings
     * PUT /settings
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> settings) {
        // In a real implementation, this would save to database
        // For now, just return the settings
        return ResponseEntity.ok(settings);
    }

    /**
     * Get system configuration settings
     * GET /settings/system
     * Returns maintenance mode, debug logging, risk thresholds, audit retention, etc.
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemSettings() {
        Map<String, Object> settings = new HashMap<>();
        
        // Get system configuration from database
        settings.put("maintenanceMode", configService.getConfigAsBoolean("system.maintenance.mode", false));
        settings.put("debugLogging", configService.getConfigAsBoolean("system.debug.logging", false));
        settings.put("riskThresholdHigh", configService.getConfigAsInteger("risk.threshold.high", 80));
        settings.put("riskThresholdMedium", configService.getConfigAsInteger("risk.threshold.medium", 50));
        settings.put("auditRetentionDays", configService.getConfigAsInteger("audit.retention.days", 90));
        settings.put("allowCrossBorderTxns", configService.getConfigAsBoolean("transaction.allow.cross.border", true));
        
        return ResponseEntity.ok(settings);
    }

    /**
     * Update system configuration settings
     * PUT /settings/system
     * Updates maintenance mode, debug logging, risk thresholds, audit retention, etc.
     */
    @PutMapping("/system")
    public ResponseEntity<Map<String, Object>> updateSystemSettings(
            @RequestBody SystemSettingsRequest request,
            @AuthenticationPrincipal User currentUser) {
        
        String updatedBy = currentUser != null ? currentUser.getUsername() : "system";
        
        // Update each setting if provided
        if (request.getMaintenanceMode() != null) {
            configService.updateConfig("system.maintenance.mode", 
                String.valueOf(request.getMaintenanceMode()), updatedBy, 
                "System maintenance mode setting");
        }
        
        if (request.getDebugLogging() != null) {
            configService.updateConfig("system.debug.logging", 
                String.valueOf(request.getDebugLogging()), updatedBy, 
                "Debug logging enabled/disabled");
        }
        
        if (request.getRiskThresholdHigh() != null) {
            configService.updateConfig("risk.threshold.high", 
                String.valueOf(request.getRiskThresholdHigh()), updatedBy, 
                "High risk score threshold (0-100)");
        }
        
        if (request.getRiskThresholdMedium() != null) {
            configService.updateConfig("risk.threshold.medium", 
                String.valueOf(request.getRiskThresholdMedium()), updatedBy, 
                "Medium risk score threshold (0-100)");
        }
        
        if (request.getAuditRetentionDays() != null) {
            configService.updateConfig("audit.retention.days", 
                String.valueOf(request.getAuditRetentionDays()), updatedBy, 
                "Number of days to retain audit logs");
        }
        
        if (request.getAllowCrossBorderTxns() != null) {
            configService.updateConfig("transaction.allow.cross.border", 
                String.valueOf(request.getAllowCrossBorderTxns()), updatedBy, 
                "Allow cross-border transactions");
        }
        
        // Return updated settings
        return getSystemSettings();
    }

    /**
     * Get application configuration
     * GET /settings/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("appName", "AML Fraud Detector");
        config.put("version", "1.0.0");
        config.put("environment", System.getProperty("spring.profiles.active", "development"));
        config.put("database", "PostgreSQL");
        config.put("cache", "Aerospike");
        return ResponseEntity.ok(config);
    }

    /**
     * Get all PSPs for theme management
     * GET /api/v1/settings/psps
     * Super Admin only - can manage themes for all PSPs
     */
    @GetMapping("/psps")
    public ResponseEntity<List<Map<String, Object>>> getAllPsps() {
        List<Psp> psps = pspRepository.findAll();
        List<Map<String, Object>> pspList = psps.stream()
                .map(psp -> {
                    Map<String, Object> pspMap = new HashMap<>();
                    pspMap.put("id", psp.getPspId());
                    pspMap.put("code", psp.getPspCode());
                    pspMap.put("name", psp.getTradingName());
                    pspMap.put("status", psp.getStatus());
                    return pspMap;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(pspList);
    }

    /**
     * Get PSP theme configuration
     * GET /settings/psps/{pspId}/theme
     */
    @GetMapping("/psps/{pspId}/theme")
    public ResponseEntity<Map<String, Object>> getPspTheme(@PathVariable Long pspId) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        Map<String, Object> theme = new HashMap<>();
        theme.put("pspId", psp.getPspId());
        theme.put("pspName", psp.getTradingName());
        theme.put("brandingTheme", psp.getBrandingTheme() != null ? psp.getBrandingTheme() : "default");
        theme.put("primaryColor", psp.getPrimaryColor());
        theme.put("secondaryColor", psp.getSecondaryColor());
        theme.put("accentColor", psp.getAccentColor());
        theme.put("logoUrl", psp.getLogoUrl());
        theme.put("fontFamily", psp.getFontFamily());
        theme.put("fontSize", psp.getFontSize());
        theme.put("buttonRadius", psp.getButtonRadius());
        theme.put("buttonStyle", psp.getButtonStyle());
        theme.put("navStyle", psp.getNavStyle());

        return ResponseEntity.ok(theme);
    }

    /**
     * Update PSP theme configuration
     * PUT /settings/psps/{pspId}/theme
     */
    @PutMapping("/psps/{pspId}/theme")
    public ResponseEntity<Map<String, Object>> updatePspTheme(
            @PathVariable Long pspId,
            @RequestBody PspThemeUpdateRequest request) {
        
        Psp updatedPsp = pspService.updatePspTheme(pspId, request);

        Map<String, Object> theme = new HashMap<>();
        theme.put("pspId", updatedPsp.getPspId());
        theme.put("pspName", updatedPsp.getTradingName());
        theme.put("brandingTheme", updatedPsp.getBrandingTheme());
        theme.put("primaryColor", updatedPsp.getPrimaryColor());
        theme.put("secondaryColor", updatedPsp.getSecondaryColor());
        theme.put("accentColor", updatedPsp.getAccentColor());
        theme.put("logoUrl", updatedPsp.getLogoUrl());
        theme.put("fontFamily", updatedPsp.getFontFamily());
        theme.put("fontSize", updatedPsp.getFontSize());
        theme.put("buttonRadius", updatedPsp.getButtonRadius());
        theme.put("buttonStyle", updatedPsp.getButtonStyle());
        theme.put("navStyle", updatedPsp.getNavStyle());

        return ResponseEntity.ok(theme);
    }

    /**
     * Get available theme presets
     * GET /settings/themes/presets
     */
    @GetMapping("/themes/presets")
    public ResponseEntity<Map<String, Map<String, String>>> getThemePresets() {
        Map<String, Map<String, String>> presets = new HashMap<>();
        
        // Default theme
        Map<String, String> defaultTheme = new HashMap<>();
        defaultTheme.put("primaryColor", "#a93226");
        defaultTheme.put("secondaryColor", "#922b21");
        defaultTheme.put("accentColor", "#8B4049");
        presets.put("default", defaultTheme);

        // Burgundy theme
        Map<String, String> burgundyTheme = new HashMap<>();
        burgundyTheme.put("primaryColor", "#800020");
        burgundyTheme.put("secondaryColor", "#9B2D30");
        burgundyTheme.put("accentColor", "#A52A2A");
        presets.put("burgundy", burgundyTheme);

        // Emerald theme
        Map<String, String> emeraldTheme = new HashMap<>();
        emeraldTheme.put("primaryColor", "#50C878");
        emeraldTheme.put("secondaryColor", "#00A86B");
        emeraldTheme.put("accentColor", "#028A0F");
        presets.put("emerald", emeraldTheme);

        // Purple theme
        Map<String, String> purpleTheme = new HashMap<>();
        purpleTheme.put("primaryColor", "#6A0DAD");
        purpleTheme.put("secondaryColor", "#8B00FF");
        purpleTheme.put("accentColor", "#9370DB");
        presets.put("purple", purpleTheme);

        return ResponseEntity.ok(presets);
    }
}
