package com.posgateway.aml.config.jev;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jev")
public class JevProperties {

    /** OpenRouter API key — never logged or exposed. */
    private String apiKey = "";

    /** OpenRouter model id. No hardcoded real default — unset means disabled. */
    private String model = "";

    private String fallbackModel = "";

    private String baseUrl = "https://openrouter.ai/api/v1";

    private Duration timeout = Duration.ofSeconds(15);

    private Duration inlineTimeout = Duration.ofMillis(500);

    private int maxTokens = 1024;

    private double temperature = 0.2;

    private String httpReferer = "https://hokeka.com";

    private String appTitle = "Hokeka";

    /** Per-tenant daily call budget (0 = unlimited). */
    private int dailyCallBudgetPerPsp = 0;

    /** Per-tenant daily spend cap in USD (0 = unlimited). */
    private double dailySpendCapUsdPerPsp = 0.0;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public void setFallbackModel(String fallbackModel) {
        this.fallbackModel = fallbackModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getInlineTimeout() {
        return inlineTimeout;
    }

    public void setInlineTimeout(Duration inlineTimeout) {
        this.inlineTimeout = inlineTimeout;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getHttpReferer() {
        return httpReferer;
    }

    public void setHttpReferer(String httpReferer) {
        this.httpReferer = httpReferer;
    }

    public String getAppTitle() {
        return appTitle;
    }

    public void setAppTitle(String appTitle) {
        this.appTitle = appTitle;
    }

    public int getDailyCallBudgetPerPsp() {
        return dailyCallBudgetPerPsp;
    }

    public void setDailyCallBudgetPerPsp(int dailyCallBudgetPerPsp) {
        this.dailyCallBudgetPerPsp = dailyCallBudgetPerPsp;
    }

    public double getDailySpendCapUsdPerPsp() {
        return dailySpendCapUsdPerPsp;
    }

    public void setDailySpendCapUsdPerPsp(double dailySpendCapUsdPerPsp) {
        this.dailySpendCapUsdPerPsp = dailySpendCapUsdPerPsp;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
