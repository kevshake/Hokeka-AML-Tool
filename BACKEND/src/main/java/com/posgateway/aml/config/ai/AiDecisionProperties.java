package com.posgateway.aml.config.ai;

import com.posgateway.aml.service.ai.decision.LayaModelRouting;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "hokeka.ai")
public class AiDecisionProperties {

    /** Laya API key ({@code LAYA_API_KEY}) — never logged or exposed. */
    private String apiKey = "";

    /** Laya API base URL. */
    private String apiBaseUrl = "https://api.laya.studio";

    /** Optional Laya checkpoint pin: english, multilingual, typed-decisions (blank = auto). */
    private String model = "";

    /** Optional BCP-47 language hint (e.g. en, de-CH). */
    private String lang = "";

    /** Async Laya systemone call timeout. */
    private Duration decisionsTimeout = Duration.ofSeconds(3);

    /** Retries after the first attempt for 429/5xx only. */
    private int maxRetries = 2;

    /** Shadow mode: log-only, no decision/queue/priority mutations (default). */
    private boolean shadowMode = true;

    /** Explicit promotion flag; both this and {@link #shadowMode=false} are required to apply mutations. */
    private boolean promoted = false;

    /** Provisional bands version label persisted with each audit row. */
    private String bandsVersion = "provisional-v1";

    /** Minimum primary confidence to treat Laya output as actionable (else rules + human review). */
    private double minConfidenceToApply = 0.55;

    /** Minimum confidence to auto-apply when promoted (non-shadow); below → escalate human. */
    private double minConfidenceToAutoAct = 0.72;

    private Duration inlineTimeout = Duration.ofMillis(500);

    /** Per-tenant daily call budget (0 = unlimited). */
    private int dailyCallBudgetPerPsp = 0;

    /**
     * Per-tenant daily input-token budget (0 = unlimited). Replaces legacy USD spend cap for Laya metering.
     */
    private long dailyInputTokenCapPerPsp = 0;

    /** Generation / ask path timeout (rule suggestion). */
    private Duration askTimeout = Duration.ofSeconds(30);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    /** @deprecated use {@link #getApiBaseUrl()} */
    @Deprecated
    public String getDecisionsBaseUrl() {
        return apiBaseUrl;
    }

    /** @deprecated use {@link #setApiBaseUrl(String)} */
    @Deprecated
    public void setDecisionsBaseUrl(String decisionsBaseUrl) {
        this.apiBaseUrl = decisionsBaseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
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

    public double getMinConfidenceToApply() {
        return minConfidenceToApply;
    }

    public void setMinConfidenceToApply(double minConfidenceToApply) {
        this.minConfidenceToApply = minConfidenceToApply;
    }

    public double getMinConfidenceToAutoAct() {
        return minConfidenceToAutoAct;
    }

    public void setMinConfidenceToAutoAct(double minConfidenceToAutoAct) {
        this.minConfidenceToAutoAct = minConfidenceToAutoAct;
    }

    public Duration getInlineTimeout() {
        return inlineTimeout;
    }

    public void setInlineTimeout(Duration inlineTimeout) {
        this.inlineTimeout = inlineTimeout;
    }

    public int getDailyCallBudgetPerPsp() {
        return dailyCallBudgetPerPsp;
    }

    public void setDailyCallBudgetPerPsp(int dailyCallBudgetPerPsp) {
        this.dailyCallBudgetPerPsp = dailyCallBudgetPerPsp;
    }

    public long getDailyInputTokenCapPerPsp() {
        return dailyInputTokenCapPerPsp;
    }

    public void setDailyInputTokenCapPerPsp(long dailyInputTokenCapPerPsp) {
        this.dailyInputTokenCapPerPsp = dailyInputTokenCapPerPsp;
    }

    /** @deprecated Laya bills input tokens; use {@link #getDailyInputTokenCapPerPsp()}. */
    @Deprecated
    public double getDailySpendCapUsdPerPsp() {
        return dailyInputTokenCapPerPsp > 0 ? dailyInputTokenCapPerPsp : 0;
    }

    /** @deprecated use {@link #setDailyInputTokenCapPerPsp(long)} */
    @Deprecated
    public void setDailySpendCapUsdPerPsp(double dailySpendCapUsdPerPsp) {
        this.dailyInputTokenCapPerPsp = dailySpendCapUsdPerPsp > 0 ? (long) dailySpendCapUsdPerPsp : 0;
    }

    public Duration getAskTimeout() {
        return askTimeout;
    }

    public void setAskTimeout(Duration askTimeout) {
        this.askTimeout = askTimeout;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String pinnedModel() {
        return LayaModelRouting.requestModel(model);
    }

    public boolean isActive() {
        return isConfigured() && promoted && !shadowMode;
    }
}
