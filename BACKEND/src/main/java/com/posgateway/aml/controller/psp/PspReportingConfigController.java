package com.posgateway.aml.controller.psp;



import com.posgateway.aml.dto.psp.PspReportConfigRequest;
import com.posgateway.aml.dto.psp.PspReportConfigResponse;
import com.posgateway.aml.service.psp.PspReportingConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/psps")
public class PspReportingConfigController {

    private final PspReportingConfigService configService;

    public PspReportingConfigController(PspReportingConfigService configService) {
        this.configService = configService;
    }


    @GetMapping("/{pspId}/report-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'APP_CONTROLLER', 'PSP_ADMIN', 'PSP_USER')")
    public ResponseEntity<PspReportConfigResponse> getConfig(@PathVariable Long pspId) {
        try {
            return ResponseEntity.ok(configService.getConfig(pspId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{pspId}/report-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'APP_CONTROLLER', 'PSP_ADMIN')")
    public ResponseEntity<PspReportConfigResponse> updateConfig(@PathVariable Long pspId,
            @RequestBody PspReportConfigRequest request) {
        return ResponseEntity.ok(configService.updateConfig(pspId, request));
    }
}
