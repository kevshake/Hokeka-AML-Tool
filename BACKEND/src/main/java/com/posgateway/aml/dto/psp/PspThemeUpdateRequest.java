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
}
