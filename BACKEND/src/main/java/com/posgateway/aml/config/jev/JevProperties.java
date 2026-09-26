package com.posgateway.aml.config.jev;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jev")
public class JevProperties {

    /** OpenRouter API key — never logged or exposed. */
    private String apiKey = "";

    /** Decisions API base URL (alpha). Model is pinned separately. */
    private String decisionsBaseUrl = "https://openrouter.ai/api/alpha";

    /** Async Jev call timeout. */
    private Duration decisionsTimeout = Duration.ofSeconds(3);

    /** Retries after the first attempt for 429/5xx only. */
    private int maxRetries = 2;

    /** Shadow mode: log-only, no decision/queue/priority mutations (default). */
    private boolean shadowMode = true;

    /** Explicit promotion flag; both this and {@link #shadowMode=false} are required to apply mutations. */
    private boolean promoted = false;

    /** Provisional bands version label persisted with each audit row. */
    private String bandsVersion = "provisional-v1";

    /** Chat completions model for non-Jev generation tasks (rule suggestion, etc.). */
    private String chatModel = "";

    private String chatFallbackModel = "";

    private String chatBaseUrl = "https://openrouter.ai/api/v1";

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

    public String getDecisionsBaseUrl() {
        return decisionsBaseUrl;
    }

    public void setDecisionsBaseUrl(String decisionsBaseUrl) {
        this.decisionsBaseUrl = decisionsBaseUrl;
    }

    public Duration getDecisionsTimeout() {
        return decisionsTimeout;
    }

    public void setDecisionsTimeout(Duration decisionsTimeout) {
        this.decisionsTimeout = decisionsTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isShadowMode() {
        return shadowMode;
    }

    public void setShadowMode(boolean shadowMode) {
        this.shadowMode = shadowMode;
    }

    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    public String getBandsVersion() {
        return bandsVersion;
    }

    public void setBandsVersion(String bandsVersion) {
        this.bandsVersion = bandsVersion;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getChatFallbackModel() {
        return chatFallbackModel;
    }

    public void setChatFallbackModel(String chatFallbackModel) {
        this.chatFallbackModel = chatFallbackModel;
    }

    public String getChatBaseUrl() {
        return chatBaseUrl;
    }

    public void setChatBaseUrl(String chatBaseUrl) {
        this.chatBaseUrl = chatBaseUrl;
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

    /** Jev Decisions API is configured when the OpenRouter key is present. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Chat LLM path for generation tasks (rule suggestion, narratives). */
    public boolean isChatConfigured() {
        return isConfigured() && chatModel != null && !chatModel.isBlank();
    }

    /** Pinned model id exposed for status endpoints. */
    public String pinnedModel() {
        return com.posgateway.aml.service.jev.JevPinnedModel.MODEL_ID;
    }

    public boolean isActive() {
        return isConfigured() && promoted && !shadowMode;
    }
}
