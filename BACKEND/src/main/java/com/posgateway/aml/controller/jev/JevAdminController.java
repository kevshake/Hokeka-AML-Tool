package com.posgateway.aml.controller.jev;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.jev.JevEngineSetting;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.jev.JevAuditAccessService;
import com.posgateway.aml.service.jev.JevEngineConfigService;
import com.posgateway.aml.service.jev.JevStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/jev")
public class JevAdminController {

    private final JevStatusService statusService;
    private final JevEngineConfigService engineConfigService;
    private final JevAuditAccessService auditAccessService;
    private final PspRepository pspRepository;

    public JevAdminController(JevStatusService statusService,
                              JevEngineConfigService engineConfigService,
                              JevAuditAccessService auditAccessService,
                              PspRepository pspRepository) {
        this.statusService = statusService;
        this.engineConfigService = engineConfigService;
        this.auditAccessService = auditAccessService;
        this.pspRepository = pspRepository;
    }

    @GetMapping("/status")
    @PreAuthorize("@jevOperatorAuth.isPlatformOperator()")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(statusService.status());
    }

    @GetMapping("/engines")
    @PreAuthorize("@jevOperatorAuth.isPlatformOperator()")
    public ResponseEntity<List<JevEngineSetting>> engines() {
        return ResponseEntity.ok(engineConfigService.listAll());
    }

    @PutMapping("/engines/{engineCode}")
    @PreAuthorize("@jevOperatorAuth.isPlatformOperator()")
    public ResponseEntity<JevEngineSetting> updateEngine(
            @PathVariable String engineCode,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        Boolean enabled = body.get("enabled") instanceof Boolean b ? b : null;
        Boolean advisoryOnly = body.get("advisoryOnly") instanceof Boolean b ? b : null;
        String promptVersion = body.get("promptVersion") != null ? String.valueOf(body.get("promptVersion")) : null;
        String updatedBy = user != null ? user.getUsername() : "system";
        return ResponseEntity.ok(engineConfigService.update(engineCode, enabled, advisoryOnly, promptVersion, updatedBy));
    }

    @GetMapping("/psps/{pspId}/ai-settings")
    @PreAuthorize("@jevOperatorAuth.isPlatformOperator()")
    public ResponseEntity<Map<String, Object>> pspAiSettings(@PathVariable Long pspId) {
        Psp psp = pspRepository.findById(pspId).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pspId", psp.getPspId());
        out.put("aiInlineMode", psp.getAiInlineMode());
        out.put("aiInlineBudgetMs", psp.getAiInlineBudgetMs());
        return ResponseEntity.ok(out);
    }

    @PutMapping("/psps/{pspId}/ai-settings")
    @PreAuthorize("@jevOperatorAuth.isPlatformOperator()")
    public ResponseEntity<Map<String, Object>> updatePspAiSettings(
            @PathVariable Long pspId,
            @RequestBody Map<String, Object> body) {
        Psp psp = pspRepository.findById(pspId).orElseThrow();
        if (body.containsKey("aiInlineMode")) {
            psp.setAiInlineMode(Boolean.TRUE.equals(body.get("aiInlineMode")));
        }
        if (body.get("aiInlineBudgetMs") instanceof Number n) {
            psp.setAiInlineBudgetMs(n.intValue());
        }
        pspRepository.save(psp);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pspId", psp.getPspId());
        out.put("aiInlineMode", psp.getAiInlineMode());
        out.put("aiInlineBudgetMs", psp.getAiInlineBudgetMs());
        return ResponseEntity.ok(out);
    }

    @GetMapping("/audit/transaction/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> auditForTransaction(@PathVariable Long transactionId) {
        return ResponseEntity.ok(auditAccessService.forTransaction(transactionId));
    }

    @GetMapping("/audit/alert/{alertId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> auditForAlert(@PathVariable Long alertId) {
        return ResponseEntity.ok(auditAccessService.forAlert(alertId));
    }

    @GetMapping("/audit/case/{caseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> auditForCase(@PathVariable Long caseId) {
        return ResponseEntity.ok(auditAccessService.forCase(caseId));
    }

    @GetMapping("/audit/merchant/{merchantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> auditForMerchant(
            @PathVariable Long merchantId,
            @RequestParam(required = false) String engine) {
        return ResponseEntity.ok(auditAccessService.forMerchant(merchantId, engine));
    }

    @GetMapping("/audit/id/{auditId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> auditById(@PathVariable Long auditId) {
        return ResponseEntity.ok(auditAccessService.byId(auditId));
    }

    @GetMapping("/audit/screening/{screeningHitId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> auditForScreeningHit(
            @PathVariable String screeningHitId) {
        return ResponseEntity.ok(auditAccessService.forScreeningHit(screeningHitId));
    }
}
