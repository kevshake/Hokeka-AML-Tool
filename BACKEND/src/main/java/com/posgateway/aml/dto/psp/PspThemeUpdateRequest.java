package com.posgateway.aml.dto.psp;

import lombok.Data;

/**
 * DTO for updating PSP theme/branding configuration
 * Uses Lombok @Data annotation for getters/setters
 */
@Data
public class PspThemeUpdateRequest {
    private String brandingTheme;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String logoUrl;
    private String faviconUrl;
    private String fontFamily;
    private String fontSize;
    private String buttonRadius;
    private String buttonStyle;
    private String navStyle;
    private String customCss;

    public PspThemeUpdateRequest() {
    }

    public String getBrandingTheme() { return brandingTheme; }
    public void setBrandingTheme(String brandingTheme) { this.brandingTheme = brandingTheme; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getFaviconUrl() { return faviconUrl; }
    public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontSize() { return fontSize; }
    public void setFontSize(String fontSize) { this.fontSize = fontSize; }

    public String getButtonRadius() { return buttonRadius; }
    public void setButtonRadius(String buttonRadius) { this.buttonRadius = buttonRadius; }

    public String getButtonStyle() { return buttonStyle; }
    public void setButtonStyle(String buttonStyle) { this.buttonStyle = buttonStyle; }

    public String getNavStyle() { return navStyle; }
    public void setNavStyle(String navStyle) { this.navStyle = navStyle; }

    public String getCustomCss() { return customCss; }
    public void setCustomCss(String customCss) { this.customCss = customCss; }
}
