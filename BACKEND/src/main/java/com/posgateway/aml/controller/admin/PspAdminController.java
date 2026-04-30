package com.posgateway.aml.controller.admin;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;

import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/admin/psp")
public class PspAdminController {

    private final PspRepository pspRepository;
    private final PermissionService permissionService;

    public PspAdminController(PspRepository pspRepository, PermissionService permissionService) {
        this.pspRepository = pspRepository;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<List<Psp>> list() {
        return ResponseEntity.ok(pspRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Psp> create(@RequestBody CreatePspRequest req, @RequestHeader("X-User-Role") String role) {
        ensurePspAdmin(role);
        if (pspRepository.existsByPspCode(req.getPspCode())) {
            throw new IllegalArgumentException("PSP code already exists");
        }
        Psp psp = Psp.builder()
                .pspCode(req.getPspCode())
                .legalName(req.getLegalName())
                .tradingName(req.getTradingName())
                .country(req.getCountry())
                .contactEmail(req.getContactEmail())
                .contactPhone(req.getContactPhone())
                .billingPlan(req.getBillingPlan() != null ? req.getBillingPlan() : "PAY_AS_YOU_GO")
                .billingCycle(req.getBillingCycle() != null ? req.getBillingCycle() : "MONTHLY")
                .status("PENDING")
                .logoUrl(req.getLogoUrl())
                .primaryColor(req.getPrimaryColor())
                .secondaryColor(req.getSecondaryColor())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(pspRepository.save(psp));
    }

    @PutMapping("/{pspId}/activate")
    public ResponseEntity<Psp> activate(@PathVariable Long pspId, @RequestHeader("X-User-Role") String role) {
        ensurePspAdmin(role);
        Psp psp = pspRepository.findById(pspId).orElseThrow(() -> new IllegalArgumentException("PSP not found"));
        psp.activate();
        return ResponseEntity.ok(pspRepository.save(psp));
    }

    @PutMapping("/{pspId}/suspend")
    public ResponseEntity<Psp> suspend(@PathVariable Long pspId, @RequestHeader("X-User-Role") String role) {
        ensurePspAdmin(role);
        Psp psp = pspRepository.findById(pspId).orElseThrow(() -> new IllegalArgumentException("PSP not found"));
        psp.suspend("manual");
        return ResponseEntity.ok(pspRepository.save(psp));
    }

    @PutMapping("/{pspId}/terminate")
    public ResponseEntity<Psp> terminate(@PathVariable Long pspId, @RequestHeader("X-User-Role") String role) {
        ensurePspAdmin(role);
        Psp psp = pspRepository.findById(pspId).orElseThrow(() -> new IllegalArgumentException("PSP not found"));
        psp.terminate();
        return ResponseEntity.ok(pspRepository.save(psp));
    }

    @PutMapping("/{pspId}/theme")
    public ResponseEntity<Psp> updateTheme(@PathVariable Long pspId,
            @RequestBody ThemeRequest req,
            @RequestHeader("X-User-Role") String role) {
        ensurePspAdmin(role);
        Psp psp = pspRepository.findById(pspId).orElseThrow(() -> new IllegalArgumentException("PSP not found"));
        psp.setLogoUrl(req.getLogoUrl());
        psp.setPrimaryColor(req.getPrimaryColor());
        psp.setSecondaryColor(req.getSecondaryColor());
        psp.setAccentColor(req.getAccentColor());
        psp.setFontFamily(req.getFontFamily());
        psp.setFontSize(req.getFontSize());
        psp.setButtonRadius(req.getButtonRadius());
        psp.setButtonStyle(req.getButtonStyle());
        psp.setNavStyle(req.getNavStyle());
        psp.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(pspRepository.save(psp));
    }

    @DeleteMapping("/{pspId}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @RequestHeader("X-User-Role") String role) {
        ensurePspAdmin(role);
        pspRepository.deleteById(pspId);
        return ResponseEntity.noContent().build();
    }

    private void ensurePspAdmin(String role) { // role param ignored now, preserved for signature compatibility if
                                               // needed, but safer to ignore.
        if (!permissionService.hasPermission(Permission.MANAGE_PSP)) {
            throw new SecurityException("Not authorized - requires MANAGE_PSP permission");
        }
    }

    public static class CreatePspRequest {
        private String pspCode;
        private String legalName;
        private String tradingName;
        private String country;
        private String contactEmail;
        private String contactPhone;
        private String billingPlan;
        private String billingCycle;
        private String logoUrl;
        private String primaryColor;
        private String secondaryColor;

        public CreatePspRequest() {
        }

        public String getPspCode() {
            return pspCode;
        }

        public void setPspCode(String pspCode) {
            this.pspCode = pspCode;
        }

        public String getLegalName() {
            return legalName;
        }

        public void setLegalName(String legalName) {
            this.legalName = legalName;
        }

        public String getTradingName() {
            return tradingName;
        }

        public void setTradingName(String tradingName) {
            this.tradingName = tradingName;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getContactEmail() {
            return contactEmail;
        }

        public void setContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
        }

        public String getContactPhone() {
            return contactPhone;
        }

        public void setContactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
        }

        public String getBillingPlan() {
            return billingPlan;
        }

        public void setBillingPlan(String billingPlan) {
            this.billingPlan = billingPlan;
        }

        public String getBillingCycle() {
            return billingCycle;
        }

        public void setBillingCycle(String billingCycle) {
            this.billingCycle = billingCycle;
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public void setLogoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public String getSecondaryColor() {
            return secondaryColor;
        }

        public void setSecondaryColor(String secondaryColor) {
            this.secondaryColor = secondaryColor;
        }
    }

    public static class ThemeRequest {
        private String logoUrl;
        private String primaryColor;
        private String secondaryColor;
        private String accentColor;
        private String fontFamily;
        private String fontSize;
        private String buttonRadius;
        private String buttonStyle;
        private String navStyle;

        public ThemeRequest() {
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public void setLogoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public String getSecondaryColor() {
            return secondaryColor;
        }

        public void setSecondaryColor(String secondaryColor) {
            this.secondaryColor = secondaryColor;
        }

        public String getAccentColor() {
            return accentColor;
        }

        public void setAccentColor(String accentColor) {
            this.accentColor = accentColor;
        }

        public String getFontFamily() {
            return fontFamily;
        }

        public void setFontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
        }

        public String getFontSize() {
            return fontSize;
        }

        public void setFontSize(String fontSize) {
            this.fontSize = fontSize;
        }

        public String getButtonRadius() {
            return buttonRadius;
        }

        public void setButtonRadius(String buttonRadius) {
            this.buttonRadius = buttonRadius;
        }

        public String getButtonStyle() {
            return buttonStyle;
        }

        public void setButtonStyle(String buttonStyle) {
            this.buttonStyle = buttonStyle;
        }

        public String getNavStyle() {
            return navStyle;
        }

        public void setNavStyle(String navStyle) {
            this.navStyle = navStyle;
        }
    }
}
