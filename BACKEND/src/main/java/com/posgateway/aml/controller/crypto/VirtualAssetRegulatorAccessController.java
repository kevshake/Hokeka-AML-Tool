package com.posgateway.aml.controller.crypto;

import com.posgateway.aml.service.crypto.VirtualAssetComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/regulator/virtual-assets")
public class VirtualAssetRegulatorAccessController {
    private final VirtualAssetComplianceService service;
    public VirtualAssetRegulatorAccessController(VirtualAssetComplianceService service) { this.service = service; }

    @GetMapping("/transactions")
    public Page<Map<String, Object>> transactions(
            @RequestHeader("X-Regulator-Access-Key") String accessKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "false") boolean includePii,
            HttpServletRequest request) {
        String sourceIp = request.getRemoteAddr();
        return service.regulatorTransactions(accessKey, sourceIp,
                request.getHeader("User-Agent"), from, to, page, size, includePii);
    }

    @GetMapping("/wallet-screenings")
    public Page<Map<String, Object>> walletScreenings(
            @RequestHeader("X-Regulator-Access-Key") String accessKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            HttpServletRequest request) {
        return service.regulatorWalletScreenings(accessKey, request.getRemoteAddr(),
                request.getHeader("User-Agent"), from, to, page, size);
    }

    @GetMapping("/travel-rule-transfers")
    public Page<Map<String, Object>> travelRuleTransfers(
            @RequestHeader("X-Regulator-Access-Key") String accessKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "false") boolean includePii,
            HttpServletRequest request) {
        return service.regulatorTravelRuleTransfers(accessKey, request.getRemoteAddr(),
                request.getHeader("User-Agent"), from, to, page, size, includePii);
    }
}
